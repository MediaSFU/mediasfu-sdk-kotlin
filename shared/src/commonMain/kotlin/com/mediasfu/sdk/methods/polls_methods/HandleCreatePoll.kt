package com.mediasfu.sdk.methods.polls_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.model.toTransportMap
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Defines options for creating a poll in a room.
 */
data class HandleCreatePollOptions(
    val poll: Poll,
    val socket: SocketManager? = null,
    val roomName: String,
    val showAlert: ShowAlert? = null,
    val updateIsPollModalVisible: (Boolean) -> Unit
)

/**
 * Type definition for handling poll creation.
 */
typealias HandleCreatePollType = suspend (HandleCreatePollOptions) -> Unit

/**
 * Handles the creation of a poll by emitting a "createPoll" event with the provided details.
 * Shows an alert based on the success or failure of the operation.
 * 
 * Example:
 * ```kotlin
 * val options = HandleCreatePollOptions(
 *     poll = Poll(question = "Favorite color?", type = "singleChoice", options = listOf("Red", "Blue", "Green")),
 *     socket = socketInstance,
 *     roomName = "roomA",
 *     showAlert = { message -> Logger.d("HandleCreatePoll", message) },
 *     updateIsPollModalVisible = { isVisible -> setIsPollModalVisible(isVisible) }
 * )
 * handleCreatePoll(options)
 * ```
 */
suspend fun handleCreatePoll(options: HandleCreatePollOptions) {
    try {
    val pollMap = options.poll.toTransportMap().toMutableMap()
        // keep only the question, type, and options
        pollMap.keys.removeAll { key -> key !in listOf("question", "type", "options") }
        
        // Emit socket event and wait for acknowledgment
        val result = suspendCancellableCoroutine<Map<String, Any>> { continuation ->
            options.socket?.emitWithAck(
                "createPoll",
                mapOf("roomName" to options.roomName, "poll" to pollMap)
            ) { response ->
                continuation.resume(response as Map<String, Any>)
            }
        }
        
        if (result["success"] as? Boolean == true) {
            options.showAlert.call(
                message = "Poll created successfully",
                type = "success",
                duration = 3000
            )
            options.updateIsPollModalVisible(false)
        } else {
            options.showAlert.call(
                message = result["reason"] as? String ?: "Failed to create poll",
                type = "danger",
                duration = 3000
            )
        }
    } catch (error: Exception) {
        Logger.e("HandleCreatePoll", "Error creating poll: $error")
    }
}
