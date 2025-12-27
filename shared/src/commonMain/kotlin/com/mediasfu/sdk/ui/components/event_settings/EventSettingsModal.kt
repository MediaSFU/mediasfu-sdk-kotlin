package com.mediasfu.sdk.ui.components.event_settings

import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import kotlin.collections.buildMap

/**
 * EventSettingsModal - Modal for configuring event-specific media settings.
 *
 * Provides options to control participant permissions for audio, video, screenshare, and chat.
 *
 * @property options Configuration options for the event settings modal
 */
data class ModifySettingsOptions(
    val roomName: String,
    val socket: SocketManager?,
    val showAlert: ShowAlert?,
    val audioSetting: String,
    val videoSetting: String,
    val screenshareSetting: String,
    val chatSetting: String,
    val updateAudioSetting: (String) -> Unit,
    val updateVideoSetting: (String) -> Unit,
    val updateScreenshareSetting: (String) -> Unit,
    val updateChatSetting: (String) -> Unit,
    val updateIsSettingsModalVisible: (Boolean) -> Unit,
)

data class EventSettingsModalOptions(
    val isVisible: Boolean = false,
    val onClose: () -> Unit,
    val onModifySettings: (ModifySettingsOptions) -> Unit = { /* default implementation */ },
    val position: String = "topRight",
    val backgroundColor: Int = 0xFF83C0E9.toInt(),
    val audioSetting: String,
    val videoSetting: String,
    val screenshareSetting: String,
    val chatSetting: String,
    val roomName: String,
    val socket: SocketManager?,
    val showAlert: ShowAlert?,
    val updateAudioSetting: (String) -> Unit,
    val updateVideoSetting: (String) -> Unit,
    val updateScreenshareSetting: (String) -> Unit,
    val updateChatSetting: (String) -> Unit,
    val updateIsSettingsModalVisible: (Boolean) -> Unit,
)

interface EventSettingsModal : MediaSfuUIComponent {
    val options: EventSettingsModalOptions
    override val id: String get() = "event_settings_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /**
     * State management for settings
     */
    var audioState: String
    var videoState: String
    var screenshareState: String
    var chatState: String
    
    /**
     * Available setting options for dropdowns
     */
    val settingOptions: List<String>
        get() = listOf("allow", "approval", "disallow")
    
    /**
     * Handles updating audio setting
     */
    fun handleAudioSettingChange(value: String) {
        audioState = value
    }
    
    /**
     * Handles updating video setting
     */
    fun handleVideoSettingChange(value: String) {
        videoState = value
    }
    
    /**
     * Handles updating screenshare setting
     */
    fun handleScreenshareSettingChange(value: String) {
        screenshareState = value
    }
    
    /**
     * Handles updating chat setting
     */
    fun handleChatSettingChange(value: String) {
        chatState = value
    }
    
    /**
     * Handles saving settings
     */
    fun handleSaveSettings() {
        options.onModifySettings(
            ModifySettingsOptions(
                roomName = options.roomName,
                socket = options.socket,
                showAlert = options.showAlert,
                audioSetting = audioState,
                videoSetting = videoState,
                screenshareSetting = screenshareState,
                chatSetting = chatState,
                updateAudioSetting = options.updateAudioSetting,
                updateVideoSetting = options.updateVideoSetting,
                updateScreenshareSetting = options.updateScreenshareSetting,
                updateChatSetting = options.updateChatSetting,
                updateIsSettingsModalVisible = options.updateIsSettingsModalVisible
            )
        )
    }
}

/**
 * Default implementation of EventSettingsModal
 */
class DefaultEventSettingsModal(
    override val options: EventSettingsModalOptions
) : EventSettingsModal {
    override var audioState: String = options.audioSetting
    override var videoState: String = options.videoSetting
    override var screenshareState: String = options.screenshareSetting
    override var chatState: String = options.chatSetting
    
    fun render(): Map<String, Any> {
        return buildMap {
            put("type", "eventSettingsModal")
            put("isVisible", options.isVisible)
            put("onClose", options.onClose)
            put("audioState", audioState)
            put("videoState", videoState)
            put("screenshareState", screenshareState)
            put("chatState", chatState)
            put("settingOptions", settingOptions)
            put("position", options.position)
            put("backgroundColor", options.backgroundColor)
            put("onAudioSettingChange", { value: String -> handleAudioSettingChange(value) })
            put("onVideoSettingChange", { value: String -> handleVideoSettingChange(value) })
            put("onScreenshareSettingChange", { value: String -> handleScreenshareSettingChange(value) })
            put("onChatSettingChange", { value: String -> handleChatSettingChange(value) })
            put("onSave", { handleSaveSettings() })
        }
    }
}
