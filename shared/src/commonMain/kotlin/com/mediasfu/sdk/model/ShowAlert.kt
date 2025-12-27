package com.mediasfu.sdk.model

fun interface ShowAlert {
    operator fun invoke(message: String, type: String, duration: Int)
}

fun ShowAlert?.call(
    message: String,
    type: String = "info",
    duration: Int = 3000
) {
    this?.invoke(message, type, duration)
}
