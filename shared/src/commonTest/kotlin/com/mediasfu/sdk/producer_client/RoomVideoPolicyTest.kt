package com.mediasfu.sdk.producer_client

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RoomVideoPolicyTest {
    private fun preset() = ProducerOptionsType(
        encodings = listOf(
            RtpEncodingParameters(rid = "low", maxBitrate = 200_000, scaleResolutionDownBy = 4.0),
            RtpEncodingParameters(rid = "mid", maxBitrate = 400_000, scaleResolutionDownBy = 2.0),
            RtpEncodingParameters(rid = "high", maxBitrate = 800_000, minBitrate = 160_000,
                maxFramerate = 24.0, active = false, scalabilityMode = "L3T3"),
        ),
        codecOptions = ProducerCodecOptions(videoGoogleStartBitrate = 320),
    )

    @Test fun roomSizeAndExplicitPolicy() {
        assertTrue(cameraSimulcastOff("chat", null, false))
        assertTrue(cameraSimulcastOff("conference", 2, false))
        assertTrue(cameraSimulcastOff("conference", 6, true))
        assertFalse(cameraSimulcastOff("conference", 6, false))
        assertFalse(cameraSimulcastOff("conference", null, false))
    }

    @Test fun fullResolutionEncodingRetainsLimitsAndPreset() {
        val original = preset()
        val scaled = updateEncodingBitrates(original, "hd")
        val single = applyCameraEncodingPolicy(scaled, true)
        val full = single.encodings.single()
        assertEquals(3_200_000, full.maxBitrate)
        assertEquals(640_000, full.minBitrate)
        assertEquals(24.0, full.maxFramerate)
        assertFalse(full.active)
        assertNull(full.rid)
        assertEquals(1.0, full.scaleResolutionDownBy)
        assertEquals("L1T3", full.scalabilityMode)
        assertSame(original.codecOptions, single.codecOptions)
        assertEquals(3, original.encodings.size)
        assertEquals(800_000, original.encodings.last().maxBitrate)
        assertEquals(3, applyCameraEncodingPolicy(scaled, false).encodings.size)
    }

    @Test fun customPresetDoesNotNeedFullResolutionLayerLast() {
        val original = preset()
        val custom = original.copy(encodings = original.encodings.reversed())
        val single = applyCameraEncodingPolicy(custom, true)
        assertEquals(800_000, single.encodings.single().maxBitrate)
        assertEquals(3, custom.encodings.size)
    }

    @Test fun resolutionScalingPreservesOtherProducerOptions() {
        val original = preset()
        original["customSetting"] = "preserved"
        for ((resolution, factor) in listOf("sd" to 1.0, "hd" to 4.0, "fhd" to 8.0, "qhd" to 16.0, "QnHD" to 0.25)) {
            val scaled = updateEncodingBitrates(original, resolution)
            assertEquals((800_000 * factor).toInt(), scaled.encodings.last().maxBitrate)
            assertSame(original.codecOptions, scaled.codecOptions)
            assertFalse(scaled.encodings.last().active)
            assertEquals("preserved", scaled["customSetting"])
        }
    }
}
