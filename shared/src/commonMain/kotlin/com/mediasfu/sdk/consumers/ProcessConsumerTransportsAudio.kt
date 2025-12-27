package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.Stream

/**
 * Parameters for processing consumer transports audio.
 */
interface ProcessConsumerTransportsAudioParameters {
    val sleep: suspend (SleepOptions) -> Unit
    fun getUpdatedAllParams(): ProcessConsumerTransportsAudioParameters
}

// Note: ProcessConsumerTransportsAudioOptions is defined in ResumePauseAudioStreams.kt

/**
 * Adjusts the audio state of consumer transports based on provided streams.
 *
 * This function examines each audio consumer transport:
 * - If a transport's `producerId` matches an entry in `lStreams` and is currently paused, it resumes the transport.
 * - If a transport's `producerId` does not match any entry in `lStreams` and is unpaused, it pauses the transport.
 * - The function incorporates a delay before pausing to allow for smoother transitions.
 *
 * ### Parameters:
 * - `options` (`ProcessConsumerTransportsAudioOptions`):
 *   - `consumerTransports`: List of audio transports that may need to be paused or resumed.
 *   - `lStreams`: List of streams that represent valid audio sources for the transports.
 *   - `parameters`: Contains sleep function and getUpdatedAllParams method.
 *
 * ### Behavior:
 * - **Pausing and Resuming**: Pauses transports not found in `lStreams` and resumes those that are.
 * - **Delay Handling**: A short delay is added before pausing transports to optimize timing.
 * - **Socket Events**: Emits `consumer-pause` and `consumer-resume` events to synchronize states.
 *
 * ### Example Usage:
 * ```kotlin
 * val parameters = object : ProcessConsumerTransportsAudioParameters {
 *     override val sleep = { options -> delay(options.ms) }
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * processConsumerTransportsAudio(
 *     ProcessConsumerTransportsAudioOptions(
 *         consumerTransports = listOf(transport1, transport2),
 *         lStreams = listOf(stream1, stream2),
 *         parameters = parameters
 *     )
 * )
 * ```
 *
 * ### Error Handling:
 * - Logs any errors encountered during the processing of transports.
 */
suspend fun processConsumerTransportsAudio(options: ProcessConsumerTransportsAudioOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    val sleep = parameters.sleep

    val consumerTransports = options.consumerTransports
    val lStreams = options.lStreams

    // Debug logging
        val streamIds = lStreams.map { stream ->
            val id = when {
                stream.producerId.isNotEmpty() -> stream.producerId
                !stream.audioID.isNullOrEmpty() -> stream.audioID!!
                else -> "empty"
            }
            "${stream.name ?: "?"}:$id"
        }
    
    val audioTransports = consumerTransports.filter { transport ->
        val consumer = transport.consumer ?: return@filter false
        val kind = consumer.track?.kind ?: consumer.kind.name.lowercase()
        kind == "audio"
    }

    try {
        fun isProducerTracked(producerId: String?): Boolean {
            if (producerId.isNullOrEmpty()) return false
            return lStreams.any { stream -> 
                val streamProducerId = when {
                    stream.producerId.isNotEmpty() -> stream.producerId
                    !stream.audioID.isNullOrEmpty() -> stream.audioID!!
                    else -> ""
                }
                streamProducerId == producerId
            }
        }

        val transportsToResume = consumerTransports.filter { transport ->
            val consumer = transport.consumer ?: return@filter false
            val kind = consumer.track?.kind ?: consumer.kind.name.lowercase()
            // Resume any tracked audio transport, even if local paused state is stale.
            kind == "audio" && isProducerTracked(transport.producerId)
        }

        val transportsToPause = consumerTransports.filter { transport ->
            val consumer = transport.consumer ?: return@filter false
            val kind = consumer.track?.kind ?: consumer.kind.name.lowercase()
            val producerId = transport.producerId
            kind == "audio" && !consumer.paused && producerId.isNotEmpty() && !isProducerTracked(producerId)
        }

        if (transportsToPause.isEmpty() && transportsToResume.isEmpty()) {
            return
        }

        transportsToResume.forEach { transport ->
            val consumerId = transport.consumer?.id ?: "unknown"
        }
        transportsToPause.forEach { transport ->
            val consumerId = transport.consumer?.id ?: "unknown"
        }

        sleep(SleepOptions(ms = 100))

        transportsToPause.forEach { transport ->
            val consumer = transport.consumer ?: return@forEach
            val socket = transport.socket
            val consumerId = consumer.id

            runCatching { consumer.pause() }
                .onSuccess {
                    }
                    .onFailure { }

            if (socket != null) {
                runCatching {
                    socket.emitWithAck<Any?>(
                        event = "consumer-pause",
                        data = mapOf("serverConsumerId" to consumerId)
                    )
                }.onFailure {
                }
            }
        }

        transportsToResume.forEach { transport ->
            val consumer = transport.consumer ?: return@forEach
            val socket = transport.socket
            val consumerId = consumer.id

            val resumeResponse = if (socket != null) {
                runCatching {
                    socket.emitWithAck<Any?>(
                        event = "consumer-resume",
                        data = mapOf("serverConsumerId" to consumerId)
                    )
                }.onFailure {
                }.getOrNull()
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
                        .onSuccess { }
                        .onFailure { }
            } else {
            }
        }
    } catch (error: Exception) {
            // Logger.e("ProcessConsumerTrans", "Error processing consumer transports audio: ${'$'}{error.message}")
    }
}

