package com.mediasfu.sdk
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.*
import com.mediasfu.sdk.consumers.calculateRowsAndColumns as consumerCalculateRowsAndColumns
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.utils.mini_audio_player.MiniAudioPlayerParameters
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.ReUpdateInterType
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedParameters as SocketProducerClosedParameters
import com.mediasfu.sdk.consumers.UpdateParticipantAudioDecibelsType
import com.mediasfu.sdk.methods.utils.CheckLimitsAndMakeRequestParameters
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.delay

internal class EngineConnectSendTransportParameters(
    private val backing: MediasfuParameters
) : ConnectSendTransportParameters, CreateSendTransportParameters {

    // ---------------------------------------------------------------------
    // CreateSendTransportParameters
    // ---------------------------------------------------------------------
    override val islevel: String
        get() = backing.islevel

    override val member: String
        get() = backing.member

    override val socket
        get() = backing.socket

    override val localSocket
        get() = backing.localSocket

    override val device
        get() = backing.device

    override val rtpCapabilities
        get() = backing.rtpCapabilities

    override val routerRtpCapabilities
        get() = backing.routerRtpCapabilities

    override val extendedRtpCapabilities
        get() = backing.extendedRtpCapabilities

    override val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?
        get() = { caps -> backing.extendedRtpCapabilities = caps }

    override var transportCreated: Boolean
        get() = backing.transportCreated
        set(value) { backing.transportCreated = value }

    override var localTransportCreated: Boolean
        get() = backing.localTransportCreated
        set(value) { backing.localTransportCreated = value }

    override var producerTransport: WebRtcTransport?
        get() = backing.producerTransport
        set(value) { backing.producerTransport = value }

    override var localProducerTransport: WebRtcTransport?
        get() = backing.localProducerTransport
        set(value) { backing.localProducerTransport = value }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { backing.producerTransport = it }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { backing.localProducerTransport = it }

    override val updateTransportCreated: (Boolean) -> Unit
        get() = { backing.transportCreated = it }

    override val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { backing.localTransportCreated = it }

    // ---------------------------------------------------------------------
    // ConnectSendTransportAudioParameters & ResumeSendTransportAudioParameters
    // ---------------------------------------------------------------------
    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportAudio(options) }

    override val participants: List<Participant>
        get() = backing.participants

    override val localStream: MediaStream?
        get() = backing.localStream

    override val transportCreatedAudio: Boolean
        get() = backing.transportCreatedAudio

    override val audioAlreadyOn: Boolean
        get() = backing.audioAlreadyOn

    override val hostLabel: String
        get() = backing.hostLabel

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val shared: Boolean
        get() = backing.shared

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val audioProducer: WebRtcProducer?
        get() = backing.audioProducer

    override val localAudioProducer: WebRtcProducer?
        get() = backing.localAudioProducer

    override val audioParams: ProducerOptionsType?
        get() = backing.audioParams

    override val videoParams: ProducerOptionsType?
        get() = backing.vParams

    override val localStreamAudio: MediaStream?
        get() = backing.localStreamAudio

    override val defAudioID: String
        get() = backing.defAudioID

    override val userDefaultAudioInputDevice: String
        get() = backing.userDefaultAudioInputDevice

    override val params: ProducerOptionsType?
        get() = backing.params

    override val aParams: ProducerOptionsType?
        get() = backing.aParams

    override val micAction: Boolean
        get() = backing.micAction

    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { backing.updateParticipants(it) }

    override val updateTransportCreatedAudio: (Boolean) -> Unit
        get() = { backing.transportCreatedAudio = it }

    override val updateAudioAlreadyOn: (Boolean) -> Unit
        get() = { backing.audioAlreadyOn = it }

    override val updateMicAction: (Boolean) -> Unit
        get() = { backing.micAction = it }

    override val updateAudioParams: (ProducerOptionsType?) -> Unit
        get() = { backing.audioParams = it }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { backing.localStream = it }

    override val updateLocalStreamAudio: (MediaStream?) -> Unit
        get() = { backing.localStreamAudio = it }

    override val updateDefAudioID: (String) -> Unit
        get() = { backing.defAudioID = it }

    override val updateUserDefaultAudioInputDevice: (String) -> Unit
        get() = { backing.userDefaultAudioInputDevice = it }

    override val updateAudioLevel: (Double) -> Unit
        get() = { backing.audioLevel = it }

    override val updateAudioProducer: (WebRtcProducer?) -> Unit
        get() = { backing.audioProducer = it }

    override val updateLocalAudioProducer: ((WebRtcProducer?) -> Unit)?
        get() = { backing.localAudioProducer = it }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options -> prepopulateUserMedia(options) }

    // ---------------------------------------------------------------------
    // ConnectSendTransportVideoParameters & ResumeSendTransportVideoParameters
    // ---------------------------------------------------------------------
    override val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportVideo(options) }

    override var videoProducer: WebRtcProducer?
        get() = backing.videoProducer
        set(value) { backing.videoProducer = value }

    override var localVideoProducer: WebRtcProducer?
        get() = backing.localVideoProducer
        set(value) { backing.localVideoProducer = value }

    override val updateVideoProducer: (WebRtcProducer?) -> Unit
        get() = { backing.videoProducer = it }

    override val updateLocalVideoProducer: ((WebRtcProducer?) -> Unit)?
        get() = { backing.localVideoProducer = it }

    override val updateLocalStreamVideo: (MediaStream?) -> Unit
        get() = { backing.localStreamVideo = it }

    // ---------------------------------------------------------------------
    // ConnectSendTransportScreenParameters
    // ---------------------------------------------------------------------
    override val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportScreen(options) }

    override val screenProducer: WebRtcProducer?
        get() = backing.screenProducer

    override val localScreenProducer: WebRtcProducer?
        get() = backing.localScreenProducer

    override val screenParams: ProducerOptionsType?
        get() = backing.screenParams

    override val defScreenID: String
        get() = backing.screenId

    override val updateScreenProducer: (WebRtcProducer?) -> Unit
        get() = { backing.screenProducer = it }

    override val updateLocalScreenProducer: ((WebRtcProducer?) -> Unit)?
        get() = { backing.localScreenProducer = it }

    override val updateLocalStreamScreen: (MediaStream?) -> Unit
        get() = { backing.localStreamScreen = it }

    override val updateDefScreenID: (String) -> Unit
        get() = { backing.screenId = it }

    override val canvasStream: MediaStream?
        get() = backing.virtualStream as? MediaStream

    // ---------------------------------------------------------------------
    // PrepopulateUserMediaParameters support
    // ---------------------------------------------------------------------
    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val eventType
        get() = backing.eventType

    override val screenId: String?
        get() = backing.screenId.ifEmpty { null }

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val mainScreenFilled: Boolean
        get() = backing.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = backing.adminOnMainScreen

    override val mainScreenPerson: String
        get() = backing.mainScreenPerson

    override val oldAllStreams: List<Stream>
        get() = backing.oldAllStreams

    override val screenForceFullDisplay: Boolean
        get() = backing.screenForceFullDisplay

    override val localStreamScreen: MediaStream?
        get() = backing.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = backing.remoteScreenStream

    override val localStreamVideo: MediaStream?
        get() = backing.localStreamVideo

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val localUIMode: Boolean
        get() = backing.localUIMode

    override val whiteboardStarted: Boolean
        get() = backing.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = backing.whiteboardEnded

    override val virtualStream: Any?
        get() = backing.virtualStream

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val annotateScreenStream: Boolean
        get() = backing.annotateScreenStream

    override val audioDecibels: List<AudioDecibels>
        get() = backing.audioDecibels

    override val updateMainScreenPerson: (String) -> Unit
        get() = { backing.mainScreenPerson = it }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { backing.mainScreenFilled = it }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { backing.adminOnMainScreen = it }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { backing.mainHeightWidth = it }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { backing.screenForceFullDisplay = it }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { backing.updateMainWindow = it }

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = { backing.showAlertHandler = it }

    override val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit
        get() = { components -> backing.updateMainGridStream(components) }

    // ---------------------------------------------------------------------
    // Common helpers
    // ---------------------------------------------------------------------
    override fun getUpdatedAllParams(): EngineConnectSendTransportParameters = this
}

internal class EngineResumePauseStreamsParameters(
    private val backing: MediasfuParameters
) : ResumePauseStreamsParameters {

    override val participants: List<Participant>
        get() = backing.participants

    override val dispActiveNames: List<String>
        get() = backing.dispActiveNames

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val screenId: String
        get() = backing.screenId

    override val islevel: String
        get() = backing.islevel

    override val updateDispActiveNames: (List<String>) -> Unit
        get() = { names -> backing.dispActiveNames = names }

    override fun getUpdatedAllParams(): ResumePauseStreamsParameters = this
}

internal class EngineReorderStreamsParameters(
    private val backing: MediasfuParameters
) : ReorderStreamsParameters,
    ChangeVidsParameters,
    AddVideosGridParameters,
    GetEstimateParameters,
    ReadjustParameters {

    // ------------------------------------------------------------------
    // ReorderStreamsParameters properties
    // ------------------------------------------------------------------
    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val participants: List<Participant>
        get() = backing.participants

    override val oldAllStreams: List<Stream>
        get() = backing.oldAllStreams

    override val screenId: String
        get() = backing.screenId

    override val adminVidID: String
        get() = backing.adminVidID

    override val newLimitedStreams: List<Stream>
        get() = backing.newLimitedStreams

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
        backing.allVideoStreamsState = streams
    }

    override fun updateParticipants(participants: List<Participant>) {
        backing.updateParticipants(participants)
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        backing.oldAllStreams = streams
    }

    override fun updateScreenId(id: String) {
        backing.screenId = id
    }

    override fun updateAdminVidID(id: String) {
        backing.adminVidID = id
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        backing.newLimitedStreams = streams
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        backing.newLimitedStreamsIDs = ids
    }

    override fun updateActiveSounds(sounds: List<String>) {
        backing.activeSounds = sounds
    }

    override fun updateScreenShareIDStream(id: String) {
        backing.screenShareIDStream = id
    }

    override fun updateScreenShareNameStream(name: String) {
        backing.screenShareNameStream = name
    }

    override fun updateAdminIDStream(id: String) {
        backing.adminIDStream = id
    }

    override fun updateAdminNameStream(name: String) {
        backing.adminNameStream = name
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        backing.youYouStream = streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        backing.youYouStreamIDs = ids
    }

    override suspend fun changeVids(options: com.mediasfu.sdk.consumers.ChangeVidsOptions): Result<Unit> {
        return runCatching { com.mediasfu.sdk.consumers.changeVids(options) }
    }

    // ------------------------------------------------------------------
    // ChangeVidsParameters properties
    // ------------------------------------------------------------------
    
    // Properties from ResumePauseAudioStreamsParameters (inherited by ChangeVidsParameters)
    override val allAudioStreams: List<Stream>
        get() = backing.allAudioStreams
    
    override val limitedBreakRoom: List<BreakoutParticipant>
        get() = backing.limitedBreakRoom
    
    override val updateLimitedBreakRoom: (List<BreakoutParticipant>) -> Unit
        get() = { value -> backing.limitedBreakRoom = value }
    
    override val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineProcessConsumerTransportsAudioParameters(backing))
            com.mediasfu.sdk.consumers.processConsumerTransportsAudio(resolved)
        }
    
    override val pActiveNames: List<String>
        get() = backing.pActiveNames

    override val activeNames: List<String>
        get() = backing.activeNames

    override val dispActiveNames: List<String>
        get() = backing.dispActiveNames

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val shared: Boolean
        get() = backing.shared

    override val nonAlVideoStreams: List<Stream>
        get() = backing.nonAlVideoStreams

    override val refParticipants: List<Participant>
        get() = backing.refParticipants

    override val eventType
        get() = backing.eventType

    override val islevel: String
        get() = backing.islevel

    override val member: String
        get() = backing.member

    override val sortAudioLoudness: Boolean
        get() = backing.sortAudioLoudness

    override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
        get() = backing.audioDecibels

    override val mixedAlVideoStreams: List<Stream>
        get() = backing.mixedAlVideoStreams

    override val nonAlVideoStreamsMuted: List<Stream>
        get() = backing.nonAlVideoStreamsMuted

    override val streamNames: List<Stream>
        get() = backing.streamNames

    override val audStreamNames: List<Stream>
        get() = backing.audStreamNames

    override val youYouStream: List<Stream>
        get() = backing.youYouStream

    override val youYouStreamIDs: List<String>
        get() = backing.youYouStreamIDs

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val localStreamVideo: MediaStream?
        get() = backing.localStreamVideo

    override val prevMainHeightWidth: Double
        get() = backing.prevMainHeightWidth

    override val firstAll: Boolean
        get() = backing.firstAll

    override val shareEnded: Boolean
        get() = backing.shareEnded

    override val pDispActiveNames: List<String>
        get() = backing.pDispActiveNames

    override val nForReadjustRecord: Int
        get() = backing.nForReadjustRecord

    override val firstRound: Boolean
        get() = backing.firstRound

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val chatRefStreams: List<Stream>
        get() = backing.chatRefStreams

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val meetingDisplayType: String
        get() = backing.meetingDisplayType

    override val meetingVideoOptimized: Boolean
        get() = backing.meetingVideoOptimized

    override val recordingVideoOptimized: Boolean
        get() = backing.recordingVideoOptimized

    override val recordingDisplayType: String
        get() = backing.recordingDisplayType

    override val paginatedStreams: List<List<Stream>>
        get() = backing.paginatedStreams

    override val itemPageLimit: Int
        get() = backing.itemPageLimit

    override val doPaginate: Boolean
        get() = backing.doPaginate

    override val prevDoPaginate: Boolean
        get() = backing.prevDoPaginate

    override val currentUserPage: Int
        get() = backing.currentUserPage

    override val breakoutRooms: List<List<com.mediasfu.sdk.model.BreakoutParticipant>>
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

    override val updatePActiveNames: (List<String>) -> Unit
        get() = { backing.pActiveNames = it }

    override val updateActiveNames: (List<String>) -> Unit
        get() = { backing.activeNames = it }

    override val updateDispActiveNames: (List<String>) -> Unit
        get() = { backing.dispActiveNames = it }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = { backing.newLimitedStreams = it }

    override val updateNonAlVideoStreams: (List<Stream>) -> Unit
        get() = { backing.nonAlVideoStreams = it }

    override val updateRefParticipants: (List<Participant>) -> Unit
        get() = { backing.refParticipants = it }

    override val updateSortAudioLoudness: (Boolean) -> Unit
        get() = { backing.sortAudioLoudness = it }

    override val updateMixedAlVideoStreams: (List<Stream>) -> Unit
        get() = { backing.mixedAlVideoStreams = it }

    override val updateNonAlVideoStreamsMuted: (List<Stream>) -> Unit
        get() = { backing.nonAlVideoStreamsMuted = it }

    override val updatePaginatedStreams: (List<List<Stream>>) -> Unit
        get() = { backing.updatePaginatedStreams(it) }

    override val updateDoPaginate: (Boolean) -> Unit
        get() = { backing.doPaginate = it }

    override val updatePrevDoPaginate: (Boolean) -> Unit
        get() = { backing.prevDoPaginate = it }

    override val updateCurrentUserPage: (Int) -> Unit
        get() = { backing.currentUserPage = it }

    override val updateNumberPages: (Int) -> Unit
        get() = { backing.numberPages = it }

    override val updateMainRoomsLength: (Int) -> Unit
        get() = { backing.mainRoomsLength = it }

    override val updateMemberRoom: (Int) -> Unit
        get() = { backing.memberRoom = it }

    override val updateChatRefStreams: (List<Stream>) -> Unit
        get() = { backing.chatRefStreams = it }

    override val updateNForReadjustRecord: (Int) -> Unit
        get() = { backing.nForReadjustRecord = it }

    override val updateShowMiniView: (Boolean) -> Unit
        get() = { backing.showMiniView = it }

    override val updateShareEnded: (Boolean) -> Unit
        get() = { backing.shareEnded = it }

    override val mixStreams: suspend (com.mediasfu.sdk.consumers.MixStreamsOptions) -> List<Stream>
        get() = { options ->
            com.mediasfu.sdk.consumers.mixStreams(options)
                .getOrElse { emptyList() }
                .mapNotNull { it as? Stream }
        }

    override val dispStreams: suspend (DispStreamsOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.dispStreams(options) }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.prepopulateUserMedia(options) }

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineRePortParameters(backing))
            com.mediasfu.sdk.consumers.rePort(resolved)
        }

    override val processConsumerTransports: suspend (ProcessConsumerTransportsOptions) -> Result<Unit>
        get() = { options ->
            val resolved = options.copy(parameters = EngineProcessConsumerTransportsParameters(backing))
            com.mediasfu.sdk.consumers.processConsumerTransports(resolved)
        }

    override val resumePauseStreams: suspend (ResumePauseStreamsOptions) -> Result<Unit>
        get() = { options -> com.mediasfu.sdk.consumers.resumePauseStreams(options) }

    override val readjust: suspend (ReadjustOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineReadjustParameters(backing))
            com.mediasfu.sdk.consumers.readjust(resolved)
        }

    override val addVideosGrid: suspend (AddVideosGridOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineAddVideosGridParameters(backing))
            com.mediasfu.sdk.consumers.addVideosGrid(resolved)
        }

    override val getEstimate: (GetEstimateOptions) -> List<Int>
        get() = { options -> com.mediasfu.sdk.consumers.getEstimate(options) }

    override val resumePauseAudioStreams: suspend (ResumePauseAudioStreamsOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineResumePauseAudioStreamsParameters(backing))
            com.mediasfu.sdk.consumers.resumePauseAudioStreams(resolved)
        }

    // ------------------------------------------------------------------
    // PrepopulateUserMediaParameters & ReadjustParameters properties
    // ------------------------------------------------------------------
    private val prepopulateDelegate
        get() = EnginePrepopulateUserMediaParameters(backing)

    override val socket
        get() = prepopulateDelegate.socket

    override val localSocket
        get() = prepopulateDelegate.localSocket

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val mainScreenFilled: Boolean
        get() = backing.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = backing.adminOnMainScreen

    override val mainScreenPerson: String
        get() = backing.mainScreenPerson

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = backing.audioAlreadyOn

    override val screenForceFullDisplay: Boolean
        get() = backing.screenForceFullDisplay

    override val localStreamScreen: MediaStream?
        get() = backing.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = backing.remoteScreenStream

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val localUIMode: Boolean
        get() = backing.localUIMode

    override val whiteboardStarted: Boolean
        get() = backing.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = backing.whiteboardEnded

    override val annotateScreenStream: Boolean
        get() = backing.annotateScreenStream

    override val updateMainScreenPerson: (String) -> Unit
        get() = { value -> backing.mainScreenPerson = value }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { value -> backing.mainScreenFilled = value }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { value -> backing.adminOnMainScreen = value }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value -> backing.mainHeightWidth = value }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { value -> backing.screenForceFullDisplay = value }

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = { alert -> backing.showAlertHandler = alert }

    // ------------------------------------------------------------------
    // ReadjustParameters helpers
    // ------------------------------------------------------------------
    // ------------------------------------------------------------------
    // AddVideosGridParameters properties
    // ------------------------------------------------------------------
    private val addVideosDelegate
        get() = EngineAddVideosGridParameters(backing)

    override val componentSizes: ComponentSizes
        get() = addVideosDelegate.componentSizes

    override val gridSizes: GridSizes
        get() = addVideosDelegate.gridSizes

    override val paginationDirection: String
        get() = addVideosDelegate.paginationDirection

    override val paginationHeightWidth: Double
        get() = addVideosDelegate.paginationHeightWidth

    override val otherGridStreams: List<List<MediaSfuUIComponent>>
        get() = addVideosDelegate.otherGridStreams

    override val customVideoCardBuilder: CustomVideoCardBuilder?
        get() = addVideosDelegate.customVideoCardBuilder

    override val customAudioCardBuilder: CustomAudioCardBuilder?
        get() = addVideosDelegate.customAudioCardBuilder

    override val customMiniCardBuilder: CustomMiniCardBuilder?
        get() = addVideosDelegate.customMiniCardBuilder

    override val controlMediaAdapter: ControlMediaAdapter?
        get() = addVideosDelegate.controlMediaAdapter

    override fun updateOtherGridStreams(streams: List<List<MediaSfuUIComponent>>) {
        addVideosDelegate.updateOtherGridStreams(streams)
    }

    override fun updateAddAltGrid(add: Boolean) {
        addVideosDelegate.updateAddAltGrid(add)
    }

    override fun updateGridRows(rows: Int) {
        addVideosDelegate.updateGridRows(rows)
    }

    override fun updateGridCols(cols: Int) {
        addVideosDelegate.updateGridCols(cols)
    }

    override fun updateAltGridRows(rows: Int) {
        addVideosDelegate.updateAltGridRows(rows)
    }

    override fun updateAltGridCols(cols: Int) {
        addVideosDelegate.updateAltGridCols(cols)
    }

    override fun updateGridSizes(sizes: GridSizes) {
        addVideosDelegate.updateGridSizes(sizes)
    }

    override suspend fun updateMiniCardsGrid(options: UpdateMiniCardsGridOptions): Result<Unit> {
        return addVideosDelegate.updateMiniCardsGrid(options)
    }

    // ------------------------------------------------------------------
    // GetEstimateParameters properties
    // ------------------------------------------------------------------
    override val fixedPageLimit: Int
        get() = backing.fixedPageLimit

    override val screenPageLimit: Int
        get() = backing.screenPageLimit

    override val removeAltGrid: Boolean
        get() = backing.removeAltGrid

    override val isMediumScreen: Boolean
        get() = backing.isMediumScreen

    override val updateRemoveAltGrid: (Boolean) -> Unit
        get() = { value -> backing.removeAltGrid = value }

    override val calculateRowsAndColumns: (CalculateRowsAndColumnsOptions) -> Result<List<Int>>
        get() = { options -> consumerCalculateRowsAndColumns(options) }

    // ------------------------------------------------------------------
    // DispStreamsParameters properties
    // ------------------------------------------------------------------
    override val lStreams: List<Stream>
        get() = backing.lStreams

    override val hostLabel: String
        get() = backing.hostLabel

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val updateLStreams: (List<Stream>) -> Unit
        get() = { backing.updateLStreams(it) }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { backing.updateMainWindow = it }

    override val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit
        get() = { components -> backing.updateMainGridStream(components) }

    // ------------------------------------------------------------------
    // ProcessConsumerTransportsParameters property (for pause/resume during pagination)
    // ------------------------------------------------------------------
    override val sleep: suspend (SleepOptions) -> Unit
        get() = { options -> kotlinx.coroutines.delay(options.ms.toLong()) }

    override fun getUpdatedAllParams(): EngineReorderStreamsParameters = this
}

internal class EngineOnScreenChangesParameters(
    private val backing: MediasfuParameters
) : OnScreenChangesParameters,
    ReorderStreamsParameters by EngineReorderStreamsParameters(backing) {

    override val eventType
        get() = backing.eventType

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val shared: Boolean
        get() = backing.shared

    override val addForBasic: Boolean
        get() = backing.addForBasic

    override val updateMainHeightWidth: (Double) -> Unit
        get() = backing.updateMainHeightWidth

    override val updateAddForBasic: (Boolean) -> Unit
        get() = backing.updateAddForBasic

    override val itemPageLimit: Int
        get() = backing.itemPageLimit

    override val updateItemPageLimit: (Int) -> Unit
        get() = backing.updateItemPageLimit

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineReorderStreamsParameters(backing))
            com.mediasfu.sdk.consumers.reorderStreams(resolved).getOrThrow()
        }

    override fun getUpdatedAllParams(): OnScreenChangesParameters =
        EngineOnScreenChangesParameters(backing)
}

internal class EngineReUpdateInterParameters(
    private val backing: MediasfuParameters
) : ReUpdateInterParameters,
    ReorderStreamsParameters by EngineReorderStreamsParameters(backing) {

    override val screenPageLimit: Int
        get() = backing.screenPageLimit

    override val itemPageLimit: Int
        get() = backing.itemPageLimit

    override val addForBasic: Boolean
        get() = backing.addForBasic

    override val reorderInterval: Int
        get() = backing.reorderInterval

    override val fastReorderInterval: Int
        get() = backing.fastReorderInterval

    override val eventType
        get() = backing.eventType

    override val participants
        get() = backing.participants

    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val shared: Boolean
        get() = backing.shared

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val adminNameStream: String
        get() = backing.adminNameStream

    override val screenShareNameStream: String
        get() = backing.screenShareNameStream

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val updateMainHeightWidth: (Double) -> Unit
        get() = backing.updateMainHeightWidth

    override val sortAudioLoudness: Boolean
        get() = backing.sortAudioLoudness

    override val lastReorderTime: Int
        get() = backing.lastReorderTime

    override val newLimitedStreams: List<Stream>
        get() = backing.newLimitedStreams

    override val newLimitedStreamsIDs: List<String>
        get() = backing.newLimitedStreamsIDs

    override val oldSoundIds: List<String>
        get() = backing.oldSoundIds

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = backing.updateUpdateMainWindow

    override val updateAddForBasic: (Boolean) -> Unit
        get() = backing.updateAddForBasic

    override val updateSortAudioLoudness: (Boolean) -> Unit
        get() = backing.updateSortAudioLoudness

    override val updateLastReorderTime: (Int) -> Unit
        get() = backing.updateLastReorderTime

    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = backing.updateNewLimitedStreams

    override val updateNewLimitedStreamsIDs: (List<String>) -> Unit
        get() = backing.updateNewLimitedStreamsIDs

    override val updateOldSoundIds: (List<String>) -> Unit
        get() = backing.updateOldSoundIds

    override val updateItemPageLimit: (Int) -> Unit
        get() = backing.updateItemPageLimit

    override val onScreenChanges: suspend (OnScreenChangesOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineOnScreenChangesParameters(backing))
            com.mediasfu.sdk.consumers.onScreenChanges(resolved)
        }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineReorderStreamsParameters(backing))
            com.mediasfu.sdk.consumers.reorderStreams(resolved).getOrThrow()
        }

    override val changeVids: suspend (Any) -> Unit
        get() = backing.changeVids

    override fun getUpdatedAllParams(): ReUpdateInterParameters =
        EngineReUpdateInterParameters(backing)
}

internal class EngineMiniAudioPlayerParameters(
    private val backing: MediasfuParameters
) : MiniAudioPlayerParameters,
    ReUpdateInterParameters by EngineReUpdateInterParameters(backing) {

    override val breakOutRoomStarted: Boolean
        get() = backing.breakOutRoomStarted

    override val breakOutRoomEnded: Boolean
        get() = backing.breakOutRoomEnded

    override val limitedBreakRoom: List<BreakoutParticipant>
        get() = backing.limitedBreakRoom

    override val autoWave: Boolean
        get() = backing.autoWave

    override val meetingDisplayType: String
        get() = backing.meetingDisplayType

    override val dispActiveNames: List<String>
        get() = backing.dispActiveNames

    override val paginatedStreams: List<List<Stream>>
        get() = backing.paginatedStreams

    override val currentUserPage: Int
        get() = backing.currentUserPage

    @Suppress("UNCHECKED_CAST")
    override val audioDecibels: List<AudioDecibels>
        get() = backing.audioDecibels as List<AudioDecibels>

    override val reUpdateInter: ReUpdateInterType
        get() = { options ->
            val resolved = options.copy(parameters = EngineReUpdateInterParameters(backing))
            com.mediasfu.sdk.consumers.reUpdateInter(resolved)
        }

    override val updateParticipantAudioDecibels: UpdateParticipantAudioDecibelsType
        get() = { options ->
            com.mediasfu.sdk.consumers.updateParticipantAudioDecibels(options)
        }

    override val updateAudioDecibels: (List<AudioDecibels>) -> Unit
        get() = { values -> backing.audioDecibels = values }

    override val updateActiveSounds: (List<String>) -> Unit
        get() = backing.updateActiveSounds

    override fun getUpdatedAllParams(): MiniAudioPlayerParameters =
        EngineMiniAudioPlayerParameters(backing)
}

    internal class EngineProcessConsumerTransportsParameters(
        private val backing: MediasfuParameters
    ) : ProcessConsumerTransportsParameters {

        override val remoteScreenStream: List<Stream>
            get() = backing.remoteScreenStream

        override val oldAllStreams: List<Stream>
            get() = backing.oldAllStreams

        override val newLimitedStreams: List<Stream>
            get() = backing.newLimitedStreams

        override val sleep: suspend (SleepOptions) -> Unit
            get() = { options -> delay(options.ms.toLong()) }

        override fun getUpdatedAllParams(): ProcessConsumerTransportsParameters = this
    }

    internal class EngineProcessConsumerTransportsAudioParameters(
        private val backing: MediasfuParameters
    ) : ProcessConsumerTransportsAudioParameters {

        override val sleep: suspend (SleepOptions) -> Unit
            get() = { options -> delay(options.ms.toLong()) }

        override fun getUpdatedAllParams(): ProcessConsumerTransportsAudioParameters = this
    }

    internal class EngineReadjustParameters(
        private val backing: MediasfuParameters
    ) : ReadjustParameters,
        PrepopulateUserMediaParameters by EnginePrepopulateUserMediaParameters(backing) {

        override val eventType
            get() = backing.eventType

        override val shareScreenStarted: Boolean
            get() = backing.shareScreenStarted

        override val shared: Boolean
            get() = backing.shared

        override val mainHeightWidth: Double
            get() = backing.mainHeightWidth

        override val prevMainHeightWidth: Double
            get() = backing.prevMainHeightWidth

        override val hostLabel: String
            get() = backing.hostLabel

        override val firstRound: Boolean
            get() = backing.firstRound

        override val lockScreen: Boolean
            get() = backing.lockScreen

        override val updateMainHeightWidth: (Double) -> Unit
            get() = backing.updateMainHeightWidth

        override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = EnginePrepopulateUserMediaParameters(backing))
                com.mediasfu.sdk.consumers.prepopulateUserMedia(resolved)
            }

        override fun getUpdatedAllParams(): ReadjustParameters = this
    }

    internal class EngineResumePauseAudioStreamsParameters(
        private val backing: MediasfuParameters
    ) : ResumePauseAudioStreamsParameters,
        ProcessConsumerTransportsAudioParameters by EngineProcessConsumerTransportsAudioParameters(backing) {

        override val breakoutRooms: List<List<BreakoutParticipant>>
            get() = backing.breakoutRooms

        override val refParticipants: List<Participant>
            get() = backing.refParticipants

        override val allAudioStreams: List<Stream>
            get() = backing.allAudioStreams

        override val participants: List<Participant>
            get() = backing.participants

        override val islevel: String
            get() = backing.islevel

        override val eventType
            get() = backing.eventType

        override val consumerTransports: List<ConsumerTransportInfo>
            get() = backing.consumerTransportInfos

        override val limitedBreakRoom: List<BreakoutParticipant>
            get() = backing.limitedBreakRoom

        override val hostNewRoom: Int
            get() = backing.hostNewRoom

        override val member: String
            get() = backing.member

        override val updateLimitedBreakRoom: (List<BreakoutParticipant>) -> Unit
            get() = { value -> backing.limitedBreakRoom = value }

        override val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = EngineProcessConsumerTransportsAudioParameters(backing))
                com.mediasfu.sdk.consumers.processConsumerTransportsAudio(resolved)
            }

        override fun getUpdatedAllParams(): ResumePauseAudioStreamsParameters = this
    }

internal class EngineTriggerParameters(
    private val backing: MediasfuParameters
) : TriggerParameters {

    override val socket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.socket

    override val localSocket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.localSocket

    override val roomName: String
        get() = backing.roomName

    override val screenStates: List<Any>
        get() = backing.screenStates.map { it as Any }

    override val participants: List<Any>
        get() = backing.participants.map { it as Any }

    override val updateDateState: Int?
        get() = (backing.updateDateState as? Number)?.toInt()

    override val lastUpdate: Int?
        get() = (backing.lastUpdate as? Number)?.toInt()

    override val nForReadjust: Int?
        get() = backing.nForReadjust

    override val eventType: Any?
        get() = backing.eventType

    override val shared: Boolean
        get() = backing.shared

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val whiteboardStarted: Boolean
        get() = backing.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = backing.whiteboardEnded

    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler

    override fun updateUpdateDateState(timestamp: Int?) {
        backing.updateDateState = timestamp
    }

    override fun updateLastUpdate(lastUpdate: Int?) {
        backing.lastUpdate = lastUpdate
    }

    override fun updateNForReadjust(nForReadjust: Int) {
        backing.nForReadjust = nForReadjust
    }

    override suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>> {
        return com.mediasfu.sdk.consumers.autoAdjust(options)
    }

    override fun getUpdatedAllParams(): EngineTriggerParameters = this
}

internal class EngineCompareScreenStatesParameters(
    private val backing: MediasfuParameters
) : CompareScreenStatesParameters, TriggerParameters by EngineTriggerParameters(backing) {

    override val recordingDisplayType: String
        get() = backing.recordingDisplayType

    override val recordingVideoOptimized: Boolean
        get() = backing.recordingVideoOptimized

    override val screenStates: List<Any>
        get() = backing.screenStates

    override val prevScreenStates: List<ScreenState>
        get() = backing.prevScreenStates

    override val activeNames: List<String>
        get() = backing.activeNames

    override val trigger: suspend (TriggerOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.trigger(options).getOrThrow()
        }

    override fun getUpdatedAllParams(): CompareScreenStatesParameters = this
}

internal class EngineCompareActiveNamesParameters(
    private val backing: MediasfuParameters
) : CompareActiveNamesParameters,
    TriggerParameters by EngineTriggerParameters(backing) {

    override val activeNames: List<String>
        get() = backing.activeNames

    override val prevActiveNames: List<String>
        get() = backing.prevActiveNames

    override val trigger: suspend (TriggerOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineTriggerParameters(backing))
            com.mediasfu.sdk.consumers.trigger(resolved).getOrThrow()
        }

    override val updatePrevActiveNames: (List<String>) -> Unit
        get() = { names -> backing.prevActiveNames = names }

    override fun getUpdatedAllParams(): CompareActiveNamesParameters = this
}

internal class EngineRePortParameters(
    private val backing: MediasfuParameters
) : RePortParameters,
    TriggerParameters by EngineTriggerParameters(backing) {

    override val recordingDisplayType: String
        get() = backing.recordingDisplayType

    override val recordingVideoOptimized: Boolean
        get() = backing.recordingVideoOptimized

    override val screenStates: List<Any>
        get() = backing.screenStates.map { it as Any }

    override val prevScreenStates: List<ScreenState>
        get() = backing.prevScreenStates

    override val activeNames: List<String>
        get() = backing.activeNames

    override val prevActiveNames: List<String>
        get() = backing.prevActiveNames

    override val trigger: suspend (TriggerOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineTriggerParameters(backing))
            com.mediasfu.sdk.consumers.trigger(resolved).getOrThrow()
        }

    override val updatePrevActiveNames: (List<String>) -> Unit
        get() = { names -> backing.prevActiveNames = names }

    override val updateScreenStates: (List<ScreenState>) -> Unit
        get() = { states -> backing.screenStates = states }

    override val updatePrevScreenStates: (List<ScreenState>) -> Unit
        get() = { states -> backing.prevScreenStates = states }

    override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineCompareActiveNamesParameters(backing))
            com.mediasfu.sdk.consumers.compareActiveNames(resolved)
        }

    override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineCompareScreenStatesParameters(backing))
            com.mediasfu.sdk.consumers.compareScreenStates(resolved)
        }

    override val islevel: String
        get() = backing.islevel

    override val mainScreenPerson: String
        get() = backing.mainScreenPerson

    override val adminOnMainScreen: Boolean
        get() = backing.adminOnMainScreen

    override val mainScreenFilled: Boolean
        get() = backing.mainScreenFilled

    override val recordStarted: Boolean
        get() = backing.recordStarted

    override val recordStopped: Boolean
        get() = backing.recordStopped

    override val recordPaused: Boolean
        get() = backing.recordPaused

    override val recordResumed: Boolean
        get() = backing.recordResumed

    override fun getUpdatedAllParams(): RePortParameters = EngineRePortParameters(backing)
}

internal class EnginePrepopulateUserMediaParameters(
    private val backing: MediasfuParameters
) : com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters {

    override val participants: List<com.mediasfu.sdk.model.Participant>
        get() = backing.participants

    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val islevel: String
        get() = backing.islevel

    override val member: String
        get() = backing.member

    override val shared: Boolean
        get() = backing.shared

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val eventType: com.mediasfu.sdk.model.EventType
        get() = backing.eventType

    override val screenId: String?
        get() = backing.screenId.ifEmpty { null }

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val socket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.socket

    override val localSocket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.localSocket

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val mainScreenFilled: Boolean
        get() = backing.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = backing.adminOnMainScreen

    override val mainScreenPerson: String
        get() = backing.mainScreenPerson

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = backing.audioAlreadyOn

    override val oldAllStreams: List<Stream>
        get() = backing.oldAllStreams

    override val screenForceFullDisplay: Boolean
        get() = backing.screenForceFullDisplay

    override val localStreamScreen: com.mediasfu.sdk.webrtc.MediaStream?
        get() = backing.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = backing.remoteScreenStream

    override val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
        get() = backing.localStreamVideo

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val localUIMode: Boolean
        get() = backing.localUIMode

    override val whiteboardStarted: Boolean
        get() = backing.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = backing.whiteboardEnded

    override val virtualStream: Any?
        get() = backing.virtualStream

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val annotateScreenStream: Boolean
        get() = backing.annotateScreenStream

    override val updateMainScreenPerson: (String) -> Unit
        get() = { value -> backing.mainScreenPerson = value }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { value -> backing.mainScreenFilled = value }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { value -> backing.adminOnMainScreen = value }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value -> backing.mainHeightWidth = value }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { value -> backing.screenForceFullDisplay = value }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value -> backing.updateMainWindow = value }

    override val updateShowAlert: (com.mediasfu.sdk.model.ShowAlert?) -> Unit
        get() = { alert -> backing.showAlertHandler = alert }

    override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
        get() = backing.audioDecibels

    override val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit
        get() = { components -> backing.updateMainGridStream(components) }

    override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters = this
}

internal class EngineCloseAndResizeParameters(
    private val backing: MediasfuParameters
) : CloseAndResizeParameters,
    ReorderStreamsParameters by EngineReorderStreamsParameters(backing),
    com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters by EnginePrepopulateUserMediaParameters(backing),
    RePortParameters by EngineRePortParameters(backing) {

    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreamsState

    override val participants: List<Participant>
        get() = backing.participants

    override val allAudioStreams: List<Stream>
        get() = backing.allAudioStreams

    override val activeNames: List<String>
        get() = backing.activeNames

    override val streamNames: List<Stream>
        get() = backing.streamNames

    override val islevel: String
        get() = backing.islevel

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val shared: Boolean
        get() = backing.shared

    override val socket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.socket

    override val localSocket: com.mediasfu.sdk.socket.SocketManager?
        get() = backing.localSocket

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = backing.audioAlreadyOn

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val localUIMode: Boolean
        get() = backing.localUIMode

    override val whiteboardStarted: Boolean
        get() = backing.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = backing.whiteboardEnded

    override val virtualStream: Any?
        get() = backing.virtualStream

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val annotateScreenStream: Boolean
        get() = backing.annotateScreenStream

    override val eventType: com.mediasfu.sdk.model.EventType
        get() = backing.eventType

    override val updateMainWindow: Boolean
        get() = backing.updateMainWindow

    override val mainScreenFilled: Boolean
        get() = backing.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = backing.adminOnMainScreen

    override val mainScreenPerson: String
        get() = backing.mainScreenPerson

    override val recordingDisplayType: String
        get() = backing.recordingDisplayType

    override val recordingVideoOptimized: Boolean
        get() = backing.recordingVideoOptimized

    override val adminIDStream: String
        get() = backing.adminIDStream

    override val screenId: String
        get() = backing.screenId

    override val meetingDisplayType: String
        get() = backing.meetingDisplayType

    override val deferReceive: Boolean
        get() = backing.deferReceive

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val firstAll: Boolean
        get() = backing.firstAll

    override val firstRound: Boolean
        get() = backing.firstRound

    override val gotAllVids: Boolean
        get() = backing.gotAllVids

    override val hostLabel: String
        get() = backing.hostLabel

    override val shareEnded: Boolean
        get() = backing.shareEnded

    override val newLimitedStreams: List<Stream>
        get() = backing.newLimitedStreams

    override val newLimitedStreamsIDs: List<String>
        get() = backing.newLimitedStreamsIDs

    override val screenForceFullDisplay: Boolean
        get() = backing.screenForceFullDisplay

    override val localStreamScreen: com.mediasfu.sdk.webrtc.MediaStream?
        get() = backing.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = backing.remoteScreenStream

    override val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
        get() = backing.localStreamVideo

    override val mainHeightWidth: Double
        get() = backing.mainHeightWidth

    override val updateActiveNames: (List<String>) -> Unit
        get() = { names -> backing.activeNames = names }

    override val updateAllVideoStreams: (List<Stream>) -> Unit
        get() = { streams -> backing.allVideoStreamsState = streams }

    override val updateAllAudioStreams: (List<Stream>) -> Unit
        get() = { streams -> backing.allAudioStreams = streams }

    override val oldAllStreams: List<Stream>
        get() = backing.oldAllStreams

    override val updateShareScreenStarted: (Boolean) -> Unit
        get() = { value -> backing.shareScreenStarted = value }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = { streams -> backing.newLimitedStreams = streams }

    override val updateOldAllStreams: (List<Stream>) -> Unit
        get() = { streams -> backing.oldAllStreams = streams }

    override val updateDeferReceive: (Boolean) -> Unit
        get() = { value -> backing.deferReceive = value }

    override val updateShareEnded: (Boolean) -> Unit
        get() = { value -> backing.shareEnded = value }

    override val updateLockScreen: (Boolean) -> Unit
        get() = { value -> backing.lockScreen = value }

    override val updateFirstAll: (Boolean) -> Unit
        get() = { value -> backing.firstAll = value }

    override val updateFirstRound: (Boolean) -> Unit
        get() = { value -> backing.firstRound = value }

    override val updateMainScreenPerson: (String) -> Unit
        get() = { backing.mainScreenPerson = it }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { backing.mainScreenFilled = it }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { backing.adminOnMainScreen = it }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { backing.mainHeightWidth = it }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { backing.screenForceFullDisplay = it }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { backing.updateMainWindow = it }

    override val updateShowAlert: (com.mediasfu.sdk.model.ShowAlert?) -> Unit
        get() = { backing.showAlertHandler = it }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineReorderStreamsParameters(backing))
            com.mediasfu.sdk.consumers.reorderStreams(resolved).getOrThrow()
        }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EnginePrepopulateUserMediaParameters(backing))
            com.mediasfu.sdk.consumers.prepopulateUserMedia(resolved)
        }

    override val getVideos: suspend (GetVideosOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.getVideos(options).getOrThrow() }

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = EngineRePortParameters(backing))
            com.mediasfu.sdk.consumers.rePort(resolved)
        }

    override fun getUpdatedAllParams(): CloseAndResizeParameters = EngineCloseAndResizeParameters(backing)
}

internal class EngineAddVideosGridParameters(
    private val backing: MediasfuParameters
) : com.mediasfu.sdk.consumers.AddVideosGridParameters {

    override val eventType: com.mediasfu.sdk.model.EventType
        get() = backing.eventType

    override val refParticipants: List<com.mediasfu.sdk.model.Participant>
        get() = backing.refParticipants

    override val audioDecibels: List<AudioDecibels>
        get() = backing.audioDecibels

    override val islevel: String
        get() = backing.islevel

    override val videoAlreadyOn: Boolean
        get() = backing.videoAlreadyOn

    override val localStreamVideo: MediaStream?
        get() = backing.localStreamVideo

    override val keepBackground: Boolean
        get() = backing.keepBackground

    override val virtualStream: MediaStream?
        get() = backing.virtualStream

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val member: String
        get() = backing.member

    override val componentSizes: ComponentSizes
        get() = backing.componentSizes

    override val gridSizes: GridSizes
        get() = backing.gridSizes

    override val paginationDirection: String
        get() = backing.paginationDirection

    override val paginationHeightWidth: Double
        get() = backing.paginationHeightWidth

    override val doPaginate: Boolean
        get() = backing.doPaginate

    override val otherGridStreams: List<List<MediaSfuUIComponent>>
        get() = backing.otherGridStreams

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
                Logger.e("EngineParameterAdapt", "MediaSFU - controlMediaAdapter error -> ${error.message}")
            }
            result.onSuccess {
            }

            result
        }

    override fun updateOtherGridStreams(streams: List<List<MediaSfuUIComponent>>) {
        backing.updateOtherGridStreamsFromEngine(streams)
    }

    override fun updateAddAltGrid(add: Boolean) {
        backing.addAltGrid = add
    }

    override fun updateGridRows(rows: Int) {
        backing.gridRows = rows
    }

    override fun updateGridCols(cols: Int) {
        backing.gridCols = cols
    }

    override fun updateAltGridRows(rows: Int) {
        backing.altGridRows = rows
    }

    override fun updateAltGridCols(cols: Int) {
        backing.altGridCols = cols
    }

    override fun updateGridSizes(sizes: GridSizes) {
        backing.gridSizes = sizes
    }

    override suspend fun updateMiniCardsGrid(options: UpdateMiniCardsGridOptions): Result<Unit> {
        return runCatching {
            updateMiniCardsGridImpl(
                rows = options.rows,
                cols = options.cols,
                defal = options.defal,
                actualRows = options.actualRows,
                gridSizes = backing.gridSizes,
                paginationDirection = backing.paginationDirection,
                paginationHeightWidth = backing.paginationHeightWidth,
                doPaginate = backing.doPaginate,
                componentSizes = backing.componentSizes,
                eventType = backing.eventType.name.lowercase(),
                updateGridRows = { value -> backing.gridRows = value },
                updateGridCols = { value -> backing.gridCols = value },
                updateAltGridRows = { value -> backing.altGridRows = value },
                updateAltGridCols = { value -> backing.altGridCols = value },
                updateGridSizes = { value -> backing.gridSizes = value }
            )
        }
    }

    override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.AddVideosGridParameters =
        EngineAddVideosGridParameters(backing)
}

internal class EngineGetEstimateParameters(
    private val backing: MediasfuParameters
) : GetEstimateParameters {
    override val fixedPageLimit: Int
        get() = backing.fixedPageLimit

    override val screenPageLimit: Int
        get() = backing.screenPageLimit

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val shared: Boolean
        get() = backing.shared

    override val eventType: Any?
        get() = backing.eventType

    override val removeAltGrid: Boolean
        get() = backing.removeAltGrid

    override val isWideScreen: Boolean
        get() = backing.isWideScreen

    override val isMediumScreen: Boolean
        get() = backing.isMediumScreen

    override val updateRemoveAltGrid: (Boolean) -> Unit
        get() = { value -> backing.removeAltGrid = value }

    override val calculateRowsAndColumns: (CalculateRowsAndColumnsOptions) -> Result<List<Int>>
        get() = { options -> consumerCalculateRowsAndColumns(options) }
}

internal class EngineStartShareScreenParameters(
    private val backing: MediasfuParameters
) : com.mediasfu.sdk.consumers.StartShareScreenParameters {

    override val shared: Boolean
        get() = backing.shared

    override val showAlert: Any?
        get() = backing.showAlertHandler

    override val onWeb: Boolean
        get() = true // Default to true for now - platform-specific logic can be added later

    override val device: com.mediasfu.sdk.webrtc.WebRtcDevice?
        get() = backing.device
    
    override val requestScreenCapturePermission: com.mediasfu.sdk.consumers.RequestScreenCapturePermissionType?
        get() = backing.requestScreenCapturePermission

    override fun updateShared(shared: Boolean) {
        backing.shared = shared
    }

    override suspend fun streamSuccessScreen(options: com.mediasfu.sdk.consumers.StreamSuccessScreenOptions): Result<Unit> {
        return runCatching {
            com.mediasfu.sdk.consumers.streamSuccessScreen(options)
            Unit
        }
    }

    override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.StartShareScreenParameters = this
}

internal class EngineConnectRecvTransportParameters(
    private val backing: MediasfuParameters
) : com.mediasfu.sdk.consumers.ConnectRecvTransportParameters {

    override val consumerTransports: List<com.mediasfu.sdk.consumers.ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val updateConsumerTransports: (List<com.mediasfu.sdk.consumers.ConsumerTransportInfo>) -> Unit
        get() = { infos ->
            backing.consumerTransportInfos = infos
            backing.consumerTransportsWebRtc = infos.mapNotNull { it.consumerTransport }
        }

    override val consumerResume: suspend (com.mediasfu.sdk.consumers.ConsumerResumeOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.consumerResume(options) }

    override val consumerResumeParamsProvider: () -> com.mediasfu.sdk.consumers.ConsumerResumeParameters
        get() = { createConsumerResumeParameters(backing) }

    override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.ConnectRecvTransportParameters = this
}

internal class EngineProducerClosedParameters(
    private val backing: MediasfuParameters
) : SocketProducerClosedParameters {

    override val consumerTransports: List<ConsumerTransportInfo>
        get() = backing.consumerTransportInfos

    override val screenId: String
        get() = backing.screenId

    override val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit
        get() = { infos ->
            backing.consumerTransportInfos = infos
            backing.consumerTransportsWebRtc = infos.mapNotNull { it.consumerTransport }
        }

    override val closeAndResize: suspend (String, String) -> Unit
        get() = { producerId, kind ->
            val options = CloseAndResizeOptions(
                producerId = producerId,
                kind = kind,
                parameters = EngineCloseAndResizeParameters(backing)
            )
            com.mediasfu.sdk.consumers.closeAndResize(options)
        }

    override fun getUpdatedAllParams(): SocketProducerClosedParameters = EngineProducerClosedParameters(backing)
}

internal fun createConsumerResumeParameters(
    backing: MediasfuParameters
): com.mediasfu.sdk.consumers.ConsumerResumeParameters {
    return object : com.mediasfu.sdk.consumers.ConsumerResumeParameters,
        com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters by EnginePrepopulateUserMediaParameters(backing) {

        override val nStream: com.mediasfu.sdk.webrtc.MediaStream?
            get() = backing.nStream

        override val allAudioStreams: List<Stream>
            get() = backing.allAudioStreams

        override val allVideoStreams: List<Stream>
            get() = backing.allVideoStreamsState

        override val streamNames: List<Stream>
            get() = backing.streamNames

        override val audStreamNames: List<Stream>
            get() = backing.audStreamNames

        override val meetingDisplayType: String
            get() = backing.meetingDisplayType

        override val firstRound: Boolean
            get() = backing.firstRound

        override val lockScreen: Boolean
            get() = backing.lockScreen

        override val adminVidID: String
            get() = backing.adminVidID

        override val audioOnlyStreams: List<Any>
            get() = backing.audioOnlyStreams

        override val gotAllVids: Boolean
            get() = backing.gotAllVids

        override val deferReceive: Boolean
            get() = backing.deferReceive

        override val firstAll: Boolean
            get() = backing.firstAll

        override val hostLabel: String
            get() = backing.hostLabel

        override val updateAllAudioStreams: (List<Stream>) -> Unit
            get() = { streams -> backing.allAudioStreams = streams }

        override val updateAllVideoStreams: (List<Stream>) -> Unit
            get() = { streams -> backing.allVideoStreamsState = streams }

        override val updateStreamNames: (List<Stream>) -> Unit
            get() = { streams -> backing.streamNames = streams }

        override val updateAudStreamNames: (List<Stream>) -> Unit
            get() = { streams -> backing.audStreamNames = streams }

        override val updateNStream: (com.mediasfu.sdk.webrtc.MediaStream?) -> Unit
            get() = { stream -> backing.nStream = stream }

        override val updateLockScreen: (Boolean) -> Unit
            get() = { value -> backing.lockScreen = value }

        override val updateFirstAll: (Boolean) -> Unit
            get() = { value -> backing.firstAll = value }

        override val updateRemoteScreenStream: (List<Stream>) -> Unit
            get() = { streams -> backing.remoteScreenStream = streams }

        override val updateOldAllStreams: (List<Stream>) -> Unit
            get() = { streams -> backing.oldAllStreams = streams }

        override val updateAudioOnlyStreams: (List<Any>) -> Unit
            get() = { items -> backing.audioOnlyStreams = items }

        override val updateShareScreenStarted: (Boolean) -> Unit
            get() = { value -> backing.shareScreenStarted = value }

        override val updateGotAllVids: (Boolean) -> Unit
            get() = { value -> backing.gotAllVids = value }

        override val updateScreenId: (String) -> Unit
            get() = { value -> backing.screenId = value }

        override val updateDeferReceive: (Boolean) -> Unit
            get() = { value -> backing.deferReceive = value }

        override val reorderStreams: suspend (com.mediasfu.sdk.consumers.ReorderStreamsOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = EngineReorderStreamsParameters(backing))
                com.mediasfu.sdk.consumers.reorderStreams(resolved).getOrThrow()
            }

        override val prepopulateUserMedia: suspend (com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = EnginePrepopulateUserMediaParameters(backing))
                com.mediasfu.sdk.consumers.prepopulateUserMedia(resolved)
            }

        override fun asReorderStreamsParameters(): com.mediasfu.sdk.consumers.ReorderStreamsParameters =
            EngineReorderStreamsParameters(backing)

        override fun asMiniAudioPlayerParameters(): MiniAudioPlayerParameters =
            EngineMiniAudioPlayerParameters(backing)

        override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.ConsumerResumeParameters =
            createConsumerResumeParameters(backing)
    }
}

object EngineParameterAdapters {
    fun checkLimitsAndMakeRequestParameters(
        parameters: MediasfuParameters,
        connectSocket: suspend (String, String, String, String) -> SocketManager?
    ): CheckLimitsAndMakeRequestParameters {
        return EngineCheckLimitsAndMakeRequestParameters(parameters, connectSocket)
    }
}

internal class EngineCheckLimitsAndMakeRequestParameters(
    private val backing: MediasfuParameters,
    override val connectSocket: suspend (String, String, String, String) -> SocketManager?
) : CheckLimitsAndMakeRequestParameters {
    override val apiUserName: String get() = backing.apiUserName
    override val apiToken: String get() = backing.apiToken
    override val link: String get() = backing.link
    override val userName: String get() = backing.member
    override val validate: Boolean get() = true
    override val socket: SocketManager? get() = backing.socket
    override val localSocket: SocketManager? get() = backing.localSocket
    override val updateSocket: (SocketManager?) -> Unit = { backing.socket = it }
    override val updateLocalSocket: (SocketManager?) -> Unit = { backing.localSocket = it }
    
    override fun getUpdatedAllParams(): CheckLimitsAndMakeRequestParameters = this
}
