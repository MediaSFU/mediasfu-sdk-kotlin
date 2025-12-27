// ResumePauseStreams.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaKind
import kotlinx.coroutines.withTimeout

/**
 * Parameters interface for resuming and pausing streams.
 */
interface ResumePauseStreamsParameters {
    val participants: List<Participant>
    val dispActiveNames: List<String>
    val consumerTransports: List<ConsumerTransportInfo>
    val screenId: String  // Empty string means no screen ID
    val islevel: String
    
    // Update functions
    val updateDispActiveNames: (List<String>) -> Unit
    
    // Get updated parameters
    fun getUpdatedAllParams(): ResumePauseStreamsParameters
}

/**
 * Options for resuming and pausing streams.
 */
data class ResumePauseStreamsOptions(
    val parameters: ResumePauseStreamsParameters,
    val socket: SocketManager? = null
)

/**
 * Exception thrown when resume/pause streams fails.
 */
class ResumePauseStreamsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Resumes or pauses video streams based on active participants and display names.
 *
 * This function manages the resumption of specific video streams (excluding audio) for participants
 * in a virtual session. It identifies the relevant video IDs from active display names, screen sharing IDs,
 * and the host's video ID, then resumes each associated transport.
 *
 * ## Features:
 * - Stream resumption based on active participants
 * - Host video handling
 * - Screen sharing stream management
 * - Transport lifecycle management
 * - Error handling
 *
 * ## Parameters:
 * - [options] Configuration options for resuming/pausing streams
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = ResumePauseStreamsOptions(
 *     parameters = myParameters,
 *     socket = mySocket
 * )
 *
 * val result = resumePauseStreams(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ResumePauseStreams", "Error resuming/pausing streams: ${error.message}")
 * }
 * ```
 */
suspend fun resumePauseStreams(
    options: ResumePauseStreamsOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        // Socket is now retrieved from each transport info, not passed in options
        
        // Get active video IDs from participants
        val activeVideoIds = getActiveVideoIds(parameters)
        
        // Include screen producer ID when available
        val screenProducerId = parameters.screenId.takeUnless { it.isBlank() }

        // Get host video ID if user is not host
        val hostVideoId = if (parameters.islevel != "2") {
            getHostVideoId(parameters)
        } else null
        
        // Combine all video IDs to resume
        val videoIdsToResume = (activeVideoIds + listOfNotNull(screenProducerId, hostVideoId)).distinct()
        
        // Resume transports for each video ID
        val transportsToResume = parameters.consumerTransports.filter { info ->
            val producerId = info.producerId
            val consumer = info.consumer
            val isVideo = consumer?.let { consumerKind ->
                val trackedKind = consumerKind.track?.kind
                when {
                    trackedKind != null -> trackedKind.lowercase() != "audio"
                    else -> consumerKind.kind != MediaKind.AUDIO
                }
            } ?: false

            producerId.isNotEmpty() && producerId in videoIdsToResume && isVideo
        }

        for (transportInfo in transportsToResume) {
            val resumeResult = resumeTransport(transportInfo)
            resumeResult.onFailure { error ->
                Logger.e("ResumePauseStreams", "Error resuming transport for producer ${transportInfo.producerId}: ${error.message}")
            }
            resumeResult.onSuccess {
            }
        }
        
        Result.success(Unit)
    } catch (error: Exception) {
        Logger.e("ResumePauseStreams", "MediaSFU - resumePauseStreams: EXCEPTION ${error.message}")
        Result.failure(
            ResumePauseStreamsException(
                "resumePauseStreams error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Gets active video IDs from participants based on display names.
 */
private fun getActiveVideoIds(parameters: ResumePauseStreamsParameters): List<String> {
    return try {
        val activeNames = parameters.dispActiveNames
        val participants = parameters.participants

        activeNames.mapNotNull { name ->
            participants.firstOrNull { participant -> participant.name == name }
                ?.videoID
                ?.takeIf { it.isNotBlank() }
        }
    } catch (error: Exception) {
        Logger.e("ResumePauseStreams", "Error getting active video IDs: ${error.message}")
        emptyList()
    }
}

/**
 * Gets the host video ID from participants.
 */
private fun getHostVideoId(parameters: ResumePauseStreamsParameters): String? {
    return try {
        val participants = parameters.participants

        participants.firstOrNull { participant -> participant.islevel == "2" }
            ?.videoID
            ?.takeIf { it.isNotBlank() }
    } catch (error: Exception) {
        Logger.e("ResumePauseStreams", "Error getting host video ID: ${error.message}")
        null
    }
}

/**
 * Emits resume for a transport and resumes the local consumer when acknowledged.
 * Uses the socket from the transport info for server communication.
 */
private suspend fun resumeTransport(
    transportInfo: ConsumerTransportInfo
): Result<Unit> {
    return try {
        val consumer = transportInfo.consumer
            ?: return Result.failure(ResumePauseStreamsException("Missing consumer instance"))

        val socket = transportInfo.socket
        val resumeSucceeded = if (socket != null) {
            val consumerId = consumer.id.ifEmpty { transportInfo.serverConsumerTransportId }
            val emitData = mapOf("serverConsumerId" to consumerId)

            val ackResult = runCatching {
                withTimeout(10_000) {
                    socket.emitWithAck<Any?>("consumer-resume", emitData)
                }
            }

            when (val response = ackResult.getOrElse { error ->
                Logger.e("ResumePauseStreams", "consumer-resume ack failed for ${transportInfo.producerId}: ${error.message}")
                null
            }) {
                is Map<*, *> -> (response["resumed"] as? Boolean) ?: false
                is Boolean -> response
                null -> true // Assume success if no response
                else -> false
            }
        } else {
            true
        }

        if (resumeSucceeded) {
            runCatching { consumer.resume() }
                .onFailure { error ->
                    Logger.e("ResumePauseStreams", "Consumer resume call failed for ${transportInfo.producerId}: ${error.message}")
                }
            Result.success(Unit)
        } else {
            Result.failure(ResumePauseStreamsException("Resume acknowledgement negative"))
        }
    } catch (error: Exception) {
        Result.failure(
            ResumePauseStreamsException(
                "Error resuming transport: ${error.message}",
                error
            )
        )
    }
}