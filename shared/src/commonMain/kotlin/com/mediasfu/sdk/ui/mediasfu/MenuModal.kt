package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * MenuModal - Displays the main menu with room info and action buttons
 * 
 * This modal provides access to various settings and features including:
 * - Room information and sharing
 * - Event settings (host only)
 * - Recording controls (host only)
 * - Co-host management (host only)
 * - Request management
 * - Waiting room
 * - Poll access
 * 
 * @param state The MediasfuGenericState containing all necessary state and callbacks
 */
@Composable
fun MenuModal(state: MediasfuGenericState) {
    val props = state.createMenuModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.menuModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultMenuModalContent(it) }
    )

    contentBuilder(props)
}

@Composable
private fun DefaultMenuModalContent(props: MenuModalProps) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = { Text("Menu") },
        text = {
            MenuModalContentBody(props = props)
        },
        confirmButton = {
            TextButton(onClick = props.onClose) {
                Text("Close")
            }
        }
    )
}

/**
 * Public content body for MenuModal - can be used in unified modal system
 * Contains full menu functionality: event settings, recording, media, display, etc.
 */
@Composable
fun MenuModalContentBody(
    props: MenuModalProps,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val roomName = props.roomName
    val roomLink = props.roomLink
    val adminPasscode = props.adminPasscode
    val totalRequests = props.totalRequests
    val waitingCount = props.waitingCount
    val isHost = props.islevel == "2"
    val isCoHost = props.coHost == props.member
    val participantsValue = props.coHostResponsibility.find { it.name == "participants" }?.value == true
    val canManageRequests = isHost || (isCoHost && participantsValue)
    val canManageWaiting = isHost || (isCoHost && participantsValue)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (props.onToggleTheme != null) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Theme",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filledColors = ButtonDefaults.buttonColors()
                            val outlinedColors = ButtonDefaults.outlinedButtonColors()
                            // Dark button
                            if (props.isDarkMode) {
                                Button(
                                    onClick = { props.onToggleTheme.invoke(true) },
                                    modifier = Modifier.weight(1f),
                                    colors = filledColors
                                ) { Text("Dark") }
                            } else {
                                OutlinedButton(
                                    onClick = { props.onToggleTheme.invoke(true) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Dark") }
                            }
                            // Light button
                            if (!props.isDarkMode) {
                                Button(
                                    onClick = { props.onToggleTheme.invoke(false) },
                                    modifier = Modifier.weight(1f),
                                    colors = filledColors
                                ) { Text("Light") }
                            } else {
                                OutlinedButton(
                                    onClick = { props.onToggleTheme.invoke(false) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Light") }
                            }
                        }
                    }
                }
            }
        }

        if (isHost) {
            item {
                MenuActionButton(
                    label = "Event Settings",
                    description = "Configure participant permissions for this event",
                    icon = Icons.Rounded.Settings,
                    onClick = props.onOpenSettings
                )
            }
        }

        if (isHost) {
            val recordingState = props.state.recording
            val showRecordButtons = recordingState.showRecordButtons

            if (!showRecordButtons) {
                item {
                    MenuActionButton(
                        label = "Recording",
                        description = "Start or manage session recording",
                        icon = Icons.Rounded.FiberManualRecord,
                        onClick = props.onOpenRecording
                    )
                }
            } else {
                item {
                    RecordingControlsRow(
                        state = props.state,
                        onOpenRecording = props.onOpenRecording,
                        onCloseMenu = props.onClose
                    )
                }
            }
        }

        if (isHost) {
            item {
                MenuActionButton(
                    label = "Co-Host",
                    description = "Manage co-host and their permissions",
                    icon = Icons.Rounded.SupervisorAccount,
                    onClick = props.onOpenCoHost
                )
            }
        }

        if (isHost) {
            item {
                MenuActionButton(
                    label = "Breakout Rooms",
                    description = "Create and manage breakout room sessions",
                    icon = Icons.Rounded.Group,
                    onClick = props.onOpenBreakoutRooms
                )
            }
        }

        item {
            MenuActionButton(
                label = "Set Media",
                description = "Configure audio, video, and sharing settings",
                icon = Icons.Rounded.Videocam,
                onClick = props.onOpenMediaSettings
            )
        }

        item {
            MenuActionButton(
                label = "Display",
                description = "Customize layout and display options",
                icon = Icons.Rounded.DisplaySettings,
                onClick = props.onOpenDisplaySettings
            )
        }

        if (canManageRequests) {
            item {
                MenuActionButton(
                    label = buildString {
                        append("Manage Requests")
                        if (totalRequests > 0) {
                            append(" (")
                            append(totalRequests)
                            append(")")
                        }
                    },
                    description = "Review and respond to participant requests",
                    icon = Icons.Rounded.Menu,
                    onClick = props.onOpenRequests
                )
            }
        }

        if (canManageWaiting) {
            item {
                MenuActionButton(
                    label = buildString {
                        append("Waiting Room")
                        if (waitingCount > 0) {
                            append(" (")
                            append(waitingCount)
                            append(")")
                        }
                    },
                    description = "Manage participants in the waiting area",
                    icon = Icons.Rounded.Group,
                    onClick = props.onOpenWaiting
                )
            }
        }

        item {
            MenuActionButton(
                label = "Share Event",
                description = "Share event details with others",
                icon = Icons.Rounded.Share,
                onClick = props.onOpenShareEvent
            )
        }

        item {
            MenuActionButton(
                label = "Polls",
                description = "View and participate in polls",
                icon = Icons.Rounded.HowToVote,
                onClick = props.onOpenPolls
            )
        }

        if (props.canConfigureWhiteboard) {
            item {
                MenuActionButton(
                    label = "Whiteboard",
                    description = "Configure and launch the whiteboard",
                    icon = Icons.Rounded.Create,
                    onClick = props.onOpenConfigureWhiteboard
                )
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Event Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    MenuInfoField(
                        label = "Meeting ID",
                        value = roomName.ifBlank { "Unavailable" },
                        onCopy = {
                            if (roomName.isNotBlank()) {
                                clipboard.setText(AnnotatedString(roomName))
                            }
                        }
                    )

                    if (isHost && adminPasscode.isNotBlank()) {
                        MenuInfoField(
                            label = "Admin Passcode",
                            value = adminPasscode,
                            isPassword = true,
                            onCopy = { clipboard.setText(AnnotatedString(adminPasscode)) }
                        )
                    }

                    if (roomLink.isNotBlank()) {
                        MenuInfoField(
                            label = "Event Link",
                            value = roomLink,
                            onCopy = { clipboard.setText(AnnotatedString(roomLink)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuInfoField(
    label: String,
    value: String,
    isPassword: Boolean = false,
    onCopy: () -> Unit
) {
    var isPasswordVisible by remember { mutableStateOf(false) }
    
    val displayValue = if (isPassword && !isPasswordVisible) {
        "••••••••"
    } else {
        value
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                
                if (isPassword) {
                    IconButton(
                        onClick = { isPasswordVisible = !isPasswordVisible },
                        modifier = Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide Passcode" else "Show Passcode",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier
                        .size(34.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy $label",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuActionButton(
    label: String,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * RecordingControlsRow - Expanded recording controls shown when recording is active
 * Matches Flutter's recordButtons: pause/resume, stop, timer, status indicator, settings
 */
@Composable
private fun RecordingControlsRow(
    state: MediasfuGenericState,
    onOpenRecording: () -> Unit,
    onCloseMenu: () -> Unit
) {
    val recordingState = state.recording
    val isPaused = recordingState.recordPaused
    val progressTime = recordingState.recordingProgressTime
    val recordState = recordingState.recordState // "green", "yellow", "red"
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Recording Controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Pause, stop, or adjust the active session",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF34D399).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFF34D399).copy(alpha = 0.35f))
                    ) {
                        IconButton(
                            onClick = {
                                state.launchInScope {
                                    state.handleUpdateRecording()
                                    onCloseMenu()
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                                contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFEF4444).copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f))
                    ) {
                        IconButton(
                            onClick = {
                                state.launchInScope {
                                    state.handleStopRecording()
                                    onCloseMenu()
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = "Stop Recording",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(
                            text = progressTime,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                when (recordState) {
                                    "yellow" -> Color(0xFFF59E0B)
                                    "red" -> Color(0xFFEF4444)
                                    else -> Color(0xFF34D399)
                                }
                            )
                    ) {}

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        IconButton(
                            onClick = {
                                if (isPaused) {
                                    onOpenRecording()
                                }
                            },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Recording Settings",
                                tint = if (isPaused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
