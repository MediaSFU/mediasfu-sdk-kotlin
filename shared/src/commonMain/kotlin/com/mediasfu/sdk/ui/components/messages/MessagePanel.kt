package com.mediasfu.sdk.ui.components.messages

import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * MessagePanel - Panel for displaying and sending messages.
 *
 * Shows message history and input field for composing new messages.
 *
 * @property options Configuration options for the message panel
 */
data class MessagePanelOptions(
    val messages: List<Message>,
    val type: String, // "direct" or "group"
    val onSendMessage: (String) -> Unit,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val inputBackgroundColor: Int = 0xFFF0F0F0.toInt(),
    val member: String,
    val replyInfo: ReplyInfo? = null,
    val onClearReply: (() -> Unit)? = null,
)

data class ReplyInfo(
    val message: Message,
    val senderName: String,
)

interface MessagePanel : MediaSfuUIComponent {
    val options: MessagePanelOptions
    override val id: String get() = "message_panel"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Formats timestamp for message display
     */
    fun formatTimestamp(timestamp: String?): String {
        if (timestamp.isNullOrBlank()) return "--:--"

        val instant = timestamp.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }
            ?: runCatching { Instant.parse(timestamp) }.getOrNull()
            ?: return "--:--"

        val localTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${localTime.hour.toString().padStart(2, '0')}:${localTime.minute.toString().padStart(2, '0')}"
    }
    
    /**
     * Checks if message is sent by current user
     */
    fun isOwnMessage(message: Message): Boolean {
        return message.sender == options.member
    }
    
    /**
     * Gets message bubble style based on sender
     */
    fun getMessageBubbleStyle(message: Message): Map<String, Any> {
        val isOwn = isOwnMessage(message)
        
        return if (isOwn) {
            mapOf(
                "backgroundColor" to 0xFF007AFF.toInt(), // Blue for own messages
                "alignSelf" to "flex-end",
                "color" to 0xFFFFFFFF.toInt() // White text
            )
        } else {
            mapOf(
                "backgroundColor" to 0xFFE5E5EA.toInt(), // Gray for others
                "alignSelf" to "flex-start",
                "color" to 0xFF000000.toInt() // Black text
            )
        }
    }
}

/**
 * Default implementation of MessagePanel
 */
class DefaultMessagePanel(
    override val options: MessagePanelOptions
) : MessagePanel {
    fun render(): Any {
        val formattedMessages = options.messages.map { message ->
            mapOf(
                "message" to message,
                "timestamp" to formatTimestamp(message.timestamp),
                "isOwn" to isOwnMessage(message),
                "bubbleStyle" to getMessageBubbleStyle(message)
            )
        }
        
        return mapOf(
            "type" to "messagePanel",
            "messages" to formattedMessages,
            "messageType" to options.type,
            "onSendMessage" to options.onSendMessage,
            "backgroundColor" to options.backgroundColor,
            "inputBackgroundColor" to options.inputBackgroundColor,
            "replyInfo" to options.replyInfo,
            "onClearReply" to options.onClearReply
        )
    }
}
