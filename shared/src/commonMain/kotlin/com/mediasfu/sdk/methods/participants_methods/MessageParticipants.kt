package com.mediasfu.sdk.methods.participants_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call

/**
 * Defines options for messaging a participant.
 */
data class MessageParticipantsOptions(
    val coHostResponsibility: List<CoHostResponsibility>,
    val participant: Participant,
    val member: String,
    val islevel: String,
    val showAlert: ShowAlert? = null,
    val coHost: String,
    val updateIsMessagesModalVisible: (Boolean) -> Unit,
    val updateDirectMessageDetails: (Participant?) -> Unit,
    val updateStartDirectMessage: (Boolean) -> Unit
)

/**
 * Type definition for the function that sends a message to participants.
 */
typealias MessageParticipantsType = (MessageParticipantsOptions) -> Unit

/**
 * Sends a direct message to a participant if the current member has the necessary permissions.
 * 
 * This function checks if the current member has the required permissions based on their level
 * and co-host responsibilities. If authorized, it initiates a direct message.
 * 
 * Example:
 * ```kotlin
 * val options = MessageParticipantsOptions(
 *     coHostResponsibility = listOf(CoHostResponsibility(name = "chat", value = true)),
 *     participant = Participant(name = "John Doe", islevel = "1"),
 *     member = "currentMember",
 *     islevel = "2",
 *     showAlert = { alert -> Logger.d("MessageParticipants", alert.message) },
 *     coHost = "coHostMember",
 * )
 * 
 * messageParticipants(options)
 * ```
 */
fun messageParticipants(options: MessageParticipantsOptions) {
    var chatValue = false
    
    try {
        chatValue = options.coHostResponsibility
            .find { it.name == "chat" }
            ?.value ?: false
    } catch (e: Exception) {
        chatValue = false
    }
    
    if (options.islevel == "2" ||
        (options.coHost == options.member && chatValue)) {
        if (options.participant.islevel != "2") {
            options.updateDirectMessageDetails(options.participant)
            options.updateStartDirectMessage(true)
            options.updateIsMessagesModalVisible(true)
        }
    } else {
    options.showAlert.call(
            message = "You are not allowed to send this message",
            type = "danger",
            duration = 3000
        )
    }
}
