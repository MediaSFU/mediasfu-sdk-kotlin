package com.mediasfu.sdk.ui.components

import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Modal - A UI component for displaying modal dialogs and overlays.
 * 
 * This component provides:
 * - Modal dialog functionality
 * - Backdrop/overlay support
 * - Customizable content
 * - Animation support (fade in/out, slide in/out)
 * - Dismissible by backdrop click or close button
 * - Responsive sizing and positioning
 */
class Modal(
    private val options: ModalOptions
) : BaseMediaSfuUIComponent("modal_${options.id}"),
    LayoutComponent,
    InteractiveComponent,
    AnimatedComponent,
    StylableComponent {
    
    private val _children = MutableStateFlow<List<MediaSfuUIComponent>>(emptyList())
    override val children: StateFlow<List<MediaSfuUIComponent>> = _children.asStateFlow()
    
    private val _isOpen = MutableStateFlow(options.isOpen)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()
    
    private val _isAnimating = MutableStateFlow(false)
    override val isAnimating: StateFlow<Boolean> = _isAnimating.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle
    
    private var currentAnimation: Animation? = null
    
    init {
        // Initialize with content if provided
        options.content?.let { content ->
            addChild(content)
        }
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
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    override fun startAnimation(animation: Animation) {
        currentAnimation = animation
        _isAnimating.value = true
        
        when (animation) {
            is Animation.FadeIn -> startFadeInAnimation()
            is Animation.FadeOut -> startFadeOutAnimation()
            is Animation.SlideIn -> startSlideInAnimation()
            is Animation.SlideOut -> startSlideOutAnimation()
            else -> {
                // Handle other animation types
                _isAnimating.value = false
            }
        }
    }
    
    override fun stopAnimation() {
        currentAnimation = null
        _isAnimating.value = false
        stopCurrentAnimation()
    }
    
    override fun handleInteraction(event: InteractionEvent) {
        when (event) {
            is InteractionEvent.Click -> {
                if (options.dismissible && event is InteractionEvent.Click) {
                    // Check if click was on backdrop
                    handleBackdropClick()
                }
            }
            else -> {
                // Handle other interaction types
            }
        }
    }
    
    /**
     * Open the modal.
     */
    fun open() {
        if (!_isOpen.value) {
            _isOpen.value = true
            show()
            onModalOpened()
            
            if (options.animateOnOpen) {
                startAnimation(Animation.FadeIn)
            }
        }
    }
    
    /**
     * Close the modal.
     */
    fun close() {
        if (_isOpen.value) {
            if (options.animateOnClose) {
                startAnimation(Animation.FadeOut)
                // Close after animation completes
                // This would typically be handled by the animation completion callback
                _isOpen.value = false
                hide()
            } else {
                _isOpen.value = false
                hide()
            }
            onModalClosed()
        }
    }
    
    /**
     * Toggle the modal open/close state.
     */
    fun toggle() {
        if (_isOpen.value) {
            close()
        } else {
            open()
        }
    }
    
    /**
     * Set the modal content.
     */
    fun setContent(content: MediaSfuUIComponent) {
        clearChildren()
        addChild(content)
        onContentSet(content)
    }
    
    /**
     * Get the current modal content.
     */
    fun getContent(): MediaSfuUIComponent? = _children.value.firstOrNull()
    
    /**
     * Set the modal size.
     */
    fun setSize(width: Float, height: Float) {
        onSizeSet(width, height)
    }
    
    /**
     * Set the modal position.
     */
    fun setPosition(x: Float, y: Float) {
        onPositionSet(x, y)
    }
    
    // Private methods for internal functionality
    
    private fun handleBackdropClick() {
        if (options.dismissible) {
            close()
            options.onDismiss?.invoke()
        }
    }
    
    private fun onModalOpened() {
        // Platform-specific modal open logic
        platformOnModalOpened()
    }
    
    private fun onModalClosed() {
        // Platform-specific modal close logic
        platformOnModalClosed()
    }
    
    private fun onContentSet(content: MediaSfuUIComponent) {
        // Platform-specific content set logic
        platformOnContentSet(content)
    }
    
    private fun onSizeSet(width: Float, height: Float) {
        // Platform-specific size set logic
        platformOnSizeSet(width, height)
    }
    
    private fun onPositionSet(x: Float, y: Float) {
        // Platform-specific position set logic
        platformOnPositionSet(x, y)
    }
    
    private fun onChildAdded(component: MediaSfuUIComponent) {
        // Platform-specific child addition logic
        platformAddChild(component)
    }
    
    private fun onChildRemoved(component: MediaSfuUIComponent) {
        // Platform-specific child removal logic
        platformRemoveChild(component)
    }
    
    private fun onChildrenCleared() {
        // Platform-specific children clear logic
        platformClearChildren()
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    private fun startFadeInAnimation() {
        // Start fade in animation
        // Platform-specific implementation
    }
    
    private fun startFadeOutAnimation() {
        // Start fade out animation
        // Platform-specific implementation
    }
    
    private fun startSlideInAnimation() {
        // Start slide in animation
        // Platform-specific implementation
    }
    
    private fun startSlideOutAnimation() {
        // Start slide out animation
        // Platform-specific implementation
    }
    
    private fun stopCurrentAnimation() {
        // Stop current animation
        // Platform-specific implementation
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformOnModalOpened() {
        // Platform-specific modal open logic
    }
    
    private fun platformOnModalClosed() {
        // Platform-specific modal close logic
    }
    
    private fun platformOnContentSet(content: MediaSfuUIComponent) {
        // Platform-specific content set logic
    }
    
    private fun platformOnSizeSet(width: Float, height: Float) {
        // Platform-specific size set logic
    }
    
    private fun platformOnPositionSet(x: Float, y: Float) {
        // Platform-specific position set logic
    }
    
    private fun platformAddChild(component: MediaSfuUIComponent) {
        // Platform-specific child addition
    }
    
    private fun platformRemoveChild(component: MediaSfuUIComponent) {
        // Platform-specific child removal
    }
    
    private fun platformClearChildren() {
        // Platform-specific children clear
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
    
    override fun onDispose() {
        super.onDispose()
        clearChildren()
        currentAnimation = null
        _isOpen.value = false
    }
}

/**
 * Configuration options for the Modal component.
 */
data class ModalOptions(
    val id: String,
    val content: MediaSfuUIComponent? = null,
    val isOpen: Boolean = false,
    val style: ComponentStyle = ComponentStyle(),
    val size: ModalSize = ModalSize.Auto,
    val position: ModalPosition = ModalPosition.Center,
    val dismissible: Boolean = true,
    val showBackdrop: Boolean = true,
    val backdropColor: Color = Color(0f, 0f, 0f, 0.5f),
    val animateOnOpen: Boolean = true,
    val animateOnClose: Boolean = true,
    val animationDuration: Long = 300L,
    val borderRadius: Float = 8f,
    val backgroundColor: Color = Color.White,
    val padding: EdgeInsets = EdgeInsets.all(16f),
    val margin: EdgeInsets = EdgeInsets.zero,
    val shadow: Shadow? = Shadow(
        color = Color(0f, 0f, 0f, 0.3f),
        offsetX = 0f,
        offsetY = 4f,
        blurRadius = 8f
    ),
    val onOpen: (() -> Unit)? = null,
    val onClose: (() -> Unit)? = null,
    val onDismiss: (() -> Unit)? = null
)

/**
 * Represents different modal sizes.
 */
sealed class ModalSize {
    object Auto : ModalSize()
    object Small : ModalSize()
    object Medium : ModalSize()
    object Large : ModalSize()
    object FullScreen : ModalSize()
    data class Custom(val width: Float, val height: Float) : ModalSize()
}

/**
 * Represents different modal positions.
 */
sealed class ModalPosition {
    object Center : ModalPosition()
    object Top : ModalPosition()
    object Bottom : ModalPosition()
    object Left : ModalPosition()
    object Right : ModalPosition()
    object TopLeft : ModalPosition()
    object TopRight : ModalPosition()
    object BottomLeft : ModalPosition()
    object BottomRight : ModalPosition()
    data class Custom(val x: Float, val y: Float) : ModalPosition()
}
