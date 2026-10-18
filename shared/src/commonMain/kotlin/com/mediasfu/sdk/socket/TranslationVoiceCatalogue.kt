package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.TranslationVoiceCatalogue
import com.mediasfu.sdk.model.parseTranslationVoiceCatalogue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Discovers stock voices using the authenticated joined room, never a client credential. */
suspend fun fetchTranslationVoices(
    socket: SocketManager,
    roomName: String,
    language: String,
    timeoutMillis: Long = 20_000
): TranslationVoiceCatalogue {
    fun unavailable() = TranslationVoiceCatalogue(language = language)
    if (roomName.isBlank() || language.isBlank() || !socket.isConnected()) return unavailable()
    return try {
        withTimeout(timeoutMillis) {
            val reply = socket.emitWithAck<Any?>(
                "translation:getVoices",
                mapOf("roomName" to roomName, "language" to language),
                timeout = timeoutMillis
            )
            parseTranslationVoiceCatalogue(reply, language)
        }
    } catch (_: TimeoutCancellationException) {
        unavailable()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        unavailable()
    }
}
