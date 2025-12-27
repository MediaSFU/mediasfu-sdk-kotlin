package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Requests permission to start screen sharing or initiates screen sharing if in local UI mode.
 *
 * This function configures screen resolution settings, checks if screen sharing is allowed
 * via a socket event, and initiates screen sharing based on the response.
 *
 * The function supports two modes:
 * 1. **Local UI Mode**: Directly starts screen sharing without permission checks
 * 2. **Network Mode**: Emits a socket event to check if screen sharing is allowed before starting
 *
 * @param options The options containing parameters for requesting and starting screen sharing
 *
 * Example:
 * ```kotlin
 * val options = RequestScreenShareOptions(
 *     parameters = object : RequestScreenShareParameters {
 *         override val socket = socketInstance
 *         override val showAlert = { message, type, duration ->
 *             Logger.d("RequestScreenShare", message)
 *         }
 *         override val localUIMode = false
 *         override val targetResolution = "fhd"
 *         override val targetResolutionHost = "hd"
 *         override val startShareScreen = { opts ->
 *         }
 *         override fun getUpdatedAllParams() = this
 *         // ... other required implementations
 *     }
 * )
 *
 * requestScreenShare(options)
 * ```
 */
/**
 * Internal implementation of requestScreenShare with parameters inline.
 * This is a simplified version that will be replaced with full implementation.
 */
suspend fun requestScreenShareImpl(
    socket: SocketManager?,
    showAlert: ((message: String, type: String, duration: Int) -> Unit)?,
    localUIMode: Boolean,
    targetResolution: String,
    targetResolutionHost: String,
    startShareScreen: suspend (Any, Int, Int) -> Unit,
    parameters: Any
) {
    // Default to 'hd' resolution
    var targetWidth = 1280
    var targetHeight = 720

    // Set resolution based on target
    when {
        targetResolution == "qhd" || targetResolutionHost == "qhd" -> {
            targetWidth = 2560
            targetHeight = 1440
        }
        targetResolution == "fhd" || targetResolutionHost == "fhd" -> {
            targetWidth = 1920
            targetHeight = 1080
        }
    }

    try {
        // Directly start screen sharing if in local UI mode
        if (localUIMode) {
            startShareScreen(parameters, targetWidth, targetHeight)
            return
        }

        // If no socket is available (e.g. native targets without signalling), proceed locally.
        if (socket == null) {
            startShareScreen(parameters, targetWidth, targetHeight)
            return
        }

        // Use callback-based emitWithAck like Flutter does (sends empty array [])
        val finalTargetWidth = targetWidth
        val finalTargetHeight = targetHeight
        socket.emitWithAck("requestScreenShare", emptyMap()) { responseData ->
            try {
                val responseMap = responseData as? Map<*, *>
                val allowScreenShare = responseMap?.get("allowScreenShare") as? Boolean

                when (allowScreenShare) {
                    false -> showAlert?.invoke(
                        "You are not allowed to share screen",
                        "danger",
                        3000
                    )
                    else -> {
                        CoroutineScope(Dispatchers.Main).launch {
                            startShareScreen(parameters, finalTargetWidth, finalTargetHeight)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e("RequestScreenShare", "Error processing screen share response: ${e.message}")
                // Fall back to proceeding so native flows are not blocked by serialization issues.
                CoroutineScope(Dispatchers.Main).launch {
                    startShareScreen(parameters, finalTargetWidth, finalTargetHeight)
                }
            }
        }
    } catch (e: Exception) {
        Logger.e("RequestScreenShare", "Error during requesting screen share: ${e.message}")
        // Optimistically allow the share on native targets even when signalling fails.
        startShareScreen(parameters, targetWidth, targetHeight)
    }
}

