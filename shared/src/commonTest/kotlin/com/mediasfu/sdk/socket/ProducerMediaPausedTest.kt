package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.PrepopulateUserMediaOptions
import com.mediasfu.sdk.model.ProducerMediaPausedOptions
import com.mediasfu.sdk.model.ReUpdateInterOptions
import com.mediasfu.sdk.model.ReorderStreamsOptions
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.testutil.TestProducerMediaPausedParameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProducerMediaPausedTest {
    @Test
    fun adminParticipantTriggersPrepopulate() = runTest {
        val participant = Participant(
            name = "Speaker",
            audioID = "audio-1",
            videoID = "video-1",
            muted = true,
            islevel = "2"
        )

        val params = TestProducerMediaPausedParameters(
            participants = listOf(participant),
            sharedInitial = false,
            shareScreenStartedInitial = false,
            level = "1"
        )

        val options = ProducerMediaPausedOptions(
            producerId = "video-1",
            kind = "video",
            name = "Speaker",
            parameters = params
        )

        producerMediaPaused(options)

        assertEquals(listOf(true, false), params.updateMainWindowCalls)
        assertEquals(1, params.prepopulateCalls.size)
        assertEquals("Host", params.prepopulateCalls.first().name)
    }

    @Test
    fun removesActiveSoundAndReupdatesWhenSharing() = runTest {
        val participant = Participant(
            name = "Listener",
            audioID = "audio-2",
            videoID = "video-2",
            muted = true
        )

        val params = TestProducerMediaPausedParameters(
            activeSounds = listOf("Listener"),
            participants = listOf(participant),
            sharedInitial = true,
            shareScreenStartedInitial = true
        )

        val options = ProducerMediaPausedOptions(
            producerId = "video-2",
            kind = "video",
            name = "Listener",
            parameters = params
        )

        producerMediaPaused(options)

        assertTrue(params.updateActiveSoundsCalls.isNotEmpty())
        assertFalse(params.updateActiveSoundsCalls.first().contains("Listener"))
        assertEquals(1, params.reUpdateCalls.size)
        assertEquals("Listener", params.reUpdateCalls.first().name)
    }

    @Test
    fun audioPauseReordersAndReupdates() = runTest {
        val participant = Participant(
            name = "AudioUser",
            audioID = "audio-3",
            videoID = "",
            muted = true
        )

        val params = TestProducerMediaPausedParameters(
            participants = listOf(participant),
            oldSoundIds = listOf("AudioUser"),
            sharedInitial = false,
            shareScreenStartedInitial = false
        )

        val options = ProducerMediaPausedOptions(
            producerId = "audio-3",
            kind = "audio",
            name = "AudioUser",
            parameters = params
        )

        producerMediaPaused(options)

        assertEquals(1, params.reorderCalls.size)
        assertEquals(1, params.reUpdateCalls.size)
        assertEquals("AudioUser", params.reUpdateCalls.first().name)
    }
}