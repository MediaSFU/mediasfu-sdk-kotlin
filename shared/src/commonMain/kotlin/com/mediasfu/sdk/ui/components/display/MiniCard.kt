package com.mediasfu.sdk.ui.components.display

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
    val initials = getInitials().ifEmpty { "?" }
    val avatarText = remember(options.name) {
        options.name.ifBlank { "?" }
    }
    val imageSource = remember(options.imageSource) { options.imageSource.trim() }
    var mounted by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (mounted) 1f else 0.8f,
        animationSpec = tween(durationMillis = 250),
        label = "miniCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (mounted) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "miniCardAlpha"
    )
    val gradientBackground = remember {
        Brush.linearGradient(
            colors = listOf(
                Color(0xFF6366F1),
                Color(0xFF8B5CF6),
                Color(0xFFEC4899)
            ),
            start = Offset.Zero,
            end = Offset(640f, 640f)
        )
    }

    LaunchedEffect(Unit) {
        mounted = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                alpha = alpha
            ),
        contentAlignment = Alignment.Center
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val innerSize = minOf(maxWidth * 0.72f, maxHeight * 0.72f, 80.dp)
            val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = if (imageSource.isBlank()) 0.4f else 0.2f)
            val cardBackground = if (imageSource.isBlank()) {
                gradientBackground
            } else {
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant
                    ),
                    start = Offset.Zero,
                    end = Offset(320f, 320f)
                )
            }

            Box(
                modifier = Modifier
                    .size(innerSize)
                    .shadow(8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(cardBackground)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (imageSource.isNotBlank()) {
                    AsyncImage(
                        model = imageSource,
                        contentDescription = options.name.ifBlank { avatarText },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (imageSource.isBlank()) {
                    Text(
                        text = avatarText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.3f),
                                offset = Offset(0f, 1f),
                                blurRadius = 4f
                            )
                        )
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0x26FFFFFF),
                                Color.Transparent
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.5f, size.height * 0.5f)
                        )
                    )
                }
            }
        }
    }
}
