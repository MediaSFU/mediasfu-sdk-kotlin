// MixStreams.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.Participant

/**
 * Options for mixing video and audio streams with participants.
 */
data class MixStreamsOptions(
    val alVideoStreams: List<Stream>,
    val nonAlVideoStreams: List<Stream>,
    val refParticipants: List<Participant>
)

/**
 * Exception thrown when mixing streams fails.
 */
class MixStreamsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Mixes video and audio streams and participants based on specified parameters.
 *
 * This function combines different categories of streams by interleaving muted and unmuted
 * streams, while ensuring prioritized positioning for streams with specific identifiers.
 * It creates a balanced mix of video and audio streams for optimal display.
 *
 * ## Features:
 * - Stream category mixing
 * - Muted/unmuted stream interleaving
 * - Priority-based positioning
 * - Balanced stream distribution
 * - Error handling
 *
 * ## Parameters:
 * - [options] Configuration options containing different stream categories
 *
 * ## Returns:
 * - [Result]<[List]<[Any]>> containing the mixed streams list
 *
 * ## Example Usage:
 * ```kotlin
 * val options = MixStreamsOptions(
 *     alVideoStreams = listOf(stream1, stream2),
 *     nonAlVideoStreams = listOf(participant1, participant2),
 *     refParticipants = listOf(participant1, participant2)
 * )
 *
 * val result = mixStreams(options)
 * result.onSuccess { mixedStreams ->
 * }
 * result.onFailure { error ->
 *     Logger.e("MixStreams", "Error mixing streams: ${error.message}")
 * }
 * ```
 */
suspend fun mixStreams(
    options: MixStreamsOptions
): Result<List<Any>> {
    return try {
        var alVideoStreams = options.alVideoStreams.toList()
        val nonAlVideoStreams = options.nonAlVideoStreams.toList()
        val refParticipants = options.refParticipants

        val youyouStream = alVideoStreams.firstOrNull { stream ->
            stream.producerId == "youyou" || stream.producerId == "youyouyou"
        }

        alVideoStreams = alVideoStreams.filterNot { stream ->
            stream.producerId == "youyou" || stream.producerId == "youyouyou"
        }

        val unmutedAlVideoStreams = alVideoStreams.filter { stream ->
            val participant = refParticipants.firstOrNull { it.videoID == stream.producerId }
            stream.muted != true && participant != null && !participant.muted
        }

        val mutedAlVideoStreams = alVideoStreams.filter { stream ->
            val participant = refParticipants.firstOrNull { it.videoID == stream.producerId }
            stream.muted == true || (participant != null && participant.muted)
        }

        val mixedStreams = mutableListOf<Stream>()
        mixedStreams.addAll(unmutedAlVideoStreams)

        var nonAlIndex = 0
        for (mutedStream in mutedAlVideoStreams) {
            if (nonAlIndex < nonAlVideoStreams.size) {
                mixedStreams.add(nonAlVideoStreams[nonAlIndex])
                nonAlIndex += 1
            }
            mixedStreams.add(mutedStream)
        }

        if (nonAlIndex < nonAlVideoStreams.size) {
            mixedStreams.addAll(nonAlVideoStreams.subList(nonAlIndex, nonAlVideoStreams.size))
        }

        if (youyouStream != null && youyouStream.producerId.isNotEmpty()) {
            mixedStreams.add(0, youyouStream)
        }

        Result.success(mixedStreams)
    } catch (error: Exception) {
        Result.failure(
            MixStreamsException(
                "mixStreams error: ${error.message}",
                error
            )
        )
    }
}

