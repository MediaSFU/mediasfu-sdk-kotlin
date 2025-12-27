package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * OtherGridComponent - Secondary grid for additional participants.
 *
 * Displays overflow participants in a compact grid layout.
 *
 * @property options Configuration options for the other grid
 */
data class OtherGridComponentOptions(
    val height: Int,
    val width: Int,
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val children: List<MediaSfuUIComponent> = emptyList(),
    val gridSize: Double = 0.0,
    val showAspect: Boolean = true,
)

interface OtherGridComponent : MediaSfuUIComponent {
    val options: OtherGridComponentOptions
    override val id: String get() = "other_grid"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets other grid container style
     */
    fun getGridStyle(): Map<String, Any> {
        return mapOf(
            "width" to "${options.width}px",
            "height" to "${options.height}px",
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexWrap" to "wrap",
            "overflow" to "auto",
            "position" to "relative",
            "flex" to options.gridSize
        )
    }
}

/**
 * Default implementation of OtherGridComponent
 */
class DefaultOtherGridComponent(
    override val options: OtherGridComponentOptions
) : OtherGridComponent {
    fun render(): Any {
        return mapOf(
            "type" to "otherGrid",
            "gridStyle" to getGridStyle(),
            "showAspect" to options.showAspect,
            "children" to options.children,
            "gridSize" to options.gridSize
        )
    }
}

/**
 * Composable extension for rendering OtherGridComponent in Jetpack Compose
 */
@Composable
fun OtherGridComponent.renderCompose(content: @Composable ColumnScope.() -> Unit) {
    if (!options.showAspect) {
        return
    }

    Box(
        modifier = Modifier
            .width(options.width.dp)
            .height(options.height.dp)
            .background(Color(options.backgroundColor))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
fun OtherGridComponent.renderCompose() {
    renderCompose { /* No default children rendering yet */ }
}
