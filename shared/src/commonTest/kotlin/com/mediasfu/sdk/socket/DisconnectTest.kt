package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.DisconnectOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DisconnectTest {
    @Test
    fun showsAlertAndInvalidatesAfterDelay() = runTest {
        val alerts = mutableListOf<Triple<String, String, Int>>()
        val validatedStates = mutableListOf<Boolean>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            disconnect(
                DisconnectOptions(
                    showAlert = { message, type, duration ->
                        alerts += Triple(message, type, duration)
                    },
                    redirectUrl = null,
                    onWeb = false,
                    updateValidated = { validatedStates += it }
                )
            )
        }

        assertEquals(
            listOf(
                Triple(
                    "You have been disconnected from the session.",
                    "danger",
                    3_000
                )
            ),
            alerts
        )
        assertTrue(validatedStates.isEmpty())

        advanceTimeBy(2_000)
        advanceUntilIdle()
        job.join()

        assertEquals(listOf(false), validatedStates)
    }

    @Test
    fun webRedirectSkipsAlertAndValidation() = runTest {
        var alertCalls = 0
        var validatedCalls = 0

        disconnect(
            DisconnectOptions(
                showAlert = { _, _, _ -> alertCalls += 1 },
                redirectUrl = "https://example.com",
                onWeb = true,
                updateValidated = { validatedCalls += 1 }
            )
        )

        assertEquals(0, alertCalls)
        assertEquals(0, validatedCalls)
    }

    @Test
    fun webWithoutRedirectBehavesLikeNative() = runTest {
        val alerts = mutableListOf<String>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            disconnect(
                DisconnectOptions(
                    showAlert = { message, _, _ -> alerts += message },
                    redirectUrl = "",
                    onWeb = true,
                    updateValidated = null
                )
            )
        }

        job.join()

        assertEquals(
            listOf("You have been disconnected from the session."),
            alerts
        )
    }
}
