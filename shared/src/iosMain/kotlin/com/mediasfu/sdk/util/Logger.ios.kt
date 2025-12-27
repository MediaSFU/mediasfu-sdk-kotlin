package com.mediasfu.sdk.util

import platform.Foundation.NSLog

/**
 * iOS implementation of Logger using NSLog
 */
actual object Logger {
    actual var isDebugEnabled: Boolean = false
    
    actual fun d(tag: String, message: String) {
        if (isDebugEnabled) {
            NSLog("D/$tag: $message")
        }
    }
    
    actual fun i(tag: String, message: String) {
        NSLog("I/$tag: $message")
    }
    
    actual fun w(tag: String, message: String) {
        NSLog("W/$tag: $message")
    }
    
    actual fun e(tag: String, message: String) {
        NSLog("E/$tag: $message")
    }
    
    actual fun e(tag: String, message: String, throwable: Throwable) {
        NSLog("E/$tag: $message\n${throwable.stackTraceToString()}")
    }
}
