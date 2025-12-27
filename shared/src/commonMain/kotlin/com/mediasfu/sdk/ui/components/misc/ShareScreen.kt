package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * ShareScreen - Share meeting modal with social media and link options.
 *
 * Provides interface for sharing meeting links via various platforms
 * or copying to clipboard.
 *
 * @property options Configuration options for the share screen
 */
data class ShareScreenOptions(
    val isVisible: Boolean = false,
    val onClose: () -> Unit,
    val meetingLink: String,
    val meetingId: String,
    val onCopyLink: (() -> Unit)? = null,
    val onSharePlatform: ((String) -> Unit)? = null,
    val showQRCode: Boolean = true,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
)

data class SharePlatform(
    val name: String,
    val icon: String,
    val color: Int,
)

interface ShareScreen : MediaSfuUIComponent {
    val options: ShareScreenOptions
    override val id: String get() = "share_screen"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets available share platforms
     */
    fun getSharePlatforms(): List<SharePlatform> {
        return listOf(
            SharePlatform(
                name = "Email",
                icon = "email",
                color = 0xFFEA4335.toInt()
            ),
            SharePlatform(
                name = "WhatsApp",
                icon = "whatsapp",
                color = 0xFF25D366.toInt()
            ),
            SharePlatform(
                name = "Telegram",
                icon = "telegram",
                color = 0xFF0088CC.toInt()
            ),
            SharePlatform(
                name = "Twitter",
                icon = "twitter",
                color = 0xFF1DA1F2.toInt()
            ),
            SharePlatform(
                name = "LinkedIn",
                icon = "linkedin",
                color = 0xFF0077B5.toInt()
            ),
            SharePlatform(
                name = "Facebook",
                icon = "facebook",
                color = 0xFF1877F2.toInt()
            )
        )
    }
    
    /**
     * Handles platform share action
     */
    fun handleShare(platform: String) {
        options.onSharePlatform?.invoke(platform)
    }
    
    /**
     * Handles copy link action
     */
    fun handleCopyLink() {
        options.onCopyLink?.invoke()
    }
    
    /**
     * Gets modal container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to options.backgroundColor,
            "padding" to "32px",
            "borderRadius" to "8px",
            "maxWidth" to "500px",
            "width" to "90%"
        )
    }
}

/**
 * Default implementation of ShareScreen
 */
class DefaultShareScreen(
    override val options: ShareScreenOptions
) : ShareScreen {
    fun render(): Any {
        return mapOf(
            "type" to "shareScreen",
            "isVisible" to options.isVisible,
            "onClose" to options.onClose,
            "meetingLink" to options.meetingLink,
            "meetingId" to options.meetingId,
            "showQRCode" to options.showQRCode,
            "sharePlatforms" to getSharePlatforms(),
            "onShare" to ::handleShare,
            "onCopyLink" to ::handleCopyLink,
            "containerStyle" to getContainerStyle()
        )
    }
}
