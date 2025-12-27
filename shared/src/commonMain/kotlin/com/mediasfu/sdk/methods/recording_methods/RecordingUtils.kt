package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import kotlinx.coroutines.Job

/** Utility helpers for recording lifecycle cleanup and state resets. */
object RecordingUtils {
    private const val DEFAULT_PROGRESS_TIME = "00:00:00"

    data class CleanUpRecordingOptions(
        val recordTimerJob: Job?,
        val updateRecordTimerJob: (Job?) -> Unit,
        val updateIsTimerRunning: UpdateBooleanState,
        val updateCanPauseResume: UpdateBooleanState,
        val updateRecordElapsedTime: (Int) -> Unit,
        val updateRecordingProgressTime: UpdateRecordingProgressTime,
        val updateRecordStartTime: (Long?) -> Unit,
        val updateRecordStarted: UpdateBooleanState,
        val updateRecordPaused: UpdateBooleanState,
        val updateRecordResumed: UpdateBooleanState,
        val updateRecordStopped: UpdateBooleanState,
        val updateStartReport: UpdateBooleanState,
        val updateEndReport: UpdateBooleanState,
        val updateCanRecord: UpdateBooleanState,
        val updateShowRecordButtons: UpdateBooleanState,
        val updateRecordState: (String) -> Unit,
        val updateClearedToRecord: UpdateBooleanState? = null,
        val updateClearedToResume: UpdateBooleanState? = null,
        val updatePauseRecordCount: ((Int) -> Unit)? = null,
        val showAlert: ShowAlert?,
        val alertMessage: String = "Recording Stopped",
        val alertType: String = "success",
        val alertDurationMillis: Int = 3000
    )

    /** Resets recording flags, clears timers, and surfaces a completion alert. */
    fun cleanUpRecording(options: CleanUpRecordingOptions) {
        options.recordTimerJob?.cancel()
        options.updateRecordTimerJob(null)

        options.updateIsTimerRunning(false)
        options.updateCanPauseResume(false)

        options.updateRecordElapsedTime(0)
        options.updateRecordingProgressTime(DEFAULT_PROGRESS_TIME)
        options.updateRecordStartTime(null)

        options.updateRecordStarted(false)
        options.updateRecordPaused(false)
        options.updateRecordResumed(false)
        options.updateRecordStopped(true)

        options.updateStartReport(false)
        options.updateEndReport(true)
        options.updateCanRecord(true)
        options.updateShowRecordButtons(false)
        options.updateRecordState("green")

        options.updateClearedToRecord?.invoke(true)
        options.updateClearedToResume?.invoke(true)
        options.updatePauseRecordCount?.invoke(0)

        options.showAlert.call(options.alertMessage, options.alertType, options.alertDurationMillis)
    }
}
