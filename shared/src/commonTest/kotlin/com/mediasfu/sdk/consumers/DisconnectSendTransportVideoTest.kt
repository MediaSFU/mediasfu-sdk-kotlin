// DisconnectSendTransportVideoTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.testutil.TestDisconnectSendTransportVideoParameters
import com.mediasfu.sdk.testutil.TestSocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Test suite for DisconnectSendTransportVideo functionality.
 */
class DisconnectSendTransportVideoTest {

    private suspend fun connectSocket(socket: TestSocketManager) {
        socket.connect("wss://test.mediasfu.example", SocketConfig()).getOrThrow()
    }

    @Test
    fun disconnectSendTransportVideo_whenVideoProducerExists_shouldPauseProducer() = runTest {
        val remoteSocket = TestSocketManager()
        val localSocket = TestSocketManager()
        connectSocket(remoteSocket)
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportVideoParameters(
            initialSocket = remoteSocket,
            initialLocalSocket = localSocket
        )

        val options = DisconnectSendTransportVideoOptions(parameters = parameters)
        val result = disconnectSendTransportVideo(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.videoProducer)
        assertEquals(null, parameters.localVideoProducer)
        assertTrue(remoteSocket.emitCalls.any { it.first == "pauseProducerMedia" })
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectSendTransportVideo_whenNoVideoProducer_shouldSucceed() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportVideoParameters(
            initialVideoProducer = null,
            initialSocket = remoteSocket,
            initialLocalVideoProducer = null,
            initialLocalSocket = null
        )

        val options = DisconnectSendTransportVideoOptions(parameters = parameters)
        val result = disconnectSendTransportVideo(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportVideo_whenLevel2AndNotAudioOn_shouldUpdateMainWindow() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportVideoParameters(
            initialSocket = remoteSocket,
            islevel = "2",
            audioAlreadyOn = false,
            lockScreen = false,
            shared = false
        )

        val options = DisconnectSendTransportVideoOptions(parameters = parameters)
        val result = disconnectSendTransportVideo(options)
        
        assertTrue(result.isSuccess)
        assertTrue(parameters.prepopulateCalls.isNotEmpty())
        assertTrue(parameters.updateMainWindowUpdates.contains(true))
    }
    
    @Test
    fun disconnectLocalSendTransportVideo_whenLocalProducerExists_shouldPause() = runTest {
        val localSocket = TestSocketManager()
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportVideoParameters(
            initialSocket = null,
            initialLocalSocket = localSocket
        )

        val options = DisconnectSendTransportVideoOptions(parameters = parameters)
        val result = disconnectLocalSendTransportVideo(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.localVideoProducer)
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectLocalSendTransportVideo_whenNoLocalSocket_shouldSucceed() = runTest {
        val parameters = TestDisconnectSendTransportVideoParameters(
            initialLocalSocket = null
        )

        val options = DisconnectSendTransportVideoOptions(parameters = parameters)
        val result = disconnectLocalSendTransportVideo(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportVideoException_shouldCreateCorrectly() {
        val exception = DisconnectSendTransportVideoException("Test error")
        assertEquals("Test error", exception.message)
    }
}
