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
    val wasScreenAlreadyOn = parameters.screenAlreadyOn

    try {
        val socket = parameters.socket
        val participants = parameters.participants
        var transportCreated = parameters.transportCreated
        var transportCreatedScreen = parameters.transportCreatedScreen
        var screenAlreadyOn = parameters.screenAlreadyOn
        var screenAction = parameters.screenAction
        val hostLabel = parameters.hostLabel
        val member = parameters.member
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared
        val eventType = parameters.eventType
        var annotateScreenStream = parameters.annotateScreenStream

        // Update functions
        val updateParticipants = parameters::updateParticipants
        val updateTransportCreated = parameters.updateTransportCreated
        val updateTransportCreatedScreen = parameters.updateTransportCreatedScreen
        val updateScreenAlreadyOn = parameters.updateScreenAlreadyOn
        val updateScreenAction = parameters.updateScreenAction
        val updateLocalStream = parameters.updateLocalStream
        val updateLocalStreamScreen = parameters.updateLocalStreamScreen
        val updateShareScreenStarted = parameters.updateShareScreenStarted
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow
        val updateShared = parameters.updateShared
        val updateIsScreenboardModalVisible = parameters.updateIsScreenboardModalVisible

        // Mediasfu functions
        val createSendTransport = parameters.createSendTransport
        val connectSendTransportScreen = parameters.connectSendTransportScreen
        val prepopulateUserMedia = parameters.prepopulateUserMedia
        val reorderStreams = parameters.reorderStreams

        // Update local screen stream
        updateLocalStreamScreen(stream)
        updateLocalStream(stream)

        try {
            if (!transportCreated) {
                val optionsCreate = CreateSendTransportOptions(
                    option = "screen",
                    parameters = parameters as CreateSendTransportParameters
                )
                createSendTransport(optionsCreate)
            } else {
                val optionsConnect = ConnectSendTransportScreenOptions(
                    targetOption = "all",
                    stream = stream,
                    parameters = parameters as ConnectSendTransportScreenParameters
                )
                connectSendTransportScreen(optionsConnect)
            }

            runCatching { socket?.emit("startScreenShare", emptyMap()) }
                .onFailure { error ->
                    Logger.e("StreamSuccessScreen", "MediaSFU - Error emitting startScreenShare: ${error.message}")
                }
        } catch (error: Exception) {
            Logger.e("StreamSuccessScreen", "MediaSFU - Error sharing screen: ${error.message}")
            throw error
        }

        val refreshed = parameters.getUpdatedAllParams()
        val screenTransportReady = refreshed.transportCreated
        val screenProduceReady = refreshed.screenProducer != null
        if (!screenTransportReady || !screenProduceReady) {
            throw IllegalStateException("Screen transport did not become ready")
        }

        if (!screenAlreadyOn) {
            updateScreenAlreadyOn(true)
            screenAlreadyOn = true
        }

        if (!parameters.shareScreenStarted) {
            updateShareScreenStarted(true)
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

        try {
            updateShared(true)
            val optionsPrepopulate = PrepopulateUserMediaOptions(
                name = hostLabel,
                parameters = parameters as PrepopulateUserMediaParameters
            )
            prepopulateUserMedia(optionsPrepopulate)
        } catch (_: Exception) {
        }

        try {
            val reorderOptions = if (eventType == EventType.CONFERENCE) {
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = parameters
                )
            } else {
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = false,
                    parameters = parameters
                )
            }
            reorderStreams(reorderOptions)

            if (eventType == EventType.CONFERENCE) {
                val optionsPrepopulate = PrepopulateUserMediaOptions(
                    name = hostLabel,
                    parameters = parameters as PrepopulateUserMediaParameters
                )
                prepopulateUserMedia(optionsPrepopulate)
            }
        } catch (error: Exception) {
            val rePortParameters = parameters as? RePortParameters
            if (rePortParameters != null) {
                runCatching {
                    rePort(RePortOptions(parameters = rePortParameters))
                }.onFailure { rePortError ->
                    Logger.e("StreamSuccessScreen", "MediaSFU - Error in rePort fallback: ${rePortError.message}")
                }
            } else {
                Logger.e("StreamSuccessScreen", "MediaSFU - Error reordering screen streams: ${error.message}")
            }
        }

        if (screenAction) {
            screenAction = false
            updateScreenAction(false)
        }

        transportCreatedScreen = true
        updateTransportCreatedScreen(true)
        if (!transportCreated) {
            transportCreated = true
            updateTransportCreated(true)
        }

        if (annotateScreenStream) {
            annotateScreenStream = false
            updateIsScreenboardModalVisible(true)
            kotlinx.coroutines.delay(1000)
            updateIsScreenboardModalVisible(false)
        }

    } catch (error: Exception) {
        Logger.e("StreamSuccessScreen", "MediaSFU - streamSuccessScreen error: ${error.message}")
        if (!wasScreenAlreadyOn) {
            parameters.updateScreenAlreadyOn(false)
            parameters.updateTransportCreatedScreen(false)
        }
        parameters.showAlert?.invoke(
            "Error setting up screen sharing: ${error.message}",
            "danger",
            3000
        )
    }
}

