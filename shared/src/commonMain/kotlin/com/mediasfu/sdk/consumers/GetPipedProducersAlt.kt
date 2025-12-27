package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

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

        // Emit request to get piped producers
        val producerIds = nsock.emitWithAck<Any>(
            event = emitEvent,
            data = mapOf("islevel" to islevel, "member" to member)
        )

        // Handle the server response with producer IDs
        if (producerIds is List<*> && producerIds.isNotEmpty()) {
            for (id in producerIds) {
                if (id is String) {
                    val signalOptions = SignalNewConsumerTransportOptions(
                        producerId = id,
                        islevel = islevel,
                        socket = nsock,
                        parameters = parameters
                    )
                    signalNewConsumerTransport(signalOptions)
                }
            }
        }
    } catch (error: Exception) {
        Logger.e("GetPipedProducersAlt", "Error getting piped producers: ${error.message}")
        throw error
    }
}

