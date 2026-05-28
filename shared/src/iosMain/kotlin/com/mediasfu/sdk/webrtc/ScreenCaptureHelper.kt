package com.mediasfu.sdk.webrtc

import platform.ReplayKit.RPScreenRecorder

/**
 * Helper utilities for iOS screen capture using ReplayKit in-app capture.
 *
 * Unlike Android's MediaProjection flow, iOS in-app ReplayKit capture does not
 * require a separate permission token to be passed into `getDisplayMedia()`.
 * This helper therefore focuses on capability checks and building a consistent
 * constraints payload for callers that want a platform utility similar to the
 * Android helper surface.
 */
object ScreenCaptureHelper {

    /**
     * Builds constraints for `WebRtcDevice.getDisplayMedia()`.
     *
     * @param width Desired capture width (default: 1080)
     * @param height Desired capture height (default: 1920)
     * @param frameRate Desired frame rate (default: 15)
     * @param maxFrameRate Maximum frame rate hint (default: 30)
     * @param audio Whether audio capture should be requested (default: false)
     */
    fun buildConstraints(
        width: Int = 1080,
        height: Int = 1920,
        frameRate: Int = 15,
        maxFrameRate: Int = 30,
        audio: Boolean = false
    ): Map<String, Any?> {
        return mapOf(
            "video" to mapOf(
                "width" to mapOf("ideal" to width),
                "height" to mapOf("ideal" to height),
                "frameRate" to mapOf("ideal" to frameRate, "max" to maxFrameRate)
            ),
            "audio" to audio
        )
    }

    /**
     * Returns whether ReplayKit in-app capture is currently available.
     */
    fun isSupported(): Boolean = RPScreenRecorder.sharedRecorder().isAvailable()
}