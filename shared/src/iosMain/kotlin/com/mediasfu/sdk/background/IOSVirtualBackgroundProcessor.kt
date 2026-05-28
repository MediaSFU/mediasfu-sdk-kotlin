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
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.VirtualVideoSource
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.cinterop.CPointerVar
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
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectMake
import platform.CoreImage.CIColor
import platform.CoreImage.CIContext
import platform.CoreImage.CIImage
import platform.CoreVideo.CVPixelBufferCreate
import platform.CoreVideo.CVPixelBufferGetHeight
import platform.CoreVideo.CVPixelBufferGetWidth
import platform.CoreVideo.CVPixelBufferRef
import platform.CoreVideo.kCVPixelFormatType_32BGRA
import platform.CoreVideo.kCVPixelFormatType_OneComponent8
import platform.CoreVideo.kCVReturnSuccess
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.QuartzCore.CACurrentMediaTime
import platform.UIKit.UIImage
import platform.Vision.VNGeneratePersonSegmentationRequest
import platform.Vision.VNGeneratePersonSegmentationRequestQualityLevelBalanced
import platform.Vision.VNSequenceRequestHandler
import platform.Vision.VNPixelBufferObservation
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
internal class IOSVirtualBackgroundProcessor : VirtualBackgroundProcessor {

    companion object {
        private const val TAG = "IOSVBProcessor"
        private const val TARGET_FPS = 8
        private const val FRAME_INTERVAL_MS = 1000.0 / TARGET_FPS.toDouble()
        private const val DEFAULT_WIDTH = 720
        private const val DEFAULT_HEIGHT = 1280
    }

    private val ciContext = CIContext()
    private val request = VNGeneratePersonSegmentationRequest().apply {
        qualityLevel = VNGeneratePersonSegmentationRequestQualityLevelBalanced
        outputPixelFormat = kCVPixelFormatType_OneComponent8
    }
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var processingFrame = false
    private var lastFrameAtMs = 0.0
    private var _isProcessing = false
    private var _currentBackground: VirtualBackground? = null

    private var inputStream: MediaStream? = null
    private var frameCallback: ((ProcessedFrame) -> Unit)? = null
    private var virtualVideoSource: VirtualVideoSource? = null
    private var outputStream: MediaStream? = null
    private var attachedTrack: RTCVideoTrack? = null
    private var frameRenderer: FrameRenderer? = null
    private var frameJob: Job? = null

    private var cachedBackgroundId: String? = null
    private var cachedBackgroundImage: CIImage? = null

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
        loadBackgroundImage(background)

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
        loadBackgroundImage(background)
    }

    override suspend fun stopProcessing(): MediaStream? {
        _isProcessing = false
        frameJob?.cancel()
        frameJob = null
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
        processingFrame = true
        lastFrameAtMs = nowMs

        frameJob = processingScope.launch {
            try {
                processFrame(frame)
            } catch (error: Throwable) {
                Logger.d("IOSVirtualBackgr", "$TAG: frame processing error -> ${error.message}")
            } finally {
                processingFrame = false
            }
        }
    }

    private suspend fun processFrame(frame: RTCVideoFrame) {
        val sourcePixelBuffer = (frame.buffer as? RTCCVPixelBuffer)?.pixelBuffer ?: return
        val background = _currentBackground ?: return
        val width = CVPixelBufferGetWidth(sourcePixelBuffer).toInt()
        val height = CVPixelBufferGetHeight(sourcePixelBuffer).toInt()
        val bounds = CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble())

        val sourceImage = CIImage(cVPixelBuffer = sourcePixelBuffer)
        val maskPixelBuffer = generateMask(sourcePixelBuffer) ?: return
        val maskImage = CIImage(cVPixelBuffer = maskPixelBuffer)
        val composedImage = composeFrame(sourceImage, maskImage, background) ?: sourceImage

        val outputPixelBuffer = createOutputPixelBuffer(width = width, height = height) ?: return

        ciContext.render(composedImage, toCVPixelBuffer = outputPixelBuffer, bounds = bounds, colorSpace = null)

        val outputFrame = RTCVideoFrame(
            buffer = RTCCVPixelBuffer(outputPixelBuffer),
            rotation = frame.rotation,
            timeStampNs = frame.timeStampNs
        )

        virtualVideoSource?.onFrame(outputFrame, frame.timeStampNs, frame.rotation.toInt())

        frameCallback?.let { callback ->
            withContext(Dispatchers.Main) {
                callback(
                    ProcessedFrame(
                        width = width,
                        height = height,
                        timestamp = (CACurrentMediaTime() * 1000.0).toLong(),
                        imageData = UIImage.imageWithCIImage(composedImage)
                    )
                )
            }
        }
    }

    private fun generateMask(sourcePixelBuffer: CVPixelBufferRef): CVPixelBufferRef? {
        val handler = VNSequenceRequestHandler()
        val success = handler.performRequests(
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
                    "inputMaskImage" to maskImage
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

    private fun createOutputPixelBuffer(width: Int, height: Int): CVPixelBufferRef? = memScoped {
        val pixelBufferRef = alloc<CPointerVar<__CVBuffer>>()
        val status = CVPixelBufferCreate(
            allocator = null,
            width = width.toULong(),
            height = height.toULong(),
            pixelFormatType = kCVPixelFormatType_32BGRA.toUInt(),
            pixelBufferAttributes = null,
            pixelBufferOut = pixelBufferRef.ptr
        )
        if (status == kCVReturnSuccess) pixelBufferRef.value else null
    }
}

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
    val sourceRect = extent()
    val sourceWidth = sourceRect.useContents { size.width }
    val sourceHeight = sourceRect.useContents { size.height }
    val targetWidth = targetRect.useContents { size.width }
    val targetHeight = targetRect.useContents { size.height }
    if (sourceWidth <= 0.0 || sourceHeight <= 0.0) {
        return imageByCroppingToRect(targetRect)
    }

    val scale = maxOf(
        targetWidth / sourceWidth,
        targetHeight / sourceHeight
    )
    val scaled = imageByApplyingTransform(CGAffineTransformMakeScale(scale, scale))
    val scaledRect = scaled.extent()
    val scaledWidth = scaledRect.useContents { size.width }
    val scaledHeight = scaledRect.useContents { size.height }
    val scaledOriginX = scaledRect.useContents { origin.x }
    val scaledOriginY = scaledRect.useContents { origin.y }
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