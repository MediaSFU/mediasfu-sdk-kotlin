package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.WebRtcFactory

/**
 * Type definition for the getMediaDevicesList function.
 */
typealias GetMediaDevicesListType = suspend (String) -> List<MediaDeviceInfo>

/**
 * Retrieves a filtered list of media devices based on the specified kind.
 * 
 * This function attempts to get user media permissions before enumerating devices
 * to ensure proper device labels and information are available.
 * 
 * **Parameters:**
 * - [kind] (`String`): The type of media device to filter.
 *   - `'videoinput'`: Video input devices (cameras)
 *   - `'audioinput'`: Audio input devices (microphones)
 * 
 * **Returns:**
 * - `List<MediaDeviceInfo>`: A list of media devices matching the specified kind.
 *   Returns an empty list if an error occurs.
 * 
 * **Example:**
 * ```kotlin
 * // Get all video input devices (cameras)
 * val videoDevices = getMediaDevicesList("videoinput")
 * for (device in videoDevices) {
 * }
 * 
 * // Get all audio input devices (microphones)
 * val audioDevices = getMediaDevicesList("audioinput")
 * for (device in audioDevices) {
 * }
 * ```
 */
suspend fun getMediaDevicesList(kind: String): List<MediaDeviceInfo> {
    return try {
        // Get the WebRTC device instance
    val device = WebRtcFactory.createDevice()
        
        // Attempt to get media stream to trigger permission prompt if needed
        // This ensures device labels are available
        try {
            val constraints = mutableMapOf<String, Any>()
            
            when (kind) {
                "videoinput" -> {
                    constraints["video"] = true
                    constraints["audio"] = false
                }
                "audioinput" -> {
                    constraints["audio"] = true
                    constraints["video"] = false
                }
                else -> {
                    // If kind is not recognized, try to get both
                    constraints["audio"] = true
                    constraints["video"] = true
                }
            }
            
            // Get user media to trigger permission prompt
            try {
                val stream = device.getUserMedia(constraints)
                
                // Close the stream immediately as we don't need it
                stream.getTracks().forEach { track ->
                    track.stop()
                }
            } catch (permissionError: Exception) {
                // Permission denied or not available, continue anyway
                // Devices may still be enumerated but with limited information
                Logger.e("GetMediaDevicesList", "Permission not granted for media devices: $permissionError")
            }
        } catch (e: Exception) {
            // Continue even if getUserMedia fails
            Logger.e("GetMediaDevicesList", "Could not get user media: $e")
        }
        
        // Enumerate all available media devices
        val devices = device.enumerateDevices()
        
        // Filter devices based on the specified kind
        devices.filter { it.kind == kind }
        
    } catch (e: Exception) {
        // Return an empty list if an error occurs
        Logger.e("GetMediaDevicesList", "Error getting media devices list: $e")
        emptyList()
    }
}