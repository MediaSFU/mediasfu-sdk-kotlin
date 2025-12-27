package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.EventType

/**
 * ShareEventModal - Modal for sharing event details, meeting ID, and invite link
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */
@Composable
internal fun ShareEventModal(state: MediasfuGenericState) {
    val props = state.createShareEventModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.shareEventModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultShareEventModalContent(it) }
    )

    contentBuilder(props)
}

/**
 * Public content body for ShareEventModal - can be embedded in unified modal system
 * Provides full share functionality without AlertDialog wrapper
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareEventModalContentBody(
    props: ShareEventModalProps,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val displayRoomName = props.roomName.ifBlank { "Unavailable" }
    val shareSummaryText = buildString {
        append("Join our event")
        if (props.roomName.isNotBlank()) {
            append(" \"")
            append(props.roomName)
            append("\"")
        }
        append(" on MediaSFU.")
        if (props.shareLink.isNotBlank()) {
            append(" Use this link: ")
            append(props.shareLink)
            append('.')
        } else if (displayRoomName.isNotBlank() && displayRoomName != "Unavailable") {
            append(" Use the meeting ID ")
            append(displayRoomName)
            append('.')
        }
    }
    val encodedShareSummary = shareSummaryText.urlEncoded()
    val encodedShareLink = props.shareLink.urlEncoded()
    val emailSubject = "Join my MediaSFU event".urlEncoded()
    val launchShare: (String, String) -> Unit = { url, warning ->
        runCatching { uriHandler.openUri(url) }
            .onFailure { props.state.showAlert(warning, "warning") }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CopyableValueRow(
            label = "Meeting ID",
            value = displayRoomName,
            copyEnabled = props.roomName.isNotBlank(),
            onCopy = {
                clipboard.setText(AnnotatedString(props.roomName))
                props.state.showAlert("Meeting ID copied to clipboard.", "success")
            }
        )

        if (props.shareLink.isNotBlank()) {
            CopyableValueRow(
                label = "Invite Link",
                value = props.shareLink,
                copyEnabled = true,
                onCopy = {
                    clipboard.setText(AnnotatedString(props.shareLink))
                    props.state.showAlert("Invite link copied to clipboard.", "success")
                }
            )

            if (props.shareButtonsEnabled) {
                val quickShareTargets = listOf(
                    ShareTarget(
                        label = "Copy Link",
                        icon = Icons.Rounded.ContentCopy,
                        fallbackText = null,
                        backgroundColor = MaterialTheme.colorScheme.primary
                    ) {
                        clipboard.setText(AnnotatedString(props.shareLink))
                        props.state.showAlert("Invite link copied to clipboard.", "success")
                    },
                    ShareTarget(
                        label = "Email",
                        icon = Icons.Rounded.Email,
                        fallbackText = null,
                        backgroundColor = Color(0xFF1976D2)
                    ) {
                        val mailUrl = "mailto:?subject=$emailSubject&body=$encodedShareSummary"
                        launchShare(mailUrl, "Email apps are not available on this device.")
                    },
                    ShareTarget(
                        label = "Facebook",
                        icon = null,
                        fallbackText = "f",
                        backgroundColor = Color(0xFF1877F2)
                    ) {
                        val facebookUrl = "https://www.facebook.com/sharer/sharer.php?u=$encodedShareLink"
                        launchShare(facebookUrl, "Unable to open Facebook right now.")
                    },
                    ShareTarget(
                        label = "WhatsApp",
                        icon = Icons.AutoMirrored.Rounded.Message,
                        fallbackText = null,
                        backgroundColor = Color(0xFF25D366)
                    ) {
                        val waUrl = "https://wa.me/?text=$encodedShareSummary"
                        launchShare(waUrl, "WhatsApp is not available on this device.")
                    },
                    ShareTarget(
                        label = "Telegram",
                        icon = Icons.AutoMirrored.Rounded.Send,
                        fallbackText = null,
                        backgroundColor = Color(0xFF229ED9)
                    ) {
                        val tgUrl = "https://t.me/share/url?url=$encodedShareLink&text=$encodedShareSummary"
                        launchShare(tgUrl, "Telegram is not available on this device.")
                    }
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick share",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickShareTargets.forEach { target ->
                            ShareTargetButton(target)
                        }
                    }
                    Text(
                        text = "These shortcuts open your browser or installed apps for sharing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Text(
                text = "An invite link is not available yet. Share the meeting ID instead.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (props.adminPasscode != null) {
            CopyableValueRow(
                label = "Admin Passcode",
                value = props.adminPasscode,
                copyEnabled = true,
                onCopy = {
                    clipboard.setText(AnnotatedString(props.adminPasscode))
                    props.state.showAlert("Admin passcode copied to clipboard.", "success")
                }
            )
            Text(
                text = "Only hosts can view the admin passcode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val shareMessage = if (props.shareLink.isNotBlank()) {
            "Share this link or meeting ID with attendees to invite them."
        } else {
            "Share the meeting ID with attendees to invite them."
        }
        Text(
            text = shareMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "Event type: ${formatEventTypeLabel(props.eventType)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefaultShareEventModalContent(props: ShareEventModalProps) {
    AlertDialog(
        onDismissRequest = props.onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Share Event",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close share event")
                }
            }
        },
        text = {
            ShareEventModalContentBody(
                props = props,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = props.onDismiss) { Text("Close") }
        }
    )
}

private data class ShareTarget(
    val label: String,
    val icon: ImageVector?,
    val fallbackText: String?,
    val backgroundColor: Color,
    val action: () -> Unit
)

@Composable
private fun ShareTargetButton(target: ShareTarget) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable(onClick = target.action),
            color = target.backgroundColor,
            contentColor = Color.White,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (target.icon != null) {
                    Icon(
                        imageVector = target.icon,
                        contentDescription = target.label,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (!target.fallbackText.isNullOrBlank()) {
                    Text(
                        text = target.fallbackText,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Text(
            text = target.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CopyableValueRow(
    label: String,
    value: String,
    copyEnabled: Boolean,
    onCopy: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (value.isNotBlank()) value else "Unavailable",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopy, enabled = copyEnabled) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy $label")
            }
        }
    }
}

private fun String.urlEncoded(): String {
    if (isEmpty()) return this
    val builder = StringBuilder(length)
    for (byte in encodeToByteArray()) {
        val value = byte.toInt() and 0xFF
        val char = value.toChar()
        if (char.isLetterOrDigit() || char in "-_.~") {
            builder.append(char)
        } else {
            val hex = value.toString(16).uppercase()
            builder.append('%')
            if (hex.length == 1) builder.append('0')
            builder.append(hex)
        }
    }
    return builder.toString()
}

private fun formatEventTypeLabel(eventType: EventType): String {
    val label = eventType.name.lowercase()
    return label.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}
