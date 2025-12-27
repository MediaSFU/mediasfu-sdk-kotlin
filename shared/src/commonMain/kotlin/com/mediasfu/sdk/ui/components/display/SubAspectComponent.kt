package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.ui.*

/**
 * SubAspectComponent - Sub aspect ratio container for secondary video displays.
 *
 * Maintains proper aspect ratio for secondary video views or sidebars.
 *
 * @property options Configuration options for the sub aspect component
 */
data class SubAspectComponentOptions(
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val showControls: Boolean = false,
    val children: List<MediaSfuUIComponent> = emptyList(),
    val defaultFraction: Double = 0.0,
)

interface SubAspectComponent : MediaSfuUIComponent {
    val options: SubAspectComponentOptions
    override val id: String get() = "sub_aspect"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets container style with sub aspect dimensions
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "width" to "100%",
            "height" to "100%",
            "backgroundColor" to options.backgroundColor,
            "flex" to options.defaultFraction,
            "position" to "relative",
            "overflow" to "hidden"
        )
    }
}

/**
 * Default implementation of SubAspectComponent
 */
class DefaultSubAspectComponent(
    override val options: SubAspectComponentOptions
) : SubAspectComponent {
    fun render(): Any {
        return mapOf(
            "type" to "subAspect",
            "containerStyle" to getContainerStyle(),
            "showControls" to options.showControls,
            "children" to options.children,
            "defaultFraction" to options.defaultFraction
        )
    }
}

/**
 * Composable extension for rendering SubAspectComponent in Jetpack Compose
 */
@Composable
fun SubAspectComponent.renderCompose() {
    LaunchedEffect(options.defaultFraction, options.showControls) {
    }

    val sizedModifier = Modifier
        .fillMaxWidth()
        .then(
            when {
                options.defaultFraction > 1.0 -> Modifier.height(options.defaultFraction.dp)
                options.defaultFraction > 0.0 -> Modifier.fillMaxHeight(options.defaultFraction.toFloat())
                else -> Modifier.wrapContentHeight()
            }
        )
        .background(Color(options.backgroundColor))

    Box(modifier = sizedModifier) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            options.children.forEach { child ->
                // Children would render themselves using their own renderCompose()
            }
        }
    }
}

@Composable
fun SubAspectComponent.renderCompose(content: @Composable ColumnScope.() -> Unit) {
    LaunchedEffect(options.defaultFraction, options.showControls) {
    }

    val sizedModifier = Modifier
        .fillMaxWidth()
        .then(
            when {
                options.defaultFraction > 1.0 -> Modifier.height(options.defaultFraction.dp)
                options.defaultFraction > 0.0 -> Modifier.fillMaxHeight(options.defaultFraction.toFloat())
                else -> Modifier.wrapContentHeight()
            }
        )
        .background(Color(options.backgroundColor))

    Box(modifier = sizedModifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            content = content
        )
    }
}
