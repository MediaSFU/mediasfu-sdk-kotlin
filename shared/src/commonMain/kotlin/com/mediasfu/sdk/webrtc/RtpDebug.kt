package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.text.StringBuilder

private val debugJson = Json {
    encodeDefaults = true
    prettyPrint = false
}

/**
 * Produces a compact, stable summary of the codecs/header extensions for logging and parity checks.
 */
fun RtpCapabilities.debugSummary(label: String = "RTP Caps"): String {
    val builder = StringBuilder()
    builder.append(label).append("[codecs=")
    builder.append(codecs.joinToString(
        separator = ";",
        transform = { codec -> codec.debugSummary() }
    ))
    builder.append(", headerExts=")
    builder.append(
        headerExtensions.joinToString(separator = ";") { ext ->
            buildString {
                append(ext.uri)
                append("#")
                append(ext.preferredId)
                ext.direction?.let { append("/").append(it.name.lowercase()) }
            }
        }
    )
    builder.append("]")
    return builder.toString()
}

/**
 * Emits a JSON payload for deeper parity investigations.
 */
fun RtpCapabilities.debugJson(): String {
    return runCatching { debugJson.encodeToString(this) }
        .getOrElse { error -> "rtpCapabilities-json-error: ${error.message}" }
}

fun OrtcUtils.ExtendedRtpCapabilities.debugSummary(label: String = "ExtendedCaps"): String {
    val codecSummary = codecs.joinToString(separator = ";") { codec ->
        buildString {
            append(codec.mimeType)
            append(":local=")
            append(codec.localPayloadType ?: "?")
            append("->remote=")
            append(codec.remotePayloadType ?: "?")
            if (codec.localRtxPayloadType != null || codec.remoteRtxPayloadType != null) {
                append("(rtxL=")
                append(codec.localRtxPayloadType ?: "-")
                append(",rtxR=")
                append(codec.remoteRtxPayloadType ?: "-")
                append(")")
            }
            val localParams = codec.localParameters.interestingParams()
            val remoteParams = codec.remoteParameters.interestingParams()
            if (localParams.isNotEmpty() || remoteParams.isNotEmpty()) {
                append("[L=")
                append(if (localParams.isEmpty()) "-" else localParams)
                append("|R=")
                append(if (remoteParams.isEmpty()) "-" else remoteParams)
                append("]")
            }
        }
    }
    val headerSummary = headerExtensions.joinToString(separator = ";") { ext ->
        buildString {
            append(ext.uri)
            append(":send=")
            append(ext.sendId)
            append("/recv=")
            append(ext.recvId)
            append("/")
            append(ext.direction.name.lowercase())
        }
    }
    return "$label[codecs=$codecSummary, headerExts=$headerSummary]"
}

private fun Map<String, String>.interestingParams(): String {
    if (isEmpty()) return ""
    val interestingKeys = setOf(
        "packetization-mode",
        "profile-level-id",
        "apt",
        "level-asymmetry-allowed",
        "profile-id"
    )
    val prioritized = entries
        .filter { it.key in interestingKeys }
        .joinToString(separator = ",") { (key, value) -> "$key=$value" }
    if (prioritized.isNotEmpty()) return prioritized
    return entries.joinToString(separator = ",") { (key, value) -> "$key=$value" }
}

private fun RtpCodecCapability.debugSummary(): String {
    val interestingParams = listOf(
        "packetization-mode",
        "profile-level-id",
        "apt",
        "level-asymmetry-allowed",
        "profile-id"
    )
    val paramsString = parameters
        .filterKeys { it in interestingParams }
        .entries
        .joinToString(separator = ",") { (key, value) -> "$key=$value" }
    return buildString {
        append(mimeType)
        preferredPayloadType?.let { append(":pt=").append(it) }
        if (paramsString.isNotEmpty()) {
            append("[")
            append(paramsString)
            append("]")
        }
    }
}