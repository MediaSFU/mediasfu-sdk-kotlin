package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.util.MediaSFURuntimeProbe

import com.mediasfu.sdk.socket.SocketManager

/**
 * Parameters for receiving all piped transports.
 */
interface ReceiveAllPipedTransportsParameters : GetPipedProducersAltParameters {
    val roomName: String
    override val member: String
    val getPipedProducersAlt: suspend (GetPipedProducersAltOptions) -> Unit
}

// Note: ReceiveAllPipedTransportsOptions is defined in ConnectLocalIps.kt
// We'll use a wrapper function that accepts the parameters interface

/**
 * Receives all piped transports for a specific room and member by requesting piped producers at
 * different levels.
 *
 * This function sends a `createReceiveAllTransportsPiped` event to the server, which checks if
 * piped producers exist for the given room and member. If producers are found, it calls the
 * `getPipedProducersAlt` function to retrieve piped transports for levels 0, 1, and 2.
 *
 * @param options The options containing socket, community flag, and parameters
 *
 * Example:
 * ```kotlin
 * receiveAllPipedTransportsImpl(
 *     nsock = socket,
 *     community = true,
 *     roomName = "roomA",
 *     member = "userB",
 *     getPipedProducersAlt = { opts ->
 *         // Handle getting piped producers
 *     }
 * )
 * ```
 */
suspend fun receiveAllPipedTransportsImpl(
    nsock: SocketManager,
    community: Boolean = false,
    roomName: String,
    member: String,
    getPipedProducersAlt: suspend (GetPipedProducersAltOptions) -> Unit
) {
    val parameters = object : ReceiveAllPipedTransportsParameters {
        override val roomName = roomName
        override val member = member
        override val getPipedProducersAlt = getPipedProducersAlt
        override val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit =
            { /* no-op */ }
        override val consumingTransports = emptyList<String>()
        override val consumerTransports = emptyList<ConsumerTransportInfo>()
        override val lockScreen = false
        override val device = null
        override val rtpCapabilities = null
        override val negotiatedRecvRtpCapabilities = null
        override fun updateConsumingTransports(transports: List<String>) { /* no-op */ }
        override val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit = { /* no-op */ }
        override val consumerResume: suspend (ConsumerResumeOptions) -> Unit = { /* no-op */ }
        override val consumerResumeParamsProvider: () -> ConsumerResumeParameters = { 
            StubConsumerResumeParameters()
        }
        override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit = { /* no-op */ }
        override fun getUpdatedAllParams() = this
    }

    try {
        val levels = listOf("0", "1", "2")

        val emitName = if (community) {
            "createReceiveAllTransports"
        } else {
            "createReceiveAllTransportsPiped"
        }

        val details = if (community) {
            mapOf("islevel" to "0")
        } else {
            mapOf(
                "roomName" to parameters.roomName,
                "member" to parameters.member
            )
        }

        val response = nsock.emitWithAck<Any?>(
            event = emitName,
            data = details,
            timeout = 30_000
        )
        Logger.d(
            "ReceiveAllPipedTrans",
            "ack event=$emitName community=$community room='${parameters.roomName}' member='${parameters.member}' responseType=${response?.let { it::class.simpleName } ?: "null"}"
        )

        try {
            val responseMap = response as? Map<*, *>
            val responseList = response as? List<*>

            fun countListValue(raw: Any?): Int? = when (raw) {
                is List<*> -> raw.size
                else -> null
            }

            val nestedProducerList = responseMap
                ?.let { map -> listOf("producers", "producerIds", "data", "result", "payload")
                    .asSequence()
                    .mapNotNull { key -> map[key] }
                    .firstOrNull { it is List<*> }
                }

            val producerCount = when {
                responseMap != null -> when (val raw = responseMap["producers"]) {
                    is List<*> -> raw.size
                    else -> countListValue(nestedProducerList)
                }
                responseList != null -> responseList.size
                else -> null
            }

            val producerIdsCount = when {
                responseMap != null -> when (val raw = responseMap["producerIds"]) {
                    is List<*> -> raw.size
                    else -> countListValue(nestedProducerList)
                }
                else -> null
            }

            val producersExistFlag = responseMap?.get("producersExist").toLooseBoolean()
            val producersExist = producersExistFlag || (producerCount ?: 0) > 0 || (producerIdsCount ?: 0) > 0

            val responseShape = when {
                responseMap != null -> "map(keys=" + responseMap.keys
                    .map { it.toString() }
                    .sorted()
                    .joinToString(",") + ")"
                responseList != null -> "list(size=${responseList.size})"
                else -> "none"
            }

            Logger.d(
                "ReceiveAllPipedTrans",
                "receive-all ack producersExist=$producersExist producersCount=${producerCount ?: -1} producerIdsCount=${producerIdsCount ?: -1} shape=$responseShape"
            )

            MediaSFURuntimeProbe.recordConsumerSignalStage(
                "receive-all-ack",
                "",
                "community=${if (community) 1 else 0},producers=${if (producersExist) 1 else 0},pCount=${producerCount ?: -1},pidCount=${producerIdsCount ?: -1},shape=$responseShape"
            )

            if (producersExist) {
                // Retrieve piped producers for each level if producers exist
                for (islevel in levels) {
                    val optionsGetPipedProducersAlt = GetPipedProducersAltOptions(
                        community = community,
                        nsock = nsock,
                        islevel = islevel,
                        parameters = parameters as GetPipedProducersAltParameters
                    )
                    parameters.getPipedProducersAlt(optionsGetPipedProducersAlt)
                }
            } else {
                Logger.w(
                    "ReceiveAllPipedTrans",
                    "No producers in receive-all ack event=$emitName community=$community room='${parameters.roomName}' member='${parameters.member}'"
                )
            }
        } catch (e: Exception) {
            Logger.e("ReceiveAllPipedTrans", "Error processing piped transports response: ${e.message}")
        }
    } catch (error: Exception) {
        Logger.e("ReceiveAllPipedTrans", "Error in receiveAllPipedTransports: ${error.message}")
        throw error
    }
}
