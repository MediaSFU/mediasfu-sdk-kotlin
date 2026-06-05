package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.ui.*

/**
 * CardVideoDisplay - Displays a video stream within a card.
 *
 * Renders video stream with optional mirroring, aspect ratio control,
 * and overlay controls for audio/video toggling.
 *
 * @property options Configuration options for the card video display
 */
data class CardVideoDisplayOptions(
    val videoStream: MediaStream?,
    val remoteProducerId: String = "",
    val eventType: String = "",
    val forceFullDisplay: Boolean = false,
    val customStyle: Map<String, Any> = emptyMap(),
    val backgroundColor: Int = 0xFF0D1B2A.toInt(),
    val doMirror: Boolean = false,
    val displayLabel: String = "",
    // Controls overlay options
    val showControls: Boolean = false,
    val showInfo: Boolean = true,
    val controlsPosition: String = "topLeft",
    val infoPosition: String = "bottomLeft",
    val participant: Participant? = null,
    val onAudioToggle: ((Participant) -> Unit)? = null,
    val onVideoToggle: ((Participant) -> Unit)? = null,
    // Audio waveform options (like Flutter's VideoCard)
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val barColor: Int = 0xFF818CF8.toInt() // Indigo waveform bars
)

interface CardVideoDisplay : MediaSfuUIComponent {
    val options: CardVideoDisplayOptions
    override val id: String get() = "card_video_display"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets video element style configuration
     */
    fun getVideoStyle(): Map<String, Any> {
        val baseStyle = mutableMapOf<String, Any>(
            "width" to "100%",
            "height" to "100%",
            "objectFit" to if (options.forceFullDisplay) "cover" else "contain",
            "backgroundColor" to options.backgroundColor
        )
        
        if (options.doMirror) {
            baseStyle["transform"] = "scaleX(-1)"
        }
        
        baseStyle.putAll(options.customStyle)
        return baseStyle
    }
    
    /**
     * Checks if video stream is available
     */
    fun hasVideoStream(): Boolean {
        return options.videoStream != null
    }
}

/**
 * Default implementation of CardVideoDisplay
 */
class DefaultCardVideoDisplay(
    override val options: CardVideoDisplayOptions
) : CardVideoDisplay {
    fun render(): Any {
        return mapOf(
            "type" to "cardVideoDisplay",
            "hasVideoStream" to hasVideoStream(),
            "videoStream" to options.videoStream,
            "remoteProducerId" to options.remoteProducerId,
            "videoStyle" to getVideoStyle(),
            "eventType" to options.eventType,
            "displayLabel" to options.displayLabel
        )
    }
}

/**
 * Composable extension for rendering CardVideoDisplay in Jetpack Compose
 * 
 * NOTE: This is a placeholder implementation. Full video rendering requires platform-specific code.
 * 
 * For production use, implement platform-specific video renderers:
 * 
 * **Android:** Use AndroidView with SurfaceViewRenderer from WebRTC
 * ```kotlin
 * AndroidView(
 *     factory = { context ->
 *         SurfaceViewRenderer(context).apply {
 *             init(EglBase.create().eglBaseContext, null)
 *             setScalingType(if (forceFullDisplay) 
 *                 RendererCommon.ScalingType.SCALE_ASPECT_FILL 
 *                 else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
 *             setMirror(doMirror)
 *             
 *             // Attach video track from stream
 *             videoStream?.getVideoTracks()?.firstOrNull()?.let { track ->
 *                 track.addSink(this)
 *             }
 *         }
 *     },
 *     update = { view ->
 *         // Update when stream changes
 *     },
 *     onRelease = { view ->
 *         view.release()
 *     }
 * )
 * ```
 * 
 * **iOS:** Use UIViewRepresentable with RTCMTLVideoView
 * ```kotlin
 * UIKitView(
 *     factory = {
 *         val videoView = RTCMTLVideoView()
 *         videoView.contentMode = if (forceFullDisplay) 
 *             UIViewContentMode.scaleAspectFill 
 *             else UIViewContentMode.scaleAspectFit
 *         
 *         // Attach video track from stream
 *         videoStream?.getVideoTracks()?.firstOrNull()?.let { track ->
 *             track.add(videoView)
 *         }
 *         videoView
 *     },
 *     update = { view ->
 *         // Update when stream changes
 *     }
 * )
 * ```
 * 
 * See Flutter's card_video_display.dart (757 lines) for reference implementation.
 * Key features to implement:
 * - RTCVideoRenderer lifecycle (init, attach stream, dispose)
 * - Stream polling for async track availability
 * - Layered rendering (video + placeholder + overlay)
 * - Mirror transform for local camera
 * - Object-fit control (cover vs contain)
 */
@Composable
fun CardVideoDisplay.renderCompose() {
    val firstVideoTrack: MediaStreamTrack? = options.videoStream
        ?.getVideoTracks()
        ?.firstOrNull()
    val label = options.displayLabel.ifBlank { options.remoteProducerId }

    val participantName = options.participant?.name ?: options.displayLabel
    val isMutedP = options.participant?.muted ?: false
    val isVideoOn = options.participant?.videoOn ?: true
    val audioLevel = if (!isMutedP) {
        options.audioDecibels
            .find { it.name.equals(participantName, ignoreCase = true) }
            ?.averageLoudness ?: 0.0
    } else 0.0
    val showWaveform = !isMutedP && audioLevel > 0.0

    // Animated inline waveform bars (5 bars, like React ModernVideoCard)
    val waveformValues = remember { mutableStateListOf(*Array(5) { 0f }) }
    LaunchedEffect(showWaveform) {
        while (showWaveform) {
            for (i in 0 until 5) waveformValues[i] = kotlin.random.Random.nextFloat() * 12f + 4f
            delay(100L)
        }
        for (i in 0 until 5) waveformValues[i] = 0f
    }

    val barColor = Color(options.barColor)
    val speakingBorderColor = if (showWaveform) Color(0xFF34D399) else Color.Transparent
    val cardShape = RoundedCornerShape(8.dp)
    val infoAlignment = getAlignmentForPosition(options.infoPosition)
    val controlsAlignment = getAlignmentForPosition(options.controlsPosition)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(2.5.dp, speakingBorderColor, cardShape),
        contentAlignment = Alignment.Center
    ) {
        // Video or placeholder
        if (firstVideoTrack != null) {
            PlatformVideoRenderer(
                track = firstVideoTrack,
                doMirror = options.doMirror,
                forceFullDisplay = options.forceFullDisplay,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VideoPlaceholder(
                label = label,
                message = "Waiting for video",
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom gradient overlay — matches React gradientOverlayStyle
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(0f, Float.POSITIVE_INFINITY)
                    )
                )
        )

        // Status indicator: top-right colored dot (green=videoOn, red=videoOff)
        if (options.showInfo) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(10.dp)
                    .background(
                        if (isVideoOn) Color(0xFF34D399) else Color(0xFFEF4444),
                        CircleShape
                    )
            )
        }

        // Info overlay: transparent bg, speaking pulse dot + name + inline waveform
        if (options.showInfo) {
            Row(
                modifier = Modifier
                    .align(infoAlignment)
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showWaveform) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                }
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        maxLines = 1,
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.7f),
                                blurRadius = 4f
                            )
                        )
                    )
                }
                // Inline waveform bars
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(16.dp)
                        .alpha(if (showWaveform) 1f else 0f)
                ) {
                    waveformValues.forEach { value ->
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((if (showWaveform) value else 0f).dp)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Controls overlay: glassmorphic pill with mic + video buttons
        val participantForControls = options.participant
        if (options.showControls && participantForControls != null) {
            val isMuted = participantForControls.muted ?: false
            val videoOn = participantForControls.videoOn ?: true

            Row(
                modifier = Modifier
                    .align(controlsAlignment)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Transparent)
                        .clickable { options.onAudioToggle?.invoke(participantForControls) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) Color(0xFFEF4444) else Color(0xFF34D399),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Transparent)
                        .clickable { options.onVideoToggle?.invoke(participantForControls) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (videoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (videoOn) "Turn off video" else "Turn on video",
                        tint = if (videoOn) Color(0xFF34D399) else Color(0xFFEF4444),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Converts position string to Compose Alignment.
 */
private fun getAlignmentForPosition(position: String): Alignment {
    return when (position.lowercase()) {
        "topleft" -> Alignment.TopStart
        "topright" -> Alignment.TopEnd
        "topcenter" -> Alignment.TopCenter
        "bottomleft" -> Alignment.BottomStart
        "bottomright" -> Alignment.BottomEnd
        "bottomcenter" -> Alignment.BottomCenter
        "centerleft" -> Alignment.CenterStart
        "centerright" -> Alignment.CenterEnd
        "center" -> Alignment.Center
        else -> Alignment.TopStart
    }
}

@Composable
private fun VideoPlaceholder(
    label: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val text = if (label.isNotBlank()) label else message
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/**
 * Audio waveform visualization for VideoCard (matches Flutter's VideoCard waveform).
 * Shows animated bars based on audio level when participant is unmuted.
 * Uses animation loop to continuously randomize bar heights like Flutter.
 */
@Composable
private fun VideoCardWaveform(
    audioLevel: Double,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    // 9 bars like Flutter's VideoCard waveform - animated with random heights
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
