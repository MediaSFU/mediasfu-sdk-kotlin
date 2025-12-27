package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.ui.*

/**
 * FlexibleVideo - Displays a flexible video container that adapts to content.
 *
 * Automatically adjusts video dimensions based on available space and content aspect ratio.
 *
 * @property options Configuration options for the flexible video
 */
data class FlexibleVideoOptions(
    val customWidth: Int,
    val customHeight: Int,
    val rows: Int = 1,
    val columns: Int = 1,
    val componentsToRender: List<MediaSfuUIComponent> = emptyList(),
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val showAspect: Boolean = false,
)

interface FlexibleVideo : MediaSfuUIComponent {
    val options: FlexibleVideoOptions
    override val id: String get() = "flexible_video"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Calculates component dimensions based on grid layout
     */
    fun calculateComponentDimensions(): Pair<Int, Int> {
        val width = options.customWidth / options.columns
        val height = options.customHeight / options.rows
        return Pair(width, height)
    }
    
    /**
     * Gets grid style configuration
     */
    fun getGridStyle(): Map<String, Any> {
        val (width, height) = calculateComponentDimensions()
        
        return mapOf(
            "display" to "grid",
            "gridTemplateColumns" to "repeat(${options.columns}, 1fr)",
            "gridTemplateRows" to "repeat(${options.rows}, 1fr)",
            "width" to "${options.customWidth}px",
            "height" to "${options.customHeight}px",
            "backgroundColor" to options.backgroundColor,
            "gap" to "2px"
        )
    }
}

/**
 * Default implementation of FlexibleVideo
 */
class DefaultFlexibleVideo(
    override val options: FlexibleVideoOptions
) : FlexibleVideo {
    fun render(): Any {
        val (componentWidth, componentHeight) = calculateComponentDimensions()
        
        return mapOf(
            "type" to "flexibleVideo",
            "componentsToRender" to options.componentsToRender,
            "gridStyle" to getGridStyle(),
            "componentWidth" to componentWidth,
            "componentHeight" to componentHeight,
            "showAspect" to options.showAspect
        )
    }
}

/**
 * Composable extension for rendering FlexibleVideo in Jetpack Compose
 */
@Composable
fun FlexibleVideo.renderCompose() {
    if (!options.showAspect) {
        return
    }

    val rows = options.rows.coerceAtLeast(1)
    val columns = options.columns.coerceAtLeast(1)
    val components = options.componentsToRender

    if (components.isEmpty()) {
        return
    }

    // Main container with fixed dimensions
    Column(
        modifier = Modifier
            .width(options.customWidth.dp)
            .height(options.customHeight.dp)
            .background(Color(options.backgroundColor)),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),  // <-- FIX: Each row takes equal vertical space
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                for (colIndex in 0 until columns) {
                    val itemIndex = rowIndex * columns + colIndex
                    val component = components.getOrNull(itemIndex)

                    Box(
                        modifier = Modifier
                            .weight(1f)        // <-- FIX: Each cell takes equal horizontal space
                            .fillMaxHeight(),  // <-- FIX: Fill the row's height
                        contentAlignment = Alignment.Center
                    ) {
                        if (component != null) {
                            // Generate a unique key for each component based on its stream/track ID
                            // This ensures Compose recreates the video renderer when the stream changes
                            val componentKey = when (component) {
                                is CardVideoDisplay -> {
                                    val streamId = component.options.videoStream?.id ?: "null"
                                    val trackId = component.options.videoStream?.getVideoTracks()?.firstOrNull()?.id ?: "no-track"
                                    "video-$itemIndex-$streamId-$trackId"
                                }
                                is AudioCard -> "audio-$itemIndex-${component.options.participant?.name}"
                                is MiniCard -> "mini-$itemIndex-${component.options.name}"
                                is MiniAudio -> "miniaudio-$itemIndex-${component.options.name}"
                                else -> "other-$itemIndex"
                            }
                            
                            key(componentKey) {
                                when (component) {
                                    is AudioCard -> component.renderCompose()
                                    is CardVideoDisplay -> component.renderCompose()
                                    is MiniCard -> component.renderCompose()
                                    is MiniAudio -> component.renderCompose()
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
