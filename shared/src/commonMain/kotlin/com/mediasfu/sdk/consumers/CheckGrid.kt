package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Options for checking the grid configuration.
 *
 * @property rows The number of rows in the grid
 * @property cols The number of columns in the grid
 * @property actives The number of active elements to display
 */
data class CheckGridOptions(
    val rows: Int,
    val cols: Int,
    val actives: Int
)

/**
 * Result of grid configuration check.
 *
 * @property removeAltGrid Whether to remove the alternate grid
 * @property numToAdd The number of elements to add
 * @property numRows The number of rows
 * @property numCols The number of columns
 * @property remainingVideos The remaining videos count
 * @property actualRows The actual number of rows
 * @property lastRowCols The number of columns in the last row
 */
data class CheckGridResult(
    val removeAltGrid: Boolean,
    val numToAdd: Int,
    val numRows: Int,
    val numCols: Int,
    val remainingVideos: Int,
    val actualRows: Int,
    val lastRowCols: Int
)

/**
 * Checks the grid configuration and calculates various parameters based on the number of rows,
 * columns, and active elements.
 *
 * This function determines:
 * - Whether the grid fits perfectly or needs adjustment
 * - How many elements to add to the main grid
 * - The number of remaining videos for the alternate grid
 * - The configuration of the last row
 *
 * @param options The grid configuration options
 * @return CheckGridResult containing grid layout calculations
 *
 * Example:
 * ```kotlin
 * val options = CheckGridOptions(rows = 3, cols = 4, actives = 10)
 * val result = checkGrid(options)
 * ```
 */
fun checkGrid(options: CheckGridOptions): CheckGridResult {
    return try {
        var numRows = 0
        var numCols = 0
        var lastRow = 0
        var lastRowCols = 0
        var remainingVideos = 0
        var numToAdd = 0
        var actualRows = 0
        var removeAltGrid = false

        val totalCapacity = options.rows * options.cols

        if (totalCapacity != options.actives) {
            if (totalCapacity > options.actives) {
                // Calculate how many fit in rows - 1
                val res = options.actives - (options.rows - 1) * options.cols

                if (options.cols * 0.5 < res) {
                    // Last row has enough elements
                    lastRow = options.rows
                    lastRowCols = res
                    remainingVideos = lastRowCols
                } else {
                    // Combine with previous row
                    lastRowCols = res + options.cols
                    lastRow = options.rows - 1
                    remainingVideos = lastRowCols
                }

                numRows = lastRow - 1
                numCols = options.cols
                numToAdd = (lastRow - 1) * numCols
                actualRows = lastRow

                removeAltGrid = false
            }
        } else {
            // Perfect fit
            numCols = options.cols
            numRows = options.rows
            lastRow = options.rows
            lastRowCols = options.cols
            remainingVideos = 0
            numToAdd = lastRow * numCols
            actualRows = lastRow
            removeAltGrid = true
        }

        CheckGridResult(
            removeAltGrid = removeAltGrid,
            numToAdd = numToAdd,
            numRows = numRows,
            numCols = numCols,
            remainingVideos = remainingVideos,
            actualRows = actualRows,
            lastRowCols = lastRowCols
        )
    } catch (e: Exception) {
        Logger.e("CheckGrid", "checkGrid error: ${e.message}")
        CheckGridResult(
            removeAltGrid = false,
            numToAdd = 0,
            numRows = 0,
            numCols = 0,
            remainingVideos = 0,
            actualRows = 0,
            lastRowCols = 0
        )
    }
}

