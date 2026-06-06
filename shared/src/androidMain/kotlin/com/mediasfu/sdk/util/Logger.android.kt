package com.mediasfu.sdk.util

import android.util.Log as AndroidLog

/**
 * Android implementation of Logger using android.util.Log
 */
actual object Logger {
    private const val MAX_TAG_LENGTH = 23
    
    actual var isDebugEnabled: Boolean = false
    
    private fun sanitizeTag(tag: String): String {
        return if (tag.length > MAX_TAG_LENGTH) {
            tag.substring(0, MAX_TAG_LENGTH)
        } else {
            tag
        }
    }

    private inline fun safeLog(block: () -> Unit) {
        try {
            block()
        } catch (_: RuntimeException) {
            // Host-side Android unit tests use stubbed android.util.Log methods that throw.
        }
    }
    
    actual fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            safeLog { AndroidLog.d(sanitizeTag(tag), message) }
        }
    }
    
    actual fun i(tag: String, message: String) {
        safeLog { AndroidLog.i(sanitizeTag(tag), message) }
    }
    
    actual fun w(tag: String, message: String) {
        safeLog { AndroidLog.w(sanitizeTag(tag), message) }
    }
    
    actual fun e(tag: String, message: String) {
        safeLog { AndroidLog.e(sanitizeTag(tag), message) }
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable) {
        safeLog { AndroidLog.e(sanitizeTag(tag), message, throwable) }
    }
}
