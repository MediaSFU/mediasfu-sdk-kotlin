package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.ResponseJoinLocalRoom
import com.mediasfu.sdk.socket.ResponseJoinRoom

/**
 * Represents the options for creating a ResponseJoinRoom.
 *
 * @property localRoom The local room response to convert
 */
data class CreateResponseJoinRoomOptions(
    val localRoom: ResponseJoinLocalRoom
)

/**
 * Creates a ResponseJoinRoom object from a ResponseJoinLocalRoom object.
 *
 * This function takes a `CreateResponseJoinRoomOptions` containing the
 * `ResponseJoinLocalRoom` object and returns a `ResponseJoinRoom`.
 *
 * ### Example:
 * ```kotlin
 * val localRoom = ResponseJoinLocalRoom(
 *     rtpCapabilities = null,
 *     isHost = true,
 *     eventStarted = false,
 *     isBanned = false,
 *     hostNotJoined = false,
 *     eventRoomParams = MeetingRoomParams(...),
 *     recordingParams = RecordingParams(...),
 *     secureCode = "12345",
 *     mediasfuURL = "https://example.com",
 *     apiKey = "api-key",
 *     apiUserName = "user-name",
 *     allowRecord = true
 * )
 *
 * val joinRoom = createResponseJoinRoom(
 *     CreateResponseJoinRoomOptions(localRoom = localRoom)
 * )
 * Logger.d("CreateResponseJoinRo", joinRoom)
 * ```
 *
 * ### Platform Notes:
 * - **Android**: Uses Android-specific networking
 * - **iOS**: Uses iOS-specific networking
 * - **Common**: Core logic is platform-agnostic
 *
 * @param options Configuration options containing the local room response
 * @return ResponseJoinRoom object created from the local room data
 */
suspend fun createResponseJoinRoom(options: CreateResponseJoinRoomOptions): ResponseJoinRoom {
    val localRoom = options.localRoom
    
    val isBanned = localRoom.isBanned == true
    val hasRtpCapabilities = localRoom.rtpCapabilities != null

    return ResponseJoinRoom(
        rtpCapabilities = localRoom.rtpCapabilities,
        success = hasRtpCapabilities,
        roomRecvIPs = listOf("none"), // Set to ["none"] for local/community edition as per Flutter reference
        meetingRoomParams = localRoom.eventRoomParams,
        recordingParams = localRoom.recordingParams,
        secureCode = localRoom.secureCode,
        recordOnly = false,
        isHost = localRoom.isHost,
        safeRoom = false,
        autoStartSafeRoom = false,
        safeRoomStarted = false,
        safeRoomEnded = false,
        reason = if (isBanned) "User is banned from the room." else null,
        banned = localRoom.isBanned,
        suspended = false,
        noAdmin = localRoom.hostNotJoined
    )
}
