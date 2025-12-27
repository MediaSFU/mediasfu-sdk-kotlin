package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant

/**
 * Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/receive_message.dart.
 */
data class ReceiveMessageOptions(
    val message: Message,
    val messages: List<Message>,
    val participantsAll: List<Participant>,
    val member: String,
    val eventType: EventType,
    val isLevel: String,
    val coHost: String,
    val updateMessages: (List<Message>) -> Unit,
    val updateShowMessagesBadge: (Boolean) -> Unit
)

fun receiveMessage(options: ReceiveMessageOptions) {
    val incoming = options.message

    val mutableMessages = options.messages.toMutableList()
    mutableMessages += Message(
        sender = incoming.sender,
        receivers = incoming.receivers,
        message = incoming.message,
        timestamp = incoming.timestamp,
        group = incoming.group,
        extra = incoming.extra
    )

    val filteredMessages = if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        mutableMessages.filter { msg ->
            options.participantsAll.any { participant ->
                participant.name == msg.sender && !participant.isBanned
            }
        }
    } else {
        mutableMessages.filter { msg ->
            val participant = options.participantsAll.firstOrNull { it.name == msg.sender }
                ?: Participant(name = "", isBanned = true)
            !participant.isBanned
        }
    }

    options.updateMessages(filteredMessages)

    val oldGroupMessages = options.messages.filter { it.group }
    val oldDirectMessages = options.messages.filterNot { it.group }
    val groupMessages = filteredMessages.filter { it.group }
    val directMessages = filteredMessages.filterNot { it.group }

    if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        if (oldGroupMessages.size != groupMessages.size) {
            val newGroupMessages = groupMessages.filter { newMsg ->
                oldGroupMessages.none { oldMsg -> oldMsg.timestamp == newMsg.timestamp }
            }

            val relevantNewGroupMessages = newGroupMessages.filter { msg ->
                msg.sender == options.member || msg.receivers.contains(options.member)
            }

            if (newGroupMessages.isNotEmpty() && newGroupMessages.size != relevantNewGroupMessages.size) {
                options.updateShowMessagesBadge(true)
            }
        }
    }

    if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        if (oldDirectMessages.size != directMessages.size) {
            val newDirectMessages = directMessages.filter { newMsg ->
                oldDirectMessages.none { oldMsg -> oldMsg.timestamp == newMsg.timestamp }
            }

            val relevantNewDirectMessages = newDirectMessages.filter { msg ->
                msg.sender == options.member || msg.receivers.contains(options.member)
            }

            val isAdminOrCoHost = options.isLevel == "2" || options.coHost == options.member

            if ((newDirectMessages.isNotEmpty() && relevantNewDirectMessages.isNotEmpty()) ||
                (newDirectMessages.isNotEmpty() && isAdminOrCoHost)
            ) {
                if (isAdminOrCoHost) {
                    if (newDirectMessages.size != relevantNewDirectMessages.size) {
                        options.updateShowMessagesBadge(true)
                    }
                } else if (relevantNewDirectMessages.isNotEmpty()) {
                    if (newDirectMessages.size != relevantNewDirectMessages.size) {
                        options.updateShowMessagesBadge(true)
                    }
                }
            }
        }
    }
}
