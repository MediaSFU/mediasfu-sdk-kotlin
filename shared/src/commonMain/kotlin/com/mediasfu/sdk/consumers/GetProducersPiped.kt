package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager

/**
 * Parameters for getting piped producers.
 */
interface GetProducersPipedParameters : SignalNewConsumerTransportParameters {
    val member: String
    val signalNewConsumerTransport: suspend (SignalNewConsumerTransportOptions) -> Unit
}

/**
 * Options for retrieving piped producers.
 *
 * @property nsock The socket connection to use
 * @property islevel The user's level/role
 * @property parameters The parameters for signaling new consumer transports
 */
data class GetProducersPipedOptions(
    val nsock: SocketManager,
    val islevel: String,
    val parameters: GetProducersPipedParameters
)

/**
 * Retrieves piped producers and signals new consumer transport for each retrieved producer.
 *
 * Emits a `getProducersPipedAlt` event to the server using the provided socket instance. The server
 * responds with a list of producer IDs, and for each ID, this function calls the
 * `signalNewConsumerTransport` function to handle the new consumer transport.
 *
 * @param options The options for the operation, including socket, level, and parameters
 *
 * Example:
 * ```kotlin
 * val parameters = object : GetProducersPipedParameters {
 *     override val member = "memberId"
 *     override val signalNewConsumerTransport = { options ->
 *         // Handle new consumer transport
 *     }
 * }
 *
 * getProducersPiped(
 *     GetProducersPipedOptions(
 *         nsock = socketInstance,
 *         islevel = "1",
 *         parameters = parameters
 *     )
 * )
 * ```
 *
 * @throws Exception Logs and rethrows any errors encountered during the operation
 */
suspend fun getProducersPiped(options: GetProducersPipedOptions) {
    try {
        val nsock = options.nsock
        val islevel = options.islevel
        val parameters = options.parameters
        val member = parameters.member
        val signalNewConsumerTransport = parameters.signalNewConsumerTransport

        // Emit request to get piped producers
        val producerIds = nsock.emitWithAck<Any>(
            event = "getProducersPipedAlt",
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
        Logger.e("GetProducersPiped", "Error getting piped producers: ${error.message}")
        throw error
    }
}

