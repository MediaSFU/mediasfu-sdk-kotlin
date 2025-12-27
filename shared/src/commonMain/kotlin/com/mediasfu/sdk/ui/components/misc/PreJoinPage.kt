package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * PreJoinPage - Pre-meeting join screen with user info and device preview.
 *
 * Provides interface for users to enter their name, preview audio/video,
 * and configure settings before joining a meeting.
 *
 * @property options Configuration options for the pre-join page
 */
data class PreJoinPageOptions(
    val onJoinRoom: (JoinRoomParams) -> Unit,
    val userName: String = "",
    val onUserNameChange: (String) -> Unit,
    val showVideoPreview: Boolean = true,
    val showAudioPreview: Boolean = true,
    val audioEnabled: Boolean = true,
    val videoEnabled: Boolean = true,
    val onToggleAudio: () -> Unit,
    val onToggleVideo: () -> Unit,
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val isHost: Boolean = false,
    val roomName: String = "",
    val eventType: String = "conference",
)

data class JoinRoomParams(
    val userName: String,
    val audioEnabled: Boolean,
    val videoEnabled: Boolean,
)

interface PreJoinPage : MediaSfuUIComponent {
    val options: PreJoinPageOptions
    override val id: String get() = "pre_join_page"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Validates user input before joining
     */
    fun canJoin(): Boolean {
        return options.userName.isNotBlank()
    }
    
    /**
     * Handles join button press
     */
    fun handleJoin() {
        if (canJoin()) {
            options.onJoinRoom(
                JoinRoomParams(
                    userName = options.userName,
                    audioEnabled = options.audioEnabled,
                    videoEnabled = options.videoEnabled
                )
            )
        }
    }
    
    /**
     * Gets page style configuration
     */
    fun getPageStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexDirection" to "column",
            "alignItems" to "center",
            "justifyContent" to "center",
            "height" to "100vh",
            "padding" to "20px"
        )
    }
}

/**
 * Default implementation of PreJoinPage
 */
class DefaultPreJoinPage(
    override val options: PreJoinPageOptions
) : PreJoinPage {
    fun render(): Any {
        return mapOf(
            "type" to "preJoinPage",
            "userName" to options.userName,
            "onUserNameChange" to options.onUserNameChange,
            "showVideoPreview" to options.showVideoPreview,
            "showAudioPreview" to options.showAudioPreview,
            "audioEnabled" to options.audioEnabled,
            "videoEnabled" to options.videoEnabled,
            "onToggleAudio" to options.onToggleAudio,
            "onToggleVideo" to options.onToggleVideo,
            "canJoin" to canJoin(),
            "onJoin" to ::handleJoin,
            "pageStyle" to getPageStyle(),
            "isHost" to options.isHost,
            "roomName" to options.roomName,
            "eventType" to options.eventType
        )
    }
}
