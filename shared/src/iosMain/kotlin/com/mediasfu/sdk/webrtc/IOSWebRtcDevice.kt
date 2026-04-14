package com.mediasfu.sdk.webrtc

import cnames.structs.__CVBuffer
import cocoapods.WebRTC.RTCCameraVideoCapturer
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.ui.components.whiteboard.renderWhiteboardFramePng
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeVoiceChat
import platform.AVFAudio.AVAudioSessionPortOverrideNone
import platform.AVFAudio.AVAudioSessionPortOverrideSpeaker
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceFormat
import platform.AVFoundation.AVFrameRateRange
import cocoapods.WebRTC.RTCAudioSource
import cocoapods.WebRTC.RTCAudioTrack
import cocoapods.WebRTC.RTCCVPixelBuffer
import cocoapods.WebRTC.RTCMediaStream
import cocoapods.WebRTC.RTCMediaStreamTrack
import cocoapods.WebRTC.RTCMediaStreamTrackState
import cocoapods.WebRTC.RTCPeerConnectionFactory
import cocoapods.WebRTC.RTCVideoCapturer
import cocoapods.WebRTC.RTCVideoFrame
import cocoapods.WebRTC.RTCVideoSource
import cocoapods.WebRTC.RTCVideoTrack
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
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
import platform.CoreFoundation.CFRelease
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGContextClearRect
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Little
import platform.CoreMedia.CMSampleBufferGetImageBuffer
import platform.CoreMedia.CMSampleBufferGetPresentationTimeStamp
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSLog
import platform.Foundation.NSData
import platform.Foundation.NSUUID
import platform.Foundation.dataWithBytes
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetBaseAddress
import platform.CoreVideo.CVPixelBufferGetBytesPerRow
import platform.CoreVideo.CVPixelBufferLockBaseAddress
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.CVPixelBufferRefVar
import platform.CoreVideo.CVPixelBufferUnlockBaseAddress
import platform.CoreVideo.kCVPixelBufferLock_ReadOnly
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVReturnSuccess
import platform.QuartzCore.CACurrentMediaTime
import platform.ReplayKit.RPSampleBufferTypeVideo
import platform.ReplayKit.RPScreenRecorder
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

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
    private var activeScreenShare: IOSScreenCaptureSession? = null

    companion object {
        private val instance: IOSWebRtcDevice by lazy { IOSWebRtcDevice() }

        fun getInstance(): IOSWebRtcDevice = instance
    }

    override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
        return runCatching {
            lastLoadedCapabilities = rtpCapabilities
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

            val streamId = "ios_stream_${NSUUID().UUIDString}"
            val nativeStream = peerConnectionFactory.mediaStreamWithStreamId(streamId)

            var audioSource: RTCAudioSource? = null
            var audioTrack: RTCAudioTrack? = null
            if (audioEnabled) {
                audioSource = peerConnectionFactory.audioSourceWithConstraints(null)
                audioTrack = peerConnectionFactory.audioTrackWithSource(audioSource, "audio_$streamId")
                nativeStream.addAudioTrack(audioTrack)
            }

            var videoSource: RTCVideoSource? = null
            var videoTrack: RTCVideoTrack? = null
            var cameraSession: IOSCameraCaptureSession? = null
            if (videoEnabled) {
                videoSource = peerConnectionFactory.videoSource()
                val captureWidth = resolveIntConstraint(videoConstraints["width"], fallback = 1280)
                val captureHeight = resolveIntConstraint(videoConstraints["height"], fallback = 720)
                val captureFrameRate = resolveFrameRate(videoConstraints["frameRate"], fallback = 30)
                val requestedDeviceId = resolveStringConstraint(videoConstraints["deviceId"])
                val requestedFacingMode = resolveFacingMode(videoConstraints["facingMode"])
                videoSource.adaptOutputFormatToWidth(captureWidth, captureHeight, captureFrameRate)
                videoTrack = peerConnectionFactory.videoTrackWithSource(videoSource, "video_$streamId")
                nativeStream.addVideoTrack(videoTrack)

                val captureDevice = selectCaptureDevice(
                    requestedDeviceId = requestedDeviceId,
                    requestedFacingMode = requestedFacingMode
                )
                val captureFormat = captureDevice?.let {
                    selectCaptureFormat(
                        device = it,
                        targetWidth = captureWidth,
                        targetHeight = captureHeight,
                        targetFrameRate = captureFrameRate
                    )
                }

                if (captureDevice != null && captureFormat != null) {
                    activeCameraCapture?.stop()
                    val capturer = RTCCameraVideoCapturer(videoSource)
                    cameraSession = IOSCameraCaptureSession(
                        capturer = capturer,
                        videoSource = videoSource,
                        device = captureDevice,
                        format = captureFormat,
                        targetWidth = captureWidth,
                        targetHeight = captureHeight,
                        frameRate = captureFrameRate
                    )
                    cameraSession.start()
                    activeCameraCapture = cameraSession
                } else {
                    NSLog("MediaSFU - IOSWebRtcDevice: Unable to resolve an iOS camera device/format for requested constraints")
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
        val nativeHandle = IosNativeMediasoupBridgeProvider.bridge.createSendTransport(params)
        return IosMediasoupTransport.createSend(nativeHandle)
    }

    override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
        val nativeHandle = IosNativeMediasoupBridgeProvider.bridge.createRecvTransport(params)
        return IosMediasoupTransport.createRecv(nativeHandle)
    }

    override fun currentRtpCapabilities(): RtpCapabilities? = lastLoadedCapabilities

    override suspend fun setAudioOutputDevice(deviceId: String): Boolean {
        return withContext(Dispatchers.Main) {
            runCatching {
                val audioSession = AVAudioSession.sharedInstance()
                configureCallAudioSession(audioSession)

                val normalizedId = deviceId.lowercase()
                when {
                    normalizedId == "audio_output_speaker" || normalizedId.contains("speaker") -> {
                        audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideSpeaker, error = null)
                        true
                    }

                    normalizedId == "audio_output_earpiece" ||
                        normalizedId.contains("earpiece") ||
                        normalizedId.contains("receiver") -> {
                        audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                        true
                    }

                    normalizedId == "audio_output_default" ||
                        normalizedId == "audio_output_bluetooth" ||
                        normalizedId == "audio_output_wired" ||
                        normalizedId.contains("bluetooth") ||
                        normalizedId.contains("headphone") ||
                        normalizedId.contains("wired") -> {
                        audioSession.overrideOutputAudioPort(AVAudioSessionPortOverrideNone, error = null)
                        true
                    }

                    else -> false
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
        activeScreenShare?.stop()
        activeScreenShare = null
        runCatching { }
            .onFailure { NSLog("MediaSFU - IOSWebRtcDevice: close failed -> ${it.message}") }
    }

    private fun configureCallAudioSession(audioSession: AVAudioSession) {
        audioSession.setCategory(AVAudioSessionCategoryPlayAndRecord, error = null)
        audioSession.setMode(AVAudioSessionModeVoiceChat, error = null)
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

        return formats.firstOrNull { format ->
            format.videoSupportedFrameRateRanges
                .filterIsInstance<AVFrameRateRange>()
                .any { range -> targetFrameRate.toDouble() <= range.maxFrameRate + 0.5 }
        } ?: formats.firstOrNull()
    }

    private fun resolveIntConstraint(value: Any?, fallback: Int): Int {
        return when (value) {
            is Number -> value.toInt()
            is Map<*, *> -> (value["ideal"] as? Number)?.toInt()
                ?: (value["max"] as? Number)?.toInt()
                ?: fallback
            else -> fallback
        }
    }

    private fun resolveFrameRate(value: Any?, fallback: Int): Int {
        return resolveIntConstraint(value, fallback).coerceIn(1, 30)
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

    private fun createPixelBuffer(width: Int, height: Int): CVPixelBufferRef? = memScoped {
        val pixelBufferRef = alloc<CPointerVar<__CVBuffer>>()
        val status = CVPixelBufferCreate(
            null,
            width.toULong(),
            height.toULong(),
            kCVPixelFormatType_32BGRA.toUInt(),
            null,
            pixelBufferRef.ptr
        )
        if (status == kCVReturnSuccess) pixelBufferRef.value else null
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
        private val capturer: RTCCameraVideoCapturer,
        private val videoSource: RTCVideoSource,
        private val device: AVCaptureDevice,
        private val format: AVCaptureDeviceFormat,
        private val targetWidth: Int,
        private val targetHeight: Int,
        private val frameRate: Int
    ) {
        private var started = false

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
            capturer.startCaptureWithDevice(device, format, selectedFrameRate.toLong())
            started = true
        }

        fun stop() {
            if (!started) return
            capturer.stopCapture()
            started = false
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

        override val id: String = nativeStream.streamId

        override val active: Boolean
            get() = listOfNotNull(audioWrapper, videoWrapper).any { it.isLive }

        override fun getTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper, videoWrapper)

        override fun getAudioTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper)

        override fun getVideoTracks(): List<MediaStreamTrack> = listOfNotNull(videoWrapper)

        override fun addTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> nativeStream.addAudioTrack(native)
                    is RTCVideoTrack -> nativeStream.addVideoTrack(native)
                }
            }
        }

        override fun removeTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> nativeStream.removeAudioTrack(native)
                    is RTCVideoTrack -> nativeStream.removeVideoTrack(native)
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
            get() = nativeTrack.kind

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
