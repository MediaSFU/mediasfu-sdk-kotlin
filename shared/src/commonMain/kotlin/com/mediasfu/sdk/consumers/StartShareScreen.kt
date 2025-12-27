// StartShareScreen.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.*
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock

/**
 * Callback type for requesting screen capture permission.
 * Returns a Map containing "resultCode" (Int) and "data" (platform Intent) on success,
 * or null if permission was denied or not available.
 * 
 * On Android, the app should:
 * 1. Call MediaProjectionManager.createScreenCaptureIntent()
 * 2. Launch the intent for activity result
 * 3. Return mapOf("resultCode" to resultCode, "data" to data) on success
 * 4. Return null on failure or permission denial
 */
typealias RequestScreenCapturePermissionType = suspend () -> Map<String, Any?>?

/**
 * Parameters interface for starting screen sharing.
 */
interface StartShareScreenParameters {
    val shared: Boolean
    val showAlert: Any? // ShowAlert
    val onWeb: Boolean
    val device: WebRtcDevice? // WebRTC device for screen capture
    
    /**
     * Callback to request screen capture permission from the platform.
     * This is required on Android to get MediaProjection permission.
     * Returns permission data or null if denied.
     */
    val requestScreenCapturePermission: RequestScreenCapturePermissionType?
    
    // Update functions
    fun updateShared(shared: Boolean)
    
    // MediaSFU functions
    suspend fun streamSuccessScreen(options: StreamSuccessScreenOptions): Result<Unit>
    
    // Get updated parameters
    fun getUpdatedAllParams(): StartShareScreenParameters
}

/**
 * Options for starting screen sharing.
 * 
 * @property parameters The parameters for screen sharing
 * @property targetWidth Desired capture width (default: 1920)
 * @property targetHeight Desired capture height (default: 1080)
 * @property mediaProjectionData Platform-specific data for screen capture permission.
 *                                On Android, this should contain "resultCode" and "data" from
 *                                MediaProjectionManager permission result.
 *                                Use ScreenCaptureHelper on Android to obtain this data.
 */
data class StartShareScreenOptions(
    val parameters: StartShareScreenParameters,
    val targetWidth: Int? = null,
    val targetHeight: Int? = null,
    val mediaProjectionData: Map<String, Any?>? = null
)

/**
 * Exception thrown when starting screen sharing fails.
 */
class StartShareScreenException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Starts the screen sharing process.
 *
 * This function initiates screen sharing by requesting screen capture permissions
 * and setting up the necessary WebRTC components. It handles platform-specific
 * considerations and provides appropriate feedback to the user.
 *
 * ## Features:
 * - Screen capture permission handling
 * - Platform-specific screen sharing setup
 * - WebRTC screen stream creation
 * - Error handling and user feedback
 * - Success callback triggering
 *
 * ## Parameters:
 * - [options] Configuration options for starting screen sharing
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Android Usage:
 * ```kotlin
 * // In your Activity, use ScreenCaptureHelper to get permission:
 * val screenCaptureHelper = ScreenCaptureHelper.create(activity)
 * val permissionData = screenCaptureHelper.requestPermission()
 * 
 * val options = StartShareScreenOptions(
 *     parameters = myParameters,
 *     targetWidth = 1920,
 *     targetHeight = 1080,
 *     mediaProjectionData = mapOf(
 *         "resultCode" to permissionData.resultCode,
 *         "data" to permissionData.data
 *     )
 * )
 *
 * val result = startShareScreen(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("StartShareScreen", "Error starting screen sharing: ${error.message}")
 * }
 * ```
 */
suspend fun startShareScreen(
    options: StartShareScreenOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val targetWidth = options.targetWidth ?: 1920
        val targetHeight = options.targetHeight ?: 1080
        
        // Check if already sharing
        if (parameters.shared) {
            return Result.failure(
                StartShareScreenException("Screen sharing is already active")
            )
        }
        
        // Get mediaProjectionData - either from options or by requesting permission
        val mediaProjectionData = options.mediaProjectionData ?: run {
            // Try to request permission via callback if available
            val requestPermission = parameters.requestScreenCapturePermission
            if (requestPermission != null) {
                val permissionResult = requestPermission()
                if (permissionResult == null) {
                    showAlert(parameters, "Screen capture permission denied")
                    return Result.failure(
                        StartShareScreenException("Screen capture permission denied by user")
                    )
                }
                permissionResult
            } else {
                null // Will be handled in requestScreenCapture
            }
        }
        
        // Request screen capture (platform-specific implementation via WebRtcDevice)
        val screenCaptureResult = requestScreenCapture(
            device = parameters.device,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            mediaProjectionData = mediaProjectionData
        )
        screenCaptureResult.fold(
            onSuccess = { screenStream ->
                if (screenStream == null) {
                    // Screen capture not yet implemented or permission denied
                    showAlert(parameters, "Screen sharing requires platform-specific setup")
                    return Result.failure(
                        StartShareScreenException("Screen capture not available - platform implementation required")
                    )
                }
                
                // Update shared state
                parameters.updateShared(true)
                
                // Call stream success screen
                val streamSuccessOptions = StreamSuccessScreenOptions(
                    stream = screenStream,
                    parameters = parameters as StreamSuccessScreenParameters
                )
                val streamSuccessResult = parameters.streamSuccessScreen(streamSuccessOptions)
                streamSuccessResult.fold(
                    onSuccess = {
                        Result.success(Unit)
                    },
                    onFailure = { error ->
                        parameters.updateShared(false)
                        Result.failure(
                            StartShareScreenException(
                                "Failed to setup screen stream: ${error.message}",
                                error
                            )
                        )
                    }
                )
            },
            onFailure = { error ->
                showAlert(parameters, "Failed to start screen sharing: ${error.message}")
                Result.failure(
                    StartShareScreenException(
                        "Screen capture failed: ${error.message}",
                        error
                    )
                )
            }
        )
    } catch (error: Exception) {
        Result.failure(
            StartShareScreenException(
                "startShareScreen error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Requests screen capture from the platform using getDisplayMedia.
 * 
 * On Android, this requires MediaProjection API setup. Use ScreenCaptureHelper
 * to obtain the mediaProjectionData containing resultCode and Intent.
 * 
 * On iOS, this requires ReplayKit integration (not yet implemented).
 * 
 * On Web, this uses the navigator.mediaDevices.getDisplayMedia() API.
 * 
 * @param device The WebRTC device to use for capture
 * @param targetWidth Desired capture width
 * @param targetHeight Desired capture height
 * @param mediaProjectionData Platform-specific permission data (required on Android)
 */
private suspend fun requestScreenCapture(
    device: WebRtcDevice?,
    targetWidth: Int,
    targetHeight: Int,
    mediaProjectionData: Map<String, Any?>?
): Result<MediaStream?> {
    return try {
        if (device == null) {
            return Result.failure(
                StartShareScreenException("WebRTC device not initialized")
            )
        }
        
        // Build constraints for screen capture
        val constraints = mutableMapOf<String, Any?>(
            "video" to mapOf(
                "width" to mapOf("ideal" to targetWidth),
                "height" to mapOf("ideal" to targetHeight),
                "frameRate" to mapOf("ideal" to 15, "max" to 30),
                "displaySurface" to "monitor"
            ),
            "audio" to false // Screen audio can be added later if needed
        )
        
        // Add MediaProjection data if provided (required on Android)
        if (mediaProjectionData != null) {
            constraints["mediaProjection"] = mediaProjectionData
        }
        
        
        // Use getDisplayMedia to capture screen
        val screenStream = device.getDisplayMedia(constraints)
        
        Result.success(screenStream)
    } catch (error: UnsupportedOperationException) {
        // Platform doesn't support screen capture yet
        Logger.e("StartShareScreen", "Screen capture not supported: ${error.message}")
        Result.failure(
            StartShareScreenException(
                "Screen capture not supported on this platform: ${error.message}",
                error
            )
        )
    } catch (error: IllegalArgumentException) {
        // Missing required permission data (e.g., on Android without MediaProjection)
        Logger.e("StartShareScreen", "Screen capture permission data missing: ${error.message}")
        Result.failure(
            StartShareScreenException(
                "Screen capture requires permission. On Android, use ScreenCaptureHelper to obtain permission first.",
                error
            )
        )
    } catch (error: Exception) {
        Result.failure(
            StartShareScreenException(
                "Screen capture request failed: ${error.message}",
                error
            )
        )
    }
}

/**
 * Shows an alert to the user.
 */
private fun showAlert(parameters: StartShareScreenParameters, message: String) {
    try {
        // TODO: Implement platform-specific alert showing
        // This would use platform-specific UI components to show alerts
        
        
        // If showAlert function is available, call it
        // parameters.showAlert?.show(message)
    } catch (error: Exception) {
        Logger.e("StartShareScreen", "Error showing alert: ${error.message}")
    }
}
