package com.mediasfu.sdk.socket

import android.os.Trace
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import com.mediasfu.sdk.ui.mediasfu.TranslationVoicePickerState
import com.mediasfu.sdk.ui.mediasfu.rememberTranslationVoicePicker
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TranslationVoiceCatalogueComposeTest {
    // The JVM test executes the real Compose runtime; Android tracing alone needs a platform stub.
    @BeforeTest fun prepareTracing() {
        mockkStatic(Trace::class)
        every { Trace.beginSection(any()) } returns Unit
        every { Trace.endSection() } returns Unit
    }
    @AfterTest fun restoreTracing() { unmockkStatic(Trace::class) }
    private fun reply(provider: String = "fish", language: String = "fr") = mapOf(
        "schemaVersion" to 1, "provider" to provider, "language" to language, "status" to "available",
        "voices" to mapOf("neutral" to listOf(mapOf("id" to "stock-n", "gender" to "neutral")))
    )

    @Test fun actualComposeBindingInvalidatesContextAndIgnoresLateResponses() = runTest {
        val replies = List(4) { CompletableDeferred<Any?>() }
        var requestIndex = 0
        val socket = FakeVoiceSocket { replies[requestIndex++].await() }
        val room = mutableStateOf("RoomA")
        val language = mutableStateOf("fr")
        val configuration = mutableStateOf("model-one")
        val frameClock = BroadcastFrameClock()
        val recomposer = Recomposer(coroutineContext + frameClock)
        val composition = Composition(EmptyVoiceApplier(), recomposer)
        var picker: TranslationVoicePickerState? = null
        backgroundScope.launch(frameClock) { recomposer.runRecomposeAndApplyChanges() }
        fun recompose() {
            Snapshot.sendApplyNotifications()
            runCurrent()
            frameClock.sendFrame(testScheduler.currentTime * 1_000_000)
            runCurrent()
        }
        try {
            composition.setContent {
                picker = rememberTranslationVoicePicker(socket, room.value, language.value, configuration.value, true)
            }
            recompose()
            replies[0].complete(reply())
            recompose()
            picker!!.select("stock-n")
            assertNotNull(picker!!.selectionConfig())

            configuration.value = "model-two"
            recompose()
            assertNull(picker!!.selectionConfig())
            assertTrue(picker!!.loading)

            language.value = "en"
            recompose()
            replies[1].complete(reply("old-provider"))
            recompose()
            assertNull(picker!!.selectionConfig())
            assertTrue(picker!!.loading)
            replies[2].complete(reply("xai", "en"))
            recompose()
            picker!!.select("stock-n")
            assertEquals("xai", picker!!.selectionConfig()?.get("ttsProvider"))

            room.value = "RoomB"
            recompose()
            assertNull(picker!!.selectionConfig())
            assertEquals("RoomB", socket.requests.last().second["roomName"])
            replies[3].complete(reply("fish", "en"))
            recompose()
            assertEquals("fish", picker!!.catalogue.provider)
            assertNull(picker!!.selectedId)
            assertEquals(4, requestIndex)
        } finally {
            composition.dispose()
            recomposer.cancel()
        }
    }
}


private class EmptyVoiceApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(index: Int, instance: Unit) {}
    override fun insertTopDown(index: Int, instance: Unit) {}
    override fun move(from: Int, to: Int, count: Int) {}
    override fun remove(index: Int, count: Int) {}
    override fun onClear() {}
}
