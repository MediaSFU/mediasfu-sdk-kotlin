package com.mediasfu.sdk.ui.components.display

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import cocoapods.WebRTC.RTCMTLVideoView
import cocoapods.WebRTC.RTCVideoTrack
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIViewContentMode
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformMakeScale

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoRenderer(
    track: MediaStreamTrack,
    doMirror: Boolean,
    forceFullDisplay: Boolean,
    modifier: Modifier
) {
    val nativeTrack = track.asPlatformNativeTrack() as? RTCVideoTrack
    if (nativeTrack == null) {
        VideoRendererFallback(
            message = "Video track unavailable",
            modifier = modifier
        )
        return
    }

    val updatedTrack = rememberUpdatedState(nativeTrack)
    var boundTrack by remember { mutableStateOf<RTCVideoTrack?>(null) }

    UIKitView(
        modifier = modifier,
        factory = {
            RTCMTLVideoView().apply {
                contentMode = desiredContentMode(forceFullDisplay)
                transform = mirrorTransform(doMirror)
            }.also { view ->
                updatedTrack.value.addRenderer(view)
                boundTrack = updatedTrack.value
            }
        },
        update = { view ->
            view.contentMode = desiredContentMode(forceFullDisplay)
            view.transform = mirrorTransform(doMirror)

            val desiredTrack = updatedTrack.value
            if (boundTrack !== desiredTrack) {
                boundTrack?.removeRenderer(view)
                desiredTrack.addRenderer(view)
                boundTrack = desiredTrack
            }
        },
        onRelease = { view ->
            boundTrack?.removeRenderer(view)
            boundTrack = null
        }
    )
}

private fun desiredContentMode(forceFullDisplay: Boolean): UIViewContentMode {
    return if (forceFullDisplay) {
        UIViewContentMode.UIViewContentModeScaleAspectFill
    } else {
        UIViewContentMode.UIViewContentModeScaleAspectFit
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun mirrorTransform(doMirror: Boolean): CValue<CGAffineTransform> = if (doMirror) {
    CGAffineTransformMakeScale(-1.0, 1.0) as CValue<CGAffineTransform>
} else {
    CGAffineTransformMakeScale(1.0, 1.0) as CValue<CGAffineTransform>
}
