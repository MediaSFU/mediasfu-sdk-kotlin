package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport

/**
 * Parameters for stream success screen handling.
 */
interface StreamSuccessScreenParameters :
    CreateSendTransportParameters,
    ConnectSendTransportScreenParameters,
    DisconnectSendTransportScreenParameters,
    StopShareScreenParameters {

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?

    val transportCreatedScreen: Boolean
    val screenAlreadyOn: Boolean
    val screenAction: Boolean
    val sParams: ProducerOptionsType?
    val updateTransportCreatedScreen: (Boolean) -> Unit
    val updateScreenAlreadyOn: (Boolean) -> Unit
    val updateScreenAction: (Boolean) -> Unit
    val updateScreenParams: (ProducerOptionsType?) -> Unit

    override fun getUpdatedAllParams(): StreamSuccessScreenParameters
}

/**
 * Options for stream success screen handling.
 *
 * @property stream The screen media stream to process
 * @property parameters The parameter bundle providing state and callbacks
 */
data class StreamSuccessScreenOptions(
    val stream: MediaStream?,
    val parameters: StreamSuccessScreenParameters
)

suspend fun streamSuccessScreen(options: StreamSuccessScreenOptions) {
    val stream = options.stream
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        val socket = parameters.socket
        val participants = parameters.participants
        var transportCreated = parameters.transportCreated
        var transportCreatedScreen = parameters.transportCreatedScreen
        var screenAlreadyOn = parameters.screenAlreadyOn
        var shareScreenStarted = parameters.shareScreenStarted
        val screenAction = parameters.screenAction
        val screenParams = parameters.screenParams
        val localStreamScreen = parameters.localStreamScreen
        val defScreenID = parameters.defScreenID
        val hostLabel = parameters.hostLabel
        val islevel = parameters.islevel
        val member = parameters.member
        val updateMainWindow = parameters.updateMainWindow
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared

        // Update functions
    val updateParticipants = parameters::updateParticipants
        val updateTransportCreated = parameters.updateTransportCreated
        val updateTransportCreatedScreen = parameters.updateTransportCreatedScreen
        val updateScreenAlreadyOn = parameters.updateScreenAlreadyOn
        val updateScreenAction = parameters.updateScreenAction
        val updateLocalStream = parameters.updateLocalStream
        val updateLocalStreamScreen = parameters.updateLocalStreamScreen
        val updateDefScreenID = parameters.updateDefScreenID
        val updateShareScreenStarted = parameters.updateShareScreenStarted
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow

        // Mediasfu functions
        val createSendTransport = parameters.createSendTransport
        val connectSendTransportScreen = parameters.connectSendTransportScreen
        val prepopulateUserMedia = parameters.prepopulateUserMedia

        // Update local screen stream
        updateLocalStreamScreen(stream)
        updateLocalStream(stream)

        // TODO: Platform-specific implementation needed
        // In a full implementation, this would:
        // 1. Extract screen track from stream
        // 2. Update screen sharing configuration
        // 3. Create/connect screen transport if needed
        // 4. Update participant screen sharing states
        // 5. Manage UI updates for screen sharing display
        //
        // For now, this is a placeholder that manages basic state

        // Update screen sharing state
        if (!screenAlreadyOn) {
            updateScreenAlreadyOn(true)
        }

        if (!shareScreenStarted) {
            updateShareScreenStarted(true)
        }

        // Create transport if needed
        if (!transportCreated) {
            try {
                val optionsCreate = CreateSendTransportOptions(
                    option = "screen",
                    parameters = parameters as CreateSendTransportParameters
                )
                createSendTransport(optionsCreate)
                transportCreated = true
                updateTransportCreated(true)
            } catch (error: Exception) {
                Logger.e("StreamSuccessScreen", "MediaSFU - Error creating send transport: ${error.message}")
            }
        } else {
            // Connect screen transport
            try {
                if (!transportCreatedScreen) {
                    val optionsConnect = ConnectSendTransportScreenOptions(
                        targetOption = "all",
                        stream = stream,
                        parameters = parameters as ConnectSendTransportScreenParameters
                    )
                    connectSendTransportScreen(optionsConnect)
                    transportCreatedScreen = true
                    updateTransportCreatedScreen(true)
                }
            } catch (error: Exception) {
                Logger.e("StreamSuccessScreen", "MediaSFU - Error connecting screen transport: ${error.message}")
            }
        }

        // Update participant screen sharing state
        val updatedParticipants = participants.map { participant ->
            if (participant.name == member) {
                participant.copy(ScreenOn = true)
            } else {
                participant
            }
        }
        updateParticipants(updatedParticipants)

        // Handle UI updates
        if (!lockScreen && !shared) {
            updateUpdateMainWindow(true)
            val optionsPrepopulate = PrepopulateUserMediaOptions(
                name = hostLabel,
                parameters = parameters as PrepopulateUserMediaParameters
            )
            prepopulateUserMedia(optionsPrepopulate)
            updateUpdateMainWindow(false)
        }

    } catch (error: Exception) {
        Logger.e("StreamSuccessScreen", "MediaSFU - streamSuccessScreen error: ${error.message}")
        parameters.showAlert?.invoke(
            "Error setting up screen sharing: ${error.message}",
            "danger",
            3000
        )
    }
}

