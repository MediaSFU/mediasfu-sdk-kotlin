package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.ControlMediaHostOptions
import com.mediasfu.sdk.model.OnScreenChangesOptions
import com.mediasfu.sdk.model.StopShareScreenOptions
import com.mediasfu.sdk.testutil.TestControllableMediaStream
import com.mediasfu.sdk.testutil.TestControlMediaHostParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ControlMediaHostTest {
    @Test
    fun disablesAudioAndDisconnects() = runTest {
        val params = TestControlMediaHostParameters()
        val audioStream = TestControllableMediaStream()
        params.assignLocalStream(audioStream)

        controlMediaHost(ControlMediaHostOptions(type = "audio", parameters = params))

        val stream = params.localStream as TestControllableMediaStream
        assertEquals(1, stream.disableAudioCalls)
        assertEquals(listOf(true), params.adminRestrictUpdates)
        assertEquals(listOf(false), params.audioAlreadyOnUpdates)
        assertEquals(1, params.disconnectAudioOptions.size)
    }

    @Test
    fun disablesVideoOnBothStreamsAndTriggersReorder() = runTest {
        val params = TestControlMediaHostParameters()
        val audioStream = TestControllableMediaStream()
        val videoStream = TestControllableMediaStream()
        params.assignLocalStream(audioStream)
        params.assignLocalVideoStream(videoStream)

        controlMediaHost(ControlMediaHostOptions(type = "video", parameters = params))

        val updatedStream = params.localStream as TestControllableMediaStream
        val updatedVideoStream = params.localStreamVideo as TestControllableMediaStream
        assertEquals(1, updatedStream.disableVideoCalls)
        assertEquals(1, updatedVideoStream.disableVideoCalls)
        assertEquals(2, params.disconnectVideoOptions.size)
        assertEquals(2, params.videoAlreadyOnUpdates.size)
        assertTrue(params.onScreenChangesCalls.all { it.changed })
        assertEquals(2, params.onScreenChangesCalls.size)
    }

    @Test
    fun stopsScreenshareAndDisconnects() = runTest {
        val params = TestControlMediaHostParameters()
        val screenStream = TestControllableMediaStream()
        params.assignLocalScreenStream(screenStream)

        controlMediaHost(ControlMediaHostOptions(type = "screenshare", parameters = params))

        val updatedScreenStream = params.localStreamScreen as TestControllableMediaStream
        assertEquals(1, updatedScreenStream.disableVideoCalls)
        assertEquals(1, params.disconnectScreenOptions.size)
        assertEquals(1, params.stopShareCalls.size)
        assertEquals(listOf(false), params.screenAlreadyOnUpdates)
    }

    @Test
    fun togglesChatOnly() = runTest {
        val params = TestControlMediaHostParameters()

        controlMediaHost(ControlMediaHostOptions(type = "chat", parameters = params))

        assertEquals(listOf(false), params.chatAlreadyOnUpdates)
        assertEquals(0, params.disconnectAudioOptions.size)
        assertEquals(0, params.disconnectVideoOptions.size)
    }

    @Test
    fun controlsAllMediaWhenSuccessful() = runTest {
        val audioStream = TestControllableMediaStream()
        val altVideoStream = TestControllableMediaStream()
        val screenStream = TestControllableMediaStream()

        val params = TestControlMediaHostParameters()
        params.assignLocalStream(audioStream)
        params.assignLocalVideoStream(altVideoStream)
        params.assignLocalScreenStream(screenStream)

        controlMediaHost(ControlMediaHostOptions(type = "all", parameters = params))

        assertEquals(listOf(true), params.adminRestrictUpdates)
        assertEquals(listOf(false), params.audioAlreadyOnUpdates)
        assertEquals(listOf(false), params.screenAlreadyOnUpdates)
        assertEquals(listOf(false, false), params.videoAlreadyOnUpdates)
        assertEquals(1, params.disconnectAudioOptions.size)
        assertEquals(1, params.disconnectScreenOptions.size)
        assertEquals(2, params.disconnectVideoOptions.size)
        assertEquals(1, params.stopShareCalls.size)
        assertEquals(2, params.onScreenChangesCalls.size)
        assertTrue(params.onScreenChangesCalls.all { it.changed })
    }

    @Test
    fun controlsAllMediaGracefullyHandlesErrors() = runTest {
        val audioStream = TestControllableMediaStream().apply { shouldThrowOnAudio = true }
        val altVideoStream = TestControllableMediaStream()
        val screenStream = TestControllableMediaStream().apply { shouldThrowOnVideo = true }

        val params = TestControlMediaHostParameters()
        params.assignLocalStream(audioStream)
        params.assignLocalVideoStream(altVideoStream)
        params.assignLocalScreenStream(screenStream)

        controlMediaHost(ControlMediaHostOptions(type = "all", parameters = params))

        assertEquals(listOf(true), params.adminRestrictUpdates)
        assertEquals(0, params.disconnectAudioOptions.size)
        assertEquals(0, params.disconnectScreenOptions.size)
        assertTrue(params.disconnectVideoOptions.size >= 1)
        assertTrue(params.videoAlreadyOnUpdates.isNotEmpty())
        assertEquals(0, params.stopShareCalls.size)
    }

    @Test
    fun ignoresInvalidTypeGracefully() = runTest {
        val params = TestControlMediaHostParameters()

        controlMediaHost(ControlMediaHostOptions(type = "unknown", parameters = params))

        assertTrue(params.adminRestrictUpdates.isNotEmpty())
        assertEquals(0, params.disconnectAudioOptions.size)
        assertEquals(0, params.disconnectVideoOptions.size)
        assertEquals(0, params.disconnectScreenOptions.size)
    }
}
