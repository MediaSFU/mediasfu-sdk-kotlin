package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.EngineCloseAndResizeParameters
import com.mediasfu.sdk.createConsumerResumeParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.NewPipeProducerParameters as SocketNewPipeProducerParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedParameters as SocketProducerClosedParameters
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.WebRtcDevice

internal fun Any?.resolveNewPipeProducerParameters(): SocketNewPipeProducerParameters? = when (this) {
    is SocketNewPipeProducerParameters -> this
    is MediasfuParameters -> MediasfuNewPipeProducerParameters(this)
    else -> null
}

internal fun Any?.resolveProducerClosedParameters(): SocketProducerClosedParameters? = when (this) {
    is SocketProducerClosedParameters -> this
    is MediasfuParameters -> MediasfuProducerClosedParameters(this)
    else -> null
}

internal fun Any?.resolveReceiveAllPipedTransportsParameters(): ReceiveAllPipedTransportsParameters? = when (this) {
    is ReceiveAllPipedTransportsParameters -> this
    is MediasfuParameters -> MediasfuReceiveAllPipedParameters(this)
    else -> null
}

private open class MediasfuSignalParameters(
    protected val backing: MediasfuParameters
) : SignalNewConsumerTransportParameters {
    override val consumingTransports: List<String>
        get() = backing.consumingTransports

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val device: WebRtcDevice?
        get() = backing.device

    override val rtpCapabilities: com.mediasfu.sdk.webrtc.RtpCapabilities?
        get() = backing.rtpCapabilities

    override val routerRtpCapabilities: RtpCapabilities?
        get() = backing.routerRtpCapabilities

    override val negotiatedRecvRtpCapabilities: RtpCapabilities?
        get() = backing.negotiatedRecvRtpCapabilities

    override fun updateConsumingTransports(transports: List<String>) {
        backing.consumingTransports = transports
    }

    override val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit
        get() = { infos ->
            backing.consumerTransportInfos = infos
            backing.consumerTransportsWebRtc = infos.mapNotNull { it.consumerTransport }
        }

    override val consumerResume: suspend (ConsumerResumeOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.consumerResume(options) }

    override val consumerResumeParamsProvider: () -> ConsumerResumeParameters
        get() = { createConsumerResumeParameters(backing) }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = backing.reorderStreams

    override fun getUpdatedAllParams(): SignalNewConsumerTransportParameters =
        MediasfuSignalParameters(backing)
}

private class MediasfuNewPipeProducerParameters(
    backing: MediasfuParameters
) : MediasfuSignalParameters(backing), SocketNewPipeProducerParameters {

    override val firstRound: Boolean
        get() = backing.firstRound

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val shared: Boolean
        get() = backing.shared

    override val landScaped: Boolean
        get() = backing.landScaped

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler ?: backing.showAlert

    override val updateFirstRound: (Boolean) -> Unit
        get() = { value -> backing.firstRound = value }

    override val updateLandScaped: (Boolean) -> Unit
        get() = { value -> backing.landScaped = value }

    override val updateConsumingTransports: (List<String>) -> Unit
        get() = { transports -> updateConsumingTransports(transports) }

    override val connectRecvTransport: suspend (Any) -> Unit
        get() = { /* No-op: actual connection handled downstream */ }

    override val reorderStreams: suspend (Any) -> Unit
        get() = { /* No-op: actual reordering handled downstream */ }

    override val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = this)
            com.mediasfu.sdk.consumers.signalNewConsumerTransport(resolved)
        }

    override fun updateConsumingTransports(transports: List<String>) {
        super.updateConsumingTransports(transports)
    }

    override fun getUpdatedAllParams(): SocketNewPipeProducerParameters =
        MediasfuNewPipeProducerParameters(backing)
}

private class MediasfuProducerClosedParameters(
    private val backing: MediasfuParameters
) : SocketProducerClosedParameters {

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val screenId: String
        get() = backing.screenId

    override val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit
        get() = { infos ->
            backing.consumerTransportInfos = infos
            backing.consumerTransportsWebRtc = infos.mapNotNull { it.consumerTransport }
        }

    override val closeAndResize: suspend (String, String) -> Unit
        get() = { producerId, kind ->
            val options = CloseAndResizeOptions(
                producerId = producerId,
                kind = kind,
                parameters = EngineCloseAndResizeParameters(backing)
            )
            closeAndResize(options)
        }

    override fun getUpdatedAllParams(): SocketProducerClosedParameters =
        MediasfuProducerClosedParameters(backing)
}

private open class MediasfuGetPipedParameters(
    backing: MediasfuParameters
) : MediasfuSignalParameters(backing), GetPipedProducersAltParameters {

    override val member: String
        get() = backing.member

    override val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = this)
            com.mediasfu.sdk.consumers.signalNewConsumerTransport(resolved)
        }

    override fun getUpdatedAllParams(): GetPipedProducersAltParameters =
        MediasfuGetPipedParameters(backing)
}

private class MediasfuReceiveAllPipedParameters(
    backing: MediasfuParameters
) : MediasfuGetPipedParameters(backing), ReceiveAllPipedTransportsParameters {

    override val roomName: String
        get() = backing.roomName

    override val getPipedProducersAlt: suspend (GetPipedProducersAltOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = this)
            com.mediasfu.sdk.consumers.getPipedProducersAlt(resolved)
        }

    override fun getUpdatedAllParams(): ReceiveAllPipedTransportsParameters =
        MediasfuReceiveAllPipedParameters(backing)
}
