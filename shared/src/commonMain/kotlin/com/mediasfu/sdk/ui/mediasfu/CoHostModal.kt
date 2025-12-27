package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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

    DefaultCoHostModalContent(props)
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
        // Current Co-Host Status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Current Co-Host",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedCohost == "No coHost") "None" else selectedCohost,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Co-host Selection
        Column {
            Text(
                text = "Select Co-Host",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            
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
                    colors = OutlinedTextFieldDefaults.colors()
                )
                
                ExposedDropdownMenu(
                    expanded = expandedCoHost,
                    onDismissRequest = { expandedCoHost = false }
                ) {
                    // No co-host option
                    DropdownMenuItem(
                        text = { Text("No Co-Host") },
                        onClick = {
                            selectedCohost = "No coHost"
                            expandedCoHost = false
                        }
                    )
                    
                    // Participant options (excluding host - islevel '2')
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

        // Co-host Responsibilities (only show if a co-host is selected)
        if (selectedCohost != "No coHost") {
            HorizontalDivider()

            Column {
                Text(
                    text = "Manage Co-Host Responsibilities",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Table Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Responsibility",
                        modifier = Modifier.weight(0.6f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select",
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dedicated",
                        modifier = Modifier.weight(0.3f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                // Responsibility rows
                responsibilities.forEachIndexed { index, responsibility ->
                    val isChecked = responsibility.value
                    val isDedicated = responsibility.dedicated
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Responsibility Label
                        Text(
                            text = formatResponsibilityLabel(responsibility.name),
                            modifier = Modifier.weight(0.6f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        // Manage Switch
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
                        
                        // Dedicated Switch
                        Box(
                            modifier = Modifier.weight(0.3f),
                            contentAlignment = Alignment.Center
                        ) {
                            Switch(
                                checked = isDedicated,
                                enabled = isChecked, // Only enabled when manage is on
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
                    
                    if (index < responsibilities.size - 1) {
                        HorizontalDivider()
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
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
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
