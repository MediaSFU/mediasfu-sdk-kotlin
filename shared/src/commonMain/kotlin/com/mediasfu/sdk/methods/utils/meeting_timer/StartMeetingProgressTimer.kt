package com.mediasfu.sdk.methods.utils.meeting_timer

import com.mediasfu.sdk.util.Logger
import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Type definition for updating the meeting progress time in HH:MM:SS format.
 */
typealias UpdateMeetingProgressTime = (String) -> Unit

/**
 * Parameters for starting the meeting progress timer.
 */
interface StartMeetingProgressTimerParameters {
    val updateMeetingProgressTime: UpdateMeetingProgressTime
    val validated: Boolean
    val roomName: String
    
    /**
     * Method to retrieve updated parameters.
     */
    fun getUpdatedAllParams(): StartMeetingProgressTimerParameters
}

/**
 * Options for starting the meeting progress timer.
 */
data class StartMeetingProgressTimerOptions(
    val startTime: Long,
    val parameters: StartMeetingProgressTimerParameters
)

/**
 * Starts a timer to track the progress of a meeting.
 * 
 * This function calculates the elapsed time from the provided start time,
 * updates the time every second, and formats it to `HH:MM:SS`.
 * 
 * - If the meeting is invalidated or the room name is empty, the timer stops.
 * 
 * ### Example Usage:
 * ```kotlin
 * startMeetingProgressTimer(
 *     options = StartMeetingProgressTimerOptions(
 *         startTime = Clock.System.now().toEpochMilliseconds() / 1000,
 *         parameters = object : StartMeetingProgressTimerParameters {
 *             override val updateMeetingProgressTime: UpdateMeetingProgressTime = { time ->
 *             }
 *             override val validated: Boolean = true
 *             override val roomName: String = "room1"
 *             override fun getUpdatedAllParams(): StartMeetingProgressTimerParameters = this
 *         }
 *     )
 * )
 * ```
 */
fun startMeetingProgressTimer(
    options: StartMeetingProgressTimerOptions
) {
    val startTime = options.startTime
    var parameters = options.parameters
    Logger.d(
        "MeetingProgressTimer",
        "startMeetingProgressTimer startTime=$startTime validated=${parameters.validated} room='${parameters.roomName}'"
    )
    
    // Utility function to calculate elapsed time based on start time.
    fun calculateElapsedTime(startTime: Long): Long {
        val currentTimeInSeconds = Clock.System.now().toEpochMilliseconds() / 1000
        return currentTimeInSeconds - startTime
    }
    
    // Utility function to format time in HH:MM:SS format.
    fun padNumber(number: Long): String = number.toString().padStart(2, '0')
    
    fun formatTime(timeInSeconds: Long): String {
        val hours = timeInSeconds / 3600
        val minutes = (timeInSeconds % 3600) / 60
        val seconds = timeInSeconds % 60
        return "${padNumber(hours)}:${padNumber(minutes)}:${padNumber(seconds)}"
    }
    
    var elapsedTime = calculateElapsedTime(startTime)
    
    // Initialize and start the timer
    val job = CoroutineScope(Dispatchers.Default).launch {
        var tickCount = 0L
        while (isActive) {
            delay(1000) // Wait for 1 second
            elapsedTime++
            tickCount++
            val formattedTime = formatTime(elapsedTime)
            parameters.updateMeetingProgressTime(formattedTime)

            if (tickCount % 10L == 0L) {
                Logger.d(
                    "MeetingProgressTimer",
                    "tick=$tickCount elapsed=$formattedTime validated=${parameters.validated} room='${parameters.roomName}'"
                )
            }
            
            // Get updated parameters
            val updatedParams = parameters.getUpdatedAllParams()
            val validated = updatedParams.validated
            val roomName = updatedParams.roomName
            
            // Stop the timer if the meeting is invalidated or room name is missing
            if (!validated || roomName.isEmpty()) {
                Logger.w(
                    "MeetingProgressTimer",
                    "stopping timer tick=$tickCount validated=$validated room='${roomName}'"
                )
                cancel()
            }
        }
    }

    job.invokeOnCompletion { error ->
        if (error != null && error !is CancellationException) {
            Logger.e("MeetingProgressTimer", "timer completed with error=${error.message}")
        } else {
            Logger.d("MeetingProgressTimer", "timer completed/cancelled normally")
        }
    }
}
