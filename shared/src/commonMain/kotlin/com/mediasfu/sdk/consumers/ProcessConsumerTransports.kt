// ProcessConsumerTransports.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.SleepOptions

/**
 * Exception thrown when an error occurs during processing of consumer transports.
 */
class ProcessConsumerTransportsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parameters interface for processing consumer transports.
 */
interface ProcessConsumerTransportsParameters {
    val remoteScreenStream: List<Stream>
    val oldAllStreams: List<Stream>
    val newLimitedStreams: List<Stream>
    
    // Mediasfu function for sleep/delay
    val sleep: suspend (SleepOptions) -> Unit
    
    // Method to retrieve updated parameters
    fun getUpdatedAllParams(): ProcessConsumerTransportsParameters
}

/**
 * Options for processing consumer transports.
 */
data class ProcessConsumerTransportsOptions(
    val consumerTransports: List<ConsumerTransportInfo>,
    val lStreams_: List<Stream>,
    val parameters: ProcessConsumerTransportsParameters
)

/**
 * Processes consumer transports to pause or resume video streams based on provided stream lists.
 *
 * This function iterates over consumer transports, checking if each transport's producerId
 * matches any in the provided lists of streams. It then pauses or resumes consumers accordingly.
 *
 * ## Features:
 * - Consumer transport processing
 * - Stream-based pause/resume logic
 * - Audio transport handling (no-op)
 * - Error handling and recovery
 *
 * ## Parameters:
 * - [options] Configuration options for processing consumer transports
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = ProcessConsumerTransportsOptions(
 *     consumerTransports = myTransportInfos,
 *     lStreams_ = myStreams,
 *     parameters = myParameters
 * )
 *
 * val result = processConsumerTransports(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ProcessConsumerTrans", "Error processing consumer transports: ${error.message}")
 * }
 * ```
 */
suspend fun processConsumerTransports(
    options: ProcessConsumerTransportsOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val consumerTransports = options.consumerTransports
        val lStreams_ = options.lStreams_
        val remoteScreenStream = parameters.remoteScreenStream
        val oldAllStreams = parameters.oldAllStreams
        val newLimitedStreams = parameters.newLimitedStreams
        val sleep = parameters.sleep
        
        
        val streamBuckets = listOf(lStreams_, remoteScreenStream, oldAllStreams, newLimitedStreams)

        fun matchesAnyStream(producerId: String?): Boolean {
            if (producerId.isNullOrEmpty()) return false
            return streamBuckets.any { bucket ->
                bucket.any { stream -> stream.producerId == producerId }
            }
        }

        val transportsToResume = consumerTransports.filter { info ->
            val consumer = info.consumer
            val producerId = info.producerId
            val kind = consumer?.track?.kind ?: consumer?.kind?.name?.lowercase()
            consumer != null &&
                kind != "audio" &&
                consumer.paused &&
                matchesAnyStream(producerId)
        }

        val transportsToPause = consumerTransports.filter { info ->
            val consumer = info.consumer
            val producerId = info.producerId
            val kind = consumer?.track?.kind ?: consumer?.kind?.name?.lowercase()
            consumer != null &&
                kind != "audio" &&
                !consumer.paused &&
                producerId.isNotEmpty() &&
                streamBuckets.all { bucket -> bucket.none { it.producerId == producerId } }
        }

        if (transportsToResume.isEmpty() && transportsToPause.isEmpty()) {
            return Result.success(Unit)
        }

        sleep(SleepOptions(ms = 100))

        transportsToPause.forEach { transport ->
            val consumer = transport.consumer ?: return@forEach
            val socket = transport.socket
            val consumerId = consumer.id

            runCatching { consumer.pause() }

            if (socket != null) {
                try {
                    socket.emitWithAck<Any?>(
                        event = "consumer-pause",
                        data = mapOf("serverConsumerId" to consumerId)
                    )
                } catch (error: Exception) {
                    Logger.e("ProcessConsumerTrans", "MediaSFU - consumer-pause ack failed for $consumerId: ${error.message}")
                }
            }
        }

        transportsToResume.forEach { transport ->
            val consumer = transport.consumer ?: return@forEach
            val socket = transport.socket
            val consumerId = consumer.id

            val resumeResponse = if (socket != null) {
                try {
                    socket.emitWithAck<Any?>(
                        event = "consumer-resume",
                        data = mapOf("serverConsumerId" to consumerId)
                    )
                } catch (error: Exception) {
                    Logger.e("ProcessConsumerTrans", "MediaSFU - consumer-resume ack failed for $consumerId: ${error.message}")
                    null
                }
            } else {
                null
            }

            val resumed = when (resumeResponse) {
                is Map<*, *> -> resumeResponse["resumed"] as? Boolean ?: false
                is Boolean -> resumeResponse
                null -> true
                else -> false
            }

            if (resumed) {
                runCatching { consumer.resume() }
            }
        }

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ProcessConsumerTransportsException(
                "processConsumerTransports error: ${error.message}",
                error
            )
        )
    }
}
