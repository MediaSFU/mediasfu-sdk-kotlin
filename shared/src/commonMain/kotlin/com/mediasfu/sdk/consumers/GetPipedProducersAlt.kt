package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.util.MediaSFURuntimeProbe

import com.mediasfu.sdk.socket.SocketManager

/**
 * Parameters for getting piped producers (alternative version).
 */
interface GetPipedProducersAltParameters : SignalNewConsumerTransportParameters {
    val member: String
    val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit
}

/**
 * Options for retrieving piped producers (alternative version).
 *
 * @property community Whether this is for a community event
 * @property nsock The socket connection to use
 * @property islevel The user's level/role
 * @property parameters The parameters for signaling new consumer transports
 */
data class GetPipedProducersAltOptions(
    val community: Boolean = false,
    val nsock: SocketManager,
    val islevel: String,
    val parameters: GetPipedProducersAltParameters
)

// Note: SignalNewConsumerTransportOptions is defined in SignalNewConsumerTransport.kt

/**
 * Retrieves piped producers and signals new consumer transport for each retrieved producer.
 *
 * Emits a `getProducersPipedAlt` or `getProducersAlt` event to the server using the provided
 * socket instance. The server responds with a list of producer IDs, and for each ID, this function
 * calls the `signalNewConsumerTransport` function to handle the new consumer transport.
 *
 * @param options The options for the operation, including socket, level, and parameters
 *
 * Example:
 * ```kotlin
 * val parameters = object : GetPipedProducersAltParameters {
 *     override val member = "memberId"
 *     override val signalNewConsumerTransport = { options ->
 *         // Handle new consumer transport
 *     }
 * }
 *
 * getPipedProducersAlt(
 *     GetPipedProducersAltOptions(
 *         community = true,
 *         nsock = socketInstance,
 *         islevel = "1",
 *         parameters = parameters
 *     )
 * )
 * ```
 *
 * @throws Exception Logs and rethrows any errors encountered during the operation
 */
suspend fun getPipedProducersAlt(options: GetPipedProducersAltOptions) {
    try {
        val nsock = options.nsock
        val islevel = options.islevel
        val parameters = options.parameters
        val member = parameters.member
        val signalNewConsumerTransport = parameters.signalNewConsumerTransport
        val community = options.community

        val emitEvent = if (community) "getProducersAlt" else "getProducersPipedAlt"

        MediaSFURuntimeProbe.recordConsumerSignalStage(
            "get-producers-emit",
            "",
            "event=$emitEvent,level=$islevel,member=$member"
        )

        // Emit request to get piped producers
        val requestData = mapOf("islevel" to islevel, "member" to member)
        val producerIds = try {
            nsock.emitWithAck<Any>(
                event = emitEvent,
                data = requestData
            )
        } catch (error: Exception) {
            val isPipedTimeout = emitEvent == "getProducersPipedAlt" &&
                (error.message?.contains("Acknowledgment timeout", ignoreCase = true) == true)
            if (isPipedTimeout) {
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "get-producers-fallback-event",
                    "",
                    "from=$emitEvent,to=getProducersAlt,level=$islevel"
                )
                nsock.emitWithAck(
                    event = "getProducersAlt",
                    data = requestData
                )
            } else {
                throw error
            }
        }

        val extractedProducerIds = extractProducerIds(producerIds)
        val responseShape = summarizeProducerAckShape(producerIds)

        Logger.d(
            "GetPipedProducersAlt",
            "ack event=$emitEvent level=$islevel member='$member' shape=$responseShape extractedCount=${extractedProducerIds.size}"
        )
        MediaSFURuntimeProbe.recordConsumerSignalStage(
            "get-producers-ack",
            "",
            "event=$emitEvent,level=$islevel,count=${extractedProducerIds.size},shape=$responseShape"
        )

        // Handle the server response with producer IDs
        if (extractedProducerIds.isNotEmpty()) {
            MediaSFURuntimeProbe.recordConsumerSignalStage(
                "get-producers",
                "",
                "event=$emitEvent,level=$islevel,count=${extractedProducerIds.size}"
            )
            for (remoteProducerId in extractedProducerIds) {
                val signalOptions = SignalNewConsumerTransportOptions(
                    producerId = remoteProducerId,
                    islevel = islevel,
                    socket = nsock,
                    parameters = parameters
                )
                signalNewConsumerTransport(signalOptions)
            }
        }
    } catch (error: Exception) {
        MediaSFURuntimeProbe.recordConsumerSignalStage(
            "get-producers-error",
            "",
            "detail=${error.message ?: "unknown"}"
        )
        Logger.e("GetPipedProducersAlt", "Error getting piped producers: ${error.message}")
        throw error
    }
}

data class BootstrapProducerEntry(
    val id: String,
    val screenShareHint: Boolean = false
)

internal fun extractProducerIds(response: Any?): List<String> =
    extractProducerEntries(response).map { it.id }

internal fun extractProducerEntries(response: Any?): List<BootstrapProducerEntry> {
    val entries = mutableListOf<BootstrapProducerEntry>()
    collectProducerEntries(response, entries)
    return entries.distinctBy { it.id }
}

private fun collectProducerEntries(
    value: Any?,
    entries: MutableList<BootstrapProducerEntry>,
    keyHint: String = "",
    allowStringValue: Boolean = false
) {
    when (value) {
        is String -> {
            if (allowStringValue && value.isNotBlank()) {
                entries += BootstrapProducerEntry(
                    id = value,
                    screenShareHint = keyHint.contains("screen", ignoreCase = true)
                )
            }
        }

        is List<*> -> {
            value.forEach { entry ->
                collectProducerEntries(
                    value = entry,
                    entries = entries,
                    keyHint = keyHint,
                    allowStringValue = allowStringValue
                )
            }
        }

        is Map<*, *> -> {
            val idKey = listOf("id", "producerId", "producer_id", "remoteProducerId")
                .firstOrNull { key -> value[key] is String }
            val directId = idKey?.let { value[it] as? String }?.takeIf { it.isNotBlank() }
            if (directId != null) {
                entries += BootstrapProducerEntry(
                    id = directId,
                    screenShareHint = keyHint.contains("screen", ignoreCase = true) ||
                        idKey.contains("screen", ignoreCase = true)
                )
            }

            value.forEach { (rawKey, nestedValue) ->
                val key = rawKey?.toString().orEmpty()
                val keyLooksProducerRelated = key.contains("producer", ignoreCase = true) ||
                    key.contains("screen", ignoreCase = true)
                val nestedAllowsStrings = keyLooksProducerRelated ||
                    key.equals("data", ignoreCase = true) ||
                    key.equals("result", ignoreCase = true) ||
                    key.equals("payload", ignoreCase = true)

                collectProducerEntries(
                    value = nestedValue,
                    entries = entries,
                    keyHint = key,
                    allowStringValue = nestedAllowsStrings
                )
            }
        }
    }
}

private fun summarizeProducerAckShape(response: Any?): String {
    return when (response) {
        is List<*> -> "list(size=${response.size})"
        is Map<*, *> -> {
            val keys = response.keys
                .map { it.toString() }
                .sorted()
                .joinToString(",")
            "map(keys=$keys)"
        }
        null -> "null"
        else -> response::class.simpleName ?: "unknown"
    }
}
