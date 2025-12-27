// CalculateRowsAndColumns.kt
package com.mediasfu.sdk.consumers

import kotlin.math.*

/**
 * Options for calculating the number of rows and columns in a grid layout.
 */
data class CalculateRowsAndColumnsOptions(
    val n: Int
)

/**
 * Exception thrown when calculating rows and columns fails.
 */
class CalculateRowsAndColumnsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

fun calculateRowsAndColumns(
    options: CalculateRowsAndColumnsOptions
): Result<List<Int>> {
    return try {
        val n = options.n

        if (n <= 0) {
            return Result.success(listOf(0, 0))
        }

        // CRITICAL FIX: Initialize cols first (like Dart), then calculate rows
        // This ensures bottom-heavy distribution (last row gets extras)
        val sqrtVal = sqrt(n.toDouble())
        var cols = sqrtVal.toInt().coerceAtLeast(1)  // floor of sqrt
        var rows = ceil(n.toDouble() / cols).toInt()  // ceil of n/cols
        var prod = rows * cols

        // Adjust until we have enough cells - prioritize rows to match Dart
        while (prod < n) {
            if (cols < rows) {
                cols++
            } else {
                rows++
            }
            prod = rows * cols
        }

        val result = listOf(rows, cols)
        Result.success(result)
    } catch (error: Exception) {
        Result.failure(
            CalculateRowsAndColumnsException(
                "calculateRowsAndColumns error: ${error.message}",
                error
            )
        )
    }
}