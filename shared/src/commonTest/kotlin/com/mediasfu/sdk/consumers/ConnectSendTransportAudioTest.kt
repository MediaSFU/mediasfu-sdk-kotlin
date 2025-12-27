// ConnectSendTransportAudioTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.testutil.TestConnectSendTransportAudioParameters
import com.mediasfu.sdk.testutil.TestMediaStream
import com.mediasfu.sdk.testutil.TestMediaStreamTrack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

/**
 * Test suite for ConnectSendTransportAudio functionality.
 * 
 * Tests cover:
 * - Audio transport connection
 * - Local transport connection
 * - Audio level monitoring
 * - State updates
 * - Error handling
 */
class ConnectSendTransportAudioTest {

    @Test
    fun testConnectSendTransportAudioSuccess() = runTest {
        val parameters = TestConnectSendTransportAudioParameters()
        val stream = TestMediaStream(
            audioTracks = listOf(TestMediaStreamTrack(kind = "audio"))
        )
        val options = ConnectSendTransportAudioOptions(
            stream = stream,
            parameters = parameters,
            audioConstraints = mapOf("audio" to true),
            targetOption = "all"
        )
        
        val result = connectSendTransportAudio(options)
        assertTrue(result.isSuccess)
        assertTrue(parameters.transportCreatedAudio)
        assertFalse(parameters.micAction)
        assertTrue(parameters.audioAlreadyOnUpdates.contains(true))
    }
    
    @Test
    fun testConnectSendTransportAudioWithNullTransport() = runTest {
        val parameters = TestConnectSendTransportAudioParameters(
            initialProducerTransport = null
        )
        val stream = TestMediaStream(
            audioTracks = listOf(TestMediaStreamTrack(kind = "audio"))
        )
        val options = ConnectSendTransportAudioOptions(
            stream = stream,
            parameters = parameters,
            targetOption = "remote"
        )
        
        val result = connectSendTransportAudio(options)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ConnectSendTransportAudioException)
    }
    
    @Test
    fun testConnectLocalSendTransportAudioSuccess() = runTest {
        val parameters = TestConnectSendTransportAudioParameters()
        val stream = TestMediaStream(
            audioTracks = listOf(TestMediaStreamTrack(kind = "audio"))
        )
        val options = ConnectSendTransportAudioOptions(
            stream = stream,
            parameters = parameters,
            targetOption = "local"
        )
        
        val result = connectLocalSendTransportAudio(options)
        assertTrue(result.isSuccess)
        assertEquals(stream, parameters.currentLocalStreamAudio)
    }
    
    @Test
    fun testUpdateMicLevel() {
        // Test that updateMicLevel doesn't crash with null producer
        updateMicLevel(null) { level ->
            // No-op; ensure callback can be invoked without crashing
        }
        
        // The function should start a coroutine, so we can't easily test the level updates
        // But we can verify it doesn't crash
        assertTrue(true) // Test passes if no exceptions
    }
    
    @Test
    fun testConnectSendTransportAudioOptions() {
        val parameters = TestConnectSendTransportAudioParameters()
        val stream = TestMediaStream()
        val options = ConnectSendTransportAudioOptions(
            stream = stream,
            parameters = parameters,
            audioConstraints = mapOf("audio" to true),
            targetOption = "all"
        )
        
        assertEquals(stream, options.stream)
        assertEquals(parameters, options.parameters)
        assertEquals(mapOf("audio" to true), options.audioConstraints)
        assertEquals("all", options.targetOption)
    }
    
    @Test
    fun testConnectSendTransportAudioException() {
        val exception = ConnectSendTransportAudioException("Test error")
        assertEquals("Test error", exception.message)
        
        val exceptionWithCause = ConnectSendTransportAudioException("Test error", exception)
        assertEquals("Test error", exceptionWithCause.message)
        assertEquals(exception, exceptionWithCause.cause)
    }
}