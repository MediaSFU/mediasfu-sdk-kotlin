package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    val overrideContent = state.options.uiOverrides.displaySettingsModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultDisplaySettingsModalContent(it) }
    )

    contentBuilder(props)
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                        Triple("video", "Video Only", "Show only participants with video on"),
                        Triple("media", "Media", "Show participants with audio or video"),
                        Triple("all", "All Participants", "Show all participants in the room")
                    ).forEach { (value, label, description) ->
                        val selected = meetingDisplayType == value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.36f) else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .selectable(
                                    selected = selected,
                                    onClick = { meetingDisplayType = value },
                                    role = Role.RadioButton
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
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
        }

        HorizontalDivider()

        DisplayToggleRow(
            title = "Auto Wave",
            description = "Automatically detect and highlight active speakers",
            checked = autoWave,
            onCheckedChange = {
                autoWave = it
                props.parameters.updateAutoWave(it)
            }
        )

        HorizontalDivider()

        DisplayToggleRow(
            title = "Force Full Display",
            description = "Always show all participants regardless of grid size",
            checked = forceFullDisplay,
            onCheckedChange = {
                forceFullDisplay = it
                props.parameters.updateForceFullDisplay(it)
            }
        )

        HorizontalDivider()

        DisplayToggleRow(
            title = "Optimize Video",
            description = "Force only video participants on screen",
            checked = meetingVideoOptimized,
            onCheckedChange = {
                meetingVideoOptimized = it
                props.parameters.updateMeetingVideoOptimized(it)
            }
        )

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
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Apply")
        }
    }
}

@Composable
private fun DisplayToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
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
