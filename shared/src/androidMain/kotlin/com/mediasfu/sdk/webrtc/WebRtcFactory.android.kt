package com.mediasfu.sdk.webrtc

import android.content.Context

/**
 * Android-specific implementation of WebRtcFactory.
 */
actual object WebRtcFactory {
    actual fun createDevice(): WebRtcDevice {
        throw IllegalStateException("Android WebRTC device requires a Context. Use createDevice(context) instead.")
    }
    
    actual fun createDevice(context: Any?): WebRtcDevice {
        if (context !is Context) {
            throw IllegalArgumentException("Android WebRTC device requires an Android Context")
        }
        return AndroidWebRtcDevice.getInstance(context)
    }
}
