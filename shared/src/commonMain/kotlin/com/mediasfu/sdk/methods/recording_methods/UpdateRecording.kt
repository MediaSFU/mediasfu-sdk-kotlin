package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.RePortParameters
import com.mediasfu.sdk.consumers.RePortType
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.UserRecordingParams
import com.mediasfu.sdk.model.toTransportMap
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Parameters required for updating the recording state, implementing several
 * interfaces for managing recording and timer state, and providing abstract
 * getters for flexible and detailed recording configurations.
 */
interface UpdateRecordingParameters :
    RePortParameters,
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
    val recordChangeSeconds: Int
    val pauseRecordCount: Int
    val startReport: Boolean
    val endReport: Boolean
    val canRecord: Boolean
    override val canPauseResume: Boolean
    val recordingVideoPausesLimit: Int
    val recordingAudioPausesLimit: Int
    override val isTimerRunning: Boolean

    // Update functions
    override val updateCanPauseResume: UpdateBooleanState
    val updatePauseRecordCount: (Int) -> Unit
    val updateClearedToRecord: UpdateBooleanState
    val updateRecordPaused: UpdateBooleanState
    val updateRecordResumed: UpdateBooleanState
    val updateStartReport: UpdateBooleanState
    val updateEndReport: UpdateBooleanState
    val updateCanRecord: UpdateBooleanState

    // Mediasfu functions
    val rePort: RePortType
    val checkPauseState: CheckPauseStateType
    val checkResumeState: CheckResumeStateType
    val recordPauseTimer: RecordPauseTimerType
    val recordResumeTimer: RecordResumeTimerType
    override val recordUpdateTimer: RecordUpdateTimerType

    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): UpdateRecordingParameters
}

/**
 * Options for the updateRecording function, containing recording parameters.
 */
data class UpdateRecordingOptions(
    val parameters: UpdateRecordingParameters
)

/**
 * Type definition for the update recording function.
 */
typealias UpdateRecordingType = suspend (UpdateRecordingOptions) -> Unit

/**
 * Updates the recording based on the given parameters, managing recording start,
 * pause, and resume states, as well as providing alerts for required conditions.
 *
 * ### Recording State Management
 * - **Pause**: Validates if recording can be paused based on limits and triggers
 *   the `recordPauseTimer` if conditions are met.
 * - **Resume**: Validates if recording can be resumed based on limits and confirms
 *   before triggering the `recordResumeTimer`.
 *
 * ### Example Usage:
 * ```kotlin
 * val options = UpdateRecordingOptions(
 *     parameters = recordingParameters
 * )
 *
 * updateRecording(options)
 * ```
 *
 * ### Error Handling:
 * - Provides alerts for invalid actions (e.g., recording stopped or media not on).
 * - Reports success or failure for each recording state change through `ShowAlert`.
 */
suspend fun updateRecording(options: UpdateRecordingOptions) {
    val parameters = options.parameters
    val roomName = parameters.roomName
    val userRecordingParams = parameters.userRecordingParams
    val socket = parameters.socket
    val localSocket = parameters.localSocket
    val updateIsRecordingModalVisible = parameters.updateIsRecordingModalVisible
    val confirmedToRecord = parameters.confirmedToRecord
    val showAlert = parameters.showAlert
    val recordingMediaOptions = parameters.recordingMediaOptions
    val videoAlreadyOn = parameters.videoAlreadyOn
    val audioAlreadyOn = parameters.audioAlreadyOn
    val recordStarted = parameters.recordStarted
    val recordPaused = parameters.recordPaused
    val recordResumed = parameters.recordResumed
    val recordStopped = parameters.recordStopped
    val recordChangeSeconds = parameters.recordChangeSeconds
    var pauseRecordCount = parameters.pauseRecordCount
    var startReport = parameters.startReport
    var endReport = parameters.endReport
    var canRecord = parameters.canRecord
    val updateCanPauseResume = parameters.updateCanPauseResume
    val updatePauseRecordCount = parameters.updatePauseRecordCount
    val updateClearedToRecord = parameters.updateClearedToRecord
    val updateRecordPaused = parameters.updateRecordPaused
    val updateRecordResumed = parameters.updateRecordResumed
    val updateStartReport = parameters.updateStartReport
    val updateEndReport = parameters.updateEndReport
    val updateCanRecord = parameters.updateCanRecord
    val rePort = parameters.rePort
    val checkPauseState = parameters.checkPauseState
    val checkResumeState = parameters.checkResumeState
    val recordPauseTimer = parameters.recordPauseTimer
    val recordResumeTimer = parameters.recordResumeTimer
    val recordUpdateTimer = parameters.recordUpdateTimer

    // Check if recording has stopped
    if (recordStopped) {
        showAlert?.invoke(
            "Recording has already stopped",
            "danger",
            3000
        )
        return
    }

    // Check media options for video and audio
    if (recordingMediaOptions == "video" && !videoAlreadyOn) {
        showAlert?.invoke(
            "You must turn on your video before you can start recording",
            "danger",
            3000
        )
        return
    }
    if (recordingMediaOptions == "audio" && !audioAlreadyOn) {
        showAlert?.invoke(
            "You must turn on your audio before you can start recording",
            "danger",
            3000
        )
        return
    }

    val socketRef = if (localSocket != null && localSocket.id != null) {
        localSocket
    } else {
        socket!!
    }

    // Handle Pause Action
    if (recordStarted && !recordPaused && !recordStopped) {
        val optionsCheckPause = CheckPauseStateOptions(
            recordingMediaOptions = recordingMediaOptions,
            recordingVideoPausesLimit = parameters.recordingVideoPausesLimit,
            recordingAudioPausesLimit = parameters.recordingAudioPausesLimit,
            pauseRecordCount = pauseRecordCount,
            showAlert = showAlert
        )
        val proceed = checkPauseState(optionsCheckPause)
        if (!proceed) return

        val optionsPause = RecordPauseTimerOptions(
            stop = false,
            isTimerRunning = parameters.isTimerRunning,
            canPauseResume = parameters.canPauseResume,
            showAlert = parameters.showAlert
        )
        val record = recordPauseTimer(optionsPause)
        if (record) {
            // Close the recording modal as soon as we're committed to pausing.
            // (React/Flutter parity: modal closes immediately on Pause/Resume)
            updateIsRecordingModalVisible(false)

            val action = "pauseRecord"

            // Emit socket event and wait for acknowledgment
            val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
                socketRef.emitWithAck(action, mapOf("roomName" to roomName)) { data ->
                    continuation.resume(data as Map<String, Any>)
                }
            }

            val success = result["success"] as? Boolean ?: false
            val reason = result["reason"] as? String ?: ""
            val recordState = result["recordState"] as? String ?: ""
            val pauseCount = result["pauseCount"] as? Int ?: 0

            pauseRecordCount = pauseCount
            updatePauseRecordCount(pauseRecordCount)

            if (success) {
                startReport = false
                endReport = true
                val newRecordPaused = true
                updateStartReport(startReport)
                updateEndReport(endReport)
                updateRecordPaused(newRecordPaused)

                showAlert?.invoke(
                    "Recording paused successfully",
                    "success",
                    3000
                )

                delay(recordChangeSeconds.toLong())
                updateCanPauseResume(true)
            } else {
                val reasonMessage = "Recording Pause Failed: $reason; the current state is: $recordState"
                showAlert?.invoke(
                    reasonMessage,
                    "danger",
                    3000
                )
            }
        }
    }
    // Handle Resume Action
    else if (recordStarted && recordPaused && !recordStopped) {
        if (!confirmedToRecord) {
            showAlert?.invoke(
                "You must click confirm before you can start recording",
                "danger",
                3000
            )
            return
        }

        val optionsResume = RecordResumeTimerOptions(
            parameters = parameters
        )

        val optionsCheckResume = CheckResumeStateOptions(
            recordingMediaOptions = recordingMediaOptions,
            recordingVideoPausesLimit = parameters.recordingVideoPausesLimit,
            recordingAudioPausesLimit = parameters.recordingAudioPausesLimit,
            pauseRecordCount = pauseRecordCount
        )
        val proceed = checkResumeState(optionsCheckResume)
        if (!proceed) return

        val resume = recordResumeTimer(optionsResume)
        if (resume) {
            // Close the recording modal as soon as we're committed to resuming.
            updateIsRecordingModalVisible(false)

            updateClearedToRecord(true)

            val action = "resumeRecord"

            // Emit socket event and wait for acknowledgment
            val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
            socketRef.emitWithAck(action, mapOf(
                "roomName" to roomName,
                "userRecordingParams" to userRecordingParams.toTransportMap()
            )) { data ->
                    continuation.resume(data as Map<String, Any>)
                }
            }

            val success = result["success"] as? Boolean ?: false
            val reason = result["reason"] as? String ?: ""

            if (success) {
                val newRecordPaused = false
                val newRecordResumed = true
                updateRecordPaused(newRecordPaused)
                updateRecordResumed(newRecordResumed)

                val optionsReport = RePortOptions(
                    parameters = parameters.getUpdatedAllParams(),
                    restart = true
                )
                rePort(optionsReport)
            } else {
                showAlert?.invoke(
                    "Cannot start recording. Ensure media is on and you are cleared to record",
                    "danger",
                    3000
                )

                canRecord = true
                startReport = false
                endReport = true
                updateCanRecord(canRecord)
                updateStartReport(startReport)
                updateEndReport(endReport)
            }
            delay(recordChangeSeconds.toLong())
            updateCanPauseResume(true)
        }
    }
}
