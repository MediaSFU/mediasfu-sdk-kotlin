package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.UserWaitingOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserWaitingTest {

    @Test
    fun incrementsTotalReqWaitAndShowsAlert() {
        var alertMessage = ""
        var alertType = ""
        var alertDuration = 0
        var updatedTotal = 0

        val options = UserWaitingOptions(
            name = "John Doe",
            showAlert = { message, type, duration ->
                alertMessage = message
                alertType = type
                alertDuration = duration
            },
            totalReqWait = 3,
            updateTotalReqWait = { updatedTotal = it }
        )

        userWaiting(options)

        assertEquals(4, updatedTotal, "Total should be incremented by 1")
        assertTrue(alertMessage.contains("John Doe"), "Alert should mention user name")
        assertTrue(alertMessage.contains("joined the waiting room"), "Alert should mention waiting room")
        assertEquals("success", alertType, "Alert type should be success")
        assertEquals(3000, alertDuration, "Alert duration should be 3000ms")
    }

    @Test
    fun worksWithoutShowAlert() {
        var updatedTotal = 0

        val options = UserWaitingOptions(
            name = "Jane Smith",
            showAlert = null,
            totalReqWait = 5,
            updateTotalReqWait = { updatedTotal = it }
        )

        userWaiting(options)

        assertEquals(6, updatedTotal, "Total should be incremented even without alert")
    }

    @Test
    fun incrementsFromZero() {
        var updatedTotal = -1

        val options = UserWaitingOptions(
            name = "Alice",
            showAlert = { _, _, _ -> },
            totalReqWait = 0,
            updateTotalReqWait = { updatedTotal = it }
        )

        userWaiting(options)

        assertEquals(1, updatedTotal, "Should increment from 0 to 1")
    }

    @Test
    fun handlesLargeCount() {
        var updatedTotal = 0

        val options = UserWaitingOptions(
            name = "Bob",
            showAlert = null,
            totalReqWait = 999,
            updateTotalReqWait = { updatedTotal = it }
        )

        userWaiting(options)

        assertEquals(1000, updatedTotal, "Should handle large counts")
    }

    @Test
    fun swallowsExceptionsGracefully() {
        var updateCalled = false

        val options = UserWaitingOptions(
            name = "Charlie",
            showAlert = { _, _, _ -> throw RuntimeException("Test exception") },
            totalReqWait = 10,
            updateTotalReqWait = { updateCalled = true }
        )

        // Should not throw
        userWaiting(options)

        // Update should still be called despite alert throwing
        assertTrue(updateCalled, "Update should be called even if alert throws")
    }
}
