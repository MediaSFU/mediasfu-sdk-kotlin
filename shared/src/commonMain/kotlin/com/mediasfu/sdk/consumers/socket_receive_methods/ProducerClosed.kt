package com.mediasfu.sdk.consumers.socket_receive_methods

import com.mediasfu.sdk.consumers.ConsumerTransportInfo

/**
 * Parameters for the producerClosed function.
 */
interface ProducerClosedParameters {
    val consumerTransports: List<ConsumerTransportInfo>
    val screenId: String
    val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit
    val closeAndResize: suspend (producerId: String, kind: String) -> Unit
    fun getUpdatedAllParams(): ProducerClosedParameters
}

/**
 * Options for the producerClosed function.
 *
 * @property remoteProducerId The ID of the remote producer to be closed
 * @property parameters The parameters required to handle the closure
 */
data class ProducerClosedOptions(
    val remoteProducerId: String,
    val parameters: ProducerClosedParameters
)

/**
 * Handles the closure of a producer identified by its remote producer ID.
 * This function updates the consumer transports and triggers close-and-resize operations.
 *
 * @param options The options containing the producer ID and necessary parameters
 *
 * Example:
 * ```kotlin
 * val parameters = object : ProducerClosedParameters {
 *     override val consumerTransports = listOf<ConsumerTransportInfo>()
 *     override val screenId = "screen123"
 *     override val updateConsumerTransports = { transports: List<ConsumerTransportInfo> ->
 *         // update backing state with transports
 *     }
 *     override val closeAndResize = { producerId: String, kind: String ->
 *         // invoke closeAndResize handler with appropriate parameters
 *     }
 *     override fun getUpdatedAllParams() = this
 * }
 * 
 * val options = ProducerClosedOptions(
 *     remoteProducerId = "producerId",
 *     parameters = parameters
 * )
 *
 * producerClosed(options)
 * ```
 */
suspend fun producerClosed(options: ProducerClosedOptions) {
    val remoteProducerId = options.remoteProducerId
    val parameters = options.parameters.getUpdatedAllParams()

    val consumerTransports = parameters.consumerTransports
    val screenId = parameters.screenId
    val updateConsumerTransports = parameters.updateConsumerTransports
    val closeAndResize = parameters.closeAndResize

    // Find the producer to close based on the provided ID
    val producerToClose = consumerTransports.firstOrNull { transport ->
        transport.producerId == remoteProducerId
    } ?: return

    val producerId = producerToClose.producerId
    if (producerId.isEmpty()) {
        return
    }

    val consumer = producerToClose.consumer
    val baseKind = consumer?.track?.kind ?: consumer?.kind?.name?.lowercase().orEmpty()
    val kind = when {
        producerId == screenId -> "screenshare"
        baseKind.isNotEmpty() -> baseKind
        else -> "video"
    }

    runCatching { producerToClose.consumerTransport?.close() }
    runCatching { consumer?.close() }

    val remainingTransports = consumerTransports.filter { transport ->
        transport.producerId != remoteProducerId
    }
    updateConsumerTransports(remainingTransports)

    closeAndResize(remoteProducerId, kind)
}

