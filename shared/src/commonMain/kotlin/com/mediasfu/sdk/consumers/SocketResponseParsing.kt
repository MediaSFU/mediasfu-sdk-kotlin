package com.mediasfu.sdk.consumers

internal fun Any?.toLooseBoolean(default: Boolean = false): Boolean = when (this) {
    is Boolean -> this
    is Number -> this.toInt() != 0
    is String -> when {
        this.equals("true", ignoreCase = true) || this == "1" -> true
        this.equals("false", ignoreCase = true) || this == "0" -> false
        else -> default
    }
    else -> default
}

internal fun Any?.resumeAckSucceeded(defaultOnNull: Boolean = true): Boolean = when (this) {
    is Map<*, *> -> this["resumed"].toLooseBoolean()
    is Boolean -> this
    null -> defaultOnNull
    else -> false
}