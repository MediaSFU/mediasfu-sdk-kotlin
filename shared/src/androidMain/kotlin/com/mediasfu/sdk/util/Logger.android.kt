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
    
    actual fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            AndroidLog.d(sanitizeTag(tag), message)
        }
    }
    
    actual fun i(tag: String, message: String) {
        AndroidLog.i(sanitizeTag(tag), message)
    }
    
    actual fun w(tag: String, message: String) {
        AndroidLog.w(sanitizeTag(tag), message)
    }
    
    actual fun e(tag: String, message: String) {
        AndroidLog.e(sanitizeTag(tag), message)
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable) {
        AndroidLog.e(sanitizeTag(tag), message, throwable)
    }
}
