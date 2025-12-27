package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.mediasfu.sdk.model.WhiteboardShape

/**
 * iOS implementation of image picker for whiteboard.
 * Currently returns a stub - needs native iOS implementation.
 */
@Composable
actual fun rememberImagePickerLauncher(
    onImagePicked: (WhiteboardImageResult?) -> Unit
): () -> Unit {
    // iOS implementation would use PHPickerViewController
    // For now, return a stub that does nothing
    return { 
        // TODO: Implement iOS image picker using PHPickerViewController
        onImagePicked(null)
    }
}

/**
 * iOS implementation of canvas sharing.
 * Currently returns a stub - needs native iOS implementation.
 */
actual fun shareWhiteboardCanvas(
    shapes: List<WhiteboardShape>,
    canvasWidth: Int,
    canvasHeight: Int,
    useImageBackground: Boolean,
    onComplete: (success: Boolean, message: String) -> Unit
) {
    // iOS implementation would use UIActivityViewController
    // For now, return an error
    onComplete(false, "Sharing not yet implemented for iOS")
}

/**
 * iOS implementation for decoding image ByteArray to ImageBitmap.
 * Currently returns null - needs native iOS implementation with UIImage.
 */
actual fun decodeImageBitmap(imageData: ByteArray): ImageBitmap? {
    // iOS implementation would use UIImage(data:) and convert to ImageBitmap
    // For now, return null
    return null
}
