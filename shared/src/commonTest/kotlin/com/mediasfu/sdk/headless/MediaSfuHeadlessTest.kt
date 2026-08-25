package com.mediasfu.sdk.headless

import com.mediasfu.sdk.MediaSfuEngine
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Poll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MediaSfuHeadlessTest {
    @Test
    fun `current parameters is a pure identity read`() {
        val parameters = MediasfuParameters()
        var participantCallbackCount = 0
        parameters.onParticipantsUpdated = { participantCallbackCount++ }

        assertSame(parameters, parameters.getCurrentParams())
        assertEquals(0, participantCallbackCount)
    }

    @Test
    fun `snapshot is detached from later mutable bag publication`() {
        val engine = MediaSfuEngine(deviceProvider = { null })
        val parameters = engine.getParameters()
        parameters.validated = true
        parameters.roomName = "room-before"
        parameters.participants = listOf(Participant(id = "p1", name = "Ada", audioOn = true))
        parameters.polls = listOf(Poll(id = "poll-1", question = "Before", options = listOf("A", "B")))

        val before = MediaSfuHeadlessController(engine).snapshot()

        parameters.roomName = "room-after"
        parameters.participants = emptyList()
        parameters.polls = emptyList()

        assertEquals("room-before", before.readiness.roomName)
        assertEquals(listOf("Ada"), before.participants.map { it.name })
        assertEquals(listOf("Before"), before.polls.map { it.question })
    }

    @Test
    fun `unsupported playback capabilities are explicit`() {
        val capabilities = MediaSfuHeadlessCapabilities()

        assertFalse(capabilities.viewerMode.available)
        assertTrue(capabilities.viewerMode.reason?.isNotBlank() == true)
        assertFalse(capabilities.hlsPlayback.available)
        assertTrue(capabilities.hlsPlayback.reason?.contains("playback") == true)
    }
}
