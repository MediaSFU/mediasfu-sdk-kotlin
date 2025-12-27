package com.mediasfu.sdk.methods.polls_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.model.PollUpdatedData
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call

/**
 * Defines options for updating poll information.
 */
data class PollUpdatedOptions(
    val data: PollUpdatedData,
    val polls: List<Poll>,
    val poll: Poll? = null,
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val updatePolls: (List<Poll>) -> Unit,
    val updatePoll: (Poll) -> Unit,
    val updateIsPollModalVisible: (Boolean) -> Unit
)

/**
 * Type definition for the function that updates poll information.
 */
typealias PollUpdatedType = suspend (PollUpdatedOptions) -> Unit

/**
 * Updates the poll state based on the provided options.
 * 
 * This function checks the poll's status and updates the state accordingly.
 * If a new poll starts, it displays an alert and opens the poll modal for eligible members.
 * 
 * Parameters:
 * - [options]: The [PollUpdatedOptions] containing details such as the poll data, member level,
 *   and update functions for the poll state.
 * 
 * Example:
 * ```kotlin
 * val options = PollUpdatedOptions(
 *     data = PollUpdatedData(poll = updatedPoll, status = "started"),
 *     polls = currentPolls,
 *     poll = currentPoll,
 *     member = "user123",
 *     islevel = "1",
 *     showAlert = { alert -> Logger.d("PollUpdated", alert.message) },
 *     updatePolls = { updatedPolls -> setPolls(updatedPolls) },
 *     updatePoll = { updatedPoll -> setCurrentPoll(updatedPoll) },
 *     updateIsPollModalVisible = { visible -> setIsPollModalVisible(visible) }
 * )
 * 
 * pollUpdated(options)
 * ```
 */
suspend fun pollUpdated(options: PollUpdatedOptions) {
    try {
        var polls = options.polls
        
        var poll = options.poll ?: Poll(
            id = "",
            question = "",
            type = "",
            options = emptyList(),
            votes = emptyList(),
            status = "",
            voters = emptyMap()
        )

        val incomingPolls = options.data.polls
        
        polls = if (incomingPolls != null && incomingPolls.isNotEmpty()) {
            // Server sent full list of polls (non-empty)
            incomingPolls
        } else {
            // Server sent single poll update OR empty polls array - merge with existing polls
            val updatedPoll = options.data.poll
            val existingPolls = polls.toMutableList()
            val existingIndex = existingPolls.indexOfFirst { it.id == updatedPoll.id }
            
            if (existingIndex >= 0) {
                // Update existing poll
                existingPolls[existingIndex] = updatedPoll
            } else {
                // Add new poll
                existingPolls.add(updatedPoll)
            }
            existingPolls
        }
        
        options.updatePolls(polls)

        val previousPoll = if (poll.id.isNullOrEmpty()) {
            null
        } else {
            poll
        }

        if (options.data.status != "ended") {
            poll = options.data.poll
            options.updatePoll(poll)
        }

        if (options.data.status == "started" && options.islevel != "2") {
            val hasVoted = poll.voters.containsKey(options.member)
            if (!hasVoted) {
                options.showAlert.call(
                    message = "New poll started",
                    type = "success",
                    duration = 3000
                )
                options.updateIsPollModalVisible(true)
            }
        } else if (options.data.status == "ended") {
            if (previousPoll?.id == options.data.poll.id) {
                options.showAlert.call(
                    message = "Poll ended",
                    type = "danger",
                    duration = 3000
                )
            }
            // Always update the poll when ended (matches React behavior)
            options.updatePoll(options.data.poll)
        }
    } catch (error: Exception) {
        Logger.e("PollUpdated", "Error updating poll: $error")
    }
}
