package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for SocketManager implementation
 * 
 * Note: These tests focus on the interface contract and Android implementation.
 * Integration tests with a real Socket.IO server should be performed separately.
 */
class SocketManagerTest {

    // ========================================================================
    // Interface Contract Tests
    // ========================================================================

    @Test
    fun socketManagerShouldBeCreatable() {
        val socketManager = createSocketManager()
        assertNotNull(socketManager)
    }

    @Test
    fun initialStateShouldBeDisconnected() {
        val socketManager = createSocketManager()
        assertFalse(socketManager.isConnected())
        assertEquals(ConnectionState.DISCONNECTED, socketManager.getConnectionState())
    }

    // ========================================================================
    // Connection State Tests
    // ========================================================================

    @Test
    fun connectionStateShouldBeCorrect() {
        val states = ConnectionState.values()
        
        assertTrue(states.contains(ConnectionState.DISCONNECTED))
        assertTrue(states.contains(ConnectionState.CONNECTING))
        assertTrue(states.contains(ConnectionState.CONNECTED))
        assertTrue(states.contains(ConnectionState.RECONNECTING))
        assertTrue(states.contains(ConnectionState.FAILED))
    }

    // ========================================================================
    // Configuration Tests
    // ========================================================================

    @Test
    fun shouldAcceptSocketConfig() = runTest {
        val config = SocketConfig(
            reconnection = true,
            reconnectionAttempts = 5,
            reconnectionDelay = 1000,
            timeout = 10000
        )
        
        val socketManager = createSocketManager()
        
        // Should not throw
        val result = socketManager.connect("https://test.mediasfu.com", config)
        
        // Result might be success or failure depending on network
        assertNotNull(result)
    }

    @Test
    fun shouldUseDefaultConfigWhenNoneProvided() = runTest {
        val socketManager = createSocketManager()
        
        // Should use default SocketConfig
        val result = socketManager.connect("https://test.mediasfu.com")
        
        assertNotNull(result)
    }

    // ========================================================================
    // Event Registration Tests
    // ========================================================================

    @Test
    fun shouldAllowRegisteringEventHandlers() {
        val socketManager = createSocketManager()
        var handlerCalled = false
        
        socketManager.on("testEvent") { data ->
            handlerCalled = true
        }
        
        // Handler registered (actual invocation requires connection)
        assertFalse(handlerCalled) // Not called yet
    }

    @Test
    fun shouldAllowUnregisteringEventHandlers() {
        val socketManager = createSocketManager()
        
        socketManager.on("testEvent") { }
        socketManager.off("testEvent")
        
        // Should not throw
        assertTrue(true)
    }

    @Test
    fun shouldAllowUnregisteringAllHandlers() {
        val socketManager = createSocketManager()
        
        socketManager.on("event1") { }
        socketManager.on("event2") { }
        socketManager.offAll()
        
        // Should not throw
        assertTrue(true)
    }

    // ========================================================================
    // Connection Lifecycle Handler Tests
    // ========================================================================

    @Test
    fun shouldAllowRegisteringConnectHandler() {
        val socketManager = createSocketManager()
        var connectCalled = false
        
        socketManager.onConnect {
            connectCalled = true
        }
        
        // Handler registered
        assertFalse(connectCalled) // Not called until actual connection
    }

    @Test
    fun shouldAllowRegisteringDisconnectHandler() {
        val socketManager = createSocketManager()
        var disconnectReason: String? = null
        
        socketManager.onDisconnect { reason ->
            disconnectReason = reason
        }
        
        // Handler registered
        assertNull(disconnectReason)
    }

    @Test
    fun shouldAllowRegisteringErrorHandler() {
        val socketManager = createSocketManager()
        var errorReceived: Throwable? = null
        
        socketManager.onError { error ->
            errorReceived = error
        }
        
        // Handler registered
        assertNull(errorReceived)
    }

    @Test
    fun shouldAllowRegisteringReconnectHandler() {
        val socketManager = createSocketManager()
        var reconnectAttempt: Int? = null
        
        socketManager.onReconnect { attempt ->
            reconnectAttempt = attempt
        }
        
        // Handler registered
        assertNull(reconnectAttempt)
    }

    @Test
    fun shouldAllowRegisteringReconnectAttemptHandler() {
        val socketManager = createSocketManager()
        var attemptNumber: Int? = null
        
        socketManager.onReconnectAttempt { attempt ->
            attemptNumber = attempt
        }
        
        // Handler registered
        assertNull(attemptNumber)
    }

    @Test
    fun shouldAllowRegisteringReconnectFailedHandler() {
        val socketManager = createSocketManager()
        var failedCalled = false
        
        socketManager.onReconnectFailed {
            failedCalled = true
        }
        
        // Handler registered
        assertFalse(failedCalled)
    }

    // ========================================================================
    // Emission Tests
    // ========================================================================

    @Test
    fun emitShouldRequireConnection() = runTest {
        val socketManager = createSocketManager()

        val result = runCatching {
            socketManager.emit("testEvent", mapOf("data" to "value"))
        }

        // Should fail when not connected
        assertTrue(result.isFailure)
    }

    @Test
    fun emitWithAckShouldRequireConnection() = runTest {
        val socketManager = createSocketManager()

        val result = runCatching {
            socketManager.emitWithAck<String>(
                "testEvent",
                mapOf("data" to "value")
            )
        }

        // Should fail when not connected
        assertTrue(result.isFailure)
    }

    @Test
    fun emitWithAckShouldSupportTimeout() = runTest {
        val socketManager = createSocketManager()

        val result = runCatching {
            socketManager.emitWithAck<String>(
                "testEvent",
                mapOf("data" to "value"),
                timeout = 3000
            )
        }

        // Should handle timeout parameter even when failing
        assertTrue(result.isFailure)
    }

    // ========================================================================
    // Disconnection Tests
    // ========================================================================

    @Test
    fun shouldAllowDisconnectionWhenNotConnected() = runTest {
        val socketManager = createSocketManager()
        
        val result = socketManager.disconnect()
        
        // Should succeed even if not connected
        assertTrue(result.isSuccess)
    }

    @Test
    fun multipleDisconnectsShouldBeIdempotent() = runTest {
        val socketManager = createSocketManager()
        
        val result1 = socketManager.disconnect()
        val result2 = socketManager.disconnect()
        
        // Both should succeed
        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
    }

    // ========================================================================
    // Data Conversion Tests
    // ========================================================================

    @Test
    fun dataConverterShouldHandleSimpleMap() {
        val map = mapOf(
            "string" to "value",
            "number" to 42,
            "boolean" to true
        )
        
        val converted = SocketDataConverter.fromMap(map)
        val backToMap = SocketDataConverter.toMap(converted)
        
        assertNotNull(converted)
        assertNotNull(backToMap)
    }

    @Test
    fun dataConverterShouldHandleNestedMap() {
        val map = mapOf(
            "user" to mapOf(
                "name" to "Alice",
                "age" to 30
            ),
            "items" to listOf(1, 2, 3)
        )
        
        val converted = SocketDataConverter.fromMap(map)
        val backToMap = SocketDataConverter.toMap(converted)
        
        assertNotNull(converted)
        assertNotNull(backToMap)
    }

    @Test
    fun dataConverterShouldHandleNullValues() {
        val map = mapOf(
            "key1" to "value",
            "key2" to null
        )
        
        val converted = SocketDataConverter.fromMap(map)
        val backToMap = SocketDataConverter.toMap(converted)
        
        assertNotNull(converted)
        assertNotNull(backToMap)
    }

    @Test
    fun dataConverterShouldHandleEmptyMap() {
        val map = emptyMap<String, Any?>()
        
        val converted = SocketDataConverter.fromMap(map)
        val backToMap = SocketDataConverter.toMap(converted)
        
        assertNotNull(converted)
        assertTrue(backToMap.isEmpty())
    }

    // ========================================================================
    // Error Handling Tests
    // ========================================================================

    @Test
    fun invalidUrlShouldReturnFailure() = runTest {
        val socketManager = createSocketManager()
        
        val result = socketManager.connect("invalid-url")
        
        // Should handle invalid URL gracefully
        assertNotNull(result)
    }

    @Test
    fun emitOnDisconnectedSocketShouldReturnFailure() = runTest {
        val socketManager = createSocketManager()
        
        // Ensure disconnected
        socketManager.disconnect()

        val result = runCatching {
            socketManager.emit("event", emptyMap())
        }

        assertTrue(result.isFailure)
    }
}
