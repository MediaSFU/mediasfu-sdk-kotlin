package com.mediasfu.sdk.util

fun Any?.toStringAnyMap(): Map<String, Any> = when (this) {
    is Map<*, *> -> buildMap {
        this@toStringAnyMap.forEach { (key, value) ->
            val stringKey = key as? String ?: return@forEach
            val nonNullValue = value ?: return@forEach
            put(stringKey, nonNullValue)
        }
    }
    else -> emptyMap()
}

fun Any?.toLooseBoolean(default: Boolean = false): Boolean = when (this) {
    is Boolean -> this
    is Number -> this.toInt() != 0
    is String -> when {
        this.equals("true", ignoreCase = true) || this == "1" -> true
        this.equals("false", ignoreCase = true) || this == "0" -> false
        else -> default
    }
    else -> default
}

fun Any?.toLooseInt(default: Int = 0): Int = when (this) {
    is Int -> this
    is Number -> this.toInt()
    is String -> this.trim().toDoubleOrNull()?.toInt() ?: default
    else -> default
}