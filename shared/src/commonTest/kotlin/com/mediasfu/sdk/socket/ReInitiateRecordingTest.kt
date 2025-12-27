package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.ReInitiateRecordingOptions
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private class FakeSocket : TestSocketManager() {
    var lastEvent: String? = null
    var lastData: Map<String, Any?>? = null
    var ackInvokedWith: Any? = null

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        lastEvent = event
        lastData = data
        val response = mapOf("success" to true)
        callback(response)
        ackInvokedWith = response
    }

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        lastEvent = event
        lastData = data
    }
}

class ReInitiateRecordingTest {
    @Test
    fun emitsStartRecordingWhenAllowed() = runTest {
        val socket = FakeSocket()
        val socketLike = SocketManagerToSocketLikeAdapter(socket)
        val options = ReInitiateRecordingOptions(
            roomName = "room",
            member = "host",
            socket = socketLike,
            adminRestrictSetting = false
        )

        reInitiateRecording(options)

        assertEquals("startRecordIng", socket.lastEvent)
        assertEquals("room", socket.lastData?.get("roomName"))
        assertEquals("host", socket.lastData?.get("member"))
    val ackMap = socket.ackInvokedWith as? Map<*, *>
    assertEquals(true, ackMap?.get("success"))
    }

    @Test
    fun doesNothingWhenRestrictedOrSocketMissing() = runTest {
        val socket = FakeSocket()
        val socketLike = SocketManagerToSocketLikeAdapter(socket)
        val restricted = ReInitiateRecordingOptions(
            roomName = "room",
            member = "host",
            socket = socketLike,
            adminRestrictSetting = true
        )

        reInitiateRecording(restricted)

        assertNull(socket.lastEvent)

        val noSocket = ReInitiateRecordingOptions(
            roomName = "room",
            member = "host",
            socket = null,
            adminRestrictSetting = false
        )

        reInitiateRecording(noSocket)

        assertNull(socket.lastEvent)
    }
}