package com.mediasfu.sdk.producer_client
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcFactory
import com.mediasfu.sdk.webrtc.debugJson
import com.mediasfu.sdk.webrtc.debugSummary

/**
 * Options for creating a mediasoup client device.
 */
data class CreateDeviceClientOptions(
    val rtpCapabilities: RtpCapabilities?
)

/**
 * Type definition for creating device client.
 */
typealias CreateDeviceClientType = suspend (CreateDeviceClientOptions) -> WebRtcDevice?

/**
 * Creates a mediasoup client device with the provided RTP capabilities.
 * 
 * The [CreateDeviceClientOptions] is required and must contain the RTP capabilities.
 * 
 * Returns a [Device] object representing the created mediasoup client device or
 * throws an [Exception] if the device creation is not supported.
 * 
 * Example usage:
 * ```kotlin
 * val device = createDeviceClient(
 *     options = CreateDeviceClientOptions(rtpCapabilities = rtpCapabilities)
 * )
 * if (device != null) {
 * } else {
 *     Logger.e("CreateDeviceClient", "Failed to create device")
 * }
 * ```
 */
suspend fun createDeviceClient(options: CreateDeviceClientOptions): WebRtcDevice? {
    return try {
        // Check if rtpCapabilities is provided
        if (options.rtpCapabilities == null) {
            throw Exception("RTP capabilities must be provided.")
        }
        
        // Initialize the mediasoup client device
        val device = WebRtcFactory.createDevice()

        val routerCaps = options.rtpCapabilities!!
        
        // Remove orientation capabilities if present in rtpCapabilities directly
        val filteredCapabilities = routerCaps.copy(
            headerExtensions = routerCaps.headerExtensions.filter { ext ->
                ext.uri != "urn:3gpp:video-orientation"
            }
        )
        
        // Load the provided RTP capabilities into the device
        device.load(filteredCapabilities).getOrThrow()

        device.currentRtpCapabilities()?.let { currentCaps ->
        }
        
        device
    } catch (error: Exception) {
        if (error is UnsupportedOperationException) {
            null
        } else {
            throw error
        }
    }
}
