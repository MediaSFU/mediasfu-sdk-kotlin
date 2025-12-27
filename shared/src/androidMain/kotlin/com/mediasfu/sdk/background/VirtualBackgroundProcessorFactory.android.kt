package com.mediasfu.sdk.background

import com.mediasfu.sdk.util.Logger
import android.content.Context

/**
 * Android implementation of VirtualBackgroundProcessorFactory.
 */
actual object VirtualBackgroundProcessorFactory {
    
    /**
     * Create a new VirtualBackgroundProcessor instance.
     * 
     * @param context Android Context
     * @return A new AndroidVirtualBackgroundProcessor instance
     */
    actual fun create(context: Any?): VirtualBackgroundProcessor? {
        val androidContext = context as? Context
        if (androidContext == null) {
            Logger.d("VirtualBackgroundPro", "VirtualBackgroundProcessorFactory: Invalid context, expected Android Context")
            return null
        }
        return AndroidVirtualBackgroundProcessor(androidContext)
    }
    
    /**
     * Virtual background processing is supported on Android via ML Kit.
     */
    actual fun isSupported(): Boolean = true
}
