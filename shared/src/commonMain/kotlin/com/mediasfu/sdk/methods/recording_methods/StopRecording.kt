package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.methods.recording_methods.RecordingUtils
import com.mediasfu.sdk.methods.whiteboard_methods.StopCanvasStreamOptions
import com.mediasfu.sdk.methods.whiteboard_methods.StopCanvasStreamParameters
import com.mediasfu.sdk.methods.whiteboard_methods.stopCanvasStream
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Parameters required to stop the recording.
 */
interface StopRecordingParameters {
    // Core properties
    val roomName: String
    val socket: SocketManager?
    val localSocket: SocketManager?
    val showAlert: ShowAlert?
    val startReport: Boolean
    val endReport: Boolean
    val recordStarted: Boolean
    val recordPaused: Boolean
    val recordStopped: Boolean
    val isTimerRunning: Boolean
    val canPauseResume: Boolean
    val recordTimerJob: Job?
    
    // Update functions
    val updateRecordPaused: UpdateBooleanState
    val updateRecordStopped: UpdateBooleanState
    val updateStartReport: UpdateBooleanState
    val updateEndReport: UpdateBooleanState
    val updateShowRecordButtons: UpdateBooleanState
    val updateRecordStarted: UpdateBooleanState
    val updateRecordResumed: UpdateBooleanState
    val updateCanRecord: UpdateBooleanState
    val updateIsTimerRunning: UpdateBooleanState
    val updateCanPauseResume: UpdateBooleanState
    val updateRecordElapsedTime: (Int) -> Unit
    val updateRecordingProgressTime: UpdateRecordingProgressTime
    val updateRecordStartTime: (Long?) -> Unit
    val updateRecordTimerJob: (Job?) -> Unit
    val updateRecordState: (String) -> Unit
    val updateClearedToRecord: UpdateBooleanState?
    val updateClearedToResume: UpdateBooleanState?
    val updatePauseRecordCount: ((Int) -> Unit)?
    val updateIsRecordingModalVisible: UpdateBooleanState
    
    // Additional properties
    val whiteboardStarted: Boolean
    val whiteboardEnded: Boolean
    val recordingMediaOptions: String
    
    // Stop canvas stream parameters (optional - may return null if not available)
    fun asStopCanvasStreamParameters(): StopCanvasStreamParameters?
}

/**
 * Options for stopping recording.
 */
data class StopRecordingOptions(
    val parameters: StopRecordingParameters
)

/**
 * Type definition for StopRecording function.
 */
typealias StopRecordingType = suspend (StopRecordingOptions) -> Unit

/**
 * Stops the recording process, managing different states and actions based on current recording status.
 * 
 * The `stopRecording` function verifies if a recording session is active and not yet stopped. If conditions
 * allow, it sends an event to stop recording on the server, updates state parameters accordingly, and shows
 * an alert confirming the stop action. It also handles any required canvas stream stop if the recording involves video.
 * 
 * ## Parameters:
 * - `parameters`: An instance of [StopRecordingParameters] providing the recording room, socket connection,
 *   and callback functions needed to manage recording states and UI updates.
 * 
 * ## Returns:
 * - `Unit`: This function completes once the recording stop action and any related updates or alerts are handled.
 * 
 * ## Example Usage:
 * 
 * ```kotlin
 * val stopParams = object : StopRecordingParameters {
 *     override val roomName: String = "Room_123"
 *     override val socket: SocketManager? = socketInstance
 *     override val localSocket: SocketManager? = localSocketInstance
 *     override val startReport: Boolean = true
 *     override val endReport: Boolean = false
 *     override val recordStarted: Boolean = true
 *     override val recordPaused: Boolean = false
 *     override val recordStopped: Boolean = false
 *     override val whiteboardStarted: Boolean = true
 *     override val whiteboardEnded: Boolean = false
 *     override val recordingMediaOptions: String = "video"
 * }
 * 
 * // Call stopRecording to stop the recording process
 * stopRecording(StopRecordingOptions(parameters = stopParams))
 * // Expected output:
 * // Alert: Recording Stopped
 * // Show Record Buttons: false
 * // Capture Canvas Stream: {parameters: StopRecordingParameters, start: false}
 * ```
 */
suspend fun stopRecording(options: StopRecordingOptions) {
    val parameters = options.parameters
    
    if (parameters.recordStarted && !parameters.recordStopped) {
        val optionsPause = RecordPauseTimerOptions(
            stop = true,
            isTimerRunning = parameters.isTimerRunning,
            canPauseResume = parameters.canPauseResume,
            showAlert = parameters.showAlert
        )
        val canStop = recordPauseTimer(optionsPause)
        
        if (canStop) {
            // Close the recording modal as soon as we're committed to stopping.
            // (React/Flutter parity: modal closes immediately on Stop)
            parameters.updateIsRecordingModalVisible(false)

            val action = "stopRecord"
            
            val socketRef = if (parameters.localSocket != null && parameters.localSocket?.id != null) {
                parameters.localSocket!!
            } else {
                parameters.socket!!
            }
            
            // Emit socket event and wait for acknowledgment
            val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
                socketRef.emitWithAck(action, mapOf("roomName" to parameters.roomName)) { data ->
                    continuation.resume(data as Map<String, Any>)
                }
            }
            
            val success = result["success"] as Boolean
            val reason = result["reason"] as String
            val recordState = result["recordState"] as String
            
            if (success) {
                RecordingUtils.cleanUpRecording(
                    RecordingUtils.CleanUpRecordingOptions(
                        recordTimerJob = parameters.recordTimerJob,
                        updateRecordTimerJob = parameters.updateRecordTimerJob,
                        updateIsTimerRunning = parameters.updateIsTimerRunning,
                        updateCanPauseResume = parameters.updateCanPauseResume,
                        updateRecordElapsedTime = parameters.updateRecordElapsedTime,
                        updateRecordingProgressTime = parameters.updateRecordingProgressTime,
                        updateRecordStartTime = parameters.updateRecordStartTime,
                        updateRecordStarted = parameters.updateRecordStarted,
                        updateRecordPaused = parameters.updateRecordPaused,
                        updateRecordResumed = parameters.updateRecordResumed,
                        updateRecordStopped = parameters.updateRecordStopped,
                        updateStartReport = parameters.updateStartReport,
                        updateEndReport = parameters.updateEndReport,
                        updateCanRecord = parameters.updateCanRecord,
                        updateShowRecordButtons = parameters.updateShowRecordButtons,
                        updateRecordState = parameters.updateRecordState,
                        updateClearedToRecord = parameters.updateClearedToRecord,
                        updateClearedToResume = parameters.updateClearedToResume,
                        updatePauseRecordCount = parameters.updatePauseRecordCount,
                        showAlert = parameters.showAlert,
                        alertMessage = "Recording Stopped"
                    )
                )

                // Handle canvas stream if necessary
                if (parameters.whiteboardStarted &&
                    !parameters.whiteboardEnded &&
                    parameters.recordingMediaOptions == "video") {
                    // Stop the whiteboard canvas stream capture
                    try {
                        val stopParams = parameters.asStopCanvasStreamParameters()
                        if (stopParams != null) {
                            val optionsStop = StopCanvasStreamOptions(
                                parameters = stopParams
                            )
                            stopCanvasStream(optionsStop)
                        }
                    } catch (e: Exception) {
                        // Whiteboard stream stop failed - non-fatal
                    }
                }
            } else {
                val reasonMessage = "Recording Stop Failed: $reason; the recording is currently $recordState"
                parameters.showAlert?.invoke(reasonMessage, "danger", 3000)
            }
        }
    } else {
        parameters.showAlert?.invoke(
            "Recording is not started yet or already stopped",
            "danger",
            3000
        )
    }
}
