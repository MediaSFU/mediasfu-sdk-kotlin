package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mediasfu.sdk.ui.*

/**
 * MainScreenComponent - Main screen container for primary content display.
 *
 * Contains the main content area for video, screen sharing, or other primary views.
 *
 * @property options Configuration options for the main screen
 */
data class MainScreenComponentOptions(
    val children: List<MediaSfuUIComponent> = emptyList(),
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val mainSize: Double = 1.0,
    val showAspect: Boolean = true,
    val updateComponentSizes: ((Map<String, Any>) -> Unit)? = null,
)

interface MainScreenComponent : MediaSfuUIComponent {
    val options: MainScreenComponentOptions
    override val id: String get() = "main_screen"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets main screen container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "width" to "100%",
            "height" to "100%",
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexDirection" to "column",
            "overflow" to "hidden",
            "position" to "relative",
            "flex" to options.mainSize
        )
    }
}

/**
 * Default implementation of MainScreenComponent
 */
class DefaultMainScreenComponent(
    override val options: MainScreenComponentOptions
) : MainScreenComponent {
    fun render(): Any {
        return mapOf(
            "type" to "mainScreen",
            "containerStyle" to getContainerStyle(),
            "showAspect" to options.showAspect,
            "children" to options.children,
            "mainSize" to options.mainSize
        )
    }
}

/**
 * Composable extension for rendering MainScreenComponent in Jetpack Compose
 */
@Composable
fun MainScreenComponent.renderCompose(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(options.backgroundColor))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}

@Composable
fun MainScreenComponent.renderCompose() {
    renderCompose { /* No default children rendering yet */ }
}
