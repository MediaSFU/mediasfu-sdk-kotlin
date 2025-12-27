package com.mediasfu.sdk.background

import com.mediasfu.sdk.util.Logger
/**
 * iOS implementation of VirtualBackgroundProcessorFactory.
 * 
 * Note: Full iOS implementation requires MLKit for iOS which uses
 * different APIs. This is a stub that returns null.
 */
actual object VirtualBackgroundProcessorFactory {
    
    /**
     * iOS implementation not yet available.
     */
    actual fun create(context: Any?): VirtualBackgroundProcessor? {
        // TODO: Implement iOS version using MLKit for iOS
        Logger.d("VirtualBackgroundPro", "VirtualBackgroundProcessorFactory: iOS implementation not yet available")
        return null
    }
    
    /**
     * iOS support not yet implemented.
     */
    actual fun isSupported(): Boolean = false
}
