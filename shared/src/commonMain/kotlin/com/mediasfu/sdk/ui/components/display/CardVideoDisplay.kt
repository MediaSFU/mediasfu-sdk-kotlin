package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val doMirror: Boolean = false,
    val displayLabel: String = "",
    // Controls overlay options
    val showControls: Boolean = false,
    val showInfo: Boolean = true,
    val controlsPosition: String = "topLeft",
    val infoPosition: String = "topRight",
    val participant: Participant? = null,
    val onAudioToggle: ((Participant) -> Unit)? = null,
    val onVideoToggle: ((Participant) -> Unit)? = null,
    // Audio waveform options (like Flutter's VideoCard)
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val barColor: Int = 0xFFE82E2E.toInt() // Red waveform bars
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
    
    // Debug: Log what we're rendering

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(options.backgroundColor)),
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

        // Name badge with audio waveform (info overlay) - matches Flutter's VideoCard
        if (options.showInfo) {
            val infoAlignment = getAlignmentForPosition(options.infoPosition)
            val participantName = options.participant?.name ?: options.displayLabel
            val isMuted = options.participant?.muted ?: false
            
            // Get audio level for this participant
            val audioLevel = if (!isMuted) {
                options.audioDecibels
                    .find { it.name.equals(participantName, ignoreCase = true) }
                    ?.averageLoudness ?: 0.0
            } else {
                0.0
            }
            val showWaveform = !isMuted && audioLevel > 0.0
            
            Row(
                modifier = Modifier
                    .align(infoAlignment)
                    .padding(4.dp)
                    .background(
                        color = Color.White.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(0.dp)
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Name badge
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Audio waveform (like Flutter's VideoCard)
                if (showWaveform) {
                    VideoCardWaveform(
                        audioLevel = audioLevel,
                        barColor = Color(options.barColor)
                    )
                }
            }
        }
        
        // Controls overlay (mic/video toggle buttons)
        val participantForControls = options.participant
        if (options.showControls && participantForControls != null) {
            val controlsAlignment = getAlignmentForPosition(options.controlsPosition)
            val isMuted = participantForControls.muted ?: false
            val isVideoOn = participantForControls.videoOn ?: true
            
            Row(
                modifier = Modifier
                    .align(controlsAlignment)
                    .padding(4.dp)
            ) {
                // Audio toggle button
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(0.dp)
                        )
                        .clickable { options.onAudioToggle?.invoke(participantForControls) }
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) Color.Red else Color.Green,
                        modifier = Modifier.size(14.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Video toggle button
                Box(
                    modifier = Modifier
                        .background(
                            color = Color.White.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(0.dp)
                        )
                        .clickable { options.onVideoToggle?.invoke(participantForControls) }
                        .padding(2.dp)
                ) {
                    Icon(
                        imageVector = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        contentDescription = if (isVideoOn) "Turn off video" else "Turn on video",
                        tint = if (isVideoOn) Color.Green else Color.Red,
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
        color = Color.White.copy(alpha = 0.9f),
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
