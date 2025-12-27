package com.mediasfu.sdk.ui.components.waiting

import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.socket.SocketManager

/**
 * WaitingModal - Modal for managing participants in waiting room.
 *
 * Displays searchable list of participants in waiting room with accept/reject controls.
 *
 * @property options Configuration options for the waiting room modal
 */
data class WaitingRoomParticipant(
    val id: String,
    val name: String,
)

data class RespondToWaitingOptions(
    val participantId: String,
    val participantName: String,
    val type: Boolean, // true = accept, false = reject
    val socket: SocketManager?,
    val roomName: String,
    val updateWaitingList: (List<WaitingRoomParticipant>) -> Unit,
)

data class WaitingModalParameters(
    val filteredWaitingRoomList: List<WaitingRoomParticipant>,
    val getUpdatedAllParams: () -> WaitingModalParameters,
)

data class WaitingModalOptions(
    val isWaitingModalVisible: Boolean = false,
    val onWaitingRoomClose: () -> Unit,
    val waitingRoomCounter: Int = 0,
    val onWaitingRoomFilterChange: (String) -> Unit,
    val waitingRoomList: List<WaitingRoomParticipant> = emptyList(),
    val updateWaitingList: (List<WaitingRoomParticipant>) -> Unit,
    val roomName: String,
    val socket: SocketManager?,
    val onWaitingRoomItemPress: (RespondToWaitingOptions) -> Unit,
    val position: String = "topRight",
    val backgroundColor: Int = 0xFF83C0E9.toInt(),
    val parameters: WaitingModalParameters,
)

interface WaitingModal : MediaSfuUIComponent {
    val options: WaitingModalOptions
    override val id: String get() = "waiting_modal"
    override val isVisible: Boolean get() = options.isWaitingModalVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /**
     * Filters waiting room list by search query
     */
    fun filterWaitingRoomList(query: String): List<WaitingRoomParticipant> {
        return if (query.isEmpty()) {
            options.waitingRoomList
        } else {
            options.waitingRoomList.filter { participant ->
                participant.name.contains(query, ignoreCase = true)
            }
        }
    }
    
    /**
     * Handles accepting a participant
     */
    fun handleAccept(participant: WaitingRoomParticipant) {
        options.onWaitingRoomItemPress(
            RespondToWaitingOptions(
                participantId = participant.id,
                participantName = participant.name,
                type = true,
                socket = options.socket,
                roomName = options.roomName,
                updateWaitingList = options.updateWaitingList
            )
        )
    }
    
    /**
     * Handles rejecting a participant
     */
    fun handleReject(participant: WaitingRoomParticipant) {
        options.onWaitingRoomItemPress(
            RespondToWaitingOptions(
                participantId = participant.id,
                participantName = participant.name,
                type = false,
                socket = options.socket,
                roomName = options.roomName,
                updateWaitingList = options.updateWaitingList
            )
        )
    }
}

/**
 * Default implementation of WaitingModal
 */
class DefaultWaitingModal(
    override val options: WaitingModalOptions
) : WaitingModal {
    fun render(): Map<String, Any> {
        return mapOf(
            "type" to "waitingModal",
            "isVisible" to options.isWaitingModalVisible,
            "onClose" to options.onWaitingRoomClose,
            "waitingRoomCounter" to options.waitingRoomCounter,
            "waitingRoomList" to options.waitingRoomList,
            "onFilterChange" to options.onWaitingRoomFilterChange,
            "position" to options.position,
            "backgroundColor" to options.backgroundColor,
            "onAccept" to { participant: WaitingRoomParticipant -> handleAccept(participant) },
            "onReject" to { participant: WaitingRoomParticipant -> handleReject(participant) }
        )
    }
}
