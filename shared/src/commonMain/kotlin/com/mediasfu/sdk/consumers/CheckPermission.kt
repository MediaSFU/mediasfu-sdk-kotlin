// CheckPermission.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Options for checking permission based on specific settings.
 */
data class CheckPermissionOptions(
    val audioSetting: String,
    val videoSetting: String,
    val screenshareSetting: String,
    val chatSetting: String,
    val permissionType: String
)

/**
 * Exception thrown when checking permission fails.
 */
class CheckPermissionException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

typealias CheckPermissionType = suspend (CheckPermissionOptions) -> Int

/**
 * Checks the permission based on the provided settings.
 *
 * This function evaluates permission settings for different media types and returns
 * the appropriate permission status. It supports various permission levels including
 * allow, approval, and disallow.
 *
 * ## Features:
 * - Multi-media permission checking
 * - Configurable permission levels
 * - Type-specific permission evaluation
 * - Status code return system
 * - Error handling
 *
 * ## Parameters:
 * - [options] Configuration options containing permission settings
 *
 * ## Returns:
 * - [Result]<[Int]> representing the permission status:
 *   - `0`: Permission is allowed
 *   - `1`: Permission requires approval
 *   - `2`: Permission is disallowed or invalid type
 *
 * ## Example Usage:
 * ```kotlin
 * val options = CheckPermissionOptions(
 *     permissionType = "audioSetting",
 *     audioSetting = "allow",
 *     videoSetting = "approval",
 *     screenshareSetting = "approval",
 *     chatSetting = "allow"
 * )
 *
 * val result = checkPermission(options)
 * result.onSuccess { status ->
 *     when (status) {
 *     }
 * }
 * result.onFailure { error ->
 *     Logger.e("CheckPermission", "Error checking permission: ${error.message}")
 * }
 * ```
 */
suspend fun checkPermission(
    options: CheckPermissionOptions
): Int {
    return try {
        val permissionType = options.permissionType
        val audioSetting = options.audioSetting
        val videoSetting = options.videoSetting
        val screenshareSetting = options.screenshareSetting
        val chatSetting = options.chatSetting

        val setting = when (permissionType) {
            "audioSetting" -> audioSetting
            "videoSetting" -> videoSetting
            "screenshareSetting" -> screenshareSetting
            "chatSetting" -> chatSetting
            else -> {
                Logger.e("CheckPermission", "Invalid permission type: $permissionType")
                return 2
            }
        }

        val permissionStatus = when (setting.lowercase()) {
            "allow" -> 0
            "approval" -> 1
            "disallow" -> 2
            else -> {
                Logger.e("CheckPermission", "Invalid permission setting: $setting")
                2
            }
        }

        permissionStatus
    } catch (error: Exception) {
        throw CheckPermissionException(
            "checkPermission error: ${error.message}",
            error
        )
    }
}
