package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.webrtc.WebRtcDevice

typealias SwitchUserVideoType = suspend (SwitchUserVideoOptions) -> Unit

/**
 * Parameters for switching user video device.
 */
interface SwitchUserVideoParameters : StreamSuccessVideoParameters {
    val audioOnlyRoom: Boolean
    val frameRate: Int
    val vidCons: VidCons
    val prevVideoInputDevice: String
    override val userDefaultVideoInputDevice: String
    override val showAlert: ShowAlert?
    val hasCameraPermission: Boolean
    val checkMediaPermission: Boolean
    val currentFacingMode: String
    override val device: WebRtcDevice?

    val updateVideoSwitching: (Boolean) -> Unit
    override val updateUserDefaultVideoInputDevice: (String) -> Unit
    val updatePrevVideoInputDevice: (String) -> Unit

    // Mediasfu functions
    val streamSuccessVideo: suspend (StreamSuccessVideoOptions) -> Unit
    val requestPermissionCamera: suspend () -> Unit

    override fun getUpdatedAllParams(): SwitchUserVideoParameters
}

/**
 * Options for switching user video device.
 *
 * @property videoPreference The preferred video device ID
 * @property checkoff Boolean to bypass initial click checks
 * @property parameters Parameters for switching video
 */
data class SwitchUserVideoOptions(
    val videoPreference: String,
    val checkoff: Boolean = false,
    val parameters: SwitchUserVideoParameters
)

/**
 * Toggles or switches the video stream based on user preferences and permission checks.
 *
 * Implements the same 3-attempt fallback pattern as Flutter:
 * 1. First attempt with device ID (sourceId) + constraints
 * 2. Second attempt with facingMode + constraints (no specific device)
 * 3. Third attempt with minimal facingMode only
 *
 * @param options Options containing video preference and parameters
 */
suspend fun switchUserVideo(options: SwitchUserVideoOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    val audioOnlyRoom = parameters.audioOnlyRoom
    val frameRate = parameters.frameRate
    val vidCons = parameters.vidCons
    val prevVideoInputDevice = parameters.prevVideoInputDevice
    var userDefaultVideoInputDevice = parameters.userDefaultVideoInputDevice
    val showAlert = parameters.showAlert
    val hasCameraPermission = parameters.hasCameraPermission
    val updateVideoSwitching = parameters.updateVideoSwitching
    val updateUserDefaultVideoInputDevice = parameters.updateUserDefaultVideoInputDevice
    val updatePrevVideoInputDevice = parameters.updatePrevVideoInputDevice
    val requestPermissionCamera = parameters.requestPermissionCamera
    val checkMediaPermission = parameters.checkMediaPermission
    val streamSuccessVideo = parameters.streamSuccessVideo
    val currentFacingMode = parameters.currentFacingMode
    val device = parameters.device
    val videoPreference = options.videoPreference

    try {
        // Check if audio-only room
        if (audioOnlyRoom) {
            showAlert?.invoke(
                "You cannot turn on your camera in an audio-only event.",
                "danger",
                3000
            )
            return
        }

        // Check for camera permission
        if (!hasCameraPermission && checkMediaPermission) {
            requestPermissionCamera()
            showAlert?.invoke(
                "Allow access to your camera or check if it's not being used by another application.",
                "danger",
                3000
            )
            return
        }

        if (device == null) {
            showAlert?.invoke(
                "Unable to access the camera. Please restart the call or app.",
                "danger",
                3000
            )
            return
        }

        // Save current device as previous before switching
        updatePrevVideoInputDevice(userDefaultVideoInputDevice)
        
        // Update video switching state
        updateVideoSwitching(true)

        // Build constraints with device ID (first attempt)
        val mediaConstraints = buildMediaConstraints(
            deviceId = videoPreference,
            vidCons = vidCons,
            facingMode = currentFacingMode,
            frameRate = frameRate
        )

        try {
            val stream = device.getUserMedia(mediaConstraints)
            
            // Update default device on success
            updateUserDefaultVideoInputDevice(videoPreference)
            
            val optionsStream = StreamSuccessVideoOptions(
                stream = stream,
                videoConstraints = mediaConstraints,
                parameters = parameters
            )
            streamSuccessVideo(optionsStream)
            updateVideoSwitching(false)
            
        } catch (error1: Exception) {
            Logger.e("SwitchUserVideo", "MediaSFU - switchUserVideo: Attempt 1 failed -> ${error1.message}")
            
            // Second attempt - without specific device ID
            val altConstraints = buildAltMediaConstraints(
                vidCons = vidCons,
                frameRate = frameRate,
                facingMode = currentFacingMode
            )
            
            try {
                val stream = device.getUserMedia(altConstraints)
                
                updateUserDefaultVideoInputDevice(videoPreference)
                
                val optionsStream = StreamSuccessVideoOptions(
                    stream = stream,
                    videoConstraints = altConstraints,
                    parameters = parameters
                )
                streamSuccessVideo(optionsStream)
                updateVideoSwitching(false)
                
            } catch (error2: Exception) {
                Logger.e("SwitchUserVideo", "MediaSFU - switchUserVideo: Attempt 2 failed -> ${error2.message}")
                
                // Third attempt - minimal constraints
                val finalConstraints = buildFinalMediaConstraints(
                    vidCons = vidCons,
                    facingMode = currentFacingMode
                )
                
                try {
                    val stream = device.getUserMedia(finalConstraints)
                    
                    updateUserDefaultVideoInputDevice(videoPreference)
                    
                    val optionsStream = StreamSuccessVideoOptions(
                        stream = stream,
                        videoConstraints = finalConstraints,
                        parameters = parameters
                    )
                    streamSuccessVideo(optionsStream)
                    updateVideoSwitching(false)
                    
                } catch (error3: Exception) {
                    Logger.e("SwitchUserVideo", "MediaSFU - switchUserVideo: Attempt 3 failed -> ${error3.message}")
                    
                    // All attempts failed - revert to previous device
                    updateUserDefaultVideoInputDevice(prevVideoInputDevice)
                    updateVideoSwitching(false)
                    
                    showAlert?.invoke(
                        "Error switching; not accessible, might need to turn off your video and turn it back on after switching.",
                        "danger",
                        3000
                    )
                }
            }
        }

    } catch (error: Exception) {
        Logger.e("SwitchUserVideo", "MediaSFU - switchUserVideo error: ${error.message}")
        updateUserDefaultVideoInputDevice(prevVideoInputDevice)
        updateVideoSwitching(false)
        showAlert?.invoke(
            "Error switching; not accessible, might need to turn off your video and turn it back on after switching.",
            "danger",
            3000
        )
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

