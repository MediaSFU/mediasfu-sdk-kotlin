// DisconnectSendTransportAudioTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.testutil.TestDisconnectSendTransportAudioParameters
import com.mediasfu.sdk.testutil.TestSocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Test suite for DisconnectSendTransportAudio functionality.
 */
class DisconnectSendTransportAudioTest {

    private suspend fun connectSocket(socket: TestSocketManager) {
        socket.connect("wss://test.mediasfu.example", SocketConfig()).getOrThrow()
    }

    @Test
    fun disconnectSendTransportAudio_whenAudioProducerExists_shouldPauseProducer() = runTest {
        val remoteSocket = TestSocketManager()
        val localSocket = TestSocketManager()
        connectSocket(remoteSocket)
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportAudioParameters(
            initialSocket = remoteSocket,
            initialLocalSocket = localSocket
        )

        val options = DisconnectSendTransportAudioOptions(parameters = parameters)
        val result = disconnectSendTransportAudio(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.audioProducer)
        assertEquals(null, parameters.localAudioProducer)
        assertTrue(remoteSocket.emitCalls.any { it.first == "pauseProducerMedia" })
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectSendTransportAudio_whenNoAudioProducer_shouldSucceed() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportAudioParameters(
            initialAudioProducer = null,
            initialSocket = remoteSocket,
            initialLocalAudioProducer = null,
            initialLocalSocket = null
        )
        
        val options = DisconnectSendTransportAudioOptions(parameters = parameters)
        val result = disconnectSendTransportAudio(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportAudio_whenLevel2AndNotVideoOn_shouldUpdateMainWindow() = runTest {
        val remoteSocket = TestSocketManager()
        connectSocket(remoteSocket)

        val parameters = TestDisconnectSendTransportAudioParameters(
            initialSocket = remoteSocket,
            islevel = "2",
            videoAlreadyOn = false,
            lockScreen = false,
            shared = false
        )
        
        val options = DisconnectSendTransportAudioOptions(parameters = parameters)
        val result = disconnectSendTransportAudio(options)
        
        assertTrue(result.isSuccess)
        assertTrue(parameters.prepopulateCalls.isNotEmpty())
        assertTrue(parameters.updateMainWindowUpdates.contains(true))
    }
    
    @Test
    fun disconnectLocalSendTransportAudio_whenLocalProducerExists_shouldPause() = runTest {
        val localSocket = TestSocketManager()
        connectSocket(localSocket)

        val parameters = TestDisconnectSendTransportAudioParameters(
            initialSocket = null,
            initialLocalSocket = localSocket
        )
        
        val options = DisconnectSendTransportAudioOptions(parameters = parameters)
        val result = disconnectLocalSendTransportAudio(options)
        
        assertTrue(result.isSuccess)
        assertEquals(null, parameters.localAudioProducer)
        assertTrue(localSocket.emitCalls.any { it.first == "pauseProducerMedia" })
    }
    
    @Test
    fun disconnectLocalSendTransportAudio_whenNoLocalSocket_shouldSucceed() = runTest {
        val parameters = TestDisconnectSendTransportAudioParameters(
            initialLocalSocket = null
        )
        
        val options = DisconnectSendTransportAudioOptions(parameters = parameters)
        val result = disconnectLocalSendTransportAudio(options)
        
        assertTrue(result.isSuccess)
    }
    
    @Test
    fun disconnectSendTransportAudioException_shouldCreateCorrectly() {
        val exception = DisconnectSendTransportAudioException("Test error")
        assertEquals("Test error", exception.message)
    }
}
