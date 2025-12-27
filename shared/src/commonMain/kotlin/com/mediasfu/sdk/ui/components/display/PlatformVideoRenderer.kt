package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.webrtc.MediaStreamTrack

@Composable
expect fun PlatformVideoRenderer(
    track: MediaStreamTrack,
    doMirror: Boolean,
    forceFullDisplay: Boolean,
    modifier: Modifier = Modifier
)

@Composable
internal fun VideoRendererFallback(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
