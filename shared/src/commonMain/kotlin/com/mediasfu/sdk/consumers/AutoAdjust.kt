package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.EventType

/**
 * Options for adjusting layout ratios based on active participants and event context.
 */
data class AutoAdjustOptions(
    val n: Int,
    val eventType: Any?, // EventType
    val shareScreenStarted: Boolean,
    val shared: Boolean
)

/**
 * Exception thrown when auto adjust fails.
 */
class AutoAdjustException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Mirrors the React `autoAdjust` heuristic with 100% parity.
 * Returns two integers whose sum is 12 representing the split between the primary and secondary grids.
 * 
 * @param options Auto adjust configuration options
 * @return Result containing list of two integers [val1, val2] where val1 + val2 = 12
 */
suspend fun autoAdjust(
    options: AutoAdjustOptions
): Result<List<Int>> {
    return try {
        val n = options.n
        val normalizedEventType = when (val raw = options.eventType) {
            is EventType -> raw.name.lowercase()
            else -> raw?.toString()?.lowercase()
        }
        val shareActive = options.shareScreenStarted || options.shared

        var val1 = 6
        var val2 = 12 - val1

        when {
            normalizedEventType == "broadcast" -> {
                // Broadcast always gets 0/12 split
                val1 = 0
                val2 = 12 - val1
            }
            normalizedEventType == "chat" ||
                (normalizedEventType == "conference" && !shareActive) -> {
                // Chat or conference without sharing gets 12/0 split
                val1 = 12
                val2 = 12 - val1
            }
            shareActive -> {
                // When sharing, secondary gets priority (2/10 split)
                val2 = 10
                val1 = 12 - val2
            }
            else -> {
                // Standard conference mode - adjust based on participant count
                val1 = when {
                    n == 0 -> 1
                    n in 1 until 4 -> 4
                    n in 4 until 6 -> 6
                    n in 6 until 9 -> 6
                    n in 9 until 12 -> 6
                    n in 12 until 20 -> 8
                    n in 20 until 50 -> 8
                    else -> 10
                }
                val2 = 12 - val1
            }
        }

        val adjustedValues = listOf(val1, val2)
        Result.success(adjustedValues)
    } catch (error: Exception) {
        Result.failure(
            AutoAdjustException(
                "autoAdjust error: ${error.message}",
                error
            )
        )
    }
}