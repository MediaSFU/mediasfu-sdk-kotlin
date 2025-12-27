package com.mediasfu.sdk.methods.recording_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.RePortParameters
import com.mediasfu.sdk.consumers.RePortType
import com.mediasfu.sdk.consumers.TriggerOptions
import com.mediasfu.sdk.methods.recording_methods.UpdateBooleanState
import com.mediasfu.sdk.methods.whiteboard_methods.CaptureCanvasStreamOptions
import com.mediasfu.sdk.methods.whiteboard_methods.captureCanvasStream
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.UserRecordingParams
import com.mediasfu.sdk.model.toTransportMap
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Parameters required for starting the recording.
 */
interface StartRecordingParameters :
    CaptureCanvasStreamParameters,
    RePortParameters,
    RecordStartTimerParameters,
    RecordResumeTimerParameters {

    // Core properties
    override val roomName: String
    val userRecordingParams: UserRecordingParams
    override val socket: SocketManager?
    override val localSocket: SocketManager?
    val updateIsRecordingModalVisible: UpdateBooleanState
    val confirmedToRecord: Boolean
    override val showAlert: ShowAlert?
    val recordingMediaOptions: String
    override val videoAlreadyOn: Boolean
    override val audioAlreadyOn: Boolean
    override val recordStarted: Boolean
    override val recordPaused: Boolean
    override val recordResumed: Boolean
    override val recordStopped: Boolean
    val startReport: Boolean
    val endReport: Boolean
    val canRecord: Boolean
    val updateClearedToRecord: UpdateBooleanState
    val updateRecordStarted: UpdateBooleanState
    val updateRecordPaused: UpdateBooleanState
    val updateRecordResumed: UpdateBooleanState
    val updateStartReport: UpdateBooleanState
    val updateEndReport: UpdateBooleanState
    val updateCanRecord: UpdateBooleanState
    override val updateRecordingProgressTime: (String) -> Unit
    override val whiteboardStarted: Boolean
    override val whiteboardEnded: Boolean

    // Mediasfu functions
    val rePort: RePortType
    val recordStartTimer: RecordStartTimerType
    val recordResumeTimer: RecordResumeTimerType

    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): StartRecordingParameters
}

/**
 * Options for starting recording.
 */
data class StartRecordingOptions(
    val parameters: StartRecordingParameters
)

/**
 * Type definition for StartRecording function.
 */
typealias StartRecordingType = suspend (StartRecordingOptions) -> Boolean?

/**
 * Starts the recording process, managing different states and actions based on recording options.
 *
 * The `startRecording` function validates if recording can begin by checking conditions such as user confirmation,
 * video/audio availability, and specific recording options. It then initiates either a new recording or resumes an
 * existing one, updating related state properties and calling required functions.
 *
 * This function is also responsible for displaying alerts if conditions are not met and emitting socket events
 * to manage recording on the server. Additionally, it supports capturing a whiteboard stream if specified.
 *
 * ## Parameters:
 * - `parameters`: An instance of [StartRecordingParameters] that provides all the required properties and callback
 *   functions needed to manage the recording state, interactions with socket events, and any required updates.
 *
 * ## Returns:
 * - `Boolean?`: A boolean indicating whether the recording started/resumed
 *   successfully (`true`), was unable to start due to a condition (`false`), or encountered an error (`null`).
 *
 * ## Example Usage:
 *
 * ```kotlin
 * val startParams = object : StartRecordingParameters {
 *     override val roomName: String = "Room_123"
 *     override val userRecordingParams: UserRecordingParams = UserRecordingParams(...)
 *     override val socket: SocketManager? = socketInstance
 *     override val localSocket: SocketManager? = localSocketInstance
 *     override val confirmedToRecord: Boolean = true
 *     override val recordingMediaOptions: String = "video"
 *     override val videoAlreadyOn: Boolean = true
 *     override val audioAlreadyOn: Boolean = true
 *     override val recordStarted: Boolean = false
 *     override val recordPaused: Boolean = false
 *     override val recordResumed: Boolean = false
 *     override val recordStopped: Boolean = false
 *     override val startReport: Boolean = false
 *     override val endReport: Boolean = true
 *     override val canRecord: Boolean = true
 *     override val whiteboardStarted: Boolean = true
 *     override val whiteboardEnded: Boolean = false
 *     // Other properties...
 *     override fun getUpdatedAllParams(): StartRecordingParameters = this
 * }
 *
 * val started = startRecording(StartRecordingOptions(parameters = startParams))
 * ```
 */
suspend fun startRecording(options: StartRecordingOptions): Boolean? {
    val parameters = options.parameters
    return try {
        val updatedParams = parameters.getUpdatedAllParams()
        var recordStarted = parameters.recordStarted
        var startReport = parameters.startReport
        var endReport = parameters.endReport
        var recordPaused = parameters.recordPaused

        if (!parameters.confirmedToRecord) {
            parameters.showAlert?.invoke(
                "You must click confirm before you can start recording",
                "danger",
                3000
            )
            return false
        }

        if (parameters.recordingMediaOptions == "video" && !parameters.videoAlreadyOn) {
            parameters.showAlert?.invoke(
                "You must turn on your video before you can start recording",
                "danger",
                3000
            )
            return false
        }

        if (parameters.recordingMediaOptions == "audio" && !parameters.audioAlreadyOn) {
            parameters.showAlert?.invoke(
                "You must turn on your audio before you can start recording",
                "danger",
                3000
            )
            return false
        }

        // Close the recording modal as soon as we're committed to starting.
        // (React/Flutter parity: modal closes immediately on Start)
        parameters.updateIsRecordingModalVisible(false)

        parameters.updateClearedToRecord(true)

        val action = if (parameters.recordStarted &&
            parameters.recordPaused &&
            !parameters.recordResumed &&
            !parameters.recordStopped) {
            "resumeRecord"
        } else {
            "startRecord"
        }

        var recAttempt = false
        val socketRef = if (parameters.localSocket != null && parameters.localSocket?.id != null) {
            parameters.localSocket!!
        } else {
            parameters.socket!!
        }

        // Emit socket event and wait for acknowledgment
        val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
            socketRef.emitWithAck(action, mapOf(
                "roomName" to parameters.roomName,
                "userRecordingParams" to parameters.userRecordingParams.toTransportMap()
            )) { data ->
                continuation.resume(data as Map<String, Any>)
            }
        }

        val success = result["success"] as Boolean
        val reason = result["reason"] as String

        if (success) {
            recordStarted = true
            startReport = true
            endReport = false
            recordPaused = false
            recAttempt = true

            parameters.updateRecordStarted(recordStarted)
            parameters.updateStartReport(startReport)
            parameters.updateEndReport(endReport)
            parameters.updateRecordPaused(recordPaused)

            if (action == "startRecord") {
                val optionsReport = RePortOptions(parameters = updatedParams)
                parameters.rePort(optionsReport)

                // Force trigger to ensure backend gets the layout
                val triggerOptions = TriggerOptions(
                    refActiveNames = updatedParams.activeNames,
                    parameters = updatedParams
                )
                parameters.trigger(triggerOptions)

                val recordOptions = RecordStartTimerOptions(parameters = updatedParams)
                // Call through parameters adapter to ensure fresh state is used
                parameters.recordStartTimer(recordOptions)
            } else {
                parameters.updateRecordResumed(true)
                val optionsReport = RePortOptions(parameters = updatedParams, restart = true)
                parameters.rePort(optionsReport)
                val recordOptions = RecordResumeTimerOptions(parameters = updatedParams)
                // Call through parameters adapter to ensure fresh state is used
                parameters.recordResumeTimer(recordOptions)
            }
        } else {
            parameters.showAlert?.invoke(
                "Recording could not start - $reason",
                "danger",
                3000
            )
            parameters.updateCanRecord(true)
            parameters.updateStartReport(false)
            parameters.updateEndReport(true)
        }

        if (recAttempt &&
            parameters.whiteboardStarted &&
            !parameters.whiteboardEnded &&
            parameters.recordingMediaOptions == "video") {
            // Capture the whiteboard canvas stream for recording
            try {
                val optionsCapture = CaptureCanvasStreamOptions(
                    parameters = parameters,
                    start = true
                )
                captureCanvasStream(optionsCapture)
            } catch (e: Exception) {
                // Whiteboard stream capture failed - non-fatal
            }
        }

        // Keep this here as a safety net in case upstream callers didn't close.
        parameters.updateIsRecordingModalVisible(false)
        CoroutineScope(Dispatchers.Main).launch {
            // recordChangeSeconds is already in milliseconds (default 15000)
            delay(updatedParams.recordChangeSeconds.toLong())
            parameters.updateCanPauseResume(true)
        }
        recAttempt
    } catch (error: Exception) {
        Logger.e("StartRecording", "Error in startRecording: $error")
        null
    }
}
