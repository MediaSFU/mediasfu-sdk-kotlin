package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.components.display_settings.DisplaySettingsModalOptions

/**
 * Display Settings Modal - Configure display preferences
 *
 * Features:
 * - Meeting display type (video/media/all)
 * - Auto wave detection
 * - Force full display
 * - Video optimization
 */
@Composable
fun DisplaySettingsModal(state: MediasfuGenericState) {
    val props = state.createDisplaySettingsModalProps()
    if (!props.isVisible) return

    DefaultDisplaySettingsModalContent(props)
}

/**
 * Public content body for DisplaySettingsModal - can be embedded in unified modal system
 * Provides full display settings functionality without AlertDialog wrapper
 */
@Composable
fun DisplaySettingsModalContentBody(
    props: DisplaySettingsModalOptions,
    modifier: Modifier = Modifier
) {
    var meetingDisplayType by remember(props.parameters.meetingDisplayType) { 
        mutableStateOf(props.parameters.meetingDisplayType) 
    }
    var autoWave by remember(props.parameters.autoWave) { mutableStateOf(props.parameters.autoWave) }
    var forceFullDisplay by remember(props.parameters.forceFullDisplay) { mutableStateOf(props.parameters.forceFullDisplay) }
    var meetingVideoOptimized by remember(props.parameters.meetingVideoOptimized) { mutableStateOf(props.parameters.meetingVideoOptimized) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Meeting Display Type Selection
        Column {
            Text(
                text = "Display Option",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Choose which participants to display",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            
            Column(
                Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    Triple("video", "Video Participants", "Show only participants with video on"),
                    Triple("media", "Media Only", "Show participants with audio or video"),
                    Triple("all", "All Participants", "Show all participants in the room")
                ).forEach { (value, label, description) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = meetingDisplayType == value,
                                onClick = { meetingDisplayType = value },
                                role = Role.RadioButton
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = meetingDisplayType == value,
                            onClick = null,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
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

        HorizontalDivider()

        // Auto Wave Setting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto Wave", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Automatically detect and highlight active speakers",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = autoWave,
                onCheckedChange = {
                    autoWave = it
                    props.parameters.updateAutoWave(it)
                }
            )
        }

        HorizontalDivider()

        // Force Full Display Setting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Force Full Display", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Always show all participants regardless of grid size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = forceFullDisplay,
                onCheckedChange = {
                    forceFullDisplay = it
                    props.parameters.updateForceFullDisplay(it)
                }
            )
        }

        HorizontalDivider()

        // Meeting Video Optimized Setting
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Optimize Video", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Force only video participants on screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = meetingVideoOptimized,
                onCheckedChange = {
                    meetingVideoOptimized = it
                    props.parameters.updateMeetingVideoOptimized(it)
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Apply Button
        Button(
            onClick = {
                props.parameters.updateMeetingDisplayType(meetingDisplayType)
                props.parameters.updateAutoWave(autoWave)
                props.parameters.updateForceFullDisplay(forceFullDisplay)
                props.parameters.updateMeetingVideoOptimized(meetingVideoOptimized)
                
                props.onModifySettings(
                    com.mediasfu.sdk.ui.components.display_settings.ModifyDisplaySettingsOptions(
                        parameters = props.parameters
                    )
                )
                
                // Close modal after applying settings
                props.onClose()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun DefaultDisplaySettingsModalContent(
    props: DisplaySettingsModalOptions
) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = { Text("Display Settings") },
        text = {
            DisplaySettingsModalContentBody(
                props = props,
                modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = props.onClose) {
                Text("Close")
            }
        }
    )
}
