package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.ParticipantRequestedOptions
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParticipantRequestedTest {

    @Test
    fun addsRequestToListAndUpdatesTotalCount() {
        val initialRequestList = listOf(
            Request(id = "req1", icon = "fa-microphone", name = "User1")
        )
        val waitingRoomList = listOf(
            WaitingRoomParticipant(name = "User2", id = "wait1")
        )
        val newRequest = Request(id = "req2", icon = "fa-video", name = "User3")
        
        var updatedList: List<Request>? = null
        var totalCount = 0

        val options = ParticipantRequestedOptions(
            userRequest = newRequest,
            requestList = initialRequestList,
            waitingRoomList = waitingRoomList,
            updateTotalReqWait = { totalCount = it },
            updateRequestList = { updatedList = it }
        )

        participantRequested(options)

        // Verify the request was added to the list
        assertEquals(2, updatedList?.size, "Request list should have 2 items")
        assertTrue(updatedList?.contains(newRequest) == true, "New request should be in the list")
        assertTrue(updatedList?.contains(initialRequestList[0]) == true, "Original request should remain")
        
        // Verify total count includes both request list and waiting room
        assertEquals(3, totalCount, "Total count should be 3 (2 requests + 1 waiting)")
    }

    @Test
    fun handlesEmptyRequestList() {
        val newRequest = Request(id = "req1", icon = "fa-desktop", name = "User1")
        var updatedList: List<Request>? = null
        var totalCount = 0

        val options = ParticipantRequestedOptions(
            userRequest = newRequest,
            requestList = emptyList(),
            waitingRoomList = emptyList(),
            updateTotalReqWait = { totalCount = it },
            updateRequestList = { updatedList = it }
        )

        participantRequested(options)

        assertEquals(1, updatedList?.size, "Request list should have 1 item")
        assertEquals(newRequest, updatedList?.firstOrNull(), "The new request should be the only item")
        assertEquals(1, totalCount, "Total count should be 1")
    }

    @Test
    fun handlesEmptyWaitingRoom() {
        val initialRequestList = listOf(
            Request(id = "req1", icon = "fa-microphone", name = "User1"),
            Request(id = "req2", icon = "fa-video", name = "User2")
        )
        val newRequest = Request(id = "req3", icon = "fa-comments", name = "User3")
        var totalCount = 0

        val options = ParticipantRequestedOptions(
            userRequest = newRequest,
            requestList = initialRequestList,
            waitingRoomList = emptyList(),
            updateTotalReqWait = { totalCount = it },
            updateRequestList = {}
        )

        participantRequested(options)

        // Total should only include requests, no waiting room participants
        assertEquals(3, totalCount, "Total count should be 3 (all requests)")
    }

    @Test
    fun countsMultipleWaitingRoomParticipants() {
        val waitingRoomList = listOf(
            WaitingRoomParticipant(name = "User1", id = "wait1"),
            WaitingRoomParticipant(name = "User2", id = "wait2"),
            WaitingRoomParticipant(name = "User3", id = "wait3")
        )
        val newRequest = Request(id = "req1", icon = "fa-microphone", name = "User4")
        var totalCount = 0

        val options = ParticipantRequestedOptions(
            userRequest = newRequest,
            requestList = emptyList(),
            waitingRoomList = waitingRoomList,
            updateTotalReqWait = { totalCount = it },
            updateRequestList = {}
        )

        participantRequested(options)

        // Total should include 1 request + 3 waiting room participants
        assertEquals(4, totalCount, "Total count should be 4 (1 request + 3 waiting)")
    }

    @Test
    fun preservesRequestOrder() {
        val initialRequestList = listOf(
            Request(id = "req1", icon = "fa-microphone", name = "User1"),
            Request(id = "req2", icon = "fa-video", name = "User2")
        )
        val newRequest = Request(id = "req3", icon = "fa-desktop", name = "User3")
        var updatedList: List<Request>? = null

        val options = ParticipantRequestedOptions(
            userRequest = newRequest,
            requestList = initialRequestList,
            waitingRoomList = emptyList(),
            updateTotalReqWait = {},
            updateRequestList = { updatedList = it }
        )

        participantRequested(options)

        assertEquals(3, updatedList?.size, "Request list should have 3 items")
        assertEquals("req1", updatedList?.get(0)?.id, "First request should be req1")
        assertEquals("req2", updatedList?.get(1)?.id, "Second request should be req2")
        assertEquals("req3", updatedList?.get(2)?.id, "Third request should be req3")
    }
}
