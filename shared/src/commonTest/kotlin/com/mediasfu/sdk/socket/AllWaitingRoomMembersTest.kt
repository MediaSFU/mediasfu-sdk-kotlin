package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.AllWaitingRoomMembersOptions
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlinx.coroutines.test.runTest

class AllWaitingRoomMembersTest {
    @Test
    fun updatesWaitingRoomListAndTotals() = runTest {
        val capturedParticipants = mutableListOf<List<WaitingRoomParticipant>>()
        val capturedTotals = mutableListOf<Int>()

        val waitingParticipants = listOf(
            WaitingRoomParticipant(name = "Alice", id = "1"),
            WaitingRoomParticipant(name = "Bob", id = "2")
        )

        val options = AllWaitingRoomMembersOptions(
            waitingParticipants = waitingParticipants,
            updateWaitingRoomList = { capturedParticipants += it },
            updateTotalReqWait = { capturedTotals += it }
        )

        allWaitingRoomMembers(options)

        assertEquals(1, capturedParticipants.size)
        assertSame(waitingParticipants, capturedParticipants.first())
        assertEquals(listOf(waitingParticipants.size), capturedTotals)
    }
}
