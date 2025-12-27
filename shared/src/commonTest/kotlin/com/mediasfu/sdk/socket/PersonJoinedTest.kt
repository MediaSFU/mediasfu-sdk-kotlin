package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.PersonJoinedOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class PersonJoinedTest {

    @Test
    fun `shows alert when person joins with name`() {
        var alertMessage = ""
        var alertType = ""
        var alertDuration = 0

        val options = PersonJoinedOptions(
            name = "John Doe",
            showAlert = { message, type, duration ->
                alertMessage = message
                alertType = type
                alertDuration = duration
            }
        )

        personJoined(options)

        assertEquals("John Doe has joined the event.", alertMessage)
        assertEquals("success", alertType)
        assertEquals(3000, alertDuration)
    }

    @Test
    fun `does nothing when showAlert is null`() {
        val options = PersonJoinedOptions(
            name = "Jane Smith",
            showAlert = null
        )

        // Should not throw
        personJoined(options)
    }

    @Test
    fun `formats alert message correctly with special characters in name`() {
        var alertMessage = ""

        val options = PersonJoinedOptions(
            name = "Alice O'Brien",
            showAlert = { message, _, _ ->
                alertMessage = message
            }
        )

        personJoined(options)

        assertEquals("Alice O'Brien has joined the event.", alertMessage)
    }

    @Test
    fun `handles empty name gracefully`() {
        var alertMessage = ""

        val options = PersonJoinedOptions(
            name = "",
            showAlert = { message, _, _ ->
                alertMessage = message
            }
        )

        personJoined(options)

        assertEquals(" has joined the event.", alertMessage)
    }

    @Test
    fun `handles long name without error`() {
        var alertMessage = ""
        val longName = "A".repeat(100)

        val options = PersonJoinedOptions(
            name = longName,
            showAlert = { message, _, _ ->
                alertMessage = message
            }
        )

        personJoined(options)

        assertEquals("$longName has joined the event.", alertMessage)
    }
}
