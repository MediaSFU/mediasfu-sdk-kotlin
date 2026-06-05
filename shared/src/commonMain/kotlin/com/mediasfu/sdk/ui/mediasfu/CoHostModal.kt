package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.components.cohost.CoHostModalOptions
import com.mediasfu.sdk.ui.components.cohost.CoHostResponsibility
import com.mediasfu.sdk.ui.components.cohost.ModifyCoHostSettingsOptions

/**
 * Co-Host Modal - Manage co-host selection and responsibilities
 *
 * Features:
 * - Select co-host from participants
 * - Assign specific responsibilities
 * - Real-time permission management
 */

/**
 * Formats responsibility label from camelCase to spaced words
 * Example: 'manageParticipants' -> 'Manage Participants'
 */
private fun formatResponsibilityLabel(name: String): String {
    // Split on capital letters
    val regex = Regex("(?=[A-Z])")
    val words = name.split(regex).filter { it.isNotEmpty() }
    
    // Join with spaces and capitalize first letter
    return words.joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoHostModal(state: MediasfuGenericState) {
    val props = state.createCoHostModalProps()
    if (!props.isCoHostModalVisible) return

    val overrideContent = state.options.uiOverrides.coHostModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultCoHostModalContent(it) }
    )

    contentBuilder(props)
}

/**
 * Public content body for CoHostModal - can be embedded in unified modal system
 * Provides full co-host management functionality without AlertDialog wrapper
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoHostModalContentBody(
    props: CoHostModalOptions,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null
) {
    var selectedCohost by remember { mutableStateOf(props.currentCohost) }
    var responsibilities by remember { 
        mutableStateOf(props.coHostResponsibility.toMutableList())
    }
    
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Current Co-Host",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (selectedCohost == "No coHost") "None" else selectedCohost,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (selectedCohost == "No coHost") {
                        "Pick a moderator to delegate specific meeting controls."
                    } else {
                        "Responsibilities below control which parts of the session this co-host can manage."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Co-Host",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Choose one participant to assist with moderation and room management.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

            var expandedCoHost by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = expandedCoHost,
                    onExpandedChange = { expandedCoHost = !expandedCoHost }
                ) {
                    OutlinedTextField(
                        value = if (selectedCohost == "No coHost") "No Co-Host" else selectedCohost,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Co-Host") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCoHost)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = expandedCoHost,
                        onDismissRequest = { expandedCoHost = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("No Co-Host") },
                            onClick = {
                                selectedCohost = "No coHost"
                                expandedCoHost = false
                            }
                        )

                        props.participants.filter { it.islevel != "2" }.forEach { participant ->
                            DropdownMenuItem(
                                text = { Text(participant.name) },
                                onClick = {
                                    selectedCohost = participant.name
                                    expandedCoHost = false
                                }
                            )
                        }
                    }
                }
            }
        }

        if (selectedCohost != "No coHost") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Manage Co-Host Responsibilities",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Enable each area the co-host can manage. Dedicated locks that area to the co-host once assigned.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            modifier = Modifier.weight(0.6f),
                            text = "Responsibility",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            modifier = Modifier.weight(0.3f),
                            text = "Enabled",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            modifier = Modifier.weight(0.3f),
                            text = "Dedicated",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    responsibilities.forEachIndexed { index, responsibility ->
                        val isChecked = responsibility.value
                        val isDedicated = responsibility.dedicated

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatResponsibilityLabel(responsibility.name),
                                    modifier = Modifier.weight(0.6f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Box(
                                    modifier = Modifier.weight(0.3f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Switch(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val updatedResponsibilities = responsibilities.toMutableList()
                                            updatedResponsibilities[index] = CoHostResponsibility(
                                                name = responsibility.name,
                                                value = checked,
                                                dedicated = if (checked) responsibility.dedicated else false
                                            )

                                            responsibilities = updatedResponsibilities
                                            props.updateCoHostResponsibility(updatedResponsibilities)
                                        }
                                    )
                                }

                                Box(
                                    modifier = Modifier.weight(0.3f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Switch(
                                        checked = isDedicated,
                                        enabled = isChecked,
                                        onCheckedChange = { dedicated ->
                                            if (isChecked) {
                                                val updatedResponsibilities = responsibilities.toMutableList()
                                                updatedResponsibilities[index] = CoHostResponsibility(
                                                    name = responsibility.name,
                                                    value = responsibility.value,
                                                    dedicated = dedicated
                                                )
                                                responsibilities = updatedResponsibilities
                                                props.updateCoHostResponsibility(updatedResponsibilities)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = {
                props.onModifyCoHostSettings(
                    ModifyCoHostSettingsOptions(
                        roomName = props.roomName,
                        socket = props.socket,
                        showAlert = props.showAlert,
                        selectedParticipant = selectedCohost,
                        coHost = props.currentCohost,
                        coHostResponsibility = responsibilities,
                        updateCoHost = props.updateCoHost,
                        updateCoHostResponsibility = props.updateCoHostResponsibility,
                        updateIsCoHostModalVisible = props.updateIsCoHostModalVisible
                    )
                )
                onSave?.invoke()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Save Changes")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultCoHostModalContent(
    props: CoHostModalOptions
) {
    AlertDialog(
        onDismissRequest = props.onCoHostClose,
        title = { 
            Column {
                Text("Manage Co-Host")
                Text(
                    text = "Select a co-host and assign responsibilities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            CoHostModalContentBody(
                props = props,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = props.onCoHostClose) {
                Text("Cancel")
            }
        }
    )
}
