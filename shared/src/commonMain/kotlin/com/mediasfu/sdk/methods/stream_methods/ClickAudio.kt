package com.mediasfu.sdk.methods.stream_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.CheckPermissionException
import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioType
import com.mediasfu.sdk.consumers.RequestPermissionAudioType
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioParameters
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioOptions
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioType
import com.mediasfu.sdk.consumers.StreamSuccessAudioOptions
import com.mediasfu.sdk.consumers.StreamSuccessAudioParameters
import com.mediasfu.sdk.consumers.StreamSuccessAudioType
import com.mediasfu.sdk.methods.recording_methods.CheckPauseStateType
import com.mediasfu.sdk.methods.recording_methods.RecordPauseTimerType
import com.mediasfu.sdk.methods.recording_methods.RecordPauseTimerOptions
import com.mediasfu.sdk.methods.recording_methods.RecordResumeTimerType
import com.mediasfu.sdk.methods.recording_methods.RecordResumeTimerOptions
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.datetime.Clock

/**
 * Parameters for ClickAudio function.
 */
interface ClickAudioParameters :
    DisconnectSendTransportAudioParameters,
    ResumeSendTransportAudioParameters,
    StreamSuccessAudioParameters {

    // Core properties
    val checkMediaPermission: Boolean
    val hasAudioPermission: Boolean
    val audioPaused: Boolean
    override val audioAlreadyOn: Boolean
    val audioOnlyRoom: Boolean
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordPaused: Boolean
    val recordStopped: Boolean
    val recordingMediaOptions: String
    override val islevel: String
    val youAreCoHost: Boolean
    val adminRestrictSetting: Boolean
    val audioRequestState: String?
    val audioRequestTime: Long?
    override val member: String
    override val socket: SocketManager?
    override val localSocket: SocketManager?
    override val roomName: String
    override val userDefaultAudioInputDevice: String
    override val micAction: Boolean
    override val localStream: MediaStream?
    val audioSetting: String
    val videoSetting: String
    val screenshareSetting: String
    val chatSetting: String
    val updateRequestIntervalSeconds: Int
    override val participants: List<Participant>
    override var transportCreated: Boolean
    override val transportCreatedAudio: Boolean

    // Callback functions
    override val updateAudioAlreadyOn: (Boolean) -> Unit
    val updateAudioRequestState: (String?) -> Unit
    val updateAudioPaused: (Boolean) -> Unit
    override val updateLocalStream: (MediaStream?) -> Unit
    override val updateParticipants: (List<Participant>) -> Unit
    override val updateTransportCreated: (Boolean) -> Unit
    override val updateTransportCreatedAudio: (Boolean) -> Unit
    override val updateMicAction: (Boolean) -> Unit
    override val showAlert: ShowAlert?

    // Mediasfu functions
    val checkPermission: CheckPermissionType
    val streamSuccessAudio: StreamSuccessAudioType
    val disconnectSendTransportAudio: DisconnectSendTransportAudioType
    val requestPermissionAudio: RequestPermissionAudioType
    override val resumeSendTransportAudio: ResumeSendTransportAudioType

    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): ClickAudioParameters
}

/**
 * Options for ClickAudio function.
 */
data class ClickAudioOptions(
    val parameters: ClickAudioParameters
)

/**
 * Type definition for ClickAudio function.
 */
typealias ClickAudioType = suspend (ClickAudioOptions) -> Unit

/**
 * Toggles audio for a user, either enabling or disabling the microphone.
 * 
 * ### Parameters:
 * - `options` (`ClickAudioOptions`): Contains all required parameters and callbacks.
 *   - `parameters` (`ClickAudioParameters`): The key configurations for permissions,
 *      media settings, callback functions, and state variables.
 * 
 * ### Workflow:
 * 1. **Audio Toggle**:
 *    - **Disable**: Checks if recording is active and if it's safe to disable audio.
 *    - **Enable**: Verifies permissions and sends a request to the host if necessary.
 * 
 * 2. **Permissions & Requests**:
 *    - If the user lacks permissions, sends a request to the host or prompts for permissions.
 *    - Uses callbacks to update the UI and emit requests or permission checks.
 * 
 * 3. **Media Constraints**:
 *    - Configures constraints for `getUserMedia` based on device and user preference.
 * 
 * ### Example Usage:
 * ```kotlin
 * val parameters = object : ClickAudioParameters {
 *     override val checkMediaPermission: Boolean = true
 *     override val hasAudioPermission: Boolean = false
 *     override val audioPaused: Boolean = false
 *     // Other properties and callbacks...
 * }
 * 
 * clickAudio(ClickAudioOptions(parameters = parameters))
 * ```
 * 
 * ### Error Handling:
 * - Logs any errors to the console in debug mode.
 */
suspend fun clickAudio(options: ClickAudioOptions) {
    try {
        val parameters = options.parameters
        
        // Destructure parameters
        val checkMediaPermission = parameters.checkMediaPermission
        var hasAudioPermission = parameters.hasAudioPermission
        var audioPaused = parameters.audioPaused
        var audioAlreadyOn = parameters.audioAlreadyOn
        val audioOnlyRoom = parameters.audioOnlyRoom
        val recordStarted = parameters.recordStarted
        val recordResumed = parameters.recordResumed
        val recordPaused = parameters.recordPaused
        val recordStopped = parameters.recordStopped
        val recordingMediaOptions = parameters.recordingMediaOptions
        val islevel = parameters.islevel
        val youAreCoHost = parameters.youAreCoHost
        val adminRestrictSetting = parameters.adminRestrictSetting
        var audioRequestState = parameters.audioRequestState
        val audioRequestTime = parameters.audioRequestTime
        val member = parameters.member
        val socket = parameters.socket
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        val userDefaultAudioInputDevice = parameters.userDefaultAudioInputDevice
        var micAction = parameters.micAction
        var localStream = parameters.localStream
        val audioSetting = parameters.audioSetting
        val videoSetting = parameters.videoSetting
        val screenshareSetting = parameters.screenshareSetting
        val chatSetting = parameters.chatSetting
        val updateRequestIntervalSeconds = parameters.updateRequestIntervalSeconds
        val participants = parameters.participants
        var transportCreated = parameters.transportCreated
        var transportCreatedAudio = parameters.transportCreatedAudio
        
        // Callback functions
        val updateAudioAlreadyOn = parameters.updateAudioAlreadyOn
        val updateAudioRequestState = parameters.updateAudioRequestState
        val updateAudioPaused = parameters.updateAudioPaused
        val updateLocalStream = parameters.updateLocalStream
        val updateParticipants = parameters.updateParticipants
        val updateTransportCreated = parameters.updateTransportCreated
        val updateTransportCreatedAudio = parameters.updateTransportCreatedAudio
        val updateMicAction = parameters.updateMicAction
        val showAlert = parameters.showAlert
        
        // mediasfu functions
        val checkPermission = parameters.checkPermission
        val streamSuccessAudio = parameters.streamSuccessAudio
        val requestPermissionAudio = parameters.requestPermissionAudio
        val disconnectSendTransportAudio = parameters.disconnectSendTransportAudio
        val resumeSendTransportAudio = parameters.resumeSendTransportAudio

        
        if (audioOnlyRoom) {
            showAlert?.invoke(
                "You cannot turn on your camera in an audio-only event.",
                "danger",
                3000
            )
            return
        }
        
        if (audioAlreadyOn) {
            // Check and alert before turning off
            if (islevel == "2" &&
                (recordStarted || recordResumed) &&
                !(recordPaused || recordStopped) &&
                recordingMediaOptions == "audio") {
                showAlert?.invoke(
                    "You cannot turn off your audio while recording, please pause or stop recording first.",
                    "danger",
                    3000
                )
                return
            }
            
            // Update the icon and turn off audio
            audioAlreadyOn = false
            updateAudioAlreadyOn(audioAlreadyOn)
            
            // Disable the audio track to stop capturing/sending media
            localStream?.getAudioTracks()?.firstOrNull()?.setEnabled(false)
            
            updateLocalStream(localStream)
            val optionsDisconnect = DisconnectSendTransportAudioOptions(
                parameters = parameters
            )
            disconnectSendTransportAudio(optionsDisconnect)
            audioPaused = true
            updateAudioPaused(audioPaused)
        } else {
            if (adminRestrictSetting) {
                showAlert?.invoke(
                    message = "You cannot turn on your microphone. Access denied by host.",
                    type = "danger",
                    duration = 3000
                )
                return
            }
            
            var response = 2
            
            if (!micAction && islevel != "2" && !youAreCoHost) {
                val optionsCheck = CheckPermissionOptions(
                    permissionType = "audioSetting",
                    audioSetting = audioSetting,
                    videoSetting = videoSetting,
                    screenshareSetting = screenshareSetting,
                    chatSetting = chatSetting
                )
                response = try {
                    checkPermission(optionsCheck)
                } catch (error: CheckPermissionException) {
                    showAlert?.invoke(
                        message = error.message ?: "Permission check failed.",
                        type = "danger",
                        duration = 3000
                    )
                    2
                }
            } else {
                response = 0
            }
            
            when (response) {
                1 -> {
                    if (audioRequestState == "pending") {
                        showAlert?.invoke(
                            message = "A request is pending. Please wait for the host to respond.",
                            type = "danger",
                            duration = 3000
                        )
                        return
                    }
                    
                    if (audioRequestState == "rejected" &&
                        audioRequestTime != null &&
                        Clock.System.now().toEpochMilliseconds() - audioRequestTime <
                        updateRequestIntervalSeconds * 1000L) {
                        showAlert?.invoke(
                            message = "A request was rejected. Please wait for $updateRequestIntervalSeconds seconds before sending another request.",
                            type = "danger",
                            duration = 3000
                        )
                        return
                    }
                    
                    showAlert?.invoke(
                        message = "Request sent to host.",
                        type = "success",
                        duration = 3000
                    )
                    audioRequestState = "pending"
                    updateAudioRequestState(audioRequestState)
                    
                    val userRequest = mapOf(
                        "id" to socket?.id,
                        "name" to member,
                        "icon" to "fa-microphone"
                    )
                    socket?.emit("participantRequest", mapOf(
                        "userRequest" to userRequest,
                        "roomName" to roomName
                    ))
                }
                
                2 -> {
                    showAlert?.invoke(
                        message = "You cannot turn on your microphone. Access denied by host.",
                        type = "danger",
                        duration = 3000
                    )
                }
                
                0 -> {
                    if (audioPaused) {
                        
                        // Re-enable the audio track to resume capturing/sending media
                        localStream?.getAudioTracks()?.firstOrNull()?.setEnabled(true)
                        
                        updateAudioAlreadyOn(true)
                        val optionsResume = ResumeSendTransportAudioOptions(
                            parameters = parameters
                        )
                        resumeSendTransportAudio(optionsResume)
                        socket?.emit("resumeProducerAudio", mapOf(
                            "mediaTag" to "audio",
                            "roomName" to roomName
                        ))
                        
                        try {
                            if (localSocket != null && localSocket.id != null) {
                                localSocket.emit("resumeProducerAudio", mapOf(
                                    "mediaTag" to "audio",
                                    "roomName" to roomName
                                ))
                            }
                        } catch (e: Exception) {
                            Logger.e("ClickAudio", "Error resuming audio producer", e)
                        }
                        
                        updateLocalStream(localStream)
                        if (micAction) {
                            micAction = false
                            updateMicAction(micAction)
                        }
                        
                        val updatedParticipants = participants.map { participant ->
                            if (participant.id == socket?.id && participant.name == member) {
                                participant.copy(muted = false)
                            } else {
                                participant
                            }
                        }
                        updateParticipants(updatedParticipants)
                        
                        transportCreated = true
                        updateTransportCreated(transportCreated)
                        transportCreatedAudio = true
                        updateTransportCreatedAudio(transportCreatedAudio)
                    } else {
                        // First check if permission is granted
                        if (!hasAudioPermission) {
                            if (checkMediaPermission) {
                                val statusMic = requestPermissionAudio()
                                if (statusMic != true) {
                                    showAlert?.invoke(
                                        message = "Allow access to your microphone or check if your microphone is not being used by another application.",
                                        type = "danger",
                                        duration = 3000
                                    )
                                    return
                                }
                            }
                        }
                        
                        val mediaConstraints = if (userDefaultAudioInputDevice.isNotEmpty()) {
                            mapOf(
                                "audio" to mapOf("deviceId" to userDefaultAudioInputDevice),
                                "video" to false
                            )
                        } else {
                            mapOf("audio" to true, "video" to false)
                        }
                        
                        try {
                            val device = parameters.device
                            if (device == null) {
                                showAlert?.invoke(
                                    message = "Unable to access the microphone. Please restart the call or app.",
                                    type = "danger",
                                    duration = 3000
                                )
                                return
                            }
                            val stream = device.getUserMedia(mediaConstraints)
                            val optionsStream = StreamSuccessAudioOptions(
                                parameters = parameters,
                                stream = stream,
                                audioConstraints = mediaConstraints
                            )
                            streamSuccessAudio(optionsStream)
                        } catch (error: Exception) {
                            Logger.e("ClickAudio", "MediaSFU - clickAudio: failed to obtain audio stream -> ${error.message}")
                            showAlert?.invoke(
                                message = "Allow access to your microphone or check if your microphone is not being used by another application.",
                                type = "danger",
                                duration = 3000
                            )
                        }
                    }
                }
            }
        }
    } catch (error: Exception) {
        Logger.e("ClickAudio", "Error in clickAudio: $error")
    }
}
