package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeetingHandlersTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun meetingEndedShowsAlertAndClearsValidation() = runTest {
        var alertMessage: String? = null
        var alertType: String? = null
        var alertDuration: Int? = null
        var validated: Boolean? = true

        val options = MeetingEndedOptions(
            showAlert = { message, type, duration ->
                alertMessage = message
                alertType = type
                alertDuration = duration
            },
            redirectUrl = null,
            onWeb = false,
            eventType = EventType.CONFERENCE,
            updateValidated = { validated = it }
        )

        meetingEnded(options)

        assertEquals("The meeting has ended. Redirecting to the home page...", alertMessage)
        assertEquals("danger", alertType)
        assertEquals(2_000, alertDuration)
        assertFalse(validated!!)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun meetingEndedSkipsAlertForChat() = runTest {
        var alertInvoked = false

        val options = MeetingEndedOptions(
            showAlert = { _, _, _ -> alertInvoked = true },
            redirectUrl = "https://example.com",
            onWeb = true,
            eventType = EventType.CHAT,
            updateValidated = null
        )

        meetingEnded(options)

        assertFalse(alertInvoked)
    }

    @Test
    fun meetingStillThereShowsModal() {
        var modalVisible = false
        meetingStillThere(
            MeetingStillThereOptions { visible -> modalVisible = visible }
        )
        assertTrue(modalVisible)
    }

    @Test
    fun meetingTimeRemainingFormatsMessage() {
        var message: String? = null
        var alertType: String? = null
        var duration: Int? = null

        meetingTimeRemaining(
            MeetingTimeRemainingOptions(
                timeRemainingMillis = 450_000,
                eventType = EventType.WEBINAR,
                showAlert = { msg, type, dur ->
                    message = msg
                    alertType = type
                    duration = dur
                }
            )
        )

        assertEquals("The event will end in 7:30 minutes.", message)
        assertEquals("success", alertType)
        assertEquals(3_000, duration)
    }

    @Test
    fun participantRequestedAppendsAndUpdatesCount() {
        var capturedRequests: List<Request>? = null
        var totalCount: Int? = null

        val initialRequests = listOf(Request(id = "1", icon = "icon"))
        val waiting = listOf(WaitingRoomParticipant(name = "Bob", id = "2"))
        val newRequest = Request(id = "3", icon = "icon")

        participantRequested(
            ParticipantRequestedOptions(
                userRequest = newRequest,
                requestList = initialRequests,
                waitingRoomList = waiting,
                updateTotalReqWait = { totalCount = it },
                updateRequestList = { capturedRequests = it }
            )
        )

        assertEquals(2, capturedRequests?.size)
        assertEquals(3, totalCount)
    }

    @Test
    fun personJoinedTriggersAlert() {
        var message: String? = null
        var type: String? = null
        var duration: Int? = null

        personJoined(
            PersonJoinedOptions(
                name = "Alice",
                showAlert = { msg, alertType, dur ->
                    message = msg
                    type = alertType
                    duration = dur
                }
            )
        )

        assertEquals("Alice has joined the event.", message)
        assertEquals("success", type)
        assertEquals(3_000, duration)
    }

    @Test
    fun personJoinedDoesNothingWhenAlertMissing() {
        var message: String? = null
        personJoined(PersonJoinedOptions(name = "NoAlert", showAlert = null))
        assertNull(message)
    }
}
