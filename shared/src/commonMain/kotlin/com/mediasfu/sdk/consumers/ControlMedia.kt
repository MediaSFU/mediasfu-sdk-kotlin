package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert

/**
 * Options for controlling media in a room.
 *
 * @property participantId The unique ID of the participant whose media is being controlled
 * @property participantName The name of the participant
 * @property type Type of media to control ('audio', 'video', 'screenshare', or 'all')
 * @property socket The socket connection for communication
 * @property coHostResponsibility Responsibilities assigned to the co-host
 * @property participants List of participants in the room
 * @property member Current user's ID
 * @property islevel Level of control for the current user (e.g., admin level)
 * @property showAlert Optional function for showing alerts to the user
 * @property coHost ID of the co-host
 * @property roomName Name of the room where the control action is being performed
 */
data class ControlMediaOptions(
    val participantId: String,
    val participantName: String,
    val type: String, // 'audio', 'video', 'screenshare', or 'all'
    val socket: SocketManager?,
    val coHostResponsibility: List<CoHostResponsibility>,
    val participants: List<Participant>,
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val coHost: String,
    val roomName: String
)

/**
 * Controls media for a participant in a room by sending a `controlMedia` event to the server.
 *
 * This function allows specific users, like admins and authorized co-hosts, to manage the media
 * (audio, video, screenshare, or all) for other participants in a room. The function checks
 * permissions based on participant level, co-host responsibilities, and media type before
 * sending a control request to the server. Unauthorized users receive an alert instead.
 *
 * ### Logic Flow:
 * 1. **Permission Check**: Checks if the current user has permission to control media by verifying
 *    admin level or co-host responsibilities.
 * 2. **Participant Lookup**: Searches for the specified participant in the room.
 * 3. **Media Type and Status Check**: Based on the specified media type, checks the current state.
 * 4. **Emit Control Event**: If the user has permission and conditions are met, emits a
 *    `controlMedia` event with media details.
 *
 * ### Example:
 * ```kotlin
 * val options = ControlMediaOptions(
 *     participantId = "participant-123",
 *     participantName = "John Doe",
 *     type = "audio",
 *     socket = socket,
 *     coHostResponsibility = myCoHostResponsibility,
 *     participants = myParticipants,
 *     member = "user-456",
 *     islevel = "1",
 *     showAlert = { msg, type, duration -> Logger.d("ControlMedia", msg) },
 *     coHost = "cohost-789",
 *     roomName = "Room 1"
 * )
 *
 * controlMedia(options)
 * ```
 *
 * @param options Configuration and details for controlling media
 */
suspend fun controlMedia(options: ControlMediaOptions) {
    try {
        var mediaValue = false

        // Check co-host responsibilities for media control
        try {
            mediaValue = options.coHostResponsibility
                .firstOrNull { it.name == "media" }
                ?.value ?: false
        } catch (error: Exception) {
            Logger.e("ControlMedia", "Error retrieving media control value: ${error.message}")
        }

        // Find the participant by name
        val participant = options.participants.firstOrNull { obj ->
            obj.name == options.participantName
        }

        if (participant == null || participant.name.isEmpty()) {
            Logger.e("ControlMedia", "Participant not found")
            return
        }

        // Check permissions and media type conditions
        if (options.islevel == "2" ||
            (options.coHost == options.member && mediaValue)) {
            
            val shouldControl = when (options.type) {
                "audio" -> {
                    val muted = participant.muted ?: false
                    !muted && participant.islevel != "2"
                }
                "video" -> {
                    val videoOn = participant.videoOn ?: false
                    participant.islevel != "2" && videoOn
                }
                else -> true
            }

            if (shouldControl) {
                // Emit controlMedia event to the server
                options.socket?.emit(
                    event = "controlMedia",
                    data = mapOf(
                        "participantId" to options.participantId,
                        "participantName" to options.participantName,
                        "type" to options.type,
                        "roomName" to options.roomName
                    )
                )
            }
        } else {
            // Show an alert if the user is not allowed to control media
            options.showAlert?.invoke(
                "You are not allowed to control media for other participants.",
                "danger",
                3000
            )
        }
    } catch (error: Exception) {
        Logger.e("ControlMedia", "MediaSFU - controlMedia error: ${error.message}")
    }
}

