package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.methods.message_methods.SendMessageOptions
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun MessagesModal(state: MediasfuGenericState) {
    val props = state.createMessagesModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.messagesModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultMessagesModalContent(it) }
    )

    contentBuilder(props)
}

@Composable
private fun DefaultMessagesModalContent(props: MessagesModalProps) {
    // Determine initial tab based on event type
    val initialTab = remember(props.eventType) {
        when (props.eventType) {
            EventType.WEBINAR, EventType.CONFERENCE -> "direct"
            else -> "group"
        }
    }
    
    var selectedTab by remember { mutableStateOf(initialTab) }
    
    // Handle direct message initiation
    LaunchedEffect(props.startDirectMessage, props.directMessageDetails) {
        if (props.startDirectMessage && props.directMessageDetails != null) {
            if (props.eventType == EventType.WEBINAR || props.eventType == EventType.CONFERENCE) {
                selectedTab = "direct"
            }
        }
    }

    AlertDialog(
        onDismissRequest = props.onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close messages")
                }
            }
        },
        text = {
            MessagesModalContentBody(
                props = props,
                selectedTab = selectedTab,
                onTabChange = { selectedTab = it },
                modifier = Modifier.heightIn(min = 300.dp, max = 500.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = props.onClose) { Text("Close") }
        }
    )
}

/**
 * Public content body for MessagesModal - can be used in unified modal system
 * Contains full functionality: tabs, message list, send messages with permissions
 */
@Composable
fun MessagesModalContentBody(
    props: MessagesModalProps,
    selectedTab: String = "group",
    onTabChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Filter messages
    val directMessages = remember(props.messages, props.member, props.islevel, props.coHost, props.coHostResponsibility) {
        val chatValue = props.coHostResponsibility.find { it.name == "chat" }?.value == true
        props.messages.filter { message ->
            !message.group && (
                message.sender == props.member ||
                message.receivers.contains(props.member) ||
                props.islevel == "2" ||
                (props.coHost == props.member && chatValue)
            )
        }
    }
    
    val groupMessages = remember(props.messages) {
        props.messages.filter { it.group }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Tabs (only show for webinar/conference)
        if (props.eventType == EventType.WEBINAR || props.eventType == EventType.CONFERENCE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MessageTab(
                    text = "Direct",
                    selected = selectedTab == "direct",
                    onClick = { onTabChange("direct") },
                    modifier = Modifier.weight(1f)
                )
                MessageTab(
                    text = "Group",
                    selected = selectedTab == "group",
                    onClick = { onTabChange("group") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Message Panel
        val displayMessages = if (selectedTab == "direct") directMessages else groupMessages
        MessagePanel(
            messages = displayMessages,
            type = selectedTab,
            member = props.member,
            islevel = props.islevel,
            coHost = props.coHost,
            coHostResponsibility = props.coHostResponsibility,
            chatSetting = props.chatSetting,
            directMessageDetails = props.directMessageDetails,
            roomName = props.roomName,
            socket = props.socket,
            showAlert = props.showAlert,
            onSendMessage = props.onSendMessage
        )
    }
}

@Composable
private fun MessageTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Text(text, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun MessagePanel(
    messages: List<Message>,
    type: String,
    member: String,
    islevel: String,
    coHost: String,
    coHostResponsibility: List<CoHostResponsibility>,
    chatSetting: String,
    directMessageDetails: Participant?,
    roomName: String,
    socket: SocketManager?,
    showAlert: ShowAlert?,
    onSendMessage: (SendMessageOptions) -> Unit
) {
    var messageText by remember(type) { mutableStateOf("") }
    val timestamps = remember(messages) { messages.map { resolveMessageTimestamp(it.timestamp) } }
    
    // Calculate send permission
    val canSend = remember(islevel, coHost, member, coHostResponsibility, chatSetting) {
        when {
            chatSetting == "disallow" -> false
            islevel == "2" -> true
            coHost == member -> coHostResponsibility.find { it.name == "chat" }?.value == true
            else -> chatSetting == "allow"
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Messages List
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (type == "direct") "No direct messages yet" else "No group messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = false
            ) {
                itemsIndexed(messages, key = { index, message -> "${message.timestamp}_${message.sender}_$index" }) { index, message ->
                    val timestamp = timestamps[index]
                    val previousDate = timestamps.getOrNull(index - 1)?.dateLabel
                    if (timestamp.dateLabel.isNotBlank() && timestamp.dateLabel != previousDate) {
                        MessageDateDivider(timestamp.dateLabel)
                    }
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.sender.equals(member, ignoreCase = true),
                        timeLabel = timestamp.timeLabel
                    )
                }
            }
        }
        
        // Input Section
        Spacer(modifier = Modifier.height(12.dp))
        
        if (canSend) {
            // For direct messages, require recipient selection for non-hosts
            val isHost = islevel == "2"
            val directRecipientMissing = type == "direct" && directMessageDetails == null && !isHost
            val sendEnabled = messageText.isNotBlank() && !directRecipientMissing

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (type == "direct") {
                    val labelText = if (directMessageDetails != null) {
                        "Direct message to ${directMessageDetails.name}"
                    } else if (isHost) {
                        "Direct message will be sent to host"
                    } else {
                        "Select a participant to send a direct message"
                    }
                    val labelColor = if (directRecipientMissing) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelMedium,
                        color = labelColor
                    )
                } else {
                    Text(
                        text = "Messages to everyone",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Type a message...") },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 3
                    )

                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                val isHost = islevel == "2"
                                
                                // Block non-hosts from sending direct messages without recipient
                                if (type == "direct" && directMessageDetails == null && !isHost) {
                                    showAlert?.call(
                                        message = "Please select a participant from the Participants list to send a direct message.",
                                        type = "danger",
                                        duration = 3000
                                    )
                                    return@IconButton
                                }

                                // Determine receivers
                                val receivers = if (type == "direct") {
                                    if (directMessageDetails != null) {
                                        listOf(directMessageDetails.name)
                                    } else if (isHost) {
                                        // Host can send to themselves if no recipient selected
                                        emptyList()
                                    } else {
                                        // This shouldn't happen due to button disabled state
                                        emptyList()
                                    }
                                } else {
                                    emptyList()
                                }

                                onSendMessage(
                                    SendMessageOptions(
                                        member = member,
                                        islevel = islevel,
                                        showAlert = showAlert,
                                        coHostResponsibility = coHostResponsibility,
                                        coHost = coHost,
                                        chatSetting = chatSetting,
                                        message = messageText.trim(),
                                        roomName = roomName,
                                        messagesLength = messages.size,
                                        receivers = receivers,
                                        group = type == "group",
                                        sender = member,
                                        socket = socket
                                    )
                                )
                                messageText = ""
                            }
                        },
                        enabled = sendEnabled
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (sendEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        } else {
            Text(
                text = "Chat is disabled for this event",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MessageBubble(message: Message, isCurrentUser: Boolean, timeLabel: String) {
    val alignment = if (isCurrentUser) Arrangement.End else Arrangement.Start
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val bubbleContentColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val recipientsLabel = if (!message.group && message.receivers.isNotEmpty()) {
        "Direct message to ${message.receivers.joinToString()}"
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = alignment
    ) {
        Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = bubbleColor,
                contentColor = bubbleContentColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isCurrentUser) "You" else message.sender,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (recipientsLabel != null) {
                        Text(
                            text = recipientsLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = bubbleContentColor.copy(alpha = 0.8f)
                        )
                    }
                    Text(text = message.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageDateDivider(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

private data class FormattedMessageTimestamp(val dateLabel: String, val timeLabel: String)

private fun resolveMessageTimestamp(raw: String): FormattedMessageTimestamp {
    if (raw.isBlank()) return FormattedMessageTimestamp("", "")
    return runCatching {
        val instant = Instant.parse(raw)
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val dateLabel = local.date.toString()
        val timeLabel = "${local.time.hour.toString().padStart(2, '0')}:${local.time.minute.toString().padStart(2, '0')}"
        FormattedMessageTimestamp(dateLabel, timeLabel)
    }.getOrElse {
        FormattedMessageTimestamp("", raw)
    }
}
