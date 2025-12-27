package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.ControlButtonOptions

/**
 * ControlButtonsComponentTouch - Touch-optimized control buttons for mobile devices.
 *
 * Provides larger touch targets and haptic feedback for mobile interactions.
 *
 * @property options Configuration options for the touch control buttons
 */
data class ControlButtonsComponentTouchOptions(
    val buttons: List<ControlButtonOptions> = emptyList(),
    val position: String = "bottom",
    val direction: String = "horizontal",
    val showAspect: Boolean = true,
    val backgroundColor: Int = 0x80000000.toInt(),
    val buttonBackgroundColor: Int = 0x40FFFFFF.toInt(), // Semi-transparent white
    val buttonSpacing: Int = 16,
    val buttonSize: Int = 56, // Larger for touch
)

interface ControlButtonsComponentTouch : MediaSfuUIComponent {
    val options: ControlButtonsComponentTouchOptions
    override val id: String get() = "control_buttons_touch"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets button style optimized for touch
     */
    fun getTouchButtonStyle(): Map<String, Any> {
        return mapOf(
            "width" to "${options.buttonSize}px",
            "height" to "${options.buttonSize}px",
            "borderRadius" to "${options.buttonSize / 2}px",
            "backgroundColor" to options.buttonBackgroundColor,
            "display" to "flex",
            "alignItems" to "center",
            "justifyContent" to "center",
            "cursor" to "pointer",
            "minTouchTarget" to "48px" // Accessibility requirement
        )
    }
    
    /**
     * Gets container style for touch layout
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "display" to "flex",
            "flexDirection" to if (options.direction == "horizontal") "row" else "column",
            "gap" to "${options.buttonSpacing}px",
            "padding" to "16px",
            "backgroundColor" to options.backgroundColor,
            "position" to "absolute",
            options.position to "0",
            "left" to "0",
            "right" to "0"
        )
    }
}

/**
 * Default implementation of ControlButtonsComponentTouch
 */
class DefaultControlButtonsComponentTouch(
    override val options: ControlButtonsComponentTouchOptions
) : ControlButtonsComponentTouch {
    fun render(): Any {
        return mapOf(
            "type" to "controlButtonsTouch",
            "buttons" to options.buttons,
            "containerStyle" to getContainerStyle(),
            "buttonStyle" to getTouchButtonStyle(),
            "showAspect" to options.showAspect
        )
    }
}

/**
 * Composable extension for rendering ControlButtonsComponentTouch in Jetpack Compose
 */
@Composable
fun ControlButtonsComponentTouch.renderCompose() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(options.backgroundColor))
            .padding(16.dp)
    ) {
        if (options.direction == "horizontal") {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(options.buttonSpacing.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.buttons.forEach { button ->
                    TouchButton(button, options.buttonSize, options.buttonBackgroundColor)
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(options.buttonSpacing.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.buttons.forEach { button ->
                    TouchButton(button, options.buttonSize, options.buttonBackgroundColor)
                }
            }
        }
    }
}

@Composable
private fun TouchButton(button: ControlButtonOptions, size: Int, bgColor: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(bgColor))
            .clickable { button.onClick?.invoke() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Convert com.mediasfu.sdk.ui.Color to androidx.compose.ui.graphics.Color
        val activeColor = Color(button.activeColor.red, button.activeColor.green, button.activeColor.blue, button.activeColor.alpha)
        val inactiveColor = Color(button.inactiveColor.red, button.inactiveColor.green, button.inactiveColor.blue, button.inactiveColor.alpha)
        
        Icon(
            imageVector = Icons.Default.Star, // Placeholder
            contentDescription = button.id,
            tint = if (button.active) activeColor else inactiveColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}
