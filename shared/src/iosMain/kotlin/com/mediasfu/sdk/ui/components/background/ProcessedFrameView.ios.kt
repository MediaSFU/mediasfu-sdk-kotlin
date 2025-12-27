package com.mediasfu.sdk.ui.components.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * iOS implementation of ProcessedFrameView.
 * TODO: Implement actual UIImage rendering when iOS ML Kit is added.
 */
@Composable
actual fun ProcessedFrameView(
    bitmap: Any?,
    modifier: Modifier
) {
    // Placeholder - iOS virtual background processing not yet implemented
    Box(modifier = modifier.background(Color.DarkGray))
}
