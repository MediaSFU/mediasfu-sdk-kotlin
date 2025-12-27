package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.methods.utils.producer.AParams
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.utils.producer.ScreenParams
import com.mediasfu.sdk.methods.utils.producer.VParams
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils

/**
 * Normalises [ProducerOptionsType] instances against the negotiated ORTC result so Android
 * senders mirror the behaviour of the Flutter/React clients (which lean on mediasoup-client).
 */
internal object ProducerCapabilityAlignment {
    /**
     * Selects the negotiated codec for the provided [kind] (audio/video) if one exists.
     */
    fun selectCodec(
        kind: MediaKind,
        extendedCaps: OrtcUtils.ExtendedRtpCapabilities?
    ): RtpCodecCapability? {
        val negotiated = extendedCaps?.codecs?.firstOrNull { codec ->
            codec.kind == kind && codec.localPayloadType != null
        } ?: return null

        val parameters = negotiated.localParameters.mapValues { it.value }
        return RtpCodecCapability(
            kind = negotiated.kind,
            mimeType = negotiated.mimeType,
            preferredPayloadType = negotiated.localPayloadType,
            clockRate = negotiated.clockRate,
            channels = negotiated.channels,
            parameters = parameters,
            rtcpFeedback = negotiated.rtcpFeedback
        )
    }

    /**
     * Applies negotiated codec + default encodings to whichever producer options the UI supplied.
     */
    fun alignProducerOptions(
        baseOptions: ProducerOptionsType?,
        stream: MediaStream?,
        track: MediaStreamTrack?,
        negotiatedCodec: RtpCodecCapability?,
        label: String,
        fallbackKind: ProducerFallbackKind
    ): ProducerOptionsType {
        val fallback = fallbackKind.defaultOptions()
        val source = baseOptions ?: fallback

        val encodings = source.encodings.takeIf { it.isNotEmpty() } ?: fallback.encodings
        val codecOptions = source.codecOptions ?: fallback.codecOptions
        val resolvedStream = stream ?: source.stream ?: fallback.stream
        val resolvedTrack = track ?: source.track ?: fallback.track
        val codec = negotiatedCodec ?: source.codec ?: fallback.codec

        if (codec != null) {
        } else {
        }

        return ProducerOptionsType(
            encodings = encodings,
            codecOptions = codecOptions,
            track = resolvedTrack,
            stream = resolvedStream,
            codec = codec
        )
    }
}

internal enum class ProducerFallbackKind {
    AUDIO,
    CAMERA,
    SCREEN;

    fun defaultOptions(): ProducerOptionsType {
        return when (this) {
            AUDIO -> AParams.getAudioParams()
            CAMERA -> VParams.getVideoParams()
            SCREEN -> ScreenParams.getScreenParams()
        }
    }
}
