package com.mediasfu.sdk.methods.settings_methods

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager

/**
 * Type definitions for settings updates.
 */
typealias UpdateSetting = (String) -> Unit
typealias UpdateIsSettingsModalVisible = (Boolean) -> Unit

/**
 * Options for modifying room settings.
 */
data class ModifySettingsOptions(
    val showAlert: ShowAlert? = null,
    val roomName: String,
    val audioSet: String,
    val videoSet: String,
    val screenshareSet: String,
    val chatSet: String,
    val socket: SocketManager? = null,
    val updateAudioSetting: UpdateSetting,
    val updateVideoSetting: UpdateSetting,
    val updateScreenshareSetting: UpdateSetting,
    val updateChatSetting: UpdateSetting,
    val updateIsSettingsModalVisible: UpdateIsSettingsModalVisible
)

/**
 * Type definition for the modifySettings function.
 */
typealias ModifySettingsType = suspend (ModifySettingsOptions) -> Unit

/**
 * Modifies settings for a given room and updates the state accordingly.
 * 
 * - `options`: Options for modifying settings, including:
 *   - `showAlert`: Function to show alert messages (optional).
 *   - `roomName`: The name of the room.
 *   - `audioSet`: The audio setting to be applied.
 *   - `videoSet`: The video setting to be applied.
 *   - `screenshareSet`: The screenshare setting to be applied.
 *   - `chatSet`: The chat setting to be applied.
 *   - `socket`: The socket instance for emitting events.
 *   - `updateAudioSetting`: Function to update the audio setting state.
 *   - `updateVideoSetting`: Function to update the video setting state.
 *   - `updateScreenshareSetting`: Function to update the screenshare setting state.
 *   - `updateChatSetting`: Function to update the chat setting state.
 *   - `updateIsSettingsModalVisible`: Function to update the visibility of the settings modal.
 * 
 * Throws an alert if any setting is set to "approval" in demo mode (room name starts with "d").
 * 
 * Example usage:
 * ```kotlin
 * modifySettings(
 *     ModifySettingsOptions(
 *         roomName = "d123",
 *         audioSet = "allow",
 *         videoSet = "allow",
 *         screenshareSet = "deny",
 *         chatSet = "allow",
 *         socket = mySocketInstance,
 *         updateAudioSetting = setAudioSetting,
 *         updateVideoSetting = setVideoSetting,
 *         updateScreenshareSetting = setScreenshareSetting,
 *         updateChatSetting = setChatSetting,
 *         updateIsSettingsModalVisible = setIsSettingsModalVisible,
 *         showAlert = { options -> alertUser(options) }
 *     )
 * )
 * ```
 */
suspend fun modifySettings(options: ModifySettingsOptions) {
    if (options.roomName.lowercase().startsWith("d")) {
        // None of the settings should be set to 'approval' in demo mode
        if (options.audioSet == "approval" ||
            options.videoSet == "approval" ||
            options.screenshareSet == "approval" ||
            options.chatSet == "approval") {
            options.showAlert.call(
                "You cannot set approval for demo mode.",
                "danger",
                3000
            )
            return
        }
    }
    
    // Update settings based on the provided options
    if (options.audioSet.isNotEmpty()) options.updateAudioSetting(options.audioSet)
    if (options.videoSet.isNotEmpty()) options.updateVideoSetting(options.videoSet)
    if (options.screenshareSet.isNotEmpty()) {
        options.updateScreenshareSetting(options.screenshareSet)
    }
    if (options.chatSet.isNotEmpty()) options.updateChatSetting(options.chatSet)
    
    // Emit updated settings
    val settings = listOf(
        options.audioSet,
        options.videoSet,
        options.screenshareSet,
        options.chatSet
    )
    options.socket?.emit("updateSettingsForRequests", mapOf(
        "settings" to settings,
        "roomName" to options.roomName
    ))
    
    // Close modal
    options.updateIsSettingsModalVisible(false)
}
