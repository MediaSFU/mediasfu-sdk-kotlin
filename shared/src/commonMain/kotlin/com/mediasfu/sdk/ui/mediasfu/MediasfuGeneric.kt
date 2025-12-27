package com.mediasfu.sdk.ui.mediasfu
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.ui.mediasfu.SidebarContent
import com.mediasfu.sdk.ui.mediasfu.UnifiedModalState
import com.mediasfu.sdk.ui.mediasfu.UnifiedModalHost
import com.mediasfu.sdk.ui.mediasfu.ModernTheme
import com.mediasfu.sdk.ui.mediasfu.LocalModernColors
import kotlinx.datetime.Clock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.ScreenShare
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.StopScreenShare
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HowToVote
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SupervisorAccount
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material.icons.rounded.VideoCameraFront
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.ui.components.display.MiniAudio

// Extracted composable imports
import com.mediasfu.sdk.ui.mediasfu.ShareEventModal
import com.mediasfu.sdk.ui.mediasfu.ConfirmExitModal
import com.mediasfu.sdk.ui.mediasfu.ConfirmHereModal
import com.mediasfu.sdk.ui.mediasfu.WelcomePage
import com.mediasfu.sdk.ui.mediasfu.MeetingProgressTimerBadge
import com.mediasfu.sdk.ui.mediasfu.MeetingTimerBar
import androidx.compose.ui.platform.LocalClipboardManager
// import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.mediasfu.sdk.consumers.ComponentSizes
import com.mediasfu.sdk.consumers.DispStreamsOptions
import com.mediasfu.sdk.consumers.GeneratePageContentOptions
import com.mediasfu.sdk.consumers.GeneratePageContentParameters
import com.mediasfu.sdk.consumers.GridSizes
import com.mediasfu.sdk.consumers.OnScreenChangesOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.ScreenState
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.EngineReorderStreamsParameters
import com.mediasfu.sdk.consumers.generatePageContent
import com.mediasfu.sdk.consumers.onScreenChanges as consumerOnScreenChanges
import com.mediasfu.sdk.consumers.updateMiniCardsGridImpl
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.breakout_rooms_methods.BreakoutRoomUpdatedData as BreakoutData
import com.mediasfu.sdk.methods.breakout_rooms_methods.BreakoutRoomUpdatedOptions
import com.mediasfu.sdk.methods.breakout_rooms_methods.breakoutRoomUpdated
import com.mediasfu.sdk.methods.exit_methods.ConfirmExitOptions
import com.mediasfu.sdk.methods.exit_methods.confirmExit
import com.mediasfu.sdk.methods.menu_methods.LaunchMenuModalOptions
import com.mediasfu.sdk.methods.menu_methods.launchMenuModal
import com.mediasfu.sdk.methods.message_methods.SendMessageOptions
import com.mediasfu.sdk.methods.message_methods.sendMessage
import com.mediasfu.sdk.methods.polls_methods.HandleCreatePollOptions
import com.mediasfu.sdk.methods.polls_methods.HandleEndPollOptions
import com.mediasfu.sdk.methods.polls_methods.HandleVotePollOptions
import com.mediasfu.sdk.methods.polls_methods.LaunchPollOptions
import com.mediasfu.sdk.methods.polls_methods.PollUpdatedOptions
import com.mediasfu.sdk.methods.polls_methods.handleCreatePoll
import com.mediasfu.sdk.methods.polls_methods.handleEndPoll
import com.mediasfu.sdk.methods.polls_methods.handleVotePoll
import com.mediasfu.sdk.methods.polls_methods.launchPoll
import com.mediasfu.sdk.methods.polls_methods.pollUpdated
import com.mediasfu.sdk.methods.recording_methods.ConfirmRecordingOptions as MethodConfirmRecordingOptions
import com.mediasfu.sdk.methods.recording_methods.LaunchRecordingOptions
import com.mediasfu.sdk.methods.recording_methods.StartRecordingOptions as MethodStartRecordingOptions
import com.mediasfu.sdk.methods.recording_methods.confirmRecording
import com.mediasfu.sdk.methods.recording_methods.launchRecording
import com.mediasfu.sdk.methods.recording_methods.startRecording
import com.mediasfu.sdk.methods.requests_methods.LaunchRequestsOptions
import com.mediasfu.sdk.methods.requests_methods.RespondToRequestsOptions
import com.mediasfu.sdk.methods.requests_methods.launchRequests
import com.mediasfu.sdk.methods.requests_methods.respondToRequests
import com.mediasfu.sdk.methods.participants_methods.RemoveParticipantsOptions
import com.mediasfu.sdk.methods.participants_methods.removeParticipants
import com.mediasfu.sdk.methods.media_settings_methods.LaunchMediaSettingsOptions
import com.mediasfu.sdk.methods.media_settings_methods.launchMediaSettings
import com.mediasfu.sdk.methods.co_host_methods.ModifyCoHostSettingsOptions
import com.mediasfu.sdk.methods.co_host_methods.modifyCoHostSettings
import com.mediasfu.sdk.methods.stream_methods.ClickAudioOptions
import com.mediasfu.sdk.methods.stream_methods.ClickScreenShareOptions
import com.mediasfu.sdk.methods.stream_methods.ClickVideoOptions
import com.mediasfu.sdk.methods.stream_methods.clickAudio
import com.mediasfu.sdk.methods.stream_methods.clickScreenShare
import com.mediasfu.sdk.methods.stream_methods.clickVideo
import com.mediasfu.sdk.methods.stream_methods.switchAudio
import com.mediasfu.sdk.methods.stream_methods.switchVideo
import com.mediasfu.sdk.methods.stream_methods.switchVideoAlt
import com.mediasfu.sdk.methods.utils.CheckLimitsAndMakeRequestOptions
import com.mediasfu.sdk.methods.utils.CheckLimitsAndMakeRequestParameters
import com.mediasfu.sdk.methods.utils.CreateJoinRoomResult
import com.mediasfu.sdk.methods.utils.CreateMediaSFUOptions
import com.mediasfu.sdk.methods.utils.CreateResponseJoinRoomOptions
import com.mediasfu.sdk.methods.utils.JoinMediaSFUOptions
import com.mediasfu.sdk.methods.utils.checkLimitsAndMakeRequest
import com.mediasfu.sdk.methods.utils.createResponseJoinRoom
import com.mediasfu.sdk.methods.utils.createRoomOnMediaSfu
import com.mediasfu.sdk.methods.utils.joinRoomOnMediaSfu
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.waiting_methods.LaunchWaitingOptions
import com.mediasfu.sdk.methods.waiting_methods.RespondToWaitingOptions
import com.mediasfu.sdk.methods.waiting_methods.launchWaiting
import com.mediasfu.sdk.methods.waiting_methods.respondToWaiting
import com.mediasfu.sdk.methods.whiteboard_methods.CaptureCanvasStreamOptions
import com.mediasfu.sdk.methods.whiteboard_methods.LaunchConfigureWhiteboardOptions
import com.mediasfu.sdk.methods.whiteboard_methods.launchConfigureWhiteboard
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.model.BreakoutParticipant as ModelBreakoutParticipant
import com.mediasfu.sdk.model.CoHostResponsibility as ModelCoHostResponsibility
import com.mediasfu.sdk.model.SeedData as ModelSeedData
import com.mediasfu.sdk.network.mediaSfuJson
import com.mediasfu.sdk.producer_client.JoinRoomClientOptions
import com.mediasfu.sdk.producer_client.UpdateRoomParametersClientOptions
import com.mediasfu.sdk.producer_client.UpdateRoomParametersClientParameters
import com.mediasfu.sdk.producer_client.joinRoomClient
import com.mediasfu.sdk.producer_client.updateRoomParametersClient
import com.mediasfu.sdk.producer_client.*
import com.mediasfu.sdk.socket.ConnectionState
import com.mediasfu.sdk.socket.JoinLocalRoomOptions
import com.mediasfu.sdk.socket.ResponseJoinLocalRoom
import com.mediasfu.sdk.socket.ResponseJoinRoom
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.allMembers
import com.mediasfu.sdk.socket.allMembersRest
import com.mediasfu.sdk.socket.allWaitingRoomMembers
import com.mediasfu.sdk.socket.disconnect
import com.mediasfu.sdk.socket.joinLocalRoom
import com.mediasfu.sdk.socket.MeetingEndedOptions as SocketMeetingEndedOptions
import com.mediasfu.sdk.socket.MeetingStillThereOptions as SocketMeetingStillThereOptions
import com.mediasfu.sdk.socket.MeetingTimeRemainingOptions as SocketMeetingTimeRemainingOptions
import com.mediasfu.sdk.socket.ParticipantRequestedOptions as SocketParticipantRequestedOptions
import com.mediasfu.sdk.socket.PersonJoinedOptions as SocketPersonJoinedOptions
import com.mediasfu.sdk.socket.ReceiveMessageOptions as SocketReceiveMessageOptions
import com.mediasfu.sdk.socket.*
import com.mediasfu.sdk.model.MediaDeviceInfo as ModelMediaDeviceInfo
import com.mediasfu.sdk.ui.components.cohost.CoHostModalOptions
import com.mediasfu.sdk.ui.components.cohost.CoHostResponsibility as UiCoHostResponsibility
import com.mediasfu.sdk.ui.components.display_settings.DisplaySettingsModalOptions
import com.mediasfu.sdk.ui.components.display_settings.DisplaySettingsModalParameters
import com.mediasfu.sdk.ui.components.event_settings.EventSettingsModalOptions
import com.mediasfu.sdk.ui.components.event_settings.ModifySettingsOptions
import com.mediasfu.sdk.ui.components.media_settings.MediaSettingsModalOptions
import com.mediasfu.sdk.ui.components.media_settings.MediaSettingsModalParameters
import com.mediasfu.sdk.ui.components.media_settings.MediaDeviceInfo as UiMediaDeviceInfo
import com.mediasfu.sdk.ui.components.media_settings.SwitchAudioOptions as UiSwitchAudioOptions
import com.mediasfu.sdk.ui.components.media_settings.SwitchAudioOutputOptions as UiSwitchAudioOutputOptions
import com.mediasfu.sdk.ui.components.media_settings.SwitchVideoAltOptions as UiSwitchVideoAltOptions
import com.mediasfu.sdk.ui.components.media_settings.SwitchVideoOptions as UiSwitchVideoOptions
import com.mediasfu.sdk.ui.components.breakout.BreakoutParticipant as UiBreakoutParticipant
import com.mediasfu.sdk.ui.components.breakout.BreakoutRoomsModalOptions
import com.mediasfu.sdk.ui.components.breakout.BreakoutRoomsModalParameters
import com.mediasfu.sdk.ui.components.whiteboard.ConfigureWhiteboardModalOptions
import com.mediasfu.sdk.ui.components.whiteboard.ConfigureWhiteboardModalParameters
import com.mediasfu.sdk.ui.components.whiteboard.WhiteboardModalOptions
import com.mediasfu.sdk.ui.components.whiteboard.Whiteboard
import com.mediasfu.sdk.ui.components.whiteboard.WhiteboardOptions
import com.mediasfu.sdk.ui.components.whiteboard.WhiteboardParameters
import com.mediasfu.sdk.ui.components.display.*
import com.mediasfu.sdk.ui.components.ControlButtonOptions
import com.mediasfu.sdk.ui.ComponentStyle
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.debugJson
import com.mediasfu.sdk.webrtc.debugSummary
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val CONFIRM_HERE_COUNTDOWN_SECONDS = 60
private const val DEFAULT_SOCKET_BASE = "https://mediasfu.com"
private const val DEFAULT_WHITEBOARD_LIMIT = 12

private val seedNameSanitizer = Regex("[^a-z0-9]+")

// Provides demo data so development builds render without a live session.
private fun defaultSeedData(): ModelSeedData {
    val participants = listOf(
        Participant(
            id = "host-1",
            audioID = "audio-host-1",
            videoID = "video-host-1",
            islevel = "2",
            isHost = true,
            name = "Alex Morgan",
            muted = false,
            videoOn = true,
            audioOn = true
        ),
        Participant(
            id = "speaker-1",
            audioID = "audio-speaker-1",
            videoID = "video-speaker-1",
            islevel = "1",
            name = "Jordan Lee",
            muted = false,
            videoOn = true,
            audioOn = true
        ),
        Participant(
            id = "speaker-2",
            audioID = "audio-speaker-2",
            videoID = "video-speaker-2",
            islevel = "1",
            name = "Morgan Patel",
            muted = true,
            videoOn = true,
            audioOn = false
        ),
        Participant(
            id = "listener-1",
            audioID = "audio-listener-1",
            videoID = "video-listener-1",
            islevel = "0",
            name = "Riley Chen",
            muted = false,
            videoOn = false,
            audioOn = true
        )
    )

    return ModelSeedData(
        member = "Alex Morgan",
        host = "Alex Morgan",
        eventType = EventType.CONFERENCE,
        participants = participants,
        messages = listOf(
            Message(
                sender = "Alex Morgan",
                receivers = listOf("Everyone"),
                message = "Welcome to the MediaSFU demo session!",
                timestamp = "09:58",
                group = true
            ),
            Message(
                sender = "Jordan Lee",
                receivers = listOf("Everyone"),
                message = "Good morning all!",
                timestamp = "09:59",
                group = true
            )
        ),
        polls = listOf(
            Poll(
                id = "poll-1",
                question = "How is the call quality?",
                type = "singleChoice",
                options = listOf("Great", "Okay", "Needs work"),
                votes = listOf(8, 2, 0),
                status = "live"
            )
        ),
        breakoutRooms = listOf(
            listOf(participants[1], participants[2]),
            listOf(participants[3])
        ),
        requests = listOf(
            Request(id = "req-1", icon = "hand", name = "Jamie Alvarez", username = "jamie")
        ),
        waitingList = listOf(
            WaitingRoomParticipant(name = "Taylor Kim", id = "waiting-1")
        )
    )
}

private fun buildSeedStreams(participants: List<Participant>): List<Stream> {
    return participants.mapIndexed { index, participant ->
        val fallbackId = "participant-${index + 1}"
        val baseId = participant.id?.takeIf { it.isNotBlank() }
            ?: participant.name.lowercase().replace(seedNameSanitizer, "-").trim('-').ifBlank { fallbackId }
        val audioId = participant.audioID.ifBlank { "audio-$baseId" }
        val videoId = participant.videoID.ifBlank { "video-$baseId" }

        Stream(
            id = videoId,
            producerId = videoId,
            muted = !participant.audioOn,
            name = participant.name,
            audioID = audioId,
            videoID = videoId
        )
    }
}

internal fun buildPlaceholderStreams(participants: List<Participant>): List<Stream> {
    if (participants.isEmpty()) return emptyList()

    return participants.mapIndexed { index, participant ->
        val fallbackId = "placeholder-${index + 1}"
        val baseId = participant.id?.takeIf { it.isNotBlank() }
            ?: participant.name.lowercase().replace(seedNameSanitizer, "-").trim('-').ifBlank { fallbackId }
        val audioId = participant.audioID.ifBlank { "audio-$baseId" }
        val videoId = participant.videoID.ifBlank { "video-$baseId" }

        // Use "youyou" as producerId so reorderStreams always preserves these placeholder streams
        // even when participants list is empty (matches React/Flutter behavior)
        Stream(
            id = videoId,
            producerId = "youyou",
            muted = !participant.audioOn,
            name = participant.name,
            audioID = audioId,
            videoID = videoId,
            extra = buildJsonObject {
                put("placeholder", JsonPrimitive(true))
                put("participantName", JsonPrimitive(participant.name))
            }
        )
    }
}

class MediasfuGenericState internal constructor(
    private val scope: CoroutineScope,
    internal val parameters: MediasfuParameters,
    internal val options: MediasfuGenericOptions,
) {
    private val _validated = MutableStateFlow(parameters.validated)
    val validated: StateFlow<Boolean> = _validated.asStateFlow()

    // Session counter - increments on each new meeting to force UI recomposition
    // This clears cached composables (remember blocks) when starting a new session
    private val _sessionCounter = MutableStateFlow(0)
    val sessionCounter: StateFlow<Int> = _sessionCounter.asStateFlow()
    
    internal fun incrementSessionCounter() {
        _sessionCounter.value++
    }

    // Initialize isLoading to false - only show loader when user initiates an action
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingMessage = MutableStateFlow<String?>(null)
    val loadingMessage: StateFlow<String?> = _loadingMessage.asStateFlow()

    private val _orientation = MutableStateFlow("landscape")
    val orientation: StateFlow<String> = _orientation.asStateFlow()

    val connectivity = ConnectivityState(parameters, ::notifyParametersChanged)
    val room = RoomState(parameters, ::notifyParametersChanged)
    val media = MediaState(parameters, ::notifyParametersChanged)
    val display = DisplayState(parameters, ::notifyParametersChanged)
    val streams = StreamsState(parameters, ::notifyParametersChanged)
    private val pageContentParameters = MediasfuPageContentParameters(parameters, this)
    internal val paginationParameters: PaginationParameters
        get() = pageContentParameters
    val recording = RecordingState(parameters, ::notifyParametersChanged)
    val meeting = MeetingState(scope, parameters, ::notifyParametersChanged)
    val messaging = MessagingState(parameters, ::notifyParametersChanged)
    val waitingRoom = WaitingRoomState(parameters, ::notifyParametersChanged)
    val requests = RequestsState(parameters, ::notifyParametersChanged)
    val polls = PollsState(parameters, ::notifyParametersChanged)
    val breakout = BreakoutState(parameters, ::notifyParametersChanged)
    val whiteboard = WhiteboardState(parameters, ::notifyParametersChanged)
    val modals = ModalState(parameters, ::notifyParametersChanged, scope)
    val alert = AlertState(parameters)
    private val coroutineScope: CoroutineScope
        get() = scope

    fun refreshFromParameters() {
        room.refresh()
        media.refresh()
        display.refresh()
        streams.refresh()
        recording.refresh()
        meeting.refresh()
        messaging.refresh()
        waitingRoom.refresh()
        requests.refresh()
        polls.refresh()
        breakout.refresh()
        whiteboard.refresh()
        modals.refresh()
        alert.refresh()
    }

    fun toggleMediaSettings() = modals.toggleMediaSettings()
    fun toggleDisplaySettings() = modals.toggleDisplaySettings()
    fun toggleCoHost() = modals.toggleCoHost()
    fun toggleBreakoutRooms() = modals.toggleBreakoutRooms()
    fun toggleScreenboard() = modals.setScreenboardVisibility(!modals.isScreenboardVisible)
    fun toggleWhiteboard() = modals.setWhiteboardVisibility(!modals.isWhiteboardVisible)
    fun toggleConfigureWhiteboard() = modals.setConfigureWhiteboardVisibility(!modals.isConfigureWhiteboardVisible)

    private fun resolveMemberName(): String = room.member.ifBlank { parameters.member }

    private fun hasWhiteboardDrawingAccess(): Boolean {
        if (room.youAreHost || room.islevel.equals("2", ignoreCase = true)) return true
        if (room.youAreCoHost) return true
        val memberName = resolveMemberName()
        if (memberName.isBlank()) return false
        return whiteboard.users.any { user ->
            user.name.equals(memberName, ignoreCase = true)
        }
    }

    private fun startWhiteboardSession() {
        if (!hasWhiteboardDrawingAccess()) {
            showAlert("You do not have permission to start the whiteboard.", "danger")
            return
        }

        if (whiteboard.users.isEmpty()) {
            modals.setWhiteboardVisibility(false)
            modals.showConfigureWhiteboard()
            showAlert("Add collaborators before starting the whiteboard.", "info")
            return
        }

        if (whiteboard.whiteboardStarted && !whiteboard.whiteboardEnded) {
            showAlert("Whiteboard is already active.", "info")
            return
        }

        whiteboard.updateStarted(true)
        whiteboard.updateEnded(false)
        modals.setWhiteboardVisibility(false)
        showAlert("Whiteboard started.", "success")
    }

    private fun stopWhiteboardSession() {
        if (!whiteboard.whiteboardStarted || whiteboard.whiteboardEnded) {
            showAlert("Whiteboard is not currently active.", "info")
            return
        }

        whiteboard.updateEnded(true)
        whiteboard.updateStarted(false)
        showAlert("Whiteboard ended.", "success")
    }

    private fun clearWhiteboardContent() {
        val hasContent = whiteboard.shapes.isNotEmpty() ||
            whiteboard.undoStack.isNotEmpty() ||
            whiteboard.redoStack.isNotEmpty()

        if (!hasContent) {
            showAlert("Whiteboard is already clear.", "info")
            return
        }

        whiteboard.updateShapes(emptyList())
        whiteboard.updateUndoStack(emptyList())
        whiteboard.updateRedoStack(emptyList())
        showAlert("Whiteboard cleared.", "success")
    }

    private fun ModelCoHostResponsibility.toUi(): UiCoHostResponsibility =
        UiCoHostResponsibility(name = name, value = value, dedicated = dedicated)

    private fun UiCoHostResponsibility.toModel(): ModelCoHostResponsibility =
        ModelCoHostResponsibility(name = name, value = value, dedicated = dedicated)

    private fun List<ModelCoHostResponsibility>.toUiList(): List<UiCoHostResponsibility> =
        map { it.toUi() }

    private fun List<UiCoHostResponsibility>.toModelList(): List<ModelCoHostResponsibility> =
        map { it.toModel() }

    private fun ModelBreakoutParticipant.toUi(): UiBreakoutParticipant =
        UiBreakoutParticipant(name = name, breakRoom = breakRoom)

    private fun UiBreakoutParticipant.toModel(): ModelBreakoutParticipant =
        ModelBreakoutParticipant(name = name, breakRoom = breakRoom)

    private fun List<List<ModelBreakoutParticipant>>.toUiRooms(): List<List<UiBreakoutParticipant>> =
        map { room -> room.map { it.toUi() } }

    private fun List<List<UiBreakoutParticipant>>.toModelRooms(): List<List<ModelBreakoutParticipant>> =
        map { room -> room.map { it.toModel() } }

    private fun resolveHostLabel(): String {
        val screenLead = streams.mainScreenPerson
        if (screenLead.isNotBlank()) return screenLead
        val fallback = parameters.hostLabel
        if (fallback.isNotBlank()) return fallback
        val memberName = room.member
        return memberName.ifBlank { "Host" }
    }

    /**
     * Handles pause/resume recording - matches Flutter's updateRecording
     * This is called when user clicks play/pause button in recording controls
     */
    suspend fun handleUpdateRecording() {
        try {
            modals.setRecordingVisibility(false)
            val options = com.mediasfu.sdk.methods.recording_methods.UpdateRecordingOptions(
                parameters = createUpdateRecordingParameters()
            )
            com.mediasfu.sdk.methods.recording_methods.updateRecording(options)
        } catch (e: Exception) {
            showAlert("Failed to update recording: ${e.message}", "danger", 3000)
        }
    }

    /**
     * Handles stop recording - matches Flutter's stopRecording
     * This is called when user clicks stop button in recording controls
     */
    suspend fun handleStopRecording() {
        try {
            modals.setRecordingVisibility(false)
            val options = com.mediasfu.sdk.methods.recording_methods.StopRecordingOptions(
                parameters = createStopRecordingParameters()
            )
            com.mediasfu.sdk.methods.recording_methods.stopRecording(options)
        } catch (e: Exception) {
            showAlert("Failed to stop recording: ${e.message}", "danger", 3000)
        }
    }

    private fun effectiveWhiteboardLimit(): Int {
        val configured = whiteboard.limit
        if (configured > 0) return configured
        val mediaLimit = media.itemPageLimit
        return if (mediaLimit > 0) mediaLimit else DEFAULT_WHITEBOARD_LIMIT
    }

    private var autoJoinJob: Job? = null
    private var socketPrefetchJob: Job? = null
    private var seedApplied = false
    private val socketEventNames = mutableSetOf<String>()
    private var registeredSocket: SocketManager? = null
    private var initialMediaHydrated = false
    private var localJoinPerformed = false  // Guards against duplicate joinRoom emits
    private val socketConnectMutex = Mutex()
    private var activeSocketSignature: SocketSignature? = null
    private val connectGuardMutex = Mutex()
    private var ongoingConnectJob: Job? = null
    private var ongoingJoinSignature: JoinRequestSignature? = null
    private var latestCredentialSnapshot: CredentialSnapshot? = null
    private var socketPrefetchSuspended: Boolean = false
    private var lastAutoJoinAttemptAt = 0L
    private val AUTO_JOIN_MIN_INTERVAL_MS = 5_000L

    init {
        // Initialize loading state in parameters to match local state.
        // If we are not restoring a validated session, always start with the loader hidden to
        // avoid leaking a previous run's loading state from shared parameters.
        val restoringValidatedSession = options.connectMediaSFU && parameters.validated
        parameters.isLoadingModalVisible = restoringValidatedSession
        modals.setLoadingVisibility(restoringValidatedSession)
        applyInitialOptions()
        parameters.onParticipantsUpdated = { updated -> handleParticipantsUpdated(updated) }
        parameters.onOtherGridStreamsUpdated = { _ ->
            scope.launch {
                propagateParameterChanges()
            }
        }
        // Wire up media state callbacks for Compose reactivity
        parameters.onAudioAlreadyOnChanged = { value ->
            media.syncAudioAlreadyOn(value)
        }
        parameters.onVideoAlreadyOnChanged = { value ->
            media.syncVideoAlreadyOn(value)
        }
        parameters.onScreenAlreadyOnChanged = { value ->
            media.syncScreenAlreadyOn(value)
        }
        parameters.onSharedChanged = { value ->
            media.syncShared(value)
        }
        parameters.onShareScreenStartedChanged = { value ->
            media.syncShareScreenStarted(value)
        }
        // Wire up recording state callback for Compose reactivity
        // Use scope.launch to ensure state changes happen on the Compose/main thread
        parameters.onRecordingStateChanged = {
            scope.launch {
                recording.refresh()
            }
        }
        // Wire up alert state callback for Compose reactivity
        // Use scope.launch to ensure state changes happen on the Compose/main thread
        parameters.onAlertStateChanged = { message, type, duration ->
            scope.launch {
                alert.show(message, type, duration)
            }
        }
        // Wire up mainGridStream callback for React parity - routes prepopulateUserMedia updates to UI
        parameters.onMainGridStreamUpdated = { components ->
            streams.updateMainGridStream(components)
        }
        // Wire up lStreams callback for Compose reactivity - routes DispStreams updates to UI
        parameters.onLStreamsUpdated = { newStreams ->
            streams.updateLStreams(newStreams)
        }
        // Wire up paginatedStreams callback for Compose reactivity - routes ChangeVids updates to UI
        parameters.onPaginatedStreamsUpdated = { newStreams ->
            streams.updatePaginatedStreams(newStreams)
        }
        parameters.onAudioOnlyStreamsUpdated = { newItems ->
            streams.updateAudioOnlyStreams(newItems)
        }
        observeOrientationChanges()
        observeAutoJoinTriggers()
        observeSocketConnections()
        observeCredentialChanges()
    }

    private fun observeOrientationChanges() {
        scope.launch {
            snapshotFlow { media.screenAlreadyOn to media.targetOrientation }
                .collectLatest { (screenOn, targetOrientation) ->
                    _orientation.value = if (screenOn) "landscape" else targetOrientation
                }
        }
    }

    internal fun handleOrientationFromLayout(orientation: String, availableHeightPx: Float) {
        val normalizedOrientation = orientation.lowercase()
        if (!media.targetOrientation.equals(normalizedOrientation, ignoreCase = true)) {
            media.updateTargetOrientation(normalizedOrientation)
        }

        val shouldShowControls = when (room.eventType) {
            EventType.CONFERENCE, EventType.WEBINAR -> true
            else -> false
        }

        if (!shouldShowControls) {
            display.updateControlHeight(0.0)
            return
        }

        if (availableHeightPx.isNaN() || availableHeightPx.isInfinite() || availableHeightPx <= 0f) {
            return
        }

        val controlHeightFraction = (40f / availableHeightPx).coerceIn(0f, 1f).toDouble()
        display.updateControlHeight(controlHeightFraction)
    }

    private fun observeAutoJoinTriggers() {
        scope.launch {
            combine(
                validated,
                snapshotFlow { connectivity.socket },
                snapshotFlow { connectivity.roomResponse },
            ) { isValidated, socket, response ->
                Triple(isValidated, socket, response)
            }.collectLatest { (isValidated, socket, response) ->
                if (!isValidated || socket == null) {
                    autoJoinJob?.cancel()
                    autoJoinJob = null
                    return@collectLatest
                }
                // Sync room state from parameters in case external code updated them
                // (e.g., launchMediaSfuCloudSession setting engineParameters.roomName)
                room.refresh()
                // If we are validated but the socket is disconnected or response is missing,
                // we should attempt to reconnect (auto-join).
                // However, we must NOT do this if the user is explicitly in a "loading" state
                // initiated by a manual action (like clicking Join).
                if (parameters.isLoadingModalVisible) {
                    return@collectLatest
                }
                if (response.success == true) {
                    autoJoinJob?.cancel()
                    autoJoinJob = null
                    return@collectLatest
                }
                attemptAutoJoin()
            }
        }
    }

    private fun observeSocketConnections() {
        scope.launch {
            snapshotFlow { connectivity.socket }.collectLatest { socket ->
                if (registeredSocket === socket) return@collectLatest

                clearSocketListeners(registeredSocket)
                registeredSocket = socket

                if (socket != null) {
                    setupSocketListeners(socket)
                }
            }
        }
    }

    private data class CredentialSnapshot(
        val apiUserName: String,
        val apiToken: String,
        val apiKey: String,
        val link: String,
        val memberName: String
    )

    private data class SocketSignature(
        val baseUrl: String,
        val apiUserName: String,
        val credentialFingerprint: String
    )

    private data class JoinRequestSignature(
        val roomName: String,
        val member: String,
        val islevel: String,
        val adminPasscode: String,
        val socketSignature: SocketSignature?
    )

    private fun buildSocketSignature(link: String, apiUserName: String, credential: String): SocketSignature? {
        if (apiUserName.isBlank() || credential.isBlank()) return null
        val baseUrl = resolveSocketBaseUrl(link.ifBlank { DEFAULT_SOCKET_BASE })
        return SocketSignature(baseUrl = baseUrl, apiUserName = apiUserName, credentialFingerprint = credential)
    }

    private fun deriveCurrentSocketSignature(): SocketSignature? {
        val user = resolveApiUserName()
        val credential = resolveApiToken().ifBlank { resolveApiKey() }
        val linkValue = effectiveLocalLink().ifBlank { DEFAULT_SOCKET_BASE }
        return buildSocketSignature(linkValue, user, credential)
    }

    private fun buildJoinRequestSignature(
        roomName: String,
        member: String,
        islevel: String,
        adminPasscode: String,
        link: String,
        apiUserName: String,
        credential: String
    ): JoinRequestSignature {
        val normalizedLink = link.ifBlank { DEFAULT_SOCKET_BASE }
        val socketSignature = buildSocketSignature(normalizedLink, apiUserName, credential)
        return JoinRequestSignature(
            roomName = roomName.trim(),
            member = member.trim(),
            islevel = islevel.trim(),
            adminPasscode = adminPasscode.trim(),
            socketSignature = socketSignature
        )
    }

    private fun clearActiveSocketSignature() {
        activeSocketSignature = null
    }

    private fun observeCredentialChanges() {
        scope.launch {
            snapshotFlow {
                CredentialSnapshot(
                    apiUserName = resolveApiUserName(),
                    apiToken = resolveApiToken(),
                    apiKey = resolveApiKey(),
                    link = effectiveLocalLink(),
                    memberName = room.member
                )
            }.collectLatest { snapshot ->
                latestCredentialSnapshot = snapshot
                launchPrefetchIfEligible(snapshot)
            }
        }
    }

    private fun launchPrefetchIfEligible(snapshot: CredentialSnapshot) {
        if (socketPrefetchSuspended) return
        if (_validated.value) return
        if (snapshot.link.isBlank()) return
        if (snapshot.apiUserName.isBlank()) return
        if (snapshot.apiToken.isBlank() && snapshot.apiKey.isBlank()) return
        if (hasUsableSocket()) return
        if (socketPrefetchJob?.isActive == true) return

        val memberName = snapshot.memberName.ifBlank { snapshot.apiUserName }
        socketPrefetchJob = scope.launch {
            try {
                ensureSocketsReady(
                    link = snapshot.link,
                    apiUserName = snapshot.apiUserName,
                    apiToken = snapshot.apiToken,
                    memberName = memberName,
                    apiKey = snapshot.apiKey,
                    silent = true
                )
            } finally {
                socketPrefetchJob = null
            }
        }
    }

    private fun suspendSocketPrefetching() {
        socketPrefetchSuspended = true
        socketPrefetchJob?.cancel()
        socketPrefetchJob = null
    }

    private fun resumeSocketPrefetching() {
        if (_validated.value) {
            socketPrefetchSuspended = true
            return
        }
        socketPrefetchSuspended = false
        val snapshot = latestCredentialSnapshot ?: return
        launchPrefetchIfEligible(snapshot)
    }

    private fun hasUsableSocket(): Boolean {
        if (isSocketConnected(connectivity.socket)) return true
        if (isSocketConnected(connectivity.localSocket)) return true
        return false
    }

    private fun isSocketConnected(socket: SocketManager?): Boolean {
        return runCatching { socket?.isConnected() == true }.getOrDefault(false)
    }

    private fun handleParticipantsUpdated(updated: List<Participant>) {
        room.refresh()

        if (!_validated.value || updated.isEmpty() || initialMediaHydrated) {
            return
        }

        initialMediaHydrated = true

        scope.launch {
            initializeMediaStateAfterValidation(
                defaultMemberName = room.member.ifBlank { resolveApiUserName() }
            )

            if (parameters.participants.isEmpty()) {
                initialMediaHydrated = false
            }
        }
    }

    private fun clearSocketListeners(socket: SocketManager?) {
        socketEventNames.forEach { event ->
            runCatching { socket?.off(event) }
        }
        socketEventNames.clear()
    }

    private fun setupSocketListeners(socket: SocketManager) {
        
        registerSocketListener(socket, "disconnect") {
            scope.launch {
                handleDisconnectEvent()
            }
        }

        registerSocketListener(socket, "allMembers") { payload ->
            scope.launch {
                handleAllMembersEvent(payload)
            }
        }

        registerSocketListener(socket, "allMembersRest") { payload ->
            scope.launch {
                handleAllMembersRestEvent(payload)
            }
        }

        registerSocketListener(socket, "userWaiting") { payload ->
            scope.launch {
                handleUserWaitingEvent(payload)
            }
        }

        registerSocketListener(socket, "personJoined") { payload ->
            scope.launch {
                handlePersonJoinedEvent(payload)
            }
        }

        registerSocketListener(socket, "allWaitingRoomMembers") { payload ->
            scope.launch {
                handleAllWaitingRoomMembersEvent(payload)
            }
        }

        registerSocketListener(socket, "participantRequested") { payload ->
            scope.launch {
                handleParticipantRequestedEvent(payload)
            }
        }

        registerSocketListener(socket, "meetingEnded") { payload ->
            scope.launch {
                handleMeetingEndedEvent(payload)
            }
        }

        registerSocketListener(socket, "ban") { payload ->
            scope.launch {
                handleBanEvent(payload)
            }
        }

        registerSocketListener(socket, "updatedCoHost") { payload ->
            scope.launch {
                handleUpdatedCoHostEvent(payload)
            }
        }

        registerSocketListener(socket, "screenProducerId") { payload ->
            scope.launch {
                handleScreenProducerIdEvent(payload)
            }
        }

        registerSocketListener(socket, "updateMediaSettings") { payload ->
            scope.launch {
                handleUpdateMediaSettingsEvent(payload)
            }
        }

        registerSocketListener(socket, "producer-media-paused") { payload ->
            scope.launch {
                handleProducerMediaPausedEvent(payload)
            }
        }

        registerSocketListener(socket, "producer-media-resumed") { payload ->
            scope.launch {
                handleProducerMediaResumedEvent(payload)
            }
        }

        registerSocketListener(socket, "producer-media-closed") { payload ->
            scope.launch {
                handleProducerMediaClosedEvent(payload)
            }
        }

        registerSocketListener(socket, "controlMediaHost") { payload ->
            scope.launch {
                handleControlMediaHostEvent(payload)
            }
        }

        registerSocketListener(socket, "disconnectUserSelf") { payload ->
            scope.launch {
                handleDisconnectUserSelfEvent(payload)
            }
        }

        registerSocketListener(socket, "receiveMessage") { payload ->
            scope.launch {
                handleReceiveMessageEvent(payload)
            }
        }

        registerSocketListener(socket, "meetingTimeRemaining") { payload ->
            scope.launch {
                handleMeetingTimeRemainingEvent(payload)
            }
        }

        registerSocketListener(socket, "meetingStillThere") { payload ->
            scope.launch {
                handleMeetingStillThereEvent(payload)
            }
        }

        registerSocketListener(socket, "updateConsumingDomains") { payload ->
            scope.launch {
                handleUpdateConsumingDomainsEvent(payload)
            }
        }

        registerSocketListener(socket, "hostRequestResponse") { payload ->
            scope.launch {
                handleHostRequestResponseEvent(payload)
            }
        }

        registerSocketListener(socket, "pollUpdated") { payload ->
            scope.launch {
                handlePollUpdatedEvent(payload)
            }
        }

        registerSocketListener(socket, "breakoutRoomUpdated") { payload ->
            scope.launch {
                handleBreakoutRoomUpdatedEvent(payload)
            }
        }

        // Recording-related events (typically on socketAlt in Flutter)
        registerSocketListener(socket, "roomRecordParams") { payload ->
            scope.launch {
                handleRoomRecordParamsEvent(payload)
            }
        }

        registerSocketListener(socket, "startRecords") { payload ->
            scope.launch {
                handleStartRecordsEvent(payload)
            }
        }

        registerSocketListener(socket, "reInitiateRecording") { payload ->
            scope.launch {
                handleReInitiateRecordingEvent(payload)
            }
        }

        registerSocketListener(socket, "RecordingNotice") { payload ->
            scope.launch {
                handleRecordingNoticeEvent(payload)
            }
        }

        registerSocketListener(socket, "timeLeftRecording") { payload ->
            scope.launch {
                handleTimeLeftRecordingEvent(payload)
            }
        }

        registerSocketListener(socket, "stoppedRecording") { payload ->
            scope.launch {
                handleStoppedRecordingEvent(payload)
            }
        }

        // Whiteboard-related events for real-time collaborative drawing
        registerSocketListener(socket, "whiteboardAction") { payload ->
            scope.launch {
                handleWhiteboardActionEvent(payload)
            }
        }

        registerSocketListener(socket, "whiteboardUpdated") { payload ->
            scope.launch {
                handleWhiteboardUpdatedEvent(payload)
            }
        }
    }

    private fun registerSocketListener(
        socket: SocketManager,
        event: String,
        handler: suspend (Map<String, Any?>) -> Unit
    ) {
        socketEventNames.add(event)
        socket.on(event) { data ->
            scope.launch { handler(data) }
        }
    }

    private suspend fun handleDisconnectEvent() {
        // CRITICAL: Call closeAndReset BEFORE disconnect - matches Flutter exactly
        // This resets all state variables like youAreHost, youAreCoHost, streams, etc.
        try {
            closeAndReset()
        } catch (_: Exception) { }

        val alertCallback = ShowAlert { message, type, duration ->
            showAlert(message, type, duration)
        }

        disconnect(
            DisconnectOptions(
                showAlert = alertCallback,
                redirectUrl = null,
                onWeb = false,
                updateValidated = ::updateValidated
            )
        )

        clearActiveSocketSignature()
        connectGuardMutex.withLock {
            ongoingConnectJob = null
            ongoingJoinSignature = null
        }

        parameters.reset()
        propagateParameterChanges()
    }

    private suspend fun handleAllMembersEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<AllMembersData>()
        
        if (data == null) {
            return
        }

        val options = AllMembersOptions(
            members = data.members,
            requests = data.requests,
            settings = data.settings,
            coHost = data.coHost ?: room.coHost,
            coHostRes = data.coHostResponsibilities,
            parameters = createAllMembersParameters(),
            consumeSockets = parameters.consumeSocketsState,
            apiUserName = resolveApiUserName(),
            apiKey = room.apiKey.takeIf { it.isNotBlank() },
            apiToken = room.apiToken
        )

        allMembers(options)
        propagateParameterChanges()
    }

    private suspend fun handleAllMembersRestEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<AllMembersRestData>()
        
        if (data == null) {
            return
        }

        val options = AllMembersRestOptions(
            members = data.members,
            settings = data.settings,
            coHost = data.coHost ?: room.coHost,
            coHostRes = data.coHostResponsibilities,
            parameters = createAllMembersRestParameters(),
            consumeSockets = parameters.consumeSocketsState,
            apiUserName = resolveApiUserName(),
            apiKey = room.apiKey.takeIf { it.isNotBlank() },
            apiToken = room.apiToken
        )

        allMembersRest(options)
    }

    private suspend fun handleUserWaitingEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<UserWaitingData>() ?: return
        val name = data.name ?: return

        val options = UserWaitingOptions(
            name = name,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            totalReqWait = requests.totalPending,
            updateTotalReqWait = requests::updateTotalPending
        )

        userWaiting(options)
    }

    private suspend fun handlePersonJoinedEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<PersonJoinedData>() ?: return
        val name = data.name ?: return

    val options = SocketPersonJoinedOptions(
            name = name,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            }
        )

        personJoined(options)
    }

    private suspend fun handleAllWaitingRoomMembersEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<AllWaitingRoomMembersData>()

        val participants = when {
            data == null -> null
            !data.waitingParticipants.isNullOrEmpty() -> data.waitingParticipants
            !data.waitingParticipantss.isNullOrEmpty() -> data.waitingParticipantss
            else -> null
        }

        val resolvedParticipants = participants ?: waitingRoom.waitingRoomList.toList()

        val options = AllWaitingRoomMembersOptions(
            waitingParticipants = resolvedParticipants,
            updateWaitingRoomList = waitingRoom::updateList,
            updateTotalReqWait = requests::updateTotalPending
        )

        allWaitingRoomMembers(options)
        propagateParameterChanges()
    }

    private suspend fun handleParticipantRequestedEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<ParticipantRequestedData>() ?: return
        val request = data.userRequest ?: return

        val currentRequests = requests.requests.toList()
        val waitingParticipants = waitingRoom.waitingRoomList.toList()

    val options = SocketParticipantRequestedOptions(
            userRequest = request,
            requestList = currentRequests,
            waitingRoomList = waitingParticipants,
            updateTotalReqWait = requests::updateTotalPending,
            updateRequestList = requests::updateList
        )

        participantRequested(options)
        propagateParameterChanges()
    }

    private suspend fun handleMeetingEndedEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<MeetingEndedData>()

        fun parseBoolean(raw: Any?): Boolean? = when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> raw.equals("true", ignoreCase = true) || raw == "1"
            else -> null
        }

        fun parseEventType(raw: Any?): EventType? = when (raw) {
            is EventType -> raw
            is String -> runCatching { EventType.valueOf(raw.trim().uppercase()) }.getOrNull()
            else -> null
        }

        val redirectUrl = data?.redirectUrl
            ?: payload["redirectURL"] as? String
            ?: payload["redirect_url"] as? String

        val onWeb = data?.onWeb
            ?: parseBoolean(payload["onWeb"])
            ?: parseBoolean(payload["onweb"])
            ?: false

        val eventType = data?.eventType
            ?: parseEventType(payload["eventType"])
            ?: parseEventType(payload["event_type"])
            ?: room.eventType

        // CRITICAL: Call closeAndReset BEFORE meetingEnded - matches Flutter exactly
        // This resets all state variables like youAreHost, youAreCoHost, streams, etc.
        try {
            closeAndReset()
        } catch (_: Exception) { }

    val options = SocketMeetingEndedOptions(
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            redirectUrl = redirectUrl,
            onWeb = onWeb,
            eventType = eventType,
            updateValidated = ::updateValidated
        )

        meeting.stopTimer(reset = true)
        meeting.hide()

        meetingEnded(options)
        propagateParameterChanges()
    }

    private suspend fun handleBanEvent(payload: Map<String, Any?>) {
        val name = payload["name"] as? String ?: return

        val options = BanParticipantOptions(
            name = name,
            parameters = parameters
        )

        banParticipant(options)
        propagateParameterChanges()
    }

    private suspend fun handleUpdatedCoHostEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<UpdatedCoHostData>()
        if (data == null) {
            return
        }

        val options = UpdatedCoHostOptions(
            coHost = data.coHost ?: room.coHost,
            coHostResponsibility = data.coHostResponsibilities,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            eventType = room.eventType,
            islevel = room.islevel,
            member = room.member,
            youAreCoHost = room.youAreCoHost,
            updateCoHost = room::updateCoHost,
            updateCoHostResponsibility = room::updateCoHostResponsibility,
            updateYouAreCoHost = { newValue -> 
                room.youAreCoHost = newValue
                parameters.youAreCoHost = newValue
                notifyParametersChanged()
            }
        )

        updatedCoHost(options)
        propagateParameterChanges()
    }

    private suspend fun handleScreenProducerIdEvent(payload: Map<String, Any?>) {
        val producerId = payload["producerId"] as? String ?: return

        val options = ScreenProducerIdOptions(
            producerId = producerId,
            screenId = parameters.screenId,
            membersReceived = parameters.membersReceived,
            shareScreenStarted = parameters.shareScreenStarted,
            deferScreenReceived = parameters.deferScreenReceived,
            participants = room.participants.toList(),
            updateScreenId = parameters::updateScreenId,
            updateShareScreenStarted = parameters::updateShareScreenStarted,
            updateDeferScreenReceived = parameters::updateDeferScreenReceived
        )

        screenProducerId(options)
        propagateParameterChanges()
    }

    private suspend fun handleUpdateMediaSettingsEvent(payload: Map<String, Any?>) {
        val settingsList = payload["settings"] as? List<*> ?: return
        val settings = settingsList.mapNotNull { it as? String }

        val options = UpdateMediaSettingsOptions(
            settings = settings,
            updateAudioSetting = room::updateAudioSetting,
            updateVideoSetting = room::updateVideoSetting,
            updateScreenshareSetting = room::updateScreenshareSetting,
            updateChatSetting = room::updateChatSetting
        )

        updateMediaSettings(options)
        propagateParameterChanges()
    }

    private suspend fun handleProducerMediaPausedEvent(payload: Map<String, Any?>) {
        val producerId = payload["producerId"] as? String ?: return
        val kind = payload["kind"] as? String ?: return
        val name = payload["name"] as? String ?: ""

        val options = ProducerMediaPausedOptions(
            producerId = producerId,
            kind = kind,
            name = name,
            parameters = parameters
        )

        producerMediaPaused(options)
        propagateParameterChanges()
    }

    private suspend fun handleProducerMediaResumedEvent(payload: Map<String, Any?>) {
        val kind = payload["kind"] as? String ?: return
        val name = payload["name"] as? String ?: ""

        val options = ProducerMediaResumedOptions(
            kind = kind,
            name = name,
            parameters = parameters
        )

        producerMediaResumed(options)
        propagateParameterChanges()
    }

    private suspend fun handleProducerMediaClosedEvent(payload: Map<String, Any?>) {
        val producerId = payload["producerId"] as? String ?: return
        val kind = payload["kind"] as? String ?: return

        val options = ProducerMediaClosedOptions(
            producerId = producerId,
            kind = kind,
            parameters = parameters
        )

        producerMediaClosed(options)
        propagateParameterChanges()
    }

    private suspend fun handleControlMediaHostEvent(payload: Map<String, Any?>) {
        val type = payload["type"] as? String ?: return

        val options = ControlMediaHostOptions(
            type = type,
            parameters = parameters
        )

        controlMediaHost(options)
        propagateParameterChanges()
    }

    private suspend fun handleDisconnectUserSelfEvent(payload: Map<String, Any?>) {
        val socket = connectivity.socket ?: return
        
        val options = DisconnectUserSelfOptions(
            socket = socket.toSocketLike(),
            member = room.member,
            roomName = room.roomName,
            localSocket = null
        )

        disconnectUserSelf(options)
    }

    private suspend fun handleReceiveMessageEvent(payload: Map<String, Any?>) {
        val messageMap = payload["message"] as? Map<String, Any?> ?: return
        val message = Message.fromMap(messageMap)

        val options = SocketReceiveMessageOptions(
            message = message,
            messages = messaging.messages.toList(),
            participantsAll = room.participants.toList(),
            member = room.member,
            eventType = room.eventType,
            isLevel = room.islevel,
            coHost = room.coHost,
            updateMessages = messaging::updateMessages,
            updateShowMessagesBadge = messaging::updateShowMessagesBadge
        )

        receiveMessage(options)
        propagateParameterChanges()
    }

    private suspend fun handleMeetingTimeRemainingEvent(payload: Map<String, Any?>) {
        val timeRemaining = payload["timeRemaining"] as? Int ?: return

        val options = SocketMeetingTimeRemainingOptions(
            timeRemainingMillis = timeRemaining,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            eventType = room.eventType
        )

        meetingTimeRemaining(options)
    }

    private suspend fun handleMeetingStillThereEvent(payload: Map<String, Any?>) {
        val options = SocketMeetingStillThereOptions(
            updateIsConfirmHereModalVisible = modals::updateIsConfirmHereModalVisible
        )

        meetingStillThere(options)
        propagateParameterChanges()
    }

    private suspend fun handleUpdateConsumingDomainsEvent(payload: Map<String, Any?>) {
        val data = payload.decodePayload<UpdateConsumingDomainsData>() ?: return

        val options = UpdateConsumingDomainsOptions(
            domains = data.domains,
            altDomains = data.altDomains,
            apiUserName = resolveApiUserName(),
            apiToken = room.apiToken,
            apiKey = room.apiKey.takeIf { it.isNotBlank() } ?: "",
            parameters = parameters
        )

        updateConsumingDomains(options)
        propagateParameterChanges()
    }

    private suspend fun handleHostRequestResponseEvent(payload: Map<String, Any?>) {
        val requestResponseMap = payload["requestResponse"] as? Map<String, Any?> ?: return
        val requestResponse = RequestResponse.fromMap(requestResponseMap)

        val options = HostRequestResponseOptions(
            requestResponse = requestResponse,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            requestList = requests.requests,
            updateRequestList = requests::updateList,
            updateMicAction = requests::updateMicAction,
            updateVideoAction = requests::updateVideoAction,
            updateScreenAction = requests::updateScreenAction,
            updateChatAction = requests::updateChatAction,
            updateAudioRequestState = requests::updateAudioRequestState,
            updateVideoRequestState = requests::updateVideoRequestState,
            updateScreenRequestState = requests::updateScreenRequestState,
            updateChatRequestState = requests::updateChatRequestState,
            updateAudioRequestTime = requests::updateAudioRequestTime,
            updateVideoRequestTime = requests::updateVideoRequestTime,
            updateScreenRequestTime = requests::updateScreenRequestTime,
            updateChatRequestTime = requests::updateChatRequestTime,
            updateRequestIntervalSeconds = parameters.updateRequestIntervalSeconds
        )

        hostRequestResponse(options)
        propagateParameterChanges()
    }

    private suspend fun handlePollUpdatedEvent(payload: Map<String, Any?>) {
        val data = PollUpdatedData.fromMap(payload)

        val options = PollUpdatedOptions(
            data = data,
            polls = polls.polls,
            poll = polls.poll,
            member = room.member,
            islevel = room.islevel,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            },
            updatePolls = polls::updatePolls,
            updatePoll = polls::updatePoll,
            updateIsPollModalVisible = modals::updateIsPollModalVisible
        )

        pollUpdated(options)
        propagateParameterChanges()
    }

    private suspend fun handleBreakoutRoomUpdatedEvent(payload: Map<String, Any?>) {
        val coreData = BreakoutRoomUpdatedData.fromMap(payload)
        
        // Convert to breakout_rooms_methods version
        val data = BreakoutData(
            forHost = coreData.forHost,
            newRoom = coreData.newRoom,
            members = coreData.members,
            breakoutRooms = coreData.breakoutRooms,
            status = coreData.status
        )

        val options = BreakoutRoomUpdatedOptions(
            data = data,
            parameters = parameters
        )

        breakoutRoomUpdated(options)
        propagateParameterChanges()
    }

    private suspend fun handleRoomRecordParamsEvent(payload: Map<String, Any?>) {
        val recordParams = RecordParameters.fromMap(payload)
        
        // Convert RecordParameters to RecordingParams
        val recordingParams = RecordingParams(
            recordingAudioPausesLimit = recordParams.recordingAudioPausesLimit,
            recordingAudioSupport = recordParams.recordingAudioSupport,
            recordingAudioPeopleLimit = recordParams.recordingAudioPeopleLimit,
            recordingAudioParticipantsTimeLimit = recordParams.recordingAudioParticipantsTimeLimit,
            recordingVideoPausesLimit = recordParams.recordingVideoPausesLimit,
            recordingVideoSupport = recordParams.recordingVideoSupport,
            recordingVideoPeopleLimit = recordParams.recordingVideoPeopleLimit,
            recordingVideoParticipantsTimeLimit = recordParams.recordingVideoParticipantsTimeLimit,
            recordingAllParticipantsSupport = recordParams.recordingAllParticipantsSupport,
            recordingVideoParticipantsSupport = recordParams.recordingVideoParticipantsSupport,
            recordingAllParticipantsFullRoomSupport = recordParams.recordingAllParticipantsFullRoomSupport,
            recordingVideoParticipantsFullRoomSupport = recordParams.recordingVideoParticipantsFullRoomSupport,
            recordingPreferredOrientation = recordParams.recordingPreferredOrientation,
            recordingSupportForOtherOrientation = recordParams.recordingSupportForOtherOrientation,
            recordingMultiFormatsSupport = recordParams.recordingMultiFormatsSupport,
            recordingHlsSupport = false,
            recordingAudioPausesCount = recordParams.recordingAudioPausesCount,
            recordingVideoPausesCount = recordParams.recordingVideoPausesCount
        )

        val options = RoomRecordParamsOptions(
            recordParams = recordingParams,
            parameters = parameters
        )

        roomRecordParams(options)
        propagateParameterChanges()
    }

    private suspend fun handleStartRecordsEvent(payload: Map<String, Any?>) {
        val socket = connectivity.socket ?: return

        val options = StartRecordsOptions(
            roomName = room.roomName,
            member = room.member,
            socket = socket.toSocketLike()
        )

        startRecords(options)
    }

    private suspend fun handleReInitiateRecordingEvent(payload: Map<String, Any?>) {
        val socket = connectivity.socket ?: return

        val options = ReInitiateRecordingOptions(
            roomName = room.roomName,
            member = room.member,
            socket = socket.toSocketLike(),
            adminRestrictSetting = room.adminRestrictSetting
        )

        reInitiateRecording(options)
    }

    private suspend fun handleRecordingNoticeEvent(payload: Map<String, Any?>) {
        val state = payload["state"] as? String ?: return
        val pauseCount = payload["pauseCount"] as? Int ?: 0
        val timeDone = payload["timeDone"] as? Int ?: 0

        val userRecordingParams = if (payload.containsKey("userRecordingParam") && 
            payload["userRecordingParam"] != null) {
            (payload["userRecordingParam"] as? Map<String, Any?>)?.let { map ->
                UserRecordingParams.fromMap(map)
            }
        } else {
            parameters.userRecordingParams
        }

        val options = RecordingNoticeOptions(
            state = state,
            userRecordingParams = userRecordingParams,
            pauseCount = pauseCount,
            timeDone = timeDone,
            parameters = parameters
        )

        recordingNotice(options)
        propagateParameterChanges()
    }

    private suspend fun handleTimeLeftRecordingEvent(payload: Map<String, Any?>) {
        val timeLeft = payload["timeLeft"] as? Int ?: return

        val options = TimeLeftRecordingOptions(
            timeLeft = timeLeft,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            }
        )

        timeLeftRecording(options)
    }

    private suspend fun handleStoppedRecordingEvent(payload: Map<String, Any?>) {
        val state = payload["state"] as? String ?: return
        val reason = payload["reason"] as? String ?: ""

        val options = StoppedRecordingOptions(
            state = state,
            reason = reason,
            showAlert = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            }
        )

        stoppedRecording(options)
        propagateParameterChanges()
    }

    /**
     * Handle incoming whiteboard action from socket.
     * Processes draw, shape, erase, clear, undo, redo, text actions.
     */
    private suspend fun handleWhiteboardActionEvent(payload: Map<String, Any?>) {
        val action = payload["action"] as? String ?: return
        @Suppress("UNCHECKED_CAST")
        val actionPayload = payload["payload"] as? Map<String, Any?>

        val options = WhiteboardActionOptions(
            action = action,
            payload = actionPayload,
            shapes = whiteboard.shapes.toList(),
            redoStack = whiteboard.redoStackLists.toList(),
            undoStack = whiteboard.undoStackLists.toList(),
            useImageBackground = whiteboard.useImageBackground,
            updateShapes = { whiteboard.updateShapes(it) },
            updateRedoStack = { 
                whiteboard.redoStackLists.clear()
                whiteboard.redoStackLists.addAll(it)
            },
            updateUndoStack = {
                whiteboard.undoStackLists.clear()
                whiteboard.undoStackLists.addAll(it)
            },
            updateUseImageBackground = { whiteboard.updateUseImageBackground(it) }
        )

        handleWhiteboardAction(options)
        propagateParameterChanges()
    }

    /**
     * Handle whiteboard state updated event from socket.
     * Updates whiteboard users, shapes, and status.
     */
    private suspend fun handleWhiteboardUpdatedEvent(payload: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val whiteboardUsers = payload["whiteboardUsers"] as? List<Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val whiteboardData = payload["whiteboardData"] as? Map<String, Any?>
        val status = payload["status"] as? String

        val options = WhiteboardUpdatedOptions(
            whiteboardUsers = whiteboardUsers,
            whiteboardData = whiteboardData,
            status = status,
            updateWhiteboardUsers = { whiteboard.updateUsers(it) },
            updateShapes = { whiteboard.updateShapes(it) },
            updateWhiteboardStarted = { whiteboard.updateStarted(it) },
            updateWhiteboardEnded = { whiteboard.updateEnded(it) },
            shapes = whiteboard.shapes.toList()
        )

        handleWhiteboardUpdated(options)
        propagateParameterChanges()
    }

    // Helper to convert SocketManager to SocketLike
    private fun SocketManager.toSocketLike(): SocketLike = object : SocketLike {
        override val isConnected: Boolean get() = this@toSocketLike.isConnected()
        override val id: String? get() = this@toSocketLike.id
        
        override fun emit(event: String, data: Map<String, Any?>) {
            runBlocking { this@toSocketLike.emit(event, data) }
        }
        
        override fun emitWithAck(event: String, data: Map<String, Any?>, ack: (Map<String, Any?>) -> Unit) {
            this@toSocketLike.emitWithAck(event, data) { response ->
                @Suppress("UNCHECKED_CAST")
                val mapped = (response as? Map<*, *>)?.let { it as Map<String, Any?> } ?: emptyMap()
                ack(mapped)
            }
        }
    }

    private inline fun <reified T> Any?.decodePayload(): T? {
        return when (this) {
            is T -> this
            is Map<*, *> -> runCatching {
                val element = this.toJsonElement()
                mediaSfuJson.decodeFromJsonElement<T>(element)
            }.getOrNull()
            is String -> runCatching { 
                mediaSfuJson.decodeFromString<T>(this) 
            }.getOrNull()
            is JsonElement -> runCatching { 
                mediaSfuJson.decodeFromJsonElement<T>(this) 
            }.getOrNull()
            else -> {
                null
            }
        }
    }

    private fun Map<*, *>.toJsonElement(): JsonObject = buildJsonObject {
        for ((key, value) in this@toJsonElement) {
            val name = key as? String ?: continue
            put(name, value.toJsonElement())
        }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is Map<*, *> -> this.toJsonElement()
        is Iterable<*> -> buildJsonArray {
            for (item in this@toJsonElement) {
                add(item.toJsonElement())
            }
        }
        is Array<*> -> buildJsonArray {
            for (item in this@toJsonElement) {
                add(item.toJsonElement())
            }
        }
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is String -> runCatching { mediaSfuJson.parseToJsonElement(this) }
            .getOrElse { JsonPrimitive(this) }
        else -> {
            // Handle org.json.JSONArray and org.json.JSONObject by converting to string and parsing
            val className = this::class.simpleName ?: ""
            if (className.contains("JSONArray") || className.contains("JSONObject")) {
                runCatching { mediaSfuJson.parseToJsonElement(this.toString()) }
                    .getOrElse { JsonPrimitive(toString()) }
            } else {
                JsonPrimitive(toString())
            }
        }
    }

    private fun applyInitialOptions() {
        options.credentials?.let { creds ->
            room.updateApiUserName(creds.apiUserName)
            room.updateApiKey(creds.apiKey)
            if (room.apiToken.isBlank()) {
                room.updateApiToken(creds.apiKey)
            }
        }
        room.updateLink(options.localLink)
        options.sourceParameters?.let { source ->
            val sharedSocket = source.socket
            if (sharedSocket != null) {
                connectivity.updateSocket(sharedSocket)
            }
            connectivity.updateLocalSocket(source.localSocket)
            connectivity.updateDevice(source.device)
            connectivity.updateRoomResponse(source.roomData)
            if (sharedSocket != null && isSocketConnected(sharedSocket)) {
                activeSocketSignature = deriveCurrentSocketSignature()
            }
        }
        if (options.sourceParameters == null && parameters.socket != null && connectivity.socket == null) {
            connectivity.updateSocket(parameters.socket)
        }
        if (isSocketConnected(connectivity.socket)) {
            activeSocketSignature = deriveCurrentSocketSignature()
        } else {
            clearActiveSocketSignature()
        }
        room.refresh()
        display.refresh()
        streams.refresh()
        recording.refresh()
        polls.refresh()
        requests.refresh()
        applySeedDataIfNeeded()
    }

    private fun applySeedDataIfNeeded() {
        if (seedApplied) return

        val hasParticipants = parameters.participants.isNotEmpty()
        val hasStreams = parameters.currentStreams.isNotEmpty()
        val explicitSeedRequest = options.useSeed == true
        val implicitSeedRequest = options.seedData != null && options.useSeed != false

        if (!explicitSeedRequest && !implicitSeedRequest) return
        if (!explicitSeedRequest && (hasParticipants || hasStreams)) return

        val seed = options.seedData ?: defaultSeedData()
        applySeedData(seed)
    }

    private fun applySeedData(seed: ModelSeedData) {
        seedApplied = true

        val fallback = defaultSeedData()
        val participants = seed.participants.ifEmpty { fallback.participants }
        val memberName = seed.member?.takeIf { it.isNotBlank() }
            ?: participants.firstOrNull()?.name
            ?: "Guest"
        val hostName = seed.host?.takeIf { it.isNotBlank() }
            ?: participants.firstOrNull { it.isHost }?.name
            ?: memberName
        val resolvedEventType = seed.eventType ?: fallback.eventType ?: EventType.CONFERENCE

        if (room.roomName.isBlank()) {
            room.updateRoomName("MediaSFU Demo")
        }
        room.updateMember(memberName)
        val resolvedLevel = participants.firstOrNull { it.name.equals(memberName, ignoreCase = true) }?.islevel
            ?.takeIf { it.isNotBlank() }
            ?: if (hostName.equals(memberName, ignoreCase = true)) "2" else "1"
        room.updateIslevel(resolvedLevel)

        parameters.youAreHost = hostName.equals(memberName, ignoreCase = true)
        parameters.youAreCoHost = false
        parameters.eventType = resolvedEventType
        parameters.meetingDisplayType = if (resolvedEventType == EventType.CHAT || resolvedEventType == EventType.BROADCAST) "all" else "media"

        room.updateParticipants(participants)

        val streamsSnapshot = buildSeedStreams(participants)
        val mutedStreams = streamsSnapshot.filter { it.muted == true }
        val breakoutRooms: List<List<ModelBreakoutParticipant>> = seed.breakoutRooms.map { roomParticipants ->
            roomParticipants.map { participant ->
                ModelBreakoutParticipant(name = participant.name, breakRoom = participant.breakRoom)
            }
        }

        parameters.streamNames = streamsSnapshot
        parameters.currentStreams = streamsSnapshot
    parameters.paginatedStreams = if (streamsSnapshot.isEmpty()) emptyList() else listOf(streamsSnapshot)
        parameters.nonAlVideoStreams = streamsSnapshot
        parameters.mixedAlVideoStreams = emptyList()
        parameters.nonAlVideoStreamsMuted = mutedStreams
        parameters.allAudioStreams = streamsSnapshot
        parameters.remoteScreenStreamState = emptyList()
        parameters.lStreams = streamsSnapshot
        parameters.lStreams_ = streamsSnapshot
        parameters.chatRefStreams = emptyList()
        parameters.mainScreenPerson = streamsSnapshot.firstOrNull()?.producerId ?: ""
        parameters.adminOnMainScreen = hostName.equals(streamsSnapshot.firstOrNull()?.name ?: "", ignoreCase = true)
        parameters.mainScreenFilled = streamsSnapshot.isNotEmpty()
        parameters.gotAllVids = streamsSnapshot.isNotEmpty()

        val activeNames = participants.map(Participant::name)
        parameters.activeNames = activeNames
        parameters.prevActiveNames = activeNames
        parameters.pActiveNames = activeNames
        parameters.dispActiveNames = activeNames
        parameters.pDispActiveNames = activeNames

        parameters.messages = seed.messages
        parameters.showMessagesBadge = seed.messages.isNotEmpty()

        messaging.messages.apply {
            clear()
            addAll(seed.messages)
        }
        messaging.showMessagesBadge = seed.messages.isNotEmpty()
        messaging.directMessageDetails = null

    waitingRoom.updateList(seed.waitingList)
    requests.updateList(seed.requests)
    requests.updateTotalPending(seed.requests.size + seed.waitingList.size)
        polls.updatePolls(seed.polls)
        val activePoll = seed.polls.firstOrNull { it.status?.equals("live", ignoreCase = true) == true }
        if (activePoll != null) {
            polls.updatePoll(activePoll)
        } else {
            polls.clearActivePoll()
        }

        breakout.updateRooms(breakoutRooms)
        meeting.startTimer()

        propagateParameterChanges()
        updateValidated(true)
    }

    private fun attemptAutoJoin() {
        // Only auto-join if we are already validated (i.e. recovering a session)
        if (!_validated.value) return
        // Skip if local join already performed (prevents duplicate joinRoom during noUI CE flow)
        if (localJoinPerformed) {
            return
        }
        if (connectivity.socket == null) return
        val roomName = room.roomName.takeUnless(String::isBlank) ?: return
        val adminPasscode = room.adminPasscode.takeUnless(String::isBlank) ?: return
        val apiUserName = resolveApiUserName().takeUnless(String::isBlank) ?: return
        val member = room.member.ifBlank { apiUserName }
        if (autoJoinJob?.isActive == true) return
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - lastAutoJoinAttemptAt < AUTO_JOIN_MIN_INTERVAL_MS) return
        lastAutoJoinAttemptAt = now
        autoJoinJob = scope.launch {
            connectAndValidate(
                roomName = roomName,
                member = member,
                adminPasscode = adminPasscode,
                islevel = room.islevel.ifBlank { "0" },
                apiUserName = apiUserName,
                apiToken = room.apiToken,
                showLoadingModal = !_validated.value
            ) {
                autoJoinJob = null
            }
        }
    }

    private fun resolveApiUserName(): String {
        return room.apiUserName.ifBlank {
            options.credentials?.apiUserName?.takeIf { it.isNotBlank() }
                ?: parameters.apiUserName
        }
    }

    private fun resolveApiToken(): String {
        return room.apiToken.ifBlank {
            room.apiKey.ifBlank { options.credentials?.apiKey ?: "" }
        }
    }

    private fun resolveApiKey(): String {
        return room.apiKey.ifBlank { options.credentials?.apiKey ?: "" }
    }

    private fun resolveCloudSecret(token: String, apiKey: String): String? {
        return sequenceOf(token, apiKey)
            .firstOrNull { secret -> secret.isNotBlank() && isValidCloudSecret(secret) }
    }

    private fun isValidCloudSecret(secret: String): Boolean {
        return secret.length == 64 && secret.all { it.isLetterOrDigit() }
    }

    private fun seedImmediateSelfStreamIfNeeded() {
        if (parameters.allVideoStreamsState.isNotEmpty() || parameters.youYouStream.isNotEmpty()) return

        val streamId = "youyou"
        val youStream = Stream(
            id = streamId,
            producerId = streamId,
            name = streamId,
            extra = buildJsonObject {
                put("placeholder", JsonPrimitive(true))
                put("source", JsonPrimitive("self-seed"))
            }
        )
        val seededStreams = listOf(youStream)
        val activeName = room.member.ifBlank { resolveApiUserName() }
            .ifBlank { parameters.member }
            .ifBlank { streamId }

        parameters.updateAllVideoStreams(seededStreams)
        parameters.updateOldAllStreams(seededStreams)
        parameters.updateNewLimitedStreams(seededStreams)
        parameters.updateNewLimitedStreamsIDs(listOf(streamId))
        parameters.updateYouYouStream(seededStreams)
        parameters.updateYouYouStreamIDs(listOf(streamId))

        streams.updateStreamNames(seededStreams)
        streams.updateCurrentStreams(seededStreams)
        streams.updatePaginatedStreams(listOf(seededStreams))
        streams.updateNonAlVideoStreams(seededStreams)
        streams.updateMixedAlVideoStreams(emptyList())
        streams.updateNonAlVideoStreamsMuted(emptyList())
        streams.updateAllAudioStreams(seededStreams)
        streams.updateRemoteScreenStreams(emptyList())
        streams.updateLStreams(seededStreams)
        streams.updateChatRefStreams(emptyList())
        streams.updateMainScreenPerson(streamId)
        streams.updateMainScreenFilled(true)
        streams.updateAdminOnMainScreen(false)
        streams.updateGotAllVids(false)
        streams.updateActiveNames(listOf(activeName))
        streams.updatePrevActiveNames(listOf(activeName))
        streams.updatePActiveNames(listOf(activeName))
        streams.updateDispActiveNames(listOf(activeName))
        streams.updatePDispActiveNames(listOf(activeName))
    }

    /**
     * Close and reset all state when meeting ends.
     * Matches Flutter's closeAndReset function:
     * - Closes all modals
     * - Turns off video/audio if on
     * - Disconnects sockets
     * - Resets state to initial values
     * - Resets timers
     */
    suspend fun closeAndReset() {
        try {
            // 1. Close all modals using the closeAll() method
            modals.closeAll()
        } catch (_: Exception) { }

        try {
            // 2. Turn off video if on - using toggleVideo which properly handles the toggle
            if (media.videoAlreadyOn) {
                toggleVideo()
            }
        } catch (_: Exception) { }

        try {
            // 3. Turn off audio if on
            if (media.audioAlreadyOn) {
                toggleAudio()
            }
        } catch (_: Exception) { }

        try {
            // 4. Disconnect all consume sockets
            // consumeSockets is List<Map<String, SocketManager>>
            parameters.consumeSockets.forEach { socketMap: Map<String, SocketManager> ->
                socketMap.values.forEach { socketManager: SocketManager ->
                    runCatching { socketManager.disconnect() }
                }
            }
            // Clear consume sockets list
            parameters.consumeSockets = emptyList()
        } catch (_: Exception) { }

        // 5. Reset all state to initial values (matching Flutter's updateStatesToInitialValues)
        resetToInitialValues()

        // 6. Reset timers
        meeting.stopTimer(reset = true)
        recording.refresh()

        // 7. Delay before updating validated (matching Flutter's 1000ms delay)
        kotlinx.coroutines.delay(1000)
        updateValidated(false)
    }

    /**
     * Reset all state values to their initial defaults.
     * Matches Flutter's updateStatesToInitialValues function from initial_values.dart.
     */
    private fun resetToInitialValues() {
        // Increment session counter FIRST to force UI recomposition and clear cached composables
        incrementSessionCounter()
        
        // ROOM STATE - participants, members, host info
        room.updateCoHost("No coHost")
        room.updateIslevel("0")
        room.updateParticipants(emptyList())
        room.updateFilteredParticipants(emptyList())

        // MEDIA STATE - camera, mic, screen share flags
        media.syncVideoAlreadyOn(false)
        media.syncAudioAlreadyOn(false)
        media.syncScreenAlreadyOn(false)
        media.syncShared(false)
        media.syncShareScreenStarted(false)

        // STREAMS STATE - ALL stream collections (CRITICAL for Flutter parity)
        streams.updateStreamNames(emptyList())
        streams.updateCurrentStreams(emptyList())
        streams.updatePaginatedStreams(emptyList())
        streams.updateLStreams(emptyList())
        streams.updateChatRefStreams(emptyList())
        streams.updateNonAlVideoStreams(emptyList())
        streams.updateMixedAlVideoStreams(emptyList())
        streams.updateNonAlVideoStreamsMuted(emptyList())
        streams.updateAllAudioStreams(emptyList())
        streams.updateRemoteScreenStreams(emptyList())
        streams.updateMainGridStream(emptyList())
        streams.updateAudioOnlyStreams(emptyList())
        streams.updateMainScreenPerson("")
        streams.updateMainScreenFilled(false)
        streams.updateAdminOnMainScreen(false)
        streams.updateGotAllVids(false)
        streams.updateShareEnded(false)
        
        // Active names (CRITICAL for grid display)
        streams.updateActiveNames(emptyList())
        streams.updatePrevActiveNames(emptyList())
        streams.updatePActiveNames(emptyList())
        streams.updateDispActiveNames(emptyList())
        streams.updatePDispActiveNames(emptyList())
        
        // Screen states (CRITICAL for main screen display)
        streams.updateScreenStates(listOf(ScreenState()))
        streams.updatePrevScreenStates(listOf(ScreenState()))
        
        // PARAMETERS - allVideoStreams, adminVidID, screenId, etc.
        parameters.updateAllVideoStreams(emptyList())
        parameters.updateOldAllStreams(emptyList())
        parameters.updateNewLimitedStreams(emptyList())
        parameters.updateNewLimitedStreamsIDs(emptyList())
        parameters.updateActiveSounds(emptyList())
        parameters.audStreamNames = emptyList()
        parameters.youYouStream = emptyList()
        parameters.youYouStreamIDs = emptyList()
        
        // Admin/Screen IDs (CRITICAL)
        parameters.adminVidID = ""
        parameters.screenId = ""
        parameters.screenShareIDStream = ""
        parameters.screenShareNameStream = ""
        parameters.adminIDStream = ""
        parameters.adminNameStream = ""

        // RECORDING STATE
        recording.updateRecordStarted(false)
        recording.updateRecordPaused(false)
        recording.updateRecordResumed(false)
        recording.updateRecordStopped(false)
        recording.updateRecordingProgressTime("00:00:00")

        // MESSAGING STATE
        messaging.updateMessages(emptyList())
        messaging.clearBadge()

        // WAITING ROOM STATE
        waitingRoom.updateList(emptyList())

        // REQUESTS STATE
        requests.updateList(emptyList())
        requests.updateTotalPending(0)

        // POLLS STATE
        polls.updatePolls(emptyList())
        polls.clearActivePoll()

        // BREAKOUT STATE
        breakout.updateRooms(emptyList())
        breakout.updateBreakOutRoomStarted(false)
        breakout.updateBreakOutRoomEnded(false)

        // WHITEBOARD STATE
        whiteboard.refresh()

        // DISPLAY STATE - Grid components (CRITICAL for FlexibleGrid)
        display.updateOtherGridStreams(emptyList())
        display.updateGridRows(0)
        display.updateGridCols(0)
        display.updateAltGridRows(0)
        display.updateAltGridCols(0)
        display.updateShowMiniView(false)
        display.updateDoPaginate(false)
        display.updateCurrentUserPage(0)
        display.updateNumberPages(0)
        display.refresh()

        // CONNECTIVITY STATE - roomResponse (CRITICAL for auto-join)
        connectivity.updateRoomResponse(ResponseJoinRoom())

        // CORE PARAMETERS - flags and counters
        parameters.youAreCoHost = false
        parameters.youAreHost = false
        parameters.islevel = "0"
        parameters.participants = emptyList()
        parameters.filteredParticipants = emptyList()
        parameters.membersReceived = false
        parameters.deferScreenReceived = false
        parameters.hostFirstSwitch = false
        parameters.firstAll = false
        parameters.firstRound = false
        parameters.updateMainWindow = false
        parameters.lockScreen = false
        parameters.otherGridStreams = emptyList()
        
        // Consumer/transport state
        parameters.consumeSockets = emptyList()
        parameters.consumerTransports = emptyList()
        parameters.consumingTransports = emptyList()
        parameters.transportCreated = false
        parameters.transportCreatedVideo = false
        parameters.transportCreatedAudio = false
        parameters.transportCreatedScreen = false
    }

    fun updateValidated(newValue: Boolean) {
        if (_validated.value == newValue) {
            return
        }
        _validated.value = newValue
        parameters.validated = newValue
        if (!newValue) {
            initialMediaHydrated = false
        } else {
            seedImmediateSelfStreamIfNeeded()
            if (parameters.participants.isNotEmpty() && !initialMediaHydrated) {
                scope.launch {
                    initializeMediaStateAfterValidation(
                        defaultMemberName = room.member.ifBlank { resolveApiUserName() }
                    )
                }
            }
        }
        if (newValue) {
            socketPrefetchSuspended = true
            socketPrefetchJob?.cancel()
            socketPrefetchJob = null
            // Like Flutter: call joinAndUpdate when validated becomes true
            // This emits 'joinRoom' to the server, which sets peers[socket.id]
            
            scope.launch {
                joinAndUpdate()
            }
        } else {
            socketPrefetchSuspended = false
            resumeSocketPrefetching()
        }
        notifyParametersChanged()
    }

    /**
     * Called when validated becomes true (like Flutter's joinAndUpdate).
     * This emits 'joinRoom' to the server for local rooms, which sets peers[socket.id]
     * required for WebRTC transport creation.
     */
    private suspend fun joinAndUpdate() {
        try {
            // Guard against duplicate joinRoom emits
            if (localJoinPerformed) {
                return
            }
            
            val localLink = effectiveLocalLink()
            val isLocalRoom = localLink.isNotBlank() && !localLink.contains("mediasfu.com", ignoreCase = true)
            
            if (!isLocalRoom) {
                // For MediaSFU.com rooms, the flow is different - handled elsewhere
                return
            }
            
            // For local CE rooms, we need to emit joinRoom to set up peers[socket.id]
            // createRoom only creates the room structure, joinRoom registers the peer
            
            val socket = connectivity.localSocket ?: connectivity.socket
            if (socket == null) {
                return
            }
            
            val roomName = room.roomName.ifBlank { parameters.roomName }
            val islevel = room.islevel.ifBlank { parameters.islevel }
            val member = room.member.ifBlank { parameters.member }
            val adminPasscode = room.adminPasscode.ifBlank { parameters.adminPasscode }
            val apiUserName = room.apiUserName.ifBlank { parameters.apiUserName }
            
            
            if (roomName.isBlank() || adminPasscode.isBlank()) {
                Logger.e("MediasfuGeneric", "MediaSFU - joinAndUpdate: Missing roomName or adminPasscode, skipping")
                return
            }
            
            
            // Mark that we're performing local join BEFORE the call to prevent race with attemptAutoJoin
            localJoinPerformed = true
            
            val (joinResponse, localResponse) = performLocalJoin(
                socket = socket,
                roomName = roomName,
                islevel = islevel,
                member = member,
                sec = adminPasscode,
                apiUserName = apiUserName
            )
            
            
            applyLocalJoinMetadata(localResponse, localLink)
            room.updateRoomData(joinResponse)
            connectivity.updateRoomResponse(joinResponse)
            
            updateRoomParametersClient(
                UpdateRoomParametersClientOptions(
                    parameters = createUpdateRoomParametersBridge()
                )
            )
            
            
        } catch (e: Exception) {
            Logger.e("MediasfuGeneric", "MediaSFU - joinAndUpdate: Error: ${e.message}")
            e.printStackTrace()
        }
    }

    fun showAlert(message: String, type: String = "info", duration: Int = 3000) {
        alert.show(message, type, duration)
        parameters.showAlert(message, type, duration)
        notifyParametersChanged()
    }

    fun hideAlert() {
        alert.hide()
        parameters.hideAlert()
        notifyParametersChanged()
    }

    fun updatePolls(newPolls: List<Poll>) {
        polls.updatePolls(newPolls)
    }

    fun updatePoll(poll: Poll) {
        polls.updatePoll(poll)
    }

    fun clearActivePoll() {
        polls.clearActivePoll()
    }

    fun setPollModalVisible(visible: Boolean) {
        polls.setPollModalVisibility(visible)
    }

    fun showLoader() {
        _isLoading.value = true
        parameters.isLoadingModalVisible = true
        modals.setLoadingVisibility(true)
        notifyParametersChanged()
    }

    fun hideLoader() {
        _isLoading.value = false
        parameters.isLoadingModalVisible = false
        modals.setLoadingVisibility(false)
        notifyParametersChanged()
    }

    fun openShareEvent() {
        modals.showShareEvent()
    }

    fun closeShareEvent() {
        modals.setShareEventVisibility(false)
    }

    fun openConfirmExit() {
        modals.showConfirmExit()
    }

    fun closeAllModals() {
        modals.closeAll()
    }

    fun openParticipants() {
        modals.showParticipants()
    }

    fun muteParticipant(participant: Participant) {
        val socket = connectivity.socket
        if (socket == null) {
            showAlert("Unable to mute participant. Connection not established.", "danger")
            return
        }

        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Missing room information. Please try again.", "danger")
            return
        }

        // Check if already muted or is host
        if (participant.muted == true) {
            return
        }
        if (participant.islevel == "2") {
            showAlert("Cannot mute the host.", "danger")
            return
        }

        scope.launch {
            runCatching {
                socket.emit("controlMedia", mapOf(
                    "participantId" to participant.id,
                    "participantName" to participant.name,
                    "type" to "all",
                    "roomName" to roomName
                ))
            }.onFailure { error ->
                showAlert("Unable to mute ${participant.name}. Please try again.", "danger")
            }
        }
    }

    fun removeParticipant(participant: Participant) {
        val socket = connectivity.socket
        if (socket == null) {
            showAlert("Unable to remove participant. Connection not established.", "danger")
            return
        }

        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Missing room information. Please try again.", "danger")
            return
        }

        val currentMember = room.member.ifBlank { parameters.member }
        scope.launch {
            val alertBridge = ShowAlert { message, type, duration ->
                showAlert(message, type, duration)
            }

            val options = RemoveParticipantsOptions(
                coHostResponsibility = room.coHostResponsibility,
                participant = participant,
                member = currentMember,
                islevel = room.islevel.ifBlank { parameters.islevel.ifBlank { "0" } },
                showAlert = alertBridge,
                coHost = room.coHost,
                participants = room.participants.toList(),
                socket = socket,
                roomName = roomName,
                updateParticipants = { updated ->
                    room.updateParticipants(updated)
                }
            )

            runCatching {
                removeParticipants(options)
            }.onFailure { error ->
                showAlert("Unable to remove ${participant.name}. Please try again.", "danger")
            }
        }
    }

    fun openMessages() {
        modals.showMessages()
        messaging.clearBadge()
    }

    fun openSettings() {
        modals.showSettings()
    }

    fun toggleMenu() {
        launchMenuModal(
            LaunchMenuModalOptions(
                updateIsMenuModalVisible = modals::setMenuVisibility,
                isMenuModalVisible = modals.isMenuVisible
            )
        )
    }

    fun togglePollModal(openExplicit: Boolean = false) {
        if (openExplicit) {
            setPollModalVisible(true)
            return
        }
        launchPoll(
            LaunchPollOptions(
                updateIsPollModalVisible = ::setPollModalVisible,
                isPollModalVisible = polls.isPollModalVisible
            )
        )
    }

    fun toggleRequestsModal() {
        launchRequests(
            LaunchRequestsOptions(
                updateIsRequestsModalVisible = { visible ->
                    if (visible) {
                        modals.showRequests()
                    } else {
                        modals.setRequestsVisibility(false)
                    }
                },
                isRequestsModalVisible = modals.isRequestsVisible
            )
        )
    }

    fun toggleWaitingModal() {
        launchWaiting(
            LaunchWaitingOptions(
                updateIsWaitingModalVisible = { visible ->
                    if (visible) {
                        modals.showWaiting()
                    } else {
                        modals.setWaitingVisibility(false)
                    }
                },
                isWaitingModalVisible = modals.isWaitingVisible
            )
        )
    }

    fun openRequests() {
        modals.showRequests()
    }

    fun closeRequests() {
        modals.setRequestsVisibility(false)
    }

    fun openWaitingRoom() {
        modals.showWaiting()
    }

    fun closeWaitingRoom() {
        modals.setWaitingVisibility(false)
    }

    fun closeConfirmExit() {
        modals.setConfirmExitVisibility(false)
    }

    fun closeConfirmHere() {
        modals.setConfirmHereVisibility(false)
    }

    fun confirmExitFromPrompt() {
        val isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true)
        closeConfirmExit()
        val successMessage = if (isHost) {
            "You ended the event for everyone."
        } else {
            "You have left the event."
        }
        exitSession(successMessage = successMessage)
    }

    fun acknowledgePresence() {
        closeConfirmHere()
    }

    fun handlePresenceTimeout() {
        closeConfirmHere()
        exitSession(successMessage = "You have been disconnected due to inactivity.")
    }

    fun exitSession(ban: Boolean = false, successMessage: String? = null) {
        closeAllModals()
        val roomName = room.roomName.ifBlank { parameters.roomName }
        val memberName = room.member.ifBlank { parameters.member }
        if (roomName.isBlank() || memberName.isBlank()) {
            showAlert("Join a room before exiting.", "warning")
            return
        }

        scope.launch {
            try {
                confirmExit(
                    ConfirmExitOptions(
                        socket = connectivity.socket,
                        localSocket = connectivity.localSocket,
                        member = memberName,
                        roomName = roomName,
                        ban = ban
                    )
                )
                successMessage?.let { message ->
                    showAlert(message, "info")
                }
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to exit the event.", "danger")
                return@launch
            }
            meeting.stopTimer(reset = true)
            updateValidated(false)
        }
    }

    fun createPoll(
        question: String,
        options: List<String>,
        type: String = "singleChoice",
        onComplete: (() -> Unit)? = null
    ) {
        val trimmedQuestion = question.trim()
        val trimmedOptions = options.map(String::trim).filter { it.isNotEmpty() }
        if (trimmedQuestion.isEmpty() || trimmedOptions.size < 2) {
            showAlert("Enter a question and at least two options", "warning")
            onComplete?.invoke()
            return
        }
        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Join a room before creating polls", "warning")
            onComplete?.invoke()
            return
        }
        val poll = Poll(
            question = trimmedQuestion,
            type = type,
            options = trimmedOptions,
            votes = List(trimmedOptions.size) { 0 }
        )
        scope.launch {
            try {
                handleCreatePoll(
                    HandleCreatePollOptions(
                        poll = poll,
                        socket = connectivity.socket,
                        roomName = roomName,
                        showAlert = ShowAlert { message, type, duration ->
                            showAlert(message, type, duration)
                        },
                        updateIsPollModalVisible = ::setPollModalVisible
                    )
                )
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to create poll", "danger")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun endPoll(pollId: String, onComplete: (() -> Unit)? = null) {
        if (pollId.isBlank()) {
            onComplete?.invoke()
            return
        }
        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Join a room before ending polls", "warning")
            onComplete?.invoke()
            return
        }
        scope.launch {
            try {
                handleEndPoll(
                    HandleEndPollOptions(
                        pollId = pollId,
                        socket = connectivity.socket,
                        showAlert = ShowAlert { message, type, duration ->
                            showAlert(message, type, duration)
                        },
                        roomName = roomName,
                        updateIsPollModalVisible = ::setPollModalVisible
                    )
                )
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to end poll", "danger")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun voteInPoll(
        pollId: String,
        optionIndex: Int,
        onComplete: (() -> Unit)? = null
    ) {
        if (pollId.isBlank()) {
            onComplete?.invoke()
            return
        }
        val memberName = room.member.ifBlank { parameters.member }
        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (memberName.isBlank() || roomName.isBlank()) {
            showAlert("Join a room before voting", "warning")
            onComplete?.invoke()
            return
        }
        scope.launch {
            try {
                handleVotePoll(
                    HandleVotePollOptions(
                        pollId = pollId,
                        optionIndex = optionIndex,
                        socket = connectivity.socket,
                        showAlert = ShowAlert { message, type, duration ->
                            showAlert(message, type, duration)
                        },
                        member = memberName,
                        roomName = roomName,
                        updateIsPollModalVisible = ::setPollModalVisible
                    )
                )
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to submit vote", "danger")
            } finally {
                onComplete?.invoke()
            }
        }
    }

    fun toggleAudio() {
        scope.launch {
            try {
                val options = ClickAudioOptions(parameters = createClickAudioParameters())
                clickAudio(options)
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to toggle audio.", "danger")
            }
        }
    }

    fun toggleVideo() {
        scope.launch {
            try {
                val options = ClickVideoOptions(parameters = createClickVideoParameters())
                clickVideo(options)
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to toggle video.", "danger")
            }
        }
    }

    fun toggleScreenShare() {
        // Don't allow multiple clicks while loading
        if (media.isScreenShareLoading) return
        
        media.isScreenShareLoading = true
        
        scope.launch {
            try {
                // Short delay to allow UI to render loading state
                kotlinx.coroutines.delay(50)
                val options = ClickScreenShareOptions(parameters = createClickScreenShareParameters())
                clickScreenShare(options)
            } catch (error: Throwable) {
                error.printStackTrace()
                showAlert(error.message ?: "Unable to toggle screen share.", "danger")
            } finally {
                media.isScreenShareLoading = false
            }
        }
    }

    private fun updateRequestsList(newList: List<Request>) {
        requests.updateList(newList)
        requests.updateTotalPending(newList.size + waitingRoom.waitingRoomList.size)
    }

    private fun updateWaitingList(newList: List<WaitingRoomParticipant>) {
        waitingRoom.updateList(newList)
        requests.updateTotalPending(newList.size + requests.requests.size)
    }

    fun handleRequestAction(request: Request, action: String) {
        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Join a room before responding to requests.", "warning")
            return
        }

        val currentRequests = requests.requests.toList()
        scope.launch {
            try {
                respondToRequests(
                    RespondToRequestsOptions(
                        socket = connectivity.socket,
                        request = request,
                        updateRequestList = ::updateRequestsList,
                        requestList = currentRequests,
                        action = action,
                        roomName = roomName
                    )
                )
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to update request status.", "danger")
            }
        }
    }

    fun handleWaitingParticipant(participant: WaitingRoomParticipant, allow: Boolean) {
        val roomName = room.roomName.ifBlank { parameters.roomName }
        if (roomName.isBlank()) {
            showAlert("Join a room before managing the waiting room.", "warning")
            return
        }

        val currentWaiting = waitingRoom.waitingRoomList.toList()
        scope.launch {
            try {
                respondToWaiting(
                    RespondToWaitingOptions(
                        participantId = participant.id,
                        participantName = participant.name,
                        updateWaitingList = ::updateWaitingList,
                        waitingList = currentWaiting,
                        type = allow,
                        roomName = roomName,
                        socket = connectivity.socket
                    )
                )
            } catch (error: Throwable) {
                showAlert(error.message ?: "Unable to update waiting room.", "danger")
            }
        }
    }

    internal fun createRequestsModalProps(): RequestsModalProps {
        return RequestsModalProps(
            isVisible = modals.isRequestsVisible,
            requests = requests.filteredRequests.toList(),
            filter = requests.filter,
            pendingCount = requests.counter,
            onFilterChange = ::onRequestFilterChange,
            onClose = ::closeRequests,
            onAccept = { request -> handleRequestAction(request, "accepted") },
            onReject = { request -> handleRequestAction(request, "rejected") }
        )
    }

    internal fun createWaitingModalProps(): WaitingModalProps {
        return WaitingModalProps(
            isVisible = modals.isWaitingVisible,
            participants = waitingRoom.filteredWaitingRoomList.toList(),
            filter = waitingRoom.filter,
            pendingCount = waitingRoom.counter,
            onFilterChange = ::onWaitingRoomFilterChange,
            onClose = ::closeWaitingRoom,
            onAllow = { participant -> handleWaitingParticipant(participant, true) },
            onDeny = { participant -> handleWaitingParticipant(participant, false) }
        )
    }

    internal fun createMenuModalProps(): MenuModalProps {
        val whiteboardActive = whiteboard.whiteboardStarted && !whiteboard.whiteboardEnded
        val whiteboardCollaboratorCount = whiteboard.users.count { it.useBoard }
        val canAccessWhiteboard = hasWhiteboardDrawingAccess() || whiteboardActive
        val canConfigureWhiteboard = room.youAreHost || room.islevel.equals("2", ignoreCase = true)

        return MenuModalProps(
            state = this,
            isVisible = modals.isMenuVisible,
            roomName = room.roomName,
            roomLink = room.link,
            adminPasscode = room.adminPasscode,
            totalRequests = requests.totalPending,
            waitingCount = waitingRoom.counter,
            islevel = room.islevel,
            coHost = room.coHost,
            member = room.member,
            eventType = room.eventType,
            coHostResponsibility = room.coHostResponsibility,
            onClose = { modals.setMenuVisibility(false) },
            onOpenRequests = {
                modals.setMenuVisibility(false)
                openRequests()
            },
            onOpenWaiting = {
                modals.setMenuVisibility(false)
                openWaitingRoom()
            },
            onOpenPolls = {
                modals.setMenuVisibility(false)
                togglePollModal(openExplicit = true)
            },
            onOpenShareEvent = {
                modals.setMenuVisibility(false)
                openShareEvent()
            },
            onOpenSettings = {
                modals.showSettings()
            },
            onOpenRecording = {
                modals.showRecording()
            },
            onOpenCoHost = {
                modals.showCoHost()
            },
            onOpenMediaSettings = {
                modals.showMediaSettings()
            },
            onOpenDisplaySettings = {
                modals.showDisplaySettings()
            },
            onOpenBreakoutRooms = {
                modals.toggleBreakoutRooms()
            },
            onOpenWhiteboard = {
                modals.setMenuVisibility(false)
                modals.showWhiteboard()
            },
            onOpenConfigureWhiteboard = {
                modals.setMenuVisibility(false)
                launchConfigureWhiteboard(
                    LaunchConfigureWhiteboardOptions(
                        updateIsConfigureWhiteboardModalVisible = { value ->
                            modals.setConfigureWhiteboardVisibility(value)
                        },
                        isConfigureWhiteboardModalVisible = modals.isConfigureWhiteboardVisible
                    )
                )
            },
            whiteboardActive = whiteboardActive,
            whiteboardCollaboratorCount = whiteboardCollaboratorCount,
            canAccessWhiteboard = canAccessWhiteboard,
            canConfigureWhiteboard = canConfigureWhiteboard
        )
    }

    internal fun createParticipantsModalProps(): ParticipantsModalProps {
        return ParticipantsModalProps(
            state = this,
            isVisible = modals.isParticipantsVisible,
            filter = room.participantsFilter,
            participants = room.participants.toList(),
            filteredParticipants = room.filteredParticipants.toList(),
            participantCount = room.participantsCounter,
            onFilterChange = ::onParticipantsFilterChange,
            onClose = ::closeAllModals
        )
    }

    internal fun createMessagesModalProps(): MessagesModalProps {
        return MessagesModalProps(
            state = this,
            isVisible = modals.isMessagesVisible,
            messages = messaging.messages.toList(),
            onClose = ::closeAllModals,
            // Message system fields
            eventType = room.eventType,
            member = room.member,
            islevel = room.islevel,
            coHost = room.coHost,
            coHostResponsibility = room.coHostResponsibility,
            startDirectMessage = messaging.startDirectMessage,
            directMessageDetails = messaging.directMessageDetails,
            chatSetting = media.chatSetting,
            roomName = room.roomName,
            socket = connectivity.socket,
            showAlert = ::showAlert,
            onSendMessage = { options ->
                scope.launch {
                    sendMessage(options)
                }
            }
        )
    }

    internal fun createShareEventModalProps(): ShareEventModalProps {
        val resolvedRoomName = room.roomName.ifBlank { parameters.roomName }
        val resolvedPasscode = room.adminPasscode.ifBlank { parameters.adminPasscode }
        val isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true)
        val visiblePasscode = resolvedPasscode.takeIf { isHost && it.isNotBlank() }
        val shareLink = effectiveLocalLink().ifBlank { parameters.link }

        return ShareEventModalProps(
            state = this,
            isVisible = modals.isShareEventVisible,
            roomName = resolvedRoomName,
            shareLink = shareLink,
            adminPasscode = visiblePasscode,
            isHost = isHost,
            eventType = room.eventType,
            shareButtonsEnabled = shareLink.isNotBlank(),
            onDismiss = ::closeShareEvent
        )
    }

    internal fun createWhiteboardModalOptions(): WhiteboardModalOptions {
        val isActive = whiteboard.whiteboardStarted && !whiteboard.whiteboardEnded
        val hasAccess = hasWhiteboardDrawingAccess()

        return WhiteboardModalOptions(
            isVisible = modals.isWhiteboardVisible,
            onClose = { modals.setWhiteboardVisibility(false) },
            onStart = { startWhiteboardSession() },
            onStop = { stopWhiteboardSession() },
            onClear = { clearWhiteboardContent() },
            isWhiteboardActive = isActive,
            hasDrawingAccess = hasAccess,
            socketManager = connectivity.socket
        )
    }

    /**
     * Creates WhiteboardOptions for rendering the Whiteboard canvas component.
     * The whiteboard canvas shows when whiteboardStarted && !whiteboardEnded.
     */
    internal fun createWhiteboardOptions(width: Float, height: Float): WhiteboardOptions {
        val showAspect = whiteboard.whiteboardStarted && !whiteboard.whiteboardEnded
        val currentSocket = connectivity.socket
        
        // Create emit function that uses connectivity.socket with ack to get server response
        val emitWhiteboardAction: ((String, Map<String, Any?>) -> Unit)? = currentSocket?.let { socket ->
            { action: String, payload: Map<String, Any?> ->
                val data = mapOf(
                    "action" to action,
                    "payload" to payload,
                    "roomName" to room.roomName
                )
                
                // Use emitWithAck to get server response - backend listens on "updateBoardAction"
                socket.emitWithAck("updateBoardAction", data) { /* response handled silently */ }
            }
        }
        
        val params = object : WhiteboardParameters {
            // Emit function for sending whiteboard actions to server
            override val emitWhiteboardAction: ((action: String, payload: Map<String, Any?>) -> Unit)? = emitWhiteboardAction
            override val showAlert: ((message: String, type: String, duration: Long) -> Unit)? = { message, type, duration ->
                showAlert(message, type, duration.toInt())
            }
            override val islevel: String get() = room.islevel
            override val roomName: String get() = room.roomName
            override val shapes: List<com.mediasfu.sdk.model.WhiteboardShape> get() = whiteboard.shapes.toList()
            override val useImageBackground: Boolean get() = whiteboard.useImageBackground
            override val redoStack: List<com.mediasfu.sdk.model.WhiteboardShape> get() = whiteboard.redoStack.toList()
            override val undoStack: List<String> get() = whiteboard.undoStack.toList()
            override val whiteboardStarted: Boolean get() = whiteboard.whiteboardStarted
            override val whiteboardEnded: Boolean get() = whiteboard.whiteboardEnded
            override val whiteboardUsers: List<com.mediasfu.sdk.model.WhiteboardUser> get() = whiteboard.users.toList()
            override val member: String get() = room.member
            override val shareScreenStarted: Boolean get() = media.shareScreenStarted
            override val targetResolution: String? get() = media.targetResolution
            override val targetResolutionHost: String? get() = media.targetResolutionHost

            override val updateShapes: (List<com.mediasfu.sdk.model.WhiteboardShape>) -> Unit = { whiteboard.updateShapes(it) }
            override val updateUseImageBackground: (Boolean) -> Unit = { whiteboard.updateUseImageBackground(it) }
            override val updateRedoStack: (List<com.mediasfu.sdk.model.WhiteboardShape>) -> Unit = { 
                whiteboard.redoStack.clear()
                whiteboard.redoStack.addAll(it)
            }
            override val updateUndoStack: (List<String>) -> Unit = {
                whiteboard.undoStack.clear()
                whiteboard.undoStack.addAll(it)
            }
            override val updateWhiteboardStarted: (Boolean) -> Unit = { whiteboard.updateStarted(it) }
            override val updateWhiteboardEnded: (Boolean) -> Unit = { whiteboard.updateEnded(it) }
            override val updateWhiteboardUsers: (List<com.mediasfu.sdk.model.WhiteboardUser>) -> Unit = { whiteboard.updateUsers(it) }
            override val updateScreenId: (String) -> Unit = { parameters.screenId = it }
            override val updateShareScreenStarted: (Boolean) -> Unit = { media.syncShareScreenStarted(it) }

            override fun getUpdatedAllParams(): WhiteboardParameters = this
        }

        return WhiteboardOptions(
            customWidth = width,
            customHeight = height,
            parameters = params,
            showAspect = showAspect
        )
    }

    internal fun createConfirmExitModalProps(): ConfirmExitModalProps {
        val isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true)
        val memberName = room.member.ifBlank { parameters.member }
        val roomName = room.roomName.ifBlank { parameters.roomName }
        val message = if (isHost) {
            "This will end the event for everyone. Are you sure you want to continue?"
        } else {
            "Are you sure you want to exit the event?"
        }
        val confirmLabel = if (isHost) "End Event" else "Exit"

        return ConfirmExitModalProps(
            state = this,
            isVisible = modals.isConfirmExitVisible,
            memberName = memberName,
            roomName = roomName,
            isHost = isHost,
            message = message,
            confirmLabel = confirmLabel,
            onConfirm = ::confirmExitFromPrompt,
            onDismiss = ::closeConfirmExit
        )
    }

    internal fun createConfirmHereModalProps(): ConfirmHereModalProps {
        return ConfirmHereModalProps(
            state = this,
            isVisible = modals.isConfirmHereVisible,
            message = "Are you still there?",
            countdownSeconds = CONFIRM_HERE_COUNTDOWN_SECONDS,
            onConfirm = ::acknowledgePresence,
            onTimeout = ::handlePresenceTimeout,
            onDismiss = ::closeConfirmHere
        )
    }

    internal fun createLoadingModalProps(): com.mediasfu.sdk.ui.components.display.LoadingModalOptions {
        // Show loader whenever an explicit loading state is active.
        // This needs to work on the PreJoin/Welcome screen too (e.g., when user taps Create/Join).
        val shouldShowLoader = _isLoading.value || modals.isLoadingVisible || parameters.isLoadingModalVisible
        return com.mediasfu.sdk.ui.components.display.LoadingModalOptions(
            isVisible = shouldShowLoader,
            backgroundColor = 0xE6000000.toInt(), // 90% opacity black
            indicatorColor = 0xFFFFFFFF.toInt(),
            displayColor = 0xFFFFFFFF.toInt(),
            message = "Setting things up, just a moment..."
        )
    }

    internal fun createRecordingModalParameters(): com.mediasfu.sdk.ui.components.recording.RecordingModalParameters {
        return com.mediasfu.sdk.ui.components.recording.RecordingModalParameters(
            recordPaused = recording.recordPaused,
            recordingVideoType = recording.recordingVideoType,
            recordingDisplayType = recording.recordingDisplayType,
            recordingBackgroundColor = recording.recordingBackgroundColor,
            recordingNameTagsColor = recording.recordingNameTagsColor,
            recordingOrientationVideo = recording.recordingOrientationVideo,
            recordingNameTags = recording.recordingNameTags,
            recordingAddText = recording.recordingAddText,
            recordingCustomText = recording.recordingCustomText,
            recordingCustomTextPosition = recording.recordingCustomTextPosition,
            recordingCustomTextColor = recording.recordingCustomTextColor,
            recordingMediaOptions = recording.recordingMediaOptions,
            recordingAudioOptions = recording.recordingAudioOptions,
            recordingVideoOptions = recording.recordingVideoOptions,
            recordingAddHLS = recording.recordingAddHLS,
            eventType = parameters.eventType,
            updateRecordingVideoType = recording::updateRecordingVideoType,
            updateRecordingDisplayType = recording::updateRecordingDisplayType,
            updateRecordingBackgroundColor = recording::updateRecordingBackgroundColor,
            updateRecordingNameTagsColor = recording::updateRecordingNameTagsColor,
            updateRecordingOrientationVideo = recording::updateRecordingOrientationVideo,
            updateRecordingNameTags = recording::updateRecordingNameTags,
            updateRecordingAddText = recording::updateRecordingAddText,
            updateRecordingCustomText = recording::updateRecordingCustomText,
            updateRecordingCustomTextPosition = recording::updateRecordingCustomTextPosition,
            updateRecordingCustomTextColor = recording::updateRecordingCustomTextColor,
            updateRecordingMediaOptions = recording::updateRecordingMediaOptions,
            updateRecordingAudioOptions = recording::updateRecordingAudioOptions,
            updateRecordingVideoOptions = recording::updateRecordingVideoOptions,
            updateRecordingAddHLS = recording::updateRecordingAddHLS
        )
    }

    internal fun createRecordingModalProps(): com.mediasfu.sdk.ui.components.recording.RecordingModalOptions {
        return com.mediasfu.sdk.ui.components.recording.RecordingModalOptions(
            isRecordingModalVisible = modals.isRecordingVisible,
            onClose = { toggleRecording() },
            confirmRecording = { _ ->
                coroutineScope.launch {
                    runCatching {
                        // Do NOT close modal on confirm - only on start/stop/pause/resume
                        confirmRecording(
                            MethodConfirmRecordingOptions(
                                parameters = createConfirmRecordingParameters()
                            )
                        )
                    }.onFailure { error ->
                        showAlert(
                            error.message ?: "Unable to confirm recording.",
                            "danger"
                        )
                    }
                }
            },
            startRecording = { _ ->
                coroutineScope.launch {
                    runCatching {
                        // Let startRecording handle its own modal close to avoid flicker
                        startRecording(
                            MethodStartRecordingOptions(
                                parameters = createStartRecordingParameters()
                            )
                        )
                    }.onFailure { error ->
                        showAlert(
                            error.message ?: "Unable to start recording.",
                            "danger"
                        )
                    }
                }
            },
            parameters = createRecordingModalParameters()
        )
    }

    private fun ModelMediaDeviceInfo.toUiDevice(): UiMediaDeviceInfo {
        return UiMediaDeviceInfo(
            deviceId = deviceId,
            label = label,
            kind = kind.toString()
        )
    }

    internal fun createMediaSettingsModalParameters(): MediaSettingsModalParameters {
        return object : MediaSettingsModalParameters {
            override val userDefaultVideoInputDevice: String
                get() = parameters.userDefaultVideoInputDevice
            override val userDefaultAudioInputDevice: String
                get() = parameters.userDefaultAudioInputDevice
            override val userDefaultAudioOutputDevice: String
                get() = parameters.userDefaultAudioOutputDevice
            override val videoInputs: List<UiMediaDeviceInfo>
                get() = parameters.videoInputs.map { it.toUiDevice() }
            override val audioInputs: List<UiMediaDeviceInfo>
                get() = parameters.audioInputs.map { it.toUiDevice() }
            override val audioOutputs: List<UiMediaDeviceInfo>
                get() = parameters.audioOutputs.map { it.toUiDevice() }
            override val isMediaSettingsModalVisible: Boolean
                get() = modals.isMediaSettingsVisible
            
            override fun updateIsMediaSettingsModalVisible(value: Boolean) {
                modals.updateIsMediaSettingsVisible(value)
            }
            
            override fun getUpdatedAllParams(): MediaSettingsModalParameters {
                return this
            }
        }
    }

    internal fun createMediaSettingsModalProps(): MediaSettingsModalOptions {
        return MediaSettingsModalOptions(
            isVisible = modals.isMediaSettingsVisible,
            onClose = { toggleMediaSettings() },
            switchCameraOnPress = { _: UiSwitchVideoAltOptions ->
                coroutineScope.launch {
                    switchVideoAlt(
                        com.mediasfu.sdk.methods.stream_methods.SwitchVideoAltOptions(
                            parameters = createSwitchVideoAltParameters()
                        )
                    )
                }
            },
            switchVideoOnPress = { uiOptions: UiSwitchVideoOptions ->
                coroutineScope.launch {
                    switchVideo(
                        com.mediasfu.sdk.methods.stream_methods.SwitchVideoOptions(
                            videoPreference = uiOptions.videoPreference,
                            parameters = createSwitchVideoParameters()
                        )
                    )
                }
            },
            switchAudioOnPress = { uiOptions: UiSwitchAudioOptions ->
                coroutineScope.launch {
                    switchAudio(
                        com.mediasfu.sdk.methods.stream_methods.SwitchAudioOptions(
                            audioPreference = uiOptions.audioPreference,
                            parameters = createSwitchAudioParameters()
                        )
                    )
                }
            },
            switchAudioOutputOnPress = { uiOptions: UiSwitchAudioOutputOptions ->
                coroutineScope.launch {
                    switchAudioOutput(uiOptions.audioOutputPreference)
                }
            },
            onVirtualBackgroundPress = {
                // Close media settings and open background modal
                toggleMediaSettings()
                modals.updateIsBackgroundModalVisible(true)
            },
            parameters = createMediaSettingsModalParameters()
        )
    }
    
    /**
     * Switches the audio output device (speaker, Bluetooth, headphones)
     */
    private suspend fun switchAudioOutput(audioOutputPreference: String) {
        
        val currentOutput = parameters.userDefaultAudioOutputDevice
        if (audioOutputPreference != currentOutput) {
            // Update previous device
            parameters.prevAudioOutputDevice = currentOutput
            // Update current device
            parameters.userDefaultAudioOutputDevice = audioOutputPreference
            
            // Perform the actual device switch via WebRTC device
            val device = parameters.device
            if (device != null) {
                device.setAudioOutputDevice(audioOutputPreference)
            }
        }
    }

    internal fun createDisplaySettingsModalParameters(): DisplaySettingsModalParameters {
        return object : DisplaySettingsModalParameters {
            override val meetingDisplayType: String
                get() = display.meetingDisplayType
            override val autoWave: Boolean
                get() = display.autoWave
            override val forceFullDisplay: Boolean
                get() = display.forceFullDisplay
            override val meetingVideoOptimized: Boolean
                get() = display.meetingVideoOptimized
            
            override fun updateMeetingDisplayType(value: String) {
                display.updateMeetingDisplayType(value)
            }
            
            override fun updateAutoWave(value: Boolean) {
                display.updateAutoWave(value)
            }
            
            override fun updateForceFullDisplay(value: Boolean) {
                display.updateForceFullDisplay(value)
            }
            
            override fun updateMeetingVideoOptimized(value: Boolean) {
                display.updateMeetingVideoOptimized(value)
            }
        }
    }

    internal fun createDisplaySettingsModalProps(): DisplaySettingsModalOptions {
        return DisplaySettingsModalOptions(
            isVisible = modals.isDisplaySettingsVisible,
            onClose = { toggleDisplaySettings() },
            onModifySettings = { _ ->
                // Don't close modal here - let the Apply button handle it
                // Inline the key logic from modifyDisplaySettings to properly handle firstAll reset
                // The parameters have already been updated in DisplaySettingsModal before calling this callback
                coroutineScope.launch {
                    try {
                        // Read the CURRENT parameter values (modal has already updated them)
                        val meetingDisplayType = parameters.meetingDisplayType
                        val forceFullDisplay = parameters.forceFullDisplay
                        val prevMeetingDisplayType = parameters.prevMeetingDisplayType
                        val prevForceFullDisplay = parameters.prevForceFullDisplay
                        
                        // Check if settings changed OR if we need to force refresh for "all" mode
                        val settingsChanged = prevMeetingDisplayType != meetingDisplayType || prevForceFullDisplay != forceFullDisplay
                        val needsRefresh = meetingDisplayType == "all" && !settingsChanged  // Force refresh if staying on "all"
                        
                        if (settingsChanged || needsRefresh) {
                            
                            // KEY FIX: Set firstAll=false when meetingDisplayType="all"
                            val newFirstAllValue = meetingDisplayType != "all"
                            parameters.updateFirstAll(newFirstAllValue)
                            
                            parameters.updateUpdateMainWindow(true)
                            // DON'T re-update meetingDisplayType or forceFullDisplay - modal already did it!
                            
                            val adapter = createAllMembersParameters()
                            consumerOnScreenChanges(
                                OnScreenChangesOptions(
                                    changed = true,
                                    parameters = adapter
                                )
                            )
                            
                            parameters.prevForceFullDisplay = forceFullDisplay
                            parameters.prevMeetingDisplayType = meetingDisplayType
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            },
            parameters = createDisplaySettingsModalParameters()
        )
    }

    /**
     * Creates parameters for the BackgroundModal
     */
    internal fun createBackgroundModalParameters(): com.mediasfu.sdk.ui.components.background.BackgroundModalParameters {
        return object : com.mediasfu.sdk.ui.components.background.BackgroundModalParameters {
            override val showAlert: ((message: String, type: String, duration: Long) -> Unit)?
                get() = { message, type, duration ->
                    this@MediasfuGenericState.showAlert(message, type, duration.toInt())
                }

            override val selectedBackground: com.mediasfu.sdk.model.VirtualBackground?
                get() = parameters.selectedBackground

            override val targetResolution: String
                get() = parameters.targetResolution
            
            override val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
                get() = parameters.localStreamVideo
            
            // Return the stored processor, which is set from the BackgroundModal preview
            override val backgroundProcessor: com.mediasfu.sdk.background.VirtualBackgroundProcessor?
                get() = connectivity.virtualBackgroundProcessor
            
            // Camera initialization support - allows preview when video is off
            override val videoAlreadyOn: Boolean
                get() = parameters.videoAlreadyOn
            
            override val device: com.mediasfu.sdk.webrtc.WebRtcDevice?
                get() = parameters.device
            
            override val vidCons: Map<String, Any?>?
                get() = parameters.vidCons.toMap()
            
            override val frameRate: Int
                get() = parameters.frameRate

            override val updateSelectedBackground: (com.mediasfu.sdk.model.VirtualBackground?) -> Unit
                get() = { bg -> parameters.updateSelectedBackground(bg) }

            override val updateIsBackgroundModalVisible: (Boolean) -> Unit
                get() = { value -> parameters.updateIsBackgroundModalVisible(value) }

            override val updateKeepBackground: (Boolean) -> Unit
                get() = { value -> parameters.updateKeepBackground(value) }

            override val updateBackgroundHasChanged: (Boolean) -> Unit
                get() = { value -> parameters.updateBackgroundHasChanged(value) }
            
            override val updateBackgroundProcessor: ((com.mediasfu.sdk.background.VirtualBackgroundProcessor?) -> Unit)?
                get() = { processor ->
                    // Store the processor created by VideoPreviewSection
                    connectivity.virtualBackgroundProcessor = processor
                }

            override val onBackgroundApply: (suspend (com.mediasfu.sdk.model.VirtualBackground) -> Unit)?
                get() = { background ->
                    // Apply the background to the video stream
                    // React/Flutter flow:
                    // 1. Clone localStreamVideo for processing (don't impact original)
                    // 2. For VB enabled: Process cloned stream, disconnect old producer, connect new producer with processed track
                    // 3. For VB disabled: Stop processing, disconnect old producer, connect new producer with original track
                    
                    // Store the selection
                    parameters.updateSelectedBackground(background)
                    val isBackgroundEnabled = background.type != com.mediasfu.sdk.model.BackgroundType.NONE
                    parameters.updateKeepBackground(isBackgroundEnabled)
                    parameters.updateBackgroundHasChanged(true)
                    
                    // Get the device, local video stream, and current producer
                    val webRtcDevice = parameters.device
                    val localVideoStream = parameters.localStreamVideo
                    val currentVideoProducer = parameters.videoProducer
                    val producerTransport = parameters.producerTransport
                    
                    if (isBackgroundEnabled) {
                        // === ENABLE VIRTUAL BACKGROUND ===
                        val processor = connectivity.virtualBackgroundProcessor
                        if (processor != null && webRtcDevice != null && localVideoStream != null) {
                            try {
                                // Start processing with the device to create output stream
                                val outputStream = processor.startProcessingWithDevice(
                                    inputStream = localVideoStream,
                                    background = background,
                                    device = webRtcDevice,
                                    onProcessedFrame = null // No preview callback needed for production
                                )
                                
                                if (outputStream != null) {
                                    val videoTracks = outputStream.getVideoTracks()
                                    if (videoTracks.isNotEmpty()) {
                                        val newTrack = videoTracks.first()
                                        
                                        // Set the virtual stream in parameters
                                        parameters.virtualStream = outputStream
                                        parameters.processedStream = outputStream
                                        
                                        // CRITICAL: Following React pattern - disconnect old producer, then connect new one
                                        if (currentVideoProducer != null) {
                                            try {
                                                // Close the old producer - this notifies server
                                                currentVideoProducer.close()
                                                parameters.updateVideoProducer(null)
                                            } catch (e: Exception) {
                                            }
                                            
                                            // Wait for server to process (like React's sleep(500))
                                            kotlinx.coroutines.delay(500)
                                        }
                                        
                                        // Create new producer with the processed track
                                        if (producerTransport != null) {
                                            try {
                                                val newProducer = producerTransport.produce(
                                                    track = newTrack,
                                                    encodings = emptyList(),
                                                    codecOptions = null,
                                                    appData = null
                                                )
                                                parameters.updateVideoProducer(newProducer)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }
                                        
                                        // Trigger grid refresh
                                        try {
                                            val prepopulateOptions = PrepopulateUserMediaOptions(
                                                name = parameters.member,
                                                parameters = parameters
                                            )
                                            parameters.prepopulateUserMedia.invoke(prepopulateOptions)
                                        } catch (e: Exception) {
                                        }
                                        
                                        try {
                                            val reorderOptions = ReorderStreamsOptions(
                                                add = false,
                                                screenChanged = true,
                                                parameters = EngineReorderStreamsParameters(parameters)
                                            )
                                            parameters.reorderStreams.invoke(reorderOptions)
                                        } catch (e: Exception) {
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        // === DISABLE VIRTUAL BACKGROUND (NONE selected) ===
                        
                        val processor = connectivity.virtualBackgroundProcessor
                        if (processor != null) {
                            try {
                                processor.stopProcessing()
                            } catch (e: Exception) {
                            }
                        }
                        
                        // Clear virtual stream
                        parameters.keepBackground = false
                        parameters.virtualStream = null
                        parameters.processedStream = null
                        
                        // Get original track from localStreamVideo
                        if (localVideoStream != null) {
                            val videoTracks = localVideoStream.getVideoTracks()
                            if (videoTracks.isNotEmpty()) {
                                val originalTrack = videoTracks.first()
                                
                                // CRITICAL: Following React pattern - disconnect old producer, then connect new one
                                if (currentVideoProducer != null) {
                                    try {
                                        currentVideoProducer.close()
                                        parameters.updateVideoProducer(null)
                                    } catch (e: Exception) {
                                    }
                                    
                                    // Wait for server to process
                                    kotlinx.coroutines.delay(500)
                                }
                                
                                // Create new producer with the original track
                                if (producerTransport != null) {
                                    try {
                                        val newProducer = producerTransport.produce(
                                            track = originalTrack,
                                            encodings = emptyList(),
                                            codecOptions = null,
                                            appData = null
                                        )
                                        parameters.updateVideoProducer(newProducer)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }
                        
                        // Trigger grid refresh
                        try {
                            val prepopulateOptions = PrepopulateUserMediaOptions(
                                name = parameters.member,
                                parameters = parameters
                            )
                            parameters.prepopulateUserMedia.invoke(prepopulateOptions)
                        } catch (e: Exception) {
                        }
                        
                        try {
                            val reorderOptions = ReorderStreamsOptions(
                                add = false,
                                screenChanged = true,
                                parameters = EngineReorderStreamsParameters(parameters)
                            )
                            parameters.reorderStreams.invoke(reorderOptions)
                        } catch (e: Exception) {
                        }
                    }
                }

            override val onBackgroundPreview: (suspend (com.mediasfu.sdk.model.VirtualBackground) -> Unit)?
                get() = { background ->
                    // Preview the background on the video stream
                }

            override fun getUpdatedAllParams(): com.mediasfu.sdk.ui.components.background.BackgroundModalParameters {
                return this
            }
        }
    }

    /**
     * Creates props for the BackgroundModal
     */
    internal fun createBackgroundModalProps(): com.mediasfu.sdk.ui.components.background.BackgroundModalOptions {
        return com.mediasfu.sdk.ui.components.background.BackgroundModalOptions(
            isVisible = modals.isBackgroundVisible,
            onClose = { modals.updateIsBackgroundModalVisible(false) },
            parameters = createBackgroundModalParameters(),
            allowCustomUpload = true,
            showColorPicker = true,
            showPreview = true
        )
    }

    internal fun createConfigureWhiteboardModalParameters(): ConfigureWhiteboardModalParameters {
        val alertCallback: ((String, String, Long) -> Unit)? = { message, type, duration ->
            this@MediasfuGenericState.showAlert(message, type, duration.toInt())
        }

        return object : ConfigureWhiteboardModalParameters {
            override val participants: List<Participant>
                get() = room.participants.toList()
            override val showAlert: ((String, String, Long) -> Unit)?
                get() = alertCallback
            override val socket: SocketManager?
                get() = connectivity.socket
            override val itemPageLimit: Int
                get() = effectiveWhiteboardLimit()
            override val islevel: String
                get() = room.islevel
            override val roomName: String
                get() = room.roomName.ifBlank { parameters.roomName }
            override val eventType: String
                get() = room.eventType.name.lowercase()
            override val shareScreenStarted: Boolean
                get() = media.shareScreenStarted
            override val shared: Boolean
                get() = media.shared
            override val breakOutRoomStarted: Boolean
                get() = breakout.breakOutRoomStarted
            override val breakOutRoomEnded: Boolean
                get() = breakout.breakOutRoomEnded
            override val recordStarted: Boolean
                get() = recording.recordStarted
            override val recordResumed: Boolean
                get() = recording.recordResumed
            override val recordPaused: Boolean
                get() = recording.recordPaused
            override val recordStopped: Boolean
                get() = recording.recordStopped
            override val recordingMediaOptions: String
                get() = recording.recordingMediaOptions
            override val canStartWhiteboard: Boolean
                get() = whiteboard.canStartWhiteboard
            override val whiteboardStarted: Boolean
                get() = whiteboard.whiteboardStarted
            override val whiteboardEnded: Boolean
                get() = whiteboard.whiteboardEnded
            override val hostLabel: String
                get() = resolveHostLabel()

            override val updateWhiteboardStarted: (Boolean) -> Unit
                get() = { value -> whiteboard.updateStarted(value) }
            override val updateWhiteboardEnded: (Boolean) -> Unit
                get() = { value -> whiteboard.updateEnded(value) }
            override val updateWhiteboardUsers: (List<WhiteboardUser>) -> Unit
                get() = { users -> whiteboard.updateUsers(users) }
            override val updateCanStartWhiteboard: (Boolean) -> Unit
                get() = { value -> whiteboard.updateCanStart(value) }
            override val updateIsConfigureWhiteboardModalVisible: (Boolean) -> Unit
                get() = { visible -> modals.setConfigureWhiteboardVisibility(visible) }
            
            override val captureCanvasStream: (suspend () -> Unit)?
                get() = {
                    // StartRecordingParameters extends CaptureCanvasStreamParameters
                    val captureParams = createStartRecordingParameters()
                    val options = CaptureCanvasStreamOptions(
                        parameters = captureParams,
                        start = true
                    )
                    com.mediasfu.sdk.methods.whiteboard_methods.captureCanvasStream(options)
                }

            override fun getUpdatedAllParams(): ConfigureWhiteboardModalParameters = this
        }
    }

    internal fun createConfigureWhiteboardModalOptions(): ConfigureWhiteboardModalOptions {
        val limit = effectiveWhiteboardLimit()
        if (whiteboard.limit != limit) {
            whiteboard.updateLimit(limit)
        }

        return ConfigureWhiteboardModalOptions(
            isVisible = modals.isConfigureWhiteboardVisible,
            onClose = { modals.setConfigureWhiteboardVisibility(false) },
            parameters = createConfigureWhiteboardModalParameters()
        )
    }

    internal fun createCoHostModalProps(): CoHostModalOptions {
        val currentRoomName = room.roomName.ifBlank { parameters.roomName }
        val activeSocket = connectivity.socket
        return CoHostModalOptions(
            isCoHostModalVisible = modals.isCoHostVisible,
            onCoHostClose = { toggleCoHost() },
            onModifyCoHostSettings = { uiOptions ->
                
                // Call the actual modifyCoHostSettings function
                coroutineScope.launch {
                    
                    // Convert UI options to method options
                    val methodOptions = com.mediasfu.sdk.methods.co_host_methods.ModifyCoHostSettingsOptions(
                        roomName = uiOptions.roomName,
                        showAlert = uiOptions.showAlert,
                        selectedParticipant = uiOptions.selectedParticipant,
                        coHost = uiOptions.coHost,
                        coHostResponsibility = uiOptions.coHostResponsibility.toModelList(), // Convert UI to Model
                        updateIsCoHostModalVisible = uiOptions.updateIsCoHostModalVisible,
                        updateCoHostResponsibility = { modelList -> 
                            uiOptions.updateCoHostResponsibility(modelList.toUiList()) // Convert Model to UI
                        },
                        updateCoHost = uiOptions.updateCoHost,
                        socket = uiOptions.socket
                    )
                    
                    modifyCoHostSettings(methodOptions)
                }
            },
            currentCohost = room.coHost,
            participants = room.filteredParticipants.toList(),
            coHostResponsibility = room.coHostResponsibility.toUiList(),
            roomName = currentRoomName,
            showAlert = parameters.showAlert,
            updateCoHostResponsibility = { responsibilities ->
                room.updateCoHostResponsibility(responsibilities.toModelList())
            },
            updateCoHost = room::updateCoHost,
            updateIsCoHostModalVisible = modals::updateIsCoHostVisible,
            socket = activeSocket
        )
    }
    
    internal fun createBreakoutRoomsModalParameters(): BreakoutRoomsModalParameters {
        return object : BreakoutRoomsModalParameters() {
            override val participants: List<Participant>
                get() = room.filteredParticipants.toList()
            override val showAlert: ShowAlert?
                get() = parameters.showAlert
            override val socket: SocketManager?
                get() = connectivity.socket
            override val localSocket: SocketManager?
                get() = connectivity.localSocket
            override val itemPageLimit: Int
                get() = parameters.itemPageLimit
            override val meetingDisplayType: String
                get() = display.meetingDisplayType
            override val prevMeetingDisplayType: String
                get() = display.prevMeetingDisplayType
            override val roomName: String
                get() = room.roomName.ifBlank { parameters.roomName }
            override val shareScreenStarted: Boolean
                get() = media.shareScreenStarted
            override val shared: Boolean
                get() = media.shared
            override val breakOutRoomStarted: Boolean
                get() = breakout.breakOutRoomStarted
            override val breakOutRoomEnded: Boolean
                get() = breakout.breakOutRoomEnded
            override val canStartBreakout: Boolean
                get() = breakout.canStartBreakout
            override val newParticipantAction: String
                get() = breakout.newParticipantAction
            override val breakoutRooms: List<List<UiBreakoutParticipant>>
                get() = breakout.breakoutRooms.toUiRooms()
            
            override val updateBreakOutRoomStarted: (Boolean) -> Unit
                get() = breakout::updateBreakOutRoomStarted
            override val updateBreakOutRoomEnded: (Boolean) -> Unit
                get() = breakout::updateBreakOutRoomEnded
            override val updateCurrentRoomIndex: (Int) -> Unit
                get() = breakout::updateCurrentRoomIndex
            override val updateCanStartBreakout: (Boolean) -> Unit
                get() = breakout::updateCanStartBreakout
            override val updateNewParticipantAction: (String) -> Unit
                get() = breakout::updateNewParticipantAction
            override val updateBreakoutRooms: (List<List<UiBreakoutParticipant>>) -> Unit
                get() = { rooms -> breakout.updateBreakoutRooms(rooms.toModelRooms()) }
            override val updateMeetingDisplayType: (String) -> Unit
                get() = display::updateMeetingDisplayType
            
            override fun getUpdatedAllParams(): BreakoutRoomsModalParameters {
                return this
            }
        }
    }

    internal fun createBreakoutRoomsModalProps(): BreakoutRoomsModalOptions {
        return BreakoutRoomsModalOptions(
            isVisible = modals.isBreakoutRoomsVisible,
            onBreakoutRoomsClose = { toggleBreakoutRooms() },
            parameters = createBreakoutRoomsModalParameters()
        )
    }

    fun totalPages(): Int {
        val configured = if (display.numberPages >= 0) display.numberPages + 1 else 0
        val available = parameters.paginatedStreams.size
        val candidate = maxOf(configured, available)
        return if (candidate <= 0) 1 else candidate
    }

    fun hasPreviousPage(): Boolean = display.currentUserPage > 0

    fun hasNextPage(): Boolean = display.currentUserPage < totalPages() - 1

    fun onPageChange(targetPage: Int) {
        val total = totalPages()
        if (total <= 0) return
        val sanitized = targetPage.coerceIn(0, total - 1)
        if (sanitized == display.currentUserPage) return
        launchInScope {
            val available = parameters.paginatedStreams.size
            val targetIndex = if (available == 0) sanitized else sanitized.coerceAtMost(available - 1)
            if (available == 0) {
                display.updateCurrentUserPage(targetIndex)
                propagateParameterChanges()
            } else {
                try {
                    generatePageContent(
                        GeneratePageContentOptions(
                            page = targetIndex,
                            parameters = pageContentParameters
                        )
                    )
                    propagateParameterChanges()
                } catch (error: Throwable) {
                    showAlert(error.message ?: "Unable to change page.", "danger")
                }
            }
        }
    }

    internal fun launchInScope(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(block = block)
    }

    fun toggleRecording() {
        val updateModalVisibility: (Boolean) -> Unit = { visible ->
            var changed = false
            if (modals.isRecordingVisible != visible) {
                modals.isRecordingVisible = visible
                changed = true
            }
            if (parameters.isRecordingModalVisible != visible) {
                parameters.updateIsRecordingModalVisible(visible)
                changed = true
            }
            if (changed) {
                notifyParametersChanged()
            }
        }

        val launchOptions = LaunchRecordingOptions(
            updateIsRecordingModalVisible = updateModalVisibility,
            isRecordingModalVisible = modals.isRecordingVisible,
            showAlert = ::showAlert,
            stopLaunchRecord = parameters.stopLaunchRecord,
            canLaunchRecord = parameters.canLaunchRecord,
            recordingAudioSupport = recording.recordingAudioSupport,
            recordingVideoSupport = recording.recordingVideoSupport,
            updateCanRecord = { value -> recording.updateCanRecord(value) },
            updateClearedToRecord = { value -> recording.updateClearedToRecord(value) },
            recordStarted = recording.recordStarted,
            recordPaused = recording.recordPaused,
            localUIMode = options.useLocalUIMode ?: parameters.localUIMode
        )

        launchRecording(launchOptions)
    }

    fun onParticipantsFilterChange(filter: String) {
        room.participantsFilter = filter
        if (filter.isBlank()) {
            room.updateFilteredParticipants(room.participants)
        } else {
            room.updateFilteredParticipants(
                room.participants.filter { participant ->
                    participant.name.contains(filter, ignoreCase = true)
                }
            )
        }
        notifyParametersChanged()
    }

    fun onWaitingRoomFilterChange(filter: String) {
        waitingRoom.filter = filter
        waitingRoom.updateFilteredList()
        notifyParametersChanged()
    }

    fun onRequestFilterChange(filter: String) {
        requests.filter = filter
        requests.updateFilteredList()
        notifyParametersChanged()
    }

    suspend fun joinRoom(
        socket: SocketManager?,
        roomName: String,
        islevel: String,
        member: String,
        sec: String,
        apiUserName: String
    ): ResponseJoinRoom {
        return try {
            // STEP 1: Join the room
            val options = JoinRoomClientOptions(
                socket = socket,
                roomName = roomName,
                islevel = islevel,
                member = member,
                sec = sec,
                apiUserName = apiUserName
            )
            val response = options.joinRoom()

            // STEP 2: Update in-memory room state with the server response
            room.updateRoomData(response)
            connectivity.updateRoomResponse(response)

            // STEP 3: Request the latest room parameters from the server
            if (socket != null) {
                try {
                    updateRoomParametersClient(
                        UpdateRoomParametersClientOptions(
                            parameters = UpdateRoomParametersBridge()
                        )
                    )
                } catch (e: Exception) {
                }
            }
            
            // STEP 4: Setup socket listeners for real-time events
            if (socket != null) {
                try {
                    setupSocketListeners(socket)
                } catch (e: Exception) {
                }
            }
            
            response
        } catch (e: Exception) {
            // Error handling with user notification
            showAlert(
                message = "Failed to join room: ${e.message}",
                type = "danger"
            )
            throw e
        }
    }

    // Note: setupSocketListeners() is already fully implemented at line ~1122
    // with all 30+ event handlers. This duplicate stub has been removed.

    fun connectAndValidate(
        roomName: String,
        member: String,
        adminPasscode: String,
        islevel: String,
        apiUserName: String,
        apiToken: String,
        showLoadingModal: Boolean = true,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val trimmedRoomName = roomName.trim()
        val trimmedMember = member.trim()
        val trimmedAdminPasscode = adminPasscode.trim()
        val trimmedIslevel = islevel.trim()
        val trimmedApiUserName = apiUserName.trim()
        val trimmedApiToken = apiToken.trim()
        val resolvedApiToken = trimmedApiToken.ifBlank { resolveApiToken() }
        val resolvedApiKey = resolveApiKey()
        val credentialFingerprint = resolvedApiToken.ifBlank { resolvedApiKey }
        val socketAlreadyConnected = isSocketConnected(connectivity.socket)

        if (trimmedRoomName.isBlank()) {
            showAlert("Please provide a room name.", "error")
            if (showLoadingModal) hideLoader()
            onComplete(false)
            return
        }

        if (credentialFingerprint.isBlank() && !socketAlreadyConnected) {
            showAlert("Please provide an API key/token.", "error")
            if (showLoadingModal) hideLoader()
            onComplete(false)
            return
        }

        if (showLoadingModal) showLoader()
        scope.launch {
            val currentJob = coroutineContext[Job]
            try {
                val resolvedApiUserName = trimmedApiUserName.ifBlank { resolveApiUserName() }
                if (resolvedApiUserName.isBlank()) {
                    showAlert("Please provide an API username.", "error")
                    onComplete(false)
                    return@launch
                }

                val resolvedMember = trimmedMember.ifBlank { resolvedApiUserName }
                val resolvedIslevel = trimmedIslevel.ifBlank { room.islevel.ifBlank { "0" } }
                val resolvedLink = effectiveLocalLink()
                val joinSignature = buildJoinRequestSignature(
                    roomName = trimmedRoomName,
                    member = resolvedMember,
                    islevel = resolvedIslevel,
                    adminPasscode = trimmedAdminPasscode,
                    link = resolvedLink,
                    apiUserName = resolvedApiUserName,
                    credential = credentialFingerprint
                )

                val isDuplicate = connectGuardMutex.withLock {
                    val activeJob = ongoingConnectJob
                    if (activeJob?.isActive == true && joinSignature == ongoingJoinSignature) {
                        true
                    } else {
                        ongoingConnectJob = currentJob
                        ongoingJoinSignature = joinSignature
                        false
                    }
                }
                if (isDuplicate) {
                    return@launch
                }

                val useLocalJoin = shouldUseLocalJoin(resolvedLink, trimmedAdminPasscode)
                val cloudSecret = resolveCloudSecret(resolvedApiToken, resolvedApiKey)
                if (!useLocalJoin && cloudSecret == null) {
                    showAlert(
                        "MediaSFU credentials missing socket secret. Please re-create or re-join the room.",
                        "danger"
                    )
                    onComplete(false)
                    return@launch
                }

                room.updateRoomName(trimmedRoomName)
                room.updateMember(resolvedMember)
                room.updateAdminPasscode(trimmedAdminPasscode)
                room.updateIslevel(resolvedIslevel)
                room.updateApiUserName(resolvedApiUserName)
                room.updateApiToken(resolvedApiToken)
                if (resolvedLink.isNotBlank()) {
                    room.updateLink(resolvedLink)
                }
                delay(500)
                suspendSocketPrefetching()
                val socketsReady = ensureSocketsReady(
                    link = resolvedLink,
                    apiUserName = resolvedApiUserName,
                    apiToken = resolvedApiToken,
                    memberName = resolvedMember,
                    apiKey = resolvedApiKey,
                    silent = !showLoadingModal
                )
                if (!socketsReady) {
                    onComplete(false)
                    return@launch
                }

                val activeSocket = when {
                    useLocalJoin -> connectivity.localSocket ?: connectivity.socket
                    else -> connectivity.socket
                }

                if (activeSocket == null) {
                    showAlert("Socket connection is not established.", "danger")
                    onComplete(false)
                    return@launch
                }

                val response = if (useLocalJoin) {
                    val (joinResponse, localResponse) = performLocalJoin(
                        socket = activeSocket,
                        roomName = trimmedRoomName,
                        islevel = resolvedIslevel,
                        member = resolvedMember,
                        sec = trimmedAdminPasscode,
                        apiUserName = resolvedApiUserName
                    )
                    // Mark that local join was performed to prevent duplicate joinRoom in joinAndUpdate
                    localJoinPerformed = true
                    applyLocalJoinMetadata(localResponse, resolvedLink)
                    joinResponse
                } else {
                    joinRoom(
                        socket = activeSocket,
                        roomName = trimmedRoomName,
                        islevel = resolvedIslevel,
                        member = resolvedMember,
                        sec = cloudSecret!!,
                        apiUserName = resolvedApiUserName
                    )
                }

                if (response.success != true) {
                    val reason = response.reason?.takeIf { it.isNotBlank() }
                        ?: when {
                            response.banned == true -> "You have been removed from this event."
                            response.suspended == true -> "This event is currently suspended."
                            response.noAdmin == true -> "Host has not joined yet."
                            else -> "Unable to join the event."
                        }
                    showAlert(reason, "danger")
                    onComplete(false)
                    return@launch
                }

                room.updateRoomData(response)
                connectivity.updateRoomResponse(response)
                updateRoomParametersClient(
                    UpdateRoomParametersClientOptions(
                        parameters = UpdateRoomParametersBridge()
                    )
                )
                updateValidated(true)
                // Initialize UI immediately like Flutter does - don't wait for allMembers
                // This will use hostLabel/resolvedMember as the initial participant
                scope.launch {
                    initializeMediaStateAfterValidation(resolvedMember)
                }
                meeting.startTimer()
                showAlert("Connected to ${trimmedRoomName}", "success")
                // Always clear any visible loader after a successful join.
                // This prevents stuck loaders when callers pass showLoadingModal=false
                // but some internal path still turned the loader on.
                if (showLoadingModal || _isLoading.value || modals.isLoadingVisible || parameters.isLoadingModalVisible) {
                    hideLoader()
                }
                onComplete(true)
            } catch (error: Throwable) {
                showAlert(error.message ?: "Failed to connect", "error")
                if (showLoadingModal || _isLoading.value || modals.isLoadingVisible || parameters.isLoadingModalVisible) {
                    hideLoader()
                }
                onComplete(false)
            } finally {
                connectGuardMutex.withLock {
                    if (ongoingConnectJob == currentJob) {
                        ongoingConnectJob = null
                        ongoingJoinSignature = null
                    }
                }
                resumeSocketPrefetching()
                if (showLoadingModal || _isLoading.value || modals.isLoadingVisible || parameters.isLoadingModalVisible) {
                    hideLoader()
                }
            }
        }
    }

    private suspend fun ensureSocketsReady(
        link: String,
        apiUserName: String,
        apiToken: String,
        memberName: String,
        apiKey: String,
        silent: Boolean = false
    ): Boolean {
        val normalizedUser = apiUserName.ifBlank { return false }
        val normalizedToken = apiToken.trim()
        val normalizedKey = apiKey.trim()
        if (normalizedToken.isBlank() && normalizedKey.isBlank()) {
            return false
        }
        val normalizedLink = link.ifBlank { DEFAULT_SOCKET_BASE }
        val normalizedMember = memberName.ifBlank { normalizedUser }
        val effectiveToken = normalizedToken.ifBlank { normalizedKey }

        if (isSocketConnected(connectivity.socket)) {
            if (activeSocketSignature == null) {
                activeSocketSignature = deriveCurrentSocketSignature()
            }
            return true
        }
        return socketConnectMutex.withLock {
            if (isSocketConnected(connectivity.socket)) {
                if (activeSocketSignature == null) {
                    activeSocketSignature = deriveCurrentSocketSignature()
                }
                return@withLock true
            }

            if (!isSocketConnected(connectivity.socket) && connectivity.socket != null) {
                runCatching { connectivity.socket?.disconnect() }
                connectivity.updateSocket(null)
            }

            val loaderShown = if (!silent && !parameters.isLoadingModalVisible) {
                showLoader()
                true
            } else {
                false
            }

            try {
                val attemptsAllowed = if (silent) 1 else 2
                var attempt = 0
                var lastFailureMessage: String? = null
                while (attempt < attemptsAllowed) {
                    attempt++
                    val (success, failureMessage) = attemptSocketAcquisition(
                        normalizedLink = normalizedLink,
                        normalizedUser = normalizedUser,
                        effectiveToken = effectiveToken,
                        normalizedKey = normalizedKey,
                        normalizedMember = normalizedMember
                    )
                    if (success) {
                        activeSocketSignature = deriveCurrentSocketSignature()
                        return@withLock true
                    }
                    lastFailureMessage = failureMessage
                    clearActiveSocketSignature()
                    if (attempt < attemptsAllowed && !silent) {
                    }
                }

                if (!silent) {
                    showAlert(lastFailureMessage ?: "Unable to establish socket connection.", "danger")
                }
                return@withLock false
            } finally {
                if (loaderShown) {
                    hideLoader()
                }
            }
        }
    }

    private suspend fun attemptSocketAcquisition(
        normalizedLink: String,
        normalizedUser: String,
        effectiveToken: String,
        normalizedKey: String,
        normalizedMember: String
    ): Pair<Boolean, String?> {
        return try {
            val params = AutoConnectParameters(
                state = this,
                targetLink = normalizedLink,
                resolvedApiUserName = normalizedUser,
                resolvedApiToken = effectiveToken,
                resolvedApiKey = normalizedKey,
                resolvedUserName = normalizedMember
            )
            checkLimitsAndMakeRequest(
                CheckLimitsAndMakeRequestOptions(
                    apiUserName = normalizedUser,
                    apiToken = effectiveToken,
                    link = normalizedLink,
                    userName = normalizedMember,
                    parameters = params,
                    validate = true
                )
            )
            val connected = isSocketConnected(connectivity.socket)
            if (connected) {
                true to null
            } else {
                false to "Unable to establish socket connection."
            }
        } catch (error: Throwable) {
            false to (error.message ?: "Failed to connect to MediaSFU.")
        }
    }

    private class AutoConnectParameters(
        private val state: MediasfuGenericState,
        private val targetLink: String,
        private val resolvedApiUserName: String,
        private val resolvedApiToken: String,
        private val resolvedApiKey: String,
        private val resolvedUserName: String
    ) : CheckLimitsAndMakeRequestParameters {
        override val apiUserName: String get() = resolvedApiUserName
        override val apiToken: String get() = resolvedApiToken
        override val link: String get() = targetLink
        override val userName: String get() = resolvedUserName
        override val validate: Boolean get() = true
        override val socket: SocketManager? get() = state.connectivity.socket
        override val localSocket: SocketManager? get() = state.connectivity.localSocket
        override val updateSocket: (SocketManager?) -> Unit = { state.connectivity.updateSocket(it) }
        override val updateLocalSocket: (SocketManager?) -> Unit = { state.connectivity.updateLocalSocket(it) }
        override val connectSocket: suspend (String, String, String, String) -> SocketManager? =
            { providedUser, providedToken, linkValue, _ ->
                val normalizedUser = providedUser.ifBlank { resolvedApiUserName }
                val normalizedToken = providedToken.ifBlank { resolvedApiToken }
                state.openSocket(
                    link = linkValue,
                    apiUserName = normalizedUser,
                    apiToken = normalizedToken,
                    apiKey = resolvedApiKey
                )
            }

        override fun getUpdatedAllParams(): CheckLimitsAndMakeRequestParameters = this
    }

    internal suspend fun openSocket(
        link: String,
        apiUserName: String,
        apiToken: String?,
        apiKey: String?
    ): SocketManager? {
        val baseUrl = resolveSocketBaseUrl(link)
        if (baseUrl.isBlank()) return null

        val mediaEndpoint = ensureMediaNamespace(baseUrl)
        val socketUrl = appendCredentialQuery(mediaEndpoint, apiUserName, apiToken, apiKey)
        val socket = createSocketManager()
        val config = SocketConfig(transports = listOf("websocket"))
        val result = socket.connect(socketUrl, config)
        return if (result.isSuccess) {
            // React/Flutter wait 15–18 seconds before abandoning the initial dial; mirror that here
            val connected = waitForSocketConnection(socket, timeoutMs = 18_000L)
            if (!connected) {
                socket.disconnect()
                null
            } else {
                socket
            }
        } else {
            null
        }
    }

    private suspend fun waitForSocketConnection(
        socket: SocketManager,
        timeoutMs: Long = 15_000L,
        pollIntervalMs: Long = 100L
    ): Boolean {
        return try {
            withTimeout(timeoutMs) {
                while (isActive) {
                    when (socket.getConnectionState()) {
                        ConnectionState.CONNECTED -> return@withTimeout true
                        ConnectionState.FAILED -> return@withTimeout false
                        ConnectionState.DISCONNECTED -> if (!socket.isConnected()) return@withTimeout false
                        else -> Unit
                    }

                    if (socket.isConnected()) {
                        return@withTimeout true
                    }

                    delay(pollIntervalMs)
                }
                socket.isConnected()
            }
        } catch (e: Exception) {
            socket.isConnected()
        }
    }

    private fun resolveSocketBaseUrl(link: String): String {
        val trimmed = link.trim().ifBlank { return DEFAULT_SOCKET_BASE }
        val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val schemeSplit = normalized.indexOf("://")
        if (schemeSplit <= 0) return DEFAULT_SOCKET_BASE
        val rawScheme = normalized.substring(0, schemeSplit).lowercase()
        val scheme = when (rawScheme) {
            "ws" -> "http"
            "wss" -> "https"
            else -> rawScheme
        }
        val remainder = normalized.substring(schemeSplit + 3)
        if (remainder.isBlank()) return DEFAULT_SOCKET_BASE
        val pathIndex = remainder.indexOfAny(charArrayOf('/', '?', '#'))
        val hostPart = if (pathIndex >= 0) remainder.substring(0, pathIndex) else remainder
        if (hostPart.isBlank()) return DEFAULT_SOCKET_BASE
        return "$scheme://$hostPart"
    }

    private fun ensureMediaNamespace(baseUrl: String): String {
        val sanitized = baseUrl.trimEnd('/')
        return if (sanitized.endsWith("/media")) sanitized else "$sanitized/media"
    }

    private fun appendCredentialQuery(
        endpoint: String,
        apiUserName: String,
        apiToken: String?,
        apiKey: String?
    ): String {
        val params = mutableListOf<Pair<String, String>>()
        if (apiUserName.isNotBlank()) {
            params += "apiUserName" to apiUserName
        }

        val tokenCandidate = apiToken?.takeIf { it.isNotBlank() }
        val keyCandidate = apiKey?.takeIf { it.isNotBlank() }
        when {
            tokenCandidate != null && tokenCandidate.length == 64 -> params += "apiToken" to tokenCandidate
            keyCandidate != null && keyCandidate.length == 64 -> params += "apiKey" to keyCandidate
            tokenCandidate != null -> params += "apiToken" to tokenCandidate
            keyCandidate != null -> params += "apiKey" to keyCandidate
        }

        if (params.isEmpty()) return endpoint

        return buildString {
            append(endpoint)
            append(if (endpoint.contains("?")) '&' else '?')
            params.joinTo(this, "&") { (key, value) ->
                "$key=${value.urlEncode()}"
            }
        }
    }

    private fun String.urlEncode(): String {
        if (isEmpty()) return ""
        val safeChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
        val builder = StringBuilder(length)
        for (ch in this) {
            if (safeChars.indexOf(ch) >= 0) {
                builder.append(ch)
            } else {
                builder.append('%')
                builder.append(ch.code.toString(16).padStart(2, '0').uppercase())
            }
        }
        return builder.toString()
    }

    private fun effectiveLocalLink(): String = room.link.ifBlank { options.localLink }

    private fun shouldUseLocalJoin(link: String, adminPasscode: String): Boolean {
        val normalizedLink = link.trim()
        val explicitLocalLink = normalizedLink.isNotBlank() &&
            !normalizedLink.contains("mediasfu.com", ignoreCase = true)

        if (options.connectMediaSFU) {
            return explicitLocalLink || connectivity.localSocket != null
        }

        if (explicitLocalLink) return true
        if (connectivity.localSocket != null) return true
        return adminPasscode.length == 32 && adminPasscode.all { it.isLetterOrDigit() }
    }

    private suspend fun socketJoinLocalRoom(options: JoinLocalRoomOptions) = joinLocalRoom(options)

    private suspend fun performLocalJoin(
        socket: SocketManager,
        roomName: String,
        islevel: String,
        member: String,
        sec: String,
        apiUserName: String
    ): Pair<ResponseJoinRoom, ResponseJoinLocalRoom> {
        val options = JoinLocalRoomOptions(
            socket = socket,
            roomName = roomName,
            islevel = islevel,
            member = member,
            sec = sec,
            apiUserName = apiUserName
        )

        val localResponse = socketJoinLocalRoom(options).getOrElse { error ->
            throw error
        }

        val joinResponse = createResponseJoinRoom(
            CreateResponseJoinRoomOptions(localRoom = localResponse)
        )

        return joinResponse to localResponse
    }

    private fun applyLocalJoinMetadata(localResponse: ResponseJoinLocalRoom, fallbackLink: String) {
        val resolvedLink = localResponse.mediasfuURL?.takeIf { it.isNotBlank() } ?: fallbackLink
        if (resolvedLink.isNotBlank()) {
            room.updateLink(resolvedLink)
        }

        localResponse.apiUserName?.takeIf { it.isNotBlank() }?.let { value ->
            room.updateApiUserName(value)
        }

        localResponse.apiKey?.takeIf { it.isNotBlank() }?.let { value ->
            room.updateApiKey(value)
        }

        localResponse.allowRecord?.let { allowed ->
            recording.updateCanRecord(allowed)
            room.updateConfirmedToRecord(allowed)
        }
    }

    private suspend fun initializeMediaStateAfterValidation(defaultMemberName: String) {
        val participantsSnapshot = parameters.participants
        
        // Even if participants are empty, we can still initialize with the member name
        // This matches Flutter's behavior of calling prepopulateUserMedia immediately
        // Priority: 1) defaultMemberName (the joining member), 2) mainScreenPerson, 3) hostLabel
        val targetName = listOfNotNull(
            defaultMemberName.takeIf { it.isNotBlank() },
            parameters.mainScreenPerson.takeIf { it.isNotBlank() },
            participantsSnapshot.firstOrNull { it.isHost }?.name?.takeIf { it.isNotBlank() },
            participantsSnapshot.firstOrNull()?.name?.takeIf { it.isNotBlank() },
            parameters.hostLabel.takeIf { it.isNotBlank() }
        ).firstOrNull()

        if (targetName.isNullOrBlank()) {
            propagateParameterChanges()
            return
        }

        var effectiveParticipants = participantsSnapshot
        if (effectiveParticipants.isEmpty()) {
            val normalizedLevel = if (targetName.equals(parameters.member, ignoreCase = true)) {
                parameters.islevel.ifBlank { "1" }
            } else {
                "1"
            }

            val placeholderParticipant = Participant(
                id = "placeholder-${targetName.lowercase().replace(seedNameSanitizer, "-").trim('-').ifBlank { "self" }}",
                audioID = "audio-${targetName.lowercase().replace(seedNameSanitizer, "-").trim('-').ifBlank { "self" }}",
                videoID = "video-${targetName.lowercase().replace(seedNameSanitizer, "-").trim('-').ifBlank { "self" }}",
                islevel = normalizedLevel,
                isAdmin = normalizedLevel == "2",
                isHost = normalizedLevel == "2",
                name = targetName,
                videoOn = parameters.videoAlreadyOn,
                audioOn = parameters.audioAlreadyOn,
                extra = buildJsonObject {
                    put("placeholder", JsonPrimitive(true))
                    put("source", JsonPrimitive("immediate-hydration"))
                }
            )

            room.updateParticipants(listOf(placeholderParticipant))
            parameters.refParticipants = listOf(placeholderParticipant)
            effectiveParticipants = listOf(placeholderParticipant)
        }

        // Seed placeholder streams if needed (only if we don't have real streams yet)
        val existingStreams = parameters.allVideoStreamsState
        val shouldSeedPlaceholders = existingStreams.isEmpty() || existingStreams.all { stream ->
            stream.extra["placeholder"] == JsonPrimitive(true)
        }

        if (shouldSeedPlaceholders) {
            val placeholderStreams = buildPlaceholderStreams(effectiveParticipants)
            if (placeholderStreams.isNotEmpty()) {
                parameters.updateAllVideoStreams(placeholderStreams)
                parameters.updateOldAllStreams(placeholderStreams)
                parameters.updateNewLimitedStreams(placeholderStreams)
                parameters.updateNewLimitedStreamsIDs(
                    placeholderStreams
                        .map { stream -> stream.id ?: stream.producerId }
                        .distinct()
                )

                streams.updateStreamNames(placeholderStreams)
                streams.updateCurrentStreams(placeholderStreams)
                streams.updatePaginatedStreams(listOf(placeholderStreams))
                streams.updateNonAlVideoStreams(placeholderStreams)
                streams.updateMixedAlVideoStreams(emptyList())
                streams.updateNonAlVideoStreamsMuted(placeholderStreams.filter { it.muted == true })
                streams.updateAllAudioStreams(placeholderStreams)
                streams.updateRemoteScreenStreams(emptyList())
                streams.updateLStreams(placeholderStreams)
                streams.updateChatRefStreams(emptyList())
                val placeholderNames = effectiveParticipants.map(Participant::name)
                streams.updateActiveNames(placeholderNames)
                streams.updatePrevActiveNames(placeholderNames)
                streams.updatePActiveNames(placeholderNames)
                streams.updateDispActiveNames(placeholderNames)
                streams.updatePDispActiveNames(placeholderNames)
                streams.updateMainScreenPerson(targetName)
                streams.updateMainScreenFilled(true)
                streams.updateAdminOnMainScreen(
                    effectiveParticipants.firstOrNull()?.let { it.isHost || it.isAdmin } == true
                )
                streams.updateGotAllVids(true)

                if (!display.showMiniView) {
                    display.updateShowMiniView(true)
                }
            }
        }

        try {
            val prepopulateOptions = PrepopulateUserMediaOptions(
                name = targetName,
                parameters = parameters
            )
            parameters.prepopulateUserMedia.invoke(prepopulateOptions)
        } catch (error: Throwable) {
        }

        try {
            // Use EngineReorderStreamsParameters wrapper to provide ChangeVidsParameters support
            // This is required for changeVids to be called which sets up pagination
            val reorderOptions = ReorderStreamsOptions(
                add = false,
                screenChanged = false,
                parameters = EngineReorderStreamsParameters(parameters),
                streams = emptyList()
            )
            parameters.reorderStreams.invoke(reorderOptions)
        } catch (error: Throwable) {
        }

        propagateParameterChanges()

        val hasRealParticipants = parameters.participants.any { participant ->
            participant.extra["placeholder"] != JsonPrimitive(true)
        }

        if (hasRealParticipants) {
            initialMediaHydrated = true
        }
    }

    /** Socket-level room join using JoinRoomClientOptions */
    private suspend fun JoinRoomClientOptions.joinRoom(): ResponseJoinRoom {
        return try {
            options.joinRoomClient(this)
        } catch (error: Throwable) {
            throw IllegalStateException(
                "Failed to join room: ${error.message ?: "unknown error"}",
                error
            )
        }
    }

    internal fun createUpdateRoomParametersBridge(): UpdateRoomParametersClientParameters {
        return UpdateRoomParametersBridge()
    }

    private inner class UpdateRoomParametersBridge : UpdateRoomParametersClientParameters {
        override val rtpCapabilities get() = parameters.rtpCapabilities
        override val roomRecvIPs get() = parameters.roomRecvIPs
        override val meetingRoomParams get() = parameters.meetingRoomParams
        override val itemPageLimit get() = media.itemPageLimit
        override val audioOnlyRoom get() = media.audioOnlyRoom
        override val addForBasic get() = media.addForBasic
        override val screenPageLimit get() = media.screenPageLimit
        override val shareScreenStarted get() = media.shareScreenStarted
        override val shared get() = media.shared
        override val targetOrientation get() = media.targetOrientation
        override val vidCons get() = parameters.vidCons
        override val recordingVideoSupport get() = parameters.recordingVideoSupport
        override val frameRate get() = media.frameRate
        override val adminPasscode get() = room.adminPasscode
        override val eventType get() = room.eventType
        override val youAreCoHost get() = room.youAreCoHost
        override val autoWave get() = parameters.autoWave
        override val forceFullDisplay get() = parameters.forceFullDisplay
        override val chatSetting get() = media.chatSetting
        override val meetingDisplayType get() = room.meetingDisplayType
        override val audioSetting get() = media.audioSetting
        override val videoSetting get() = media.videoSetting
        override val screenshareSetting get() = media.screenshareSetting
        override val hParams get() = parameters.hParams
        override val vParams get() = parameters.vParams
        override val screenParams get() = parameters.screenParams
        override val aParams get() = parameters.aParams
        override val islevel get() = room.islevel
        override val showAlert: ShowAlert?
            get() = ShowAlert { message, type, duration ->
                this@MediasfuGenericState.showAlert(message, type, duration)
            }
        override val roomData get() = parameters.roomData

        override val updateRtpCapabilities: UpdateRtpCapabilities
            get() = { value ->
                media.rtpCapabilities = value
                parameters.routerRtpCapabilities = value
                parameters.rtpCapabilities = value
                parameters.extendedRtpCapabilities = null
                parameters.negotiatedRecvRtpCapabilities = null
                notifyParametersChanged()

                if (value != null) {
                    val currentDevice = parameters.device
                    if (currentDevice != null) {
                        launchInScope {
                            currentDevice
                                .load(value)
                                .onSuccess {
                                    val deviceCaps = currentDevice.currentRtpCapabilities() ?: value
                                    parameters.rtpCapabilities = deviceCaps
                                    media.rtpCapabilities = deviceCaps
                                    val extendedCaps = parameters.routerRtpCapabilities?.let { routerCaps ->
                                        runCatching {
                                            OrtcUtils.getExtendedRtpCapabilities(
                                                localCaps = deviceCaps,
                                                remoteCaps = routerCaps
                                            )
                                        }.getOrNull()
                                    }
                                    parameters.extendedRtpCapabilities = extendedCaps
                                    val negotiatedRecvCaps = extendedCaps?.let { extended ->
                                        runCatching {
                                            OrtcUtils.getRecvRtpCapabilities(extended)
                                        }.getOrNull()
                                    }
                                    parameters.negotiatedRecvRtpCapabilities = negotiatedRecvCaps
                                    notifyParametersChanged()
                                }
                        }
                    }
                }
            }

        override val updateRoomRecvIPs: UpdateRoomRecvIPs
            get() = { value ->
                parameters.roomRecvIPs = value
                notifyParametersChanged()
            }

        override val updateMeetingRoomParams: UpdateMeetingRoomParams
            get() = { value ->
                media.meetingRoomParams = value
                parameters.meetingRoomParams = value
                notifyParametersChanged()
            }

        override val updateItemPageLimit: UpdateItemPageLimit
            get() = { value ->
                media.itemPageLimit = value
                parameters.itemPageLimit = value
                notifyParametersChanged()
            }

        override val updateAudioOnlyRoom: UpdateAudioOnlyRoom
            get() = { value ->
                media.audioOnlyRoom = value
                parameters.audioOnlyRoom = value
                notifyParametersChanged()
            }

        override val updateAddForBasic: UpdateAddForBasic
            get() = { value ->
                media.addForBasic = value
                parameters.addForBasic = value
                notifyParametersChanged()
            }

        override val updateScreenPageLimit: UpdateScreenPageLimit
            get() = { value ->
                media.screenPageLimit = value
                parameters.screenPageLimit = value
                notifyParametersChanged()
            }

        override val updateVidCons: UpdateVidCons
            get() = { value ->
                media.vidCons = value
                parameters.vidCons = value
                notifyParametersChanged()
            }

        override val updateFrameRate: UpdateFrameRate
            get() = { value ->
                media.frameRate = value
                parameters.frameRate = value
                notifyParametersChanged()
            }

        override val updateAdminPasscode: UpdateAdminPasscode
            get() = { value -> room.updateAdminPasscode(value) }

        override val updateEventType: UpdateEventType
            get() = { value ->
                room.eventType = value
                parameters.eventType = value
                notifyParametersChanged()
            }

        override val updateYouAreCoHost: UpdateYouAreCoHost
            get() = { value ->
                room.youAreCoHost = value
                parameters.youAreCoHost = value
                notifyParametersChanged()
            }

        override val updateAutoWave: UpdateAutoWave
            get() = { value ->
                parameters.autoWave = value
                notifyParametersChanged()
            }

        override val updateForceFullDisplay: UpdateForceFullDisplay
            get() = { value ->
                parameters.forceFullDisplay = value
                notifyParametersChanged()
            }

        override val updateChatSetting: UpdateChatSetting
            get() = { value ->
                media.chatSetting = value
                parameters.chatSetting = value
                notifyParametersChanged()
            }

        override val updateMeetingDisplayType: UpdateMeetingDisplayType
            get() = { value ->
                room.meetingDisplayType = value
                parameters.meetingDisplayType = value
                notifyParametersChanged()
            }

        override val updateAudioSetting: UpdateAudioSetting
            get() = { value ->
                media.audioSetting = value
                parameters.audioSetting = value
                notifyParametersChanged()
            }

        override val updateVideoSetting: UpdateVideoSetting
            get() = { value ->
                media.videoSetting = value
                parameters.videoSetting = value
                notifyParametersChanged()
            }

        override val updateScreenshareSetting: UpdateScreenshareSetting
            get() = { value ->
                media.screenshareSetting = value
                parameters.screenshareSetting = value
                notifyParametersChanged()
            }

        override val updateHParams: UpdateHParams
            get() = { value ->
                parameters.hParams = value
                notifyParametersChanged()
            }

        override val updateVParams: UpdateVParams
            get() = { value ->
                parameters.vParams = value
                notifyParametersChanged()
            }

        override val updateScreenParams: UpdateScreenParams
            get() = { value ->
                parameters.screenParams = value
                notifyParametersChanged()
            }

        override val updateAParams: UpdateAParams
            get() = { value ->
                parameters.aParams = value
                notifyParametersChanged()
            }

        override val updateMainHeightWidth: UpdateMainHeightWidth
            get() = { value ->
                display.updateMainHeightWidth(value)
            }

        override val updateTargetResolution: UpdateTargetResolution
            get() = { value ->
                media.targetResolution = value
                parameters.targetResolution = value
                notifyParametersChanged()
            }

        override val updateTargetResolutionHost: UpdateTargetResolutionHost
            get() = { value ->
                media.targetResolutionHost = value
                parameters.targetResolutionHost = value
                notifyParametersChanged()
            }

        override val updateRecordingAudioPausesLimit: UpdateRecordingAudioPausesLimit
            get() = { value ->
                parameters.updateRecordingAudioPausesLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingAudioPausesCount: UpdateRecordingAudioPausesCount
            get() = { value ->
                parameters.updateRecordingAudioPausesCount(value)
                notifyParametersChanged()
            }

        override val updateRecordingAudioSupport: UpdateRecordingAudioSupport
            get() = { value ->
                recording.updateRecordingAudioSupport(value)
            }

        override val updateRecordingAudioPeopleLimit: UpdateRecordingAudioPeopleLimit
            get() = { value ->
                parameters.updateRecordingAudioPeopleLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingAudioParticipantsTimeLimit: UpdateRecordingAudioParticipantsTimeLimit
            get() = { value ->
                parameters.updateRecordingAudioParticipantsTimeLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoPausesCount: UpdateRecordingVideoPausesCount
            get() = { value ->
                parameters.updateRecordingVideoPausesCount(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoPausesLimit: UpdateRecordingVideoPausesLimit
            get() = { value ->
                parameters.updateRecordingVideoPausesLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoSupport: UpdateRecordingVideoSupport
            get() = { value ->
                recording.updateRecordingVideoSupport(value)
            }

        override val updateRecordingVideoPeopleLimit: UpdateRecordingVideoPeopleLimit
            get() = { value ->
                parameters.updateRecordingVideoPeopleLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoParticipantsTimeLimit: UpdateRecordingVideoParticipantsTimeLimit
            get() = { value ->
                parameters.updateRecordingVideoParticipantsTimeLimit(value)
                notifyParametersChanged()
            }

        override val updateRecordingAllParticipantsSupport: UpdateRecordingAllParticipantsSupport
            get() = { value ->
                recording.updateRecordingAllParticipantsSupport(value)
            }

        override val updateRecordingVideoParticipantsSupport: UpdateRecordingVideoParticipantsSupport
            get() = { value ->
                recording.updateRecordingVideoParticipantsSupport(value)
            }

        override val updateRecordingAllParticipantsFullRoomSupport: UpdateRecordingAllParticipantsFullRoomSupport
            get() = { value ->
                parameters.updateRecordingAllParticipantsFullRoomSupport(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoParticipantsFullRoomSupport: UpdateRecordingVideoParticipantsFullRoomSupport
            get() = { value ->
                parameters.updateRecordingVideoParticipantsFullRoomSupport(value)
                notifyParametersChanged()
            }

        override val updateRecordingPreferredOrientation: UpdateRecordingPreferredOrientation
            get() = { value ->
                parameters.updateRecordingPreferredOrientation(value)
                parameters.updateRecordingOrientationVideo(value)
                notifyParametersChanged()
            }

        override val updateRecordingSupportForOtherOrientation: UpdateRecordingSupportForOtherOrientation
            get() = { value ->
                parameters.updateRecordingSupportForOtherOrientation(value)
                notifyParametersChanged()
            }

        override val updateRecordingMultiFormatsSupport: UpdateRecordingMultiFormatsSupport
            get() = { value ->
                parameters.updateRecordingMultiFormatsSupport(value)
                notifyParametersChanged()
            }

        override val updateRecordingVideoOptions: UpdateRecordingVideoOptions
            get() = { value ->
                recording.updateRecordingVideoOptions(value)
            }

        override val updateRecordingAudioOptions: UpdateRecordingAudioOptions
            get() = { value ->
                recording.updateRecordingAudioOptions(value)
            }

        override fun getUpdatedAllParams(): UpdateRoomParametersClientParameters = this
    }

    internal fun propagateParameterChanges() {
        syncDisplayGridComponentsFromParameters()
        room.refresh()
        display.refresh()
        streams.refresh()
        recording.refresh()
        polls.refresh()
        requests.refresh()
        notifyParametersChanged()
    }

    private fun syncDisplayGridComponentsFromParameters() {
        val sourceStreams = parameters.otherGridStreams
        val currentStreams = display.otherGridStreams

        if (sourceStreams.isStructurallyEqualTo(currentStreams)) {
            return
        }

        val normalizedStreams = listOf(
            sourceStreams.getOrNull(0)?.toList().orEmpty(),
            sourceStreams.getOrNull(1)?.toList().orEmpty()
        )

        display.updateOtherGridStreams(normalizedStreams)
    }

    private fun List<List<MediaSfuUIComponent>>.isStructurallyEqualTo(
        other: List<List<MediaSfuUIComponent>>
    ): Boolean {
        val maxSize = maxOf(size, other.size)
        for (index in 0 until maxSize) {
            val left = getOrNull(index).orEmpty()
            val right = other.getOrNull(index).orEmpty()

            if (left.size != right.size) {
                return false
            }

            for (i in left.indices) {
                val leftComponent = left[i]
                val rightComponent = right[i]

                if (leftComponent !== rightComponent ||
                    leftComponent.id != rightComponent.id ||
                    leftComponent::class != rightComponent::class
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun notifyParametersChanged() {
        options.updateSourceParameters?.invoke(parameters)
    }
}

// ---------------------------------------------------------------------------
// State slices (grouping related data similar to Flutter implementation)
// ---------------------------------------------------------------------------

class ConnectivityState(
    private val parameters: MediasfuParameters,
    private val notifier: () -> Unit
) {
    var socket: SocketManager? by mutableStateOf(parameters.socket)
        private set

    var localSocket: SocketManager? by mutableStateOf(parameters.localSocket)
        private set

    var device: WebRtcDevice? by mutableStateOf(parameters.device)
        private set
    
    // Virtual background processor - persists during the session
    var virtualBackgroundProcessor: com.mediasfu.sdk.background.VirtualBackgroundProcessor? = null
        internal set

    var roomResponse: ResponseJoinRoom by mutableStateOf(parameters.roomData)
        private set

    fun updateSocket(value: SocketManager?) {
        if (socket === value) return
        socket = value
        parameters.socket = value
        notifier()
    }

    fun updateLocalSocket(value: SocketManager?) {
        if (localSocket === value) return
        localSocket = value
        parameters.localSocket = value
        notifier()
    }

    fun updateDevice(value: WebRtcDevice?) {
        if (device === value) return
        device = value
        parameters.device = value
        notifier()
    }

    fun updateRoomResponse(value: ResponseJoinRoom) {
        if (roomResponse == value) return
        roomResponse = value
        parameters.roomData = value
        notifier()
    }

    fun clearConnections() {
        updateSocket(null)
        updateLocalSocket(null)
    }
}

class RoomState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var apiKey by mutableStateOf(parameters.apiKey)
    var apiUserName by mutableStateOf(parameters.apiUserName)
    var apiToken by mutableStateOf(parameters.apiToken)
    var link by mutableStateOf(parameters.link)

    var roomName by mutableStateOf(parameters.roomName)
        private set
    var member by mutableStateOf(parameters.member)
        private set
    var adminPasscode by mutableStateOf(parameters.adminPasscode)
        private set
    var islevel by mutableStateOf(parameters.islevel)
        private set
    var coHost by mutableStateOf(parameters.coHost)
    var youAreCoHost by mutableStateOf(parameters.youAreCoHost)
    var youAreHost by mutableStateOf(parameters.youAreHost)
    var confirmedToRecord by mutableStateOf(parameters.confirmedToRecord)

    var meetingDisplayType by mutableStateOf(parameters.meetingDisplayType)
    var meetingVideoOptimized by mutableStateOf(parameters.meetingVideoOptimized)
    var eventType by mutableStateOf(parameters.eventType)

    val participants = mutableStateListOf<Participant>().apply {
        addAll(parameters.participants)
    }
    val filteredParticipants = mutableStateListOf<Participant>().apply {
        addAll(parameters.filteredParticipants.ifEmpty { parameters.participants })
    }

    var participantsCounter by mutableStateOf(parameters.participantsCounter)
    var participantsFilter by mutableStateOf(parameters.participantsFilter)

    var coHostResponsibility by mutableStateOf(parameters.coHostResponsibility)
    var adminRestrictSetting by mutableStateOf(parameters.adminRestrictSetting)

    fun updateRoomData(value: ResponseJoinRoom) {
        parameters.roomData = value
        notifier()
    }

    fun refresh() {
        apiKey = parameters.apiKey
        apiUserName = parameters.apiUserName
        apiToken = parameters.apiToken
        link = parameters.link
        roomName = parameters.roomName
        member = parameters.member
        adminPasscode = parameters.adminPasscode
        islevel = parameters.islevel
        coHost = parameters.coHost
        youAreCoHost = parameters.youAreCoHost
        youAreHost = parameters.youAreHost
        confirmedToRecord = parameters.confirmedToRecord
        meetingDisplayType = parameters.meetingDisplayType
        meetingVideoOptimized = parameters.meetingVideoOptimized
        eventType = parameters.eventType
        participants.syncWith(parameters.participants)
        filteredParticipants.syncWith(parameters.filteredParticipants.ifEmpty { parameters.participants })
        participantsCounter = parameters.participantsCounter
        participantsFilter = parameters.participantsFilter
        coHostResponsibility = parameters.coHostResponsibility
        adminRestrictSetting = parameters.adminRestrictSetting
    }

    fun updateRoomName(value: String) {
        roomName = value
        parameters.roomName = value
        notifier()
    }

    fun updateMember(value: String) {
        member = value
        parameters.member = value
        notifier()
    }

    fun updateAdminPasscode(value: String) {
        adminPasscode = value
        parameters.adminPasscode = value
        notifier()
    }

    fun updateIslevel(value: String) {
        islevel = value
        parameters.islevel = value
        notifier()
    }

    fun updateApiKey(value: String) {
        apiKey = value
        parameters.apiKey = value
        notifier()
    }

    fun updateApiUserName(value: String) {
        apiUserName = value
        parameters.apiUserName = value
        notifier()
    }

    fun updateApiToken(value: String) {
        apiToken = value
        parameters.apiToken = value
        notifier()
    }

    fun updateLink(value: String) {
        link = value
        parameters.link = value
        notifier()
    }

    fun updateCoHost(value: String) {
        if (coHost == value) return
        coHost = value
        parameters.coHost = value
        notifier()
    }

    fun updateCoHostResponsibility(value: List<ModelCoHostResponsibility>) {
        if (coHostResponsibility == value) return
        coHostResponsibility = value
        parameters.coHostResponsibility = value
        notifier()
    }

    fun updateConfirmedToRecord(value: Boolean) {
        if (confirmedToRecord == value) return
        confirmedToRecord = value
        parameters.updateConfirmedToRecord(value)
        notifier()
    }

    fun updateParticipants(newParticipants: List<Participant>) {
        participants.clear()
        participants.addAll(newParticipants)
        parameters.participants = newParticipants
        updateFilteredParticipants(newParticipants)
        participantsCounter = newParticipants.size
        parameters.participantsCounter = participantsCounter
        notifier()
    }

    fun updateFilteredParticipants(newFiltered: List<Participant>) {
        filteredParticipants.clear()
        filteredParticipants.addAll(newFiltered)
        parameters.filteredParticipants = newFiltered
        participantsCounter = newFiltered.size
        parameters.participantsCounter = participantsCounter
        notifier()
    }

    fun updateAudioSetting(setting: String) {
        parameters.audioSetting = setting
        notifier()
    }

    fun updateVideoSetting(setting: String) {
        parameters.videoSetting = setting
        notifier()
    }

    fun updateScreenshareSetting(setting: String) {
        parameters.screenshareSetting = setting
        notifier()
    }

    fun updateChatSetting(setting: String) {
        parameters.chatSetting = setting
        notifier()
    }

    private fun <T> MutableList<T>.syncWith(values: List<T>) {
        if (this == values) return
        clear()
        addAll(values)
    }
}

class MediaState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var consumeSockets by mutableStateOf(parameters.consumeSocketsState)
    var rtpCapabilities by mutableStateOf(parameters.rtpCapabilities)
    var meetingRoomParams by mutableStateOf(parameters.meetingRoomParams)
    var itemPageLimit by mutableStateOf(parameters.itemPageLimit)
    var audioOnlyRoom by mutableStateOf(parameters.audioOnlyRoom)
    var addForBasic by mutableStateOf(parameters.addForBasic)
    var screenPageLimit by mutableStateOf(parameters.screenPageLimit)
    var shareScreenStarted by mutableStateOf(parameters.shareScreenStarted)
    var shared by mutableStateOf(parameters.shared)
    var targetOrientation by mutableStateOf(parameters.targetOrientation)
    var targetResolution by mutableStateOf(parameters.targetResolution)
    var targetResolutionHost by mutableStateOf(parameters.targetResolutionHost)
    var vidCons by mutableStateOf(parameters.vidCons)
    var frameRate by mutableStateOf(parameters.frameRate)

    var videoAlreadyOn by mutableStateOf(parameters.videoAlreadyOn)
    var audioAlreadyOn by mutableStateOf(parameters.audioAlreadyOn)
    var screenAlreadyOn by mutableStateOf(parameters.screenAlreadyOn)

    var audioSetting by mutableStateOf(parameters.audioSetting)
    var videoSetting by mutableStateOf(parameters.videoSetting)
    var screenshareSetting by mutableStateOf(parameters.screenshareSetting)
    var chatSetting by mutableStateOf(parameters.chatSetting)

    var screenRequestState by mutableStateOf(parameters.screenRequestState)
    
    // Loading state for screen share button
    var isScreenShareLoading by mutableStateOf(false)

    fun refresh() {
        consumeSockets = parameters.consumeSocketsState
        rtpCapabilities = parameters.rtpCapabilities
        meetingRoomParams = parameters.meetingRoomParams
        itemPageLimit = parameters.itemPageLimit
        audioOnlyRoom = parameters.audioOnlyRoom
        addForBasic = parameters.addForBasic
        screenPageLimit = parameters.screenPageLimit
        shareScreenStarted = parameters.shareScreenStarted
        shared = parameters.shared
        targetOrientation = parameters.targetOrientation
        targetResolution = parameters.targetResolution
        targetResolutionHost = parameters.targetResolutionHost
        vidCons = parameters.vidCons
        frameRate = parameters.frameRate
        videoAlreadyOn = parameters.videoAlreadyOn
        audioAlreadyOn = parameters.audioAlreadyOn
        screenAlreadyOn = parameters.screenAlreadyOn
        audioSetting = parameters.audioSetting
        videoSetting = parameters.videoSetting
        screenshareSetting = parameters.screenshareSetting
        chatSetting = parameters.chatSetting
        screenRequestState = parameters.screenRequestState
    }

    // Sync functions - called by parameters callbacks to update Compose state
    fun syncAudioAlreadyOn(value: Boolean) {
        if (audioAlreadyOn == value) return
        audioAlreadyOn = value
        notifier()
    }

    fun syncVideoAlreadyOn(value: Boolean) {
        if (videoAlreadyOn == value) return
        videoAlreadyOn = value
        notifier()
    }

    fun syncScreenAlreadyOn(value: Boolean) {
        if (screenAlreadyOn == value) return
        screenAlreadyOn = value
        notifier()
    }

    fun syncShared(value: Boolean) {
        if (shared == value) return
        shared = value
        notifier()
    }

    fun syncShareScreenStarted(value: Boolean) {
        if (shareScreenStarted == value) return
        shareScreenStarted = value
        notifier()
    }

    fun updateTargetOrientation(value: String) {
        targetOrientation = value
        parameters.targetOrientation = value
        notifier()
    }

    fun updateAudioSetting(value: String) {
        if (audioSetting == value) return
        audioSetting = value
        parameters.audioSetting = value
        notifier()
    }

    fun updateVideoSetting(value: String) {
        if (videoSetting == value) return
        videoSetting = value
        parameters.videoSetting = value
        notifier()
    }

    fun updateScreenshareSetting(value: String) {
        if (screenshareSetting == value) return
        screenshareSetting = value
        parameters.screenshareSetting = value
        notifier()
    }

    fun updateChatSetting(value: String) {
        if (chatSetting == value) return
        chatSetting = value
        parameters.chatSetting = value
        notifier()
    }
}

class DisplayState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var controlHeight by mutableStateOf(parameters.controlHeight)
        private set
    var paginationHeightWidth by mutableStateOf(parameters.paginationHeightWidth)
        private set
    var paginationDirection by mutableStateOf(parameters.paginationDirection)
        private set
    var gridSizes by mutableStateOf(parameters.gridSizes)
        private set
    var componentSizes by mutableStateOf(parameters.componentSizes)
        private set
    var isWideScreen by mutableStateOf(parameters.isWideScreen)
        private set
    var isMediumScreen by mutableStateOf(parameters.isMediumScreen)
        private set
    var isSmallScreen by mutableStateOf(parameters.isSmallScreen)
        private set
    var addGrid by mutableStateOf(parameters.addGrid)
        private set
    var addAltGrid by mutableStateOf(parameters.addAltGrid)
        private set
    var gridRows by mutableStateOf(parameters.gridRows)
        private set
    var gridCols by mutableStateOf(parameters.gridCols)
        private set
    var altGridRows by mutableStateOf(parameters.altGridRows)
        private set
    var altGridCols by mutableStateOf(parameters.altGridCols)
        private set
    var numberPages by mutableStateOf(parameters.numberPages)
        private set
    var currentUserPage by mutableStateOf(parameters.currentUserPage)
        private set
    var showMiniView by mutableStateOf(parameters.showMiniView)
        private set
    var doPaginate by mutableStateOf(parameters.doPaginate)
        private set
    var prevDoPaginate by mutableStateOf(parameters.prevDoPaginate)
        private set
    var mainHeightWidth by mutableStateOf(parameters.mainHeightWidth)
        private set
    var prevMainHeightWidth by mutableStateOf(parameters.prevMainHeightWidth)
        private set
    var screenForceFullDisplay by mutableStateOf(parameters.screenForceFullDisplay)
        private set

    // DO NOT initialize from parameters.otherGridStreams - creates circular dependency!
    // Components are populated by updateOtherGridStreams() called from addVideosGrid
    private val mainGridComponentsState = mutableStateListOf<MediaSfuUIComponent>()
    private val miniGridComponentsState = mutableStateListOf<MediaSfuUIComponent>()

    val mainGridComponents: SnapshotStateList<MediaSfuUIComponent>
        get() = mainGridComponentsState

    val miniGridComponents: SnapshotStateList<MediaSfuUIComponent>
        get() = miniGridComponentsState

    val otherGridStreams: List<List<MediaSfuUIComponent>>
        get() = listOf(mainGridComponentsState.toList(), miniGridComponentsState.toList())

    // Display settings properties
    var meetingDisplayType by mutableStateOf(parameters.meetingDisplayType)
    var autoWave by mutableStateOf(parameters.autoWave)
    var forceFullDisplay by mutableStateOf(parameters.forceFullDisplay)
    var meetingVideoOptimized by mutableStateOf(parameters.meetingVideoOptimized)
    var prevMeetingDisplayType by mutableStateOf(parameters.prevMeetingDisplayType)

    fun refresh() {
        controlHeight = parameters.controlHeight
        gridRows = parameters.gridRows
        gridCols = parameters.gridCols
        altGridRows = parameters.altGridRows
        altGridCols = parameters.altGridCols
        numberPages = parameters.numberPages
        currentUserPage = parameters.currentUserPage
        showMiniView = parameters.showMiniView
        doPaginate = parameters.doPaginate
        prevDoPaginate = parameters.prevDoPaginate
        mainHeightWidth = parameters.mainHeightWidth
        prevMainHeightWidth = parameters.prevMainHeightWidth
        screenForceFullDisplay = parameters.screenForceFullDisplay
        meetingDisplayType = parameters.meetingDisplayType
        autoWave = parameters.autoWave
        forceFullDisplay = parameters.forceFullDisplay
        meetingVideoOptimized = parameters.meetingVideoOptimized
        prevMeetingDisplayType = parameters.prevMeetingDisplayType
        isWideScreen = parameters.isWideScreen
        isMediumScreen = parameters.isMediumScreen
        isSmallScreen = parameters.isSmallScreen
        addGrid = parameters.addGrid
        addAltGrid = parameters.addAltGrid
        paginationHeightWidth = parameters.paginationHeightWidth
        paginationDirection = parameters.paginationDirection
        gridSizes = parameters.gridSizes
        componentSizes = parameters.componentSizes
    }

    fun updateControlHeight(value: Double) {
        if (controlHeight == value) return
        controlHeight = value
        parameters.controlHeight = value
        notifier()
    }

    fun updatePaginationHeightWidth(value: Double) {
        if (paginationHeightWidth == value) return
        paginationHeightWidth = value
        parameters.paginationHeightWidth = value
        notifier()
    }

    fun updatePaginationDirection(value: String) {
        if (paginationDirection == value) return
        paginationDirection = value
        parameters.paginationDirection = value
        notifier()
    }

    fun updateGridSizes(value: GridSizes) {
        if (gridSizes == value) return
        gridSizes = value
        parameters.gridSizes = value
        notifier()
    }

    fun updateComponentSizes(value: ComponentSizes) {
        if (componentSizes == value) return
        componentSizes = value
        parameters.componentSizes = value
        notifier()
    }

    fun updateGridRows(value: Int) {
        if (gridRows == value) return
        gridRows = value
        parameters.gridRows = value
        notifier()
    }

    fun updateGridCols(value: Int) {
        if (gridCols == value) return
        gridCols = value
        parameters.gridCols = value
        notifier()
    }

    fun updateAltGridRows(value: Int) {
        if (altGridRows == value) return
        altGridRows = value
        parameters.altGridRows = value
        notifier()
    }

    fun updateAltGridCols(value: Int) {
        if (altGridCols == value) return
        altGridCols = value
        parameters.altGridCols = value
        notifier()
    }

    fun updateNumberPages(value: Int) {
        if (numberPages == value) return
        numberPages = value
        parameters.numberPages = value
        notifier()
    }

    fun updateCurrentUserPage(value: Int) {
        if (currentUserPage == value) return
        currentUserPage = value
        parameters.currentUserPage = value
        notifier()
    }

    fun updateShowMiniView(value: Boolean) {
        if (showMiniView == value) return
        showMiniView = value
        parameters.showMiniView = value
        notifier()
    }

    fun updateDoPaginate(value: Boolean) {
        if (doPaginate == value) return
        doPaginate = value
        parameters.doPaginate = value
        notifier()
    }

    fun updatePrevDoPaginate(value: Boolean) {
        if (prevDoPaginate == value) return
        prevDoPaginate = value
        parameters.prevDoPaginate = value
        notifier()
    }

    fun updateMainHeightWidth(value: Double) {
        if (mainHeightWidth == value) return
        mainHeightWidth = value
        parameters.mainHeightWidth = value
        notifier()
    }

    fun updatePrevMainHeightWidth(value: Double) {
        if (prevMainHeightWidth == value) return
        prevMainHeightWidth = value
        parameters.prevMainHeightWidth = value
        notifier()
    }

    fun updateScreenForceFullDisplay(value: Boolean) {
        if (screenForceFullDisplay == value) return
        screenForceFullDisplay = value
        parameters.screenForceFullDisplay = value
        notifier()
    }

    fun updateMainGridComponents(components: List<MediaSfuUIComponent>) {
        if (mainGridComponentsState == components) return
        mainGridComponentsState.replaceAll(components)
        syncOtherGridStreamsParameter()
        notifier()
    }

    fun updateMiniGridComponents(components: List<MediaSfuUIComponent>) {
        if (miniGridComponentsState == components) return
        miniGridComponentsState.replaceAll(components)
        syncOtherGridStreamsParameter()
        notifier()
    }

    fun updateOtherGridStreams(streams: List<List<MediaSfuUIComponent>>) {
        val main = streams.getOrNull(0).orEmpty()
        val mini = streams.getOrNull(1).orEmpty()
        val changed = mainGridComponentsState != main || miniGridComponentsState != mini
        if (changed) {
            mainGridComponentsState.replaceAll(main)
            miniGridComponentsState.replaceAll(mini)
            syncOtherGridStreamsParameter()
            notifier()
        } else if (parameters.otherGridStreams != streams) {
            syncOtherGridStreamsParameter()
            notifier()
        }
    }

    private fun syncOtherGridStreamsParameter() {
        parameters.otherGridStreams = listOf(
            mainGridComponentsState.toList(),
            miniGridComponentsState.toList()
        )
    }

    private fun SnapshotStateList<MediaSfuUIComponent>.replaceAll(newValues: List<MediaSfuUIComponent>) {
        clear()
        addAll(newValues)
    }

    fun updateMeetingDisplayType(value: String) {
        if (meetingDisplayType == value) {
            return
        }
        meetingDisplayType = value
        parameters.meetingDisplayType = value
        notifier()
    }

    fun updateAutoWave(value: Boolean) {
        if (autoWave == value) return
        autoWave = value
        parameters.autoWave = value
        notifier()
    }

    fun updateForceFullDisplay(value: Boolean) {
        if (forceFullDisplay == value) return
        forceFullDisplay = value
        parameters.forceFullDisplay = value
        notifier()
    }

    fun updateMeetingVideoOptimized(value: Boolean) {
        if (meetingVideoOptimized == value) return
        meetingVideoOptimized = value
        parameters.meetingVideoOptimized = value
        notifier()
    }

    fun setWideScreenFlag(value: Boolean) {
        if (isWideScreen == value) return
        isWideScreen = value
        parameters.isWideScreen = value
        notifier()
    }

    fun setMediumScreenFlag(value: Boolean) {
        if (isMediumScreen == value) return
        isMediumScreen = value
        parameters.isMediumScreen = value
        notifier()
    }

    fun setSmallScreenFlag(value: Boolean) {
        if (isSmallScreen == value) return
        isSmallScreen = value
        parameters.isSmallScreen = value
        notifier()
    }

    fun setAddGridEnabled(value: Boolean) {
        if (addGrid == value) return
        addGrid = value
        parameters.addGrid = value
        notifier()
    }

    fun setAddAltGridEnabled(value: Boolean) {
        if (addAltGrid == value) return
        addAltGrid = value
        parameters.addAltGrid = value
        notifier()
    }

    private fun <T> MutableList<T>.replaceAll(values: List<T>) {
        clear()
        addAll(values)
    }

    private fun <T> MutableList<T>.syncWith(values: List<T>) {
        if (this == values) return
        clear()
        addAll(values)
    }
}

class StreamsState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    private val streamNamesState = mutableStateListOf<Stream>().apply { addAll(parameters.streamNames) }
    private val currentStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.currentStreams) }
    private val paginatedStreamsState = mutableStateListOf<List<Stream>>().apply { addAll(parameters.paginatedStreams) }
    private val nonAlVideoStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.nonAlVideoStreams) }
    private val mixedAlVideoStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.mixedAlVideoStreams) }
    private val nonAlVideoStreamsMutedState = mutableStateListOf<Stream>().apply { addAll(parameters.nonAlVideoStreamsMuted) }
    private val allAudioStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.allAudioStreams) }
    private val remoteScreenStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.remoteScreenStreamState) }
    private val lStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.lStreams) }
    private val chatRefStreamsState = mutableStateListOf<Stream>().apply { addAll(parameters.chatRefStreams) }
    private val audioOnlyStreamsState = mutableStateListOf<MediaSfuUIComponent>().apply {
        addAll(parameters.audioOnlyStreams.mapNotNull { it as? MediaSfuUIComponent })
    }
    
    // Pre-built main grid components from prepopulateUserMedia (matches React's mainGridStream pattern)
    private val mainGridStreamState = mutableStateListOf<MediaSfuUIComponent>()

    var mainScreenPerson by mutableStateOf(parameters.mainScreenPerson)
        private set
    var adminOnMainScreen by mutableStateOf(parameters.adminOnMainScreen)
        private set
    var mainScreenFilled by mutableStateOf(parameters.mainScreenFilled)
        private set
    var shareEnded by mutableStateOf(parameters.shareEnded)
        private set
    var gotAllVids by mutableStateOf(parameters.gotAllVids)
        private set
    var screenStates by mutableStateOf(parameters.screenStates)
        private set
    var prevScreenStates by mutableStateOf(parameters.prevScreenStates)
        private set
    var activeNames by mutableStateOf(parameters.activeNames)
        private set
    var prevActiveNames by mutableStateOf(parameters.prevActiveNames)
        private set
    var pActiveNames by mutableStateOf(parameters.pActiveNames)
        private set
    var dispActiveNames by mutableStateOf(parameters.dispActiveNames)
        private set
    var pDispActiveNames by mutableStateOf(parameters.pDispActiveNames)
        private set

    val streamNames: SnapshotStateList<Stream> get() = streamNamesState
    val currentStreams: SnapshotStateList<Stream> get() = currentStreamsState
    val paginatedStreams: SnapshotStateList<List<Stream>> get() = paginatedStreamsState
    val nonAlVideoStreams: SnapshotStateList<Stream> get() = nonAlVideoStreamsState
    val mixedAlVideoStreams: SnapshotStateList<Stream> get() = mixedAlVideoStreamsState
    val nonAlVideoStreamsMuted: SnapshotStateList<Stream> get() = nonAlVideoStreamsMutedState
    val allAudioStreams: SnapshotStateList<Stream> get() = allAudioStreamsState
    val remoteScreenStreams: SnapshotStateList<Stream> get() = remoteScreenStreamsState
    val lStreams: SnapshotStateList<Stream> get() = lStreamsState
    val chatRefStreams: SnapshotStateList<Stream> get() = chatRefStreamsState
    val audioOnlyStreams: SnapshotStateList<MediaSfuUIComponent> get() = audioOnlyStreamsState
    
    // Pre-built main grid components - matches React's mainGridStream pattern
    val mainGridStream: SnapshotStateList<MediaSfuUIComponent> get() = mainGridStreamState

    fun updateMainScreenPerson(value: String) {
        if (mainScreenPerson == value) return
        mainScreenPerson = value
        parameters.mainScreenPerson = value
        notifier()
    }

    fun updateAdminOnMainScreen(value: Boolean) {
        if (adminOnMainScreen == value) return
        adminOnMainScreen = value
        parameters.adminOnMainScreen = value
        notifier()
    }

    fun updateMainScreenFilled(value: Boolean) {
        if (mainScreenFilled == value) return
        mainScreenFilled = value
        parameters.mainScreenFilled = value
        notifier()
    }

    fun updateShareEnded(value: Boolean) {
        if (shareEnded == value) return
        shareEnded = value
        parameters.shareEnded = value
        notifier()
    }

    fun updateGotAllVids(value: Boolean) {
        if (gotAllVids == value) return
        gotAllVids = value
        parameters.gotAllVids = value
        notifier()
    }

    fun updateStreamNames(newNames: List<Stream>) {
        if (parameters.streamNames == newNames) return
        streamNamesState.replaceAll(newNames)
        parameters.streamNames = newNames
        notifier()
    }

    fun updateCurrentStreams(newStreams: List<Stream>) {
        if (parameters.currentStreams == newStreams) return
        currentStreamsState.replaceAll(newStreams)
        parameters.currentStreams = newStreams
        notifier()
    }

    fun updatePaginatedStreams(newStreams: List<List<Stream>>) {
        if (parameters.paginatedStreams == newStreams) return
        paginatedStreamsState.replaceAll(newStreams)
        parameters.paginatedStreams = newStreams
        notifier()
    }

    fun updateAudioOnlyStreams(newStreams: List<Any>) {
        val filtered = newStreams.mapNotNull { it as? MediaSfuUIComponent }
        audioOnlyStreamsState.syncWith(filtered)
        notifier()
    }

    fun updateNonAlVideoStreams(newStreams: List<Stream>) {
        if (parameters.nonAlVideoStreams == newStreams) return
        nonAlVideoStreamsState.replaceAll(newStreams)
        parameters.nonAlVideoStreams = newStreams
        notifier()
    }

    fun updateMixedAlVideoStreams(newStreams: List<Stream>) {
        if (parameters.mixedAlVideoStreams == newStreams) return
        mixedAlVideoStreamsState.replaceAll(newStreams)
        parameters.mixedAlVideoStreams = newStreams
        notifier()
    }

    fun updateNonAlVideoStreamsMuted(newStreams: List<Stream>) {
        if (parameters.nonAlVideoStreamsMuted == newStreams) return
        nonAlVideoStreamsMutedState.replaceAll(newStreams)
        parameters.nonAlVideoStreamsMuted = newStreams
        notifier()
    }

    fun updateAllAudioStreams(newStreams: List<Stream>) {
        if (parameters.allAudioStreams == newStreams) return
        allAudioStreamsState.replaceAll(newStreams)
        parameters.allAudioStreams = newStreams
        notifier()
    }

    fun updateRemoteScreenStreams(newStreams: List<Stream>) {
        if (parameters.remoteScreenStreamState == newStreams) return
        remoteScreenStreamsState.replaceAll(newStreams)
        parameters.remoteScreenStreamState = newStreams
        notifier()
    }

    fun updateLStreams(newStreams: List<Stream>) {
        if (parameters.lStreams == newStreams) {
            return
        }
        lStreamsState.replaceAll(newStreams)
        parameters.lStreams = newStreams
        notifier()
    }

    fun updateChatRefStreams(newStreams: List<Stream>) {
        if (parameters.chatRefStreams == newStreams) return
        chatRefStreamsState.replaceAll(newStreams)
        parameters.chatRefStreams = newStreams
        notifier()
    }

    /**
     * Updates the pre-built main grid components.
     * Called by prepopulateUserMedia with VideoCard/AudioCard/MiniCard components.
     * This matches React's updateMainGridStream pattern.
     */
    fun updateMainGridStream(components: List<MediaSfuUIComponent>) {
        mainGridStreamState.replaceAll(components)
        @Suppress("UNCHECKED_CAST")
        parameters.mainGridStream = components as List<Any>
        notifier()
    }

    fun updateScreenStates(newStates: List<ScreenState>) {
        if (screenStates == newStates) return
        screenStates = newStates
        parameters.screenStates = newStates
        notifier()
    }

    fun updatePrevScreenStates(newStates: List<ScreenState>) {
        if (prevScreenStates == newStates) return
        prevScreenStates = newStates
        parameters.prevScreenStates = newStates
        notifier()
    }

    fun updateActiveNames(newNames: List<String>) {
        if (activeNames == newNames) return
        activeNames = newNames
        parameters.activeNames = newNames
        notifier()
    }

    fun updatePrevActiveNames(newNames: List<String>) {
        if (prevActiveNames == newNames) return
        prevActiveNames = newNames
        parameters.prevActiveNames = newNames
        notifier()
    }

    fun updatePActiveNames(newNames: List<String>) {
        if (pActiveNames == newNames) return
        pActiveNames = newNames
        parameters.pActiveNames = newNames
        notifier()
    }

    fun updateDispActiveNames(newNames: List<String>) {
        if (dispActiveNames == newNames) return
        dispActiveNames = newNames
        parameters.dispActiveNames = newNames
        notifier()
    }

    fun updatePDispActiveNames(newNames: List<String>) {
        if (pDispActiveNames == newNames) return
        pDispActiveNames = newNames
        parameters.pDispActiveNames = newNames
        notifier()
    }

    private fun <T> MutableList<T>.replaceAll(values: List<T>) {
        clear()
        addAll(values)
    }

    private fun <T> MutableList<T>.syncWith(values: List<T>) {
        if (this == values) return
        clear()
        addAll(values)
    }

    fun refresh() {
        streamNamesState.syncWith(parameters.streamNames)
        currentStreamsState.syncWith(parameters.currentStreams)
        paginatedStreamsState.syncWith(parameters.paginatedStreams)
        nonAlVideoStreamsState.syncWith(parameters.nonAlVideoStreams)
        mixedAlVideoStreamsState.syncWith(parameters.mixedAlVideoStreams)
        nonAlVideoStreamsMutedState.syncWith(parameters.nonAlVideoStreamsMuted)
        allAudioStreamsState.syncWith(parameters.allAudioStreams)
        remoteScreenStreamsState.syncWith(parameters.remoteScreenStreamState)
        lStreamsState.syncWith(parameters.lStreams)
        chatRefStreamsState.syncWith(parameters.chatRefStreams)
        audioOnlyStreamsState.syncWith(parameters.audioOnlyStreams.mapNotNull { it as? MediaSfuUIComponent })

        mainScreenPerson = parameters.mainScreenPerson
        adminOnMainScreen = parameters.adminOnMainScreen
        mainScreenFilled = parameters.mainScreenFilled
        shareEnded = parameters.shareEnded
        gotAllVids = parameters.gotAllVids
        screenStates = parameters.screenStates
        prevScreenStates = parameters.prevScreenStates
        activeNames = parameters.activeNames
        prevActiveNames = parameters.prevActiveNames
        pActiveNames = parameters.pActiveNames
        dispActiveNames = parameters.dispActiveNames
        pDispActiveNames = parameters.pDispActiveNames
    }
}

private class MediasfuPageContentParameters(
    private val backing: MediasfuParameters,
    private val state: MediasfuGenericState
) : PaginationParameters {
    // Create adapter that provides ChangeVidsParameters support for dispStreams
    private val engineParams = EngineReorderStreamsParameters(backing)
    
    override val paginatedStreams: List<List<Any>>
        get() = backing.paginatedStreams.map { streamList -> streamList.map { it as Any } }

    override val updateCurrentUserPage: (Int) -> Unit
        get() = { value -> state.display.updateCurrentUserPage(value) }

    override val dispStreams: suspend (DispStreamsOptions) -> Unit
        get() = { options ->
            // Pass engineParams to dispStreams so it has access to ChangeVidsParameters
            val optionsWithEngine = DispStreamsOptions(
                lStreams = options.lStreams,
                ind = options.ind,
                auto = options.auto,
                chatSkip = options.chatSkip,
                forChatCard = options.forChatCard,
                forChatID = options.forChatID,
                parameters = engineParams,
                breakRoom = options.breakRoom,
                inBreakRoom = options.inBreakRoom
            )
            com.mediasfu.sdk.consumers.dispStreams(optionsWithEngine)
        }

    override fun getUpdatedAllParams(): PaginationParameters = this

    override val lStreams: List<Stream>
        get() = backing.lStreams

    override val participants: List<Participant>
        get() = backing.participants

    override val refParticipants: List<Participant>
        get() = backing.refParticipants

    override val currentUserPage: Int
        get() = backing.currentUserPage

    override val hostLabel: String
        get() = backing.hostLabel

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val shared: Boolean
        get() = backing.shared

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val eventType: EventType
        get() = backing.eventType

    override val islevel: String
        get() = backing.islevel

    override val member: String
        get() = backing.member

    override val updateLStreams: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateLStreams(value) }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value ->
            if (backing.updateMainWindow != value) {
                backing.updateMainWindow = value
                state.propagateParameterChanges()
            }
        }

    // PaginationParameters - breakout room properties
    override val mainRoomsLength: Int
        get() = backing.mainRoomsLength

    override val memberRoom: Int
        get() = backing.memberRoom

    override val breakOutRoomStarted: Boolean
        get() = state.breakout.breakOutRoomStarted

    override val breakOutRoomEnded: Boolean
        get() = state.breakout.breakOutRoomEnded

    override val breakoutRoomsCount: Int
        get() = state.breakout.breakoutRooms.size

    override fun getBreakoutRoomNames(roomIndex: Int): List<String> {
        val rooms = state.breakout.breakoutRooms
        return if (roomIndex in rooms.indices) {
            rooms[roomIndex].map { it.name }
        } else {
            emptyList()
        }
    }

    override val hostNewRoom: Int
        get() = backing.hostNewRoom

    override val roomName: String
        get() = backing.roomName

    override val showAlert: ((message: String, type: String, duration: Int) -> Unit)?
        get() = { message, type, duration ->
            state.showAlert(message, type, duration)
        }

    override val socket: SocketManager?
        get() = backing.socket
}

class RecordingState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var recordingMediaOptions by mutableStateOf(parameters.recordingMediaOptions)
    var recordingAudioOptions by mutableStateOf(parameters.recordingAudioOptions)
    var recordingVideoOptions by mutableStateOf(parameters.recordingVideoOptions)
    var recordingVideoType by mutableStateOf(parameters.recordingVideoType)
    var recordingVideoOptimized by mutableStateOf(parameters.recordingVideoOptimized)
    var recordingDisplayType by mutableStateOf(parameters.recordingDisplayType)
    var recordingAddHLS by mutableStateOf(parameters.recordingAddHLS)
    var recordState by mutableStateOf(parameters.recordState)
    var showRecordButtons by mutableStateOf(parameters.showRecordButtons)
    var recordingProgressTime by mutableStateOf(parameters.recordingProgressTime)

    var recordStarted by mutableStateOf(parameters.recordStarted)
    var recordPaused by mutableStateOf(parameters.recordPaused)
    var recordResumed by mutableStateOf(parameters.recordResumed)
    var recordStopped by mutableStateOf(parameters.recordStopped)
    var recordingAudioSupport by mutableStateOf(parameters.recordingAudioSupport)
    var recordingVideoSupport by mutableStateOf(parameters.recordingVideoSupport)
    var recordingAllParticipantsSupport by mutableStateOf(parameters.recordingAllParticipantsSupport)
    var recordingVideoParticipantsSupport by mutableStateOf(parameters.recordingVideoParticipantsSupport)
    var canRecord by mutableStateOf(parameters.canRecord)
    var clearedToRecord by mutableStateOf(parameters.clearedToRecord)

    // Additional recording properties
    var recordingBackgroundColor by mutableStateOf(parameters.recordingBackgroundColor)
    var recordingNameTagsColor by mutableStateOf(parameters.recordingNameTagsColor)
    var recordingOrientationVideo by mutableStateOf(parameters.recordingOrientationVideo)
    var recordingNameTags by mutableStateOf(parameters.recordingNameTags)
    var recordingAddText by mutableStateOf(parameters.recordingAddText)
    var recordingCustomText by mutableStateOf(parameters.recordingCustomText)
    var recordingCustomTextPosition by mutableStateOf(parameters.recordingCustomTextPosition)
    var recordingCustomTextColor by mutableStateOf(parameters.recordingCustomTextColor)

    fun updateRecordingMediaOptions(value: String) {
        if (recordingMediaOptions == value) return
        recordingMediaOptions = value
        parameters.updateRecordingMediaOptions(value)
        notifier()
    }

    fun updateRecordingAudioOptions(value: String) {
        if (recordingAudioOptions == value) return
        recordingAudioOptions = value
        parameters.updateRecordingAudioOptions(value)
        notifier()
    }

    fun updateRecordingVideoOptions(value: String) {
        if (recordingVideoOptions == value) return
        recordingVideoOptions = value
        parameters.updateRecordingVideoOptions(value)
        notifier()
    }

    fun updateRecordingVideoType(value: String) {
        if (recordingVideoType == value) return
        recordingVideoType = value
        parameters.updateRecordingVideoType(value)
        notifier()
    }

    fun updateRecordingVideoOptimized(value: Boolean) {
        if (recordingVideoOptimized == value) return
        recordingVideoOptimized = value
        parameters.updateRecordingVideoOptimized(value)
        notifier()
    }

    fun updateRecordingDisplayType(value: String) {
        if (recordingDisplayType == value) return
        recordingDisplayType = value
        parameters.updateRecordingDisplayType(value)
        notifier()
    }

    fun updateRecordingAddHls(value: Boolean) {
        if (recordingAddHLS == value) return
        recordingAddHLS = value
        parameters.updateRecordingAddHls(value)
        notifier()
    }

    fun updateRecordingBackgroundColor(value: String) {
        if (recordingBackgroundColor == value) return
        recordingBackgroundColor = value
        parameters.recordingBackgroundColor = value
        notifier()
    }

    fun updateRecordingNameTagsColor(value: String) {
        if (recordingNameTagsColor == value) return
        recordingNameTagsColor = value
        parameters.recordingNameTagsColor = value
        notifier()
    }

    fun updateRecordingOrientationVideo(value: String) {
        if (recordingOrientationVideo == value) return
        recordingOrientationVideo = value
        parameters.recordingOrientationVideo = value
        notifier()
    }

    fun updateRecordingNameTags(value: Boolean) {
        if (recordingNameTags == value) return
        recordingNameTags = value
        parameters.recordingNameTags = value
        notifier()
    }

    fun updateRecordingAddText(value: Boolean) {
        if (recordingAddText == value) return
        recordingAddText = value
        parameters.recordingAddText = value
        notifier()
    }

    fun updateRecordingProgressTime(value: String) {
        if (recordingProgressTime == value) return
        recordingProgressTime = value
        parameters.updateRecordingProgressTime(value)
        notifier()
    }

    fun updateRecordingCustomTextPosition(value: String) {
        if (recordingCustomTextPosition == value) return
        recordingCustomTextPosition = value
        parameters.recordingCustomTextPosition = value
        notifier()
    }

    fun updateRecordingCustomTextColor(value: String) {
        if (recordingCustomTextColor == value) return
        recordingCustomTextColor = value
        parameters.recordingCustomTextColor = value
        notifier()
    }

    fun updateRecordingAddHLS(value: Boolean) {
        updateRecordingAddHls(value)
    }

    fun updateShowRecordButtons(value: Boolean) {
        if (showRecordButtons == value) return
        showRecordButtons = value
        parameters.updateShowRecordButtons(value)
        notifier()
    }

    fun updateRecordingAudioSupport(value: Boolean) {
        if (recordingAudioSupport == value) return
        recordingAudioSupport = value
        parameters.updateRecordingAudioSupport(value)
        notifier()
    }

    fun updateRecordingVideoSupport(value: Boolean) {
        if (recordingVideoSupport == value) return
        recordingVideoSupport = value
        parameters.updateRecordingVideoSupport(value)
        notifier()
    }

    fun updateRecordingAllParticipantsSupport(value: Boolean) {
        if (recordingAllParticipantsSupport == value) return
        recordingAllParticipantsSupport = value
        parameters.updateRecordingAllParticipantsSupport(value)
        notifier()
    }

    fun updateRecordingVideoParticipantsSupport(value: Boolean) {
        if (recordingVideoParticipantsSupport == value) return
        recordingVideoParticipantsSupport = value
        parameters.updateRecordingVideoParticipantsSupport(value)
        notifier()
    }

    fun updateCanRecord(value: Boolean) {
        if (canRecord == value) return
        canRecord = value
        parameters.updateCanRecord(value)
        notifier()
    }

    fun updateClearedToRecord(value: Boolean) {
        if (clearedToRecord == value) return
        clearedToRecord = value
        parameters.updateClearedToRecord(value)
        notifier()
    }

    fun toggleRecording() {
        val shouldStart = !recordStarted
        updateRecordStarted(shouldStart)
        if (shouldStart) {
            updateRecordStopped(false)
            updateRecordPaused(false)
            updateRecordResumed(false)
        } else {
            updateRecordPaused(false)
            updateRecordResumed(false)
            updateRecordStopped(true)
        }
    }

    fun updateRecordStarted(value: Boolean) {
        if (recordStarted == value) return
        recordStarted = value
        parameters.updateRecordStarted(value)
        refreshRecordState()
    }

    fun updateRecordPaused(value: Boolean) {
        if (recordPaused == value) return
        recordPaused = value
        parameters.updateRecordPaused(value)
        refreshRecordState()
    }

    fun updateRecordStopped(value: Boolean) {
        if (recordStopped == value) return
        recordStopped = value
        parameters.updateRecordStopped(value)
        refreshRecordState()
    }

    fun updateRecordResumed(value: Boolean) {
        if (recordResumed == value) return
        recordResumed = value
        parameters.updateRecordResumed(value)
        refreshRecordState()
    }

    fun updateRecordingCustomText(value: String) {
        if (recordingCustomText == value) return
        recordingCustomText = value
        parameters.recordingCustomText = value
        notifier()
    }

    fun refresh() {
        recordingMediaOptions = parameters.recordingMediaOptions
        recordingAudioOptions = parameters.recordingAudioOptions
        recordingVideoOptions = parameters.recordingVideoOptions
        recordingVideoType = parameters.recordingVideoType
        recordingVideoOptimized = parameters.recordingVideoOptimized
        recordingDisplayType = parameters.recordingDisplayType
        recordingAddHLS = parameters.recordingAddHLS
        recordState = parameters.recordState
        showRecordButtons = parameters.showRecordButtons
        recordingProgressTime = parameters.recordingProgressTime
        recordStarted = parameters.recordStarted
        recordPaused = parameters.recordPaused
        recordResumed = parameters.recordResumed
        recordStopped = parameters.recordStopped
        recordingAudioSupport = parameters.recordingAudioSupport
        recordingVideoSupport = parameters.recordingVideoSupport
        recordingAllParticipantsSupport = parameters.recordingAllParticipantsSupport
        recordingVideoParticipantsSupport = parameters.recordingVideoParticipantsSupport
        canRecord = parameters.canRecord
        clearedToRecord = parameters.clearedToRecord
        recordingBackgroundColor = parameters.recordingBackgroundColor
        recordingNameTagsColor = parameters.recordingNameTagsColor
        recordingOrientationVideo = parameters.recordingOrientationVideo
        recordingNameTags = parameters.recordingNameTags
        recordingAddText = parameters.recordingAddText
        recordingCustomText = parameters.recordingCustomText
        recordingCustomTextPosition = parameters.recordingCustomTextPosition
        recordingCustomTextColor = parameters.recordingCustomTextColor
    }

    private fun refreshRecordState() {
        val newState = when {
            recordStarted && !recordStopped && recordPaused -> "yellow"
            recordStarted && !recordStopped -> "red"
            else -> "green"
        }
        if (recordState != newState) {
            recordState = newState
        }
        parameters.updateRecordState(newState)
        notifier()
    }
}

class MeetingState(
    private val scope: CoroutineScope,
    private val parameters: MediasfuParameters,
    private val notifier: () -> Unit
) {
    var progressTime by mutableStateOf(formatElapsed(parameters.meetingElapsedTime))
        private set
    var isVisible by mutableStateOf(parameters.progressTimerVisible)
        private set

    private var timerJob: Job? = null

    init {
        if (parameters.isTimerRunning) {
            startTimer()
        } else {
            progressTime = formatElapsed(parameters.meetingElapsedTime)
        }
    }

    val isRunning: Boolean
        get() = timerJob?.isActive == true

    fun startTimer() {
        if (timerJob?.isActive == true) return
        timerJob = scope.launch {
            parameters.isTimerRunning = true
            var elapsedSeconds = parameters.meetingElapsedTime
            while (isActive) {
                progressTime = formatElapsed(elapsedSeconds)
                parameters.meetingProgressTime = progressTime
                parameters.meetingElapsedTime = elapsedSeconds
                notifier()
                delay(1000)
                elapsedSeconds += 1
            }
        }
        show()
    }

    fun pauseTimer() {
        if (timerJob?.isActive != true) return
        timerJob?.cancel()
        timerJob = null
        parameters.isTimerRunning = false
        notifier()
    }

    fun stopTimer(reset: Boolean = false) {
        timerJob?.cancel()
        timerJob = null
        parameters.isTimerRunning = false
        if (reset) {
            parameters.meetingElapsedTime = 0
            progressTime = formatElapsed(0)
            parameters.meetingProgressTime = progressTime
        }
        notifier()
    }

    fun resumeTimer() {
        if (timerJob?.isActive == true) return
        startTimer()
    }

    fun hide() {
        if (!isVisible) return
        isVisible = false
        parameters.progressTimerVisible = false
        notifier()
    }

    fun show() {
        if (isVisible) return
        isVisible = true
        parameters.progressTimerVisible = true
        notifier()
    }

    fun toggleVisibility() {
        if (isVisible) hide() else show()
    }

    fun refresh() {
        if (parameters.isTimerRunning && timerJob?.isActive != true) {
            startTimer()
        } else if (!parameters.isTimerRunning && timerJob?.isActive == true) {
            pauseTimer()
        }
        if (!parameters.isTimerRunning) {
             progressTime = formatElapsed(parameters.meetingElapsedTime)
        }
        isVisible = parameters.progressTimerVisible
    }

    private fun formatElapsed(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
}

class MessagingState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    val messages = mutableStateListOf<Message>().apply { addAll(parameters.messages) }
    var startDirectMessage by mutableStateOf(parameters.startDirectMessage)
    var directMessageDetails by mutableStateOf(parameters.directMessageDetails)
    var showMessagesBadge by mutableStateOf(parameters.showMessagesBadge)

    fun append(message: Message) {
        messages.add(message)
        parameters.messages = messages.toList()
        showMessagesBadge = true
        parameters.showMessagesBadge = true
        notifier()
    }

    fun clearBadge() {
        if (!showMessagesBadge) return
        showMessagesBadge = false
        parameters.showMessagesBadge = false
        notifier()
    }

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        parameters.messages = newMessages
        notifier()
    }

    fun updateShowMessagesBadge(show: Boolean) {
        if (showMessagesBadge == show) return
        showMessagesBadge = show
        parameters.showMessagesBadge = show
        notifier()
    }

    fun refresh() {
        messages.clear()
        messages.addAll(parameters.messages)
        startDirectMessage = parameters.startDirectMessage
        directMessageDetails = parameters.directMessageDetails
        showMessagesBadge = parameters.showMessagesBadge
    }
}

class WaitingRoomState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var filter by mutableStateOf(parameters.waitingRoomFilter)
    val waitingRoomList = mutableStateListOf<WaitingRoomParticipant>().apply {
        addAll(parameters.waitingRoomList)
    }
    val filteredWaitingRoomList = mutableStateListOf<WaitingRoomParticipant>().apply {
        addAll(parameters.filteredWaitingRoomList.ifEmpty { parameters.waitingRoomList })
    }
    var counter by mutableStateOf(parameters.waitingRoomCounter)

    fun updateList(list: List<WaitingRoomParticipant>) {
        waitingRoomList.clear()
        waitingRoomList.addAll(list)
        parameters.waitingRoomList = list
        updateFilteredList()
    }

    fun updateFilteredList() {
        val newList = if (filter.isBlank()) waitingRoomList else waitingRoomList.filter {
            it.name.contains(filter, ignoreCase = true)
        }
        filteredWaitingRoomList.clear()
        filteredWaitingRoomList.addAll(newList)
        counter = newList.size
        parameters.filteredWaitingRoomList = newList
        parameters.waitingRoomCounter = counter
        notifier()
    }

    fun refresh() {
        filter = parameters.waitingRoomFilter
        waitingRoomList.clear()
        waitingRoomList.addAll(parameters.waitingRoomList)
        updateFilteredList()
    }
}

class RequestsState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    var filter by mutableStateOf(parameters.requestFilter)
    val requests = mutableStateListOf<Request>().apply { addAll(parameters.requestList) }
    val filteredRequests = mutableStateListOf<Request>().apply {
        addAll(parameters.filteredRequestList.ifEmpty { parameters.requestList })
    }
    var counter by mutableStateOf(parameters.requestCounter)
    var totalPending by mutableStateOf(parameters.totalReqWait)

    fun updateList(list: List<Request>) {
        requests.clear()
        requests.addAll(list)
        parameters.requestList = list
        updateFilteredList()
    }

    fun updateFilteredList() {
        val newList = if (filter.isBlank()) requests else requests.filter {
            it.name?.contains(filter, ignoreCase = true) == true
        }
        filteredRequests.clear()
        filteredRequests.addAll(newList)
        counter = newList.size
        parameters.filteredRequestList = newList
        parameters.requestCounter = counter
        notifier()
    }

    fun updateTotalPending(value: Int) {
        if (totalPending == value) return
        totalPending = value
        parameters.totalReqWait = value
        notifier()
    }

    fun refresh() {
        val newTotal = parameters.totalReqWait
        if (totalPending != newTotal) {
            totalPending = newTotal
        }
    }

    // Action update methods
    fun updateMicAction(action: Boolean) {
        parameters.micAction = action
        notifier()
    }

    fun updateVideoAction(action: Boolean) {
        parameters.videoAction = action
        notifier()
    }

    fun updateScreenAction(action: Boolean) {
        parameters.screenAction = action
        notifier()
    }

    fun updateChatAction(action: Boolean) {
        parameters.chatAction = action
        notifier()
    }

    // Request state update methods
    fun updateAudioRequestState(state: String) {
        parameters.audioRequestState = state
        notifier()
    }

    fun updateVideoRequestState(state: String) {
        parameters.videoRequestState = state
        notifier()
    }

    fun updateScreenRequestState(state: String) {
        parameters.screenRequestState = state
        notifier()
    }

    fun updateChatRequestState(state: String) {
        parameters.chatRequestState = state
        notifier()
    }

    // Request time update methods
    fun updateAudioRequestTime(time: Long?) {
        parameters.audioRequestTime = time
        notifier()
    }

    fun updateVideoRequestTime(time: Long?) {
        parameters.videoRequestTime = time
        notifier()
    }

    fun updateScreenRequestTime(time: Long?) {
        parameters.screenRequestTime = time
        notifier()
    }

    fun updateChatRequestTime(time: Long?) {
        parameters.chatRequestTime = time
        notifier()
    }
}

class PollsState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    val polls = mutableStateListOf<Poll>().apply { addAll(parameters.polls) }
    var activePoll by mutableStateOf(parameters.poll)
        private set
    var isPollModalVisible by mutableStateOf(parameters.isPollModalVisible)
        private set

    // Alias for activePoll to match Flutter/React naming
    val poll: Poll? get() = activePoll

    fun updatePolls(newPolls: List<Poll>) {
        if (polls == newPolls) return
        polls.syncWith(newPolls)
        parameters.polls = newPolls
        notifier()
    }

    fun updatePoll(poll: Poll) {
        if (activePoll == poll) return
        activePoll = poll
        parameters.poll = poll
        notifier()
    }

    fun clearActivePoll() {
        if (activePoll == null) return
        activePoll = null
        parameters.poll = null
        notifier()
    }

    fun setPollModalVisibility(visible: Boolean) {
        if (isPollModalVisible == visible) return
        isPollModalVisible = visible
        parameters.isPollModalVisible = visible
        notifier()
    }

    fun updateIsPollModalVisible(visible: Boolean) {
        setPollModalVisibility(visible)
    }

    fun refresh() {
        polls.syncWith(parameters.polls)
        if (activePoll != parameters.poll) {
            activePoll = parameters.poll
        }
        if (isPollModalVisible != parameters.isPollModalVisible) {
            isPollModalVisible = parameters.isPollModalVisible
        }
    }

    private fun MutableList<Poll>.syncWith(values: List<Poll>) {
        if (this == values) return
        clear()
        addAll(values)
    }
}

class BreakoutState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    val breakoutRooms = mutableStateListOf<List<ModelBreakoutParticipant>>().apply {
        addAll(parameters.breakoutRooms)
    }
    var canStartBreakout by mutableStateOf(parameters.canStartBreakout)
    var breakOutRoomStarted by mutableStateOf(parameters.breakOutRoomStarted)
    var breakOutRoomEnded by mutableStateOf(parameters.breakOutRoomEnded)
    var currentRoomIndex by mutableStateOf(parameters.currentRoomIndex)
    var newParticipantAction by mutableStateOf(parameters.newParticipantAction)

    fun updateRooms(rooms: List<List<ModelBreakoutParticipant>>) {
        breakoutRooms.clear()
        breakoutRooms.addAll(rooms)
        parameters.breakoutRooms = rooms
        notifier()
    }

    fun updateBreakoutRooms(rooms: List<List<ModelBreakoutParticipant>>) {
        updateRooms(rooms)
    }

    fun updateBreakOutRoomStarted(value: Boolean) {
        if (breakOutRoomStarted == value) return
        breakOutRoomStarted = value
        parameters.breakOutRoomStarted = value
        notifier()
    }

    fun updateBreakOutRoomEnded(value: Boolean) {
        if (breakOutRoomEnded == value) return
        breakOutRoomEnded = value
        parameters.breakOutRoomEnded = value
        notifier()
    }

    fun updateCurrentRoomIndex(index: Int) {
        if (currentRoomIndex == index) return
        currentRoomIndex = index
        parameters.currentRoomIndex = index
        notifier()
    }

    fun updateCanStartBreakout(value: Boolean) {
        if (canStartBreakout == value) return
        canStartBreakout = value
        parameters.canStartBreakout = value
        notifier()
    }

    fun updateNewParticipantAction(action: String) {
        if (newParticipantAction == action) return
        newParticipantAction = action
        parameters.newParticipantAction = action
        notifier()
    }

    fun refresh() {
        breakoutRooms.syncWith(parameters.breakoutRooms)
        canStartBreakout = parameters.canStartBreakout
        breakOutRoomStarted = parameters.breakOutRoomStarted
        breakOutRoomEnded = parameters.breakOutRoomEnded
        currentRoomIndex = parameters.currentRoomIndex
        newParticipantAction = parameters.newParticipantAction
    }

    private fun <T> MutableList<T>.syncWith(values: List<T>) {
        if (this == values) return
        clear()
        addAll(values)
    }
}

class WhiteboardState(private val parameters: MediasfuParameters, private val notifier: () -> Unit) {
    val users = mutableStateListOf<com.mediasfu.sdk.model.WhiteboardUser>().apply {
        addAll(parameters.whiteboardUsers)
    }
    var currentIndex by mutableStateOf(parameters.currentWhiteboardIndex)
        private set
    var canStartWhiteboard by mutableStateOf(parameters.canStartWhiteboard)
        private set
    var whiteboardStarted by mutableStateOf(parameters.whiteboardStarted)
        private set
    var whiteboardEnded by mutableStateOf(parameters.whiteboardEnded)
        private set
    var limit by mutableStateOf(parameters.whiteboardLimit)
        private set
    val shapes = mutableStateListOf<com.mediasfu.sdk.model.WhiteboardShape>().apply {
        addAll(parameters.shapes)
    }
    var useImageBackground by mutableStateOf(parameters.useImageBackground)
        private set
    val redoStack = mutableStateListOf<com.mediasfu.sdk.model.WhiteboardShape>().apply {
        addAll(parameters.redoStack)
    }
    val undoStack = mutableStateListOf<String>().apply {
        addAll(parameters.undoStack)
    }
    /** Undo stack containing lists of shape states for socket handler sync */
    val undoStackLists = mutableStateListOf<List<com.mediasfu.sdk.model.WhiteboardShape>>()
    /** Redo stack containing lists of shape states for socket handler sync */
    val redoStackLists = mutableStateListOf<List<com.mediasfu.sdk.model.WhiteboardShape>>()
    var canvasWhiteboard by mutableStateOf(parameters.canvasWhiteboard)
        private set

    fun updateUsers(value: List<com.mediasfu.sdk.model.WhiteboardUser>) {
        users.clear()
        users.addAll(value)
        parameters.whiteboardUsers = value
        notifier()
    }

    fun updateCurrentIndex(value: Int?) {
        if (currentIndex == value) return
        currentIndex = value
        parameters.currentWhiteboardIndex = value
        notifier()
    }

    fun updateCanStart(value: Boolean) {
        if (canStartWhiteboard == value) return
        canStartWhiteboard = value
        parameters.canStartWhiteboard = value
        notifier()
    }

    fun updateStarted(value: Boolean) {
        if (whiteboardStarted == value) return
        whiteboardStarted = value
        parameters.whiteboardStarted = value
        notifier()
    }

    fun updateEnded(value: Boolean) {
        if (whiteboardEnded == value) return
        whiteboardEnded = value
        parameters.whiteboardEnded = value
        notifier()
    }

    fun updateLimit(value: Int) {
        if (limit == value) return
        limit = value
        parameters.whiteboardLimit = value
        notifier()
    }

    fun updateUseImageBackground(value: Boolean) {
        if (useImageBackground == value) return
        useImageBackground = value
        parameters.useImageBackground = value
        notifier()
    }

    fun updateShapes(value: List<com.mediasfu.sdk.model.WhiteboardShape>) {
        shapes.clear()
        shapes.addAll(value)
        parameters.shapes = value
        notifier()
    }

    fun updateRedoStack(value: List<com.mediasfu.sdk.model.WhiteboardShape>) {
        redoStack.clear()
        redoStack.addAll(value)
        parameters.redoStack = value
        notifier()
    }

    fun updateUndoStack(value: List<String>) {
        undoStack.clear()
        undoStack.addAll(value)
        parameters.undoStack = value
        notifier()
    }

    fun refresh() {
        users.syncWith(parameters.whiteboardUsers)
        currentIndex = parameters.currentWhiteboardIndex
        canStartWhiteboard = parameters.canStartWhiteboard
        whiteboardStarted = parameters.whiteboardStarted
        whiteboardEnded = parameters.whiteboardEnded
        limit = parameters.whiteboardLimit
        shapes.syncWith(parameters.shapes)
        useImageBackground = parameters.useImageBackground
        redoStack.syncWith(parameters.redoStack)
        undoStack.syncWith(parameters.undoStack)
        canvasWhiteboard = parameters.canvasWhiteboard
    }

    private fun <T> MutableList<T>.syncWith(values: List<T>) {
        if (this == values) return
        clear()
        addAll(values)
    }
}

class ModalState(
    private val parameters: MediasfuParameters, 
    private val notifier: () -> Unit,
    private val coroutineScope: CoroutineScope
) {
    var isMenuVisible by mutableStateOf(parameters.isMenuModalVisible)
    var isRecordingVisible by mutableStateOf(parameters.isRecordingModalVisible)
    var isSettingsVisible by mutableStateOf(parameters.isSettingsModalVisible)
    var isRequestsVisible by mutableStateOf(parameters.isRequestsModalVisible)
    var isWaitingVisible by mutableStateOf(parameters.isWaitingModalVisible)
    var isCoHostVisible by mutableStateOf(parameters.isCoHostModalVisible)
    var isMediaSettingsVisible by mutableStateOf(parameters.isMediaSettingsModalVisible)
    var isDisplaySettingsVisible by mutableStateOf(parameters.isDisplaySettingsModalVisible)
    var isParticipantsVisible by mutableStateOf(parameters.isParticipantsModalVisible)
    var isMessagesVisible by mutableStateOf(parameters.isMessagesModalVisible)
    var isConfirmExitVisible by mutableStateOf(parameters.isConfirmExitModalVisible)
    var isConfirmHereVisible by mutableStateOf(parameters.isConfirmHereModalVisible)
    var isShareEventVisible by mutableStateOf(parameters.isShareEventModalVisible)
    var isLoadingVisible by mutableStateOf(parameters.isLoadingModalVisible)
    var isScreenboardVisible by mutableStateOf(parameters.isScreenboardModalVisible)
    var isWhiteboardVisible by mutableStateOf(parameters.isWhiteboardModalVisible)
    var isConfigureWhiteboardVisible by mutableStateOf(parameters.isConfigureWhiteboardModalVisible)
    var isBreakoutRoomsVisible by mutableStateOf(parameters.isBreakoutRoomsModalVisible)
    var isBackgroundVisible by mutableStateOf(parameters.isBackgroundModalVisible)
    
    /**
     * Callback invoked when a modal should close the unified sidebar.
     * Maps from SidebarContent to call navigateBack when appropriate.
     * Set by the composable layer when UnifiedModalState is available.
     */
    var onSidebarClose: ((SidebarContent) -> Unit)? = null

    fun closeAll() {
        isMenuVisible = false
        isRecordingVisible = false
        isSettingsVisible = false
        isRequestsVisible = false
        isWaitingVisible = false
        isCoHostVisible = false
        isMediaSettingsVisible = false
        isDisplaySettingsVisible = false
        isParticipantsVisible = false
        isMessagesVisible = false
        isConfirmExitVisible = false
        isConfirmHereVisible = false
        isShareEventVisible = false
        isLoadingVisible = false
        isScreenboardVisible = false
        isWhiteboardVisible = false
        isConfigureWhiteboardVisible = false
        isBackgroundVisible = false
        parameters.isMenuModalVisible = false
        parameters.isRecordingModalVisible = false
        parameters.isSettingsModalVisible = false
        parameters.isRequestsModalVisible = false
        parameters.isWaitingModalVisible = false
        parameters.isCoHostModalVisible = false
        parameters.isMediaSettingsModalVisible = false
        parameters.isDisplaySettingsModalVisible = false
        parameters.isParticipantsModalVisible = false
        parameters.isMessagesModalVisible = false
        parameters.isConfirmExitModalVisible = false
        parameters.isConfirmHereModalVisible = false
        parameters.isShareEventModalVisible = false
        parameters.isLoadingModalVisible = false
        parameters.isScreenboardModalVisible = false
        parameters.isWhiteboardModalVisible = false
        parameters.isConfigureWhiteboardModalVisible = false
        parameters.isBreakoutRoomsModalVisible = false
        parameters.isBackgroundModalVisible = false
        notifier()
    }

    fun setMenuVisibility(visible: Boolean) {
        if (isMenuVisible == visible) return
        isMenuVisible = visible
        parameters.isMenuModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Menu)
        notifier()
    }

    fun setRecordingVisibility(visible: Boolean) {
        if (isRecordingVisible == visible) return
        isRecordingVisible = visible
        parameters.isRecordingModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Recording)
        notifier()
    }

    fun setLoadingVisibility(visible: Boolean) {
        if (isLoadingVisible == visible) return
        isLoadingVisible = visible
        parameters.isLoadingModalVisible = visible
        notifier()
    }

    fun setRequestsVisibility(visible: Boolean) {
        if (isRequestsVisible == visible) return
        isRequestsVisible = visible
        parameters.isRequestsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Requests)
        notifier()
    }

    fun showRequests() {
        if (isRequestsVisible) return
        closeAll()
        setRequestsVisibility(true)
    }

    fun setWaitingVisibility(visible: Boolean) {
        if (isWaitingVisible == visible) return
        isWaitingVisible = visible
        parameters.isWaitingModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Waiting)
        notifier()
    }

    fun showWaiting() {
        if (isWaitingVisible) return
        closeAll()
        setWaitingVisibility(true)
    }

    fun setParticipantsVisibility(visible: Boolean) {
        if (isParticipantsVisible == visible) return
        isParticipantsVisible = visible
        parameters.isParticipantsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Participants)
        notifier()
    }

    fun showParticipants() {
        closeAll()
        setParticipantsVisibility(true)
    }

    fun setMessagesVisibility(visible: Boolean) {
        if (isMessagesVisible == visible) return
        isMessagesVisible = visible
        parameters.isMessagesModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Messages)
        notifier()
    }

    fun showMessages() {
        closeAll()
        setMessagesVisibility(true)
    }

    fun showSettings() {
        closeAll()
        setSettingsVisibility(true)
    }

    fun setSettingsVisibility(visible: Boolean) {
        if (isSettingsVisible == visible) return
        isSettingsVisible = visible
        parameters.isSettingsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Settings)
        notifier()
    }

    fun showShareEvent() {
        closeAll()
        setShareEventVisibility(true)
    }

    fun setShareEventVisibility(visible: Boolean) {
        if (isShareEventVisible == visible) return
        isShareEventVisible = visible
        parameters.isShareEventModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Share)
        notifier()
    }

    fun showConfirmExit() {
        closeAll()
        setConfirmExitVisibility(true)
    }

    fun setConfirmExitVisibility(visible: Boolean) {
        if (isConfirmExitVisible == visible) return
        isConfirmExitVisible = visible
        parameters.isConfirmExitModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.ConfirmExit)
        notifier()
    }

    fun setConfirmHereVisibility(visible: Boolean) {
        if (isConfirmHereVisible == visible) return
        isConfirmHereVisible = visible
        parameters.isConfirmHereModalVisible = visible
        notifier()
    }

    fun updateIsConfirmHereModalVisible(visible: Boolean) {
        setConfirmHereVisibility(visible)
    }

    fun showConfirmHere() {
        closeAll()
        setConfirmHereVisibility(true)
    }

    fun setScreenboardVisibility(visible: Boolean) {
        if (isScreenboardVisible == visible) return
        isScreenboardVisible = visible
        parameters.isScreenboardModalVisible = visible
        notifier()
    }

    fun setWhiteboardVisibility(visible: Boolean) {
        if (isWhiteboardVisible == visible) return
        isWhiteboardVisible = visible
        parameters.isWhiteboardModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Whiteboard)
        notifier()
    }

    fun setConfigureWhiteboardVisibility(visible: Boolean) {
        if (isConfigureWhiteboardVisible == visible) return
        isConfigureWhiteboardVisible = visible
        parameters.isConfigureWhiteboardModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Whiteboard)
        notifier()
    }

    fun updateIsPollModalVisible(visible: Boolean) {
        parameters.isPollModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Polls)
        notifier()
    }

    fun showRecording() {
        closeAll()
        setRecordingVisibility(true)
    }

    fun showWhiteboard() {
        closeAll()
        setWhiteboardVisibility(true)
    }

    fun showConfigureWhiteboard() {
        closeAll()
        setConfigureWhiteboardVisibility(true)
    }

    fun showCoHost() {
        closeAll()
        isCoHostVisible = true
        parameters.isCoHostModalVisible = true
        notifier()
    }

    fun showMediaSettings() {
        coroutineScope.launch {
            try {
                // Close other modals first
                closeAll()
                
                // Launch media settings (requests permissions and enumerates devices)
                // Note: launchMediaSettings handles modal visibility toggle at the end
                launchMediaSettings(
                    LaunchMediaSettingsOptions(
                        updateIsMediaSettingsModalVisible = { value ->
                            isMediaSettingsVisible = value
                            parameters.isMediaSettingsModalVisible = value
                            notifier()
                        },
                        isMediaSettingsModalVisible = isMediaSettingsVisible,
                        // Convert from model.MediaDeviceInfo to webrtc.MediaDeviceInfo
                        audioInputs = parameters.audioInputs.map { modelDevice ->
                            com.mediasfu.sdk.webrtc.MediaDeviceInfo(
                                deviceId = modelDevice.deviceId,
                                label = modelDevice.label,
                                kind = modelDevice.kind.toString(), // Convert enum to String
                                groupId = modelDevice.groupId
                            )
                        },
                        videoInputs = parameters.videoInputs.map { modelDevice ->
                            com.mediasfu.sdk.webrtc.MediaDeviceInfo(
                                deviceId = modelDevice.deviceId,
                                label = modelDevice.label,
                                kind = modelDevice.kind.toString(), // Convert enum to String
                                groupId = modelDevice.groupId
                            )
                        },
                        updateAudioInputs = { inputs ->
                            // Convert from webrtc.MediaDeviceInfo to model.MediaDeviceInfo
                            parameters.audioInputs = inputs.map { webrtcDevice ->
                                val kindStr = webrtcDevice.kind ?: "audioinput"
                                val kind = when (kindStr.lowercase()) {
                                    "audioinput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOINPUT
                                    "audiooutput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOOUTPUT
                                    "videoinput" -> com.mediasfu.sdk.model.MediaDeviceKind.VIDEOINPUT
                                    else -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOINPUT
                                }
                                com.mediasfu.sdk.model.MediaDeviceInfo(
                                    deviceId = webrtcDevice.deviceId ?: "",
                                    label = webrtcDevice.label ?: "",
                                    kind = kind,
                                    groupId = webrtcDevice.groupId ?: ""
                                )
                            }
                            notifier()
                        },
                        updateVideoInputs = { inputs ->
                            // Convert from webrtc.MediaDeviceInfo to model.MediaDeviceInfo
                            parameters.videoInputs = inputs.map { webrtcDevice ->
                                val kindStr = webrtcDevice.kind ?: "videoinput"
                                val kind = when (kindStr.lowercase()) {
                                    "audioinput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOINPUT
                                    "audiooutput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOOUTPUT
                                    "videoinput" -> com.mediasfu.sdk.model.MediaDeviceKind.VIDEOINPUT
                                    else -> com.mediasfu.sdk.model.MediaDeviceKind.VIDEOINPUT
                                }
                                com.mediasfu.sdk.model.MediaDeviceInfo(
                                    deviceId = webrtcDevice.deviceId ?: "",
                                    label = webrtcDevice.label ?: "",
                                    kind = kind,
                                    groupId = webrtcDevice.groupId ?: ""
                                )
                            }
                            notifier()
                        },
                        updateAudioOutputs = { outputs ->
                            // Convert from webrtc.MediaDeviceInfo to model.MediaDeviceInfo
                            parameters.audioOutputs = outputs.map { webrtcDevice ->
                                val kindStr = webrtcDevice.kind ?: "audiooutput"
                                val kind = when (kindStr.lowercase()) {
                                    "audioinput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOINPUT
                                    "audiooutput" -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOOUTPUT
                                    "videoinput" -> com.mediasfu.sdk.model.MediaDeviceKind.VIDEOINPUT
                                    else -> com.mediasfu.sdk.model.MediaDeviceKind.AUDIOOUTPUT
                                }
                                com.mediasfu.sdk.model.MediaDeviceInfo(
                                    deviceId = webrtcDevice.deviceId ?: "",
                                    label = webrtcDevice.label ?: "",
                                    kind = kind,
                                    groupId = webrtcDevice.groupId ?: ""
                                )
                            }
                            notifier()
                        },
                        videoAlreadyOn = parameters.videoAlreadyOn,
                        audioAlreadyOn = parameters.audioAlreadyOn,
                        onWeb = false, // We're on mobile
                        updateIsLoadingModalVisible = { visible ->
                            setLoadingVisibility(visible)
                        },
                        device = parameters.device  // Pass the device with context
                    )
                )
                // Don't call closeAll() here - launchMediaSettings handles modal visibility
            } catch (e: Exception) {
            }
        }
    }

    fun showDisplaySettings() {
        closeAll()
        isDisplaySettingsVisible = true
        parameters.isDisplaySettingsModalVisible = true
        notifier()
    }

    fun toggleCoHost() {
        isCoHostVisible = !isCoHostVisible
        parameters.isCoHostModalVisible = isCoHostVisible
        if (!isCoHostVisible) onSidebarClose?.invoke(SidebarContent.CoHost)
        notifier()
    }

    fun toggleMediaSettings() {
        isMediaSettingsVisible = !isMediaSettingsVisible
        parameters.isMediaSettingsModalVisible = isMediaSettingsVisible
        if (!isMediaSettingsVisible) onSidebarClose?.invoke(SidebarContent.MediaSettings)
        notifier()
    }

    fun toggleDisplaySettings() {
        isDisplaySettingsVisible = !isDisplaySettingsVisible
        parameters.isDisplaySettingsModalVisible = isDisplaySettingsVisible
        if (!isDisplaySettingsVisible) onSidebarClose?.invoke(SidebarContent.DisplaySettings)
        notifier()
    }

    fun toggleBreakoutRooms() {
        isBreakoutRoomsVisible = !isBreakoutRoomsVisible
        parameters.isBreakoutRoomsModalVisible = isBreakoutRoomsVisible
        if (!isBreakoutRoomsVisible) onSidebarClose?.invoke(SidebarContent.BreakoutRooms)
        notifier()
    }

    fun updateIsMediaSettingsVisible(visible: Boolean) {
        if (isMediaSettingsVisible == visible) return
        isMediaSettingsVisible = visible
        parameters.isMediaSettingsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.MediaSettings)
        notifier()
    }

    fun updateIsDisplaySettingsVisible(visible: Boolean) {
        if (isDisplaySettingsVisible == visible) return
        isDisplaySettingsVisible = visible
        parameters.isDisplaySettingsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.DisplaySettings)
        notifier()
    }

    fun updateIsCoHostVisible(visible: Boolean) {
        if (isCoHostVisible == visible) return
        isCoHostVisible = visible
        parameters.isCoHostModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.CoHost)
        notifier()
    }

    fun updateIsCoHostModalVisible(visible: Boolean) {
        updateIsCoHostVisible(visible)
    }

    fun updateIsBreakoutRoomsModalVisible(visible: Boolean) {
        if (isBreakoutRoomsVisible == visible) return
        isBreakoutRoomsVisible = visible
        parameters.isBreakoutRoomsModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.BreakoutRooms)
        notifier()
    }

    fun updateIsBackgroundModalVisible(visible: Boolean) {
        if (isBackgroundVisible == visible) return
        isBackgroundVisible = visible
        parameters.isBackgroundModalVisible = visible
        if (!visible) onSidebarClose?.invoke(SidebarContent.Background)
        notifier()
    }

    fun refresh() {
        isMenuVisible = parameters.isMenuModalVisible
        isRecordingVisible = parameters.isRecordingModalVisible
        isSettingsVisible = parameters.isSettingsModalVisible
        isRequestsVisible = parameters.isRequestsModalVisible
        isWaitingVisible = parameters.isWaitingModalVisible
        isCoHostVisible = parameters.isCoHostModalVisible
        isMediaSettingsVisible = parameters.isMediaSettingsModalVisible
        isDisplaySettingsVisible = parameters.isDisplaySettingsModalVisible
        isParticipantsVisible = parameters.isParticipantsModalVisible
        isMessagesVisible = parameters.isMessagesModalVisible
        isConfirmExitVisible = parameters.isConfirmExitModalVisible
        isConfirmHereVisible = parameters.isConfirmHereModalVisible
        isShareEventVisible = parameters.isShareEventModalVisible
        isLoadingVisible = parameters.isLoadingModalVisible
        isScreenboardVisible = parameters.isScreenboardModalVisible
        isWhiteboardVisible = parameters.isWhiteboardModalVisible
        isConfigureWhiteboardVisible = parameters.isConfigureWhiteboardModalVisible
        isBreakoutRoomsVisible = parameters.isBreakoutRoomsModalVisible
        isBackgroundVisible = parameters.isBackgroundModalVisible
    }
}

class AlertState(private val parameters: MediasfuParameters) {
    var visible by mutableStateOf(parameters.alertVisible)
        private set
    var message by mutableStateOf(parameters.alertMessage)
        private set
    var type by mutableStateOf(parameters.alertType)
        private set
    var duration by mutableStateOf(parameters.alertDuration)
        private set

    fun show(message: String, type: String, duration: Int) {
        this.visible = true
        this.message = message
        this.type = type
        this.duration = duration
    }

    fun hide() {
        this.visible = false
        parameters.alertVisible = false
    }

    fun refresh() {
        visible = parameters.alertVisible
        message = parameters.alertMessage
        type = parameters.alertType
        duration = parameters.alertDuration
    }
}

data class MediasfuGenericOptions(
    val preJoinPageWidget: (@Composable (MediasfuGenericState) -> Unit)? = null,
    val localLink: String = "",
    val connectMediaSFU: Boolean = true,
    val credentials: Credentials? = null,
    val useLocalUIMode: Boolean? = null,
    val seedData: ModelSeedData? = null,
    val useSeed: Boolean? = null,
    val imgSrc: String? = null,
    val sourceParameters: MediasfuParameters? = null,
    val updateSourceParameters: ((MediasfuParameters) -> Unit)? = null,
    val returnUI: Boolean = true,
    val noUIPreJoinOptionsCreate: Map<String, Any>? = null,
    val noUIPreJoinOptionsJoin: Map<String, Any>? = null,
    /** Default event type for PreJoinPage dropdown - used by specialized components like MediasfuBroadcast */
    val defaultEventType: EventType = EventType.CONFERENCE,
    /** Callback for socket-level room joining (internal use) */
    val joinRoomClient: suspend (JoinRoomClientOptions) -> ResponseJoinRoom = { com.mediasfu.sdk.producer_client.joinRoomClient(it) },
    /** REST API callback to join a room on MediaSFU Cloud (used by PreJoinPage) */
    val joinMediaSFURoom: suspend (JoinMediaSFUOptions) -> CreateJoinRoomResult = { joinRoomOnMediaSfu(it) },
    /** REST API callback to create a room on MediaSFU Cloud (used by PreJoinPage) */
    val createMediaSFURoom: suspend (CreateMediaSFUOptions) -> CreateJoinRoomResult = { createRoomOnMediaSfu(it) },
    val customVideoCard: (@Composable (Stream) -> Unit)? = null,
    val customAudioCard: (@Composable (Stream) -> Unit)? = null,
    val customMiniCard: (@Composable (Stream) -> Unit)? = null,
    var customComponent: (@Composable (MediasfuGenericState) -> Unit)? = null,
    val containerStyle: ContainerStyleOptions = ContainerStyleOptions(),
    val uiOverrides: MediasfuUiOverrides = MediasfuUiOverrides(),
    val customWorkspaceBuilder: (@Composable (MediasfuGenericState) -> Unit)? = null,
    /** Apply modern theme styling (colors, typography) - default: true */
    val useModernTheme: Boolean = true,
    /** Enable modern UI with unified modal system - set to false to use original modals with modern theme */
    val useModernUI: Boolean = true,
    /** Force dark mode (true), light mode (false), or use system default (null) */
    val darkMode: Boolean? = null,
    /** Mutable holder for the toggle audio callback - set by MediasfuGeneric when state is created */
    var onToggleAudio: (suspend () -> Unit)? = null,
    /** Mutable holder for the toggle video callback - set by MediasfuGeneric when state is created */
    var onToggleVideo: (suspend () -> Unit)? = null,
) {
    init {
        if (customComponent == null && customWorkspaceBuilder != null) {
            customComponent = customWorkspaceBuilder
        }
    }

    @Deprecated("Use preJoinPageWidget", ReplaceWith("preJoinPageWidget"))
    val preJoinPage: (@Composable (MediasfuGenericState) -> Unit)?
        get() = preJoinPageWidget
}

@Composable
fun rememberMediasfuGenericState(options: MediasfuGenericOptions): MediasfuGenericState {
    val scope = rememberCoroutineScope()
    // Use sourceParameters directly - don't cache with remember since the same object may be mutated
    // externally (e.g., by launchMediaSfuCloudSession setting roomName, adminPasscode, etc.)
    val parameters = options.sourceParameters ?: remember { MediasfuParameters() }
    
    // Key on both options AND the critical parameters to ensure state is recreated when they change
    val stateKey = remember(options, parameters.roomName, parameters.validated) { Any() }
    val state = remember(stateKey) {
        MediasfuGenericState(scope = scope, parameters = parameters, options = options)
    }
    
    // Wire up the toggle callbacks so external code can control audio/video
    LaunchedEffect(state) {
        options.onToggleAudio = { 
            state.toggleAudio() 
        }
        options.onToggleVideo = { 
            state.toggleVideo() 
        }
    }

    LaunchedEffect(state.parameters) {
        options.updateSourceParameters?.invoke(state.parameters)
    }

    DisposableEffect(state) {
        onDispose {
            state.parameters.onParticipantsUpdated = null
            state.parameters.onOtherGridStreamsUpdated = null
            state.parameters.onAudioOnlyStreamsUpdated = null
            state.parameters.onRecordingStateChanged = null
            options.onToggleAudio = null
            options.onToggleVideo = null
        }
    }

    return state
}

@Composable
fun MediasfuGeneric(
    options: MediasfuGenericOptions,
    modifier: Modifier = Modifier,
    state: MediasfuGenericState = rememberMediasfuGenericState(options),
) {
    // Wrap with ModernTheme when useModernTheme is enabled
    if (options.useModernTheme) {
        ModernTheme {
            MediasfuGenericContent(state = state, modifier = modifier)
        }
    } else {
        MediasfuGenericContent(state = state, modifier = modifier)
    }
}

// ---------------------------------------------------------------------------
// Compose UI
// ---------------------------------------------------------------------------

data class ControlButtonModel(
    val label: String,
    val icon: ImageVector,
    val alternateIcon: ImageVector? = null,
    val onClick: () -> Unit,
    val isVisible: Boolean = true,
    val isEnabled: Boolean = true,
    val isActive: Boolean = false,
    val activeTint: Color = Color(0xFF40C4FF),
    val inactiveTint: Color = Color(0xFF90A4AE),
    val textColor: Color = Color.White,
    val badgeText: String? = null,
    val badgeColor: Color = Color(0xFFFF4D4F),
    val isLoading: Boolean = false
)

/**
 * Converts ControlButtonModel to ControlButtonOptions for use with ControlButtonsComponentTouch
 */
internal fun ControlButtonModel.toControlButtonOptions(): ControlButtonOptions {
    // Convert androidx.compose.ui.graphics.Color to com.mediasfu.sdk.ui.Color
    fun androidx.compose.ui.graphics.Color.toMediaSfuColor(): com.mediasfu.sdk.ui.Color {
        return com.mediasfu.sdk.ui.Color(
            red = this.red,
            green = this.green,
            blue = this.blue,
            alpha = this.alpha
        )
    }
    
    return ControlButtonOptions(
        id = label.lowercase().replace(" ", "_"),
        text = label,
        icon = null, // Icons are handled differently in Compose
        active = isActive,
        enabled = isEnabled,
        visible = isVisible,
        textColor = textColor.toMediaSfuColor(),
        activeColor = activeTint.toMediaSfuColor(),
        inactiveColor = inactiveTint.toMediaSfuColor(),
        onClick = onClick
    )
}

/**
 * Converts list of ControlButtonModel to ControlButtonOptions
 */
internal fun List<ControlButtonModel>.toControlButtonOptions(): List<ControlButtonOptions> {
    return this.map { it.toControlButtonOptions() }
}

@Composable
internal fun ControlButtonsRow(
    buttons: List<ControlButtonModel>,
    modifier: Modifier = Modifier
) {
    val visibleButtons = remember(buttons) { buttons.filter(ControlButtonModel::isVisible) }
    if (visibleButtons.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleButtons.forEach { button ->
            ControlButtonItem(button)
        }
    }
}

@Composable
private fun ControlButtonItem(button: ControlButtonModel) {
    val iconTint = if (button.isActive) button.activeTint else button.inactiveTint
    val iconVector = if (button.isActive && button.alternateIcon != null) {
        button.alternateIcon
    } else {
        button.icon
    }
    val adjustedIconTint = if (button.isEnabled) iconTint else iconTint.copy(alpha = 0.4f)
    val labelColor = if (button.isEnabled) button.textColor else button.textColor.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .clickable(enabled = button.isEnabled) { button.onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            if (button.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = button.activeTint,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    iconVector,
                    contentDescription = button.label,
                    tint = adjustedIconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            if (button.badgeText != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(button.badgeColor, CircleShape)
                        .size(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (button.badgeText.isNotEmpty()) {
                        Text(
                            text = button.badgeText,
                            color = Color.White,
                            fontSize = 8.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = button.label, color = labelColor, fontSize = 12.sp)
    }
}

@Composable
private fun MediasfuGenericContent(state: MediasfuGenericState, modifier: Modifier = Modifier) {
    val isValidated by state.validated.collectAsState()
    val isLoading by state.isLoading.collectAsState()
    val sessionKey by state.sessionCounter.collectAsState()  // Track session changes for UI reset
    val hasAlert by remember { derivedStateOf { state.alert.visible } }
    val containerStyle = state.options.containerStyle
    val backgroundColor = containerStyle.backgroundColor ?: Color(0xFF0B172A)
    val useModernUI = state.options.useModernUI

    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()

    // Unified modal state for modern UI
    var unifiedModalState by remember { mutableStateOf(UnifiedModalState()) }
    
    // Wire up sidebar close callback for ModalState (matches Flutter lib_modern pattern)
    // When updateIs*ModalVisible(false) is called, also close the sidebar if showing that content
    LaunchedEffect(unifiedModalState, useModernUI, state) {
        if (useModernUI) {
            state.modals.onSidebarClose = { sidebarContent ->
                // Only close if the unified modal is currently showing this content
                if (unifiedModalState.activeContent == sidebarContent) {
                    // Use close() instead of navigateBack() because the intent is to 
                    // fully close the modal (e.g., after starting recording), not to 
                    // navigate back to a previous screen in the stack
                    unifiedModalState.close()
                }
            }
        }
    }

    // Close unified modal when validation fails (event ends)
    LaunchedEffect(isValidated) {
        if (!isValidated) {
            unifiedModalState.close()
        }
    }

    // Sync _isLoading with modals.isLoadingVisible
    // If external code (e.g. socket handlers) hides the loader, we must update our local state
    LaunchedEffect(state.modals.isLoadingVisible) {
        if (!state.modals.isLoadingVisible && isLoading) {
            state.hideLoader()
        }
    }

    // Observe loading flags from Compose to trace stuck states
    LaunchedEffect(isLoading, state.modals.isLoadingVisible) {
        // Observation point for loading state
    }
    
    // Reverse sync: When unified modal navigates (e.g., Menu -> Recording),
    // update legacy visibility flags so that programmatic close calls work correctly
    LaunchedEffect(unifiedModalState.activeContent) {
        if (useModernUI) {
            val content = unifiedModalState.activeContent
            // Set the corresponding legacy flag to true (without triggering onSidebarClose)
            // This ensures setRecordingVisibility(false) etc. will work when called from business logic
            when (content) {
                SidebarContent.Recording -> {
                    if (!state.modals.isRecordingVisible) {
                        state.modals.isRecordingVisible = true
                        state.parameters.isRecordingModalVisible = true
                    }
                }
                SidebarContent.Menu -> {
                    if (!state.modals.isMenuVisible) {
                        state.modals.isMenuVisible = true
                        state.parameters.isMenuModalVisible = true
                    }
                }
                // Add more cases as needed for modals that can be navigated to from unified modal
                else -> { /* Other modals use legacy visibility directly */ }
            }
        }
    }
    
    // Sync unified modal state with legacy modal visibility
    LaunchedEffect(
        state.modals.isMenuVisible,
        state.modals.isParticipantsVisible,
        state.modals.isMessagesVisible,
        state.modals.isRecordingVisible,
        state.modals.isMediaSettingsVisible,
        state.modals.isDisplaySettingsVisible,
        state.modals.isConfirmExitVisible,
        state.modals.isSettingsVisible,
        state.modals.isRequestsVisible,
        state.modals.isWaitingVisible,
        state.modals.isCoHostVisible,
        state.modals.isShareEventVisible,
        state.modals.isBreakoutRoomsVisible,
        state.polls.isPollModalVisible,
        state.modals.isWhiteboardVisible,
        state.modals.isConfigureWhiteboardVisible,
        state.modals.isBackgroundVisible
    ) {
        if (useModernUI) {
            val newContent = when {
                state.modals.isMenuVisible -> SidebarContent.Menu
                state.modals.isParticipantsVisible -> SidebarContent.Participants
                state.modals.isMessagesVisible -> SidebarContent.Messages
                state.modals.isRecordingVisible -> SidebarContent.Recording
                state.modals.isMediaSettingsVisible -> SidebarContent.MediaSettings
                state.modals.isDisplaySettingsVisible -> SidebarContent.DisplaySettings
                state.modals.isConfirmExitVisible -> SidebarContent.ConfirmExit
                state.modals.isSettingsVisible -> SidebarContent.Settings
                state.modals.isRequestsVisible -> SidebarContent.Requests
                state.modals.isWaitingVisible -> SidebarContent.Waiting
                state.modals.isCoHostVisible -> SidebarContent.CoHost
                state.modals.isShareEventVisible -> SidebarContent.Share
                state.modals.isBreakoutRoomsVisible -> SidebarContent.BreakoutRooms
                state.polls.isPollModalVisible -> SidebarContent.Polls
                state.modals.isWhiteboardVisible -> SidebarContent.Whiteboard
                state.modals.isConfigureWhiteboardVisible -> SidebarContent.Whiteboard // Map ConfigureWhiteboard to Whiteboard
                state.modals.isBackgroundVisible -> SidebarContent.Background
                else -> null
            }
            if (newContent != null) {
                unifiedModalState.show(newContent)
            } else {
                unifiedModalState.close()
            }
        }
    }

    // Wrapper to apply modern theme when useModernUI is true
    val content: @Composable () -> Unit = {
        val isDark = state.options.darkMode ?: isSystemInDarkTheme()
        ModernTheme(isDark = isDark) {
            BoxWithConstraints(
                modifier = modifier
                    .fillMaxSize()
                    .padding(safeDrawingPadding)
            ) {
                DeviceOrientationEffect(state)
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .applyContainerStyle(containerStyle)
                            .background(if (useModernUI) MaterialTheme.colorScheme.background else backgroundColor)
                    ) {
                        if (!isValidated) {
                            PreJoinOrWelcome(state)
                        } else {
                            // MainContainer > MainAspect + SubAspect (matches React/Flutter structure)
                            // Use session key to force full recomposition when starting new meeting
                            // This clears cached composables from previous session
                            key(sessionKey) {
                                MainContainerInline(state)
                            }
                        }
                    }

                    if (state.options.useModernUI) {
                        UnifiedModalHost(
                            state = unifiedModalState,
                            content = {
                                UnifiedModalContentAdapter(
                                    state = state,
                                    unifiedModalState = unifiedModalState,
                                    onClose = {
                                        state.modals.closeAll()
                                        unifiedModalState.close()
                                    }
                                )
                            }
                        )
                        
                        ConfirmHereModal(state)
                    } else {
                        MenuModal(state)
                        RecordingModal(state)
                        RequestsModal(state)
                        WaitingModal(state)
                        DisplaySettingsModal(state)
                        CoHostModal(state)
                        MediaSettingsModal(state)
                        BackgroundModalWrapper(state)
                        BreakoutRoomsModal(state)
                        PollModal(state)
                        ParticipantsModal(state)
                        MessagesModal(state)
                        SettingsModal(state)
                        ShareEventModal(state)
                        WhiteboardModal(state)
                        ConfigureWhiteboardModal(state)
                        ConfirmExitModal(state)
                        ConfirmHereModal(state)
                    }

                    AlertBanner(
                        state = state,
                        isVisible = hasAlert,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                    )

                    // Render the loading overlay exactly once. Visibility is controlled internally
                    // by the unified loader flags (isLoading/modals/parameters) inside LoadingModal.
                    LoadingModal(state)
                }
            }
        }
    }

    content()
}

/**
 * Adapter that renders modal content for the unified modal system using actual MediasfuGenericState data.
 */
@Composable
private fun UnifiedModalContentAdapter(
    state: MediasfuGenericState,
    unifiedModalState: UnifiedModalState,
    onClose: () -> Unit
) {
    val onBack = if (unifiedModalState.canGoBack) { { unifiedModalState.navigateBack() } } else null

    when (unifiedModalState.activeContent) {
        SidebarContent.Menu -> {
            val props = state.createMenuModalProps().copy(
                onOpenPolls = { unifiedModalState.show(SidebarContent.Polls, pushToStack = true) },
                onOpenConfigureWhiteboard = { unifiedModalState.show(SidebarContent.Whiteboard, pushToStack = true) },
                onOpenRecording = { unifiedModalState.show(SidebarContent.Recording, pushToStack = true) },
                onOpenSettings = { unifiedModalState.show(SidebarContent.Settings, pushToStack = true) },
                onOpenMediaSettings = { unifiedModalState.show(SidebarContent.MediaSettings, pushToStack = true) },
                onOpenDisplaySettings = { unifiedModalState.show(SidebarContent.DisplaySettings, pushToStack = true) },
                onOpenRequests = { unifiedModalState.show(SidebarContent.Requests, pushToStack = true) },
                onOpenWaiting = { unifiedModalState.show(SidebarContent.Waiting, pushToStack = true) },
                onOpenCoHost = { unifiedModalState.show(SidebarContent.CoHost, pushToStack = true) },
                onOpenShareEvent = { unifiedModalState.show(SidebarContent.Share, pushToStack = true) },
                onOpenBreakoutRooms = { unifiedModalState.show(SidebarContent.BreakoutRooms, pushToStack = true) },
                onClose = onClose
            )
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Menu", onBack = onBack, onClose = onClose)
                MenuModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Participants -> {
            val props = state.createParticipantsModalProps().copy(onClose = onClose)
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Participants (${props.participantCount})", onBack = onBack, onClose = onClose)
                ParticipantsModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Messages -> {
            val props = state.createMessagesModalProps().copy(onClose = onClose)
            var selectedTab by remember { mutableStateOf(
                if (props.eventType == EventType.WEBINAR || props.eventType == EventType.CONFERENCE) "direct" else "group"
            ) }
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Messages", onBack = onBack, onClose = onClose)
                MessagesModalContentBody(
                    props = props,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Recording -> {
            val props = state.createRecordingModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Recording", onBack = onBack, onClose = onClose)
                RecordingModalContentBody(
                    props = props,
                    showActionButtons = true,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.MediaSettings -> {
            val props = state.createMediaSettingsModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Media Settings", onBack = onBack, onClose = onClose)
                MediaSettingsModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.DisplaySettings -> {
            val props = state.createDisplaySettingsModalProps().copy(onClose = onClose)
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Display Settings", onBack = onBack, onClose = onClose)
                DisplaySettingsModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Settings, SidebarContent.EventSettings -> {
            val options = state.createEventSettingsModalOptions()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Event Settings", onBack = onBack, onClose = onClose)
                SettingsModalContentBody(
                    options = options,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Requests -> {
            val props = state.createRequestsModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Requests", onBack = onBack, onClose = onClose)
                RequestsModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Waiting -> {
            val props = state.createWaitingModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Waiting Room (${props.pendingCount})", onBack = onBack, onClose = onClose)
                WaitingModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.CoHost -> {
            val props = state.createCoHostModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Manage Co-Hosts", onBack = onBack, onClose = onClose)
                CoHostModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Share -> {
            val props = state.createShareEventModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Share Event", onBack = onBack, onClose = onClose)
                ShareEventModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.BreakoutRooms -> {
            val props = state.createBreakoutRoomsModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Breakout Rooms", onBack = onBack, onClose = onClose)
                BreakoutRoomsModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Polls -> {
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Polls", onBack = onBack, onClose = onClose)
                PollModalContentForUnifiedModal(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                )
            }
        }
        
        SidebarContent.Whiteboard -> {
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Whiteboard", onBack = onBack, onClose = onClose)
                WhiteboardModalContentEmbedded(
                    state = state,
                    onClose = onClose
                )
            }
        }
        
        SidebarContent.Background -> {
            LaunchedEffect(Unit) {
                onClose()
                state.modals.updateIsBackgroundModalVisible(true)
            }
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        
        SidebarContent.ConfirmExit -> {
            val props = state.createConfirmExitModalProps()
            Column(modifier = Modifier.fillMaxSize()) {
                UnifiedModalHeader(title = "Exit Event", onBack = onBack, onClose = onClose)
                ConfirmExitModalContentBody(
                    props = props,
                    modifier = Modifier.fillMaxSize().padding(24.dp)
                )
            }
        }
        
        SidebarContent.None -> {}
        else -> {}
    }
}

private fun formatRecordingTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    } else {
        "${minutes.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
    }
}

// ==================== Embedded Modal Content Composables ====================
// These composables render the actual modal content inside the unified modal system
// They use the same props and logic as the original modals but without the AlertDialog wrapper

/**
 * Embedded Menu Modal Content - displays menu options for navigation
 */
@Composable
private fun MenuModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit,
    onNavigate: (SidebarContent) -> Unit
) {
    val props = state.createMenuModalProps()
    val scrollState = rememberScrollState()
    val isHost = props.islevel == "2"
    val isCoHost = props.coHost == props.member

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Event Settings (Host only)
        if (isHost) {
            EmbeddedMenuButton(
                label = "Event Settings",
                description = "Configure participant permissions for this event",
                icon = Icons.Rounded.Settings,
                onClick = { onNavigate(SidebarContent.Settings) }
            )
        }

        // Recording (Host only)
        if (isHost) {
            EmbeddedMenuButton(
                label = "Recording",
                description = "Start or manage session recording",
                icon = Icons.Rounded.FiberManualRecord,
                onClick = { onNavigate(SidebarContent.Recording) }
            )
        }

        // Co-Host (Host only)
        if (isHost) {
            EmbeddedMenuButton(
                label = "Co-Host",
                description = "Manage co-host and their permissions",
                icon = Icons.Rounded.SupervisorAccount,
                onClick = { onNavigate(SidebarContent.CoHost) }
            )
        }

        // Breakout Rooms (Host only)
        if (isHost) {
            EmbeddedMenuButton(
                label = "Breakout Rooms",
                description = "Create and manage breakout room sessions",
                icon = Icons.Rounded.Group,
                onClick = { onNavigate(SidebarContent.BreakoutRooms) }
            )
        }

        // Set Media (always available)
        EmbeddedMenuButton(
            label = "Set Media",
            description = "Configure audio, video, and sharing settings",
            icon = Icons.Rounded.Videocam,
            onClick = { onNavigate(SidebarContent.MediaSettings) }
        )

        // Display (always available)
        EmbeddedMenuButton(
            label = "Display",
            description = "Customize layout and display options",
            icon = Icons.Rounded.DisplaySettings,
            onClick = { onNavigate(SidebarContent.DisplaySettings) }
        )

        // Check co-host responsibilities
        val participantsValue = props.coHostResponsibility.find { it.name == "participants" }?.value == true

        // Manage Requests (Host or Co-Host with participants permission)
        val canManageRequests = isHost || (isCoHost && participantsValue)
        if (canManageRequests) {
            EmbeddedMenuButton(
                label = buildString {
                    append("Manage Requests")
                    if (props.totalRequests > 0) append(" (${props.totalRequests})")
                },
                description = "Review and respond to participant requests",
                icon = Icons.Rounded.Menu,
                onClick = { onNavigate(SidebarContent.Requests) }
            )
        }

        // Waiting Room (Host or Co-Host with participants permission)
        val canManageWaiting = isHost || (isCoHost && participantsValue)
        if (canManageWaiting) {
            EmbeddedMenuButton(
                label = buildString {
                    append("Waiting Room")
                    if (props.waitingCount > 0) append(" (${props.waitingCount})")
                },
                description = "Manage participants in the waiting area",
                icon = Icons.Rounded.Group,
                onClick = { onNavigate(SidebarContent.Waiting) }
            )
        }

        // Share Event
        EmbeddedMenuButton(
            label = "Share Event",
            description = "Share event details with others",
            icon = Icons.Rounded.Share,
            onClick = { onNavigate(SidebarContent.Share) }
        )

        // Polls
        EmbeddedMenuButton(
            label = "Polls",
            description = "Create and participate in polls",
            icon = Icons.Rounded.HowToVote,
            onClick = { onNavigate(SidebarContent.Polls) }
        )

        // Whiteboard
        EmbeddedMenuButton(
            label = "Whiteboard",
            description = "Collaborative drawing and annotations",
            icon = Icons.Rounded.Create,
            onClick = { onNavigate(SidebarContent.Whiteboard) }
        )

        // Background
        EmbeddedMenuButton(
            label = "Virtual Background",
            description = "Change your video background",
            icon = Icons.Rounded.Wallpaper,
            onClick = { onNavigate(SidebarContent.Background) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Exit button at bottom
        OutlinedButton(
            onClick = { onNavigate(SidebarContent.ConfirmExit) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Leave Event")
        }
    }
}

/**
 * Menu button helper for embedded menu content
 */
@Composable
private fun EmbeddedMenuButton(
    label: String,
    description: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Embedded Participants Modal Content
 */
@Composable
private fun ParticipantsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createParticipantsModalProps()
    val visibleParticipants = props.filteredParticipants
        .takeIf { it.isNotEmpty() || props.filter.isNotBlank() }
        ?: props.participants

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Filter participants") },
            modifier = Modifier.fillMaxWidth()
        )

        if (visibleParticipants.isNotEmpty()) {
            Text(
                text = "Showing ${visibleParticipants.size} of ${props.participantCount} participants",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (visibleParticipants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val emptyMessage = if (props.filter.isBlank()) {
                    "No one has joined the event yet."
                } else {
                    "No participants match \"${props.filter}\"."
                }
                Text(
                    text = emptyMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visibleParticipants, key = { it.id ?: it.name }) { participant ->
                    EmbeddedParticipantRow(
                        participant = participant,
                        isCurrentUser = participant.name.equals(props.state.room.member, ignoreCase = true),
                        state = props.state
                    )
                }
            }
        }
    }
}

/**
 * Participant row for embedded content
 */
@Composable
private fun EmbeddedParticipantRow(
    participant: Participant,
    isCurrentUser: Boolean,
    state: MediasfuGenericState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.name.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = participant.name + if (isCurrentUser) " (You)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (participant.islevel == "2") {
                    Text(
                        text = "Host",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Media status icons
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (participant.muted != true) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                    contentDescription = null,
                    tint = if (participant.muted != true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
                Icon(
                    imageVector = if (participant.videoOn == true) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    contentDescription = null,
                    tint = if (participant.videoOn == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Embedded Messages Modal Content
 */
@Composable
private fun MessagesModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createMessagesModalProps()
    
    // Determine initial tab based on event type
    val initialTab = remember(props.eventType) {
        when (props.eventType) {
            EventType.WEBINAR, EventType.CONFERENCE -> "direct"
            else -> "group"
        }
    }
    
    var selectedTab by remember { mutableStateOf(initialTab) }
    
    // Filter messages
    val directMessages = remember(props.messages, props.member, props.islevel, props.coHost, props.coHostResponsibility) {
        val chatValue = props.coHostResponsibility.find { it.name == "chat" }?.value == true
        props.messages.filter { message ->
            !message.group && (
                message.sender == props.member ||
                message.receivers.contains(props.member) ||
                props.islevel == "2" ||
                (props.coHost == props.member && chatValue)
            )
        }
    }
    
    val groupMessages = remember(props.messages) {
        props.messages.filter { it.group }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Tabs (only show for webinar/conference)
        if (props.eventType == EventType.WEBINAR || props.eventType == EventType.CONFERENCE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EmbeddedMessageTab(
                    text = "Direct",
                    selected = selectedTab == "direct",
                    onClick = { selectedTab = "direct" },
                    modifier = Modifier.weight(1f)
                )
                EmbeddedMessageTab(
                    text = "Group",
                    selected = selectedTab == "group",
                    onClick = { selectedTab = "group" },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        // Message List
        val displayMessages = if (selectedTab == "direct") directMessages else groupMessages
        
        if (displayMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(displayMessages) { message ->
                    EmbeddedMessageBubble(
                        message = message,
                        isOwnMessage = message.sender == props.member
                    )
                }
            }
        }
        
        // Send message area
        Spacer(modifier = Modifier.height(8.dp))
        EmbeddedMessageInput(
            onSend = { text ->
                props.onSendMessage(
                    SendMessageOptions(
                        message = text,
                        receivers = emptyList(),
                        group = selectedTab == "group",
                        messagesLength = props.messages.size,
                        member = props.member,
                        sender = props.member,
                        roomName = props.roomName,
                        socket = props.socket,
                        showAlert = props.showAlert,
                        islevel = props.islevel,
                        coHostResponsibility = props.coHostResponsibility,
                        coHost = props.coHost,
                        chatSetting = props.chatSetting
                    )
                )
            }
        )
    }
}

@Composable
private fun EmbeddedMessageTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 2.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EmbeddedMessageBubble(
    message: Message,
    isOwnMessage: Boolean
) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val color = if (isOwnMessage) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (!isOwnMessage) {
            Text(
                text = message.sender,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = color
        ) {
            Text(
                text = message.message,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun EmbeddedMessageInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type a message...") },
            singleLine = true
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onSend(text)
                    text = ""
                }
            }
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
        }
    }
}

/**
 * Embedded Recording Modal Content
 */
@Composable
private fun RecordingModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createRecordingModalProps()
    val isRecording = state.recording.recordStarted && !state.recording.recordPaused
    val isPaused = state.recording.recordStarted && state.recording.recordPaused
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.FiberManualRecord,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = when {
                isRecording -> MaterialTheme.colorScheme.error
                isPaused -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = when {
                isRecording -> "Recording in Progress"
                isPaused -> "Recording Paused"
                else -> "Recording"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        if (state.recording.recordStarted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.recording.recordingProgressTime.ifBlank { "00:00" },
                style = MaterialTheme.typography.titleLarge,
                color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        if (!state.recording.recordStarted) {
            // Recording configuration info
            Text(
                text = "Configure and start recording for this session",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = { 
                    props.startRecording(
                        com.mediasfu.sdk.ui.components.recording.StartRecordingOptions(
                            parameters = props.parameters
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.FiberManualRecord, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording")
            }
        } else {
            Text(
                text = "Recording is managed by the server.\nUse the recording controls in the control bar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Embedded Media Settings Modal Content
 */
@Composable
private fun MediaSettingsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Media Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Audio toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Mic, contentDescription = null)
                    Text("Microphone", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = state.media.audioAlreadyOn,
                    onCheckedChange = { state.toggleAudio() }
                )
            }
        }
        
        // Video toggle
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Videocam, contentDescription = null)
                    Text("Camera", style = MaterialTheme.typography.bodyLarge)
                }
                Switch(
                    checked = state.media.videoAlreadyOn,
                    onCheckedChange = { state.toggleVideo() }
                )
            }
        }
    }
}

/**
 * Embedded Display Settings Modal Content
 */
@Composable
private fun DisplaySettingsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Display Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Configure your display layout and options",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Display settings will be configured through state
        // This is a simplified view
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Current Layout", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = state.display.meetingDisplayType.ifBlank { "Default" },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * Embedded Settings (Event Settings) Modal Content
 */
@Composable
private fun SettingsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Event Settings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Text(
            text = "Configure event permissions and settings",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        // Settings from MediaState
        val settings = listOf(
            "Audio" to state.media.audioSetting,
            "Video" to state.media.videoSetting,
            "Screenshare" to state.media.screenshareSetting,
            "Chat" to state.media.chatSetting
        )
        
        settings.forEach { (name: String, value: String) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = value.ifBlank { "allowed" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Embedded Requests Modal Content
 */
@Composable
private fun RequestsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createRequestsModalProps()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Manage Requests",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Filter requests") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (props.requests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No pending requests",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(props.requests) { request ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = request.name ?: request.username ?: "Unknown",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = request.icon ?: "Request",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { props.onAccept(request) }) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Accept",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { props.onReject(request) }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Reject",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Embedded Waiting Room Modal Content
 */
@Composable
private fun WaitingModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createWaitingModalProps()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Waiting Room",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        OutlinedTextField(
            value = props.filter,
            onValueChange = props.onFilterChange,
            label = { Text("Filter participants") },
            modifier = Modifier.fillMaxWidth()
        )
        
        if (props.participants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No one in waiting room",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(props.participants) { participant ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = participant.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(onClick = { props.onAllow(participant) }) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "Allow",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(onClick = { props.onDeny(participant) }) {
                                    Icon(
                                        Icons.Rounded.Close,
                                        contentDescription = "Deny",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Embedded Co-Host Modal Content
 */
@Composable
private fun CoHostModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createCoHostModalProps()
    
    CoHostModalContentInternal(props)
}

/**
 * Embedded Share Event Modal Content
 */
@Composable
private fun ShareEventModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createShareEventModalProps()
    
    ShareEventModalContentInternal(props)
}

/**
 * Embedded Breakout Rooms Modal Content
 */
@Composable
private fun BreakoutRoomsModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val props = state.createBreakoutRoomsModalProps()
    
    BreakoutRoomsModalContentInternal(props)
}

/**
 * Embedded Poll Modal Content
 */
@Composable
private fun PollModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    PollModalContentInternal(state)
}

/**
 * Poll Modal Content for Unified Modal - Creates props and uses real PollModalContentBody
 */
@Composable
private fun PollModalContentForUnifiedModal(
    state: MediasfuGenericState,
    modifier: Modifier = Modifier
) {
    val pollsState = state.polls
    val room = state.room
    val parameters = state.parameters
    val memberName = room.member.ifBlank { parameters.member }
    val isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true)

    val activePollCandidate = pollsState.activePoll
    val activePoll = if (activePollCandidate?.isActive() == true) {
        activePollCandidate
    } else {
        pollsState.polls.firstOrNull { it.isActive() }
    }
    val previousPolls = pollsState.polls.filter { poll ->
        val hasId = !poll.id.isNullOrBlank()
        val isCurrent = activePoll?.id != null && poll.id == activePoll.id && activePoll?.isActive() == true
        hasId && !isCurrent
    }

    val questionState = remember(pollsState.isPollModalVisible) { mutableStateOf("") }
    val pollTypeState = remember(pollsState.isPollModalVisible) { mutableStateOf("choose") }
    val customOptionInputs = remember(pollsState.isPollModalVisible) {
        mutableStateListOf("", "")
    }
    val submittingState = remember { mutableStateOf(false) }
    val votingIndexState = remember { mutableStateOf<Int?>(null) }
    val endingPollIdState = remember { mutableStateOf<String?>(null) }

    val pollTypeOptions = remember {
        listOf(
            MediasfuPollTypeOption("choose", "Choose..."),
            MediasfuPollTypeOption("trueFalse", "True/False"),
            MediasfuPollTypeOption("yesNo", "Yes/No"),
            MediasfuPollTypeOption("custom", "Custom")
        )
    }

    val resetCreatePollState: () -> Unit = {
        questionState.value = ""
        pollTypeState.value = "choose"
        customOptionInputs.clear()
        repeat(2) { customOptionInputs.add("") }
    }

    val handleEndPoll: (String) -> Unit = { pollId ->
        if (pollId.isNotBlank() && endingPollIdState.value == null) {
            endingPollIdState.value = pollId
            state.endPoll(pollId) {
                endingPollIdState.value = null
            }
        }
    }

    val props = PollModalProps(
        state = state,
        isVisible = pollsState.isPollModalVisible,
        isHost = isHost,
        memberName = memberName,
        activePoll = activePoll,
        previousPolls = previousPolls,
        pollTypeOptions = pollTypeOptions,
        questionState = questionState,
        pollTypeState = pollTypeState,
        customOptionInputs = customOptionInputs,
        submittingState = submittingState,
        votingIndexState = votingIndexState,
        endingPollIdState = endingPollIdState,
        resetCreatePollState = resetCreatePollState,
        handleEndPoll = handleEndPoll,
        onDismiss = { state.setPollModalVisible(false) }
    )

    PollModalContentBody(
        props = props,
        modifier = modifier
    )
}

/**
 * Embedded Whiteboard Modal Content - uses the real ConfigureWhiteboardModal content
 */
@Composable
private fun WhiteboardModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val options = state.createConfigureWhiteboardModalOptions()
    
    ConfigureWhiteboardModalContentBody(
        options = options,
        modifier = Modifier.fillMaxSize().padding(16.dp)
    )
}

// Note: BackgroundModalContentEmbedded removed - we launch the full BackgroundModal dialog instead
// because it requires video preview, ML Kit segmentation, and complex tab-based UI that
// can't be properly embedded in the sidebar.

/**
 * Embedded Confirm Exit Modal Content
 */
@Composable
private fun ConfirmExitModalContentEmbedded(
    state: MediasfuGenericState,
    onClose: () -> Unit
) {
    val isHost = state.room.youAreHost || state.room.islevel == "2"
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Leave Event?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isHost) {
                "As the host, leaving will end the event for all participants."
            } else {
                "Are you sure you want to leave this event?"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                state.confirmExitFromPrompt()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(if (isHost) "End Event" else "Leave Event")
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

// Internal content composables for modals that don't have extracted content functions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoHostModalContentInternal(props: CoHostModalOptions) {
    var selectedCoHost by remember { mutableStateOf(props.currentCohost) }
    var expandedDropdown by remember { mutableStateOf(false) }
    
    val participantNames = remember(props.participants) {
        props.participants.filter { it.name != props.currentCohost }.map { it.name }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Select Co-Host",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Participant dropdown using Box with DropdownMenu
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = selectedCoHost.ifBlank { "Select participant" },
                onValueChange = { },
                readOnly = true,
                label = { Text("Co-Host") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDropdown = true },
                trailingIcon = {
                    IconButton(onClick = { expandedDropdown = !expandedDropdown }) {
                        Icon(
                            imageVector = if (expandedDropdown) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                            contentDescription = "Toggle dropdown"
                        )
                    }
                }
            )
            
            DropdownMenu(
                expanded = expandedDropdown,
                onDismissRequest = { expandedDropdown = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                DropdownMenuItem(
                    text = { Text("No coHost") },
                    onClick = {
                        selectedCoHost = "No coHost"
                        expandedDropdown = false
                    }
                )
                participantNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            selectedCoHost = name
                            expandedDropdown = false
                        }
                    )
                }
            }
        }
        
        // Responsibilities section
        Text(
            text = "Co-Host Responsibilities",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium
        )
        
        props.coHostResponsibility.forEachIndexed { index, responsibility ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = responsibility.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = responsibility.value,
                    onCheckedChange = { newValue ->
                        // Create updated responsibility list
                        val updated = props.coHostResponsibility.toMutableList().apply {
                            this[index] = responsibility.copy(value = newValue)
                        }
                        props.updateCoHostResponsibility(updated)
                    }
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                props.onModifyCoHostSettings(
                    com.mediasfu.sdk.ui.components.cohost.ModifyCoHostSettingsOptions(
                        roomName = props.roomName,
                        socket = props.socket,
                        showAlert = props.showAlert,
                        selectedParticipant = selectedCoHost,
                        coHost = props.currentCohost,
                        coHostResponsibility = props.coHostResponsibility,
                        updateCoHost = props.updateCoHost,
                        updateCoHostResponsibility = props.updateCoHostResponsibility,
                        updateIsCoHostModalVisible = props.updateIsCoHostModalVisible
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update Co-Host")
        }
    }
}

@Composable
private fun ShareEventModalContentInternal(props: ShareEventModalProps) {
    val clipboard = LocalClipboardManager.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Share Event",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        // Event Link
        OutlinedTextField(
            value = props.shareLink,
            onValueChange = { },
            readOnly = true,
            label = { Text("Event Link") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(props.shareLink))
                    props.state.showAlert("Link copied to clipboard", "success")
                }) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy link")
                }
            }
        )
        
        // Room Name
        OutlinedTextField(
            value = props.roomName,
            onValueChange = { },
            readOnly = true,
            label = { Text("Room Name") },
            modifier = Modifier.fillMaxWidth()
        )
        
        // Admin Passcode (host only)
        val passcode = props.adminPasscode
        if (!passcode.isNullOrBlank()) {
            OutlinedTextField(
                value = passcode,
                onValueChange = { },
                readOnly = true,
                label = { Text("Admin Passcode") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(passcode))
                        props.state.showAlert("Passcode copied to clipboard", "success")
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy passcode")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            onClick = {
                val shareText = buildString {
                    append("Join my MediaSFU event!\n")
                    append("Room: ${props.roomName}\n")
                    append("Link: ${props.shareLink}")
                }
                clipboard.setText(AnnotatedString(shareText))
                props.state.showAlert("Event details copied!", "success")
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = props.shareButtonsEnabled
        ) {
            Icon(Icons.Rounded.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Copy All Details")
        }
    }
}

@Composable
private fun BreakoutRoomsModalContentInternal(props: BreakoutRoomsModalOptions) {
    val breakoutRooms = props.parameters.breakoutRooms
    val isHost = props.parameters.canStartBreakout
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Breakout Rooms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (isHost) {
                FilledTonalButton(onClick = {
                    // Add a new empty room
                    val newRooms = breakoutRooms + listOf(emptyList())
                    props.parameters.updateBreakoutRooms(newRooms)
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Room")
                }
            }
        }
        
        if (breakoutRooms.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No breakout rooms created yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(breakoutRooms) { index, room ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Room ${index + 1}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${room.size} participants",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            if (isHost) {
                                IconButton(onClick = {
                                    // Remove this room
                                    val newRooms = breakoutRooms.toMutableList().apply { removeAt(index) }
                                    props.parameters.updateBreakoutRooms(newRooms)
                                }) {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = "Delete room",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Start/Stop Breakout Rooms button
        if (isHost && breakoutRooms.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            
            if (!props.parameters.breakOutRoomStarted) {
                Button(
                    onClick = { props.parameters.updateBreakOutRoomStarted(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Breakout Rooms")
                }
            } else {
                OutlinedButton(
                    onClick = { 
                        props.parameters.updateBreakOutRoomEnded(true)
                        props.parameters.updateBreakOutRoomStarted(false)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("End Breakout Rooms")
                }
            }
        }
    }
}

@Composable
private fun PollModalContentInternal(state: MediasfuGenericState) {
    val polls = state.polls.polls
    val isHost = state.room.youAreHost
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Polls",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            
            if (isHost) {
                FilledTonalButton(onClick = { 
                    state.showAlert("Poll creation is handled via the server", "info")
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Poll")
                }
            }
        }
        
        if (polls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No polls available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(polls) { poll ->
                    SimplePollCard(
                        poll = poll,
                        isHost = isHost,
                        onVote = { _ ->
                            state.showAlert("Voting is handled through the server", "info")
                        },
                        onEndPoll = {
                            state.showAlert("Ending polls is handled through the server", "info")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SimplePollCard(
    poll: Poll,
    isHost: Boolean,
    onVote: (Int) -> Unit,
    onEndPoll: () -> Unit
) {
    // votes is List<Int> - each Int represents vote count for that option index
    val totalVotes = poll.votes.sum()
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = poll.question,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                
                if (poll.status == "active") {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            poll.options.forEachIndexed { index, option ->
                val voteCount = poll.votes.getOrElse(index) { 0 }
                val percentage = if (totalVotes > 0) (voteCount * 100f / totalVotes) else 0f
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(option, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$voteCount votes (${percentage.toInt()}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    LinearProgressIndicator(
                        progress = { percentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
            
            if (poll.status == "active") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    poll.options.forEachIndexed { index, option ->
                        OutlinedButton(
                            onClick = { onVote(index) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(option, maxLines = 1)
                        }
                    }
                }
                
                if (isHost) {
                    TextButton(
                        onClick = onEndPoll,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("End Poll")
                    }
                }
            }
        }
    }
}

/**
 * ConfigureWhiteboardModalContentBody - Full content body for the ConfigureWhiteboard modal
 * Extracted from ConfigureWhiteboardModal for use in the unified modal system.
 * Contains participant management, start/stop whiteboard functionality.
 */
@Composable
private fun ConfigureWhiteboardModalContentBody(
    options: ConfigureWhiteboardModalOptions,
    modifier: Modifier = Modifier
) {
    val params = options.parameters
    val scope = rememberCoroutineScope()

    // State for participant lists
    var assignedParticipants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var pendingParticipants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var isEditing by remember { mutableStateOf(false) }
    var canStartWhiteboard by remember { mutableStateOf(params.canStartWhiteboard) }

    // Initialize participants
    LaunchedEffect(Unit) {
        // Get all non-host participants
        val allParticipants = params.participants.filter { it.islevel != "2" }

        // Separate into assigned and pending
        assignedParticipants = allParticipants.filter { it.useBoard == true }
        pendingParticipants = allParticipants.filter { it.useBoard != true }

        // Check if we can start whiteboard
        canStartWhiteboard = assignedParticipants.size <= params.itemPageLimit
        params.updateCanStartWhiteboard(canStartWhiteboard)
    }

    // Check if we can start whiteboard
    fun checkCanStartWhiteboard() {
        val isValid = assignedParticipants.size <= params.itemPageLimit
        canStartWhiteboard = isValid
        params.updateCanStartWhiteboard(isValid)
    }

    // Add participant to assigned list
    fun addParticipant(participant: Participant) {
        pendingParticipants = pendingParticipants - participant
        assignedParticipants = assignedParticipants + participant
        isEditing = true
        checkCanStartWhiteboard()
    }

    // Remove participant from assigned list
    fun removeParticipant(participant: Participant) {
        assignedParticipants = assignedParticipants - participant
        pendingParticipants = pendingParticipants + participant
        isEditing = true
        checkCanStartWhiteboard()
    }

    fun buildWhiteboardPayload(
        usersData: List<Map<String, Any?>>, 
        roomName: String
    ): Map<String, Any?> = mapOf(
        "whiteboardUsers" to usersData,
        "roomName" to roomName
    )

    fun emitWhiteboardEvent(
        event: String,
        payload: Map<String, Any?>,
        onSuccess: () -> Unit,
        failureMessage: String
    ) {
        val socket = params.socket ?: run {
            scope.launch {
                params.showAlert?.invoke("Socket connection not available", "danger", 3000L)
            }
            return
        }

        try {
            socket.emitWithAck(event, payload) { response ->
                val (success, reason) = response.toWhiteboardAckResult()
                scope.launch {
                    if (success) {
                        onSuccess()
                    } else {
                        params.showAlert?.invoke(reason ?: failureMessage, "danger", 3000L)
                    }
                }
            }
        } catch (error: Exception) {
            scope.launch {
                params.showAlert?.invoke(error.message ?: failureMessage, "danger", 3000L)
            }
        }
    }

    // Save assignments
    fun saveAssignments() {
        if (assignedParticipants.size > params.itemPageLimit) {
            scope.launch {
                params.showAlert?.invoke("Participant limit exceeded", "danger", 3000L)
            }
            return
        }

        val whiteboardUsersData = assignedParticipants.map { participant ->
            mapOf("name" to participant.name, "useBoard" to true)
        }

        val updateLocalUsers = {
            params.updateWhiteboardUsers(
                assignedParticipants.map { participant ->
                    WhiteboardUser(name = participant.name, useBoard = true)
                }
            )
        }

        if (params.whiteboardStarted && !params.whiteboardEnded) {
            emitWhiteboardEvent(
                event = "updateWhiteboard",
                payload = buildWhiteboardPayload(whiteboardUsersData, params.roomName),
                onSuccess = {
                    updateLocalUsers()
                    params.showAlert?.invoke("Whiteboard users updated", "success", 3000L)
                },
                failureMessage = "Failed to update whiteboard users"
            )
        } else {
            updateLocalUsers()
            scope.launch {
                params.showAlert?.invoke("Whiteboard saved successfully", "success", 3000L)
            }
        }

        checkCanStartWhiteboard()
        isEditing = false
    }

    // Validate start whiteboard
    fun validateStartWhiteboard(): Boolean {
        // Check if screen sharing is active
        if (params.shareScreenStarted || params.shared) {
            params.showAlert?.invoke(
                "Cannot start whiteboard while screen sharing is active",
                "danger",
                3000L
            )
            return false
        }

        // Check if breakout rooms are active
        if (params.breakOutRoomStarted && !params.breakOutRoomEnded) {
            params.showAlert?.invoke(
                "Cannot start whiteboard while breakout rooms are active",
                "danger",
                3000L
            )
            return false
        }

        return true
    }

    // Start whiteboard
    fun startWhiteboard() {
        if (!validateStartWhiteboard()) return

        if (params.socket == null) {
            scope.launch {
                params.showAlert?.invoke("Socket connection not available", "danger", 3000L)
            }
            return
        }

        val whiteboardUsersData = assignedParticipants.map { participant ->
            mapOf("name" to participant.name, "useBoard" to true)
        }

        val emitName = if (params.whiteboardStarted && !params.whiteboardEnded) {
            "updateWhiteboard"
        } else {
            "startWhiteboard"
        }

        emitWhiteboardEvent(
            event = emitName,
            payload = buildWhiteboardPayload(whiteboardUsersData, params.roomName),
            onSuccess = {
                params.updateWhiteboardStarted(true)
                params.updateWhiteboardEnded(false)
                params.updateWhiteboardUsers(
                    assignedParticipants.map { participant ->
                        WhiteboardUser(name = participant.name, useBoard = true)
                    }
                )
                params.updateCanStartWhiteboard(false)
                params.showAlert?.invoke("Whiteboard active", "success", 3000L)
                options.onClose()
                
                // Capture canvas stream if recording is active and whiteboard just started
                // This is for when the whiteboard is started AFTER recording started
                if (params.islevel == "2" && (params.recordStarted || params.recordResumed)) {
                    if (!(params.recordPaused || params.recordStopped) && 
                        params.recordingMediaOptions == "video") {
                        scope.launch {
                            try {
                                params.captureCanvasStream?.invoke()
                            } catch (_: Exception) { }
                        }
                    }
                }
            },
            failureMessage = "Failed to start whiteboard"
        )
    }

    // Stop whiteboard
    fun stopWhiteboard() {
        emitWhiteboardEvent(
            event = "stopWhiteboard",
            payload = mapOf("roomName" to params.roomName),
            onSuccess = {
                params.updateWhiteboardStarted(false)
                params.updateWhiteboardEnded(true)
                params.updateCanStartWhiteboard(true)
                params.showAlert?.invoke("Whiteboard stopped successfully", "success", 3000L)
                options.onClose()
            },
            failureMessage = "Failed to stop whiteboard"
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Status indicator
        if (params.whiteboardStarted && !params.whiteboardEnded) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Color(0xFF4CAF50).copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Whiteboard is active",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Two column layout for participants
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Assigned participants
            WhiteboardParticipantList(
                modifier = Modifier.weight(1f),
                title = "Assigned",
                participants = assignedParticipants,
                onAction = { removeParticipant(it) },
                actionIcon = Icons.Rounded.Remove,
                actionColor = Color.Red,
                emptyMessage = "No participants assigned"
            )

            // Pending participants
            WhiteboardParticipantList(
                modifier = Modifier.weight(1f),
                title = "Available",
                participants = pendingParticipants,
                onAction = { addParticipant(it) },
                actionIcon = Icons.Rounded.Add,
                actionColor = Color.Green,
                emptyMessage = "No participants available"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Participant limit warning
        if (assignedParticipants.size > params.itemPageLimit) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = Color.Red.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "⚠️ Participant limit exceeded (max: ${params.itemPageLimit})",
                    color = Color.Red,
                    modifier = Modifier.padding(12.dp),
                    fontSize = 14.sp
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Save button (only if editing)
            if (isEditing) {
                Button(
                    onClick = { saveAssignments() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Save,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }

            // Start button
            if (canStartWhiteboard && (!params.whiteboardStarted || params.whiteboardEnded)) {
                Button(
                    onClick = { startWhiteboard() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start")
                }
            }

            // Update and Stop buttons (when whiteboard is running)
            if (params.whiteboardStarted && !params.whiteboardEnded) {
                Button(
                    onClick = { saveAssignments() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Update")
                }

                Button(
                    onClick = { stopWhiteboard() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop")
                }
            }
        }
    }
}

/**
 * WhiteboardParticipantList - A reusable component for displaying a list of participants in whiteboard config
 */
@Composable
private fun WhiteboardParticipantList(
    modifier: Modifier = Modifier,
    title: String,
    participants: List<Participant>,
    onAction: (Participant) -> Unit,
    actionIcon: ImageVector,
    actionColor: Color,
    emptyMessage: String
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF2196F3), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${participants.size}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // Participant list
        if (participants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2D2D2D))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Color(0xFF2D2D2D)),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(participants) { participant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = participant.name,
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                        IconButton(
                            onClick = { onAction(participant) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                actionIcon,
                                contentDescription = "Action",
                                tint = actionColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Any?.toWhiteboardAckResult(): Pair<Boolean, String?> = when (this) {
    is Map<*, *> -> {
        val success = (this["success"] as? Boolean) ?: false
        val reason = this["reason"] as? String
        success to reason
    }
    is Array<*> -> this.firstOrNull().toWhiteboardAckResult()
    is List<*> -> this.firstOrNull().toWhiteboardAckResult()
    is Boolean -> this to null
    is String -> when (this.lowercase()) {
        "true" -> true to null
        "false" -> false to null
        else -> false to this
    }
    else -> false to null
}

// ==================== End of Embedded Modal Content Composables ====================

/**
 * MainContainerInline - Inline main container layout matching React/Flutter structure
 * Replaces the extracted ConferenceRoomContent with inline code for easier comparison
 */
@Composable
private fun MainContainerInline(state: MediasfuGenericState) {
    val mainContainerComponent = remember {
        DefaultMainContainerComponent(
            MainContainerComponentOptions(
                backgroundColor = 0xFF172645.toInt()
            )
        )
    }

    mainContainerComponent.renderCompose {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val shouldShowSubAspect = state.room.eventType == EventType.WEBINAR ||
                state.room.eventType == EventType.CONFERENCE
            val controlFraction = if (shouldShowSubAspect) {
                state.display.controlHeight.toFloat().coerceIn(0f, 0.5f)
            } else {
                0f
            }

            // MainAspect
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - controlFraction, fill = true)
            ) {
                MainAspectInline(state, shouldShowSubAspect)
            }

            // SubAspect (control buttons)
            if (shouldShowSubAspect) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    SubAspectInline(state)
                }
            }
        }
    }
}

@Composable
private fun MiniAudioOverlay(
    miniAudios: List<MediaSfuUIComponent>,
    modifier: Modifier = Modifier
) {
    val audioItems = miniAudios.mapNotNull { it as? MiniAudio }.filter { it.isVisible }
    val dragOffset = remember { mutableStateOf(Offset.Zero) }

    AnimatedVisibility(
        visible = audioItems.isNotEmpty(),
        enter = fadeIn(animationSpec = TweenSpec(durationMillis = 150)),
        exit = fadeOut(animationSpec = TweenSpec(durationMillis = 150)),
        modifier = modifier
            .offset { IntOffset(dragOffset.value.x.roundToInt(), dragOffset.value.y.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newOffset = dragOffset.value + dragAmount
                    dragOffset.value = newOffset
                }
            }
    ) {
        LazyRow(
            modifier = Modifier
                .background(Color(0xCC0A0F1C), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(audioItems, key = { mini -> mini.options.name }) { mini ->
                mini.renderCompose()
            }
        }
    }
}

/**
 * MainAspectInline - Main video grid layout inline (matches React MainAspect)
 * Contains MainScreen > MainGrid + OtherGrid
 */
@Composable
private fun MainAspectInline(
    state: MediasfuGenericState,
    shouldShowSubAspect: Boolean
) {
    val display = state.display
    val streams = state.streams
    val participants = state.room.filteredParticipants
    val participantList = participants.toList()

    val customComponent = state.options.customComponent
    if (customComponent != null) {
        customComponent.invoke(state)
        return
    }

    val placeholderStreams = remember(participantList) { buildPlaceholderStreams(participantList) }

    val totalPages = state.totalPages()
    val showPagination = display.doPaginate && totalPages > 1

    val paginatedStreamsForPage = if (display.doPaginate) {
        streams.paginatedStreams.getOrNull(display.currentUserPage)?.toList().orEmpty()
    } else {
        emptyList()
    }

    val activeMainStreams = when {
        paginatedStreamsForPage.isNotEmpty() -> paginatedStreamsForPage
        streams.lStreams.isNotEmpty() -> streams.lStreams.toList()
        streams.currentStreams.isNotEmpty() -> streams.currentStreams.toList()
        else -> emptyList()
    }
    
    // Debug stream sources
    
    val audioDecibels = remember(state.parameters.audioDecibels) {
        state.parameters.audioDecibels.toList()
    }

    // React pattern: prepopulateUserMedia builds VideoCard/AudioCard/MiniCard components
    // and stores them in mainGridStream. If available, use those directly.
    // Otherwise fall back to converting raw streams to components.
    // Note: We read directly from streams.mainGridStream (a SnapshotStateList) for reactivity
    val prebuiltComponents = streams.mainGridStream.toList()
    val mainGridComponents: List<MediaSfuUIComponent> = if (prebuiltComponents.isNotEmpty()) {
        prebuiltComponents
    } else {
        // Priority 2: Convert raw streams to components
        val rawStreams = if (activeMainStreams.isNotEmpty()) {
            activeMainStreams
        } else {
            placeholderStreams
        }
        rawStreams.toDisplayComponents(
            participants = participants,
            audioDecibels = audioDecibels,
            isVideoCard = true,
            showControls = false,
            eventType = state.room.eventType
        )
    }

    // No recalculation - use stored components directly from display.otherGridStreams
    // which are set by updateOtherGridStreams() called from addVideosGrid

    val mainAspectComponent = remember(shouldShowSubAspect, display.controlHeight) {
        DefaultMainAspectComponent(
            MainAspectComponentOptions(
                backgroundColor = 0xFF172645.toInt(),
                defaultFraction = 1.0 - display.controlHeight,
                showControls = shouldShowSubAspect,
                updateIsWideScreen = display::setWideScreenFlag,
                updateIsMediumScreen = display::setMediumScreenFlag,
                updateIsSmallScreen = display::setSmallScreenFlag
            )
        )
    }

    val mainScreenComponent = remember(display.mainHeightWidth) {
        DefaultMainScreenComponent(
            MainScreenComponentOptions(
                backgroundColor = 0xFF172645.toInt(),
                mainSize = display.mainHeightWidth,
                showAspect = true
            )
        )
    }

    mainAspectComponent.renderCompose {
        Box(modifier = Modifier.fillMaxSize()) {
            mainScreenComponent.renderCompose {
                MainScreenInline(
                    state = state,
                    display = display,
                    mainGridComponents = mainGridComponents,
                    showPagination = showPagination,
                    totalPages = totalPages
                )
            }

            MiniAudioOverlay(
                miniAudios = state.streams.audioOnlyStreams,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * MainScreenInline - Main screen layout (matches React MainScreen)
 * Contains MainGrid + OtherGrid side by side or stacked
 */
@Composable
private fun MainScreenInline(
    state: MediasfuGenericState,
    display: DisplayState,
    mainGridComponents: List<MediaSfuUIComponent>,
    showPagination: Boolean,
    totalPages: Int
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight

        val widthDp = when (maxWidth) {
            Dp.Infinity, Dp.Unspecified -> screenWidthDp
            else -> maxWidth
        }
        val heightDp = when (maxHeight) {
            Dp.Infinity, Dp.Unspecified -> screenHeightDp
            else -> maxHeight
        }.coerceAtLeast(0.dp)

        val widthPx = with(density) { widthDp.toPx().coerceAtLeast(0f) }
        val heightPx = with(density) { heightDp.toPx().coerceAtLeast(0f) }

        val wideThresholdPx = with(density) { 768.dp.toPx() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        
        val widthDp = maxWidth
        val heightDp = maxHeight
        
        // Calculate screen size flags
        val isWideScreen = widthDp > 768.dp
        val isMediumScreen = widthDp > 576.dp && widthDp <= 768.dp
        val isSmallScreen = widthDp <= 576.dp
        
        LaunchedEffect(isWideScreen, isMediumScreen, isSmallScreen) {
             display.setWideScreenFlag(isWideScreen)
             display.setMediumScreenFlag(isMediumScreen)
             display.setSmallScreenFlag(isSmallScreen)
        }

        val configuredMainPercent = display.mainHeightWidth
        val clampedMainPercent = when {
            configuredMainPercent.isNaN() || configuredMainPercent.isInfinite() -> 100.0
            configuredMainPercent < 0.0 -> 0.0
            configuredMainPercent > 100.0 -> 100.0
            else -> configuredMainPercent
        }

        val mainFraction = (clampedMainPercent / 100.0).toFloat().coerceIn(0f, 1f)
        val otherFraction = (1f - mainFraction).coerceIn(0f, 1f)
        val doStack = true
        val isWideStack = doStack && isWideScreen

        val mainWidthDp = when {
            !doStack -> widthDp
            isWideStack -> widthDp * mainFraction
            else -> widthDp
        }
        val otherWidthDp = when {
            !doStack -> widthDp
            isWideStack -> widthDp * otherFraction
            else -> widthDp
        }
        val mainHeightDp = when {
            !doStack -> heightDp
            isWideStack -> heightDp
            else -> heightDp * mainFraction
        }
        val otherHeightDp = when {
            !doStack -> heightDp
            isWideStack -> heightDp
            else -> heightDp * otherFraction
        }

        val componentSizes = remember(mainWidthDp, mainHeightDp, otherWidthDp, otherHeightDp) {
            ComponentSizes(
                mainWidth = with(density) { mainWidthDp.toPx().coerceAtLeast(0f) }.toDouble(),
                mainHeight = with(density) { mainHeightDp.toPx().coerceAtLeast(0f) }.toDouble(),
                otherWidth = with(density) { otherWidthDp.toPx().coerceAtLeast(0f) }.toDouble(),
                otherHeight = with(density) { otherHeightDp.toPx().coerceAtLeast(0f) }.toDouble()
            )
        }

        LaunchedEffect(componentSizes) {
            display.updateComponentSizes(componentSizes)
        }

        val shouldRenderMainGrid = mainFraction > 0f && mainHeightDp > 0.dp && mainWidthDp > 0.dp
        val shouldRenderOtherGrid = otherFraction > 0f && otherHeightDp > 0.dp && otherWidthDp > 0.dp
        
        val mainGridWidth = mainWidthDp.value.roundToInt().coerceAtLeast(0)
        val mainGridHeight = mainHeightDp.value.roundToInt().coerceAtLeast(0)
        val otherGridWidth = otherWidthDp.value.roundToInt().coerceAtLeast(0)
        val otherGridHeight = otherHeightDp.value.roundToInt().coerceAtLeast(0)

        val hasMainHeight = shouldRenderMainGrid && mainGridHeight > 0 && mainGridWidth > 0
        val hasOtherHeight = shouldRenderOtherGrid && otherGridHeight > 0 && otherGridWidth > 0

        val mainWeight = when {
            !shouldRenderMainGrid -> 0f
            shouldRenderOtherGrid -> mainFraction.coerceAtLeast(0.001f)
            else -> 1f
        }
        val otherWeight = when {
            !shouldRenderOtherGrid -> 0f
            shouldRenderMainGrid -> (otherFraction).coerceAtLeast(0.001f)
            else -> 1f
        }

        val (mainRows, mainColumns) = remember(mainGridComponents) {
            val count = mainGridComponents.size.coerceAtLeast(1)
            val estimatedRows = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
            val estimatedColumns = ((count + estimatedRows - 1) / estimatedRows).coerceAtLeast(1)
            estimatedRows to estimatedColumns
        }

        val flexibleVideo = remember(
            mainGridComponents,
            mainGridWidth,
            mainGridHeight,
            mainRows,
            mainColumns,
            shouldRenderMainGrid
        ) {
            DefaultFlexibleVideo(
                FlexibleVideoOptions(
                    customWidth = mainGridWidth,
                    customHeight = mainGridHeight,
                    rows = mainRows,
                    columns = mainColumns,
                    componentsToRender = mainGridComponents,
                    backgroundColor = 0xFF172645.toInt(),
                    showAspect = shouldRenderMainGrid && mainGridComponents.isNotEmpty()
                )
            )
        }

        val stateOtherGridStreams = display.otherGridStreams
        val resolvedPrimaryComponents = stateOtherGridStreams.getOrNull(0).orEmpty()
        val resolvedAlternateComponents = stateOtherGridStreams.getOrNull(1).orEmpty()

        val shouldShowPrimaryOtherGrid = resolvedPrimaryComponents.isNotEmpty()
        val shouldShowAlternateOtherGrid = resolvedAlternateComponents.isNotEmpty()

        // React just uses gridRows/gridCols directly from state - no fallback calculations
        val gridRows = display.gridRows
        val gridCols = display.gridCols
        val altGridRows = display.altGridRows
        val altGridCols = display.altGridCols
        
        // Read gridSizes outside remember to ensure reactive updates
        val currentGridSizes = display.gridSizes
        val gridWidth = currentGridSizes.gridWidth
        val gridHeight = currentGridSizes.gridHeight
        val altGridWidth = currentGridSizes.altGridWidth
        val altGridHeight = currentGridSizes.altGridHeight

        val primaryFlexGrid = remember(
            resolvedPrimaryComponents,
            gridRows,
            gridCols,
            gridWidth,
            gridHeight
        ) {
            DefaultFlexibleGrid(
                FlexibleGridOptions(
                    customWidth = gridWidth.toInt(),
                    customHeight = gridHeight.toInt(),
                    rows = gridRows,
                    columns = gridCols,
                    componentsToRender = resolvedPrimaryComponents,
                    backgroundColor = 0xFF0F1A2D.toInt(),
                    showAspect = true
                )
            )
        }

        val alternateFlexGrid = remember(
            resolvedAlternateComponents,
            altGridRows,
            altGridCols,
            altGridWidth,
            altGridHeight
        ) {
            DefaultFlexibleGrid(
                FlexibleGridOptions(
                    customWidth = altGridWidth.toInt(),
                    customHeight = altGridHeight.toInt(),
                    rows = altGridRows,
                    columns = altGridCols,
                    componentsToRender = resolvedAlternateComponents,
                    backgroundColor = 0xFF0F1A2D.toInt(),
                    showAspect = true
                )
            )
        }

        LaunchedEffect(
            gridRows,
            gridCols,
            altGridRows,
            altGridCols,
            display.componentSizes,
            display.doPaginate,
            display.paginationDirection,
            display.paginationHeightWidth,
            resolvedPrimaryComponents.size,
            resolvedAlternateComponents.size
        ) {
            if (display.componentSizes.otherWidth > 0.0) {
                
                // Calculate total actual rows across BOTH grids (matches React pattern)
                val primaryActualRows = if (resolvedPrimaryComponents.isNotEmpty()) gridRows.coerceAtLeast(1) else 0
                val altActualRows = if (resolvedAlternateComponents.isNotEmpty()) altGridRows.coerceAtLeast(1) else 0
                val totalActualRows = primaryActualRows + altActualRows
                
                updateMiniCardsGridImpl(
                    rows = gridRows,
                    cols = gridCols,
                    defal = true,
                    actualRows = totalActualRows,  // Use total rows, not just primary
                    gridSizes = display.gridSizes,
                    paginationDirection = display.paginationDirection,
                    paginationHeightWidth = display.paginationHeightWidth,
                    doPaginate = display.doPaginate,
                    componentSizes = display.componentSizes,
                    eventType = state.room.eventType.name,
                    updateGridRows = display::updateGridRows,
                    updateGridCols = display::updateGridCols,
                    updateAltGridRows = display::updateAltGridRows,
                    updateAltGridCols = display::updateAltGridCols,
                    updateGridSizes = display::updateGridSizes
                )

                updateMiniCardsGridImpl(
                    rows = altGridRows,
                    cols = altGridCols,
                    defal = false,
                    actualRows = totalActualRows,  // Use same total rows for alternate grid
                    gridSizes = display.gridSizes,
                    paginationDirection = display.paginationDirection,
                    paginationHeightWidth = display.paginationHeightWidth,
                    doPaginate = display.doPaginate,
                    componentSizes = display.componentSizes,
                    eventType = state.room.eventType.name,
                    updateGridRows = display::updateGridRows,
                    updateGridCols = display::updateGridCols,
                    updateAltGridRows = display::updateAltGridRows,
                    updateAltGridCols = display::updateAltGridCols,
                    updateGridSizes = display::updateGridSizes
                )
            }
        }

        LaunchedEffect(shouldShowPrimaryOtherGrid) {
            display.setAddGridEnabled(shouldShowPrimaryOtherGrid)
        }

        LaunchedEffect(shouldShowAlternateOtherGrid) {
            display.setAddAltGridEnabled(shouldShowAlternateOtherGrid)
        }

        val hasMainPane = hasMainHeight
        val hasOtherPane = shouldRenderOtherGrid && hasOtherHeight
        val hasBothPanes = hasMainPane && hasOtherPane
        val resolvedMainWeight = if (hasBothPanes) mainWeight.coerceAtLeast(0.001f) else 1f
        val resolvedOtherWeight = if (hasBothPanes) otherWeight.coerceAtLeast(0.001f) else 1f
        
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isWideStack) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasMainPane) {
                        Box(
                            modifier = Modifier
                                .weight(resolvedMainWeight, fill = true)
                                .fillMaxHeight()
                        ) {
                            MainGridInline(
                                state = state,
                                display = display,
                                mainGridComponents = mainGridComponents,
                                flexibleVideo = flexibleVideo,
                                mainGridWidth = mainGridWidth,
                                mainGridHeight = mainGridHeight,
                                shouldRenderMainGrid = shouldRenderMainGrid
                            )
                        }
                    }

                    if (hasOtherPane) {
                        Box(
                            modifier = Modifier
                                .weight(resolvedOtherWeight, fill = true)
                                .fillMaxHeight()
                        ) {
                            OtherGridInline(
                                state = state,
                                display = display,
                                otherGridWidth = otherGridWidth,
                                otherGridHeight = otherGridHeight,
                                primaryFlexGrid = primaryFlexGrid,
                                alternateFlexGrid = alternateFlexGrid
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (hasMainPane) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(resolvedMainWeight, fill = true)
                        ) {
                            MainGridInline(
                                state = state,
                                display = display,
                                mainGridComponents = mainGridComponents,
                                flexibleVideo = flexibleVideo,
                                mainGridWidth = mainGridWidth,
                                mainGridHeight = mainGridHeight,
                                shouldRenderMainGrid = shouldRenderMainGrid
                            )
                        }
                    }

                    if (hasOtherPane) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(resolvedOtherWeight, fill = true)
                        ) {
                            OtherGridInline(
                                state = state,
                                display = display,
                                otherGridWidth = otherGridWidth,
                                otherGridHeight = otherGridHeight,
                                primaryFlexGrid = primaryFlexGrid,
                                alternateFlexGrid = alternateFlexGrid
                            )
                        }
                    }
                }
            }

            if (showPagination) {
                val coroutineScope = rememberCoroutineScope()
                val pagination = remember(
                    display.currentUserPage,
                    totalPages,
                    state.parameters
                ) {
                    DefaultPagination(
                        PaginationOptions(
                            totalPages = totalPages,
                            currentUserPage = display.currentUserPage,
                            handlePageChange = { options ->
                                coroutineScope.launch {
                                    generatePageContent(options)
                                }
                            },
                            parameters = state.paginationParameters,
                            position = "middle",
                            location = "middle",
                            direction = "horizontal",
                            backgroundColor = 0x331E88E5,
                            activeColor = 0xFF1E88E5.toInt(),
                            inactiveColor = 0xFFFFFFFF.toInt()
                        )
                    )
                }
                pagination.renderCompose()
            }
        }
    }
    }
}

@Composable
private fun DeviceOrientationEffect(state: MediasfuGenericState) {
    BoxWithConstraints {
        val density = LocalDensity.current
        val width = maxWidth
        val height = maxHeight
        
        LaunchedEffect(width, height) {
            with(density) {
                val availableHeightPx = height.toPx()
                val orientation = if (width > height) "landscape" else "portrait"
                state.handleOrientationFromLayout(orientation, availableHeightPx)
            }
        }
    }
}

/**
 * MainGridInline - Main grid component (matches React MainGrid)
 */
@Composable
private fun BoxScope.MainGridInline(
    state: MediasfuGenericState,
    display: DisplayState,
    mainGridComponents: List<MediaSfuUIComponent>,
    flexibleVideo: FlexibleVideo,
    mainGridWidth: Int,
    mainGridHeight: Int,
    shouldRenderMainGrid: Boolean
) {
    val mainGridComponent = remember(
        mainGridComponents,
        mainGridWidth,
        mainGridHeight,
        display.mainHeightWidth,
        state.meeting.isVisible,
        shouldRenderMainGrid
    ) {
        DefaultMainGridComponent(
            MainGridComponentOptions(
                height = mainGridHeight,
                width = mainGridWidth,
                backgroundColor = 0xFF172645.toInt(),
                mainSize = display.mainHeightWidth,
                showAspect = shouldRenderMainGrid,
                timeBackgroundColor = 0xFF2E7D32.toInt(),
                showTimer = state.meeting.isVisible
            )
        )
    }

    val timerSlot: @Composable BoxScope.() -> Unit = {
        MeetingProgressTimerBadge(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }

    mainGridComponent.renderCompose(
        renderTimer = state.meeting.isVisible,
        timer = if (state.meeting.isVisible) timerSlot else null
    ) {
        if (shouldRenderMainGrid && mainGridComponents.isNotEmpty()) {
            flexibleVideo.renderCompose()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C2B4A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for streams...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Whiteboard canvas - shown when whiteboard is active (matching React/Flutter)
        val whiteboardOptions = state.createWhiteboardOptions(
            width = mainGridWidth.toFloat(),
            height = mainGridHeight.toFloat()
        )
        if (whiteboardOptions.showAspect) {
            Whiteboard(
                options = whiteboardOptions,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Floating control buttons for BROADCAST event type (bottom-right, vertical)
        // Matches Flutter's controlBroadcastButtons rendered via _controlButtonsTouchBuilder
        if (state.room.eventType == EventType.BROADCAST) {
            val isHost = state.room.islevel == "2"
            val showRecordButtons = state.recording.showRecordButtons
            
            val broadcastButtons = remember(
                state.media.audioAlreadyOn,
                state.media.videoAlreadyOn,
                state.messaging.showMessagesBadge,
                state.room.participantsCounter,
                state.room.islevel
            ) {
                state.controlBroadcastButtons()
            }
            
            FloatingControlButtons(
                buttons = broadcastButtons,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
            
            // Record button(s) for broadcast - positioned at bottom-center (host only)
            // Shows single record button before recording starts, expanded buttons after
            if (isHost) {
                if (!showRecordButtons) {
                    // Single record button (before recording starts)
                    val singleRecordButton = remember { state.recordButton() }
                    FloatingRecordButtons(
                        buttons = singleRecordButton,
                        recordingProgressTime = "",
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                } else {
                    // Expanded record buttons (after recording starts)
                    val recordButtons = remember(
                        state.recording.recordPaused
                    ) {
                        state.recordButtonsTouch()
                    }
                    FloatingRecordButtons(
                        buttons = recordButtons,
                        recordingProgressTime = state.recording.recordingProgressTime,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
            
            // Meeting progress timer badge for broadcast
            MeetingProgressTimerBadge(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
            
            // Participants counter badge for broadcast (bottom-left)
            val participantsCount = state.room.participantsCounter
            if (participantsCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = participantsCount.toString(),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
        
        // Floating control buttons for CHAT event type (bottom-right, vertical)
        // Matches Flutter's controlChatButtons rendered via _controlButtonsTouchBuilder
        if (state.room.eventType == EventType.CHAT) {
            val chatButtons = remember(
                state.media.audioAlreadyOn,
                state.media.videoAlreadyOn,
                state.messaging.showMessagesBadge,
                state.room.islevel
            ) {
                state.controlChatButtons()
            }
            
            FloatingControlButtons(
                buttons = chatButtons,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
            
            // Meeting progress timer badge for chat (when mainHeightWidth == 0)
            if (display.mainHeightWidth == 0.0) {
                MeetingProgressTimerBadge(
                    state = state,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
                
                // Participants counter badge for chat (top-right, when mainHeightWidth == 0)
                val participantsCount = state.room.participantsCounter
                if (participantsCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = participantsCount.toString(),
                                color = Color.White,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * OtherGridInline - Other grid component (matches React OtherGrid)
 */
@Composable
private fun BoxScope.OtherGridInline(
    state: MediasfuGenericState,
    display: DisplayState,
    otherGridWidth: Int,
    otherGridHeight: Int,
    primaryFlexGrid: FlexibleGrid,
    alternateFlexGrid: FlexibleGrid
) {
    val otherGridComponent = remember(
        otherGridWidth,
        otherGridHeight,
        display.altGridRows
    ) {
        DefaultOtherGridComponent(
            OtherGridComponentOptions(
                height = otherGridHeight,
                width = otherGridWidth,
                backgroundColor = 0xFF0F1A2D.toInt(),
                gridSize = display.altGridRows.toDouble(),
                showAspect = true
            )
        )
    }

    otherGridComponent.renderCompose {
        
        val density = LocalDensity.current
        
        // Convert pixel dimensions to DP for padding
        val paginationHeightWidthDp = with(density) { display.paginationHeightWidth.toInt().toDp() }
        
        val otherGridHeightDp = with(density) { display.componentSizes.otherHeight.toInt().toDp() }
        val gridHeightDp = with(density) { display.gridSizes.gridHeight.toInt().toDp() }
        val altGridHeightDp = with(density) { display.gridSizes.altGridHeight.toInt().toDp() }
        
        // Use Column to stack grids vertically (matches React's natural flow)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(
                    top = if (display.doPaginate) {
                        if (display.paginationDirection == "horizontal") {
                            paginationHeightWidthDp
                        } else 0.dp
                    } else 0.dp,
                    start = if (display.doPaginate) {
                        if (display.paginationDirection == "vertical") {
                            paginationHeightWidthDp
                        } else 0.dp
                    } else 0.dp
                )
        ) {
            primaryFlexGrid.renderCompose()
            
            alternateFlexGrid.renderCompose()
        }
    }
    
    // Floating control buttons for CHAT event type (bottom-right, vertical)
    // Matches Flutter's controlChatButtons rendered via _controlButtonsTouchBuilder in OtherGrid
    // Note: These are rendered outside otherGridComponent.renderCompose to use BoxScope.align()
    if (state.room.eventType == EventType.CHAT) {
        val chatButtons = remember(
            state.media.audioAlreadyOn,
            state.media.videoAlreadyOn,
            state.messaging.showMessagesBadge,
            state.room.islevel
        ) {
            state.controlChatButtons()
        }
        
        FloatingControlButtons(
            buttons = chatButtons,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
        
        // Meeting progress timer badge for chat (when mainHeightWidth == 0)
        if (display.mainHeightWidth == 0.0) {
            MeetingProgressTimerBadge(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            )
        }
    }
}

/**
 * SubAspectInline - Control buttons (matches React SubAspect)
 */
@Composable
private fun SubAspectInline(state: MediasfuGenericState) {
    val subAspectComponent = remember {
        DefaultSubAspectComponent(
            SubAspectComponentOptions(
                backgroundColor = 0xFF0F1A2D.toInt(),
                showControls = true,
                defaultFraction = 40.0
            )
        )
    }

    subAspectComponent.renderCompose {
        val controlButtons = remember(
            state.media.audioAlreadyOn,
            state.media.videoAlreadyOn,
            state.media.screenAlreadyOn,
            state.media.isScreenShareLoading,
            state.recording.recordStarted,
            state.messaging.showMessagesBadge,
            state.room.participantsCounter,
            state.requests.totalPending
        ) {
            state.primaryControlButtons(includeExtended = false)
        }

        val visibleButtons = remember(controlButtons) {
            controlButtons.filter { it.isVisible }
        }

        if (visibleButtons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleButtons.forEach { button ->
                    val iconVector = if (button.isActive && button.alternateIcon != null) {
                        button.alternateIcon
                    } else {
                        button.icon
                    }
                    val baseTint = if (button.isActive) button.activeTint else button.inactiveTint
                    val iconTint = if (button.isEnabled) baseTint else baseTint.copy(alpha = 0.4f)
                    val backgroundTint = if (button.isActive) {
                        button.activeTint.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(backgroundTint)
                            .clickable(enabled = button.isEnabled && !button.isLoading) { button.onClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (button.isLoading) {
                            // Show loading spinner instead of icon
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = button.activeTint,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = button.label,
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        button.badgeText?.let { badgeText ->
                            if (badgeText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(button.badgeColor, CircleShape)
                                        .size(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = 8.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(button.badgeColor, CircleShape)
                                        .size(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.DeviceOrientationEffect(state: MediasfuGenericState) {
    val density = LocalDensity.current
    val orientation = if (maxHeight >= maxWidth) "portrait" else "landscape"
    val availableHeightPx = with(density) { maxHeight.toPx() }
    val eventType = state.room.eventType

    LaunchedEffect(orientation, availableHeightPx, eventType) {
        state.handleOrientationFromLayout(orientation, availableHeightPx)
    }
}

// WelcomePage, PreJoinOrWelcome, EventRoom, MeetingProgressTimerBadge, MainAspect, andControlStrip extracted to separate files

// Primary control buttons helper function
internal fun MediasfuGenericState.primaryControlButtons(includeExtended: Boolean = true): List<ControlButtonModel> {
    val mediaState = media
    val recordingState = recording
    val messagingState = messaging
    val participantsCount = room.participantsCounter
    val requestCount = requests.totalPending

    val participantsBadge = participantsCount.takeIf { it > 0 }?.let { count ->
        when {
            count > 99 -> "99+"
            else -> count.toString()
        }
    }
    val requestsBadge = requestCount.takeIf { it > 0 }?.let { count ->
        when {
            count > 99 -> "99+"
            else -> count.toString()
        }
    }

    val baseButtons = listOf(
        ControlButtonModel(
            label = if (mediaState.audioAlreadyOn) "Mute" else "Unmute",
            icon = Icons.Rounded.MicOff,
            alternateIcon = Icons.Rounded.Mic,
            isActive = mediaState.audioAlreadyOn,
            onClick = { toggleAudio() },
            activeTint = Color(0xFF52C41A)
        ),
        ControlButtonModel(
            label = if (mediaState.videoAlreadyOn) "Stop Video" else "Video",
            icon = Icons.Rounded.VideoCall,
            alternateIcon = Icons.Rounded.VideoCameraFront,
            isActive = mediaState.videoAlreadyOn,
            onClick = { toggleVideo() }
        ),
        ControlButtonModel(
            label = if (mediaState.isScreenShareLoading) "Loading..." else if (mediaState.screenAlreadyOn) "Stop Share" else "Share",
            icon = Icons.AutoMirrored.Rounded.ScreenShare,
            alternateIcon = Icons.AutoMirrored.Rounded.StopScreenShare,
            isActive = mediaState.screenAlreadyOn,
            isLoading = mediaState.isScreenShareLoading,
            isEnabled = !mediaState.isScreenShareLoading,
            onClick = { toggleScreenShare() },
            activeTint = Color(0xFFFAAD14)
        ),
        ControlButtonModel(
            label = "Hang Up",
            icon = Icons.Rounded.CallEnd,
            onClick = { openConfirmExit() },
            activeTint = Color(0xFFFF4D4F),
            inactiveTint = Color(0xFFFF4D4F)
        ),
        ControlButtonModel(
            label = "People",
            icon = Icons.Rounded.Group,
            onClick = { openParticipants() },
            badgeText = participantsBadge
        ),
        ControlButtonModel(
            label = "Menu",
            icon = Icons.Rounded.Menu,
            onClick = { toggleMenu() },
            badgeText = requestsBadge
        ),
        ControlButtonModel(
            label = "Chat",
            icon = Icons.AutoMirrored.Rounded.Chat,
            onClick = { openMessages() },
            badgeText = if (messagingState.showMessagesBadge) "" else null
        )
    )

    if (!includeExtended) {
        return baseButtons
    }

    val recordingSupported = recordingState.recordingAudioSupport || recordingState.recordingVideoSupport
    val recordingButtonEnabled = recordingSupported && recordingState.canRecord && parameters.canLaunchRecord
    val activePollCount = polls.polls.count { it.status.equals("active", ignoreCase = true) }
    val pollBadge = activePollCount.takeIf { it > 0 }?.let { it.toString() }

    val extendedButtons = listOf(
        ControlButtonModel(
            label = if (recordingState.recordStarted) "Stop Rec" else "Record",
            icon = Icons.Rounded.VideoCall,
            onClick = { toggleRecording() },
            isActive = recordingState.recordStarted,
            activeTint = Color(0xFFFF4D4F),
            isEnabled = recordingButtonEnabled,
            isVisible = recordingSupported
        ),
        ControlButtonModel(
            label = "Polls",
            icon = Icons.Rounded.HowToVote,
            onClick = { togglePollModal() },
            badgeText = pollBadge
        ),
        ControlButtonModel(
            label = "Settings",
            icon = Icons.Rounded.Settings,
            onClick = { openSettings() }
        )
    )

    return baseButtons + extendedButtons
}

/**
 * Control buttons for BROADCAST event type (floating on the main grid).
 * Matches Flutter's initializeControlBroadcastButtons.
 */
internal fun MediasfuGenericState.controlBroadcastButtons(): List<ControlButtonModel> {
    val mediaState = media
    val isHost = room.islevel == "2"
    val participantsCount = room.participantsCounter

    return listOf(
        // Users/Participants button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Group,
            onClick = { openParticipants() },
            isVisible = true
        ),
        // Share button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Share,
            onClick = { modals.setShareEventVisibility(true) },
            isVisible = true
        ),
        // Messages button with badge
        ControlButtonModel(
            label = "",
            icon = Icons.AutoMirrored.Rounded.Chat,
            onClick = { openMessages() },
            badgeText = if (messaging.showMessagesBadge) "" else null,
            isVisible = true
        ),
        // Switch camera button (host only)
        ControlButtonModel(
            label = "",
            icon = Icons.Filled.FlipCameraAndroid,
            onClick = {
                launchInScope {
                    switchVideoAlt(
                        com.mediasfu.sdk.methods.stream_methods.SwitchVideoAltOptions(
                            parameters = createSwitchVideoAltParameters()
                        )
                    )
                }
            },
            isVisible = isHost
        ),
        // Video button (host only)
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Videocam,
            alternateIcon = Icons.Rounded.VideocamOff,
            isActive = mediaState.videoAlreadyOn,
            onClick = { toggleVideo() },
            activeTint = Color(0xFF52C41A),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = isHost
        ),
        // Mic button (host only)
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Mic,
            alternateIcon = Icons.Rounded.MicOff,
            isActive = mediaState.audioAlreadyOn,
            onClick = { toggleAudio() },
            activeTint = Color(0xFF52C41A),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = isHost
        ),
        // Participants count display
        ControlButtonModel(
            label = participantsCount.toString(),
            icon = Icons.Rounded.Person,
            onClick = { },
            isEnabled = false,
            isVisible = true
        ),
        // End call button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.CallEnd,
            onClick = { openConfirmExit() },
            activeTint = Color(0xFFFF4D4F),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = true
        )
    )
}

/**
 * Control buttons for CHAT event type (floating on the main grid).
 * Matches Flutter's initializeControlChatButtons.
 */
internal fun MediasfuGenericState.controlChatButtons(): List<ControlButtonModel> {
    val mediaState = media
    val isHost = room.islevel == "2"

    return listOf(
        // Share button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Share,
            onClick = { modals.setShareEventVisibility(true) },
            isVisible = true
        ),
        // Messages button with badge
        ControlButtonModel(
            label = "",
            icon = Icons.AutoMirrored.Rounded.Chat,
            onClick = { openMessages() },
            badgeText = if (messaging.showMessagesBadge) "" else null,
            isVisible = true
        ),
        // Switch camera button
        ControlButtonModel(
            label = "",
            icon = Icons.Filled.FlipCameraAndroid,
            onClick = {
                launchInScope {
                    switchVideoAlt(
                        com.mediasfu.sdk.methods.stream_methods.SwitchVideoAltOptions(
                            parameters = createSwitchVideoAltParameters()
                        )
                    )
                }
            },
            isVisible = true
        ),
        // Video button (host only)
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Videocam,
            alternateIcon = Icons.Rounded.VideocamOff,
            isActive = mediaState.videoAlreadyOn,
            onClick = { toggleVideo() },
            activeTint = Color(0xFF52C41A),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = isHost
        ),
        // Mic button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Mic,
            alternateIcon = Icons.Rounded.MicOff,
            isActive = mediaState.audioAlreadyOn,
            onClick = { toggleAudio() },
            activeTint = Color(0xFF52C41A),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = true
        ),
        // End call button
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.CallEnd,
            onClick = { openConfirmExit() },
            activeTint = Color(0xFFFF4D4F),
            inactiveTint = Color(0xFFFF4D4F),
            isVisible = true
        )
    )
}

/**
 * Single record button for BROADCAST event type (shown before recording starts).
 * Matches Flutter's initializeRecordButton.
 */
internal fun MediasfuGenericState.recordButton(): List<ControlButtonModel> {
    return listOf(
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.FiberManualRecord,
            onClick = { toggleRecording() },
            activeTint = Color(0xFFF40303),
            inactiveTint = Color(0xFFFB0909),
            isVisible = true
        )
    )
}

/**
 * Expanded record buttons for BROADCAST event type (shown when recording has started).
 * Matches Flutter's initializeRecordButtonsTouch.
 */
internal fun MediasfuGenericState.recordButtonsTouch(): List<ControlButtonModel> {
    val recordingState = recording
    val isPaused = recordingState.recordPaused
    val state = this
    
    return listOf(
        // Play/Pause button - toggles pause/resume recording
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.PlayArrow,
            alternateIcon = Icons.Rounded.Pause,
            isActive = !isPaused,
            onClick = {
                launchInScope {
                    com.mediasfu.sdk.methods.recording_methods.updateRecording(
                        com.mediasfu.sdk.methods.recording_methods.UpdateRecordingOptions(
                            parameters = state.createUpdateRecordingParameters()
                        )
                    )
                }
            },
            activeTint = Color(0xFF90A4AE),
            inactiveTint = Color(0xFF90A4AE),
            isVisible = true
        ),
        // Stop button - stops recording
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Stop,
            onClick = {
                launchInScope {
                    com.mediasfu.sdk.methods.recording_methods.stopRecording(
                        com.mediasfu.sdk.methods.recording_methods.StopRecordingOptions(
                            parameters = state.createStopRecordingParameters()
                        )
                    )
                }
            },
            activeTint = Color(0xFF52C41A),
            inactiveTint = Color(0xFF90A4AE),
            isVisible = true
        ),
        // Status indicator (red when recording, yellow when paused)
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.FiberManualRecord,
            onClick = { },
            isEnabled = false,
            activeTint = if (!isPaused) Color(0xFFFF4D4F) else Color(0xFFFFD700),
            inactiveTint = if (!isPaused) Color(0xFFFF4D4F) else Color(0xFFFFD700),
            isVisible = true
        ),
        // Settings button - opens recording modal
        ControlButtonModel(
            label = "",
            icon = Icons.Rounded.Settings,
            onClick = { 
                if (isPaused) {
                    toggleRecording() 
                }
            },
            activeTint = if (isPaused) Color(0xFF52C41A) else Color(0xFF90A4AE).copy(alpha = 0.5f),
            inactiveTint = if (isPaused) Color(0xFF90A4AE) else Color(0xFF90A4AE).copy(alpha = 0.5f),
            isVisible = true
        )
    )
}

/**
 * Floating record buttons overlay for broadcast event type.
 * Positioned at bottom-center of the main grid (horizontal layout).
 */
@Composable
private fun FloatingRecordButtons(
    buttons: List<ControlButtonModel>,
    recordingProgressTime: String,
    modifier: Modifier = Modifier
) {
    val visibleButtons = buttons.filter { it.isVisible }
    if (visibleButtons.isEmpty()) return

    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleButtons.forEachIndexed { index, button ->
            // Insert timer display after stop button (index 1) and before status indicator (index 2)
            if (index == 2 && recordingProgressTime.isNotEmpty()) {
                Text(
                    text = recordingProgressTime,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            
            val iconVector = if (button.isActive && button.alternateIcon != null) {
                button.alternateIcon
            } else {
                button.icon
            }
            val baseTint = if (button.isActive) button.activeTint else button.inactiveTint
            val iconTint = if (button.isEnabled) baseTint else baseTint.copy(alpha = 0.8f)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .then(
                        if (button.isEnabled) {
                            Modifier.clickable { button.onClick() }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = button.label,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Floating control buttons overlay for broadcast/chat event types.
 * Positioned at bottom-right of the main grid (vertical layout).
 */
@Composable
private fun FloatingControlButtons(
    buttons: List<ControlButtonModel>,
    modifier: Modifier = Modifier
) {
    val visibleButtons = buttons.filter { it.isVisible }
    if (visibleButtons.isEmpty()) return

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(24.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        visibleButtons.forEach { button ->
            val iconVector = if (button.isActive && button.alternateIcon != null) {
                button.alternateIcon
            } else {
                button.icon
            }
            val baseTint = if (button.isActive) button.activeTint else button.inactiveTint
            val iconTint = if (button.isEnabled) baseTint else baseTint.copy(alpha = 0.4f)

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
                    .clickable(enabled = button.isEnabled) { button.onClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = button.label,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                    // Show label as text next to icon (for participant count)
                    button.label?.let { label ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }
                }

                // Badge
                button.badgeText?.let { badgeText ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .background(button.badgeColor, CircleShape)
                            .size(if (badgeText.isEmpty()) 8.dp else 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (badgeText.isNotEmpty()) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ControlStrip and MeetingTimerBar extracted to separate files

internal fun <T> SnapshotStateList<T>.elementOrNull(index: Int): T? =
    if (index in 0 until size) this[index] else null

@Composable
private fun LoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun AlertBanner(state: MediasfuGenericState, isVisible: Boolean, modifier: Modifier = Modifier) {
    val alertState = state.alert
    
    val containerColor = when (alertState.type) {
        "danger", "error" -> Color(0xFFFF4D4F)
        "warning" -> Color(0xFFFFC53D)
        "success" -> Color(0xFF52C41A)
        else -> Color(0xFF1890FF)
    }

    LaunchedEffect(alertState.visible) {
        if (alertState.visible) {
            delay(alertState.duration.toLong())
            alertState.hide()
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = TweenSpec(durationMillis = 200)),
        exit = fadeOut(animationSpec = TweenSpec(durationMillis = 200)),
        modifier = modifier.fillMaxWidth()
    ) {
        ElevatedCard(
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alertState.message,
                    modifier = Modifier.fillMaxWidth(0.85f),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                IconButton(onClick = alertState::hide) {
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss alert", tint = Color.White)
                }
            }
        }
    }
}

// ShareEventModal, ConfirmExitModal, and ConfirmHereModal already extracted to separate files

private val EVENT_SETTING_OPTIONS = listOf("allow", "approval", "disallow")

private fun <T> androidx.compose.runtime.snapshots.SnapshotStateList<T>.syncWith(newList: List<T>) {
    clear()
    addAll(newList)
}
