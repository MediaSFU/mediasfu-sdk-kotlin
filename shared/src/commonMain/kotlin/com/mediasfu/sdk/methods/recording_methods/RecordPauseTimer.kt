package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.model.ShowAlert

/** Options for controlling the recording timer, allowing pause and resume actions. */
data class RecordPauseTimerOptions(
    val stop: Boolean = false,
    val isTimerRunning: Boolean,
    val canPauseResume: Boolean,
    val showAlert: ShowAlert?
)

/** Type alias for the recordPauseTimer function. */
typealias RecordPauseTimerType = (RecordPauseTimerOptions) -> Boolean

/**
 * Controls the recording timer by allowing pause and resume actions.
 *
 * Returns true if the timer can be paused or resumed based on [isTimerRunning]
 * and [canPauseResume] flags in [options]. Shows an alert if conditions are not met.
 *
 * If [stop] is true, the alert shows a message about stopping only after 15 seconds;
 * otherwise, it shows a pause/resume restriction message.
 */
fun recordPauseTimer(options: RecordPauseTimerOptions): Boolean {
    if (options.isTimerRunning && options.canPauseResume) {
        return true
    }

    val message = if (options.stop) {
        "Can only stop after 15 seconds of starting or pausing or resuming recording"
    } else {
        "Can only pause or resume after 15 seconds of starting or pausing or resuming recording"
    }

    options.showAlert?.invoke(message, "danger", 3000)
    return false
}
