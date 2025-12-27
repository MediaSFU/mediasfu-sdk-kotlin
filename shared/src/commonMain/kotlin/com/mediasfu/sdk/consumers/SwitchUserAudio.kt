package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.webrtc.WebRtcDevice

typealias RequestPermissionAudioType = suspend () -> Boolean?
typealias SwitchUserAudioType = suspend (SwitchUserAudioOptions) -> Unit

/**
 * Parameters for switching user audio device.
 */
interface SwitchUserAudioParameters : StreamSuccessAudioSwitchParameters {
    override val userDefaultAudioInputDevice: String
    val prevAudioInputDevice: String
    override val showAlert: ShowAlert?
    val hasAudioPermission: Boolean
    val checkMediaPermission: Boolean
    override val device: WebRtcDevice?

    override val updateUserDefaultAudioInputDevice: (String) -> Unit
    val updatePrevAudioInputDevice: (String) -> Unit

    val streamSuccessAudioSwitch: suspend (StreamSuccessAudioSwitchOptions) -> Unit
    val requestPermissionAudio: RequestPermissionAudioType

    override fun getUpdatedAllParams(): SwitchUserAudioParameters
}

// Note: StreamSuccessAudioSwitchOptions is now defined in StreamSuccessAudioSwitch.kt

/**
 * Options for switching user audio device.
 *
 * @property parameters Parameters for switching audio
 * @property audioPreference The ID of the new audio input device to switch to
 * @property audioConstraints Optional audio constraints
 */
data class SwitchUserAudioOptions(
    val parameters: SwitchUserAudioParameters,
    val audioPreference: String,
    val audioConstraints: Map<String, Any>? = null
)

/**
 * Switches the user's audio input to the specified device.
 *
 * ### Workflow:
 * 1. **Permission Check**: Verifies if audio permissions are granted
 * 2. **Media Constraints Setup**: Configures constraints for the desired device
 * 3. **Stream Retrieval and Switch**: Creates a new audio stream with the specified device
 * 4. **Error Handling and Fallback**: Reverts to the previous device if the switch fails
 *
 * @param options Options containing parameters and audio preference
 *
 * Example:
 * ```kotlin
 * val parameters = object : SwitchUserAudioParameters {
 *     override val userDefaultAudioInputDevice = "defaultDeviceID"
 *     override val prevAudioInputDevice = "oldDeviceID"
 *     override val hasAudioPermission = true
 *     // ... other properties
 * }
 *
 * switchUserAudio(
 *     SwitchUserAudioOptions(
 *         parameters = parameters,
 *         audioPreference = "newDeviceID"
 *     )
 * )
 * ```
 *
 * ### Note:
 * This is a simplified implementation. Full implementation requires platform-specific
 * media device access and stream management.
 */
suspend fun switchUserAudio(options: SwitchUserAudioOptions) {
    val audioPreference = options.audioPreference
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        val prevAudioInputDevice = parameters.prevAudioInputDevice
        val hasAudioPermission = parameters.hasAudioPermission
        val showAlert = parameters.showAlert
        val streamSuccessAudioSwitch = parameters.streamSuccessAudioSwitch
        val requestPermissionAudio = parameters.requestPermissionAudio
        val checkMediaPermission = parameters.checkMediaPermission
        val updateUserDefaultAudioInputDevice = parameters.updateUserDefaultAudioInputDevice
        val updatePrevAudioInputDevice = parameters.updatePrevAudioInputDevice
        val device = parameters.device

        // Check for audio permission
        if (!hasAudioPermission) {
            if (checkMediaPermission) {
                val granted = requestPermissionAudio()
                if (granted != true) {
                    showAlert?.invoke(
                        "Allow access to your microphone or check if it's not being used by another application.",
                        "danger",
                        3000
                    )
                    return
                }
            } else {
                showAlert?.invoke(
                    "Allow access to your microphone to switch audio input.",
                    "danger",
                    3000
                )
                return
            }
        }

        if (device == null) {
            showAlert?.invoke(
                "Unable to access the microphone. Please restart the call or app.",
                "danger",
                3000
            )
            return
        }

        // Save current device as previous before switching
        updatePrevAudioInputDevice(parameters.userDefaultAudioInputDevice)

        // Build audio constraints with device ID
        val mediaConstraints = mapOf<String, Any?>(
            "audio" to mapOf(
                "optional" to listOf(
                    mapOf("sourceId" to audioPreference)
                )
            ),
            "video" to false
        )

        try {
            val stream = device.getUserMedia(mediaConstraints)
            
            // Update default device on success
            updateUserDefaultAudioInputDevice(audioPreference)
            
            // Call streamSuccessAudioSwitch with the new stream
            val optionsSwitch = StreamSuccessAudioSwitchOptions(
                stream = stream,
                audioConstraints = options.audioConstraints,
                parameters = parameters
            )
            streamSuccessAudioSwitch(optionsSwitch)
            
            
        } catch (error: Exception) {
            Logger.e("SwitchUserAudio", "MediaSFU - switchUserAudio: Failed to switch audio -> ${error.message}")
            
            // Revert to previous device on error
            updateUserDefaultAudioInputDevice(prevAudioInputDevice)
            
            showAlert?.invoke(
                "Error switching; the specified microphone could not be accessed.",
                "danger",
                3000
            )
        }

    } catch (error: Exception) {
        Logger.e("SwitchUserAudio", "MediaSFU - Error switching audio: ${error.message}")
        
        // Revert to previous device on error
        try {
            val prevDevice = parameters.prevAudioInputDevice
            parameters.updateUserDefaultAudioInputDevice(prevDevice)
            
            parameters.showAlert?.invoke(
                "Error switching audio input device. Reverted to previous device.",
                "danger",
                3000
            )
        } catch (revertError: Exception) {
            Logger.e("SwitchUserAudio", "MediaSFU - Error reverting audio device: ${revertError.message}")
        }
    }
}

