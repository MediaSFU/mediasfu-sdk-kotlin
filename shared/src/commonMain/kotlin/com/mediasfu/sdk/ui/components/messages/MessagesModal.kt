package com.mediasfu.sdk.ui.components.messages
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.participants.ShowAlertOptions

/**
 * MessagesModal - Modal for displaying and sending messages.
 *
 * Provides tabbed interface for direct and group messaging with message history.
 *
 * @property options Configuration options for the messages modal
 */
data class MessagesModalOptions(
    val isMessagesModalVisible: Boolean = false,
    val onMessagesClose: () -> Unit,
    val onSendMessagePress: (SendMessageOptions) -> Unit,
    val messages: List<Message>,
    val position: String = "topRight",
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val activeTabBackgroundColor: Int = 0xFF96E7EC.toInt(),
    val eventType: EventType,
    val member: String,
    val islevel: String,
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val startDirectMessage: Boolean,
    val directMessageDetails: Participant?,
    val updateStartDirectMessage: (Boolean) -> Unit,
    val updateDirectMessageDetails: (Participant?) -> Unit,
    val roomName: String,
    val socket: SocketManager?,
    val chatSetting: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
)

data class SendMessageOptions(
    val message: String,
    val receivers: List<String>,
    val group: Boolean,
    val socket: SocketManager?,
    val roomName: String,
    val member: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
)

interface MessagesModal : MediaSfuUIComponent {
    val options: MessagesModalOptions
    override val id: String get() = "messages_modal"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets direct messages filtered by user permissions
     */
    fun getDirectMessages(): List<Message> {
        val member = options.member
        val islevel = options.islevel
        val coHost = options.coHost
        val chatValue = options.coHostResponsibility.any { it.name == "chat" && it.value }
        
        return options.messages.filter { message ->
            !message.group && (
                message.sender == member ||
                message.receivers.contains(member) ||
                islevel == "2" ||
                (coHost == member && chatValue)
            )
        }
    }
    
    /**
     * Gets group messages filtered by user permissions
     */
    fun getGroupMessages(): List<Message> {
        val member = options.member
        val islevel = options.islevel
        val coHost = options.coHost
        val chatValue = options.coHostResponsibility.any { it.name == "chat" && it.value }
        
        return options.messages.filter { message ->
            message.group && (
                islevel == "2" ||
                (coHost == member && chatValue) ||
                options.chatSetting != "noChatOnlyHost"
            )
        }
    }
    
    /**
     * Checks if direct messaging tab should be shown
     */
    fun shouldShowDirectTab(): Boolean {
        return options.eventType == EventType.WEBINAR || 
               options.eventType == EventType.CONFERENCE
    }
    
    /**
     * Checks if user can send messages
     */
    fun canSendMessage(): Boolean {
        val islevel = options.islevel
        val coHost = options.coHost
        val member = options.member
        val chatValue = options.coHostResponsibility.any { it.name == "chat" && it.value }
        
        return when (options.chatSetting) {
            "disallow" -> false
            "noChatOnlyHost" -> islevel == "2"
            else -> islevel == "2" || (coHost == member && chatValue)
        }
    }
}

/**
 * Default implementation of MessagesModal
 */
class DefaultMessagesModal(
    override val options: MessagesModalOptions
) : MessagesModal {
    fun render(): Any {
        return mapOf(
            "type" to "messagesModal",
            "isVisible" to options.isMessagesModalVisible,
            "onClose" to options.onMessagesClose,
            "directMessages" to getDirectMessages(),
            "groupMessages" to getGroupMessages(),
            "showDirectTab" to shouldShowDirectTab(),
            "canSendMessage" to canSendMessage(),
            "backgroundColor" to options.backgroundColor,
            "activeTabBackgroundColor" to options.activeTabBackgroundColor,
            "position" to options.position,
            "startDirectMessage" to options.startDirectMessage,
            "directMessageDetails" to options.directMessageDetails
        )
    }
}
