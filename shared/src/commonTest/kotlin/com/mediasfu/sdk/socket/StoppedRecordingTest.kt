package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.StoppedRecordingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class StoppedRecordingTest {
    @Test
    fun showsAlertWhenStopped() = runTest {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val options = StoppedRecordingOptions(
            state = "stop",
            reason = "The session ended",
            showAlert = { message, type, duration ->
                calls += Triple(message, type, duration)
            }
        )

        stoppedRecording(options)

        assertEquals(1, calls.size)
        val (message, type, duration) = calls.first()
        assertEquals("The recording has stopped - The session ended.", message)
        assertEquals("danger", type)
        assertEquals(3000, duration)
    }

    @Test
    fun skipsAlertWhenStateNotStop() = runTest {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val options = StoppedRecordingOptions(
            state = "pause",
            reason = "Paused",
            showAlert = { message, type, duration ->
                calls += Triple(message, type, duration)
            }
        )

        stoppedRecording(options)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun swallowsExceptionsFromAlert() = runTest {
        val options = StoppedRecordingOptions(
            state = "stop",
            reason = "Network error",
            showAlert = { _, _, _ -> throw IllegalStateException("fail") }
        )

        stoppedRecording(options)

        assertTrue(true)
    }
}
