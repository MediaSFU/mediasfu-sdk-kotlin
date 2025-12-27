package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.consumers.AddVideosGridOptions
import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.CheckScreenShareOptions
import com.mediasfu.sdk.consumers.CheckScreenShareType
import com.mediasfu.sdk.consumers.ChangeVidsParameters
import com.mediasfu.sdk.consumers.ConsumerTransportInfo
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.DispStreamsOptions
import com.mediasfu.sdk.consumers.GetEstimateOptions
import com.mediasfu.sdk.consumers.GetVideosOptions
import com.mediasfu.sdk.consumers.MixStreamsOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsAudioOptions
import com.mediasfu.sdk.consumers.ReadjustOptions
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.consumers.RequestScreenShareType
import com.mediasfu.sdk.consumers.ResumePauseAudioStreamsOptions
import com.mediasfu.sdk.consumers.ResumePauseStreamsOptions
import com.mediasfu.sdk.consumers.StartShareScreenOptions
import com.mediasfu.sdk.consumers.StopShareScreenOptions
import com.mediasfu.sdk.consumers.StopShareScreenType
import com.mediasfu.sdk.consumers.StreamSuccessScreenOptions
import com.mediasfu.sdk.consumers.StreamSuccessScreenParameters
import com.mediasfu.sdk.consumers.RequestScreenCapturePermissionType
import com.mediasfu.sdk.consumers.StartShareScreenParameters
import com.mediasfu.sdk.consumers.connectSendTransportScreen
import com.mediasfu.sdk.consumers.createSendTransport
import com.mediasfu.sdk.consumers.dispStreams
import com.mediasfu.sdk.consumers.getVideos
import com.mediasfu.sdk.consumers.mixStreams
import com.mediasfu.sdk.consumers.prepopulateUserMedia
import com.mediasfu.sdk.consumers.reorderStreams
import com.mediasfu.sdk.consumers.requestScreenShareImpl
import com.mediasfu.sdk.consumers.startShareScreen
import com.mediasfu.sdk.consumers.stopShareScreen
import com.mediasfu.sdk.consumers.streamSuccessScreen
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.stream_methods.ClickScreenShareParameters
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import kotlinx.datetime.Clock

internal fun MediasfuGenericState.createClickScreenShareParameters(): ClickScreenShareParameters =
    MediasfuClickScreenShareParameters(parameters, this)

private class MediasfuClickScreenShareParameters(
    private val backing: MediasfuParameters,
    private val state: MediasfuGenericState
) : ClickScreenShareParameters,
    StreamSuccessScreenParameters,
    StartShareScreenParameters,
    ChangeVidsParameters {

    // -----------------------------------------------------------------------------------------
    // Core ClickScreenShareParameters properties
    // -----------------------------------------------------------------------------------------
    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler ?: ShowAlert { message, type, duration ->
            state.alert.show(message, type, duration)
            backing.showAlert(message, type, duration)
            state.propagateParameterChanges()
        }

    override val roomName: String get() = backing.roomName
    override val member: String get() = backing.member
    override val socket: SocketManager? get() = backing.socket
    override val localSocket: SocketManager? get() = backing.localSocket
    override val islevel: String get() = backing.islevel
    override val youAreCoHost: Boolean get() = backing.youAreCoHost
    override val adminRestrictSetting: Boolean get() = backing.adminRestrictSetting
    override val audioSetting: String get() = backing.audioSetting
    override val videoSetting: String get() = backing.videoSetting
    override val screenshareSetting: String get() = backing.screenshareSetting
    override val chatSetting: String get() = backing.chatSetting
    override val screenAction: Boolean get() = backing.screenAction
    override val screenAlreadyOn: Boolean get() = backing.screenAlreadyOn
    override val screenRequestState: String?
        get() = backing.screenRequestState.takeUnless { it == "none" }
    override val screenRequestTime: Long? get() = backing.screenRequestTime
    override val audioOnlyRoom: Boolean get() = backing.audioOnlyRoom
    override val updateRequestIntervalSeconds: Int get() = backing.updateRequestIntervalSeconds
    override val updateScreenRequestState: (String?) -> Unit
        get() = { value ->
            val normalized = value ?: "none"
            if (backing.screenRequestState != normalized) {
                backing.screenRequestState = normalized
                state.media.screenRequestState = normalized
                if (normalized == "pending" || normalized == "rejected") {
                    backing.screenRequestTime = Clock.System.now().toEpochMilliseconds()
                }
                state.propagateParameterChanges()
            }
        }

    override val updateScreenAlreadyOn: (Boolean) -> Unit
        get() = { value ->
            if (backing.screenAlreadyOn != value) {
                backing.screenAlreadyOn = value
                state.media.screenAlreadyOn = value
                backing.shareScreenStarted = value
                state.media.shareScreenStarted = value
                state.propagateParameterChanges()
            }
        }

    override val checkPermission: CheckPermissionType
        get() = { options -> com.mediasfu.sdk.consumers.checkPermission(options) }

    override val checkScreenShare: CheckScreenShareType
        get() = CheckScreenShareType { options ->
            com.mediasfu.sdk.consumers.checkScreenShare(options)
        }

    // -----------------------------------------------------------------------------------------
    // CheckScreenShareParameters
    // -----------------------------------------------------------------------------------------
    override val shared: Boolean get() = backing.shared
    override val whiteboardStarted: Boolean get() = backing.whiteboardStarted
    override val whiteboardEnded: Boolean get() = backing.whiteboardEnded
    override val breakOutRoomStarted: Boolean get() = backing.breakOutRoomStarted
    override val breakOutRoomEnded: Boolean get() = backing.breakOutRoomEnded
    override val mainHeightWidth: Double get() = backing.mainHeightWidth
    override val mainScreenFilled: Boolean get() = backing.mainScreenFilled

    override val stopShareScreen: StopShareScreenType
        get() = { options ->
            com.mediasfu.sdk.consumers.stopShareScreen(options)
        }

    override val requestScreenShare: RequestScreenShareType
        get() = RequestScreenShareType { options ->
            val params = options.parameters.getUpdatedAllParams() as MediasfuClickScreenShareParameters
            requestScreenShareImpl(
                socket = params.socket,
                showAlert = params.showAlert?.let { handler ->
                    { message: String, type: String, duration: Int -> handler(message, type, duration) }
                },
                localUIMode = backing.localUIMode,
                targetResolution = backing.targetResolution,
                targetResolutionHost = backing.targetResolutionHost,
                startShareScreen = { parameterRef, width, height ->
                    val startOptions = StartShareScreenOptions(
                        parameters = parameterRef as StartShareScreenParameters,
                        targetWidth = width,
                        targetHeight = height
                    )
                    startShareScreen(startOptions)
                },
                parameters = params
            )
        }

    // -----------------------------------------------------------------------------------------
    // StopShareScreenParameters implementation
    // -----------------------------------------------------------------------------------------
    override val shareScreenStarted: Boolean get() = backing.shareScreenStarted
    override val shareEnded: Boolean get() = backing.shareEnded
    override val updateMainWindow: Boolean get() = backing.updateMainWindow
    override val deferReceive: Boolean get() = backing.deferReceive
    override val hostLabel: String get() = backing.hostLabel
    override val lockScreen: Boolean get() = backing.lockScreen
    override val forceFullDisplay: Boolean get() = backing.forceFullDisplay
    override val firstAll: Boolean get() = backing.firstAll
    override val firstRound: Boolean get() = backing.firstRound
    override val localStream: MediaStream? get() = backing.localStream
    override val localStreamScreen: MediaStream? get() = backing.localStreamScreen
    override val adminOnMainScreen: Boolean get() = backing.adminOnMainScreen
    override val mainScreenPerson: String get() = backing.mainScreenPerson
    override val videoAlreadyOn: Boolean get() = backing.videoAlreadyOn
    override val audioAlreadyOn: Boolean get() = backing.audioAlreadyOn
    override val screenForceFullDisplay: Boolean get() = backing.screenForceFullDisplay
    override val remoteScreenStream: List<Stream> get() = backing.remoteScreenStream
    override val eventType: EventType get() = backing.eventType
    override val prevForceFullDisplay: Boolean get() = backing.prevForceFullDisplay
    override val annotateScreenStream: Boolean get() = backing.annotateScreenStream
    override val participants: List<Participant> get() = backing.participants
    override val allVideoStreams: List<Stream> get() = backing.allVideoStreamsState
    override val oldAllStreams: List<Stream> get() = backing.oldAllStreams
    override val isWideScreen: Boolean get() = backing.isWideScreen
    override val localUIMode: Boolean get() = backing.localUIMode
    override val keepBackground: Boolean get() = backing.keepBackground
    override val adminVidID: String get() = backing.adminVidID
    
    // ProcessConsumerTransportsParameters property
    override val sleep: suspend (com.mediasfu.sdk.model.SleepOptions) -> Unit
        get() = { options -> kotlinx.coroutines.delay(options.ms.toLong()) }
    
    override val stopScreenCaptureService: (() -> Unit)?
        get() = backing.stopScreenCaptureService

    override val updateShared: (Boolean) -> Unit
        get() = { value ->
            if (backing.shared != value) {
                backing.shared = value
                state.media.shared = value
                state.propagateParameterChanges()
            }
        }

    override val updateShareScreenStarted: (Boolean) -> Unit
        get() = { value ->
            if (backing.shareScreenStarted != value) {
                backing.shareScreenStarted = value
                state.media.shareScreenStarted = value
                state.propagateParameterChanges()
            }
        }

    override val updateShareEnded: (Boolean) -> Unit
        get() = { value ->
            backing.shareEnded = value
            state.propagateParameterChanges()
        }

    override val updateMainScreenPerson: (String) -> Unit
        get() = { value ->
            backing.mainScreenPerson = value
            state.propagateParameterChanges()
        }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { value ->
            backing.mainScreenFilled = value
            state.propagateParameterChanges()
        }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value ->
            backing.updateMainWindow = value
            state.propagateParameterChanges()
        }

    override val updateDeferReceive: (Boolean) -> Unit
        get() = { value ->
            backing.deferReceive = value
            state.propagateParameterChanges()
        }

    override val updateLockScreen: (Boolean) -> Unit
        get() = { value ->
            backing.lockScreen = value
            state.propagateParameterChanges()
        }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { value ->
            backing.adminOnMainScreen = value
            state.propagateParameterChanges()
        }

    override val updateForceFullDisplay: (Boolean) -> Unit
        get() = { value ->
            backing.forceFullDisplay = value
            state.propagateParameterChanges()
        }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { value ->
            backing.screenForceFullDisplay = value
            state.propagateParameterChanges()
        }

    override val updateFirstAll: (Boolean) -> Unit
        get() = { value ->
            backing.firstAll = value
            state.propagateParameterChanges()
        }

    override val updateFirstRound: (Boolean) -> Unit
        get() = { value ->
            backing.firstRound = value
            state.propagateParameterChanges()
        }

    override val updateLocalStreamScreen: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStreamScreen = stream
            state.propagateParameterChanges()
        }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value ->
            backing.mainHeightWidth = value
            state.propagateParameterChanges()
        }

    override val updateAnnotateScreenStream: (Boolean) -> Unit
        get() = { value ->
            backing.annotateScreenStream = value
            state.propagateParameterChanges()
        }

    override val updateIsScreenboardModalVisible: (Boolean) -> Unit
        get() = { visible ->
            state.modals.setScreenboardVisibility(visible)
        }

    override val updateAllVideoStreams: (List<Stream>) -> Unit
        get() = { value ->
            backing.allVideoStreamsState = value
            state.propagateParameterChanges()
        }

    override val updateOldAllStreams: (List<Stream>) -> Unit
        get() = { value ->
            backing.oldAllStreams = value
            state.propagateParameterChanges()
        }

    override val disconnectSendTransportScreen: suspend (DisconnectSendTransportScreenOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.disconnectSendTransportScreen(options)
        }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.prepopulateUserMedia(options)
        }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.reorderStreams(options)
        }

    override val getVideos: suspend (GetVideosOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.getVideos(options)
        }

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = { handler ->
            backing.showAlertHandler = handler
        }

    override val updateMainGridStream: (List<com.mediasfu.sdk.ui.MediaSfuUIComponent>) -> Unit
        get() = { components ->
            backing.updateMainGridStream(components)
            state.propagateParameterChanges()
        }

    // -----------------------------------------------------------------------------------------
    // DisconnectSendTransportScreenParameters
    // -----------------------------------------------------------------------------------------
    override val screenProducer: WebRtcProducer? get() = backing.screenProducer
    override val localScreenProducer: WebRtcProducer? get() = backing.localScreenProducer

    override fun updateScreenProducer(producer: WebRtcProducer?) {
        backing.screenProducer = producer
        state.propagateParameterChanges()
    }

    override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
        backing.localScreenProducer = producer
        state.propagateParameterChanges()
    }

    override fun getUpdatedAllParams(): MediasfuClickScreenShareParameters = this

    // -----------------------------------------------------------------------------------------
    // ReorderStreamsParameters implementation
    // -----------------------------------------------------------------------------------------
    override val screenId: String get() = backing.screenId
    override val newLimitedStreams: List<Stream> get() = backing.newLimitedStreams
    override val newLimitedStreamsIDs: List<String> get() = backing.newLimitedStreamsIDs
    override val activeSounds: List<String> get() = backing.activeSounds
    override val screenShareIDStream: String get() = backing.screenShareIDStream
    override val screenShareNameStream: String get() = backing.screenShareNameStream
    override val adminIDStream: String get() = backing.adminIDStream
    override val adminNameStream: String get() = backing.adminNameStream

    override fun updateParticipants(participants: List<Participant>) {
        backing.participants = participants
        state.room.updateParticipants(participants)
        state.propagateParameterChanges()
    }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        backing.allVideoStreamsState = streams
        state.propagateParameterChanges()
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        backing.oldAllStreams = streams
        state.propagateParameterChanges()
    }

    override fun updateScreenId(id: String) {
        backing.screenId = id
        state.propagateParameterChanges()
    }

    override fun updateAdminVidID(id: String) {
        backing.adminVidID = id
        state.propagateParameterChanges()
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        backing.newLimitedStreams = streams
        state.propagateParameterChanges()
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        backing.newLimitedStreamsIDs = ids
        state.propagateParameterChanges()
    }

    override fun updateActiveSounds(sounds: List<String>) {
        backing.activeSounds = sounds
        state.propagateParameterChanges()
    }

    override fun updateScreenShareIDStream(id: String) {
        backing.screenShareIDStream = id
        state.propagateParameterChanges()
    }

    override fun updateScreenShareNameStream(name: String) {
        backing.screenShareNameStream = name
        state.propagateParameterChanges()
    }

    override fun updateAdminIDStream(id: String) {
        backing.adminIDStream = id
        state.propagateParameterChanges()
    }

    override fun updateAdminNameStream(name: String) {
        backing.adminNameStream = name
        state.propagateParameterChanges()
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        backing.youYouStream = streams
        state.propagateParameterChanges()
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        backing.youYouStreamIDs = ids
        state.propagateParameterChanges()
    }

    override suspend fun changeVids(options: com.mediasfu.sdk.consumers.ChangeVidsOptions): Result<Unit> =
        runCatching { com.mediasfu.sdk.consumers.changeVids(options) }

    // -----------------------------------------------------------------------------------------
    // ChangeVidsParameters implementation
    // -----------------------------------------------------------------------------------------
    
    // Properties from ResumePauseAudioStreamsParameters (inherited by ChangeVidsParameters)
    override val allAudioStreams: List<Stream>
        get() = backing.allAudioStreams
    
    override val limitedBreakRoom: List<com.mediasfu.sdk.model.BreakoutParticipant>
        get() = backing.limitedBreakRoom
    
    override val updateLimitedBreakRoom: (List<com.mediasfu.sdk.model.BreakoutParticipant>) -> Unit
        get() = { value -> backing.limitedBreakRoom = value }
    
    override val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.processConsumerTransportsAudio(options)
        }
    
    override val pActiveNames: List<String> get() = backing.pActiveNames
    override val activeNames: List<String> get() = backing.activeNames
    override val dispActiveNames: List<String> get() = backing.dispActiveNames
    override val pDispActiveNames: List<String> get() = backing.pDispActiveNames
    override val nForReadjustRecord: Int get() = backing.nForReadjustRecord
    override val nonAlVideoStreams: List<Stream> get() = backing.nonAlVideoStreams
    override val streamNames: List<Stream> get() = backing.streamNames
    override val audStreamNames: List<Stream> get() = backing.audStreamNames
    override val youYouStream: List<Stream> get() = backing.youYouStream
    override val youYouStreamIDs: List<String> get() = backing.youYouStreamIDs
    override val refParticipants: List<Participant> get() = backing.refParticipants
    override val sortAudioLoudness: Boolean get() = backing.sortAudioLoudness
    override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels> get() = backing.audioDecibels
    override val mixedAlVideoStreams: List<Stream> get() = backing.mixedAlVideoStreams
    override val nonAlVideoStreamsMuted: List<Stream> get() = backing.nonAlVideoStreamsMuted
    override val localStreamVideo: MediaStream? get() = backing.localStreamVideo
    override val screenPageLimit: Int get() = backing.screenPageLimit
    override val meetingDisplayType: String get() = backing.meetingDisplayType
    override val meetingVideoOptimized: Boolean get() = backing.meetingVideoOptimized
    override val recordingVideoOptimized: Boolean get() = backing.recordingVideoOptimized
    override val recordingDisplayType: String get() = backing.recordingDisplayType
    override val paginatedStreams: List<List<Stream>> get() = backing.paginatedStreams
    override val itemPageLimit: Int get() = backing.itemPageLimit
    override val doPaginate: Boolean get() = backing.doPaginate
    override val prevDoPaginate: Boolean get() = backing.prevDoPaginate
    override val currentUserPage: Int get() = backing.currentUserPage
    override val consumerTransports: List<ConsumerTransportInfo> get() = backing.consumerTransportInfos
    override val prevMainHeightWidth: Double get() = backing.prevMainHeightWidth
    override val breakoutRooms: List<List<com.mediasfu.sdk.model.BreakoutParticipant>> get() = backing.breakoutRooms
    override val hostNewRoom: Int get() = backing.hostNewRoom
    override val virtualStream: Any? get() = backing.virtualStream
    override val mainRoomsLength: Int get() = backing.mainRoomsLength
    override val memberRoom: Int get() = backing.memberRoom
    override val chatRefStreams: List<Stream> get() = backing.chatRefStreams

    override val updatePActiveNames: (List<String>) -> Unit
        get() = { value ->
            backing.pActiveNames = value
            state.propagateParameterChanges()
        }

    override val updateActiveNames: (List<String>) -> Unit
        get() = { value ->
            backing.activeNames = value
            state.propagateParameterChanges()
        }

    override val updateDispActiveNames: (List<String>) -> Unit
        get() = { value ->
            backing.dispActiveNames = value
            state.propagateParameterChanges()
        }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = { value ->
            backing.newLimitedStreams = value
            state.propagateParameterChanges()
        }

    override val updateNonAlVideoStreams: (List<Stream>) -> Unit
        get() = { value ->
            backing.nonAlVideoStreams = value
            state.propagateParameterChanges()
        }

    override val updateRefParticipants: (List<Participant>) -> Unit
        get() = { value ->
            backing.refParticipants = value
            state.propagateParameterChanges()
        }

    override val updateSortAudioLoudness: (Boolean) -> Unit
        get() = { value ->
            backing.sortAudioLoudness = value
            state.propagateParameterChanges()
        }

    override val updateMixedAlVideoStreams: (List<Stream>) -> Unit
        get() = { value ->
            backing.mixedAlVideoStreams = value
            state.propagateParameterChanges()
        }

    override val updateNonAlVideoStreamsMuted: (List<Stream>) -> Unit
        get() = { value ->
            backing.nonAlVideoStreamsMuted = value
            state.propagateParameterChanges()
        }

    override val updatePaginatedStreams: (List<List<Stream>>) -> Unit
        get() = { value ->
            backing.paginatedStreams = value
            state.propagateParameterChanges()
        }

    override val updateDoPaginate: (Boolean) -> Unit
        get() = { value ->
            backing.doPaginate = value
            state.propagateParameterChanges()
        }

    override val updatePrevDoPaginate: (Boolean) -> Unit
        get() = { value ->
            backing.prevDoPaginate = value
            state.propagateParameterChanges()
        }

    override val updateCurrentUserPage: (Int) -> Unit
        get() = { value ->
            backing.currentUserPage = value
            state.propagateParameterChanges()
        }

    override val updateNumberPages: (Int) -> Unit
        get() = { value ->
            backing.numberPages = value
            state.propagateParameterChanges()
        }

    override val updateMainRoomsLength: (Int) -> Unit
        get() = { value ->
            backing.mainRoomsLength = value
            state.propagateParameterChanges()
        }

    override val updateMemberRoom: (Int) -> Unit
        get() = { value ->
            backing.memberRoom = value
            state.propagateParameterChanges()
        }

    override val updateChatRefStreams: (List<Stream>) -> Unit
        get() = { value -> state.streams.updateChatRefStreams(value) }

    override val updateNForReadjustRecord: (Int) -> Unit
        get() = { value ->
            backing.nForReadjustRecord = value
            state.propagateParameterChanges()
        }

    override val updateShowMiniView: (Boolean) -> Unit
        get() = { value -> state.display.updateShowMiniView(value) }

    override val mixStreams: suspend (MixStreamsOptions) -> List<Stream>
        get() = { options ->
            val result = com.mediasfu.sdk.consumers.mixStreams(options)
            result.getOrElse { emptyList() }.filterIsInstance<Stream>()
        }

    override val dispStreams: suspend (DispStreamsOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.dispStreams(options)
        }

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.rePort(options)
        }

    override val processConsumerTransports: suspend (ProcessConsumerTransportsOptions) -> Result<Unit>
        get() = { options ->
            com.mediasfu.sdk.consumers.processConsumerTransports(options)
        }

    override val resumePauseStreams: suspend (ResumePauseStreamsOptions) -> Result<Unit>
        get() = { options ->
            com.mediasfu.sdk.consumers.resumePauseStreams(options)
        }

    override val readjust: suspend (ReadjustOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.readjust(options)
        }

    override val addVideosGrid: suspend (AddVideosGridOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.addVideosGrid(options)
        }

    override val getEstimate: (GetEstimateOptions) -> List<Int>
        get() = { options ->
            com.mediasfu.sdk.consumers.getEstimate(options)
        }

    override val resumePauseAudioStreams: suspend (ResumePauseAudioStreamsOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.resumePauseAudioStreams(options)
        }

    // -----------------------------------------------------------------------------------------
    // DispStreamsParameters implementation
    // -----------------------------------------------------------------------------------------
    override val lStreams: List<Stream> get() = backing.lStreams
    override val updateLStreams: (List<Stream>) -> Unit
        get() = { streams ->
            // Use the MediasfuParameters updateLStreams which routes through onLStreamsUpdated for UI reactivity
            backing.updateLStreams(streams)
            state.propagateParameterChanges()
        }

    // -----------------------------------------------------------------------------------------
    // CreateSendTransportParameters implementation (via StreamSuccessScreenParameters)
    // -----------------------------------------------------------------------------------------
    override val device: WebRtcDevice? get() = backing.device
    override val requestScreenCapturePermission: RequestScreenCapturePermissionType?
        get() = backing.requestScreenCapturePermission
    override val rtpCapabilities get() = backing.rtpCapabilities
    override val routerRtpCapabilities get() = backing.routerRtpCapabilities
    override val extendedRtpCapabilities get() = backing.extendedRtpCapabilities
    override val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?
        get() = { caps ->
            backing.extendedRtpCapabilities = caps
            state.propagateParameterChanges()
        }
    override var transportCreated: Boolean
        get() = backing.transportCreated
        set(value) {
            backing.transportCreated = value
            state.propagateParameterChanges()
        }
    override var localTransportCreated: Boolean
        get() = backing.localTransportCreated
        set(value) {
            backing.localTransportCreated = value
            state.propagateParameterChanges()
        }
    override var producerTransport: WebRtcTransport?
        get() = backing.producerTransport
        set(value) {
            backing.producerTransport = value
            state.propagateParameterChanges()
        }
    override var localProducerTransport: WebRtcTransport?
        get() = backing.localProducerTransport
        set(value) {
            backing.localProducerTransport = value
            state.propagateParameterChanges()
        }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { transport ->
            backing.producerTransport = transport
            state.propagateParameterChanges()
        }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { transport ->
            backing.localProducerTransport = transport
            state.propagateParameterChanges()
        }

    override val updateTransportCreated: (Boolean) -> Unit
        get() = { created ->
            backing.transportCreated = created
            state.propagateParameterChanges()
        }

    override val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { created ->
            backing.localTransportCreated = created
            state.propagateParameterChanges()
        }

    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.createSendTransport(options)
        }

    // -----------------------------------------------------------------------------------------
    // ConnectSendTransportScreenParameters
    // -----------------------------------------------------------------------------------------
    override val screenParams: ProducerOptionsType? get() = backing.screenParams
    override val params: ProducerOptionsType? get() = backing.params
    override val defScreenID: String get() = backing.screenId

    override val updateScreenProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            backing.screenProducer = producer
            state.propagateParameterChanges()
        }

    override val updateLocalScreenProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            backing.localScreenProducer = producer
            state.propagateParameterChanges()
        }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStream = stream
            state.propagateParameterChanges()
        }

    override val updateDefScreenID: (String) -> Unit
        get() = { value ->
            backing.screenId = value
            state.propagateParameterChanges()
        }

    override val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportScreen(options)
        }

    // -----------------------------------------------------------------------------------------
    // StreamSuccessScreenParameters additions
    // -----------------------------------------------------------------------------------------
    override val transportCreatedScreen: Boolean get() = backing.transportCreatedScreen
    override val sParams: ProducerOptionsType? get() = backing.screenParams

    override val updateTransportCreatedScreen: (Boolean) -> Unit
        get() = { value ->
            backing.transportCreatedScreen = value
            state.propagateParameterChanges()
        }

    override val updateScreenAction: (Boolean) -> Unit
        get() = { value ->
            backing.screenAction = value
            state.propagateParameterChanges()
        }

    override val updateScreenParams: (ProducerOptionsType?) -> Unit
        get() = { value ->
            backing.screenParams = value
            state.propagateParameterChanges()
        }

    // -----------------------------------------------------------------------------------------
    // StartShareScreenParameters
    // -----------------------------------------------------------------------------------------
    override val onWeb: Boolean get() = !backing.localUIMode

    override fun updateShared(shared: Boolean) {
        updateShared.invoke(shared)
    }

    override suspend fun streamSuccessScreen(options: StreamSuccessScreenOptions): Result<Unit> =
        runCatching {
            com.mediasfu.sdk.consumers.streamSuccessScreen(options)
        }
}
