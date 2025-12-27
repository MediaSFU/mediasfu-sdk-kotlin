package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.ui.components.media_settings.MediaSettingsModalOptions

/**
 * Media Settings Modal - Configure camera and microphone
 *
 * Features:
 * - Camera selection (simple dropdown)
 * - Microphone selection (simple dropdown)
 * - Switch camera button (mobile camera flip)
 */
@Composable
fun MediaSettingsModal(state: MediasfuGenericState) {
    val props = state.createMediaSettingsModalProps()
    if (!props.isVisible) return

    DefaultMediaSettingsModalContent(props)
}

/**
 * Public content body for MediaSettingsModal - can be embedded in unified modal system
 * Provides full camera/microphone/speaker selection functionality without AlertDialog wrapper
 */
@Composable
fun MediaSettingsModalContentBody(
    props: MediaSettingsModalOptions,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val parameters = props.parameters.getUpdatedAllParams()
    val videoInputs = parameters.videoInputs
    val audioInputs = parameters.audioInputs
    val audioOutputs = parameters.audioOutputs
    
    var selectedCamera by remember { 
        mutableStateOf(
            parameters.userDefaultVideoInputDevice.ifEmpty { 
                videoInputs.firstOrNull()?.deviceId ?: ""
            }
        )
    }
    var selectedMicrophone by remember { 
        mutableStateOf(
            parameters.userDefaultAudioInputDevice.ifEmpty { 
                audioInputs.firstOrNull()?.deviceId ?: ""
            }
        )
    }
    var selectedSpeaker by remember { 
        mutableStateOf(
            parameters.userDefaultAudioOutputDevice.ifEmpty { 
                audioOutputs.firstOrNull()?.deviceId ?: ""
            }
        )
    }

    Column(
        modifier = modifier.verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Camera Selection
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Select Camera",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            var expandedCamera by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (videoInputs.isNotEmpty()) expandedCamera = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            videoInputs.find { it.deviceId == selectedCamera }?.label 
                                ?: videoInputs.firstOrNull()?.label 
                                ?: "No Camera",
                            maxLines = 1
                        )
                        Text("▼", fontSize = 12.sp)
                    }
                }
                
                DropdownMenu(
                    expanded = expandedCamera,
                    onDismissRequest = { expandedCamera = false }
                ) {
                    videoInputs.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.label) },
                            onClick = {
                                selectedCamera = device.deviceId
                                props.switchVideoOnPress(
                                    com.mediasfu.sdk.ui.components.media_settings.SwitchVideoOptions(
                                        videoPreference = device.deviceId,
                                        parameters = props.parameters
                                    )
                                )
                                expandedCamera = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Microphone Selection
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Select Microphone",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            var expandedMic by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (audioInputs.isNotEmpty()) expandedMic = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            audioInputs.find { it.deviceId == selectedMicrophone }?.label 
                                ?: audioInputs.firstOrNull()?.label 
                                ?: "No Microphone",
                            maxLines = 1
                        )
                        Text("▼", fontSize = 12.sp)
                    }
                }
                
                DropdownMenu(
                    expanded = expandedMic,
                    onDismissRequest = { expandedMic = false }
                ) {
                    audioInputs.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.label) },
                            onClick = {
                                selectedMicrophone = device.deviceId
                                props.switchAudioOnPress(
                                    com.mediasfu.sdk.ui.components.media_settings.SwitchAudioOptions(
                                        audioPreference = device.deviceId,
                                        parameters = props.parameters
                                    )
                                )
                                expandedMic = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // Speaker Selection
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Select Speaker",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            
            var expandedSpeaker by remember { mutableStateOf(false) }
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { if (audioOutputs.isNotEmpty()) expandedSpeaker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            audioOutputs.find { it.deviceId == selectedSpeaker }?.label 
                                ?: audioOutputs.firstOrNull()?.label 
                                ?: "No Speaker",
                            maxLines = 1
                        )
                        Text("▼", fontSize = 12.sp)
                    }
                }
                
                DropdownMenu(
                    expanded = expandedSpeaker,
                    onDismissRequest = { expandedSpeaker = false }
                ) {
                    audioOutputs.forEach { device ->
                        DropdownMenuItem(
                            text = { Text(device.label) },
                            onClick = {
                                selectedSpeaker = device.deviceId
                                props.switchAudioOutputOnPress(
                                    com.mediasfu.sdk.ui.components.media_settings.SwitchAudioOutputOptions(
                                        audioOutputPreference = device.deviceId,
                                        parameters = props.parameters
                                    )
                                )
                                expandedSpeaker = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Switch Camera Button
        ElevatedButton(
            onClick = {
                props.switchCameraOnPress(
                    com.mediasfu.sdk.ui.components.media_settings.SwitchVideoAltOptions(
                        parameters = props.parameters
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Switch Camera")
        }

        // Virtual Background Button
        ElevatedButton(
            onClick = { props.onVirtualBackgroundPress() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Virtual Background")
        }
    }
}

@Composable
private fun DefaultMediaSettingsModalContent(
    props: com.mediasfu.sdk.ui.components.media_settings.MediaSettingsModalOptions
) {
    AlertDialog(
        onDismissRequest = props.onClose,
        title = { 
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Media Settings",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = props.onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Black
                    )
                }
            }
        },
        text = {
            MediaSettingsModalContentBody(
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
