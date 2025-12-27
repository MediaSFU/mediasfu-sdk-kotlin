package com.mediasfu.sdk.consumers.socket_receive_methods

import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.consumers.SignalNewConsumerTransportOptions
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager

/**
 * Parameters for the newPipeProducer function.
 */
interface NewPipeProducerParameters : com.mediasfu.sdk.consumers.SignalNewConsumerTransportParameters {
    val firstRound: Boolean
    val shareScreenStarted: Boolean
    val shared: Boolean
    val landScaped: Boolean
    val isWideScreen: Boolean
    override val device: com.mediasfu.sdk.webrtc.WebRtcDevice?
    override val consumingTransports: List<String>
    override val lockScreen: Boolean
    val showAlert: ShowAlert?

    val updateFirstRound: (Boolean) -> Unit
    val updateLandScaped: (Boolean) -> Unit
    val updateConsumingTransports: (List<String>) -> Unit

    val connectRecvTransport: suspend (Any) -> Unit
    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit

    override fun getUpdatedAllParams(): NewPipeProducerParameters
}

/**
 * Options for the newPipeProducer function.
 *
 * @property producerId The ID of the producer to be consumed
 * @property islevel The level status of the participant
 * @property nsock The socket instance for managing real-time communication
 * @property parameters Additional parameters to set up the producer
 */
data class NewPipeProducerOptions(
    val producerId: String,
    val islevel: String,
    val nsock: SocketManager,
    val parameters: NewPipeProducerParameters
)

/**
 * Initiates a new pipe producer by signaling a new consumer transport and updating display settings.
 *
 * This function performs the following steps:
 * 1. Signals the creation of a new consumer transport for the specified `producerId`.
 * 2. Updates the `firstRound` and `landScaped` display parameters based on sharing mode and device orientation.
 * 3. Optionally, triggers an alert to prompt the user to rotate their device for optimal viewing if
 *    screen sharing is active and the device is not in landscape mode.
 *
 * @param options Contains the required data for handling the new pipe producer
 *
 * Example:
 * ```kotlin
 * val parameters = object : NewPipeProducerParameters {
 *     override val firstRound = true
 *     override val shareScreenStarted = true
 *     override val shared = false
 *     override val landScaped = false
 *     override val isWideScreen = false
 *     override val device = null
 *     override val consumingTransports = emptyList()
 *     override val lockScreen = false
 *     override val showAlert = { message, type, duration ->
 *     }
 *     override val updateConsumingTransports = { transports -> }
 *     override val connectRecvTransport = { opts -> }
 *     override val reorderStreams = { opts -> }
 *     override val signalNewConsumerTransport = { opts -> }
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * val options = NewPipeProducerOptions(
 *     producerId = "producer-123",
 *     islevel = "2",
 *     nsock = socketInstance,
 *     parameters = parameters
 * )
 *
 * newPipeProducer(options)
 * ```
 */
suspend fun newPipeProducer(options: NewPipeProducerOptions) {
    val producerId = options.producerId
    val islevel = options.islevel
    val nsock = options.nsock
    val parameters = options.parameters

    var firstRound = parameters.firstRound
    val shareScreenStarted = parameters.shareScreenStarted
    val shared = parameters.shared
    var landScaped = parameters.landScaped
    val isWideScreen = parameters.isWideScreen
    val showAlert = parameters.showAlert

    // Call the signalNewConsumerTransport function
    val optionsSignal = SignalNewConsumerTransportOptions(
        producerId = producerId,
        islevel = islevel,
        socket = nsock,
        parameters = parameters
    )
    parameters.signalNewConsumerTransport(optionsSignal)

    // Modify firstRound and landscape status
    firstRound = false
    if (shareScreenStarted || shared) {
        if (!isWideScreen && !landScaped) {
            showAlert?.invoke(
                "Please rotate your device to landscape mode for better experience",
                "success",
                3000
            )
            landScaped = true
            parameters.updateLandScaped(landScaped)
        }
        firstRound = true
        parameters.updateFirstRound(firstRound)
    }
}

