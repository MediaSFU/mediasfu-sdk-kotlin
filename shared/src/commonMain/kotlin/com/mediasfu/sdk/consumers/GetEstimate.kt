package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Parameters interface for calculating grid estimates
 */
interface GetEstimateParameters {
    val fixedPageLimit: Int
    val screenPageLimit: Int
    val shareScreenStarted: Boolean
    val shared: Boolean
    val eventType: Any? // EventType enum
    val removeAltGrid: Boolean
    val isWideScreen: Boolean
    val isMediumScreen: Boolean
    val updateRemoveAltGrid: (Boolean) -> Unit
    val calculateRowsAndColumns: (CalculateRowsAndColumnsOptions) -> Result<List<Int>>
}

/**
 * Options for the getEstimate function.
 *
 * @property n The number of participants/streams to display
 * @property parameters The parameters containing configuration for estimation
 */
data class GetEstimateOptions(
    val n: Int,
    val parameters: GetEstimateParameters
)

/**
 * Calculates the optimal grid layout dimensions based on the number of participants and various
 * display parameters.
 *
 * This function determines the best number of rows and columns for displaying video streams
 * or participants in a grid layout, taking into account:
 * - Screen sharing status
 * - Event type (conference, broadcast, etc.)
 * - Screen orientation (wide vs narrow)
 * - Page limits
 *
 * @param options The estimation options containing participant count and parameters
 * @return List of three integers: [limit, rows, columns] where:
 *         - limit: The maximum number of items to display
 *         - rows: The optimal number of rows
 *         - columns: The optimal number of columns
 *
 * Example:
 * ```kotlin
 * val options = GetEstimateOptions(
 *     n = 10,
 *     parameters = object : GetEstimateParameters {
 *         override val fixedPageLimit = 4
 *         override val screenPageLimit = 12
 *         override val shareScreenStarted = false
 *         override val shared = false
 *         override val eventType = "conference"
 *         override val removeAltGrid = false
        override val isWideScreen = true
        override val isMediumScreen = false
        override val updateRemoveAltGrid = { _: Boolean -> }
        override val calculateRowsAndColumns = { opts: CalculateRowsAndColumnsOptions ->
            calculateRowsAndColumns(opts)
        }
 *     }
 * )
 * val result = getEstimate(options) // Returns [12, 3, 4] for example
 * ```
 */
fun getEstimate(options: GetEstimateOptions): List<Int> {
    return try {
        val parameters = options.parameters
        val count = options.n

        val fixedPageLimit = parameters.fixedPageLimit
        val screenPageLimit = parameters.screenPageLimit
        val shareScreenStarted = parameters.shareScreenStarted
        val shared = parameters.shared
        val eventType = parameters.eventType?.toString()?.lowercase() ?: ""
        var removeAltGrid = parameters.removeAltGrid
        val isWideScreen = parameters.isWideScreen
        val isMediumScreen = parameters.isMediumScreen
    val updateRemoveAltGrid = parameters.updateRemoveAltGrid
        val calculateRowsAndColumns = parameters.calculateRowsAndColumns

        val dimensions = calculateRowsAndColumns(
            CalculateRowsAndColumnsOptions(n = count)
        ).getOrElse { error ->
            Logger.e("GetEstimate", "getEstimate error: ${error.message}")
            return listOf(0, 0, 0)
        }

        if (dimensions.size < 2) {
            Logger.e("GetEstimate", "getEstimate error: calculateRowsAndColumns returned insufficient data")
            return listOf(0, 0, 0)
        }

        val rows = dimensions[0]
        val cols = dimensions[1]
        val shareActive = shareScreenStarted || shared

        if (count < fixedPageLimit || (shareActive && count < screenPageLimit + 1)) {
            removeAltGrid = true
            updateRemoveAltGrid(removeAltGrid)

            val isChat = eventType == "chat"
            val isConference = eventType == "conference"
            val useCompactLayout = !(isMediumScreen || isWideScreen)
            

            return if (useCompactLayout) {
                if (isChat || (isConference && !shareActive)) {
                    listOf(count, count, 1)
                } else {
                    listOf(count, 1, count)
                }
            } else {
                if (isChat || (isConference && !shareActive)) {
                    listOf(count, 1, count)
                } else {
                    listOf(count, count, 1)
                }
            }
        }

        listOf(rows * cols, rows, cols)
    } catch (e: Exception) {
        Logger.e("GetEstimate", "getEstimate error: ${e.message}")
        e.printStackTrace()
        listOf(0, 0, 0)
    }
}

