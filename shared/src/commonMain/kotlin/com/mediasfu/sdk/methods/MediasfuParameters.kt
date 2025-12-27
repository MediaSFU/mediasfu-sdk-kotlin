// MediasfuParameters.kt
package com.mediasfu.sdk.methods
import com.mediasfu.sdk.util.Logger

import kotlinx.datetime.Clock
import com.mediasfu.sdk.EngineOnScreenChangesParameters
import com.mediasfu.sdk.EngineReorderStreamsParameters
import com.mediasfu.sdk.consumers.*
import com.mediasfu.sdk.consumers.ChangeVidsParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.ReceiveAllPipedTransportsOptions as SocketReceiveAllPipedTransportsOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.JoinConsumeRoomOptions as SocketJoinConsumeRoomOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.JoinConsumeRoomParameters as SocketJoinConsumeRoomParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.CreateDeviceClientOptions as SocketCreateDeviceClientOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.joinConsumeRoom as socketJoinConsumeRoom
import com.mediasfu.sdk.consumers.Message as ConsumerMessage
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.CloseAndResizeType
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.DisconnectSendTransportVideoType
import com.mediasfu.sdk.model.DisconnectSendTransportAudioType
import com.mediasfu.sdk.model.DisconnectSendTransportScreenType
import com.mediasfu.sdk.model.StopShareScreenType
import com.mediasfu.sdk.model.GetDomainsType
import com.mediasfu.sdk.model.ConnectIpsType
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.DispSpecs
import com.mediasfu.sdk.model.DimensionConstraints
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MainSpecs
import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.model.PollUpdatedData
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.RequestResponse
import com.mediasfu.sdk.model.SeedData
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.SoundPlayer
import com.mediasfu.sdk.model.UserRecordingParams
import com.mediasfu.sdk.model.WaitingRoomParticipant
import com.mediasfu.sdk.model.AudioDecibels as ModelAudioDecibels
import com.mediasfu.sdk.model.MediaDeviceInfo as ModelMediaDeviceInfo
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.model.toTransportMap
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.producer_client.CreateDeviceClientOptions as ProducerCreateDeviceClientOptions
import com.mediasfu.sdk.producer_client.createDeviceClient as producerCreateDeviceClient
import com.mediasfu.sdk.socket.ResponseJoinRoom
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.webrtc.*
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import com.mediasfu.sdk.methods.utils.SoundPlayer as PlatformSoundPlayer
import com.mediasfu.sdk.methods.utils.SoundPlayerOptions as PlatformSoundPlayerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch

/**
 * Central parameter management class for MediaSFU SDK.
 * 
 * This class provides a simplified implementation of the core parameter
 * management functionality needed by the MediaSFU SDK. It manages the
 * global state and provides update functions for all state changes.
 * 
 * ## Key Features:
 * - Manages WebRTC state (transports, producers, consumers)
 * - Handles socket connections and events
 * - Manages UI state and participant data
 * - Provides update functions for all state changes
 * 
 * ## Usage:
 * ```kotlin
 * val parameters = MediasfuParameters()
 * 
 * // Use in consumer methods
 * val options = ConnectIpsOptions(
 *     consumeSockets = emptyList(),
 *     remIP = listOf("100.122.1.1"),
 *     apiUserName = "user",
 *     apiToken = "token",
 *     parameters = parameters
 * )
 * 
 * val result = connectIps(options)
 * ```
 * 
 * ## State Management:
 * - All state is mutable and can be updated via update functions
 * - State changes trigger callbacks for UI updates
 * - Thread-safe access through coroutines
 */
class MediasfuParameters : 
    com.mediasfu.sdk.consumers.ConnectIpsParameters,
    com.mediasfu.sdk.consumers.ConnectLocalIpsParameters,
    com.mediasfu.sdk.model.BanParticipantParameters,
    com.mediasfu.sdk.model.ProducerMediaPausedParameters,
    com.mediasfu.sdk.model.ProducerMediaResumedParameters,
    com.mediasfu.sdk.model.ProducerMediaClosedParameters,
    com.mediasfu.sdk.model.ControlMediaHostParameters,
    com.mediasfu.sdk.model.UpdateConsumingDomainsParameters,
    com.mediasfu.sdk.methods.breakout_rooms_methods.BreakoutRoomUpdatedParameters,
    com.mediasfu.sdk.model.RoomRecordParamsParameters,
    com.mediasfu.sdk.model.RecordingNoticeParameters {

    private val soundPlayerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun defaultSoundPlayer(): SoundPlayer = { options ->
        soundPlayerScope.launch {
            try {
                PlatformSoundPlayer.play(PlatformSoundPlayerOptions(options.soundUrl))
            } catch (t: Throwable) {
                Logger.e("MediasfuParameters", "Sound playback failed: ${'$'}t")
            }
        }
    }
    
    // ========================================================================
    // Core State Properties
    // ========================================================================
    
    // Socket connections
    override var socket: SocketManager? = null
    override var localSocket: SocketManager? = null
    override var consumeSockets: List<Map<String, SocketManager>> = emptyList()
    override var roomRecvIps: List<String> = emptyList()
    override var roomRecvIPs: List<String> = emptyList()
    var apiKey: String = ""
    var apiUserName: String = ""
    var apiToken: String = ""
    var link: String = ""
    var roomData: ResponseJoinRoom = ResponseJoinRoom()
    
    // WebRTC state
    var device: WebRtcDevice? = null
    var sendTransport: WebRtcTransport? = null
    var recvTransport: WebRtcTransport? = null
    
    // Store WebRTC transports separately
    private var _consumerTransportsWebRtc: List<WebRtcTransport> = emptyList()
    
    // Provide TransportType list for interface compatibility (typically empty for this implementation)
    override var consumerTransports: List<com.mediasfu.sdk.model.TransportType> = emptyList()
    
    // Public accessor for WebRTC transports
    var consumerTransportsWebRtc: List<WebRtcTransport>
        get() = _consumerTransportsWebRtc
        set(value) { _consumerTransportsWebRtc = value }
    
    var consumerTransportInfos: List<com.mediasfu.sdk.consumers.ConsumerTransportInfo> = emptyList()
    
    // Producers
    override var audioProducer: WebRtcProducer? = null
    override var videoProducer: WebRtcProducer? = null
    override var screenProducer: WebRtcProducer? = null
    override var localAudioProducer: WebRtcProducer? = null
    override var localVideoProducer: WebRtcProducer? = null
    override var localScreenProducer: WebRtcProducer? = null
    
    // Streams and participants
    var lStreams_: List<Stream> = emptyList()
    override var remoteScreenStream: List<Stream> = emptyList()
    override var oldAllStreams: List<Stream> = emptyList()
    override var newLimitedStreams: List<Stream> = emptyList()
    var validated: Boolean = false
    override var roomName: String = ""
    override var member: String = ""
    var adminPasscode: String = ""
    override var islevel: String = "1"
    override val isLevel: String  // Adapter for AllMembersParameters (camelCase)
        get() = islevel
    var coHost: String = "No coHost"
    var coHostResponsibility: List<CoHostResponsibility> = listOf(
        CoHostResponsibility(name = "participants", value = false, dedicated = false),
        CoHostResponsibility(name = "media", value = false, dedicated = false),
        CoHostResponsibility(name = "waiting", value = false, dedicated = false),
        CoHostResponsibility(name = "chat", value = false, dedicated = false)
    )
    var youAreCoHost: Boolean = false
    var youAreHost: Boolean = false
    var confirmedToRecord: Boolean = false
    override var meetingDisplayType: String = "media"
    override var meetingVideoOptimized: Boolean = false
    override var eventType: EventType = EventType.WEBINAR
    override var participants: List<Participant> = emptyList()
    var filteredParticipants: List<Participant> = emptyList()
    var onParticipantsUpdated: ((List<Participant>) -> Unit)? = null
    var onOtherGridStreamsUpdated: ((List<List<MediaSfuUIComponent>>) -> Unit)? = null
    var onAudioOnlyStreamsUpdated: ((List<Any>) -> Unit)? = null
    
    // Media state change callbacks - wired up by MediasfuGenericState for Compose reactivity
    var onAudioAlreadyOnChanged: ((Boolean) -> Unit)? = null
    var onVideoAlreadyOnChanged: ((Boolean) -> Unit)? = null
    var onScreenAlreadyOnChanged: ((Boolean) -> Unit)? = null
    var onSharedChanged: ((Boolean) -> Unit)? = null
    var onShareScreenStartedChanged: ((Boolean) -> Unit)? = null
    
    // Recording state change callback - wired up by MediasfuGenericState for Compose reactivity
    var onRecordingStateChanged: (() -> Unit)? = null
    
    // Alert state change callback - wired up by MediasfuGenericState for Compose reactivity
    var onAlertStateChanged: ((String, String, Int) -> Unit)? = null
    
    // Main grid stream callback - routes updateMainGridStream to StreamsState for UI reactivity
    var onMainGridStreamUpdated: ((List<MediaSfuUIComponent>) -> Unit)? = null
    
    // lStreams callback - routes lStreams updates to StreamsState for Compose UI reactivity
    var onLStreamsUpdated: ((List<Stream>) -> Unit)? = null
    
    // paginatedStreams callback - routes paginatedStreams updates to StreamsState for Compose UI reactivity
    var onPaginatedStreamsUpdated: ((List<List<Stream>>) -> Unit)? = null
    
    var participantsCounter: Int = 0
    var participantsFilter: String = ""
    var consumeSocketsState: List<ConsumeSocket> = emptyList()
    var rtpCapabilities: com.mediasfu.sdk.webrtc.RtpCapabilities? = null
    var routerRtpCapabilities: com.mediasfu.sdk.webrtc.RtpCapabilities? = null
    var extendedRtpCapabilities: OrtcUtils.ExtendedRtpCapabilities? = null
    var negotiatedRecvRtpCapabilities: com.mediasfu.sdk.webrtc.RtpCapabilities? = null
    var meetingRoomParams: MeetingRoomParams? = null
    override var itemPageLimit: Int = 4
    var audioOnlyRoom: Boolean = false
    override var addForBasic: Boolean = false
    override var screenPageLimit: Int = 4
    override var shareScreenStarted: Boolean = false
    override var shared: Boolean = false
    var targetOrientation: String = "landscape"
    var targetResolution: String = "sd"
    var targetResolutionHost: String = "sd"
    var vidCons: VidCons = VidCons(
        width = DimensionConstraints(ideal = 640),
        height = DimensionConstraints(ideal = 480)
    )
    var frameRate: Int = 5
    var hParams: ProducerOptionsType? = null
    var vParams: ProducerOptionsType? = null
    var screenParams: ProducerOptionsType? = null
    var aParams: ProducerOptionsType? = null

    // Recording state
    var recordingAudioPausesLimit: Int = 0
    var recordingAudioPausesCount: Int = 0
    var recordingAudioSupport: Boolean = false
    var recordingAudioPeopleLimit: Int = 0
    var recordingAudioParticipantsTimeLimit: Int = 0
    var recordingVideoPausesCount: Int = 0
    var recordingVideoPausesLimit: Int = 0
    var recordingVideoSupport: Boolean = false
    var recordingVideoPeopleLimit: Int = 0
    var recordingVideoParticipantsTimeLimit: Int = 0
    var recordingAllParticipantsSupport: Boolean = false
    var recordingVideoParticipantsSupport: Boolean = false
    var recordingAllParticipantsFullRoomSupport: Boolean = false
    var recordingVideoParticipantsFullRoomSupport: Boolean = false
    var recordingPreferredOrientation: String = "landscape"
    var recordingSupportForOtherOrientation: Boolean = false
    var recordingMultiFormatsSupport: Boolean = false
    override var userRecordingParams: UserRecordingParams = UserRecordingParams(
        mainSpecs = MainSpecs(
            mediaOptions = "video",
            audioOptions = "all",
            videoOptions = "all",
            videoType = "fullDisplay",
            videoOptimized = false,
            recordingDisplayType = "media",
            addHls = false
        ),
        dispSpecs = DispSpecs(
            nameTags = true,
            backgroundColor = "#000000",
            nameTagsColor = "#ffffff",
            orientationVideo = "portrait"
        )
    )
    var canRecord: Boolean = false
    var startReport: Boolean = false
    var endReport: Boolean = false
    var recordTimerInterval: Any? = null
    override var recordStartTime: Long? = null
    override var recordElapsedTime: Int = 0
    var recordTimerJob: Job? = null
    override var isTimerRunning: Boolean = false
    override var canPauseResume: Boolean = false
    var recordChangeSeconds: Int = 15000
    var pauseLimit: Int = 0
    var pauseRecordCount: Int = 0
    override var canLaunchRecord: Boolean = true
    var stopLaunchRecord: Boolean = false
    override var participantsAll: List<Participant> = emptyList()

    // Display and meeting state
    override var firstAll: Boolean = false
    override var updateMainWindow: Boolean = false
    override var firstRound: Boolean = false
    var landScaped: Boolean = false
    override var lockScreen: Boolean = false
    override var screenId: String = ""
    var allVideoStreamsState: List<Stream> = emptyList()
    override val allVideoStreams: List<Stream> get() = allVideoStreamsState
    override var newLimitedStreamsIDs: List<String> = emptyList()
    override var activeSounds: List<String> = emptyList()
    override var screenShareIDStream: String = ""
    override var screenShareNameStream: String = ""
    override var adminIDStream: String = ""
    override var adminNameStream: String = ""
    var youYouStream: List<Stream> = emptyList()
    var youYouStreamIDs: List<String> = emptyList()
    override var localStream: MediaStream? = null
    override var recordStarted: Boolean = false
    override var recordResumed: Boolean = false
    override var recordPaused: Boolean = false
    override var recordStopped: Boolean = false
    var adminRestrictSetting: Boolean = false
    var videoRequestState: String = "none"
    var videoRequestTime: Long? = null
    var videoAction: Boolean = false
    override var localStreamVideo: MediaStream? = null
    var userDefaultVideoInputDevice: String = ""
    var currentFacingMode: String = "user"
    var prevFacingMode: String = "user"
    var defVideoID: String = ""
    var allowed: Boolean = false
    override var dispActiveNames: List<String> = emptyList()
    var pDispActiveNames: List<String> = emptyList()
    override var activeNames: List<String> = emptyList()
    override var prevActiveNames: List<String> = emptyList()
    var pActiveNames: List<String> = emptyList()
    var membersReceived: Boolean = false
    var deferScreenReceived: Boolean = false
    var hostFirstSwitch: Boolean = false
    var micAction: Boolean = false
    var screenAction: Boolean = false
    var chatAction: Boolean = false
    var audioRequestState: String = "none"
    var screenRequestState: String = "none"
    var chatRequestState: String = "none"
    var audioRequestTime: Long? = null
    var screenRequestTime: Long? = null
    var chatRequestTime: Long? = null
    var updateRequestIntervalSeconds: Int = 240
    override var oldSoundIds: List<String> = emptyList()
    override var hostLabel: String = "Host"
    override var level: String = "1"
    override var mainScreenFilled: Boolean = false
    override var localStreamScreen: MediaStream? = null
    var screenAlreadyOn: Boolean = false
    var chatAlreadyOn: Boolean = false
    var redirectURL: String = ""
    override var adminVidID: String = ""
    override var streamNames: List<Stream> = emptyList()
    var nonAlVideoStreams: List<Stream> = emptyList()
    override var sortAudioLoudness: Boolean = false
    override var audioDecibels: List<ModelAudioDecibels> = emptyList()
    var mixedAlVideoStreams: List<Stream> = emptyList()
    var nonAlVideoStreamsMuted: List<Stream> = emptyList()
    var paginatedStreams: List<List<Stream>> = emptyList()
    var localStreamAudio: MediaStream? = null
    var defAudioID: String = ""
    var userDefaultAudioInputDevice: String = ""
    var userDefaultAudioOutputDevice: String = ""
    var prevAudioInputDevice: String = ""
    var prevAudioOutputDevice: String = ""
    var prevVideoInputDevice: String = ""
    var audioPaused: Boolean = false
    var audioLevel: Double = 0.0
    override var mainScreenPerson: String = ""
    override var adminOnMainScreen: Boolean = false
    override var screenStates: List<ScreenState> = listOf(
        ScreenState(
            mainScreenPerson = null,
            mainScreenProducerId = null,
            mainScreenFilled = false,
            adminOnMainScreen = false
        )
    )
    override var prevScreenStates: List<ScreenState> = screenStates
    override var updateDateState: Int? = null
    override var lastUpdate: Int? = null
    var nForReadjustRecord: Int = 0
    var fixedPageLimit: Int = 4
    var removeAltGrid: Boolean = false
    override var nForReadjust: Int = 0
    override var reorderInterval: Int = 30000
    override var fastReorderInterval: Int = 10000
    override var lastReorderTime: Int = 0
    var audStreamNames: List<Stream> = emptyList()
    var currentUserPage: Int = 0
    override var mainHeightWidth: Double = 67.0
    var prevMainHeightWidth: Double = 67.0
    var prevDoPaginate: Boolean = false
    var doPaginate: Boolean = false
    override var shareEnded: Boolean = false
    var lStreams: List<Stream> = emptyList()
    var chatRefStreams: List<Stream> = emptyList()
    var controlHeight: Double = 0.06
    override var isWideScreen: Boolean = false
    var isMediumScreen: Boolean = false
    var isSmallScreen: Boolean = false
    var addGrid: Boolean = false
    var addAltGrid: Boolean = false
    var gridRows: Int = 0
    var gridCols: Int = 0
    var altGridRows: Int = 0
    var altGridCols: Int = 0
    var numberPages: Int = 0
    var currentStreams: List<Stream> = emptyList()
    var showMiniView: Boolean = false
    var nStream: MediaStream? = null
    override var deferReceive: Boolean = false
    override var allAudioStreams: List<Stream> = emptyList()
    var remoteScreenStreamState: List<Stream> = emptyList()
    override var gotAllVids: Boolean = false
    var paginationHeightWidth: Double = 40.0
    var paginationDirection: String = "horizontal"
    var gridSizes: GridSizes = GridSizes(gridWidth = 0, gridHeight = 0, altGridWidth = 0, altGridHeight = 0)
    override var screenForceFullDisplay: Boolean = false
    var mainGridStream: List<Any> = emptyList()
    var otherGridStreams: List<List<MediaSfuUIComponent>> = listOf(emptyList(), emptyList())
    var audioOnlyStreams: List<Any> = emptyList()
        set(value) {
            field = value
            onAudioOnlyStreamsUpdated?.invoke(value)
        }
    var videoInputs: List<ModelMediaDeviceInfo> = emptyList()
    var audioInputs: List<ModelMediaDeviceInfo> = emptyList()
    var audioOutputs: List<ModelMediaDeviceInfo> = emptyList()
    var meetingProgressTime: String = "00:00:00"
    var meetingElapsedTime: Int = 0
    var refParticipants: List<Participant> = emptyList()
    override var localUIMode: Boolean = false
    override var whiteboardStarted: Boolean = false
    override var whiteboardEnded: Boolean = false
    override var virtualStream: MediaStream? = null
    override var keepBackground: Boolean = false
    override var annotateScreenStream: Boolean = false

    // Whiteboard state
    var whiteboardUsers: List<com.mediasfu.sdk.model.WhiteboardUser> = emptyList()
    var currentWhiteboardIndex: Int? = null
    var canStartWhiteboard: Boolean = false
    var whiteboardLimit: Int = 0
    var shapes: List<com.mediasfu.sdk.model.WhiteboardShape> = emptyList()
    var useImageBackground: Boolean = true
    var redoStack: List<com.mediasfu.sdk.model.WhiteboardShape> = emptyList()
    var undoStack: List<String> = emptyList()
    var canvasWhiteboard: Any? = null
    var canvasStream: MediaStream? = null  // MediaStream for whiteboard video capture
    var isWhiteboardModalVisible: Boolean = false
    var isConfigureWhiteboardModalVisible: Boolean = false

    // Virtual background state
    var selectedBackground: com.mediasfu.sdk.model.VirtualBackground? = null
    var isBackgroundModalVisible: Boolean = false
    var backgroundHasChanged: Boolean = false
    var processedStream: MediaStream? = null

    var customVideoCardBuilder: CustomVideoCardBuilder? = null
    var customAudioCardBuilder: CustomAudioCardBuilder? = null
    var customMiniCardBuilder: CustomMiniCardBuilder? = null

    // Messages
    var messages: List<com.mediasfu.sdk.model.Message> = emptyList()
    var startDirectMessage: Boolean = false
    var directMessageDetails: Participant? = null
    var showMessagesBadge: Boolean = false

    // Event settings
    var audioSetting: String = "allow"
    var videoSetting: String = "allow"
    var screenshareSetting: String = "allow"
    var chatSetting: String = "allow"

    // Display settings
    var autoWave: Boolean = true
    override var forceFullDisplay: Boolean = true
    override var prevForceFullDisplay: Boolean = false
    override var prevMeetingDisplayType: String = "video"

    // Waiting room
    var waitingRoomFilter: String = ""
    var waitingRoomList: List<WaitingRoomParticipant> = emptyList()
    var waitingRoomCounter: Int = 0
    var filteredWaitingRoomList: List<WaitingRoomParticipant> = emptyList()

    // Requests
    var requestFilter: String = ""
    var requestList: List<Request> = emptyList()
    var requestCounter: Int = 0
    var filteredRequestList: List<Request> = emptyList()

    // Polls
    var polls: List<Poll> = emptyList()
    var poll: Poll? = null
    var isPollModalVisible: Boolean = false

    // Breakout rooms
    override var breakoutRooms: List<List<BreakoutParticipant>> = emptyList()
    var currentRoomIndex: Int = 0
    var canStartBreakout: Boolean = false
    override var breakOutRoomStarted: Boolean = false
    override var breakOutRoomEnded: Boolean = false
    var newParticipantAction: String = "autoAssignNewRoom"
    override var hostNewRoom: Int = -1
    var limitedBreakRoom: List<BreakoutParticipant> = emptyList()
    var mainRoomsLength: Int = 0
    var memberRoom: Int = -1

    // Modal visibility
    var isMenuModalVisible: Boolean = false
    var isRecordingModalVisible: Boolean = false
    var isSettingsModalVisible: Boolean = false
    var isRequestsModalVisible: Boolean = false
    var isWaitingModalVisible: Boolean = false
    var isCoHostModalVisible: Boolean = false
    var isMediaSettingsModalVisible: Boolean = false
    var isDisplaySettingsModalVisible: Boolean = false
    var isParticipantsModalVisible: Boolean = false
    var isMessagesModalVisible: Boolean = false
    var isConfirmExitModalVisible: Boolean = false
    var isConfirmHereModalVisible: Boolean = false
    var isShareEventModalVisible: Boolean = false
    var isLoadingModalVisible: Boolean = false
    var isScreenboardModalVisible: Boolean = false
    var isBreakoutRoomsModalVisible: Boolean = false

    // Recording modal options
    var recordingMediaOptions: String = "video"
    var recordingAudioOptions: String = "all"
    var recordingVideoOptions: String = "all"
    var recordingVideoType: String = "fullDisplay"
    override var recordingVideoOptimized: Boolean = false
    override var recordingDisplayType: String = "media"
    var recordingAddHLS: Boolean = true
    var recordingNameTags: Boolean = true
    var recordingBackgroundColor: String = "#83c0e9"
    var recordingNameTagsColor: String = "#ffffff"
    var recordingAddText: Boolean = false
    var recordingCustomText: String = "Add Text"
    var recordingCustomTextPosition: String = "top"
    var recordingCustomTextColor: String = "#ffffff"
    var recordingOrientationVideo: String = "landscape"
    var clearedToResume: Boolean = true
    var clearedToRecord: Boolean = true
    var recordState: String = "green"
    var showRecordButtons: Boolean = false
    var recordingProgressTime: String = "00:00:00"
    var audioSwitching: Boolean = false
    var videoSwitching: Boolean = false
    override var currentTimeProvider: () -> Long = { Clock.System.now().toEpochMilliseconds() }
    override var playSound: SoundPlayer = defaultSoundPlayer()
    
    // Lambda wrappers for interfaces that expect function properties
    override val updateUpdateMainWindow: (Boolean) -> Unit = { update -> this.updateMainWindow = update }
    override val updateShareScreenStarted: (Boolean) -> Unit = { started -> this.shareScreenStarted = started }
    override val updateShared: (Boolean) -> Unit = { value -> this.shared = value }
    override val updateShareEnded: (Boolean) -> Unit = { ended -> this.shareEnded = ended }
    override val updateDeferReceive: (Boolean) -> Unit = { value -> this.deferReceive = value }
    override val updateLockScreen: (Boolean) -> Unit = { value -> this.lockScreen = value }
    override val updateFirstAll: (Boolean) -> Unit = { value -> this.firstAll = value }
    override val updateFirstRound: (Boolean) -> Unit = { value -> this.firstRound = value }
    override val updateScreenId: (String) -> Unit = { id -> updateScreenId(id) }
    override val updateRecordingMediaOptions: (String) -> Unit = { value -> updateRecordingMediaOptions(value) }
    override val updateRecordingAudioOptions: (String) -> Unit = { value -> this.recordingAudioOptions = value }
    override val updateRecordingVideoOptions: (String) -> Unit = { value -> this.recordingVideoOptions = value }
    override val updateRecordingVideoType: (String) -> Unit = { value -> this.recordingVideoType = value }
    override val updateRecordingVideoOptimized: (Boolean) -> Unit = { value -> this.recordingVideoOptimized = value }
    override val updateRecordingDisplayType: (String) -> Unit = { value -> this.recordingDisplayType = value }
    override val updateRecordingAddHls: (Boolean) -> Unit = { value -> this.recordingAddHLS = value }
    override val updateRecordingNameTags: (Boolean) -> Unit = { value -> this.recordingNameTags = value }
    override val updateRecordingBackgroundColor: (String) -> Unit = { value -> this.recordingBackgroundColor = value }
    override val updateRecordingNameTagsColor: (String) -> Unit = { value -> this.recordingNameTagsColor = value }
    override val updateRecordingAddText: (Boolean) -> Unit = { value -> this.recordingAddText = value }
    override val updateRecordingCustomText: (String) -> Unit = { value -> this.recordingCustomText = value }
    override val updateRecordingCustomTextPosition: (String) -> Unit = { value -> this.recordingCustomTextPosition = value }
    override val updateRecordingCustomTextColor: (String) -> Unit = { value -> this.recordingCustomTextColor = value }
    override val updateRecordingOrientationVideo: (String) -> Unit = { value -> this.recordingOrientationVideo = value }
    override val updateUserRecordingParams: (UserRecordingParams) -> Unit = { params -> this.userRecordingParams = params }
    override val updateRecordingAudioPausesLimit: (Int) -> Unit = { value -> this.recordingAudioPausesLimit = value }
    override val updateRecordingAudioPausesCount: (Int) -> Unit = { value -> this.recordingAudioPausesCount = value }
    override val updateRecordingAudioSupport: (Boolean) -> Unit = { value -> this.recordingAudioSupport = value }
    override val updateRecordingAudioPeopleLimit: (Int) -> Unit = { value -> this.recordingAudioPeopleLimit = value }
    override val updateRecordingAudioParticipantsTimeLimit: (Int) -> Unit = { value -> this.recordingAudioParticipantsTimeLimit = value }
    override val updateRecordingVideoPausesCount: (Int) -> Unit = { value -> this.recordingVideoPausesCount = value }
    override val updateRecordingVideoPausesLimit: (Int) -> Unit = { value -> this.recordingVideoPausesLimit = value }
    override val updateRecordingVideoSupport: (Boolean) -> Unit = { value -> this.recordingVideoSupport = value }
    override val updateRecordingVideoPeopleLimit: (Int) -> Unit = { value -> this.recordingVideoPeopleLimit = value }
    override val updateRecordingVideoParticipantsTimeLimit: (Int) -> Unit = { value -> this.recordingVideoParticipantsTimeLimit = value }
    override val updateRecordingAllParticipantsSupport: (Boolean) -> Unit = { value -> this.recordingAllParticipantsSupport = value }
    override val updateRecordingVideoParticipantsSupport: (Boolean) -> Unit = { value -> this.recordingVideoParticipantsSupport = value }
    override val updateRecordingAllParticipantsFullRoomSupport: (Boolean) -> Unit = { value -> this.recordingAllParticipantsFullRoomSupport = value }
    override val updateRecordingVideoParticipantsFullRoomSupport: (Boolean) -> Unit = { value -> this.recordingVideoParticipantsFullRoomSupport = value }
    val updateAudioProducer: (WebRtcProducer?) -> Unit = { producer -> this.audioProducer = producer }
    override val updateConsumeSockets: (List<ConsumeSocket>) -> Unit = { sockets -> 
        @Suppress("UNCHECKED_CAST")
        this.consumeSockets = sockets as List<Map<String, SocketManager>>
    }
    override val getVideos: suspend (GetVideosOptions) -> Unit = { options -> 
        com.mediasfu.sdk.consumers.getVideos(options) 
    }
    override val rePort: suspend (RePortOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.rePort(options)
    }
    override val updateParticipants: (List<Participant>) -> Unit = { parts -> 
        this.participants = parts
        onParticipantsUpdated?.invoke(parts)
    }
    override val updateAllVideoStreams: (List<Stream>) -> Unit = { streams -> this.allVideoStreamsState = streams }
    override val updateOldAllStreams: (List<Stream>) -> Unit = { streams -> this.oldAllStreams = streams }
    override val updateNewLimitedStreams: (List<Stream>) -> Unit = { streams -> this.newLimitedStreams = streams }
    override val updateMainHeightWidth: (Double) -> Unit = { height -> this.mainHeightWidth = height }
    
    // updateLStreams - routes to UI for Compose reactivity via onLStreamsUpdated callback
    val updateLStreams: (List<Stream>) -> Unit = { streams ->
        this.lStreams = streams
        onLStreamsUpdated?.invoke(streams)
    }
    
    // updatePaginatedStreams - routes to UI for Compose reactivity via onPaginatedStreamsUpdated callback
    val updatePaginatedStreams: (List<List<Stream>>) -> Unit = { streams ->
        this.paginatedStreams = streams
        onPaginatedStreamsUpdated?.invoke(streams)
    }
    
    override val onScreenChanges: suspend (com.mediasfu.sdk.consumers.OnScreenChangesOptions) -> Unit = { options ->
        // Wrap parameters with EngineOnScreenChangesParameters to ensure reorderStreams gets ChangeVidsParameters
        val wrappedOptions = com.mediasfu.sdk.consumers.OnScreenChangesOptions(
            changed = options.changed,
            parameters = com.mediasfu.sdk.EngineOnScreenChangesParameters(this)
        )
        com.mediasfu.sdk.consumers.onScreenChanges(wrappedOptions)
    }
    override val prepopulateUserMedia: suspend (com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.prepopulateUserMedia(options)
    }
    override val reorderStreams: suspend (com.mediasfu.sdk.consumers.ReorderStreamsOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.reorderStreams(options)
    }
    override val reUpdateInter: suspend (com.mediasfu.sdk.consumers.ReUpdateInterOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.reUpdateInter(options)
    }
    override val updateRecordingPreferredOrientation: (String) -> Unit = { value ->
        this.recordingPreferredOrientation = value
    }
    override val updateRecordingSupportForOtherOrientation: (Boolean) -> Unit = { value ->
        this.recordingSupportForOtherOrientation = value
    }
    override val updateRecordingMultiFormatsSupport: (Boolean) -> Unit = { value ->
        this.recordingMultiFormatsSupport = value
    }
    override val showAlert: ShowAlert? = ShowAlert { message, type, duration ->
        showAlert(message, type, duration)
    }
    override val updateRecordStarted: (Boolean) -> Unit = { value ->
        this.recordStarted = value
        onRecordingStateChanged?.invoke()
    }
    override val updateRecordPaused: (Boolean) -> Unit = { value ->
        this.recordPaused = value
        onRecordingStateChanged?.invoke()
    }
    override val updateRecordStopped: (Boolean) -> Unit = { value ->
        this.recordStopped = value
        onRecordingStateChanged?.invoke()
    }
    override val updateRecordState: (String) -> Unit = { value ->
        this.recordState = value
    }
    override val updateShowRecordButtons: (Boolean) -> Unit = { value ->
        this.showRecordButtons = value
        onRecordingStateChanged?.invoke()
    }
    override val updateRecordingProgressTime: (String) -> Unit = { value ->
        this.recordingProgressTime = value
    }
    override val updateRecordElapsedTime: (Int) -> Unit = { value ->
        this.recordElapsedTime = value
    }
    override val updateRecordStartTime: (Long?) -> Unit = { value ->
        this.recordStartTime = value
    }
    override val updateIsTimerRunning: (Boolean) -> Unit = { value ->
        this.isTimerRunning = value
    }
    override val updateCanPauseResume: (Boolean) -> Unit = { value ->
        this.canPauseResume = value
    }
    override val updatePauseRecordCount: (Int) -> Unit = { value ->
        this.pauseRecordCount = value
    }
    override val updateCanLaunchRecord: (Boolean) -> Unit = { value ->
        this.canLaunchRecord = value
    }
    override val updateLastReorderTime: (Int) -> Unit = { value ->
        this.lastReorderTime = value
    }
    override val updateOldSoundIds: (List<String>) -> Unit = { ids ->
        this.oldSoundIds = ids
    }
    override val updateAddForBasic: (Boolean) -> Unit = { value ->
        this.addForBasic = value
    }
    override val updateItemPageLimit: (Int) -> Unit = { value ->
        this.itemPageLimit = value
    }
    override val updateConsumerTransports: (List<com.mediasfu.sdk.model.TransportType>) -> Unit = { transports ->
        this.consumerTransports = transports
    }
    override val closeAndResize: CloseAndResizeType = { options ->
        @Suppress("RedundantSuspendModifier")
        suspend {
            com.mediasfu.sdk.consumers.closeAndResize(options)
        }
    }
    override val updateActiveNames: (List<String>) -> Unit = { names ->
        this.activeNames = names
    }
    override val updateAllAudioStreams: (List<Stream>) -> Unit = { streams ->
        this.allAudioStreams = streams
    }
    override val updateScreenStates: (List<ScreenState>) -> Unit = { states ->
        this.screenStates = states
    }
    override val updatePrevScreenStates: (List<ScreenState>) -> Unit = { states ->
        this.prevScreenStates = states
    }
    override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.compareActiveNames(options)
    }
    override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.compareScreenStates(options)
    }
    override val trigger: suspend (TriggerOptions) -> Unit = { options ->
        com.mediasfu.sdk.consumers.trigger(options)
    }
    override val updatePrevActiveNames: (List<String>) -> Unit = { names ->
        this.prevActiveNames = names
    }
    override val updateLocalStream: (MediaStream?) -> Unit = { stream ->
        this.localStream = stream
    }
    override val updateLocalStreamVideo: (MediaStream?) -> Unit = { stream ->
        this.localStreamVideo = stream
    }
    override val updateLocalStreamScreen: (MediaStream?) -> Unit = { stream ->
        this.localStreamScreen = stream
    }
    override val updateAdminRestrictSetting: (Boolean) -> Unit = { value ->
        this.adminRestrictSetting = value
    }
    override val updateAudioAlreadyOn: (Boolean) -> Unit = { value ->
        this.audioAlreadyOn = value
        onAudioAlreadyOnChanged?.invoke(value)
    }
    override val updateScreenAlreadyOn: (Boolean) -> Unit = { value ->
        this.screenAlreadyOn = value
        onScreenAlreadyOnChanged?.invoke(value)
    }
    override val updateVideoAlreadyOn: (Boolean) -> Unit = { value ->
        this.videoAlreadyOn = value
        onVideoAlreadyOnChanged?.invoke(value)
    }
    override val updateChatAlreadyOn: (Boolean) -> Unit = { value ->
        this.chatAlreadyOn = value
    }
    override val stopShareScreen: StopShareScreenType = { options ->
        com.mediasfu.sdk.consumers.stopShareScreen(options)
    }
    override val disconnectSendTransportVideo: DisconnectSendTransportVideoType = { options ->
        com.mediasfu.sdk.consumers.disconnectSendTransportVideo(
            com.mediasfu.sdk.consumers.DisconnectSendTransportVideoOptions(options.parameters)
        )
    }
    override val disconnectSendTransportAudio: DisconnectSendTransportAudioType = { options ->
        com.mediasfu.sdk.consumers.disconnectSendTransportAudio(
            com.mediasfu.sdk.consumers.DisconnectSendTransportAudioOptions(options.parameters)
        )
    }
    override val disconnectSendTransportScreen: DisconnectSendTransportScreenType = { options ->
        com.mediasfu.sdk.consumers.disconnectSendTransportScreen(
            com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions(options.parameters)
        )
    }
    override val updateForceFullDisplay: (Boolean) -> Unit = { value ->
        this.forceFullDisplay = value
    }
    override val updateAnnotateScreenStream: (Boolean) -> Unit = { value ->
        this.annotateScreenStream = value
    }
    override val updateIsScreenboardModalVisible: (Boolean) -> Unit = { value ->
        this.isScreenboardModalVisible = value
    }
    override val getDomains: GetDomainsType = { options ->
        com.mediasfu.sdk.socket.getDomains(options)
    }
    override val connectIps: ConnectIpsType = { modelOptions ->
        // Convert List<ConsumeSocket> (List<Map<String, Any?>>) to List<Map<String, SocketManager>>
        val socketManagerMaps = modelOptions.consumeSockets.mapNotNull { entry ->
            val converted = entry.mapNotNull { (key, value) ->
                val manager = value as? SocketManager ?: return@mapNotNull null
                key to manager
            }.toMap()
            if (converted.isEmpty()) null else converted
        }

        val joinConsumeRoomBridge: suspend (com.mediasfu.sdk.consumers.JoinConsumeRoomOptions) -> Result<Map<String, Any?>> = { joinOptions ->
            val joinParams = object : SocketJoinConsumeRoomParameters {
                private val backing = this@MediasfuParameters

                override val roomName: String
                    get() = backing.roomName

                override val islevel: String
                    get() = backing.islevel

                override val member: String
                    get() = backing.member

                override val device: WebRtcDevice?
                    get() = backing.device

                override val updateDevice: (WebRtcDevice?) -> Unit = { newDevice ->
                    backing.device = newDevice
                }

                override val receiveAllPipedTransports: suspend (SocketReceiveAllPipedTransportsOptions) -> Unit = { options ->
                    backing.receiveAllPipedTransports(options)
                }

                override val createDeviceClient: suspend (SocketCreateDeviceClientOptions) -> WebRtcDevice? = { deviceOptions ->
                    val rtpCaps = when (val caps = deviceOptions.rtpCapabilities) {
                        is com.mediasfu.sdk.webrtc.RtpCapabilities -> caps
                        is com.mediasfu.sdk.model.RtpCapabilities -> caps.toWebRtcCapabilities()
                        else -> null
                    }

                    if (rtpCaps != null) {
                        runCatching {
                            producerCreateDeviceClient(ProducerCreateDeviceClientOptions(rtpCaps))
                        }.onFailure {
                            Logger.e("MediasfuParameters", "MediaSFU - createDeviceClient bridge failed: ${'$'}{it.message}")
                        }.getOrElse { backing.device }
                    } else {
                        backing.device
                    }
                }

                override fun getUpdatedAllParams(): SocketJoinConsumeRoomParameters = this
            }

            try {
                val response = socketJoinConsumeRoom(
                    SocketJoinConsumeRoomOptions(
                        remoteSock = joinOptions.remoteSock,
                        apiToken = joinOptions.apiToken,
                        apiUserName = joinOptions.apiUserName,
                        parameters = joinParams
                    )
                )

                Result.success(
                    mapOf(
                        "success" to response.success,
                        "rtpCapabilities" to response.rtpCapabilities
                    )
                )
            } catch (error: Exception) {
                Result.failure(
                    ConnectIpsException(
                        "Failed to join consume room: ${'$'}{error.message}",
                        error
                    )
                )
            }
        }
        
        val consumerOptions = com.mediasfu.sdk.consumers.ConnectIpsOptions(
            consumeSockets = socketManagerMaps,
            remIP = modelOptions.remoteIps,
            apiUserName = modelOptions.apiUserName,
            apiKey = modelOptions.apiKey,
            apiToken = modelOptions.apiToken,
            joinConsumeRoomMethod = joinConsumeRoomBridge,
            parameters = this // MediasfuParameters implements both interfaces
        )
        val result = com.mediasfu.sdk.consumers.connectIps(consumerOptions)
        val connectIpsResult = result.getOrElse { throw it }
        
        // Convert List<Map<String, SocketManager>> back to List<ConsumeSocket> (List<Map<String, Any?>>)
        val consumeSockets = connectIpsResult.consumeSockets.map { socketMap -> 
            socketMap.mapValues { it.value as Any? } 
        }
        Pair(consumeSockets, connectIpsResult.roomRecvIPs)
    }
    override val updateNewLimitedStreamsIDs: (List<String>) -> Unit = { ids ->
        updateNewLimitedStreamsIDs(ids)
    }
    
    override val updateBreakoutRooms: (List<List<BreakoutParticipant>>) -> Unit = { rooms ->
        this.breakoutRooms = rooms
    }
    override val updateBreakOutRoomStarted: (Boolean) -> Unit = { started ->
        this.breakOutRoomStarted = started
    }
    override val updateBreakOutRoomEnded: (Boolean) -> Unit = { ended ->
        this.breakOutRoomEnded = ended
    }
    override val updateHostNewRoom: (Int) -> Unit = { room ->
        this.hostNewRoom = room
    }
    override val updateMeetingDisplayType: (String) -> Unit = { type ->
        this.meetingDisplayType = type
    }
    override val updateParticipantsAll: (List<Participant>) -> Unit = { participants ->
        this.participantsAll = participants
    }
    override val updateActiveSounds: (List<String>) -> Unit = { sounds ->
        updateActiveSounds(sounds)
    }
    override val changeVids: suspend (Any) -> Unit = { options ->
        when (options) {
            is ChangeVidsOptions -> changeVids(options)
            else -> {}
        }
    }
    override val updateMainScreenPerson: (String) -> Unit = { value ->
        this.mainScreenPerson = value
    }
    override val updateMainScreenFilled: (Boolean) -> Unit = { value ->
        this.mainScreenFilled = value
    }
    override val updateAdminOnMainScreen: (Boolean) -> Unit = { value ->
        this.adminOnMainScreen = value
    }
    override val updateScreenForceFullDisplay: (Boolean) -> Unit = { value ->
        this.screenForceFullDisplay = value
    }
    override val updateShowAlert: (ShowAlert?) -> Unit = { value ->
        this.showAlertHandler = value
    }
    override val updateSortAudioLoudness: (Boolean) -> Unit = { value ->
        this.sortAudioLoudness = value
    }
    override val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit = { components ->
        @Suppress("UNCHECKED_CAST")
        this.mainGridStream = components as List<Any>
        // Route to UI for Compose reactivity (matches React's mainGridStream pattern)
        onMainGridStreamUpdated?.invoke(components)
    }

    // Permissions
    var checkMediaPermission: Boolean = true
    override var videoAlreadyOn: Boolean = false
    override var audioAlreadyOn: Boolean = false
    var hasCameraPermission: Boolean = false
    var hasAudioPermission: Boolean = false
    var requestPermissionAudio: RequestPermissionAudioType? = null
    var requestPermissionCamera: RequestPermissionCameraType? = null
    
    /**
     * Callback to request screen capture permission.
     * On Android, this should request MediaProjection permission and return
     * mapOf("resultCode" to resultCode, "data" to data) on success, or null on denial.
     */
    var requestScreenCapturePermission: RequestScreenCapturePermissionType? = null
    
    /**
     * Callback to stop the screen capture foreground service.
     * Called when screen sharing is stopped.
     */
    override var stopScreenCaptureService: (() -> Unit)? = null

    // Component sizes
    var componentSizes: ComponentSizes = ComponentSizes(
        mainWidth = 0.0,
        mainHeight = 0.0,
        otherWidth = 0.0,
        otherHeight = 0.0
    )

    // Transport state
    var transportCreated: Boolean = false
    var localTransportCreated: Boolean = false
    var transportCreatedVideo: Boolean = false
    var transportCreatedAudio: Boolean = false
    var transportCreatedScreen: Boolean = false
    var producerTransport: WebRtcTransport? = null
    var localProducerTransport: WebRtcTransport? = null
    var videoParams: ProducerOptionsType? = null
    var audioParams: ProducerOptionsType? = null
    var params: ProducerOptionsType? = null
    var consumingTransports: List<String> = emptyList()

    // Waiting/request totals
    var totalReqWait: Int = 0

    // Alert modal
    var alertVisible: Boolean = false
    var alertMessage: String = ""
    var alertType: String = "info"
    var alertDuration: Int = 3000
    var showAlertHandler: ShowAlert? = null

    // Progress timer
    var progressTimerVisible: Boolean = true
    var progressTimerValue: Int = 0
    
    // ========================================================================
    // Update Functions
    // ========================================================================
    
    override fun updateRoomRecvIPs(roomRecvIPs: List<String>) {
        this.roomRecvIPs = roomRecvIPs
    }
    
    override val updateRoomRecvIps: (List<String>) -> Unit = { ips ->
        this.roomRecvIps = ips
    }
    
    override fun updateConsumeSockets(consumeSockets: List<Map<String, SocketManager>>) {
        this.consumeSockets = consumeSockets
    }
    
    override fun updateAudioProducer(producer: WebRtcProducer?) {
        this.audioProducer = producer
    }
    
    override fun updateVideoProducer(producer: WebRtcProducer?) {
        this.videoProducer = producer
    }
    
    override fun updateScreenProducer(producer: WebRtcProducer?) {
        this.screenProducer = producer
    }
    
    override fun updateLocalAudioProducer(producer: WebRtcProducer?) {
        this.localAudioProducer = producer
    }
    
    override fun updateLocalVideoProducer(producer: WebRtcProducer?) {
        this.localVideoProducer = producer
    }
    
    override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
        this.localScreenProducer = producer
    }
    
    fun updateRecordTimerJob(job: Job?) {
        this.recordTimerJob = job
    }

    fun updateProducerTransport(transport: WebRtcTransport?) {
        this.producerTransport = transport
    }
    
    fun updateLocalProducerTransport(transport: WebRtcTransport?) {
        this.localProducerTransport = transport
    }
    
    fun updateTransportCreated(created: Boolean) {
        this.transportCreated = created
    }
    
    fun updateLocalTransportCreated(created: Boolean) {
        this.localTransportCreated = created
    }
    
    override fun updateUpdateMainWindow(update: Boolean) {
        this.updateMainWindow = update
    }

    override fun updateScreenId(id: String) {
        this.screenId = id
    }

    fun updateShareScreenStarted(started: Boolean) {
        this.shareScreenStarted = started
        onShareScreenStartedChanged?.invoke(started)
    }

    fun updateDeferScreenReceived(deferred: Boolean) {
        this.deferScreenReceived = deferred
    }

    fun updateFilteredParticipants(filtered: List<Participant>) {
        this.filteredParticipants = filtered
        onParticipantsUpdated?.invoke(participants)
    }

    fun updateFilteredWaitingRoomList(filtered: List<WaitingRoomParticipant>) {
        this.filteredWaitingRoomList = filtered
    }

    fun updateRecordingMediaOptions(value: String) {
        this.recordingMediaOptions = value
    }

    fun updateRecordingAudioOptions(value: String) {
        this.recordingAudioOptions = value
    }

    fun updateRecordingVideoOptions(value: String) {
        this.recordingVideoOptions = value
    }

    fun updateRecordingVideoType(value: String) {
        this.recordingVideoType = value
    }

    fun updateRecordingVideoOptimized(value: Boolean) {
        this.recordingVideoOptimized = value
    }

    fun updateRecordingDisplayType(value: String) {
        this.recordingDisplayType = value
    }

    fun updateRecordingAddHls(value: Boolean) {
        this.recordingAddHLS = value
    }

    fun updateRecordingNameTags(value: Boolean) {
        this.recordingNameTags = value
    }

    fun updateRecordingBackgroundColor(value: String) {
        this.recordingBackgroundColor = value
    }

    fun updateRecordingNameTagsColor(value: String) {
        this.recordingNameTagsColor = value
    }

    fun updateRecordingAddText(value: Boolean) {
        this.recordingAddText = value
    }

    fun updateRecordingCustomText(value: String) {
        this.recordingCustomText = value
    }

    fun updateRecordingCustomTextPosition(value: String) {
        this.recordingCustomTextPosition = value
    }

    fun updateRecordingCustomTextColor(value: String) {
        this.recordingCustomTextColor = value
    }

    fun updateRecordingOrientationVideo(value: String) {
        this.recordingOrientationVideo = value
    }

    fun updateUserRecordingParams(params: UserRecordingParams) {
        this.userRecordingParams = params
    }

    fun updateRecordingAudioPausesLimit(value: Int) {
        this.recordingAudioPausesLimit = value
    }

    fun updateRecordingAudioPausesCount(value: Int) {
        this.recordingAudioPausesCount = value
    }

    fun updateRecordingAudioSupport(value: Boolean) {
        this.recordingAudioSupport = value
    }

    fun updateRecordingAudioPeopleLimit(value: Int) {
        this.recordingAudioPeopleLimit = value
    }

    fun updateRecordingAudioParticipantsTimeLimit(value: Int) {
        this.recordingAudioParticipantsTimeLimit = value
    }

    fun updateRecordingVideoPausesCount(value: Int) {
        this.recordingVideoPausesCount = value
    }

    fun updateRecordingVideoPausesLimit(value: Int) {
        this.recordingVideoPausesLimit = value
    }

    fun updateRecordingVideoSupport(value: Boolean) {
        this.recordingVideoSupport = value
    }

    fun updateRecordingVideoPeopleLimit(value: Int) {
        this.recordingVideoPeopleLimit = value
    }

    fun updateRecordingVideoParticipantsTimeLimit(value: Int) {
        this.recordingVideoParticipantsTimeLimit = value
    }

    fun updateRecordingAllParticipantsSupport(value: Boolean) {
        this.recordingAllParticipantsSupport = value
    }

    fun updateRecordingVideoParticipantsSupport(value: Boolean) {
        this.recordingVideoParticipantsSupport = value
    }

    fun updateRecordingAllParticipantsFullRoomSupport(value: Boolean) {
        this.recordingAllParticipantsFullRoomSupport = value
    }

    fun updateRecordingVideoParticipantsFullRoomSupport(value: Boolean) {
        this.recordingVideoParticipantsFullRoomSupport = value
    }

    fun updateRecordingPreferredOrientation(value: String) {
        this.recordingPreferredOrientation = value
    }

    fun updateRecordingSupportForOtherOrientation(value: Boolean) {
        this.recordingSupportForOtherOrientation = value
    }

    fun updateRecordingMultiFormatsSupport(value: Boolean) {
        this.recordingMultiFormatsSupport = value
    }

    fun updateIsParticipantsModalVisible(visible: Boolean) {
        this.isParticipantsModalVisible = visible
    }

    fun updateIsMessagesModalVisible(visible: Boolean) {
        this.isMessagesModalVisible = visible
    }

    fun updateIsRecordingModalVisible(visible: Boolean) {
        this.isRecordingModalVisible = visible
    }

    fun updateIsWaitingModalVisible(visible: Boolean) {
        this.isWaitingModalVisible = visible
    }

    fun updateIsRequestsModalVisible(visible: Boolean) {
        this.isRequestsModalVisible = visible
    }

    fun showAlert(message: String, type: String = "info", duration: Int = 3000) {
        alertMessage = message
        alertType = type
        alertDuration = duration
        alertVisible = true
        // Notify UI to update AlertState for Compose recomposition
        onAlertStateChanged?.invoke(message, type, duration)
    }

    fun hideAlert() {
        alertVisible = false
    }

    fun updateRecordStarted(value: Boolean) {
        this.recordStarted = value
    }

    fun updateRecordPaused(value: Boolean) {
        this.recordPaused = value
    }

    fun updateRecordResumed(value: Boolean) {
        this.recordResumed = value
    }

    fun updateRecordStopped(value: Boolean) {
        this.recordStopped = value
    }

    fun updateRecordState(value: String) {
        this.recordState = value
    }

    fun updateCanRecord(value: Boolean) {
        this.canRecord = value
    }

    fun updateStartReport(value: Boolean) {
        this.startReport = value
    }

    fun updateEndReport(value: Boolean) {
        this.endReport = value
    }

    fun updateShowRecordButtons(value: Boolean) {
        this.showRecordButtons = value
    }

    fun updateRecordingProgressTime(value: String) {
        this.recordingProgressTime = value
    }

    fun updateRecordElapsedTime(value: Int) {
        this.recordElapsedTime = value
    }

    fun updateRecordStartTime(value: Int?) {
        this.recordStartTime = value?.toLong()
    }

    fun updateRecordStartTime(value: Long?) {
        this.recordStartTime = value
    }

    fun updateIsTimerRunning(value: Boolean) {
        this.isTimerRunning = value
    }

    fun updateCanPauseResume(value: Boolean) {
        this.canPauseResume = value
    }

    fun updateRecordTimerInterval(value: Any?) {
        this.recordTimerInterval = value
    }

    fun updatePauseRecordCount(value: Int) {
        this.pauseRecordCount = value
    }

    fun updateClearedToRecord(value: Boolean) {
        this.clearedToRecord = value
    }

    fun updateClearedToResume(value: Boolean) {
        this.clearedToResume = value
    }

    fun updateCanLaunchRecord(value: Boolean) {
        this.canLaunchRecord = value
    }

    fun updateConfirmedToRecord(value: Boolean) {
        this.confirmedToRecord = value
    }

    fun updatePauseLimit(value: Int) {
        this.pauseLimit = value
    }

    fun updateStopLaunchRecord(value: Boolean) {
        this.stopLaunchRecord = value
    }

    fun updateCurrentTimeProvider(provider: () -> Long) {
        this.currentTimeProvider = provider
    }

    fun updatePlaySound(player: SoundPlayer) {
        this.playSound = player
    }
    
    // ========================================================================
    // Mediasfu Functions (Placeholders for now)
    // ========================================================================
    
    override suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit> {
        val resolvedName = (options["name"] as? String)?.takeIf { it.isNotBlank() }
            ?: hostLabel.takeIf { it.isNotBlank() }
            ?: member.takeIf { it.isNotBlank() }
            ?: participants.firstOrNull()?.name?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalArgumentException("prepopulateUserMedia requires a participant name"))

        val candidateParameters = when (val provided = options["parameters"]) {
            is PrepopulateUserMediaParameters -> provided
            is MediasfuParameters -> provided
            null -> this
            else -> provided as? PrepopulateUserMediaParameters
        }

        val consumerParameters = (candidateParameters as? PrepopulateUserMediaParameters)
            ?: return Result.failure(IllegalArgumentException("prepopulateUserMedia requires parameters implementing PrepopulateUserMediaParameters"))

        val typedOptions = PrepopulateUserMediaOptions(
            name = resolvedName,
            parameters = consumerParameters
        )

        return runCatching { com.mediasfu.sdk.consumers.prepopulateUserMedia(typedOptions) }
    }
    
    suspend fun reorderStreams(options: Any): Result<Unit> {
        val resolvedOptions = when (options) {
            is ReorderStreamsOptions -> {
                val params = options.parameters
                val wrappedParams = when {
                    params is ChangeVidsParameters -> params
                    params is MediasfuParameters -> EngineReorderStreamsParameters(params)
                    else -> params
                }

                if (wrappedParams === params) {
                    options
                } else {
                    options.copy(parameters = wrappedParams)
                }
            }
            is Map<*, *> -> {
                val addFlag = options["add"] as? Boolean ?: true
                val screenChangedFlag = options["screenChanged"] as? Boolean ?: false
                val providedStreams = (options["streams"] as? List<*>)
                    ?.filterIsInstance<Stream>()
                    ?: emptyList()

                val candidateParameters = when (val provided = options["parameters"]) {
                    is ReorderStreamsParameters -> provided
                    is MediasfuParameters -> EngineReorderStreamsParameters(provided)
                    null -> EngineReorderStreamsParameters(this)
                    else -> provided as? ReorderStreamsParameters
                }

                val consumerParameters = (candidateParameters as? ReorderStreamsParameters)
                    ?: return Result.failure(IllegalArgumentException("reorderStreams requires parameters implementing ReorderStreamsParameters"))

                ReorderStreamsOptions(
                    add = addFlag,
                    screenChanged = screenChangedFlag,
                    parameters = consumerParameters,
                    streams = providedStreams
                )
            }
            else -> return Result.failure(IllegalArgumentException("Unsupported reorderStreams options type: ${options::class.simpleName}"))
        }

        return com.mediasfu.sdk.consumers.reorderStreams(resolvedOptions)
    }
    
    suspend fun receiveAllPipedTransports(options: Any) {
        when (options) {
            is com.mediasfu.sdk.consumers.ReceiveAllPipedTransportsOptions -> {
                // Already in the expected shape, just forward to default implementation.
                com.mediasfu.sdk.consumers.defaultReceiveAllPipedTransports(options)
            }
            is SocketReceiveAllPipedTransportsOptions -> {
                // Bridge socket-specific options into the consumer-layer helper so we reuse
                // the MediasfuParameter adapters for room/member/signal handling.
                val bridgedOptions = com.mediasfu.sdk.consumers.ReceiveAllPipedTransportsOptions(
                    community = false,
                    nsock = options.nsock,
                    parameters = this
                )
                com.mediasfu.sdk.consumers.defaultReceiveAllPipedTransports(bridgedOptions)
            }
            else -> {
                throw IllegalArgumentException(
                    "Unsupported receiveAllPipedTransports options type: ${options::class.simpleName}"
                )
            }
        }
    }
    
    suspend fun sleep(options: Any) {
        if (options is com.mediasfu.sdk.methods.utils.SleepOptions) {
            com.mediasfu.sdk.methods.utils.sleep(options)
        } else {
            kotlinx.coroutines.delay(100)
        }
    }
    
    suspend fun connectSendTransport(options: Any): Result<Unit> {
        return try {
            if (options is com.mediasfu.sdk.consumers.ConnectSendTransportOptions) {
                com.mediasfu.sdk.consumers.connectSendTransport(options)
                Result.success(Unit)
            } else {
                Result.failure(Exception("Invalid options type for connectSendTransport"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun com.mediasfu.sdk.model.RtpCapabilities.toWebRtcCapabilities(): com.mediasfu.sdk.webrtc.RtpCapabilities {
        val codecList = codecs.map { codec ->
            com.mediasfu.sdk.webrtc.RtpCodecCapability(
                kind = codec.kind.toWebRtcMediaKind(),
                mimeType = codec.mimeType,
                preferredPayloadType = codec.preferredPayloadType,
                clockRate = codec.clockRate,
                channels = codec.channels,
                parameters = codec.parameters?.mapValues { (_, value) -> value?.toString() ?: "" } ?: emptyMap(),
                rtcpFeedback = codec.rtcpFeedback?.map { fb ->
                    com.mediasfu.sdk.webrtc.RtcpFeedback(fb.type, fb.parameter)
                } ?: emptyList()
            )
        }

        val extensionList = headerExtensions.map { extension ->
            com.mediasfu.sdk.webrtc.RtpHeaderExtension(
                kind = extension.kind?.toWebRtcMediaKind(),
                uri = extension.uri,
                preferredId = extension.preferredId,
                preferredEncrypt = extension.preferredEncrypt ?: false,
                direction = extension.direction?.toWebRtcDirection()
            )
        }

        return com.mediasfu.sdk.webrtc.RtpCapabilities(
            codecs = codecList,
            headerExtensions = extensionList,
            fecMechanisms = fecMechanisms
        )
    }

    private fun com.mediasfu.sdk.model.MediaKind.toWebRtcMediaKind(): com.mediasfu.sdk.webrtc.MediaKind =
        when (this) {
            com.mediasfu.sdk.model.MediaKind.AUDIO -> com.mediasfu.sdk.webrtc.MediaKind.AUDIO
            com.mediasfu.sdk.model.MediaKind.VIDEO -> com.mediasfu.sdk.webrtc.MediaKind.VIDEO
        }

    private fun String?.toWebRtcDirection(): com.mediasfu.sdk.webrtc.RtpHeaderDirection? = when (this?.lowercase()) {
        "sendrecv" -> com.mediasfu.sdk.webrtc.RtpHeaderDirection.SENDRECV
        "sendonly" -> com.mediasfu.sdk.webrtc.RtpHeaderDirection.SENDONLY
        "recvonly" -> com.mediasfu.sdk.webrtc.RtpHeaderDirection.RECVONLY
        "inactive" -> com.mediasfu.sdk.webrtc.RtpHeaderDirection.INACTIVE
        else -> null
    }
    
    // ========================================================================
    // Get Updated Parameters (Simplified)
    // ========================================================================
    
    override fun getUpdatedAllParams(): MediasfuParameters = this
    
    // ========================================================================
    // Additional State Management
    // ========================================================================
    
    /**
     * Updates the room name and triggers related state changes.
     */
    fun updateRoomName(roomName: String) {
        this.roomName = roomName
    }
    
    /**
     * Updates the user level and triggers related state changes.
     */
    fun updateIslevel(islevel: String) {
        this.islevel = islevel
    }
    
    /**
     * Updates the member name.
     */
    fun updateMember(member: String) {
        this.member = member
    }
    
    /**
     * Updates the participants list.
     */
    override fun updateParticipants(participants: List<Participant>) {
        this.participants = participants
        val sampleNames = participants.take(5).joinToString { participant ->
            participant.name.ifBlank { "(unnamed)" }
        }
        onParticipantsUpdated?.invoke(participants)
    }

    fun updateOtherGridStreamsFromEngine(streams: List<List<MediaSfuUIComponent>>) {
        otherGridStreams = streams
        onOtherGridStreamsUpdated?.invoke(streams)
    }
    
    /**
     * Updates the all video streams list.
     */
    override fun updateAllVideoStreams(streams: List<Stream>) {
        this.allVideoStreamsState = streams
    }
    
    /**
     * Updates the old all streams list.
     */
    override fun updateOldAllStreams(streams: List<Stream>) {
        this.oldAllStreams = streams
    }
    
    /**
     * Updates the new limited streams list.
     */
    override fun updateNewLimitedStreams(streams: List<Stream>) {
        this.newLimitedStreams = streams
    }
    
    /**
     * Updates the new limited streams IDs list.
     */
    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        this.newLimitedStreamsIDs = ids
    }
    
    /**
     * Updates the admin video ID.
     */
    override fun updateAdminVidID(id: String) {
        this.adminVidID = id
    }
    
    /**
     * Updates the active sounds list.
     */
    override fun updateActiveSounds(sounds: List<String>) {
        this.activeSounds = sounds
    }
    
    /**
     * Updates the screen share ID stream.
     */
    override fun updateScreenShareIDStream(id: String) {
        this.screenShareIDStream = id
    }
    
    /**
     * Updates the screen share name stream.
     */
    override fun updateScreenShareNameStream(name: String) {
        this.screenShareNameStream = name
    }
    
    /**
     * Updates the admin ID stream.
     */
    override fun updateAdminIDStream(id: String) {
        this.adminIDStream = id
    }
    
    /**
     * Updates the admin name stream.
     */
    override fun updateAdminNameStream(name: String) {
        this.adminNameStream = name
    }
    
    /**
     * Updates the you-you stream list.
     */
    override fun updateYouYouStream(streams: List<Stream>) {
        this.youYouStream = streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        this.youYouStreamIDs = ids
    }
    
    /**
     * Updates the update date state timestamp.
     */
    override fun updateUpdateDateState(timestamp: Int?) {
        this.updateDateState = timestamp
    }
    
    /**
     * Updates the last update timestamp.
     */
    override fun updateLastUpdate(lastUpdate: Int?) {
        this.lastUpdate = lastUpdate
    }
    
    /**
     * Updates the nForReadjust value.
     */
    override fun updateNForReadjust(nForReadjust: Int) {
        this.nForReadjust = nForReadjust
    }
    
    /**
     * Auto-adjusts layout parameters.
     */
    override suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>> {
        return com.mediasfu.sdk.consumers.autoAdjust(options)
    }
    
    /**
     * Changes video streams configuration.
     */
    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> {
        return runCatching {
            com.mediasfu.sdk.consumers.changeVids(options)
        }
    }
    
    /**
     * Updates the streams list.
     */
    fun updateStreams(streams: List<Stream>) {
        this.lStreams_ = streams
    }
    
    /**
     * Updates the shared state.
     */
    fun updateShared(value: Boolean) {
        this.shared = value
        onSharedChanged?.invoke(value)
    }

    // ========================================================================
    // Whiteboard Update Functions
    // ========================================================================

    /**
     * Updates the whiteboard users list.
     */
    fun updateWhiteboardUsers(users: List<com.mediasfu.sdk.model.WhiteboardUser>) {
        this.whiteboardUsers = users
    }

    /**
     * Updates the shapes list on the whiteboard.
     */
    fun updateShapes(shapes: List<com.mediasfu.sdk.model.WhiteboardShape>) {
        this.shapes = shapes
    }

    /**
     * Updates whether to use image background on whiteboard.
     */
    fun updateUseImageBackground(value: Boolean) {
        this.useImageBackground = value
    }

    /**
     * Updates the redo stack for whiteboard.
     */
    fun updateRedoStack(stack: List<com.mediasfu.sdk.model.WhiteboardShape>) {
        this.redoStack = stack
    }

    /**
     * Updates the undo stack for whiteboard.
     */
    fun updateUndoStack(stack: List<String>) {
        this.undoStack = stack
    }

    /**
     * Updates whiteboard started state.
     */
    fun updateWhiteboardStarted(value: Boolean) {
        this.whiteboardStarted = value
    }

    /**
     * Updates whiteboard ended state.
     */
    fun updateWhiteboardEnded(value: Boolean) {
        this.whiteboardEnded = value
    }

    /**
     * Updates the canvas whiteboard reference.
     */
    fun updateCanvasWhiteboard(canvas: Any?) {
        this.canvasWhiteboard = canvas
    }

    /**
     * Updates the canvas stream for whiteboard video capture.
     */
    fun updateCanvasStream(stream: MediaStream?) {
        this.canvasStream = stream
    }

    /**
     * Updates whiteboard modal visibility.
     */
    fun updateIsWhiteboardModalVisible(value: Boolean) {
        this.isWhiteboardModalVisible = value
    }

    /**
     * Updates configure whiteboard modal visibility.
     */
    fun updateIsConfigureWhiteboardModalVisible(value: Boolean) {
        this.isConfigureWhiteboardModalVisible = value
    }

    // ========================================================================
    // Virtual Background Update Functions
    // ========================================================================

    /**
     * Updates the selected virtual background.
     */
    fun updateSelectedBackground(background: com.mediasfu.sdk.model.VirtualBackground?) {
        this.selectedBackground = background
    }

    /**
     * Updates the background modal visibility.
     */
    fun updateIsBackgroundModalVisible(value: Boolean) {
        this.isBackgroundModalVisible = value
    }

    /**
     * Updates whether background has changed.
     */
    fun updateBackgroundHasChanged(value: Boolean) {
        this.backgroundHasChanged = value
    }

    /**
     * Updates the keep background state.
     */
    fun updateKeepBackground(value: Boolean) {
        this.keepBackground = value
    }

    /**
     * Updates the processed stream (with virtual background applied).
     */
    fun updateProcessedStream(stream: MediaStream?) {
        this.processedStream = stream
    }
    
    /**
     * Resets all state to initial values.
     */
    fun reset() {
        socket = null
        localSocket = null
        consumeSockets = emptyList()
        roomRecvIPs = emptyList()
        device = null
        sendTransport = null
        recvTransport = null
        consumerTransports = emptyList()
        consumerTransportInfos = emptyList()
        audioProducer = null
        videoProducer = null
        screenProducer = null
        localAudioProducer = null
        localVideoProducer = null
        localScreenProducer = null
        lStreams_ = emptyList()
        remoteScreenStream = emptyList()
        oldAllStreams = emptyList()
        newLimitedStreams = emptyList()
        participants = emptyList()
        videoAlreadyOn = false
        audioAlreadyOn = false
        checkMediaPermission = true
        islevel = "1"
        member = ""
        lockScreen = false
        shared = false
        updateMainWindow = false
        hostLabel = "Host"
        roomName = ""
        transportCreated = false
        localTransportCreated = false
        transportCreatedAudio = false
        params = null
        requestPermissionAudio = null
        requestPermissionCamera = null
        onParticipantsUpdated = null
        onLStreamsUpdated = null
        onPaginatedStreamsUpdated = null
        localUIMode = false
        whiteboardStarted = false
        whiteboardEnded = false
        virtualStream = null
        keepBackground = false
        annotateScreenStream = false
        audioLevel = 0.0
        showAlertHandler = null
        recordTimerJob?.cancel()
        recordTimerJob = null
        isTimerRunning = false
        canPauseResume = false
        recordElapsedTime = 0
        recordingProgressTime = "00:00:00"
        recordStartTime = null
        pauseRecordCount = 0
        showRecordButtons = false
        recordState = "green"
        clearedToRecord = true
        clearedToResume = true
        playSound = defaultSoundPlayer()
        currentTimeProvider = { Clock.System.now().toEpochMilliseconds() }
        soundPlayerScope.coroutineContext.cancelChildren()
    }
}