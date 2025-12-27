package com.mediasfu.sdk.ui.components.menu

import com.mediasfu.sdk.ui.*

/**
 * Data classes for button types
 */
data class ShareButton(
    val icon: String,
    val action: () -> Unit,
    val show: Boolean = true,
)

data class MenuButton(
    val icon: String,
    val text: String,
    val action: () -> Unit,
)

/**
 * MeetingIdComponent - Displays meeting ID with copy functionality.
 */
data class MeetingIdComponentOptions(
    val meetingID: String,
    val onCopy: ((String) -> Unit)? = null,
)

interface MeetingIdComponent : MediaSfuUIComponent {
    val options: MeetingIdComponentOptions
    override val id: String get() = "meeting_id"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
}

class DefaultMeetingIdComponent(
    override val options: MeetingIdComponentOptions
) : MeetingIdComponent {
    fun render(): Any {
        return mapOf(
            "type" to "meetingId",
            "meetingID" to options.meetingID,
            "onCopy" to options.onCopy
        )
    }
}

/**
 * MeetingPasscodeComponent - Displays meeting passcode with copy functionality.
 */
data class MeetingPasscodeComponentOptions(
    val passcode: String,
    val onCopy: ((String) -> Unit)? = null,
)

interface MeetingPasscodeComponent : MediaSfuUIComponent {
    val options: MeetingPasscodeComponentOptions
    override val id: String get() = "meeting_passcode"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
}

class DefaultMeetingPasscodeComponent(
    override val options: MeetingPasscodeComponentOptions
) : MeetingPasscodeComponent {
    fun render(): Any {
        return mapOf(
            "type" to "meetingPasscode",
            "passcode" to options.passcode,
            "onCopy" to options.onCopy
        )
    }
}

/**
 * ShareButtonsComponent - Buttons for sharing meeting details.
 */
data class ShareButtonsComponentOptions(
    val buttons: List<ShareButton>,
    val meetingID: String,
)

interface ShareButtonsComponent : MediaSfuUIComponent {
    val options: ShareButtonsComponentOptions
    override val id: String get() = "share_buttons"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
}

class DefaultShareButtonsComponent(
    override val options: ShareButtonsComponentOptions
) : ShareButtonsComponent {
    fun render(): Any {
        return mapOf(
            "type" to "shareButtons",
            "buttons" to options.buttons,
            "meetingID" to options.meetingID
        )
    }
}

/**
 * CustomButtons - Custom action buttons for menu.
 */
data class CustomButtonsOptions(
    val buttons: List<MenuButton>,
)

interface CustomButtons : MediaSfuUIComponent {
    val options: CustomButtonsOptions
    override val id: String get() = "custom_buttons"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
}

class DefaultCustomButtons(
    override val options: CustomButtonsOptions
) : CustomButtons {
    fun render(): Any {
        return mapOf(
            "type" to "customButtons",
            "buttons" to options.buttons
        )
    }
}

/**
 * MenuItemComponent - Individual menu item.
 */
data class MenuItemComponentOptions(
    val icon: String,
    val text: String,
    val onPress: () -> Unit,
)

interface MenuItemComponent : MediaSfuUIComponent {
    val options: MenuItemComponentOptions
    override val id: String get() = "menu_item"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
}

class DefaultMenuItemComponent(
    override val options: MenuItemComponentOptions
) : MenuItemComponent {
    fun render(): Any {
        return mapOf(
            "type" to "menuItem",
            "icon" to options.icon,
            "text" to options.text,
            "onPress" to options.onPress
        )
    }
}
