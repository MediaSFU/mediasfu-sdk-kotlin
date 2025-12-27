package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcTransport

typealias StreamSuccessAudioType = suspend (StreamSuccessAudioOptions) -> Unit

/**
 * Parameters for stream success audio handling.
 */
interface StreamSuccessAudioParameters : CreateSendTransportParameters, ConnectSendTransportAudioParameters {
    override val socket: SocketManager?
    override val localSocket: SocketManager?
    override val participants: List<Participant>
    override val localStream: MediaStream?
    override var transportCreated: Boolean
    override var localTransportCreated: Boolean
    override val transportCreatedAudio: Boolean
    override val audioAlreadyOn: Boolean
    override val micAction: Boolean
    override val audioParams: ProducerOptionsType?
    override val localStreamAudio: MediaStream?
    override val defAudioID: String
    override val userDefaultAudioInputDevice: String
    override val params: ProducerOptionsType?
    override val aParams: ProducerOptionsType?
    override val hostLabel: String
    override val islevel: String
    override val member: String
    override var producerTransport: WebRtcTransport?
    override var localProducerTransport: WebRtcTransport?
    override val updateMainWindow: Boolean
    override val lockScreen: Boolean
    override val shared: Boolean
    override val videoAlreadyOn: Boolean
    override val showAlert: ShowAlert?

    override val updateParticipants: (List<Participant>) -> Unit
    override val updateTransportCreated: (Boolean) -> Unit
    override val updateTransportCreatedAudio: (Boolean) -> Unit
    override val updateAudioAlreadyOn: (Boolean) -> Unit
    override val updateMicAction: (Boolean) -> Unit
    override val updateAudioParams: (ProducerOptionsType?) -> Unit
    override val updateLocalStream: (MediaStream?) -> Unit
    override val updateLocalStreamAudio: (MediaStream?) -> Unit
    override val updateDefAudioID: (String) -> Unit
    override val updateUserDefaultAudioInputDevice: (String) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit

    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
    override val resumeSendTransportAudio: suspend (ResumeSendTransportAudioOptions) -> Unit

    override fun getUpdatedAllParams(): StreamSuccessAudioParameters
}

/**
 * Options for stream success audio.
 *
 * @property stream The audio media stream
 * @property parameters Parameters for audio stream handling
 * @property audioConstraints Optional audio constraints
 */
data class StreamSuccessAudioOptions(
    val stream: MediaStream,
    val parameters: StreamSuccessAudioParameters,
    val audioConstraints: Map<String, Any>? = null
)

/**
 * Manages the setup and successful transition of audio streaming.
 *
 * This function handles the configuration of audio transports, updates audio settings,
 * and manages UI state when audio streaming is successfully established.
 *
 * ### Key Responsibilities:
 * - Sets up or switches to a new local audio stream
 * - Manages default audio device configuration
 * - Creates or connects to audio transport if required
 * - Updates participant mute states
 * - Manages UI components based on user roles
 *
 * ### Workflow:
 * 1. **Stream Setup**: Configures the local audio stream
 * 2. **Device Management**: Updates default audio input device
 * 3. **Transport Handling**: Creates/connects transport as needed
 * 4. **State Updates**: Updates audio and participant states
 * 5. **UI Refresh**: Triggers UI updates if needed
 *
 * @param options Options containing the audio stream and parameters
 *
 * Example:
 * ```kotlin
 * val options = StreamSuccessAudioOptions(
 *     stream = audioMediaStream,
 *     parameters = streamSuccessAudioParams
 * )
 *
 * streamSuccessAudio(options)
 * ```
 *
 * ### Note:
 * This is a simplified stub implementation. Full implementation requires:
 * - Platform-specific MediaStream handling
 * - WebRTC audio track management
 * - Device enumeration and selection
 * - Audio producer creation and management
 */
suspend fun streamSuccessAudio(options: StreamSuccessAudioOptions) {
    val stream = options.stream
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        val socket = parameters.socket
        val participants = parameters.participants
        var transportCreated = parameters.transportCreated
        var transportCreatedAudio = parameters.transportCreatedAudio
        var audioAlreadyOn = parameters.audioAlreadyOn
        val micAction = parameters.micAction
        val audioParams = parameters.audioParams
        val localStreamAudio = parameters.localStreamAudio
        val defAudioID = parameters.defAudioID
        val userDefaultAudioInputDevice = parameters.userDefaultAudioInputDevice
        val hostLabel = parameters.hostLabel
        val islevel = parameters.islevel
        val member = parameters.member
        val updateMainWindow = parameters.updateMainWindow
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared
        val videoAlreadyOn = parameters.videoAlreadyOn

        // Update functions
        val updateParticipants = parameters.updateParticipants
        val updateTransportCreated = parameters.updateTransportCreated
        val updateTransportCreatedAudio = parameters.updateTransportCreatedAudio
        val updateAudioAlreadyOn = parameters.updateAudioAlreadyOn
        val updateMicAction = parameters.updateMicAction
        val updateLocalStream = parameters.updateLocalStream
        val updateLocalStreamAudio = parameters.updateLocalStreamAudio
        val updateDefAudioID = parameters.updateDefAudioID
        val updateUserDefaultAudioInputDevice = parameters.updateUserDefaultAudioInputDevice
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow

        // Mediasfu functions
        val createSendTransport = parameters.createSendTransport
        val connectSendTransportAudio = parameters.connectSendTransportAudio
        val resumeSendTransportAudio = parameters.resumeSendTransportAudio
        val prepopulateUserMedia = parameters.prepopulateUserMedia

        // Update local audio stream
        updateLocalStreamAudio(stream)
        updateLocalStream(stream)

        // TODO: Platform-specific implementation needed
        // In a full implementation, this would:
        // 1. Extract audio track from stream
        // 2. Update default audio device ID
        // 3. Create/connect audio transport if needed
        // 4. Update participant mute states
        // 5. Manage UI updates based on user level
        //
        // For now, this is a placeholder that manages basic state

        // Update audio state
        if (!audioAlreadyOn) {
            updateAudioAlreadyOn(true)
        }

        // Create transport if needed
        if (!transportCreated) {
            try {
                val optionsCreate = CreateSendTransportOptions(
                    option = "audio",
                    parameters = parameters as CreateSendTransportParameters
                )
                createSendTransport(optionsCreate)
                transportCreated = true
                updateTransportCreated(true)
            } catch (error: Exception) {
                Logger.e("StreamSuccessAudio", "MediaSFU - Error creating send transport: ${error.message}")
            }
        } else {
            // Connect or resume audio transport
            try {
                if (!transportCreatedAudio) {
                    val optionsConnect = ConnectSendTransportAudioOptions(
                        stream = stream,
                        parameters = parameters as ConnectSendTransportAudioParameters,
                        audioConstraints = options.audioConstraints
                    )
                    connectSendTransportAudio(optionsConnect)
                    transportCreatedAudio = true
                    updateTransportCreatedAudio(true)
                } else {
                    val optionsResume = ResumeSendTransportAudioOptions(
                        parameters = parameters as ResumeSendTransportAudioParameters
                    )
                    resumeSendTransportAudio(optionsResume)
                }
            } catch (error: Exception) {
                Logger.e("StreamSuccessAudio", "MediaSFU - Error connecting audio transport: ${error.message}")
            }
        }

        // Update participant mute state
        val updatedParticipants = participants.map { participant ->
            if (participant.name == member) {
                participant.copy(muted = false)
            } else {
                participant
            }
        }
        updateParticipants(updatedParticipants)

        // Handle UI updates for host
        if (!videoAlreadyOn && islevel == "2") {
            if (!lockScreen && !shared) {
                updateUpdateMainWindow(true)
                val optionsPrepopulate = PrepopulateUserMediaOptions(
                    name = hostLabel,
                    parameters = parameters as PrepopulateUserMediaParameters
                )
                prepopulateUserMedia(optionsPrepopulate)
                updateUpdateMainWindow(false)
            }
        }

    } catch (error: Exception) {
        Logger.e("StreamSuccessAudio", "MediaSFU - streamSuccessAudio error: ${error.message}")
        parameters.showAlert.call(
            message = "Error setting up audio stream: ${error.message}",
            type = "danger",
            duration = 3000
        )
    }
}

