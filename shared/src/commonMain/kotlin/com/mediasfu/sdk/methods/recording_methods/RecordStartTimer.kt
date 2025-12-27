package com.mediasfu.sdk.methods.recording_methods

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

data class RecordStartTimerOptions(
    val parameters: RecordStartTimerParameters
)

typealias RecordStartTimerType = suspend (RecordStartTimerOptions) -> Unit

interface RecordStartTimerParameters {
    val recordStartTime: Long?
    val recordTimerJob: Job?
    val isTimerRunning: Boolean
    val canPauseResume: Boolean
    val recordChangeSeconds: Int
    val recordPaused: Boolean
    val recordStopped: Boolean
    val roomName: String?
    val recordElapsedTime: Int

    val updateRecordStartTime: (Long?) -> Unit
    val updateRecordTimerJob: (Job?) -> Unit
    val updateIsTimerRunning: (Boolean) -> Unit
    val updateCanPauseResume: (Boolean) -> Unit
    val updateRecordElapsedTime: (Int) -> Unit
    val updateRecordingProgressTime: (String) -> Unit

    fun getUpdatedAllParams(): RecordStartTimerParameters
}

suspend fun recordStartTimer(options: RecordStartTimerOptions) {
    var parameters = options.parameters.getUpdatedAllParams()

    
    // Force reset timer running state if it's true but no job exists (stale state)
    if (parameters.isTimerRunning && parameters.recordTimerJob == null) {
        parameters.updateIsTimerRunning(false)
        parameters = options.parameters.getUpdatedAllParams()
    }

    if (parameters.isTimerRunning) {
        return
    }

    val startTime = Clock.System.now().toEpochMilliseconds()
    parameters.updateRecordStartTime(startTime)

    // Optimistic initial update to show 00:00:00 immediately
    val initialUpdateOptions = RecordUpdateTimerOptions(
        recordElapsedTime = 0,
        recordStartTime = startTime,
        updateRecordElapsedTime = parameters.updateRecordElapsedTime,
        updateRecordingProgressTime = parameters.updateRecordingProgressTime
    )
    kotlinx.coroutines.withContext(Dispatchers.Main) {
        recordUpdateTimer(initialUpdateOptions)
    }

    val job = CoroutineScope(Dispatchers.Default).launch {
        try {
            while (true) {
                delay(1000)
                parameters = parameters.getUpdatedAllParams()
                val updateOptions = RecordUpdateTimerOptions(
                    recordElapsedTime = parameters.recordElapsedTime,
                    recordStartTime = parameters.recordStartTime ?: startTime,
                    updateRecordElapsedTime = parameters.updateRecordElapsedTime,
                    updateRecordingProgressTime = parameters.updateRecordingProgressTime
                )
                // Call recordUpdateTimer on main thread to properly update UI state
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    recordUpdateTimer(updateOptions)
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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    parameters.updateRecordTimerJob(job)
    parameters.updateIsTimerRunning(true)
    CoroutineScope(Dispatchers.Default).launch {
        // recordChangeSeconds is already in milliseconds (default 15000)
        delay(parameters.recordChangeSeconds.toLong())
        parameters = parameters.getUpdatedAllParams()
        parameters.updateCanPauseResume(true)
    }
}
