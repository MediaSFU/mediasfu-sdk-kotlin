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
        position = "topRight",
        backgroundColor = 0xFF83C0E9.toInt(),
        audioSetting = media.audioSetting,
        videoSetting = media.videoSetting,
        screenshareSetting = media.screenshareSetting,
        chatSetting = media.chatSetting,
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
