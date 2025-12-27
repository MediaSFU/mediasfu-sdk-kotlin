package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.WaitingRoomParticipant
import com.mediasfu.sdk.methods.message_methods.SendMessageOptions
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.components.event_settings.EventSettingsModalOptions
import com.mediasfu.sdk.ui.components.whiteboard.ConfigureWhiteboardModalOptions
import com.mediasfu.sdk.ui.components.whiteboard.WhiteboardModalOptions

/**
 * Entry points for overriding key MediaSFU UI surfaces, mirroring
 * the React SDK's `uiOverrides` structure.
 */
data class MediasfuUiOverrides(
    val requestsModal: ComponentOverride<RequestsModalProps>? = null,
    val waitingModal: ComponentOverride<WaitingModalProps>? = null,
    val pollModal: ComponentOverride<PollModalProps>? = null,
    val menuModal: ComponentOverride<MenuModalProps>? = null,
    val participantsModal: ComponentOverride<ParticipantsModalProps>? = null,
    val messagesModal: ComponentOverride<MessagesModalProps>? = null,
    val eventSettingsModal: ComponentOverride<EventSettingsModalOptions>? = null,
    val confirmExitModal: ComponentOverride<ConfirmExitModalProps>? = null,
    val confirmHereModal: ComponentOverride<ConfirmHereModalProps>? = null,
    val shareEventModal: ComponentOverride<ShareEventModalProps>? = null,
    val whiteboardModal: ComponentOverride<WhiteboardModalOptions>? = null,
    val configureWhiteboardModal: ComponentOverride<ConfigureWhiteboardModalOptions>? = null
)

/**
 * Structured props supplied to the requests modal override.
 */
data class RequestsModalProps(
    val isVisible: Boolean,
    val requests: List<Request>,
    val filter: String,
    val pendingCount: Int,
    val onFilterChange: (String) -> Unit,
    val onClose: () -> Unit,
    val onAccept: (Request) -> Unit,
    val onReject: (Request) -> Unit
)

/**
 * Structured props supplied to the waiting room modal override.
 */
data class WaitingModalProps(
    val isVisible: Boolean,
    val participants: List<WaitingRoomParticipant>,
    val filter: String,
    val pendingCount: Int,
    val onFilterChange: (String) -> Unit,
    val onClose: () -> Unit,
    val onAllow: (WaitingRoomParticipant) -> Unit,
    val onDeny: (WaitingRoomParticipant) -> Unit
)

/**
 * Shared poll type descriptor used by poll modal overrides.
 */
data class MediasfuPollTypeOption(val value: String, val label: String)

/**
 * Structured props supplied to the poll modal override.
 */
data class PollModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val isHost: Boolean,
    val memberName: String,
    val activePoll: Poll?,
    val previousPolls: List<Poll>,
    val pollTypeOptions: List<MediasfuPollTypeOption>,
    val questionState: MutableState<String>,
    val pollTypeState: MutableState<String>,
    val customOptionInputs: SnapshotStateList<String>,
    val submittingState: MutableState<Boolean>,
    val votingIndexState: MutableState<Int?>,
    val endingPollIdState: MutableState<String?>,
    val resetCreatePollState: () -> Unit,
    val handleEndPoll: (String) -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Structured props supplied to the menu modal override.
 */
data class MenuModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val roomName: String,
    val roomLink: String,
    val adminPasscode: String,
    val totalRequests: Int,
    val waitingCount: Int,
    val islevel: String,
    val coHost: String,
    val member: String,
    val eventType: EventType,
    val coHostResponsibility: List<CoHostResponsibility>,
    val onClose: () -> Unit,
    val onOpenRequests: () -> Unit,
    val onOpenWaiting: () -> Unit,
    val onOpenPolls: () -> Unit,
    val onOpenShareEvent: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenRecording: () -> Unit,
    val onOpenCoHost: () -> Unit,
    val onOpenMediaSettings: () -> Unit,
    val onOpenDisplaySettings: () -> Unit,
    val onOpenBreakoutRooms: () -> Unit,
    val onOpenWhiteboard: () -> Unit,
    val onOpenConfigureWhiteboard: () -> Unit,
    val whiteboardActive: Boolean,
    val whiteboardCollaboratorCount: Int,
    val canAccessWhiteboard: Boolean,
    val canConfigureWhiteboard: Boolean
)

/**
 * Structured props supplied to the participants modal override.
 */
data class ParticipantsModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val filter: String,
    val participants: List<Participant>,
    val filteredParticipants: List<Participant>,
    val participantCount: Int,
    val onFilterChange: (String) -> Unit,
    val onClose: () -> Unit
)

/**
 * Structured props supplied to the messages modal override.
 */
data class MessagesModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val messages: List<Message>,
    val onClose: () -> Unit,
    // Message system fields
    val eventType: EventType,
    val member: String,
    val islevel: String,
    val coHost: String,
    val coHostResponsibility: List<CoHostResponsibility>,
    val startDirectMessage: Boolean,
    val directMessageDetails: Participant?,
    val chatSetting: String,
    val roomName: String,
    val socket: SocketManager?,
    val showAlert: ShowAlert?,
    val onSendMessage: (SendMessageOptions) -> Unit
)

/**
 * Structured props supplied to the share event modal override.
 */
data class ShareEventModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val roomName: String,
    val shareLink: String,
    val adminPasscode: String?,
    val isHost: Boolean,
    val eventType: EventType,
    val shareButtonsEnabled: Boolean,
    val onDismiss: () -> Unit
)

/**
 * Structured props supplied to the confirm exit modal override.
 */
data class ConfirmExitModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val memberName: String,
    val roomName: String,
    val isHost: Boolean,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Structured props supplied to the confirm here modal override.
 */
data class ConfirmHereModalProps(
    val state: MediasfuGenericState,
    val isVisible: Boolean,
    val message: String,
    val countdownSeconds: Int,
    val onConfirm: () -> Unit,
    val onTimeout: () -> Unit,
    val onDismiss: () -> Unit
)
