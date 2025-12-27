package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

typealias AudioControlAction = (Participant) -> Unit

/**
 * AudioCard - Displays an audio-only participant card with waveform animation.
 *
 * Shows participant name, avatar/initials, and audio level waveform animation
 * based on decibel levels.
 *
 * @property options Configuration options for the audio card
 */
data class AudioCardOptions(
    val name: String,
    val barColor: Int = 0xFFE82E2E.toInt(), // Red
    val textColor: Int = 0xFF191919.toInt(), // Dark gray
    val customStyle: Map<String, Any> = emptyMap(),
    val controlsPosition: String = "topLeft",
    val infoPosition: String = "topRight",
    val participant: Participant,
    val backgroundColor: Int = 0xFF2C678F.toInt(), // Blue
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val roundedImage: Boolean = false,
    val imageSource: String = "",
    val showControls: Boolean = true,
    val showInfo: Boolean = true,
    val showWaveform: Boolean = true,
    val waveformColor: Int = 0xFF4CAF50.toInt(), // Green
    val onToggleAudio: AudioControlAction? = null,
    val onToggleVideo: AudioControlAction? = null,
)

interface AudioCard : MediaSfuUIComponent {
    val options: AudioCardOptions
    override val id: String get() = "audio_card"
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
        val decibel = options.audioDecibels.find { it.name.equals(options.name, ignoreCase = true) }
            ?: options.audioDecibels.find { it.name.equals(options.participant.name, ignoreCase = true) }
        return decibel?.averageLoudness ?: 0.0
    }
    
    /**
     * Gets initials from participant name for avatar
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
     * Checks if waveform should be animated based on audio level
     */
    fun shouldAnimateWaveform(): Boolean {
        return options.showWaveform && getAudioLevel() > 0.0
    }
}

/**
 * Default implementation of AudioCard
 */
class DefaultAudioCard(
    override val options: AudioCardOptions
) : AudioCard {
    fun render(): Any {
        return mapOf(
            "type" to "audioCard",
            "name" to options.name,
            "initials" to getInitials(),
            "audioLevel" to getAudioLevel(),
            "animateWaveform" to shouldAnimateWaveform(),
            "barColor" to options.barColor,
            "textColor" to options.textColor,
            "backgroundColor" to options.backgroundColor,
            "waveformColor" to options.waveformColor,
            "showControls" to options.showControls,
            "showInfo" to options.showInfo,
            "controlsPosition" to options.controlsPosition,
            "infoPosition" to options.infoPosition,
            "imageSource" to options.imageSource,
            "roundedImage" to options.roundedImage,
            "customStyle" to options.customStyle
        )
    }
}

/**
 * Composable render function for AudioCard.
 */
@Composable
fun AudioCard.renderCompose(modifier: Modifier = Modifier) {
    val initials = getInitials()
    val audioLevel = getAudioLevel()
    val bgColor = Color(options.backgroundColor)
    val textColor = Color(options.textColor)
    val waveColor = Color(options.waveformColor)
    val infoAlignment = options.infoPosition.toOverlayAlignment()
    val controlsAlignment = options.controlsPosition.toOverlayAlignment()
    val shouldShowWaveform = options.showWaveform && (!options.participant.muted || audioLevel > 0.0)
    val cardShape = if (options.roundedImage) RoundedCornerShape(16.dp) else RoundedCornerShape(10.dp)
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (options.showInfo) {
            Row(
                modifier = Modifier
                    .align(infoAlignment)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = options.name,
                    color = textColor,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (shouldShowWaveform) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        AudioWaveform(
                            audioLevel = audioLevel,
                            barColor = waveColor,
                            modifier = Modifier.widthIn(min = 44.dp, max = 56.dp)
                        )
                    }
                }
            }
        }

        // Controls positioned matching Flutter: topLeft by default, with smaller icons (14dp)
        // Flutter uses: padding: const EdgeInsets.all(2), borderRadius: 0, icon size: 14
        if (options.showControls) {
            val isMuted = options.participant.muted
            val isVideoOn = options.participant.videoOn
            Row(
                modifier = Modifier
                    .align(controlsAlignment)
                    .padding(0.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AudioControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isMuted) "Unmute ${options.name}" else "Mute ${options.name}",
                    tint = if (isMuted) Color.Red else Color(0xFF4CAF50),
                    enabled = options.onToggleAudio != null,
                ) {
                    coroutineScope.launch {
                        options.onToggleAudio?.invoke(options.participant)
                    }
                }

                AudioControlButton(
                    icon = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = if (isVideoOn) "Turn off video for ${options.name}" else "Turn on video for ${options.name}",
                    tint = if (isVideoOn) Color(0xFF4CAF50) else Color.Red,
                    enabled = options.onToggleVideo != null,
                ) {
                    coroutineScope.launch {
                        options.onToggleVideo?.invoke(options.participant)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioControlButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    // Match Flutter: padding: EdgeInsets.all(2), borderRadius: 0, icon size: 14
    val background = Color.White.copy(alpha = 0.25f)
    Box(
        modifier = Modifier
            .background(background)
            .padding(2.dp)
            .let { base -> if (enabled) base.clickable(onClick = onClick) else base },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
    }
}

/**
 * Animated audio waveform visualization.
 * Shows animated bars based on audio level when participant is unmuted.
 * Uses animation loop to continuously randomize bar heights like Flutter.
 */
@Composable
private fun AudioWaveform(
    audioLevel: Double,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    // 9 bars like Flutter's waveform - animated with random heights
    val barHeights = remember { mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f) }
    
    // Animation loop: update bar heights every 100ms like Flutter's AnimationController
    LaunchedEffect(audioLevel) {
        while (true) {
            // Generate random heights (0-14dp) like Flutter's Random().nextDouble() * 14
            for (i in 0 until 9) {
                barHeights[i] = if (audioLevel > 0.0) {
                    (kotlin.random.Random.nextFloat() * 14f).coerceAtLeast(2f)
                } else {
                    2f
                }
            }
            delay(100L) // ~10 fps animation
        }
    }
    
    Row(
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(14.dp)
    ) {
        barHeights.forEach { height ->
            Surface(
                modifier = Modifier
                    .width(5.dp)
                    .height(height.dp),
                color = barColor
            ) {}
        }
    }
}

private fun String?.toOverlayAlignment(): Alignment {
    return when (this) {
        "topLeft" -> Alignment.TopStart
        "topRight" -> Alignment.TopEnd
        "bottomLeft" -> Alignment.BottomStart
        "bottomRight" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }
}
