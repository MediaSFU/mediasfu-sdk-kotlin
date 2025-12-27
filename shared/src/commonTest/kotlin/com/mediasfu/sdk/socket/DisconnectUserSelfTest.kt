package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.DisconnectUserSelfOptions
import com.mediasfu.sdk.model.SocketLike
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private open class RecordingSocket : TestSocketManager() {
    val emits: MutableList<Pair<String, Map<String, Any?>>> = mutableListOf()

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        callback(emptyMap<String, Any?>())
    }

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        emits += event to data
    }
}

private class ThrowingSocket : RecordingSocket() {
    var throwOnEmit: Boolean = true

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        if (throwOnEmit) {
            throwOnEmit = false
            throw IllegalStateException("emit failure")
        }
        super.emit(event, data)
    }
}

class DisconnectUserSelfTest {
    @Test
    fun emitsDisconnectForRemoteAndLocalSockets() = runTest {
        val remote = SocketLikeAdapter(RecordingSocket())
        val local = SocketLikeAdapter(RecordingSocket())

        disconnectUserSelf(
            DisconnectUserSelfOptions(
                member = "user123",
                roomName = "main-room",
                socket = remote,
                localSocket = local
            )
        )

        assertEquals(1, remote.socket.emits.size)
        val (event, payload) = remote.socket.emits.first()
        assertEquals("disconnectUser", event)
        assertEquals("user123", payload["member"])
        assertEquals("main-room", payload["roomName"])
        assertEquals(true, payload["ban"])

        assertEquals(2, local.socket.emits.size)
        assertTrue(local.socket.emits.all { it.first == "disconnectUser" })
    }

    @Test
    fun skipsLocalSocketWhenNotConnected() = runTest {
        val remote = SocketLikeAdapter(RecordingSocket())
        val local = SocketLikeAdapter(RecordingSocket()).apply { setConnected(false) }

        disconnectUserSelf(
            DisconnectUserSelfOptions(
                member = "user123",
                roomName = "main-room",
                socket = remote,
                localSocket = local
            )
        )

        assertEquals(1, remote.socket.emits.size)
        assertTrue(local.socket.emits.isEmpty())
    }

    @Test
    fun ignoresErrorsFromLocalSocket() = runTest {
        val remote = SocketLikeAdapter(RecordingSocket())
        val local = SocketLikeAdapter(ThrowingSocket())

        disconnectUserSelf(
            DisconnectUserSelfOptions(
                member = "user123",
                roomName = "main-room",
                socket = remote,
                localSocket = local
            )
        )

        assertEquals(1, remote.socket.emits.size)
        assertTrue(local.socket.emits.isEmpty())
    }

    private class SocketLikeAdapter(val socket: RecordingSocket) : SocketLike {
        override val isConnected: Boolean
            get() = socket.isConnected()

        override val id: String?
            get() = socket.id

        override fun emitWithAck(
            event: String,
            data: Map<String, Any?>,
            ack: (Map<String, Any?>) -> Unit
        ) {
            socket.emitWithAck(event, data) { response ->
                @Suppress("UNCHECKED_CAST")
                val mapped = if (response is Map<*, *>) {
                    response as Map<String, Any?>
                } else {
                    emptyMap()
                }
                ack(mapped)
            }
        }

        override fun emit(event: String, data: Map<String, Any?>) {
            runBlocking { socket.emit(event, data) }
        }

        fun setConnected(value: Boolean) {
            socket.setConnected(value)
        }
    }
}
