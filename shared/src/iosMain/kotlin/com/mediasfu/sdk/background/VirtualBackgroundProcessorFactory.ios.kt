package com.mediasfu.sdk.background

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo

/**
 * iOS implementation of VirtualBackgroundProcessorFactory.
 *
 * Uses Vision framework VNGeneratePersonSegmentationRequest (iOS 15+).
 * Returns false on iOS 14.x to degrade gracefully.
 */
actual object VirtualBackgroundProcessorFactory {

    /**
     * Virtual background is supported on iOS 15+ via the Vision framework.
     * VNGeneratePersonSegmentationRequest is unavailable on earlier OS versions.
     */
    @OptIn(ExperimentalForeignApi::class)
    actual fun isSupported(): Boolean {
        val version = cValue<NSOperatingSystemVersion> {
            majorVersion = 15
            minorVersion = 0
            patchVersion = 0
        }
        return NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(version)
    }

    /**
     * Create an IOSVirtualBackgroundProcessor on iOS 15+.
     * Returns null on iOS 14.x where Vision segmentation is unavailable.
     */
    actual fun create(context: Any?): VirtualBackgroundProcessor? {
        return if (isSupported()) IOSVirtualBackgroundProcessor() else null
    }
}
