package com.mediasfu.sdk.methods.utils

import kotlinx.serialization.Serializable

/**
 * Represents the successful response from creating or joining a room.
 */
@Serializable
data class CreateJoinRoomResponse(
    val message: String,
    val roomName: String,
    val secureCode: String? = null,
    val publicURL: String,
    val link: String,
    val secret: String,
    val success: Boolean
)

/**
 * Represents the error response from creating or joining a room.
 */
@Serializable
data class CreateJoinRoomError(
    val error: String,
    val success: Boolean? = null
)

/**
 * Aggregates the result from attempting to create or join a room.
 * Mirrors the Flutter SDK where the payload may be either a success
 * response, an error response, or `null` when the request fails early.
 */
data class CreateJoinRoomResult(
    val data: Any? = null,
    val success: Boolean
)

/**
 * Signature for functions that create or join a MediaSFU room.
 *
 * @param payload Request body sent to the MediaSFU service.
 * @param apiUserName MediaSFU API username credential.
 * @param apiKey MediaSFU API key credential.
 */
typealias CreateJoinRoomType = suspend (
    payload: Map<String, Any?>,
    apiUserName: String,
    apiKey: String
) -> CreateJoinRoomResult

/**
 * Resolves the loosely typed payload into the correct REST helper call. This mirrors
 * the behaviour of the Flutter SDK by accepting dynamic maps and normalising the
 * values before delegating to [createRoomOnMediaSfu] or [joinRoomOnMediaSfu].
 */
suspend fun createJoinRoom(
    payload: Map<String, Any?>,
    apiUserName: String,
    apiKey: String
): CreateJoinRoomResult {
    val trimmedUser = apiUserName.trim()
    val trimmedKey = apiKey.trim()
    if (trimmedUser.isEmpty() || trimmedKey.isEmpty()) {
        return errorResult("API credentials are required")
    }

    val action = payload.stringValue("action")?.lowercase()
    if (action.isNullOrEmpty()) {
        return errorResult("Payload must include an action of either 'create' or 'join'")
    }

    val localLink = payload.stringValue("localLink")
        ?: payload.stringValue("link")
        ?: ""

    return try {
        when (action) {
            "create" -> {
                val userName = payload.stringValue("userName")
                if (userName.isNullOrBlank()) {
                    return errorResult("Creating a room requires userName")
                }

                val duration = payload.intValue("duration")?.coerceAtLeast(1) ?: 30
                val capacity = payload.intValue("capacity")?.coerceAtLeast(1) ?: 10
                val scheduledDate = payload.longValue("scheduledDate")
                val secureCode = payload.stringValue("secureCode")?.ifBlank { null }
                val eventType = payload.stringValue("eventType")?.ifBlank { null } ?: "meeting"
                val recordOnly = payload.boolValue("recordOnly") ?: false
                val safeRoom = payload.boolValue("safeRoom") ?: false
                val autoStartSafeRoom = payload.boolValue("autoStartSafeRoom") ?: false
                val safeRoomAction = payload.stringValue("safeRoomAction")?.ifBlank { null } ?: "kick"
                val dataBuffer = payload.boolValue("dataBuffer") ?: false
                val bufferType = payload.stringValue("bufferType")?.ifBlank { null } ?: "all"

                val createOptions = CreateMediaSFURoomOptions(
                    action = "create",
                    duration = duration,
                    capacity = capacity,
                    userName = userName,
                    scheduledDate = scheduledDate,
                    secureCode = secureCode,
                    eventType = eventType.lowercase(),
                    recordOnly = recordOnly,
                    safeRoom = safeRoom,
                    autoStartSafeRoom = autoStartSafeRoom,
                    safeRoomAction = safeRoomAction,
                    dataBuffer = dataBuffer,
                    bufferType = bufferType
                )

                createRoomOnMediaSfu(
                    CreateMediaSFUOptions(
                        payload = createOptions,
                        apiUserName = trimmedUser,
                        apiKey = trimmedKey,
                        localLink = localLink
                    )
                )
            }

            "join" -> {
                val userName = payload.stringValue("userName")
                val meetingId = payload.stringValue("meetingID")
                    ?: payload.stringValue("roomName")
                if (meetingId.isNullOrBlank() || userName.isNullOrBlank()) {
                    return errorResult("Joining a room requires both meetingID and userName")
                }

                val isLevel = payload.stringValue("islevel")?.ifBlank { null } ?: "0"
                val adminPasscode = payload.stringValue("adminPasscode")?.ifBlank { null }

                val joinOptions = JoinMediaSFURoomOptions(
                    action = "join",
                    meetingID = meetingId,
                    userName = userName,
                    adminPasscode = adminPasscode,
                    islevel = isLevel
                )

                joinRoomOnMediaSfu(
                    JoinMediaSFUOptions(
                        payload = joinOptions,
                        apiUserName = trimmedUser,
                        apiKey = trimmedKey,
                        localLink = localLink
                    )
                )
            }

            else -> errorResult("Unsupported action '$action'")
        }
    } catch (error: Throwable) {
        errorResult("Failed to ${action} room: ${error.message ?: "Unknown error"}")
    }
}

private fun Map<String, Any?>.stringValue(key: String): String? {
    val raw = this[key] ?: return null
    val value = when (raw) {
        is String -> raw
        is Number -> {
            val doubleValue = raw.toDouble()
            if (doubleValue % 1.0 == 0.0) {
                doubleValue.toLong().toString()
            } else {
                doubleValue.toString()
            }
        }
        is Boolean -> raw.toString()
        else -> null
    }

    return value?.trim()?.takeIf { it.isNotEmpty() }
}

private fun Map<String, Any?>.intValue(key: String): Int? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Number -> raw.toInt()
        is String -> raw.trim().toDoubleOrNull()?.toInt()
        else -> null
    }
}

private fun Map<String, Any?>.longValue(key: String): Long? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Number -> raw.toLong()
        is String -> raw.trim().toDoubleOrNull()?.toLong()
        else -> null
    }
}

private fun Map<String, Any?>.boolValue(key: String): Boolean? {
    val raw = this[key] ?: return null
    return when (raw) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> {
            when (raw.trim().lowercase()) {
                "true", "1", "yes", "y" -> true
                "false", "0", "no", "n" -> false
                else -> null
            }
        }
        else -> null
    }
}

private fun errorResult(message: String): CreateJoinRoomResult = CreateJoinRoomResult(
    data = CreateJoinRoomError(error = message, success = false),
    success = false
)
