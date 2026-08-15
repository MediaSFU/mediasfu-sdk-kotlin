package com.mediasfu.sdk.webrtc

// Match the default MediaSFU React and Flutter behavior. Without the
// extension, WebRTC applies RTCVideoFrame.rotation before encoding the pixels.
internal actual fun shouldPreserveVideoOrientationHeaderExtension(): Boolean = false
