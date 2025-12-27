// ConnectSendTransportScreenTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.testutil.TestConnectSendTransportScreenParameters
import com.mediasfu.sdk.testutil.TestMediaStream
import com.mediasfu.sdk.testutil.TestMediaStreamTrack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Test suite for ConnectSendTransportScreen functionality.
 * 
 * Tests cover:
 * - Screen transport connection
 * - Local transport connection
 * - Screen stream management
 * - State updates
 * - Error handling
 */
class ConnectSendTransportScreenTest {

    @Test
    fun testConnectSendTransportScreenSuccess() = runTest {
        val parameters = TestConnectSendTransportScreenParameters()
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        val options = ConnectSendTransportScreenOptions(
            stream = stream,
            parameters = parameters,
            targetOption = "all"
        )
        
        val result = connectSendTransportScreen(options)
        
        assertTrue(result.isSuccess)
        assertTrue(parameters.producerTransportUpdates.isNotEmpty())
        assertTrue(parameters.localStreamScreenUpdates.contains(stream))
    }
    
    @Test
    fun testConnectSendTransportScreenWithNullTransport() = runTest {
        val parameters = TestConnectSendTransportScreenParameters(
            initialProducerTransport = null
        )
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        val options = ConnectSendTransportScreenOptions(
            stream = stream,
            parameters = parameters,
            targetOption = "remote"
        )
        
        val result = connectSendTransportScreen(options)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ConnectSendTransportScreenException)
    }
    
    @Test
    fun testConnectLocalSendTransportScreenSuccess() = runTest {
        val parameters = TestConnectSendTransportScreenParameters()
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        
        val result = connectLocalSendTransportScreen(
            stream = stream,
            parameters = parameters
        )
        
        assertTrue(result.isSuccess)
        assertTrue(parameters.localStreamScreenUpdates.contains(stream))
    }
    
    @Test
    fun testConnectSendTransportScreenOptions() {
        val parameters = TestConnectSendTransportScreenParameters()
        val stream = TestMediaStream()
        val options = ConnectSendTransportScreenOptions(
            stream = stream,
            parameters = parameters,
            targetOption = "all"
        )
        
        assertEquals(stream, options.stream)
        assertEquals(parameters, options.parameters)
        assertEquals("all", options.targetOption)
    }
    
    @Test
    fun testConnectSendTransportScreenException() {
        val exception = ConnectSendTransportScreenException("Test error")
        assertEquals("Test error", exception.message)
        
        val exceptionWithCause = ConnectSendTransportScreenException("Test error", exception)
        assertEquals("Test error", exceptionWithCause.message)
        assertEquals(exception, exceptionWithCause.cause)
    }
}