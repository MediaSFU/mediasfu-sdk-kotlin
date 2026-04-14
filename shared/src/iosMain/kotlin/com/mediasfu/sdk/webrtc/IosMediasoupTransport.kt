@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mediasfu.sdk.webrtc

import cocoapods.WebRTC.RTCMediaStreamTrack
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUUID

internal class IosMediasoupTransport private constructor(
    private val handle: IosNativeTransportHandle,
    override val type: TransportType
) : WebRtcTransport {

    override val id: String
        get() = handle.id

    override val connectionState: TransportConnectionState
        get() = handle.connectionState().toTransportConnectionState()

    override fun close() {
        handle.close()
        handle.setOnConnect(null)
        handle.setOnConnectionStateChange(null)
        (handle as? IosNativeSendTransportHandle)?.setOnProduce(null)
    }

    override fun onConnect(handler: (ConnectData) -> Unit) {
        handle.setOnConnect(
            IosNativeConnectListener { dtlsParametersJson, callback, errback ->
                handler(
                    ConnectData(
                        dtlsParameters = parseDtlsParametersBridgeJson(dtlsParametersJson),
                        callback = callback,
                        errback = errback
                    )
                )
            }
        )
    }

    override fun onProduce(handler: (ProduceData) -> Unit) {
        val sendHandle = handle as? IosNativeSendTransportHandle ?: return
        sendHandle.setOnProduce(
            IosNativeProduceListener { kind, rtpParametersJson, appDataJson, callback, errback ->
                handler(
                    ProduceData(
                        kind = kind.toMediaKind(),
                        rtpParameters = parseRtpParametersBridgeJson(rtpParametersJson),
                        appData = parseAppDataBridgeJson(appDataJson),
                        callback = callback,
                        errback = errback
                    )
                )
            }
        )
    }

    override fun onConnectionStateChange(handler: (String) -> Unit) {
        handle.setOnConnectionStateChange(handler)
    }

    override fun produce(
        track: MediaStreamTrack,
        encodings: List<RtpEncodingParameters>,
        codecOptions: com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions?,
        appData: Map<String, Any?>?
    ): WebRtcProducer {
        val sendHandle = handle as? IosNativeSendTransportHandle
            ?: throw IllegalStateException("produce called on non-send iOS transport")
        val nativeTrack = track.asPlatformNativeTrack() as? RTCMediaStreamTrack
            ?: throw IllegalArgumentException("Unsupported iOS track implementation: ${track::class.simpleName}")

        val producerHandle = sendHandle.produce(
            track = nativeTrack,
            encodingsJson = encodings.takeIf { it.isNotEmpty() }?.map { it.toMap() }.toIosBridgeJsonString(),
            appDataJson = appData.toIosBridgeJsonString()
        )
        return IosWebRtcProducer(producerHandle, appData)
    }

    override fun consume(
        id: String,
        producerId: String,
        kind: String,
        rtpParameters: Map<String, Any?>
    ): WebRtcConsumer {
        val recvHandle = handle as? IosNativeRecvTransportHandle
            ?: throw IllegalStateException("consume called on non-receive iOS transport")
        val consumerHandle = recvHandle.consume(
            id = id,
            producerId = producerId,
            kind = kind,
            rtpParametersJson = rtpParameters.toIosBridgeJsonString()
                ?: throw IllegalArgumentException("Failed to serialize RTP parameters for iOS consume")
        )
        return IosWebRtcConsumer(consumerHandle)
    }

    companion object {
        fun createSend(handle: IosNativeSendTransportHandle): IosMediasoupTransport =
            IosMediasoupTransport(handle = handle, type = TransportType.SEND)

        fun createRecv(handle: IosNativeRecvTransportHandle): IosMediasoupTransport =
            IosMediasoupTransport(handle = handle, type = TransportType.RECEIVE)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class IosWebRtcProducer(
    private val handle: IosNativeProducerHandle,
    private val appData: Map<String, Any?>?
) : WebRtcProducer {

    override val id: String
        get() = handle.id

    override val kind: MediaKind
        get() = handle.kind.toMediaKind()

    override val source: ProducerSource
        get() = appData.toProducerSource(kind)

    override val paused: Boolean
        get() = handle.isPaused()

    override fun close() = handle.close()

    override fun pause() = handle.pause()

    override fun resume() = handle.resume()

    override fun replaceTrack(track: MediaStreamTrack) {
        val nativeTrack = track.asPlatformNativeTrack() as? RTCMediaStreamTrack
            ?: throw IllegalArgumentException("Unsupported iOS track implementation: ${track::class.simpleName}")
        handle.replaceTrack(nativeTrack)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal class IosWebRtcConsumer(
    private val handle: IosNativeConsumerHandle
) : WebRtcConsumer {

    override val id: String
        get() = handle.id

    override val kind: MediaKind
        get() = handle.kind.toMediaKind()

    override val track: MediaStreamTrack?
        get() = handle.track?.let(::BridgeIosMediaStreamTrack)

    override val stream: MediaStream?
        get() = handle.track?.let { nativeTrack ->
            BridgeIosSingleTrackMediaStream(nativeTrack)
        }

    override val paused: Boolean
        get() = handle.isPaused()

    override fun close() = handle.close()

    override fun pause() = handle.pause()

    override fun resume() = handle.resume()
}

@OptIn(ExperimentalForeignApi::class)
private class BridgeIosSingleTrackMediaStream(
    nativeTrack: RTCMediaStreamTrack
) : MediaStream {
    private val wrappedTrack = BridgeIosMediaStreamTrack(nativeTrack)

    override val id: String = "ios_consumer_stream_${NSUUID().UUIDString}"

    override val active: Boolean
        get() = wrappedTrack.enabled

    override fun getTracks(): List<MediaStreamTrack> = listOf(wrappedTrack)

    override fun getAudioTracks(): List<MediaStreamTrack> =
        if (wrappedTrack.kind == "audio") listOf(wrappedTrack) else emptyList()

    override fun getVideoTracks(): List<MediaStreamTrack> =
        if (wrappedTrack.kind == "video") listOf(wrappedTrack) else emptyList()

    override fun addTrack(track: MediaStreamTrack) = Unit

    override fun removeTrack(track: MediaStreamTrack) = Unit

    override fun stop() {
        wrappedTrack.stop()
    }
}

@OptIn(ExperimentalForeignApi::class)
private class BridgeIosMediaStreamTrack(
    private val nativeTrack: RTCMediaStreamTrack
) : MediaStreamTrack {
    override val id: String
        get() = nativeTrack.trackId

    override val kind: String
        get() = nativeTrack.kind

    override val enabled: Boolean
        get() = nativeTrack.isEnabled

    override fun setEnabled(enabled: Boolean) {
        nativeTrack.isEnabled = enabled
    }

    override fun stop() {
        nativeTrack.isEnabled = false
    }

    override fun asPlatformNativeTrack(): Any = nativeTrack
}

private fun String.toMediaKind(): MediaKind = when (lowercase()) {
    "video" -> MediaKind.VIDEO
    else -> MediaKind.AUDIO
}

private fun String.toTransportConnectionState(): TransportConnectionState = when (lowercase()) {
    "new" -> TransportConnectionState.NEW
    "connecting", "checking" -> TransportConnectionState.CONNECTING
    "connected", "completed" -> TransportConnectionState.CONNECTED
    "disconnected" -> TransportConnectionState.DISCONNECTED
    "failed" -> TransportConnectionState.FAILED
    "closed", "close" -> TransportConnectionState.CLOSED
    else -> TransportConnectionState.NEW
}

private fun Map<String, Any?>?.toProducerSource(kind: MediaKind): ProducerSource {
    val source = this?.get("source")?.toString()?.lowercase()
    return when (source) {
        "screen", "screenshare" -> ProducerSource.SCREEN
        "microphone", "mic", "audio" -> ProducerSource.MICROPHONE
        "camera", "video" -> ProducerSource.CAMERA
        else -> if (kind == MediaKind.VIDEO) ProducerSource.CAMERA else ProducerSource.MICROPHONE
    }
}