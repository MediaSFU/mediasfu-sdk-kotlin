package com.mediasfu.sdk.ui.ios

import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.socket.FakeVoiceSocket
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericState
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TranslationVoiceCatalogueNativeTest {
    @Test fun nativeContextChangesWithRoomSocketLanguageModeAndSettings() = runTest {
        val parameters = MediasfuParameters().apply { roomName = "owned-test-room" }
        val options = MediasfuGenericOptions(sourceParameters = parameters, connectMediaSFU = false, returnUI = false)
        val state = MediasfuGenericState(backgroundScope, parameters, options)
        val bridge = MediaSFUIosTranslationVoices(this) { state }
        var token = bridge.contextToken("fr", "stock")
        assertEquals(token, bridge.contextToken("fr", "stock"))
        state.room.updateRoomName("another-owned-test-room")
        var next = bridge.contextToken("fr", "stock")
        assertNotEquals(token, next)
        token = next
        val socket = FakeVoiceSocket { null }
        state.connectivity.updateSocket(socket)
        next = bridge.contextToken("fr", "stock")
        assertNotEquals(token, next)
        token = next
        socket.connected = false
        next = bridge.contextToken("fr", "stock")
        assertNotEquals(token, next)
        assertNotEquals(next, bridge.contextToken("de", "stock"))
        token = bridge.contextToken("de", "stock")
        assertNotEquals(token, bridge.contextToken("de", "configured"))
        token = bridge.contextToken("de", "configured")
        state.applyTranslationSettings("en", false, "de", null, emptyMap(), true)
        assertNotEquals(token, bridge.contextToken("de", "configured"))
        runCurrent()
        assertTrue(socket.requests.isEmpty()) // Unsupported room never emits settings.
    }

    @Test fun unmountedRoomAndCancellationCannotAuthorizeSelection() = runTest {
        val bridge = MediaSFUIosTranslationVoices(this) { null }
        var completions = 0
        val cancelled = bridge.load("fr", "stock") { completions++ }
        cancelled.cancel()
        runCurrent()
        assertEquals(0, completions)
        bridge.load("fr", "stock") {
            completions++
            assertEquals("unavailable", it.status)
            assertTrue(it.voices.isEmpty())
        }
        runCurrent()
        assertEquals(1, completions)
        assertFalse(bridge.apply("fr", "stock", bridge.contextToken("fr", "stock"), "fixture-only-id", "test-provider"))
    }
}
