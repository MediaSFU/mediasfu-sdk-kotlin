package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.*

/**
 * ConfirmHereModal - Presence confirmation dialog for inactive users.
 *
 * Displays "Are you still there?" prompt to confirm user presence.
 * Automatically triggers callback if not responded within timeout.
 *
 * @property options Configuration options for the confirm here modal
 */
data class ConfirmHereModalOptions(
    val isVisible: Boolean = false,
    val message: String = "Are you still there?",
    val onConfirm: () -> Unit,
    val onTimeout: () -> Unit,
    val countdownSeconds: Int = 60,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val warningColor: Int = 0xFFFF9800.toInt(), // Orange
)

interface ConfirmHereModal : MediaSfuUIComponent {
    val options: ConfirmHereModalOptions
    override val id: String get() = "confirm_here_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets modal style with warning appearance
     */
    fun getModalStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to options.backgroundColor,
            "padding" to "32px",
            "borderRadius" to "8px",
            "borderTop" to "4px solid ${options.warningColor}",
            "minWidth" to "350px",
            "textAlign" to "center",
            "boxShadow" to "0 4px 12px rgba(0,0,0,0.15)"
        )
    }
    
    /**
     * Gets countdown display style
     */
    fun getCountdownStyle(): Map<String, Any> {
        return mapOf(
            "fontSize" to "48px",
            "fontWeight" to "bold",
            "color" to options.warningColor,
            "margin" to "20px 0"
        )
    }
    
    /**
     * Starts countdown timer
     */
    fun startCountdown(scope: CoroutineScope, onTick: (Int) -> Unit) {
        scope.launch {
            var remaining = options.countdownSeconds
            while (remaining > 0) {
                onTick(remaining)
                delay(1000)
                remaining--
            }
            options.onTimeout()
        }
    }
}

/**
 * Default implementation of ConfirmHereModal
 */
class DefaultConfirmHereModal(
    override val options: ConfirmHereModalOptions
) : ConfirmHereModal {
    fun render(): Any {
        return mapOf(
            "type" to "confirmHereModal",
            "isVisible" to options.isVisible,
            "message" to options.message,
            "countdownSeconds" to options.countdownSeconds,
            "onConfirm" to options.onConfirm,
            "onTimeout" to options.onTimeout,
            "modalStyle" to getModalStyle(),
            "countdownStyle" to getCountdownStyle()
        )
    }
}
