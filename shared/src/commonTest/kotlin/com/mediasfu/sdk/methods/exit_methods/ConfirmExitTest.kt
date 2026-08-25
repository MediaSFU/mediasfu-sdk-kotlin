package com.mediasfu.sdk.methods.exit_methods

import com.mediasfu.sdk.socket.TestSocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class ExitCapturingSocket : TestSocketManager() {
    val emissions = mutableListOf<Pair<String, Map<String, Any?>>>()

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        emissions += event to data
    }
}

class ConfirmExitTest {
    @Test
    fun `default host exit preserves historical end-room behavior`() = runTest {
        val socket = ExitCapturingSocket()

        confirmExit(
            ConfirmExitOptions(
                socket = socket,
                member = "Host",
                roomName = "RoomA"
            )
        )

        assertEquals(1, socket.emissions.size)
        assertEquals("disconnectUser", socket.emissions.single().first)
        assertEquals(true, socket.emissions.single().second["endRoomOnHostExit"])
    }

    @Test
    fun `host can leave without ending on both active sockets`() = runTest {
        val socket = ExitCapturingSocket()
        val localSocket = ExitCapturingSocket().apply { setId("local-socket") }

        confirmExit(
            ConfirmExitOptions(
                socket = socket,
                localSocket = localSocket,
                member = "Host",
                roomName = "RoomA",
                endRoomOnHostExit = false
            )
        )

        assertEquals(false, socket.emissions.single().second["endRoomOnHostExit"])
        assertEquals(false, localSocket.emissions.single().second["endRoomOnHostExit"])
    }
}
