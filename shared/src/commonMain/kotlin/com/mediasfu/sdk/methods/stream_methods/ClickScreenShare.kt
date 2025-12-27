package com.mediasfu.sdk.methods.stream_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.CheckScreenShareOptions
import com.mediasfu.sdk.consumers.CheckScreenShareParameters
import com.mediasfu.sdk.consumers.CheckScreenShareType
import com.mediasfu.sdk.consumers.StopShareScreenOptions
import com.mediasfu.sdk.consumers.StopShareScreenParameters
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.datetime.Clock

/**
 * Parameters for ClickScreenShare function.
 */
interface ClickScreenShareParameters :
    CheckScreenShareParameters,
    StopShareScreenParameters {

    // Core properties
    override val showAlert: ShowAlert?
    override val roomName: String
    override val member: String
    override val socket: SocketManager?
    override val islevel: String
    val youAreCoHost: Boolean
    val adminRestrictSetting: Boolean
    val audioSetting: String
    val videoSetting: String
    val screenshareSetting: String
    val chatSetting: String
    val screenAction: Boolean
    val screenAlreadyOn: Boolean
    val screenRequestState: String?
    val screenRequestTime: Long?
    val audioOnlyRoom: Boolean
    val updateRequestIntervalSeconds: Int
    val transportCreated: Boolean

    // Update functions
    val updateScreenRequestState: (String?) -> Unit
    val updateScreenAlreadyOn: (Boolean) -> Unit

    // Mediasfu functions
    val checkPermission: CheckPermissionType
    val checkScreenShare: CheckScreenShareType

    override fun getUpdatedAllParams(): ClickScreenShareParameters
}

/**
 * Options for handling screen share actions.
 */
data class ClickScreenShareOptions(
    val parameters: ClickScreenShareParameters
)

/**
 * Type definition for the clickScreenShare function.
 */
typealias ClickScreenShareType = suspend (ClickScreenShareOptions) -> Unit

/**
 * Handles the action for the screen button, including starting and stopping screen sharing.
 * 
 * This function performs the following actions:
 * - Checks if the room is audio-only or a demo room and shows alerts accordingly.
 * - Toggles screen sharing based on the current status.
 * - Checks for admin restrictions and permissions before starting screen sharing.
 * - Sends requests to the host for screen sharing approval if necessary.
 * - Updates the UI and state based on the action taken.
 * 
 * Example:
 * ```kotlin
 * val options = ClickScreenShareOptions(
 *     parameters = object : ClickScreenShareParameters {
 *         override val showAlert: ShowAlert? = showAlertFunction
 *         override val roomName: String = "room123"
 *         override val member: String = "John Doe"
 *         override val socket: Socket? = socketInstance
 *         override val islevel: String = "1"
 *         override val youAreCoHost: Boolean = false
 *         override val adminRestrictSetting: Boolean = false
 *         override val audioSetting: String = "allow"
 *         override val videoSetting: String = "allow"
 *         override val screenshareSetting: String = "allow"
 *         override val chatSetting: String = "allow"
 *         override val screenAction: Boolean = false
 *         override val screenAlreadyOn: Boolean = false
 *         override val screenRequestState: String? = null
 *         override val screenRequestTime: Long? = Clock.System.now().toEpochMilliseconds()
 *         override val audioOnlyRoom: Boolean = false
 *         override val updateRequestIntervalSeconds: Int = 60
 *         override val updateScreenRequestState: (String?) -> Unit = setScreenRequestState
 *         override val updateScreenAlreadyOn: (Boolean) -> Unit = setScreenAlreadyOn
 *         override val checkPermission: CheckPermissionType = checkPermissionFunction
 *         override val checkScreenShare: CheckScreenShareType = checkScreenShareFunction
 *         override val stopShareScreen: StopShareScreenType = stopShareScreenFunction
 *         // Other properties...
 *     }
 * )
 * 
 * clickScreenShare(options)
 * ```
 */
suspend fun clickScreenShare(options: ClickScreenShareOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    try {
        val showAlert = parameters.showAlert
        val socket = parameters.socket
    val member = parameters.member
        val islevel = parameters.islevel
        val youAreCoHost = parameters.youAreCoHost
        val adminRestrictSetting = parameters.adminRestrictSetting
        val audioSetting = parameters.audioSetting
        val videoSetting = parameters.videoSetting
        val screenshareSetting = parameters.screenshareSetting
        val chatSetting = parameters.chatSetting
        val screenAction = parameters.screenAction
        var screenAlreadyOn = parameters.screenAlreadyOn
        var screenRequestState = parameters.screenRequestState
        val screenRequestTime = parameters.screenRequestTime
        val audioOnlyRoom = parameters.audioOnlyRoom
        val updateRequestIntervalSeconds = parameters.updateRequestIntervalSeconds
        val updateScreenRequestState = parameters.updateScreenRequestState
        val updateScreenAlreadyOn = parameters.updateScreenAlreadyOn
        val checkPermission = parameters.checkPermission
        val checkScreenShare = parameters.checkScreenShare
        val stopShareScreen = parameters.stopShareScreen
        
        // Check if the room is audio-only
        if (audioOnlyRoom) {
            showAlert?.invoke(
                "You cannot turn on your camera in an audio-only event.",
                "danger",
                3000
            )
            return
        }
        
        val roomName = parameters.roomName

        // Check if the room is a demo room
        if (roomName.lowercase().startsWith("d")) {
            showAlert?.invoke(
                message = "You cannot start screen share in a demo room.",
                type = "danger",
                duration = 3000
            )
            return
        }
        
        // Toggle screen sharing based on current status
        if (screenAlreadyOn) {
            screenAlreadyOn = false
            updateScreenAlreadyOn(screenAlreadyOn)
            stopShareScreen(StopShareScreenOptions(parameters))
        } else {
            // Check if screen sharing is restricted by the host
            if (adminRestrictSetting) {
                showAlert?.invoke(
                    message = "You cannot start screen share. Access denied by host.",
                    type = "danger",
                    duration = 3000
                )
                return
            }
            
            var response = 2
            // Check and turn on screen sharing
            if (!screenAction && islevel != "2" && !youAreCoHost) {
                val optionsCheck = CheckPermissionOptions(
                    permissionType = "screenshareSetting",
                    audioSetting = audioSetting,
                    videoSetting = videoSetting,
                    screenshareSetting = screenshareSetting,
                    chatSetting = chatSetting
                )
                response = checkPermission(optionsCheck)
            } else {
                response = 0
            }
            
            // Handle different responses
            when (response) {
                0 -> {
                    // Allow screen sharing - proceed directly without requiring mic/camera first
                    val optionsCheck = CheckScreenShareOptions(
                        parameters = parameters
                    )
                    checkScreenShare(optionsCheck)
                }
                1 -> {
                    // Approval required
                    // Check if a request is already pending
                    if (screenRequestState == "pending") {
                        showAlert?.invoke(
                            message = "A request is already pending. Please wait for the host to respond.",
                            type = "danger",
                            duration = 3000
                        )
                        return
                    }
                    
                    // Check if rejected and current time is less than requestIntervalSeconds
                    if (screenRequestState == "rejected" &&
                        screenRequestTime != null &&
                        Clock.System.now().toEpochMilliseconds() - screenRequestTime <
                        updateRequestIntervalSeconds * 1000L) {
                        showAlert?.invoke(
                            message = "You cannot send another request at this time.",
                            type = "danger",
                            duration = 3000
                        )
                        return
                    }
                    
                    // Send request to host
                    showAlert?.invoke(
                        message = "Your request has been sent to the host.",
                        type = "success",
                        duration = 3000
                    )
                    screenRequestState = "pending"
                    updateScreenRequestState(screenRequestState)
                    
                    // Create a request and send it to the host
                    val userRequest = mapOf(
                        "id" to socket?.id,
                        "name" to member,
                        "icon" to "fa-desktop"
                    )
                    socket?.emit("participantRequest", mapOf(
                        "userRequest" to userRequest,
                        "roomName" to roomName
                    ))
                }
                2 -> {
                    // Disallow screen sharing
                    showAlert?.invoke(
                        message = "You are not allowed to start screen share.",
                        type = "danger",
                        duration = 3000
                    )
                }
            }
        }
    } catch (error: Exception) {
        Logger.e("ClickScreenShare", "Error during screen share action: $error")
    }
}
