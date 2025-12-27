package com.mediasfu.sdk.methods.utils

import kotlinx.coroutines.delay

/**
 * Options for the sleep function, containing the sleep duration in milliseconds.
 */
data class SleepOptions(
    val ms: Long
)

/**
 * Type definition for sleep function.
 */
typealias SleepType = suspend (SleepOptions) -> Unit

/**
 * Suspends the execution of the current coroutine for the specified [options.ms] milliseconds.
 * 
 * Returns after the specified duration.
 * 
 * Example usage:
 * ```kotlin
 * sleep(SleepOptions(ms = 2000))
 * ```
 */
suspend fun sleep(options: SleepOptions) {
    delay(options.ms)
}