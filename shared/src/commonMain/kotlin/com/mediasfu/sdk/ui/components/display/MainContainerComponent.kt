package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.*

/**
 * MainContainerComponent - Main container for the entire application layout.
 *
 * Provides the root container with responsive layout management.
 *
 * @property options Configuration options for the main container
 */
data class MainContainerComponentOptions(
    val backgroundColor: Int = 0xFF000000.toInt(), // Black
    val children: List<MediaSfuUIComponent> = emptyList(),
    val containerWidthFraction: Double = 1.0,
    val containerHeightFraction: Double = 1.0,
    val marginLeft: Int = 0,
    val marginRight: Int = 0,
    val marginTop: Int = 0,
    val marginBottom: Int = 0,
    val padding: Int = 0,
)

interface MainContainerComponent : MediaSfuUIComponent {
    val options: MainContainerComponentOptions
    override val id: String get() = "main_container"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets container style with responsive dimensions
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "width" to "${options.containerWidthFraction * 100}%",
            "height" to "${options.containerHeightFraction * 100}%",
            "backgroundColor" to options.backgroundColor,
            "marginLeft" to "${options.marginLeft}px",
            "marginRight" to "${options.marginRight}px",
            "marginTop" to "${options.marginTop}px",
            "marginBottom" to "${options.marginBottom}px",
            "padding" to "${options.padding}px",
            "display" to "flex",
            "flexDirection" to "column",
            "overflow" to "hidden",
            "position" to "relative"
        )
    }
}

/**
 * Default implementation of MainContainerComponent
 */
class DefaultMainContainerComponent(
    override val options: MainContainerComponentOptions
) : MainContainerComponent {
    fun render(): Any {
        return mapOf(
            "type" to "mainContainer",
            "containerStyle" to getContainerStyle(),
            "children" to options.children
        )
    }
}

/**
 * Composable extension for rendering MainContainerComponent in Jetpack Compose
 */
@Composable
fun MainContainerComponent.renderCompose(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth(options.containerWidthFraction.toFloat())
            .fillMaxHeight(options.containerHeightFraction.toFloat())
            .background(Color(options.backgroundColor))
            .padding(
                start = options.marginLeft.dp,
                end = options.marginRight.dp,
                top = options.marginTop.dp,
                bottom = options.marginBottom.dp
            )
            .padding(options.padding.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

@Composable
fun MainContainerComponent.renderCompose() {
    renderCompose { /* No default children rendering yet */ }
}
