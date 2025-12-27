package com.mediasfu.sdk.ui.components.whiteboard

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathDashPathEffect
import android.graphics.DashPathEffect
import android.graphics.RectF
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.mediasfu.sdk.model.LineType
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// Global context reference for sharing (set during composition)
private var appContext: Context? = null

/**
 * Android implementation of image picker for whiteboard.
 * Uses PhotoPicker (ActivityResultContracts.PickVisualMedia) for modern Android versions.
 */
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (WhiteboardImageResult?) -> Unit
): () -> Unit {
    val context = LocalContext.current
    appContext = context.applicationContext // Store context for sharing
    
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) {
            onImagePicked(null)
            return@rememberLauncherForActivityResult
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = loadImageFromUri(context, uri)
                withContext(Dispatchers.Main) {
                    onImagePicked(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onImagePicked(null)
                }
            }
        }
    }
    
    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}

/**
 * Load image from URI and convert to ByteArray with dimensions.
 */
private fun loadImageFromUri(context: Context, uri: Uri): WhiteboardImageResult {
    val inputStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Cannot open input stream for URI")
    
    val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
    inputStream.close()
    
    // Convert to ByteArray (PNG format)
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
    val byteArray = outputStream.toByteArray()
    
    val width = bitmap.width
    val height = bitmap.height
    bitmap.recycle()
    
    return WhiteboardImageResult(
        imageData = byteArray,
        imageSrc = uri.toString(),
        width = width,
        height = height
    )
}

/**
 * Android implementation of canvas sharing.
 * Renders all shapes to a bitmap and shares via Intent.ACTION_SEND.
 */
actual fun shareWhiteboardCanvas(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean,
    onComplete: (success: Boolean, message: String) -> Unit
) {
    val context = appContext
    if (context == null) {
        onComplete(false, "Unable to share: App context not available")
        return
    }
    
    CoroutineScope(Dispatchers.IO).launch {
        try {
            // Use reasonable dimensions if canvas size is too small
            val width = if (canvasWidth > 100) canvasWidth else 1080
            val height = if (canvasHeight > 100) canvasHeight else 1920
            
            // Create bitmap
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            
            // Fill with white background
            canvas.drawColor(android.graphics.Color.WHITE)
            
            // Draw grid if enabled
            if (useImageBackground) {
                drawGrid(canvas, width, height)
            }
            
            // Draw all shapes
            for (shape in shapes) {
                drawShape(canvas, shape)
            }
            
            // Save to app's cache directory for sharing
            val cacheDir = File(context.cacheDir, "whiteboard")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val fileName = "whiteboard_${System.currentTimeMillis()}.png"
            val file = File(cacheDir, fileName)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bitmap.recycle()
            
            // Create content URI using FileProvider
            val contentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } catch (e: Exception) {
                // Fallback: try without FileProvider (may not work on all devices)
                Uri.fromFile(file)
            }
            
            // Create share intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Whiteboard Drawing")
                putExtra(Intent.EXTRA_TEXT, "Check out this whiteboard drawing!")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // Create chooser intent
            val chooserIntent = Intent.createChooser(shareIntent, "Share Whiteboard").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            withContext(Dispatchers.Main) {
                try {
                    context.startActivity(chooserIntent)
                    onComplete(true, "Share dialog opened")
                } catch (e: Exception) {
                    onComplete(false, "Unable to open share dialog: ${e.message}")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onComplete(false, "Failed to share: ${e.message}")
            }
        }
    }
}

/**
 * Draw grid lines on the canvas.
 */
private fun drawGrid(canvas: Canvas, width: Int, height: Int) {
    val paint = Paint().apply {
        color = android.graphics.Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }
    
    val gridSize = 20
    
    // Vertical lines
    var x = 0
    while (x <= width) {
        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), paint)
        x += gridSize
    }
    
    // Horizontal lines
    var y = 0
    while (y <= height) {
        canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), paint)
        y += gridSize
    }
}

/**
 * Draw a single shape on the canvas.
 */
private fun drawShape(canvas: Canvas, shape: WhiteboardShape) {
    val paint = Paint().apply {
        color = shape.color.toArgb()
        strokeWidth = shape.thickness
        style = Paint.Style.STROKE
        isAntiAlias = true
        
        // Apply line type
        pathEffect = when (shape.lineType) {
            LineType.DASHED -> DashPathEffect(floatArrayOf(10f, 10f), 0f)
            LineType.DOTTED -> DashPathEffect(floatArrayOf(2f, 8f), 0f)
            LineType.DASH_DOT -> DashPathEffect(floatArrayOf(10f, 5f, 2f, 5f), 0f)
            else -> null
        }
    }
    
    when (shape.type) {
        WhiteboardShapeType.FREEHAND -> drawFreehand(canvas, shape, paint)
        WhiteboardShapeType.LINE -> drawLine(canvas, shape, paint)
        WhiteboardShapeType.RECTANGLE -> drawRectangle(canvas, shape, paint)
        WhiteboardShapeType.CIRCLE -> drawCircle(canvas, shape, paint)
        WhiteboardShapeType.TRIANGLE -> drawTriangle(canvas, shape, paint)
        WhiteboardShapeType.PENTAGON -> drawPolygon(canvas, shape, paint, 5)
        WhiteboardShapeType.HEXAGON -> drawPolygon(canvas, shape, paint, 6)
        WhiteboardShapeType.OCTAGON -> drawPolygon(canvas, shape, paint, 8)
        WhiteboardShapeType.OVAL -> drawOval(canvas, shape, paint)
        WhiteboardShapeType.RHOMBUS -> drawRhombus(canvas, shape, paint)
        WhiteboardShapeType.PARALLELOGRAM -> drawParallelogram(canvas, shape, paint)
        WhiteboardShapeType.TEXT -> drawText(canvas, shape)
        WhiteboardShapeType.IMAGE -> { /* TODO: Draw image shapes */ }
    }
}

private fun drawFreehand(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    
    for (i in 1 until points.size) {
        path.lineTo(points[i].x, points[i].y)
    }
    
    canvas.drawPath(path, paint)
}

private fun drawLine(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, paint)
}

private fun drawRectangle(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val left = minOf(points[0].x, points[1].x)
    val top = minOf(points[0].y, points[1].y)
    val right = maxOf(points[0].x, points[1].x)
    val bottom = maxOf(points[0].y, points[1].y)
    
    canvas.drawRect(left, top, right, bottom, paint)
}

private fun drawCircle(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val cx = (points[0].x + points[1].x) / 2
    val cy = (points[0].y + points[1].y) / 2
    val radius = kotlin.math.sqrt(
        (points[1].x - points[0].x) * (points[1].x - points[0].x) +
        (points[1].y - points[0].y) * (points[1].y - points[0].y)
    ) / 2
    
    canvas.drawCircle(cx, cy, radius, paint)
}

private fun drawTriangle(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val left = minOf(points[0].x, points[1].x)
    val top = minOf(points[0].y, points[1].y)
    val right = maxOf(points[0].x, points[1].x)
    val bottom = maxOf(points[0].y, points[1].y)
    
    val path = Path()
    path.moveTo((left + right) / 2, top)
    path.lineTo(right, bottom)
    path.lineTo(left, bottom)
    path.close()
    
    canvas.drawPath(path, paint)
}

private fun drawPolygon(canvas: Canvas, shape: WhiteboardShape, paint: Paint, sides: Int) {
    val points = shape.points
    if (points.size < 2) return
    
    val cx = (points[0].x + points[1].x) / 2
    val cy = (points[0].y + points[1].y) / 2
    val radius = kotlin.math.sqrt(
        (points[1].x - points[0].x) * (points[1].x - points[0].x) +
        (points[1].y - points[0].y) * (points[1].y - points[0].y)
    ) / 2
    
    val path = Path()
    val angleStep = 2 * PI / sides
    val startAngle = -PI / 2 // Start from top
    
    for (i in 0 until sides) {
        val angle = startAngle + i * angleStep
        val x = (cx + radius * cos(angle)).toFloat()
        val y = (cy + radius * sin(angle)).toFloat()
        
        if (i == 0) {
            path.moveTo(x, y)
        } else {
            path.lineTo(x, y)
        }
    }
    path.close()
    
    canvas.drawPath(path, paint)
}

private fun drawOval(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val left = minOf(points[0].x, points[1].x)
    val top = minOf(points[0].y, points[1].y)
    val right = maxOf(points[0].x, points[1].x)
    val bottom = maxOf(points[0].y, points[1].y)
    
    canvas.drawOval(RectF(left, top, right, bottom), paint)
}

private fun drawRhombus(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val left = minOf(points[0].x, points[1].x)
    val top = minOf(points[0].y, points[1].y)
    val right = maxOf(points[0].x, points[1].x)
    val bottom = maxOf(points[0].y, points[1].y)
    val cx = (left + right) / 2
    val cy = (top + bottom) / 2
    
    val path = Path()
    path.moveTo(cx, top)
    path.lineTo(right, cy)
    path.lineTo(cx, bottom)
    path.lineTo(left, cy)
    path.close()
    
    canvas.drawPath(path, paint)
}

private fun drawParallelogram(canvas: Canvas, shape: WhiteboardShape, paint: Paint) {
    val points = shape.points
    if (points.size < 2) return
    
    val left = minOf(points[0].x, points[1].x)
    val top = minOf(points[0].y, points[1].y)
    val right = maxOf(points[0].x, points[1].x)
    val bottom = maxOf(points[0].y, points[1].y)
    val skew = (right - left) * 0.2f
    
    val path = Path()
    path.moveTo(left + skew, top)
    path.lineTo(right, top)
    path.lineTo(right - skew, bottom)
    path.lineTo(left, bottom)
    path.close()
    
    canvas.drawPath(path, paint)
}

private fun drawText(canvas: Canvas, shape: WhiteboardShape) {
    val text = shape.text ?: return
    val points = shape.points
    if (points.isEmpty()) return
    
    val paint = Paint().apply {
        color = shape.color.toArgb()
        textSize = shape.fontSize
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    
    canvas.drawText(text, points[0].x, points[0].y, paint)
}

/**
 * Android implementation for decoding image ByteArray to ImageBitmap.
 * Uses BitmapFactory to decode the bytes and converts to Compose ImageBitmap.
 */
actual fun decodeImageBitmap(imageData: ByteArray): ImageBitmap? {
    return try {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
