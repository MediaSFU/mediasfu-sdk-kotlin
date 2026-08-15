package com.mediasfu.sdk.methods.utils

internal const val MEDIA_SFU_CLOUD_ROOMS_ENDPOINT = "https://mediasfu.com/v1/rooms" // ENDPOINT_TOGGLE

internal fun normalizeCloudRoomsEndpoint(endpoint: String): String {
    return endpoint.trim().trimEnd('/').ifBlank { MEDIA_SFU_CLOUD_ROOMS_ENDPOINT }
}
