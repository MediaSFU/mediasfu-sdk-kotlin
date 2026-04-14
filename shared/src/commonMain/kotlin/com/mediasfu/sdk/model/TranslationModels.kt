package com.mediasfu.sdk.model

/** Translation room language filter mode. */
enum class LanguageMode { ALLOWLIST, BLOCKLIST, ANY }

/** Per-language entry, optionally with display nickname. */
data class LanguageEntry(
    val code: String,
    val nickname: String? = null
)

/** Room-level translation configuration shared through socket events. */
data class TranslationRoomConfig(
    val supportTranslation: Boolean,
    val spokenLanguageMode: LanguageMode = LanguageMode.ANY,
    val allowedSpokenLanguages: List<LanguageEntry>? = null,
    val blockedSpokenLanguages: List<String>? = null,
    val listenLanguageMode: LanguageMode = LanguageMode.ANY,
    val allowedListenLanguages: List<LanguageEntry>? = null,
    val blockedListenLanguages: List<String>? = null,
    val maxActiveChannelsPerSpeaker: Int = 5,
    val autoDetectSpokenLanguage: Boolean = false,
    val allowSpokenLanguageChange: Boolean? = null,
    val allowListenLanguageChange: Boolean? = null
)

data class TranslationRoomConfigData(val config: TranslationRoomConfig)
data class TranslationConfigUpdatedData(val config: TranslationRoomConfig)

data class TranslationLanguageSetData(
    val success: Boolean,
    val language: String,
    val enabled: Boolean,
    val error: String? = null
)

data class TranslationSubscribedData(
    val speakerId: String,
    val speakerName: String? = null,
    val language: String,
    val channelCreated: Boolean,
    val producerId: String? = null,
    val originalProducerId: String? = null
)

data class TranslationUnsubscribedData(
    val speakerId: String,
    val language: String,
    val channelClosed: Boolean
)

data class TranslationProducerReadyData(
    val speakerId: String,
    val speakerName: String? = null,
    val language: String,
    val producerId: String,
    val originalProducerId: String
)

data class TranslationProducerClosedData(
    val speakerId: String,
    val language: String,
    val producerId: String,
    val originalProducerId: String? = null,
    val reason: String? = null
)

data class TranslationChannelsAvailableData(
    val speakerId: String,
    val speakerName: String? = null,
    val languages: List<String>,
    val originalProducerId: String
)

data class TranslationMemberStateData(
    val memberId: String,
    val memberName: String? = null,
    val state: Map<String, Any?>
)

data class TranslationErrorData(
    val error: String,
    val code: String? = null,
    val details: Any? = null,
    val availableChannels: List<String>? = null,
    val maxChannels: Int? = null,
    val message: String? = null
)

data class TranslationTranscriptData(
    val speakerId: String,
    val speakerName: String,
    val language: String,
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val detectedLanguage: String? = null,
    val timestamp: Long
)

data class TranslationSpeakerOutputChangedData(
    val speakerId: String,
    val speakerName: String,
    val inputLanguage: String,
    val outputLanguage: String? = null,
    val originalProducerId: String,
    val enabled: Boolean
)

typealias TranslationProducerMap = Map<String, Map<String, String>>

data class ListenerOverride(
    val speakerId: String,
    val wantOriginal: Boolean,
    val preferredLanguage: String? = null
)

data class TranslationRoomConfigOptions(
    val data: TranslationRoomConfigData,
    val updateTranslationConfig: ((TranslationRoomConfig) -> Unit)? = null,
    val updateTranslationSupported: ((Boolean) -> Unit)? = null
)

data class TranslationConfigUpdatedOptions(
    val data: TranslationConfigUpdatedData,
    val updateTranslationConfig: ((TranslationRoomConfig) -> Unit)? = null,
    val updateTranslationSupported: ((Boolean) -> Unit)? = null,
    val showAlert: ShowAlert? = null
)

data class TranslationLanguageSetOptions(
    val data: TranslationLanguageSetData,
    val updateMySpokenLanguage: ((String) -> Unit)? = null,
    val updateMySpokenLanguageEnabled: ((Boolean) -> Unit)? = null,
    val showAlert: ShowAlert? = null
)

data class TranslationSubscribedOptions(
    val data: TranslationSubscribedData,
    val updateListenPreferences: ((Map<String, String>) -> Unit)? = null,
    val updateTranslationProducerMap: ((TranslationProducerMap) -> Unit)? = null,
    val showAlert: ShowAlert? = null,
)

data class TranslationUnsubscribedOptions(
    val data: TranslationUnsubscribedData,
    val updateListenPreferences: ((Map<String, String>) -> Unit)? = null,
)

data class TranslationProducerReadyOptions(
    val data: TranslationProducerReadyData,
    val updateTranslationProducerMap: ((TranslationProducerMap) -> Unit)? = null,
)

data class TranslationProducerClosedOptions(
    val data: TranslationProducerClosedData,
    val updateTranslationProducerMap: ((TranslationProducerMap) -> Unit)? = null,
    val showAlert: ShowAlert? = null,
)

data class TranslationChannelsAvailableOptions(
    val data: TranslationChannelsAvailableData,
    val updateAvailableTranslationChannels: ((Map<String, List<String>>) -> Unit)? = null,
)

data class TranslationMemberStateOptions(
    val data: TranslationMemberStateData,
    val updateParticipantTranslationState: ((Map<String, Map<String, Any?>>) -> Unit)? = null,
)

data class TranslationErrorOptions(
    val data: TranslationErrorData,
    val showAlert: ShowAlert? = null
)

data class TranslationTranscriptOptions(
    val data: TranslationTranscriptData,
    val updateTranscripts: ((List<TranslationTranscriptData>) -> Unit)? = null,
    val onTranscriptReceived: ((TranslationTranscriptData) -> Unit)? = null,
    val existingTranscripts: List<TranslationTranscriptData> = emptyList(),
    val maxTranscripts: Int = 100
)

data class TranslationSpeakerOutputChangedOptions(
    val data: TranslationSpeakerOutputChangedData,
    val pauseOriginalProducer: (suspend (String, String) -> Unit)? = null,
    val resumeOriginalProducer: (suspend (String, String) -> Unit)? = null,
    val stopConsumingTranslationForSpeaker: (suspend (String) -> Unit)? = null,
    val updateSpeakerTranslationState: ((String, String?, String) -> Unit)? = null,
    val showAlert: ShowAlert? = null,
    val listenerOverride: ListenerOverride? = null
)

typealias TranslationRoomConfigType = suspend (TranslationRoomConfigOptions) -> Unit
typealias TranslationConfigUpdatedType = suspend (TranslationConfigUpdatedOptions) -> Unit
typealias TranslationLanguageSetType = suspend (TranslationLanguageSetOptions) -> Unit
typealias TranslationSubscribedType = suspend (TranslationSubscribedOptions) -> Unit
typealias TranslationUnsubscribedType = suspend (TranslationUnsubscribedOptions) -> Unit
typealias TranslationProducerReadyType = suspend (TranslationProducerReadyOptions) -> Unit
typealias TranslationProducerClosedType = suspend (TranslationProducerClosedOptions) -> Unit
typealias TranslationChannelsAvailableType = suspend (TranslationChannelsAvailableOptions) -> Unit
typealias TranslationMemberStateType = suspend (TranslationMemberStateOptions) -> Unit
typealias TranslationErrorType = suspend (TranslationErrorOptions) -> Unit
typealias TranslationTranscriptType = suspend (TranslationTranscriptOptions) -> Unit
typealias TranslationSpeakerOutputChangedType = suspend (TranslationSpeakerOutputChangedOptions) -> Unit
