package com.mediasfu.sdk.ui.components

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Button - A UI component for interactive buttons.
 * 
 * This component provides:
 * - Click, long press, and double click interactions
 * - Customizable styling and appearance
 * - Support for icons and text
 * - Different button states (normal, pressed, disabled)
 * - Loading state support
 * - Custom button shapes and sizes
 */
class Button(
    private val options: ButtonOptions
) : BaseMediaSfuUIComponent("button_${options.id}"),
    InteractiveComponent,
    StylableComponent {
    
    private val _isPressed = MutableStateFlow(false)
    val isPressed: StateFlow<Boolean> = _isPressed.asStateFlow()
    
    private val _isLoading = MutableStateFlow(options.isLoading)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle = _currentStyle
    
    override fun handleInteraction(event: InteractionEvent) {
        if (!isEnabled || _isLoading.value) return
        
        when (event) {
            is InteractionEvent.Click -> {
                _isPressed.value = true
                options.onClick?.invoke()
                // Reset pressed state after a short delay
                // This would typically be handled by the platform-specific implementation
                _isPressed.value = false
            }
            is InteractionEvent.LongPress -> {
                options.onLongPress?.invoke()
            }
            is InteractionEvent.DoubleClick -> {
                options.onDoubleClick?.invoke()
            }
            else -> {
                // Handle other interaction types
            }
        }
    }
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    /**
     * Set the loading state of the button.
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
        onLoadingStateChanged(loading)
    }
    
    /**
     * Set the button text.
     */
    fun setText(text: String) {
        onTextChanged(text)
    }
    
    /**
     * Set the button icon.
     */
    fun setIcon(icon: String) {
        onIconChanged(icon)
    }
    
    /**
     * Get the current button text.
     */
    fun getText(): String? = options.text
    
    /**
     * Get the current button icon.
     */
    fun getIcon(): String? = options.icon
    
    /**
     * Trigger the button click programmatically.
     */
    fun click() {
        if (isEnabled && !_isLoading.value) {
            options.onClick?.invoke()
        }
    }
    
    /**
     * Get the current button state.
     */
    fun getState(): ButtonState {
        return when {
            !isEnabled -> ButtonState.Disabled
            _isLoading.value -> ButtonState.Loading
            _isPressed.value -> ButtonState.Pressed
            else -> ButtonState.Normal
        }
    }
    
    // Private methods for internal functionality
    
    private fun onLoadingStateChanged(loading: Boolean) {
        // Platform-specific loading state change logic
        platformOnLoadingStateChanged(loading)
    }
    
    private fun onTextChanged(text: String) {
        // Platform-specific text change logic
        platformOnTextChanged(text)
    }
    
    private fun onIconChanged(icon: String) {
        // Platform-specific icon change logic
        platformOnIconChanged(icon)
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformOnLoadingStateChanged(loading: Boolean) {
        // Platform-specific loading state change logic
    }
    
    private fun platformOnTextChanged(text: String) {
        // Platform-specific text change logic
    }
    
    private fun platformOnIconChanged(icon: String) {
        // Platform-specific icon change logic
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
}

/**
 * Represents different button states.
 */
enum class ButtonState {
    Normal, Pressed, Disabled, Loading
}

/**
 * Configuration options for the Button component.
 */
data class ButtonOptions(
    val id: String,
    val text: String? = null,
    val icon: String? = null,
    val style: ComponentStyle = ComponentStyle(),
    val buttonType: ButtonType = ButtonType.Primary,
    val size: ButtonSize = ButtonSize.Medium,
    val shape: ButtonShape = ButtonShape.Rounded,
    val isLoading: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val backgroundColor: Color = Color.Blue,
    val textColor: Color = Color.White,
    val iconColor: Color = Color.White,
    val disabledBackgroundColor: Color = Color.Gray,
    val disabledTextColor: Color = Color(0.7f, 0.7f, 0.7f),
    val pressedBackgroundColor: Color = Color(0.8f, 0.8f, 1f),
    val loadingColor: Color = Color.White,
    val borderRadius: Float = 8f,
    val padding: EdgeInsets = EdgeInsets.symmetric(horizontal = 16f, vertical = 8f),
    val margin: EdgeInsets = EdgeInsets.zero,
    val minWidth: Float = 0f,
    val minHeight: Float = 0f,
    val shadow: Shadow? = null,
    val onClick: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onDoubleClick: (() -> Unit)? = null,
    val customComponent: MediaSfuUIComponent? = null
)

/**
 * Represents different button types.
 */
enum class ButtonType {
    Primary, Secondary, Success, Danger, Warning, Info, Light, Dark
}

/**
 * Represents different button sizes.
 */
enum class ButtonSize {
    Small, Medium, Large, ExtraLarge
}

/**
 * Represents different button shapes.
 */
enum class ButtonShape {
    Rectangle, Rounded, Circle, Pill
}
