package com.mediasfu.sdk.ui.components.participants
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.ui.*

/**
 * ParticipantsModal - Modal for displaying and managing event participants.
 *
 * Provides participant list with filtering, muting, messaging, and removal capabilities.
 *
 * @property options Configuration options for the participants modal
 */
data class ParticipantsModalOptions(
    val isParticipantsModalVisible: Boolean = false,
    val onParticipantsClose: () -> Unit,
    val onParticipantsFilterChange: (String) -> Unit,
    val participantsCounter: Int,
    val onMuteParticipants: (MuteParticipantsOptions) -> Unit,
    val onMessageParticipants: (MessageParticipantsOptions) -> Unit,
    val onRemoveParticipants: (RemoveParticipantsOptions) -> Unit,
    val backgroundColor: Int = 0xFF83C0E9.toInt(),
    val position: String = "topRight",
    val parameters: ParticipantsModalParameters,
)

data class ParticipantsModalParameters(
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val member: String,
    val islevel: String,
    val participants: List<Participant>,
    val eventType: EventType,
    val socket: SocketManager?,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val roomName: String,
    val updateIsMessagesModalVisible: (Boolean) -> Unit,
    val updateDirectMessageDetails: (Participant?) -> Unit,
    val updateStartDirectMessage: (Boolean) -> Unit,
    val updateParticipants: (List<Participant>) -> Unit,
)

data class MuteParticipantsOptions(
    val socket: SocketManager?,
    val roomName: String,
    val participant: Participant,
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val member: String,
    val islevel: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val participants: List<Participant>,
    val eventType: EventType,
)

data class MessageParticipantsOptions(
    val participant: Participant,
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val member: String,
    val islevel: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val eventType: EventType,
    val updateIsMessagesModalVisible: (Boolean) -> Unit,
    val updateDirectMessageDetails: (Participant?) -> Unit,
    val updateStartDirectMessage: (Boolean) -> Unit,
)

data class RemoveParticipantsOptions(
    val socket: SocketManager?,
    val roomName: String,
    val participant: Participant,
    val coHostResponsibility: List<CoHostResponsibility>,
    val coHost: String,
    val member: String,
    val islevel: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val participants: List<Participant>,
    val eventType: EventType,
    val updateParticipants: (List<Participant>) -> Unit,
)

data class ShowAlertOptions(
    val message: String,
    val type: String = "info",
    val durationMillis: Long = 3000L,
)

data class ParticipantsModalContainer(
    val isVisible: Boolean,
    val participants: List<Participant>,
    val filteredParticipants: List<Participant>,
    val searchQuery: String,
    val islevel: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val eventType: EventType,
    val updateParticipants: (List<Participant>) -> Unit,
)

interface ParticipantsModal : MediaSfuUIComponent {
    val options: ParticipantsModalOptions
    override val id: String get() = "participants_modal"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets modal dimensions based on screen size
     */
    fun getModalDimensions(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
        var modalWidth = (0.8 * screenWidth).toInt()
        if (modalWidth > 400) {
            modalWidth = 400
        }
        val modalHeight = (0.75 * screenHeight).toInt()
        return Pair(modalWidth, modalHeight)
    }
    
    /**
     * Gets host participant
     */
    fun getHostParticipant(): Participant? {
        return options.parameters.participants.find { it.islevel == "2" }
    }
    
    /**
     * Checks if user can manage participants
     */
    fun canManageParticipants(): Boolean {
        val islevel = options.parameters.islevel
        val coHost = options.parameters.coHost
        val member = options.parameters.member
        val participantsValue = options.parameters.coHostResponsibility
            .any { it.name == "participants" && it.value }
        
        return islevel == "2" || (coHost == member && participantsValue)
    }
}

/**
 * Default implementation of ParticipantsModal
 */
class DefaultParticipantsModal(
    override val options: ParticipantsModalOptions
) : ParticipantsModal {
    fun render(): Any {
        return mapOf(
            "type" to "participantsModal",
            "isVisible" to options.isParticipantsModalVisible,
            "onClose" to options.onParticipantsClose,
            "onFilterChange" to options.onParticipantsFilterChange,
            "participantsCounter" to options.participantsCounter,
            "backgroundColor" to options.backgroundColor,
            "position" to options.position,
            "canManage" to canManageParticipants(),
            "hostParticipant" to getHostParticipant(),
            "parameters" to options.parameters
        )
    }
}
