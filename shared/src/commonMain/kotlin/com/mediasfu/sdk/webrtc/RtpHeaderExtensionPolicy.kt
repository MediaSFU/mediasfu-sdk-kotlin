package com.mediasfu.sdk.webrtc

internal const val VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI = "urn:3gpp:video-orientation"

internal expect fun shouldPreserveVideoOrientationHeaderExtension(): Boolean

internal fun RtpCapabilities.applyCurrentPlatformHeaderExtensionPolicy(): RtpCapabilities =
    applyHeaderExtensionPolicy(
        preserveVideoOrientation = shouldPreserveVideoOrientationHeaderExtension()
    )

internal fun Map<String, Any?>.applyCurrentPlatformHeaderExtensionPolicy(): Map<String, Any?> =
    applyHeaderExtensionPolicy(
        preserveVideoOrientation = shouldPreserveVideoOrientationHeaderExtension()
    )

internal fun RtpCapabilities.applyHeaderExtensionPolicy(
    preserveVideoOrientation: Boolean
): RtpCapabilities {
    if (preserveVideoOrientation) return this

    val filteredHeaderExtensions = headerExtensions.filterNot { ext ->
        ext.uri == VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI
    }

    return if (filteredHeaderExtensions.size == headerExtensions.size) {
        this
    } else {
        copy(headerExtensions = filteredHeaderExtensions)
    }
}

internal fun Map<String, Any?>.applyHeaderExtensionPolicy(
    preserveVideoOrientation: Boolean
): Map<String, Any?> {
    if (preserveVideoOrientation) return this

    val headerExtensions = this["headerExtensions"] as? List<*> ?: return this
    val filteredHeaderExtensions = headerExtensions.filterNot { ext ->
        (ext as? Map<*, *>)?.get("uri")?.toString() == VIDEO_ORIENTATION_RTP_HEADER_EXTENSION_URI
    }

    return if (filteredHeaderExtensions.size == headerExtensions.size) {
        this
    } else {
        toMutableMap().apply {
            put("headerExtensions", filteredHeaderExtensions)
        }
    }
}
