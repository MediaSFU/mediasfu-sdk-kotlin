package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.util.Logger
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.mediasoup.droid.Consumer
import org.mediasoup.droid.Device
import org.mediasoup.droid.MediasoupClient
import org.mediasoup.droid.MediasoupException
import org.mediasoup.droid.PeerConnection
import org.mediasoup.droid.Producer
import org.mediasoup.droid.RecvTransport
import org.mediasoup.droid.SendTransport
import org.mediasoup.droid.Transport
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CapturerObserver
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.HardwareVideoDecoderFactory
import org.webrtc.JavaI420Buffer
import org.webrtc.PlatformSoftwareVideoDecoderFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SoftwareVideoDecoderFactory
import org.webrtc.VideoCodecInfo
import org.webrtc.VideoDecoder
import org.webrtc.VideoDecoderFactory
import org.webrtc.VideoDecoderFallback
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpParameters as NativeRtpParameters
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.VideoFrame
import org.webrtc.VideoSink
import org.webrtc.audio.AudioDeviceModule
import org.webrtc.audio.JavaAudioDeviceModule
import org.webrtc.MediaStream as NativeMediaStream
import org.webrtc.MediaStreamTrack as NativeMediaStreamTrack
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min

private const val TAG = "MediaSFU-WebRTC"

private val FLUTTER_BASELINE_VIDEO_HEADER_URIS = setOf(
    "urn:ietf:params:rtp-hdrext:sdes:mid",
    "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
    "http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01",
    "urn:3gpp:video-orientation",
    "urn:ietf:params:rtp-hdrext:toffset",
    "http://www.webrtc.org/experiments/rtp-hdrext/playout-delay"
)

/**
 * Android implementation of the WebRTC device backed by mediasoup-client.
 */
class AndroidWebRtcDevice private constructor(
    private val appContext: Context
) : WebRtcDevice {

    private val device = Device()
    @Volatile private var isLoaded = false
    @Volatile private var lastLoadedCapabilities: RtpCapabilities? = null
    private val loadMutex = Mutex()

    private val eglBaseDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val base = EglBase.create()
        // Register the EGL context globally so renderers can share textures with decoders
        SharedEglContext.setEglBase(base)
        base
    }
    private val eglBase by eglBaseDelegate

    private val audioModuleDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createAudioDeviceModule(appContext)
    }
    private val audioModule by audioModuleDelegate

    private val audioFocusManagerDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AndroidAudioFocusManager(appContext)
    }
    private val audioFocusManager by audioFocusManagerDelegate

    private val peerFactoryDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        createPeerConnectionFactory()
    }
    private val peerFactory by peerFactoryDelegate

    private val connectHandlers = ConcurrentHashMap<String, (ConnectData) -> Unit>()
    private val pendingConnectEvents = ConcurrentHashMap<String, PendingConnectEvent>()
    private val produceHandlers = ConcurrentHashMap<String, (ProduceData) -> Unit>()
    private val pendingProduceEvents = ConcurrentHashMap<String, MutableList<PendingProduceEvent>>()
    private val produceEventTimeoutMs = 5000L
    private val connectEventTimeoutMs = 10000L  // Connect can take longer due to DTLS handshake
    private val stateHandlers = ConcurrentHashMap<String, (String) -> Unit>()
    private val transports = Collections.newSetFromMap(ConcurrentHashMap<MediasoupTransport, Boolean>())
    private val activeProducers = Collections.newSetFromMap(ConcurrentHashMap<AndroidWebRtcProducer, Boolean>())
    private val pendingEncodingOverrides = ConcurrentHashMap<String, ConcurrentLinkedQueue<List<RtpEncodingParameters>>>()
    private val activeAudioConsumers = AtomicInteger(0)
    private val debugVideoSinks = ConcurrentHashMap<String, VideoSink>()
    private val eagerVideoProbes = ConcurrentHashMap<String, VideoProbeHandle>()
    private val videoProbeEnabled = true

    private data class VideoProbeHandle(
        val track: VideoTrack,
        val sink: VideoSink,
        val startedAtMs: Long
    )

    companion object {
        @Volatile private var INSTANCE: AndroidWebRtcDevice? = null
        @Volatile private var mediasoupInitialized = false
        @Volatile private var peerConnectionInitialized = false

        fun getInstance(context: Context): AndroidWebRtcDevice {
            val appContext = context.applicationContext
            ensureMediasoupInitialized(appContext)
            ensurePeerConnectionInitialized(appContext)
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AndroidWebRtcDevice(appContext).also { INSTANCE = it }
            }
        }

        private fun ensureMediasoupInitialized(context: Context) {
            if (!mediasoupInitialized) {
                runCatching { MediasoupClient.initialize(context) }
                    .onSuccess { 
                        mediasoupInitialized = true
                        // Enable debug logging in native mediasoup-client library
                        org.mediasoup.droid.Logger.setLogLevel(org.mediasoup.droid.Logger.LogLevel.LOG_DEBUG)
                        org.mediasoup.droid.Logger.setDefaultHandler()
                        Log.d("AndroidWebRtcDevice", "Mediasoup initialized with DEBUG logging enabled")
                    }
                    .onFailure { error -> throw error }
            }
        }

        private fun ensurePeerConnectionInitialized(context: Context) {
            if (!peerConnectionInitialized) {
                val options = PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()
                PeerConnectionFactory.initialize(options)
                peerConnectionInitialized = true
            }
        }
    }

    override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
        // NOTE: Native mediasoup Device::Load is not thread-safe; concurrent calls can crash (SIGSEGV).
        // We serialize load() so only one call enters the native layer at a time.
        return loadMutex.withLock {
            if (isLoaded) {
                return@withLock Result.success(Unit)
            }

            withContext(Dispatchers.Default) {
                runCatching {
                    // === CODEC DEBUG: Log router RTP capabilities ===
                    Logger.d("AndroidWebRtcDevice", "\n=== KOTLIN DEVICE LOAD ===")
                    Logger.d("AndroidWebRtcDevice", "  Router Video Codecs:")
                    rtpCapabilities.codecs
                        .filter { it.mimeType.startsWith("video/", ignoreCase = true) }
                        .forEach { codec ->
                            Logger.d("AndroidWebRtcDevice", "    ${codec.mimeType} PT=${codec.preferredPayloadType} clockRate=${codec.clockRate}")
                            codec.parameters?.let { Logger.d("AndroidWebRtcDevice", "      params: $it") }
                        }
                    Logger.d("AndroidWebRtcDevice", "=== END DEVICE LOAD INPUT ===\n")

                    val options = PeerConnection.Options().apply {
                        setFactory(peerFactory)
                    }
                    val payload = rtpCapabilities.toJsonString()
                    device.load(payload, options)

                    isLoaded = true
                    lastLoadedCapabilities = fetchDeviceRtpCapabilitiesInternal() ?: rtpCapabilities

                    // === CODEC DEBUG: Log device RTP capabilities after load ===
                    Logger.d("AndroidWebRtcDevice", "\n=== KOTLIN DEVICE LOADED CAPS ===")
                    Logger.d("AndroidWebRtcDevice", "  Device Video Codecs:")
                    lastLoadedCapabilities?.codecs
                        ?.filter { it.mimeType.startsWith("video/", ignoreCase = true) }
                        ?.forEach { codec ->
                            Logger.d("AndroidWebRtcDevice", "    ${codec.mimeType} PT=${codec.preferredPayloadType} clockRate=${codec.clockRate}")
                            codec.parameters?.let { Logger.d("AndroidWebRtcDevice", "      params: $it") }
                        }
                    Logger.d("AndroidWebRtcDevice", "=== END DEVICE LOADED CAPS ===\n")
                }
            }
        }
    }

    override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
        return withContext(Dispatchers.Default) {
            val audioEnabled = resolveAudioEnabled(constraints["audio"])
            val videoRequest = constraints["video"]
            val videoEnabled = resolveVideoEnabled(videoRequest)

            val streamId = "android-stream-${UUID.randomUUID()}"
            val nativeStream = peerFactory.createLocalMediaStream(streamId)

            var audioSource: AudioSource? = null
            var audioTrack: AudioTrack? = null

            if (audioEnabled) {
                val audioConstraints = createAudioConstraints(constraints["audio"])
                audioSource = peerFactory.createAudioSource(audioConstraints)
                audioTrack = peerFactory.createAudioTrack("audio-$streamId", audioSource).apply {
                    setEnabled(true)
                    nativeStream.addTrack(this)
                }
            }

            var videoSource: VideoSource? = null
            var videoTrack: VideoTrack? = null
            var videoCapturer: VideoCapturer? = null
            var surfaceTextureHelper: SurfaceTextureHelper? = null

            if (videoEnabled) {
                val captureConfig = resolveVideoSettings(videoRequest)
                videoCapturer = createVideoCapturer(captureConfig)
                    ?: throw IllegalStateException("MediaSFU - AndroidWebRtcDevice: No usable camera found")

                surfaceTextureHelper = SurfaceTextureHelper.create("MediaSFU-Capture", eglBase.eglBaseContext)
                videoSource = peerFactory.createVideoSource(videoCapturer.isScreencast)
                videoCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)

                runCatching {
                    videoCapturer.startCapture(captureConfig.width, captureConfig.height, captureConfig.frameRate)
                }.onFailure { error ->
                    videoCapturer.disposeSafely()
                    surfaceTextureHelper.dispose()
                    videoSource.dispose()
                    throw error
                }

                videoTrack = peerFactory.createVideoTrack("video-$streamId", videoSource).apply {
                    setEnabled(true)
                    nativeStream.addTrack(this)
                }
            }

            AndroidMediaStream(
                nativeStream = nativeStream,
                audioTrack = audioTrack,
                audioSource = audioSource,
                videoTrack = videoTrack,
                videoSource = videoSource,
                videoCapturer = videoCapturer,
                surfaceTextureHelper = surfaceTextureHelper
            )
        }
    }

    /**
     * Captures the screen for sharing using MediaProjection API.
     * 
     * This method creates a screen capture stream using Android's MediaProjection API.
     * The caller must have already obtained MediaProjection permission via 
     * Activity.startActivityForResult with MediaProjectionManager.createScreenCaptureIntent().
     * 
     * ## Required constraints:
     * ```kotlin
     * constraints = mapOf(
     *     "mediaProjection" to mapOf(
     *         "resultCode" to Activity.RESULT_OK,
     *         "data" to intent  // The Intent returned from onActivityResult
     *     ),
     *     "video" to mapOf(
     *         "width" to mapOf("ideal" to 1920),
     *         "height" to mapOf("ideal" to 1080),
     *         "frameRate" to mapOf("ideal" to 15, "max" to 30)
     *     )
     * )
     * ```
     * 
     * ## Usage from Activity:
     * ```kotlin
     * // 1. Request screen capture permission
     * val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
     * startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST_CODE)
     * 
     * // 2. In onActivityResult, pass data to getDisplayMedia
     * override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
     *     if (requestCode == SCREEN_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
     *         val constraints = mapOf(
     *             "mediaProjection" to mapOf("resultCode" to resultCode, "data" to data),
     *             "video" to mapOf("width" to mapOf("ideal" to 1920), "height" to mapOf("ideal" to 1080))
     *         )
     *         lifecycleScope.launch {
     *             val stream = webRtcDevice.getDisplayMedia(constraints)
     *             // Use stream for screen sharing
     *         }
     *     }
     * }
     * ```
     * 
     * @param constraints Map containing mediaProjection data and optional video constraints
     * @return MediaStream containing the screen capture video track
     * @throws IllegalArgumentException If mediaProjection data is missing or invalid
     * @throws IllegalStateException If screen capture initialization fails
     */
    override suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream {
        return withContext(Dispatchers.Default) {
            // Extract MediaProjection permission data
            @Suppress("UNCHECKED_CAST")
            val mediaProjectionData = constraints["mediaProjection"] as? Map<String, Any?>
                ?: throw IllegalArgumentException(
                    "MediaProjection permission data required. " +
                    "Call MediaProjectionManager.createScreenCaptureIntent() and pass the result."
                )
            
            val resultCode = mediaProjectionData["resultCode"] as? Int
                ?: throw IllegalArgumentException("resultCode is required in mediaProjection data")
            
            val data = mediaProjectionData["data"] as? Intent
                ?: throw IllegalArgumentException("data Intent is required in mediaProjection data")
            
            // Extract video constraints with defaults
            @Suppress("UNCHECKED_CAST")
            val videoConstraints = constraints["video"] as? Map<String, Any?> ?: emptyMap()
            val screenConfig = resolveScreenCaptureSettings(videoConstraints)
            
            Log.d(TAG, "Starting screen capture: ${screenConfig.width}x${screenConfig.height}@${screenConfig.frameRate}fps")
            
            // Create unique stream ID
            val streamId = "screen-${UUID.randomUUID()}"
            val nativeStream = peerFactory.createLocalMediaStream(streamId)
            
            // Create screen capturer using WebRTC's ScreenCapturerAndroid
            val screenCapturer = ScreenCapturerAndroid(data, object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                }
            })
            
            // Create surface texture helper for screen capture
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "MediaSFU-ScreenCapture",
                eglBase.eglBaseContext
            ) ?: throw IllegalStateException("Failed to create SurfaceTextureHelper for screen capture")
            
            // Create video source (isScreencast = true for screen capture)
            val videoSource = peerFactory.createVideoSource(true) // true = isScreencast
            
            // Initialize the screen capturer
            screenCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)
            
            // Start screen capture
            runCatching {
                screenCapturer.startCapture(screenConfig.width, screenConfig.height, screenConfig.frameRate)
                Log.d(TAG, "Screen capture started successfully")
            }.onFailure { error ->
                Log.e(TAG, "Failed to start screen capture", error)
                screenCapturer.dispose()
                surfaceTextureHelper.dispose()
                videoSource.dispose()
                throw IllegalStateException("Failed to start screen capture: ${error.message}", error)
            }
            
            // Create video track
            val videoTrack = peerFactory.createVideoTrack("screen-video-$streamId", videoSource).apply {
                setEnabled(true)
                nativeStream.addTrack(this)
            }
            
            Log.d(TAG, "Screen capture stream created: $streamId")
            
            // Return the stream with screen capture resources
            AndroidMediaStream(
                nativeStream = nativeStream,
                audioTrack = null,  // Screen capture doesn't include audio by default
                audioSource = null,
                videoTrack = videoTrack,
                videoSource = videoSource,
                videoCapturer = screenCapturer,
                surfaceTextureHelper = surfaceTextureHelper
            )
        }
    }
    
    /**
     * Resolves screen capture settings from video constraints.
     */
    private fun resolveScreenCaptureSettings(videoConstraints: Map<String, Any?>): ScreenCaptureConfig {
        @Suppress("UNCHECKED_CAST")
        val widthConstraint = videoConstraints["width"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val heightConstraint = videoConstraints["height"] as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val frameRateConstraint = videoConstraints["frameRate"] as? Map<String, Any?>
        
        // Extract ideal values with sensible defaults for screen sharing
        val width = (widthConstraint?.get("ideal") as? Number)?.toInt() ?: 1920
        val height = (heightConstraint?.get("ideal") as? Number)?.toInt() ?: 1080
        val frameRate = (frameRateConstraint?.get("ideal") as? Number)?.toInt() ?: 15
        val maxFrameRate = (frameRateConstraint?.get("max") as? Number)?.toInt() ?: 30
        
        // Clamp frame rate to max
        val finalFrameRate = minOf(frameRate, maxFrameRate)
        
        return ScreenCaptureConfig(
            width = width.coerceIn(320, 3840),
            height = height.coerceIn(240, 2160),
            frameRate = finalFrameRate.coerceIn(1, 60)
        )
    }
    
    /**
     * Configuration for screen capture.
     */
    private data class ScreenCaptureConfig(
        val width: Int,
        val height: Int,
        val frameRate: Int
    )

    override suspend fun enumerateDevices(): List<MediaDeviceInfo> {
        return withContext(Dispatchers.Default) {
            val devices = mutableListOf<MediaDeviceInfo>()
            
            // Enumerate audio input devices using AudioManager
            val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && audioManager != null) {
                // Device types that are useful for VoIP/WebRTC
                val usableDeviceTypes = setOf(
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC,
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    android.media.AudioDeviceInfo.TYPE_HEARING_AID,
                    android.media.AudioDeviceInfo.TYPE_BLE_HEADSET,
                    android.media.AudioDeviceInfo.TYPE_BLE_SPEAKER,
                )
                
                // Get audio input devices (microphones, Bluetooth headsets, etc.)
                val inputDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_INPUTS)
                inputDevices.filter { it.type in usableDeviceTypes }.forEach { deviceInfo ->
                    val deviceId = "audio_input_${deviceInfo.id}"
                    val label = getAudioDeviceLabel(deviceInfo)
                    val groupId = when (deviceInfo.type) {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
                        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
                        else -> "builtin"
                    }
                    devices += MediaDeviceInfo(
                        deviceId = deviceId,
                        kind = "audioinput",
                        label = label,
                        groupId = groupId
                    )
                }
                
                // Get audio output devices (speakers, Bluetooth headsets, etc.)
                val outputDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                outputDevices.filter { it.type in usableDeviceTypes }.forEach { deviceInfo ->
                    val deviceId = "audio_output_${deviceInfo.id}"
                    val label = getAudioDeviceLabel(deviceInfo)
                    val groupId = when (deviceInfo.type) {
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "bluetooth"
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
                        android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
                        android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "usb"
                        else -> "builtin"
                    }
                    devices += MediaDeviceInfo(
                        deviceId = deviceId,
                        kind = "audiooutput",
                        label = label,
                        groupId = groupId
                    )
                }
            }
            
            // Fallback if no devices were found or API level is too low
            if (devices.none { it.kind == "audioinput" }) {
                devices += MediaDeviceInfo(
                    deviceId = "default_audio_input",
                    kind = "audioinput",
                    label = "Default Microphone",
                    groupId = "builtin"
                )
            }
            if (devices.none { it.kind == "audiooutput" }) {
                devices += MediaDeviceInfo(
                    deviceId = "default_audio_output",
                    kind = "audiooutput",
                    label = "Default Speaker",
                    groupId = "builtin"
                )
            }

            val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(appContext)) {
                Camera2Enumerator(appContext)
            } else {
                Camera1Enumerator(true)
            }

            // Track camera counts for labeling
            var frontCount = 0
            var backCount = 0
            var otherCount = 0

            enumerator.deviceNames.forEach { name ->
                // Determine camera facing and create a meaningful label
                val label = when {
                    enumerator.isFrontFacing(name) -> {
                        frontCount++
                        if (frontCount == 1) "Front Camera" else "Front Camera $frontCount"
                    }
                    enumerator.isBackFacing(name) -> {
                        backCount++
                        when (backCount) {
                            1 -> "Back Camera"
                            2 -> "Back Camera (Wide)"
                            3 -> "Back Camera (Ultra Wide)"
                            4 -> "Back Camera (Telephoto)"
                            else -> "Back Camera $backCount"
                        }
                    }
                    else -> {
                        otherCount++
                        "Camera $otherCount"
                    }
                }
                
                devices += MediaDeviceInfo(
                    deviceId = name,
                    kind = "videoinput",
                    label = label,
                    groupId = "video"
                )
            }
            
            // Deduplicate devices with the same kind and label (keep first occurrence)
            devices.distinctBy { "${it.kind}:${it.label}" }
        }
    }
    
    /**
     * Gets a human-readable label for an audio device.
     */
    private fun getAudioDeviceLabel(deviceInfo: android.media.AudioDeviceInfo): String {
        // For built-in devices, always use type-based labels (productName is often just the device model)
        val typeLabel = when (deviceInfo.type) {
            android.media.AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Audio"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
            android.media.AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
            android.media.AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
            android.media.AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
            android.media.AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line Analog"
            android.media.AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            android.media.AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            android.media.AudioDeviceInfo.TYPE_AUX_LINE -> "Aux Line"
            else -> null
        }
        
        // If we have a known type, use that label
        if (typeLabel != null) return typeLabel
        
        // For external/unknown devices, try to get the product name (available on API 28+)
        val productName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            deviceInfo.productName?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        } else null
        
        return productName ?: "Audio Device ${deviceInfo.id}"
    }

    override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
        val transportId = params["id"] as? String
            ?: throw MediasoupException("Send transport id missing")
        val iceParameters = params["iceParameters"].toJsonString()
            ?: throw MediasoupException("Send transport iceParameters missing")
        val iceCandidates = params["iceCandidates"].toJsonString()
            ?: throw MediasoupException("Send transport iceCandidates missing")
        val dtlsParameters = params["dtlsParameters"].toJsonString()
            ?: throw MediasoupException("Send transport dtlsParameters missing")
        val sctpParameters = params["sctpParameters"].toJsonString()
        val appData = params["appData"].toJsonString()

        val listener = object : SendTransport.Listener {
            override fun onConnect(transport: Transport, dtlsParameters: String) {
                Logger.d("AndroidWebRtcDevice", "=== NATIVE SEND onConnect FIRED ===")
                Logger.d("AndroidWebRtcDevice", "  TransportID: ${transport.id}")
                Logger.d("AndroidWebRtcDevice", "  Connection state BEFORE deliverConnectEvent: ${transport.connectionState}")
                deliverConnectEvent(transport, dtlsParameters)
                Logger.d("AndroidWebRtcDevice", "  Connection state AFTER deliverConnectEvent: ${transport.connectionState}")
                Logger.d("AndroidWebRtcDevice", "=== NATIVE SEND onConnect RETURNING ===")
            }

            override fun onProduce(
                transport: Transport,
                kind: String,
                rtpParameters: String,
                appData: String?
            ): String {
                return deliverProduceEvent(transport, kind, rtpParameters, appData)
            }

            override fun onProduceData(
                transport: Transport,
                sctpStreamParameters: String,
                label: String?,
                protocol: String?,
                appData: String?
            ): String {
                return ""
            }

            override fun onConnectionStateChange(transport: Transport, newState: String) {
                Logger.d("AndroidWebRtcDevice", "=== NATIVE SEND onConnectionStateChange ===")
                Logger.d("AndroidWebRtcDevice", "  TransportID: ${transport.id}")
                Logger.d("AndroidWebRtcDevice", "  New state: $newState")
                Logger.d("AndroidWebRtcDevice", "  Timestamp: ${System.currentTimeMillis()}")
                stateHandlers[transport.id]?.invoke(newState)
            }
        }

        val options = createPeerConnectionOptions()
        Logger.d("AndroidWebRtcDevice", "=== SEND TRANSPORT CREATION ===")
        Logger.d("AndroidWebRtcDevice", "  TransportID: $transportId")
        Logger.d("AndroidWebRtcDevice", "  ICE candidates JSON: $iceCandidates")
        Logger.d("AndroidWebRtcDevice", "  ICE parameters JSON: $iceParameters")
        Logger.d("AndroidWebRtcDevice", "=== CALLING NATIVE CREATE SEND TRANSPORT ===")
        
        val nativeTransport = device.createSendTransport(
            listener,
            transportId,
            iceParameters,
            iceCandidates,
            dtlsParameters,
            sctpParameters,
            options,
            appData
        )
        
        Logger.d("AndroidWebRtcDevice", "=== SEND TRANSPORT CREATED ===")
        Logger.d("AndroidWebRtcDevice", "  TransportID: $transportId")
        Logger.d("AndroidWebRtcDevice", "  Initial connection state: ${nativeTransport.connectionState}")
        Logger.d("AndroidWebRtcDevice", "  ICE candidates count: ${iceCandidates?.let { it.length } ?: 0}")
        Logger.d("AndroidWebRtcDevice", "=== END TRANSPORT CREATION ===")

        return MediasoupTransport(nativeTransport, TransportType.SEND)
    }

    override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
        val transportId = params["id"] as? String
            ?: throw MediasoupException("Recv transport id missing")
        val iceParameters = params["iceParameters"].toJsonString()
            ?: throw MediasoupException("Recv transport iceParameters missing")
        val iceCandidates = params["iceCandidates"].toJsonString()
            ?: throw MediasoupException("Recv transport iceCandidates missing")
        val dtlsParameters = params["dtlsParameters"].toJsonString()
            ?: throw MediasoupException("Recv transport dtlsParameters missing")
        val appData = params["appData"].toJsonString()

        val listener = object : RecvTransport.Listener {
            override fun onConnect(transport: Transport, dtlsParameters: String) {
                Logger.d("AndroidWebRtcDevice", "=== NATIVE RECV onConnect FIRED ===")
                Logger.d("AndroidWebRtcDevice", "  TransportID: ${transport.id}")
                Logger.d("AndroidWebRtcDevice", "  dtlsParameters length: ${dtlsParameters.length}")
                deliverConnectEvent(transport, dtlsParameters)
            }

            override fun onConnectionStateChange(transport: Transport, newState: String) {
                Logger.d("AndroidWebRtcDevice", "=== NATIVE RECV onConnectionStateChange: $newState ===")
                stateHandlers[transport.id]?.invoke(newState)
            }
        }

        Logger.d("AndroidWebRtcDevice", "=== RECV TRANSPORT CREATION ===")
        Logger.d("AndroidWebRtcDevice", "  TransportID: $transportId")
        Logger.d("AndroidWebRtcDevice", "  ICE candidates JSON: $iceCandidates")
        Logger.d("AndroidWebRtcDevice", "  ICE parameters JSON: $iceParameters")
        Logger.d("AndroidWebRtcDevice", "=== CALLING NATIVE CREATE RECV TRANSPORT ===")
        
        val options = createPeerConnectionOptions()
        val nativeTransport = device.createRecvTransport(
            listener,
            transportId,
            iceParameters,
            iceCandidates,
            dtlsParameters,
            null,
            options,
            appData
        )
        
        Logger.d("AndroidWebRtcDevice", "=== RECV TRANSPORT CREATED ===")
        Logger.d("AndroidWebRtcDevice", "  Initial connection state: ${nativeTransport.connectionState}")
        Logger.d("AndroidWebRtcDevice", "=== END RECV TRANSPORT CREATION ===")

        return MediasoupTransport(nativeTransport, TransportType.RECEIVE)
    }

    override fun currentRtpCapabilities(): RtpCapabilities? {
        if (!isLoaded) {
            return lastLoadedCapabilities
        }
        return fetchDeviceRtpCapabilitiesInternal() ?: lastLoadedCapabilities
    }

    override fun close() {
        transports.toList().forEach { transport ->
            runCatching { transport.close() }
        }
        activeProducers.toList().forEach { producer ->
            runCatching { producer.close() }
        }

        runCatching { device.dispose() }
        if (peerFactoryDelegate.isInitialized()) runCatching { peerFactory.dispose() }
        if (audioModuleDelegate.isInitialized()) runCatching { audioModule.release() }
        if (eglBaseDelegate.isInitialized()) {
            SharedEglContext.clear()
            runCatching { eglBase.release() }
        }
    if (audioFocusManagerDelegate.isInitialized()) runCatching { audioFocusManager.abandonFocus() }

        connectHandlers.clear()
        produceHandlers.clear()
        stateHandlers.clear()
        transports.clear()
        activeProducers.clear()
        activeAudioConsumers.set(0)
        isLoaded = false
    }

    /**
     * Sets the audio output device for playback.
     * 
     * On Android API 31+, uses AudioManager.setCommunicationDevice().
     * On older versions, uses isSpeakerphoneOn or Bluetooth SCO routing.
     */
    override suspend fun setAudioOutputDevice(deviceId: String): Boolean = withContext(Dispatchers.Main) {
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            ?: return@withContext false
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                // API 31+ - Use setCommunicationDevice
                val outputDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                
                // Extract the numeric ID from our deviceId format "audio_output_123"
                val numericId = deviceId.removePrefix("audio_output_").toIntOrNull()
                
                val targetDevice = if (numericId != null) {
                    outputDevices.find { it.id == numericId }
                } else {
                    // Fallback to matching by label patterns
                    when {
                        deviceId.contains("speaker", ignoreCase = true) -> 
                            outputDevices.find { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                        deviceId.contains("bluetooth", ignoreCase = true) ->
                            outputDevices.find { 
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                            }
                        deviceId.contains("headphone", ignoreCase = true) || deviceId.contains("wired", ignoreCase = true) ->
                            outputDevices.find { 
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET
                            }
                        else -> null
                    }
                }
                
                if (targetDevice != null) {
                    return@withContext audioManager.setCommunicationDevice(targetDevice)
                } else {
                    return@withContext false
                }
            } else {
                // Legacy API - Use speakerphone/Bluetooth routing
                when {
                    deviceId.contains("speaker", ignoreCase = true) || deviceId.contains("builtin", ignoreCase = true) -> {
                        audioManager.isSpeakerphoneOn = true
                        audioManager.stopBluetoothSco()
                        return@withContext true
                    }
                    deviceId.contains("bluetooth", ignoreCase = true) -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                        return@withContext true
                    }
                    deviceId.contains("headphone", ignoreCase = true) || deviceId.contains("wired", ignoreCase = true) -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.stopBluetoothSco()
                        return@withContext true
                    }
                    else -> {
                        audioManager.isSpeakerphoneOn = true
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext false
        }
    }

    /**
     * Creates a virtual video source for feeding processed frames (e.g., from ML Kit virtual backgrounds).
     * 
     * This creates a VideoSource that can receive Bitmap frames and outputs them as a WebRTC stream.
     * The frames can be sent to remote participants by replacing the video producer's track.
     */
    override fun createVirtualVideoSource(width: Int, height: Int, frameRate: Int): VirtualVideoSource {
        val streamId = "virtual-${System.currentTimeMillis()}"
        
        // Create video source - not screencast
        val videoSource = peerFactory.createVideoSource(false)
        
        // Create video track
        val videoTrack = peerFactory.createVideoTrack("virtual-video-$streamId", videoSource)
        videoTrack.setEnabled(true)
        
        // Create media stream
        val nativeStream = peerFactory.createLocalMediaStream(streamId)
        nativeStream.addTrack(videoTrack)
        
        // Wrap in our abstraction
        val mediaStream = AndroidMediaStream(
            nativeStream = nativeStream,
            audioTrack = null,
            audioSource = null,
            videoTrack = videoTrack,
            videoSource = videoSource,
            videoCapturer = null,
            surfaceTextureHelper = null
        )
        
        return AndroidVirtualVideoSource(
            nativeVideoSource = videoSource,
            nativeVideoTrack = videoTrack,
            stream = mediaStream,
            width = width,
            height = height
        )
    }

    /**
     * Captures the whiteboard canvas as a video stream for recording.
     * Similar to HTML Canvas's captureStream(30) API in web browsers.
     * 
     * This creates a WhiteboardVideoCapturer that renders whiteboard shapes to frames
     * and outputs them as a WebRTC stream for sending to the backend.
     */
    @Suppress("UNCHECKED_CAST")
    override suspend fun captureWhiteboardStream(
        shapesProvider: () -> List<Any>,
        useImageBackgroundProvider: () -> Boolean,
        width: Int,
        height: Int,
        frameRate: Int
    ): MediaStream {
        return withContext(Dispatchers.Default) {
            Log.d(TAG, "Creating whiteboard capture stream: ${width}x${height}@${frameRate}fps")
            
            // Cast the shapes provider to the proper type
            val typedShapesProvider: () -> List<com.mediasfu.sdk.model.WhiteboardShape> = {
                (shapesProvider() as? List<com.mediasfu.sdk.model.WhiteboardShape>) ?: emptyList()
            }
            
            // Create unique stream ID
            val streamId = "whiteboard-${UUID.randomUUID()}"
            val nativeStream = peerFactory.createLocalMediaStream(streamId)
            
            // Create whiteboard capturer
            val whiteboardCapturer = WhiteboardVideoCapturer.create(
                context = appContext,
                shapesProvider = typedShapesProvider,
                useImageBackgroundProvider = useImageBackgroundProvider
            )
            
            // Create surface texture helper
            val surfaceTextureHelper = SurfaceTextureHelper.create(
                "MediaSFU-WhiteboardCapture",
                eglBase.eglBaseContext
            ) ?: throw IllegalStateException("Failed to create SurfaceTextureHelper for whiteboard capture")
            
            // Create video source (isScreencast = true for better quality)
            val videoSource = peerFactory.createVideoSource(true)
            
            // Initialize the capturer
            whiteboardCapturer.initialize(surfaceTextureHelper, appContext, videoSource.capturerObserver)
            
            // Start whiteboard capture
            runCatching {
                whiteboardCapturer.startCapture(width, height, frameRate)
                Log.d(TAG, "Whiteboard capture started successfully")
            }.onFailure { error ->
                Log.e(TAG, "Failed to start whiteboard capture", error)
                whiteboardCapturer.dispose()
                surfaceTextureHelper.dispose()
                videoSource.dispose()
                throw IllegalStateException("Failed to start whiteboard capture: ${error.message}", error)
            }
            
            // Create video track
            val videoTrack = peerFactory.createVideoTrack("whiteboard-video-$streamId", videoSource).apply {
                setEnabled(true)
                nativeStream.addTrack(this)
            }
            
            Log.d(TAG, "Whiteboard capture stream created: $streamId")
            
            // Return the stream
            AndroidMediaStream(
                nativeStream = nativeStream,
                audioTrack = null,
                audioSource = null,
                videoTrack = videoTrack,
                videoSource = videoSource,
                videoCapturer = whiteboardCapturer,
                surfaceTextureHelper = surfaceTextureHelper
            )
        }
    }

    private fun createAudioDeviceModule(context: Context): AudioDeviceModule {
        val audioRecordErrorCallback = object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) { }
            
            override fun onWebRtcAudioRecordStartError(
                errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode,
                errorMessage: String
            ) { }
            
            override fun onWebRtcAudioRecordError(errorMessage: String) { }
        }
        
        val audioTrackErrorCallback = object : JavaAudioDeviceModule.AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) { }
            
            override fun onWebRtcAudioTrackStartError(
                errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode,
                errorMessage: String
            ) { }
            
            override fun onWebRtcAudioTrackError(errorMessage: String) { }
        }

        val audioTrackStateCallback = object : JavaAudioDeviceModule.AudioTrackStateCallback {
            override fun onWebRtcAudioTrackStart() { }
            override fun onWebRtcAudioTrackStop() { }
        }
        
        val module = JavaAudioDeviceModule.builder(context)
            .setAudioRecordErrorCallback(audioRecordErrorCallback)
            .setAudioTrackErrorCallback(audioTrackErrorCallback)
            .setAudioTrackStateCallback(audioTrackStateCallback)
            .createAudioDeviceModule()

        (module as? JavaAudioDeviceModule)?.let {
            it.setSpeakerMute(false)
            it.setMicrophoneMute(false)
        }

        return module
    }

    /**
     * Custom video decoder factory following Flutter WebRTC's approach.
     * 
     * Uses VideoDecoderFallback for automatic hardware→software fallback, similar to
     * flutter-webrtc's CustomVideoDecoderFactory. This provides:
     * 
     * 1. Hardware decoder as primary (ALL hardware decoders enabled including Qualcomm H264)
     * 2. Platform software decoder as fallback (c2.android.avc.decoder for H264)
     * 3. Pure software decoder as final fallback (libvpx for VP8/VP9)
     * 4. Automatic fallback via VideoDecoderFallback wrapper
     * 
     * The VideoDecoderFallback handles any hardware decoder failures automatically,
     * so we don't need to pre-filter Qualcomm decoders. The SharedEglContext singleton
     * ensures proper EGL context sharing which resolves previous UBWC/EGL issues.
     */
    private inner class SafeVideoDecoderFactory(
        private val eglContext: EglBase.Context?
    ) : VideoDecoderFactory {
        
        // Pure software decoders (libvpx for VP8/VP9)
        private val softwareFactory = SoftwareVideoDecoderFactory()
        
        // Platform software decoder (c2.android.avc.decoder for H264)
        private val platformSoftwareFactory = PlatformSoftwareVideoDecoderFactory(eglContext)
        
        // Hardware decoder factory - allow ALL hardware decoders including Qualcomm H264
        // VideoDecoderFallback will handle any failures automatically
        private val hardwareFactory = HardwareVideoDecoderFactory(eglContext)

        override fun createDecoder(codecType: VideoCodecInfo): VideoDecoder? {
            val codecName = codecType.name.lowercase()
            
            // Get available decoders
            val hardwareDecoder = hardwareFactory.createDecoder(codecType)
            var softwareDecoder = softwareFactory.createDecoder(codecType)
            
            // For H264, platform software is preferred over pure software (which doesn't support H264)
            if (softwareDecoder == null) {
                softwareDecoder = platformSoftwareFactory.createDecoder(codecType)
            }
            
            // Log decoder info for debugging
            if (hardwareDecoder != null) {
                Log.i(TAG, "[$codecName] Hardware decoder available")
            }
            if (softwareDecoder != null) {
                Log.i(TAG, "[$codecName] Software decoder available")
            }
            
            // Use VideoDecoderFallback for automatic hardware→software fallback (Flutter approach)
            return when {
                hardwareDecoder != null && softwareDecoder != null -> {
                    Log.i(TAG, "[$codecName] Using hardware decoder with software fallback (VideoDecoderFallback)")
                    VideoDecoderFallback(/* fallback */ softwareDecoder, /* primary */ hardwareDecoder)
                }
                hardwareDecoder != null -> {
                    Log.i(TAG, "[$codecName] Using hardware decoder only")
                    hardwareDecoder
                }
                softwareDecoder != null -> {
                    Log.i(TAG, "[$codecName] Using software decoder only")
                    softwareDecoder
                }
                else -> {
                    Log.w(TAG, "[$codecName] No decoder available")
                    null
                }
            }
        }

        override fun getSupportedCodecs(): Array<VideoCodecInfo> {
            val codecs = LinkedHashSet<VideoCodecInfo>()
            codecs.addAll(softwareFactory.supportedCodecs.toList())
            codecs.addAll(platformSoftwareFactory.supportedCodecs.toList())
            codecs.addAll(hardwareFactory.supportedCodecs.toList())
            return codecs.toTypedArray()
        }
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        // Use our custom safe decoder factory that avoids problematic Qualcomm H264 hardware decoders
        val decoderFactory = SafeVideoDecoderFactory(eglBase.eglBaseContext)

        return PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun createPeerConnectionOptions(): PeerConnection.Options {
        return PeerConnection.Options().apply { setFactory(peerFactory) }
    }

    private fun resolveAudioEnabled(value: Any?): Boolean = when (value) {
        null -> true
        is Boolean -> value
        is Map<*, *> -> true
        else -> true
    }

    private fun resolveVideoEnabled(value: Any?): Boolean = when (value) {
        null -> true
        is Boolean -> value
        is Map<*, *> -> true
        else -> false
    }

    private fun createAudioConstraints(value: Any?): MediaConstraints {
        val constraints = MediaConstraints()
        if (value is Map<*, *>) {
            sequenceOf("echoCancellation", "noiseSuppression", "autoGainControl").forEach { key ->
                value[key]?.let { constraints.mandatory.add(MediaConstraints.KeyValuePair(key, it.toConstraintValue())) }
            }
        }
        return constraints
    }

    private fun resolveVideoSettings(value: Any?): VideoCaptureConfig {
        if (value !is Map<*, *>) return VideoCaptureConfig()
        // Check for constraints in "mandatory" wrapper (ClickVideo format) or direct format
        val constraintMap = (value["mandatory"] as? Map<*, *>) ?: value
        val width = extractIntConstraint(constraintMap["width"], 640)
        val height = extractIntConstraint(constraintMap["height"], 360)
        val frameRate = extractIntConstraint(constraintMap["frameRate"], 10)
        val facingMode = extractStringConstraint(constraintMap["facingMode"])
        val deviceId = extractStringConstraint(constraintMap["deviceId"]) 
            ?: extractStringConstraint(constraintMap["sourceId"])  // Also check sourceId
        Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcDevice: resolveVideoSettings facingMode=$facingMode deviceId=$deviceId")
        return VideoCaptureConfig(width, height, frameRate, facingMode, deviceId)
    }

    private fun createVideoCapturer(config: VideoCaptureConfig): VideoCapturer? {
        val enumerator: CameraEnumerator = if (Camera2Enumerator.isSupported(appContext)) {
            Camera2Enumerator(appContext)
        } else {
            Camera1Enumerator(true)
        }

        val preferred = mutableListOf<String>()
        config.deviceId?.let { preferred += it }
        val facing = config.facingMode?.lowercase()
        if (facing != null) {
            val matches = enumerator.deviceNames.filter { name ->
                when (facing) {
                    "user", "front" -> enumerator.isFrontFacing(name)
                    "environment", "back" -> enumerator.isBackFacing(name)
                    else -> false
                }
            }
            preferred.addAll(matches)
        }
        preferred.addAll(enumerator.deviceNames)

        preferred.distinct().forEach { name ->
            val capturer = enumerator.createCapturer(name, null)
            if (capturer != null) {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcDevice: using camera '$name'")
                return capturer
            }
        }

        return null
    }

    private fun Any?.toConstraintValue(): String = when (this) {
        is Boolean -> if (this) "true" else "false"
        is Number -> toString()
        is String -> this
        else -> toString()
    }

    private fun extractIntConstraint(value: Any?, defaultValue: Int): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt() ?: defaultValue
        is Map<*, *> -> {
            val ideal = value["ideal"]
            val exact = value["exact"]
            val max = value["max"]
            val min = value["min"]
            when {
                ideal != null -> extractIntConstraint(ideal, defaultValue)
                exact != null -> extractIntConstraint(exact, defaultValue)
                max != null -> extractIntConstraint(max, defaultValue)
                min != null -> extractIntConstraint(min, defaultValue)
                else -> defaultValue
            }
        }
        else -> defaultValue
    }

    private fun extractStringConstraint(value: Any?): String? = when (value) {
        null -> null
        is String -> value
        is Map<*, *> -> {
            when (val ideal = value["ideal"]) {
                is String -> ideal
                else -> when (val exact = value["exact"]) {
                    is String -> exact
                    else -> null
                }
            }
        }
        else -> value.toString()
    }

    private fun parseDtlsParameters(json: String): DtlsParameters {
        val jsonObject = JSONObject(json)
        val roleValue = jsonObject.optString("role", "auto").uppercase()
        val role = runCatching { DtlsRole.valueOf(roleValue) }.getOrDefault(DtlsRole.AUTO)
        val fingerprints = jsonObject.optJSONArray("fingerprints")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val algorithm = item.optString("algorithm")
                    val value = item.optString("value")
                    if (algorithm.isNotBlank() && value.isNotBlank()) {
                        add(DtlsFingerprint(algorithm, value))
                    }
                }
            }
        } ?: emptyList()
        return DtlsParameters(role = role, fingerprints = fingerprints)
    }

    private fun parseRtpParameters(json: String): RtpParameters {
        val jsonObject = JSONObject(json)

        // CRITICAL FIX: Parse 'mid' from native library - required for BUNDLE to work
        // Without mid, RTP packets cannot be mapped to producers on the server
        val mid = jsonObject.optString("mid", null).takeIf { 
            jsonObject.has("mid") && !jsonObject.isNull("mid") 
        }
        
        // Parse RTCP parameters including cname (required for RTP stream identification)
        val rtcp = jsonObject.optJSONObject("rtcp")?.let { rtcpObj ->
            RtcpParameters(
                cname = rtcpObj.optString("cname", null).takeIf { rtcpObj.has("cname") },
                reducedSize = rtcpObj.optBoolean("reducedSize").takeIf { rtcpObj.has("reducedSize") },
                mux = rtcpObj.optBoolean("mux").takeIf { rtcpObj.has("mux") }
            )
        }

        val codecs = jsonObject.optJSONArray("codecs")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val codecObject = array.optJSONObject(index) ?: continue
                    val parametersObject = codecObject.optJSONObject("parameters") ?: JSONObject()
                    val parameters = parametersObject.keySet().associateWith { key -> parametersObject.optString(key) }
                    add(
                        RtpCodecParameters(
                            mimeType = codecObject.optString("mimeType"),
                            payloadType = codecObject.optInt("payloadType"),
                            clockRate = codecObject.optInt("clockRate"),
                            channels = codecObject.opt("channels").let { if (it is Number) it.toInt() else null },
                            parameters = parameters
                        )
                    )
                }
            }
        } ?: emptyList()

        val headerExtensions = jsonObject.optJSONArray("headerExtensions")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val header = array.optJSONObject(index) ?: continue
                    add(
                        RtpHeaderExtensionParameters(
                            uri = header.optString("uri"),
                            id = header.optInt("id"),
                            encrypt = header.optBoolean("encrypt", false)
                        )
                    )
                }
            }
        } ?: emptyList()

        val encodings = jsonObject.optJSONArray("encodings")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val encoding = array.optJSONObject(index) ?: continue
                    add(
                        RtpEncodingParameters(
                            // CRITICAL FIX: Only include ssrc if it's a valid number (not null)
                            // When using rid for simulcast, ssrc should be omitted (not sent as null)
                            // The server assigns SSRCs when using rid-based simulcast
                            ssrc = encoding.opt("ssrc").let { 
                                if (it is Number && it.toLong() > 0) it.toLong() else null 
                            },
                            rid = encoding.optString("rid", null).takeIf { 
                                encoding.has("rid") && !encoding.isNull("rid") 
                            },
                            // CRITICAL: Parse 'active' from native lib, default to true if not present
                            active = if (encoding.has("active")) encoding.optBoolean("active", true) else true,
                            maxBitrate = encoding.optInt("maxBitrate").takeIf { encoding.has("maxBitrate") && !encoding.isNull("maxBitrate") },
                            minBitrate = encoding.optInt("minBitrate").takeIf { encoding.has("minBitrate") && !encoding.isNull("minBitrate") },
                            maxFramerate = encoding.optDouble("maxFramerate").takeIf { encoding.has("maxFramerate") && !encoding.isNull("maxFramerate") },
                            scalabilityMode = encoding.optString("scalabilityMode", null).takeIf { 
                                encoding.has("scalabilityMode") && !encoding.isNull("scalabilityMode") 
                            },
                            scaleResolutionDownBy = encoding.optDouble("scaleResolutionDownBy").takeIf { 
                                encoding.has("scaleResolutionDownBy") && !encoding.isNull("scaleResolutionDownBy") 
                            },
                            dtx = encoding.optBoolean("dtx").takeIf { encoding.has("dtx") && !encoding.isNull("dtx") }
                            // NOTE: networkPriority deliberately not parsed - not needed
                        )
                    )
                }
            }
        } ?: emptyList()

        return RtpParameters(
            mid = mid,
            codecs = codecs,
            headerExtensions = headerExtensions,
            encodings = encodings,
            rtcp = rtcp
        )
    }

    private fun Any?.toJsonString(): String? = when (this) {
        null -> null
        is String -> this
        is JSONObject, is JSONArray -> toString()
        is Map<*, *> -> mapToJson(this).toString()
        is List<*> -> listToJsonArray(this).toString()
        is Number, is Boolean -> toString()
        else -> JSONObject.wrap(this)?.toString()
    }

    private fun mapToJson(map: Map<*, *>): JSONObject {
        val result = JSONObject()
        for ((key, value) in map) {
            if (key is String) {
                result.put(key, wrapJsonValue(value))
            }
        }
        return result
    }

    private fun listToJsonArray(list: List<*>): JSONArray {
        val array = JSONArray()
        list.forEach { item -> array.put(wrapJsonValue(item)) }
        return array
    }

    private fun fetchDeviceRtpCapabilitiesInternal(): RtpCapabilities? {
        if (!isLoaded) return null
        return runCatching {
            val json = device.rtpCapabilities
            if (json.isNullOrBlank()) {
                null
            } else {
                parseDeviceRtpCapabilities(json)
            }
        }.getOrNull()?.also { parsed ->
            lastLoadedCapabilities = parsed
        }
    }

    private fun parseDeviceRtpCapabilities(json: String): RtpCapabilities {
        val jsonObject = JSONObject(json)
        val codecsArray = jsonObject.optJSONArray("codecs") ?: JSONArray()
        val codecs = mutableListOf<RtpCodecCapability>()
        for (index in 0 until codecsArray.length()) {
            val codecJson = codecsArray.optJSONObject(index) ?: continue
            val kind = codecJson.optString("kind", null)?.toMediaKindOrNull() ?: continue
            val mimeType = codecJson.optString("mimeType", null) ?: continue
            val clockRate = codecJson.optInt("clockRate")
            val preferredPayloadType = codecJson.optInt("preferredPayloadType")
                .takeIf { codecJson.has("preferredPayloadType") }
            val channels = codecJson.optInt("channels").takeIf { codecJson.has("channels") }
            val parameters = codecJson.optJSONObject("parameters")?.toStringMap() ?: emptyMap()
            val rtcpFeedback = codecJson.optJSONArray("rtcpFeedback")?.toRtcpFeedbackList() ?: emptyList()
            codecs += RtpCodecCapability(
                kind = kind,
                mimeType = mimeType,
                preferredPayloadType = preferredPayloadType,
                clockRate = clockRate,
                channels = channels,
                parameters = parameters,
                rtcpFeedback = rtcpFeedback
            )
        }

        val extensionsArray = jsonObject.optJSONArray("headerExtensions") ?: JSONArray()
        val extensions = mutableListOf<RtpHeaderExtension>()
        for (index in 0 until extensionsArray.length()) {
            val extJson = extensionsArray.optJSONObject(index) ?: continue
            val uri = extJson.optString("uri", null) ?: continue
            val preferredId = extJson.optInt("preferredId")
            val preferredEncrypt = extJson.optBoolean("preferredEncrypt")
            val kind = extJson.optString("kind", null)?.toMediaKindOrNull()
            val direction = extJson.optString("direction", null)?.toHeaderDirectionOrNull()
            extensions += RtpHeaderExtension(
                kind = kind,
                uri = uri,
                preferredId = preferredId,
                preferredEncrypt = preferredEncrypt,
                direction = direction
            )
        }

        val fecMechanisms = jsonObject.optJSONArray("fecMechanisms")?.toStringList() ?: emptyList()

        return RtpCapabilities(
            codecs = codecs,
            headerExtensions = extensions,
            fecMechanisms = fecMechanisms
        )
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val keysIterator = keys()
        while (keysIterator.hasNext()) {
            val key = keysIterator.next()
            val value = opt(key)
            if (value != null && value != JSONObject.NULL) {
                result[key] = value.toString()
            }
        }
        return result
    }

    private fun JSONArray.toRtcpFeedbackList(): List<RtcpFeedback> {
        val feedback = mutableListOf<RtcpFeedback>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val type = item.optString("type", null) ?: continue
            val parameter = item.optString("parameter", null)
            feedback += RtcpFeedback(type = type, parameter = parameter?.ifBlank { null })
        }
        return feedback
    }

    private fun JSONArray.toStringList(): List<String> {
        val values = mutableListOf<String>()
        for (i in 0 until length()) {
            val value = optString(i, null)
            if (!value.isNullOrEmpty()) {
                values += value
            }
        }
        return values
    }

    private fun String?.toHeaderDirectionOrNull(): RtpHeaderDirection? = when (this?.lowercase(Locale.US)) {
        "sendrecv" -> RtpHeaderDirection.SENDRECV
        "sendonly" -> RtpHeaderDirection.SENDONLY
        "recvonly" -> RtpHeaderDirection.RECVONLY
        "inactive" -> RtpHeaderDirection.INACTIVE
        else -> null
    }

    private fun wrapJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is JSONObject, is JSONArray -> value
        is Map<*, *> -> mapToJson(value)
        is List<*> -> listToJsonArray(value)
        is Number -> value
        is Boolean -> value
        is String -> value
        else -> value.toString()
    }

    private fun jsonToMap(jsonObject: JSONObject): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = jsonObject.get(key)
            result[key] = unwrapJsonValue(value)
        }
        return result
    }

    private fun unwrapJsonValue(value: Any?): Any? = when (value) {
        JSONObject.NULL -> null
        is JSONObject -> jsonToMap(value)
        is JSONArray -> (0 until value.length()).map { index -> unwrapJsonValue(value.get(index)) }
        is String -> {
            // Try to parse string numbers (e.g., "96" -> 96) for codec parameters like apt
            value.toIntOrNull() ?: value.toLongOrNull() ?: value.toDoubleOrNull() ?: value
        }
        else -> value
    }

    private fun RtpCapabilities.toJsonString(): String {
        val codecsArray = JSONArray()
        codecs.forEach { codec -> codecsArray.put(codec.toJsonObject()) }
        val extensionsArray = JSONArray()
        headerExtensions.forEach { ext -> extensionsArray.put(ext.toJsonObject()) }
        val fecArray = JSONArray()
        fecMechanisms.forEach { fecArray.put(it) }
        return JSONObject(
            mapOf(
                "codecs" to codecsArray,
                "headerExtensions" to extensionsArray,
                "fecMechanisms" to fecArray
            )
        ).toString()
    }

    private fun RtpCodecCapability.toJsonObject(): JSONObject {
        val json = JSONObject()
        json.put("kind", kind.name.lowercase())
        json.put("mimeType", mimeType)
        preferredPayloadType?.let { json.put("preferredPayloadType", it) }
        json.put("clockRate", clockRate)
        channels?.let { json.put("channels", it) }
        val normalizedParams = JSONObject()
        parameters.forEach { (key, value) ->
            normalizedParams.put(key, mapScalarJsonValue(value))
        }
        json.put("parameters", normalizedParams)
        if (rtcpFeedback.isNotEmpty()) {
            val feedbackArray = JSONArray()
            rtcpFeedback.forEach { fb ->
                feedbackArray.put(
                    JSONObject().apply {
                        put("type", fb.type)
                        fb.parameter?.let { put("parameter", it) }
                    }
                )
            }
            json.put("rtcpFeedback", feedbackArray)
        }
        return json
    }

    private fun mapScalarJsonValue(value: Any?): Any? = when (value) {
        null -> JSONObject.NULL
        is Number, is Boolean -> value
        is String -> {
            val trimmed = value.trim()
            trimmed.toBooleanStrictOrNull()
                ?: trimmed.toLongOrNull()
                    ?.let { longValue ->
                        if (longValue in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) longValue.toInt() else longValue
                    }
                ?: trimmed.toDoubleOrNull()
                    ?.let { doubleValue ->
                        val intCandidate = doubleValue.toInt()
                        if (doubleValue == intCandidate.toDouble()) intCandidate else doubleValue
                    }
                ?: trimmed
        }
        else -> value.toString()
    }

    private fun RtpHeaderExtension.toJsonObject(): JSONObject {
        val json = JSONObject()
        kind?.let { json.put("kind", it.name.lowercase()) }
        json.put("uri", uri)
        json.put("preferredId", preferredId)
        json.put("preferredEncrypt", preferredEncrypt)
        direction?.let { json.put("direction", it.name.lowercase(Locale.US)) }
        return json
    }

    private fun RtpEncodingParameters.toNativeEncoding(): NativeRtpParameters.Encoding {
        val scale = scaleResolutionDownBy ?: 1.0
        return NativeRtpParameters.Encoding(rid ?: "", true, scale).also { nativeEncoding ->
            maxBitrate?.let { nativeEncoding.maxBitrateBps = it }
            minBitrate?.let { nativeEncoding.minBitrateBps = it }
            maxFramerate?.let { framerate ->
                nativeEncoding.maxFramerate = framerate.toInt()
            }
            if (scaleResolutionDownBy != null) {
                nativeEncoding.scaleResolutionDownBy = scaleResolutionDownBy
            }
        }
    }

    private fun ProducerCodecOptions?.toJsonString(): String? {
        if (this == null) return null
        // Convert number to string for mediasoup server compatibility
        return JSONObject(mapOf("videoGoogleStartBitrate" to wrapJsonValue(videoGoogleStartBitrate))).toString()
    }

    private fun mapConnectionState(state: String?): TransportConnectionState = when (state?.lowercase()) {
        "connecting" -> TransportConnectionState.CONNECTING
        "connected" -> TransportConnectionState.CONNECTED
        "disconnected" -> TransportConnectionState.DISCONNECTED
        "failed" -> TransportConnectionState.FAILED
        "closed" -> TransportConnectionState.CLOSED
        else -> TransportConnectionState.NEW
    }

    private fun String.toMediaKind(): MediaKind = when (lowercase()) {
        "audio" -> MediaKind.AUDIO
        "video" -> MediaKind.VIDEO
        else -> MediaKind.AUDIO
    }

    private fun String.toMediaKindOrNull(): MediaKind? = when (lowercase()) {
        "audio" -> MediaKind.AUDIO
        "video" -> MediaKind.VIDEO
        else -> null
    }

    private fun VideoCapturer?.disposeSafely() {
        if (this == null) return
        runCatching { stopCapture() }
        dispose()
    }

    private fun registerProducer(producer: AndroidWebRtcProducer) {
        activeProducers += producer
    }

    private fun unregisterProducer(producer: AndroidWebRtcProducer) {
        activeProducers -= producer
    }

    /**
     * Delivers the connect event to the registered handler.
     * 
     * CRITICAL: Based on mediasoup-demo-android reference implementation, the onConnect
     * callback should be FIRE-AND-FORGET. The native mediasoup-client-android library
     * handles the ICE/DTLS handshake internally AFTER this callback returns.
     * 
     * Previously we were blocking with CompletableFuture.get() which prevented the
     * native library from continuing its ICE connectivity checks and DTLS handshake,
     * causing the transport to get stuck in "checking" state forever.
     * 
     * The handler just needs to send the DTLS parameters to the server - the native
     * library will complete the handshake and transition to "connected" state automatically.
     * 
     * @see https://github.com/haiyangwu/mediasoup-demo-android/blob/57951b7d3c14a581e5096c9cb001e186c7ece0ac/app/src/main/java/org/mediasoup/droid/lib/RoomClient.java#L1163
     */
    private fun deliverConnectEvent(transport: Transport, dtlsParameters: String) {
        Logger.d("AndroidWebRtcDevice", "=== deliverConnectEvent (BLOCKING) ===")
        Logger.d("AndroidWebRtcDevice", "  TransportID: ${transport.id}")
        val handler = connectHandlers[transport.id]
        if (handler == null) {
            Logger.d("AndroidWebRtcDevice", "  NO HANDLER - storing as pending connect event")
            pendingConnectEvents[transport.id] = PendingConnectEvent(transport, dtlsParameters)
            return
        }

        Logger.d("AndroidWebRtcDevice", "  Handler FOUND - invoking and BLOCKING until callback completes")
        val parsed = parseDtlsParameters(dtlsParameters)
        
        // CRITICAL: We MUST block here until the handler completes!
        // The native mediasoup-client-android expects onConnect to be synchronous.
        // The handler will emit transport-connect and call callback() when server acks.
        // We use a CountDownLatch to block until callback() is invoked.
        val latch = java.util.concurrent.CountDownLatch(1)
        var callbackError: Throwable? = null
        
        handler(
            ConnectData(
                dtlsParameters = parsed,
                callback = {
                    Logger.d("AndroidWebRtcDevice", "=== deliverConnectEvent: callback() invoked (ack received), releasing latch ===")
                    latch.countDown()
                },
                errback = { error ->
                    Logger.d("AndroidWebRtcDevice", "=== deliverConnectEvent: errback() invoked: ${error.message} ===")
                    callbackError = error
                    latch.countDown()
                }
            )
        )
        
        Logger.d("AndroidWebRtcDevice", "  Waiting for callback (max 10 seconds)...")
        val completed = latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        if (!completed) {
            Logger.d("AndroidWebRtcDevice", "  TIMEOUT waiting for transport-connect callback!")
        } else if (callbackError != null) {
            Logger.d("AndroidWebRtcDevice", "  Connect failed: ${callbackError?.message}")
        } else {
            Logger.d("AndroidWebRtcDevice", "  deliverConnectEvent completed successfully, transport should now connect")
        }
    }

    private fun deliverProduceEvent(
        transport: Transport,
        kind: String,
        rtpParameters: String,
        appData: String?
    ): String {
        // === CODEC DEBUG: Log producer RTP parameters ===
        if (kind == "video") {
            try {
                val json = JSONObject(rtpParameters)
                val codecs = json.optJSONArray("codecs")
                Logger.d("AndroidWebRtcDevice", "\n=== KOTLIN PRODUCE (video) - RAW FROM NATIVE ===")
                Logger.d("AndroidWebRtcDevice", "  TransportID: ${transport.id}")
                Logger.d("AndroidWebRtcDevice", "  MID (from native): ${json.opt("mid")}")  // Check if mid is present
                Logger.d("AndroidWebRtcDevice", "  RTCP (from native): ${json.opt("rtcp")}")
                if (codecs != null) {
                    for (i in 0 until codecs.length()) {
                        val codec = codecs.optJSONObject(i)
                        val mimeType = codec?.optString("mimeType") ?: "unknown"
                        val payloadType = codec?.optInt("payloadType", -1)
                        val clockRate = codec?.optInt("clockRate", -1)
                        Logger.d("AndroidWebRtcDevice", "  Codec[$i]: $mimeType PT=$payloadType clockRate=$clockRate")
                        val params = codec?.optJSONObject("parameters")
                        if (params != null) {
                            Logger.d("AndroidWebRtcDevice", "    Parameters: $params")
                        }
                    }
                }
                val encodings = json.optJSONArray("encodings")
                if (encodings != null) {
                    for (i in 0 until encodings.length()) {
                        val enc = encodings.optJSONObject(i)
                        val ssrc = enc?.opt("ssrc")  // Use opt() to see actual value (null vs number)
                        val rid = enc?.optString("rid")
                        Logger.d("AndroidWebRtcDevice", "  Encoding[$i]: ssrc=$ssrc (type=${ssrc?.javaClass?.simpleName}) rid=$rid")
                    }
                }
                Logger.d("AndroidWebRtcDevice", "=== END RAW KOTLIN PRODUCE ===\n")
            } catch (e: Exception) {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - PRODUCE DEBUG ERROR: ${e.message}")
            }
        }

        val normalizedRtpParameters = applyEncodingOverridesIfNeeded(transport.id, kind, rtpParameters)
        val handler = produceHandlers[transport.id]
        if (handler == null) {
            val pendingEvent = PendingProduceEvent(
                transport = transport,
                kind = kind,
                rtpParameters = normalizedRtpParameters,
                appData = appData,
                completion = CompletableFuture()
            )
            pendingProduceEvents.compute(transport.id) { _, list ->
                (list ?: mutableListOf()).apply { add(pendingEvent) }
            }

            return try {
                pendingEvent.completion.get(produceEventTimeoutMs, TimeUnit.MILLISECONDS)
            } catch (timeout: TimeoutException) {
                pendingProduceEvents.compute(transport.id) { _, list ->
                    list?.apply { remove(pendingEvent) }?.takeIf { it.isNotEmpty() }
                }
                runCatching { pendingEvent.completion.completeExceptionally(timeout) }
                transport.close()
                throw timeout
            } catch (error: Exception) {
                pendingProduceEvents.compute(transport.id) { _, list ->
                    list?.apply { remove(pendingEvent) }?.takeIf { it.isNotEmpty() }
                }
                val cause = error.cause ?: error
                runCatching { pendingEvent.completion.completeExceptionally(cause) }
                throw cause
            }
        }

        return invokeProduceHandler(handler, transport, kind, normalizedRtpParameters, appData)
    }

    private fun invokeProduceHandler(
        handler: (ProduceData) -> Unit,
        transport: Transport,
        kind: String,
        rtpParameters: String,
        appData: String?
    ): String {
        val parsedParameters = parseRtpParameters(rtpParameters)
        val parsedAppData = appData?.let { jsonToMap(JSONObject(it)) }
        
        // CRITICAL FIX: Use CompletableFuture to block until the async socket.emitWithAck completes
        // The handler calls socket.emitWithAck which is async, but native produce() expects sync return
        // Without blocking, we return "" before the callback sets the producer ID
        val completion = CompletableFuture<String>()
        
        Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: invoking handler for ${kind}, waiting for callback...")

        handler(
            ProduceData(
                kind = kind.toMediaKind(),
                rtpParameters = parsedParameters,
                appData = parsedAppData,
                callback = { producerId ->
                    Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: callback received producerId=${producerId}")
                    completion.complete(producerId ?: "")
                },
                errback = { error ->
                    Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: errback received error=${error.message}")
                    completion.completeExceptionally(error)
                    transport.close()
                }
            )
        )

        return try {
            // Wait up to 10 seconds for the server to respond with producer ID
            val producerId = completion.get(produceEventTimeoutMs, TimeUnit.MILLISECONDS)
            Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: returning producerId=${producerId}")
            producerId
        } catch (timeout: TimeoutException) {
            Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: TIMEOUT waiting for producer ID")
            transport.close()
            throw timeout
        } catch (error: Exception) {
            Logger.d("AndroidWebRtcDevice", "MediaSFU - invokeProduceHandler: ERROR ${error.message}")
            val cause = error.cause ?: error
            throw cause
        }
    }

    private data class PendingConnectEvent(
        val transport: Transport,
        val dtlsParameters: String
    )

    private data class PendingProduceEvent(
        val transport: Transport,
        val kind: String,
        val rtpParameters: String,
        val appData: String?,
        val completion: CompletableFuture<String>
    )

    private inner class MediasoupTransport(
        private val nativeTransport: Transport,
        override val type: TransportType
    ) : WebRtcTransport {

        init { transports += this }

        override val id: String
            get() = nativeTransport.id

        override val connectionState: TransportConnectionState
            get() = mapConnectionState(nativeTransport.connectionState)

        override fun close() {
            nativeTransport.close()
            connectHandlers.remove(id)
            produceHandlers.remove(id)
            stateHandlers.remove(id)
            pendingConnectEvents.remove(id)
            pendingProduceEvents.remove(id)?.forEach { event ->
                event.completion.completeExceptionally(IllegalStateException("Transport closed"))
            }
            transports -= this
        }

        override fun onConnect(handler: (ConnectData) -> Unit) {
            Logger.d("AndroidWebRtcDevice", "=== onConnect HANDLER REGISTERED ===")
            Logger.d("AndroidWebRtcDevice", "  TransportID: $id")
            connectHandlers[id] = handler
            val pending = pendingConnectEvents.remove(id)
            if (pending != null) {
                Logger.d("AndroidWebRtcDevice", "  Found PENDING connect event - delivering now")
                deliverConnectEvent(pending.transport, pending.dtlsParameters)
            } else {
                Logger.d("AndroidWebRtcDevice", "  No pending connect event - will wait for native callback")
            }
        }

        override fun onProduce(handler: (ProduceData) -> Unit) {
            if (type == TransportType.SEND) {
                produceHandlers[id] = handler
                pendingProduceEvents.remove(id)?.forEach { event ->
                    runCatching {
                        val producedId = invokeProduceHandler(
                            handler = handler,
                            transport = event.transport,
                            kind = event.kind,
                            rtpParameters = event.rtpParameters,
                            appData = event.appData
                        )
                        event.completion.complete(producedId)
                    }.onFailure { error ->
                        event.completion.completeExceptionally(error)
                    }
                }
            }
        }

        override fun onConnectionStateChange(handler: (String) -> Unit) {
            stateHandlers[id] = handler
        }

        override fun produce(
            track: MediaStreamTrack,
            encodings: List<RtpEncodingParameters>,
            codecOptions: ProducerCodecOptions?,
            appData: Map<String, Any?>?
        ): WebRtcProducer {
            val sendTransport = nativeTransport as? SendTransport
                ?: throw IllegalStateException("MediaSFU - AndroidWebRtcDevice: produce called on non-send transport")

            val nativeTrack = (track as? AndroidMediaStreamTrack)?.nativeTrack
                ?: throw IllegalArgumentException("MediaSFU - AndroidWebRtcDevice: unsupported track implementation ${track::class.simpleName}")

            val nativeKind = nativeTrack.kind()
            
            // ENCODING STRATEGY:
            // For VIDEO with simulcast: Pass the encodings with rid values (r0, r1, r2)
            // For VIDEO without simulcast: Pass a single encoding without rid
            // For AUDIO: Pass null to let native library handle defaults
            // 
            // The key insight from the Flutter SDK (unified_plan.dart lines 512-515):
            // - Assigns rid = "r{idx}" for each encoding when there are multiple
            // - Passes encodings to sendEncodings in RTCRtpTransceiverInit
            //
            // For mediasoup-client-android, we need to do the same - pass properly configured
            // encodings with rid values for simulcast to work correctly.
            val encodingList: List<NativeRtpParameters.Encoding>? = if (nativeKind == NativeMediaStreamTrack.VIDEO_TRACK_KIND && encodings.isNotEmpty()) {
                // Ensure rid values are properly assigned (r0, r1, r2 for simulcast)
                encodings.mapIndexed { index, encoding ->
                    val rid = encoding.rid ?: "r$index"
                    NativeRtpParameters.Encoding(rid, true, encoding.scaleResolutionDownBy ?: 1.0).also { nativeEncoding ->
                        encoding.maxBitrate?.let { nativeEncoding.maxBitrateBps = it }
                        encoding.minBitrate?.let { nativeEncoding.minBitrateBps = it }
                        encoding.maxFramerate?.let { framerate ->
                            nativeEncoding.maxFramerate = framerate.toInt()
                        }
                        if (encoding.scaleResolutionDownBy != null) {
                            nativeEncoding.scaleResolutionDownBy = encoding.scaleResolutionDownBy
                        }
                    }
                }.also { nativeEncodings ->
                    Logger.d("AndroidWebRtcDevice", "MediaSFU - produce: using ${nativeEncodings.size} simulcast encodings with rids: ${nativeEncodings.map { it.rid }}")
                }
            } else {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - produce: using NULL encodings for kind=$nativeKind")
                null // Let native library handle defaults for audio
            }
            
            // Don't pass codecOptions - native library validates numbers but server needs strings
            // Codec options are applied via peer connection settings, not produce params
            val codecOptionsJson = "{}"
            
            val appDataJson = appData.toJsonString() ?: "{}"
            val canProduce = runCatching { device.canProduce(nativeKind) }.getOrDefault(false)

            if (!isLoaded || !canProduce) {
                // Device not ready or cannot produce this kind
            }

            val producer = try {
                sendTransport.produce(
                    object : Producer.Listener {
                        override fun onTransportClose(producer: Producer) {
                            activeProducers.firstOrNull { it.id == producer.id }?.let { runCatching { it.close() } }
                        }
                    },
                    nativeTrack,
                    encodingList,
                    null, // codecOptions - must be null for native library
                    null, // codec - must be null (matches mediasoup-client example)
                    appDataJson  // appData - properly stringified with all values as strings
                )
            } catch (error: Throwable) {
                throw error
            }

            val source = when (nativeTrack.kind()) {
                NativeMediaStreamTrack.VIDEO_TRACK_KIND -> ProducerSource.CAMERA
                else -> ProducerSource.MICROPHONE
            }
            
            // PRODUCER VIDEO FRAME PROBE: Check if video frames are actually being captured
            if (nativeKind == NativeMediaStreamTrack.VIDEO_TRACK_KIND) {
                val videoTrack = nativeTrack as? VideoTrack
                if (videoTrack != null) {
                    Logger.d("AndroidWebRtcDevice", "=== ATTACHING PRODUCER VIDEO PROBE ===")
                    Logger.d("AndroidWebRtcDevice", "  ProducerId: ${producer.id}")
                    Logger.d("AndroidWebRtcDevice", "  Track enabled: ${videoTrack.enabled()}")
                    Logger.d("AndroidWebRtcDevice", "  Track state: ${videoTrack.state()}")
                    
                    val startTime = System.currentTimeMillis()
                    val frameCount = java.util.concurrent.atomic.AtomicInteger(0)
                    val sink = object : VideoSink {
                        override fun onFrame(frame: VideoFrame) {
                            val count = frameCount.incrementAndGet()
                            if (count <= 5 || count % 60 == 0) {
                                val elapsed = System.currentTimeMillis() - startTime
                                Logger.d("AndroidWebRtcDevice", "=== PRODUCER VIDEO FRAME #$count (${elapsed}ms) ===")
                                Logger.d("AndroidWebRtcDevice", "  width: ${frame.buffer.width}, height: ${frame.buffer.height}")
                                Logger.d("AndroidWebRtcDevice", "  rotation: ${frame.rotation}")
                            }
                        }
                    }
                    videoTrack.addSink(sink)
                    Logger.d("AndroidWebRtcDevice", "=== PRODUCER VIDEO PROBE ATTACHED ===")
                } else {
                    Logger.d("AndroidWebRtcDevice", "=== WARNING: Could not cast to VideoTrack for probe ===")
                }
            }

            // Log transport state after produce completes
            Logger.d("AndroidWebRtcDevice", "=== AFTER PRODUCE ===")
            Logger.d("AndroidWebRtcDevice", "  ProducerId: ${producer.id}")
            Logger.d("AndroidWebRtcDevice", "  Producer paused: ${producer.isPaused}")
            Logger.d("AndroidWebRtcDevice", "  Transport connectionState: ${sendTransport.connectionState}")
            Logger.d("AndroidWebRtcDevice", "=== END AFTER PRODUCE ===")
            
            return AndroidWebRtcProducer(producer, source).also { registerProducer(it) }
        }
        
        override fun consume(
            id: String,
            producerId: String,
            kind: String,
            rtpParameters: Map<String, Any?>
        ): WebRtcConsumer {
            val recvTransport = nativeTransport as? RecvTransport
                ?: throw IllegalStateException("consume called on non-recv transport")
            
            // Convert kind to native format
            val nativeKind = if (kind == "video") "video" else "audio"
            
            // Convert rtpParameters to JSON string
            val rtpParametersJson = rtpParameters.toJsonString()
                ?: throw MediasoupException("Failed to serialize RTP parameters")
            
            // === H264 CODEC DEBUG ===
            Log.d(TAG, "=== CONSUME() CALLED ===")
            Log.d(TAG, "  ConsumerId: $id")
            Log.d(TAG, "  ProducerId: $producerId")
            Log.d(TAG, "  Kind: $kind")
            Log.d(TAG, "  TransportId: ${nativeTransport.id}")
            
            if (kind == "video") {
                @Suppress("UNCHECKED_CAST")
                val codecs = rtpParameters["codecs"] as? List<Map<String, Any?>>
                codecs?.forEachIndexed { idx, codec ->
                    val mimeType = codec["mimeType"] as? String
                    val payloadType = codec["payloadType"]
                    val params = codec["parameters"] as? Map<*, *>
                    val profileLevelId = params?.get("profile-level-id")
                    Log.d(TAG, "  CODEC[$idx]: $mimeType PT=$payloadType profile-level-id=$profileLevelId")
                }
                
                // Log full RTP parameters JSON
                Log.d(TAG, "=== FULL RTP PARAMETERS JSON ===")
                Log.d(TAG, rtpParametersJson)
                Log.d(TAG, "=== END RTP PARAMETERS JSON ===")
            }
            // === END H264 DEBUG ===
            
            Log.d(TAG, "  >>> Calling native recvTransport.consume()...")
            val consumer = try {
                recvTransport.consume(
                    object : Consumer.Listener {
                        override fun onTransportClose(consumer: org.mediasoup.droid.Consumer) { }
                    },
                    id,
                    producerId,
                    nativeKind,
                    rtpParametersJson,
                    "{}" // appData
                )
            } catch (error: Throwable) {
                Log.e(TAG, "=== NATIVE CONSUME ERROR ===")
                Log.e(TAG, "  Error: ${error.message}")
                error.printStackTrace()
                throw error
            }
            Log.d(TAG, "  <<< Native consume returned successfully")
            
            // === POST-CONSUME DEBUG ===
            Log.d(TAG, "=== NATIVE CONSUME SUCCESS ===")
            Log.d(TAG, "  ConsumerId: ${consumer.id}")
            Log.d(TAG, "  Kind: ${consumer.kind}")
            Log.d(TAG, "  Track: ${consumer.track}")
            Log.d(TAG, "  Track state: ${consumer.track?.state()}")
            Log.d(TAG, "  Track enabled: ${consumer.track?.enabled()}")
            
            consumer.track?.let { nativeTrack ->
                if (nativeTrack is VideoTrack) {
                    Log.d(TAG, "  VideoTrack state: ${nativeTrack.state()}")
                    attachEarlyVideoProbe(consumer.id, nativeTrack)
                }
            }
            
            // === CONSUMER STATS DEBUG ===
            // Log RTP parameters returned by native consumer
            try {
                val nativeRtpParams = consumer.rtpParameters
                Log.d(TAG, "=== NATIVE CONSUMER RTP PARAMETERS ===")
                Log.d(TAG, nativeRtpParams)
                Log.d(TAG, "=== END NATIVE CONSUMER RTP PARAMETERS ===")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get native RTP parameters: ${e.message}")
            }
            
            // Schedule stats check after 2 seconds
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val stats = consumer.stats
                    Log.d(TAG, "=== CONSUMER STATS (2s after creation) ===")
                    Logger.d("AndroidWebRtcDevice", stats)
                    Logger.d("AndroidWebRtcDevice", "=== END CONSUMER STATS ===")
                } catch (e: Exception) {
                    Logger.d("AndroidWebRtcDevice", "Failed to get consumer stats: ${e.message}")
                }
            }, 2000)
            
            return AndroidWebRtcConsumer(consumer)
        }
    }

    private inner class AndroidMediaStreamTrack(
        val nativeTrack: NativeMediaStreamTrack
    ) : MediaStreamTrack {

        private val stopped = AtomicBoolean(false)

        override val id: String
            get() = nativeTrack.id()

        override val kind: String
            get() = nativeTrack.kind()

        override val enabled: Boolean
            get() = nativeTrack.enabled()
        
        override fun setEnabled(enabled: Boolean) {
            if (!stopped.get()) {
                nativeTrack.setEnabled(enabled)
            }
        }

        val isLive: Boolean
            get() = nativeTrack.state() == NativeMediaStreamTrack.State.LIVE

        override fun asPlatformNativeTrack(): Any = nativeTrack

        override fun stop() {
            if (stopped.compareAndSet(false, true)) {
                nativeTrack.setEnabled(false)
                nativeTrack.dispose()
            }
        }
    }

    private inner class AndroidMediaStream(
        private val nativeStream: NativeMediaStream,
        private val audioTrack: AudioTrack?,
        private val audioSource: AudioSource?,
        private val videoTrack: VideoTrack?,
        private val videoSource: VideoSource?,
        private val videoCapturer: VideoCapturer?,
        private val surfaceTextureHelper: SurfaceTextureHelper?
    ) : MediaStream {

        private val audioWrapper = audioTrack?.let { AndroidMediaStreamTrack(it) }
        private val videoWrapper = videoTrack?.let { AndroidMediaStreamTrack(it) }
        private val stopped = AtomicBoolean(false)

        override val id: String = nativeStream.id

        override val active: Boolean
            get() = listOfNotNull(audioWrapper, videoWrapper).any { it.isLive }

        override fun getTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper, videoWrapper)

        override fun getAudioTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper)

        override fun getVideoTracks(): List<MediaStreamTrack> = listOfNotNull(videoWrapper)

        override fun addTrack(track: MediaStreamTrack) {
            if (track is AndroidMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is AudioTrack -> nativeStream.addTrack(native)
                    is VideoTrack -> nativeStream.addTrack(native)
                }
            }
        }

        override fun removeTrack(track: MediaStreamTrack) {
            if (track is AndroidMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is AudioTrack -> nativeStream.removeTrack(native)
                    is VideoTrack -> nativeStream.removeTrack(native)
                }
            }
        }

        override fun stop() {
            if (!stopped.compareAndSet(false, true)) return

            audioWrapper?.stop()
            audioSource?.dispose()

            videoCapturer?.let { capturer ->
                runCatching { capturer.stopCapture() }
                capturer.dispose()
            }

            videoWrapper?.stop()
            videoSource?.dispose()
            surfaceTextureHelper?.dispose()
            nativeStream.dispose()
        }
    }

    private inner class AndroidWebRtcProducer(
        private val nativeProducer: Producer,
        override val source: ProducerSource
    ) : WebRtcProducer {

        private val closed = AtomicBoolean(false)

        override val id: String
            get() = nativeProducer.id

        override val kind: MediaKind
            get() = nativeProducer.kind.toMediaKind()

        override val paused: Boolean
            get() = nativeProducer.isPaused

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { nativeProducer.close() }
                unregisterProducer(this)
            }
        }

        override fun pause() {
            if (!closed.get()) runCatching { nativeProducer.pause() }
        }

        override fun resume() {
            if (!closed.get()) runCatching { nativeProducer.resume() }
        }
        
        override fun replaceTrack(track: MediaStreamTrack) {
            if (closed.get()) {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcProducer.replaceTrack: Producer is closed, ignoring")
                return
            }
            
            val nativeTrack = track.asPlatformNativeTrack() as? org.webrtc.MediaStreamTrack
            if (nativeTrack == null) {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcProducer.replaceTrack: Could not get native track from MediaStreamTrack")
                return
            }
            
            runCatching {
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcProducer.replaceTrack: Replacing track on producer ${nativeProducer.id} with track ${nativeTrack.id()}")
                nativeProducer.replaceTrack(nativeTrack)
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcProducer.replaceTrack: Successfully replaced track")
            }.onFailure { error ->
                Logger.d("AndroidWebRtcDevice", "MediaSFU - AndroidWebRtcProducer.replaceTrack: Failed to replace track -> ${error.message}")
            }
        }
    }

    private fun logRtpParametersSummary(
        kind: String,
        producerId: String,
        rtpParameters: Map<String, Any?>
    ) {
        val codecsSummary = (rtpParameters["codecs"] as? List<*>)
            ?.mapNotNull { entry ->
                (entry as? Map<*, *>)?.let { codec ->
                    val mime = (codec["mimeType"] ?: codec["mime_type"])?.toString()
                    val payload = (codec["payloadType"] ?: codec["payload_type"])?.toString()
                    val clockRate = (codec["clockRate"] ?: codec["clock_rate"])?.toString()
                    when {
                        mime != null && payload != null && clockRate != null -> "$mime/$payload@$clockRate"
                        mime != null && payload != null -> "$mime/$payload"
                        mime != null -> mime
                        else -> null
                    }
                }
            }
            ?.joinToString()
            ?.takeIf { it.isNotEmpty() }
            ?: "n/a"

        val encodings = (rtpParameters["encodings"] as? List<*>)
        val headerExts = (rtpParameters["headerExtensions"] as? List<*>)
        val encodingsCount = encodings?.size ?: 0
        val headerCount = headerExts?.size ?: 0
        val mid = rtpParameters["mid"] ?: "n/a"
        val degradation = (rtpParameters["degradationPreference"] ?: "n/a").toString()

        // Silent: parity check disabled
    }

    private fun emitVideoRtpParityWarnings(
        producerId: String,
        headerUris: List<String>,
        encodings: List<Map<*, *>>,
        rtcp: Map<*, *>?
    ) {
        // Silent: parity warnings disabled
    }

    private fun attachEarlyVideoProbe(consumerId: String, track: VideoTrack) {
        if (!videoProbeEnabled || eagerVideoProbes.containsKey(consumerId)) return
        val startTime = System.currentTimeMillis()
        Logger.d("AndroidWebRtcDevice", "=== ATTACHING VIDEO PROBE for consumer: $consumerId ===")
        val sink = object : VideoSink {
            private val frameCount = AtomicInteger(0)
            override fun onFrame(frame: VideoFrame) {
                val count = frameCount.incrementAndGet()
                if (count <= 5 || count % 30 == 0) {
                    val elapsed = System.currentTimeMillis() - startTime
                    Logger.d("AndroidWebRtcDevice", "=== VIDEO FRAME #$count (${elapsed}ms) ===")
                    Logger.d("AndroidWebRtcDevice", "  width: ${frame.buffer.width}, height: ${frame.buffer.height}")
                    Logger.d("AndroidWebRtcDevice", "  rotation: ${frame.rotation}")
                }
                if (count >= 120) {
                    track.removeSink(this)
                    eagerVideoProbes.remove(consumerId)
                    Logger.d("AndroidWebRtcDevice", "=== VIDEO PROBE COMPLETE: 120 frames received ===")
                }
            }
        }
        eagerVideoProbes[consumerId] = VideoProbeHandle(track, sink, startTime)
        track.addSink(sink)
    }

    private fun detachEarlyVideoProbe(consumerId: String) {
        val handle = eagerVideoProbes.remove(consumerId) ?: return
        handle.track.removeSink(handle.sink)
    }

    private data class TransportDiagnostics(
        val dtlsState: String?,
        val candidateState: String?,
        val selectedPairId: String?,
        val localCandidate: String?,
        val remoteCandidate: String?,
        val availableOutgoingBitrate: Double?,
        val currentRoundTripTime: Double?
    )

    private inner class AndroidWebRtcConsumer(
        private val nativeConsumer: org.mediasoup.droid.Consumer
    ) : WebRtcConsumer, AudioStatsProvider {

        private val closed = AtomicBoolean(false)
        private val countedForFocus = AtomicBoolean(false)
        private val statsLoggingStarted = AtomicBoolean(false)
        private val videoStatsLoggingStarted = AtomicBoolean(false)

        override val id: String
            get() = nativeConsumer.id

        override val kind: MediaKind
            get() = nativeConsumer.kind.toMediaKind()

        override val track: MediaStreamTrack?
            get() {
                val nativeTrack = nativeConsumer.track ?: return null
                
                // For audio tracks, ensure they're enabled and set volume for playback
                if (nativeTrack is AudioTrack) {
                    nativeTrack.setEnabled(true)
                    nativeTrack.setVolume(10.0)
                    
                    audioFocusManager.ensureVoiceCommunicationRouting()
                    if (countedForFocus.compareAndSet(false, true)) {
                        activeAudioConsumers.incrementAndGet()
                    }

                    scheduleAudioStatsLogging()
                } else if (nativeTrack is VideoTrack) {
                    detachEarlyVideoProbe(nativeConsumer.id)
                    scheduleVideoStatsLogging()
                }
                
                return AndroidMediaStreamTrack(nativeTrack)
            }

        override val stream: MediaStream?
            get() {
                // Get the media stream from the consumer's track
                val nativeTrack = nativeConsumer.track ?: return null
                nativeTrack.setEnabled(true)
                
                // Create a MediaStream with the track
                val streamId = "stream_${nativeConsumer.id}"
                val nativeStream = peerFactory.createLocalMediaStream(streamId)
                
                when (nativeTrack) {
                    is AudioTrack -> {
                        // Boost volume as high as possible to ensure playback
                        nativeTrack.setVolume(10.0)
                        
                        audioFocusManager.ensureVoiceCommunicationRouting()
                        if (countedForFocus.compareAndSet(false, true)) {
                            activeAudioConsumers.incrementAndGet()
                        }
                        nativeStream.addTrack(nativeTrack)
                        scheduleAudioStatsLogging()
                    }
                    is VideoTrack -> {
                        nativeStream.addTrack(nativeTrack)
                        detachEarlyVideoProbe(nativeConsumer.id)
                        attachDebugVideoProbe(nativeConsumer.id, nativeTrack)
                        scheduleVideoStatsLogging()
                    }
                }
                
                return AndroidMediaStream(
                    nativeStream = nativeStream,
                    audioTrack = nativeTrack as? AudioTrack,
                    audioSource = null,
                    videoTrack = nativeTrack as? VideoTrack,
                    videoSource = null,
                    videoCapturer = null,
                    surfaceTextureHelper = null
                )
            }

        override val paused: Boolean
            get() = nativeConsumer.isPaused

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                runCatching { nativeConsumer.close() }
                if (countedForFocus.compareAndSet(true, false)) {
                    val remaining = activeAudioConsumers.decrementAndGet()
                    if (remaining <= 0) {
                        runCatching { audioFocusManager.abandonFocus() }
                    }
                }
                detachEarlyVideoProbe(nativeConsumer.id)
                detachDebugVideoProbe(nativeConsumer.id)
            }
        }

        override fun pause() {
            if (!closed.get()) runCatching { nativeConsumer.pause() }
        }

        override fun resume() {
            if (!closed.get()) runCatching { nativeConsumer.resume() }
        }

        override fun setPreferredLayers(spatialLayer: Int, temporalLayer: Int) {
            // Not exposed by current mediasoup-client build
        }

        override fun setPriority(priority: Int) {
            // Not exposed by current mediasoup-client build
        }

        override fun requestKeyFrame() {
            // Not exposed by current mediasoup-client build
        }

        private fun attachDebugVideoProbe(consumerId: String, track: VideoTrack) {
            if (!videoProbeEnabled || debugVideoSinks.containsKey(consumerId)) return
            detachEarlyVideoProbe(consumerId)
            val sink = object : VideoSink {
                override fun onFrame(frame: VideoFrame) {
                    // Silent: frame received
                }
            }
            track.addSink(sink)
            debugVideoSinks[consumerId] = sink
        }

        private fun detachDebugVideoProbe(consumerId: String) {
            val sink = debugVideoSinks.remove(consumerId) ?: return
            (nativeConsumer.track as? VideoTrack)?.removeSink(sink)
        }

        private fun scheduleAudioStatsLogging() {
            // Silent: stats logging disabled
        }

        private fun logInboundAudioStats(phase: String) {
            // Silent: verbose stats logging removed
        }

        private fun scheduleVideoStatsLogging() {
            // Silent: stats logging disabled
        }

        private fun logInboundVideoStats(phase: String) {
            // Silent: verbose stats logging removed
        }

        private fun parseInboundVideoStats(statsArray: JSONArray): VideoInboundStats? {
            return runCatching {
                for (index in 0 until statsArray.length()) {
                    val entry = statsArray.optJSONObject(index) ?: continue
                    val type = entry.optString("type")
                    if (type != "inbound-rtp") continue
                    val mediaKind = entry.optString("kind").ifEmpty { entry.optString("mediaType") }
                    if (!mediaKind.equals("video", ignoreCase = true)) continue

                    return VideoInboundStats(
                        packetsReceived = entry.optLongOrDefault("packetsReceived"),
                        packetsLost = entry.optLongOrDefault("packetsLost"),
                        bytesReceived = entry.optLongOrNullCompat("bytesReceived"),
                        framesDecoded = entry.optLongOrNullCompat("framesDecoded"),
                        framesDropped = entry.optLongOrNullCompat("framesDropped"),
                        framesPerSecond = entry.optDoubleOrNullCompat("framesPerSecond"),
                        frameWidth = entry.optIntOrNullCompat("frameWidth"),
                        frameHeight = entry.optIntOrNullCompat("frameHeight"),
                        qpSum = entry.optDoubleOrNullCompat("qpSum"),
                        totalDecodeTime = entry.optDoubleOrNullCompat("totalDecodeTime"),
                        jitter = entry.optDoubleOrNullCompat("jitter"),
                        firCount = entry.optLongOrNullCompat("firCount"),
                        pliCount = entry.optLongOrNullCompat("pliCount"),
                        nackCount = entry.optLongOrNullCompat("nackCount"),
                        freezeCount = entry.optLongOrNullCompat("freezeCount"),
                        pauseCount = entry.optLongOrNullCompat("pauseCount"),
                        keyFramesDecoded = entry.optLongOrNullCompat("keyFramesDecoded"),
                        lastPacketReceivedTimestamp = entry.optDoubleOrNullCompat("lastPacketReceivedTimestamp"),
                        decoderImplementation = entry.optStringOrNullCompat("decoderImplementation")
                    )
                }
                null
            }.getOrElse { _ ->
                // Silent: failed to parse video stats
                null
            }
        }


        override suspend fun getInboundAudioStats(): AudioInboundStats? {
            return fetchInboundAudioStats()
        }

        private fun fetchInboundAudioStats(): AudioInboundStats? {
            val statsJson = runCatching { nativeConsumer.getStats() }
                .getOrNull() ?: return null

            val statsArray = runCatching { JSONArray(statsJson) }
                .getOrNull() ?: return null

            return parseInboundAudioStats(statsArray)
        }

        private fun parseInboundAudioStats(statsJson: String): AudioInboundStats? {
            val statsArray = runCatching { JSONArray(statsJson) }
                .getOrNull() ?: return null
            return parseInboundAudioStats(statsArray)
        }

        private fun parseInboundAudioStats(statsArray: JSONArray): AudioInboundStats? {
            return runCatching {
                for (index in 0 until statsArray.length()) {
                    val entry = statsArray.optJSONObject(index) ?: continue
                    val type = entry.optString("type")
                    if (type != "inbound-rtp") continue
                    val mediaKind = entry.optString("kind").ifEmpty { entry.optString("mediaType") }
                    if (!mediaKind.equals("audio", ignoreCase = true)) continue

                    return AudioInboundStats(
                        packetsReceived = entry.optLongOrDefault("packetsReceived"),
                        packetsLost = entry.optLongOrDefault("packetsLost"),
                        audioLevel = entry.optDoubleOrNullCompat("audioLevel"),
                        totalAudioEnergy = entry.optDoubleOrNullCompat("totalAudioEnergy"),
                        totalSamplesDuration = entry.optDoubleOrNullCompat("totalSamplesDuration"),
                        concealedSamples = entry.optDoubleOrNullCompat("concealedSamples"),
                        jitter = entry.optDoubleOrNullCompat("jitter"),
                        bytesReceived = entry.optLongOrNullCompat("bytesReceived"),
                        lastPacketReceivedTimestamp = entry.optDoubleOrNullCompat("lastPacketReceivedTimestamp"),
                        ssrc = entry.optLongOrNullCompat("ssrc"),
                        jitterBufferDelay = entry.optDoubleOrNullCompat("jitterBufferDelay"),
                        jitterBufferEmittedCount = entry.optLongOrNullCompat("jitterBufferEmittedCount"),
                        framesDecoded = entry.optLongOrNullCompat("framesDecoded"),
                        trackIdentifier = entry.optStringOrNullCompat("trackIdentifier"),
                        mid = entry.optStringOrNullCompat("mid")
                    )
                }
                null
            }.getOrElse { _ ->
                // Silent: failed to parse audio stats
                null
            }
        }

        private fun extractTransportDiagnostics(statsArray: JSONArray): TransportDiagnostics? {
            var dtlsState: String? = null
            var selectedPairId: String? = null
            val candidatePairs = mutableMapOf<String, JSONObject>()
            val localCandidates = mutableMapOf<String, JSONObject>()
            val remoteCandidates = mutableMapOf<String, JSONObject>()

            for (index in 0 until statsArray.length()) {
                val entry = statsArray.optJSONObject(index) ?: continue
                when (entry.optString("type")) {
                    "transport" -> {
                        dtlsState = entry.optStringOrNullCompat("dtlsState") ?: dtlsState
                        selectedPairId = entry.optStringOrNullCompat("selectedCandidatePairId")
                            ?: entry.optStringOrNullCompat("iceSelectedCandidatePairId")
                    }
                    "candidate-pair" -> candidatePairs[entry.optString("id")] = entry
                    "local-candidate" -> localCandidates[entry.optString("id")] = entry
                    "remote-candidate" -> remoteCandidates[entry.optString("id")] = entry
                }
            }

            if (dtlsState == null && selectedPairId == null && candidatePairs.isEmpty()) return null

            val selectedPair = selectedPairId?.let { candidatePairs[it] }
            val candidateState = selectedPair?.optStringOrNullCompat("state")
            val localSummary = selectedPair?.optStringOrNullCompat("localCandidateId")
                ?.let { summarizeCandidate(localCandidates[it]) }
            val remoteSummary = selectedPair?.optStringOrNullCompat("remoteCandidateId")
                ?.let { summarizeCandidate(remoteCandidates[it]) }

            return TransportDiagnostics(
                dtlsState = dtlsState,
                candidateState = candidateState,
                selectedPairId = selectedPairId,
                localCandidate = localSummary,
                remoteCandidate = remoteSummary,
                availableOutgoingBitrate = selectedPair?.optDoubleOrNullCompat("availableOutgoingBitrate"),
                currentRoundTripTime = selectedPair?.optDoubleOrNullCompat("currentRoundTripTime")
            )
        }

        private fun extractAudioContributingSources(statsArray: JSONArray): List<String> {
            val sources = mutableSetOf<String>()
            for (index in 0 until statsArray.length()) {
                val entry = statsArray.optJSONObject(index) ?: continue
                when (entry.optString("type")) {
                    "receiver" -> {
                        val kind = entry.optString("kind").ifEmpty { entry.optString("mediaType") }
                        if (!kind.equals("audio", ignoreCase = true)) continue
                        val contributing = entry.optJSONArray("contributingSources") ?: continue
                        for (i in 0 until contributing.length()) {
                            val obj = contributing.optJSONObject(i)
                            if (obj != null) {
                                sources += buildContributingSourceSummary(obj)
                            } else {
                                val rawValue = contributing.opt(i)
                                if (rawValue != null) sources += rawValue.toString()
                            }
                        }
                    }
                    "contributing-source" -> sources += buildContributingSourceSummary(entry)
                }
            }
            return sources.filter { it.isNotEmpty() }
        }

        private fun buildContributingSourceSummary(obj: JSONObject): String {
            val sourceId = obj.optLongOrNullCompat("source")
                ?: obj.optLongOrNullCompat("contributorSsrc")
                ?: obj.optLongOrNullCompat("ssrc")
            val audioLevel = obj.optDoubleOrNullCompat("audioLevel")
            val timestamp = obj.optDoubleOrNullCompat("timestamp")
            val levelText = audioLevel?.let { formatStat(it, 5) }
            val tsText = timestamp?.let { formatStat(it) }
            return listOfNotNull(
                sourceId?.toString(),
                levelText?.let { "level=$it" },
                tsText?.let { "ts=$it" }
            ).joinToString(separator = ", ")
        }

        private fun summarizeCandidate(candidate: JSONObject?): String? {
            candidate ?: return null
            val ip = candidate.optStringOrNullCompat("ip")
                ?: candidate.optStringOrNullCompat("address")
            val port = candidate.optIntOrNullCompat("port")
            val protocol = candidate.optStringOrNullCompat("protocol")?.uppercase(Locale.US)
            val candidateType = candidate.optStringOrNullCompat("candidateType")
                ?: candidate.optStringOrNullCompat("type")
            val networkType = candidate.optStringOrNullCompat("networkType")
            val endpoint = when {
                ip != null && port != null -> "$ip:$port"
                ip != null -> ip
                else -> null
            }
            return listOfNotNull(endpoint, candidateType, protocol, networkType)
                .joinToString(separator = "/")
        }

        private fun JSONObject.optDoubleOrNullCompat(key: String): Double? {
            return if (has(key) && !isNull(key)) optDouble(key) else null
        }

        private fun JSONObject.optLongOrDefault(key: String, default: Long = -1L): Long {
            return if (has(key) && !isNull(key)) optLong(key) else default
        }

        private fun JSONObject.optLongOrNullCompat(key: String): Long? {
            return if (has(key) && !isNull(key)) optLong(key) else null
        }

        private fun JSONObject.optIntOrNullCompat(key: String): Int? {
            return if (has(key) && !isNull(key)) optInt(key) else null
        }

        private fun JSONObject.optStringOrNullCompat(key: String): String? {
            return if (has(key) && !isNull(key)) optString(key) else null
        }
    }

    private fun buildEncodingOverrideKey(transportId: String, kind: String): String {
        return "$transportId::${kind.lowercase()}"
    }

    private fun formatStat(value: Double?, decimals: Int = 4): String {
        val actual = value ?: return "n/a"
        if (actual.isNaN() || actual.isInfinite()) return "n/a"
        return String.format(Locale.US, "%.${decimals}f", actual)
    }

    private fun applyEncodingOverridesIfNeeded(
        transportId: String,
        kind: String,
        originalRtpParameters: String
    ): String {
        val key = buildEncodingOverrideKey(transportId, kind)
        val queue = pendingEncodingOverrides[key] ?: return originalRtpParameters
        val template = queue.poll() ?: run {
            if (queue.isEmpty()) {
                pendingEncodingOverrides.remove(key, queue)
            }
            return originalRtpParameters
        }
        if (queue.isEmpty()) {
            pendingEncodingOverrides.remove(key, queue)
        }

        return runCatching {
            val json = JSONObject(originalRtpParameters)
            val encodingsArray = json.optJSONArray("encodings") ?: return originalRtpParameters
            val upperBound = min(encodingsArray.length(), template.size)
            for (index in 0 until upperBound) {
                val encodingJson = encodingsArray.optJSONObject(index) ?: continue
                val templateEncoding = template[index]
                putIfNotNullString(encodingJson, "scalabilityMode", templateEncoding.scalabilityMode)
                // NOTE: networkPriority deliberately excluded - not needed
                putIfNotNullString(encodingJson, "rid", templateEncoding.rid)
                putIfNotNullString(encodingJson, "maxBitrate", templateEncoding.maxBitrate)
                putIfNotNullString(encodingJson, "minBitrate", templateEncoding.minBitrate)
                putIfNotNullString(encodingJson, "scaleResolutionDownBy", templateEncoding.scaleResolutionDownBy)
                putIfNotNullString(encodingJson, "maxFramerate", templateEncoding.maxFramerate)
                putIfNotNullString(encodingJson, "dtx", templateEncoding.dtx)
                putIfNotNullString(encodingJson, "ssrc", templateEncoding.ssrc)
            }
            (stringifyJson(json) as? JSONObject ?: json).toString()
        }.getOrElse { originalRtpParameters }
    }

    private fun serializeEncodingsForNative(encodings: List<RtpEncodingParameters>): String {
        // Manually build JSON string to ensure all values are quoted strings
        val jsonObjects = encodings.map { encoding ->
            buildMap<String, String> {
                encoding.ssrc?.let { put("ssrc", it.toString()) }
                encoding.rid?.let { put("rid", it) }
                encoding.maxBitrate?.let { put("maxBitrate", it.toString()) }
                encoding.minBitrate?.let { put("minBitrate", it.toString()) }
                encoding.maxFramerate?.let { put("maxFramerate", it.toString()) }
                encoding.scalabilityMode?.let { put("scalabilityMode", it) }
                encoding.scaleResolutionDownBy?.let { put("scaleResolutionDownBy", it.toString()) }
                encoding.dtx?.let { put("dtx", it.toString()) }
                // NOTE: networkPriority deliberately excluded - not needed for native layer
            }.entries.joinToString(",", "{", "}") { (key, value) ->
                """"$key":"$value""""
            }
        }
        return jsonObjects.joinToString(",", "[", "]")
    }

    private fun putIfNotNullString(target: JSONObject, key: String, value: Any?) {
        when (value) {
            null -> target.remove(key)
            is Boolean -> target.put(key, value.toString())
            is Number -> target.put(key, value.toString())
            else -> target.put(key, value.toString())
        }
    }

    private fun stringifyJson(node: Any?): Any? = when (node) {
        null -> JSONObject.NULL
        JSONObject.NULL -> JSONObject.NULL
        is JSONObject -> {
            val toUpdate = mutableListOf<Pair<String, Any?>>()
            val keys = node.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val processed = stringifyJson(node.opt(key))
                toUpdate += key to processed
            }
            toUpdate.forEach { (key, value) ->
                when (value) {
                    null, JSONObject.NULL -> node.put(key, JSONObject.NULL)
                    else -> node.put(key, value)
                }
            }
            node
        }
        is JSONArray -> {
            for (index in 0 until node.length()) {
                when (val processed = stringifyJson(node.opt(index))) {
                    null, JSONObject.NULL -> node.put(index, JSONObject.NULL)
                    else -> node.put(index, processed)
                }
            }
            node
        }
        is Number, is Boolean -> node.toString()
        else -> node
    }

    private data class VideoCaptureConfig(
        val width: Int = 1280,
        val height: Int = 720,
        val frameRate: Int = 30,
        val facingMode: String? = null,
        val deviceId: String? = null
    )
}

/**
 * Android implementation of VirtualVideoSource.
 * Allows feeding processed Bitmap frames into a WebRTC video track.
 */
class AndroidVirtualVideoSource(
    private val nativeVideoSource: org.webrtc.VideoSource,
    private val nativeVideoTrack: org.webrtc.VideoTrack,
    override val stream: MediaStream,
    private val width: Int,
    private val height: Int
) : VirtualVideoSource {
    
    companion object {
        private const val TAG = "AndroidVirtualVideoSource"
    }
    
    private var isStarted = false
    private val capturerObserver: CapturerObserver = nativeVideoSource.capturerObserver
    
    override val videoTrack: MediaStreamTrack
        get() = object : MediaStreamTrack {
            override val id: String = nativeVideoTrack.id()
            override val kind: String = nativeVideoTrack.kind()
            override val enabled: Boolean = nativeVideoTrack.enabled()
            override fun setEnabled(enabled: Boolean) { nativeVideoTrack.setEnabled(enabled) }
            override fun stop() { nativeVideoTrack.setEnabled(false) }
            override fun asPlatformNativeTrack(): Any = nativeVideoTrack
        }
    
    override fun onFrame(bitmap: Any, timestampNs: Long, rotation: Int) {
        if (!isStarted) {
            Logger.d("AndroidWebRtcDevice", "$TAG: onFrame called but not started, ignoring")
            return
        }
        
        val androidBitmap = bitmap as? android.graphics.Bitmap
        if (androidBitmap == null) {
            Logger.d("AndroidWebRtcDevice", "$TAG: onFrame received non-Bitmap object: ${bitmap::class.simpleName}")
            return
        }
        
        try {
            // Convert Bitmap to I420 buffer and create VideoFrame
            val videoFrame = bitmapToVideoFrame(androidBitmap, timestampNs, rotation)
            capturerObserver.onFrameCaptured(videoFrame)
            Logger.d("AndroidWebRtcDevice", "$TAG: Frame captured to video source: ${androidBitmap.width}x${androidBitmap.height}, timestamp=$timestampNs")
            videoFrame.release()
        } catch (e: Exception) {
            Logger.d("AndroidWebRtcDevice", "$TAG: Error converting bitmap to frame: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun start() {
        if (isStarted) {
            Logger.d("AndroidWebRtcDevice", "$TAG: start() called but already started")
            return
        }
        isStarted = true
        capturerObserver.onCapturerStarted(true)
        Logger.d("AndroidWebRtcDevice", "$TAG: Started virtual video source ${width}x${height}")
        Logger.d("AndroidWebRtcDevice", "$TAG:   - Track ID: ${nativeVideoTrack.id()}")
        Logger.d("AndroidWebRtcDevice", "$TAG:   - Track enabled: ${nativeVideoTrack.enabled()}")
        Logger.d("AndroidWebRtcDevice", "$TAG:   - Track state: ${nativeVideoTrack.state()}")
    }
    
    override fun stop() {
        if (!isStarted) return
        isStarted = false
        capturerObserver.onCapturerStopped()
        Logger.d("AndroidWebRtcDevice", "$TAG: Stopped virtual video source")
    }
    
    override fun release() {
        stop()
        nativeVideoTrack.dispose()
        nativeVideoSource.dispose()
        Logger.d("AndroidWebRtcDevice", "$TAG: Released virtual video source")
    }
    
    /**
     * Converts an Android Bitmap to a WebRTC VideoFrame with I420 buffer.
     */
    private fun bitmapToVideoFrame(bitmap: android.graphics.Bitmap, timestampNs: Long, rotation: Int): VideoFrame {
        val width = bitmap.width
        val height = bitmap.height
        
        // Get ARGB pixels from bitmap
        val argbPixels = IntArray(width * height)
        bitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)
        
        // Allocate I420 buffer
        val i420Buffer = JavaI420Buffer.allocate(width, height)
        
        // Convert ARGB to I420
        val yBuffer = i420Buffer.dataY
        val uBuffer = i420Buffer.dataU
        val vBuffer = i420Buffer.dataV
        
        val yStride = i420Buffer.strideY
        val uStride = i420Buffer.strideU
        val vStride = i420Buffer.strideV
        
        // Convert each pixel from ARGB to YUV
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = argbPixels[y * width + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                
                // Standard BT.601 conversion
                val yValue = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuffer.put(y * yStride + x, yValue.coerceIn(0, 255).toByte())
                
                // Subsample U and V (2x2 blocks)
                if (y % 2 == 0 && x % 2 == 0) {
                    val uValue = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val vValue = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uBuffer.put((y / 2) * uStride + (x / 2), uValue.coerceIn(0, 255).toByte())
                    vBuffer.put((y / 2) * vStride + (x / 2), vValue.coerceIn(0, 255).toByte())
                }
            }
        }
        
        return VideoFrame(i420Buffer, rotation, timestampNs)
    }
}

private data class VideoInboundStats(
    val packetsReceived: Long,
    val packetsLost: Long,
    val bytesReceived: Long?,
    val framesDecoded: Long?,
    val framesDropped: Long?,
    val framesPerSecond: Double?,
    val frameWidth: Int?,
    val frameHeight: Int?,
    val qpSum: Double?,
    val totalDecodeTime: Double?,
    val jitter: Double?,
    val firCount: Long?,
    val pliCount: Long?,
    val nackCount: Long?,
    val freezeCount: Long?,
    val pauseCount: Long?,
    val keyFramesDecoded: Long?,
    val lastPacketReceivedTimestamp: Double?,
    val decoderImplementation: String?
)
