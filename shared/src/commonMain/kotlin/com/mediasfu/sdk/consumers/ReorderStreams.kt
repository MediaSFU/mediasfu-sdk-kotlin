// ReorderStreams.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream

/**
 * Parameters interface for reordering streams.
 */
interface ReorderStreamsParameters {
    val allVideoStreams: List<Stream>
    val participants: List<Participant>
    val oldAllStreams: List<Stream>
    val screenId: String
    val adminVidID: String
    val newLimitedStreams: List<Stream>
    val newLimitedStreamsIDs: List<String>
    val activeSounds: List<String>
    val screenShareIDStream: String
    val screenShareNameStream: String
    val adminIDStream: String
    val adminNameStream: String
    
    // Update functions
    fun updateAllVideoStreams(streams: List<Stream>)
    fun updateParticipants(participants: List<Participant>)
    fun updateOldAllStreams(streams: List<Stream>)
    fun updateScreenId(id: String)
    fun updateAdminVidID(id: String)
    fun updateNewLimitedStreams(streams: List<Stream>)
    fun updateNewLimitedStreamsIDs(ids: List<String>)
    fun updateActiveSounds(sounds: List<String>)
    fun updateScreenShareIDStream(id: String)
    fun updateScreenShareNameStream(name: String)
    fun updateAdminIDStream(id: String)
    fun updateAdminNameStream(name: String)
    fun updateYouYouStream(streams: List<Stream>)
    fun updateYouYouStreamIDs(ids: List<String>)
    
    // MediaSFU functions
    suspend fun changeVids(options: ChangeVidsOptions): Result<Unit>
    
    // Get updated parameters
    fun getUpdatedAllParams(): ReorderStreamsParameters
}

/**
 * Options for reordering streams.
 */
data class ReorderStreamsOptions(
    val add: Boolean,
    val screenChanged: Boolean,
    val parameters: ReorderStreamsParameters,
    val streams: List<Stream> = emptyList()
)

/**
 * Exception thrown when reordering streams fails.
 */
class ReorderStreamsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Reorders video streams based on the provided parameters and options.
 *
 * This function manages the reordering of video streams in a meeting interface,
 * handling additions, removals, and screen changes. It updates the stream lists
 * and triggers necessary UI updates.
 *
 * ## Features:
 * - Stream reordering and management
 * - Screen sharing stream handling
 * - Admin stream prioritization
 * - Active sounds management
 * - UI update triggering
 * - Error handling
 *
 * ## Matches React/Flutter behavior 100%:
 * - Admin name is only set when using oldAdminStream
 * - Screen share streams are properly filtered
 * - youyou streams are added with duplicate checks
 *
 * ## Parameters:
 * - [options] Configuration options for reordering streams
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = ReorderStreamsOptions(
 *     add = true,
 *     screenChanged = false,
 *     parameters = myParameters,
 *     streams = myStreams
 * )
 *
 * val result = reorderStreams(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ReorderStreams", "Error reordering streams: ${error.message}")
 * }
 * ```
 */
suspend fun reorderStreams(
    options: ReorderStreamsOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val add = options.add
        val screenChanged = options.screenChanged
        val participants: List<Participant> = parameters.participants
        val allVideoStreams = parameters.allVideoStreams.toMutableList()
        val oldAllStreams = parameters.oldAllStreams
        var adminVidID = parameters.adminVidID
        val screenId = parameters.screenId
        
        allVideoStreams.forEachIndexed { index, stream ->
        }
        participants.forEachIndexed { index, participant ->
        }

        // Initialize lists based on add flag (matching React/Flutter)
        val newLimitedStreams = if (add) {
            parameters.newLimitedStreams.toMutableList()
        } else {
            mutableListOf()
        }
        val newLimitedStreamsIDs = if (add) {
            parameters.newLimitedStreamsIDs.toMutableList()
        } else {
            mutableListOf()
        }
        val activeSounds = if (add) {
            parameters.activeSounds.toMutableList()
        } else {
            mutableListOf()
        }

        // Get youyou streams and admin participants
        val youyouStreams = allVideoStreams.filter { it.producerId == "youyou" }
        val adminParticipants = participants.filter { it.islevel == "2" }
        
        // Update adminVidID (local variable, not persisted - matches React/Flutter)
        adminVidID = adminParticipants.firstOrNull()?.videoID ?: ""

        // Local variables for admin and screen share info (matches React/Flutter pattern)
        var adminIDStream = parameters.adminIDStream
        var adminNameStream = parameters.adminNameStream
        var screenShareIDStream = parameters.screenShareIDStream
        var screenShareNameStream = parameters.screenShareNameStream

        // Helper function to add streams if not already present
        fun ensureStreamsPresent(target: MutableList<Stream>, ids: MutableList<String>, candidates: List<Stream>) {
            candidates.forEach { candidate ->
                if (candidate.producerId.isNotEmpty() && ids.none { it == candidate.producerId }) {
                    target.add(candidate)
                    ids.add(candidate.producerId)
                }
            }
        }

        // Add youyou streams (matching React/Flutter logic)
        if (!add) {
            ensureStreamsPresent(newLimitedStreams, newLimitedStreamsIDs, youyouStreams)
        } else {
            // Only add if not already present
            if (newLimitedStreams.none { it.producerId == "youyou" }) {
                ensureStreamsPresent(newLimitedStreams, newLimitedStreamsIDs, youyouStreams)
            }
        }

        // Handle admin stream (matching React/Flutter pattern)
        if (adminVidID.isNotEmpty()) {
            val adminStream = allVideoStreams.firstOrNull { it.producerId == adminVidID }
            val adminParticipant = adminParticipants.firstOrNull()

            if (adminStream != null) {
                // Admin stream exists in current streams
                adminIDStream = adminVidID  // Update ID
                // NOTE: Don't update name here (matches React/Flutter)
                
                if (!add) {
                    if (newLimitedStreams.none { it.producerId == adminVidID }) {
                        newLimitedStreams.add(adminStream)
                        newLimitedStreamsIDs.add(adminStream.producerId)
                    }
                } else {
                    // Check if not already in limited streams
                    if (newLimitedStreams.none { it.producerId == adminVidID }) {
                        newLimitedStreams.add(adminStream)
                        newLimitedStreamsIDs.add(adminStream.producerId)
                    }
                }
            } else {
                // Admin stream not in current, check old streams
                val oldAdminStream = oldAllStreams.firstOrNull { it.producerId == adminVidID }
                
                if (oldAdminStream != null) {
                    // CRITICAL FIX: Update BOTH ID and name when using old stream (matches React/Flutter)
                    adminIDStream = adminVidID
                    if (adminParticipant != null) {
                        adminNameStream = adminParticipant.name
                    }
                    
                    if (!add) {
                        if (newLimitedStreams.none { it.producerId == adminVidID }) {
                            newLimitedStreams.add(oldAdminStream)
                            newLimitedStreamsIDs.add(oldAdminStream.producerId)
                        }
                    } else {
                        if (newLimitedStreams.none { it.producerId == adminVidID }) {
                            newLimitedStreams.add(oldAdminStream)
                            newLimitedStreamsIDs.add(oldAdminStream.producerId)
                        }
                    }
                }
            }

            // Handle screen share streams
            val screenParticipant = participants.firstOrNull { it.ScreenID == screenId }
            if (screenParticipant != null) {
                val screenParticipantVidID = screenParticipant.videoID
                
                if (screenParticipantVidID.isNotEmpty() && 
                    newLimitedStreams.none { it.producerId == screenParticipantVidID }) {
                    screenShareIDStream = screenParticipantVidID
                    screenShareNameStream = screenParticipant.name
                    
                    val matchingStreams = allVideoStreams.filter { it.producerId == screenParticipantVidID }
                    ensureStreamsPresent(newLimitedStreams, newLimitedStreamsIDs, matchingStreams)
                }
            }
        } else {
            // No admin - just handle screen share
            val screenParticipant = participants.firstOrNull { it.ScreenID == screenId }
            if (screenParticipant != null) {
                val screenParticipantVidID = screenParticipant.videoID
                
                if (screenParticipantVidID.isNotEmpty() && 
                    newLimitedStreams.none { it.producerId == screenParticipantVidID }) {
                    screenShareIDStream = screenParticipantVidID
                    screenShareNameStream = screenParticipant.name
                    
                    val matchingStreams = allVideoStreams.filter { it.producerId == screenParticipantVidID }
                    ensureStreamsPresent(newLimitedStreams, newLimitedStreamsIDs, matchingStreams)
                }
            }
        }

        // Update all state (matching React/Flutter order)
        newLimitedStreams.forEachIndexed { index, stream ->
        }
        
        parameters.updateNewLimitedStreams(newLimitedStreams.toList())
        parameters.updateNewLimitedStreamsIDs(newLimitedStreamsIDs.toList())
        parameters.updateActiveSounds(activeSounds.toList())
        parameters.updateScreenShareIDStream(screenShareIDStream)
        parameters.updateScreenShareNameStream(screenShareNameStream)
        parameters.updateAdminIDStream(adminIDStream)
        parameters.updateAdminNameStream(adminNameStream)
        parameters.updateYouYouStream(youyouStreams.toList())
        parameters.updateYouYouStreamIDs(youyouStreams.map { it.producerId })

        // Reflect changes on UI by calling changeVids
        try {
            // Check if parameters implements ChangeVidsParameters for the full options
            if (parameters is ChangeVidsParameters) {
                val changeVidsOptions = ChangeVidsOptions(
                    screenChanged = screenChanged,
                    parameters = parameters
                )
                val changeResult = parameters.changeVids(changeVidsOptions)
                Logger.e("ReorderStreams", "MediaSFU - reorderStreams: changeVids returned, isFailure=${changeResult.isFailure}")
                if (changeResult.isFailure) {
                    val failure = changeResult.exceptionOrNull()
                    Logger.e("ReorderStreams", "MediaSFU - reorderStreams: ERROR applying video changes: ${failure?.message}")
                    if (failure != null) {
                        Logger.e("ReorderStreams", "MediaSFU - reorderStreams: ERROR applying video changes: $failure")
                    }
                    failure?.printStackTrace()
                }
            } else {
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        Result.success(Unit)
    } catch (error: Exception) {
        Logger.e("ReorderStreams", "MediaSFU - reorderStreams: ERROR - caught exception: ${error.message}")
        error.printStackTrace()
        Result.failure(
            ReorderStreamsException(
                "reorderStreams error: ${error.message}",
                error
            )
        )
    }
}