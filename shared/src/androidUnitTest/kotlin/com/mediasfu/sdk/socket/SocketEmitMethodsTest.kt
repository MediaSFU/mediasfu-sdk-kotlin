package com.mediasfu.sdk.socket

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Tests for socket emit methods (joinRoom, joinConRoom, joinLocalRoom).
 *
 * These tests verify input validation, socket communication, and response parsing.
 */
class SocketEmitMethodsTest {

    private lateinit var mockSocket: SocketManager

    @BeforeTest
    fun setup() {
        mockSocket = mockk<SocketManager>()
    }

    @AfterTest
    fun teardown() {
        clearAllMocks()
    }

    // ========================================================================
    // joinRoom Tests
    // ========================================================================

    @Test
    fun joinRoom_shouldFailWithMissingParameters() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SocketEmitException)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required parameters") == true)
    }

    @Test
    fun joinRoom_shouldFailWithNonAlphanumericRoomName() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s1234-567",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("alphanumeric") == true)
    }

    @Test
    fun joinRoom_shouldFailWithInvalidRoomNamePrefix() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "x12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must start with") == true)
    }

    @Test
    fun joinRoom_shouldFailWithInvalidSecretLength() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "short",
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("secret format") == true)
    }

    @Test
    fun joinRoom_shouldFailWithInvalidIslevel() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "5",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("islevel") == true)
    }

    @Test
    fun joinRoom_shouldFailWhenUserBanned() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf("banned" to true)

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("banned") == true)
    }

    @Test
    fun joinRoom_shouldFailWhenUserSuspended() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf("suspended" to true)

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("suspended") == true)
    }

    @Test
    fun joinRoom_shouldFailWhenNoAdmin() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf("noAdmin" to true)

        val result = joinRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Host has not joined") == true)
    }

    // ========================================================================
    // joinConRoom Tests
    // ========================================================================

    @Test
    fun joinConRoom_shouldFailWithMissingParameters() = runTest {
        val options = JoinConRoomOptions(
            socket = mockSocket,
            roomName = "",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinConRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required parameters") == true)
    }

    @Test
    fun joinConRoom_shouldFailWithInvalidRoomNamePrefix() = runTest {
        val options = JoinConRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        val result = joinConRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must start with") == true)
    }

    @Test
    fun joinConRoom_shouldEmitCorrectEvent() = runTest {
        val options = JoinConRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>("joinConRoom", any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true
        )

        joinConRoom(options)

        coVerify {
            mockSocket.emitWithAck<Map<String, Any?>>("joinConRoom", any(), any())
        }
    }

    // ========================================================================
    // joinLocalRoom Tests
    // ========================================================================

    @Test
    fun joinLocalRoom_shouldFailWithMissingParameters() = runTest {
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(32),
            apiUserName = "apiuser"
        )

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Missing required parameters") == true)
    }

    @Test
    fun joinLocalRoom_shouldFailWithInvalidRoomNamePrefix() = runTest {
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(32),
            apiUserName = "apiuser"
        )

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("must start with 'm'") == true)
    }

    @Test
    fun joinLocalRoom_shouldFailWithInvalidSecretLength() = runTest {
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),  // Should be 32 for local rooms
            apiUserName = "apiuser"
        )

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("secret format") == true)
    }

    @Test
    fun joinLocalRoom_shouldFailWhenUserBanned() = runTest {
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(32),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf("isBanned" to true)

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("banned") == true)
    }

    @Test
    fun joinLocalRoom_shouldFailWhenHostNotJoined() = runTest {
        val options = JoinLocalRoomOptions(
            socket = mockSocket,
            roomName = "m12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(32),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf("hostNotJoined" to true)

        val result = joinLocalRoom(options)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Host has not joined") == true)
    }

    // ========================================================================
    // Validation Helper Tests
    // ========================================================================

    @Test
    fun joinRoom_shouldAcceptValidRoomNameWithS() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "s12345678",
            islevel = "1",
            member = "user123",
            sec = "a".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true
        )

        val result = joinRoom(options)

        // Should not fail validation
        coVerify {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        }
    }

    @Test
    fun joinRoom_shouldAcceptValidRoomNameWithP() = runTest {
        val options = JoinRoomOptions(
            socket = mockSocket,
            roomName = "p12345678",
            islevel = "2",
            member = "user456",
            sec = "b".repeat(64),
            apiUserName = "apiuser"
        )

        coEvery {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        } returns mapOf(
            "rtpCapabilities" to mapOf<String, Any?>(),
            "success" to true
        )

        val result = joinRoom(options)

        // Should not fail validation
        coVerify {
            mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
        }
    }

    @Test
    fun joinRoom_shouldAcceptAllValidIslevels() = runTest {
        for (level in listOf("0", "1", "2")) {
            clearMocks(mockSocket)
            
            val options = JoinRoomOptions(
                socket = mockSocket,
                roomName = "s12345678",
                islevel = level,
                member = "user123",
                sec = "a".repeat(64),
                apiUserName = "apiuser"
            )

            coEvery {
                mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
            } returns mapOf(
                "rtpCapabilities" to mapOf<String, Any?>(),
                "success" to true
            )

            joinRoom(options)

            coVerify {
                mockSocket.emitWithAck<Map<String, Any?>>(any(), any(), any())
            }
        }
    }
}
