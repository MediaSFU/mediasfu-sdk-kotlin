package com.mediasfu.sdk

actual fun getPlatform(): Platform = object : Platform {
    override val name: String = "Android ${android.os.Build.VERSION.SDK_INT}"
}
