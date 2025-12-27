package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.TimeLeftRecordingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeLeftRecordingTest {
    @Test
    fun showsAlertWithRemainingSeconds() {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val options = TimeLeftRecordingOptions(
            timeLeft = 25,
            showAlert = { message, type, duration ->
                calls += Triple(message, type, duration)
            }
        )

        timeLeftRecording(options)

        assertEquals(1, calls.size)
        val (message, type, duration) = calls.first()
        assertEquals("The recording will stop in less than 25 seconds.", message)
        assertEquals("danger", type)
        assertEquals(3000, duration)
    }

    @Test
    fun handlesMissingAlertCallback() {
        val options = TimeLeftRecordingOptions(
            timeLeft = 10,
            showAlert = null
        )

        timeLeftRecording(options)

        assertTrue(true)
    }

    @Test
    fun swallowsExceptionsFromAlert() {
        val options = TimeLeftRecordingOptions(
            timeLeft = 5,
            showAlert = { _, _, _ -> throw IllegalStateException("boom") }
        )

        timeLeftRecording(options)

        assertTrue(true)
    }
}
