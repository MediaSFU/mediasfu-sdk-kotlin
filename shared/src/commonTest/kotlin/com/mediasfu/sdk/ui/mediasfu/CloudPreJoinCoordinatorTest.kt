package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.methods.utils.CreateJoinRoomResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudPreJoinCoordinatorTest {
    @Test
    fun `maps valid response and public URL fallback`() = runTest {
        var observed: CloudPreJoinRequest? = null

        val result = completeCloudPreJoin(
            response = response(link = "", publicUrl = "https://media.example"),
            member = "Host",
            islevel = "2"
        ) { request, complete ->
            observed = request
            complete(true)
        }

        assertTrue(result.isSuccess)
        assertEquals("room1234", observed?.roomName)
        assertEquals("https://media.example", observed?.link)
        assertEquals("room-secret", observed?.adminPasscode)
    }

    @Test
    fun `preserves an explicit join passcode`() = runTest {
        var observed: CloudPreJoinRequest? = null

        completeCloudPreJoin(
            response = response(),
            member = "Guest",
            islevel = "0",
            adminPasscodeOverride = "invite-code"
        ) { request, complete ->
            observed = request
            complete(true)
        }

        assertEquals("invite-code", observed?.adminPasscode)
    }

    @Test
    fun `rejects incomplete response before connecting`() = runTest {
        var called = false

        val result = completeCloudPreJoin(
            response = response(secret = ""),
            member = "Host",
            islevel = "2"
        ) { _, _ -> called = true }

        assertTrue(result.isFailure)
        assertFalse(called)
        assertTrue(result.exceptionOrNull()?.message?.contains("room secret") == true)
    }

    @Test
    fun `reports rejected connection`() = runTest {
        val result = completeCloudPreJoin(response(), "Guest", "0") { _, complete ->
            complete(false)
        }

        assertEquals("Unable to join the MediaSFU room.", result.exceptionOrNull()?.message)
    }

    @Test
    fun `bounds missing callback`() = runTest {
        val result = completeCloudPreJoin(
            response = response(),
            member = "Host",
            islevel = "2",
            timeoutMillis = 1L
        ) { _, _ -> }

        assertEquals("MediaSFU room connection timed out.", result.exceptionOrNull()?.message)
    }

    private fun response(
        secret: String = "room-secret",
        link: String = "https://media.example",
        publicUrl: String = link
    ) = CreateJoinRoomResponse(
        message = "Room ready",
        roomName = "room1234",
        secureCode = null,
        publicURL = publicUrl,
        link = link,
        secret = secret,
        success = true
    )
}
