package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * WelcomePage - Initial welcome screen with meeting options.
 *
 * Displays welcome message and provides options to create or join a meeting.
 *
 * @property options Configuration options for the welcome page
 */
data class WelcomePageOptions(
    val onCreateRoom: () -> Unit,
    val onJoinRoom: () -> Unit,
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val logoUrl: String = "",
    val appName: String = "MediaSFU",
    val welcomeMessage: String = "Welcome to MediaSFU",
    val subtitle: String = "Professional video conferencing made simple",
    val showQuickStart: Boolean = true,
)

interface WelcomePage : MediaSfuUIComponent {
    val options: WelcomePageOptions
    override val id: String get() = "welcome_page"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets page container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexDirection" to "column",
            "alignItems" to "center",
            "justifyContent" to "center",
            "height" to "100vh",
            "padding" to "40px",
            "textAlign" to "center"
        )
    }
    
    /**
     * Gets quick start items
     */
    fun getQuickStartItems(): List<QuickStartItem> {
        return listOf(
            QuickStartItem(
                icon = "video",
                title = "High Quality Video",
                description = "Crystal clear HD video calls"
            ),
            QuickStartItem(
                icon = "screen",
                title = "Screen Sharing",
                description = "Share your screen seamlessly"
            ),
            QuickStartItem(
                icon = "chat",
                title = "Real-time Chat",
                description = "Message participants during calls"
            ),
            QuickStartItem(
                icon = "record",
                title = "Recording",
                description = "Record your sessions"
            )
        )
    }
}

data class QuickStartItem(
    val icon: String,
    val title: String,
    val description: String,
)

/**
 * Default implementation of WelcomePage
 */
class DefaultWelcomePage(
    override val options: WelcomePageOptions
) : WelcomePage {
    fun render(): Any {
        return mapOf(
            "type" to "welcomePage",
            "logoUrl" to options.logoUrl,
            "appName" to options.appName,
            "welcomeMessage" to options.welcomeMessage,
            "subtitle" to options.subtitle,
            "onCreateRoom" to options.onCreateRoom,
            "onJoinRoom" to options.onJoinRoom,
            "containerStyle" to getContainerStyle(),
            "showQuickStart" to options.showQuickStart,
            "quickStartItems" to if (options.showQuickStart) getQuickStartItems() else emptyList()
        )
    }
}
