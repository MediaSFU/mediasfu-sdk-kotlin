package com.mediasfu.sdk.background

import com.mediasfu.sdk.util.Logger
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.mediasfu.sdk.model.BackgroundType
import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.VirtualVideoSource
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.webrtc.*
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of VirtualBackgroundProcessor using ML Kit Selfie Segmentation.
 * 
 * This captures frames from WebRTC video, runs ML Kit segmentation to detect the person,
 * composites the person with a virtual background, and outputs to a virtual video source.
 */
class AndroidVirtualBackgroundProcessor(
    private val context: Context
) : VirtualBackgroundProcessor {
    
    companion object {
        private const val TAG = "AndroidVBProcessor"
        // Reduced FPS to prevent UI freezing - ML Kit is CPU intensive
        private const val TARGET_FPS = 8
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS
    }
    
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    )
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private val processingScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var _isProcessing = AtomicBoolean(false)
    @Volatile private var _currentBackground: VirtualBackground? = null
    private var inputStream: MediaStream? = null
    private var frameCallback: ((ProcessedFrame) -> Unit)? = null
    
    // Virtual video source for output
    private var virtualVideoSource: VirtualVideoSource? = null
    private var outputStream: MediaStream? = null
    
    // Cached background bitmap - use lock for thread-safe access
    private val backgroundLock = Any()
    @Volatile private var cachedBackgroundBitmap: Bitmap? = null
    @Volatile private var cachedBackgroundId: String? = null
    
    // Frame capture
    private var videoCapturerObserver: SurfaceTextureHelper? = null
    private var processingJob: Job? = null
    
    override val isProcessing: Boolean
        get() = _isProcessing.get()
    
    override val currentBackground: VirtualBackground?
        get() = _currentBackground
    
    override fun setFrameCallback(onProcessedFrame: ((ProcessedFrame) -> Unit)?) {
        this.frameCallback = onProcessedFrame
        Logger.d("AndroidVirtualBackgr", "$TAG: Frame callback updated, isNull=${onProcessedFrame == null}")
    }
    
    override suspend fun startProcessing(
        inputStream: MediaStream,
        background: VirtualBackground,
        onProcessedFrame: ((ProcessedFrame) -> Unit)?
    ): MediaStream? {
        if (_isProcessing.get()) {
            Logger.d("AndroidVirtualBackgr", "$TAG: Already processing, stopping first")
            stopProcessing()
        }
        
        this.inputStream = inputStream
        this._currentBackground = background
        this.frameCallback = onProcessedFrame
        
        // Load background image if needed
        if (background.type == BackgroundType.IMAGE) {
            loadBackgroundImage(background)
        }
        
        _isProcessing.set(true)
        
        // Start frame processing
        startFrameProcessing()
        
        Logger.d("AndroidVirtualBackgr", "$TAG: Started processing with background: ${background.name}")
        
        // Return the output stream (virtual video source) if available, otherwise input
        return outputStream ?: inputStream
    }
    
    /**
     * Start processing with a WebRtcDevice to create a virtual output stream.
     * This creates a new video source that receives processed frames and can be
     * used with mediasoup to send to remote participants.
     */
    override suspend fun startProcessingWithDevice(
        inputStream: MediaStream,
        background: VirtualBackground,
        device: WebRtcDevice,
        onProcessedFrame: ((ProcessedFrame) -> Unit)?
    ): MediaStream? {
        if (_isProcessing.get()) {
            Logger.d("AndroidVirtualBackgr", "$TAG: Already processing, stopping first")
            stopProcessing()
        }
        
        this.inputStream = inputStream
        this._currentBackground = background
        this.frameCallback = onProcessedFrame
        
        // Load background image if needed
        if (background.type == BackgroundType.IMAGE) {
            loadBackgroundImage(background)
        }
        
        // Get input video dimensions
        val videoTracks = inputStream.getVideoTracks()
        val (width, height) = if (videoTracks.isNotEmpty()) {
            // Default dimensions if we can't get them from track
            720 to 1280
        } else {
            720 to 1280
        }
        
        // Create virtual video source for output
        virtualVideoSource = device.createVirtualVideoSource(width, height, TARGET_FPS)
        if (virtualVideoSource == null) {
            Logger.d("AndroidVirtualBackgr", "$TAG: Failed to create virtual video source")
            return null
        }
        
        virtualVideoSource?.start()
        outputStream = virtualVideoSource?.stream
        
        _isProcessing.set(true)
        
        // Start frame processing that feeds to virtual source
        startFrameProcessingWithOutput()
        
        Logger.d("AndroidVirtualBackgr", "$TAG: Started processing with device, output stream: ${outputStream?.id}")
        
        return outputStream
    }
    
    override suspend fun updateBackground(background: VirtualBackground) {
        _currentBackground = background
        
        // Check if background changed (don't clear cache yet - keep old background visible during load)
        val needsReload = synchronized(backgroundLock) {
            background.id != cachedBackgroundId
        }
        
        if (needsReload) {
            if (background.type == BackgroundType.IMAGE) {
                // loadBackgroundImage will atomically swap the old bitmap for the new one
                loadBackgroundImage(background)
            } else {
                // Not an image type - clear the cache
                synchronized(backgroundLock) {
                    cachedBackgroundBitmap?.recycle()
                    cachedBackgroundBitmap = null
                    cachedBackgroundId = background.id
                }
            }
        }
    }
    
    override suspend fun stopProcessing(): MediaStream? {
        _isProcessing.set(false)
        processingJob?.cancel()
        processingJob = null
        
        // Stop and release virtual video source on IO dispatcher to avoid blocking UI
        withContext(Dispatchers.IO) {
            try {
                virtualVideoSource?.stop()
                virtualVideoSource?.release()
            } catch (e: Exception) {
                Logger.d("AndroidVirtualBackgr", "$TAG: Error stopping virtual video source: ${e.message}")
            }
        }
        virtualVideoSource = null
        outputStream = null
        
        Logger.d("AndroidVirtualBackgr", "$TAG: Stopped processing")
        return inputStream
    }
    
    override fun release() {
        runBlocking {
            stopProcessing()
        }
        processingScope.cancel()
        segmenter.close()
        synchronized(backgroundLock) {
            cachedBackgroundBitmap?.recycle()
            cachedBackgroundBitmap = null
            cachedBackgroundId = null
        }
        
        Logger.d("AndroidVirtualBackgr", "$TAG: Released resources")
    }
    
    private fun startFrameProcessing() {
        processingJob = processingScope.launch(Dispatchers.Default) {
            while (_isProcessing.get() && isActive) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Capture and process frame (off main thread as much as possible)
                    captureAndProcessFrame()
                    
                    // Maintain target FPS
                    val elapsed = System.currentTimeMillis() - startTime
                    val delay = FRAME_INTERVAL_MS - elapsed
                    if (delay > 0) {
                        delay(delay)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.d("AndroidVirtualBackgr", "$TAG: Frame processing error: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Frame processing loop that feeds processed frames to the virtual video source.
     * This is used when startProcessingWithDevice is called.
     */
    private fun startFrameProcessingWithOutput() {
        Logger.d("AndroidVirtualBackgr", "$TAG: Starting frame processing loop with output")
        processingJob = processingScope.launch(Dispatchers.Default) {
            Logger.d("AndroidVirtualBackgr", "$TAG: Frame processing coroutine started, isProcessing=${_isProcessing.get()}")
            while (_isProcessing.get() && isActive) {
                try {
                    val startTime = System.currentTimeMillis()
                    
                    // Capture and process frame, feeding to virtual source
                    captureAndProcessFrameWithOutput()
                    
                    // Maintain target FPS
                    val elapsed = System.currentTimeMillis() - startTime
                    val delay = FRAME_INTERVAL_MS - elapsed
                    if (delay > 0) {
                        delay(delay)
                    }
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        Logger.d("AndroidVirtualBackgr", "$TAG: Frame processing error (output): ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
            Logger.d("AndroidVirtualBackgr", "$TAG: Frame processing loop ended, isProcessing=${_isProcessing.get()}, isActive=$isActive")
        }
    }
    
    /**
     * Captures a frame, processes it with ML Kit, and feeds to virtual video source.
     */
    private suspend fun captureAndProcessFrameWithOutput() {
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - entering, inputStream=$inputStream, background=$_currentBackground, source=$virtualVideoSource")
        val stream = inputStream ?: run {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - no inputStream")
            return
        }
        val background = _currentBackground ?: run {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - no currentBackground")
            return
        }
        val source = virtualVideoSource ?: run {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - no virtualVideoSource")
            return
        }
        
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - passed initial checks")
        
        // Get video track
        val videoTracks = stream.getVideoTracks()
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - videoTracks count: ${videoTracks.size}")
        if (videoTracks.isEmpty()) {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - no video tracks")
            return
        }
        
        val videoTrack = videoTracks.first()
        val nativeTrack = videoTrack.asPlatformNativeTrack() as? VideoTrack
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - nativeTrack: $nativeTrack")
        if (nativeTrack == null) {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - failed to get native track")
            return
        }
        
        // Capture frame using VideoSink
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - calling captureFrameFromTrack")
        val bitmap = captureFrameFromTrack(nativeTrack)
        Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - captured bitmap: $bitmap")
        if (bitmap == null) {
            Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - failed to capture frame")
            return
        }
        
        // Process on background thread
        withContext(Dispatchers.Default) {
            try {
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - in withContext, starting ML Kit processing")
                
                // Run ML Kit segmentation
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - creating InputImage from bitmap (${bitmap.width}x${bitmap.height})")
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - InputImage created, calling segmenter.process")
                val mask = segmenter.process(inputImage).await()
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - segmenter returned mask: $mask")
                
                // Composite with background
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - calling compositeWithBackground")
                val processedBitmap = compositeWithBackground(bitmap, mask, background)
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - composite complete, processedBitmap: ${processedBitmap.width}x${processedBitmap.height}")
                
                // Feed processed frame to virtual video source
                val timestampNs = System.nanoTime()
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - calling source.onFrame, timestamp=$timestampNs")
                source.onFrame(processedBitmap, timestampNs, 0)
                Logger.d("AndroidVirtualBackgr", "$TAG: captureAndProcessFrameWithOutput - source.onFrame completed")
                
                // Also notify callback for preview (if set)
                withContext(Dispatchers.Main) {
                    frameCallback?.invoke(ProcessedFrame(
                        width = processedBitmap.width,
                        height = processedBitmap.height,
                        timestamp = System.currentTimeMillis(),
                        imageData = processedBitmap
                    ))
                }
                
                // Don't recycle processedBitmap here - it's used by the virtual source
                // The virtual source makes a copy during conversion
                
            } catch (e: Exception) {
                Logger.d("AndroidVirtualBackgr", "$TAG: Error processing frame (output): ${e.message}")
                e.printStackTrace()
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }

    private suspend fun captureAndProcessFrame() {
        val stream = inputStream ?: return
        val background = _currentBackground ?: return
        
        // Get video track
        val videoTracks = stream.getVideoTracks()
        if (videoTracks.isEmpty()) return
        
        val videoTrack = videoTracks.first()
        val nativeTrack = videoTrack.asPlatformNativeTrack() as? VideoTrack ?: return
        
        // Capture frame using VideoSink (must be on main thread for WebRTC)
        val bitmap = captureFrameFromTrack(nativeTrack) ?: return
        
        // Process on background thread
        withContext(Dispatchers.Default) {
            try {
                // Run ML Kit segmentation
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val mask = segmenter.process(inputImage).await()
                
                // Composite with background
                val processedBitmap = compositeWithBackground(bitmap, mask, background)
                
                // Notify callback on main thread
                withContext(Dispatchers.Main) {
                    frameCallback?.invoke(ProcessedFrame(
                        width = processedBitmap.width,
                        height = processedBitmap.height,
                        timestamp = System.currentTimeMillis(),
                        imageData = processedBitmap
                    ))
                }
                
            } catch (e: Exception) {
                Logger.d("AndroidVirtualBackgr", "$TAG: Error processing frame: ${e.message}")
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }
    }
    
    private var frameSink: FrameCaptureSink? = null
    
    private suspend fun captureFrameFromTrack(videoTrack: VideoTrack): Bitmap? {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                var sinkRef: VideoSink? = null
                
                val sink = object : VideoSink {
                    @Volatile
                    private var captured = false
                    
                    override fun onFrame(frame: VideoFrame) {
                        if (captured) return
                        captured = true
                        
                        // Retain frame and convert on background thread to avoid blocking UI
                        frame.retain()
                        processingScope.launch(Dispatchers.Default) {
                            try {
                                val bitmap = videoFrameToBitmap(frame)
                                withContext(Dispatchers.Main) {
                                    sinkRef?.let { videoTrack.removeSink(it) }
                                }
                                if (continuation.isActive) {
                                    continuation.resume(bitmap) {}
                                } else {
                                    bitmap?.recycle()
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    sinkRef?.let { videoTrack.removeSink(it) }
                                }
                                if (continuation.isActive) {
                                    continuation.resume(null) {}
                                }
                            } finally {
                                frame.release()
                            }
                        }
                    }
                }
                sinkRef = sink
                
                videoTrack.addSink(sink)
                
                continuation.invokeOnCancellation {
                    mainHandler.post {
                        videoTrack.removeSink(sink)
                    }
                }
                
                // Timeout after 500ms (increased for processing time)
                mainHandler.postDelayed({
                    if (continuation.isActive) {
                        videoTrack.removeSink(sink)
                        continuation.resume(null) {}
                    }
                }, 500)
            }
        }
    }
    
    private fun videoFrameToBitmap(frame: VideoFrame): Bitmap {
        val buffer = frame.buffer
        val width = buffer.width
        val height = buffer.height
        val rotation = frame.rotation
        
        val i420Buffer = buffer.toI420()
        
        try {
            val yuvImage = YuvImage(
                i420ToNv21(i420Buffer, width, height),
                ImageFormat.NV21,
                width,
                height,
                null
            )
            
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), 90, out)
            val imageBytes = out.toByteArray()
            
            var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            // Apply rotation if needed (front camera often needs rotation)
            if (rotation != 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotatedBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = rotatedBitmap
                }
            }
            
            return bitmap
        } finally {
            i420Buffer.release()
        }
    }
    
    private fun i420ToNv21(i420Buffer: VideoFrame.I420Buffer, width: Int, height: Int): ByteArray {
        val nv21 = ByteArray(width * height * 3 / 2)
        
        val yBuffer = i420Buffer.dataY
        val uBuffer = i420Buffer.dataU
        val vBuffer = i420Buffer.dataV
        
        val yStride = i420Buffer.strideY
        val uStride = i420Buffer.strideU
        val vStride = i420Buffer.strideV
        
        // Copy Y plane
        var pos = 0
        for (row in 0 until height) {
            yBuffer.position(row * yStride)
            yBuffer.get(nv21, pos, width)
            pos += width
        }
        
        // Interleave U and V planes for NV21
        val uvHeight = height / 2
        val uvWidth = width / 2
        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                vBuffer.position(row * vStride + col)
                nv21[pos++] = vBuffer.get()
                uBuffer.position(row * uStride + col)
                nv21[pos++] = uBuffer.get()
            }
        }
        
        return nv21
    }
    
    private fun compositeWithBackground(
        originalBitmap: Bitmap,
        mask: SegmentationMask,
        background: VirtualBackground
    ): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        
        val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        
        when (background.type) {
            BackgroundType.IMAGE -> {
                // Draw background image - get bitmap under lock
                val bgBitmap = synchronized(backgroundLock) { cachedBackgroundBitmap }
                if (bgBitmap != null && !bgBitmap.isRecycled) {
                    val srcRect = calculateCoverRect(bgBitmap, width, height)
                    val destRect = Rect(0, 0, width, height)
                    canvas.drawBitmap(bgBitmap, srcRect, destRect, null)
                } else {
                    canvas.drawColor(Color.GRAY)
                }
            }
            BackgroundType.BLUR -> {
                // Draw blurred original
                val blurredBitmap = blurBitmap(originalBitmap, background.blurIntensity)
                canvas.drawBitmap(blurredBitmap, 0f, 0f, null)
                blurredBitmap.recycle()
            }
            BackgroundType.COLOR -> {
                // Draw solid color
                val color = background.color
                if (color != null) {
                    canvas.drawColor(
                        Color.argb(
                            (color.alpha * 255).toInt(),
                            (color.red * 255).toInt(),
                            (color.green * 255).toInt(),
                            (color.blue * 255).toInt()
                        )
                    )
                } else {
                    canvas.drawColor(Color.GREEN)
                }
            }
            else -> {
                // No background - just draw original
                canvas.drawBitmap(originalBitmap, 0f, 0f, null)
                return resultBitmap
            }
        }
        
        // Draw person using mask
        drawPersonWithMask(canvas, originalBitmap, mask)
        
        return resultBitmap
    }
    
    private fun drawPersonWithMask(
        canvas: Canvas,
        personBitmap: Bitmap,
        mask: SegmentationMask
    ) {
        val maskWidth = mask.width
        val maskHeight = mask.height
        val maskBuffer = mask.buffer
        
        val targetWidth = personBitmap.width
        val targetHeight = personBitmap.height
        
        // Create ARGB mask bitmap where alpha = confidence
        val maskBitmap = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val maskPixels = IntArray(maskWidth * maskHeight)
        
        maskBuffer.rewind()
        for (i in maskPixels.indices) {
            val confidence = maskBuffer.float
            // Create white pixel with alpha based on confidence
            // Higher confidence = more opaque (person visible)
            val alpha = (confidence * 255).toInt().coerceIn(0, 255)
            maskPixels[i] = Color.argb(alpha, 255, 255, 255)
        }
        
        maskBitmap.setPixels(maskPixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)
        
        // Scale mask to match person bitmap if needed
        val scaledMask = if (maskWidth != targetWidth || maskHeight != targetHeight) {
            Bitmap.createScaledBitmap(maskBitmap, targetWidth, targetHeight, true).also {
                maskBitmap.recycle()
            }
        } else {
            maskBitmap
        }
        
        // Create person with alpha from mask
        val personWithAlpha = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val personCanvas = Canvas(personWithAlpha)
        
        // Draw person first
        personCanvas.drawBitmap(personBitmap, 0f, 0f, null)
        
        // Apply mask as alpha using PorterDuff DST_IN
        // DST_IN: keeps destination (person) only where source (mask) has alpha
        val maskPaint = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        personCanvas.drawBitmap(scaledMask, 0f, 0f, maskPaint)
        scaledMask.recycle()
        
        // Draw masked person over background
        canvas.drawBitmap(personWithAlpha, 0f, 0f, null)
        personWithAlpha.recycle()
    }
    
    private fun calculateCoverRect(source: Bitmap, targetWidth: Int, targetHeight: Int): Rect {
        val sourceRatio = source.width.toFloat() / source.height
        val targetRatio = targetWidth.toFloat() / targetHeight
        
        return if (sourceRatio > targetRatio) {
            // Source is wider - crop sides
            val scaledWidth = (source.height * targetRatio).toInt()
            val left = (source.width - scaledWidth) / 2
            Rect(left, 0, left + scaledWidth, source.height)
        } else {
            // Source is taller - crop top/bottom
            val scaledHeight = (source.width / targetRatio).toInt()
            val top = (source.height - scaledHeight) / 2
            Rect(0, top, source.width, top + scaledHeight)
        }
    }
    
    private fun blurBitmap(bitmap: Bitmap, intensity: Float): Bitmap {
        // Simple box blur implementation
        val radius = (intensity * 25).toInt().coerceIn(1, 25)
        
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        
        val paint = Paint().apply {
            maskFilter = BlurMaskFilter(radius.toFloat(), BlurMaskFilter.Blur.NORMAL)
        }
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
    
    private suspend fun loadBackgroundImage(background: VirtualBackground) {
        // Check under lock if we already have this cached
        val alreadyCached = synchronized(backgroundLock) {
            background.id == cachedBackgroundId && cachedBackgroundBitmap != null
        }
        if (alreadyCached) return
        
        try {
            // Load the new bitmap (this may take time for URL)
            val newBitmap = when {
                background.imageBytes != null -> {
                    BitmapFactory.decodeByteArray(
                        background.imageBytes,
                        0,
                        background.imageBytes!!.size
                    )
                }
                background.imageUrl != null -> {
                    withContext(Dispatchers.IO) {
                        val url = URL(background.imageUrl)
                        val stream = url.openStream()
                        val bmp = BitmapFactory.decodeStream(stream)
                        stream.close()
                        bmp
                    }
                }
                else -> null
            }
            
            // Atomically swap the old bitmap for the new one under lock
            // This ensures the frame processing always has a valid bitmap (either old or new)
            val oldBitmap = synchronized(backgroundLock) {
                val old = cachedBackgroundBitmap
                cachedBackgroundBitmap = newBitmap
                cachedBackgroundId = background.id
                old  // Return the old bitmap to recycle outside the lock
            }
            
            // Recycle the old bitmap outside the lock to avoid holding the lock too long
            oldBitmap?.recycle()
        } catch (e: Exception) {
            // Silent failure - keep using previous background if available
        }
    }
}

/**
 * Helper class to capture frames from VideoTrack
 */
private class FrameCaptureSink : VideoSink {
    var lastFrame: VideoFrame? = null
    
    override fun onFrame(frame: VideoFrame) {
        frame.retain()
        lastFrame?.release()
        lastFrame = frame
    }
    
    fun release() {
        lastFrame?.release()
        lastFrame = null
    }
}
