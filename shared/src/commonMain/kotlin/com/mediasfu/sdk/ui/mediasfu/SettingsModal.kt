package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
private val CHAT_SETTING_OPTIONS = listOf("allow", "disallow")

private data class PermissionPreset(
    val mic: String,
    val camera: String,
    val screen: String,
    val chat: String
)

private val DEFAULT_LEVEL0_PRESET = PermissionPreset(
    mic = "approval",
    camera = "approval",
    screen = "disallow",
    chat = "allow"
)

private val DEFAULT_LEVEL1_PRESET = PermissionPreset(
    mic = "allow",
    camera = "allow",
    screen = "approval",
    chat = "allow"
)

private fun Map<*, *>.toStringAnyMapLocal(): Map<String, Any?> =
    entries.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }.toMap()

private val DEFAULT_TRANSLATION_LANGUAGE_OPTIONS = listOf(
    LanguageEntry("en", "English"),
    LanguageEntry("es", "Spanish"),
    LanguageEntry("fr", "French"),
    LanguageEntry("de", "German"),
    LanguageEntry("pt", "Portuguese"),
    LanguageEntry("it", "Italian"),
    LanguageEntry("ar", "Arabic"),
    LanguageEntry("zh", "Chinese"),
    LanguageEntry("ja", "Japanese"),
    LanguageEntry("ko", "Korean"),
    LanguageEntry("hi", "Hindi")
)

private fun resolveTranslationLanguageOptions(
    mode: LanguageMode,
    allowed: List<LanguageEntry>?,
    blocked: List<String>?
): List<LanguageEntry> {
    return when (mode) {
        LanguageMode.ALLOWLIST -> allowed?.ifEmpty { DEFAULT_TRANSLATION_LANGUAGE_OPTIONS } ?: DEFAULT_TRANSLATION_LANGUAGE_OPTIONS
        LanguageMode.BLOCKLIST -> DEFAULT_TRANSLATION_LANGUAGE_OPTIONS.filterNot { blocked.orEmpty().contains(it.code) }
        LanguageMode.ANY -> DEFAULT_TRANSLATION_LANGUAGE_OPTIONS
    }
}

private fun LanguageEntry.displayLabel(): String = nickname?.takeIf { it.isNotBlank() } ?: code.uppercase()

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

    var level0Mic by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level0"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useMic")?.toString() } ?: options.audioSetting) }
    var level0Camera by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level0"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useCamera")?.toString() } ?: options.videoSetting) }
    var level0Screen by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level0"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useScreen")?.toString() } ?: options.screenshareSetting) }
    var level0Chat by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level0"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useChat")?.toString() } ?: options.chatSetting) }

    var level1Mic by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level1"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useMic")?.toString() } ?: options.audioSetting) }
    var level1Camera by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level1"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useCamera")?.toString() } ?: options.videoSetting) }
    var level1Screen by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level1"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useScreen")?.toString() } ?: options.screenshareSetting) }
    var level1Chat by remember { mutableStateOf(options.permissionConfig?.values?.let { (it["level1"] as? Map<*, *>)?.toStringAnyMapLocal()?.get("useChat")?.toString() } ?: options.chatSetting) }

    var translationEnabled by remember { mutableStateOf(options.mySpokenLanguageEnabled) }
    var spokenLanguage by remember { mutableStateOf(options.mySpokenLanguage.ifBlank { "en" }) }
    var outputLanguage by remember { mutableStateOf(options.myDefaultOutputLanguage ?: options.mySpokenLanguage.ifBlank { "en" }) }
    var listenLanguage by remember { mutableStateOf(options.myDefaultListenLanguage ?: "") }
    var showTranslationSubtitles by remember { mutableStateOf(options.showTranslationSubtitles) }
    var perSpeakerMode by remember { mutableStateOf(options.listenPreferences.isNotEmpty() && options.myDefaultListenLanguage == null) }
    var perSpeakerListenPrefs by remember { mutableStateOf(options.listenPreferences) }

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

    LaunchedEffect(options.permissionConfig) {
        val config = options.permissionConfig?.values
        val level0 = (config?.get("level0") as? Map<*, *>)?.toStringAnyMapLocal() ?: emptyMap()
        val level1 = (config?.get("level1") as? Map<*, *>)?.toStringAnyMapLocal() ?: emptyMap()

        level0Mic = level0["useMic"]?.toString() ?: audioSetting
        level0Camera = level0["useCamera"]?.toString() ?: videoSetting
        level0Screen = level0["useScreen"]?.toString() ?: screenshareSetting
        level0Chat = level0["useChat"]?.toString() ?: chatSetting

        level1Mic = level1["useMic"]?.toString() ?: audioSetting
        level1Camera = level1["useCamera"]?.toString() ?: videoSetting
        level1Screen = level1["useScreen"]?.toString() ?: screenshareSetting
        level1Chat = level1["useChat"]?.toString() ?: chatSetting
    }

    LaunchedEffect(
        options.mySpokenLanguage,
        options.mySpokenLanguageEnabled,
        options.myDefaultOutputLanguage,
        options.myDefaultListenLanguage,
        options.showTranslationSubtitles
    ) {
        translationEnabled = options.mySpokenLanguageEnabled
        spokenLanguage = options.mySpokenLanguage.ifBlank { spokenLanguage }
        outputLanguage = options.myDefaultOutputLanguage ?: outputLanguage
        listenLanguage = options.myDefaultListenLanguage ?: ""
        showTranslationSubtitles = options.showTranslationSubtitles
        perSpeakerMode = options.listenPreferences.isNotEmpty() && options.myDefaultListenLanguage == null
        perSpeakerListenPrefs = options.listenPreferences
    }

    val spokenOptions = remember(options.translationConfig) {
        val config = options.translationConfig
        if (config == null) {
            DEFAULT_TRANSLATION_LANGUAGE_OPTIONS
        } else {
            resolveTranslationLanguageOptions(
                mode = config.spokenLanguageMode,
                allowed = config.allowedSpokenLanguages,
                blocked = config.blockedSpokenLanguages
            )
        }
    }

    val listenOptions = remember(options.translationConfig) {
        val config = options.translationConfig
        if (config == null) {
            DEFAULT_TRANSLATION_LANGUAGE_OPTIONS
        } else {
            resolveTranslationLanguageOptions(
                mode = config.listenLanguageMode,
                allowed = config.allowedListenLanguages,
                blocked = config.blockedListenLanguages
            )
        }
    }

    val otherParticipants = remember(options.participants) {
        options.participants.filter { participant ->
            participant.name.isNotBlank() && participant.name != options.roomName && !participant.name.equals("Host", ignoreCase = true)
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Choose permission levels for attendees.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (!options.isHost) {
            Text(
                text = "Only the host can change permission matrix settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Quick presets",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            level0Mic = options.audioSetting
                            level0Camera = options.videoSetting
                            level0Screen = options.screenshareSetting
                            level0Chat = options.chatSetting

                            level1Mic = options.audioSetting
                            level1Camera = options.videoSetting
                            level1Screen = options.screenshareSetting
                            level1Chat = options.chatSetting
                        }) {
                            Text("Use event baseline")
                        }
                        TextButton(onClick = {
                            level0Mic = DEFAULT_LEVEL0_PRESET.mic
                            level0Camera = DEFAULT_LEVEL0_PRESET.camera
                            level0Screen = DEFAULT_LEVEL0_PRESET.screen
                            level0Chat = DEFAULT_LEVEL0_PRESET.chat

                            level1Mic = DEFAULT_LEVEL1_PRESET.mic
                            level1Camera = DEFAULT_LEVEL1_PRESET.camera
                            level1Screen = DEFAULT_LEVEL1_PRESET.screen
                            level1Chat = DEFAULT_LEVEL1_PRESET.chat
                        }) {
                            Text("Use default policy")
                        }
                    }
                }
            }
        }

        if (options.isHost) {
            PermissionLevelSection(
                title = "Level 0 (Attendees)",
                micValue = level0Mic,
                onMicChange = { level0Mic = it },
                cameraValue = level0Camera,
                onCameraChange = { level0Camera = it },
                screenValue = level0Screen,
                onScreenChange = { level0Screen = it },
                chatValue = level0Chat,
                onChatChange = { level0Chat = it },
                onReset = {
                    level0Mic = DEFAULT_LEVEL0_PRESET.mic
                    level0Camera = DEFAULT_LEVEL0_PRESET.camera
                    level0Screen = DEFAULT_LEVEL0_PRESET.screen
                    level0Chat = DEFAULT_LEVEL0_PRESET.chat
                },
                isHost = options.isHost
            )

            PermissionLevelSection(
                title = "Level 1 (Elevated)",
                micValue = level1Mic,
                onMicChange = { level1Mic = it },
                cameraValue = level1Camera,
                onCameraChange = { level1Camera = it },
                screenValue = level1Screen,
                onScreenChange = { level1Screen = it },
                chatValue = level1Chat,
                onChatChange = { level1Chat = it },
                onReset = {
                    level1Mic = DEFAULT_LEVEL1_PRESET.mic
                    level1Camera = DEFAULT_LEVEL1_PRESET.camera
                    level1Screen = DEFAULT_LEVEL1_PRESET.screen
                    level1Chat = DEFAULT_LEVEL1_PRESET.chat
                },
                isHost = options.isHost
            )
        }

        if (options.translationSupported && options.translationConfig != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Translation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingSelector(
                        label = "Spoken translation",
                        value = if (translationEnabled) "allow" else "disallow",
                        options = listOf("allow", "disallow"),
                        onValueChange = { translationEnabled = it == "allow" }
                    )
                    TranslationLanguageSelector(
                        label = "Spoken language",
                        selectedCode = spokenLanguage,
                        options = spokenOptions,
                        enabled = translationEnabled,
                        onSelected = { spokenLanguage = it }
                    )
                    TranslationLanguageSelector(
                        label = "Default output",
                        selectedCode = outputLanguage,
                        options = listenOptions,
                        enabled = translationEnabled,
                        onSelected = { outputLanguage = it }
                    )
                    TranslationLanguageSelector(
                        label = "Default listen language",
                        selectedCode = listenLanguage,
                        options = listenOptions,
                        enabled = !perSpeakerMode,
                        includeOff = true,
                        onSelected = { listenLanguage = it }
                    )
                    SettingSelector(
                        label = "Listening mode",
                        value = if (perSpeakerMode) "approval" else "allow",
                        options = listOf("allow", "approval"),
                        onValueChange = {
                            perSpeakerMode = it == "approval"
                            if (!perSpeakerMode) perSpeakerListenPrefs = emptyMap()
                        }
                    )
                    if (perSpeakerMode) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Per-speaker listen overrides",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            if (otherParticipants.isEmpty()) {
                                Text(
                                    text = "No other participants available yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                otherParticipants.forEach { participant ->
                                    val participantId = participant.id ?: participant.name
                                    val channelOptions = options.translationChannelsBySpeaker[participantId]
                                        ?.map { code -> listenOptions.firstOrNull { it.code == code } ?: LanguageEntry(code) }
                                        ?.ifEmpty { listenOptions }
                                        ?: listenOptions
                                    TranslationLanguageSelector(
                                        label = participant.name,
                                        selectedCode = perSpeakerListenPrefs[participantId].orEmpty(),
                                        options = channelOptions,
                                        enabled = true,
                                        includeOff = true,
                                        onSelected = { selected ->
                                            perSpeakerListenPrefs = perSpeakerListenPrefs.toMutableMap().also { next ->
                                                if (selected.isBlank()) next.remove(participantId) else next[participantId] = selected
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    SettingSelector(
                        label = "Subtitles overlay",
                        value = if (showTranslationSubtitles) "allow" else "disallow",
                        options = listOf("allow", "disallow"),
                        onValueChange = { showTranslationSubtitles = it == "allow" }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Save Button
        Button(
            onClick = {
                val config = PermissionConfig(
                    values = mapOf(
                        "level0" to mapOf(
                            "useMic" to level0Mic,
                            "useCamera" to level0Camera,
                            "useScreen" to level0Screen,
                            "useChat" to level0Chat
                        ),
                        "level1" to mapOf(
                            "useMic" to level1Mic,
                            "useCamera" to level1Camera,
                            "useScreen" to level1Screen,
                            "useChat" to level1Chat
                        )
                    )
                )

                if (options.isHost && options.onUpdatePermissionConfig != null) {
                    options.onUpdatePermissionConfig.invoke(config)
                } else if (options.isHost) {
                    options.onModifySettings(
                        ModifySettingsOptions(
                            roomName = options.roomName,
                            socket = options.socket,
                            showAlert = options.showAlert,
                            audioSetting = level0Mic,
                            videoSetting = level0Camera,
                            screenshareSetting = level0Screen,
                            chatSetting = level0Chat,
                            updateAudioSetting = options.updateAudioSetting,
                            updateVideoSetting = options.updateVideoSetting,
                            updateScreenshareSetting = options.updateScreenshareSetting,
                            updateChatSetting = options.updateChatSetting,
                            updateIsSettingsModalVisible = options.updateIsSettingsModalVisible
                        )
                    )
                }
                options.onApplyTranslationSettings?.invoke(
                    spokenLanguage,
                    translationEnabled,
                    outputLanguage.takeIf { it.isNotBlank() },
                    if (perSpeakerMode) null else listenLanguage.takeIf { it.isNotBlank() },
                    if (perSpeakerMode) perSpeakerListenPrefs else emptyMap(),
                    showTranslationSubtitles
                )
                onSave?.invoke()
            },
            enabled = options.isHost || options.translationSupported,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }
    }
}

@Composable
private fun TranslationLanguageSelector(
    label: String,
    selectedCode: String,
    options: List<LanguageEntry>,
    enabled: Boolean,
    includeOff: Boolean = false,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedEntry = options.firstOrNull { it.code == selectedCode }
    val displayText = when {
        includeOff && selectedCode.isBlank() -> "Off"
        selectedEntry != null -> selectedEntry.displayLabel()
        selectedCode.isNotBlank() -> selectedCode.uppercase()
        else -> "Select language"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { expanded = true },
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 1.dp
            ) {
                Text(
                    text = displayText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                if (includeOff) {
                    DropdownMenuItem(
                        text = { Text("Off") },
                        onClick = {
                            onSelected("")
                            expanded = false
                        }
                    )
                }
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayLabel()) },
                        onClick = {
                            onSelected(option.code)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionLevelSection(
    title: String,
    micValue: String,
    onMicChange: (String) -> Unit,
    cameraValue: String,
    onCameraChange: (String) -> Unit,
    screenValue: String,
    onScreenChange: (String) -> Unit,
    chatValue: String,
    onChatChange: (String) -> Unit,
    onReset: () -> Unit,
    isHost: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onReset, enabled = isHost) {
                    Text("Reset")
                }
            }
            SettingSelector(
                label = "Audio",
                value = micValue,
                options = EVENT_SETTING_OPTIONS,
                onValueChange = onMicChange
            )
            SettingSelector(
                label = "Video",
                value = cameraValue,
                options = EVENT_SETTING_OPTIONS,
                onValueChange = onCameraChange
            )
            SettingSelector(
                label = "Screenshare",
                value = screenValue,
                options = EVENT_SETTING_OPTIONS,
                onValueChange = onScreenChange
            )
            SettingSelector(
                label = "Chat",
                value = chatValue,
                options = CHAT_SETTING_OPTIONS,
                onValueChange = onChatChange
            )
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
private fun SettingSelector(
    label: String,
    value: String,
    options: List<String> = EVENT_SETTING_OPTIONS,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
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
