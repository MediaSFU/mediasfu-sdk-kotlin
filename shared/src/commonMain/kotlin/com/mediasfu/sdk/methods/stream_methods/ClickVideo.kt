package com.mediasfu.sdk.methods.stream_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.CheckPermissionException
import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.ConnectSendTransportParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoType
import com.mediasfu.sdk.consumers.RequestPermissionCameraType
import com.mediasfu.sdk.consumers.StreamSuccessVideoOptions
import com.mediasfu.sdk.consumers.StreamSuccessVideoParameters
import com.mediasfu.sdk.consumers.StreamSuccessVideoType
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.datetime.Clock

/**
 * Parameters for ClickVideo function.
 */
interface ClickVideoParameters : 
    DisconnectSendTransportVideoParameters,
    StreamSuccessVideoParameters,
    ConnectSendTransportParameters {
    
    val checkMediaPermission: Boolean
    val hasCameraPermission: Boolean
    override val videoAlreadyOn: Boolean
    val audioOnlyRoom: Boolean
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordPaused: Boolean
    val recordStopped: Boolean
    val recordingMediaOptions: String
    override val islevel: String
    val youAreCoHost: Boolean
    val adminRestrictSetting: Boolean
    val videoRequestState: String?
    val videoRequestTime: Long?
    override val member: String
    override val socket: SocketManager?
    override val roomName: String
    override val userDefaultVideoInputDevice: String
    val currentFacingMode: String
    val vidCons: VidCons
    val frameRate: Int
    override val videoAction: Boolean
    override val localStream: MediaStream?
    val audioSetting: String
    val videoSetting: String
    val screenshareSetting: String
    val chatSetting: String
    val updateRequestIntervalSeconds: Int
    override val showAlert: ShowAlert?
    override val updateVideoAlreadyOn: (Boolean) -> Unit
    val updateVideoRequestState: (String) -> Unit
    override val updateLocalStream: (MediaStream?) -> Unit
    val checkPermission: CheckPermissionType
    val streamSuccessVideo: StreamSuccessVideoType
    val disconnectSendTransportVideo: DisconnectSendTransportVideoType
    val requestPermissionCamera: RequestPermissionCameraType
    
    override fun getUpdatedAllParams(): ClickVideoParameters
}

/**
 * Options for ClickVideo function.
 */
data class ClickVideoOptions(
    val parameters: ClickVideoParameters
)

/**
 * Type definition for ClickVideo function.
 */
typealias ClickVideoType = suspend (ClickVideoOptions) -> Unit

/**
 * Toggles the video stream on or off based on the user's input and checks required permissions and constraints.
 * 
 * ### Parameters:
 * - `options` (`ClickVideoOptions`): Contains the parameters needed for toggling video, including:
 *   - `checkMediaPermission`: Boolean to verify media permission.
 *   - `hasCameraPermission`: Boolean indicating if camera permission is granted.
 *   - `videoAlreadyOn`: Boolean to check if the video is already on.
 *   - `audioOnlyRoom`: Boolean indicating if the room is audio-only.
 *   - `recordStarted`, `recordResumed`, `recordPaused`, `recordStopped`: Flags for recording state.
 *   - `recordingMediaOptions`: String defining the current recording mode ("video" or "audio").
 *   - `islevel`: User level (e.g., host, co-host).
 *   - `showAlert`: Optional function for displaying alerts.
 *   - `vidCons`: Video constraints for video width, height, etc.
 *   - `frameRate`: Preferred frame rate for video.
 *   - `userDefaultVideoInputDevice`: The device ID of the user's preferred video input device.
 *   - `currentFacingMode`: The facing mode for the camera (e.g., front or back).
 * 
 * ### Process:
 * 1. **Permission and Recording State Check**:
 *    - Checks if the user is in an audio-only room or if video toggling conflicts with ongoing recording.
 * 
 * 2. **Video Turn Off**:
 *    - If the video is already on, it disables the video tracks and disconnects the transport.
 * 
 * 3. **Video Turn On**:
 *    - Checks for admin or co-host restrictions before proceeding.
 *    - If permitted, requests camera access if not already granted, applies media constraints, and initiates the video stream.
 * 
 * ### Helper Functions:
 * - **buildMediaConstraints**: Builds media constraints using device ID and other parameters.
 * - **buildAltMediaConstraints**: Builds alternative media constraints when the preferred device is unavailable.
 * - **buildFinalMediaConstraints**: Builds final media constraints for the video stream.
 * - **attemptStream**: Attempts to initialize the video stream with the specified constraints.
 * 
 * ### Example Usage:
 * ```kotlin
 * val options = ClickVideoOptions(
 *     parameters = object : ClickVideoParameters {
 *         override val checkMediaPermission: Boolean = true
 *         override val hasCameraPermission: Boolean = false
 *         override val videoAlreadyOn: Boolean = false
 *         override val audioOnlyRoom: Boolean = false
 *         override val vidCons: VidCons = VidCons(width = 1280, height = 720)
 *         override val frameRate: Int = 30
 *         override val userDefaultVideoInputDevice: String = "front"
 *         override val currentFacingMode: String = "user"
 *         // Other properties...
 *     }
 * )
 * 
 * clickVideo(options)
 * ```
 * 
 * ### Error Handling:
 * - Handles permission issues by displaying alerts.
 * - Attempts to access alternative video devices if the preferred device is unavailable.
 * - Displays a message if camera access is denied or unavailable.
 */
suspend fun clickVideo(options: ClickVideoOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    val checkMediaPermission = parameters.checkMediaPermission
    val hasCameraPermission = parameters.hasCameraPermission
    var videoAlreadyOn = parameters.videoAlreadyOn
    val audioOnlyRoom = parameters.audioOnlyRoom
    val recordStarted = parameters.recordStarted
    val recordResumed = parameters.recordResumed
        val recordPaused = parameters.recordPaused
        val device = parameters.device
    val recordStopped = parameters.recordStopped
    val recordingMediaOptions = parameters.recordingMediaOptions
    val islevel = parameters.islevel
    val lockScreen = parameters.lockScreen
    val youAreCoHost = parameters.youAreCoHost
    val adminRestrictSetting = parameters.adminRestrictSetting
    val videoRequestState = parameters.videoRequestState
    val videoRequestTime = parameters.videoRequestTime
    val member = parameters.member
    val socket = parameters.socket
    val roomName = parameters.roomName
    val userDefaultVideoInputDevice = parameters.userDefaultVideoInputDevice
    val currentFacingMode = parameters.currentFacingMode
    val vidCons = parameters.vidCons
    val frameRate = parameters.frameRate
    val videoAction = parameters.videoAction
    var localStream = parameters.localStream
    val audioSetting = parameters.audioSetting
    val videoSetting = parameters.videoSetting
    val screenshareSetting = parameters.screenshareSetting
    val chatSetting = parameters.chatSetting
    val updateRequestIntervalSeconds = parameters.updateRequestIntervalSeconds
    val streamSuccessVideo = parameters.streamSuccessVideo
    val showAlert = parameters.showAlert
    val reorderStreams = parameters.reorderStreams
    val updateVideoAlreadyOn = parameters.updateVideoAlreadyOn
    val updateVideoRequestState = parameters.updateVideoRequestState
    val updateLocalStream = parameters.updateLocalStream
    
    val disconnectSendTransportVideo = parameters.disconnectSendTransportVideo
    val requestPermissionCamera = parameters.requestPermissionCamera
    val checkPermission = parameters.checkPermission

    
    if (audioOnlyRoom) {
        showAlert?.invoke(
            message = "You cannot turn on your camera in an audio-only event.",
            type = "danger",
            duration = 3000
        )
        return
    }
    
    if (videoAlreadyOn) {
        if (islevel == "2" && (recordStarted || recordResumed)) {
            if (!(recordPaused || recordStopped) && recordingMediaOptions == "video") {
                showAlert?.invoke(
                    message = "You cannot turn off your camera while recording video. Please pause or stop recording first.",
                    type = "danger",
                    duration = 3000
                )
                return
            }
        }
        
        videoAlreadyOn = false
        updateVideoAlreadyOn(videoAlreadyOn)
        updateLocalStream(localStream)
        val optionsDisconnect = DisconnectSendTransportVideoOptions(
            parameters = parameters
        )
        disconnectSendTransportVideo(optionsDisconnect)
        runCatching {
            reorderStreams(lockScreen, true)
        }
    } else {
        if (adminRestrictSetting) {
            showAlert?.invoke(
                message = "You cannot turn on your camera. Access denied by host.",
                duration = 3000,
                type = "danger"
            )
            return
        }
        
        var response = 2
        
        if (!videoAction && islevel != "2" && !youAreCoHost) {
            val optionsCheck = CheckPermissionOptions(
                permissionType = "videoSetting",
                audioSetting = audioSetting,
                videoSetting = videoSetting,
                screenshareSetting = screenshareSetting,
                chatSetting = chatSetting
            )
            response = try {
                checkPermission(optionsCheck)
            } catch (error: CheckPermissionException) {
                showAlert?.invoke(
                    message = error.message ?: "Permission check failed.",
                    type = "danger",
                    duration = 3000
                )
                2
            }
        } else {
            response = 0
        }
        
        when (response) {
            1 -> {
                if (videoRequestState == "pending") {
                    showAlert?.invoke(
                        message = "A request is pending. Please wait for the host to respond.",
                        type = "danger",
                        duration = 3000
                    )
                    return
                }
                
                if (videoRequestState == "rejected" &&
                    videoRequestTime != null &&
                    Clock.System.now().toEpochMilliseconds() - videoRequestTime <
                    updateRequestIntervalSeconds * 1000L) {
                    showAlert?.invoke(
                        message = "A request was rejected. Please wait $updateRequestIntervalSeconds seconds before sending another request.",
                        type = "danger",
                        duration = 3000
                    )
                    return
                }
                
                showAlert?.invoke(
                    message = "Request sent to host.",
                    type = "success",
                    duration = 3000
                )
                updateVideoRequestState("pending")
                socket?.emit("participantRequest", mapOf(
                    "userRequest" to mapOf(
                        "id" to socket.id,
                        "name" to member,
                        "icon" to "fa-video"
                    ),
                    "roomName" to roomName
                ))
            }
            2 -> {
                showAlert?.invoke(
                    message = "You cannot turn on your camera. Access denied by host.",
                    type = "danger",
                    duration = 3000
                )
            }
            else -> {
                if (!hasCameraPermission && checkMediaPermission) {
                    val statusCamera = requestPermissionCamera()
                    if (!statusCamera) {
                        showAlert?.invoke(
                            message = "Allow access to your camera or check if your camera is not being used by another application.",
                            type = "danger",
                            duration = 3000
                        )
                        return
                    }
                }
                
                var mediaConstraints = if (userDefaultVideoInputDevice.isNotEmpty()) {
                    buildMediaConstraints(userDefaultVideoInputDevice, vidCons, currentFacingMode, frameRate)
                } else {
                    buildAltMediaConstraints(vidCons, frameRate, currentFacingMode)
                }
                
                try {
                    attemptStream(mediaConstraints, streamSuccessVideo, parameters, showAlert)
                } catch (error: Exception) {
                    Logger.e("ClickVideo", "MediaSFU - clickVideo: primary constraints failed -> ${error.message}")
                    mediaConstraints = buildAltMediaConstraints(vidCons, frameRate, currentFacingMode)
                    try {
                        attemptStream(mediaConstraints, streamSuccessVideo, parameters, showAlert)
                    } catch (error: Exception) {
                        Logger.e("ClickVideo", "MediaSFU - clickVideo: alt constraints failed -> ${error.message}")
                        mediaConstraints = buildFinalMediaConstraints(vidCons, currentFacingMode)
                        try {
                            attemptStream(mediaConstraints, streamSuccessVideo, parameters, showAlert)
                        } catch (error: Exception) {
                            Logger.e("ClickVideo", "MediaSFU - clickVideo: fallback constraints failed -> ${error.message}")
                            showAlert?.invoke(
                                message = "Allow access to your camera or check if it is not being used by another application.",
                                type = "danger",
                                duration = 3000
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Builds media constraints using device ID and other parameters.
 */
private fun buildMediaConstraints(
    deviceId: String,
    vidCons: VidCons,
    facingMode: String?,
    frameRate: Int
): Map<String, Any> {
    val vidConsMap = vidCons.toMap()
    
    return mapOf(
        "video" to mapOf(
            "mandatory" to mapOf(
                "sourceId" to deviceId,
                "facingMode" to (facingMode ?: "user"),
                "width" to vidConsMap["width"],
                "height" to vidConsMap["height"],
                "frameRate" to mapOf("ideal" to frameRate)
            )
        ),
        "audio" to false
    )
}

/**
 * Builds alternative media constraints when the preferred device is unavailable.
 */
private fun buildAltMediaConstraints(
    vidCons: VidCons,
    frameRate: Int,
    facingMode: String?
): Map<String, Any> {
    val vidConsMap = vidCons.toMap()
    
    return mapOf(
        "video" to mapOf(
            "mandatory" to mapOf(
                "width" to vidConsMap["width"],
                "height" to vidConsMap["height"],
                "frameRate" to mapOf("ideal" to frameRate),
                "facingMode" to (facingMode ?: "user")
            )
        ),
        "audio" to false
    )
}

/**
 * Builds final media constraints for the video stream.
 */
private fun buildFinalMediaConstraints(
    vidCons: VidCons,
    facingMode: String?
): Map<String, Any> {
    val vidConsMap = vidCons.toMap()
    
    return mapOf(
        "video" to mapOf(
            "mandatory" to mapOf(
                "width" to vidConsMap["width"],
                "height" to vidConsMap["height"],
                "facingMode" to (facingMode ?: "user")
            )
        ),
        "audio" to false
    )
}

/**
 * Attempts to initialize the video stream with the specified constraints.
 */
private suspend fun attemptStream(
    mediaConstraints: Map<String, Any>,
    streamSuccessVideo: StreamSuccessVideoType,
    parameters: ClickVideoParameters,
    showAlert: ShowAlert?
) {
    try {
        val device = parameters.device
        if (device == null) {
            showAlert?.invoke(
                message = "Unable to access the camera. Please restart the call or app.",
                type = "danger",
                duration = 3000
            )
            return
        }
        val stream = device.getUserMedia(mediaConstraints)
        val optionsStream = StreamSuccessVideoOptions(
            stream = stream,
            videoConstraints = mediaConstraints,
            parameters = parameters
        )
        streamSuccessVideo(optionsStream)
    } catch (error: Exception) {
        Logger.e("ClickVideo", "MediaSFU - clickVideo: getUserMedia failed -> ${error.message}")
        throw error
    }
}
