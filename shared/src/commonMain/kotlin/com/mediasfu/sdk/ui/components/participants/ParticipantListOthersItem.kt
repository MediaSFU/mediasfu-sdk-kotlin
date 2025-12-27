package com.mediasfu.sdk.ui.components.participants
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * ParticipantListOthersItem - Individual item for other participants list.
 *
 * Displays single regular participant with controls.
 *
 * @property options Configuration options for the other participant item
 */
data class ParticipantListOthersItemOptions(
    val participant: Participant,
    val isBroadcast: Boolean = false,
    val onMuteParticipants: (MuteParticipantsOptions) -> Unit,
    val onMessageParticipants: (MessageParticipantsOptions) -> Unit,
    val onRemoveParticipants: (RemoveParticipantsOptions) -> Unit,
    val parameters: ParticipantsModalParameters,
)

interface ParticipantListOthersItem : MediaSfuUIComponent {
    val options: ParticipantListOthersItemOptions
    override val id: String get() = "participant_list_others_item"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets participant display name
     */
    fun getDisplayName(): String {
        return options.participant.name ?: "Unknown"
    }
    
    /**
     * Checks if can manage this participant
     */
    fun canManage(): Boolean {
        val islevel = options.parameters.islevel
        val coHost = options.parameters.coHost
        val member = options.parameters.member
        val participantsValue = options.parameters.coHostResponsibility
            .any { it.name == "participants" && it.value }
        
        return islevel == "2" || (coHost == member && participantsValue)
    }
}

/**
 * Default implementation of ParticipantListOthersItem
 */
class DefaultParticipantListOthersItem(
    override val options: ParticipantListOthersItemOptions
) : ParticipantListOthersItem {
    fun render(): Any {
        return mapOf(
            "type" to "participantListOthersItem",
            "participant" to options.participant,
            "displayName" to getDisplayName(),
            "canManage" to canManage(),
            "isMuted" to options.participant.muted,
            "onMute" to options.onMuteParticipants,
            "onMessage" to options.onMessageParticipants,
            "onRemove" to options.onRemoveParticipants
        )
    }
}
