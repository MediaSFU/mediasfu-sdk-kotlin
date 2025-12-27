package com.mediasfu.sdk

import platform.UIKit.UIDevice

actual fun getPlatform(): Platform = object : Platform {
    override val name: String = "iOS ${UIDevice.currentDevice.systemName()} ${UIDevice.currentDevice.systemVersion}"
}
