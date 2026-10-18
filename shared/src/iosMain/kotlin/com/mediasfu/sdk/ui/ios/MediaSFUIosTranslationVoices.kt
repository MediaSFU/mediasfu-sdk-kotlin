package com.mediasfu.sdk.ui.ios

import com.mediasfu.sdk.model.TranslationVoiceCatalogue
import com.mediasfu.sdk.socket.fetchTranslationVoices
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Cancellable, bounded catalogue request. All native voice APIs are main-thread APIs. */
class MediaSFUIosVoiceRequest internal constructor(private val job: Job, private val onCancel: () -> Unit) {
    fun cancel() { job.cancel(); onCancel() }
}

/** Swift-facing access to the authenticated socket owned by a mounted hosted room. */
class MediaSFUIosTranslationVoices internal constructor(
    private val scope: CoroutineScope,
    private val state: () -> MediasfuGenericState?
) {
    private var fingerprint: List<Any?>? = null
    private var revision = 0L
    private var request: Job? = null
    private var catalogue: TranslationVoiceCatalogue? = null
    private var catalogueToken = ""

    fun invalidate() {
        revision++
        fingerprint = null
        request?.cancel()
        request = null
        catalogue = null
        catalogueToken = ""
    }

    /** Opaque token; contains no room credentials or provider configuration. */
    fun contextToken(language: String, mode: String): String {
        val current = state()
        val socket = current?.connectivity?.socket
        val next = listOf(current, socket, socket?.id, socket?.isConnected(),
            current?.room?.roomName, current?.parameters?.roomName,
            current?.sessionCounter?.value, current?.translationConfig?.value,
            current?.translationSupported?.value, current?.translationVoiceRevision,
            current?.myDefaultOutputLanguage?.value, current?.mySpokenLanguageEnabled?.value,
            language, mode)
        if (next != fingerprint) {
            invalidate()
            fingerprint = next
        }
        return revision.toString()
    }

    /** The caller must retain the request and cancel it when its UI disappears. */
    fun load(language: String, mode: String, completion: (TranslationVoiceCatalogue) -> Unit): MediaSFUIosVoiceRequest {
        invalidate()
        val token = contextToken(language, mode)
        val current = state()
        val socket = current?.connectivity?.socket
        val roomName = current?.room?.roomName.orEmpty().ifBlank { current?.parameters?.roomName.orEmpty() }
        val job = scope.launch {
            val result = if (mode == "stock" && current?.translationSupported?.value == true && socket != null) {
                fetchTranslationVoices(socket, roomName, language)
            } else TranslationVoiceCatalogue(language = language)
            if (contextToken(language, mode) == token) {
                catalogue = result
                catalogueToken = token
                completion(result)
            }
        }
        request = job
        return MediaSFUIosVoiceRequest(job) {
            if (revision.toString() == token) invalidate()
        }
    }

    /** Submits only a current catalogue entry; server authorization remains authoritative. */
    fun apply(language: String, mode: String, token: String, voiceId: String, provider: String): Boolean {
        if (contextToken(language, mode) != token || catalogueToken != token || mode != "stock") return false
        val current = state() ?: return false
        if (!current.translationSupported.value || !current.mySpokenLanguageEnabled.value ||
            current.mySpokenLanguage.value.isBlank() || current.connectivity.socket?.isConnected() != true) return false
        val result = catalogue ?: return false
        if (result.provider != provider) return false
        val voice = result.selectionConfig(voiceId) ?: return false
        current.applyTranslationSettings(
            current.mySpokenLanguage.value, true, language,
            current.myDefaultListenLanguage.value, current.listenPreferences.value,
            current.showTranslationSubtitles.value, voice
        )
        invalidate()
        return true
    }
}
