package com.mediasfu.sdk.methods.media_settings_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcFactory
import kotlinx.coroutines.yield

/**
 * Defines options for launching the media settings modal, including visibility toggling,
 * available audio/video devices, and update functions.
 */
data class LaunchMediaSettingsOptions(
    val updateIsMediaSettingsModalVisible: (Boolean) -> Unit,
    val isMediaSettingsModalVisible: Boolean,
    val audioInputs: List<MediaDeviceInfo>,
    val videoInputs: List<MediaDeviceInfo>,
    val audioOutputs: List<MediaDeviceInfo> = emptyList(),
    val updateAudioInputs: (List<MediaDeviceInfo>) -> Unit,
    val updateVideoInputs: (List<MediaDeviceInfo>) -> Unit,
    val updateAudioOutputs: (List<MediaDeviceInfo>) -> Unit = {},
    val videoAlreadyOn: Boolean,
    val audioAlreadyOn: Boolean,
    val onWeb: Boolean,
    val updateIsLoadingModalVisible: (Boolean) -> Unit,
    val device: WebRtcDevice? = null,  // Device parameter
    val updateAllowed: ((Boolean) -> Unit)? = null  // Update permission flag
)

/**
 * Type definition for the function that launches the media settings modal.
 */
typealias LaunchMediaSettingsType = suspend (LaunchMediaSettingsOptions) -> Unit

/**
 * Launches the media settings modal and updates the available audio and video input devices.
 * 
 * This function checks if the media settings modal is not currently visible, and if so,
 * it attempts to get the media stream to force the permission prompt, then retrieves the
 * available media devices and updates the audio and video inputs lists.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchMediaSettingsOptions(
 *     isMediaSettingsModalVisible = false,
 *     audioInputs = emptyList(),
 *     videoInputs = emptyList(),
 *     videoAlreadyOn = false,
 *     audioAlreadyOn = false,
 *     onWeb = true,
 * )
 * 
 * launchMediaSettings(options)
 * ```
 */
suspend fun launchMediaSettings(options: LaunchMediaSettingsOptions) {
    if (!options.isMediaSettingsModalVisible) {
        options.updateIsMediaSettingsModalVisible(true)
        // Yield once so the modal can render before permission/device work begins.
        yield()
    }

    try {
        // Check if device is available
        val device = options.device
        if (device == null) {
            return
        }

        // Force permission prompt by attempting to get media stream
        options.updateIsLoadingModalVisible(true)

        // Request only the inactive/missing permissions. On iOS a fresh video
        // getUserMedia call replaces the active camera capture in IOSWebRtcDevice,
        // so requesting audio+video while already in-room can freeze the live feed.
        val needsAudioPermission = !options.audioAlreadyOn && options.audioInputs.isEmpty()
        val needsVideoPermission = !options.videoAlreadyOn && options.videoInputs.isEmpty()
        val shouldRequestPermissions = needsAudioPermission || needsVideoPermission

        if (shouldRequestPermissions) {
            try {
                val stream = device.getUserMedia(
                    mapOf(
                        "audio" to needsAudioPermission,
                        "video" to needsVideoPermission
                    )
                )

                // Close the stream as it's not needed
                stream.getTracks().forEach { track ->
                    track.stop()
                }
            } catch (permError: Exception) {
                Logger.e("LaunchMediaSettings", "MediaSFU - Permission request warning: ${permError.message}")
                // Continue anyway - enumerate will show what's available
            }
        }

        // Get the list of all available media devices
        val devices = device.enumerateDevices()

        // Filter devices to get audio inputs, video inputs, and audio outputs
        val videoInputs = devices.filter { it.kind == "videoinput" }
        val audioInputs = devices.filter { it.kind == "audioinput" }
        val audioOutputs = devices.filter { it.kind == "audiooutput" }

        options.updateVideoInputs(videoInputs)
        options.updateAudioInputs(audioInputs)
        options.updateAudioOutputs(audioOutputs)
    } catch (error: Exception) {
        Logger.e("LaunchMediaSettings", "MediaSFU - Error getting media devices: ${error.message}")
        error.printStackTrace()
    } finally {
        options.updateIsLoadingModalVisible(false)
    }
}
