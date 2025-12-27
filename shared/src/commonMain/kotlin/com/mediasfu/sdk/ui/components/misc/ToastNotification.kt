package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.*

/**
 * ToastNotification - Brief notification popup with auto-dismiss.
 *
 * Displays temporary notification messages that automatically disappear.
 * Supports stacking multiple toasts.
 *
 * @property options Configuration options for the toast notification
 */
data class ToastNotificationOptions(
    val isVisible: Boolean = false,
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 3000L,
    val position: ToastPosition = ToastPosition.BOTTOM_CENTER,
    val onDismiss: (() -> Unit)? = null,
)

enum class ToastType {
    SUCCESS,
    ERROR,
    WARNING,
    INFO
}

enum class ToastPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}

interface ToastNotification : MediaSfuUIComponent {
    val options: ToastNotificationOptions
    override val id: String get() = "toast_notification"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets toast background color based on type
     */
    fun getBackgroundColor(): Int {
        return when (options.type) {
            ToastType.SUCCESS -> 0xFF4CAF50.toInt() // Green
            ToastType.ERROR -> 0xFFF44336.toInt() // Red
            ToastType.WARNING -> 0xFFFF9800.toInt() // Orange
            ToastType.INFO -> 0xFF2196F3.toInt() // Blue
        }
    }
    
    /**
     * Gets toast icon based on type
     */
    fun getIcon(): String {
        return when (options.type) {
            ToastType.SUCCESS -> "check-circle"
            ToastType.ERROR -> "error"
            ToastType.WARNING -> "warning"
            ToastType.INFO -> "info"
        }
    }
    
    /**
     * Gets position style based on toast position
     */
    fun getPositionStyle(): Map<String, Any> {
        val style = mutableMapOf<String, Any>(
            "position" to "fixed",
            "zIndex" to "10000"
        )
        
        when (options.position) {
            ToastPosition.TOP_LEFT -> {
                style["top"] = "20px"
                style["left"] = "20px"
            }
            ToastPosition.TOP_CENTER -> {
                style["top"] = "20px"
                style["left"] = "50%"
                style["transform"] = "translateX(-50%)"
            }
            ToastPosition.TOP_RIGHT -> {
                style["top"] = "20px"
                style["right"] = "20px"
            }
            ToastPosition.BOTTOM_LEFT -> {
                style["bottom"] = "20px"
                style["left"] = "20px"
            }
            ToastPosition.BOTTOM_CENTER -> {
                style["bottom"] = "20px"
                style["left"] = "50%"
                style["transform"] = "translateX(-50%)"
            }
            ToastPosition.BOTTOM_RIGHT -> {
                style["bottom"] = "20px"
                style["right"] = "20px"
            }
        }
        
        return style
    }
    
    /**
     * Gets toast container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to getBackgroundColor(),
            "color" to 0xFFFFFFFF.toInt(),
            "padding" to "12px 20px",
            "borderRadius" to "4px",
            "boxShadow" to "0 2px 8px rgba(0,0,0,0.2)",
            "minWidth" to "250px",
            "maxWidth" to "400px",
            "display" to "flex",
            "alignItems" to "center",
            "gap" to "8px"
        )
    }
    
    /**
     * Starts auto-dismiss timer
     */
    fun startAutoDismiss(scope: CoroutineScope) {
        scope.launch {
            delay(options.duration)
            options.onDismiss?.invoke()
        }
    }
}

/**
 * Default implementation of ToastNotification
 */
class DefaultToastNotification(
    override val options: ToastNotificationOptions
) : ToastNotification {
    fun render(): Any {
        return mapOf(
            "type" to "toastNotification",
            "isVisible" to options.isVisible,
            "message" to options.message,
            "toastType" to options.type,
            "duration" to options.duration,
            "position" to options.position,
            "backgroundColor" to getBackgroundColor(),
            "icon" to getIcon(),
            "positionStyle" to getPositionStyle(),
            "containerStyle" to getContainerStyle(),
            "onDismiss" to options.onDismiss
        )
    }
}
