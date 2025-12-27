package com.mediasfu.sdk.methods.participants_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager

/**
 * Defines options for removing a participant from a room.
 */
data class RemoveParticipantsOptions(
    val coHostResponsibility: List<CoHostResponsibility>,
    val participant: Participant,
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val coHost: String,
    val participants: List<Participant>,
    val socket: SocketManager? = null,
    val roomName: String,
    val updateParticipants: (List<Participant>) -> Unit
)

/**
 * Type definition for the function that removes a participant.
 */
typealias RemoveParticipantsType = suspend (RemoveParticipantsOptions) -> Unit

/**
 * Removes a participant from the room if the user has the necessary permissions.
 * 
 * This function checks if the current user has the required permissions based on their level and co-host responsibilities.
 * If authorized, it emits a socket event to remove the participant and updates the local participant list.
 * 
 * Example:
 * ```kotlin
 * val options = RemoveParticipantsOptions(
 *     coHostResponsibility = listOf(CoHostResponsibility(name = "participants", value = true)),
 *     participant = Participant(id = "123", name = "John Doe", islevel = "1"),
 *     member = "currentMember",
 *     islevel = "2",
 *     showAlert = { alert -> Logger.d("RemoveParticipants", alert.message) },
 *     coHost = "coHostMember",
 *     participants = listOf(Participant(id = "123", name = "John Doe", islevel = "1")),
 *     socket = socketInstance,
 *     roomName = "room1",
 *     updateParticipants = { updatedParticipants -> Logger.d("RemoveParticipants", updatedParticipants) }
 * )
 * 
 * removeParticipants(options)
 * ```
 */
suspend fun removeParticipants(options: RemoveParticipantsOptions) {
    var participantsValue = false
    
    try {
        participantsValue = options.coHostResponsibility
            .find { it.name == "participants" }
            ?.value ?: false
    } catch (e: Exception) {
        participantsValue = false
    }
    
    if (options.islevel == "2" ||
        (options.coHost == options.member && participantsValue)) {
        if (options.participant.islevel != "2") {
            val participantId = options.participant.id
            
            // Emit a socket event to disconnect the user
            options.socket?.emit("disconnectUserInitiate", mapOf(
                "member" to options.participant.name,
                "roomName" to options.roomName,
                "id" to participantId
            ))
            
            // Remove the participant from the local array
            val updatedParticipants = options.participants.filter { it.name != options.participant.name }
            
            // Update the participants array
            options.updateParticipants(updatedParticipants)
        }
    } else {
    options.showAlert.call(
            message = "You are not allowed to remove other participants",
            type = "danger",
            duration = 3000
        )
    }
}
