package com.mediasfu.sdk.webrtc

private const val forceIOSFilterVideoOrientationHeaderExtension = true

internal actual fun shouldPreserveVideoOrientationHeaderExtension(): Boolean =
    !forceIOSFilterVideoOrientationHeaderExtension
