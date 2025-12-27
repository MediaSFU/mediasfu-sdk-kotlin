package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * GenericAlert - Flexible alert component for various notification types.
 *
 * Supports multiple alert types (success, error, warning, info) with
 * customizable actions and icons.
 *
 * @property options Configuration options for the generic alert
 */
data class GenericAlertOptions(
    val isVisible: Boolean = false,
    val type: AlertType = AlertType.INFO,
    val title: String = "",
    val message: String,
    val actions: List<AlertAction> = emptyList(),
    val onClose: (() -> Unit)? = null,
    val showCloseButton: Boolean = true,
    val duration: Long? = null, // null = no auto-dismiss
    val position: String = "top-center",
)

enum class AlertType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

data class AlertAction(
    val text: String,
    val onPress: () -> Unit,
    val isPrimary: Boolean = false,
)

interface GenericAlert : MediaSfuUIComponent {
    val options: GenericAlertOptions
    override val id: String get() = "generic_alert"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets alert color based on type
     */
    fun getAlertColor(): Int {
        return when (options.type) {
            AlertType.SUCCESS -> 0xFF4CAF50.toInt() // Green
            AlertType.ERROR -> 0xFFF44336.toInt() // Red
            AlertType.WARNING -> 0xFFFF9800.toInt() // Orange
            AlertType.INFO -> 0xFF2196F3.toInt() // Blue
        }
    }
    
    /**
     * Gets alert icon based on type
     */
    fun getAlertIcon(): String {
        return when (options.type) {
            AlertType.SUCCESS -> "check-circle"
            AlertType.ERROR -> "error"
            AlertType.WARNING -> "warning"
            AlertType.INFO -> "info"
        }
    }
    
    /**
     * Gets alert container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to 0xFFFFFFFF.toInt(),
            "borderLeft" to "4px solid ${getAlertColor()}",
            "padding" to "16px",
            "borderRadius" to "4px",
            "boxShadow" to "0 2px 8px rgba(0,0,0,0.15)",
            "minWidth" to "300px",
            "maxWidth" to "500px"
        )
    }
    
    /**
     * Gets position style based on position option
     */
    fun getPositionStyle(): Map<String, Any> {
        val parts = options.position.split("-")
        val vertical = parts.getOrNull(0) ?: "top"
        val horizontal = parts.getOrNull(1) ?: "center"
        
        val style = mutableMapOf<String, Any>(
            "position" to "fixed",
            "zIndex" to "1000"
        )
        
        when (vertical) {
            "top" -> style["top"] = "20px"
            "bottom" -> style["bottom"] = "20px"
            "center" -> {
                style["top"] = "50%"
                style["transform"] = "translateY(-50%)"
            }
        }
        
        when (horizontal) {
            "left" -> style["left"] = "20px"
            "right" -> style["right"] = "20px"
            "center" -> {
                style["left"] = "50%"
                style["transform"] = "translateX(-50%)"
            }
        }
        
        return style
    }
}

/**
 * Default implementation of GenericAlert
 */
class DefaultGenericAlert(
    override val options: GenericAlertOptions
) : GenericAlert {
    fun render(): Any {
        return mapOf(
            "type" to "genericAlert",
            "isVisible" to options.isVisible,
            "alertType" to options.type,
            "title" to options.title,
            "message" to options.message,
            "actions" to options.actions,
            "onClose" to options.onClose,
            "showCloseButton" to options.showCloseButton,
            "duration" to options.duration,
            "alertColor" to getAlertColor(),
            "alertIcon" to getAlertIcon(),
            "containerStyle" to getContainerStyle(),
            "positionStyle" to getPositionStyle()
        )
    }
}
