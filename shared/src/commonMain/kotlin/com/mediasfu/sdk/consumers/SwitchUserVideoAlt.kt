package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.coroutines.delay

typealias SwitchUserVideoAltType = suspend (SwitchUserVideoAltOptions) -> Unit

/**
 * Parameters for switching user video device (alternative implementation).
 */
typealias RequestPermissionCameraType = suspend () -> Boolean

interface SwitchUserVideoAltParameters : StreamSuccessVideoParameters {
    val audioOnlyRoom: Boolean
    val frameRate: Int
    val vidCons: VidCons
    override val showAlert: ShowAlert?
    val hasCameraPermission: Boolean
    val checkMediaPermission: Boolean
    val currentFacingMode: String
    val prevFacingMode: String
    override val device: WebRtcDevice?

    // Update functions
    val updateVideoSwitching: (Boolean) -> Unit
    val updateCurrentFacingMode: (String) -> Unit
    val updatePrevFacingMode: (String) -> Unit

    // Mediasfu functions
    val requestPermissionCamera: suspend () -> Boolean
    val streamSuccessVideo: suspend (StreamSuccessVideoOptions) -> Unit
    val clickVideo: suspend () -> Unit

    override fun getUpdatedAllParams(): SwitchUserVideoAltParameters
}

/**
 * Options for switching user video device (alternative method).
 *
 * @property videoPreference The preferred facing mode ('user' for front, 'environment' for back)
 * @property checkoff Boolean to bypass initial click checks
 * @property parameters Parameters for switching video
 */
data class SwitchUserVideoAltOptions(
    val videoPreference: String,
    val checkoff: Boolean,
    val parameters: SwitchUserVideoAltParameters
)

/**
 * Switches video input devices by facing mode (front/back camera).
 *
 * This implements the Flutter pattern with 3 fallback attempts:
 * 1. First attempt with facingMode + full constraints
 * 2. If fails, enumerate devices and try matching devices by label
 * 3. Third attempt with minimal facingMode only
 *
 * @param options Options for switching video device
 */
suspend fun switchUserVideoAlt(options: SwitchUserVideoAltOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    val audioOnlyRoom = parameters.audioOnlyRoom
    val frameRate = parameters.frameRate
    val vidCons = parameters.vidCons
    val showAlert = parameters.showAlert
    val hasCameraPermission = parameters.hasCameraPermission
    val checkMediaPermission = parameters.checkMediaPermission
    var currentFacingMode = parameters.currentFacingMode
    val prevFacingMode = parameters.prevFacingMode
    val device = parameters.device

    val updateVideoSwitching = parameters.updateVideoSwitching
    val updateCurrentFacingMode = parameters.updateCurrentFacingMode
    val updatePrevFacingMode = parameters.updatePrevFacingMode

    val requestPermissionCamera = parameters.requestPermissionCamera
    val streamSuccessVideo = parameters.streamSuccessVideo
    val clickVideo = parameters.clickVideo

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

        // Handle video toggle if not bypassed
        if (!options.checkoff) {
            clickVideo()
            updateVideoSwitching(true)
            delay(500)
            updateVideoSwitching(false)
        }

        // Check for camera permission
        if (!hasCameraPermission && checkMediaPermission) {
            val permissionGranted = requestPermissionCamera()
            if (!permissionGranted) {
                showAlert?.invoke(
                    "Allow access to your camera or check if it's not being used by another application.",
                    "danger",
                    3000
                )
                return
            }
        }

        if (device == null) {
            showAlert?.invoke(
                "Unable to access the camera. Please restart the call or app.",
                "danger",
                3000
            )
            return
        }

        // Save current facing mode as previous
        updatePrevFacingMode(currentFacingMode)
        updateVideoSwitching(true)

        // Attempt 1: Build constraints with facingMode
        val mediaConstraints = buildMediaConstraints(
            vidCons = vidCons,
            frameRate = frameRate,
            facingMode = videoPreference
        )

        try {
            val stream = device.getUserMedia(mediaConstraints)
            
            // Update facing mode on success
            updateCurrentFacingMode(videoPreference)
            
            val optionsStream = StreamSuccessVideoOptions(
                stream = stream,
                videoConstraints = mediaConstraints,
                parameters = parameters
            )
            streamSuccessVideo(optionsStream)
            updateVideoSwitching(false)
            
        } catch (error1: Exception) {
            Logger.e("SwitchUserVideoAlt", "MediaSFU - switchUserVideoAlt: Attempt 1 failed -> ${error1.message}")
            
            // Attempt 2: Enumerate devices and try matching ones
            try {
                val videoDevices = device.enumerateDevices()
                    .filter { it.kind == "videoinput" }
                
                val matchingDevices = findMatchingDevices(videoDevices, videoPreference)
                
                if (matchingDevices.isNotEmpty()) {
                    
                    for (deviceInfo in matchingDevices) {
                        val deviceConstraints = buildDeviceConstraints(
                            deviceId = deviceInfo.deviceId,
                            vidCons = vidCons,
                            frameRate = frameRate
                        )
                        
                        try {
                            val stream = device.getUserMedia(deviceConstraints)
                            
                            updateCurrentFacingMode(videoPreference)
                            
                            val optionsStream = StreamSuccessVideoOptions(
                                stream = stream,
                                videoConstraints = deviceConstraints,
                                parameters = parameters
                            )
                            streamSuccessVideo(optionsStream)
                            updateVideoSwitching(false)
                            return
                            
                        } catch (deviceError: Exception) {
                            Logger.e("SwitchUserVideoAlt", "MediaSFU - switchUserVideoAlt: Device '${deviceInfo.label}' failed -> ${deviceError.message}")
                            // Continue to next device
                        }
                    }
                }
                
                // If we get here, no matching devices worked - try Attempt 3
                throw Exception("No matching devices succeeded")
                
            } catch (error2: Exception) {
                Logger.e("SwitchUserVideoAlt", "MediaSFU - switchUserVideoAlt: Attempt 2 failed -> ${error2.message}")
                
                // Attempt 3: Minimal constraints with just facingMode
                val finalConstraints = buildFinalMediaConstraints(
                    vidCons = vidCons,
                    facingMode = videoPreference
                )
                
                try {
                    val stream = device.getUserMedia(finalConstraints)
                    
                    updateCurrentFacingMode(videoPreference)
                    
                    val optionsStream = StreamSuccessVideoOptions(
                        stream = stream,
                        videoConstraints = finalConstraints,
                        parameters = parameters
                    )
                    streamSuccessVideo(optionsStream)
                    updateVideoSwitching(false)
                    
                } catch (error3: Exception) {
                    Logger.e("SwitchUserVideoAlt", "MediaSFU - switchUserVideoAlt: Attempt 3 failed -> ${error3.message}")
                    
                    // All attempts failed - revert to previous facing mode
                    updateCurrentFacingMode(prevFacingMode)
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
        Logger.e("SwitchUserVideoAlt", "MediaSFU - switchUserVideoAlt error: ${error.message}")
        updateCurrentFacingMode(prevFacingMode)
        updateVideoSwitching(false)
        showAlert?.invoke(
            "Error switching video device. Please try again.",
            "danger",
            3000
        )
    }
}

/**
 * Finds devices matching the requested facing mode by checking device labels.
 */
private fun findMatchingDevices(
    videoDevices: List<MediaDeviceInfo>,
    videoPreference: String
): List<MediaDeviceInfo> {
    return videoDevices.filter { device ->
        val label = device.label?.lowercase() ?: ""
        when (videoPreference.lowercase()) {
            "user", "front" -> label.contains("front") || label.contains("user")
            "environment", "back" -> label.contains("back") || label.contains("rear") || label.contains("environment")
            else -> false
        }
    }
}

/**
 * Builds media constraints based on video preferences.
 */
private fun buildMediaConstraints(
    vidCons: VidCons,
    frameRate: Int,
    facingMode: String
): Map<String, Any> {
    val vidConsMap = vidCons.toMap()
    return mapOf(
        "video" to mapOf(
            "mandatory" to mapOf(
                "width" to vidConsMap["width"],
                "height" to vidConsMap["height"],
                "frameRate" to mapOf("ideal" to frameRate),
                "facingMode" to facingMode
            )
        ),
        "audio" to false
    )
}

/**
 * Builds constraints with specific device ID.
 */
private fun buildDeviceConstraints(
    deviceId: String,
    vidCons: VidCons,
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
 * Builds final media constraints with minimal requirements.
 */
private fun buildFinalMediaConstraints(
    vidCons: VidCons,
    facingMode: String
): Map<String, Any> {
    val vidConsMap = vidCons.toMap()
    return mapOf(
        "video" to mapOf(
            "mandatory" to mapOf(
                "width" to vidConsMap["width"],
                "height" to vidConsMap["height"],
                "facingMode" to facingMode
            )
        ),
        "audio" to false
    )
}

