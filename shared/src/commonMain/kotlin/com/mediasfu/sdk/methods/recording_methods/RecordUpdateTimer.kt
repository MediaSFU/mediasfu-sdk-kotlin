package com.mediasfu.sdk.methods.recording_methods

import kotlinx.datetime.Clock

typealias UpdateRecordElapsedTime = (Int) -> Unit
typealias UpdateRecordingProgressTime = (String) -> Unit

/** Options for updating the recording timer. */
data class RecordUpdateTimerOptions(
    var recordElapsedTime: Int,
    val recordStartTime: Long,
    val updateRecordElapsedTime: UpdateRecordElapsedTime,
    val updateRecordingProgressTime: UpdateRecordingProgressTime
)

typealias RecordUpdateTimerType = (RecordUpdateTimerOptions) -> Unit

fun recordUpdateTimer(options: RecordUpdateTimerOptions) {
    val currentTime = Clock.System.now().toEpochMilliseconds()
    options.recordElapsedTime = ((currentTime - options.recordStartTime) / 1000).toInt()
    options.updateRecordElapsedTime(options.recordElapsedTime)

    val hours = options.recordElapsedTime / 3600
    val minutes = (options.recordElapsedTime % 3600) / 60
    val seconds = options.recordElapsedTime % 60
    val formatted = "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"

    options.updateRecordingProgressTime(formatted)
}
