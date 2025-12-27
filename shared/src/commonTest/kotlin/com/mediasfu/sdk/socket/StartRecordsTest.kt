package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.StartRecordsOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CapturingSocket : TestSocketManager() {
    val invocations: MutableList<Pair<String, Map<String, Any?>>> = mutableListOf()
    var lastAck: ((Any?) -> Unit)? = null

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        invocations += event to data
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        invocations += event to data
        lastAck = callback
        callback(mapOf("success" to true))
    }

    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        invocations += event to data
        @Suppress("UNCHECKED_CAST")
        return mapOf("success" to true) as T
    }
}

class StartRecordsTest {
    @Test
    fun emitsStartRecordingWithAck() = runTest {
        val socket = CapturingSocket()
        val socketLike = SocketManagerToSocketLikeAdapter(socket)
        val options = StartRecordsOptions(
            roomName = "RoomA",
            member = "Admin",
            socket = socketLike
        )

        startRecords(options)

        assertEquals(1, socket.invocations.size)
        val (event, payload) = socket.invocations.first()
        assertEquals("startRecordIng", event)
        assertEquals("RoomA", payload["roomName"])
        assertEquals("Admin", payload["member"])
        assertNotNull(socket.lastAck)
    }

    @Test
    fun doesNothingWhenSocketMissing() = runTest {
        val options = StartRecordsOptions(
            roomName = "RoomA",
            member = "Admin",
            socket = null
        )

        startRecords(options)

        assertTrue(true)
    }
}
