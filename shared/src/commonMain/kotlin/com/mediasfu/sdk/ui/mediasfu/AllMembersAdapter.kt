package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.consumers.AddVideosGridOptions
import com.mediasfu.sdk.consumers.AddVideosGridParameters
import com.mediasfu.sdk.consumers.CalculateRowsAndColumnsOptions
import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.consumers.ChangeVidsParameters
import com.mediasfu.sdk.consumers.ComponentSizes
import com.mediasfu.sdk.consumers.ConsumerTransportInfo
import com.mediasfu.sdk.consumers.ConnectIpsOptions as ConsumerConnectIpsOptions
import com.mediasfu.sdk.consumers.ConnectIpsParameters as ConsumerConnectIpsParameters
import com.mediasfu.sdk.consumers.ConnectLocalIpsOptions as ConsumerConnectLocalIpsOptions
import com.mediasfu.sdk.consumers.ConnectLocalIpsParameters as ConsumerConnectLocalIpsParameters
import com.mediasfu.sdk.consumers.ControlMediaOptions
import com.mediasfu.sdk.consumers.ControlMediaAdapter
import com.mediasfu.sdk.consumers.CustomAudioCardBuilder
import com.mediasfu.sdk.consumers.CustomMiniCardBuilder
import com.mediasfu.sdk.consumers.CustomVideoCardBuilder
import com.mediasfu.sdk.consumers.DispStreamsOptions
import com.mediasfu.sdk.consumers.GetEstimateOptions
import com.mediasfu.sdk.consumers.GetEstimateParameters
import com.mediasfu.sdk.consumers.GridSizes
import com.mediasfu.sdk.consumers.MixStreamsOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsAudioOptions
import com.mediasfu.sdk.consumers.ReadjustOptions
import com.mediasfu.sdk.consumers.ReadjustParameters
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.ResumePauseAudioStreamsOptions
import com.mediasfu.sdk.consumers.ResumePauseStreamsOptions
import com.mediasfu.sdk.consumers.UpdateMiniCardsGridOptions
import com.mediasfu.sdk.consumers.calculateRowsAndColumns as consumerCalculateRowsAndColumns
import com.mediasfu.sdk.consumers.updateMiniCardsGridImpl
import com.mediasfu.sdk.consumers.changeVids as consumerChangeVids
import com.mediasfu.sdk.consumers.connectIps as consumerConnectIps
import com.mediasfu.sdk.consumers.connectLocalIps as consumerConnectLocalIps
import com.mediasfu.sdk.consumers.dispStreams as consumerDispStreams
import com.mediasfu.sdk.consumers.getEstimate as consumerGetEstimate
import com.mediasfu.sdk.consumers.mixStreams as consumerMixStreams
import com.mediasfu.sdk.consumers.onScreenChanges as consumerOnScreenChanges
import com.mediasfu.sdk.consumers.prepopulateUserMedia as consumerPrepopulateUserMedia
import com.mediasfu.sdk.consumers.processConsumerTransports as consumerProcessConsumerTransports
import com.mediasfu.sdk.consumers.readjust as consumerReadjust
import com.mediasfu.sdk.consumers.reorderStreams as consumerReorderStreams
import com.mediasfu.sdk.consumers.addVideosGrid as consumerAddVideosGrid
import com.mediasfu.sdk.consumers.rePort as consumerRePort
import com.mediasfu.sdk.consumers.resumePauseAudioStreams as consumerResumePauseAudioStreams
import com.mediasfu.sdk.consumers.controlMedia
import com.mediasfu.sdk.consumers.resumePauseStreams as consumerResumePauseStreams
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.utils.SleepOptions as UtilSleepOptions
import com.mediasfu.sdk.methods.utils.sleep as utilSleep
import com.mediasfu.sdk.model.AllMembersParameters
import com.mediasfu.sdk.model.AllMembersRestParameters
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.ConnectIpsOptions
import com.mediasfu.sdk.model.ConnectLocalIpsOptions
import com.mediasfu.sdk.model.SleepOptions as ModelSleepOptions
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.OnScreenChangesOptions
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ReorderStreamsOptions
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.WaitingRoomParticipant
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.runBlocking

internal fun MediasfuGenericState.createAllMembersParameters(): AllMembersParameters =
    MediasfuAllMembersParameters(parameters, this)

internal fun MediasfuGenericState.createAllMembersRestParameters(): AllMembersRestParameters =
    MediasfuAllMembersParameters(parameters, this)

private class MediasfuAllMembersParameters(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BasePrepopulateParameters(backing, state),
    AllMembersRestParameters,
    ChangeVidsParameters,
    AddVideosGridParameters,
    ReadjustParameters,
    GetEstimateParameters {

    private val connectIpsLambda: suspend (ConnectIpsOptions) -> Pair<List<ConsumeSocket>, List<String>> = { options ->
        val consumerOptions = ConsumerConnectIpsOptions(
            consumeSockets = options.consumeSockets.toSocketManagerMaps(),
            remIP = options.remoteIps,
            apiUserName = options.apiUserName,
            apiKey = options.apiKey ?: backing.apiKey,
            apiToken = options.apiToken,
            parameters = backing
        )

        val result = consumerConnectIps(consumerOptions).getOrThrow()
        val converted = result.consumeSockets.toConsumeSockets()
        state.media.consumeSockets = converted
        backing.consumeSocketsState = converted
        propagate()
        Pair(converted, result.roomRecvIPs)
    }

    private val connectLocalIpsLambda: (suspend (ConnectLocalIpsOptions) -> Unit)? =
        connectLocalIpsLambda@ { options ->
            val manager = options.socket
                ?: state.connectivity.localSocket
                ?: state.connectivity.socket
                ?: run { return@connectLocalIpsLambda }

            consumerConnectLocalIps(
                ConsumerConnectLocalIpsOptions(
                    socket = manager,
                    parameters = backing
                )
            ).getOrThrow()
        }

    // ---------------------------------------------------------------------
    // AllMembersParameters data accessors
    // ---------------------------------------------------------------------
    override val participantsAll: List<Participant>
        get() = backing.participantsAll

    override val participants: List<Participant>
        get() = state.room.participants

    override val dispActiveNames: List<String>
        get() = backing.dispActiveNames

    override val pDispActiveNames: List<String>
        get() = backing.pDispActiveNames

    override val nForReadjustRecord: Int
        get() = backing.nForReadjustRecord

    override val requestList: List<Request>
        get() = backing.requestList

    override val coHost: String
        get() = state.room.coHost

    override val coHostResponsibility: List<CoHostResponsibility>
        get() = state.room.coHostResponsibility

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val shareEnded: Boolean
        get() = backing.shareEnded

    override val firstAll: Boolean
        get() = backing.firstAll

    override val firstRound: Boolean
        get() = backing.firstRound

    override val membersReceived: Boolean
        get() = backing.membersReceived

    override val deferScreenReceived: Boolean
        get() = backing.deferScreenReceived

    override val screenId: String
        get() = backing.screenId

    override val shareScreenStarted: Boolean
        get() = state.media.shareScreenStarted

    override val eventType: EventType
        get() = state.room.eventType

    override val meetingDisplayType: String
        get() = state.display.meetingDisplayType  // Fixed: read from display state, not room state

    override val audioSetting: String
        get() = state.media.audioSetting

    override val videoSetting: String
        get() = state.media.videoSetting

    override val screenshareSetting: String
        get() = state.media.screenshareSetting

    override val chatSetting: String
        get() = state.media.chatSetting

    override val hostFirstSwitch: Boolean
        get() = backing.hostFirstSwitch

    override val waitingRoomList: List<WaitingRoomParticipant>
        get() = backing.waitingRoomList

    override val isLevel: String
        get() = state.room.islevel

    override val consumeSockets: List<ConsumeSocket>
        get() = backing.consumeSocketsState

    override val roomRecvIps: List<String>
        get() = backing.roomRecvIPs

    override val updateParticipantsAll: (List<Participant>) -> Unit
        get() = { updated ->
            if (backing.participantsAll != updated) {
                backing.participantsAll = updated
                propagate()
            }
        }

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { updated ->
            this@MediasfuAllMembersParameters.updateParticipants(updated)
        }

    override val updateRequestList: (List<Request>) -> Unit
        get() = { updated -> state.requests.updateList(updated) }

    override val updateCoHost: (String) -> Unit
        get() = { value -> state.room.updateCoHost(value) }

    override val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit
        get() = { value -> state.room.updateCoHostResponsibility(value) }

    override val updateFirstAll: (Boolean) -> Unit
        get() = { value ->
            if (backing.firstAll != value) {
                backing.firstAll = value
                propagate()
            }
        }

    override val updateMembersReceived: (Boolean) -> Unit
        get() = { value ->
            if (backing.membersReceived != value) {
                backing.membersReceived = value
                propagate()
            }
        }

    override val updateDeferScreenReceived: (Boolean) -> Unit
        get() = { value ->
            if (backing.deferScreenReceived != value) {
                backing.deferScreenReceived = value
                propagate()
            }
        }

    override val updateShareScreenStarted: (Boolean) -> Unit
        get() = { value ->
            if (state.media.shareScreenStarted != value || backing.shareScreenStarted != value) {
                backing.shareScreenStarted = value
                state.media.shareScreenStarted = value
                propagate()
            }
        }

    override val updateAudioSetting: (String) -> Unit
        get() = { value -> state.media.updateAudioSetting(value) }

    override val updateVideoSetting: (String) -> Unit
        get() = { value -> state.media.updateVideoSetting(value) }

    override val updateScreenshareSetting: (String) -> Unit
        get() = { value -> state.media.updateScreenshareSetting(value) }

    override val updateChatSetting: (String) -> Unit
        get() = { value -> state.media.updateChatSetting(value) }

    override val updateIsLoadingModalVisible: (Boolean) -> Unit
        get() = { visible ->
            // Keep this update narrow: just toggle the loading modal visibility.
            // showLoader()/hideLoader() also mutate other loader flags and can cause
            // the overlay to remain stuck if different call sites fight each other.
            state.modals.setLoadingVisibility(visible)
        }

    override val updateTotalReqWait: (Int) -> Unit
        get() = { value ->
            if (backing.totalReqWait != value) {
                backing.totalReqWait = value
                state.requests.updateTotalPending(value)
                propagate()
            }
        }

    override val updateHostFirstSwitch: (Boolean) -> Unit
        get() = { value ->
            if (backing.hostFirstSwitch != value) {
                backing.hostFirstSwitch = value
                propagate()
            }
        }

    override val connectIps = connectIpsLambda

    override val connectLocalIps = connectLocalIpsLambda

    override val onScreenChanges: suspend (OnScreenChangesOptions) -> Unit
        get() = { options -> consumerOnScreenChanges(options) }

    override val sleep: suspend (ModelSleepOptions) -> Unit
        get() = { options -> kotlinx.coroutines.delay(options.ms.toLong()) }

    override val updateRoomRecvIps: (List<String>) -> Unit
        get() = { ips ->
            if (backing.roomRecvIPs != ips) {
                backing.roomRecvIPs = ips
                propagate()
            }
        }

    override val updateConsumeSockets: (List<ConsumeSocket>) -> Unit
        get() = { sockets ->
            backing.consumeSocketsState = sockets
            state.media.consumeSockets = sockets
            propagate()
        }

    // ---------------------------------------------------------------------
    // ReorderStreamsParameters (shared with ChangeVidsParameters)
    // ---------------------------------------------------------------------
    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val oldAllStreams: List<Stream>
        get() = backing.oldAllStreams

    override val adminVidID: String
        get() = backing.adminVidID

    override val newLimitedStreamsIDs: List<String>
        get() = backing.newLimitedStreamsIDs

    override val activeSounds: List<String>
        get() = backing.activeSounds

    override val screenShareIDStream: String
        get() = backing.screenShareIDStream

    override val screenShareNameStream: String
        get() = backing.screenShareNameStream

    override val adminIDStream: String
        get() = backing.adminIDStream

    override val adminNameStream: String
        get() = backing.adminNameStream

    override fun updateAllVideoStreams(streams: List<Stream>) {
        if (backing.allVideoStreamsState != streams) {
            backing.allVideoStreamsState = streams
            propagate()
        }
    }

    override fun updateParticipants(participants: List<Participant>) {
        // Keep UI state in sync for UI consumers first
        state.room.updateParticipants(participants)

        // Update backing store directly so engine-side adapters read the latest list
        backing.participants = participants
        backing.onParticipantsUpdated?.invoke(participants)
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        if (backing.oldAllStreams != streams) {
            backing.oldAllStreams = streams
            propagate()
        }
    }

    override fun updateScreenId(id: String) {
        if (backing.screenId != id) {
            backing.screenId = id
            propagate()
        }
    }

    override fun updateAdminVidID(id: String) {
        if (backing.adminVidID != id) {
            backing.adminVidID = id
            propagate()
        }
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        if (backing.newLimitedStreams != streams) {
            backing.newLimitedStreams = streams
            propagate()
        }
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        if (backing.newLimitedStreamsIDs != ids) {
            backing.newLimitedStreamsIDs = ids
            propagate()
        }
    }

    override fun updateActiveSounds(sounds: List<String>) {
        if (backing.activeSounds != sounds) {
            backing.activeSounds = sounds
            propagate()
        }
    }

    override fun updateScreenShareIDStream(id: String) {
        if (backing.screenShareIDStream != id) {
            backing.screenShareIDStream = id
            propagate()
        }
    }

    override fun updateScreenShareNameStream(name: String) {
        if (backing.screenShareNameStream != name) {
            backing.screenShareNameStream = name
            propagate()
        }
    }

    override fun updateAdminIDStream(id: String) {
        if (backing.adminIDStream != id) {
            backing.adminIDStream = id
            propagate()
        }
    }

    override fun updateAdminNameStream(name: String) {
        if (backing.adminNameStream != name) {
            backing.adminNameStream = name
            propagate()
        }
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        if (backing.youYouStream != streams) {
            backing.youYouStream = streams
            propagate()
        }
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        if (backing.youYouStreamIDs != ids) {
            backing.youYouStreamIDs = ids
            propagate()
        }
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> =
        runCatching { consumerChangeVids(options) }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = { options -> consumerReorderStreams(options).getOrThrow() }

    // ---------------------------------------------------------------------
    // ChangeVidsParameters
    // ---------------------------------------------------------------------
    
    // Properties from ResumePauseAudioStreamsParameters (inherited by ChangeVidsParameters)
    override val allAudioStreams: List<Stream>
        get() = backing.allAudioStreams
    
    override val limitedBreakRoom: List<BreakoutParticipant>
        get() = backing.limitedBreakRoom
    
    override val updateLimitedBreakRoom: (List<BreakoutParticipant>) -> Unit
        get() = { value -> backing.limitedBreakRoom = value }
    
    override val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.processConsumerTransportsAudio(options)
        }
    
    override val pActiveNames: List<String>
        get() = backing.pActiveNames

    override val activeNames: List<String>
        get() = backing.activeNames

    override val newLimitedStreams: List<Stream>
        get() = backing.newLimitedStreams

    override val nonAlVideoStreams: List<Stream>
        get() = backing.nonAlVideoStreams

    override val streamNames: List<Stream>
        get() = backing.streamNames

    override val audStreamNames: List<Stream>
        get() = backing.audStreamNames

    override val youYouStream: List<Stream>
        get() = backing.youYouStream

    override val youYouStreamIDs: List<String>
        get() = backing.youYouStreamIDs

    override val refParticipants: List<Participant>
        get() = backing.refParticipants

    override val islevel: String
        get() = state.room.islevel

    override val member: String
        get() = state.room.member

    override val sortAudioLoudness: Boolean
        get() = backing.sortAudioLoudness

    override val audioDecibels: List<AudioDecibels>
        get() = backing.audioDecibels

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val mixedAlVideoStreams: List<Stream>
        get() = backing.mixedAlVideoStreams

    override val nonAlVideoStreamsMuted: List<Stream>
        get() = backing.nonAlVideoStreamsMuted

    override val localStreamVideo: MediaStream?
        get() = backing.localStreamVideo

    override val screenPageLimit: Int
        get() = backing.screenPageLimit

    override val recordingVideoOptimized: Boolean
        get() = backing.recordingVideoOptimized

    override val recordingDisplayType: String
        get() = backing.recordingDisplayType

    override val paginatedStreams: List<List<Stream>>
        get() = backing.paginatedStreams

    override val itemPageLimit: Int
        get() = state.media.itemPageLimit

    override val doPaginate: Boolean
        get() = state.display.doPaginate

    override val prevDoPaginate: Boolean
        get() = state.display.prevDoPaginate

    override val currentUserPage: Int
        get() = state.display.currentUserPage

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val prevMainHeightWidth: Double
        get() = backing.prevMainHeightWidth

    override val breakoutRooms: List<List<BreakoutParticipant>>
        get() = backing.breakoutRooms

    override val hostNewRoom: Int
        get() = backing.hostNewRoom

    override val breakOutRoomStarted: Boolean
        get() = backing.breakOutRoomStarted

    override val breakOutRoomEnded: Boolean
        get() = backing.breakOutRoomEnded

    override val virtualStream: MediaStream?
        get() = backing.virtualStream

    override val mainRoomsLength: Int
        get() = backing.mainRoomsLength

    override val memberRoom: Int
        get() = backing.memberRoom

    override val chatRefStreams: List<Stream>
        get() = backing.chatRefStreams

    override val meetingVideoOptimized: Boolean
        get() = backing.meetingVideoOptimized

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    // ------------------------------------------------------------------
    // AddVideosGridParameters implementation
    // ------------------------------------------------------------------

    override val componentSizes: ComponentSizes
        get() = state.display.componentSizes

    override val gridSizes: GridSizes
        get() = state.display.gridSizes

    override val paginationDirection: String
        get() = state.display.paginationDirection

    override val paginationHeightWidth: Double
        get() = state.display.paginationHeightWidth

    override val otherGridStreams: List<List<MediaSfuUIComponent>>
        get() = state.display.otherGridStreams

    override val customVideoCardBuilder: CustomVideoCardBuilder?
        get() = backing.customVideoCardBuilder

    override val customAudioCardBuilder: CustomAudioCardBuilder?
        get() = backing.customAudioCardBuilder

    override val customMiniCardBuilder: CustomMiniCardBuilder?
        get() = backing.customMiniCardBuilder

    override val controlMediaAdapter: ControlMediaAdapter?
        get() = { participant, mediaType ->
            val participantId = (
                participant.id?.takeIf { it.isNotBlank() }
                    ?: participant.videoID.takeIf { it.isNotBlank() }
                    ?: participant.audioID.takeIf { it.isNotBlank() }
                    ?: participant.name
            ).ifBlank { "unknown-participant" }

            val options = ControlMediaOptions(
                participantId = participantId,
                participantName = participant.name,
                type = mediaType,
                socket = backing.socket,
                coHostResponsibility = backing.coHostResponsibility,
                participants = backing.participants,
                member = backing.member,
                islevel = backing.islevel,
                showAlert = backing.showAlertHandler,
                coHost = backing.coHost,
                roomName = backing.roomName
            )

            val result = runCatching {
                controlMedia(options)
            }

            result.onFailure { error ->
            }
            result.onSuccess {
            }

            result
        }

    override fun updateOtherGridStreams(streams: List<List<MediaSfuUIComponent>>) {
        state.display.updateOtherGridStreams(streams)
    }

    override fun updateAddAltGrid(add: Boolean) {
        state.display.setAddAltGridEnabled(add)
    }

    override fun updateGridRows(rows: Int) {
        state.display.updateGridRows(rows)
    }

    override fun updateGridCols(cols: Int) {
        state.display.updateGridCols(cols)
    }

    override fun updateAltGridRows(rows: Int) {
        state.display.updateAltGridRows(rows)
    }

    override fun updateAltGridCols(cols: Int) {
        state.display.updateAltGridCols(cols)
    }

    override fun updateGridSizes(sizes: GridSizes) {
        state.display.updateGridSizes(sizes)
    }

    override suspend fun updateMiniCardsGrid(options: UpdateMiniCardsGridOptions): Result<Unit> {
        return runCatching {
            updateMiniCardsGridImpl(
                rows = options.rows,
                cols = options.cols,
                defal = options.defal,
                actualRows = options.actualRows,
                gridSizes = state.display.gridSizes,
                paginationDirection = state.display.paginationDirection,
                paginationHeightWidth = state.display.paginationHeightWidth,
                doPaginate = state.display.doPaginate,
                componentSizes = state.display.componentSizes,
                eventType = state.room.eventType.name,
                updateGridRows = state.display::updateGridRows,
                updateGridCols = state.display::updateGridCols,
                updateAltGridRows = state.display::updateAltGridRows,
                updateAltGridCols = state.display::updateAltGridCols,
                updateGridSizes = state.display::updateGridSizes
            )
        }
    }

    override val updatePActiveNames: (List<String>) -> Unit
        get() = { value -> state.streams.updatePActiveNames(value) }

    override val updateActiveNames: (List<String>) -> Unit
        get() = { value -> state.streams.updateActiveNames(value) }

    override val updateDispActiveNames: (List<String>) -> Unit
        get() = { value -> state.streams.updateDispActiveNames(value) }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = { value -> updateNewLimitedStreams(value) }

    override val updateNonAlVideoStreams: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateNonAlVideoStreams(value) }

    override val updateRefParticipants: (List<Participant>) -> Unit
        get() = { value ->
            if (backing.refParticipants != value) {
                backing.refParticipants = value
                propagate()
            }
        }

    override val updateSortAudioLoudness: (Boolean) -> Unit
        get() = { value ->
            if (backing.sortAudioLoudness != value) {
                backing.sortAudioLoudness = value
                propagate()
            }
        }

    override val updateMixedAlVideoStreams: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateMixedAlVideoStreams(value) }

    override val updateNonAlVideoStreamsMuted: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateNonAlVideoStreamsMuted(value) }

    override val updatePaginatedStreams: (List<List<Stream>>) -> Unit
        get() = { value -> state.streams.updatePaginatedStreams(value) }

    override val updateDoPaginate: (Boolean) -> Unit
        get() = { value -> state.display.updateDoPaginate(value) }

    override val updatePrevDoPaginate: (Boolean) -> Unit
        get() = { value -> state.display.updatePrevDoPaginate(value) }

    override val updateCurrentUserPage: (Int) -> Unit
        get() = { value -> state.display.updateCurrentUserPage(value) }

    override val updateNumberPages: (Int) -> Unit
        get() = { value -> state.display.updateNumberPages(value) }

    override val updateMainRoomsLength: (Int) -> Unit
        get() = { value ->
            if (backing.mainRoomsLength != value) {
                backing.mainRoomsLength = value
                propagate()
            }
        }

    override val updateMemberRoom: (Int) -> Unit
        get() = { value ->
            if (backing.memberRoom != value) {
                backing.memberRoom = value
                propagate()
            }
        }

    override val updateChatRefStreams: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateChatRefStreams(value) }

    override val updateNForReadjustRecord: (Int) -> Unit
        get() = { value ->
            if (backing.nForReadjustRecord != value) {
                backing.nForReadjustRecord = value
                propagate()
            }
        }

    override val updateShowMiniView: (Boolean) -> Unit
        get() = { value -> state.display.updateShowMiniView(value) }

    override val updateShareEnded: (Boolean) -> Unit
        get() = { value -> state.streams.updateShareEnded(value) }

    override val mixStreams: suspend (MixStreamsOptions) -> List<Stream>
        get() = { options ->
            consumerMixStreams(options).getOrElse { emptyList() }.filterIsInstance<Stream>()
        }

    override val dispStreams: suspend (DispStreamsOptions) -> Unit
        get() = { options -> consumerDispStreams(options) }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options -> consumerPrepopulateUserMedia(options) }

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options -> consumerRePort(options) }

    override val processConsumerTransports: suspend (ProcessConsumerTransportsOptions) -> Result<Unit>
        get() = { options -> consumerProcessConsumerTransports(options) }

    override val resumePauseStreams: suspend (ResumePauseStreamsOptions) -> Result<Unit>
        get() = { options -> consumerResumePauseStreams(options) }

    override val readjust: suspend (ReadjustOptions) -> Unit
        get() = { options -> consumerReadjust(options) }

    override val addVideosGrid: suspend (AddVideosGridOptions) -> Unit
        get() = { options -> consumerAddVideosGrid(options) }

    // ---------------------------------------------------------------------
    // GetEstimateParameters
    // ---------------------------------------------------------------------
    override val fixedPageLimit: Int
        get() = backing.fixedPageLimit

    override val removeAltGrid: Boolean
        get() = backing.removeAltGrid

    override val isWideScreen: Boolean
        get() = state.display.isWideScreen

    override val isMediumScreen: Boolean
        get() = state.display.isMediumScreen

    override val updateRemoveAltGrid: (Boolean) -> Unit
        get() = { value ->
            if (backing.removeAltGrid != value) {
                backing.removeAltGrid = value
                propagate()
            }
        }

    override val calculateRowsAndColumns: (CalculateRowsAndColumnsOptions) -> Result<List<Int>>
        get() = { options ->
            consumerCalculateRowsAndColumns(options)
        }

    override val getEstimate: (GetEstimateOptions) -> List<Int>
        get() = { options -> consumerGetEstimate(options) }

    override val resumePauseAudioStreams: suspend (ResumePauseAudioStreamsOptions) -> Unit
        get() = { options -> consumerResumePauseAudioStreams(options) }

    // ---------------------------------------------------------------------
    // DispStreamsParameters
    // ---------------------------------------------------------------------
    override val lStreams: List<Stream>
        get() = backing.lStreams

    override val hostLabel: String
        get() = backing.hostLabel

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val shared: Boolean
        get() = state.media.shared

    override val updateLStreams: (List<Stream>) -> Unit
        get() = { streams -> state.streams.updateLStreams(streams) }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value ->
            if (backing.updateMainWindow != value) {
                backing.updateMainWindow = value
                propagate()
            }
        }

    // ---------------------------------------------------------------------
    // Common helpers
    // ---------------------------------------------------------------------
    override val addForBasic: Boolean
        get() = state.media.addForBasic

    override val updateAddForBasic: (Boolean) -> Unit
        get() = { value ->
            if (backing.addForBasic != value || state.media.addForBasic != value) {
                backing.addForBasic = value
                state.media.addForBasic = value
                propagate()
            }
        }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value -> state.display.updateMainHeightWidth(value) }

    override val updateItemPageLimit: (Int) -> Unit
        get() = { value ->
            if (state.media.itemPageLimit != value || backing.itemPageLimit != value) {
                backing.itemPageLimit = value
                state.media.itemPageLimit = value
                propagate()
            }
        }

    override fun getUpdatedAllParams(): MediasfuAllMembersParameters = this

    override val socket: SocketManager?
        get() = state.connectivity.socket

    private fun propagate() {
        state.propagateParameterChanges()
    }
}

private fun List<ConsumeSocket>.toSocketManagerMaps(): List<Map<String, SocketManager>> =
    mapNotNull { entry ->
        val converted = entry.mapNotNull { (key, value) ->
            val manager = value as? SocketManager ?: return@mapNotNull null
            key to manager
        }.toMap()
        if (converted.isEmpty()) null else converted
    }

private fun List<Map<String, SocketManager>>.toConsumeSockets(): List<ConsumeSocket> =
    map { socketMap -> socketMap.mapValues { it.value as Any? } }
