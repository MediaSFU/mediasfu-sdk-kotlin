package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.methods.settings_methods.ModifySettingsOptions as MethodModifySettingsOptions
import com.mediasfu.sdk.methods.settings_methods.modifySettings
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.ui.components.event_settings.DefaultEventSettingsModal
import com.mediasfu.sdk.ui.components.event_settings.EventSettingsModalOptions
import com.mediasfu.sdk.ui.components.event_settings.ModifySettingsOptions

internal fun MediasfuGenericState.eventSettingsModalSnapshot(): Map<String, Any> {
    val modal = DefaultEventSettingsModal(createEventSettingsModalOptions())
    return modal.render()
}

internal fun MediasfuGenericState.createEventSettingsModalOptions(): EventSettingsModalOptions {
    val showAlertHandler = parameters.showAlertHandler ?: ShowAlert { message, type, duration ->
        alert.show(message, type, duration)
        parameters.showAlert(message, type, duration)
        propagateParameterChanges()
    }

    return EventSettingsModalOptions(
        isVisible = modals.isSettingsVisible,
    onClose = { modals.setSettingsVisibility(false) },
        onModifySettings = { options -> handleModifySettings(options, showAlertHandler) },
        onUpdatePermissionConfig = { config -> applyPermissionConfig(config.values) },
        onApplyTranslationSettings = { spokenLanguage, spokenEnabled, defaultOutputLanguage, defaultListenLanguage, perSpeakerListenPreferences, showSubtitles ->
            applyTranslationSettings(
                spokenLanguage = spokenLanguage,
                spokenEnabled = spokenEnabled,
                defaultOutputLanguage = defaultOutputLanguage,
                defaultListenLanguage = defaultListenLanguage,
                perSpeakerListenPreferences = perSpeakerListenPreferences,
                showSubtitles = showSubtitles
            )
        },
        position = "topRight",
        backgroundColor = 0xFF83C0E9.toInt(),
        audioSetting = media.audioSetting,
        videoSetting = media.videoSetting,
        screenshareSetting = media.screenshareSetting,
        chatSetting = media.chatSetting,
        permissionConfig = permissionConfig.value,
        translationSupported = translationSupported.value,
        translationConfig = translationConfig.value,
        mySpokenLanguage = mySpokenLanguage.value,
        mySpokenLanguageEnabled = mySpokenLanguageEnabled.value,
        myDefaultOutputLanguage = myDefaultOutputLanguage.value,
        myDefaultListenLanguage = myDefaultListenLanguage.value,
        participants = room.participants.toList(),
        listenPreferences = listenPreferences.value,
        translationChannelsBySpeaker = translationChannelsBySpeaker.value,
        showTranslationSubtitles = showTranslationSubtitles.value,
        isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true),
        roomName = room.roomName,
        socket = parameters.socket,
        showAlert = showAlertHandler,
        updateAudioSetting = { value -> media.updateAudioSetting(value) },
        updateVideoSetting = { value -> media.updateVideoSetting(value) },
        updateScreenshareSetting = { value -> media.updateScreenshareSetting(value) },
        updateChatSetting = { value -> media.updateChatSetting(value) },
    updateIsSettingsModalVisible = { visible -> modals.setSettingsVisibility(visible) }
    )
}

private fun MediasfuGenericState.handleModifySettings(
    options: ModifySettingsOptions,
    showAlertHandler: ShowAlert
) {
    launchInScope {
        modifySettings(
            MethodModifySettingsOptions(
                showAlert = showAlertHandler,
                roomName = options.roomName,
                audioSet = options.audioSetting,
                videoSet = options.videoSetting,
                screenshareSet = options.screenshareSetting,
                chatSet = options.chatSetting,
                socket = options.socket,
                updateAudioSetting = { value -> media.updateAudioSetting(value) },
                updateVideoSetting = { value -> media.updateVideoSetting(value) },
                updateScreenshareSetting = { value -> media.updateScreenshareSetting(value) },
                updateChatSetting = { value -> media.updateChatSetting(value) },
                updateIsSettingsModalVisible = { visible -> modals.setSettingsVisibility(visible) }
            )
        )
    }
}
