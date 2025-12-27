package com.mediasfu.sdk.methods.participants_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager

/**
 * Defines options for muting a participant in a room.
 */
data class MuteParticipantsOptions(
    val socket: SocketManager? = null,
    val coHostResponsibility: List<CoHostResponsibility>,
    val participant: Participant,
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val coHost: String,
    val roomName: String
)

/**
 * Type definition for the function that mutes a participant.
 */
typealias MuteParticipantsType = suspend (MuteParticipantsOptions) -> Unit

/**
 * Mutes a participant in the room if the current member has the necessary permissions.
 * 
 * This function checks if the current member has the required permissions based on their level
 * and co-host responsibilities. If authorized, it emits a socket event to mute the participant.
 * 
 * Example:
 * ```kotlin
 * val options = MuteParticipantsOptions(
 *     socket = socketInstance,
 *     coHostResponsibility = listOf(CoHostResponsibility(name = "media", value = true)),
 *     participant = Participant(id = "123", name = "John Doe", muted = false, islevel = "1"),
 *     member = "currentMember",
 *     islevel = "2",
 *     showAlert = { alert -> Logger.d("MuteParticipants", alert.message) },
 *     coHost = "coHostMember",
 *     roomName = "room1"
 * )
 * 
 * muteParticipants(options)
 * ```
 */
suspend fun muteParticipants(options: MuteParticipantsOptions) {
    var mediaValue = false
    
    try {
        mediaValue = options.coHostResponsibility
            .find { it.name == "media" }
            ?.value ?: false
    } catch (e: Exception) {
        mediaValue = false
    }
    
    if (options.islevel == "2" ||
        (options.coHost == options.member && mediaValue)) {
        if (!(options.participant.muted ?: false) &&
            options.participant.islevel != "2") {
            val participantId = options.participant.id
            options.socket?.emit("controlMedia", mapOf(
                "participantId" to participantId,
                "participantName" to options.participant.name,
                "type" to "all",
                "roomName" to options.roomName
            ))
        }
    } else {
    options.showAlert.call(
            message = "You are not allowed to mute other participants",
            type = "danger",
            duration = 3000
        )
    }
}
