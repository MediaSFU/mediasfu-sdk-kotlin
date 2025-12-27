package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.RecordingParams
import com.mediasfu.sdk.model.RtpCapabilities

/**
 * Response received when joining a room.
 *
 * Contains RTP capabilities, room parameters, and status flags indicating
 * whether the user is the host, if recording is allowed, and various room states.
 */
data class ResponseJoinRoom(
    val rtpCapabilities: RtpCapabilities? = null,
    val success: Boolean? = false,
    val roomRecvIPs: List<String>? = null,
    val meetingRoomParams: MeetingRoomParams? = null,
    val recordingParams: RecordingParams? = null,
    val secureCode: String? = null,
    val recordOnly: Boolean? = null,
    val isHost: Boolean? = null,
    val safeRoom: Boolean? = null,
    val autoStartSafeRoom: Boolean? = null,
    val safeRoomStarted: Boolean? = null,
    val safeRoomEnded: Boolean? = null,
    val reason: String? = null,
    val banned: Boolean? = null,
    val suspended: Boolean? = null,
    val noAdmin: Boolean? = null
)

/**
 * Response received when joining a local room.
 *
 * Similar to ResponseJoinRoom but includes additional local-specific fields
 * like mediasfuURL, apiKey, and allowRecord.
 */
data class ResponseJoinLocalRoom(
    val rtpCapabilities: RtpCapabilities? = null,
    val isHost: Boolean? = null,
    val eventStarted: Boolean? = null,
    val isBanned: Boolean? = null,
    val hostNotJoined: Boolean? = null,
    val eventRoomParams: MeetingRoomParams? = null,
    val recordingParams: RecordingParams? = null,
    val secureCode: String? = null,
    val mediasfuURL: String? = null,
    val apiKey: String? = null,
    val apiUserName: String? = null,
    val allowRecord: Boolean? = null
)

/**
 * Exception thrown when socket emit operations fail.
 *
 * @property message Descriptive error message
 * @property cause Original exception that caused this error
 */
class SocketEmitException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
