package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig

/**
 * iOS implementation of SocketManager.
 * TODO: Implement using socket.io-client-swift or native iOS WebSocket implementation
 */
actual fun createSocketManager(): SocketManager = IOSSocketManagerStub()

/**
 * Stub implementation for iOS - to be implemented with native Swift libraries
 */
class IOSSocketManagerStub : SocketManager {
    override val id: String? = null

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        return Result.failure(NotImplementedError("iOS Socket implementation not yet available"))
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return Result.failure(NotImplementedError("iOS Socket implementation not yet available"))
    }
    
    override fun isConnected(): Boolean = false
    
    override fun getConnectionState(): ConnectionState = ConnectionState.DISCONNECTED
    
    override suspend fun emit(event: String, data: Map<String, Any?>) {
        throw NotImplementedError("iOS Socket implementation not yet available")
    }
    
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        throw NotImplementedError("iOS Socket implementation not yet available")
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        throw NotImplementedError("iOS Socket implementation not yet available")
    }
    
    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {
        // No-op for now
    }
    
    override fun off(event: String) {
        // No-op for now
    }
    
    override fun offAll() {
        // No-op for now
    }
    
    override fun onConnect(handler: suspend () -> Unit) {
        // No-op for now
    }
    
    override fun onDisconnect(handler: suspend (String) -> Unit) {
        // No-op for now
    }
    
    override fun onError(handler: suspend (Throwable) -> Unit) {
        // No-op for now
    }
    
    override fun onReconnect(handler: suspend (Int) -> Unit) {
        // No-op for now
    }
    
    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {
        // No-op for now
    }
    
    override fun onReconnectFailed(handler: suspend () -> Unit) {
        // No-op for now
    }
}

/**
 * iOS stub data converter
 */
actual object SocketDataConverter {
    actual fun toMap(data: Any?): Map<String, Any?> {
        // TODO: Implement for iOS
        return emptyMap()
    }
    
    actual fun fromMap(map: Map<String, Any?>): Any {
        // TODO: Implement for iOS
        return map
    }
}
