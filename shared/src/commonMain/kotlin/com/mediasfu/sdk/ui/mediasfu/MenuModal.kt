package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp

/**
 * MenuModal - Displays the main menu with room info and action buttons
 * 
 * This modal provides access to various settings and features including:
 * - Room information and sharing
 * - Event settings (host only)
 * - Recording controls (host only)
 * - Co-host management (host only)
 * - Media/Display settings (host/co-host)
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
    val scrollState = rememberScrollState()
    val roomName = props.roomName
    val roomLink = props.roomLink
    val adminPasscode = props.adminPasscode
    val totalRequests = props.totalRequests
    val waitingCount = props.waitingCount
    val isHost = props.islevel == "2"
    val isCoHost = props.coHost == props.member

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 450.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Event Settings (Host only)
        if (isHost) {
            MenuActionButton(
                label = "Event Settings",
                description = "Configure participant permissions for this event",
                icon = Icons.Rounded.Settings,
                onClick = props.onOpenSettings
            )
        }

        // Recording (Host only)
        // When showRecordButtons=false: show single "Recording" button to open modal
        // When showRecordButtons=true: show expanded recording controls (pause/stop/timer/status/settings)
        if (isHost) {
            val recordingState = props.state.recording
            val showRecordButtons = recordingState.showRecordButtons
            
            if (!showRecordButtons) {
                // Single Recording button - opens the recording modal
                MenuActionButton(
                    label = "Recording",
                    description = "Start or manage session recording",
                    icon = Icons.Rounded.FiberManualRecord,
                    onClick = props.onOpenRecording
                )
            } else {
                // Expanded recording controls - shown when recording is active
                RecordingControlsRow(
                    state = props.state,
                    onOpenRecording = props.onOpenRecording,
                    onCloseMenu = props.onClose
                )
            }
        }

        // Co-Host (Host only)
        if (isHost) {
            MenuActionButton(
                label = "Co-Host",
                description = "Manage co-host and their permissions",
                icon = Icons.Rounded.SupervisorAccount,
                onClick = props.onOpenCoHost
            )
        }

        // Breakout Rooms (Host only)
        if (isHost) {
            MenuActionButton(
                label = "Breakout Rooms",
                description = "Create and manage breakout room sessions",
                icon = Icons.Rounded.Group,
                onClick = props.onOpenBreakoutRooms
            )
        }

        // Set Media (always available to everyone)
        MenuActionButton(
            label = "Set Media",
            description = "Configure audio, video, and sharing settings",
            icon = Icons.Rounded.Videocam,
            onClick = props.onOpenMediaSettings
        )

        // Display (always available to everyone)
        MenuActionButton(
            label = "Display",
            description = "Customize layout and display options",
            icon = Icons.Rounded.DisplaySettings,
            onClick = props.onOpenDisplaySettings
        )

        // Check co-host responsibilities
        val mediaValue = props.coHostResponsibility.find { it.name == "media" }?.value == true
        val participantsValue = props.coHostResponsibility.find { it.name == "participants" }?.value == true

        // Manage Requests (Host or Co-Host with participants permission)
        val canManageRequests = isHost || (isCoHost && participantsValue)
        if (canManageRequests) {
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

        // Waiting Room (Host or Co-Host with participants permission)
        val canManageWaiting = isHost || (isCoHost && participantsValue)
        if (canManageWaiting) {
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

        // Share Event
        MenuActionButton(
            label = "Share Event",
            description = "Share event details with others",
            icon = Icons.Rounded.Share,
            onClick = props.onOpenShareEvent
        )

        // Polls
        MenuActionButton(
            label = "Polls",
            description = "View and participate in polls",
            icon = Icons.Rounded.HowToVote,
            onClick = props.onOpenPolls
        )

        // Whiteboard (Host only - matching React/Flutter)
        if (props.canConfigureWhiteboard) {
            MenuActionButton(
                label = "Whiteboard",
                description = "Configure and launch the whiteboard",
                icon = Icons.Rounded.Create,
                onClick = props.onOpenConfigureWhiteboard
            )
        }

        // Meeting Passcode (Host only)
        if (isHost && adminPasscode.isNotBlank()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Event Passcode (Host):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedTextField(
                    value = adminPasscode,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    enabled = false
                )
            }
        }

        // Meeting ID (always shown)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = "Event ID:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedTextField(
                value = roomName.ifBlank { "Unavailable" },
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledTextColor = MaterialTheme.colorScheme.onSurface
                ),
                enabled = false
            )
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Label row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.FiberManualRecord,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Recording Controls", style = MaterialTheme.typography.titleMedium)
            }
            
            // Control buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play/Pause Button
                IconButton(
                    onClick = { 
                        state.launchInScope { 
                            state.handleUpdateRecording()
                            onCloseMenu()
                        } 
                    }
                ) {
                    Icon(
                        imageVector = if (isPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                        contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                        tint = Color(0xFF52C41A), // Green
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Stop Button
                IconButton(
                    onClick = { 
                        state.launchInScope { 
                            state.handleStopRecording()
                            onCloseMenu()
                        } 
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.StopCircle,
                        contentDescription = "Stop Recording",
                        tint = Color(0xFFFF4D4F), // Red
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Timer Display
                Text(
                    text = progressTime,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Status Indicator
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            when (recordState) {
                                "yellow" -> Color(0xFFFAAD14) // Paused - yellow
                                "red" -> Color(0xFFFF4D4F) // Recording - red
                                else -> Color(0xFF52C41A) // Stopped - green
                            }
                        )
                )
                
                // Settings Button
                IconButton(
                    onClick = {
                        if (isPaused) {
                            onOpenRecording()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Recording Settings",
                        tint = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
