package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MeetingEndedOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MeetingEndedTest {
    @Test
    fun showsAlertAndKeepsOnWebBranch() = runTest {
        val alerts = mutableListOf<Triple<String, String, Int>>()
        val options = MeetingEndedOptions(
            showAlert = { message, type, duration ->
                alerts += Triple(message, type, duration)
            },
            redirectUrl = "https://example.com",
            onWeb = true,
            eventType = EventType.CONFERENCE,
            updateValidated = null
        )

        meetingEnded(options)

        assertEquals(1, alerts.size)
        val (message, type, duration) = alerts.first()
        assertEquals("The meeting has ended. Redirecting to the home page...", message)
        assertEquals("danger", type)
        assertEquals(2000, duration)
    }

    @Test
    fun updatesValidatedWhenNotOnWeb() = runTest {
        val updates = mutableListOf<Boolean>()
        val options = MeetingEndedOptions(
            showAlert = null,
            redirectUrl = null,
            onWeb = false,
            eventType = EventType.CONFERENCE,
            updateValidated = { updates += it }
        )

        meetingEnded(options)

        assertEquals(listOf(false), updates)
    }

    @Test
    fun skipsAlertWhenChatEvent() = runTest {
        val alerts = mutableListOf<Triple<String, String, Int>>()
        val options = MeetingEndedOptions(
            showAlert = { message, type, duration ->
                alerts += Triple(message, type, duration)
            },
            redirectUrl = null,
            onWeb = false,
            eventType = EventType.CHAT,
            updateValidated = null
        )

        meetingEnded(options)

        assertTrue(alerts.isEmpty())
    }
}
