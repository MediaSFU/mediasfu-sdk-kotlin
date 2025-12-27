package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.RtcpFeedback
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProducerCapabilityAlignmentTest {

    @Test
    fun `selectCodec returns negotiated codec for matching kind`() {
        val extendedCodec = OrtcUtils.ExtendedRtpCodec(
            mimeType = "audio/opus",
            kind = MediaKind.AUDIO,
            clockRate = 48000,
            channels = 2,
            localPayloadType = 111,
            remotePayloadType = 109,
            localParameters = mapOf("useinbandfec" to "1"),
            remoteParameters = mapOf("useinbandfec" to "1"),
            rtcpFeedback = listOf(RtcpFeedback(type = "nack"))
        )

        val caps = OrtcUtils.ExtendedRtpCapabilities(
            codecs = listOf(extendedCodec),
            headerExtensions = emptyList()
        )

        val codec = ProducerCapabilityAlignment.selectCodec(MediaKind.AUDIO, caps)

        assertNotNull(codec)
        assertEquals("audio/opus", codec.mimeType)
        assertEquals(111, codec.preferredPayloadType)
        assertEquals(48000, codec.clockRate)
    }

    @Test
    fun `alignProducerOptions injects fallback encodings and negotiated codec`() {
        val baseOptions = ProducerOptionsType(
            encodings = emptyList(),
            codec = null
        )
        val negotiatedCodec = RtpCodecCapability(
            kind = MediaKind.VIDEO,
            mimeType = "video/VP8",
            preferredPayloadType = 96,
            clockRate = 90000
        )

        val result = ProducerCapabilityAlignment.alignProducerOptions(
            baseOptions = baseOptions,
            stream = null,
            track = null,
            negotiatedCodec = negotiatedCodec,
            label = "video",
            fallbackKind = ProducerFallbackKind.CAMERA
        )

        assertTrue(result.encodings.isNotEmpty(), "expected fallback encodings to be injected")
        assertEquals(negotiatedCodec, result.codec)
    }
}
