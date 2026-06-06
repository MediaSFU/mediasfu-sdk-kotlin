package com.mediasfu.sdk.webrtc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RtpHeaderExtensionPolicyTest {

    @Test
    fun rtpCapabilitiesPolicyStripsVideoOrientationWhenNotPreserved() {
        val caps = sampleRtpCapabilities()

        val filtered = caps.applyHeaderExtensionPolicy(preserveVideoOrientation = false)

        assertEquals(
            listOf("urn:ietf:params:rtp-hdrext:sdes:mid"),
            filtered.headerExtensions.map { it.uri }
        )
    }

    @Test
    fun rtpCapabilitiesPolicyPreservesVideoOrientationWhenEnabled() {
        val caps = sampleRtpCapabilities()

        val filtered = caps.applyHeaderExtensionPolicy(preserveVideoOrientation = true)

        assertSame(caps, filtered)
        assertEquals(
            listOf(
                VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI,
                "urn:ietf:params:rtp-hdrext:sdes:mid"
            ),
            filtered.headerExtensions.map { it.uri }
        )
    }

    @Test
    fun rtpParameterMapPolicyStripsVideoOrientationWhenNotPreserved() {
        val rtpMap = mapOf(
            "headerExtensions" to listOf(
                mapOf("uri" to VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI, "id" to 11),
                mapOf("uri" to "urn:ietf:params:rtp-hdrext:sdes:mid", "id" to 1)
            )
        )

        val filtered = rtpMap.applyHeaderExtensionPolicy(preserveVideoOrientation = false)
        val uris = (filtered["headerExtensions"] as List<*>)
            .mapNotNull { (it as? Map<*, *>)?.get("uri")?.toString() }

        assertEquals(listOf("urn:ietf:params:rtp-hdrext:sdes:mid"), uris)
    }

    @Test
    fun rtpParameterMapPolicyPreservesVideoOrientationWhenEnabled() {
        val rtpMap = mapOf(
            "headerExtensions" to listOf(
                mapOf("uri" to VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI, "id" to 11),
                mapOf("uri" to "urn:ietf:params:rtp-hdrext:sdes:mid", "id" to 1)
            )
        )

        val filtered = rtpMap.applyHeaderExtensionPolicy(preserveVideoOrientation = true)

        assertSame(rtpMap, filtered)
    }

    private fun sampleRtpCapabilities(): RtpCapabilities =
        RtpCapabilities(
            headerExtensions = listOf(
                RtpHeaderExtension(
                    kind = MediaKind.VIDEO,
                    uri = VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI,
                    preferredId = 11
                ),
                RtpHeaderExtension(
                    kind = MediaKind.VIDEO,
                    uri = "urn:ietf:params:rtp-hdrext:sdes:mid",
                    preferredId = 1
                )
            )
        )
}
