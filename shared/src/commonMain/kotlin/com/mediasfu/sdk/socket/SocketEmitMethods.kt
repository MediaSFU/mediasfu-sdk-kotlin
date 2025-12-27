package com.mediasfu.sdk.socket
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.network.mediaSfuJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * Socket emit methods for room joining operations.
 *
 * These methods provide a higher-level API for joining MediaSFU rooms,
 * handling validation, event emission, and response parsing.
 */

/**
 * Helper function to parse ResponseJoinRoom from map.
 * 
 * Note: Complex types like RtpCapabilities, MeetingRoomParams, and RecordingParams 
 * are set to null for now. Full parsing will be implemented as needed.
 */
@Suppress("UNCHECKED_CAST")
private fun parseResponseJoinRoom(data: Map<String, Any?>): ResponseJoinRoom {
    val rawRtpCapabilities = data["rtpCapabilities"] ?: data["rtpCapabilities_"]
    val rtpCapabilities = rawRtpCapabilities.toRtpCapabilities()

    logCapsDebug(
        prefix = "MediaSFU - parseResponseJoinRoom",
        rawCaps = rawRtpCapabilities,
        parsed = rtpCapabilities
    )

    val meetingParamsMap = data["meetingRoomParams"].asStringMap()
        ?: data["eventRoomParams"].asStringMap()
        ?: data["meetingRoomParams_"]?.asStringMap()
    val recordingParamsMap = data["recordingParams"].asStringMap()
        ?: data["recordingParams_"]?.asStringMap()

    return ResponseJoinRoom(
        rtpCapabilities = rtpCapabilities,
        success = data["success"].asBoolean() ?: false,
        roomRecvIPs = data["roomRecvIPs"].asStringList()
            ?: data["roomRecvIPs_"]?.asStringList(),
        meetingRoomParams = meetingParamsMap?.toMeetingRoomParams(),
        recordingParams = recordingParamsMap?.toRecordingParams(),
        secureCode = data["secureCode"].asString(),
        recordOnly = data["recordOnly"].asBoolean(),
        isHost = data["isHost"].asBoolean(),
        safeRoom = data["safeRoom"].asBoolean(),
        autoStartSafeRoom = data["autoStartSafeRoom"].asBoolean(),
        safeRoomStarted = data["safeRoomStarted"].asBoolean(),
        safeRoomEnded = data["safeRoomEnded"].asBoolean(),
        reason = data["reason"].asString(),
        banned = data["banned"].asBoolean(),
        suspended = data["suspended"].asBoolean(),
        noAdmin = data["noAdmin"].asBoolean()
    )
}

/**
 * Helper function to parse ResponseJoinLocalRoom from map.
 * 
 * Note: Complex types like RtpCapabilities, MeetingRoomParams, and RecordingParams 
 * are set to null for now. Full parsing will be implemented as needed.
 */
@Suppress("UNCHECKED_CAST")
private fun parseResponseJoinLocalRoom(data: Map<String, Any?>): ResponseJoinLocalRoom {
    val rawRtpCapabilities = data["rtpCapabilities"] ?: data["rtpCapabilities_"]
    val rtpCapabilities = rawRtpCapabilities.toRtpCapabilities()

    logCapsDebug(
        prefix = "MediaSFU - parseResponseJoinLocalRoom",
        rawCaps = rawRtpCapabilities,
        parsed = rtpCapabilities
    )

    val meetingParamsMap = data["eventRoomParams"].asStringMap()
        ?: data["meetingRoomParams"].asStringMap()
        ?: data["meetingRoomParams_"]?.asStringMap()
    val recordingParamsMap = data["recordingParams"].asStringMap()
        ?: data["recordingParams_"]?.asStringMap()

    val recordingParams = recordingParamsMap?.toRecordingParams()

    return ResponseJoinLocalRoom(
        rtpCapabilities = rtpCapabilities,
        isHost = data["isHost"].asBoolean(),
        eventStarted = data["eventStarted"].asBoolean(),
        isBanned = data["isBanned"].asBoolean(),
        hostNotJoined = data["hostNotJoined"].asBoolean(),
        eventRoomParams = meetingParamsMap?.toMeetingRoomParams(),
        recordingParams = recordingParams,
        secureCode = data["secureCode"].asString(),
        mediasfuURL = data["mediasfuURL"].asString(),
        apiKey = data["apiKey"].asString(),
        apiUserName = data["apiUserName"].asString(),
        allowRecord = data["allowRecord"].asBoolean()
            ?: recordingParams?.let { it.recordingAudioSupport || it.recordingVideoSupport }
    )
}

/**
 * Options for joining a standard room.
 *
 * @property socket The socket manager instance for communication
 * @property roomName Name of the room (must start with 's' or 'p', at least 8 characters)
 * @property islevel User level ('0', '1', or '2')
 * @property member Member identifier (alphanumeric, at least 1 character)
 * @property sec Security token (exactly 64 characters, alphanumeric)
 * @property apiUserName API username (alphanumeric, at least 6 characters)
 */
data class JoinRoomOptions(
    val socket: SocketManager,
    val roomName: String,
    val islevel: String,
    val member: String,
    val sec: String,
    val apiUserName: String
)

/**
 * Options for joining a conference room.
 *
 * @property socket The socket manager instance for communication
 * @property roomName Name of the room (must start with 's' or 'p', at least 8 characters)
 * @property islevel User level ('0', '1', or '2')
 * @property member Member identifier (alphanumeric, at least 1 character)
 * @property sec Security token (exactly 64 characters, alphanumeric)
 * @property apiUserName API username (alphanumeric, at least 6 characters)
 */
data class JoinConRoomOptions(
    val socket: SocketManager,
    val roomName: String,
    val islevel: String,
    val member: String,
    val sec: String,
    val apiUserName: String
)

/**
 * Options for joining a local room.
 *
 * @property socket The socket manager instance for communication
 * @property roomName Name of the room (must start with 'm', at least 8 characters)
 * @property islevel User level ('0', '1', or '2')
 * @property member Member identifier (alphanumeric, at least 1 character)
 * @property sec Security token (exactly 32 characters for local rooms)
 * @property apiUserName API username (alphanumeric, at least 6 characters)
 */
data class JoinLocalRoomOptions(
    val socket: SocketManager,
    val roomName: String,
    val islevel: String,
    val member: String,
    val sec: String,
    val apiUserName: String
)

/**
 * Parameters for joining a Community Edition event using the local socket.
 */
data class JoinEventRoomParameters(
    val eventId: String,
    val userName: String,
    val secureCode: String? = null,
    val videoPreference: String? = null,
    val audioPreference: String? = null,
    val audioOutputPreference: String? = null
)

/**
 * Options wrapper for Community Edition join requests.
 */
data class JoinEventRoomOptions(
    val socket: SocketManager,
    val parameters: JoinEventRoomParameters
)

/**
 * Parameters required when creating a Community Edition event locally.
 */
data class CreateLocalRoomParameters(
    val eventId: String,
    val duration: Int,
    val capacity: Int,
    val userName: String,
    val scheduledDateIso: String,
    val secureCode: String,
    val waitRoom: Boolean? = null,
    val recordingParams: RecordingParams? = null,
    val eventRoomParams: MeetingRoomParams? = null,
    val videoPreference: String? = null,
    val audioPreference: String? = null,
    val audioOutputPreference: String? = null,
    val mediasfuURL: String? = null
)

/** Wrapper for Community Edition create requests. */
data class CreateLocalRoomOptions(
    val socket: SocketManager,
    val parameters: CreateLocalRoomParameters
)

/**
 * Simplified acknowledgment payload returned by local socket create/join operations.
 */
data class CreateJoinLocalRoomResponse(
    val success: Boolean,
    val secret: String,
    val reason: String? = null,
    val url: String? = null
)

/** Default Community Edition meeting configuration mirroring Flutter sample values. */
fun defaultMeetingRoomParams(): MeetingRoomParams = MeetingRoomParams(
    itemPageLimit = 4,
    mediaType = "video",
    addCoHost = false,
    targetOrientation = "landscape",
    targetOrientationHost = "landscape",
    targetResolution = "hd",
    targetResolutionHost = "hd",
    type = "conference",
    audioSetting = "allow",
    videoSetting = "allow",
    screenshareSetting = "allow",
    chatSetting = "allow"
)

/** Default Community Edition recording configuration used when server omits details. */
fun defaultRecordingParams(): RecordingParams = RecordingParams(
    recordingAudioPausesLimit = 0,
    recordingAudioSupport = false,
    recordingAudioPeopleLimit = 0,
    recordingAudioParticipantsTimeLimit = 0,
    recordingVideoPausesLimit = 0,
    recordingVideoSupport = false,
    recordingVideoPeopleLimit = 0,
    recordingVideoParticipantsTimeLimit = 0,
    recordingAllParticipantsSupport = false,
    recordingVideoParticipantsSupport = false,
    recordingAllParticipantsFullRoomSupport = false,
    recordingVideoParticipantsFullRoomSupport = false,
    recordingPreferredOrientation = "landscape",
    recordingSupportForOtherOrientation = false,
    recordingMultiFormatsSupport = false,
    recordingHlsSupport = false
)

/** Data emitted when the local socket reports a successful connection. */
data class ResponseLocalConnectionData(
    val socketId: String?,
    val mode: String?,
    val apiUserName: String?,
    val apiKey: String?,
    val allowRecord: Boolean,
    val mediasfuURL: String?,
    val eventRoomParams: MeetingRoomParams?,
    val recordingParams: RecordingParams?,
    val raw: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun from(payload: Map<String, Any?>): ResponseLocalConnectionData {
            val meetingParamsMap = payload["meetingRoomParams_"]?.asStringMap()
                ?: payload["meetingRoomParams"]?.asStringMap()
            val recordingParamsMap = payload["recordingParams_"]?.asStringMap()
                ?: payload["recordingParams"]?.asStringMap()

            val meetingParams = meetingParamsMap?.toMeetingRoomParams() ?: defaultMeetingRoomParams()
            val recordingParams = recordingParamsMap?.toRecordingParams() ?: defaultRecordingParams()

            return ResponseLocalConnectionData(
                socketId = payload["socketID"].asString() ?: payload["socketId"].asString(),
                mode = payload["mode"].asString(),
                apiUserName = payload["apiUserName"].asString(),
                apiKey = payload["apiKey"].asString(),
                allowRecord = payload["allowRecord"].asBoolean()
                    ?: recordingParams.recordingAudioSupport
                    || recordingParams.recordingVideoSupport,
                mediasfuURL = payload["mediasfuURL"].asString(),
                eventRoomParams = meetingParams,
                recordingParams = recordingParams,
                raw = payload
            )
        }

        val Empty = ResponseLocalConnectionData(
            socketId = null,
            mode = null,
            apiUserName = null,
            apiKey = null,
            allowRecord = false,
            mediasfuURL = null,
            eventRoomParams = null,
            recordingParams = null,
            raw = emptyMap()
        )
    }
}

/** Container pairing the connected socket with its connection metadata. */
data class ResponseLocalConnection(
    val socket: SocketManager,
    val data: ResponseLocalConnectionData
)

/** Parameters for connecting to the local Community Edition socket. */
data class ConnectLocalSocketOptions(
    val socket: SocketManager,
    val link: String,
    val config: SocketConfig = SocketConfig(
        reconnection = true,
        transports = listOf("websocket"),
        autoConnect = true
    ),
    val timeoutMillis: Long = 10000
)

/**
 * Validates an alphanumeric string (also allows underscore for member names like "SpaceHost_2").
 * @param value Value to validate
 * @param fieldName Name of the field (for error messages)
 * @throws SocketEmitException if validation fails
 */
private fun validateAlphanumeric(value: String, fieldName: String) {
    if (!value.matches(Regex("^[a-zA-Z0-9_]+$"))) {
        throw SocketEmitException("Invalid $fieldName. It should be alphanumeric (underscore allowed).")
    }
}

private fun normalizeLocalMediaUrl(link: String): String {
    val trimmed = link.trim().trimEnd('/')
    return if (trimmed.endsWith("/media")) trimmed else "$trimmed/media"
}

private fun Any?.asString(): String? = when (this) {
    is String -> this
    is Number -> this.toString()
    is Boolean -> this.toString()
    is JsonPrimitive -> when {
        this.isString -> this.contentOrNull
        this.booleanOrNull != null -> this.booleanOrNull?.toString()
        this.longOrNull != null -> this.longOrNull?.toString()
        this.doubleOrNull != null -> this.doubleOrNull?.toString()
        else -> this.contentOrNull
    }
    else -> null
}

private fun Any?.asInt(): Int? = when (this) {
    is Number -> this.toInt()
    is String -> this.toDoubleOrNull()?.toInt()
    is JsonPrimitive -> when {
        this.intOrNull != null -> this.intOrNull
        this.longOrNull != null -> this.longOrNull?.toInt()
        this.doubleOrNull != null -> this.doubleOrNull?.toInt()
        this.contentOrNull != null -> this.contentOrNull?.toDoubleOrNull()?.toInt()
        else -> null
    }
    else -> null
}

private fun Any?.asBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is Number -> this.toInt() != 0
    is String -> this.toBooleanStrictOrNull()
        ?: this.toDoubleOrNull()?.let { it != 0.0 }
    is JsonPrimitive -> when {
        this.booleanOrNull != null -> this.booleanOrNull
        this.isString -> this.contentOrNull?.let { value ->
            value.toBooleanStrictOrNull() ?: value.toDoubleOrNull()?.let { it != 0.0 }
        }
        this.longOrNull != null -> this.longOrNull?.let { it != 0L }
        this.doubleOrNull != null -> this.doubleOrNull?.let { it != 0.0 }
        else -> null
    }
    else -> null
}

private fun Any?.asStringMap(): Map<String, Any?>? = when (this) {
    is Map<*, *> -> buildMap {
        for ((key, value) in this@asStringMap) {
            val keyString = key as? String ?: continue
            put(keyString, value.unwrapPotentialJson())
        }
    }
    is JsonObject -> buildMap {
        for ((key, value) in this@asStringMap) {
            put(key, value.unwrapJsonElement())
        }
    }
    is JsonElement -> if (this is JsonObject) this.asStringMap() else null
    else -> null
}

private fun Any?.asStringList(): List<String>? = when (this) {
    is List<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asString() }
    is Array<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asString() }
    is Iterable<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asString() }
    is String -> listOf(this)
    is JsonArray -> this.mapNotNull { element -> element.unwrapJsonElement().asString() }
    else -> null
}

private fun Any?.asMapList(): List<Map<String, Any?>>? = when (this) {
    is List<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asStringMap() }
    is Array<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asStringMap() }
    is Iterable<*> -> this.mapNotNull { element -> element.unwrapPotentialJson().asStringMap() }
    is JsonArray -> this.mapNotNull { element -> element.unwrapJsonElement().asStringMap() }
    else -> null
}

private fun Any?.asMediaKind(): MediaKind? = when (this) {
    is MediaKind -> this
    is String -> runCatching { MediaKind.valueOf(this.uppercase()) }.getOrNull()
    is JsonPrimitive -> this.contentOrNull?.let { value ->
        runCatching { MediaKind.valueOf(value.uppercase()) }.getOrNull()
    }
    else -> null
}

private fun Any?.unwrapPotentialJson(): Any? = when (this) {
    is JsonElement -> this.unwrapJsonElement()
    is Map<*, *> -> this.asStringMap()
    is List<*> -> this.map { it.unwrapPotentialJson() }
    is Array<*> -> this.map { it.unwrapPotentialJson() }
    is Iterable<*> -> this.map { it.unwrapPotentialJson() }
    is Number -> this.normalizeNumber()
    else -> {
        val text = this?.toString()?.trim()
        if (!text.isNullOrEmpty() && (text.startsWith("{") && text.endsWith("}") || text.startsWith("[") && text.endsWith("]"))) {
            runCatching { mediaSfuJson.parseToJsonElement(text) }
                .getOrNull()
                ?.unwrapJsonElement()
                ?: this.normalizeScalarValue()
        } else {
            this.normalizeScalarValue()
        }
    }
}

private fun JsonElement.unwrapJsonElement(): Any? = when (this) {
    JsonNull -> null
    is JsonObject -> buildMap<String, Any?> {
        for ((key, value) in this@unwrapJsonElement) {
            put(key, value.unwrapJsonElement())
        }
    }
    is JsonArray -> this.map { it.unwrapJsonElement() }
    is JsonPrimitive -> when {
        this.booleanOrNull != null -> this.booleanOrNull
        this.longOrNull != null -> this.longOrNull?.normalizeNumber()
        this.doubleOrNull != null -> this.doubleOrNull?.normalizeNumber()
        this.isString -> this.contentOrNull?.normalizeStringValue()
        else -> null
    }
}

private fun Number.normalizeNumber(): Any = when (this) {
    is Byte, is Short, is Int -> this.toInt()
    is Long -> if (this in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) this.toInt() else this
    is Float, is Double -> {
        val doubleValue = this.toDouble()
        val intCandidate = doubleValue.toInt()
        if (doubleValue == intCandidate.toDouble()) intCandidate else doubleValue
    }
    else -> this
}

private fun Map<String, Any?>.toRtcpFeedback(): RtcpFeedback? {
    val type = this["type"].asString() ?: return null
    return RtcpFeedback(
        type = type,
        parameter = this["parameter"].asString()
    )
}

private fun Map<String, Any?>.toRtpCodecCapability(): RtpCodecCapability? {
    val kind = this["kind"].asMediaKind() ?: return null
    val mimeType = (this["mimeType"] ?: this["mime_type"]).asString() ?: return null
    val clockRate = (this["clockRate"] ?: this["clockrate"]).asInt() ?: return null

    return RtpCodecCapability(
        kind = kind,
        mimeType = mimeType,
        preferredPayloadType = this["preferredPayloadType"].asInt()
            ?: this["preferredPayloadtype"].asInt(),
        clockRate = clockRate,
        channels = this["channels"].asInt(),
        parameters = this["parameters"].asStringMap(),
        rtcpFeedback = (this["rtcpFeedback"] as? List<*>)
            ?.mapNotNull { it.asStringMap()?.toRtcpFeedback() }
    )
}

private fun Map<String, Any?>.toRtpHeaderExtensionCapability(): RtpHeaderExtensionCapability? {
    val kind = this["kind"].asMediaKind() ?: return null
    val uri = this["uri"].asString() ?: return null
    val preferredId = this["preferredId"].asInt() ?: return null

    return RtpHeaderExtensionCapability(
        kind = kind,
        uri = uri,
        preferredId = preferredId,
        preferredEncrypt = this["preferredEncrypt"].asBoolean(),
        direction = this["direction"].asString()
    )
}

private fun Any?.toRtpCapabilities(): RtpCapabilities? {
    val map = when (this) {
        is Map<*, *> -> this.asStringMap()
        is String -> runCatching { mediaSfuJson.parseToJsonElement(this) }
            .getOrNull()?.let { element ->
                when (element) {
                    is JsonObject -> element.toSocketMap()
                    else -> null
                }
            }
        is JsonObject -> this.toSocketMap()
        is JsonElement -> if (this is JsonObject) this.toSocketMap() else null
        else -> null
    } ?: return null

    val codecs = map["codecs"].asMapList()?.mapNotNull { entry ->
        entry.toRtpCodecCapability().also { parsed ->
            if (parsed == null) {
                Logger.e("SocketEmitMethods", "MediaSFU - toRtpCapabilities: invalid codec entry -> $entry")
            } else {
                val paramTypes = parsed.parameters?.mapValues { entry ->
                    entry.value?.let { it::class.simpleName } ?: "null"
                }
            }
        }
    } ?: emptyList()
    val extensions = map["headerExtensions"].asMapList()
        ?.mapNotNull { it.toRtpHeaderExtensionCapability() } ?: emptyList()
    val fecMechanisms = map["fecMechanisms"].asStringList() ?: emptyList()

    return RtpCapabilities(
        codecs = codecs,
        headerExtensions = extensions,
        fecMechanisms = fecMechanisms
    )
}

private fun logCapsDebug(
    prefix: String,
    rawCaps: Any?,
    parsed: RtpCapabilities?
) {
    val rawType = rawCaps?.let { it::class.simpleName }
    val codecCount = parsed?.codecs?.size ?: 0

    if (codecCount == 0 && rawCaps != null) {
        if (parsed?.codecs?.isEmpty() == true) {
        }
    }
}

private fun Any?.normalizeScalarValue(): Any? = when (this) {
    is String -> this.normalizeStringValue()
    is Number -> this.normalizeNumber()
    else -> this
}

private fun String.normalizeStringValue(): Any {
    val trimmed = this.trim()
    trimmed.toBooleanStrictOrNull()?.let { return it }
    trimmed.toLongOrNull()?.let { return it.normalizeNumber() }
    trimmed.toDoubleOrNull()?.let { return it.normalizeNumber() }
    return trimmed
}

private fun Map<String, Any?>.toMeetingRoomParams(): MeetingRoomParams? {
    val defaults = defaultMeetingRoomParams()
    val itemPageLimit = this["itemPageLimit"].asInt() ?: defaults.itemPageLimit
    val mediaType = this["mediaType"].asString() ?: defaults.mediaType
    val addCoHost = this["addCoHost"].asBoolean() ?: false
    val targetOrientation = this["targetOrientation"].asString() ?: defaults.targetOrientation
    val targetOrientationHost = this["targetOrientationHost"].asString() ?: defaults.targetOrientationHost
    val targetResolution = this["targetResolution"].asString() ?: defaults.targetResolution
    val targetResolutionHost = this["targetResolutionHost"].asString() ?: defaults.targetResolutionHost
    val type = this["type"].asString() ?: defaults.type
    val audioSetting = this["audioSetting"].asString() ?: defaults.audioSetting
    val videoSetting = this["videoSetting"].asString() ?: defaults.videoSetting
    val screenshareSetting = this["screenshareSetting"].asString() ?: defaults.screenshareSetting
    val chatSetting = this["chatSetting"].asString() ?: defaults.chatSetting

    return MeetingRoomParams(
        itemPageLimit = itemPageLimit,
        mediaType = mediaType,
        addCoHost = addCoHost,
        targetOrientation = targetOrientation,
        targetOrientationHost = targetOrientationHost,
        targetResolution = targetResolution,
        targetResolutionHost = targetResolutionHost,
        type = type,
        audioSetting = audioSetting,
        videoSetting = videoSetting,
        screenshareSetting = screenshareSetting,
        chatSetting = chatSetting
    )
}

private fun Map<String, Any?>.toRecordingParams(): RecordingParams {
    val defaults = defaultRecordingParams()
    return RecordingParams(
        recordingAudioPausesLimit = this["recordingAudioPausesLimit"].asInt() ?: defaults.recordingAudioPausesLimit,
        recordingAudioSupport = this["recordingAudioSupport"].asBoolean() ?: defaults.recordingAudioSupport,
        recordingAudioPeopleLimit = this["recordingAudioPeopleLimit"].asInt() ?: defaults.recordingAudioPeopleLimit,
        recordingAudioParticipantsTimeLimit = this["recordingAudioParticipantsTimeLimit"].asInt() ?: defaults.recordingAudioParticipantsTimeLimit,
        recordingVideoPausesLimit = this["recordingVideoPausesLimit"].asInt() ?: defaults.recordingVideoPausesLimit,
        recordingVideoSupport = this["recordingVideoSupport"].asBoolean() ?: defaults.recordingVideoSupport,
        recordingVideoPeopleLimit = this["recordingVideoPeopleLimit"].asInt() ?: defaults.recordingVideoPeopleLimit,
        recordingVideoParticipantsTimeLimit = this["recordingVideoParticipantsTimeLimit"].asInt() ?: defaults.recordingVideoParticipantsTimeLimit,
        recordingAllParticipantsSupport = this["recordingAllParticipantsSupport"].asBoolean() ?: defaults.recordingAllParticipantsSupport,
        recordingVideoParticipantsSupport = this["recordingVideoParticipantsSupport"].asBoolean() ?: defaults.recordingVideoParticipantsSupport,
        recordingAllParticipantsFullRoomSupport = this["recordingAllParticipantsFullRoomSupport"].asBoolean() ?: defaults.recordingAllParticipantsFullRoomSupport,
        recordingVideoParticipantsFullRoomSupport = this["recordingVideoParticipantsFullRoomSupport"].asBoolean() ?: defaults.recordingVideoParticipantsFullRoomSupport,
        recordingPreferredOrientation = this["recordingPreferredOrientation"].asString() ?: defaults.recordingPreferredOrientation,
        recordingSupportForOtherOrientation = this["recordingSupportForOtherOrientation"].asBoolean() ?: defaults.recordingSupportForOtherOrientation,
        recordingMultiFormatsSupport = this["recordingMultiFormatsSupport"].asBoolean() ?: defaults.recordingMultiFormatsSupport,
        recordingHlsSupport = this["recordingHlsSupport"].asBoolean() ?: defaults.recordingHlsSupport,
        recordingAudioPausesCount = this["recordingAudioPausesCount"].asInt(),
        recordingVideoPausesCount = this["recordingVideoPausesCount"].asInt()
    )
}

/** Establishes the local socket connection and waits for the success event. */
suspend fun connectLocalSocket(options: ConnectLocalSocketOptions): Result<ResponseLocalConnection> {
    val link = options.link.trim()
    if (link.isEmpty()) {
        return Result.failure(SocketEmitException("Socket link required."))
    }

    val targetUrl = normalizeLocalMediaUrl(link)

    if (options.socket.isConnected()) {
        return Result.success(
            ResponseLocalConnection(options.socket, ResponseLocalConnectionData.Empty)
        )
    }

    val connectResult = options.socket.connect(targetUrl, options.config)
    if (connectResult.isFailure) {
        val cause = connectResult.exceptionOrNull()
        val message = cause?.message ?: "Failed to connect to $targetUrl"
        return Result.failure(SocketEmitException(message, cause))
    }

    val deferred = CompletableDeferred<Map<String, Any?>>()
    val handler: suspend (Map<String, Any?>) -> Unit = { data ->
        if (!deferred.isCompleted) {
            deferred.complete(data)
        }
    }

    options.socket.on("connection-success", handler)

    return try {
        val payload = withTimeout(options.timeoutMillis) { deferred.await() }
        val data = ResponseLocalConnectionData.from(payload)
        Result.success(ResponseLocalConnection(options.socket, data))
    } catch (e: TimeoutCancellationException) {
        Result.failure(SocketEmitException("Timed out waiting for connection-success", e))
    } catch (e: Exception) {
        val message = e.message ?: "Unknown error"
        val exception = if (e is SocketEmitException) e else SocketEmitException("Error connecting to local socket: $message", e)
        Result.failure(exception)
    } finally {
        options.socket.off("connection-success")
    }
}

/**
 * Joins a Community Edition event room via the local socket.
 */
suspend fun joinEventRoom(options: JoinEventRoomOptions): Result<CreateJoinLocalRoomResponse> {
    return try {
        val params = options.parameters
        if (params.eventId.isBlank()) {
            throw SocketEmitException("Event ID cannot be empty")
        }
        if (params.userName.isBlank()) {
            throw SocketEmitException("User name cannot be empty")
        }

        val data = mutableMapOf<String, Any?>(
            "eventID" to params.eventId,
            "userName" to params.userName
        )

        data["secureCode"] = params.secureCode ?: ""
        params.videoPreference?.let { data["videoPreference"] = it }
        params.audioPreference?.let { data["audioPreference"] = it }
        params.audioOutputPreference?.let { data["audioOutputPreference"] = it }

        val ackResult = runCatching {
            withTimeout(30000) {
                options.socket.emitWithAck<Map<String, Any?>>("joinEventRoom", data)
            }
        }

        ackResult.fold(
            onSuccess = { response ->
                val success = response["success"] as? Boolean ?: false
                val secret = response["secret"] as? String ?: ""
                val reason = response["reason"] as? String
                val url = response["url"] as? String

                if (!success) {
                    throw SocketEmitException(reason ?: "Failed to join event room")
                }

                if (secret.isBlank()) {
                    throw SocketEmitException("Missing secret in joinEventRoom response")
                }

                Result.success(
                    CreateJoinLocalRoomResponse(
                        success = true,
                        secret = secret,
                        reason = reason,
                        url = url
                    )
                )
            },
            onFailure = { error ->
                val message = error.message ?: "Unknown error"
                throw SocketEmitException("Failed to join event room: $message", error)
            }
        )
    } catch (e: Exception) {
        Result.failure(
            if (e is SocketEmitException) e
            else SocketEmitException("Error joining event room: ${e.message ?: "Unknown error"}", e)
        )
    }
}

/**
 * Creates a Community Edition event via the local socket.
 */
suspend fun createLocalRoom(options: CreateLocalRoomOptions): Result<CreateJoinLocalRoomResponse> {
    return try {
        val params = options.parameters

        if (params.eventId.isBlank()) {
            throw SocketEmitException("Event ID cannot be empty")
        }
        if (params.userName.isBlank()) {
            throw SocketEmitException("User name cannot be empty")
        }
        if (params.duration <= 0) {
            throw SocketEmitException("Duration must be positive")
        }
        if (params.capacity <= 0) {
            throw SocketEmitException("Capacity must be positive")
        }

        val data = mutableMapOf<String, Any?>(
            "eventID" to params.eventId,
            "duration" to params.duration,
            "capacity" to params.capacity,
            "userName" to params.userName,
            "scheduledDate" to params.scheduledDateIso,
            "secureCode" to params.secureCode
        )

        params.waitRoom?.let { data["waitRoom"] = it }
        params.recordingParams?.let { data["recordingParams"] = encodeToSocketMap(it) }
        params.eventRoomParams?.let { data["eventRoomParams"] = encodeToSocketMap(it) }
        params.videoPreference?.let { data["videoPreference"] = it }
        params.audioPreference?.let { data["audioPreference"] = it }
        params.audioOutputPreference?.let { data["audioOutputPreference"] = it }
        params.mediasfuURL?.let { data["mediasfuURL"] = it }

        val ackResult = runCatching {
            withTimeout(30000) {
                options.socket.emitWithAck<Map<String, Any?>>("createRoom", data)
            }
        }

        ackResult.fold(
            onSuccess = { response ->
                val success = response["success"] as? Boolean ?: false
                val secret = response["secret"] as? String ?: ""
                val reason = response["reason"] as? String
                val url = response["url"] as? String

                if (!success) {
                    throw SocketEmitException(reason ?: "Failed to create event room")
                }

                if (secret.isBlank()) {
                    throw SocketEmitException("Missing secret in createRoom response")
                }

                Result.success(
                    CreateJoinLocalRoomResponse(
                        success = true,
                        secret = secret,
                        reason = reason,
                        url = url
                    )
                )
            },
            onFailure = { error ->
                val message = error.message ?: "Unknown error"
                throw SocketEmitException("Failed to create local room: $message", error)
            }
        )
    } catch (e: Exception) {
        Result.failure(
            if (e is SocketEmitException) e
            else SocketEmitException("Error creating local room: ${e.message ?: "Unknown error"}", e)
        )
    }
}

/**
 * Joins a standard media room.
 *
 * Validates input parameters and emits a 'joinRoom' event to the server.
 * Waits for acknowledgment with room details including RTP capabilities.
 *
 * @param options Join room configuration
 * @return ResponseJoinRoom containing room details and capabilities
 * @throws SocketEmitException if validation fails or server returns an error
 *
 * Example usage:
 * ```kotlin
 * val socket = createSocketManager()
 * socket.connect("https://mediasfu.com")
 *
 * val options = JoinRoomOptions(
 *     socket = socket,
 *     roomName = "s12345678",
 *     islevel = "1",
 *     member = "user123",
 *     sec = "64CharacterLongSecretHere...",
 *     apiUserName = "apiUser"
 * )
 *
 * try {
 *     val response = joinRoom(options)
 * } catch (e: SocketEmitException) {
 *     Logger.e("SocketEmitMethods", "Failed to join: ${e.message}")
 * }
 * ```
 */
suspend fun joinRoom(options: JoinRoomOptions): Result<ResponseJoinRoom> {
    return try {
        // Input validation
        if (options.sec.isEmpty() ||
            options.roomName.isEmpty() ||
            options.islevel.isEmpty() ||
            options.apiUserName.isEmpty() ||
            options.member.isEmpty()
        ) {
            throw SocketEmitException("Missing required parameters")
        }

        // Alphanumeric validation
        validateAlphanumeric(options.roomName, "roomName")
        validateAlphanumeric(options.apiUserName, "apiUserName")
        validateAlphanumeric(options.member, "member")

        // Validate roomName prefix (cloud rooms start with 's' or 'p')
        if (!(options.roomName.startsWith("s") || options.roomName.startsWith("p"))) {
            throw SocketEmitException("Invalid roomName, must start with 's' or 'p'")
        }

        // Additional constraints mirroring Flutter implementation
        if (!(options.sec.length == 64 &&
                    options.roomName.length >= 8 &&
                    options.islevel.length == 1 &&
                    options.apiUserName.length >= 6 &&
                    (options.islevel == "0" || options.islevel == "1" || options.islevel == "2"))
        ) {
            throw SocketEmitException("Invalid roomName, islevel, apiUserName, or secret format")
        }

        // Emit with acknowledgment
        val data = mapOf(
            "roomName" to options.roomName,
            "islevel" to options.islevel,
            "member" to options.member,
            "sec" to options.sec,
            "apiUserName" to options.apiUserName
        )

        val ackResult = runCatching {
            withTimeout(30000) {
                options.socket.emitWithAck<Map<String, Any?>>("joinRoom", data)
            }
        }

        ackResult.fold(
            onSuccess = { responseData ->
                // Check for error conditions
                if (responseData["rtpCapabilities"] == null) {
                    when {
                        responseData["banned"] == true ->
                            throw SocketEmitException("User is banned.")
                        responseData["suspended"] == true ->
                            throw SocketEmitException("User is suspended.")
                        responseData["noAdmin"] == true ->
                            throw SocketEmitException("Host has not joined the room yet.")
                        else ->
                            throw SocketEmitException("Failed to join room")
                    }
                }

                // Parse response
                Result.success(parseResponseJoinRoom(responseData))
            },
            onFailure = { error ->
                throw SocketEmitException("Failed to join room: ${error.message}", error)
            }
        )
    } catch (e: Exception) {
        Result.failure(
            if (e is SocketEmitException) e
            else SocketEmitException("Error joining room: ${e.message}", e)
        )
    }
}

/**
 * Joins a conference room.
 *
 * Similar to joinRoom but uses 'joinConRoom' event. Typically used for
 * consume-only participants in a conference.
 *
 * @param options Join conference room configuration
 * @return ResponseJoinRoom containing room details and capabilities
 * @throws SocketEmitException if validation fails or server returns an error
 *
 * Example usage:
 * ```kotlin
 * val socket = createSocketManager()
 * socket.connect("https://mediasfu.com")
 *
 * val options = JoinConRoomOptions(
 *     socket = socket,
 *     roomName = "s12345678",
 *     islevel = "1",
 *     member = "user123",
 *     sec = "64CharacterLongSecretHere...",
 *     apiUserName = "apiUser"
 * )
 *
 * try {
 *     val response = joinConRoom(options)
 * } catch (e: SocketEmitException) {
 *     Logger.e("SocketEmitMethods", "Failed to join: ${e.message}")
 * }
 * ```
 */
suspend fun joinConRoom(options: JoinConRoomOptions): Result<ResponseJoinRoom> {
    return try {
        // Input validation
        if (options.sec.isEmpty() ||
            options.roomName.isEmpty() ||
            options.islevel.isEmpty() ||
            options.apiUserName.isEmpty() ||
            options.member.isEmpty()
        ) {
            throw SocketEmitException("Missing required parameters")
        }

        // Alphanumeric validation
        validateAlphanumeric(options.roomName, "roomName")
        validateAlphanumeric(options.apiUserName, "apiUserName")
        validateAlphanumeric(options.member, "member")

        if (!(options.roomName.startsWith("s") || options.roomName.startsWith("p"))) {
            throw SocketEmitException("Invalid roomName, must start with 's' or 'p'")
        }

        if (!(options.sec.length == 64 &&
                    options.roomName.length >= 8 &&
                    options.islevel.length == 1 &&
                    options.apiUserName.length >= 6 &&
                    (options.islevel == "0" || options.islevel == "1" || options.islevel == "2"))
        ) {
            throw SocketEmitException("Invalid roomName, islevel, apiUserName, or secret format")
        }

        // Emit with acknowledgment
        val data = mapOf(
            "roomName" to options.roomName,
            "islevel" to options.islevel,
            "member" to options.member,
            "sec" to options.sec,
            "apiUserName" to options.apiUserName
        )

        val ackResult = runCatching {
            withTimeout(30000) {
                options.socket.emitWithAck<Map<String, Any?>>("joinConRoom", data)
            }
        }

        ackResult.fold(
            onSuccess = { responseData ->
                // Check for error conditions
                if (responseData["rtpCapabilities"] == null) {
                    when {
                        responseData["banned"] == true ->
                            throw SocketEmitException("User is banned.")
                        responseData["suspended"] == true ->
                            throw SocketEmitException("User is suspended.")
                        responseData["noAdmin"] == true ->
                            throw SocketEmitException("Host has not joined the room yet.")
                        else ->
                            throw SocketEmitException("Failed to join room")
                    }
                }

                // Parse response
                Result.success(parseResponseJoinRoom(responseData))
            },
            onFailure = { error ->
                throw SocketEmitException("Failed to join conference room: ${error.message}", error)
            }
        )
    } catch (e: Exception) {
        Result.failure(
            if (e is SocketEmitException) e
            else SocketEmitException("Error joining conference room: ${e.message}", e)
        )
    }
}

/**
 * Joins a local room.
 *
 * Used for local development or self-hosted MediaSFU instances.
 * Room names must start with 'm' and security tokens are 32 characters.
 *
 * @param options Join local room configuration
 * @return ResponseJoinLocalRoom containing room details and capabilities
 * @throws SocketEmitException if validation fails or server returns an error
 *
 * Example usage:
 * ```kotlin
 * val socket = createSocketManager()
 * socket.connect("http://localhost:3000")
 *
 * val options = JoinLocalRoomOptions(
 *     socket = socket,
 *     roomName = "m12345678",
 *     islevel = "1",
 *     member = "user123",
 *     sec = "32CharacterLongSecretHere...",
 *     apiUserName = "apiUser"
 * )
 *
 * try {
 *     val response = joinLocalRoom(options)
 * } catch (e: SocketEmitException) {
 *     Logger.e("SocketEmitMethods", "Failed to join: ${e.message}")
 * }
 * ```
 */
suspend fun joinLocalRoom(options: JoinLocalRoomOptions): Result<ResponseJoinLocalRoom> {
    return try {
        // Input validation
        if (options.sec.isEmpty() ||
            options.roomName.isEmpty() ||
            options.islevel.isEmpty() ||
            options.apiUserName.isEmpty() ||
            options.member.isEmpty()
        ) {
            throw SocketEmitException("Missing required parameters")
        }

        // Alphanumeric validation
        validateAlphanumeric(options.roomName, "roomName")
        validateAlphanumeric(options.apiUserName, "apiUserName")
        validateAlphanumeric(options.member, "member")

        // Validate roomName prefix for local rooms
        if (!options.roomName.startsWith("m")) {
            throw SocketEmitException("Invalid roomName for local room, must start with 'm'")
        }

        // Additional constraints (note: local rooms use 32-char secret)
        if (!(options.sec.length == 32 &&
                    options.roomName.length >= 8 &&
                    options.islevel.length == 1 &&
                    options.apiUserName.length >= 6 &&
                    (options.islevel == "0" || options.islevel == "1" || options.islevel == "2"))
        ) {
            throw SocketEmitException(
                "Invalid roomName, islevel, apiUserName, or secret format"
            )
        }

        // Emit with acknowledgment
        val data = mapOf(
            "roomName" to options.roomName,
            "islevel" to options.islevel,
            "member" to options.member,
            "sec" to options.sec,
            "apiUserName" to options.apiUserName
        )

        val ackResult = runCatching {
            withTimeout(30000) {
                options.socket.emitWithAck<Map<String, Any?>>("joinRoom", data)
            }
        }

        ackResult.fold(
            onSuccess = { responseData ->
                // Check for error conditions
                when {
                    responseData["isBanned"] == true ->
                        throw SocketEmitException("User is banned.")
                    responseData["hostNotJoined"] == true ->
                        throw SocketEmitException("Host has not joined the room yet.")
                    responseData["rtpCapabilities"] == null ->
                        throw SocketEmitException("Failed to join local room")
                }

                // Parse response
                Result.success(parseResponseJoinLocalRoom(responseData))
            },
            onFailure = { error ->
                throw SocketEmitException("Failed to join local room: ${error.message}", error)
            }
        )
    } catch (e: Exception) {
        Result.failure(
            if (e is SocketEmitException) e
            else SocketEmitException("Error joining local room: ${e.message}", e)
        )
    }
}

// -------------------------------------------------------------------------
// Helper conversion utilities
// -------------------------------------------------------------------------

private inline fun <reified T> encodeToSocketMap(value: T): Map<String, Any?> {
    val jsonObject = mediaSfuJson.encodeToJsonElement(value).jsonObject
    return jsonObject.toSocketMap()
}

private fun JsonObject.toSocketMap(): Map<String, Any?> = entries.associate { (key, value) ->
    key to value.toSocketValue()
}

private fun JsonElement.toSocketValue(): Any? = when (this) {
    JsonNull -> null
    is JsonPrimitive -> booleanOrNull ?: longOrNull ?: doubleOrNull ?: contentOrNull
    is JsonObject -> this.toSocketMap()
    is JsonArray -> this.map { it.toSocketValue() }
}
