package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mediasfu.sdk.ui.*

/**
 * MainAspectComponent - Main aspect ratio container for primary video display.
 *
 * Maintains proper aspect ratio for the main video view area.
 *
 * @property options Configuration options for the main aspect component
 */
data class MainAspectComponentOptions(
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val defaultFraction: Double = 1.0,
    val showControls: Boolean = true,
    val children: List<MediaSfuUIComponent> = emptyList(),
    val updateIsWideScreen: ((Boolean) -> Unit)? = null,
    val updateIsMediumScreen: ((Boolean) -> Unit)? = null,
    val updateIsSmallScreen: ((Boolean) -> Unit)? = null,
)

interface MainAspectComponent : MediaSfuUIComponent {
    val options: MainAspectComponentOptions
    override val id: String get() = "main_aspect"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Determines screen size category based on dimensions
     */
    fun determineScreenSize(width: Int): String {
        return when {
            width >= 1280 -> "wide"
            width >= 768 -> "medium"
            else -> "small"
        }
    }
    
    /**
     * Gets aspect ratio based on fraction
     */
    fun getAspectRatio(): String {
        val fraction = options.defaultFraction
        return when {
            fraction <= 0.5 -> "9:16" // Portrait
            fraction <= 1.0 -> "4:3" // Standard
            fraction <= 1.5 -> "16:9" // Widescreen
            else -> "21:9" // Ultra-wide
        }
    }
    
    /**
     * Gets container style with aspect ratio maintained
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "width" to "100%",
            "height" to "100%",
            "backgroundColor" to options.backgroundColor,
            "aspectRatio" to getAspectRatio(),
            "position" to "relative",
            "overflow" to "hidden"
        )
    }
}

/**
 * Default implementation of MainAspectComponent
 */
class DefaultMainAspectComponent(
    override val options: MainAspectComponentOptions
) : MainAspectComponent {
    fun render(): Any {
        return mapOf(
            "type" to "mainAspect",
            "containerStyle" to getContainerStyle(),
            "aspectRatio" to getAspectRatio(),
            "showControls" to options.showControls,
            "children" to options.children
        )
    }
}

/**
 * Composable extension for rendering MainAspectComponent in Jetpack Compose
 */
@Composable
fun MainAspectComponent.renderCompose(content: @Composable ColumnScope.() -> Unit) {
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
fun MainAspectComponent.renderCompose() {
    renderCompose { /* No default children rendering yet */ }
}
