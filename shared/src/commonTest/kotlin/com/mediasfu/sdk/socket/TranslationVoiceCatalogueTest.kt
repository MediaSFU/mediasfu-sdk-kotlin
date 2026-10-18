package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.model.parseTranslationVoiceCatalogue
import com.mediasfu.sdk.ui.mediasfu.TranslationVoicePickerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TranslationVoiceCatalogueTest {
    private fun reply(provider: String = "fish", language: String = "fr") = mapOf(
        "schemaVersion" to 1, "provider" to provider, "language" to language,
        "status" to "available", "model" to "s2-pro", "truncated" to true,
        "voices" to mapOf(
            "male" to listOf("stock-m"),
            "female" to listOf(mapOf("id" to "stock-f", "name" to "French voice")),
            "neutral" to listOf(mapOf("id" to "stock-n", "name" to "Neutral voice", "gender" to "neutral"))
        )
    )

    @Test fun structuredAndLegacyVoicesKeepProviderAndNeutralGender() {
        val catalogue = parseTranslationVoiceCatalogue(reply(), "fr")
        assertEquals("available", catalogue.status)
        assertEquals("s2-pro", catalogue.model)
        assertTrue(catalogue.truncated)
        assertEquals(listOf("stock-m", "stock-f", "stock-n"), catalogue.voices.map { it.id })
        assertEquals("neutral", catalogue.voices.last().gender)
        assertTrue(catalogue.voices.all { it.provider == "fish" && it.language == "fr" })
    }

    @Test fun exactSelectedIdAndProviderAreSubmitted() {
        val catalogue = parseTranslationVoiceCatalogue(reply(), "fr")
        assertEquals(mapOf("voiceId" to "stock-n", "ttsProvider" to "fish"), catalogue.selectionConfig("stock-n"))
        assertNull(catalogue.selectionConfig("old-voice"))
        assertNull(catalogue.selectionConfig(null))
    }

    @Test fun providerCatalogueDoesNotAssumeDeepgram() {
        for (provider in listOf("fish", "sarvam", "xai", "playht", "custom-provider")) {
            val catalogue = parseTranslationVoiceCatalogue(reply(provider), "fr")
            assertEquals(provider, catalogue.selectionConfig("stock-f")?.get("ttsProvider"))
        }
    }

    @Test fun invalidRepliesNeverProduceGuessedVoices() {
        val invalid = listOf(null, false, "bad", emptyMap<String, Any?>(), reply() + ("provider" to ""),
            reply() + ("provider" to 42), reply() + ("voices" to listOf("bad")))
        invalid.forEach { assertTrue(parseTranslationVoiceCatalogue(it, "fr").voices.isEmpty()) }
    }

    @Test fun unsupportedSchemasAndLanguageMismatchFailClosed() {
        assertTrue(parseTranslationVoiceCatalogue(reply() + ("schemaVersion" to 2), "fr").voices.isEmpty())
        assertTrue(parseTranslationVoiceCatalogue(reply(language = "en"), "fr").voices.isEmpty())
        assertEquals(3, parseTranslationVoiceCatalogue(reply() - "schemaVersion", "fr").voices.size)
    }

    @Test fun structuredErrorsAndUnavailableStatesCannotAuthorizeSelection() {
        for (error in listOf("private diagnostic", mapOf("code" to "upstream"), true)) {
            val catalogue = parseTranslationVoiceCatalogue(reply() + ("error" to error), "fr")
            assertTrue(catalogue.voices.isEmpty())
            assertNull(catalogue.selectionConfig("stock-f"))
            assertFalse(catalogue.toString().contains("private diagnostic"))
        }
        for (status in listOf("unavailable", "error", "unsupported")) {
            assertTrue(parseTranslationVoiceCatalogue(reply() + ("status" to status), "fr").voices.isEmpty())
        }
    }

    @Test fun emptyResponseRemainsEmptyEvenIfMalformedServerIncludesItems() {
        val catalogue = parseTranslationVoiceCatalogue(reply() + ("status" to "empty"), "fr")
        assertEquals("empty", catalogue.status)
        assertTrue(catalogue.voices.isEmpty())
    }

    @Test fun duplicateMalformedAndForeignEntriesAreFiltered() {
        val data = reply() + ("voices" to mapOf("neutral" to listOf(
            "same", mapOf("id" to "same"), mapOf("id" to ""), 42,
            mapOf("id" to "wrong-provider", "provider" to "deepgram"),
            mapOf("id" to "other", "gender" to "unknown")
        )))
        val catalogue = parseTranslationVoiceCatalogue(data, "fr")
        assertEquals(listOf("same", "other"), catalogue.voices.map { it.id })
        assertTrue(catalogue.voices.all { it.gender == "neutral" })
    }

    @Test fun serverAuthoritativeVoiceLocaleIsNotMistakenForCatalogueOutputLanguage() {
        val data = reply("playht", "en") + ("voices" to mapOf("neutral" to listOf(
            mapOf("id" to "provider-voice", "language" to "en-US", "provider" to "playht")
        )))
        val catalogue = parseTranslationVoiceCatalogue(data, "en")
        assertEquals("en", catalogue.voices.single().language)
        assertEquals("provider-voice", catalogue.selectionConfig("provider-voice")?.get("voiceId"))
    }

    @Test fun requestIncludesJoinedRoomAndOutputLanguage() = runTest {
        val socket = FakeVoiceSocket { reply() }
        val result = fetchTranslationVoices(socket, "Room123", "fr", 500)
        assertEquals("available", result.status)
        val expected: List<Pair<String, Map<String, Any?>>> = listOf(
            "translation:getVoices" to mapOf("roomName" to "Room123", "language" to "fr")
        )
        assertEquals(expected, socket.requests)
        assertEquals(500L, socket.timeout)
    }

    @Test fun disconnectedAndMissingContextNeverEmit() = runTest {
        val socket = FakeVoiceSocket { reply() }
        socket.connected = false
        assertEquals("unavailable", fetchTranslationVoices(socket, "Room123", "fr").status)
        socket.connected = true
        fetchTranslationVoices(socket, "", "fr")
        fetchTranslationVoices(socket, "Room123", "")
        assertTrue(socket.requests.isEmpty())
    }

    @Test fun timeoutIsBoundedEvenIfTransportDoesNotEnforceIt() = runTest {
        val socket = FakeVoiceSocket { delay(60_000); reply() }
        assertEquals("unavailable", fetchTranslationVoices(socket, "Room123", "fr", 100).status)
        assertEquals(100L, testScheduler.currentTime)
    }

    @Test fun failedTransportDoesNotLeakDiagnosticOrSupplyFallback() = runTest {
        val result = fetchTranslationVoices(FakeVoiceSocket { error("private upstream diagnostic") }, "Room123", "fr")
        assertTrue(result.voices.isEmpty())
        assertFalse(result.toString().contains("private upstream"))
    }

    @Test fun cancelledContextCancelsRequestInsteadOfReturningSelectableReply() = runTest {
        var returned = false
        val job = launch {
            fetchTranslationVoices(FakeVoiceSocket { awaitCancellation() }, "Room123", "fr")
            returned = true
        }
        runCurrent()
        job.cancel(CancellationException("room changed"))
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(returned)
    }

    @Test fun reloadClearsPreviousSelectionAndCannotSaveDuringLoading() {
        val picker = TranslationVoicePickerState("fr")
        picker.receive(parseTranslationVoiceCatalogue(reply(), "fr"))
        picker.select("stock-n")
        assertEquals("stock-n", picker.selectionConfig()?.get("voiceId"))
        picker.begin()
        assertNull(picker.selectedId)
        picker.select("stock-n")
        assertNull(picker.selectionConfig())
        picker.receive(parseTranslationVoiceCatalogue(reply("xai"), "fr"))
        assertNull(picker.selectedId)
        picker.select("stock-f")
        assertEquals("xai", picker.selectionConfig()?.get("ttsProvider"))
    }

    @Test fun oldRoomOrLanguageStateCannotSelectInNewPicker() {
        val previous = TranslationVoicePickerState("fr")
        val next = TranslationVoicePickerState("en")
        previous.receive(parseTranslationVoiceCatalogue(reply(), "fr"))
        previous.select("stock-n")
        next.select("stock-n")
        assertNull(next.selectionConfig())
    }

}

internal class FakeVoiceSocket(private val reply: suspend () -> Any?) : SocketManager {
    override val id = "test-connection"
    var connected = true
    var timeout: Long? = null
    val requests = mutableListOf<Pair<String, Map<String, Any?>>>()
    override suspend fun connect(url: String, config: SocketConfig) = Result.success(Unit)
    override suspend fun disconnect() = Result.success(Unit)
    override fun isConnected() = connected
    override fun getConnectionState() = if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED
    override suspend fun emit(event: String, data: Map<String, Any?>) { requests.add(event to data) }
    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> emitWithAck(event: String, data: Map<String, Any?>, timeout: Long): T {
        this.timeout = timeout
        requests.add(event to data)
        return reply() as T
    }
    override fun emitWithAck(event: String, data: Map<String, Any?>, callback: (Any?) -> Unit) = error("Unexpected callback API")
    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {}
    override fun off(event: String) {}
    override fun hasListener(event: String) = false
    override fun offAll() {}
    override fun onConnect(handler: suspend () -> Unit) {}
    override fun onDisconnect(handler: suspend (String) -> Unit) {}
    override fun onError(handler: suspend (Throwable) -> Unit) {}
    override fun onReconnect(handler: suspend (Int) -> Unit) {}
    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {}
    override fun onReconnectFailed(handler: suspend () -> Unit) {}
}
