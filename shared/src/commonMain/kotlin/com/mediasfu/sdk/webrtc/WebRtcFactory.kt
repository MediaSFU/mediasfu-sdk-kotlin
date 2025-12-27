package com.mediasfu.sdk.webrtc

/**
 * Common declaration for platform-specific WebRTC factory implementations.
 */
expect object WebRtcFactory {
    fun createDevice(): WebRtcDevice
    fun createDevice(context: Any?): WebRtcDevice
}
