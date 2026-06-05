package com.mediasfu.sdk.webrtc

import cnames.structs.__CVBuffer
import cocoapods.WebRTC.RTCCameraVideoCapturer
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.ui.components.whiteboard.renderWhiteboardFramePng
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVFrameRateRange
import cocoapods.WebRTC.RTCAudioSource
import platform.AVFoundation.AVCaptureVideoDataOutput
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureVideoOrientationPortrait

import cocoapods.WebRTC.RTCAudioSession
import cocoapods.WebRTC.RTCAudioTrack
import cocoapods.WebRTC.RTCCVPixelBuffer
import cocoapods.WebRTC.RTCMediaStream
import cocoapods.WebRTC.RTCMediaStreamTrack
import cocoapods.WebRTC.RTCMediaStreamTrackState
import cocoapods.WebRTC.RTCPeerConnectionFactory
import cocoapods.WebRTC.RTCVideoCapturer
import cocoapods.WebRTC.RTCVideoCapturerDelegateProtocol
import cocoapods.WebRTC.RTCVideoFrame
import cocoapods.WebRTC.RTCVideoSource
import cocoapods.WebRTC.RTCVideoTrack
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextClearRect
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMVideoFormatDescriptionGetDimensions
import platform.Foundation.NSLog
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.Foundation.dataWithBytes
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.kCVPixelBufferCGBitmapContextCompatibilityKey
import platform.CoreVideo.kCVPixelBufferCGImageCompatibilityKey
import platform.CoreVideo.kCVPixelBufferMetalCompatibilityKey
import platform.CoreVideo.kCVPixelBufferOpenGLESCompatibilityKey
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVReturnSuccess
import platform.QuartzCore.CACurrentMediaTime
import platform.ReplayKit.RPSampleBufferTypeVideo
import platform.ReplayKit.RPScreenRecorder
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.darwin.NSObject

/**
 * Lightweight iOS WebRTC device implementation that focuses on exposing native
 * media tracks to the shared UI layer. Transport support is still pending.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSWebRtcDevice private constructor() : WebRtcDevice {

    private val peerConnectionFactory: RTCPeerConnectionFactory = run {
        RTCPeerConnectionFactory.initialize()
        RTCPeerConnectionFactory()
    }

    private var lastLoadedCapabilities: RtpCapabilities? = null
    private var activeCameraCapture: IOSCameraCaptureSession? = null
    private var activeSimulatorFallbackCapture: IOSSimulatorVideoFallbackSession? = null
    private var activeScreenShare: IOSScreenCaptureSession? = null
    private var activeCameraFrameProcessor: ((RTCVideoFrame) -> RTCVideoFrame)? = null

    companion object {
        private val instance: IOSWebRtcDevice by lazy { IOSWebRtcDevice() }

        fun getInstance(): IOSWebRtcDevice = instance
    }

    override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
        return runCatching {
            val bridge = IosNativeMediasoupBridgeProvider.bridge
            val loadableBridge = bridge as? IosNativeLoadableMediasoupBridge
            if (loadableBridge != null) {
                val loadError = loadableBridge.loadRtpCapabilitiesJson(rtpCapabilities.toIosBridgeLoadJson())
                check(loadError.isNullOrBlank()) {
                    "iOS bridge RTP load failed: $loadError"
                }
            }
            lastLoadedCapabilities = fetchNativeDeviceRtpCapabilities() ?: rtpCapabilities
        }.onFailure {
            // Do not advertise loaded RTP capabilities when the native bridge rejected the load.
            // The send-transport path uses currentRtpCapabilities() as part of its readiness gate.
            lastLoadedCapabilities = null
        }
    }

    override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
        return withContext(Dispatchers.Main) {
            val audioConstraints = constraints["audio"]
            val videoConstraintsValue = constraints["video"]
            val audioEnabled = when (audioConstraints) {
                is Boolean -> audioConstraints
                is Map<*, *> -> true
                else -> false
            }
            val videoEnabled = when (videoConstraintsValue) {
                is Boolean -> videoConstraintsValue
                is Map<*, *> -> true
                else -> false
            }

            @Suppress("UNCHECKED_CAST")
            val videoConstraints = videoConstraintsValue as? Map<String, Any?> ?: emptyMap()
            @Suppress("UNCHECKED_CAST")
            val mandatoryConstraints = videoConstraints["mandatory"] as? Map<String, Any?> ?: emptyMap()
            val effectiveVideoConstraints = if (mandatoryConstraints.isEmpty()) {
                videoConstraints
            } else {
                videoConstraints + mandatoryConstraints
            }

            val streamId = "ios_stream_${NSUUID().UUIDString}"
            val nativeStream = peerConnectionFactory.mediaStreamWithStreamId(streamId)

            var audioSource: RTCAudioSource? = null
            var audioTrack: RTCAudioTrack? = null
            if (audioEnabled) {
                runCatching {
                    RTCAudioSession.sharedInstance().isAudioEnabled = true
                }.onFailure { error ->
                    Logger.e(
                        "IOSWebRtcDevice",
                        "getUserMedia(audio) failed to prime iOS audio session: ${error.message}"
                    )
                }
                audioSource = peerConnectionFactory.audioSourceWithConstraints(null)
                audioTrack = peerConnectionFactory.audioTrackWithSource(audioSource, "audio_$streamId")
                nativeStream.addAudioTrack(audioTrack)
            }

            var videoSource: RTCVideoSource? = null
            var videoTrack: RTCVideoTrack? = null
            var cameraSession: IOSCameraCaptureSession? = null
            if (videoEnabled) {
                videoSource = peerConnectionFactory.videoSource()
                // Default to 640x480@30fps — this is less demanding than 1280x720 and
                // still provides a good quality preview on physical devices.
                val requestedWidth = resolveIntConstraint(effectiveVideoConstraints["width"], fallback = 640)
                val requestedHeight = resolveIntConstraint(effectiveVideoConstraints["height"], fallback = 480)
                // Cap iOS defaults to VGA to keep capture/encode smooth on physical devices.
                val captureWidth = requestedWidth.coerceAtMost(640)
                val captureHeight = requestedHeight.coerceAtMost(480)
                val captureFrameRate = resolveFrameRate(effectiveVideoConstraints["frameRate"], fallback = 30)
                val requestedDeviceId = resolveStringConstraint(effectiveVideoConstraints["deviceId"])
                val requestedFacingMode = resolveFacingMode(effectiveVideoConstraints["facingMode"])
                videoSource.adaptOutputFormatToWidth(captureWidth, captureHeight, captureFrameRate)
                videoTrack = peerConnectionFactory.videoTrackWithSource(videoSource, "video_$streamId")
                nativeStream.addVideoTrack(videoTrack)

                val runningOnSimulator = isRunningOnSimulator()
                val captureDevice = if (runningOnSimulator) {
                    null
                } else {
                    selectCaptureDevice(
                        requestedDeviceId = requestedDeviceId,
                        requestedFacingMode = requestedFacingMode
                    )
                }
                val captureFormat = captureDevice?.let {
                    selectCaptureFormat(
                        device = it,
                        targetWidth = captureWidth,
                        targetHeight = captureHeight,
                        targetFrameRate = captureFrameRate
                    )
                }

                if (captureDevice != null && captureFormat != null) {
                    val selectedDimensions = CMVideoFormatDescriptionGetDimensions(captureFormat.formatDescription)
                    val selectedWidth = selectedDimensions.useContents { width.toInt() }
                    val selectedHeight = selectedDimensions.useContents { height.toInt() }
                    Logger.i(
                        "IOSWebRtcDevice",
                        "getUserMedia(video) requested=${requestedWidth}x${requestedHeight}@${captureFrameRate}fps effective=${captureWidth}x${captureHeight} device=${captureDevice.localizedName} selectedFormat=${selectedWidth}x${selectedHeight}"
                    )

                    activeCameraCapture?.stop()
                    val frameDelegate = IOSCameraFrameDelegate(videoSource) { activeCameraFrameProcessor }
                    val delegate: RTCVideoCapturerDelegateProtocol = frameDelegate
                    val capturer = RTCCameraVideoCapturer(delegate)
                    cameraSession = IOSCameraCaptureSession(
                        capturer = capturer,
                        frameDelegate = frameDelegate,
                        videoSource = videoSource,
                        device = captureDevice,
                        format = captureFormat,
                        targetWidth = captureWidth,
                        targetHeight = captureHeight,
                        frameRate = captureFrameRate
                    )
                    cameraSession.start()
                    activeCameraCapture = cameraSession
                    activeSimulatorFallbackCapture?.stop()
                    activeSimulatorFallbackCapture = null
                } else {
                    Logger.w(
                        "IOSWebRtcDevice",
                        "No camera device/format found for ${captureWidth}x${captureHeight}@${captureFrameRate}fps (deviceId=${requestedDeviceId ?: "none"} facing=${requestedFacingMode ?: "none"})."
                    )
                    if (runningOnSimulator) {
                        Logger.i(
                            "IOSWebRtcDevice",
                            "Running on simulator. Using synthetic video fallback frames for streamId=$streamId instead of blank simulator camera output."
                        )
                        val fallbackSession = IOSSimulatorVideoFallbackSession(
                            videoSource = videoSource,
                            targetWidth = captureWidth,
                            targetHeight = captureHeight,
                            frameRate = captureFrameRate
                        )
                        fallbackSession.start()
                        activeSimulatorFallbackCapture?.stop()
                        activeSimulatorFallbackCapture = fallbackSession
                    } else {
                        Logger.w(
                            "IOSWebRtcDevice",
                            "Physical iOS device did not resolve camera format; video track will have no frames."
                        )
                    }
                }
            }

            IOSMediaStream(
                nativeStream = nativeStream,
                audioTrack = audioTrack,
                audioSource = audioSource,
                videoTrack = videoTrack,
                videoSource = videoSource,
                onStop = {
                    if (activeCameraCapture === cameraSession) {
                        activeCameraCapture = null
                    }
                    cameraSession?.stop()
                    activeSimulatorFallbackCapture?.stop()
                    activeSimulatorFallbackCapture = null
                }
            )
        }
    }

    override suspend fun enumerateDevices(): List<MediaDeviceInfo> {
        val devices = mutableListOf(
            MediaDeviceInfo(
                deviceId = "default_audio_input",
                kind = "audioinput",
                label = "Default Microphone",
                groupId = "audio"
            )
        )

        val videoInputs = RTCCameraVideoCapturer.captureDevices()
            .filterIsInstance<AVCaptureDevice>()
            .map { captureDevice ->
                MediaDeviceInfo(
                    deviceId = captureDevice.uniqueID,
                    kind = "videoinput",
                    label = captureDevice.localizedName.ifBlank { fallbackCameraLabel(captureDevice) },
                    groupId = resolveCameraGroupId(captureDevice)
                )
            }

        if (videoInputs.isEmpty()) {
            devices += listOf(
                MediaDeviceInfo(
                    deviceId = "front_camera",
                    kind = "videoinput",
                    label = "Front Camera",
                    groupId = "front"
                ),
                MediaDeviceInfo(
                    deviceId = "back_camera",
                    kind = "videoinput",
                    label = "Back Camera",
                    groupId = "back"
                )
            )
        } else {
            devices += videoInputs
        }

        devices += enumerateAudioOutputDevices()
        return devices
    }

    /**
     * Captures the current app screen for sharing using ReplayKit in-app capture.
     *
     * This provides a minimum viable iOS screen-share path without a broadcast
     * upload extension. Full-system capture would still require the broader
     * ReplayKit broadcast-extension flow.
     */
    override suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream {
        return withContext(Dispatchers.Main) {
            val recorder = RPScreenRecorder.sharedRecorder()
            if (!recorder.isAvailable()) {
                throw UnsupportedOperationException("ReplayKit screen capture is not available on this iOS device")
            }

            @Suppress("UNCHECKED_CAST")
            val videoConstraints = constraints["video"] as? Map<String, Any?> ?: emptyMap()
            val captureWidth = resolveIntConstraint(videoConstraints["width"], fallback = 1080)
            val captureHeight = resolveIntConstraint(videoConstraints["height"], fallback = 1920)
            val captureFrameRate = resolveFrameRate(videoConstraints["frameRate"], fallback = 15)

            activeScreenShare?.stop()

            val streamId = "ios_screen_${NSUUID().UUIDString}"
            val nativeStream = peerConnectionFactory.mediaStreamWithStreamId(streamId)
            val videoSource = peerConnectionFactory.videoSource().also {
                it.adaptOutputFormatToWidth(captureWidth, captureHeight, captureFrameRate)
            }
            val videoTrack = peerConnectionFactory.videoTrackWithSource(videoSource, "screen_$streamId")
            nativeStream.addVideoTrack(videoTrack)

            val capturer = RTCVideoCapturer(videoSource)
            val screenSession = IOSScreenCaptureSession(
                recorder = recorder,
                capturer = capturer,
                videoSource = videoSource,
                targetWidth = captureWidth,
                targetHeight = captureHeight,
                frameRate = captureFrameRate
            )
            screenSession.start()
            activeScreenShare = screenSession

            IOSMediaStream(
                nativeStream = nativeStream,
                audioTrack = null,
                audioSource = null,
                videoTrack = videoTrack,
                videoSource = videoSource,
                onStop = {
                    if (activeScreenShare === screenSession) {
                        activeScreenShare = null
                    }
                    screenSession.stop()
                }
            )
        }
    }

    override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
        Logger.i(
            "IOSWebRtcDevice",
            "createSendTransport requested keys=${params.keys.joinToString(",")}"
        )
        val nativeHandle = IosNativeMediasoupBridgeProvider.bridge.createSendTransport(params)
        Logger.i(
            "IOSWebRtcDevice",
            "createSendTransport nativeHandleId=${nativeHandle.id}"
        )
        return IosMediasoupTransport.createSend(nativeHandle)
    }

    override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
        Logger.i(
            "IOSWebRtcDevice",
            "createRecvTransport requested keys=${params.keys.joinToString(",")}"
        )
        val nativeHandle = IosNativeMediasoupBridgeProvider.bridge.createRecvTransport(params)
        Logger.i(
            "IOSWebRtcDevice",
            "createRecvTransport nativeHandleId=${nativeHandle.id}"
        )
        return IosMediasoupTransport.createRecv(nativeHandle)
    }

    override fun currentRtpCapabilities(): RtpCapabilities? =
        fetchNativeDeviceRtpCapabilities() ?: lastLoadedCapabilities

    private fun fetchNativeDeviceRtpCapabilities(): RtpCapabilities? {
        val loadableBridge = IosNativeMediasoupBridgeProvider.bridge as? IosNativeLoadableMediasoupBridge
            ?: return null
        val json = loadableBridge.currentRtpCapabilitiesJson()?.trim().orEmpty()
        if (json.isBlank() || json == "{}") {
            return null
        }

        return runCatching { parseRtpCapabilitiesBridgeJson(json) }
            .onFailure { error ->
                Logger.e(
                    "IOSWebRtcDevice",
                    "Failed to parse native RTP capabilities: ${error.message}"
                )
            }
            .getOrNull()
            ?.also { parsed ->
                lastLoadedCapabilities = parsed
            }
    }

    override suspend fun setAudioOutputDevice(deviceId: String): Boolean {
        return withContext(Dispatchers.Default) {
            runCatching {
                val rtcAudioSession = RTCAudioSession.sharedInstance()
                rtcAudioSession.lockForConfiguration()
                try {
                    val normalizedId = deviceId.lowercase()
                    when {
                        normalizedId == "audio_output_speaker" || normalizedId.contains("speaker") -> {
                            rtcAudioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
                            true
                        }

                        normalizedId == "audio_output_earpiece" ||
                            normalizedId.contains("earpiece") ||
                            normalizedId.contains("receiver") -> {
                            rtcAudioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                            true
                        }

                        normalizedId == "audio_output_default" ||
                            normalizedId == "audio_output_bluetooth" ||
                            normalizedId == "audio_output_wired" ||
                            normalizedId.contains("bluetooth") ||
                            normalizedId.contains("headphone") ||
                            normalizedId.contains("wired") -> {
                            rtcAudioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                            true
                        }

                        else -> false
                    }
                } finally {
                    rtcAudioSession.unlockForConfiguration()
                }
            }.getOrElse { error ->
                NSLog("MediaSFU - IOSWebRtcDevice: setAudioOutputDevice failed -> ${error.message}")
                false
            }
        }
    }

    override fun createVirtualVideoSource(width: Int, height: Int, frameRate: Int): VirtualVideoSource? {
        val sanitizedWidth = width.coerceAtLeast(16)
        val sanitizedHeight = height.coerceAtLeast(16)
        val sanitizedFrameRate = frameRate.coerceIn(1, 30)
        val streamId = "ios_virtual_${NSUUID().UUIDString}"
        val nativeStream = peerConnectionFactory.mediaStreamWithStreamId(streamId)
        val videoSource = peerConnectionFactory.videoSource().also {
            it.adaptOutputFormatToWidth(sanitizedWidth, sanitizedHeight, sanitizedFrameRate)
        }
        val videoTrack = peerConnectionFactory.videoTrackWithSource(videoSource, "virtual_$streamId")
        nativeStream.addVideoTrack(videoTrack)

        var virtualSource: IOSVirtualVideoSource? = null
        val mediaStream = IOSMediaStream(
            nativeStream = nativeStream,
            audioTrack = null,
            audioSource = null,
            videoTrack = videoTrack,
            videoSource = videoSource,
            onStop = {
                virtualSource?.stop()
                virtualSource?.release()
            }
        )

        return IOSVirtualVideoSource(
            stream = mediaStream,
            videoTrack = IOSMediaStreamTrack(videoTrack),
            nativeVideoSource = videoSource,
            width = sanitizedWidth,
            height = sanitizedHeight,
            frameRate = sanitizedFrameRate
        ).also { virtualSource = it }
    }

    internal fun setCameraFrameProcessor(processor: ((RTCVideoFrame) -> RTCVideoFrame)?) {
        activeCameraFrameProcessor = processor
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun captureWhiteboardStream(
        shapesProvider: () -> List<Any>,
        useImageBackgroundProvider: () -> Boolean,
        width: Int,
        height: Int,
        frameRate: Int
    ): MediaStream? {
        return withContext(Dispatchers.Default) {
            val virtualSource = createVirtualVideoSource(width, height, frameRate) as? IOSVirtualVideoSource
                ?: return@withContext null

            val whiteboardSession = IOSWhiteboardCaptureSession(
                virtualVideoSource = virtualSource,
                shapesProvider = {
                    (shapesProvider() as? List<WhiteboardShape>) ?: emptyList()
                },
                useImageBackgroundProvider = useImageBackgroundProvider,
                width = width,
                height = height,
                frameRate = frameRate
            )

            virtualSource.onRelease = { whiteboardSession.stop() }
            whiteboardSession.start()
            virtualSource.stream
        }
    }

    override fun close() {
        activeCameraCapture?.stop()
        activeCameraCapture = null
        activeSimulatorFallbackCapture?.stop()
        activeSimulatorFallbackCapture = null
        activeScreenShare?.stop()
        activeScreenShare = null
        runCatching { }
            .onFailure { NSLog("MediaSFU - IOSWebRtcDevice: close failed -> ${it.message}") }
    }

    private fun enumerateAudioOutputDevices(): List<MediaDeviceInfo> {
        return listOf(
            MediaDeviceInfo(
                deviceId = "audio_output_default",
                kind = "audiooutput",
                label = "System Default",
                groupId = "default"
            ),
            MediaDeviceInfo(
                deviceId = "audio_output_speaker",
                kind = "audiooutput",
                label = "Built-in Speaker",
                groupId = "builtin"
            ),
            MediaDeviceInfo(
                deviceId = "audio_output_earpiece",
                kind = "audiooutput",
                label = "Earpiece",
                groupId = "builtin"
            ),
            MediaDeviceInfo(
                deviceId = "audio_output_bluetooth",
                kind = "audiooutput",
                label = "Bluetooth Audio",
                groupId = "bluetooth"
            ),
            MediaDeviceInfo(
                deviceId = "audio_output_wired",
                kind = "audiooutput",
                label = "Wired Headphones",
                groupId = "wired"
            )
        )
    }

    private fun fallbackCameraLabel(device: AVCaptureDevice): String {
        return when (resolveCameraGroupId(device)) {
            "front" -> "Front Camera"
            "back" -> "Back Camera"
            else -> "Camera"
        }
    }

    private fun resolveCameraGroupId(device: AVCaptureDevice): String {
        val normalizedName = device.localizedName.lowercase()
        return when {
            normalizedName.contains("front") || normalizedName.contains("user") -> "front"
            normalizedName.contains("back") || normalizedName.contains("rear") || normalizedName.contains("environment") -> "back"
            else -> "video"
        }
    }

    private fun resolveStringConstraint(value: Any?): String? {
        return when (value) {
            is String -> value.takeIf { it.isNotBlank() }
            is Map<*, *> -> (value["exact"] as? String)?.takeIf { it.isNotBlank() }
                ?: (value["ideal"] as? String)?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun resolveFacingMode(value: Any?): String? {
        return resolveStringConstraint(value)?.lowercase()
    }

    private fun selectCaptureDevice(
        requestedDeviceId: String?,
        requestedFacingMode: String?
    ): AVCaptureDevice? {
        val devices = RTCCameraVideoCapturer.captureDevices().filterIsInstance<AVCaptureDevice>()
        if (devices.isEmpty()) {
            return null
        }

        requestedDeviceId?.let { desiredId ->
            devices.firstOrNull { device ->
                device.uniqueID == desiredId ||
                    device.localizedName.equals(desiredId, ignoreCase = true) ||
                    fallbackCameraLabel(device).equals(desiredId, ignoreCase = true)
            }?.let { return it }
        }

        requestedFacingMode?.let { facingMode ->
            devices.firstOrNull { deviceMatchesFacingMode(it, facingMode) }?.let { return it }
        }

        return devices.firstOrNull { deviceMatchesFacingMode(it, "user") }
            ?: devices.firstOrNull { deviceMatchesFacingMode(it, "environment") }
            ?: devices.firstOrNull()
    }

    private fun deviceMatchesFacingMode(device: AVCaptureDevice, facingMode: String): Boolean {
        val normalizedFacingMode = facingMode.lowercase()
        val normalizedName = device.localizedName.lowercase()
        return when (normalizedFacingMode) {
            "user", "front" -> normalizedName.contains("front") || normalizedName.contains("user")
            "environment", "back", "rear" -> normalizedName.contains("back") ||
                normalizedName.contains("rear") ||
                normalizedName.contains("environment")
            else -> false
        }
    }

    private fun selectCaptureFormat(
        device: AVCaptureDevice,
        targetWidth: Int,
        targetHeight: Int,
        targetFrameRate: Int
    ): AVCaptureDeviceFormat? {
        val formats = RTCCameraVideoCapturer.supportedFormatsForDevice(device)
            .filterIsInstance<AVCaptureDeviceFormat>()
        if (formats.isEmpty()) {
            return null
        }

        return formats.minWithOrNull(
            compareBy<AVCaptureDeviceFormat>(
                { !formatSupportsFrameRate(it, targetFrameRate) },
                { maxSupportedFrameRate(it) < 15 },
                { captureFormatMetrics(it, targetWidth, targetHeight).belowTargetPenalty },
                { captureFormatMetrics(it, targetWidth, targetHeight).aspectRatioDelta },
                { captureFormatMetrics(it, targetWidth, targetHeight).areaDelta },
                { abs(maxSupportedFrameRate(it) - targetFrameRate) },
                { -maxSupportedFrameRate(it) }
            )
        )
    }

    private fun formatSupportsFrameRate(format: AVCaptureDeviceFormat, targetFrameRate: Int): Boolean {
        return format.videoSupportedFrameRateRanges
            .filterIsInstance<AVFrameRateRange>()
            .any { range -> targetFrameRate.toDouble() <= range.maxFrameRate + 0.5 }
    }

    private fun maxSupportedFrameRate(format: AVCaptureDeviceFormat): Int {
        return format.videoSupportedFrameRateRanges
            .filterIsInstance<AVFrameRateRange>()
            .maxOfOrNull { it.maxFrameRate.toInt() }
            ?: 0
    }

    private fun captureFormatMetrics(
        format: AVCaptureDeviceFormat,
        targetWidth: Int,
        targetHeight: Int
    ): CaptureFormatMetrics {
        val dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
        val nativeWidth = dimensions.useContents { width.toInt() }
        val nativeHeight = dimensions.useContents { height.toInt() }
        val direct = evaluateCaptureFormat(
            width = nativeWidth,
            height = nativeHeight,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )
        val rotated = evaluateCaptureFormat(
            width = nativeHeight,
            height = nativeWidth,
            targetWidth = targetWidth,
            targetHeight = targetHeight
        )

        return if (compareCaptureFormatMetrics(direct, rotated) <= 0) direct else rotated
    }

    private fun evaluateCaptureFormat(
        width: Int,
        height: Int,
        targetWidth: Int,
        targetHeight: Int
    ): CaptureFormatMetrics {
        val safeWidth = width.coerceAtLeast(1)
        val safeHeight = height.coerceAtLeast(1)
        val belowTargetPenalty =
            (targetWidth - safeWidth).coerceAtLeast(0) +
                (targetHeight - safeHeight).coerceAtLeast(0)
        val aspectRatioDelta = abs(
            safeWidth.toLong() * targetHeight.toLong() -
                safeHeight.toLong() * targetWidth.toLong()
        )
        val areaDelta = abs(
            safeWidth.toLong() * safeHeight.toLong() -
                targetWidth.toLong() * targetHeight.toLong()
        )

        return CaptureFormatMetrics(
            belowTargetPenalty = belowTargetPenalty,
            aspectRatioDelta = aspectRatioDelta,
            areaDelta = areaDelta
        )
    }

    private fun compareCaptureFormatMetrics(
        left: CaptureFormatMetrics,
        right: CaptureFormatMetrics
    ): Int {
        return when {
            left.belowTargetPenalty != right.belowTargetPenalty ->
                left.belowTargetPenalty.compareTo(right.belowTargetPenalty)

            left.aspectRatioDelta != right.aspectRatioDelta ->
                left.aspectRatioDelta.compareTo(right.aspectRatioDelta)

            else -> left.areaDelta.compareTo(right.areaDelta)
        }
    }

    private data class CaptureFormatMetrics(
        val belowTargetPenalty: Int,
        val aspectRatioDelta: Long,
        val areaDelta: Long
    )

    private fun resolveIntConstraint(value: Any?, fallback: Int): Int {
        return when (value) {
            is Number -> value.toInt()
            is Map<*, *> -> (value["exact"] as? Number)?.toInt()
                ?: (value["ideal"] as? Number)?.toInt()
                ?: (value["max"] as? Number)?.toInt()
                ?: fallback
            else -> fallback
        }
    }

    private fun resolveFrameRate(value: Any?, fallback: Int): Int {
        return resolveIntConstraint(value, fallback).coerceIn(1, 30)
    }

    private fun isRunningOnSimulator(): Boolean {
        val env = NSProcessInfo.processInfo.environment
        val simulatorDeviceName = env["SIMULATOR_DEVICE_NAME"] as? String
        return !simulatorDeviceName.isNullOrBlank()
    }

    private inner class IOSVirtualVideoSource(
        override val stream: MediaStream,
        override val videoTrack: MediaStreamTrack,
        private val nativeVideoSource: RTCVideoSource,
        private val width: Int,
        private val height: Int,
        private val frameRate: Int
    ) : VirtualVideoSource {
        private val capturer = RTCVideoCapturer(nativeVideoSource)
        private var started = false
        var onRelease: (() -> Unit)? = null

        override fun onFrame(bitmap: Any, timestampNs: Long, rotation: Int) {
            if (!started) return

            // Fast path: VB processor already produced an RTCVideoFrame — push directly.
            if (bitmap is RTCVideoFrame) {
                nativeVideoSource.capturer(capturer, didCaptureVideoFrame = bitmap)
                return
            }

            bitmap.toUIImage()?.toRtcVideoFrame(
                targetWidth = width,
                targetHeight = height,
                timestampNs = timestampNs,
                rotation = rotation
            )?.let { frame ->
                nativeVideoSource.capturer(capturer, didCaptureVideoFrame = frame)
            }
        }

        override fun start() {
            nativeVideoSource.adaptOutputFormatToWidth(width, height, frameRate)
            started = true
        }

        override fun stop() {
            started = false
        }

        override fun release() {
            started = false
            onRelease?.invoke()
            onRelease = null
        }
    }

    private inner class IOSWhiteboardCaptureSession(
        private val virtualVideoSource: IOSVirtualVideoSource,
        private val shapesProvider: () -> List<WhiteboardShape>,
        private val useImageBackgroundProvider: () -> Boolean,
        private val width: Int,
        private val height: Int,
        private val frameRate: Int
    ) {
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var captureJob: Job? = null

        fun start() {
            if (captureJob != null) return

            val safeWidth = width.coerceAtLeast(16)
            val safeHeight = height.coerceAtLeast(16)
            val safeFrameRate = frameRate.coerceIn(1, 15)
            val frameDelayMs = (1000L / safeFrameRate).coerceAtLeast(33L)

            virtualVideoSource.start()
            captureJob = scope.launch {
                while (isActive) {
                    runCatching {
                        renderWhiteboardFramePng(
                            shapes = shapesProvider(),
                            canvasWidth = safeWidth,
                            canvasHeight = safeHeight,
                            useImageBackground = useImageBackgroundProvider()
                        )
                    }.onSuccess { pngBytes ->
                        virtualVideoSource.onFrame(
                            bitmap = pngBytes,
                            timestampNs = currentTimestampNs(),
                            rotation = 0
                        )
                    }.onFailure { error ->
                        NSLog("MediaSFU - IOSWebRtcDevice: whiteboard frame render failed -> ${error.message}")
                    }

                    delay(frameDelayMs)
                }
            }
        }

        fun stop() {
            captureJob?.cancel()
            captureJob = null
            scope.cancel()
            virtualVideoSource.stop()
        }
    }

    private inner class IOSSimulatorVideoFallbackSession(
        private val videoSource: RTCVideoSource,
        private val targetWidth: Int,
        private val targetHeight: Int,
        private val frameRate: Int
    ) {
        private val capturer = RTCVideoCapturer(videoSource)
        private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        private var captureJob: Job? = null

        fun start() {
            if (captureJob != null) return
            videoSource.adaptOutputFormatToWidth(targetWidth, targetHeight, frameRate)

            val safeFrameRate = frameRate.coerceIn(8, 30)
            val frameDelayMs = (1000L / safeFrameRate).coerceAtLeast(33L)
            val safeWidth = targetWidth.coerceAtLeast(16)
            val safeHeight = targetHeight.coerceAtLeast(16)

            val seedFrameOk = createPixelBuffer(safeWidth, safeHeight)?.let { pixelBuffer ->
                try {
                    paintSimulatorFallbackPixelBuffer(pixelBuffer, frameIndex = 0)
                } finally {
                    CFRelease(pixelBuffer)
                }
            } ?: false
            if (!seedFrameOk) {
                Logger.w(
                    "IOSWebRtcDevice",
                    "Simulator fallback failed to allocate/paint seed frame for ${safeWidth}x${safeHeight}."
                )
                return
            }

            Logger.i(
                "IOSWebRtcDevice",
                "Simulator fallback emitting BGRA frames ${safeWidth}x${safeHeight}@${safeFrameRate}fps"
            )

            captureJob = scope.launch {
                var frameIndex = 0
                var emittedFrames = 0
                while (isActive) {
                    if (submitSimulatorFallbackFrame(safeWidth, safeHeight, frameIndex)) {
                        emittedFrames += 1

                        if (emittedFrames == 1 || emittedFrames % (safeFrameRate * 5) == 0) {
                            Logger.i(
                                "IOSWebRtcDevice",
                                "Simulator fallback frame heartbeat frames=$emittedFrames processorActive=${activeCameraFrameProcessor != null}"
                            )
                        }
                    } else if (frameIndex == 0) {
                        Logger.w("IOSWebRtcDevice", "Simulator fallback paint failed during capture loop")
                    }

                    frameIndex = (frameIndex + 1) % (safeFrameRate * 8)
                    delay(frameDelayMs)
                }
            }
        }

        fun stop() {
            captureJob?.cancel()
            captureJob = null
            scope.cancel()
        }

        private fun submitSimulatorFallbackFrame(width: Int, height: Int, frameIndex: Int): Boolean {
            val pixelBuffer = createPixelBuffer(width, height) ?: return false
            return try {
                if (!paintSimulatorFallbackPixelBuffer(pixelBuffer, frameIndex)) {
                    false
                } else {
                    val fallbackFrame = RTCVideoFrame(
                        buffer = RTCCVPixelBuffer(pixelBuffer),
                        rotation = 0,
                        timeStampNs = currentTimestampNs()
                    )
                    val processedFrame = activeCameraFrameProcessor?.invoke(fallbackFrame) ?: fallbackFrame
                    videoSource.capturer(capturer, didCaptureVideoFrame = processedFrame)
                    true
                }
            } finally {
                CFRelease(pixelBuffer)
            }
        }
    }

    private fun Any?.toUIImage(): UIImage? = when (this) {
        is UIImage -> this
        is NSData -> UIImage(data = this)
        is ByteArray -> UIImage(data = toNSData())
        else -> null
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }

    private fun currentTimestampNs(): Long =
        (CACurrentMediaTime() * 1_000_000_000.0).toLong()

    private fun UIImage.toRtcVideoFrame(
        targetWidth: Int,
        targetHeight: Int,
        timestampNs: Long,
        rotation: Int
    ): RTCVideoFrame? {
        val cgImage = CGImage ?: return null
        val pixelBuffer = createPixelBuffer(targetWidth, targetHeight) ?: return null

        return try {
            if (!drawImageIntoPixelBuffer(cgImage, pixelBuffer, targetWidth, targetHeight)) {
                null
            } else {
                RTCVideoFrame(
                    buffer = RTCCVPixelBuffer(pixelBuffer),
                    rotation = normalizeVideoRotation(rotation),
                    timeStampNs = timestampNs
                )
            }
        } finally {
            CFRelease(pixelBuffer)
        }
    }

    private fun normalizeVideoRotation(rotation: Int): Long =
        when (rotation) {
            90, 180, 270 -> rotation.toLong()
            else -> 0L
        }

    private fun createPixelBuffer(width: Int, height: Int): CVPixelBufferRef? = withCompatiblePixelBufferAttributes { attributes ->
        memScoped {
            val pixelBufferRef = alloc<CPointerVar<__CVBuffer>>()
            val status = CVPixelBufferCreate(
                null,
                width.toULong(),
                height.toULong(),
                kCVPixelFormatType_32BGRA.toUInt(),
                attributes,
                pixelBufferRef.ptr
            )
            if (status == kCVReturnSuccess) pixelBufferRef.value else null
        }
    }

    private inline fun <T> withCompatiblePixelBufferAttributes(block: (CFDictionaryRef?) -> T): T {
        val attributes = CFDictionaryCreateMutable(null, 0, null, null) ?: return block(null)

        fun setAttribute(key: CValuesRef<*>?, value: CValuesRef<*>?) {
            if (key != null && value != null) {
                CFDictionarySetValue(attributes, key, value)
            }
        }

        setAttribute(kCVPixelBufferCGImageCompatibilityKey, kCFBooleanTrue)
        setAttribute(kCVPixelBufferCGBitmapContextCompatibilityKey, kCFBooleanTrue)
        setAttribute(kCVPixelBufferMetalCompatibilityKey, kCFBooleanTrue)
        setAttribute(kCVPixelBufferOpenGLESCompatibilityKey, kCFBooleanTrue)

        return try {
            block(attributes)
        } finally {
            CFRelease(attributes)
        }
    }

    private fun paintSimulatorFallbackPixelBuffer(pixelBuffer: CVPixelBufferRef, frameIndex: Int): Boolean {
        CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
        return try {
            val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return false
            val width = CVPixelBufferGetWidth(pixelBuffer).toInt()
            val height = CVPixelBufferGetHeight(pixelBuffer).toInt()
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
            val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return false
            val bitmapInfo = kCGBitmapByteOrder32Little or 2u
            val context = CGBitmapContextCreate(
                data = baseAddress,
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = bitmapInfo
            ) ?: return false

            try {
                val framePhase = (frameIndex % 120).toDouble() / 120.0
                val w = width.toDouble()
                val h = height.toDouble()
                CGContextSetRGBFillColor(context, 0.04, 0.08, 0.14, 1.0)
                CGContextFillRect(context, CGRectMake(0.0, 0.0, w, h))

                // Keep the simulator feed obviously synthetic, but close to the
                // "image or no image" UX the product actually uses instead of
                // the old test-pattern bars/white block.
                val glowSize = w * 0.64
                val glowCenterX = (w * 0.30) + (w * 0.08 * kotlin.math.sin(framePhase * 2.0 * kotlin.math.PI))
                val glowCenterY = (h * 0.22) + (h * 0.03 * kotlin.math.cos(framePhase * 2.0 * kotlin.math.PI))
                CGContextSetRGBFillColor(context, 0.10, 0.23, 0.42, 1.0)
                CGContextFillRect(
                    context,
                    CGRectMake(
                        glowCenterX - (glowSize / 2.0),
                        glowCenterY - (glowSize / 2.0),
                        glowSize,
                        glowSize
                    )
                )

                val accentWidth = w * 0.18
                val accentX = (w * 0.62) + (w * 0.06 * kotlin.math.sin(framePhase * 2.0 * kotlin.math.PI))
                CGContextSetRGBFillColor(context, 0.17, 0.43, 0.86, 1.0)
                CGContextFillRect(context, CGRectMake(accentX, h * 0.12, accentWidth, h * 0.76))

                val headDiameter = w.coerceAtMost(h) * 0.22
                CGContextSetRGBFillColor(context, 0.39, 0.72, 0.96, 0.98)
                CGContextFillRect(
                    context,
                    CGRectMake(
                        (w - headDiameter) / 2.0,
                        h * 0.24,
                        headDiameter,
                        headDiameter
                    )
                )

                val shoulderWidth = w * 0.46
                val shoulderHeight = h * 0.28
                CGContextSetRGBFillColor(context, 0.19, 0.49, 0.88, 0.98)
                CGContextFillRect(
                    context,
                    CGRectMake(
                        (w - shoulderWidth) / 2.0,
                        h * 0.52,
                        shoulderWidth,
                        shoulderHeight
                    )
                )

                val statusDotSize = w.coerceAtLeast(h) * 0.035
                val pulse = 0.72 + (0.28 * kotlin.math.sin(framePhase * 2.0 * kotlin.math.PI).coerceAtLeast(-1.0))
                CGContextSetRGBFillColor(context, 0.42, 0.93, 0.62, pulse)
                CGContextFillRect(
                    context,
                    CGRectMake(
                        w * 0.88,
                        h * 0.10,
                        statusDotSize,
                        statusDotSize
                    )
                )
            } finally {
                CGContextRelease(context)
            }
            true
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
        }
    }

    private fun drawImageIntoPixelBuffer(
        cgImage: CGImageRef?,
        pixelBuffer: CVPixelBufferRef,
        width: Int,
        height: Int
    ): Boolean {
        if (cgImage == null) return false

        CVPixelBufferLockBaseAddress(pixelBuffer, 0u)
        return try {
            val baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) ?: return false
            val bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
            val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return false
            val bitmapInfo = kCGBitmapByteOrder32Little or 2u
            val context = CGBitmapContextCreate(
                data = baseAddress,
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = bytesPerRow.toULong(),
                space = colorSpace,
                bitmapInfo = bitmapInfo
            ) ?: run {
                CFRelease(colorSpace)
                return false
            }

            try {
                CGContextClearRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
                CGContextTranslateCTM(context, 0.0, height.toDouble())
                CGContextScaleCTM(context, 1.0, -1.0)
                CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
                true
            } finally {
                CGContextRelease(context)
                CFRelease(colorSpace)
            }
        } finally {
            CVPixelBufferUnlockBaseAddress(pixelBuffer, 0u)
        }
    }

    private class IOSCameraCaptureSession(
        val capturer: RTCCameraVideoCapturer,
        @Suppress("unused")
        val frameDelegate: IOSCameraFrameDelegate?,
        val videoSource: RTCVideoSource,
        val device: AVCaptureDevice,
        val format: AVCaptureDeviceFormat,
        val targetWidth: Int,
        val targetHeight: Int,
        val frameRate: Int
    ) {
        var started = false

        fun start() {
            if (started) return

            val selectedFrameRate = format.videoSupportedFrameRateRanges
                .filterIsInstance<AVFrameRateRange>()
                .firstOrNull { range -> frameRate.toDouble() <= range.maxFrameRate + 0.5 }
                ?.let { frameRate }
                ?: format.videoSupportedFrameRateRanges
                    .filterIsInstance<AVFrameRateRange>()
                    .maxOfOrNull { range -> range.maxFrameRate.toInt() }
                    ?.coerceAtLeast(1)
                ?: frameRate

            videoSource.adaptOutputFormatToWidth(targetWidth, targetHeight, selectedFrameRate)
            Logger.i(
                "IOSWebRtcDevice",
                "Starting camera capture device=${device.localizedName} requested=${targetWidth}x${targetHeight}@${frameRate}fps selected=${formatSummary(format)}@${selectedFrameRate}fps"
            )
            capturer.startCaptureWithDevice(device, format, selectedFrameRate.toLong())
            
            // Set AVCaptureConnection.videoOrientation to Portrait
            val session = capturer.captureSession
            if (session != null) {
                val outputsList = session.outputs as? List<*>
                if (outputsList != null) {
                    for (output in outputsList) {
                        val dataOutput = output as? platform.AVFoundation.AVCaptureVideoDataOutput
                        if (dataOutput != null) {
                            val connectionsList = dataOutput.connections as? List<*>
                            if (connectionsList != null) {
                                for (connection in connectionsList) {
                                    val conn = connection as? platform.AVFoundation.AVCaptureConnection
                                    if (conn != null && conn.isVideoOrientationSupported()) {
                                        conn.videoOrientation = platform.AVFoundation.AVCaptureVideoOrientationPortrait
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            started = true
        }

        fun stop() {
            if (!started) return
            Logger.i("IOSWebRtcDevice", "Stopping camera capture device=${device.localizedName}")
            capturer.stopCapture()
            started = false
        }

        private fun formatSummary(format: AVCaptureDeviceFormat): String {
            val dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription)
            val width = dimensions.useContents { this.width.toInt() }
            val height = dimensions.useContents { this.height.toInt() }
            return "${width}x${height}"
        }
    }

    private class IOSCameraFrameDelegate(
        private val videoSource: RTCVideoSource,
        private val frameProcessorProvider: () -> ((RTCVideoFrame) -> RTCVideoFrame)?
    ) : NSObject(), RTCVideoCapturerDelegateProtocol {
        private var firstFrameAtMs: Double? = null
        private var lastFrameAtMs: Double? = null
        private var lastHeartbeatAtMs: Double? = null
        private var framesSinceHeartbeat: Int = 0

        override fun capturer(capturer: RTCVideoCapturer, didCaptureVideoFrame: RTCVideoFrame) {
            val nowMs = CACurrentMediaTime() * 1000.0
            val previousFrameAtMs = lastFrameAtMs
            val frameProcessor = frameProcessorProvider()
            if (firstFrameAtMs == null) {
                firstFrameAtMs = nowMs
                Logger.i("IOSWebRtcDevice", "First camera frame captured")
            }

            if (previousFrameAtMs != null) {
                val gapMs = nowMs - previousFrameAtMs
                if (gapMs >= 2_000.0) {
                    Logger.w(
                        "IOSWebRtcDevice",
                        "Camera capture gap detected gapMs=${gapMs.toLong()} processed=${frameProcessor != null}"
                    )
                }
            }

            val processedFrame = if (frameProcessor != null) {
                frameProcessor.invoke(didCaptureVideoFrame)
            } else {
                RTCVideoFrame(
                    buffer = didCaptureVideoFrame.buffer,
                    rotation = cocoapods.WebRTC.RTCVideoRotation_0,
                    timeStampNs = didCaptureVideoFrame.timeStampNs
                )
            }
            framesSinceHeartbeat += 1
            val lastHeartbeat = lastHeartbeatAtMs
            if (lastHeartbeat == null) {
                lastHeartbeatAtMs = nowMs
                framesSinceHeartbeat = 0
            } else if (nowMs - lastHeartbeat >= 5_000.0) {
                val elapsedSinceFirst = ((nowMs - (firstFrameAtMs ?: nowMs)) / 1000.0).coerceAtLeast(0.001)
                val avgFps = framesSinceHeartbeat / ((nowMs - lastHeartbeat).coerceAtLeast(1.0) / 1000.0)
                Logger.i(
                    "IOSWebRtcDevice",
                    "Camera capture heartbeat frames=$framesSinceHeartbeat avgFps=${avgFps.toInt()} processorActive=${frameProcessor != null} uptimeSec=${elapsedSinceFirst.toInt()}"
                )
                framesSinceHeartbeat = 0
                lastHeartbeatAtMs = nowMs
            }

            lastFrameAtMs = nowMs
            videoSource.capturer(capturer, didCaptureVideoFrame = processedFrame)
        }
    }

    private class IOSMediaStream(
        private val nativeStream: RTCMediaStream,
        private val audioTrack: RTCAudioTrack?,
        private val audioSource: RTCAudioSource?,
        private val videoTrack: RTCVideoTrack?,
        private val videoSource: RTCVideoSource?,
        private val onStop: (() -> Unit)? = null
    ) : MediaStream {

        private val audioWrapper = audioTrack?.let { IOSMediaStreamTrack(it) }
        private val videoWrapper = videoTrack?.let { IOSMediaStreamTrack(it) }

        // Explicit mutable lists so addTrack/removeTrack/getAudioTracks are
        // consistent without relying on Kotlin/Native NSArray generic bridging
        // (filterIsInstance<RTCAudioTrack>() on NSArray<RTCAudioTrack *> is
        //  unreliable when the generic type is erased at the ObjC boundary).
        private val _audioTracks: MutableList<IOSMediaStreamTrack> =
            mutableListOf<IOSMediaStreamTrack>().also { list ->
                audioWrapper?.let { list.add(it) }
            }
        private val _videoTracks: MutableList<IOSMediaStreamTrack> =
            mutableListOf<IOSMediaStreamTrack>().also { list ->
                videoWrapper?.let { list.add(it) }
            }

        override val id: String = nativeStream.streamId

        override val active: Boolean
            get() = (_audioTracks + _videoTracks).any { it.isLive }

        override fun getTracks(): List<MediaStreamTrack> = getAudioTracks() + getVideoTracks()

        override fun getAudioTracks(): List<MediaStreamTrack> = _audioTracks.toList()

        override fun getVideoTracks(): List<MediaStreamTrack> = _videoTracks.toList()

        override fun addTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> {
                        nativeStream.addAudioTrack(native)
                        if (_audioTracks.none { it.nativeTrack === native }) _audioTracks.add(track)
                    }
                    is RTCVideoTrack -> {
                        nativeStream.addVideoTrack(native)
                        if (_videoTracks.none { it.nativeTrack === native }) _videoTracks.add(track)
                    }
                }
            }
        }

        override fun removeTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> {
                        nativeStream.removeAudioTrack(native)
                        _audioTracks.removeAll { it.nativeTrack === native }
                    }
                    is RTCVideoTrack -> {
                        nativeStream.removeVideoTrack(native)
                        _videoTracks.removeAll { it.nativeTrack === native }
                    }
                }
            }
        }

        override fun stop() {
            audioWrapper?.stop()
            videoWrapper?.stop()
            onStop?.invoke()
            audioSource?.let { _ -> }
            videoSource?.let { _ -> }
        }
    }

    private class IOSMediaStreamTrack(
        val nativeTrack: RTCMediaStreamTrack
    ) : MediaStreamTrack {

        override val id: String
            get() = nativeTrack.trackId

        override val kind: String
            get() = when (nativeTrack) {
                is RTCAudioTrack -> "audio"
                is RTCVideoTrack -> "video"
                else -> nativeTrack.kind
            }

        override val enabled: Boolean
            get() = nativeTrack.isEnabled

        val isLive: Boolean
            get() = nativeTrack.readyState == RTCMediaStreamTrackState.RTCMediaStreamTrackStateLive

        override fun setEnabled(enabled: Boolean) {
            nativeTrack.isEnabled = enabled
        }

        override fun stop() {
            nativeTrack.isEnabled = false
        }

        override fun asPlatformNativeTrack(): Any = nativeTrack
    }

    private class IOSScreenCaptureSession(
        private val recorder: RPScreenRecorder,
        private val capturer: RTCVideoCapturer,
        private val videoSource: RTCVideoSource,
        private val targetWidth: Int,
        private val targetHeight: Int,
        private val frameRate: Int
    ) {
        private var started = false

        fun start() {
            if (started) return
            videoSource.adaptOutputFormatToWidth(targetWidth, targetHeight, frameRate)
            recorder.startCaptureWithHandler(
                captureHandler = { sampleBuffer, bufferType, error ->
                    if (error != null || sampleBuffer == null) {
                        return@startCaptureWithHandler
                    }
                    if (bufferType != RPSampleBufferTypeVideo) {
                        return@startCaptureWithHandler
                    }

                    val pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) ?: return@startCaptureWithHandler
                    val timeStampNs = (CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) * 1_000_000_000.0).toLong()
                    val rtcPixelBuffer = RTCCVPixelBuffer(pixelBuffer)
                    val frame = RTCVideoFrame(
                        buffer = rtcPixelBuffer,
                        rotation = 0,
                        timeStampNs = timeStampNs
                    )
                    videoSource.capturer(capturer, didCaptureVideoFrame = frame)
                },
                completionHandler = { error ->
                    if (error != null) {
                        NSLog("MediaSFU - IOSWebRtcDevice: ReplayKit start failed -> ${error.localizedDescription}")
                    }
                }
            )
            started = true
        }

        fun stop() {
            if (!started) return
            recorder.stopCaptureWithHandler { error ->
                if (error != null) {
                    NSLog("MediaSFU - IOSWebRtcDevice: ReplayKit stop failed -> ${error.localizedDescription}")
                }
            }
            started = false
        }
    }
}