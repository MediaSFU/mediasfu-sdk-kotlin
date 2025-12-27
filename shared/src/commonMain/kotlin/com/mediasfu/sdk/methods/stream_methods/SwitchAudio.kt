package com.mediasfu.sdk.methods.stream_methods

import com.mediasfu.sdk.consumers.SwitchUserAudioOptions
import com.mediasfu.sdk.consumers.SwitchUserAudioParameters
import com.mediasfu.sdk.consumers.SwitchUserAudioType

/**
 * Parameters for SwitchAudio function.
 */
interface SwitchAudioParameters : SwitchUserAudioParameters {
    // Core properties
    override val defAudioID: String
    override val userDefaultAudioInputDevice: String
    override val prevAudioInputDevice: String
    
    // Update functions
    override val updateUserDefaultAudioInputDevice: (String) -> Unit
    override val updatePrevAudioInputDevice: (String) -> Unit
    
    // Mediasfu function
    val switchUserAudio: SwitchUserAudioType
    
    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): SwitchAudioParameters
}

/**
 * Options for switching the audio input device.
 */
data class SwitchAudioOptions(
    val audioPreference: String,
    val parameters: SwitchAudioParameters
)

/**
 * Type definition for SwitchAudio function.
 */
typealias SwitchAudioType = suspend (SwitchAudioOptions) -> Unit

/**
 * Switches the audio input device based on user preference.
 * 
 * This function updates the user's default audio input device and the previous audio input device.
 * It also calls the [switchUserAudio] function to perform the actual audio device switching.
 * If the [audioPreference] is the same as the default audio ID, no switching is performed.
 * 
 * ### Parameters:
 * - [options] (`SwitchAudioOptions`): Contains the `audioPreference` and `parameters` required for switching audio.
 * 
 * ### Example:
 * ```kotlin
 * val switchAudioOptions = SwitchAudioOptions(
 *     audioPreference = "newAudioDeviceID",
 *     parameters = object : SwitchAudioParameters {
 *         override val defAudioID: String = "defaultAudioDeviceID"
 *         override val userDefaultAudioInputDevice: String = "currentAudioDeviceID"
 *         override val prevAudioInputDevice: String = "previousAudioDeviceID"
 *         override val updateUserDefaultAudioInputDevice: (String) -> Unit = { deviceId -> setUserDefaultAudio(deviceId) }
 *         override val updatePrevAudioInputDevice: (String) -> Unit = { deviceId -> setPrevAudioDevice(deviceId) }
 *         override val switchUserAudio: SwitchUserAudioType = switchUserAudioFunction
 *         // Other properties...
 *     }
 * )
 * 
 * switchAudio(switchAudioOptions)
 * ```
 */
suspend fun switchAudio(options: SwitchAudioOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    val defAudioID = parameters.defAudioID
    var userDefaultAudioInputDevice = parameters.userDefaultAudioInputDevice
    var prevAudioInputDevice = parameters.prevAudioInputDevice
    val updateUserDefaultAudioInputDevice = parameters.updateUserDefaultAudioInputDevice
    val updatePrevAudioInputDevice = parameters.updatePrevAudioInputDevice
    
    // mediasfu functions
    val switchUserAudio = parameters.switchUserAudio
    
    if (options.audioPreference != defAudioID) {
        // Update previous audio input device
        prevAudioInputDevice = userDefaultAudioInputDevice
        updatePrevAudioInputDevice(prevAudioInputDevice)
        
        // Update current audio input device
        userDefaultAudioInputDevice = options.audioPreference
        updateUserDefaultAudioInputDevice(userDefaultAudioInputDevice)
        
        // Perform the audio switch - always call switchUserAudio when preference differs
        val optionsSwitch = SwitchUserAudioOptions(
            parameters = parameters,
            audioPreference = options.audioPreference
        )
        switchUserAudio(optionsSwitch)
    }
}
