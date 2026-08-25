package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.methods.utils.CreateJoinRoomResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

internal data class CloudPreJoinRequest(
    val roomName: String,
    val member: String,
    val adminPasscode: String,
    val islevel: String,
    val apiUserName: String,
    val apiToken: String,
    val link: String
)

internal suspend fun completeCloudPreJoin(
    response: CreateJoinRoomResponse,
    member: String,
    islevel: String,
    adminPasscodeOverride: String? = null,
    timeoutMillis: Long = 60_000L,
    connect: (CloudPreJoinRequest, (Boolean) -> Unit) -> Unit
): Result<CloudPreJoinRequest> {
    val roomSecret = response.secret.trim()
    val request = CloudPreJoinRequest(
        roomName = response.roomName.trim(),
        member = member.trim(),
        adminPasscode = adminPasscodeOverride?.trim().orEmpty().ifBlank {
            response.secureCode?.trim().orEmpty().ifBlank { roomSecret }
        },
        islevel = islevel.trim(),
        apiUserName = response.roomName.trim(),
        apiToken = roomSecret,
        link = response.link.trim().ifBlank { response.publicURL.trim() }
    )

    val missing = buildList {
        if (request.roomName.isBlank()) add("room name")
        if (request.member.isBlank()) add("display name")
        if (request.apiToken.isBlank()) add("room secret")
        if (request.link.isBlank()) add("media endpoint")
    }
    if (missing.isNotEmpty()) {
        return Result.failure(
            IllegalArgumentException("Room response is missing ${missing.joinToString()}.")
        )
    }

    val connected = withTimeoutOrNull(timeoutMillis) {
        suspendCancellableCoroutine { continuation ->
            connect(request) { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        }
    }

    return when (connected) {
        true -> Result.success(request)
        false -> Result.failure(IllegalStateException("Unable to join the MediaSFU room."))
        null -> Result.failure(IllegalStateException("MediaSFU room connection timed out."))
    }
}
