package com.mediasfu.sdk.ui.components.menu

import com.mediasfu.sdk.ui.*

/**
 * MenuModal - Modal menu for additional options and settings.
 *
 * Provides menu interface with various action items.
 *
 * @property options Configuration options for the menu modal
 */
data class MenuModalOptions(
    val isVisible: Boolean = false,
    val onClose: () -> Unit,
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val position: String = "topRight",
    val customButtons: List<MenuButton> = emptyList(),
    val shareButtons: List<ShareButton> = emptyList(),
    val roomName: String = "",
    val adminPasscode: String = "",
    val islevel: String = "1",
)

// MenuButton and ShareButton are defined in MenuComponents.kt

interface MenuModal : MediaSfuUIComponent {
    val options: MenuModalOptions
    override val id: String get() = "menu_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
}

/**
 * Default implementation of MenuModal
 */
class DefaultMenuModal(
    override val options: MenuModalOptions
) : MenuModal {
    fun render(): Any {
        return mapOf(
            "type" to "menuModal",
            "isVisible" to options.isVisible,
            "onClose" to options.onClose,
            "backgroundColor" to options.backgroundColor,
            "position" to options.position,
            "customButtons" to options.customButtons,
            "shareButtons" to options.shareButtons,
            "roomName" to options.roomName,
            "adminPasscode" to options.adminPasscode,
            "islevel" to options.islevel
        )
    }
}
