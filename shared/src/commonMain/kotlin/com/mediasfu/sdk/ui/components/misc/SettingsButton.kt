package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * SettingsButton - Quick access button for opening settings.
 *
 * Provides icon button that opens settings modal or menu.
 *
 * @property options Configuration options for the settings button
 */
data class SettingsButtonOptions(
    val onPress: () -> Unit,
    val icon: String = "settings",
    val size: Int = 40,
    val position: ButtonPosition = ButtonPosition.TOP_RIGHT,
    val backgroundColor: Int = 0x80000000.toInt(), // Semi-transparent black
    val iconColor: Int = 0xFFFFFFFF.toInt(), // White
    val showBadge: Boolean = false,
    val badgeCount: Int = 0,
)

enum class ButtonPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT
}

interface SettingsButton : MediaSfuUIComponent {
    val options: SettingsButtonOptions
    override val id: String get() = "settings_button"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets button position style
     */
    fun getPositionStyle(): Map<String, Any> {
        val style = mutableMapOf<String, Any>(
            "position" to "absolute",
            "zIndex" to "100"
        )
        
        when (options.position) {
            ButtonPosition.TOP_LEFT -> {
                style["top"] = "10px"
                style["left"] = "10px"
            }
            ButtonPosition.TOP_RIGHT -> {
                style["top"] = "10px"
                style["right"] = "10px"
            }
            ButtonPosition.BOTTOM_LEFT -> {
                style["bottom"] = "10px"
                style["left"] = "10px"
            }
            ButtonPosition.BOTTOM_RIGHT -> {
                style["bottom"] = "10px"
                style["right"] = "10px"
            }
        }
        
        return style
    }
    
    /**
     * Gets button style
     */
    fun getButtonStyle(): Map<String, Any> {
        return mapOf(
            "width" to "${options.size}px",
            "height" to "${options.size}px",
            "borderRadius" to "${options.size / 2}px",
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "alignItems" to "center",
            "justifyContent" to "center",
            "cursor" to "pointer",
            "border" to "none",
            "boxShadow" to "0 2px 4px rgba(0,0,0,0.2)"
        )
    }
    
    /**
     * Gets badge style if shown
     */
    fun getBadgeStyle(): Map<String, Any>? {
        if (!options.showBadge || options.badgeCount == 0) return null
        
        return mapOf(
            "position" to "absolute",
            "top" to "-5px",
            "right" to "-5px",
            "backgroundColor" to 0xFFF44336.toInt(), // Red
            "color" to 0xFFFFFFFF.toInt(),
            "borderRadius" to "10px",
            "minWidth" to "20px",
            "height" to "20px",
            "display" to "flex",
            "alignItems" to "center",
            "justifyContent" to "center",
            "fontSize" to "12px",
            "fontWeight" to "bold"
        )
    }
}

/**
 * Default implementation of SettingsButton
 */
class DefaultSettingsButton(
    override val options: SettingsButtonOptions
) : SettingsButton {
    fun render(): Any {
        return mapOf(
            "type" to "settingsButton",
            "onPress" to options.onPress,
            "icon" to options.icon,
            "size" to options.size,
            "iconColor" to options.iconColor,
            "positionStyle" to getPositionStyle(),
            "buttonStyle" to getButtonStyle(),
            "showBadge" to options.showBadge,
            "badgeCount" to options.badgeCount,
            "badgeStyle" to getBadgeStyle()
        )
    }
}
