package com.mediasfu.sdk.ui.components.participants
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * ParticipantListOthers - Displays list of other (non-host) participants.
 *
 * Shows regular participants with management controls.
 *
 * @property options Configuration options for the other participants list
 */
data class ParticipantListOthersOptions(
    val participants: List<Participant>,
    val isBroadcast: Boolean = false,
    val onMuteParticipants: (MuteParticipantsOptions) -> Unit,
    val onMessageParticipants: (MessageParticipantsOptions) -> Unit,
    val onRemoveParticipants: (RemoveParticipantsOptions) -> Unit,
    val parameters: ParticipantsModalParameters,
)

interface ParticipantListOthers : MediaSfuUIComponent {
    val options: ParticipantListOthersOptions
    override val id: String get() = "participant_list_others"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Filters participants for others list (excluding host and co-hosts)
     */
    fun getOthersList(): List<Participant> {
        return options.participants.filter { participant ->
            participant.islevel != "2" && 
            participant.name != options.parameters.coHost
        }
    }
}

/**
 * Default implementation of ParticipantListOthers
 */
class DefaultParticipantListOthers(
    override val options: ParticipantListOthersOptions
) : ParticipantListOthers {
    fun render(): Any {
        val othersList = getOthersList()
        
        return mapOf(
            "type" to "participantListOthers",
            "participants" to othersList,
            "isBroadcast" to options.isBroadcast,
            "onMute" to options.onMuteParticipants,
            "onMessage" to options.onMessageParticipants,
            "onRemove" to options.onRemoveParticipants
        )
    }
}
