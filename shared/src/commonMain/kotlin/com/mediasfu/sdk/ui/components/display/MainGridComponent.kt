package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * MainGridComponent - Main grid layout for displaying multiple participants.
 *
 * Manages the primary grid view showing active participants in a meeting.
 *
 * @property options Configuration options for the main grid
 */
data class MainGridComponentOptions(
    val height: Int,
    val width: Int,
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val children: List<MediaSfuUIComponent> = emptyList(),
    val mainSize: Double = 0.0,
    val showAspect: Boolean = true,
    val timeBackgroundColor: Int = 0xFFFFFFFF.toInt(),
    val showTimer: Boolean = true,
)

interface MainGridComponent : MediaSfuUIComponent {
    val options: MainGridComponentOptions
    override val id: String get() = "main_grid"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets main grid container style
     */
    fun getGridStyle(): Map<String, Any> {
        return mapOf(
            "width" to "${options.width}px",
            "height" to "${options.height}px",
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexDirection" to "column",
            "overflow" to "hidden",
            "position" to "relative"
        )
    }
    
    /**
     * Gets timer style for display
     */
    fun getTimerStyle(): Map<String, Any> {
        return mapOf(
            "position" to "absolute",
            "top" to "10px",
            "right" to "10px",
            "backgroundColor" to options.timeBackgroundColor,
            "padding" to "8px 12px",
            "borderRadius" to "4px",
            "zIndex" to "10"
        )
    }
}

/**
 * Default implementation of MainGridComponent
 */
class DefaultMainGridComponent(
    override val options: MainGridComponentOptions
) : MainGridComponent {
    fun render(): Any {
        return mapOf(
            "type" to "mainGrid",
            "gridStyle" to getGridStyle(),
            "timerStyle" to getTimerStyle(),
            "showTimer" to options.showTimer,
            "showAspect" to options.showAspect,
            "children" to options.children,
            "mainSize" to options.mainSize
        )
    }
}

/**
 * Composable extension for rendering MainGridComponent in Jetpack Compose
 */
@Composable
fun MainGridComponent.renderCompose(
    renderTimer: Boolean = options.showTimer,
    timer: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    if (!options.showAspect) {
        return
    }

    Box(
        modifier = Modifier
            .width(options.width.dp)
            .height(options.height.dp)
            .background(Color(options.backgroundColor))
    ) {
        content()

        if (renderTimer) {
            timer?.invoke(this) ?: DefaultTimerBadge(options)
        }
    }
}

@Composable
fun MainGridComponent.renderCompose() {
    renderCompose(content = {
        Column(modifier = Modifier.fillMaxSize()) {
            options.children.forEach { _ ->
                // Placeholder – children render via explicit overload usage
            }
        }
    })
}

@Composable
private fun BoxScope.DefaultTimerBadge(options: MainGridComponentOptions) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(10.dp)
            .background(
                color = Color(options.timeBackgroundColor),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = "00:00",
            color = Color.Black
        )
    }
}
