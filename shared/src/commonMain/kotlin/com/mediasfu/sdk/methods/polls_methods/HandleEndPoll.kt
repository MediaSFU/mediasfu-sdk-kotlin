package com.mediasfu.sdk.methods.polls_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Defines options for handling the end of a poll.
 */
data class HandleEndPollOptions(
    val pollId: String,
    val socket: SocketManager? = null,
    val showAlert: ShowAlert? = null,
    val roomName: String,
    val updateIsPollModalVisible: (Boolean) -> Unit
)

/**
 * Type definition for handling poll ending.
 */
typealias HandleEndPollType = suspend (HandleEndPollOptions) -> Unit

/**
 * Handles ending a poll by emitting an "endPoll" event through the provided socket.
 * Displays an alert based on the success or failure of the operation.
 * 
 * Example:
 * ```kotlin
 * val options = HandleEndPollOptions(
 *     pollId = "poll123",
 *     socket = socketInstance,
 *     showAlert = { message -> Logger.d("HandleEndPoll", message) },
 *     roomName = "roomA",
 *     updateIsPollModalVisible = { isVisible -> setIsPollModalVisible(isVisible) }
 * )
 * handleEndPoll(options)
 * ```
 */
suspend fun handleEndPoll(options: HandleEndPollOptions) {
    try {
        // Check if socket is connected
        if (options.socket == null) {
            options.showAlert.call(
                message = "Socket not connected",
                type = "danger",
                duration = 3000
            )
            return
        }
        
        // Emit socket event and wait for acknowledgment
        val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
            options.socket.emitWithAck(
                "endPoll",
                mapOf("roomName" to options.roomName, "poll_id" to options.pollId)
            ) { response ->
                continuation.resume(response as Map<String, Any>)
            }
        }
        
        if (result["success"] as? Boolean == true) {
            options.showAlert.call(
                message = "Poll ended successfully",
                type = "success",
                duration = 3000
            )
            // Don't close modal - let user see results
            // options.updateIsPollModalVisible(false)
        } else {
            options.showAlert.call(
                message = result["reason"] as? String ?: "Failed to end poll",
                type = "danger",
                duration = 3000
            )
        }
    } catch (error: Exception) {
        Logger.e("HandleEndPoll", "Error ending poll: $error")
        options.showAlert.call(
            message = "Failed to end poll: ${error.message}",
            type = "danger",
            duration = 3000
        )
    }
}
