package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.UpdatedCoHostOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdatedCoHostTest {

    @Test
    fun `updates co-host for non-broadcast event when member is co-host`() {
        var updatedCoHost = ""
        var updatedResponsibilities = listOf<CoHostResponsibility>()
        var updatedYouAreCoHost = false
        var alertMessage = ""

        val responsibilities = listOf(
            CoHostResponsibility(name = "manageParticipants", value = true, dedicated = false)
        )

        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = responsibilities,
            showAlert = { message, type, duration ->
                alertMessage = message
            },
            eventType = EventType.CONFERENCE,
            islevel = "1",
            member = "user123",
            youAreCoHost = false,
            updateCoHost = { updatedCoHost = it },
            updateCoHostResponsibility = { updatedResponsibilities = it },
            updateYouAreCoHost = { updatedYouAreCoHost = it }
        )

        updatedCoHost(options)

        assertEquals("user123", updatedCoHost)
        assertEquals(responsibilities, updatedResponsibilities)
        assertTrue(updatedYouAreCoHost)
        assertEquals("You are now a co-host.", alertMessage)
    }

    @Test
    fun `does not show alert when already co-host`() {
        var updatedYouAreCoHost = false
        var alertCalled = false

        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = emptyList(),
            showAlert = { _, _, _ -> alertCalled = true },
            eventType = EventType.CONFERENCE,
            islevel = "1",
            member = "user123",
            youAreCoHost = true, // Already co-host
            updateCoHost = {},
            updateCoHostResponsibility = {},
            updateYouAreCoHost = { updatedYouAreCoHost = it }
        )

        updatedCoHost(options)

        assertFalse(alertCalled)
        assertFalse(updatedYouAreCoHost) // Should not update since already co-host
    }

    @Test
    fun `sets youAreCoHost to false when member is not co-host`() {
        var updatedYouAreCoHost = true

        val options = UpdatedCoHostOptions(
            coHost = "user456",
            coHostResponsibility = emptyList(),
            showAlert = null,
            eventType = EventType.CONFERENCE,
            islevel = "1",
            member = "user123", // Different from coHost
            youAreCoHost = true,
            updateCoHost = {},
            updateCoHostResponsibility = {},
            updateYouAreCoHost = { updatedYouAreCoHost = it }
        )

        updatedCoHost(options)

        assertFalse(updatedYouAreCoHost)
    }

    @Test
    fun `does not update for broadcast event`() {
        var updateCoHostCalled = false
        var updateResponsibilityCalled = false

        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = emptyList(),
            showAlert = null,
            eventType = EventType.BROADCAST,
            islevel = "1",
            member = "user123",
            youAreCoHost = false,
            updateCoHost = { updateCoHostCalled = true },
            updateCoHostResponsibility = { updateResponsibilityCalled = true },
            updateYouAreCoHost = {}
        )

        updatedCoHost(options)

        assertFalse(updateCoHostCalled)
        assertFalse(updateResponsibilityCalled)
    }

    @Test
    fun `sets youAreCoHost to true for broadcast event when islevel is not 2`() {
        var updatedYouAreCoHost = false

        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = emptyList(),
            showAlert = null,
            eventType = EventType.BROADCAST,
            islevel = "1", // Not "2"
            member = "user456",
            youAreCoHost = false,
            updateCoHost = {},
            updateCoHostResponsibility = {},
            updateYouAreCoHost = { updatedYouAreCoHost = it }
        )

        updatedCoHost(options)

        assertTrue(updatedYouAreCoHost)
    }

    @Test
    fun `does not set youAreCoHost for broadcast event when islevel is 2`() {
        var updatedYouAreCoHost = false

        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = emptyList(),
            showAlert = null,
            eventType = EventType.BROADCAST,
            islevel = "2",
            member = "user456",
            youAreCoHost = false,
            updateCoHost = {},
            updateCoHostResponsibility = {},
            updateYouAreCoHost = { updatedYouAreCoHost = it }
        )

        updatedCoHost(options)

        assertFalse(updatedYouAreCoHost)
    }

    @Test
    fun `handles exception gracefully`() {
        val options = UpdatedCoHostOptions(
            coHost = "user123",
            coHostResponsibility = emptyList(),
            showAlert = null,
            eventType = EventType.CONFERENCE,
            islevel = "1",
            member = "user123",
            youAreCoHost = false,
            updateCoHost = { throw RuntimeException("Test error") },
            updateCoHostResponsibility = {},
            updateYouAreCoHost = {}
        )

        // Should not throw
        updatedCoHost(options)
    }
}
