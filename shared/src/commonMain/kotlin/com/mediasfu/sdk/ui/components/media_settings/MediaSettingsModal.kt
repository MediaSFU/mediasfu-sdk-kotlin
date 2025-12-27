package com.mediasfu.sdk.ui.components.media_settings

import com.mediasfu.sdk.ui.*

/**
 * # MediaSettingsModal
 *
 * A modal component for configuring media settings including camera and microphone selection.
 * Replicates the Flutter SDK's media_settings_modal.dart functionality.
 *
 * ## Features
 * - Camera device selection dropdown
 * - Microphone device selection dropdown
 * - Switch camera button (for mobile devices with front/back cameras)
 * - Modal positioning (topRight, topLeft, center, etc.)
 * - Customizable background color
 *
 * ## Usage Example
 * ```kotlin
 * val options = MediaSettingsModalOptions(
 *     isVisible = true,
 *     onClose = { /* close modal */ },
 *     parameters = mediaSettingsParams,
 *     switchCameraOnPress = { switchVideoAlt(it) },
 *     switchVideoOnPress = { switchVideo(it) },
 *     switchAudioOnPress = { switchAudio(it) }
 * )
 *
 * val modal = DefaultMediaSettingsModal(options)
 * val rendered = modal.render()
 * ```
 *
 * @property options Configuration options for the media settings modal
 */

/**
 * Media device information
 */
data class MediaDeviceInfo(
    val deviceId: String,
    val label: String,
    val kind: String  // "videoinput", "audioinput", "audiooutput"
)

/**
 * Parameters required by the MediaSettingsModal
 */
interface MediaSettingsModalParameters {
    val userDefaultVideoInputDevice: String
    val userDefaultAudioInputDevice: String
    val userDefaultAudioOutputDevice: String
    val videoInputs: List<MediaDeviceInfo>
    val audioInputs: List<MediaDeviceInfo>
    val audioOutputs: List<MediaDeviceInfo>
    val isMediaSettingsModalVisible: Boolean
    
    fun updateIsMediaSettingsModalVisible(value: Boolean)
    fun getUpdatedAllParams(): MediaSettingsModalParameters
}

/**
 * Options for switching video input
 */
data class SwitchVideoOptions(
    val videoPreference: String,
    val parameters: MediaSettingsModalParameters
)

/**
 * Options for switching audio input
 */
data class SwitchAudioOptions(
    val audioPreference: String,
    val parameters: MediaSettingsModalParameters
)

/**
 * Options for switching audio output (speaker/headphones)
 */
data class SwitchAudioOutputOptions(
    val audioOutputPreference: String,
    val parameters: MediaSettingsModalParameters
)

/**
 * Options for switching camera (front/back)
 */
data class SwitchVideoAltOptions(
    val parameters: MediaSettingsModalParameters
)

/**
 * Configuration options for the MediaSettingsModal component
 *
 * @property isVisible Whether the modal is visible
 * @property onClose Callback to close the modal
 * @property switchCameraOnPress Callback for switching camera (front/back)
 * @property switchVideoOnPress Callback for switching video device
 * @property switchAudioOnPress Callback for switching audio device
 * @property parameters Current media settings parameters
 * @property position Modal position on screen (topRight, topLeft, center, etc.)
 * @property backgroundColor Background color for the modal (hex string)
 */
data class MediaSettingsModalOptions(
    val isVisible: Boolean,
    val onClose: () -> Unit,
    val switchCameraOnPress: (SwitchVideoAltOptions) -> Unit = {},
    val switchVideoOnPress: (SwitchVideoOptions) -> Unit = {},
    val switchAudioOnPress: (SwitchAudioOptions) -> Unit = {},
    val switchAudioOutputOnPress: (SwitchAudioOutputOptions) -> Unit = {},
    val onVirtualBackgroundPress: () -> Unit = {},  // Opens BackgroundModal
    val parameters: MediaSettingsModalParameters,
    val position: String = "topRight",
    val backgroundColor: String = "#2196F3"  // Flutter Colors.blue
)

/**
 * Interface for the MediaSettingsModal component
 */
interface MediaSettingsModal : MediaSfuUIComponent {
    val options: MediaSettingsModalOptions
    override val id: String get() = "media_settings_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
}

/**
 * Default implementation of MediaSettingsModal
 */
class DefaultMediaSettingsModal(
    override val options: MediaSettingsModalOptions
) : MediaSettingsModal {
    
    fun render(): Map<String, Any> {
        val parameters = options.parameters.getUpdatedAllParams()
        val videoInputs = parameters.videoInputs
        val audioInputs = parameters.audioInputs
        val audioOutputs = parameters.audioOutputs
        
        // Determine selected video input
        var selectedVideoInput = parameters.userDefaultVideoInputDevice
        if (selectedVideoInput.isEmpty() && videoInputs.isNotEmpty()) {
            selectedVideoInput = videoInputs[0].deviceId
        } else {
            // If selectedVideoInput is not in the list, set it to the first device
            if (!videoInputs.any { it.deviceId == selectedVideoInput }) {
                selectedVideoInput = if (videoInputs.isNotEmpty()) {
                    videoInputs[0].deviceId
                } else {
                    "No Video Devices"
                }
            }
        }
        
        // Determine selected audio input
        var selectedAudioInput = parameters.userDefaultAudioInputDevice
        if (selectedAudioInput.isEmpty() && audioInputs.isNotEmpty()) {
            selectedAudioInput = audioInputs[0].deviceId
        } else {
            // If selectedAudioInput is not in the list, set it to the first device
            if (!audioInputs.any { it.deviceId == selectedAudioInput }) {
                selectedAudioInput = if (audioInputs.isNotEmpty()) {
                    audioInputs[0].deviceId
                } else {
                    "No Audio Devices"
                }
            }
        }
        
        // Determine selected audio output
        var selectedAudioOutput = parameters.userDefaultAudioOutputDevice
        if (selectedAudioOutput.isEmpty() && audioOutputs.isNotEmpty()) {
            selectedAudioOutput = audioOutputs[0].deviceId
        } else {
            // If selectedAudioOutput is not in the list, set it to the first device
            if (!audioOutputs.any { it.deviceId == selectedAudioOutput }) {
                selectedAudioOutput = if (audioOutputs.isNotEmpty()) {
                    audioOutputs[0].deviceId
                } else {
                    "No Audio Output Devices"
                }
            }
        }
        
        return mapOf(
            "type" to "mediaSettingsModal",
            "visible" to options.isVisible,
            "position" to options.position,
            "container" to mapOf(
                "width" to "400px",  // modalWidth: 0.8 * screenWidth, max 400
                "maxWidth" to "80%",
                "height" to "65vh",
                "padding" to "10px",
                "backgroundColor" to options.backgroundColor,
                "overflowX" to "auto"
            ),
            "header" to mapOf(
                "display" to "flex",
                "justifyContent" to "space-between",
                "alignItems" to "center",
                "title" to mapOf(
                    "text" to "Media Settings",
                    "fontSize" to "16px",
                    "fontWeight" to "bold",
                    "color" to "#000000"
                ),
                "closeButton" to mapOf(
                    "icon" to "close",
                    "color" to "#000000",
                    "onClick" to options.onClose
                )
            ),
            "divider" to mapOf(
                "height" to "10px",
                "thickness" to "1px",
                "color" to "#000000"
            ),
            "sections" to listOf(
                // Camera Selection Section
                mapOf(
                    "type" to "deviceSelector",
                    "label" to "Select Camera:",
                    "labelStyle" to mapOf(
                        "fontSize" to "16px",
                        "fontWeight" to "bold",
                        "color" to "#000000",
                        "marginTop" to "10px",
                        "marginBottom" to "5px"
                    ),
                    "selectedValue" to selectedVideoInput,
                    "devices" to videoInputs.map { device ->
                        mapOf(
                            "deviceId" to device.deviceId,
                            "label" to device.label
                        )
                    },
                    "onChange" to { newValue: String ->
                        val switchOptions = SwitchVideoOptions(
                            videoPreference = newValue,
                            parameters = parameters
                        )
                        options.switchVideoOnPress(switchOptions)
                    }
                ),
                
                // Microphone Selection Section
                mapOf(
                    "type" to "deviceSelector",
                    "label" to "Select Microphone:",
                    "labelStyle" to mapOf(
                        "fontSize" to "16px",
                        "fontWeight" to "bold",
                        "color" to "#000000",
                        "marginTop" to "20px",
                        "marginBottom" to "5px"
                    ),
                    "selectedValue" to selectedAudioInput,
                    "devices" to audioInputs.map { device ->
                        mapOf(
                            "deviceId" to device.deviceId,
                            "label" to device.label
                        )
                    },
                    "onChange" to { newValue: String ->
                        val switchOptions = SwitchAudioOptions(
                            audioPreference = newValue,
                            parameters = parameters
                        )
                        options.switchAudioOnPress(switchOptions)
                    }
                ),
                
                // Speaker/Audio Output Selection Section
                mapOf(
                    "type" to "deviceSelector",
                    "label" to "Select Speaker:",
                    "labelStyle" to mapOf(
                        "fontSize" to "16px",
                        "fontWeight" to "bold",
                        "color" to "#000000",
                        "marginTop" to "20px",
                        "marginBottom" to "5px"
                    ),
                    "selectedValue" to selectedAudioOutput,
                    "devices" to audioOutputs.map { device ->
                        mapOf(
                            "deviceId" to device.deviceId,
                            "label" to device.label
                        )
                    },
                    "onChange" to { newValue: String ->
                        val switchOptions = SwitchAudioOutputOptions(
                            audioOutputPreference = newValue,
                            parameters = parameters
                        )
                        options.switchAudioOutputOnPress(switchOptions)
                    }
                ),
                
                // Switch Camera Button
                mapOf(
                    "type" to "button",
                    "label" to "Switch Camera",
                    "marginTop" to "20px",
                    "onClick" to {
                        val switchOptions = SwitchVideoAltOptions(
                            parameters = parameters
                        )
                        options.switchCameraOnPress(switchOptions)
                    }
                )
            )
        )
    }
}
