package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.methods.stream_methods.ClickAudioOptions
import com.mediasfu.sdk.methods.stream_methods.ClickAudioParameters
import com.mediasfu.sdk.methods.stream_methods.clickAudio
import com.mediasfu.sdk.webrtc.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class MicrophoneRetryTest {
    private data class Fixture(
        val parameters: ClickAudioParameters,
        val device: WebRtcDevice,
        val resumed: MutableList<Boolean>,
        val published: MutableList<Boolean>,
        val pausedUpdates: MutableList<Boolean>
    )

    private fun fixture(state: TransportConnectionState?, producerPresent: Boolean): Fixture {
        val p = mockk<ClickAudioParameters>(relaxed = true)
        val device = mockk<WebRtcDevice>()
        val stream = mockk<MediaStream>()
        val transport = state?.let { mockk<WebRtcTransport> { every { connectionState } returns it } }
        val producer = if (producerPresent) mockk<WebRtcProducer>(relaxed = true) else null
        val resumed = mutableListOf<Boolean>()
        val published = mutableListOf<Boolean>()
        val pausedUpdates = mutableListOf<Boolean>()
        every { p.islevel } returns "2"
        every { p.audioOnlyRoom } returns false
        every { p.audioAlreadyOn } returns false
        every { p.audioPaused } returns true
        every { p.adminRestrictSetting } returns false
        every { p.hasAudioPermission } returns true
        every { p.device } returns device
        every { p.audioProducer } returns producer
        every { p.producerTransport } returns transport
        every { p.participants } returns emptyList()
        every { p.userDefaultAudioInputDevice } returns ""
        every { p.updateAudioPaused } returns { pausedUpdates.add(it) }
        every { p.resumeSendTransportAudio } returns { resumed.add(true) }
        every { p.streamSuccessAudio } returns { published.add(true) }
        coEvery { device.getUserMedia(any()) } returns stream
        return Fixture(p, device, resumed, published, pausedUpdates)
    }

    @Test
    fun missingPausedProducerUsesExplicitCapture() = runTest {
        val f = fixture(null, false)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(false), f.pausedUpdates)
        assertTrue(f.resumed.isEmpty())
        assertEquals(listOf(true), f.published)
        coVerify(exactly = 1) { f.device.getUserMedia(any()) }
    }

    @Test
    fun closedTransportDoesNotResumeItsRetainedProducer() = runTest {
        val f = fixture(TransportConnectionState.CLOSED, true)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(false), f.pausedUpdates)
        assertTrue(f.resumed.isEmpty())
        coVerify(exactly = 1) { f.device.getUserMedia(any()) }
    }

    @Test
    fun failedTransportUsesExplicitCapture() = runTest {
        val f = fixture(TransportConnectionState.FAILED, true)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(false), f.pausedUpdates)
        assertTrue(f.resumed.isEmpty())
        assertEquals(listOf(true), f.published)
        coVerify(exactly = 1) { f.device.getUserMedia(any()) }
    }

    @Test
    fun transientDisconnectKeepsMutedProducerWithoutCapture() = runTest {
        val f = fixture(TransportConnectionState.DISCONNECTED, true)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(true), f.resumed)
        assertTrue(f.pausedUpdates.isEmpty())
        coVerify(exactly = 0) { f.device.getUserMedia(any()) }
    }

    @Test
    fun missingProducerWithConnectedTransportUsesExplicitCapture() = runTest {
        val f = fixture(TransportConnectionState.CONNECTED, false)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(false), f.pausedUpdates)
        assertTrue(f.resumed.isEmpty())
        assertEquals(listOf(true), f.published)
        coVerify(exactly = 1) { f.device.getUserMedia(any()) }
    }

    private suspend fun verifyPublicationAfterStaleFlags(state: TransportConnectionState, missingProducer: Boolean) {
        val f = fixture(state, !missingProducer)
        val p = f.parameters
        var transportCreated = true
        var audioCreated = true
        var producer = p.audioProducer
        var transport = p.producerTransport
        var created = 0
        var connected = 0
        every { p.transportCreated } answers { transportCreated }
        every { p.transportCreatedAudio } answers { audioCreated }
        every { p.audioProducer } answers { producer }
        every { p.producerTransport } answers { transport }
        every { p.getUpdatedAllParams() } returns p
        every { p.updateTransportCreated } returns { transportCreated = it }
        every { p.updateTransportCreatedAudio } returns { audioCreated = it }
        every { p.updateProducerTransport } returns { transport = it }
        every { p.updateAudioProducer(any()) } answers { producer = firstArg() }
        every { p.lockScreen } returns true
        every { p.streamSuccessAudio } returns { streamSuccessAudio(it) }
        every { p.createSendTransport } returns {
            created++
            assertNull(producer)
            assertNull(transport)
            transportCreated = true
            audioCreated = true
        }
        every { p.connectSendTransportAudio } returns {
            connected++
            assertNull(producer)
            assertNotNull(transport)
            audioCreated = true
        }

        clickAudio(ClickAudioOptions(p))

        assertEquals(if (missingProducer) 0 else 1, created)
        assertEquals(if (missingProducer) 1 else 0, connected)
        assertTrue(f.resumed.isEmpty())
        assertTrue(transportCreated)
        assertTrue(audioCreated)
        coVerify(exactly = 1) { f.device.getUserMedia(any()) }
    }

    @Test
    fun terminalTransportRecreatesThroughActualStreamSuccess() = runTest {
        verifyPublicationAfterStaleFlags(TransportConnectionState.CLOSED, false)
    }

    @Test
    fun missingProducerReusesHealthyTransportThroughActualStreamSuccess() = runTest {
        verifyPublicationAfterStaleFlags(TransportConnectionState.CONNECTED, true)
    }

    @Test
    fun workingMutedProducerResumesWithoutCapture() = runTest {
        val f = fixture(TransportConnectionState.CONNECTED, true)
        clickAudio(ClickAudioOptions(f.parameters))
        assertEquals(listOf(true), f.resumed)
        assertTrue(f.pausedUpdates.isEmpty())
        coVerify(exactly = 0) { f.device.getUserMedia(any()) }
    }

    @Test
    fun restrictionStillBlocksMissingProducerCapture() = runTest {
        val f = fixture(null, false)
        every { f.parameters.adminRestrictSetting } returns true
        clickAudio(ClickAudioOptions(f.parameters))
        assertTrue(f.pausedUpdates.isEmpty())
        assertTrue(f.resumed.isEmpty())
        coVerify(exactly = 0) { f.device.getUserMedia(any()) }
    }
}
