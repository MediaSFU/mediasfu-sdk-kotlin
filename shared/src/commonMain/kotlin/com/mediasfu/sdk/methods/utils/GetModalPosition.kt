package com.mediasfu.sdk.methods.utils

/**
 * Options for configuring the modal position.
 */
data class GetModalPositionOptions(
    val position: String,
    val modalWidth: Double,
    val modalHeight: Double,
    val screenWidth: Double,
    val screenHeight: Double
)

/**
 * Type definition for get modal position function.
 */
typealias GetModalPositionType = (GetModalPositionOptions) -> Map<String, Double>

/**
 * Returns the position of a modal based on the specified options.
 * 
 * The `options` parameter specifies the desired position of the modal:
 *   - 'center': Positions the modal at the center of the screen.
 *   - 'topLeft': Positions the modal at the top left corner of the screen.
 *   - 'topRight': Positions the modal at the top right corner of the screen.
 *   - 'bottomLeft': Positions the modal at the bottom left corner of the screen.
 *   - 'bottomRight': Positions the modal at the bottom right corner of the screen.
 * 
 * The `screenWidth` and `screenHeight` parameters specify the screen dimensions.
 * The `modalWidth` and `modalHeight` parameters specify the width and height of the modal.
 * 
 * Example usage:
 * ```kotlin
 * val options = GetModalPositionOptions(
 *     position = "center",
 *     modalWidth = 200.0,
 *     modalHeight = 100.0,
 *     screenWidth = 800.0,
 *     screenHeight = 600.0
 * )
 * val modalPosition = getModalPosition(options)
 * ```
 * 
 * Returns a map containing the top and right positions of the modal.
 * The top position is the distance from the top of the screen, and the right position is the distance from the right of the screen.
 * The modal position is calculated based on the specified options and screen size.
 * The modal width and height are used to center the modal on the screen.
 * The modal is positioned at the top left, top right, bottom left, or bottom right corner of the screen.
 * The modal is positioned at the center of the screen.
 * The modal is positioned at the top right corner of the screen.
 */
fun getModalPosition(options: GetModalPositionOptions): Map<String, Double> {
    val modalWidth = options.modalWidth
    val modalHeight = options.modalHeight
    val screenWidth = options.screenWidth
    val screenHeight = options.screenHeight
    
    return when (options.position) {
        "center" -> mapOf(
            "top" to (screenHeight - modalHeight) / 2,
            "right" to (screenWidth - modalWidth) / 2
        )
        "topLeft" -> mapOf(
            "top" to 0.0,
            "right" to screenWidth - modalWidth
        )
        "topRight" -> mapOf(
            "top" to 0.0,
            "right" to 0.0
        )
        "bottomLeft" -> mapOf(
            "top" to screenHeight - modalHeight,
            "right" to screenWidth - modalWidth
        )
        "bottomRight" -> mapOf(
            "top" to screenHeight - modalHeight,
            "right" to 0.0
        )
        else -> mapOf(
            "top" to screenHeight - modalHeight,
            "right" to 0.0
        )
    }
}