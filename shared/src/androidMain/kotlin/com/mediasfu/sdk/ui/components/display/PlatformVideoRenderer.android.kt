package com.mediasfu.sdk.ui.components.display

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.SharedEglContext
import kotlinx.coroutines.delay
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
actual fun PlatformVideoRenderer(
    track: MediaStreamTrack,
    doMirror: Boolean,
    forceFullDisplay: Boolean,
    modifier: Modifier
) {
    val nativeTrack = track.asPlatformNativeTrack() as? VideoTrack
    if (nativeTrack == null) {
        VideoRendererFallback(
            message = "Video track unavailable",
            modifier = modifier
        )
        return
    }

    val density = LocalDensity.current
    val updatedTrack = rememberUpdatedState(nativeTrack)
    var boundTrack by remember { mutableStateOf<VideoTrack?>(null) }
    
    // Track if first frame has been rendered (to hide green flash)
    var firstFrameRendered by remember { mutableStateOf(false) }
    
    // Track video resolution for aspect ratio calculation
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }
    var videoRotation by remember { mutableStateOf(0) }
    
    // Track container size
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    
    // Force recalculation counter - increment to trigger recomposition
    var recalculationTrigger by remember { mutableStateOf(0) }
    
    // Force recalculation after delays to handle late-arriving size info
    LaunchedEffect(Unit) {
        delay(500)
        recalculationTrigger++
        delay(500)  // Another at 1s
        recalculationTrigger++
        delay(1000) // Another at 2s
        recalculationTrigger++
    }
    
    // Also recalculate when first frame renders (we now have real resolution)
    LaunchedEffect(firstFrameRendered) {
        if (firstFrameRendered) {
            delay(100)
            recalculationTrigger++
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { 
                if (it != containerSize) {
                    containerSize = it
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Read recalculationTrigger to force recomposition
        val layoutVersion = recalculationTrigger
        
        val effectiveWidth = if (videoRotation == 90 || videoRotation == 270) videoHeight else videoWidth
        val effectiveHeight = if (videoRotation == 90 || videoRotation == 270) videoWidth else videoHeight
        val videoAspectRatio = if (effectiveWidth > 0 && effectiveHeight > 0) {
            effectiveWidth.toFloat() / effectiveHeight.toFloat()
        } else {
            16f / 9f
        }
        
        val videoModifier = if (forceFullDisplay) {
            Modifier.fillMaxSize()
        } else {
            if (containerSize != IntSize.Zero && effectiveWidth > 0 && effectiveHeight > 0) {
                val containerW = containerSize.width.toFloat()
                val containerH = containerSize.height.toFloat()
                val containerAspect = containerW / containerH
                
                // Calculate target size to fit within container while preserving aspect ratio
                val (targetW, targetH) = if (videoAspectRatio > containerAspect) {
                    // Video is wider than container - fit to width, height will be smaller
                    containerW to (containerW / videoAspectRatio)
                } else {
                    // Video is taller than container - fit to height, width will be smaller
                    (containerH * videoAspectRatio) to containerH
                }
                
                val targetWidthDp = with(density) { targetW.toDp() }
                val targetHeightDp = with(density) { targetH.toDp() }
                
                Modifier
                    .width(targetWidthDp)
                    .height(targetHeightDp)
            } else {
                // Fallback - use full size until we have measurements
                Modifier.fillMaxSize()
            }
        }

        AndroidView(
            modifier = videoModifier,
            factory = { context ->
                createRenderer(
                    context = context,
                    mirror = doMirror,
                    forceFullDisplay = forceFullDisplay,
                    onFirstFrameRendered = { 
                        firstFrameRendered = true 
                    },
                    onFrameResolutionChanged = { w, h, r ->
                        videoWidth = w
                        videoHeight = h
                        videoRotation = r
                    }
                ).also { renderer ->
                    updatedTrack.value.addSink(renderer)
                    boundTrack = updatedTrack.value
                }
            },
            update = { renderer ->
                renderer.setMirror(doMirror)
                renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                
                renderer.visibility = if (firstFrameRendered) View.VISIBLE else View.INVISIBLE

                val desiredTrack = updatedTrack.value
                if (boundTrack !== desiredTrack) {
                    boundTrack?.removeSink(renderer)
                    desiredTrack.addSink(renderer)
                    boundTrack = desiredTrack
                    firstFrameRendered = false
                    renderer.visibility = View.INVISIBLE
                }
            },
            onRelease = { renderer ->
                boundTrack?.removeSink(renderer)
                boundTrack = null
                renderer.release()
            }
        )
    }
}

private fun createRenderer(
    context: Context,
    mirror: Boolean,
    forceFullDisplay: Boolean,
    onFirstFrameRendered: () -> Unit,
    onFrameResolutionChanged: (width: Int, height: Int, rotation: Int) -> Unit
): SurfaceViewRenderer {
    return SurfaceViewRenderer(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        
        val sharedContext = SharedEglContext.getEglBaseContext()
        init(sharedContext, object : RendererCommon.RendererEvents {
            override fun onFirstFrameRendered() {
                onFirstFrameRendered()
            }
            override fun onFrameResolutionChanged(width: Int, height: Int, rotation: Int) {
                onFrameResolutionChanged(width, height, rotation)
            }
        })
        
        setEnableHardwareScaler(true)
        setMirror(mirror)
        setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        
        // Start invisible until first frame renders (prevents green flash)
        visibility = View.INVISIBLE
    }
}