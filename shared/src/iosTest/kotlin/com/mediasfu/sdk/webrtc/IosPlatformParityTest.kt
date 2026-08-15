package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.background.IOSVirtualBackgroundProcessor
import com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory
import com.mediasfu.sdk.network.mediaSfuJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.ReplayKit.RPScreenRecorder
import platform.UIKit.UIDevice
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosPlatformParityTest {
    @AfterTest
    fun tearDown() {
        IosNativeMediasoupBridgeProvider.reset()
    }

    @Test
    fun rtpHeaderExtensionPolicy_matchesOtherSdkDefault() {
        assertEquals(false, shouldPreserveVideoOrientationHeaderExtension())

        val capabilities = RtpCapabilities(
            headerExtensions = listOf(
                RtpHeaderExtension(
                    kind = MediaKind.VIDEO,
                    uri = VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI,
                    preferredId = 11
                )
            )
        )

        assertEquals(
            emptyList(),
            capabilities.applyCurrentPlatformHeaderExtensionPolicy().headerExtensions.map { it.uri }
        )

        val producerRtpParameters = mapOf(
            "headerExtensions" to listOf(
                mapOf("uri" to VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI, "id" to 11)
            )
        ).applyCurrentPlatformHeaderExtensionPolicy()

        val producerExtensionUris = (producerRtpParameters["headerExtensions"] as List<*>)
            .mapNotNull { (it as? Map<*, *>)?.get("uri")?.toString() }
        assertEquals(emptyList(), producerExtensionUris)
    }

    @Test
    fun iosDeviceLoad_filtersVideoOrientationBeforeNativeBridge() = runTest {
        val bridge = FakeLoadableBridge(
            currentCapabilitiesJson = rtpCapabilitiesJsonWithVideoOrientation()
        )
        IosNativeMediasoupBridgeProvider.install(bridge)

        IOSWebRtcDevice.getInstance().load(rtpCapabilitiesWithVideoOrientation()).getOrThrow()

        val loadedUris = headerExtensionUris(requireNotNull(bridge.loadedCapabilitiesJson))
        assertTrue(VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI !in loadedUris)

        val currentUris = IOSWebRtcDevice.getInstance()
            .currentRtpCapabilities()
            ?.headerExtensions
            ?.map { it.uri }
            ?: emptyList()
        assertTrue(VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI !in currentUris)
    }

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
        val processor = VirtualBackgroundProcessorFactory.create(null)

        if (VirtualBackgroundProcessorFactory.isSupported()) {
            assertNotNull(processor)
            assertTrue(processor is IOSVirtualBackgroundProcessor)
        } else {
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

private fun rtpCapabilitiesWithVideoOrientation(): RtpCapabilities =
    RtpCapabilities(
        headerExtensions = listOf(
            RtpHeaderExtension(
                kind = MediaKind.VIDEO,
                uri = "http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time",
                preferredId = 3
            ),
            RtpHeaderExtension(
                kind = MediaKind.VIDEO,
                uri = VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI,
                preferredId = 11
            )
        )
    )

private fun rtpCapabilitiesJsonWithVideoOrientation(): String =
    rtpCapabilitiesWithVideoOrientation().toIosBridgeLoadJson()

private fun headerExtensionUris(json: String): List<String> =
    mediaSfuJson.parseToJsonElement(json)
        .jsonObject["headerExtensions"]
        ?.jsonArray
        ?.mapNotNull { item -> item.jsonObject["uri"]?.jsonPrimitive?.content }
        ?: emptyList()

private class FakeLoadableBridge(
    private val currentCapabilitiesJson: String
) : IosNativeLoadableMediasoupBridge {
    var loadedCapabilitiesJson: String? = null

    override fun loadRtpCapabilitiesJson(rtpCapabilitiesJson: String): String? {
        loadedCapabilitiesJson = rtpCapabilitiesJson
        return null
    }

    override fun currentRtpCapabilitiesJson(): String? = currentCapabilitiesJson

    override fun createSendTransport(params: Map<String, Any?>): IosNativeSendTransportHandle {
        throw UnsupportedOperationException("Not needed for RTP capability filtering test")
    }

    override fun createRecvTransport(params: Map<String, Any?>): IosNativeRecvTransportHandle {
        throw UnsupportedOperationException("Not needed for RTP capability filtering test")
    }
}
