package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig

/**
 * Factory function to create a SocketManager instance.
 * Uses platform-specific implementations.
 */
expect fun createSocketManager(): SocketManager

/**
 * Internal utility for converting between different data formats.
 */
internal expect object SocketDataConverter {
    /**
     * Convert platform-specific data to a Map
     */
    fun toMap(data: Any?): Map<String, Any?>
    
    /**
     * Convert a Map to platform-specific format
     */
    fun fromMap(map: Map<String, Any?>): Any
}
