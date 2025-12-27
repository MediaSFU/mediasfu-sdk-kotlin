package com.mediasfu.sdk.ui.components.participants
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.*

/**
 * ParticipantListItem - Individual participant list item with controls.
 *
 * Displays single participant with mute/message/remove action buttons.
 *
 * @property options Configuration options for the participant list item
 */
data class ParticipantListItemOptions(
    val participant: Participant,
    val isBroadcast: Boolean = false,
    val onMuteParticipants: (MuteParticipantsOptions) -> Unit,
    val onMessageParticipants: (MessageParticipantsOptions) -> Unit,
    val onRemoveParticipants: (RemoveParticipantsOptions) -> Unit,
    val parameters: ParticipantsModalParameters,
)

interface ParticipantListItem : MediaSfuUIComponent {
    val options: ParticipantListItemOptions
    override val id: String get() = "participant_list_item"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets participant display name with role indicator
     */
    fun getDisplayName(): String {
        val name = options.participant.name ?: "Unknown"
        return when {
            options.participant.islevel == "2" -> "$name (Host)"
            options.participant.name == options.parameters.coHost -> "$name (Co-host)"
            else -> name
        }
    }
    
    /**
     * Checks if mute button should be shown
     */
    fun canMute(): Boolean {
        val islevel = options.parameters.islevel
        val coHost = options.parameters.coHost
        val member = options.parameters.member
        
        return islevel == "2" || coHost == member
    }
    
    /**
     * Checks if message button should be shown
     */
    fun canMessage(): Boolean {
        return !options.isBroadcast
    }
    
    /**
     * Checks if remove button should be shown
     */
    fun canRemove(): Boolean {
        val islevel = options.parameters.islevel
        val participant = options.participant
        
        // Host can remove anyone except themselves
        // Co-host cannot remove host
        return islevel == "2" && participant.islevel != "2"
    }
}

/**
 * Default implementation of ParticipantListItem
 */
class DefaultParticipantListItem(
    override val options: ParticipantListItemOptions
) : ParticipantListItem {
    fun render(): Any {
        return mapOf(
            "type" to "participantListItem",
            "participant" to options.participant,
            "displayName" to getDisplayName(),
            "canMute" to canMute(),
            "canMessage" to canMessage(),
            "canRemove" to canRemove(),
            "isMuted" to options.participant.muted,
            "onMute" to options.onMuteParticipants,
            "onMessage" to options.onMessageParticipants,
            "onRemove" to options.onRemoveParticipants
        )
    }
}
