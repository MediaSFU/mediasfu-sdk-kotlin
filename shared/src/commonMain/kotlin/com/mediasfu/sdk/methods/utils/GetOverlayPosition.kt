package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

/**
 * Options for configuring the overlay position.
 */
data class GetOverlayPositionOptions(
    val position: String
)

/**
 * Type definition for get overlay position function.
 */
typealias GetOverlayPositionType = (GetOverlayPositionOptions) -> Map<String, Any>

/**
 * Returns the overlay position based on the specified options.
 * 
 * The [options] parameter contains the desired position of the overlay.
 * The position can be one of the following values:
 *   - 'topLeft': Returns the position with 'top' set to 0 and 'left' set to 0.
 *   - 'topRight': Returns the position with 'top' set to 0 and 'right' set to 0.
 *   - 'bottomLeft': Returns the position with 'bottom' set to 0 and 'left' set to 0.
 *   - 'bottomRight': Returns the position with 'bottom' set to 0 and 'right' set to 0.
 *   - Any other value: Returns an empty map.
 * 
 * Example usage:
 * ```kotlin
 * val position = getOverlayPosition(GetOverlayPositionOptions(position = "topLeft"))
 * Logger.d("GetOverlayPosition", position) // Output: {top=0, left=0}
 * ```
 */
fun getOverlayPosition(options: GetOverlayPositionOptions): Map<String, Any> {
    return when (options.position) {
        "topLeft" -> mapOf("top" to 0, "left" to 0)
        "topRight" -> mapOf("top" to 0, "right" to 0)
        "bottomLeft" -> mapOf("bottom" to 0, "left" to 0)
        "bottomRight" -> mapOf("bottom" to 0, "right" to 0)
        else -> emptyMap()
    }
}