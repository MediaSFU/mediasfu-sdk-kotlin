package com.mediasfu.sdk.methods.message_methods

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.model.toTransportMap
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Defines options for sending a message to a room.
 */
data class SendMessageOptions(
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val chatSetting: String,
    val message: String,
    val roomName: String,
    val messagesLength: Int,
    val receivers: List<String>,
    val group: Boolean,
    val sender: String,
    val socket: SocketManager? = null
)

/**
 * Type definition for the function that sends a message.
 */
typealias SendMessageType = suspend (SendMessageOptions) -> Unit

/**
 * Sends a message to the specified room.
 * 
 * This function checks the message limit, validates input, and
 * checks permissions based on user level and co-host responsibilities.
 * 
 * Example:
 * ```kotlin
 * val options = SendMessageOptions(
 *     member = "JohnDoe",
 *     islevel = "2",
 *     coHostResponsibility = listOf(CoHostResponsibility(name = "chat", value = true)),
 *     coHost = "JaneDoe",
 *     chatSetting = "allow",
 *     message = "Hello, world!",
 *     roomName = "Room123",
 *     messagesLength = 50,
 *     receivers = listOf("UserA", "UserB"),
 *     group = true,
 *     sender = "JohnDoe",
 *     socket = socketInstance
 * )
 * 
 * sendMessage(options)
 * ```
 */
suspend fun sendMessage(options: SendMessageOptions) {
    // Check message count limit based on the room type
    if ((options.messagesLength > 100 && options.roomName.startsWith("d")) ||
        (options.messagesLength > 500 && options.roomName.startsWith("s")) ||
        (options.messagesLength > 100000 && options.roomName.startsWith("p"))) {
    options.showAlert.call(
            message = "You have reached the maximum number of messages allowed.",
            type = "danger",
            duration = 3000
        )
        return
    }
    
    // Validate message, sender, and receivers
    if (options.message.isEmpty() ||
        (options.member.isEmpty() && options.sender.isEmpty())) {
    options.showAlert.call(
            message = "Message is not valid.",
            type = "danger",
            duration = 3000
        )
        return
    }
    
    // Create the message object
    val currentTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val timestamp = "${currentTime.hour.toString().padStart(2, '0')}:${currentTime.minute.toString().padStart(2, '0')}:${currentTime.second.toString().padStart(2, '0')}"
    
    val messageObject = Message(
        sender = if (options.sender.isNotEmpty()) options.sender else options.member,
        receivers = options.receivers,
        message = options.message,
        timestamp = timestamp,
        group = options.group
    )
    
    // Check co-host responsibility for chat
    val chatValue = options.coHostResponsibility
        .find { it.name == "chat" }
        ?.value ?: false
    
    if (options.islevel == "2" ||
        (options.coHost == options.member && chatValue)) {
        // Allow sending message
    } else {
        // Check if user is allowed to send a message in the event room
        if (options.chatSetting != "allow") {
            options.showAlert.call(
                message = "You are not allowed to send a message in this event room",
                type = "danger",
                duration = 3000
            )
            return
        }
    }
    
    // Send the message to the server
    options.socket?.emit("sendMessage", mapOf(
    "messageObject" to messageObject.toTransportMap(),
        "roomName" to options.roomName
    ))
}
