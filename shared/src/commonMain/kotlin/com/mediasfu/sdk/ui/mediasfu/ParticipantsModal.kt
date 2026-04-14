package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.*

@Composable
fun ParticipantsModal(state: MediasfuGenericState) {
    val props = state.createParticipantsModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.participantsModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultParticipantsModalContent(it) }
    )

    contentBuilder(props)
}

@Composable
private fun DefaultParticipantsModalContent(props: ParticipantsModalProps) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participants (${props.participantCount})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close participants")
                }
            }
        },
        text = {
            ParticipantsModalContentBody(props = props)
        },
        confirmButton = {
            TextButton(onClick = props.onClose) {
                Text("Close")
            }
        }
    )
}

/**
 * Public content body for ParticipantsModal - can be used in unified modal system
 * Contains full functionality: filter, list, mute, message, remove participants
 */
@Composable
fun ParticipantsModalContentBody(
    props: ParticipantsModalProps,
    modifier: Modifier = Modifier
) {
    val panelists by props.state.panelists.collectAsState()
    val panelistsFocused by props.state.panelistsFocused.collectAsState()
    val muteOthersMic by props.state.muteOthersMic.collectAsState()
    val muteOthersCamera by props.state.muteOthersCamera.collectAsState()
    val currentUserIsHost = props.state.room.youAreHost || props.state.room.islevel.equals("2", ignoreCase = true)
    val panelistLimit = props.state.media.itemPageLimit.takeIf { it > 0 } ?: 10
    val audienceMic = props.state.permissionValue(level = "level0", key = "useMic", default = "approval")
    val audienceCamera = props.state.permissionValue(level = "level0", key = "useCamera", default = "approval")
    val audienceScreen = props.state.permissionValue(level = "level0", key = "useScreen", default = "disallow")
    val audienceChat = props.state.permissionValue(level = "level0", key = "useChat", default = "allow")

    val visibleParticipants = props.filteredParticipants
        .takeIf { it.isNotEmpty() || props.filter.isNotBlank() }
        ?: props.participants

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Filter participants") },
            modifier = Modifier.fillMaxWidth()
        )

        if (currentUserIsHost && (props.state.room.eventType == EventType.WEBINAR || props.state.room.eventType == EventType.CONFERENCE)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Panelists: ${panelists.size}/$panelistLimit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { props.state.togglePanelistFocus() }) {
                            Text(if (panelistsFocused) "Disable Focus" else "Enable Focus")
                        }
                        TextButton(
                            enabled = panelistsFocused,
                            onClick = { props.state.togglePanelistFocusMuteMic() }
                        ) {
                            Text(if (muteOthersMic) "Unmute Others Mic" else "Mute Others Mic")
                        }
                        TextButton(
                            enabled = panelistsFocused,
                            onClick = { props.state.togglePanelistFocusMuteCamera() }
                        ) {
                            Text(if (muteOthersCamera) "Unmute Others Cam" else "Mute Others Cam")
                        }
                        TextButton(
                            enabled = panelists.isNotEmpty(),
                            onClick = { props.state.clearAllPanelists() }
                        ) {
                            Text("Clear Panelists")
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { props.state.cycleAudienceMicPermission() }) {
                            Text("Mic: $audienceMic")
                        }
                        TextButton(onClick = { props.state.cycleAudienceCameraPermission() }) {
                            Text("Cam: $audienceCamera")
                        }
                        TextButton(onClick = { props.state.cycleAudienceScreenPermission() }) {
                            Text("Screen: $audienceScreen")
                        }
                        TextButton(onClick = { props.state.toggleAudienceChatPermission() }) {
                            Text("Chat: $audienceChat")
                        }
                    }
                }
            }
        }

        if (visibleParticipants.isNotEmpty()) {
            Text(
                text = "Showing ${visibleParticipants.size} of ${props.participantCount} participants",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visibleParticipants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyMessage = if (props.filter.isBlank()) {
                    "No one has joined the event yet."
                } else {
                    "No participants match \"${props.filter}\"."
                }
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleParticipants, key = { it.id ?: it.name }) { participant ->
                    ParticipantRow(
                        participant = participant,
                        isCurrentUser = participant.name.equals(props.state.room.member, ignoreCase = true),
                        state = props.state
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantRow(participant: Participant, isCurrentUser: Boolean, state: MediasfuGenericState) {
    val isHost = participant.isHost || participant.islevel.equals("2", ignoreCase = true)
    val currentUserIsHost = state.room.youAreHost || state.room.islevel.equals("2", ignoreCase = true)
    val isPanelist = state.panelists.value.any { it.id == participant.id }
    val statusLabels = buildList {
        if (isCurrentUser) add("You")
        if (isHost) add("Host")
        else if (participant.isAdmin) add("Admin")
        if (isPanelist) add("Panelist")
        if (participant.breakRoom != null) add("Breakout ${participant.breakRoom}")
        if (participant.isBanned) add("Banned")
        if (participant.isSuspended) add("Suspended")
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = participant.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isHost) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (statusLabels.isNotEmpty()) {
                        Text(
                            text = statusLabels.joinToString(separator = " • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show media status if not broadcast
                if (state.room.eventType != EventType.BROADCAST) {
                    MediaStatusIndicator(participant)
                }

                // Check permissions
                val chatValue = state.room.coHostResponsibility.find { it.name == "chat" }?.value == true
                val participantsValue = state.room.coHostResponsibility.find { it.name == "participants" }?.value == true
                
                // Message button: only if not self, and user is host OR co-host with chat permission
                val canMessage = !isCurrentUser && (currentUserIsHost || (state.room.coHost == state.room.member && chatValue))
                
                // Mute button (only show if not broadcast): only if participant is not host, and user is host OR co-host with participants permission
                val canMute = !isHost && (currentUserIsHost || (state.room.coHost == state.room.member && participantsValue)) && state.room.eventType != EventType.BROADCAST
                
                // Remove button: only if user is host, not self, and target is not host
                val canRemove = currentUserIsHost && !isCurrentUser && !isHost
                val canManagePanelist = currentUserIsHost && !isCurrentUser && !isHost && participant.id != null

                // Show mute button only if user has permission and not broadcast
                if (canMute) {
                    val isMuted = participant.muted ?: false
                    IconButton(
                        onClick = {
                            state.muteParticipant(participant)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            contentDescription = if (isMuted) "Unmute participant" else "Mute participant",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Show message button only if user has permission
                if (canMessage) {
                    IconButton(
                        onClick = {
                            state.messaging.directMessageDetails = participant
                            state.messaging.startDirectMessage = true
                            state.openMessages()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Message,
                            contentDescription = "Send message",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (canManagePanelist) {
                    IconButton(
                        onClick = {
                            if (isPanelist) state.removePanelist(participant) else state.addPanelist(participant)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isPanelist) Icons.Rounded.Remove else Icons.Rounded.Add,
                            contentDescription = if (isPanelist) "Remove panelist" else "Add panelist",
                            tint = if (isPanelist) Color(0xFFFF9800) else Color(0xFF52C41A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Show remove button only if user has permission
                if (canRemove) {
                    IconButton(
                        onClick = { state.removeParticipant(participant) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Remove participant",
                            tint = Color(0xFFFF4D4F),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaStatusIndicator(participant: Participant) {
    val audioActive = participant.audioOn && !participant.muted
    val videoActive = participant.videoOn
    val color = when {
        audioActive && videoActive -> Color(0xFF34C759)
        audioActive || videoActive -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFFFF4D4F)
    }

    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        if (!audioActive && !videoActive) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onError,
                fontSize = 8.sp
            )
        }
    }
}

@Composable
private fun AudioStatusIcon(participant: Participant) {
    val audioActive = participant.audioOn && !participant.muted
    val icon = if (audioActive) Icons.Rounded.Mic else Icons.Rounded.MicOff
    val tint = if (audioActive) MaterialTheme.colorScheme.primary else Color(0xFFFF4D4F)
    Icon(icon, contentDescription = if (audioActive) "Microphone on" else "Microphone muted", tint = tint)
}
