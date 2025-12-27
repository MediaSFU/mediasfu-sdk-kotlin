package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MeetingTimeRemainingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MeetingTimeRemainingTest {
    @Test
    fun showsAlertWhenNotChat() = runTest {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val options = MeetingTimeRemainingOptions(
            timeRemaining = 450_000,
            eventType = EventType.CONFERENCE,
            showAlert = { message, type, duration ->
                calls += Triple(message, type, duration)
            }
        )

        meetingTimeRemaining(options)

        assertEquals(1, calls.size)
        val (message, type, duration) = calls.first()
        assertEquals("The event will end in 7:30 minutes.", message)
        assertEquals("success", type)
        assertEquals(3000, duration)
    }

    @Test
    fun skipsAlertWhenChatEvent() = runTest {
        val calls = mutableListOf<Triple<String, String, Int>>()
        val options = MeetingTimeRemainingOptions(
            timeRemaining = 120_000,
            eventType = EventType.CHAT,
            showAlert = { message, type, duration ->
                calls += Triple(message, type, duration)
            }
        )

        meetingTimeRemaining(options)

        assertTrue(calls.isEmpty())
    }

    @Test
    fun handlesMissingAlertCallback() = runTest {
        val options = MeetingTimeRemainingOptions(
            timeRemaining = 30_000,
            eventType = EventType.CONFERENCE,
            showAlert = null
        )

        meetingTimeRemaining(options)

        assertTrue(true)
    }
}
