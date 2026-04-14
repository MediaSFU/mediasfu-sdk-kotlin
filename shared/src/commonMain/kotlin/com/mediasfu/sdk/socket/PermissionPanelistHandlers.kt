package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.AddedAsPanelistOptions
import com.mediasfu.sdk.model.PanelistControlMediaOptions
import com.mediasfu.sdk.model.PanelistFocusChangedOptions
import com.mediasfu.sdk.model.PanelistsUpdatedOptions
import com.mediasfu.sdk.model.PermissionConfigUpdatedOptions
import com.mediasfu.sdk.model.PermissionUpdatedOptions
import com.mediasfu.sdk.model.RemovedFromPanelistsOptions
import com.mediasfu.sdk.model.call

suspend fun permissionUpdated(options: PermissionUpdatedOptions) {
    val data = options.data
    options.updateIslevel?.invoke(data.newLevel)

    if (!data.message.isNullOrBlank()) {
        options.showAlert.call(
            message = data.message,
            type = if (data.newLevel == "1") "success" else "info",
            duration = 3_000
        )
    }
}

suspend fun permissionConfigUpdated(options: PermissionConfigUpdatedOptions) {
    options.updatePermissionConfig?.invoke(options.data.config)
}

suspend fun panelistsUpdated(options: PanelistsUpdatedOptions) {
    options.updatePanelists?.invoke(options.data.panelists.map { it.toParticipant() })
}

suspend fun panelistFocusChanged(options: PanelistFocusChangedOptions) {
    val data = options.data

    val focusChanged = options.currentPanelistsFocused != null &&
        options.currentPanelistsFocused != data.focusEnabled

    val currentIds = (options.currentPanelists ?: emptyList()).mapNotNull { it.id }.sorted()
    val newIds = data.panelists.map { it.id }.sorted()
    val panelistsChanged = currentIds != newIds

    options.updatePanelistsFocused?.invoke(data.focusEnabled)
    options.updateMuteOthersMic?.invoke(data.muteOthersMic)
    options.updateMuteOthersCamera?.invoke(data.muteOthersCamera)
    options.updatePanelists?.invoke(data.panelists.map { it.toParticipant() })

    if ((focusChanged || panelistsChanged) && options.onScreenChanges != null) {
        options.onScreenChanges.invoke()
    }
}

suspend fun panelistControlMedia(options: PanelistControlMediaOptions) {
    val data = options.data

    if (data.action != "mute") return

    if (data.type == "audio" && options.audioAlreadyOn) {
        options.clickAudio?.invoke()
    } else if (data.type == "video" && options.videoAlreadyOn) {
        options.clickVideo?.invoke()
    }

    if (!data.reason.isNullOrBlank()) {
        val mediaType = if (data.type == "audio") "microphone" else "camera"
        options.showAlert.call(
            message = "Your $mediaType has been muted. ${data.reason}",
            type = "info",
            duration = 3_000
        )
    }
}

suspend fun addedAsPanelist(options: AddedAsPanelistOptions) {
    options.showAlert.call(
        message = options.data.message,
        type = "success",
        duration = 3_000
    )
}

suspend fun removedFromPanelists(options: RemovedFromPanelistsOptions) {
    options.showAlert.call(
        message = options.data.message,
        type = "info",
        duration = 3_000
    )
}
