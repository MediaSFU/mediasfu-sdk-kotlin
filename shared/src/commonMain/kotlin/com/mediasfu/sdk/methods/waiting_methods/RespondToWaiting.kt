package com.mediasfu.sdk.methods.waiting_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.WaitingRoomParticipant
import com.mediasfu.sdk.socket.SocketManager

/**
 * Options for responding to a waiting participant.
 */
data class RespondToWaitingOptions(
    val participantId: String,
    val participantName: String,
    val updateWaitingList: (List<WaitingRoomParticipant>) -> Unit,
    val waitingList: List<WaitingRoomParticipant>,
    val type: Any, // Can be either a string or boolean
    val roomName: String,
    val socket: SocketManager? = null
)

/**
 * Type definition for responding to waiting.
 */
typealias RespondToWaitingType = suspend (RespondToWaitingOptions) -> Unit

/**
 * Responds to a waiting participant by allowing or denying access based on specified options.
 * 
 * This function uses `RespondToWaitingOptions` to handle a participant in the waiting room,
 * updating the waiting list and emitting an event through a socket to approve or deny access.
 * The response type (`"true"` or `"false"`) is determined based on the `type` provided.
 * 
 * ## Parameters:
 * - [options] - An instance of `RespondToWaitingOptions` containing:
 *   - `participantId`: The unique identifier for the participant.
 *   - `participantName`: The name of the participant.
 *   - `updateWaitingList`: A function to update the waiting list.
 *   - `waitingList`: The current list of participants in the waiting room.
 *   - `type`: The approval type, which can be a `bool` or `String` (`"true"` or `"false"`).
 *   - `roomName`: The name of the room the participant is waiting to join.
 *   - `socket`: The socket used to emit the response event.
 * 
 * ## Example Usage:
 * 
 * ```kotlin
 * // Define a sample waiting list and an update function
 * val waitingList = listOf(
 *     WaitingRoomParticipant(name = "John Doe", id = "123"),
 *     WaitingRoomParticipant(name = "Jane Smith", id = "456")
 * )
 * 
 * val updateWaitingList: (List<WaitingRoomParticipant>) -> Unit = { newList ->
 * }
 * 
 * // Initialize options for responding to a participant
 * val options = RespondToWaitingOptions(
 *     participantId = "123",
 *     participantName = "John Doe",
 *     updateWaitingList = updateWaitingList,
 *     waitingList = waitingList,
 *     type = true, // Allow participant
 *     roomName = "MainRoom",
 *     socket = socketInstance // Assume socket connection is established
 * )
 * 
 * // Call respondToWaiting to process the response
 * respondToWaiting(options)
 * // Expected output:
 * // Updated waiting list: [Jane Smith]
 * // Emits an event to allow "John Doe" to join the "MainRoom".
 * ```
 */
suspend fun respondToWaiting(options: RespondToWaitingOptions) {
    // Filter out the participant from the waiting list
    val newWaitingList = options.waitingList.filter { item ->
        item.name != options.participantName
    }
    
    // Update the waiting list
    options.updateWaitingList(newWaitingList)
    
    // Determine the response type as a string ("true" or "false")
    val responseType = when (options.type) {
        true, "true" -> "true"
        else -> "false"
    }
    
    try {
        // Emit an event to allow or deny the participant based on the response type
        options.socket?.emit("allowUserIn", mapOf(
            "participantId" to options.participantId,
            "participantName" to options.participantName,
            "type" to responseType,
            "roomName" to options.roomName
        ))
    } catch (error: Exception) {
        // Handle any socket-related errors here if needed
        Logger.e("RespondToWaiting", "Error responding to waiting: $error")
    }
}
