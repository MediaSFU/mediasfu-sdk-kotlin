package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

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
            data = details
        )

        try {
            val responseMap = response as? Map<*, *>
            val producersExist = responseMap?.get("producersExist") as? Boolean ?: false

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
            }
        } catch (e: Exception) {
            Logger.e("ReceiveAllPipedTrans", "Error processing piped transports response: ${e.message}")
        }
    } catch (error: Exception) {
        Logger.e("ReceiveAllPipedTrans", "Error in receiveAllPipedTransports: ${error.message}")
        throw error
    }
}

