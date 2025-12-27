package com.mediasfu.sdk.ui.components

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * MeetingProgressTimer - A UI component for displaying meeting progress and elapsed time.
 * 
 * This component provides:
 * - Real-time meeting timer display
 * - Elapsed time tracking
 * - Meeting duration limits
 * - Customizable time format
 * - Pause/resume functionality
 * - Visual progress indicators
 */
class MeetingProgressTimer(
    private val options: MeetingProgressTimerOptions
) : BaseMediaSfuUIComponent("meeting_timer_${Clock.System.now().toEpochMilliseconds()}"),
    StylableComponent {
    
    private val _elapsedTime = MutableStateFlow(0L)
    val elapsedTime: StateFlow<Long> = _elapsedTime.asStateFlow()
    
    private val _isRunning = MutableStateFlow(options.isRunning)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()
    
    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle = _currentStyle
    
    private var timerJob: Job? = null
    private var startTime: Long = 0L
    private var pausedTime: Long = 0L
    
    init {
        if (options.isRunning) {
            start()
        }
    }
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    /**
     * Start the timer.
     */
    fun start() {
        if (!_isRunning.value) {
            _isRunning.value = true
            _isPaused.value = false
            startTime = Clock.System.now().toEpochMilliseconds() - pausedTime
            startTimerJob()
            onTimerStarted()
        }
    }
    
    /**
     * Pause the timer.
     */
    fun pause() {
        if (_isRunning.value && !_isPaused.value) {
            _isPaused.value = true
            timerJob?.cancel()
            onTimerPaused()
        }
    }
    
    /**
     * Resume the timer.
     */
    fun resume() {
        if (_isRunning.value && _isPaused.value) {
            _isPaused.value = false
            startTime = Clock.System.now().toEpochMilliseconds() - pausedTime
            startTimerJob()
            onTimerResumed()
        }
    }
    
    /**
     * Stop the timer.
     */
    fun stop() {
        _isRunning.value = false
        _isPaused.value = false
        timerJob?.cancel()
        pausedTime = 0L
        _elapsedTime.value = 0L
        onTimerStopped()
    }
    
    /**
     * Reset the timer.
     */
    fun reset() {
        stop()
        _elapsedTime.value = 0L
        onTimerReset()
    }
    
    /**
     * Get the formatted elapsed time string.
     */
    fun getFormattedTime(): String {
        return formatTime(_elapsedTime.value)
    }
    
    /**
     * Get the progress percentage (if duration limit is set).
     */
    fun getProgressPercentage(): Float {
        return if (options.durationLimit > 0) {
            (_elapsedTime.value.toFloat() / options.durationLimit.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    
    /**
     * Check if the meeting duration limit has been reached.
     */
    fun isDurationLimitReached(): Boolean {
        return options.durationLimit > 0 && _elapsedTime.value >= options.durationLimit
    }
    
    /**
     * Get the remaining time (if duration limit is set).
     */
    fun getRemainingTime(): Long {
        return if (options.durationLimit > 0) {
            (options.durationLimit - _elapsedTime.value).coerceAtLeast(0L)
        } else {
            0L
        }
    }
    
    /**
     * Get the formatted remaining time string.
     */
    fun getFormattedRemainingTime(): String {
        return formatTime(getRemainingTime())
    }
    
    // Private methods for internal functionality
    
    private fun startTimerJob() {
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (_isRunning.value && !_isPaused.value) {
                val currentTime = Clock.System.now().toEpochMilliseconds()
                _elapsedTime.value = currentTime - startTime
                
                // Check if duration limit is reached
                if (isDurationLimitReached()) {
                    onDurationLimitReached()
                    if (options.stopOnLimitReached) {
                        stop()
                    }
                }
                
                // Update display
                onTimeUpdated(_elapsedTime.value)
                
                delay(1000) // Update every second
            }
        }
    }
    
    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        
        return when (options.timeFormat) {
            TimeFormat.HHMMSS -> "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
            TimeFormat.MMSS -> "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"
            TimeFormat.SS -> remainingSeconds.toString().padStart(2, '0')
            TimeFormat.Human -> formatHumanTime(seconds)
        }
    }
    
    private fun formatHumanTime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${remainingSeconds}s"
            minutes > 0 -> "${minutes}m ${remainingSeconds}s"
            else -> "${remainingSeconds}s"
        }
    }
    
    private fun onTimerStarted() {
        // Platform-specific timer started logic
        platformOnTimerStarted()
    }
    
    private fun onTimerPaused() {
        // Platform-specific timer paused logic
        platformOnTimerPaused()
    }
    
    private fun onTimerResumed() {
        // Platform-specific timer resumed logic
        platformOnTimerResumed()
    }
    
    private fun onTimerStopped() {
        // Platform-specific timer stopped logic
        platformOnTimerStopped()
    }
    
    private fun onTimerReset() {
        // Platform-specific timer reset logic
        platformOnTimerReset()
    }
    
    private fun onTimeUpdated(elapsedTime: Long) {
        // Platform-specific time update logic
        platformOnTimeUpdated(elapsedTime)
    }
    
    private fun onDurationLimitReached() {
        // Platform-specific duration limit reached logic
        platformOnDurationLimitReached()
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformOnTimerStarted() {
        // Platform-specific timer started logic
    }
    
    private fun platformOnTimerPaused() {
        // Platform-specific timer paused logic
    }
    
    private fun platformOnTimerResumed() {
        // Platform-specific timer resumed logic
    }
    
    private fun platformOnTimerStopped() {
        // Platform-specific timer stopped logic
    }
    
    private fun platformOnTimerReset() {
        // Platform-specific timer reset logic
    }
    
    private fun platformOnTimeUpdated(elapsedTime: Long) {
        // Platform-specific time update logic
    }
    
    private fun platformOnDurationLimitReached() {
        // Platform-specific duration limit reached logic
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
    
    override fun onDispose() {
        super.onDispose()
        timerJob?.cancel()
        _isRunning.value = false
        _isPaused.value = false
    }
}

/**
 * Configuration options for the MeetingProgressTimer component.
 */
data class MeetingProgressTimerOptions(
    val isRunning: Boolean = false,
    val style: ComponentStyle = ComponentStyle(),
    val timeFormat: TimeFormat = TimeFormat.HHMMSS,
    val durationLimit: Long = 0L, // in milliseconds, 0 means no limit
    val stopOnLimitReached: Boolean = true,
    val showProgressBar: Boolean = false,
    val showRemainingTime: Boolean = false,
    val textColor: Color = Color.White,
    val backgroundColor: Color = Color.Transparent,
    val progressBarColor: Color = Color.Blue,
    val progressBarBackgroundColor: Color = Color.Gray,
    val fontSize: Float = 16f,
    val fontWeight: FontWeight = FontWeight.Normal,
    val padding: EdgeInsets = EdgeInsets.all(8f),
    val margin: EdgeInsets = EdgeInsets.zero,
    val onTimerStarted: (() -> Unit)? = null,
    val onTimerPaused: (() -> Unit)? = null,
    val onTimerResumed: (() -> Unit)? = null,
    val onTimerStopped: (() -> Unit)? = null,
    val onTimerReset: (() -> Unit)? = null,
    val onDurationLimitReached: (() -> Unit)? = null,
    val onTimeUpdated: ((Long) -> Unit)? = null
)

/**
 * Represents different time format options.
 */
enum class TimeFormat {
    HHMMSS, MMSS, SS, Human
}
