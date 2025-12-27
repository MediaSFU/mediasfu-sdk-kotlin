package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.ui.*

/**
 * MiniCard - Displays a mini video/audio card for compact participant view.
 *
 * Shows compact participant card with optional video stream or initials.
 *
 * @property options Configuration options for the mini card
 */
data class MiniCardOptions(
    val name: String,
    val showVideo: Boolean = true,
    val customStyle: Map<String, Any> = emptyMap(),
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val imageSource: String = "",
    val roundedImage: Boolean = false,
    val videoStream: MediaStream? = null,
    val participant: Participant,
)

interface MiniCard : MediaSfuUIComponent {
    val options: MiniCardOptions
    override val id: String get() = "mini_card"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets initials from participant name
     */
    fun getInitials(): String {
        val parts = options.name.split(" ")
        return when {
            parts.size >= 2 -> "${parts[0].firstOrNull()?.uppercase() ?: ""}${parts[1].firstOrNull()?.uppercase() ?: ""}"
            parts.isNotEmpty() -> parts[0].take(2).uppercase()
            else -> "?"
        }
    }
    
    /**
     * Checks if video should be displayed
     */
    fun shouldShowVideo(): Boolean {
        return options.showVideo && options.videoStream != null
    }
    
    /**
     * Gets container style for mini card
     */
    fun getContainerStyle(): Map<String, Any> {
        val style = mutableMapOf<String, Any>(
            "backgroundColor" to options.backgroundColor,
            "borderRadius" to if (options.roundedImage) "8px" else "4px",
            "width" to "100px",
            "height" to "75px",
            "display" to "flex",
            "alignItems" to "center",
            "justifyContent" to "center",
            "position" to "relative",
            "overflow" to "hidden"
        )
        style.putAll(options.customStyle)
        return style
    }
}

/**
 * Default implementation of MiniCard
 */
class DefaultMiniCard(
    override val options: MiniCardOptions
) : MiniCard {
    fun render(): Any {
        return mapOf(
            "type" to "miniCard",
            "name" to options.name,
            "initials" to getInitials(),
            "showVideo" to shouldShowVideo(),
            "videoStream" to options.videoStream,
            "containerStyle" to getContainerStyle(),
            "imageSource" to options.imageSource,
            "participant" to options.participant,
            "displayLabel" to options.name
        )
    }
}

/**
 * Composable render function for MiniCard.
 * 
 * This extension function renders the MiniCard using Jetpack Compose,
 * utilizing the interface's helper methods for customization.
 * Users can override getInitials(), shouldShowVideo(), and getContainerStyle()
 * in custom implementations to customize behavior.
 */
@Composable
fun MiniCard.renderCompose(
    modifier: Modifier = Modifier
) {
    val initials = getInitials()
    val style = getContainerStyle()
    
    // Extract style values
    val bgColor = Color((style["backgroundColor"] as? Int) ?: options.backgroundColor)
    val cornerRadius = if (options.roundedImage) 8.dp else 4.dp
    val horizontalPadding = 8.dp
    val label = options.name.trim().ifEmpty { initials }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
        )
    }
}
