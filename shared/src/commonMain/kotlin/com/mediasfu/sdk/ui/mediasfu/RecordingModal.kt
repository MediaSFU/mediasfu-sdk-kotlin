package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.*

@Composable
fun RecordingModal(state: MediasfuGenericState) {
    val props = state.createRecordingModalProps()
    if (!props.isRecordingModalVisible) return

    // TODO: Temporarily removed uiOverrides check - property doesn't exist
    // val overrideContent = state.options.uiOverrides.recordingModal
    // if (overrideContent != null) {
    //     overrideContent(props)
    // } else {
        DefaultRecordingModalContent(props)
    // }
}

@Composable
private fun DefaultRecordingModalContent(props: com.mediasfu.sdk.ui.components.recording.RecordingModalOptions) {
    val parameters = props.parameters
    val recordPaused = parameters.recordPaused
    
    AlertDialog(
        onDismissRequest = props.onClose,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recording Settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            RecordingModalContentBody(props = props)
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        props.confirmRecording(
                            com.mediasfu.sdk.ui.components.recording.ConfirmRecordingOptions(
                                parameters = parameters
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Confirm")
                }
                
                if (!recordPaused) {
                    Button(
                        onClick = {
                            props.startRecording(
                                com.mediasfu.sdk.ui.components.recording.StartRecordingOptions(
                                    parameters = parameters
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    )
}

/**
 * Public content body for RecordingModal - can be used in unified modal system
 * Contains full recording configuration: standard settings, advanced settings
 */
@Composable
fun RecordingModalContentBody(
    props: com.mediasfu.sdk.ui.components.recording.RecordingModalOptions,
    modifier: Modifier = Modifier,
    showActionButtons: Boolean = false
) {
    val scrollState = rememberScrollState()
    val parameters = props.parameters
    val eventType = parameters.eventType.name.lowercase()
    val recordPaused = parameters.recordPaused

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 400.dp)
            .heightIn(max = 600.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Standard Panel
        StandardPanelSection(parameters, eventType)
        
        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
        
        // Advanced Panel
        AdvancedPanelSection(parameters, eventType)
        
        // Optional action buttons for unified modal
        if (showActionButtons) {
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        props.confirmRecording(
                            com.mediasfu.sdk.ui.components.recording.ConfirmRecordingOptions(
                                parameters = parameters
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Text("Confirm")
                }
                
                if (!recordPaused) {
                    Button(
                        onClick = {
                            props.startRecording(
                                com.mediasfu.sdk.ui.components.recording.StartRecordingOptions(
                                    parameters = parameters
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF44336)
                        )
                    ) {
                        Text("Start")
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardPanelSection(
    parameters: com.mediasfu.sdk.ui.components.recording.RecordingModalParameters,
    eventType: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Standard Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        // Media Options
        DropdownOption(
            label = "Media Options:",
            value = parameters.recordingMediaOptions,
            options = listOf("video" to "Record Video", "audio" to "Record Audio Only"),
            onValueChange = { parameters.updateRecordingMediaOptions(it) }
        )
        
        // Specific Audios (not for broadcast)
        if (eventType != "broadcast") {
            DropdownOption(
                label = "Specific Audios:",
                value = parameters.recordingAudioOptions,
                options = listOf(
                    "all" to "Add All",
                    "onScreen" to "Add All On Screen",
                    "host" to "Add Host Only"
                ),
                onValueChange = { parameters.updateRecordingAudioOptions(it) }
            )
            
            // Specific Videos
            DropdownOption(
                label = "Specific Videos:",
                value = parameters.recordingVideoOptions,
                options = listOf(
                    "all" to "Add All",
                    "mainScreen" to "Big Screen Only (includes screenshare)"
                ),
                onValueChange = { parameters.updateRecordingVideoOptions(it) }
            )
        }
        
        // Add HLS
        DropdownOption(
            label = "Add HLS:",
            value = if (parameters.recordingAddHLS) "true" else "false",
            options = listOf("true" to "True", "false" to "False"),
            onValueChange = { parameters.updateRecordingAddHLS(it == "true") }
        )
    }
}

@Composable
private fun AdvancedPanelSection(
    parameters: com.mediasfu.sdk.ui.components.recording.RecordingModalParameters,
    eventType: String
) {
    var customTextValue by remember { mutableStateOf(parameters.recordingCustomText) }
    
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Advanced Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        // Video Type
        DropdownOption(
            label = "Video Type:",
            value = parameters.recordingVideoType,
            options = listOf(
                "fullDisplay" to "Full Display (no background)",
                "bestDisplay" to "Full Video",
                "all" to "All"
            ),
            onValueChange = { parameters.updateRecordingVideoType(it) }
        )
        
        // Display Type (not for broadcast)
        if (eventType != "broadcast") {
            DropdownOption(
                label = "Display Type:",
                value = parameters.recordingDisplayType,
                options = listOf(
                    "video" to "Only Video Participants",
                    "videoOpt" to "Only Video Participants (optimized)",
                    "media" to "Participants with media",
                    "all" to "All Participants"
                ),
                onValueChange = { parameters.updateRecordingDisplayType(it) }
            )
        }
        
        // Background Color
        ColorPickerOption(
            label = "Background Color:",
            color = parameters.recordingBackgroundColor,
            onColorChange = { parameters.updateRecordingBackgroundColor(it) }
        )
        
        // Add Text
        DropdownOption(
            label = "Add Text:",
            value = if (parameters.recordingAddText) "true" else "false",
            options = listOf("true" to "True", "false" to "False"),
            onValueChange = { parameters.updateRecordingAddText(it == "true") }
        )
        
        // Custom Text (if Add Text is true)
        if (parameters.recordingAddText) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Custom Text:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                OutlinedTextField(
                    value = customTextValue,
                    onValueChange = { newValue ->
                        // Validate: alphanumeric and spaces only, max 40 chars
                        if (newValue.length <= 40 && (newValue.isEmpty() || newValue.matches(Regex("^[a-zA-Z0-9\\s]*$")))) {
                            customTextValue = newValue
                            parameters.updateRecordingCustomText(newValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter custom text (max 40 chars)") },
                    singleLine = true
                )
                
                // Custom Text Position
                DropdownOption(
                    label = "Custom Text Position:",
                    value = parameters.recordingCustomTextPosition,
                    options = listOf(
                        "top" to "Top",
                        "middle" to "Middle",
                        "bottom" to "Bottom"
                    ),
                    onValueChange = { parameters.updateRecordingCustomTextPosition(it) }
                )
                
                // Custom Text Color
                ColorPickerOption(
                    label = "Custom Text Color:",
                    color = parameters.recordingCustomTextColor,
                    onColorChange = { parameters.updateRecordingCustomTextColor(it) }
                )
            }
        }
        
        // Add Name Tags
        DropdownOption(
            label = "Add Name Tags:",
            value = if (parameters.recordingNameTags) "true" else "false",
            options = listOf("true" to "True", "false" to "False"),
            onValueChange = { parameters.updateRecordingNameTags(it == "true") }
        )
        
        // Name Tags Color
        ColorPickerOption(
            label = "Name Tags Color:",
            color = parameters.recordingNameTagsColor,
            onColorChange = { parameters.updateRecordingNameTagsColor(it) }
        )
        
        // Orientation (Video)
        DropdownOption(
            label = "Orientation (Video):",
            value = parameters.recordingOrientationVideo,
            options = listOf(
                "landscape" to "Landscape",
                "portrait" to "Portrait",
                "all" to "All"
            ),
            onValueChange = { parameters.updateRecordingOrientationVideo(it) }
        )
    }
}

@Composable
private fun DropdownOption(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        options.find { it.first == value }?.second ?: value,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (optionValue, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onValueChange(optionValue)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPickerOption(
    label: String,
    color: String,
    onColorChange: (String) -> Unit
) {
    var showColorDialog by remember { mutableStateOf(false) }
    val currentColor = try {
        parseColor(color)
    } catch (e: Exception) {
        Color.Black
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        OutlinedButton(
            onClick = { showColorDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(24.dp),
                        color = currentColor,
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                    Text(color, textAlign = TextAlign.Start)
                }
                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
            }
        }
    }
    
    if (showColorDialog) {
        SimpleColorPickerDialog(
            currentColor = color,
            onColorSelected = { selectedColor ->
                onColorChange(selectedColor)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}

@Composable
private fun SimpleColorPickerDialog(
    currentColor: String,
    onColorSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val commonColors = listOf(
        "#000000" to "Black",
        "#FFFFFF" to "White",
        "#FF0000" to "Red",
        "#00FF00" to "Green",
        "#0000FF" to "Blue",
        "#FFFF00" to "Yellow",
        "#FF00FF" to "Magenta",
        "#00FFFF" to "Cyan",
        "#FFA500" to "Orange",
        "#800080" to "Purple",
        "#808080" to "Gray",
        "#83c0e9" to "Sky Blue"
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                commonColors.forEach { (colorHex, colorName) ->
                    val color = try {
                        parseColor(colorHex)
                    } catch (e: Exception) {
                        Color.Black
                    }
                    
                    Surface(
                        onClick = { onColorSelected(colorHex) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = if (colorHex == currentColor) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                color = color,
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.Gray)
                            ) {}
                            Column {
                                Text(colorName, fontWeight = FontWeight.Medium)
                                Text(colorHex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun parseColor(colorString: String): Color {
    return try {
        val hex = colorString.removePrefix("#")
        val fullHex = if (hex.length == 6) "FF$hex" else hex
        Color(fullHex.toLong(16))
    } catch (e: Exception) {
        Color.Black
    }
}
