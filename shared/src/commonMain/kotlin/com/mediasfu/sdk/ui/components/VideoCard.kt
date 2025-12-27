package com.mediasfu.sdk.ui.components

import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * VideoCard - A UI component for displaying video streams with participant information and controls.
 * 
 * This component provides a comprehensive video display with:
 * - Video stream rendering
 * - Participant name and status
 * - Audio waveform visualization
 * - Control buttons (mute/unmute, video on/off)
 * - Customizable styling and positioning
 */
class VideoCard(
    private val options: VideoCardOptions
) : BaseMediaSfuUIComponent("video_card_${options.participant.id}"),
    MediaDisplayComponent,
    InteractiveComponent,
    AnimatedComponent,
    StylableComponent {
    
    private val _hasMedia = MutableStateFlow(false)
    override val hasMedia: StateFlow<Boolean> = _hasMedia.asStateFlow()
    
    private val _isAnimating = MutableStateFlow(false)
    override val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle
    
    private val _showWaveform = MutableStateFlow(false)
    val showWaveform: StateFlow<Boolean> = _showWaveform.asStateFlow()
    
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()
    
    private var currentMediaStream: MediaStream? = null
    private var waveformAnimation: Animation? = null
    
    init {
        // Initialize with participant's current media state
        _hasMedia.value = options.videoStream != null
        if (options.videoStream != null) {
            currentMediaStream = options.videoStream
        }
    }
    
    override fun updateMedia(mediaStream: MediaStream) {
        currentMediaStream = mediaStream
        _hasMedia.value = true
        onMediaUpdated(mediaStream)
    }
    
    override fun clearMedia() {
        currentMediaStream = null
        _hasMedia.value = false
        onMediaCleared()
    }
    
    override fun startAnimation(animation: Animation) {
        waveformAnimation = animation
        _isAnimating.value = true
        when (animation) {
            is Animation.Pulse -> startWaveformAnimation()
            is Animation.FadeIn -> startFadeInAnimation()
            is Animation.FadeOut -> startFadeOutAnimation()
            else -> {
                // Handle other animation types
                _isAnimating.value = false
            }
        }
    }
    
    override fun stopAnimation() {
        waveformAnimation = null
        _isAnimating.value = false
        stopWaveformAnimation()
    }
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    override fun handleInteraction(event: InteractionEvent) {
        when (event) {
            is InteractionEvent.Click -> handleClick()
            is InteractionEvent.LongPress -> handleLongPress()
            is InteractionEvent.DoubleClick -> handleDoubleClick()
            else -> {
                // Handle other interaction types
            }
        }
    }
    
    /**
     * Update the audio level for waveform visualization.
     */
    fun updateAudioLevel(level: Float) {
        _audioLevel.value = level.coerceIn(0f, 1f)
        _showWaveform.value = level > 0.1f
    }
    
    /**
     * Toggle the audio mute state.
     */
    suspend fun toggleAudio() {
        if (options.participant.muted == true) {
            // Handle unmuting logic
            options.onAudioToggle?.invoke(false)
        } else {
            // Handle muting logic
            options.onAudioToggle?.invoke(true)
        }
    }
    
    /**
     * Toggle the video state.
     */
    suspend fun toggleVideo() {
        val currentVideoState = options.participant.videoOn ?: true
        options.onVideoToggle?.invoke(!currentVideoState)
    }
    
    /**
     * Get the current participant information.
     */
    fun getParticipant(): Participant = options.participant
    
    /**
     * Update participant information.
     */
    fun updateParticipant(participant: Participant) {
        // Update participant data
        onParticipantUpdated(participant)
    }
    
    // Private methods for internal functionality
    
    private fun onMediaUpdated(mediaStream: MediaStream) {
        // Platform-specific media update logic
        platformUpdateMedia(mediaStream)
    }
    
    private fun onMediaCleared() {
        // Platform-specific media clear logic
        platformClearMedia()
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    private fun onParticipantUpdated(participant: Participant) {
        // Platform-specific participant update logic
        platformUpdateParticipant(participant)
    }
    
    private fun handleClick() {
        options.onClick?.invoke()
    }
    
    private fun handleLongPress() {
        options.onLongPress?.invoke()
    }
    
    private fun handleDoubleClick() {
        options.onDoubleClick?.invoke()
    }
    
    private fun startWaveformAnimation() {
        // Start waveform animation
        _showWaveform.value = true
    }
    
    private fun stopWaveformAnimation() {
        // Stop waveform animation
        _showWaveform.value = false
    }
    
    private fun startFadeInAnimation() {
        // Start fade in animation
        // Platform-specific implementation
    }
    
    private fun startFadeOutAnimation() {
        // Start fade out animation
        // Platform-specific implementation
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformUpdateMedia(mediaStream: MediaStream) {
        // Platform-specific media update
    }
    
    private fun platformClearMedia() {
        // Platform-specific media clear
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
    
    private fun platformUpdateParticipant(participant: Participant) {
        // Platform-specific participant update
    }
    
    override fun onDispose() {
        super.onDispose()
        currentMediaStream = null
        waveformAnimation = null
        _showWaveform.value = false
        _audioLevel.value = 0f
    }
}

/**
 * Configuration options for the VideoCard component.
 */
data class VideoCardOptions(
    val participant: Participant,
    val videoStream: MediaStream? = null,
    val eventType: EventType = EventType.CONFERENCE,
    val style: ComponentStyle = ComponentStyle(),
    val showControls: Boolean = true,
    val showInfo: Boolean = true,
    val showWaveform: Boolean = true,
    val controlsPosition: ControlPosition = ControlPosition.BottomLeft,
    val infoPosition: ControlPosition = ControlPosition.TopLeft,
    val backgroundColor: Color = Color(0.17f, 0.4f, 0.56f), // #2c678f
    val textColor: Color = Color.White,
    val barColor: Color = Color(0.91f, 0.18f, 0.18f), // #e82e2e
    val doMirror: Boolean = false,
    val forceFullDisplay: Boolean = false,
    val roundedCorners: Boolean = false,
    val borderWidth: Float = 2f,
    val borderColor: Color = Color.Black,
    val onClick: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onDoubleClick: (() -> Unit)? = null,
    val onAudioToggle: ((Boolean) -> Unit)? = null,
    val onVideoToggle: ((Boolean) -> Unit)? = null,
    val customInfoComponent: MediaSfuUIComponent? = null,
    val customControlsComponent: MediaSfuUIComponent? = null
)

/**
 * Represents different positions for controls and info elements.
 */
enum class ControlPosition {
    TopLeft, TopRight, TopCenter,
    BottomLeft, BottomRight, BottomCenter,
    CenterLeft, CenterRight, Center
}
