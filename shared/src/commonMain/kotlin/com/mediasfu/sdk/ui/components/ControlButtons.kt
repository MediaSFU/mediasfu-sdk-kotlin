package com.mediasfu.sdk.ui.components

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ControlButtons - A UI component for displaying a collection of interactive control buttons.
 * 
 * This component provides:
 * - Flexible button layout (horizontal or vertical)
 * - Customizable button appearance and behavior
 * - Support for icons, text, and custom components
 * - Active/inactive states with different colors
 * - Disabled state support
 * - Click, long press, and double click interactions
 */
class ControlButtons(
    private val options: ControlButtonsOptions
) : BaseMediaSfuUIComponent("control_buttons_${options.id}"),
    LayoutComponent,
    StylableComponent {
    val buttons: List<ControlButtonOptions> get() = options.buttons
    val layoutDirection: LayoutDirection get() = options.layoutDirection
    val spacing: Float get() = options.spacing
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle

    private val _children = MutableStateFlow(options.buttons.mapNotNull { it.customComponent })
    override val children: StateFlow<List<MediaSfuUIComponent>> = _children.asStateFlow()

    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }

    override fun addChild(component: MediaSfuUIComponent) {
        _children.value = _children.value + component
        onChildAdded(component)
    }

    override fun removeChild(component: MediaSfuUIComponent) {
        _children.value = _children.value - component
        onChildRemoved(component)
    }

    override fun clearChildren() {
        _children.value.forEach { it.dispose() }
        _children.value = emptyList()
        onChildrenCleared()
    }

    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style handling hook
    }

    private fun onChildAdded(component: MediaSfuUIComponent) {
        // Platform-specific child handling hook
    }

    private fun onChildRemoved(component: MediaSfuUIComponent) {
        // Platform-specific child handling hook
    }

    private fun onChildrenCleared() {
        // Platform-specific child handling hook
    }
}

/**
 * Individual control button component.
 */
class ControlButton(
    private val options: ControlButtonOptions
) : BaseMediaSfuUIComponent("control_button_${options.id}"),
    InteractiveComponent,
    StylableComponent {
    
    private var _isActive = MutableStateFlow(options.active)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle
    
    override fun handleInteraction(event: InteractionEvent) {
        if (!isEnabled) return
        
        when (event) {
            is InteractionEvent.Click -> {
                if (options.onClick != null) {
                    options.onClick!!()
                }
            }
            is InteractionEvent.LongPress -> {
                if (options.onLongPress != null) {
                    options.onLongPress!!()
                }
            }
            is InteractionEvent.DoubleClick -> {
                if (options.onDoubleClick != null) {
                    options.onDoubleClick!!()
                }
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
     * Set the active state of this button.
     */
    fun setActive(active: Boolean) {
        _isActive.value = active
        onActiveStateChanged(active)
    }
    
    /**
     * Toggle the active state of this button.
     */
    fun toggleActive() {
        setActive(!_isActive.value)
    }
    
    /**
     * Get the button's display text.
     */
    fun getText(): String? = options.text
    
    /**
     * Get the button's icon name.
     */
    fun getIcon(): String? = options.icon
    
    /**
     * Get the button's alternate icon name (for active state).
     */
    fun getAlternateIcon(): String? = options.alternateIcon
    
    // Private methods for internal functionality
    
    private fun onActiveStateChanged(active: Boolean) {
        // Platform-specific active state change logic
        platformSetActiveState(active)
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformSetActiveState(active: Boolean) {
        // Platform-specific active state change
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
}

/**
 * Configuration options for the ControlButtons component.
 */
data class ControlButtonsOptions(
    val id: String = "default",
    val buttons: List<ControlButtonOptions>,
    val layoutDirection: LayoutDirection = LayoutDirection.Horizontal,
    val alignment: Alignment = Alignment.Center,
    val style: ComponentStyle = ComponentStyle(),
    val spacing: Float = 8f,
    val padding: EdgeInsets = EdgeInsets.all(8f),
    val backgroundColor: Color = Color.Transparent,
    val borderRadius: Float = 8f,
    val buttonBackgroundColor: Color = Color.Transparent,
    val activeButtonBackgroundColor: Color = Color(0.2f, 0.2f, 0.2f, 0.8f)
)

/**
 * Configuration options for individual control buttons.
 */
data class ControlButtonOptions(
    val id: String,
    val text: String? = null,
    val icon: String? = null,
    val alternateIcon: String? = null,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val style: ComponentStyle = ComponentStyle(),
    val textColor: Color = Color.White,
    val activeColor: Color = Color.Blue,
    val inactiveColor: Color = Color.Gray,
    val backgroundColor: Color = Color.Transparent,
    val activeBackgroundColor: Color = Color(0.2f, 0.2f, 0.2f, 0.8f),
    val borderRadius: Float = 8f,
    val padding: EdgeInsets = EdgeInsets.all(8f),
    val onClick: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onDoubleClick: (() -> Unit)? = null,
    val customComponent: MediaSfuUIComponent? = null
)
