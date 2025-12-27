package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * FlexibleGrid - Displays a flexible grid of video/audio participants.
 *
 * Automatically calculates optimal grid layout based on number of participants
 * and available screen space.
 *
 * @property options Configuration options for the flexible grid
 * @property customWidth Width of EACH CELL (not total grid width) - matches React
 * @property customHeight Height of EACH CELL (not total grid height) - matches React
 */
data class FlexibleGridOptions(
    val customWidth: Int,
    val customHeight: Int,
    val rows: Int,
    val columns: Int,
    val componentsToRender: List<MediaSfuUIComponent>,
    val backgroundColor: Int = 0xFF2C678F.toInt(),
    val participants: List<Participant> = emptyList(),
    val showAspect: Boolean = true,
    val cardWidthOverride: Int? = null,
    val cardHeightOverride: Int? = null,
    val horizontalSpacingDp: Int = 0,
    val verticalSpacingDp: Int = 0
)

interface FlexibleGrid : MediaSfuUIComponent {
    val options: FlexibleGridOptions
    override val id: String get() = "flexible_grid"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Returns grid dimensions - refs require explicit rows/columns, no auto-calculation
     */
    fun calculateGridDimensions(): Pair<Int, Int> {
        return Pair(options.rows, options.columns)
    }
    
    /**
     * Returns card dimensions - customWidth/customHeight are per-cell dimensions (not container)
     */
    fun calculateCardDimensions(): Pair<Int, Int> {
        val cardWidth = (options.cardWidthOverride ?: options.customWidth).coerceAtLeast(0)
        val cardHeight = (options.cardHeightOverride ?: options.customHeight).coerceAtLeast(0)
        return Pair(cardWidth, cardHeight)
    }
    
    /**
     * Groups participants into grid rows
     */
    fun getGridRows(): List<List<Participant>> {
        val (_, columns) = calculateGridDimensions()
        if (columns == 0) return emptyList()
        
        val rows = mutableListOf<List<Participant>>()
        val participants = options.participants
        
        for (i in participants.indices step columns) {
            val endIndex = minOf(i + columns, participants.size)
            rows.add(participants.subList(i, endIndex))
        }
        
        return rows
    }
}

/**
 * Default implementation of FlexibleGrid
 */
class DefaultFlexibleGrid(
    override val options: FlexibleGridOptions
) : FlexibleGrid {
    fun render(): Any {
        val (rows, columns) = calculateGridDimensions()
        val (cardWidth, cardHeight) = calculateCardDimensions()
        val gridRows = getGridRows()
        
        return mapOf(
            "type" to "flexibleGrid",
            "rows" to rows,
            "columns" to columns,
            "cardWidth" to cardWidth,
            "cardHeight" to cardHeight,
            "gridRows" to gridRows,
            "backgroundColor" to options.backgroundColor,
            "showAspect" to options.showAspect,
            "horizontalSpacing" to options.horizontalSpacingDp,
            "verticalSpacing" to options.verticalSpacingDp
        )
    }
}

@Composable
fun FlexibleGrid.renderCompose() {
    val (rows, columns) = calculateGridDimensions()
    
    if (rows <= 0 || columns <= 0) {
        return
    }

    val components = options.componentsToRender
    
    val horizontalSpacing = options.horizontalSpacingDp.coerceAtLeast(0)
    val verticalSpacing = options.verticalSpacingDp.coerceAtLeast(0)

    // Calculate total container size from card dimensions
    val (cardWidthPx, cardHeightPx) = calculateCardDimensions()
    val density = LocalDensity.current
    val totalWidthDp = with(density) { (cardWidthPx * columns).toDp() }
    val totalHeightDp = with(density) { (cardHeightPx * rows).toDp() }

    Column(
        modifier = Modifier
            .width(totalWidthDp)
            .height(totalHeightDp)
            .background(Color(options.backgroundColor)),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing.dp)
    ) {
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),  // Each row takes equal vertical space
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing.dp)
            ) {
                for (colIndex in 0 until columns) {
                    val itemIndex = rowIndex * columns + colIndex
                    val component = components.getOrNull(itemIndex)

                    Box(
                        modifier = Modifier
                            .weight(1f)       // Each cell takes equal horizontal space
                            .fillMaxHeight()  // Fill the row's height
                    ) {
                        when {
                            component != null -> {
                                // Generate a unique key for each component based on its stream/track ID
                                val componentKey = when (component) {
                                    is CardVideoDisplay -> {
                                        val streamId = component.options.videoStream?.id ?: "null"
                                        val trackId = component.options.videoStream?.getVideoTracks()?.firstOrNull()?.id ?: "no-track"
                                        "grid-video-$itemIndex-$streamId-$trackId"
                                    }
                                    is AudioCard -> "grid-audio-$itemIndex-${component.options.participant?.name}"
                                    is MiniCard -> "grid-mini-$itemIndex-${component.options.name}"
                                    is MiniAudio -> "grid-miniaudio-$itemIndex-${component.options.name}"
                                    else -> "grid-other-$itemIndex"
                                }
                                
                                key(componentKey) {
                                    when (component) {
                                        is AudioCard -> component.renderCompose()
                                        is MiniCard -> component.renderCompose()
                                        is CardVideoDisplay -> component.renderCompose()
                                        is MiniAudio -> component.renderCompose()
                                        else -> { /* Skip unknown component types */ }
                                    }
                                }
                            }
                            itemIndex < options.participants.size -> {
                                // Placeholder for participant without component
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF1C2B4A))
                                )
                            }
                            // Empty cells still take space due to weight(1f)
                        }
                    }
                }
            }
        }
    }
}