package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.webrtc.MediaStream

/**
 * Options for the getParticipantMedia function.
 */
data class GetParticipantMediaOptions(
    val id: String = "",
    val name: String = "",
    val kind: String = "video",
    val parameters: GetParticipantMediaParameters
)

/**
 * Parameters interface for getParticipantMedia.
 */
interface GetParticipantMediaParameters {
    val allVideoStreams: List<Stream>
    val allAudioStreams: List<Stream>
    val participants: List<Participant>
}

/**
 * Type definition for the getParticipantMedia function.
 */
typealias GetParticipantMediaType = suspend (GetParticipantMediaOptions) -> MediaStream?

/**
 * Retrieves the media stream of a participant by ID or name.
 * 
 * This function searches for a participant's media stream (video or audio)
 * using either their producer ID or participant name.
 * 
 * **Parameters:**
 * - [id] (`String`): The producer ID of the participant. Default is empty string.
 * - [name] (`String`): The name of the participant. Default is empty string.
 * - [kind] (`String`): The type of media stream to retrieve:
 *   - `'video'`: Video stream
 *   - `'audio'`: Audio stream
 *   Default is `'video'`.
 * - [parameters] (`GetParticipantMediaParameters`): Parameters containing:
 *   - `allVideoStreams`: List of all video streams
 *   - `allAudioStreams`: List of all audio streams
 *   - `participants`: List of all participants
 * 
 * **Returns:**
 * - `MediaStream?`: The media stream if found, otherwise `null`.
 * 
 * **Example:**
 * ```kotlin
 * // Get video stream by producer ID
 * val videoStream = getParticipantMedia(
 *     GetParticipantMediaOptions(
 *         id = "producer-id-123",
 *         name = "",
 *         kind = "video",
 *         parameters = parameters
 *     )
 * )
 * 
 * // Get audio stream by participant name
 * val audioStream = getParticipantMedia(
 *     GetParticipantMediaOptions(
 *         id = "",
 *         name = "John Doe",
 *         kind = "audio",
 *         parameters = parameters
 *     )
 * )
 * ```
 */
suspend fun getParticipantMedia(options: GetParticipantMediaOptions): MediaStream? {
    return try {
        var stream: MediaStream? = null
        
        // Get required parameters
        val allVideoStreams = options.parameters.allVideoStreams
        val allAudioStreams = options.parameters.allAudioStreams
        val participants = options.parameters.participants
        
        // Search by ID if provided
        if (options.id.isNotEmpty()) {
            if (options.kind == "video") {
                // Find video stream by producer ID
                stream = allVideoStreams.find { it.producerId == options.id }?.stream
            } else if (options.kind == "audio") {
                // Find audio stream by producer ID
                stream = allAudioStreams.find { it.producerId == options.id }?.stream
            }
        } else if (options.name.isNotEmpty()) {
            // Search by name if ID not provided
            val participant = participants.find { it.name == options.name }
            
            if (participant != null) {
                val participantId = participant.id ?: ""
                
                if (options.kind == "video") {
                    // Find video stream by participant ID
                    stream = allVideoStreams.find { it.producerId == participantId }?.stream
                } else if (options.kind == "audio") {
                    // Find audio stream by participant ID
                    stream = allAudioStreams.find { it.producerId == participantId }?.stream
                }
            }
        }
        
        stream
    } catch (e: Exception) {
        // Return null if an error occurs
        Logger.e("GetParticipantMedia", "Error getting participant media: $e")
        null
    }
}