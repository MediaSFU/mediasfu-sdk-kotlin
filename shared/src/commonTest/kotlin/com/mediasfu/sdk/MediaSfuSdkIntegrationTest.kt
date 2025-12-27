package com.mediasfu.sdk

import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.model.PollUpdatedData
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.RequestResponse
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.testutil.TestMediaStream
import com.mediasfu.sdk.testutil.TestMediaStreamTrack
import com.mediasfu.sdk.testutil.TestSocketManager
import com.mediasfu.sdk.testutil.TestWebRtcDevice
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Focused integration-style checks that exercise the shared test doubles and
 * core models without relying on platform WebRTC implementations.
 */
class MediaSfuSdkIntegrationTest {

    @Test
    fun `media stream track lifecycle is tracked`() {
        val track = TestMediaStreamTrack(kind = "audio")
        assertTrue(track.enabled)

        track.stop()
        assertTrue(track.isStopped)
        assertFalse(track.enabled)
        assertEquals(1, track.stopCalls.size)

        track.resume()
        assertFalse(track.isStopped)
        assertTrue(track.enabled)
    }

    @Test
    fun `media stream captures add and remove operations`() {
        val audioTrack = TestMediaStreamTrack(kind = "audio")
        val videoTrack = TestMediaStreamTrack(kind = "video")
    val stream = TestMediaStream(audioTracks = listOf<MediaStreamTrack>(audioTrack))

        stream.addTrack(videoTrack)
    assertEquals(listOf(audioTrack, videoTrack), stream.getTracks())
    assertEquals(listOf(videoTrack), stream.addTrackCalls.toList())

        stream.removeTrack(audioTrack)
    assertEquals(listOf(videoTrack), stream.getTracks())
    assertEquals(listOf(audioTrack), stream.removeTrackCalls.toList())

        stream.stop()
        assertEquals(1, stream.stopCalls.size)
        assertTrue(videoTrack.isStopped)
    }

    @Test
    fun `web rtc device fake records interactions`() = runTest {
        val device = TestWebRtcDevice()
        device.enumerateDevicesResult = listOf(
            MediaDeviceInfo(deviceId = "mic-1", kind = "audioinput", label = "Mic")
        )

        val mediaStream = TestMediaStream()
        device.nextUserMedia = mediaStream

    val constraints: Map<String, Any?> = mapOf("audio" to true)
        val stream = device.getUserMedia(constraints)
        assertEquals(mediaStream, stream)
    assertEquals(listOf(constraints), device.getUserMediaCalls.toList())

        val devices = device.enumerateDevices()
        assertEquals(1, devices.size)

    val sendParams = mapOf<String, Any?>("id" to "send")
    val recvParams = mapOf<String, Any?>("id" to "recv")
        device.createSendTransport(sendParams)
        device.createRecvTransport(recvParams)
    assertEquals(listOf(sendParams), device.createSendTransportCalls.toList())
    assertEquals(listOf(recvParams), device.createRecvTransportCalls.toList())

        device.close()
        assertTrue(device.closed)

        device.reset()
        assertTrue(device.getUserMediaCalls.isEmpty())
        assertTrue(device.createSendTransportCalls.isEmpty())
        assertTrue(device.createRecvTransportCalls.isEmpty())
        assertFalse(device.closed)
    }

    @Test
    fun `core models can be instantiated with shared defaults`() {
        val participant = Participant(name = "Primary", audioID = "a1", videoID = "v1")
        assertEquals("Primary", participant.name)

        val stream = Stream(
            id = "stream-1",
            producerId = "producer-123",
            muted = false,
            name = "Camera",
            audioID = "a1",
            videoID = "v1"
        )
        assertEquals("producer-123", stream.producerId)

        val meeting = MeetingRoomParams(
            itemPageLimit = 10,
            mediaType = "video",
            addCoHost = true,
            targetOrientation = "landscape",
            targetOrientationHost = "landscape",
            targetResolution = "720p",
            targetResolutionHost = "720p",
            type = "CONFERENCE",
            audioSetting = "stereo",
            videoSetting = "hd",
            screenshareSetting = "enabled",
            chatSetting = "enabled"
        )
        assertEquals("video", meeting.mediaType)

        val poll = Poll(
            id = "poll-1",
            question = "Do you agree?",
            options = listOf("Yes", "No"),
            status = "active"
        )
        val updated = PollUpdatedData(polls = listOf(poll), poll = poll, status = "updated")
        assertEquals("updated", updated.status)

        val request = Request(id = "req-1", icon = "hand")
        val response = RequestResponse(id = "req-1", action = "approve")
        assertEquals("hand", request.icon)
        assertEquals("approve", response.action)
    }

    @Test
    fun `socket manager fake captures emission flows`() = runTest {
        val socket = TestSocketManager()
        val config = com.mediasfu.sdk.model.SocketConfig()

        socket.connect("wss://example", config)
        assertTrue(socket.isConnected())
        assertEquals(1, socket.connectCalls.size)

    socket.emit("event", mapOf<String, Any?>("value" to 1))
        assertEquals(1, socket.emitCalls.size)

        socket.emitWithAck("ackEvent", emptyMap<String, Any?>()) { _ -> }
        assertEquals(1, socket.emitWithAckCallbackCalls.size)

        socket.disconnect()
        assertFalse(socket.isConnected())
        assertEquals(1, socket.disconnectCalls.size)
    }
}
