package com.mediasfu.sdk.consumers

import kotlin.math.floor

/**
 * Internal implementation of updateMiniCardsGrid.
 * This function calculates and updates the grid dimensions for mini cards display.
 *
 * @param rows The number of rows in the grid
 * @param cols The number of columns in the grid
 * @param defal If true, updates the main grid; if false, updates the alternate grid
 * @param actualRows The actual number of rows used in calculations
 * @param gridSizes The current grid sizes
 * @param paginationDirection The direction of pagination ("horizontal" or "vertical")
 * @param paginationHeightWidth The height/width reserved for pagination
 * @param doPaginate Whether pagination is enabled
 * @param componentSizes The container component sizes
 * @param eventType The event type (for determining spacing)
 * @param updateGridRows Callback to update main grid rows
 * @param updateGridCols Callback to update main grid columns
 * @param updateAltGridRows Callback to update alternate grid rows
 * @param updateAltGridCols Callback to update alternate grid columns
 * @param updateGridSizes Callback to update grid sizes
 */
suspend fun updateMiniCardsGridImpl(
    rows: Int,
    cols: Int,
    defal: Boolean,
    actualRows: Int,
    gridSizes: GridSizes,
    paginationDirection: String,
    paginationHeightWidth: Double,
    doPaginate: Boolean,
    componentSizes: ComponentSizes,
    eventType: String,
    updateGridRows: (Int) -> Unit,
    updateGridCols: (Int) -> Unit,
    updateAltGridRows: (Int) -> Unit,
    updateAltGridCols: (Int) -> Unit,
    updateGridSizes: (GridSizes) -> Unit
) {
    var containerWidth = componentSizes.otherWidth
    var containerHeight = componentSizes.otherHeight
    

    // Adjust container size for pagination if enabled
    if (doPaginate) {
        if (paginationDirection == "horizontal") {
            containerHeight -= paginationHeightWidth
        } else {
            containerWidth -= paginationHeightWidth
        }
    }

    val cardSpacing = if (eventType.contains("chat", ignoreCase = true)) 0 else 3
    val totalSpacingHorizontal = (cols - 1) * cardSpacing
    val totalSpacingVertical = (actualRows - 1) * cardSpacing

    // Calculate individual card dimensions
    val cardWidth = if (cols == 0 || actualRows == 0) {
        0
    } else {
        floor((containerWidth - totalSpacingHorizontal) / cols).toInt()
    }

    val cardHeight = if (cols == 0 || actualRows == 0) {
        0
    } else {
        floor((containerHeight - totalSpacingVertical) / actualRows).toInt()
    }
    

    // Update grid or alternative grid based on `defal` flag
    if (defal) {
        updateGridRows(rows)
        updateGridCols(cols)
        val newGridSizes = GridSizes(
            gridWidth = cardWidth,
            gridHeight = cardHeight,
            altGridWidth = gridSizes.altGridWidth,
            altGridHeight = gridSizes.altGridHeight
        )
        updateGridSizes(newGridSizes)
    } else {
        updateAltGridRows(rows)
        updateAltGridCols(cols)
        val newGridSizes = GridSizes(
            gridWidth = gridSizes.gridWidth,
            gridHeight = gridSizes.gridHeight,
            altGridWidth = cardWidth,
            altGridHeight = cardHeight
        )
        updateGridSizes(newGridSizes)
    }
}

/**
 * Grid size configuration.
 */
data class GridSizes(
    val gridWidth: Int,
    val gridHeight: Int,
    val altGridWidth: Int,
    val altGridHeight: Int
)

/**
 * Component size configuration.
 */
data class ComponentSizes(
    val mainWidth: Double,
    val mainHeight: Double,
    val otherWidth: Double,
    val otherHeight: Double
)

