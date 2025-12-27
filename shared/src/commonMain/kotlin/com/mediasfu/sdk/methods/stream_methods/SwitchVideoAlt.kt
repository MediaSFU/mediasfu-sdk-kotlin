package com.mediasfu.sdk.methods.stream_methods

import com.mediasfu.sdk.consumers.SwitchUserVideoAltOptions
import com.mediasfu.sdk.consumers.SwitchUserVideoAltParameters
import com.mediasfu.sdk.consumers.SwitchUserVideoAltType
import com.mediasfu.sdk.model.ShowAlert

/**
 * Parameters for SwitchVideoAlt function.
 */
interface SwitchVideoAltParameters : SwitchUserVideoAltParameters {
    // Core properties
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordStopped: Boolean
    val recordPaused: Boolean
    val recordingMediaOptions: String
    override val videoAlreadyOn: Boolean
    override val currentFacingMode: String
    override val prevFacingMode: String
    override val allowed: Boolean
    override val audioOnlyRoom: Boolean
    
    // Update functions
    override val updateCurrentFacingMode: (String) -> Unit
    override val updatePrevFacingMode: (String) -> Unit
    val updateIsMediaSettingsModalVisible: (Boolean) -> Unit
    
    // Optional alert
    override val showAlert: ShowAlert?
    
    // Mediasfu function
    val switchUserVideoAlt: SwitchUserVideoAltType
    
    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): SwitchVideoAltParameters
}

/**
 * Options for switching the user's video with alternate logic.
 */
data class SwitchVideoAltOptions(
    val parameters: SwitchVideoAltParameters
)

/**
 * Type definition for SwitchVideoAlt function.
 */
typealias SwitchVideoAltType = suspend (SwitchVideoAltOptions) -> Unit

/**
 * Switches the user's video device with alternate logic, taking into account recording state and camera access permissions.
 * 
 * ### Parameters:
 * - [options] (`SwitchVideoAltOptions`): Contains the `parameters` required for switching video.
 * 
 * ### Example:
 * ```kotlin
 * val switchVideoAltOptions = SwitchVideoAltOptions(
 *     parameters = object : SwitchVideoAltParameters {
 *         override val recordStarted: Boolean = true
 *         override val recordResumed: Boolean = false
 *         override val recordStopped: Boolean = false
 *         override val recordPaused: Boolean = false
 *         override val recordingMediaOptions: String = "video"
 *         override val videoAlreadyOn: Boolean = true
 *         override val currentFacingMode: String = "user"
 *         override val prevFacingMode: String = "environment"
 *         override val allowed: Boolean = true
 *         override val audioOnlyRoom: Boolean = false
 *         override val updateCurrentFacingMode: (String) -> Unit = { mode -> setCurrentFacingMode(mode) }
 *         override val updatePrevFacingMode: (String) -> Unit = { mode -> setPrevFacingMode(mode) }
 *         override val updateIsMediaSettingsModalVisible: (Boolean) -> Unit = { isVisible -> setMediaSettingsModal(isVisible) }
 *         override val showAlert: ShowAlert? = { message, type, duration ->
 *             showAlert(message, type, duration)
 *         }
 *         override val switchUserVideoAlt: SwitchUserVideoAltType = switchUserVideoAltFunction
 *         // Other properties...
 *     }
 * )
 * 
 * switchVideoAlt(switchVideoAltOptions)
 * ```
 */
suspend fun switchVideoAlt(options: SwitchVideoAltOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    val recordStarted = parameters.recordStarted
    val recordResumed = parameters.recordResumed
    val recordStopped = parameters.recordStopped
    val recordPaused = parameters.recordPaused
    val recordingMediaOptions = parameters.recordingMediaOptions
    val videoAlreadyOn = parameters.videoAlreadyOn
    var currentFacingMode = parameters.currentFacingMode
    var prevFacingMode = parameters.prevFacingMode
    val allowed = parameters.allowed
    val audioOnlyRoom = parameters.audioOnlyRoom
    val updateCurrentFacingMode = parameters.updateCurrentFacingMode
    val updatePrevFacingMode = parameters.updatePrevFacingMode
    val updateIsMediaSettingsModalVisible = parameters.updateIsMediaSettingsModalVisible
    val showAlert = parameters.showAlert
    
    // mediasfu functions
    val switchUserVideoAlt = parameters.switchUserVideoAlt
    
    // Check if the room is audio-only
    if (audioOnlyRoom) {
        showAlert?.invoke(
            message = "You cannot turn on your camera in an audio-only event.",
            type = "danger",
            duration = 3000
        )
        return
    }
    
    // Check if recording is in progress and if video cannot be turned off
    var checkoff = false
    if ((recordStarted || recordResumed) &&
        !recordStopped &&
        !recordPaused &&
        recordingMediaOptions == "video") {
        checkoff = true
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
    
    // Camera switching logic
    prevFacingMode = currentFacingMode
    updatePrevFacingMode(prevFacingMode)
    
    // Toggle between 'environment' and 'user'
    currentFacingMode = if (currentFacingMode == "environment") "user" else "environment"
    updateCurrentFacingMode(currentFacingMode)
    
    
    // Hide media settings modal if visible
    updateIsMediaSettingsModalVisible(false)
    
    // Perform the video switch using the mediasfu function
    val optionsSwitch = SwitchUserVideoAltOptions(
        parameters = parameters,
        videoPreference = currentFacingMode,
        checkoff = checkoff
    )
    switchUserVideoAlt(optionsSwitch)
}
