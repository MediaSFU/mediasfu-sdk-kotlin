package com.mediasfu.sdk.webrtc

/**
 * iOS-specific implementation of WebRtcFactory.
 */
actual object WebRtcFactory {
    actual fun createDevice(): WebRtcDevice {
        return IOSWebRtcDevice.getInstance()
    }
    
    actual fun createDevice(context: Any?): WebRtcDevice {
        // iOS doesn't require context, so we ignore it
        return IOSWebRtcDevice.getInstance()
    }
}
