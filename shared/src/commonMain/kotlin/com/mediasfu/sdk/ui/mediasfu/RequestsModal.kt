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
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoCameraFront
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.Request

/**
 * RequestsModal - Displays and manages participant requests
 * 
 * This modal shows pending requests from participants for actions such as:
 * - Unmuting audio
 * - Enabling video
 * - Screen sharing
 * - Chat permissions
 * - Other custom requests
 * 
 * Hosts and co-hosts can accept or reject each request.
 * 
 * @param state The MediasfuGenericState containing all necessary state and callbacks
 */
@Composable
fun RequestsModal(state: MediasfuGenericState) {
    val props = state.createRequestsModalProps()
        if (!props.isVisible) return

        val overrideContent = state.options.uiOverrides.requestsModal
        val contentBuilder = withOverride(
            override = overrideContent,
            baseBuilder = { DefaultRequestsModalContent(it) }
        )

        contentBuilder(props)
}

/**
 * Public content body for RequestsModal - can be embedded in unified modal system
 * Provides full request management functionality without AlertDialog wrapper
 */
@Composable
fun RequestsModalContentBody(
    props: RequestsModalProps,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Search requests") },
            modifier = Modifier.fillMaxWidth()
        )

        if (props.requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No pending requests",
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
                items(props.requests, key = { it.id + it.icon }) { request ->
                    RequestRow(
                        request = request,
                        onAccept = { props.onAccept(request) },
                        onReject = { props.onReject(request) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultRequestsModalContent(props: RequestsModalProps) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Requests (${props.pendingCount})",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close requests")
                }
            }
        },
        text = {
            RequestsModalContentBody(
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
private fun RequestRow(
    request: Request,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val displayName = request.name?.takeIf { it.isNotBlank() } ?: request.username?.takeIf { it.isNotBlank() } ?: "Participant"
    val secondary = request.username?.takeIf { it.isNotBlank() && !it.equals(displayName, ignoreCase = true) }
    val icon = resolveRequestIcon(request.icon)

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
            Icon(icon, contentDescription = null, tint = Color.White)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                if (secondary != null) {
                    Text(secondary, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onAccept,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x3352C41A))
                    .border(1.dp, Color(0xFF52C41A), CircleShape)
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Accept request", tint = Color(0xFF52C41A))
            }
            IconButton(
                onClick = onReject,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FF4D4F))
                    .border(1.dp, Color(0xFFFF4D4F), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Reject request", tint = Color(0xFFFF4D4F))
            }
        }
    }
}

private fun resolveRequestIcon(icon: String): ImageVector {
    return when (icon.lowercase()) {
        "fa-microphone", "microphone", "audio" -> Icons.Rounded.Mic
        "fa-video", "video" -> Icons.Rounded.VideoCameraFront
        "fa-desktop", "desktop", "screen" -> Icons.AutoMirrored.Rounded.ScreenShare
        "fa-comments", "comments", "chat" -> Icons.AutoMirrored.Rounded.Chat
        "fa-hand", "hand", "raised-hand" -> Icons.Rounded.HowToVote
        else -> Icons.Rounded.Settings
    }
}
