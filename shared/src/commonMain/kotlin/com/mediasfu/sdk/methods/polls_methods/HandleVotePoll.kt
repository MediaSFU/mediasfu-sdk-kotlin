package com.mediasfu.sdk.methods.polls_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Defines options for handling a poll vote.
 */
data class HandleVotePollOptions(
    val pollId: String,
    val optionIndex: Int,
    val socket: SocketManager? = null,
    val showAlert: ShowAlert? = null,
    val member: String,
    val roomName: String,
    val updateIsPollModalVisible: (Boolean) -> Unit
)

/**
 * Type definition for handling poll voting.
 */
typealias HandleVotePollType = suspend (HandleVotePollOptions) -> Unit

/**
 * Handles the voting process for a poll.
 * 
 * The function submits a vote to the server using the specified [HandleVotePollOptions].
 * 
 * Example:
 * ```kotlin
 * val options = HandleVotePollOptions(
 *     pollId = "poll123",
 *     optionIndex = 1,
 *     socket = socketInstance,
 *     showAlert = { message -> Logger.d("HandleVotePoll", message) },
 *     member = "user1",
 *     roomName = "roomA",
 *     updateIsPollModalVisible = { isVisible -> setPollModalVisible(isVisible) }
 * )
 * handleVotePoll(options)
 * ```
 */
suspend fun handleVotePoll(options: HandleVotePollOptions) {
    try {
        // Emit socket event and wait for acknowledgment
        val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
            options.socket?.emitWithAck(
                "votePoll",
                mapOf(
                    "roomName" to options.roomName,
                    "poll_id" to options.pollId,
                    "member" to options.member,
                    "choice" to options.optionIndex
                )
            ) { response ->
                continuation.resume(response as Map<String, Any>)
            }
        }
        
        if (result["success"] as? Boolean == true) {
            options.showAlert.call(
                message = "Vote submitted successfully",
                type = "success",
                duration = 3000
            )
            options.updateIsPollModalVisible(false)
        } else {
            options.showAlert.call(
                message = result["reason"] as? String ?: "Failed to submit vote",
                type = "danger",
                duration = 3000
            )
        }
    } catch (error: Exception) {
        Logger.e("HandleVotePoll", "Error submitting vote: $error")
    }
}
