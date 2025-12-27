package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * ConfirmationModal - Generic confirmation dialog for user actions.
 *
 * Displays a confirmation message with confirm/cancel buttons.
 *
 * @property options Configuration options for the confirmation modal
 */
data class ConfirmationModalOptions(
    val isVisible: Boolean = false,
    val title: String = "Confirm Action",
    val message: String,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel",
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val confirmColor: Int = 0xFF4CAF50.toInt(), // Green
    val cancelColor: Int = 0xFFF44336.toInt(), // Red
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val isDangerous: Boolean = false,
)

interface ConfirmationModal : MediaSfuUIComponent {
    val options: ConfirmationModalOptions
    override val id: String get() = "confirmation_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets modal style based on danger level
     */
    fun getModalStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to options.backgroundColor,
            "padding" to "24px",
            "borderRadius" to "8px",
            "minWidth" to "300px",
            "maxWidth" to "500px",
            "boxShadow" to "0 4px 6px rgba(0,0,0,0.1)"
        )
    }
    
    /**
     * Gets confirm button style
     */
    fun getConfirmButtonStyle(): Map<String, Any> {
        val color = if (options.isDangerous) options.cancelColor else options.confirmColor
        return mapOf(
            "backgroundColor" to color,
            "color" to 0xFFFFFFFF.toInt(),
            "padding" to "10px 20px",
            "borderRadius" to "4px",
            "border" to "none",
            "cursor" to "pointer",
            "fontWeight" to "bold"
        )
    }
    
    /**
     * Gets cancel button style
     */
    fun getCancelButtonStyle(): Map<String, Any> {
        return mapOf(
            "backgroundColor" to 0xFFE0E0E0.toInt(), // Gray
            "color" to 0xFF000000.toInt(),
            "padding" to "10px 20px",
            "borderRadius" to "4px",
            "border" to "none",
            "cursor" to "pointer"
        )
    }
}

/**
 * Default implementation of ConfirmationModal
 */
class DefaultConfirmationModal(
    override val options: ConfirmationModalOptions
) : ConfirmationModal {
    fun render(): Any {
        return mapOf(
            "type" to "confirmationModal",
            "isVisible" to options.isVisible,
            "title" to options.title,
            "message" to options.message,
            "confirmText" to options.confirmText,
            "cancelText" to options.cancelText,
            "onConfirm" to options.onConfirm,
            "onCancel" to options.onCancel,
            "isDangerous" to options.isDangerous,
            "modalStyle" to getModalStyle(),
            "confirmButtonStyle" to getConfirmButtonStyle(),
            "cancelButtonStyle" to getCancelButtonStyle()
        )
    }
}
