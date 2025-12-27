package com.mediasfu.sdk.model
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Core data structures shared across MediaSFU platforms.
 * These mirror the models exposed by the Flutter and React SDKs so we can share logic in Kotlin.
 */

/**
 * Custom serializer that handles server inconsistencies where a list field might be sent as:
 * - A proper JSON array: [...]
 * - An empty string: ""
 * - A number: 0
 * - null
 * 
 * This converts non-array values to empty list.
 */
object FlexibleListSerializer : KSerializer<List<WaitingRoomParticipant>?> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleList")
    
    override fun deserialize(decoder: Decoder): List<WaitingRoomParticipant>? {
        val jsonDecoder = decoder as? JsonDecoder ?: return null
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                element.mapIndexedNotNull { index, item ->
                    runCatching {
                        jsonDecoder.json.decodeFromJsonElement<WaitingRoomParticipant>(item)
                    }.onFailure { error ->
                        Logger.e("CoreModels", "MediaSFU - Failed to decode WaitingRoomParticipant at index $index: ${error.message}")
                    }.getOrNull()
                }
            }
            is JsonNull -> null
            else -> emptyList()
        }
    }
    
    override fun serialize(encoder: Encoder, value: List<WaitingRoomParticipant>?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            val jsonEncoder = encoder as JsonEncoder
            jsonEncoder.encodeJsonElement(JsonArray(value.map { 
                jsonEncoder.json.encodeToJsonElement(it) 
            }))
        }
    }
}

/**
 * Flexible serializer for List<Participant>
 * Handles socket.io JSONArray conversion issues
 */
object FlexibleParticipantListSerializer : KSerializer<List<Participant>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleParticipantList")
    
    override fun deserialize(decoder: Decoder): List<Participant> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                element.mapIndexedNotNull { index, item ->
                    runCatching {
                        jsonDecoder.json.decodeFromJsonElement<Participant>(item)
                    }.onFailure { error ->
                        Logger.e("CoreModels", "MediaSFU - Failed to decode participant at index $index: ${error.message}")
                    }.getOrNull()
                }
            }
            is JsonNull -> emptyList()
            else -> emptyList()
        }
    }
    
    override fun serialize(encoder: Encoder, value: List<Participant>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(JsonArray(value.map { 
            jsonEncoder.json.encodeToJsonElement(it) 
        }))
    }
}

/**
 * Flexible serializer for List<String>
 */
object FlexibleStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleStringList")
    
    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                element.mapNotNull { item ->
                    (item as? JsonPrimitive)?.contentOrNull
                }
            }
            is JsonNull -> emptyList()
            else -> emptyList()
        }
    }
    
    override fun serialize(encoder: Encoder, value: List<String>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(JsonArray(value.map { JsonPrimitive(it) }))
    }
}

/**
 * Flexible serializer for List<CoHostResponsibility>
 * Handles socket.io JSONArray conversion issues
 */
object FlexibleCoHostListSerializer : KSerializer<List<CoHostResponsibility>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleCoHostList")
    
    override fun deserialize(decoder: Decoder): List<CoHostResponsibility> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                element.mapIndexedNotNull { index, item ->
                    runCatching {
                        jsonDecoder.json.decodeFromJsonElement<CoHostResponsibility>(item)
                    }.onFailure { error ->
                        Logger.e("CoreModels", "MediaSFU - Failed to decode CoHostResponsibility at index $index: ${error.message}")
                    }.getOrNull()
                }
            }
            is JsonNull -> emptyList()
            else -> emptyList()
        }
    }
    
    override fun serialize(encoder: Encoder, value: List<CoHostResponsibility>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(JsonArray(value.map { 
            jsonEncoder.json.encodeToJsonElement(it) 
        }))
    }
}

/**
 * Flexible serializer for List<Request>
 * Handles socket.io JSONArray conversion issues
 */
object FlexibleRequestListSerializer : KSerializer<List<Request>> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FlexibleRequestList")
    
    override fun deserialize(decoder: Decoder): List<Request> {
        val jsonDecoder = decoder as? JsonDecoder ?: return emptyList()
        
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> {
                element.mapIndexedNotNull { index, item ->
                    runCatching {
                        jsonDecoder.json.decodeFromJsonElement<Request>(item)
                    }.onFailure { error ->
                        Logger.e("CoreModels", "MediaSFU - Failed to decode Request at index $index: ${error.message}")
                    }.getOrNull()
                }
            }
            is JsonNull -> emptyList()
            else -> emptyList()
        }
    }
    
    override fun serialize(encoder: Encoder, value: List<Request>) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(JsonArray(value.map { 
            jsonEncoder.json.encodeToJsonElement(it) 
        }))
    }
}

@Serializable
enum class EventType {
    CONFERENCE,
    WEBINAR,
    CHAT,
    BROADCAST,
    NONE
}

@Serializable
data class Participant(
    val id: String? = null,
    val audioID: String = "",
    val videoID: String = "",
    val ScreenID: String? = null,
    val ScreenOn: Boolean = false,
    val islevel: String? = null,
    val isAdmin: Boolean = false,
    val isHost: Boolean = false,
    val name: String,
    val muted: Boolean = false,
    val isBanned: Boolean = false,
    val isSuspended: Boolean = false,
    val useBoard: Boolean = false,
    val breakRoom: Int? = null,
    val videoOn: Boolean = false,
    val audioOn: Boolean = false,
    @Serializable(with = FlexibleRequestListSerializer::class)
    val requests: List<Request> = emptyList(),
    val extra: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class MediaStreamDescriptor(
    val id: String? = null,
    val producerId: String,
    val muted: Boolean = false,
    val name: String? = null,
    val audioId: String? = null,
    val videoId: String? = null,
    val extra: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class Stream(
    val id: String? = null,
    val producerId: String,
    val muted: Boolean? = null,
    @kotlinx.serialization.Contextual
    val stream: MediaStream? = null,
    @kotlinx.serialization.Contextual
    val socket_: Any? = null, // Socket placeholder
    val name: String? = null,
    val audioID: String? = null,
    val videoID: String? = null,
    val extra: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class Request(
    val id: String,
    val icon: String,
    val name: String? = null,
    val username: String? = null,
    val extra: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class RequestResponse(
    val id: String,
    val icon: String? = null,
    val name: String? = null,
    val username: String? = null,
    val action: String? = null,
    val type: String? = null,
    val extra: JsonObject = JsonObject(emptyMap())
) {
    companion object
}

@Serializable
data class AllMembersData(
    @Serializable(with = FlexibleParticipantListSerializer::class)
    val members: List<Participant> = emptyList(),
    @Serializable(with = FlexibleRequestListSerializer::class)
    val requests: List<Request> = emptyList(),
    @Serializable(with = FlexibleStringListSerializer::class)
    val settings: List<String> = emptyList(),
    val coHost: String? = null,
    @Serializable(with = FlexibleCoHostListSerializer::class)
    val coHostResponsibilities: List<CoHostResponsibility> = emptyList()
)

@Serializable
data class AllMembersRestData(
    @Serializable(with = FlexibleParticipantListSerializer::class)
    val members: List<Participant> = emptyList(),
    @Serializable(with = FlexibleStringListSerializer::class)
    val settings: List<String> = emptyList(),
    val coHost: String? = null,
    @Serializable(with = FlexibleCoHostListSerializer::class)
    val coHostResponsibilities: List<CoHostResponsibility> = emptyList()
)

@Serializable
data class UserWaitingData(
    val name: String? = null
)

@Serializable
data class PersonJoinedData(
    val name: String? = null
)

@Serializable
data class ParticipantRequestedData(
    val userRequest: Request? = null
)

@Serializable
data class AllWaitingRoomMembersData(
    @SerialName("waitingParticipants")
    @Serializable(with = FlexibleListSerializer::class)
    val waitingParticipants: List<WaitingRoomParticipant>? = null,
    @SerialName("waitingParticipantss")
    @Serializable(with = FlexibleListSerializer::class)
    val waitingParticipantss: List<WaitingRoomParticipant>? = null
)

@Serializable
data class MeetingEndedData(
    val redirectUrl: String? = null,
    val onWeb: Boolean? = null,
    val eventType: EventType? = null
)

@Serializable
data class CoHostResponsibility(
    val name: String,
    val value: Boolean,
    val dedicated: Boolean
)

@Serializable
data class Message(
    val sender: String,
    val receivers: List<String> = emptyList(),
    val message: String,
    val timestamp: String,
    val group: Boolean,
    val extra: JsonObject = JsonObject(emptyMap())
) {
    companion object
}

fun Message.toTransportMap(): Map<String, Any?> = mapOf(
    "sender" to sender,
    "receivers" to receivers,
    "message" to message,
    "timestamp" to timestamp,
    "group" to group,
    "extra" to extra
)

@Serializable
data class Poll(
    val id: String? = null,
    val question: String,
    val type: String? = null,
    val options: List<String> = emptyList(),
    val votes: List<Int> = emptyList(),
    val status: String? = null,
    val voters: Map<String, Int> = emptyMap()
)

fun Poll.toTransportMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "question" to question,
    "type" to type,
    "options" to options,
    "votes" to votes,
    "status" to status,
    "voters" to voters
)

@Serializable
data class PollUpdatedData(
    val polls: List<Poll>? = null,
    val poll: Poll,
    val status: String
) {
    companion object
}

fun PollUpdatedData.toTransportMap(): Map<String, Any?> = mapOf(
    "polls" to polls?.map { it.toTransportMap() },
    "poll" to poll.toTransportMap(),
    "status" to status
)

@Serializable
data class WaitingRoomParticipant(
    val name: String,
    val id: String
)

@Serializable
data class Settings(
    val settings: List<String>
) {
    companion object
}

@Serializable
data class MainSpecs(
    val mediaOptions: String,
    val audioOptions: String,
    val videoOptions: String,
    val videoType: String,
    val videoOptimized: Boolean,
    val recordingDisplayType: String,
    val addHls: Boolean
)

@Serializable
data class DispSpecs(
    val nameTags: Boolean,
    val backgroundColor: String,
    val nameTagsColor: String,
    val orientationVideo: String
)

@Serializable
data class TextSpecs(
    val addText: Boolean,
    val customText: String? = null,
    val customTextPosition: String? = null,
    val customTextColor: String? = null
)

@Serializable
data class UserRecordingParams(
    val mainSpecs: MainSpecs,
    val dispSpecs: DispSpecs,
    val textSpecs: TextSpecs? = null
) {
    companion object
}

fun UserRecordingParams.toTransportMap(): Map<String, Any?> = buildMap {
    put("mainSpecs", mapOf(
        "mediaOptions" to mainSpecs.mediaOptions,
        "audioOptions" to mainSpecs.audioOptions,
        "videoOptions" to mainSpecs.videoOptions,
        "videoType" to mainSpecs.videoType,
        "videoOptimized" to mainSpecs.videoOptimized,
        "recordingDisplayType" to mainSpecs.recordingDisplayType,
        "addHls" to mainSpecs.addHls
    ))
    put("dispSpecs", mapOf(
        "nameTags" to dispSpecs.nameTags,
        "backgroundColor" to dispSpecs.backgroundColor,
        "nameTagsColor" to dispSpecs.nameTagsColor,
        "orientationVideo" to dispSpecs.orientationVideo
    ))
    textSpecs?.let { specs ->
        put("textSpecs", mapOf(
            "addText" to specs.addText,
            "customText" to specs.customText,
            "customTextPosition" to specs.customTextPosition,
            "customTextColor" to specs.customTextColor
        ))
    }
}

@Serializable
data class RecordingParams(
    val recordingAudioPausesLimit: Int,
    val recordingAudioSupport: Boolean,
    val recordingAudioPeopleLimit: Int,
    val recordingAudioParticipantsTimeLimit: Int,
    val recordingVideoPausesLimit: Int,
    val recordingVideoSupport: Boolean,
    val recordingVideoPeopleLimit: Int,
    val recordingVideoParticipantsTimeLimit: Int,
    val recordingAllParticipantsSupport: Boolean,
    val recordingVideoParticipantsSupport: Boolean,
    val recordingAllParticipantsFullRoomSupport: Boolean,
    val recordingVideoParticipantsFullRoomSupport: Boolean,
    val recordingPreferredOrientation: String,
    val recordingSupportForOtherOrientation: Boolean,
    val recordingMultiFormatsSupport: Boolean,
    val recordingHlsSupport: Boolean,
    val recordingAudioPausesCount: Int? = null,
    val recordingVideoPausesCount: Int? = null
)

@Serializable
data class MeetingRoomParams(
    val itemPageLimit: Int,
    val mediaType: String,
    val addCoHost: Boolean,
    val targetOrientation: String,
    val targetOrientationHost: String,
    val targetResolution: String,
    val targetResolutionHost: String,
    val type: String,
    val audioSetting: String,
    val videoSetting: String,
    val screenshareSetting: String,
    val chatSetting: String
)

@Serializable
data class RoomCreationOptions(
    val action: String,
    @SerialName("meetingID")
    val meetingId: String,
    val durationMinutes: Int,
    val capacity: Int,
    val userName: String,
    val scheduledDateEpochMillis: Long? = null,
    val secureCode: String? = null,
    val eventType: EventType = EventType.NONE,
    val recordOnly: Boolean = false,
    val eventStatus: String = "inactive",
    val startIndex: Int = 0,
    val pageSize: Int = 50,
    val safeRoom: Boolean = false,
    val autoStartSafeRoom: Boolean = false,
    val safeRoomAction: String = "warn",
    val dataBuffer: Boolean = false,
    val bufferType: String = "all",
    val supportSip: Boolean = false,
    val directionSip: String = "both",
    val preferPcma: Boolean = false
)

@Serializable
data class SeedData(
    val member: String? = null,
    val host: String? = null,
    val eventType: EventType? = null,
    val participants: List<Participant> = emptyList(),
    val messages: List<Message> = emptyList(),
    val polls: List<Poll> = emptyList(),
    val breakoutRooms: List<List<Participant>> = emptyList(),
    val requests: List<Request> = emptyList(),
    val waitingList: List<WaitingRoomParticipant> = emptyList()
)

// ============================================================================
// Media Stream Configuration Types
// ============================================================================

/**
 * Media stream constraints for audio/video capture
 */
@Serializable
data class MediaStreamConstraints(
    val audio: AudioConstraints? = null,
    val video: VideoConstraints? = null
)

/**
 * Audio constraints for media capture
 */
@Serializable
data class AudioConstraints(
    val echoCancellation: Boolean = true,
    val noiseSuppression: Boolean = true,
    val autoGainControl: Boolean = true,
    val deviceId: String? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val latency: Double? = null,
    val volume: Double? = null
)

/**
 * Video constraints for media capture
 */
@Serializable
data class VideoConstraints(
    val width: VideoResolution? = null,
    val height: VideoResolution? = null,
    val frameRate: VideoFrameRate? = null,
    val facingMode: FacingMode? = null,
    val deviceId: String? = null,
    val aspectRatio: Double? = null
)

/**
 * Video resolution constraints
 */
@Serializable
data class VideoResolution(
    val ideal: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val exact: Int? = null
)

/**
 * Video frame rate constraints
 */
@Serializable
data class VideoFrameRate(
    val ideal: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val exact: Int? = null
)

/**
 * Camera facing mode
 */
@Serializable
enum class FacingMode {
    USER,
    ENVIRONMENT,
    LEFT,
    RIGHT;

    override fun toString(): String = name.lowercase()
}

/**
 * Media device information
 */
@Serializable
data class MediaDeviceInfo(
    val deviceId: String,
    val kind: MediaDeviceKind,
    val label: String,
    val groupId: String
)

/**
 * API credentials for MediaSFU authentication
 */
@Serializable
data class Credentials(
    val apiUserName: String = "",
    val apiKey: String = ""
)

/**
 * Media device kind
 */
@Serializable
enum class MediaDeviceKind {
    AUDIOINPUT,
    AUDIOOUTPUT,
    VIDEOINPUT;

    override fun toString(): String = name.lowercase()
}

// ============================================================================
// SDK Configuration Types
// ============================================================================

/**
 * Main SDK configuration
 */
@Serializable
data class MediaSfuConfig(
    val apiUrl: String = "https://mediasfu.com",
    val socketUrl: String = "https://mediasfu.com",
    val apiKey: String? = null,
    val apiUserName: String? = null,
    val apiToken: String? = null,
    val recordingConfig: RecordingConfig = RecordingConfig(),
    val mediaConfig: MediaConfig = MediaConfig(),
    val socketConfig: SocketConfig = SocketConfig()
)

/**
 * Recording configuration
 */
@Serializable
data class RecordingConfig(
    val recordingMediaOptions: String = "video",
    val recordingAudioOptions: String = "all",
    val recordingVideoOptions: String = "all",
    val recordingVideoType: String = "fullDisplay",
    val recordingDisplayType: String = "video",
    val recordingNameTags: Boolean = true,
    val recordingBackgroundColor: String = "#000000",
    val recordingNameTagsColor: String = "#FFFFFF",
    val recordingOrientationVideo: String = "landscape",
    val recordingAddHLS: Boolean = false,
    val recordingAddText: Boolean = false,
    val recordingCustomText: String = "",
    val recordingCustomTextPosition: String = "top",
    val recordingCustomTextColor: String = "#FFFFFF"
)

// Helper methods for data models
fun Message.Companion.fromMap(map: Map<String, Any?>): Message {
    return Message(
        sender = map["sender"] as? String ?: "",
        receivers = (map["receivers"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        message = map["message"] as? String ?: "",
        timestamp = map["timestamp"] as? String ?: "",
        group = map["group"] as? Boolean ?: false,
        extra = (map["extra"] as? JsonObject) ?: JsonObject(emptyMap())
    )
}

fun RequestResponse.Companion.fromMap(map: Map<String, Any?>): RequestResponse {
    return RequestResponse(
        id = map["id"] as? String ?: "",
        icon = map["icon"] as? String,
        name = map["name"] as? String,
        username = map["username"] as? String,
        action = map["action"] as? String,
        type = map["type"] as? String,
        extra = (map["extra"] as? JsonObject) ?: JsonObject(emptyMap())
    )
}

fun Settings.Companion.fromList(list: List<*>): Settings {
    return Settings(
        settings = list.mapNotNull { it as? String }
    )
}

fun UserRecordingParams.Companion.fromMap(map: Map<String, Any?>): UserRecordingParams {
    val mainSpecsMap = map["mainSpecs"] as? Map<String, Any?> ?: emptyMap()
    val dispSpecsMap = map["dispSpecs"] as? Map<String, Any?> ?: emptyMap()
    val textSpecsMap = map["textSpecs"] as? Map<String, Any?>

    val mainSpecs = MainSpecs(
        mediaOptions = mainSpecsMap["mediaOptions"] as? String ?: "video",
        audioOptions = mainSpecsMap["audioOptions"] as? String ?: "all",
        videoOptions = mainSpecsMap["videoOptions"] as? String ?: "all",
        videoType = mainSpecsMap["videoType"] as? String ?: "video",
        videoOptimized = mainSpecsMap["videoOptimized"] as? Boolean ?: false,
        recordingDisplayType = mainSpecsMap["recordingDisplayType"] as? String ?: "video",
        addHls = mainSpecsMap["addHls"] as? Boolean ?: false
    )

    val dispSpecs = DispSpecs(
        nameTags = dispSpecsMap["nameTags"] as? Boolean ?: true,
        backgroundColor = dispSpecsMap["backgroundColor"] as? String ?: "#000000",
        nameTagsColor = dispSpecsMap["nameTagsColor"] as? String ?: "#FFFFFF",
        orientationVideo = dispSpecsMap["orientationVideo"] as? String ?: "landscape"
    )

    val textSpecs = textSpecsMap?.let {
        TextSpecs(
            addText = it["addText"] as? Boolean ?: false,
            customText = it["customText"] as? String,
            customTextPosition = it["customTextPosition"] as? String,
            customTextColor = it["customTextColor"] as? String
        )
    }

    return UserRecordingParams(
        mainSpecs = mainSpecs,
        dispSpecs = dispSpecs,
        textSpecs = textSpecs
    )
}

fun PollUpdatedData.Companion.fromMap(map: Map<String, Any?>): PollUpdatedData {
    val optionJsonParser = Json { ignoreUnknownKeys = true }
    val preferredOptionKeys = listOf("option", "text", "label", "value", "name", "title")
    
    fun toAnyMap(raw: Any?): Map<String, Any?> = when (raw) {
        is Map<*, *> -> raw.entries
            .mapNotNull { (key, value) ->
                val safeKey = key?.toString() ?: return@mapNotNull null
                safeKey to value
            }
            .toMap()
        is JsonObject -> raw.entries.associate { entry -> entry.key to entry.value }
        else -> emptyMap()
    }

    fun asList(raw: Any?): List<Any?> {
        if (raw == null) return emptyList()
        
        return when (raw) {
            is List<*> -> raw
            is Array<*> -> raw.toList()
            is JsonArray -> raw.map { it }
            else -> listOf(raw)
        }
    }

    fun asInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toDoubleOrNull()?.toInt()
        is JsonPrimitive -> when {
            value.isString -> value.content.toDoubleOrNull()?.toInt()
            value.longOrNull != null -> value.longOrNull?.toInt()
            value.doubleOrNull != null -> value.doubleOrNull?.toInt()
            value.booleanOrNull != null -> value.booleanOrNull?.let { if (it) 1 else 0 }
            else -> null
        }
        else -> null
    }

    fun stringifyPrimitive(primitive: JsonPrimitive): String? {
        primitive.contentOrNull?.let { content ->
            val trimmed = content.trim()
            if (trimmed.isNotEmpty()) return trimmed
        }
        primitive.longOrNull?.let { return it.toString() }
        primitive.doubleOrNull?.let { return it.toString() }
        primitive.booleanOrNull?.let { return it.toString() }
        return null
    }

    fun collectOptionsFromJsonElement(element: JsonElement): List<String> = when (element) {
        is JsonArray -> element.flatMap { collectOptionsFromJsonElement(it) }
        is JsonObject -> {
            preferredOptionKeys.asSequence()
                .mapNotNull { key -> element[key]?.let { collectOptionsFromJsonElement(it) } }
                .firstOrNull { it.isNotEmpty() }
                ?: element.values.flatMap { collectOptionsFromJsonElement(it) }
        }
        is JsonPrimitive -> stringifyPrimitive(element)?.let(::listOf) ?: emptyList()
    }

    fun parseStringOptions(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[") || trimmed.startsWith("{") || (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
            val element = runCatching { optionJsonParser.parseToJsonElement(trimmed) }.getOrNull()
            when (element) {
                is JsonArray, is JsonObject -> {
                    val extracted = collectOptionsFromJsonElement(element)
                    if (extracted.isNotEmpty()) return extracted
                }
                else -> { /* fall through to manual parsing */ }
            }
        }

        val splitCandidates = trimmed
            .split('|', ',', ';')
            .map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }

        if (splitCandidates.size > 1) {
            return splitCandidates
        }

        return listOf(trimmed.trim('"', '\''))
    }

    fun normalizeOptionCandidate(candidate: Any?): List<String> {
        // Handle socket.io JSONArray (org.json.JSONArray) first
        val className = candidate?.let { it::class.simpleName }
        if (className == "JSONArray") {
            return try {
                val jsonString = candidate.toString()
                val element = optionJsonParser.parseToJsonElement(jsonString)
                if (element is JsonArray) {
                    collectOptionsFromJsonElement(element)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Logger.e("CoreModels", "MediaSFU - Error parsing JSONArray in normalizeOptionCandidate: ${e.message}")
                emptyList()
            }
        }
        
        return when (candidate) {
            null -> emptyList()
            is String -> parseStringOptions(candidate)
            is CharSequence -> parseStringOptions(candidate.toString())
            is Number -> listOf(candidate.toString())
            is Boolean -> listOf(candidate.toString())
            is JsonPrimitive -> {
                if (candidate.isString) {
                    parseStringOptions(candidate.content)
                } else {
                    stringifyPrimitive(candidate)?.let(::listOf) ?: emptyList()
                }
            }
            is JsonArray -> collectOptionsFromJsonElement(candidate)
            is JsonObject -> collectOptionsFromJsonElement(candidate)
            is Map<*, *> -> {
                preferredOptionKeys.asSequence()
                    .mapNotNull { key -> candidate[key]?.let { normalizeOptionCandidate(it) } }
                    .firstOrNull { it.isNotEmpty() }
                    ?: candidate.values.flatMap { normalizeOptionCandidate(it) }
            }
            is Iterable<*> -> candidate.flatMap { normalizeOptionCandidate(it) }
            is Array<*> -> candidate.flatMap { normalizeOptionCandidate(it) }
            else -> listOf(candidate.toString())
        }.map { it.trim().trim('"', '\'') }
            .filter { it.isNotEmpty() }
    }

    fun stringifyOption(option: Any?): String? = normalizeOptionCandidate(option).firstOrNull()

    fun extractOptions(raw: Any?): List<String> = normalizeOptionCandidate(raw)
        .distinct()

    fun parseVotes(raw: Any?): List<Int> = asList(raw)
        .mapNotNull { asInt(it) }

    fun parseVoters(raw: Any?): Map<String, Int> {
        val votersMap = toAnyMap(raw)
        if (votersMap.isEmpty()) return emptyMap()

        return votersMap.mapNotNull { (key, value) ->
            val intValue = asInt(value)
            intValue?.let { key to it }
        }.toMap()
    }

    fun buildPoll(raw: Any?): Poll? {
        val pollMap = toAnyMap(raw)
        if (pollMap.isEmpty()) return null

        val rawOptions = pollMap["options"]
        val extractedOptions = extractOptions(rawOptions)

        return Poll(
            id = pollMap["id"]?.toString()?.takeIf { it.isNotBlank() },
            question = pollMap["question"]?.toString()?.ifBlank { "" } ?: "",
            type = pollMap["type"]?.toString()?.takeIf { it.isNotBlank() },
            options = extractedOptions,
            votes = parseVotes(pollMap["votes"]),
            status = pollMap["status"]?.toString()?.takeIf { it.isNotBlank() },
            voters = parseVoters(pollMap["voters"])
        )
    }

    val poll = buildPoll(map["poll"]) ?: Poll(question = "", options = emptyList())

    val pollsRaw = map["polls"]
    val pollsList = pollsRaw?.let { raw ->
        asList(raw).mapNotNull { item -> buildPoll(item) }
    }

    val status = map["status"]?.toString().orEmpty()

    return PollUpdatedData(
        polls = pollsList,
        poll = poll,
        status = status
    )
}

// Additional socket event data models
@Serializable
data class UpdatedCoHostData(
    val coHost: String? = null,
    @Serializable(with = FlexibleCoHostListSerializer::class)
    val coHostResponsibilities: List<CoHostResponsibility> = emptyList()
)

@Serializable
data class AltDomains(
    val data: Map<String, @Contextual Any?> = emptyMap()
)

@Serializable
data class UpdateConsumingDomainsData(
    val domains: List<String> = emptyList(),
    val altDomains: AltDomains = AltDomains()
)

@Serializable
data class RecordParameters(
    val recordingAudioPausesLimit: Int = 0,
    val recordingAudioPausesCount: Int = 0,
    val recordingAudioSupport: Boolean = false,
    val recordingAudioPeopleLimit: Int = 0,
    val recordingAudioParticipantsTimeLimit: Int = 0,
    val recordingVideoPausesCount: Int = 0,
    val recordingVideoPausesLimit: Int = 0,
    val recordingVideoSupport: Boolean = false,
    val recordingVideoPeopleLimit: Int = 0,
    val recordingVideoParticipantsTimeLimit: Int = 0,
    val recordingAllParticipantsSupport: Boolean = false,
    val recordingVideoParticipantsSupport: Boolean = false,
    val recordingAllParticipantsFullRoomSupport: Boolean = false,
    val recordingVideoParticipantsFullRoomSupport: Boolean = false,
    val recordingPreferredOrientation: String = "landscape",
    val recordingSupportForOtherOrientation: Boolean = false,
    val recordingMultiFormatsSupport: Boolean = false
) {
    companion object
}

fun RecordParameters.Companion.fromMap(map: Map<String, Any?>): RecordParameters {
    return RecordParameters(
        recordingAudioPausesLimit = (map["recordingAudioPausesLimit"] as? Number)?.toInt() ?: 0,
        recordingAudioPausesCount = (map["recordingAudioPausesCount"] as? Number)?.toInt() ?: 0,
        recordingAudioSupport = map["recordingAudioSupport"] as? Boolean ?: false,
        recordingAudioPeopleLimit = (map["recordingAudioPeopleLimit"] as? Number)?.toInt() ?: 0,
        recordingAudioParticipantsTimeLimit = (map["recordingAudioParticipantsTimeLimit"] as? Number)?.toInt() ?: 0,
        recordingVideoPausesCount = (map["recordingVideoPausesCount"] as? Number)?.toInt() ?: 0,
        recordingVideoPausesLimit = (map["recordingVideoPausesLimit"] as? Number)?.toInt() ?: 0,
        recordingVideoSupport = map["recordingVideoSupport"] as? Boolean ?: false,
        recordingVideoPeopleLimit = (map["recordingVideoPeopleLimit"] as? Number)?.toInt() ?: 0,
        recordingVideoParticipantsTimeLimit = (map["recordingVideoParticipantsTimeLimit"] as? Number)?.toInt() ?: 0,
        recordingAllParticipantsSupport = map["recordingAllParticipantsSupport"] as? Boolean ?: false,
        recordingVideoParticipantsSupport = map["recordingVideoParticipantsSupport"] as? Boolean ?: false,
        recordingAllParticipantsFullRoomSupport = map["recordingAllParticipantsFullRoomSupport"] as? Boolean ?: false,
        recordingVideoParticipantsFullRoomSupport = map["recordingVideoParticipantsFullRoomSupport"] as? Boolean ?: false,
        recordingPreferredOrientation = map["recordingPreferredOrientation"] as? String ?: "landscape",
        recordingSupportForOtherOrientation = map["recordingSupportForOtherOrientation"] as? Boolean ?: false,
        recordingMultiFormatsSupport = map["recordingMultiFormatsSupport"] as? Boolean ?: false
    )
}

@Serializable
data class BreakoutRoomUpdatedData(
    val forHost: Boolean? = null,
    val newRoom: Int? = null,
    val members: List<Participant> = emptyList(),
    val breakoutRooms: List<List<@Contextual BreakoutParticipant>> = emptyList(),
    val status: String? = null
) {
    companion object
}

fun BreakoutRoomUpdatedData.Companion.fromMap(map: Map<String, Any?>): BreakoutRoomUpdatedData {
    val members = (map["members"] as? List<*>)?.mapNotNull { item ->
        (item as? Map<String, Any?>)?.let { participantMap ->
            Participant(
                name = participantMap["name"] as? String ?: "",
                islevel = participantMap["islevel"] as? String ?: "1",
                audioOn = participantMap["audioOn"] as? Boolean ?: false,
                videoOn = participantMap["videoOn"] as? Boolean ?: false,
                id = participantMap["id"] as? String ?: "",
                muted = participantMap["muted"] as? Boolean ?: false,
                audioID = participantMap["audioID"] as? String ?: "",
                videoID = participantMap["videoID"] as? String ?: ""
            )
        }
    } ?: emptyList()

    val breakoutRooms = (map["breakoutRooms"] as? List<*>)?.mapNotNull { roomItem ->
        (roomItem as? List<*>)?.mapNotNull { participantItem ->
            (participantItem as? Map<String, Any?>)?.let { participantMap ->
                BreakoutParticipant(
                    name = participantMap["name"] as? String ?: "",
                    breakRoom = (participantMap["breakRoom"] as? Number)?.toInt()
                )
            }
        }
    } ?: emptyList()

    return BreakoutRoomUpdatedData(
        forHost = map["forHost"] as? Boolean ?: false,
        newRoom = (map["newRoom"] as? Number)?.toInt(),
        members = members,
        breakoutRooms = breakoutRooms,
        status = map["status"] as? String
    )
}

/**
 * Media configuration
 */
@Serializable
data class MediaConfig(
    val autoStartAudio: Boolean = false,
    val autoStartVideo: Boolean = false,
    val autoStartScreenShare: Boolean = false,
    val audioPaused: Boolean = false,
    val videoPaused: Boolean = false,
    val screenSharePaused: Boolean = false,
    val chatSetting: String = "allow",
    val audioSetting: String = "allow",
    val videoSetting: String = "allow",
    val screenshareSetting: String = "allow"
)

/**
 * Socket.IO configuration
 */
@Serializable
data class SocketConfig(
    val reconnection: Boolean = true,
    val reconnectionAttempts: Int = Int.MAX_VALUE,
    val reconnectionDelay: Long = 1000,
    val reconnectionDelayMax: Long = 5000,
    val timeout: Long = 20000,
    val autoConnect: Boolean = true,
    val transports: List<String> = listOf("websocket", "polling")
)

/**
 * Type aliases for common callback patterns
 */
