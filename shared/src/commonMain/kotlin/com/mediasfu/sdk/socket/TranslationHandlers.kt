package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.TranslationConfigUpdatedOptions
import com.mediasfu.sdk.model.TranslationChannelsAvailableOptions
import com.mediasfu.sdk.model.TranslationErrorOptions
import com.mediasfu.sdk.model.TranslationLanguageSetOptions
import com.mediasfu.sdk.model.TranslationMemberStateOptions
import com.mediasfu.sdk.model.TranslationProducerClosedOptions
import com.mediasfu.sdk.model.TranslationProducerReadyOptions
import com.mediasfu.sdk.model.TranslationRoomConfigOptions
import com.mediasfu.sdk.model.TranslationSpeakerOutputChangedOptions
import com.mediasfu.sdk.model.TranslationSubscribedOptions
import com.mediasfu.sdk.model.TranslationTranscriptOptions
import com.mediasfu.sdk.model.TranslationUnsubscribedOptions
import com.mediasfu.sdk.model.call

/**
 * Kotlin counterparts for Flutter translation socket receive handlers.
 *
 * Initial implementation scope:
 * - room config updates
 * - language confirmation
 * - errors
 * - transcript feed
 * - speaker output changes
 */
suspend fun translationRoomConfig(options: TranslationRoomConfigOptions) {
    val config = options.data.config
    options.updateTranslationSupported?.invoke(config.supportTranslation)
    options.updateTranslationConfig?.invoke(config)
}

suspend fun translationConfigUpdated(options: TranslationConfigUpdatedOptions) {
    val config = options.data.config
    options.updateTranslationSupported?.invoke(config.supportTranslation)
    options.updateTranslationConfig?.invoke(config)
    options.showAlert.call(
        message = "Translation settings updated by host",
        type = "info",
        duration = 2_000
    )
}

suspend fun translationLanguageSet(options: TranslationLanguageSetOptions) {
    val data = options.data
    if (data.success) {
        options.updateMySpokenLanguage?.invoke(data.language)
        options.updateMySpokenLanguageEnabled?.invoke(data.enabled)
    } else if (!data.error.isNullOrBlank()) {
        options.showAlert.call(
            message = data.error,
            type = "danger",
            duration = 3_000
        )
    }
}

suspend fun translationSubscribed(options: TranslationSubscribedOptions) {
    val data = options.data

    options.updateListenPreferences?.let { update ->
        update(
            mapOf(data.speakerId to data.language)
        )
    }

    if (!data.producerId.isNullOrBlank() && !data.originalProducerId.isNullOrBlank()) {
        options.updateTranslationProducerMap?.let { update ->
            update(
                mapOf(
                    data.originalProducerId to mapOf(data.language to data.producerId)
                )
            )
        }
    }

    if (data.channelCreated) {
        options.showAlert.call(
            message = "Translation channel created for ${data.language}",
            type = "success",
            duration = 2_000
        )
    }
}

suspend fun translationUnsubscribed(options: TranslationUnsubscribedOptions) {
    val data = options.data
    options.updateListenPreferences?.invoke(
        mapOf(data.speakerId to "")
    )
}

suspend fun translationProducerReady(options: TranslationProducerReadyOptions) {
    val data = options.data
    options.updateTranslationProducerMap?.invoke(
        mapOf(data.originalProducerId to mapOf(data.language to data.producerId))
    )
}

suspend fun translationProducerClosed(options: TranslationProducerClosedOptions) {
    val data = options.data
    options.updateTranslationProducerMap?.invoke(
        mapOf((data.originalProducerId ?: data.producerId) to mapOf(data.language to ""))
    )

    if (!data.reason.isNullOrBlank()) {
        options.showAlert.call(
            message = "Translation stopped: ${data.reason}",
            type = "info",
            duration = 2_000
        )
    }
}

suspend fun translationChannelsAvailable(options: TranslationChannelsAvailableOptions) {
    val data = options.data
    options.updateAvailableTranslationChannels?.invoke(
        mapOf(data.speakerId to data.languages)
    )
}

suspend fun translationMemberState(options: TranslationMemberStateOptions) {
    val data = options.data
    options.updateParticipantTranslationState?.invoke(
        mapOf(data.memberId to data.state)
    )
}

suspend fun translationError(options: TranslationErrorOptions) {
    val data = options.data
    val message = when (data.code) {
        "max_channels" -> {
            if (!data.availableChannels.isNullOrEmpty()) {
                "Maximum ${data.maxChannels ?: 5} translation channels reached. Available: ${data.availableChannels.joinToString(", ")}" 
            } else {
                data.message ?: "Maximum translation channels reached."
            }
        }

        "speaker_not_found" -> "Speaker not found or has left the meeting."
        "language_not_allowed" -> "This language is not available for translation in this room."
        else -> if (data.error.isNotBlank()) data.error else "Translation error occurred"
    }

    options.showAlert.call(
        message = message,
        type = "danger",
        duration = 5_000
    )
}

suspend fun translationTranscript(options: TranslationTranscriptOptions) {
    val next = (options.existingTranscripts + options.data)
        .takeLast(options.maxTranscripts)

    options.updateTranscripts?.invoke(next)
    options.onTranscriptReceived?.invoke(options.data)
}

suspend fun translationSpeakerOutputChanged(options: TranslationSpeakerOutputChangedOptions) {
    val data = options.data

    options.updateSpeakerTranslationState?.invoke(
        data.speakerId,
        data.outputLanguage,
        data.originalProducerId
    )

    val listenerWantsOriginal = options.listenerOverride?.wantOriginal == true
    val listenerPref = options.listenerOverride?.preferredLanguage?.lowercase()
    val outputLang = data.outputLanguage?.lowercase()
    val listenerWantsDifferentLanguage =
        !listenerPref.isNullOrBlank() && listenerPref != outputLang

    if (listenerWantsOriginal) {
        options.showAlert.call(
            message = "${data.speakerName} output changed, but original audio remains selected.",
            type = "info",
            duration = 3_000
        )
        return
    }

    if (listenerWantsDifferentLanguage) {
        options.pauseOriginalProducer?.invoke(data.originalProducerId, data.speakerId)
        return
    }

    if (data.enabled && !data.outputLanguage.isNullOrBlank()) {
        options.pauseOriginalProducer?.invoke(data.originalProducerId, data.speakerId)
        options.showAlert.call(
            message = "${data.speakerName} is now speaking in ${data.outputLanguage}",
            type = "info",
            duration = 3_000
        )
    } else {
        options.stopConsumingTranslationForSpeaker?.invoke(data.speakerId)
        options.resumeOriginalProducer?.invoke(data.originalProducerId, data.speakerId)
        options.showAlert.call(
            message = "${data.speakerName} returned to original language",
            type = "info",
            duration = 3_000
        )
    }
}
