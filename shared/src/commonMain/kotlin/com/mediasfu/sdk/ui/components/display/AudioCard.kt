package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
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
    val barColor: Int = 0xFF818CF8.toInt(), // Indigo (matches React Modern primary)
    val textColor: Int = 0xFFFFFFFF.toInt(), // White
    val customStyle: Map<String, Any> = emptyMap(),
    val controlsPosition: String = "topLeft",
    val infoPosition: String = "bottomLeft",
    val participant: Participant,
    val backgroundColor: Int = 0xFF1A2540.toInt(), // Dark navy (matches React Modern dark theme)
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val roundedImage: Boolean = false,
    val imageSource: String = "",
    val showControls: Boolean = true,
    val showInfo: Boolean = true,
    val showWaveform: Boolean = true,
    val waveformColor: Int = 0xFF818CF8.toInt(), // Indigo waveform
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
    val waveColor = Color(options.waveformColor) // indigo 0xFF818CF8
    val controlsAlignment = options.controlsPosition.toOverlayAlignment()
    val shouldShowWaveform = options.showWaveform && (!options.participant.muted || audioLevel > 0.0)
    val cardShape = if (options.roundedImage) RoundedCornerShape(16.dp) else RoundedCornerShape(10.dp)
    val coroutineScope = rememberCoroutineScope()

    // Name-hash-based avatar gradient — matches React ModernAudioCard.initialsGradient
    val nameHash = options.name.fold(0) { acc, char -> char.code + ((acc shl 5) - acc) }
    val avatarGradients = remember {
        listOf(
            listOf(Color(0xFF818CF8), Color(0xFF60A5FA), Color(0xFF22D3EE)), // brand
            listOf(Color(0xFF22D3EE), Color(0xFF818CF8)),                    // accent
            listOf(Color(0xFF3B82F6), Color(0xFF22D3EE), Color(0xFF10B981)), // ocean
            listOf(Color(0xFF10B981), Color(0xFF22D3EE), Color(0xFF60A5FA), Color(0xFF818CF8)), // aurora
        )
    }
    val avatarGradientColors = avatarGradients[((nameHash % 4) + 4) % 4]
    val speakingBorderColor = if (shouldShowWaveform) Color(0xFF34D399) else Color.Transparent

    // Radial waveform ring: 9 bars animated around the avatar
    val ringBarHeights = remember { mutableStateListOf(*Array(9) { 8f }) }
    LaunchedEffect(shouldShowWaveform) {
        while (shouldShowWaveform) {
            for (i in 0 until 9) {
                ringBarHeights[i] = (kotlin.random.Random.nextFloat() * 20f + 8f).coerceIn(8f, 28f)
            }
            delay(100L)
        }
        for (i in 0 until 9) ringBarHeights[i] = 8f
    }

    // Inline waveform bars for the info overlay (5 bars, 150ms bounce like React)
    val waveformValues = remember { mutableStateListOf(*Array(5) { 3f }) }
    LaunchedEffect(shouldShowWaveform) {
        while (shouldShowWaveform) {
            for (i in 0 until 5) waveformValues[i] = kotlin.random.Random.nextFloat() * 22f + 2f
            delay(150L)
        }
        for (i in 0 until 5) waveformValues[i] = 3f
    }

    val isLightTheme = MaterialTheme.colorScheme.primary != Color(0xFF818CF8)
    val cardBackground = if (isLightTheme) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(Color(0xFF1A1A2E), Color(0xFF16213E), Color(0xFF0F3460))
        )
    }
    val innerCircleBg = if (isLightTheme) {
        MaterialTheme.colorScheme.surface
    } else {
        Color(0xFF0F172A)
    }
    val nameTextColor = if (isLightTheme) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Card: gradient bg + speaking glow border (React containerStyle)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .background(cardBackground)
                .border(2.5.dp, speakingBorderColor, cardShape),
            contentAlignment = Alignment.Center
        ) {
            // Centered avatar (React: avatarContainerStyle = position fill, center)
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Radial waveform ring (React: waveformRingBars — 9 bars around avatar)
                if (shouldShowWaveform) {
                    Canvas(modifier = Modifier.size(190.dp)) {
                        val r = 90.dp.toPx()
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val barW = 4.dp.toPx()
                        ringBarHeights.forEachIndexed { i, h ->
                            val hPx = h.dp.toPx()
                            val a = (i * 40f) * PI.toFloat() / 180f
                            withTransform({
                                translate(cx + r * sin(a), cy - r * cos(a))
                                rotate(degrees = i * 40f, pivot = Offset.Zero)
                            }) {
                                drawRoundRect(
                                    color = waveColor,
                                    topLeft = Offset(-barW / 2f, -hPx / 2f),
                                    size = Size(barW, hPx),
                                    cornerRadius = CornerRadius(2.dp.toPx())
                                )
                            }
                        }
                    }
                }

                // Avatar: gradient ring (3dp padding) + inner circle + initials
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(
                            Brush.linearGradient(colors = avatarGradientColors),
                            shape = CircleShape
                        )
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(innerCircleBg),
                        contentAlignment = Alignment.Center
                    ) {
                        DefaultMiniCard(
                            MiniCardOptions(
                                name = options.name,
                                participant = options.participant,
                                imageSource = options.imageSource,
                                roundedImage = true,
                                backgroundColor = 0x00000000,
                                showVideo = false
                            )
                        ).renderCompose(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // Glass sheen overlay (React: glassOverlayStyle)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            start = Offset.Zero,
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        )
                    )
            )
        }

        // Info overlay: bottom-left, transparent bg, speaking dot + name + waveform bars
        if (options.showInfo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (shouldShowWaveform) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                }
                Text(
                    text = options.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = nameTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(
                            color = if (isLightTheme) Color.Transparent else Color.Black.copy(alpha = 0.7f),
                            blurRadius = 4f
                        )
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(14.dp)
                        .alpha(if (shouldShowWaveform) 1f else 0f)
                ) {
                    waveformValues.forEach { value ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((if (shouldShowWaveform) value else 3f).dp)
                                .background(waveColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Controls overlay (glassmorphic buttons, 28dp, dark bg)
        if (options.showControls) {
            val isMuted = options.participant.muted
            val isVideoOn = options.participant.videoOn
            Row(
                modifier = Modifier
                    .align(controlsAlignment)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ModernAudioControlButton(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    contentDescription = if (isMuted) "Unmute ${options.name}" else "Mute ${options.name}",
                    tint = if (isMuted) Color(0xFFEF4444) else Color(0xFF34D399),
                    enabled = options.onToggleAudio != null,
                ) {
                    coroutineScope.launch { options.onToggleAudio?.invoke(options.participant) }
                }
                ModernAudioControlButton(
                    icon = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                    contentDescription = if (isVideoOn) "Turn off video" else "Turn on video",
                    tint = if (isVideoOn) Color(0xFF34D399) else Color(0xFFEF4444),
                    enabled = options.onToggleVideo != null,
                ) {
                    coroutineScope.launch { options.onToggleVideo?.invoke(options.participant) }
                }
            }
        }
    }
}

@Composable
private fun ModernAudioControlButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .let { base -> if (enabled) base.clickable(onClick = onClick) else base },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
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
