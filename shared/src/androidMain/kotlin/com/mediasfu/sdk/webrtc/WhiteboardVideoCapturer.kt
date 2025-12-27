package com.mediasfu.sdk.webrtc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathEffect
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.mediasfu.sdk.model.LineType
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import org.webrtc.CapturerObserver
import org.webrtc.JavaI420Buffer
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoFrame
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

private const val TAG = "MediaSFU-WhiteboardCapturer"

/**
 * WhiteboardVideoCapturer - Captures the whiteboard canvas as a video stream.
 *
 * This capturer renders whiteboard shapes to a bitmap and converts them to video frames
 * for streaming via WebRTC, similar to HTML Canvas's captureStream(30) API.
 */
class WhiteboardVideoCapturer(
    private val context: Context,
    private val shapesProvider: () -> List<WhiteboardShape>,
    private val useImageBackgroundProvider: () -> Boolean = { false }
) : VideoCapturer {

    private var capturerObserver: CapturerObserver? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    
    private val isCapturing = AtomicBoolean(false)
    private var captureWidth: Int = 1280
    private var captureHeight: Int = 720
    private var captureFrameRate: Int = 30
    
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.LEFT
    }
    
    private var frameIntervalMs: Long = 33 // ~30fps

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        context: Context?,
        observer: CapturerObserver?
    ) {
        Log.d(TAG, "Initializing whiteboard capturer")
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = observer
    }

    override fun startCapture(width: Int, height: Int, frameRate: Int) {
        Log.d(TAG, "Starting whiteboard capture: ${width}x${height}@${frameRate}fps")
        
        if (isCapturing.getAndSet(true)) {
            Log.w(TAG, "Capturer already running")
            return
        }
        
        captureWidth = width
        captureHeight = height
        captureFrameRate = frameRate.coerceIn(1, 60)
        frameIntervalMs = (1000 / captureFrameRate).toLong()
        
        // Create bitmap for rendering
        bitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        
        // Start capture thread
        handlerThread = HandlerThread("WhiteboardCapturer").apply { start() }
        handler = Handler(handlerThread!!.looper)
        
        // Start capture loop
        handler?.post(captureRunnable)
        
        Log.d(TAG, "Whiteboard capture started")
    }

    override fun stopCapture() {
        Log.d(TAG, "Stopping whiteboard capture")
        
        if (!isCapturing.getAndSet(false)) {
            Log.w(TAG, "Capturer not running")
            return
        }
        
        handler?.removeCallbacks(captureRunnable)
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        
        bitmap?.recycle()
        bitmap = null
        canvas = null
        
        Log.d(TAG, "Whiteboard capture stopped")
    }

    override fun changeCaptureFormat(width: Int, height: Int, frameRate: Int) {
        Log.d(TAG, "Changing capture format: ${width}x${height}@${frameRate}fps")
        
        captureWidth = width
        captureHeight = height
        captureFrameRate = frameRate.coerceIn(1, 60)
        frameIntervalMs = (1000 / captureFrameRate).toLong()
        
        // Recreate bitmap with new dimensions
        synchronized(this) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(captureWidth, captureHeight, Bitmap.Config.ARGB_8888)
            canvas = Canvas(bitmap!!)
        }
    }

    override fun dispose() {
        Log.d(TAG, "Disposing whiteboard capturer")
        stopCapture()
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        capturerObserver = null
    }

    override fun isScreencast(): Boolean = true // Treat as screencast for better quality

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isCapturing.get()) return
            
            try {
                captureFrame()
            } catch (e: Exception) {
                Log.e(TAG, "Error capturing frame", e)
            }
            
            handler?.postDelayed(this, frameIntervalMs)
        }
    }

    private fun captureFrame() {
        val bmp = bitmap ?: return
        val cvs = canvas ?: return
        val observer = capturerObserver ?: return
        
        synchronized(this) {
            // Clear canvas with white background
            cvs.drawColor(Color.WHITE)
            
            // Draw grid if using image background
            if (useImageBackgroundProvider()) {
                drawGrid(cvs)
            }
            
            // Get current shapes and draw them
            val shapes = shapesProvider()
            shapes.forEach { shape ->
                drawShape(cvs, shape)
            }
        }
        
        // Convert bitmap to I420 and create VideoFrame
        val i420Buffer = bitmapToI420(bmp)
        val videoFrame = VideoFrame(i420Buffer, 0, System.nanoTime())
        
        observer.onFrameCaptured(videoFrame)
        videoFrame.release()
    }

    private fun drawGrid(canvas: Canvas) {
        val gridSize = 20f
        val gridPaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 0.5f
        }
        
        // Vertical lines
        var x = 0f
        while (x <= captureWidth) {
            canvas.drawLine(x, 0f, x, captureHeight.toFloat(), gridPaint)
            x += gridSize
        }
        
        // Horizontal lines
        var y = 0f
        while (y <= captureHeight) {
            canvas.drawLine(0f, y, captureWidth.toFloat(), y, gridPaint)
            y += gridSize
        }
    }

    private fun drawShape(canvas: Canvas, shape: WhiteboardShape) {
        when (shape.type) {
            WhiteboardShapeType.FREEHAND -> drawFreehand(canvas, shape)
            WhiteboardShapeType.LINE -> drawLine(canvas, shape)
            WhiteboardShapeType.RECTANGLE -> drawRectangle(canvas, shape)
            WhiteboardShapeType.CIRCLE -> drawCircle(canvas, shape)
            WhiteboardShapeType.OVAL -> drawOval(canvas, shape)
            WhiteboardShapeType.TRIANGLE -> drawPolygon(canvas, shape, 3)
            WhiteboardShapeType.PENTAGON -> drawPolygon(canvas, shape, 5)
            WhiteboardShapeType.HEXAGON -> drawPolygon(canvas, shape, 6)
            WhiteboardShapeType.OCTAGON -> drawPolygon(canvas, shape, 8)
            WhiteboardShapeType.RHOMBUS -> drawRhombus(canvas, shape)
            WhiteboardShapeType.PARALLELOGRAM -> drawParallelogram(canvas, shape)
            WhiteboardShapeType.TEXT -> drawText(canvas, shape)
            WhiteboardShapeType.IMAGE -> { /* Images handled separately */ }
        }
    }

    private fun drawFreehand(canvas: Canvas, shape: WhiteboardShape) {
        val points = shape.points
        if (points.size < 2) return
        
        setupPaint(shape)
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        
        for (i in 1 until points.size) {
            val p0 = points[i - 1]
            val p1 = points[i]
            val midX = (p0.x + p1.x) / 2
            val midY = (p0.y + p1.y) / 2
            path.quadTo(p0.x, p0.y, midX, midY)
        }
        
        val last = points.last()
        path.lineTo(last.x, last.y)
        canvas.drawPath(path, paint)
    }

    private fun drawLine(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)
    }

    private fun drawRectangle(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val left = minOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val right = maxOf(start.x, end.x)
        val bottom = maxOf(start.y, end.y)
        
        canvas.drawRect(left, top, right, bottom, paint)
    }

    private fun drawCircle(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val centerX = (start.x + end.x) / 2
        val centerY = (start.y + end.y) / 2
        val radius = minOf(abs(end.x - start.x), abs(end.y - start.y)) / 2
        
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    private fun drawOval(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val rect = RectF(
            minOf(start.x, end.x),
            minOf(start.y, end.y),
            maxOf(start.x, end.x),
            maxOf(start.y, end.y)
        )
        canvas.drawOval(rect, paint)
    }

    private fun drawPolygon(canvas: Canvas, shape: WhiteboardShape, sides: Int) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val centerX = (start.x + end.x) / 2
        val centerY = (start.y + end.y) / 2
        val radius = minOf(abs(end.x - start.x), abs(end.y - start.y)) / 2
        
        val path = Path()
        val angleStep = 2 * PI / sides
        val startAngle = -PI / 2 // Start from top
        
        for (i in 0 until sides) {
            val angle = startAngle + i * angleStep
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawRhombus(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val centerX = (start.x + end.x) / 2
        val centerY = (start.y + end.y) / 2
        val halfWidth = abs(end.x - start.x) / 2
        val halfHeight = abs(end.y - start.y) / 2
        
        val path = Path()
        path.moveTo(centerX, centerY - halfHeight) // Top
        path.lineTo(centerX + halfWidth, centerY) // Right
        path.lineTo(centerX, centerY + halfHeight) // Bottom
        path.lineTo(centerX - halfWidth, centerY) // Left
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawParallelogram(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return
        setupPaint(shape)
        
        val offset = abs(end.x - start.x) * 0.2f
        val left = minOf(start.x, end.x)
        val right = maxOf(start.x, end.x)
        val top = minOf(start.y, end.y)
        val bottom = maxOf(start.y, end.y)
        
        val path = Path()
        path.moveTo(left + offset, top)
        path.lineTo(right, top)
        path.lineTo(right - offset, bottom)
        path.lineTo(left, bottom)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawText(canvas: Canvas, shape: WhiteboardShape) {
        val start = shape.start ?: return
        val text = shape.text ?: return
        
        textPaint.color = shape.color.toAndroidColor()
        textPaint.textSize = shape.fontSize ?: 20f
        textPaint.typeface = Typeface.DEFAULT
        
        canvas.drawText(text, start.x, start.y, textPaint)
    }

    private fun setupPaint(shape: WhiteboardShape) {
        paint.color = shape.color.toAndroidColor()
        paint.strokeWidth = shape.thickness
        paint.style = Paint.Style.STROKE
        
        paint.pathEffect = when (shape.lineType) {
            LineType.DASHED -> DashPathEffect(floatArrayOf(15f, 10f), 0f)
            LineType.DOTTED -> DashPathEffect(floatArrayOf(5f, 5f), 0f)
            else -> null
        }
    }

    /**
     * Convert Compose Color to Android Color int
     */
    private fun androidx.compose.ui.graphics.Color.toAndroidColor(): Int {
        return Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }

    /**
     * Convert ARGB_8888 Bitmap to I420 buffer for WebRTC
     */
    private fun bitmapToI420(bitmap: Bitmap): JavaI420Buffer {
        val width = bitmap.width
        val height = bitmap.height
        val strideY = width
        val strideUV = (width + 1) / 2
        val chromaHeight = (height + 1) / 2
        
        val ySize = strideY * height
        val uvSize = strideUV * chromaHeight
        
        val i420Buffer = JavaI420Buffer.allocate(width, height)
        val yBuffer = i420Buffer.dataY
        val uBuffer = i420Buffer.dataU
        val vBuffer = i420Buffer.dataV
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var yIndex = 0
        var uvIndex = 0
        
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = pixels[j * width + i]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // RGB to Y
                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                yBuffer.put(yIndex++, y.coerceIn(0, 255).toByte())
                
                // Sample U and V at half resolution
                if (j % 2 == 0 && i % 2 == 0) {
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    uBuffer.put(uvIndex, u.coerceIn(0, 255).toByte())
                    vBuffer.put(uvIndex, v.coerceIn(0, 255).toByte())
                    uvIndex++
                }
            }
        }
        
        return i420Buffer
    }

    companion object {
        /**
         * Creates a WhiteboardVideoCapturer for capturing whiteboard content.
         */
        fun create(
            context: Context,
            shapesProvider: () -> List<WhiteboardShape>,
            useImageBackgroundProvider: () -> Boolean = { false }
        ): WhiteboardVideoCapturer {
            return WhiteboardVideoCapturer(context, shapesProvider, useImageBackgroundProvider)
        }
    }
}
