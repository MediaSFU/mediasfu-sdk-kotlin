package com.mediasfu.sdk.methods.stream_methods

/**
 * Parameters required for switching audio output device
 */
interface SwitchAudioOutputParameters {
    val userDefaultAudioOutputDevice: String
    val prevAudioOutputDevice: String
    val updateUserDefaultAudioOutputDevice: (String) -> Unit
    val updatePrevAudioOutputDevice: (String) -> Unit
    val setAudioOutputDevice: suspend (String) -> Boolean
    
    fun getUpdatedAllParams(): SwitchAudioOutputParameters
}

/**
 * Options for switching audio output
 */
data class SwitchAudioOutputOptions(
    val audioOutputPreference: String,
    val parameters: SwitchAudioOutputParameters
)

/**
 * Type alias for the switch audio output function
 */
typealias SwitchAudioOutputType = suspend (SwitchAudioOutputOptions) -> Unit

/**
 * Switches the audio output device (speaker, Bluetooth, headphones).
 * 
 * This function routes audio playback to the specified output device.
 * On Android, it uses AudioManager to set the communication device.
 * 
 * ## Example Usage
 * ```kotlin
 * val options = SwitchAudioOutputOptions(
 *     audioOutputPreference = "audio_output_123",
 *     parameters = myParameters
 * )
 * switchAudioOutput(options)
 * ```
 * 
 * @param options The options containing the audio output preference and parameters
 */
suspend fun switchAudioOutput(options: SwitchAudioOutputOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    var userDefaultAudioOutputDevice = parameters.userDefaultAudioOutputDevice
    var prevAudioOutputDevice = parameters.prevAudioOutputDevice
    val updateUserDefaultAudioOutputDevice = parameters.updateUserDefaultAudioOutputDevice
    val updatePrevAudioOutputDevice = parameters.updatePrevAudioOutputDevice
    val setAudioOutputDevice = parameters.setAudioOutputDevice
    
    if (options.audioOutputPreference != userDefaultAudioOutputDevice) {
        // Update previous audio output device
        prevAudioOutputDevice = userDefaultAudioOutputDevice
        updatePrevAudioOutputDevice(prevAudioOutputDevice)
        
        // Update current audio output device
        userDefaultAudioOutputDevice = options.audioOutputPreference
        updateUserDefaultAudioOutputDevice(userDefaultAudioOutputDevice)
        
        // Perform the audio output switch
        setAudioOutputDevice(options.audioOutputPreference)
    }
}
