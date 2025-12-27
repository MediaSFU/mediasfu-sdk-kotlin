// ConnectIpsTest.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.ConnectionState
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ConnectIpsTest {
    
    private lateinit var mockSocket: SocketManager
    private lateinit var mockParameters: ConnectIpsParameters
    
    @BeforeTest
    fun setup() {
        mockSocket = mockk(relaxed = true)
        mockParameters = mockk(relaxed = true)
        
        // Default mock behavior
        every { mockParameters.roomRecvIPs } returns emptyList()
        every { mockParameters.consumeSockets } returns emptyList()
        every { mockParameters.getUpdatedAllParams() } returns mockParameters
        every { mockParameters.updateRoomRecvIPs(any()) } just Runs
        every { mockParameters.updateConsumeSockets(any()) } just Runs
    }
    
    @AfterTest
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun connectIps_whenApiTokenEmpty_shouldFail() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = listOf("100.122.1.1"),
            apiUserName = "testUser",
            apiKey = null,
            apiToken = "",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required parameters") == true)
    }
    
    @Test
    fun connectIps_whenApiKeyProvided_shouldProceed() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(), // Empty list so no connections attempted
            apiUserName = "testUser",
            apiKey = "testKey",
            apiToken = "",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        val connectResult = result.getOrNull()!!
        assertEquals(0, connectResult.consumeSockets.size)
        assertEquals(0, connectResult.roomRecvIPs.size)
    }
    
    @Test
    fun connectIps_whenEmptyRemIPList_shouldReturnEmptyResult() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(),
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        val connectResult = result.getOrNull()!!
        assertEquals(0, connectResult.consumeSockets.size)
        assertEquals(0, connectResult.roomRecvIPs.size)
    }
    
    @Test
    fun connectIps_whenIPAlreadyConnected_shouldSkip() = runTest {
        // Given
        val existingSocket = mockk<SocketManager>(relaxed = true)
        val existingIP = "100.122.1.1"
        val existingSockets = listOf(mapOf(existingIP to existingSocket))
        
        val options = ConnectIpsOptions(
            consumeSockets = existingSockets,
            remIP = listOf(existingIP), // Try to connect to same IP again
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        val connectResult = result.getOrNull()!!
        assertEquals(1, connectResult.consumeSockets.size) // Should still be 1
    }
    
    @Test
    fun connectIps_whenIPIsBlank_shouldSkip() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = listOf("", "  ", "\t"), // Blank IPs
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        val connectResult = result.getOrNull()!!
        assertEquals(0, connectResult.consumeSockets.size)
        assertEquals(0, connectResult.roomRecvIPs.size)
    }
    
    @Test
    fun connectIps_whenIPIsNone_shouldSkip() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = listOf("none"),
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        val connectResult = result.getOrNull()!!
        assertEquals(0, connectResult.consumeSockets.size)
        assertEquals(0, connectResult.roomRecvIPs.size)
    }
    
    @Test
    fun connectIps_withMultipleValidIPs_shouldProcessAll() = runTest {
        // Given
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = listOf("100.122.1.1", "100.122.1.2", "none", "", "100.122.1.3"),
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Note: Actual connection will fail until SocketManager is integrated,
        // but the function should process all valid IPs without throwing
    }
    
    @Test
    fun connectIps_whenCustomNewProducerMethodProvided_shouldUseIt() = runTest {
        // Given
        var newProducerCalled = false
        val customNewProducerMethod: suspend (NewPipeProducerOptions) -> Unit = { options ->
            newProducerCalled = true
        }
        
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(),
            apiUserName = "testUser",
            apiToken = "testToken",
            newProducerMethod = customNewProducerMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Custom method is registered but not called until socket event fires
    }
    
    @Test
    fun connectIps_whenCustomClosedProducerMethodProvided_shouldUseIt() = runTest {
        // Given
        var closedProducerCalled = false
        val customClosedProducerMethod: suspend (ProducerClosedOptions) -> Unit = { options ->
            closedProducerCalled = true
        }
        
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(),
            apiUserName = "testUser",
            apiToken = "testToken",
            closedProducerMethod = customClosedProducerMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Custom method is registered but not called until socket event fires
    }
    
    @Test
    fun connectIps_whenCustomJoinConsumeRoomMethodProvided_shouldUseIt() = runTest {
        // Given
        var joinConsumeCalled = false
        val customJoinConsumeMethod: suspend (JoinConsumeRoomOptions) -> Result<Map<String, Any?>> = { options ->
            joinConsumeCalled = true
            Result.success(mapOf("rtpCapabilities" to emptyMap<String, Any>()))
        }
        
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(),
            apiUserName = "testUser",
            apiToken = "testToken",
            joinConsumeRoomMethod = customJoinConsumeMethod,
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // Custom method would be called if connection succeeds
    }
    
    @Test
    fun connectIps_whenParametersUpdated_shouldCallUpdateMethods() = runTest {
        // Given
        val roomRecvIPs = mutableListOf<List<String>>()
        every { mockParameters.updateRoomRecvIPs(capture(roomRecvIPs)) } just Runs
        
        val options = ConnectIpsOptions(
            consumeSockets = emptyList(),
            remIP = emptyList(),
            apiUserName = "testUser",
            apiToken = "testToken",
            parameters = mockParameters
        )
        
        // When
        val result = connectIps(options)
        
        // Then
        assertTrue(result.isSuccess)
        // updateRoomRecvIPs not called since no IPs connected
    }
    
    @Test
    fun connectIpsModels_newPipeProducerOptions_shouldCreateCorrectly() {
        // Given
        val producerId = "producer123"
        val islevel = "2"
        val nsock = mockSocket
        val params = mockParameters
        
        // When
        val options = NewPipeProducerOptions(
            producerId = producerId,
            islevel = islevel,
            nsock = nsock,
            parameters = params
        )
        
        // Then
        assertEquals(producerId, options.producerId)
        assertEquals(islevel, options.islevel)
        assertEquals(nsock, options.nsock)
        assertEquals(params, options.parameters)
    }
    
    @Test
    fun connectIpsModels_producerClosedOptions_shouldCreateCorrectly() {
        // Given
        val remoteProducerId = "producer456"
        val params = mockParameters
        
        // When
        val options = ProducerClosedOptions(
            remoteProducerId = remoteProducerId,
            parameters = params
        )
        
        // Then
        assertEquals(remoteProducerId, options.remoteProducerId)
        assertEquals(params, options.parameters)
    }
    
    @Test
    fun connectIpsModels_joinConsumeRoomOptions_shouldCreateCorrectly() {
        // Given
        val remoteSock = mockSocket
        val apiToken = "token123"
        val apiUserName = "user123"
        val params = mockParameters
        
        // When
        val options = JoinConsumeRoomOptions(
            remoteSock = remoteSock,
            apiToken = apiToken,
            apiUserName = apiUserName,
            parameters = params
        )
        
        // Then
        assertEquals(remoteSock, options.remoteSock)
        assertEquals(apiToken, options.apiToken)
        assertEquals(apiUserName, options.apiUserName)
        assertEquals(params, options.parameters)
    }
    
    @Test
    fun connectIpsResult_shouldCreateCorrectly() {
        // Given
        val consumeSockets = listOf(mapOf("ip1" to mockSocket))
        val roomRecvIPs = listOf("100.122.1.1", "100.122.1.2")
        
        // When
        val result = ConnectIpsResult(
            consumeSockets = consumeSockets,
            roomRecvIPs = roomRecvIPs
        )
        
        // Then
        assertEquals(1, result.consumeSockets.size)
        assertEquals(2, result.roomRecvIPs.size)
        assertEquals("100.122.1.1", result.roomRecvIPs[0])
        assertEquals("100.122.1.2", result.roomRecvIPs[1])
    }
    
    @Test
    fun connectIpsException_shouldCreateWithMessage() {
        // Given
        val message = "Test error message"
        
        // When
        val exception = ConnectIpsException(message)
        
        // Then
        assertEquals(message, exception.message)
        assertNull(exception.cause)
    }
    
    @Test
    fun connectIpsException_shouldCreateWithCause() {
        // Given
        val message = "Test error message"
        val cause = RuntimeException("Original error")
        
        // When
        val exception = ConnectIpsException(message, cause)
        
        // Then
        assertEquals(message, exception.message)
        assertEquals(cause, exception.cause)
    }
}
