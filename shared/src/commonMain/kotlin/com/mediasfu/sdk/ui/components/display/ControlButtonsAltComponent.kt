package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.*

/**
 * ControlButtonsAltComponent - Alternative control buttons layout for media controls.
 *
 * Provides compact control buttons with alternative styling and positioning.
 *
 * @property options Configuration options for the alternative control buttons
 */
data class ControlButtonsAltComponentOptions(
    val buttons: List<ControlButton> = emptyList(),
    val position: String = "bottom",
    val location: String = "center",
    val direction: String = "horizontal",
    val showAspect: Boolean = true,
    val backgroundColor: Int = 0x80000000.toInt(), // Semi-transparent black
)

data class ControlButton(
    val icon: String,
    val active: Boolean = false,
    val onPress: () -> Unit,
    val activeColor: Int = 0xFF4CAF50.toInt(), // Green
    val inactiveColor: Int = 0xFFFFFFFF.toInt(), // White
    val disabled: Boolean = false,
    val customComponent: MediaSfuUIComponent? = null,
    val iconComponent: MediaSfuUIComponent? = null,
    val alternateIconComponent: MediaSfuUIComponent? = null,
    val name: String = "",
)

interface ControlButtonsAltComponent : MediaSfuUIComponent {
    val options: ControlButtonsAltComponentOptions
    override val id: String get() = "control_buttons_alt"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets container style based on position and direction
     */
    fun getContainerStyle(): Map<String, Any> {
        val style = mutableMapOf<String, Any>(
            "display" to "flex",
            "flexDirection" to if (options.direction == "horizontal") "row" else "column",
            "justifyContent" to when (options.location) {
                "start" -> "flex-start"
                "end" -> "flex-end"
                else -> "center"
            },
            "alignItems" to "center",
            "backgroundColor" to options.backgroundColor,
            "padding" to "8px",
            "gap" to "8px"
        )
        
        when (options.position) {
            "top" -> {
                style["position"] = "absolute"
                style["top"] = "0"
                style["left"] = "0"
                style["right"] = "0"
            }
            "bottom" -> {
                style["position"] = "absolute"
                style["bottom"] = "0"
                style["left"] = "0"
                style["right"] = "0"
            }
            "left" -> {
                style["position"] = "absolute"
                style["left"] = "0"
                style["top"] = "0"
                style["bottom"] = "0"
            }
            "right" -> {
                style["position"] = "absolute"
                style["right"] = "0"
                style["top"] = "0"
                style["bottom"] = "0"
            }
        }
        
        return style
    }
}

/**
 * Default implementation of ControlButtonsAltComponent
 */
class DefaultControlButtonsAltComponent(
    override val options: ControlButtonsAltComponentOptions
) : ControlButtonsAltComponent {
    fun render(): Any {
        return mapOf(
            "type" to "controlButtonsAlt",
            "buttons" to options.buttons,
            "containerStyle" to getContainerStyle(),
            "showAspect" to options.showAspect
        )
    }
}

/**
 * Composable extension for rendering ControlButtonsAltComponent in Jetpack Compose
 */
@Composable
fun ControlButtonsAltComponent.renderCompose() {
    val arrangement = if (options.direction == "horizontal") {
        Arrangement.spacedBy(8.dp)
    } else {
        Arrangement.spacedBy(8.dp)
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(options.backgroundColor))
            .padding(8.dp)
    ) {
        if (options.direction == "horizontal") {
            Row(
                modifier = Modifier.align(
                    when (options.location) {
                        "start" -> Alignment.CenterStart
                        "end" -> Alignment.CenterEnd
                        else -> Alignment.Center
                    }
                ),
                horizontalArrangement = arrangement,
                verticalAlignment = Alignment.CenterVertically
            ) {
                options.buttons.forEach { button ->
                    ControlButtonItem(button)
                }
            }
        } else {
            Column(
                modifier = Modifier.align(
                    when (options.location) {
                        "start" -> Alignment.TopCenter
                        "end" -> Alignment.BottomCenter
                        else -> Alignment.Center
                    }
                ),
                verticalArrangement = arrangement,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.buttons.forEach { button ->
                    ControlButtonItem(button)
                }
            }
        }
    }
}

@Composable
private fun ControlButtonItem(button: ControlButton) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(enabled = !button.disabled) { button.onPress() }
            .padding(8.dp)
    ) {
        if (button.customComponent != null) {
            // Use custom component if provided
        } else {
            Icon(
                imageVector = Icons.Default.Star, // Placeholder icon
                contentDescription = button.name,
                tint = if (button.active) Color(button.activeColor) else Color(button.inactiveColor),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
