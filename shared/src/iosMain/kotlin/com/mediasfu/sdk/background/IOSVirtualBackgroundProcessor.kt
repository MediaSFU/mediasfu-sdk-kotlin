package com.mediasfu.sdk.background

import androidx.compose.ui.graphics.Color
import cnames.structs.__CVBuffer
import cocoapods.WebRTC.RTCCVPixelBuffer
import cocoapods.WebRTC.RTCVideoFrame
import cocoapods.WebRTC.RTCVideoRendererProtocol
import cocoapods.WebRTC.RTCVideoTrack
import com.mediasfu.sdk.model.BackgroundType
import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.webrtc.IOSWebRtcDevice
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.VirtualVideoSource
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFGetRetainCount
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIColor
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.Foundation.NSLock
import platform.Metal.MTLCreateSystemDefaultDevice
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.kCVPixelBufferCGBitmapContextCompatibilityKey
import platform.CoreVideo.kCVPixelBufferCGImageCompatibilityKey
import platform.CoreVideo.kCVPixelBufferMetalCompatibilityKey
import platform.CoreVideo.kCVPixelBufferOpenGLESCompatibilityKey
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_OneComponent8
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.NSData
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIImage
import platform.Vision.VNGeneratePersonSegmentationRequest
import platform.Vision.VNGeneratePersonSegmentationRequestQualityLevelBalanced
import platform.Vision.VNGeneratePersonSegmentationRequestQualityLevelFast
import platform.Vision.VNSequenceRequestHandler
import platform.Vision.VNPixelBufferObservation
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IOSVirtualBackgroundProcessor : VirtualBackgroundProcessor {

    companion object {
        private const val TAG = "IOSVBProcessor"
        private const val TARGET_FPS = 20
        private const val FRAME_INTERVAL_MS = 1000.0 / TARGET_FPS.toDouble()
        private const val DEFAULT_WIDTH = 720
        private const val DEFAULT_HEIGHT = 1280
    }

    private val ciContext = MTLCreateSystemDefaultDevice()?.let { device ->
        CIContext.contextWithMTLDevice(device as objcnames.protocols.MTLDeviceProtocol)
    } ?: CIContext()
    private val request = VNGeneratePersonSegmentationRequest().apply {
        // Fast quality is ~3x faster than Balanced on simulator/older devices with minimal
        // visible quality difference for typical conference backgrounds.
        qualityLevel = VNGeneratePersonSegmentationRequestQualityLevelFast
        outputPixelFormat = kCVPixelFormatType_OneComponent8
    }
    // Reuse across frames so Vision can use temporal context between sequential frames.
    private val sequenceHandler = VNSequenceRequestHandler()
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val outputBufferPool = mutableListOf<CVPixelBufferRef>()
    private val poolLock = NSLock()

    private var processingFrame = false
    private var lastFrameAtMs = 0.0
    private var _isProcessing = false
    private var _currentBackground: VirtualBackground? = null

    private var inputStream: MediaStream? = null
    private var frameCallback: ((ProcessedFrame) -> Unit)? = null
    private var virtualVideoSource: VirtualVideoSource? = null
    private var outputStream: MediaStream? = null
    private var processingDevice: IOSWebRtcDevice? = null
    private var attachedTrack: RTCVideoTrack? = null
    private var frameRenderer: FrameRenderer? = null
    private var frameJob: Job? = null

    private var cachedBackgroundId: String? = null
    private var cachedBackgroundImage: CIImage? = null

    // Double-buffer the expensive Vision mask for the in-place camera path.
    // The camera callback uses the latest mask immediately and refreshes it off-thread.
    private var latestMaskImage: CIImage? = null
    private var syncProcessingActive = false

    override val isProcessing: Boolean
        get() = _isProcessing

    override val currentBackground: VirtualBackground?
        get() = _currentBackground

    override fun setFrameCallback(onProcessedFrame: ((ProcessedFrame) -> Unit)?) {
        frameCallback = onProcessedFrame
    }

    override suspend fun startProcessing(
        inputStream: MediaStream,
        background: VirtualBackground,
        onProcessedFrame: ((ProcessedFrame) -> Unit)?
    ): MediaStream? {
        stopIfRunning()

        this.inputStream = inputStream
        this._currentBackground = background
        this.frameCallback = onProcessedFrame
        latestMaskImage = null
        syncProcessingActive = false
        loadBackgroundImage(background)
        attachRenderer()
        _isProcessing = true

        Logger.d("IOSVirtualBackgr", "$TAG: Started preview processing with background=${background.name}")
        return inputStream
    }

    override suspend fun startProcessingWithDevice(
        inputStream: MediaStream,
        background: VirtualBackground,
        device: WebRtcDevice,
        onProcessedFrame: ((ProcessedFrame) -> Unit)?
    ): MediaStream? {
        stopIfRunning()

        this.inputStream = inputStream
        this._currentBackground = background
        this.frameCallback = onProcessedFrame
        latestMaskImage = null
        syncProcessingActive = false
        loadBackgroundImage(background)

        if (device is IOSWebRtcDevice) {
            this.inputStream = inputStream
            processingDevice = device
            _isProcessing = true
            device.setCameraFrameProcessor { frame ->
                processFrameSynchronously(frame)
            }

            Logger.d("IOSVirtualBackgr", "$TAG: Started in-place WebRTC track processing with background=${background.name}")
            return inputStream
        }

        virtualVideoSource = device.createVirtualVideoSource(DEFAULT_WIDTH, DEFAULT_HEIGHT, TARGET_FPS)
        val output = virtualVideoSource?.stream ?: return null
        virtualVideoSource?.start()
        outputStream = output

        attachRenderer()
        _isProcessing = true

        Logger.d("IOSVirtualBackgr", "$TAG: Started output processing with background=${background.name}")
        return output
    }

    override suspend fun updateBackground(background: VirtualBackground) {
        _currentBackground = background
        latestMaskImage = null
        loadBackgroundImage(background)
    }

    override suspend fun stopProcessing(): MediaStream? {
        _isProcessing = false
        frameJob?.cancel()
        frameJob = null
        processingDevice?.setCameraFrameProcessor(null)
        processingDevice = null
        detachRenderer()

        withContext(Dispatchers.Default) {
            runCatching {
                virtualVideoSource?.stop()
                virtualVideoSource?.release()
            }
        }

        virtualVideoSource = null
        outputStream = null
        processingFrame = false
        lastFrameAtMs = 0.0
        latestMaskImage = null
        syncProcessingActive = false

        poolLock.lock()
        try {
            for (buffer in outputBufferPool) {
                CFRelease(buffer)
            }
            outputBufferPool.clear()
        } finally {
            poolLock.unlock()
        }

        Logger.d("IOSVirtualBackgr", "$TAG: Stopped processing")
        return inputStream
    }

    override fun release() {
        runBlocking {
            stopProcessing()
        }
        processingScope.cancel()
        cachedBackgroundId = null
        cachedBackgroundImage = null
    }

    private suspend fun stopIfRunning() {
        if (isProcessing) {
            stopProcessing()
        }
    }

    private fun attachRenderer() {
        detachRenderer()

        val nativeTrack = inputStream
            ?.getVideoTracks()
            ?.firstOrNull()
            ?.asPlatformNativeTrack() as? RTCVideoTrack
            ?: return

        val renderer = FrameRenderer { frame ->
            enqueueFrame(frame)
        }

        nativeTrack.addRenderer(renderer)
        attachedTrack = nativeTrack
        frameRenderer = renderer
    }

    private fun detachRenderer() {
        attachedTrack?.let { track ->
            frameRenderer?.let { renderer -> track.removeRenderer(renderer) }
        }
        attachedTrack = null
        frameRenderer = null
    }

    private fun enqueueFrame(frame: RTCVideoFrame) {
        if (!_isProcessing) return
        val nowMs = CACurrentMediaTime() * 1000.0

        if (processingFrame || nowMs - lastFrameAtMs < FRAME_INTERVAL_MS) {
            return
        }
        val sourcePixelBuffer = (frame.buffer as? RTCCVPixelBuffer)?.pixelBuffer ?: return
        processingFrame = true
        lastFrameAtMs = nowMs

        CFRetain(sourcePixelBuffer)
        frameJob = processingScope.launch {
            try {
                processFrame(frame)
            } catch (error: Throwable) {
                Logger.d("IOSVirtualBackgr", "$TAG: frame processing error -> ${error.message}")
            } finally {
                CFRelease(sourcePixelBuffer)
                processingFrame = false
            }
        }
    }

    private suspend fun processFrame(frame: RTCVideoFrame) {
        val rendered = renderProcessedFrame(frame, includePreviewImage = frameCallback != null) ?: return

        virtualVideoSource?.onFrame(rendered.frame, frame.timeStampNs, 0)

        frameCallback?.let { callback ->
            val imageData = rendered.previewImageData ?: return@let
            withContext(Dispatchers.Main) {
                callback(
                    ProcessedFrame(
                        width = rendered.width,
                        height = rendered.height,
                        timestamp = (CACurrentMediaTime() * 1000.0).toLong(),
                        imageData = imageData
                    )
                )
            }
        }
    }

    private fun processFrameSynchronously(frame: RTCVideoFrame): RTCVideoFrame {
        val background = _currentBackground
        if (!_isProcessing || background == null || background.type == BackgroundType.NONE) {
            return frame
        }

        scheduleMaskRefresh(frame)
        val maskImage = latestMaskImage ?: return frame

        return try {
            val result = renderProcessedFrame(
                frame = frame,
                includePreviewImage = false,
                forcedBackground = background,
                forcedMaskImage = maskImage
            )
            result?.frame ?: RTCVideoFrame(
                buffer = frame.buffer,
                rotation = frame.rotation,
                timeStampNs = frame.timeStampNs
            )
        } catch (e: Throwable) {
            Logger.d("IOSVirtualBackgr", "$TAG: sync frame error -> ${e.message}")
            RTCVideoFrame(
                buffer = frame.buffer,
                rotation = frame.rotation,
                timeStampNs = frame.timeStampNs
            )
        }
    }

    private fun scheduleMaskRefresh(frame: RTCVideoFrame) {
        val sourcePixelBuffer = (frame.buffer as? RTCCVPixelBuffer)?.pixelBuffer ?: return
        val nowMs = CACurrentMediaTime() * 1000.0
        if (syncProcessingActive || nowMs - lastFrameAtMs < FRAME_INTERVAL_MS) {
            return
        }

        syncProcessingActive = true
        lastFrameAtMs = nowMs
        CFRetain(sourcePixelBuffer)
        processingScope.launch {
            try {
                val maskPixelBuffer = generateMask(sourcePixelBuffer)
                if (maskPixelBuffer != null) {
                    latestMaskImage = CIImage(cVPixelBuffer = maskPixelBuffer)
                }
            } catch (e: Throwable) {
                Logger.d("IOSVirtualBackgr", "$TAG: mask refresh error -> ${e.message}")
            } finally {
                CFRelease(sourcePixelBuffer)
                syncProcessingActive = false
            }
        }
    }

    private fun renderProcessedFrame(
        frame: RTCVideoFrame,
        includePreviewImage: Boolean,
        forcedBackground: VirtualBackground? = null,
        forcedMaskImage: CIImage? = null
    ): RenderedFrame? {
        val sourcePixelBuffer = (frame.buffer as? RTCCVPixelBuffer)?.pixelBuffer ?: return null
        val background = forcedBackground ?: _currentBackground ?: return null
        val width = CVPixelBufferGetWidth(sourcePixelBuffer).toInt()
        val height = CVPixelBufferGetHeight(sourcePixelBuffer).toInt()
        val bounds = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())

        val sourceImage = CIImage(cVPixelBuffer = sourcePixelBuffer)
        val generatedMaskImage = if (forcedMaskImage == null) {
            generateMask(sourcePixelBuffer)?.let { maskPixelBuffer ->
                CIImage(cVPixelBuffer = maskPixelBuffer)
            }
        } else {
            null
        }
        val maskImage = forcedMaskImage ?: generatedMaskImage
        val composedImage = if (maskImage != null) {
            composeFrame(sourceImage, maskImage, background) ?: sourceImage
        } else if (isRunningOnSimulator() && background.type == BackgroundType.IMAGE) {
            // The simulator camera feed is synthetic, so Vision often cannot
            // produce a person mask. Still render the selected image so the
            // image/no-image VB flow is testable without a physical camera.
            cachedBackgroundImage?.scaledAndCroppedTo(bounds) ?: sourceImage
        } else {
            // Keep preview/output alive when segmentation occasionally fails instead of dropping frame.
            sourceImage
        }

        val outputPixelBuffer = getOrCreateOutputPixelBuffer(width = width, height = height)
        val displayWidth = width
        val displayHeight = height

        if (outputPixelBuffer == null) {
            val previewImageData = if (includePreviewImage) {
                UIImage.imageWithCIImage(sourceImage)
            } else {
                null
            }
            return RenderedFrame(
                frame = RTCVideoFrame(
                    buffer = frame.buffer,
                    rotation = frame.rotation,
                    timeStampNs = frame.timeStampNs
                ),
                width = displayWidth,
                height = displayHeight,
                previewImageData = previewImageData
            )
        }

        ciContext.render(composedImage, toCVPixelBuffer = outputPixelBuffer, bounds = bounds, colorSpace = null)

        val outputFrame = RTCVideoFrame(
            buffer = RTCCVPixelBuffer(outputPixelBuffer),
            rotation = frame.rotation,
            timeStampNs = frame.timeStampNs
        )

        // Return UIImage directly to avoid expensive per-frame PNG encoding on iOS.
        // Encoding every frame can collapse preview FPS and trigger black-frame behavior.
        val previewImageData = if (includePreviewImage) {
            val previewImage = CIImage(cVPixelBuffer = outputPixelBuffer)
            UIImage.imageWithCIImage(previewImage)
        } else {
            null
        }

        return RenderedFrame(
            frame = outputFrame,
            width = displayWidth,
            height = displayHeight,
            previewImageData = previewImageData
        )
    }

    private fun generateMask(sourcePixelBuffer: CVPixelBufferRef): CVPixelBufferRef? {
        // Use the shared sequenceHandler so Vision can apply inter-frame temporal
        // smoothing. Only safe because all callers run inside processingScope.
        val success = sequenceHandler.performRequests(
            requests = listOf(request),
            onCVPixelBuffer = sourcePixelBuffer,
            error = null
        )
        if (!success) return null

        return (request.results?.firstOrNull() as? VNPixelBufferObservation)?.pixelBuffer
    }

    private fun composeFrame(
        sourceImage: CIImage,
        maskImage: CIImage,
        background: VirtualBackground
    ): CIImage? {
        val extent = sourceImage.extent()
        val (extentWidth, extentHeight) = extent.useContents { size.width to size.height }
        val maskExtent = maskImage.extent()
        val (maskWidth, maskHeight) = maskExtent.useContents { size.width to size.height }
        if (maskWidth <= 0.0 || maskHeight <= 0.0) return sourceImage

        val scaleX = extentWidth / maskWidth
        val scaleY = extentHeight / maskHeight
        val scaledMask = maskImage.imageByApplyingTransform(CGAffineTransformMakeScale(scaleX, scaleY))

        val backgroundImage = when (background.type) {
            BackgroundType.IMAGE -> cachedBackgroundImage?.scaledAndCroppedTo(extent)
            BackgroundType.BLUR -> {
                sourceImage
                    .imageByApplyingFilter(
                        filterName = "CIGaussianBlur",
                        withInputParameters = mapOf(
                            "inputRadius" to (6.0 + (background.blurIntensity.coerceIn(0f, 1f) * 18.0f))
                        )
                    )
                    ?.imageByCroppingToRect(extent)
            }
            BackgroundType.COLOR -> background.color
                ?.toCIImage(extent)
            BackgroundType.NONE -> sourceImage
            else -> sourceImage
        } ?: sourceImage

        return sourceImage
            .imageByApplyingFilter(
                filterName = "CIBlendWithMask",
                withInputParameters = mapOf(
                    "inputBackgroundImage" to backgroundImage,
                    "inputMaskImage" to scaledMask
                )
            )
            ?.imageByCroppingToRect(extent)
    }

    private suspend fun loadBackgroundImage(background: VirtualBackground) {
        if (background.type != BackgroundType.IMAGE) {
            cachedBackgroundId = background.id
            cachedBackgroundImage = null
            return
        }

        if (cachedBackgroundId == background.id && cachedBackgroundImage != null) {
            return
        }

        cachedBackgroundImage = withContext(Dispatchers.Default) {
            val loadedImage: CIImage? = when {
                background.imageBytes != null -> CIImage.imageWithData(background.imageBytes.toNSData())
                background.imageUrl != null -> {
                    val url = background.imageUrl?.let { NSURL.URLWithString(it) }
                    url?.let { targetUrl -> CIImage.imageWithContentsOfURL(targetUrl) }
                }
                else -> null
            }
            loadedImage
        }
        cachedBackgroundId = background.id
    }

    private fun getOrCreateOutputPixelBuffer(width: Int, height: Int): CVPixelBufferRef? {
        poolLock.lock()
        try {
            val existing = outputBufferPool.firstOrNull { buffer ->
                CFGetRetainCount(buffer) == 1L &&
                CVPixelBufferGetWidth(buffer).toInt() == width &&
                CVPixelBufferGetHeight(buffer).toInt() == height
            }
            if (existing != null) {
                return existing
            }

            val iterator = outputBufferPool.iterator()
            while (iterator.hasNext()) {
                val buffer = iterator.next()
                if (CFGetRetainCount(buffer) == 1L &&
                    (CVPixelBufferGetWidth(buffer).toInt() != width ||
                     CVPixelBufferGetHeight(buffer).toInt() != height)) {
                    CFRelease(buffer)
                    iterator.remove()
                }
            }

            val newBuffer = createOutputPixelBuffer(width, height)
            if (newBuffer != null) {
                outputBufferPool.add(newBuffer)
            }
            return newBuffer
        } finally {
            poolLock.unlock()
        }
    }

    private fun createOutputPixelBuffer(width: Int, height: Int): CVPixelBufferRef? =
        withCompatiblePixelBufferAttributes { attributes ->
            memScoped {
                val pixelBufferRef = alloc<CPointerVar<__CVBuffer>>()
                val status = CVPixelBufferCreate(
                    allocator = null,
                    width = width.toULong(),
                    height = height.toULong(),
                    pixelFormatType = kCVPixelFormatType_32BGRA.toUInt(),
                    pixelBufferAttributes = attributes,
                    pixelBufferOut = pixelBufferRef.ptr
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

    private fun isRunningOnSimulator(): Boolean {
        val env = NSProcessInfo.processInfo.environment
        val simulatorDeviceName = env["SIMULATOR_DEVICE_NAME"] as? String
        return !simulatorDeviceName.isNullOrBlank()
    }
}

@OptIn(ExperimentalForeignApi::class)
private data class RenderedFrame(
    val frame: RTCVideoFrame,
    val width: Int,
    val height: Int,
    val previewImageData: Any?
)

@OptIn(ExperimentalForeignApi::class)
private class FrameRenderer(
    private val onFrame: (RTCVideoFrame) -> Unit
) : NSObject(), RTCVideoRendererProtocol {
    override fun setSize(size: CValue<platform.CoreGraphics.CGSize>) = Unit

    override fun renderFrame(frame: RTCVideoFrame?) {
        frame?.let(onFrame)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun Color.toCIImage(extent: CValue<platform.CoreGraphics.CGRect>): CIImage? {
    val ciColor = CIColor(
        red = red.toDouble(),
        green = green.toDouble(),
        blue = blue.toDouble(),
        alpha = alpha.toDouble()
    )
    return CIImage.imageWithColor(ciColor)?.imageByCroppingToRect(extent)
}

@OptIn(ExperimentalForeignApi::class)
private fun CIImage.scaledAndCroppedTo(targetRect: CValue<platform.CoreGraphics.CGRect>): CIImage {
    val (sourceWidth, sourceHeight) = extent().useContents { size.width to size.height }
    val (targetWidth, targetHeight) = targetRect.useContents { size.width to size.height }
    if (sourceWidth <= 0.0 || sourceHeight <= 0.0) {
        return imageByCroppingToRect(targetRect)
    }

    val scale = maxOf(
        targetWidth / sourceWidth,
        targetHeight / sourceHeight
    )
    val scaled = imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
    val (scaledWidth, scaledHeight, scaledOriginX, scaledOriginY) = scaled.extent().useContents {
        listOf(size.width, size.height, origin.x, origin.y)
    }
    val translated = scaled.imageByApplyingTransform(
        CGAffineTransformMakeTranslation(
            tx = (targetWidth - scaledWidth) / 2.0 - scaledOriginX,
            ty = (targetHeight - scaledHeight) / 2.0 - scaledOriginY
        )
    )
    return translated.imageByCroppingToRect(targetRect)
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
}
