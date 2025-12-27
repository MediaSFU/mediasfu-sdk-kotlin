package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

import kotlin.math.pow
import kotlin.math.round

/**
 * Options for the formatNumber function.
 */
data class FormatNumberOptions(
    val number: Int
)

/**
 * Type definition for the formatNumber function.
 */
typealias FormatNumberType = suspend (FormatNumberOptions) -> String?

/**
 * Formats a given number into a human-readable string representation with suffixes (K, M, B).
 * 
 * The `formatNumber` function takes a `FormatNumberOptions` object with a specified `number`
 * and formats it based on magnitude:
 * - Numbers less than 1,000 are returned as-is.
 * - Numbers between 1,000 and 999,999 are formatted as "X.XK" (e.g., 1,500 becomes "1.5K").
 * - Numbers between 1,000,000 and 999,999,999 are formatted as "X.XM" (e.g., 1,500,000 becomes "1.5M").
 * - Numbers between 1,000,000,000 and 999,999,999,999 are formatted as "X.XB" (e.g., 1,500,000,000 becomes "1.5B").
 * 
 * Returns `null` if the `number` is non-positive or not specified.
 * 
 * ## Example Usage:
 * 
 * ```kotlin
 * // Define options for different number values
 * val options1 = FormatNumberOptions(number = 500)
 * val options2 = FormatNumberOptions(number = 1500)
 * val options3 = FormatNumberOptions(number = 1500000)
 * val options4 = FormatNumberOptions(number = 1500000000)
 * 
 * // Format the numbers using the formatNumber function
 * Logger.d("FormatNumber", formatNumber(options1)) // Output: "500"
 * Logger.d("FormatNumber", formatNumber(options2)) // Output: "1.5K"
 * Logger.d("FormatNumber", formatNumber(options3)) // Output: "1.5M"
 * Logger.d("FormatNumber", formatNumber(options4)) // Output: "1.5B"
 * ```
 */
suspend fun formatNumber(options: FormatNumberOptions): String? {
    val number = options.number
    if (number > 0) {
        when {
            number < 1e3.toInt() -> return number.toString()
            number < 1e6.toInt() -> return "${(number / 1e3).toFixed(1)}K"
            number < 1e9.toInt() -> return "${(number / 1e6).toFixed(1)}M"
            number < 1e12.toInt() -> return "${(number / 1e9).toFixed(1)}B"
        }
    }
    return null
}

/**
 * Extension function to format a Double to a fixed number of decimal places.
 */
private fun Double.toFixed(decimals: Int): String {
    val factor = 10.0.pow(decimals)
    val rounded = round(this * factor) / factor
    return rounded.toString()
}