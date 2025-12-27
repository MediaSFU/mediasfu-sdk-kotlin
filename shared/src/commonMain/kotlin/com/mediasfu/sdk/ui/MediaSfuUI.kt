package com.mediasfu.sdk.ui

import com.mediasfu.sdk.ui.components.Button
import com.mediasfu.sdk.ui.components.ButtonOptions
import com.mediasfu.sdk.ui.components.ControlButtons
import com.mediasfu.sdk.ui.components.ControlButtonsOptions
import com.mediasfu.sdk.ui.components.Grid
import com.mediasfu.sdk.ui.components.GridOptions
import com.mediasfu.sdk.ui.components.Modal
import com.mediasfu.sdk.ui.components.ModalOptions
import com.mediasfu.sdk.ui.components.Text
import com.mediasfu.sdk.ui.components.TextOptions
import com.mediasfu.sdk.ui.components.VideoCard
import com.mediasfu.sdk.ui.components.VideoCardOptions
import kotlinx.coroutines.flow.StateFlow

/**
 * MediaSFU Kotlin Multiplatform UI Framework
 * 
 * This framework provides a platform-agnostic UI layer for the MediaSFU SDK,
 * allowing developers to create consistent UI experiences across Android and iOS
 * while maintaining the flexibility to customize platform-specific behaviors.
 */

/**
 * Common interface for all UI components in the MediaSFU SDK.
 * This provides a consistent base for all UI elements.
 */
interface MediaSfuUIComponent {
    /**
     * Unique identifier for this UI component.
     */
    val id: String
    
    /**
     * Whether this component is currently visible.
     */
    val isVisible: Boolean
    
    /**
     * Whether this component is currently enabled.
     */
    val isEnabled: Boolean
    
    /**
     * Show this component.
     */
    fun show()
    
    /**
     * Hide this component.
     */
    fun hide()
    
    /**
     * Enable this component.
     */
    fun enable()
    
    /**
     * Disable this component.
     */
    fun disable()
    
    /**
     * Dispose of this component and release resources.
     */
    fun dispose()
}

/**
 * Base class for all MediaSFU UI components.
 * Provides common functionality and state management.
 */
abstract class BaseMediaSfuUIComponent(
    override val id: String
) : MediaSfuUIComponent {
    
    private var _isVisible: Boolean = true
    private var _isEnabled: Boolean = true
    
    override val isVisible: Boolean get() = _isVisible
    override val isEnabled: Boolean get() = _isEnabled
    
    override fun show() {
        _isVisible = true
        onVisibilityChanged(true)
    }
    
    override fun hide() {
        _isVisible = false
        onVisibilityChanged(false)
    }
    
    override fun enable() {
        _isEnabled = true
        onEnabledChanged(true)
    }
    
    override fun disable() {
        _isEnabled = false
        onEnabledChanged(false)
    }
    
    override fun dispose() {
        _isVisible = false
        _isEnabled = false
        onDispose()
    }
    
    /**
     * Called when the visibility state changes.
     */
    protected open fun onVisibilityChanged(visible: Boolean) {}
    
    /**
     * Called when the enabled state changes.
     */
    protected open fun onEnabledChanged(enabled: Boolean) {}
    
    /**
     * Called when the component is being disposed.
     */
    protected open fun onDispose() {}
}

/**
 * Interface for components that can handle user interactions.
 */
interface InteractiveComponent {
    /**
     * Handle a user interaction event.
     */
    fun handleInteraction(event: InteractionEvent)
}

/**
 * Represents different types of user interactions.
 */
sealed class InteractionEvent {
    object Click : InteractionEvent()
    object LongPress : InteractionEvent()
    object DoubleClick : InteractionEvent()
    data class Swipe(val direction: SwipeDirection) : InteractionEvent()
    data class Pinch(val scale: Float) : InteractionEvent()
    data class Drag(val deltaX: Float, val deltaY: Float) : InteractionEvent()
}

/**
 * Represents swipe directions.
 */
enum class SwipeDirection {
    UP, DOWN, LEFT, RIGHT
}

/**
 * Interface for components that can display media content.
 */
interface MediaDisplayComponent {
    /**
     * Update the media content being displayed.
     */
    fun updateMedia(mediaStream: com.mediasfu.sdk.webrtc.MediaStream)
    
    /**
     * Clear the media content.
     */
    fun clearMedia()
    
    /**
     * Whether media is currently being displayed.
     */
    val hasMedia: StateFlow<Boolean>
}

/**
 * Interface for components that can be animated.
 */
interface AnimatedComponent {
    /**
     * Start an animation.
     */
    fun startAnimation(animation: Animation)
    
    /**
     * Stop the current animation.
     */
    fun stopAnimation()
    
    /**
     * Whether an animation is currently running.
     */
    val isAnimating: StateFlow<Boolean>
}

/**
 * Represents different types of animations.
 */
sealed class Animation {
    object FadeIn : Animation()
    object FadeOut : Animation()
    object SlideIn : Animation()
    object SlideOut : Animation()
    object Pulse : Animation()
    object Bounce : Animation()
    data class Custom(val name: String, val duration: Long) : Animation()
}

/**
 * Interface for components that can be styled.
 */
interface StylableComponent {
    /**
     * Apply a style to this component.
     */
    fun applyStyle(style: ComponentStyle)
    
    /**
     * Get the current style of this component.
     */
    val currentStyle: ComponentStyle
}

/**
 * Represents styling information for UI components.
 */
data class ComponentStyle(
    val backgroundColor: Color? = null,
    val textColor: Color? = null,
    val borderColor: Color? = null,
    val borderWidth: Float = 0f,
    val borderRadius: Float = 0f,
    val padding: EdgeInsets = EdgeInsets.zero,
    val margin: EdgeInsets = EdgeInsets.zero,
    val fontSize: Float = 14f,
    val fontWeight: FontWeight = FontWeight.Normal,
    val opacity: Float = 1f,
    val shadow: Shadow? = null
)

/**
 * Represents a color in the UI system.
 */
data class Color(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f
) {
    companion object {
        val Black = Color(0f, 0f, 0f)
        val White = Color(1f, 1f, 1f)
        val Red = Color(1f, 0f, 0f)
        val Green = Color(0f, 1f, 0f)
        val Blue = Color(0f, 0f, 1f)
        val Gray = Color(0.5f, 0.5f, 0.5f)
        val Transparent = Color(0f, 0f, 0f, 0f)
    }
}

/**
 * Represents edge insets for padding and margins.
 */
data class EdgeInsets(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f
) {
    companion object {
        val zero = EdgeInsets()
        
        fun all(value: Float) = EdgeInsets(value, value, value, value)
        fun symmetric(horizontal: Float = 0f, vertical: Float = 0f) = 
            EdgeInsets(vertical, horizontal, vertical, horizontal)
        fun only(top: Float = 0f, right: Float = 0f, bottom: Float = 0f, left: Float = 0f) = 
            EdgeInsets(top, right, bottom, left)
    }
}

/**
 * Represents font weight options.
 */
enum class FontWeight {
    Thin, ExtraLight, Light, Normal, Medium, SemiBold, Bold, ExtraBold, Black
}

/**
 * Represents shadow information for UI components.
 */
data class Shadow(
    val color: Color,
    val offsetX: Float,
    val offsetY: Float,
    val blurRadius: Float
)

/**
 * Interface for components that can be positioned.
 */
interface PositionableComponent {
    /**
     * Set the position of this component.
     */
    fun setPosition(x: Float, y: Float)
    
    /**
     * Get the current position of this component.
     */
    val position: Pair<Float, Float>
    
    /**
     * Set the size of this component.
     */
    fun setSize(width: Float, height: Float)
    
    /**
     * Get the current size of this component.
     */
    val size: Pair<Float, Float>
}

/**
 * Interface for components that can be laid out in a container.
 */
interface LayoutComponent {
    /**
     * Add a child component to this layout.
     */
    fun addChild(component: MediaSfuUIComponent)
    
    /**
     * Remove a child component from this layout.
     */
    fun removeChild(component: MediaSfuUIComponent)
    
    /**
     * Get all child components.
     */
    val children: StateFlow<List<MediaSfuUIComponent>>
    
    /**
     * Clear all child components.
     */
    fun clearChildren()
}

/**
 * Represents different layout directions.
 */
enum class LayoutDirection {
    Horizontal, Vertical
}

/**
 * Represents different alignment options.
 */
enum class Alignment {
    Start, Center, End, SpaceBetween, SpaceAround, SpaceEvenly
}

/**
 * Factory for creating UI components.
 * This provides a centralized way to create UI components with consistent configuration.
 */
object MediaSfuUIFactory {
    /**
     * Create a video card component.
     */
    fun createVideoCard(options: VideoCardOptions): VideoCard {
        return VideoCard(options)
    }
    
    /**
     * Create a control buttons component.
     */
    fun createControlButtons(options: ControlButtonsOptions): ControlButtons {
        return ControlButtons(options)
    }
    
    /**
     * Create a modal component.
     */
    fun createModal(options: ModalOptions): Modal {
        return Modal(options)
    }
    
    /**
     * Create a grid layout component.
     */
    fun createGrid(options: GridOptions): Grid {
        return Grid(options)
    }
    
    /**
     * Create a text component.
     */
    fun createText(options: TextOptions): Text {
        return Text(options)
    }
    
    /**
     * Create a button component.
     */
    fun createButton(options: ButtonOptions): Button {
        return Button(options)
    }
}
