package com.mediasfu.sdk.methods.recording_methods

/** Options for checking if recording can be resumed. */
data class CheckResumeStateOptions(
    val recordingMediaOptions: String,
    val recordingVideoPausesLimit: Int,
    val recordingAudioPausesLimit: Int,
    val pauseRecordCount: Int
)

typealias CheckResumeStateType = suspend (CheckResumeStateOptions) -> Boolean

suspend fun checkResumeState(options: CheckResumeStateOptions): Boolean {
    val limit = if (options.recordingMediaOptions == "video") {
        options.recordingVideoPausesLimit
    } else {
        options.recordingAudioPausesLimit
    }
    return options.pauseRecordCount <= limit
}
