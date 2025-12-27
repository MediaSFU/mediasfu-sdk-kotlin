package com.mediasfu.sdk.util

/**
 * Multiplatform logging utility for MediaSFU SDK.
 * Provides consistent logging across Android and iOS platforms.
 */
expect object Logger {
    /**
     * Log a debug message
     */
    fun d(tag: String, message: String)
    
    /**
     * Log an info message
     */
    fun i(tag: String, message: String)
    
    /**
     * Log a warning message
     */
    fun w(tag: String, message: String)
    
    /**
     * Log an error message
     */
    fun e(tag: String, message: String)
    
    /**
     * Log an error message with throwable
     */
    fun e(tag: String, message: String, throwable: Throwable)
    
    /**
     * Check if debug logging is enabled
     */
    var isDebugEnabled: Boolean
}

/**
 * Extension function for easy logging with class tag
 */
inline fun <reified T> T.logD(message: String) {
    if (Logger.isDebugEnabled) {
        Logger.d(T::class.simpleName ?: "MediaSFU", message)
    }
}

inline fun <reified T> T.logI(message: String) {
    Logger.i(T::class.simpleName ?: "MediaSFU", message)
}

inline fun <reified T> T.logW(message: String) {
    Logger.w(T::class.simpleName ?: "MediaSFU", message)
}

inline fun <reified T> T.logE(message: String) {
    Logger.e(T::class.simpleName ?: "MediaSFU", message)
}

inline fun <reified T> T.logE(message: String, throwable: Throwable) {
    Logger.e(T::class.simpleName ?: "MediaSFU", message, throwable)
}
