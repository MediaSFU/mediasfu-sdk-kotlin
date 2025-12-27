package com.mediasfu.sdk.model

/**
 * Represents constraint values for a media dimension.
 */
data class DimensionConstraints(
    val ideal: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val exact: Int? = null
) {
    fun toMap(): Map<String, Int?> = mapOf(
        "ideal" to ideal,
        "min" to min,
        "max" to max,
        "exact" to exact
    ).filterValues { it != null }
}

/**
 * Video constraints for width and height.
 */
data class VidCons(
    val width: DimensionConstraints = DimensionConstraints(),
    val height: DimensionConstraints = DimensionConstraints()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "width" to width.toMap(),
        "height" to height.toMap()
    )
}

