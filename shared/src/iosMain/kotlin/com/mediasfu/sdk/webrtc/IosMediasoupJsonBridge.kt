package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.network.mediaSfuJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun Any?.toIosBridgeJsonString(): String? = when (this) {
    null -> null
    is String -> this
    is Number, is Boolean -> this.toString()
    is Map<*, *> -> mapToJsonElement(this).toString()
    is List<*> -> listToJsonElement(this).toString()
    else -> this.toString()
}

internal fun parseDtlsParametersBridgeJson(json: String): DtlsParameters {
    val jsonObject = mediaSfuJson.parseToJsonElement(json).jsonObject
    val roleValue = jsonObject["role"]?.jsonPrimitive?.contentOrNull?.uppercase() ?: "AUTO"
    val role = runCatching { DtlsRole.valueOf(roleValue) }.getOrDefault(DtlsRole.AUTO)
    val fingerprints = jsonObject["fingerprints"]?.jsonArray?.mapNotNull { item ->
        val entry = item as? JsonObject ?: return@mapNotNull null
        val algorithm = entry["algorithm"]?.jsonPrimitive?.contentOrNull
        val value = entry["value"]?.jsonPrimitive?.contentOrNull
        if (algorithm.isNullOrBlank() || value.isNullOrBlank()) {
            null
        } else {
            DtlsFingerprint(algorithm = algorithm, value = value)
        }
    } ?: emptyList()
    return DtlsParameters(role = role, fingerprints = fingerprints)
}

internal fun parseRtpParametersBridgeJson(json: String): RtpParameters {
    val jsonObject = mediaSfuJson.parseToJsonElement(json).jsonObject
    val mid = jsonObject["mid"]?.jsonPrimitive?.contentOrNull
    val rtcp = jsonObject["rtcp"]?.jsonObject?.let { rtcpObject ->
        RtcpParameters(
            cname = rtcpObject["cname"]?.jsonPrimitive?.contentOrNull,
            reducedSize = rtcpObject["reducedSize"]?.jsonPrimitive?.booleanOrNull,
            mux = rtcpObject["mux"]?.jsonPrimitive?.booleanOrNull
        )
    }

    val codecs = jsonObject["codecs"]?.jsonArray?.mapNotNull { item ->
        val codecObject = item as? JsonObject ?: return@mapNotNull null
        val mimeType = codecObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val payloadType = codecObject["payloadType"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val clockRate = codecObject["clockRate"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val channels = codecObject["channels"]?.jsonPrimitive?.intOrNull
        val parameters = codecObject["parameters"]?.jsonObject
            ?.mapValues { (_, value) -> value.jsonPrimitive.content }
            ?: emptyMap()
        RtpCodecParameters(
            mimeType = mimeType,
            payloadType = payloadType,
            clockRate = clockRate,
            channels = channels,
            parameters = parameters
        )
    } ?: emptyList()

    val headerExtensions = jsonObject["headerExtensions"]?.jsonArray?.mapNotNull { item ->
        val headerObject = item as? JsonObject ?: return@mapNotNull null
        val uri = headerObject["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val id = headerObject["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        RtpHeaderExtensionParameters(
            uri = uri,
            id = id,
            encrypt = headerObject["encrypt"]?.jsonPrimitive?.booleanOrNull ?: false
        )
    } ?: emptyList()

    val encodings = jsonObject["encodings"]?.jsonArray?.mapNotNull { item ->
        val encodingObject = item as? JsonObject ?: return@mapNotNull null
        RtpEncodingParameters(
            ssrc = encodingObject["ssrc"]?.jsonPrimitive?.longOrNull,
            rid = encodingObject["rid"]?.jsonPrimitive?.contentOrNull,
            active = encodingObject["active"]?.jsonPrimitive?.booleanOrNull ?: true,
            maxBitrate = encodingObject["maxBitrate"]?.jsonPrimitive?.intOrNull,
            minBitrate = encodingObject["minBitrate"]?.jsonPrimitive?.intOrNull,
            maxFramerate = encodingObject["maxFramerate"]?.jsonPrimitive?.doubleOrNull,
            scalabilityMode = encodingObject["scalabilityMode"]?.jsonPrimitive?.contentOrNull,
            scaleResolutionDownBy = encodingObject["scaleResolutionDownBy"]?.jsonPrimitive?.doubleOrNull,
            dtx = encodingObject["dtx"]?.jsonPrimitive?.booleanOrNull
        )
    } ?: emptyList()

    return RtpParameters(
        mid = mid,
        codecs = codecs,
        headerExtensions = headerExtensions,
        encodings = encodings,
        rtcp = rtcp
    )
}

internal fun parseRtpCapabilitiesBridgeJson(json: String): RtpCapabilities {
    val jsonObject = mediaSfuJson.parseToJsonElement(json).jsonObject

    val codecs = jsonObject["codecs"]?.jsonArray?.mapNotNull { item ->
        val codecObject = item as? JsonObject ?: return@mapNotNull null
        val kind = codecObject["kind"]?.jsonPrimitive?.contentOrNull?.toMediaKindOrNull() ?: return@mapNotNull null
        val mimeType = codecObject["mimeType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val clockRate = codecObject["clockRate"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        val preferredPayloadType = codecObject["preferredPayloadType"]?.jsonPrimitive?.intOrNull
        val channels = codecObject["channels"]?.jsonPrimitive?.intOrNull
        val parameters = codecObject["parameters"]?.jsonObject
            ?.mapNotNull { (key, value) ->
                val normalized = jsonElementToAny(value)?.toString() ?: return@mapNotNull null
                key to normalized
            }
            ?.toMap()
            ?: emptyMap()
        val rtcpFeedback = codecObject["rtcpFeedback"]?.jsonArray?.mapNotNull { feedbackItem ->
            val feedbackObject = feedbackItem as? JsonObject ?: return@mapNotNull null
            val type = feedbackObject["type"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            RtcpFeedback(
                type = type,
                parameter = feedbackObject["parameter"]?.jsonPrimitive?.contentOrNull
            )
        } ?: emptyList()

        RtpCodecCapability(
            kind = kind,
            mimeType = mimeType,
            preferredPayloadType = preferredPayloadType,
            clockRate = clockRate,
            channels = channels,
            parameters = parameters,
            rtcpFeedback = rtcpFeedback
        )
    } ?: emptyList()

    val headerExtensions = jsonObject["headerExtensions"]?.jsonArray?.mapNotNull { item ->
        val extObject = item as? JsonObject ?: return@mapNotNull null
        val uri = extObject["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val preferredId = extObject["preferredId"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
        RtpHeaderExtension(
            kind = extObject["kind"]?.jsonPrimitive?.contentOrNull?.toMediaKindOrNull(),
            uri = uri,
            preferredId = preferredId,
            preferredEncrypt = extObject["preferredEncrypt"]?.jsonPrimitive?.booleanOrNull ?: false,
            direction = extObject["direction"]?.jsonPrimitive?.contentOrNull?.toHeaderDirectionOrNull()
        )
    } ?: emptyList()

    val fecMechanisms = jsonObject["fecMechanisms"]?.jsonArray?.mapNotNull { item ->
        item.jsonPrimitive.contentOrNull
    } ?: emptyList()

    return RtpCapabilities(
        codecs = codecs,
        headerExtensions = headerExtensions,
        fecMechanisms = fecMechanisms
    )
}

internal fun parseAppDataBridgeJson(json: String?): Map<String, Any?>? {
    if (json.isNullOrBlank()) return null
    val element = runCatching { mediaSfuJson.parseToJsonElement(json) }.getOrNull() ?: return null
    return (element as? JsonObject)?.let(::jsonObjectToAnyMap)
}

/**
 * Serializes [RtpCapabilities] to the JSON format expected by the native iOS mediasoup device
 * load call. Unlike [RtpCapabilities.debugJson], this function:
 *  - Emits enum values (kind, direction) as lowercase strings ("audio"/"video", "sendrecv" etc.)
 *  - Keeps codec parameters as JSON strings (the C++ native layer expects Map<String,String>
 *    parameter values; converting to numbers causes type_error.302 in nlohmann/json).
 */
internal fun RtpCapabilities.toIosBridgeLoadJson(): String {
    val codecMaps: List<Map<String, Any?>> = codecs.map { codec ->
        // parameters are stored as Map<String,String> in the Kotlin model; keep them as strings
        val paramMap: Map<String, Any?> = codec.parameters
        val fbList: List<Map<String, Any?>> = codec.rtcpFeedback.map { fb ->
            mutableMapOf<String, Any?>("type" to fb.type).apply {
                if (!fb.parameter.isNullOrBlank()) this["parameter"] = fb.parameter
            }
        }
        mutableMapOf<String, Any?>(
            "kind" to codec.kind.name.lowercase(),
            "mimeType" to codec.mimeType,
            "clockRate" to codec.clockRate,
            "preferredPayloadType" to codec.preferredPayloadType,
            "channels" to codec.channels
        ).apply {
            if (paramMap.isNotEmpty()) this["parameters"] = paramMap
            if (fbList.isNotEmpty()) this["rtcpFeedback"] = fbList
        }
    }
    val extMaps: List<Map<String, Any?>> = headerExtensions.map { ext ->
        mutableMapOf<String, Any?>(
            "uri" to ext.uri,
            "preferredId" to ext.preferredId,
            "preferredEncrypt" to ext.preferredEncrypt
        ).apply {
            ext.kind?.let { this["kind"] = it.name.lowercase() }
            ext.direction?.let { this["direction"] = it.name.lowercase() }
        }
    }
    return mapToJsonElement(
        mapOf(
            "codecs" to codecMaps,
            "headerExtensions" to extMaps,
            "fecMechanisms" to fecMechanisms
        )
    ).toString()
}

private fun mapToJsonElement(map: Map<*, *>): JsonObject {
    return JsonObject(
        map.entries
            .filter { it.key is String && it.value != null }
            .associate { (key, value) -> key as String to wrapJsonValue(value) }
    )
}

private fun listToJsonElement(list: List<*>): JsonArray = JsonArray(list.filterNotNull().map(::wrapJsonValue))

private fun wrapJsonValue(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> mapToJsonElement(value)
    is List<*> -> listToJsonElement(value)
    else -> JsonPrimitive(value.toString())
}

private fun jsonObjectToAnyMap(jsonObject: JsonObject): Map<String, Any?> =
    jsonObject.mapValues { (_, value) -> jsonElementToAny(value) }

private fun jsonElementToAny(element: JsonElement): Any? = when (element) {
    JsonNull -> null
    is JsonObject -> jsonObjectToAnyMap(element)
    is JsonArray -> element.map(::jsonElementToAny)
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.booleanOrNull
        element.longOrNull != null -> element.longOrNull
        element.doubleOrNull != null -> element.doubleOrNull
        else -> element.content
    }
}

private val JsonElement.jsonObject: JsonObject
    get() = this as? JsonObject ?: JsonObject(emptyMap())

private val JsonElement.jsonArray: JsonArray
    get() = this as? JsonArray ?: JsonArray(emptyList())

private val JsonElement.jsonPrimitive: JsonPrimitive
    get() = this as? JsonPrimitive ?: JsonPrimitive("")

private val JsonPrimitive.contentOrNull: String?
    get() = content.takeIf { it.isNotBlank() }

private val JsonPrimitive.intOrNull: Int?
    get() = content.toIntOrNull()

private val JsonPrimitive.longOrNull: Long?
    get() = content.toLongOrNull()

private val JsonPrimitive.doubleOrNull: Double?
    get() = content.toDoubleOrNull()

private val JsonPrimitive.booleanOrNull: Boolean?
    get() = when (content.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun String.toMediaKindOrNull(): MediaKind? = when (lowercase()) {
    "audio" -> MediaKind.AUDIO
    "video" -> MediaKind.VIDEO
    else -> null
}

private fun String.toHeaderDirectionOrNull(): RtpHeaderDirection? =
    runCatching { RtpHeaderDirection.valueOf(uppercase()) }.getOrNull()