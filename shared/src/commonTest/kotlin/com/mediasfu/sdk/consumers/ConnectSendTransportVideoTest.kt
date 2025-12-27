// ConnectSendTransportVideoTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.testutil.TestConnectSendTransportVideoParameters
import com.mediasfu.sdk.testutil.TestMediaStream
import com.mediasfu.sdk.testutil.TestMediaStreamTrack
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Test suite for ConnectSendTransportVideo functionality.
 * 
 * Tests cover:
 * - Video transport connection
 * - Local transport connection
 * - Video stream management
 * - State updates
 * - Error handling
 */
class ConnectSendTransportVideoTest {

    @Test
    fun testConnectSendTransportVideoSuccess() = runTest {
        val parameters = TestConnectSendTransportVideoParameters()
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        val videoParams = ProducerOptionsType(
            stream = stream,
            track = stream.getVideoTracks().firstOrNull()
        )
        val options = ConnectSendTransportVideoOptions(
            videoParams = videoParams,
            parameters = parameters,
            videoConstraints = mapOf("video" to true),
            targetOption = "all"
        )
        
        val result = connectSendTransportVideo(options)
        assertTrue(result.isSuccess)
        assertTrue(parameters.producerTransportUpdates.isNotEmpty())
        assertTrue(parameters.localProducerTransportUpdates.isNotEmpty())
        assertTrue(parameters.localStreamVideoUpdates.contains(stream))
        assertFalse(parameters.transportCreatedUpdates.contains(false))
    }
    
    @Test
    fun testConnectSendTransportVideoWithNullTransport() = runTest {
        val parameters = TestConnectSendTransportVideoParameters(
            initialProducerTransport = null
        )
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        val options = ConnectSendTransportVideoOptions(
            videoParams = ProducerOptionsType(stream = stream),
            parameters = parameters,
            targetOption = "remote"
        )
        
        val result = connectSendTransportVideo(options)
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ConnectSendTransportVideoException)
    }
    
    @Test
    fun testConnectLocalSendTransportVideoSuccess() = runTest {
        val parameters = TestConnectSendTransportVideoParameters()
        val stream = TestMediaStream(
            videoTracks = listOf(TestMediaStreamTrack(kind = "video"))
        )
        val track = stream.getVideoTracks().first()

        val result = connectLocalSendTransportVideo(
            stream = stream,
            track = track,
            encodings = emptyList(),
            codecOptions = ProducerCodecOptions(),
            parameters = parameters
        )
        
        assertTrue(result.isSuccess)
        assertTrue(parameters.localStreamVideoUpdates.contains(stream))
    }
    
    @Test
    fun testConnectSendTransportVideoOptions() {
        val parameters = TestConnectSendTransportVideoParameters()
        val options = ConnectSendTransportVideoOptions(
            videoParams = ProducerOptionsType(encodings = listOf(RtpEncodingParameters(rid = "r0"))),
            parameters = parameters,
            videoConstraints = mapOf("video" to true),
            targetOption = "all"
        )
        
        assertEquals(listOf(RtpEncodingParameters(rid = "r0")), options.videoParams?.encodings)
        assertEquals(parameters, options.parameters)
        assertEquals(mapOf("video" to true), options.videoConstraints)
        assertEquals("all", options.targetOption)
    }
    
    @Test
    fun testConnectSendTransportVideoException() {
        val exception = ConnectSendTransportVideoException("Test error")
        assertEquals("Test error", exception.message)
        
        val exceptionWithCause = ConnectSendTransportVideoException("Test error", exception)
        assertEquals("Test error", exceptionWithCause.message)
        assertEquals(exception, exceptionWithCause.cause)
    }
}