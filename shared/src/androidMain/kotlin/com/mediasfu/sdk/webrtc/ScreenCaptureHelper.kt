package com.mediasfu.sdk.webrtc

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log

private const val TAG = "MediaSFU-ScreenCapture"

/**
 * Helper utilities for screen capture permission flow.
 * 
 * This object provides utility methods to simplify the process of requesting 
 * MediaProjection permission and building constraints for `WebRtcDevice.getDisplayMedia()`.
 * 
 * ## Usage in Activity:
 * ```kotlin
 * class MyActivity : AppCompatActivity() {
 *     companion object {
 *         const val SCREEN_CAPTURE_REQUEST_CODE = 1001
 *     }
 *     
 *     private fun startScreenShare() {
 *         // Request permission
 *         ScreenCaptureHelper.requestPermission(this, SCREEN_CAPTURE_REQUEST_CODE)
 *     }
 *     
 *     override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
 *         super.onActivityResult(requestCode, resultCode, data)
 *         if (requestCode == SCREEN_CAPTURE_REQUEST_CODE) {
 *             if (resultCode == RESULT_OK && data != null) {
 *                 val permissionData = ScreenCapturePermissionData(resultCode, data)
 *                 val constraints = ScreenCaptureHelper.buildConstraints(permissionData)
 *                 
 *                 lifecycleScope.launch {
 *                     try {
 *                         val stream = webRtcDevice.getDisplayMedia(constraints)
 *                         // Use stream for screen sharing
 *                     } catch (e: Exception) {
 *                         // Handle error
 *                     }
 *                 }
 *             } else {
 *                 // User denied permission
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * ## Usage with ActivityResultLauncher (Recommended):
 * ```kotlin
 * class MyActivity : ComponentActivity() {
 *     private val screenCaptureLauncher = registerForActivityResult(
 *         ActivityResultContracts.StartActivityForResult()
 *     ) { result ->
 *         if (result.resultCode == RESULT_OK && result.data != null) {
 *             val permissionData = ScreenCapturePermissionData(result.resultCode, result.data!!)
 *             // Use permissionData with getDisplayMedia
 *         }
 *     }
 *     
 *     private fun startScreenShare() {
 *         val intent = ScreenCaptureHelper.createPermissionIntent(this)
 *         screenCaptureLauncher.launch(intent)
 *     }
 * }
 * ```
 */
object ScreenCaptureHelper {
    
    /**
     * Creates the Intent to request screen capture permission.
     * 
     * Use this with startActivityForResult() or ActivityResultLauncher.
     * 
     * @param context The context to use for getting MediaProjectionManager
     * @return Intent to launch for screen capture permission
     */
    fun createPermissionIntent(context: Context): Intent {
        val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) 
            as MediaProjectionManager
        return mediaProjectionManager.createScreenCaptureIntent()
    }
    
    /**
     * Requests screen capture permission using deprecated startActivityForResult.
     * 
     * For modern apps, prefer using ActivityResultLauncher with createPermissionIntent().
     * 
     * @param activity The activity to use for the request
     * @param requestCode The request code to use for onActivityResult
     */
    @Suppress("DEPRECATION")
    fun requestPermission(activity: Activity, requestCode: Int) {
        val intent = createPermissionIntent(activity)
        Log.d(TAG, "Launching screen capture permission request")
        activity.startActivityForResult(intent, requestCode)
    }
    
    /**
     * Builds constraints map for getDisplayMedia from permission data.
     * 
     * @param permissionData The permission data obtained from onActivityResult
     * @param width Desired capture width (default: 1920)
     * @param height Desired capture height (default: 1080)
     * @param frameRate Desired frame rate (default: 15)
     * @param maxFrameRate Maximum frame rate (default: 30)
     * @return Map suitable for passing to WebRtcDevice.getDisplayMedia()
     */
    fun buildConstraints(
        permissionData: ScreenCapturePermissionData,
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Int = 15,
        maxFrameRate: Int = 30
    ): Map<String, Any?> {
        return mapOf(
            "mediaProjection" to mapOf(
                "resultCode" to permissionData.resultCode,
                "data" to permissionData.data
            ),
            "video" to mapOf(
                "width" to mapOf("ideal" to width),
                "height" to mapOf("ideal" to height),
                "frameRate" to mapOf("ideal" to frameRate, "max" to maxFrameRate)
            ),
            "audio" to false
        )
    }
    
    /**
     * Builds constraints map directly from onActivityResult parameters.
     * 
     * @param resultCode The resultCode from onActivityResult
     * @param data The data Intent from onActivityResult
     * @param width Desired capture width (default: 1920)
     * @param height Desired capture height (default: 1080)
     * @param frameRate Desired frame rate (default: 15)
     * @param maxFrameRate Maximum frame rate (default: 30)
     * @return Map suitable for passing to WebRtcDevice.getDisplayMedia()
     * @throws IllegalArgumentException if data is null
     */
    fun buildConstraints(
        resultCode: Int,
        data: Intent?,
        width: Int = 1920,
        height: Int = 1080,
        frameRate: Int = 15,
        maxFrameRate: Int = 30
    ): Map<String, Any?> {
        requireNotNull(data) { "data Intent cannot be null" }
        return buildConstraints(
            ScreenCapturePermissionData(resultCode, data),
            width, height, frameRate, maxFrameRate
        )
    }
    
    /**
     * Checks if screen capture is supported on this device.
     * 
     * Screen capture requires Android 5.0 (API 21) or higher.
     * 
     * @return true if screen capture is supported
     */
    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    }
    
    /**
     * Validates that the result from onActivityResult represents successful permission.
     * 
     * @param resultCode The resultCode from onActivityResult
     * @param data The data Intent from onActivityResult
     * @return true if permission was granted
     */
    fun isPermissionGranted(resultCode: Int, data: Intent?): Boolean {
        return resultCode == Activity.RESULT_OK && data != null
    }
}

/**
 * Data class holding the MediaProjection permission result.
 * 
 * This data is required to start screen capture via getDisplayMedia().
 * 
 * @property resultCode The result code from the permission activity (should be RESULT_OK)
 * @property data The Intent containing the MediaProjection token
 */
data class ScreenCapturePermissionData(
    val resultCode: Int,
    val data: Intent
)

/**
 * Exception thrown when the user denies screen capture permission.
 */
class ScreenCapturePermissionDeniedException(message: String) : Exception(message)
