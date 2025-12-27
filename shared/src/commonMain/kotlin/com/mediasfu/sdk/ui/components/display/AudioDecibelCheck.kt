package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.ui.MediaSfuUIComponent

/**
 * AudioDecibelCheck - Displays audio waveform animation based on decibel levels.
 *
 * Shows animated waveform bars that respond to audio levels for visual feedback.
 *
 * @property options Configuration options for the audio decibel check
 */
data class AudioDecibelCheckOptions(
    val name: String,
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val barColor: Int = 0xFFE82E2E.toInt(), // Red
    val waveformColor: Int = 0xFF4CAF50.toInt(), // Green
    val customStyle: Map<String, Any> = emptyMap(),
)

interface AudioDecibelCheck : MediaSfuUIComponent {
    val options: AudioDecibelCheckOptions
    override val id: String get() = "audio_decibel_check"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets current audio level for the participant
     */
    fun getAudioLevel(): Double {
        val decibel = options.audioDecibels.find { it.name == options.name }
        return decibel?.averageLoudness ?: 0.0
    }
    
    /**
     * Determines if waveform should be visible/animated
     */
    fun isActive(): Boolean {
        return getAudioLevel() > 0.0
    }
    
    /**
     * Calculates waveform bar heights based on audio level
     */
    fun getWaveformHeights(): List<Double> {
        val level = getAudioLevel()
        if (level == 0.0) return List(5) { 0.2 }
        
        // Generate 5 bar heights with variation
        val baseHeight = level / 127.5 // Normalize to 0-1
        return listOf(
            baseHeight * 0.6,
            baseHeight * 0.9,
            baseHeight * 1.0,
            baseHeight * 0.8,
            baseHeight * 0.5
        )
    }
}

/**
 * Default implementation of AudioDecibelCheck
 */
class DefaultAudioDecibelCheck(
    override val options: AudioDecibelCheckOptions
) : AudioDecibelCheck {
    fun render(): Any {
        return mapOf(
            "type" to "audioDecibelCheck",
            "name" to options.name,
            "audioLevel" to getAudioLevel(),
            "isActive" to isActive(),
            "waveformHeights" to getWaveformHeights(),
            "barColor" to options.barColor,
            "waveformColor" to options.waveformColor,
            "customStyle" to options.customStyle
        )
    }
}

/**
 * Composable extension for rendering AudioDecibelCheck in Jetpack Compose
 */
@Composable
fun AudioDecibelCheck.renderCompose() {
    Row(
        modifier = Modifier
            .height(24.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = getWaveformHeights()
        val color = if (isActive()) Color(options.waveformColor) else Color(options.barColor).copy(alpha = 0.3f)
        
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((height * 20).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

