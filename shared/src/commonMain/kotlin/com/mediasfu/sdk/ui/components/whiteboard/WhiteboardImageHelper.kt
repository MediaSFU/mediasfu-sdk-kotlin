package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.datetime.Clock
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import com.mediasfu.sdk.model.LineType

/**
 * Result from image picker operation
 */
data class WhiteboardImageResult(
    /** Base64-encoded image data as ByteArray */
    val imageData: ByteArray?,
    /** Image source URL or local path */
    val imageSrc: String?,
    /** Width of the image in pixels */
    val width: Int,
    /** Height of the image in pixels */
    val height: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as WhiteboardImageResult
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false
        if (imageSrc != other.imageSrc) return false
        if (width != other.width) return false
        if (height != other.height) return false
        return true
    }
    
    override fun hashCode(): Int {
        var result = imageData?.contentHashCode() ?: 0
        result = 31 * result + (imageSrc?.hashCode() ?: 0)
        result = 31 * result + width
        result = 31 * result + height
        return result
    }
}

/**
 * Callbacks for whiteboard image operations
 */
interface WhiteboardImageCallbacks {
    /** Called when an image is picked from the gallery */
    fun onImagePicked(result: WhiteboardImageResult)
    
    /** Called when the canvas is saved/shared successfully */
    fun onSaveSuccess(filePath: String)
    
    /** Called when an operation fails */
    fun onError(message: String)
}

/**
 * Creates a WhiteboardShape for an uploaded image.
 * 
 * @param imageData Image data as ByteArray
 * @param imageSrc Image URL or file path
 * @param width Image width
 * @param height Image height
 * @param canvasCenter Center point to place the image
 */
fun createImageShape(
    imageData: ByteArray?,
    imageSrc: String?,
    width: Int,
    height: Int,
    canvasCenter: Offset,
    userId: String
): WhiteboardShape {
    // Scale image to fit within reasonable bounds (max 300x300)
    val maxDimension = 300f
    val scale = if (width > height) {
        if (width > maxDimension) maxDimension / width else 1f
    } else {
        if (height > maxDimension) maxDimension / height else 1f
    }
    
    val scaledWidth = width * scale
    val scaledHeight = height * scale
    
    val startX = canvasCenter.x - scaledWidth / 2
    val startY = canvasCenter.y - scaledHeight / 2
    
    return WhiteboardShape(
        id = "img_${Clock.System.now().toEpochMilliseconds()}_${(0..9999).random()}",
        type = WhiteboardShapeType.IMAGE,
        start = Offset(startX, startY),
        end = Offset(startX + scaledWidth, startY + scaledHeight),
        points = listOf(
            Offset(startX, startY),
            Offset(startX + scaledWidth, startY + scaledHeight)
        ),
        color = Color.Transparent,
        thickness = 1f,
        lineType = LineType.SOLID,
        text = null,
        fontSize = 20f,
        imageData = imageData,
        imageSrc = imageSrc
    )
}

/**
 * Platform-specific implementation for picking images from gallery.
 * On Android, uses ActivityResultContracts.PickVisualMedia.
 * On iOS, uses PHPickerViewController.
 * 
 * Must be called from a Composable context.
 */
@Composable
expect fun rememberImagePickerLauncher(
    onImagePicked: (WhiteboardImageResult?) -> Unit
): () -> Unit

/**
 * Platform-specific implementation for capturing and sharing the canvas.
 * On Android, captures the composable as bitmap and shares via Intent.ACTION_SEND.
 * On iOS, uses UIActivityViewController.
 * 
 * @param shapes List of shapes to draw
 * @param canvasWidth Canvas width in pixels
 * @param canvasHeight Canvas height in pixels
 * @param useImageBackground Whether to use grid background
 * @param onComplete Callback with result (file path or null on error)
 */
expect fun shareWhiteboardCanvas(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean,
    onComplete: (success: Boolean, message: String) -> Unit
)

/**
 * Platform-specific implementation for decoding image ByteArray to ImageBitmap.
 * On Android, uses BitmapFactory to decode and converts to ImageBitmap.
 * On iOS, uses UIImage and converts to ImageBitmap.
 * 
 * @param imageData ByteArray containing image data (PNG, JPEG, etc.)
 * @return ImageBitmap if decoding succeeds, null otherwise
 */
expect fun decodeImageBitmap(imageData: ByteArray): ImageBitmap?
