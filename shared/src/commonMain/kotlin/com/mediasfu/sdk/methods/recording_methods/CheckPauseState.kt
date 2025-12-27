package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.model.ShowAlert

/** Options for checking if recording can be paused. */
data class CheckPauseStateOptions(
    val recordingMediaOptions: String,
    val recordingVideoPausesLimit: Int,
    val recordingAudioPausesLimit: Int,
    val pauseRecordCount: Int,
    val showAlert: ShowAlert?
)

typealias CheckPauseStateType = suspend (CheckPauseStateOptions) -> Boolean

suspend fun checkPauseState(options: CheckPauseStateOptions): Boolean {
    val limit = if (options.recordingMediaOptions == "video") {
        options.recordingVideoPausesLimit
    } else {
        options.recordingAudioPausesLimit
    }

    if (options.pauseRecordCount < limit) {
        return true
    }

    options.showAlert?.invoke(
        "You have reached the limit of pauses - you can choose to stop recording.",
        "danger",
        3000
    )
    return false
}
