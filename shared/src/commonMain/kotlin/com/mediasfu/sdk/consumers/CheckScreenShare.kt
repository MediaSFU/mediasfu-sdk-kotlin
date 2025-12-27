package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Parameters for checking screen sharing status and managing screen share actions.
 */
import com.mediasfu.sdk.model.ShowAlert

fun interface CheckScreenShareType {
    suspend operator fun invoke(options: CheckScreenShareOptions)
}

fun interface RequestScreenShareType {
    suspend operator fun invoke(options: RequestScreenShareOptions)
}

interface CheckScreenShareParameters : StopShareScreenParameters, RequestScreenShareParameters {
    override val shared: Boolean
    override val whiteboardStarted: Boolean
    override val whiteboardEnded: Boolean
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    val showAlert: ShowAlert?

    val stopShareScreen: StopShareScreenType
    val requestScreenShare: RequestScreenShareType
}

/**
 * Options for the checkScreenShare function.
 *
 * @property parameters The parameters containing screen share state and actions
 */
data class CheckScreenShareOptions(
    val parameters: CheckScreenShareParameters
)

/**
 * Checks and manages screen sharing status, initiating or stopping screen share actions based
 * on conditions.
 *
 * This function verifies whether screen sharing is currently active. If sharing is active, it
 * attempts to stop the screen share unless the whiteboard is active, in which case an alert is
 * shown. If not sharing, it initiates screen share unless a breakout room or whiteboard is active,
 * with alerts as needed.
 *
 * @param options The options containing parameters for managing screen sharing
 *
 * Example:
 * ```kotlin
 * val options = CheckScreenShareOptions(
 *     parameters = object : CheckScreenShareParameters {
 *         override val shared = true
 *         override val whiteboardStarted = false
 *         override val whiteboardEnded = true
 *         override val breakOutRoomStarted = false
 *         override val breakOutRoomEnded = true
 *         override val showAlert = { message, type, duration ->
 *         }
 *         override val stopShareScreen = { opts ->
 *         }
 *         override val requestScreenShare = { opts ->
 *         }
 *         // ... other required implementations
 *     }
 * )
 *
 * checkScreenShare(options)
 * ```
 */
suspend fun checkScreenShare(options: CheckScreenShareOptions) {
    val parameters = options.parameters

    try {
        val shared = parameters.shared
        val whiteboardStarted = parameters.whiteboardStarted
        val whiteboardEnded = parameters.whiteboardEnded
        val breakOutRoomStarted = parameters.breakOutRoomStarted
        val breakOutRoomEnded = parameters.breakOutRoomEnded
        val showAlert = parameters.showAlert
        val stopShareScreen = parameters.stopShareScreen
        val requestScreenShare = parameters.requestScreenShare

        // Stop screen share if already shared or request screen share if not shared
        if (shared) {
            if (whiteboardStarted && !whiteboardEnded) {
                showAlert?.invoke(
                    "Screen share is not allowed when whiteboard is active",
                    "danger",
                    3000
                )
                return
            }
            val stopOptions = StopShareScreenOptions(parameters = parameters)
            stopShareScreen(stopOptions)
        } else {
            // Can't share if breakout room is active
            if (breakOutRoomStarted && !breakOutRoomEnded) {
                showAlert?.invoke(
                    "Screen share is not allowed when breakout room is active",
                    "danger",
                    3000
                )
                return
            }

            if (whiteboardStarted && !whiteboardEnded) {
                showAlert?.invoke(
                    "Screen share is not allowed when whiteboard is active",
                    "danger",
                    3000
                )
                return
            }
            val requestOptions = RequestScreenShareOptions(parameters = parameters)
            requestScreenShare(requestOptions)
        }
    } catch (e: Exception) {
        Logger.e("CheckScreenShare", "MediaSFU - checkScreenShare error: ${e.message}")
        e.printStackTrace()
    }
}

/**
 * Parameters required for requesting screen sharing.
 */
interface RequestScreenShareParameters

/**
 * Options for requesting screen sharing.
 */
data class RequestScreenShareOptions(
    val parameters: CheckScreenShareParameters
)

