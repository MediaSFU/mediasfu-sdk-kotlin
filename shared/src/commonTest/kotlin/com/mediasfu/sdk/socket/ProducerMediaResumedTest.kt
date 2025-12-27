package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.PrepopulateUserMediaOptions
import com.mediasfu.sdk.model.ProducerMediaResumedOptions
import com.mediasfu.sdk.model.ReorderStreamsOptions
import com.mediasfu.sdk.testutil.TestProducerMediaResumedParameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProducerMediaResumedTest {
    @Test
    fun prepopulatesWhenAdminAndMainScreenFree() = runTest {
        val participant = Participant(
            name = "Presenter",
            audioID = "audio-1",
            videoID = "video-1",
            islevel = "2"
        )

        val params = TestProducerMediaResumedParameters(
            participants = listOf(participant),
            mainScreenFilledInitial = false
        )

        val options = ProducerMediaResumedOptions(
            name = "Presenter",
            kind = "video",
            parameters = params
        )

        producerMediaResumed(options)

        assertEquals(listOf(true, false), params.updateMainWindowCalls)
        assertEquals(1, params.prepopulateCalls.size)
        assertEquals("Host", params.prepopulateCalls.first().name)
    }

    @Test
    fun reordersWhenNoVideoAndMediaDisplay() = runTest {
        val participant = Participant(
            name = "Viewer",
            audioID = "audio-2",
            videoID = "",
            islevel = "1"
        )

        val params = TestProducerMediaResumedParameters(
            participants = listOf(participant),
            sharedInitial = false,
            shareScreenStartedInitial = false
        )

        val options = ProducerMediaResumedOptions(
            name = "Viewer",
            kind = "audio",
            parameters = params
        )

        producerMediaResumed(options)

        assertEquals(1, params.reorderCalls.size)
        val reorder = params.reorderCalls.first()
        assertTrue(!reorder.add)
        assertTrue(reorder.screenChanged)
    }
}