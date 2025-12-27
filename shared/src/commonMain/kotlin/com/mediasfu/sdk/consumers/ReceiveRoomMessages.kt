package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager

/**
 * Data class representing a message.
 *
 * @property sender The sender of the message
 * @property content The content/text of the message
 * @property timestamp The timestamp when the message was sent
 */
data class Message(
    val sender: String = "",
    val content: String = "",
    val timestamp: Long = 0L,
    val group: Boolean = false,
    val message: String = ""
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): Message {
            return Message(
                sender = map["sender"] as? String ?: "",
                content = map["content"] as? String ?: "",
                timestamp = (map["timestamp"] as? Number)?.toLong() ?: 0L,
                group = map["group"] as? Boolean ?: false,
                message = map["message"] as? String ?: ""
            )
        }
    }
}

/**
 * Options for receiving room messages.
 *
 * @property socket The socket connection to communicate with the server
 * @property roomName The name of the room for which messages are to be retrieved
 * @property updateMessages Callback to update the message list with retrieved messages
 */
data class ReceiveRoomMessagesOptions(
    val socket: SocketManager?,
    val roomName: String,
    val updateMessages: (List<Message>) -> Unit
)

/**
 * Retrieves messages from a specified room using a socket connection.
 *
 * This function emits a socket event to request messages for a specific room and updates
 * the message list when a response is received.
 *
 * @param options The options containing socket, room name, and update callback
 *
 * Example:
 * ```kotlin
 * val options = ReceiveRoomMessagesOptions(
 *     socket = socketManager,
 *     roomName = "Room1",
 *     updateMessages = { messages ->
 *     }
 * )
 * receiveRoomMessages(options)
 * ```
 */
suspend fun receiveRoomMessages(options: ReceiveRoomMessagesOptions) {
    try {
        // Request messages from the server
        val responseData = options.socket?.emitWithAck<Any>(
            event = "getMessage",
            data = mapOf("roomName" to options.roomName)
        )

        if (responseData != null) {
            try {
                val responseMap = responseData as? Map<*, *>
                val messagesList = responseMap?.get("messages_") as? List<*>

                if (messagesList != null) {
                    val messages = messagesList.mapNotNull { item ->
                        (item as? Map<*, *>)?.let { map ->
                            @Suppress("UNCHECKED_CAST")
                            Message.fromMap(map as Map<String, Any?>)
                        }
                    }
                    options.updateMessages(messages)
                } else {
                    Logger.e("ReceiveRoomMessages", "Error: Invalid message format received.")
                }
            } catch (e: Exception) {
                Logger.e("ReceiveRoomMessages", "Error parsing messages: ${e.message}")
            }
        }
    } catch (error: Exception) {
        // Handle errors if any
        Logger.e("ReceiveRoomMessages", "Error receiving messages: ${error.message}")
    }
}

