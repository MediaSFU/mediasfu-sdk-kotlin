@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mediasfu.sdk.webrtc

import cocoapods.WebRTC.RTCMediaStreamTrack
import cocoapods.WebRTC.RTCAudioTrack
import cocoapods.WebRTC.RTCVideoTrack
import com.mediasfu.sdk.util.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSLock
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
                val nativeKind = kind.toMediaKind()
                val hintedKind = consumePendingProduceKindHint(sendHandle.id)
                val effectiveKind = hintedKind ?: nativeKind
                if (hintedKind != null && effectiveKind != nativeKind) {
                    Logger.w(
                        "IosMediasoupTransport",
                        "produce kind override native=$kind effective=${effectiveKind.name.lowercase()} transportId=${sendHandle.id}"
                    )
                }
                val parsedRtpParameters = parseRtpParametersBridgeJson(rtpParametersJson)
                handler(
                    ProduceData(
                        kind = effectiveKind,
                        rtpParameters = parsedRtpParameters,
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
        codec: RtpCodecCapability?,
        appData: Map<String, Any?>?
    ): WebRtcProducer {
        val sendHandle = handle as? IosNativeSendTransportHandle
            ?: throw IllegalStateException("produce called on non-send iOS transport")
        val effectiveEncodings = stabilizeIosVideoEncodings(track.kind, encodings)
        Logger.i(
            "IosMediasoupTransport",
            "produce begin transportId=${sendHandle.id} trackId=${track.id} kind=${track.kind} encodings=${effectiveEncodings.size} originalEncodings=${encodings.size} hasAppData=${appData != null}"
        )
        throwIfBridgeReportedError(
            rawId = sendHandle.id,
            operation = "send transport setup"
        )
        val nativeTrack = track.asPlatformNativeTrack() as? RTCMediaStreamTrack
            ?: throw IllegalArgumentException("Unsupported iOS track implementation: ${track::class.simpleName}")

        val encodingsJson = buildIosProduceEncodingsJson(track.kind, effectiveEncodings)
        val codecOptionsJson: String? = null
        val codecJson: String? = null

        val kindHint = track.kind.toMediaKind()
        enqueuePendingProduceKindHint(sendHandle.id, kindHint)
        val producerHandle = try {
            sendHandle.produce(
                track = nativeTrack,
                encodingsJson = encodingsJson,
                codecOptionsJson = codecOptionsJson,
                codecJson = codecJson,
                appDataJson = appData.toIosBridgeJsonString()
            )
        } catch (error: Throwable) {
            removePendingProduceKindHint(sendHandle.id, kindHint)
            throw error
        }
        throwIfBridgeReportedError(
            rawId = producerHandle.id,
            operation = "produce"
        )
        Logger.i(
            "IosMediasoupTransport",
            "produce success transportId=${sendHandle.id} producerId=${producerHandle.id}"
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
        Logger.i(
            "IosMediasoupTransport",
            "consume begin transportId=${recvHandle.id} consumerId=$id producerId=$producerId kind=$kind"
        )
        throwIfBridgeReportedError(
            rawId = recvHandle.id,
            operation = "receive transport setup"
        )
        val consumerHandle = recvHandle.consume(
            id = id,
            producerId = producerId,
            kind = kind,
            rtpParametersJson = rtpParameters.toIosBridgeJsonString()
                ?: throw IllegalArgumentException("Failed to serialize RTP parameters for iOS consume")
        )
        throwIfBridgeReportedError(
            rawId = consumerHandle.id,
            operation = "consume"
        )
        val nativeTrack = consumerHandle.track
        Logger.i(
            "IosMediasoupTransport",
            "consume success transportId=${recvHandle.id} consumerId=${consumerHandle.id} producerId=$producerId kind=$kind nativeTrackId=${nativeTrack?.trackId ?: "none"} nativeTrackKind=${nativeTrack?.kind ?: "none"} nativeTrackEnabled=${nativeTrack?.isEnabled ?: false}"
        )
        return IosWebRtcConsumer(consumerHandle)
    }

    companion object {
        private val pendingProduceKindHintLock = NSLock()
        private val pendingProduceKindHintsByTransport = mutableMapOf<String, MutableList<MediaKind>>()

        private fun enqueuePendingProduceKindHint(transportId: String, kind: MediaKind) {
            pendingProduceKindHintLock.lock()
            try {
                pendingProduceKindHintsByTransport.getOrPut(transportId) { mutableListOf() }.add(kind)
            } finally {
                pendingProduceKindHintLock.unlock()
            }
        }

        private fun consumePendingProduceKindHint(transportId: String): MediaKind? {
            pendingProduceKindHintLock.lock()
            try {
                val hints = pendingProduceKindHintsByTransport[transportId] ?: return null
                val kind = hints.removeAt(0)
                if (hints.isEmpty()) {
                    pendingProduceKindHintsByTransport.remove(transportId)
                }
                return kind
            } finally {
                pendingProduceKindHintLock.unlock()
            }
        }

        private fun removePendingProduceKindHint(transportId: String, kind: MediaKind) {
            pendingProduceKindHintLock.lock()
            try {
                val hints = pendingProduceKindHintsByTransport[transportId] ?: return
                hints.remove(kind)
                if (hints.isEmpty()) {
                    pendingProduceKindHintsByTransport.remove(transportId)
                }
            } finally {
                pendingProduceKindHintLock.unlock()
            }
        }

        fun createSend(handle: IosNativeSendTransportHandle): IosMediasoupTransport =
            IosMediasoupTransport(handle = handle, type = TransportType.SEND)

        fun createRecv(handle: IosNativeRecvTransportHandle): IosMediasoupTransport =
            IosMediasoupTransport(handle = handle, type = TransportType.RECEIVE)
    }
}

internal fun buildIosProduceEncodingsJson(
    kind: String,
    encodings: List<RtpEncodingParameters>
): String? = encodings
    .takeIf { it.isNotEmpty() }
    ?.map { encoding ->
        val map = encoding.toMap().toMutableMap()
        if (kind.equals("audio", ignoreCase = true)) {
            // Audio producers do not need simulcast/video-specific encoding keys.
            map.remove("rid")
            map.remove("scalabilityMode")
            map.remove("scaleResolutionDownBy")
        }
        map
    }
    .toIosBridgeJsonString()

private fun stabilizeIosVideoEncodings(
    kind: String,
    encodings: List<RtpEncodingParameters>
): List<RtpEncodingParameters> {
    if (!kind.equals("video", ignoreCase = true) || encodings.size <= 1) {
        return encodings
    }

    val fullQualityEncoding = encodings.firstOrNull { it.scaleResolutionDownBy == null }
        ?: encodings.maxByOrNull { it.maxBitrate ?: 0 }
        ?: encodings.last()

    return listOf(
        fullQualityEncoding.copy(
            rid = null,
            scalabilityMode = null,
            scaleResolutionDownBy = null
        )
    )
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

    private var cachedStream: MediaStream? = null

    override val id: String
        get() = handle.id

    override val kind: MediaKind
        get() = handle.kind.toMediaKind()

    override val track: MediaStreamTrack?
        get() {
            val nativeTrack = handle.track
            if (nativeTrack == null) {
                Logger.w("IosMediasoupTransport", "consumer track unavailable consumerId=$id kind=$kind")
            }
            return nativeTrack?.let(::BridgeIosMediaStreamTrack)
        }

    override val stream: MediaStream?
        get() {
            cachedStream?.let { return it }
            val nativeTrack = handle.track ?: return null
            Logger.i(
                "IosMediasoupTransport",
                "consumer stream wrap consumerId=$id nativeTrackId=${nativeTrack.trackId} nativeTrackKind=${nativeTrack.kind} enabled=${nativeTrack.isEnabled}"
            )
            return BridgeIosSingleTrackMediaStream(nativeTrack).also { cachedStream = it }
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
        get() = when (nativeTrack) {
            is RTCAudioTrack -> "audio"
            is RTCVideoTrack -> "video"
            else -> nativeTrack.kind
        }

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

private fun throwIfBridgeReportedError(rawId: String, operation: String) {
    val errorPrefixes = listOf("send-error", "recv-error", "producer-error", "consumer-error")
    val matchedPrefix = errorPrefixes.firstOrNull { rawId.startsWith(it) } ?: return
    val compactMessage = rawId
        .removePrefix(matchedPrefix)
        .removePrefix(":")
        .ifBlank { "unknown" }
    val readableMessage = compactMessage
        .replace('-', ' ')
        .replace('_', ' ')
        .trim()
    throw IllegalStateException("iOS bridge $operation failed: $readableMessage")
}
