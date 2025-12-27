package com.mediasfu.sdk.ui.components.background

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

/**
 * Android implementation of ProcessedFrameView.
 * Renders an android.graphics.Bitmap to a Compose Image.
 */
@Composable
actual fun ProcessedFrameView(
    bitmap: Any?,
    modifier: Modifier
) {
    val androidBitmap = bitmap as? Bitmap
    
    if (androidBitmap != null && !androidBitmap.isRecycled) {
        Image(
            bitmap = androidBitmap.asImageBitmap(),
            contentDescription = "Processed video preview",
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    }
}
