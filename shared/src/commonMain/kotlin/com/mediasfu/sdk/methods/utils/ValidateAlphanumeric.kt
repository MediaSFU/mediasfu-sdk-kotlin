package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

/**
 * Options for the alphanumeric validation, containing the string to validate.
 */
data class ValidateAlphanumericOptions(
    val str: String
)

/**
 * Type definition for alphanumeric validation function.
 */
typealias ValidateAlphanumericType = suspend (ValidateAlphanumericOptions) -> Boolean

/**
 * Validates if the provided string in [options] contains only alphanumeric characters.
 * 
 * Returns `true` if the string is alphanumeric, otherwise `false`.
 * 
 * Example usage:
 * ```kotlin
 * val isValid = validateAlphanumeric(ValidateAlphanumericOptions(str = "abc123"))
 * Logger.d("ValidateAlphanumeric", isValid) // Output: true
 * ```
 */
suspend fun validateAlphanumeric(options: ValidateAlphanumericOptions): Boolean {
    val alphanumericRegex = Regex("^[a-zA-Z0-9]+$")
    return alphanumericRegex.matches(options.str)
}