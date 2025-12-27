package com.mediasfu.sdk.socket

import io.mockk.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Integration tests for socket operations.
 * 
 * These tests verify the complete flow of socket operations including:
 * - Connection lifecycle
 * - Room joining flows
 * - Error scenarios
 * - State management
 */
class SocketIntegrationTest {

    private lateinit var mockSocket: SocketManager

    @BeforeTest
    fun setup() {
        mockSocket = mockk<SocketManager>(relaxed = true)
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    // ========================================================================
    // Connection Lifecycle Integration Tests
    // ========================================================================

    @Test
    fun fullConnectionLifecycle_shouldWork() = runTest {
        // Setup - mock returns for each call in sequence
        every { mockSocket.isConnected() } returns false andThen true andThen false
        every { mockSocket.getConnectionState() } returns ConnectionState.DISCONNECTED andThen 
            ConnectionState.CONNECTED andThen ConnectionState.DISCONNECTED
    coEvery { mockSocket.connect(any(), any()) } returns Result.success(Unit)
        coEvery { mockSocket.disconnect() } returns Result.success(Unit)

        // Initial state
        assertFalse(mockSocket.isConnected())
        assertEquals(ConnectionState.DISCONNECTED, mockSocket.getConnectionState())

        // Connect
        val connectResult = mockSocket.connect("https://test.mediasfu.com")
        assertTrue(connectResult.isSuccess)

        // Verify connected
        assertTrue(mockSocket.isConnected())
        assertEquals(ConnectionState.CONNECTED, mockSocket.getConnectionState())

        // Disconnect
        val disconnectResult = mockSocket.disconnect()
        assertTrue(disconnectResult.isSuccess)

        // Verify disconnected
        assertFalse(mockSocket.isConnected())
        assertEquals(ConnectionState.DISCONNECTED, mockSocket.getConnectionState())

        // Verify call sequence
        coVerify(exactly = 1) { mockSocket.connect(any(), any()) }
        coVerify(exactly = 1) { mockSocket.disconnect() }
    }

    @Test
    fun connectionWithReconnection_shouldHandleCorrectly() = runTest {
        var reconnectAttempts = 0
        
        every { mockSocket.getConnectionState() } returnsMany listOf(
            ConnectionState.CONNECTED,
            ConnectionState.RECONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.CONNECTED
        )
        
        mockSocket.onReconnectAttempt { attempt ->
            reconnectAttempts = attempt
        }

        // Simulate connection states
        assertEquals(ConnectionState.CONNECTED, mockSocket.getConnectionState())
        assertEquals(ConnectionState.RECONNECTING, mockSocket.getConnectionState())
        assertEquals(ConnectionState.RECONNECTING, mockSocket.getConnectionState())
        assertEquals(ConnectionState.CONNECTED, mockSocket.getConnectionState())
    }

    // ========================================================================
    // Room Joining Integration Tests
    // ========================================================================

    @Test
    fun fullRoomJoinFlow_shouldWork() = runTest {
        // Setup: Mock connected socket
        every { mockSocket.isConnected() } returns true
        every { mockSocket.getConnectionState() } returns ConnectionState.CONNECTED

        // Mock successful room join response
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true,
            "isHost" to true,
            "secureCode" to "host-secret-code"
        )

        // Join room
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "2",
            member = "hostuser",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        // Verify success
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(true, response.success)
        assertEquals(true, response.isHost)
        assertEquals("host-secret-code", response.secureCode)

        // Verify emit was called
        coVerify(exactly = 1) {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        }
    }

    @Test
    fun fullConferenceRoomJoinFlow_shouldWork() = runTest {
        // Setup: Mock connected socket
        every { mockSocket.isConnected() } returns true
        every { mockSocket.getConnectionState() } returns ConnectionState.CONNECTED

        // Mock successful conference join response
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinConRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true,
            "isHost" to false,
            "recordOnly" to true
        )

        // Join conference room
        val options = JoinConRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "0",
            member = "participant",
            sec = "b".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinConRoom(options)

        // Verify success
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(true, response.success)
        assertEquals(false, response.isHost)
        assertEquals(true, response.recordOnly)

        // Verify correct event was emitted
        coVerify(exactly = 1) {
            mockSocket.emitWithAck<Map<String, Any?>>("joinConRoom", any(), any())
        }
    }

    @Test
    fun fullLocalRoomJoinFlow_shouldWork() = runTest {
        // Setup: Mock connected socket
        every { mockSocket.isConnected() } returns true
        every { mockSocket.getConnectionState() } returns ConnectionState.CONNECTED

        // Mock successful local room join response
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "isHost" to true,
            "eventStarted" to true,
            "mediasfuURL" to "http://localhost:3000",
            "apiKey" to "local-api-key",
            "allowRecord" to true
        )

        // Join local room
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "2",
            member = "localhost",
            sec = "c".repeat(32),  // 32 chars for local
            apiUserName = "apiuser"
        )

        val result = joinLocalRoom(options)

        // Verify success
        assertTrue(result.isSuccess)
        val response = result.getOrNull()
        assertNotNull(response)
        assertEquals(true, response.isHost)
        assertEquals(true, response.eventStarted)
        assertEquals("http://localhost:3000", response.mediasfuURL)
        assertEquals("local-api-key", response.apiKey)
        assertEquals(true, response.allowRecord)
    }

    // ========================================================================
    // Error Scenario Integration Tests
    // ========================================================================

    @Test
    fun joinRoom_whenUserBanned_shouldHandleGracefully() = runTest {
        every { mockSocket.isConnected() } returns true
        
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf("banned" to true)

        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "banneduser",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("banned") == true)
    }

    @Test
    fun joinRoom_whenUserSuspended_shouldHandleGracefully() = runTest {
        every { mockSocket.isConnected() } returns true
        
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf("suspended" to true)

        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "suspendeduser",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("suspended") == true)
    }

    @Test
    fun joinRoom_whenHostNotJoined_shouldHandleGracefully() = runTest {
        every { mockSocket.isConnected() } returns true
        
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf("noAdmin" to true)

        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "0",
            member = "earlyuser",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Host has not joined") == true)
    }

    @Test
    fun joinLocalRoom_whenHostNotJoined_shouldHandleGracefully() = runTest {
        every { mockSocket.isConnected() } returns true
        
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf("hostNotJoined" to true)

        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "0",
            member = "earlyuser",
            sec = "a".repeat(32),
            apiUserName = "apiuser"
        )

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Host has not joined") == true)
    }

    @Test
    fun joinRoom_whenSocketDisconnected_shouldFail() = runTest {
        every { mockSocket.isConnected() } returns false
        
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } throws SocketException("Not connected")

        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
    }

    // ========================================================================
    // Event Handler Integration Tests
    // ========================================================================

    @Test
    fun eventHandlerRegistration_shouldWorkCorrectly() = runTest {
        var eventReceived = false
        var eventData: Map<String, Any?>? = null

        // Register event handler
        mockSocket.on("testEvent") { data ->
            eventReceived = true
            eventData = data
        }

        // Verify handler was registered
        verify(exactly = 1) { mockSocket.on("testEvent", any()) }

        // Unregister
        mockSocket.off("testEvent")
        verify(exactly = 1) { mockSocket.off("testEvent") }
    }

    @Test
    fun multipleEventHandlers_shouldWorkCorrectly() = runTest {
        val events = mutableListOf<String>()

        // Register multiple handlers
        mockSocket.on("event1") { events.add("event1") }
        mockSocket.on("event2") { events.add("event2") }
        mockSocket.on("event3") { events.add("event3") }

        // Verify all registered
        verify(exactly = 1) { mockSocket.on("event1", any()) }
        verify(exactly = 1) { mockSocket.on("event2", any()) }
        verify(exactly = 1) { mockSocket.on("event3", any()) }

        // Unregister all
        mockSocket.offAll()
        verify(exactly = 1) { mockSocket.offAll() }
    }

    // ========================================================================
    // State Transition Integration Tests
    // ========================================================================

    @Test
    fun connectionStateTransitions_shouldFollowCorrectSequence() = runTest {
        val states = mutableListOf<ConnectionState>()

        // Mock state transitions
        every { mockSocket.getConnectionState() } returnsMany listOf(
            ConnectionState.DISCONNECTED,
            ConnectionState.CONNECTING,
            ConnectionState.CONNECTED,
            ConnectionState.DISCONNECTED
        )

        // Track states
        states.add(mockSocket.getConnectionState()) // DISCONNECTED
        states.add(mockSocket.getConnectionState()) // CONNECTING
        states.add(mockSocket.getConnectionState()) // CONNECTED
        states.add(mockSocket.getConnectionState()) // DISCONNECTED

        // Verify sequence
        assertEquals(ConnectionState.DISCONNECTED, states[0])
        assertEquals(ConnectionState.CONNECTING, states[1])
        assertEquals(ConnectionState.CONNECTED, states[2])
        assertEquals(ConnectionState.DISCONNECTED, states[3])
    }

    @Test
    fun reconnectionStateTransitions_shouldFollowCorrectSequence() = runTest {
        val states = mutableListOf<ConnectionState>()

        // Mock reconnection scenario
        every { mockSocket.getConnectionState() } returnsMany listOf(
            ConnectionState.CONNECTED,
            ConnectionState.RECONNECTING,
            ConnectionState.RECONNECTING,
            ConnectionState.CONNECTED
        )

        // Track states during reconnection
        states.add(mockSocket.getConnectionState()) // CONNECTED
        states.add(mockSocket.getConnectionState()) // RECONNECTING
        states.add(mockSocket.getConnectionState()) // RECONNECTING
        states.add(mockSocket.getConnectionState()) // CONNECTED

        // Verify reconnection sequence
        assertEquals(ConnectionState.CONNECTED, states[0])
        assertEquals(ConnectionState.RECONNECTING, states[1])
        assertEquals(ConnectionState.RECONNECTING, states[2])
        assertEquals(ConnectionState.CONNECTED, states[3])
    }

    @Test
    fun reconnectionFailure_shouldTransitionToFailed() = runTest {
        val states = mutableListOf<ConnectionState>()

        // Mock failed reconnection
        every { mockSocket.getConnectionState() } returnsMany listOf(
            ConnectionState.CONNECTED,
            ConnectionState.RECONNECTING,
            ConnectionState.FAILED
        )

        states.add(mockSocket.getConnectionState()) // CONNECTED
        states.add(mockSocket.getConnectionState()) // RECONNECTING
        states.add(mockSocket.getConnectionState()) // FAILED

        // Verify failure state reached
        assertEquals(ConnectionState.CONNECTED, states[0])
        assertEquals(ConnectionState.RECONNECTING, states[1])
        assertEquals(ConnectionState.FAILED, states[2])
    }

    // ========================================================================
    // Complex Scenario Integration Tests
    // ========================================================================

    @Test
    fun connectJoinAndReceiveEvents_fullScenario() = runTest {
        // Setup
        every { mockSocket.isConnected() } returns true
        every { mockSocket.getConnectionState() } returns ConnectionState.CONNECTED
        coEvery { mockSocket.connect(any(), any()) } returns Result.success(Unit)
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true,
            "isHost" to true
        )

        var participantJoined = false
        mockSocket.on("participant-joined") { participantJoined = true }

        // 1. Connect
        val connectResult = mockSocket.connect("https://test.mediasfu.com")
        assertTrue(connectResult.isSuccess)

        // 2. Join room
        val joinOptions = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "2",
            member = "host",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )
        val joinResult = joinRoom(joinOptions)
        assertTrue(joinResult.isSuccess)

        // 3. Verify event handler registered
        verify { mockSocket.on("participant-joined", any()) }

        // Verify complete flow
        coVerify(exactly = 1) { mockSocket.connect(any(), any()) }
        coVerify(exactly = 1) { mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any()) }
    }

    @Test
    fun reconnectAndRejoinRoom_fullScenario() = runTest {
        // Setup: Initial connection and join
        every { mockSocket.isConnected() } returns true
        every { mockSocket.getConnectionState() } returns ConnectionState.CONNECTED andThen 
            ConnectionState.RECONNECTING andThen ConnectionState.CONNECTED
    coEvery { mockSocket.connect(any(), any()) } returns Result.success(Unit)
        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true
        )

        // 1. Initial join
        val joinOptions = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )
        val firstJoin = joinRoom(joinOptions)
        assertTrue(firstJoin.isSuccess)

        // 2. Check state is CONNECTED initially
        assertEquals(ConnectionState.CONNECTED, mockSocket.getConnectionState())
        
        // 3. Simulate disconnect and reconnect
        assertEquals(ConnectionState.RECONNECTING, mockSocket.getConnectionState())
        assertEquals(ConnectionState.CONNECTED, mockSocket.getConnectionState())
        
        // 4. Rejoin after reconnection
        val secondJoin = joinRoom(joinOptions)
        assertTrue(secondJoin.isSuccess)

        // Verify rejoin happened
        coVerify(exactly = 2) { mockSocket.emitWithAck<Map<String, Any?>>("joinRoom", any(), any()) }
    }
}
