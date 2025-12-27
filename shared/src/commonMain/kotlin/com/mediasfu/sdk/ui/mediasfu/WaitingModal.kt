package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.WaitingRoomParticipant

/**
 * WaitingModal - Manages the waiting room for participants
 * 
 * This modal displays participants in the waiting room who are requesting to join.
 * Hosts and co-hosts can:
 * - View all waiting participants
 * - Search/filter waiting participants
 * - Allow participants to join the event
 * - Deny participant access
 * 
 * @param state The MediasfuGenericState containing all necessary state and callbacks
 */
@Composable
fun WaitingModal(state: MediasfuGenericState) {
    val props = state.createWaitingModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.waitingModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultWaitingModalContent(it) }
    )

    contentBuilder(props)
}

/**
 * Public content body for WaitingModal - can be embedded in unified modal system
 * Provides full waiting room management functionality without AlertDialog wrapper
 */
@Composable
fun WaitingModalContentBody(
    props: WaitingModalProps,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Search waiting participants") },
            modifier = Modifier.fillMaxWidth()
        )

        if (props.participants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No one is waiting",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(props.participants, key = { it.id }) { participant ->
                    WaitingParticipantRow(
                        participant = participant,
                        onAllow = { props.onAllow(participant) },
                        onDeny = { props.onDeny(participant) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultWaitingModalContent(props: WaitingModalProps) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Waiting Room (${props.pendingCount})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close waiting room")
                }
            }
        },
        text = {
            WaitingModalContentBody(
                props = props,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = props.onClose) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun WaitingParticipantRow(
    participant: WaitingRoomParticipant,
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1C2B4A))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Rounded.Group, contentDescription = null, tint = Color.White)
            Text(participant.name, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onAllow,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x3352C41A))
                    .border(1.dp, Color(0xFF52C41A), CircleShape)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Allow participant", tint = Color(0xFF52C41A))
            }
            IconButton(
                onClick = onDeny,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF4D4F))
                    .border(1.dp, Color(0xFFFF4D4F), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Deny participant", tint = Color(0xFFFF4D4F))
            }
        }
    }
}
