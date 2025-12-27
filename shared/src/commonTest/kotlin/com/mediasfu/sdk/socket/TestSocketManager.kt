package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.model.SocketLike

/**
 * Minimal SocketManager implementation for unit tests.
 * Provides configurable connection state and no-op handler registration.
 * 
 * For tests that need SocketLike, use SocketManagerToSocketLikeAdapter.
 */
open class TestSocketManager : SocketManager {
    protected var connectedState: Boolean = true
    private var identifier: String? = "test-socket"

    override val id: String?
        get() = identifier

    fun setId(id: String?) {
        identifier = id
    }

    fun setConnected(connected: Boolean) {
        connectedState = connected
    }

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        connectedState = true
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        connectedState = false
        return Result.success(Unit)
    }

    override fun isConnected(): Boolean = connectedState

    override fun getConnectionState(): ConnectionState =
        if (connectedState) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        // no-op by default
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        // default to Unit to allow subclasses to override as needed
        return Unit as T
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        callback(Unit)
    }

    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {
        // no-op
    }

    override fun off(event: String) {
        // no-op
    }

    override fun offAll() {
        // no-op
    }

    override fun onConnect(handler: suspend () -> Unit) {
        // no-op
    }

    override fun onDisconnect(handler: suspend (String) -> Unit) {
        // no-op
    }

    override fun onError(handler: suspend (Throwable) -> Unit) {
        // no-op
    }

    override fun onReconnect(handler: suspend (Int) -> Unit) {
        // no-op
    }

    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {
        // no-op
    }

    override fun onReconnectFailed(handler: suspend () -> Unit) {
        // no-op
    }
}

/**
 * Adapter that wraps a SocketManager (including TestSocketManager) as a SocketLike.
 * Use this when code requires SocketLike? type.
 */
class SocketManagerToSocketLikeAdapter(
    private val socketManager: SocketManager
) : SocketLike {
    override val isConnected: Boolean
        get() = socketManager.isConnected()

    override val id: String?
        get() = socketManager.id

    override fun emit(event: String, data: Map<String, Any?>) {
        kotlinx.coroutines.runBlocking {
            socketManager.emit(event, data)
        }
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        ack: (Map<String, Any?>) -> Unit
    ) {
        socketManager.emitWithAck(event, data) { response ->
            @Suppress("UNCHECKED_CAST")
            val mapped = if (response is Map<*, *>) {
                response as Map<String, Any?>
            } else {
                emptyMap()
            }
            ack(mapped)
        }
    }
}

