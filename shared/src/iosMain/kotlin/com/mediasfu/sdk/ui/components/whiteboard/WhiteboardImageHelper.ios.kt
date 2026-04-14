package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import com.mediasfu.sdk.model.LineType
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.PaintMode
import org.jetbrains.skia.PaintStrokeCap
import org.jetbrains.skia.PaintStrokeJoin
import org.jetbrains.skia.Path
import org.jetbrains.skia.PathEffect
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGRectGetMidX
import platform.CoreGraphics.CGRectGetMidY
import platform.CoreGraphics.CGRectMake
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

private var activeImagePickerDelegate: IOSWhiteboardImagePickerDelegate? = null

/**
 * iOS implementation of image picker for whiteboard.
 * Uses UIImagePickerController with the photo library.
 */
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (WhiteboardImageResult?) -> Unit
): () -> Unit {
    val currentCallback = rememberUpdatedState(onImagePicked)
    return remember {
        {
            presentImagePicker { result ->
                currentCallback.value(result)
            }
        }
    }
}

/**
 * iOS implementation of canvas sharing.
 * Renders the whiteboard to PNG and presents a native share sheet.
 */
actual fun shareWhiteboardCanvas(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean,
    onComplete: (success: Boolean, message: String) -> Unit
) {
    val presenter = findPresentationController()
    if (presenter == null) {
        onComplete(false, "Unable to share: iOS presenter not available")
        return
    }

    CoroutineScope(Dispatchers.Default).launch {
        runCatching {
            val width = if (canvasWidth > 100) canvasWidth else 1080
            val height = if (canvasHeight > 100) canvasHeight else 1920
            renderWhiteboardPng(
                shapes = shapes,
                canvasWidth = width,
                canvasHeight = height,
                useImageBackground = useImageBackground
            )
        }.fold(
            onSuccess = { pngBytes ->
                withContext(Dispatchers.Main) {
                    presentShareSheet(
                        presenter = presenter,
                        pngBytes = pngBytes,
                        onComplete = onComplete
                    )
                }
            },
            onFailure = { error ->
                withContext(Dispatchers.Main) {
                    onComplete(false, "Failed to prepare whiteboard image: ${error.message}")
                }
            }
        )
    }
}

/**
 * iOS implementation for decoding image ByteArray to ImageBitmap.
 */
actual fun decodeImageBitmap(imageData: ByteArray): ImageBitmap? {
    return runCatching {
        Image.makeFromEncoded(imageData).toComposeImageBitmap()
    }.getOrNull()
}

internal fun renderWhiteboardFramePng(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean
): ByteArray {
    return renderWhiteboardPng(
        shapes = shapes,
        canvasWidth = canvasWidth,
        canvasHeight = canvasHeight,
        useImageBackground = useImageBackground
    )
}

private fun presentImagePicker(onImagePicked: (WhiteboardImageResult?) -> Unit) {
    val presenter = findPresentationController()
    if (presenter == null) {
        onImagePicked(null)
        return
    }

    val picker = UIImagePickerController().apply {
        sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
    }

    val delegate = IOSWhiteboardImagePickerDelegate(onImagePicked)
    activeImagePickerDelegate = delegate
    picker.delegate = delegate
    presenter.presentViewController(picker, animated = true, completion = null)
}

@OptIn(ExperimentalForeignApi::class)
private fun presentShareSheet(
    presenter: UIViewController,
    pngBytes: ByteArray,
    onComplete: (success: Boolean, message: String) -> Unit
) {
    val data = pngBytes.toNSData()
    val image = UIImage(data = data) ?: run {
        onComplete(false, "Unable to create iOS share image")
        return
    }

    val activityController = UIActivityViewController(
        activityItems = listOf(image),
        applicationActivities = null
    )

    activityController.completionWithItemsHandler = { _, completed, _, error ->
        when {
            error != null -> onComplete(false, error.localizedDescription ?: "Share failed")
            completed -> onComplete(true, "Share dialog completed")
            else -> onComplete(false, "Share cancelled")
        }
    }

    activityController.popoverPresentationController?.let { popover ->
        popover.sourceView = presenter.view
        val bounds = presenter.view.bounds
        popover.sourceRect = CGRectMake(CGRectGetMidX(bounds), CGRectGetMidY(bounds), 1.0, 1.0)
    }

    presenter.presentViewController(activityController, animated = true, completion = null)
}

private fun findPresentationController(): UIViewController? {
    val application = UIApplication.sharedApplication
    val rootController = application.keyWindow?.rootViewController
        ?: application.windows
            .filterIsInstance<UIWindow>()
            .firstOrNull { it.isKeyWindow() }
            ?.rootViewController
    return rootController?.topMostController()
}

private fun renderWhiteboardPng(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean
): ByteArray {
    val surface = Surface.makeRasterN32Premul(canvasWidth, canvasHeight)
    val canvas = surface.canvas
    canvas.clear(0xFFFFFFFF.toInt())

    if (useImageBackground) {
        drawGrid(canvas, canvasWidth, canvasHeight)
    }

    shapes.forEach { drawShape(canvas, it) }

    val pngData = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.PNG)
        ?: error("Unable to encode whiteboard PNG")
    return pngData.bytes
}

private fun drawGrid(canvas: Canvas, width: Int, height: Int) {
    val paint = Paint().apply {
        color = 0xFFE0E0E0.toInt()
        mode = PaintMode.STROKE
        strokeWidth = 1f
        isAntiAlias = true
    }

    var x = 0f
    while (x <= width) {
        canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        x += 20f
    }

    var y = 0f
    while (y <= height) {
        canvas.drawLine(0f, y, width.toFloat(), y, paint)
        y += 20f
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
        WhiteboardShapeType.IMAGE -> drawImageShape(canvas, shape)
    }
}

private fun createStrokePaint(shape: WhiteboardShape): Paint {
    return Paint().apply {
        color = shape.color.toArgb()
        mode = PaintMode.STROKE
        strokeWidth = shape.thickness
        isAntiAlias = true
        strokeCap = PaintStrokeCap.ROUND
        strokeJoin = PaintStrokeJoin.ROUND
        pathEffect = when (shape.lineType) {
            LineType.DASHED -> PathEffect.makeDash(floatArrayOf(10f, 10f), 0f)
            LineType.DOTTED -> PathEffect.makeDash(floatArrayOf(2f, 8f), 0f)
            LineType.DASH_DOT -> PathEffect.makeDash(floatArrayOf(10f, 5f, 2f, 5f), 0f)
            else -> null
        }
    }
}

private fun drawFreehand(canvas: Canvas, shape: WhiteboardShape) {
    if (shape.points.size < 2) return
    val path = Path().apply {
        moveTo(shape.points.first().x, shape.points.first().y)
        shape.points.drop(1).forEach { lineTo(it.x, it.y) }
    }
    canvas.drawPath(path, createStrokePaint(shape))
}

private fun drawLine(canvas: Canvas, shape: WhiteboardShape) {
    val start = shape.start ?: shape.points.getOrNull(0) ?: return
    val end = shape.end ?: shape.points.getOrNull(1) ?: return
    canvas.drawLine(start.x, start.y, end.x, end.y, createStrokePaint(shape))
}

private fun drawRectangle(canvas: Canvas, shape: WhiteboardShape) {
    val rect = shapeBounds(shape) ?: return
    canvas.drawRect(rect, createStrokePaint(shape))
}

private fun drawCircle(canvas: Canvas, shape: WhiteboardShape) {
    val start = shape.start ?: shape.points.getOrNull(0) ?: return
    val end = shape.end ?: shape.points.getOrNull(1) ?: return
    val centerX = (start.x + end.x) / 2f
    val centerY = (start.y + end.y) / 2f
    val radius = min(abs(end.x - start.x), abs(end.y - start.y)) / 2f
    canvas.drawCircle(centerX, centerY, radius, createStrokePaint(shape))
}

private fun drawOval(canvas: Canvas, shape: WhiteboardShape) {
    val rect = shapeBounds(shape) ?: return
    canvas.drawOval(rect, createStrokePaint(shape))
}

private fun drawPolygon(canvas: Canvas, shape: WhiteboardShape, sides: Int) {
    val start = shape.start ?: shape.points.getOrNull(0) ?: return
    val end = shape.end ?: shape.points.getOrNull(1) ?: return
    val centerX = (start.x + end.x) / 2f
    val centerY = (start.y + end.y) / 2f
    val radius = min(abs(end.x - start.x), abs(end.y - start.y)) / 2f
    val angleStep = (2 * PI) / sides
    val path = Path().apply {
        for (index in 0 until sides) {
            val angle = angleStep * index - PI / 2
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        closePath()
    }
    canvas.drawPath(path, createStrokePaint(shape))
}

private fun drawRhombus(canvas: Canvas, shape: WhiteboardShape) {
    val rect = shapeBounds(shape) ?: return
    val cx = rect.left + rect.width / 2f
    val cy = rect.top + rect.height / 2f
    val path = Path().apply {
        moveTo(cx, rect.top)
        lineTo(rect.right, cy)
        lineTo(cx, rect.bottom)
        lineTo(rect.left, cy)
        closePath()
    }
    canvas.drawPath(path, createStrokePaint(shape))
}

private fun drawParallelogram(canvas: Canvas, shape: WhiteboardShape) {
    val rect = shapeBounds(shape) ?: return
    val skew = rect.width * 0.2f
    val path = Path().apply {
        moveTo(rect.left + skew, rect.top)
        lineTo(rect.right, rect.top)
        lineTo(rect.right - skew, rect.bottom)
        lineTo(rect.left, rect.bottom)
        closePath()
    }
    canvas.drawPath(path, createStrokePaint(shape))
}

private fun drawText(canvas: Canvas, shape: WhiteboardShape) {
    val origin = shape.start ?: shape.points.firstOrNull() ?: return
    val text = shape.text ?: return
    val font = Font(null, shape.fontSize)
    val paint = Paint().apply {
        color = shape.color.toArgb()
        mode = PaintMode.FILL
        isAntiAlias = true
    }
    canvas.drawString(text, origin.x, origin.y + shape.fontSize, font, paint)
}

private fun drawImageShape(canvas: Canvas, shape: WhiteboardShape) {
    val imageData = shape.imageData ?: return
    val rect = shapeBounds(shape) ?: return
    runCatching {
        val image = Image.makeFromEncoded(imageData)
        canvas.drawImageRect(
            image,
            rect,
            Paint().apply { isAntiAlias = true }
        )
    }
}

private fun shapeBounds(shape: WhiteboardShape): Rect? {
    val start = shape.start ?: shape.points.getOrNull(0) ?: return null
    val end = shape.end ?: shape.points.getOrNull(1) ?: return null
    return Rect.makeLTRB(
        min(start.x, end.x),
        min(start.y, end.y),
        max(start.x, end.x),
        max(start.y, end.y)
    )
}

private fun UIViewController.topMostController(): UIViewController {
    val presented = presentedViewController
    if (presented != null) {
        return presented.topMostController()
    }

    return when (this) {
        is UINavigationController -> visibleViewController?.topMostController() ?: this
        is UITabBarController -> selectedViewController?.topMostController() ?: this
        else -> this
    }
}

@OptIn(ExperimentalForeignApi::class)
private class IOSWhiteboardImagePickerDelegate(
    private val onImagePicked: (WhiteboardImageResult?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        finish(null)
    }

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        val imageUrl = (didFinishPickingMediaWithInfo[UIImagePickerControllerImageURL] as? NSURL)?.absoluteString
        val result = image?.toWhiteboardImageResult(imageUrl)

        picker.dismissViewControllerAnimated(true, completion = null)
        finish(result)
    }

    private fun finish(result: WhiteboardImageResult?) {
        activeImagePickerDelegate = null
        onImagePicked(result)
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun UIImage.toWhiteboardImageResult(imageUrl: String?): WhiteboardImageResult? {
    val pngData = UIImagePNGRepresentation(this) ?: return null
    val cgImage = CGImage ?: return null

    return WhiteboardImageResult(
        imageData = pngData.toByteArray(),
        imageSrc = imageUrl,
        width = CGImageGetWidth(cgImage).toInt(),
        height = CGImageGetHeight(cgImage).toInt()
    )
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size <= 0) return ByteArray(0)

    return ByteArray(size).also { byteArray ->
        byteArray.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    return usePinned { pinned ->
        NSData.dataWithBytes(pinned.addressOf(0), size.toULong())
    }
}
