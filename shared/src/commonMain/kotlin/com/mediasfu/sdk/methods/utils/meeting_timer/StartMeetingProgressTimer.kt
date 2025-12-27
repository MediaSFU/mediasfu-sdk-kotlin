package com.mediasfu.sdk.methods.utils.meeting_timer

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
        while (isActive) {
            delay(1000) // Wait for 1 second
            elapsedTime++
            val formattedTime = formatTime(elapsedTime)
            parameters.updateMeetingProgressTime(formattedTime)
            
            // Get updated parameters
            val updatedParams = parameters.getUpdatedAllParams()
            val validated = updatedParams.validated
            val roomName = updatedParams.roomName
            
            // Stop the timer if the meeting is invalidated or room name is missing
            if (!validated || roomName.isEmpty()) {
                cancel()
            }
        }
    }
}
