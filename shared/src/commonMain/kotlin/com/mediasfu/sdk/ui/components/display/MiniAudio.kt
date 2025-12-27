package com.mediasfu.sdk.ui.components.display
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.ui.*

/**
 * MiniAudio - Displays a mini audio card for compact participant view.
 *
 * Shows minimal participant info with audio indicator for compact layouts.
 *
 * @property options Configuration options for the mini audio
 */
data class MiniAudioOptions(
    val name: String,
    val visible: Boolean = true,
    val showWaveform: Boolean = true,
    val customStyle: Map<String, Any> = emptyMap(),
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val imageSource: String = "",
    val roundedImage: Boolean = true,
    val audioDecibels: List<AudioDecibels> = emptyList(),
)

interface MiniAudio : MediaSfuUIComponent {
    val options: MiniAudioOptions
    override val id: String get() = "mini_audio"
    override val isVisible: Boolean get() = options.visible
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
     * Gets audio level for waveform
     */
    fun getAudioLevel(): Double {
        val decibel = options.audioDecibels.find { it.name == options.name }
        return decibel?.averageLoudness ?: 0.0
    }
    
    /**
     * Gets container style for mini audio
     */
    fun getContainerStyle(): Map<String, Any> {
        val style = mutableMapOf<String, Any>(
            "backgroundColor" to options.backgroundColor,
            "borderRadius" to if (options.roundedImage) "50%" else "4px",
            "width" to "48px",
            "height" to "48px",
            "display" to "flex",
            "alignItems" to "center",
            "justifyContent" to "center",
            "position" to "relative"
        )
        style.putAll(options.customStyle)
        return style
    }
}

/**
 * Default implementation of MiniAudio
 */
class DefaultMiniAudio(
    override val options: MiniAudioOptions
) : MiniAudio {
    fun render(): Any {
        return mapOf(
            "type" to "miniAudio",
            "name" to options.name,
            "initials" to getInitials(),
            "audioLevel" to getAudioLevel(),
            "showWaveform" to options.showWaveform,
            "containerStyle" to getContainerStyle(),
            "imageSource" to options.imageSource
        )
    }
}

// Default waveform durations matching Flutter (in milliseconds)
private val defaultWaveformDurations = listOf(474, 433, 407, 458, 400, 427, 441, 419, 487)

/**
 * Composable render function for MiniAudio.
 */
@Composable
fun MiniAudio.renderCompose(modifier: Modifier = Modifier) {
    if (!options.visible) return
    
    val bgColor = Color(options.backgroundColor)
    val shape = if (options.roundedImage) CircleShape else RoundedCornerShape(6.dp)
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(shape)
            .background(bgColor.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = getInitials(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1
            )
            
            if (options.showWaveform) {
                Spacer(modifier = Modifier.height(4.dp))
                
                // Animated waveform bars matching Flutter
                Row(
                    modifier = Modifier.height(30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    defaultWaveformDurations.forEachIndexed { index, duration ->
                        val infiniteTransition = rememberInfiniteTransition(label = "waveBar$index")
                        
                        // Stagger the animation phase for each bar
                        val phase = index / (defaultWaveformDurations.size + 1).toFloat()
                        
                        val animatedHeight by infiniteTransition.animateFloat(
                            initialValue = phase,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(
                                    durationMillis = duration,
                                    easing = FastOutSlowInEasing
                                ),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "barHeight$index"
                        )
                        
                        val barHeight = (30.dp.value * animatedHeight.coerceIn(0f, 1f)).dp.coerceAtLeast(1.dp)
                        
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .height(barHeight)
                                .background(
                                    color = Color(0xFFF51C1C),
                                    shape = RoundedCornerShape(3.dp)
                                )
                        )
                        
                        if (index < defaultWaveformDurations.size - 1) {
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Text(
                text = options.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White,
                maxLines = 1
            )
        }
    }
}
