// DisconnectSendTransportScreenTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.testutil.TestDisconnectSendTransportScreenParameters
import com.mediasfu.sdk.testutil.TestSocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Test suite for DisconnectSendTransportScreen functionality.
 */
class DisconnectSendTransportScreenTest {

    private suspend fun connectSocket(socket: TestSocketManager) {
        socket.connect("wss://test.mediasfu.example", SocketConfig()).getOrThrow()
    }

    @Test
    fun disconnectSendTransportScreen_whenScreenProducerExists_shouldCloseProducer() = runTest {
        val remoteSocket = TestSocketManager()
        val localSocket = TestSocketManager()
        connectSocket(remoteSocket)
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportScreenParameters(
            initialSocket = remoteSocket,
            initialLocalSocket = localSocket
        )

        val options = DisconnectSendTransportScreenOptions(parameters = parameters)
        val result = disconnectSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.screenProducer)
        assertEquals(null, parameters.localScreenProducer)
        assertTrue(remoteSocket.emitCalls.any { it.first == "closeScreenProducer" })
        assertTrue(remoteSocket.emitCalls.any { it.first == "pauseProducerMedia" })
        assertTrue(localSocket.emitCalls.any { it.first == "closeScreenProducer" })
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectSendTransportScreen_whenNoScreenProducer_shouldSucceed() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportScreenParameters(
            initialScreenProducer = null,
            initialSocket = remoteSocket
        )

        val options = DisconnectSendTransportScreenOptions(parameters = parameters)
        val result = disconnectSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportScreen_shouldEmitCloseScreenProducer() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportScreenParameters(
            initialSocket = remoteSocket,
            roomName = "testRoom"
        )

        val options = DisconnectSendTransportScreenOptions(parameters = parameters)
        val result = disconnectSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
        assertTrue(remoteSocket.emitCalls.any { it.first == "closeScreenProducer" })
    }
    
    @Test
    fun disconnectLocalSendTransportScreen_whenLocalProducerExists_shouldClose() = runTest {
        val localSocket = TestSocketManager()
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportScreenParameters(
            initialSocket = null,
            initialLocalSocket = localSocket
        )

        val options = DisconnectSendTransportScreenOptions(parameters = parameters)
        val result = disconnectLocalSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.localScreenProducer)
        assertTrue(localSocket.emitCalls.any { it.first == "closeScreenProducer" })
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectLocalSendTransportScreen_whenNoLocalSocket_shouldSucceed() = runTest {
        val parameters = TestDisconnectSendTransportScreenParameters(
            initialLocalSocket = null
        )

        val options = DisconnectSendTransportScreenOptions(parameters = parameters)
        val result = disconnectLocalSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportScreenException_shouldCreateCorrectly() {
        val exception = DisconnectSendTransportScreenException("Test error")
        assertEquals("Test error", exception.message)
    }
    
    @Test
    fun disconnectSendTransportScreenException_shouldCreateWithCause() {
        val cause = RuntimeException("Root cause")
        val exception = DisconnectSendTransportScreenException("Test error", cause)
        
        assertEquals("Test error", exception.message)
        assertEquals(cause, exception.cause)
    }
}
