package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters
import com.mediasfu.sdk.model.ShowAlert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class RecordResumeTimerOptions(
    val parameters: RecordResumeTimerParameters
)

typealias RecordResumeTimerType = suspend (RecordResumeTimerOptions) -> Boolean

interface RecordResumeTimerParameters : PrepopulateUserMediaParameters {
    val isTimerRunning: Boolean
    val canPauseResume: Boolean
    val recordElapsedTime: Int
    val recordStartTime: Long?
    val recordTimerJob: Job?
    val showAlert: ShowAlert?
    val recordPaused: Boolean
    val recordStopped: Boolean
    val roomName: String?
    val recordUpdateTimer: RecordUpdateTimerType

    val updateRecordStartTime: (Long) -> Unit
    val updateRecordTimerJob: (Job?) -> Unit
    val updateIsTimerRunning: (Boolean) -> Unit
    val updateCanPauseResume: (Boolean) -> Unit
    val updateRecordElapsedTime: (Int) -> Unit
    val updateRecordingProgressTime: (String) -> Unit

    override fun getUpdatedAllParams(): RecordResumeTimerParameters
}

suspend fun recordResumeTimer(options: RecordResumeTimerOptions): Boolean {
    var parameters = options.parameters.getUpdatedAllParams()

    fun showAlert(message: String) {
        parameters.showAlert?.invoke(message, "danger", 3000)
    }

    // Force reset timer running state if it's true but no job exists (stale state)
    if (parameters.isTimerRunning && parameters.recordTimerJob == null) {
        parameters.updateIsTimerRunning(false)
        parameters = options.parameters.getUpdatedAllParams()
    }

    if (!parameters.isTimerRunning && parameters.canPauseResume) {
        val newStartTime = Clock.System.now().toEpochMilliseconds() - parameters.recordElapsedTime * 1000L
        parameters.updateRecordStartTime(newStartTime)

        val job = CoroutineScope(Dispatchers.Default).launch {
            var localElapsed = parameters.recordElapsedTime
            while (true) {
                delay(1000)
                parameters = parameters.getUpdatedAllParams()
                val updateOptions = RecordUpdateTimerOptions(
                    recordElapsedTime = localElapsed,
                    recordStartTime = parameters.recordStartTime ?: newStartTime,
                    updateRecordElapsedTime = { value ->
                        localElapsed = value + 1
                        parameters.updateRecordElapsedTime(value + 1)
                    },
                    updateRecordingProgressTime = parameters.updateRecordingProgressTime
                )
                // Call recordUpdateTimer on main thread to properly update UI state
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    parameters.recordUpdateTimer(updateOptions)
                }

                val updated = parameters.getUpdatedAllParams()
                if (updated.recordPaused || updated.recordStopped || updated.roomName.isNullOrEmpty()) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        updated.updateRecordTimerJob(null)
                        updated.updateIsTimerRunning(false)
                        updated.updateCanPauseResume(false)
                    }
                    break
                }
            }
        }

        parameters.updateRecordTimerJob(job)
        parameters.updateIsTimerRunning(true)
        parameters.updateCanPauseResume(false)
        return true
    }

    showAlert("Can only pause or resume after 15 seconds of starting or pausing or resuming recording")
    return false
}

suspend fun RecordResumeTimerOptions.startTimer(): Boolean = recordResumeTimer(this)
