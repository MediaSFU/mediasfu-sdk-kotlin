package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.background.IOSVirtualBackgroundProcessor
import com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory
import kotlinx.coroutines.test.runTest
import platform.ReplayKit.RPScreenRecorder
import platform.UIKit.UIDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPlatformParityTest {

    @Test
    fun screenCaptureHelper_buildConstraints_usesIosFriendlyDefaults() {
        val constraints = ScreenCaptureHelper.buildConstraints()

        val video = constraints["video"] as? Map<*, *>
        assertNotNull(video)
        assertEquals(mapOf("ideal" to 1080), video["width"])
        assertEquals(mapOf("ideal" to 1920), video["height"])
        assertEquals(mapOf("ideal" to 15, "max" to 30), video["frameRate"])
        assertEquals(false, constraints["audio"])
    }

    @Test
    fun screenCaptureHelper_buildConstraints_matchesExpectedShape() {
        val constraints = ScreenCaptureHelper.buildConstraints(
            width = 1280,
            height = 720,
            frameRate = 24,
            maxFrameRate = 30,
            audio = true
        )

        val video = constraints["video"] as? Map<*, *>
        assertNotNull(video)
        assertEquals(mapOf("ideal" to 1280), video["width"])
        assertEquals(mapOf("ideal" to 720), video["height"])
        assertEquals(mapOf("ideal" to 24, "max" to 30), video["frameRate"])
        assertEquals(true, constraints["audio"])
        assertEquals(false, constraints.containsKey("mediaProjection"))
    }

    @Test
    fun screenCaptureHelper_isSupported_matchesReplayKitAvailability() {
        assertEquals(
            RPScreenRecorder.sharedRecorder().isAvailable(),
            ScreenCaptureHelper.isSupported()
        )
    }

    @Test
    fun virtualBackgroundFactory_support_matchesRuntimeVersion() {
        val expectedSupport = UIDevice.currentDevice.systemVersion
            .substringBefore('.')
            .toIntOrNull()
            ?.let { it >= 15 } == true

        assertEquals(expectedSupport, VirtualBackgroundProcessorFactory.isSupported())
    }

    @Test
    fun virtualBackgroundFactory_create_followsSupportGate() {
        val creationResult = VirtualBackgroundProcessorFactory.createResult(null)
        val processor = VirtualBackgroundProcessorFactory.create(null)

        if (VirtualBackgroundProcessorFactory.isSupported()) {
            assertNotNull(processor)
            assertTrue(creationResult is com.mediasfu.sdk.background.VirtualBackgroundProcessorCreationResult.Success)
            assertTrue(processor is IOSVirtualBackgroundProcessor)
        } else {
            assertTrue(creationResult is com.mediasfu.sdk.background.VirtualBackgroundProcessorCreationResult.Failure)
            assertNull(processor)
        }
    }

    @Test
    fun enumerateDevices_includesExpectedAudioRoutes() = runTest {
        val devices = IOSWebRtcDevice.getInstance().enumerateDevices()

        assertTrue(devices.any { it.kind == "audioinput" && it.deviceId == "default_audio_input" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_speaker" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_bluetooth" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_wired" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_usb" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_airplay" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_hdmi" })
        assertTrue(devices.any { it.kind == "audiooutput" && it.deviceId == "audio_output_car" })
    }

    @Test
    fun enumerateDevices_exposesAtLeastOneVideoInputDescriptor() = runTest {
        val devices = IOSWebRtcDevice.getInstance().enumerateDevices()
        val videoInputs = devices.filter { it.kind == "videoinput" }

        assertTrue(videoInputs.isNotEmpty())
        assertTrue(videoInputs.all { !it.deviceId.isNullOrBlank() })
        assertTrue(videoInputs.all { !it.groupId.isNullOrBlank() })
        assertTrue(videoInputs.all { !it.label.isNullOrBlank() })
        assertNotEquals(0, videoInputs.size)
    }
}
