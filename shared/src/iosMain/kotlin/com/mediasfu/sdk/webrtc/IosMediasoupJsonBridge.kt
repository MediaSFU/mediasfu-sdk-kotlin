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

internal fun parseAppDataBridgeJson(json: String?): Map<String, Any?>? {
    if (json.isNullOrBlank()) return null
    val element = runCatching { mediaSfuJson.parseToJsonElement(json) }.getOrNull() ?: return null
    return (element as? JsonObject)?.let(::jsonObjectToAnyMap)
}

private fun mapToJsonElement(map: Map<*, *>): JsonObject {
    return JsonObject(
        map.entries
            .filter { it.key is String }
            .associate { (key, value) -> key as String to wrapJsonValue(value) }
    )
}

private fun listToJsonElement(list: List<*>): JsonArray = JsonArray(list.map(::wrapJsonValue))

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