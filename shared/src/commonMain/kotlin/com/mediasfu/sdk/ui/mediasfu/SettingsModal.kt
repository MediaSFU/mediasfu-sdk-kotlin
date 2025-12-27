package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.ui.components.event_settings.EventSettingsModalOptions
import com.mediasfu.sdk.ui.components.event_settings.ModifySettingsOptions

private val EVENT_SETTING_OPTIONS = listOf("allow", "approval", "disallow")

@Composable
fun SettingsModal(state: MediasfuGenericState) {
    val options = state.createEventSettingsModalOptions()
    if (!options.isVisible) return

    val overrideContent = state.options.uiOverrides.eventSettingsModal
        val contentBuilder = withOverride(
            override = overrideContent,
            baseBuilder = { DefaultEventSettingsModalContent(it) }
        )

        contentBuilder(options)
}

/**
 * Public content body for SettingsModal (Event Settings) - can be embedded in unified modal system
 * Provides full event permission settings functionality without AlertDialog wrapper
 */
@Composable
fun SettingsModalContentBody(
    options: EventSettingsModalOptions,
    modifier: Modifier = Modifier,
    onSave: (() -> Unit)? = null
) {
    var audioSetting by remember { mutableStateOf(options.audioSetting) }
    var videoSetting by remember { mutableStateOf(options.videoSetting) }
    var screenshareSetting by remember { mutableStateOf(options.screenshareSetting) }
    var chatSetting by remember { mutableStateOf(options.chatSetting) }

    LaunchedEffect(options.isVisible) {
        if (options.isVisible) {
            audioSetting = options.audioSetting
            videoSetting = options.videoSetting
            screenshareSetting = options.screenshareSetting
            chatSetting = options.chatSetting
        }
    }

    LaunchedEffect(options.audioSetting) { audioSetting = options.audioSetting }
    LaunchedEffect(options.videoSetting) { videoSetting = options.videoSetting }
    LaunchedEffect(options.screenshareSetting) { screenshareSetting = options.screenshareSetting }
    LaunchedEffect(options.chatSetting) { chatSetting = options.chatSetting }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Choose permission levels for attendees.",
            style = MaterialTheme.typography.bodyMedium
        )
        
        SettingSelector(
            label = "Audio",
            value = audioSetting,
            onValueChange = { audioSetting = it }
        )
        SettingSelector(
            label = "Video",
            value = videoSetting,
            onValueChange = { videoSetting = it }
        )
        SettingSelector(
            label = "Screenshare",
            value = screenshareSetting,
            onValueChange = { screenshareSetting = it }
        )
        SettingSelector(
            label = "Chat",
            value = chatSetting,
            onValueChange = { chatSetting = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = {
                options.onModifySettings(
                    ModifySettingsOptions(
                        roomName = options.roomName,
                        socket = options.socket,
                        showAlert = options.showAlert,
                        audioSetting = audioSetting,
                        videoSetting = videoSetting,
                        screenshareSetting = screenshareSetting,
                        chatSetting = chatSetting,
                        updateAudioSetting = options.updateAudioSetting,
                        updateVideoSetting = options.updateVideoSetting,
                        updateScreenshareSetting = options.updateScreenshareSetting,
                        updateChatSetting = options.updateChatSetting,
                        updateIsSettingsModalVisible = options.updateIsSettingsModalVisible
                    )
                )
                // Show success feedback
                options.showAlert?.invoke(
                    "Event settings saved successfully",
                    "success",
                    2000
                )
                onSave?.invoke()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun DefaultEventSettingsModalContent(options: EventSettingsModalOptions) {
    AlertDialog(
        onDismissRequest = options.onClose,
        title = { Text("Event Settings") },
        text = {
            SettingsModalContentBody(
                options = options,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = options.onClose) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingSelector(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EVENT_SETTING_OPTIONS.forEach { option ->
                SettingOptionChip(
                    label = option.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    selected = value.equals(option, ignoreCase = true),
                    onClick = { onValueChange(option) }
                )
            }
        }
    }
}

@Composable
private fun SettingOptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        tonalElevation = if (selected) 4.dp else 0.dp
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}
