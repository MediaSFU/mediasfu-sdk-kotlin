// GetVideos.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream

/**
 * Options for retrieving and processing video streams.
 */
data class GetVideosOptions(
    val participants: List<Participant>,
    val allVideoStreams: List<Stream>,
    val oldAllStreams: List<Stream>,
    val adminVidID: String? = null,
    val updateAllVideoStreams: (List<Stream>) -> Unit,
    val updateOldAllStreams: (List<Stream>) -> Unit
)

/**
 * Exception thrown when getting videos fails.
 */
class GetVideosException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Processes and updates video streams by filtering out the admin's video stream.
 *
 * This function filters out the admin's video stream from the list of all video streams
 * and updates the state variables using the provided update functions. If no admin's
 * video stream is found, it reverts to the previous state.
 *
 * ## Features:
 * - Video stream filtering
 * - Admin stream exclusion
 * - State management
 * - Stream list updates
 * - Error handling
 *
 * ## Parameters:
 * - [options] Configuration options containing participants, streams, and update functions
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = GetVideosOptions(
 *     participants = participantList,
 *     allVideoStreams = allStreams,
 *     oldAllStreams = oldStreams,
 *     adminVidID = "admin-video-id",
 * )
 *
 * val result = getVideos(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("GetVideos", "Error processing video streams: ${error.message}")
 * }
 * ```
 */
suspend fun getVideos(
    options: GetVideosOptions
): Result<Unit> {
    return try {
        val participants = options.participants
        var allVideoStreams = options.allVideoStreams.toList()
        var oldAllStreams = options.oldAllStreams.toList()
        var adminVidID = options.adminVidID

        val admin = participants.filter { it.islevel == "2" }
        if (admin.isNotEmpty()) {
            adminVidID = admin.first().videoID
        }

        if (!adminVidID.isNullOrEmpty()) {
            val previousOldAllStreams = if (oldAllStreams.isNotEmpty()) oldAllStreams.toList() else emptyList()

            oldAllStreams = allVideoStreams.filter { stream ->
                stream.producerId == adminVidID
            }

            if (oldAllStreams.isEmpty()) {
                oldAllStreams = previousOldAllStreams
            }

            options.updateOldAllStreams(oldAllStreams)

            allVideoStreams = filterOutAdminStream(allVideoStreams, adminVidID)
            options.updateAllVideoStreams(allVideoStreams)
        }
        
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            GetVideosException(
                "getVideos error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Filters out the admin's video stream from the list of streams.
 */
private fun filterOutAdminStream(
    streams: List<Stream>,
    adminVidID: String
): List<Stream> {
    return try {
        streams.filter { stream ->
            stream.producerId != adminVidID
        }
    } catch (error: Exception) {
        Logger.e("GetVideos", "Error filtering admin stream: ${error.message}")
        streams // Return original streams on error
    }
}
