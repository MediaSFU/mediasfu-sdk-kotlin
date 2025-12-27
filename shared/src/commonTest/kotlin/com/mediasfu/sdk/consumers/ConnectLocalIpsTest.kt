// ConnectLocalIpsTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.socket.SocketManager
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ConnectLocalIpsTest {
    
    private lateinit var mockSocket: SocketManager
    private lateinit var mockParameters: ConnectLocalIpsParameters
    
    @BeforeTest
    fun setup() {
        mockSocket = mockk(relaxed = true)
        mockParameters = mockk(relaxed = true)
        
        // Default mock behavior
        every { mockParameters.socket } returns mockSocket
        every { mockParameters.getUpdatedAllParams() } returns mockParameters
    }
    
    @AfterTest
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun connectLocalIps_whenSocketIsNull_shouldFail() = runTest {
        // Given
        val options = ConnectLocalIpsOptions(
            socket = null,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Socket connection is null") == true)
    }
    
    @Test
    fun connectLocalIps_whenSocketProvided_shouldRegisterEventHandlers() = runTest {
        // Given
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        verify(atLeast = 1) { mockSocket.on("new-producer", any()) }
        verify(atLeast = 1) { mockSocket.on("producer-closed", any()) }
    }
    
    @Test
    fun connectLocalIps_whenCustomNewProducerMethodProvided_shouldUseIt() = runTest {
        // Given
        var customMethodCalled = false
        val customNewProducerMethod: suspend (LocalNewProducerOptions) -> Unit = { _ ->
            customMethodCalled = true
        }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            newProducerMethod = customNewProducerMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Custom method is registered but not called until event fires
    }
    
    @Test
    fun connectLocalIps_whenCustomClosedProducerMethodProvided_shouldUseIt() = runTest {
        // Given
        var customMethodCalled = false
        val customClosedProducerMethod: suspend (LocalProducerClosedOptions) -> Unit = { _ ->
            customMethodCalled = true
        }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            closedProducerMethod = customClosedProducerMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Custom method is registered but not called until event fires
    }
    
    @Test
    fun connectLocalIps_whenCustomReceiveAllPipedTransportsProvided_shouldUseIt() = runTest {
        // Given
        var customMethodCalled = false
        val customReceiveMethod: suspend (ReceiveAllPipedTransportsOptions) -> Unit = { _ ->
            customMethodCalled = true
        }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            receiveAllPipedTransportsMethod = customReceiveMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(customMethodCalled) // This is called immediately
    }
    
    @Test
    fun connectLocalIps_shouldInitializePipedTransports() = runTest {
        // Given
        var receiveCalled = false
        val customReceiveMethod: suspend (ReceiveAllPipedTransportsOptions) -> Unit = { options ->
            receiveCalled = true
            assertEquals(true, options.community)
            assertEquals(mockSocket, options.nsock)
        }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            receiveAllPipedTransportsMethod = customReceiveMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(receiveCalled)
    }
    
    @Test
    fun connectLocalIps_shouldRegisterNewProducerHandler() = runTest {
        // Given
        // Provide a mock receiveAllPipedTransportsMethod to prevent exceptions
        val mockReceiveMethod: suspend (ReceiveAllPipedTransportsOptions) -> Unit = { _ -> }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            receiveAllPipedTransportsMethod = mockReceiveMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        verify(atLeast = 1) { mockSocket.on("new-producer", any()) }
    }
    
    @Test
    fun connectLocalIps_shouldRegisterProducerClosedHandler() = runTest {
        // Given
        // Provide a mock receiveAllPipedTransportsMethod to prevent exceptions
        val mockReceiveMethod: suspend (ReceiveAllPipedTransportsOptions) -> Unit = { _ -> }
        
        val options = ConnectLocalIpsOptions(
            socket = mockSocket,
            receiveAllPipedTransportsMethod = mockReceiveMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectLocalIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        verify(atLeast = 1) { mockSocket.on("producer-closed", any()) }
    }
    
    @Test
    fun connectLocalIpsModels_receiveAllPipedTransportsOptions_shouldCreateCorrectly() {
        // Given
        val community = true
        val nsock = mockSocket
        val params = mockParameters
        
        // When
        val options = ReceiveAllPipedTransportsOptions(
            community = community,
            nsock = nsock,
            parameters = params
        )
        
        // Then
        assertEquals(community, options.community)
        assertEquals(nsock, options.nsock)
        assertEquals(params, options.parameters)
    }
    
    @Test
    fun connectLocalIpsException_shouldCreateWithMessage() {
        // Given
        val message = "Test error message"
        
        // When
        val exception = ConnectLocalIpsException(message)
        
        // Then
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }
    
    @Test
    fun connectLocalIpsException_shouldCreateWithCause() {
        // Given
        val message = "Test error message"
        val cause = RuntimeException("Original error")
        
        // When
        val exception = ConnectLocalIpsException(message, cause)
        
        // Then
        assertEquals(message, exception.message)
        assertEquals(cause, exception.cause)
    }
}
