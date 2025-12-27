package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.util.Logger
import org.webrtc.EglBase
import java.util.concurrent.atomic.AtomicReference

/**
 * Singleton holder for the shared EGL context used across the WebRTC stack.
 * 
 * The EGL context needs to be shared between:
 * 1. Video decoders (which output TextureBuffer frames)
 * 2. Video renderers (SurfaceViewRenderer)
 * 
 * If these use different EGL contexts, TextureBuffer frames from the decoder
 * will appear as black when rendered, because the texture IDs are only valid
 * within the EGL context where they were created.
 */
object SharedEglContext {
    private val eglBaseRef = AtomicReference<EglBase?>(null)
    
    /**
     * Sets the shared EGL base. Should be called by AndroidWebRtcDevice during initialization.
     * The EglBase is expected to live for the duration of the WebRTC session.
     */
    fun setEglBase(eglBase: EglBase) {
        val prev = eglBaseRef.getAndSet(eglBase)
        if (prev != null && prev !== eglBase) {
            Logger.d("SharedEglContext", "MediaSFU - SharedEglContext: WARNING - EglBase replaced. Previous context may still be in use.")
        }
        Logger.d("SharedEglContext", "MediaSFU - SharedEglContext: EglBase set: ${eglBase.eglBaseContext}")
    }
    
    /**
     * Gets the shared EGL base. Returns null if not yet initialized.
     */
    fun getEglBase(): EglBase? {
        return eglBaseRef.get()
    }
    
    /**
     * Gets the shared EGL base context. Returns null if not yet initialized.
     * Renderers should use this to share textures with decoders.
     */
    fun getEglBaseContext(): EglBase.Context? {
        return eglBaseRef.get()?.eglBaseContext
    }
    
    /**
     * Clears the shared EGL base reference. Should be called when AndroidWebRtcDevice is closed.
     */
    fun clear() {
        eglBaseRef.set(null)
        Logger.d("SharedEglContext", "MediaSFU - SharedEglContext: Cleared")
    }
}
