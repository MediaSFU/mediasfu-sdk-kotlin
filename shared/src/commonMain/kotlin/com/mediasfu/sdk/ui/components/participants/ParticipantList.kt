package com.mediasfu.sdk.ui.components.participants
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * ParticipantList - Displays list of participants with management controls.
 *
 * Shows host and co-host participants with mute/message/remove options.
 *
 * @property options Configuration options for the participant list
 */
data class ParticipantListOptions(
    val participants: List<Participant>,
    val isBroadcast: Boolean = false,
    val onMuteParticipants: (MuteParticipantsOptions) -> Unit,
    val onMessageParticipants: (MessageParticipantsOptions) -> Unit,
    val onRemoveParticipants: (RemoveParticipantsOptions) -> Unit,
    val parameters: ParticipantsModalParameters,
)

interface ParticipantList : MediaSfuUIComponent {
    val options: ParticipantListOptions
    override val id: String get() = "participant_list"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Filters participants for host list (level 2 and co-hosts)
     */
    fun getHostList(): List<Participant> {
        return options.participants.filter { participant ->
            participant.islevel == "2" || 
            (participant.name == options.parameters.coHost)
        }
    }
    
    /**
     * Gets display name for participant
     */
    fun getDisplayName(participant: Participant): String {
        val name = participant.name ?: "Unknown"
        return if (participant.islevel == "2") {
            "$name (Host)"
        } else if (participant.name == options.parameters.coHost) {
            "$name (Co-host)"
        } else {
            name
        }
    }
}

/**
 * Default implementation of ParticipantList
 */
class DefaultParticipantList(
    override val options: ParticipantListOptions
) : ParticipantList {
    fun render(): Any {
        val hostList = getHostList()
        
        return mapOf(
            "type" to "participantList",
            "participants" to hostList,
            "isBroadcast" to options.isBroadcast,
            "onMute" to options.onMuteParticipants,
            "onMessage" to options.onMessageParticipants,
            "onRemove" to options.onRemoveParticipants,
            "displayNames" to hostList.associate { it.name to getDisplayName(it) }
        )
    }
}
