package com.mediasfu.sdk.methods.stream_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.SwitchUserVideoOptions
import com.mediasfu.sdk.consumers.SwitchUserVideoParameters
import com.mediasfu.sdk.consumers.SwitchUserVideoType
import com.mediasfu.sdk.model.ShowAlert

/**
 * Parameters for switching the user's video device.
 */
interface SwitchVideoParameters : SwitchUserVideoParameters {
    // Core properties
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordStopped: Boolean
    val recordPaused: Boolean
    val recordingMediaOptions: String
    override val videoAlreadyOn: Boolean
    override val userDefaultVideoInputDevice: String
    override val defVideoID: String
    override val allowed: Boolean
    
    // Update functions
    override val updateDefVideoID: (String) -> Unit
    override val updatePrevVideoInputDevice: (String) -> Unit
    override val updateUserDefaultVideoInputDevice: (String) -> Unit
    val updateIsMediaSettingsModalVisible: (Boolean) -> Unit
    
    // Mediasfu function
    val switchUserVideo: SwitchUserVideoType
    
    // Optional alert
    override val showAlert: ShowAlert?
    
    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): SwitchVideoParameters
}

/**
 * Options for switching the user's video device.
 */
data class SwitchVideoOptions(
    val videoPreference: String,
    val parameters: SwitchVideoParameters
)

/**
 * Type definition for the switchVideo function.
 */
typealias SwitchVideoType = suspend (SwitchVideoOptions) -> Unit

/**
 * Switches the user's video device based on the provided video preference.
 * 
 * This function performs the following tasks:
 * - Checks if the room is audio-only and shows an alert if camera usage is restricted.
 * - Validates recording states to determine if video can be switched.
 * - Checks if camera access is allowed and prompts the user accordingly.
 * - Updates the default and previous video input devices.
 * - Calls the [switchUserVideo] function to perform the actual video device switching.
 * 
 * ### Parameters:
 * - [options] (`SwitchVideoOptions`): Contains the `videoPreference` and `parameters` required for switching video.
 * 
 * ### Example:
 * ```kotlin
 * val switchVideoOptions = SwitchVideoOptions(
 *     videoPreference = "newVideoDeviceID",
 *     parameters = object : SwitchVideoParameters {
 *         override val recordStarted: Boolean = true
 *         override val recordResumed: Boolean = false
 *         override val recordStopped: Boolean = false
 *         override val recordPaused: Boolean = false
 *         override val recordingMediaOptions: String = "video"
 *         override val videoAlreadyOn: Boolean = true
 *         override val userDefaultVideoInputDevice: String = "currentVideoDeviceID"
 *         override val defVideoID: String = "defaultVideoDeviceID"
 *         override val allowed: Boolean = true
 *         override val updateDefVideoID: (String) -> Unit = { deviceId -> setDefVideoID(deviceId) }
 *         override val updatePrevVideoInputDevice: (String) -> Unit = { deviceId -> setPrevVideoDevice(deviceId) }
 *         override val updateUserDefaultVideoInputDevice: (String) -> Unit = { deviceId -> setUserDefaultVideo(deviceId) }
 *         override val updateIsMediaSettingsModalVisible: (Boolean) -> Unit = { isVisible -> setMediaSettingsModal(isVisible) }
 *         override val showAlert: ShowAlert? = { message, type, duration ->
 *             showAlert(message, type, duration)
 *         }
 *         override val switchUserVideo: SwitchUserVideoType = switchUserVideoFunction
 *         // Other properties...
 *     }
 * )
 * 
 * switchVideo(switchVideoOptions)
 * ```
 */
suspend fun switchVideo(options: SwitchVideoOptions) {
    try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Destructure parameters for easier access
        val recordStarted = parameters.recordStarted
        val recordResumed = parameters.recordResumed
        val recordStopped = parameters.recordStopped
        val recordPaused = parameters.recordPaused
        val recordingMediaOptions = parameters.recordingMediaOptions
        val videoAlreadyOn = parameters.videoAlreadyOn
        var userDefaultVideoInputDevice = parameters.userDefaultVideoInputDevice
        var defVideoID = parameters.defVideoID
        val allowed = parameters.allowed
        
        // Callback functions to update state
        val updateDefVideoID = parameters.updateDefVideoID
        val updatePrevVideoInputDevice = parameters.updatePrevVideoInputDevice
        val updateUserDefaultVideoInputDevice = parameters.updateUserDefaultVideoInputDevice
        val updateIsMediaSettingsModalVisible = parameters.updateIsMediaSettingsModalVisible
        
        // mediasfu function to switch user video
        val switchUserVideo = parameters.switchUserVideo
        
        // Optional alert function
        val showAlert = parameters.showAlert
        
        // Check if recording is in progress and whether the selected video device is the default one
        var checkoff = false
        if ((recordStarted || recordResumed) && !recordStopped && !recordPaused) {
            if (recordingMediaOptions == "video") {
                checkoff = true
            }
        }
        
        // Check camera access permission
        if (!allowed) {
            showAlert?.invoke(
                message = "Allow access to your camera by starting it for the first time.",
                type = "danger",
                duration = 3000
            )
            return
        }
        
        // Check video state and display appropriate alert messages
        if (checkoff) {
            if (videoAlreadyOn) {
                showAlert?.invoke(
                    message = "Please turn off your video before switching.",
                    type = "danger",
                    duration = 3000
                )
                return
            }
        } else {
            if (!videoAlreadyOn) {
                showAlert?.invoke(
                    message = "Please turn on your video before switching.",
                    type = "danger",
                    duration = 3000
                )
                return
            }
        }
        
        // Set default video ID if not already set
        if (defVideoID.isEmpty()) {
            defVideoID = if (userDefaultVideoInputDevice.isNotEmpty()) {
                userDefaultVideoInputDevice
            } else {
                "default"
            }
            updateDefVideoID(defVideoID)
        }
        
        // Switch video only if the selected video device is different from the default
        if (options.videoPreference != defVideoID) {
            // Update previous video input device
            val prevVideoInputDevice = userDefaultVideoInputDevice
            updatePrevVideoInputDevice(prevVideoInputDevice)
            
            // Update current video input device
            userDefaultVideoInputDevice = options.videoPreference
            updateUserDefaultVideoInputDevice(userDefaultVideoInputDevice)
            
            // Hide media settings modal if visible
            updateIsMediaSettingsModalVisible(false)
            
            // Perform the video switch using the mediasfu function
            val optionsSwitch = SwitchUserVideoOptions(
                parameters = parameters,
                videoPreference = options.videoPreference,
                checkoff = checkoff
            )
            switchUserVideo(optionsSwitch)
        }
    } catch (error: Exception) {
        Logger.e("SwitchVideo", "switchVideo error: $error")
    }
}
