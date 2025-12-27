package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.consumers.AutoAdjustOptions
import com.mediasfu.sdk.consumers.CompareActiveNamesOptions
import com.mediasfu.sdk.consumers.CompareActiveNamesParameters
import com.mediasfu.sdk.consumers.CompareScreenStatesOptions
import com.mediasfu.sdk.consumers.CompareScreenStatesParameters
import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.RePortParameters
import com.mediasfu.sdk.consumers.ScreenState
import com.mediasfu.sdk.consumers.TriggerOptions
import com.mediasfu.sdk.consumers.TriggerParameters
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.recording_methods.CheckPauseStateOptions
import com.mediasfu.sdk.methods.recording_methods.CheckPauseStateType
import com.mediasfu.sdk.methods.recording_methods.CheckResumeStateOptions
import com.mediasfu.sdk.methods.recording_methods.CheckResumeStateType
import com.mediasfu.sdk.methods.recording_methods.ConfirmRecordingParameters
import com.mediasfu.sdk.methods.recording_methods.RecordPauseTimerOptions
import com.mediasfu.sdk.methods.recording_methods.RecordPauseTimerType
import com.mediasfu.sdk.methods.recording_methods.RecordResumeTimerOptions
import com.mediasfu.sdk.methods.recording_methods.RecordResumeTimerParameters
import com.mediasfu.sdk.methods.recording_methods.RecordResumeTimerType
import com.mediasfu.sdk.methods.recording_methods.RecordStartTimerOptions
import com.mediasfu.sdk.methods.recording_methods.RecordStartTimerParameters
import com.mediasfu.sdk.methods.recording_methods.RecordStartTimerType
import com.mediasfu.sdk.methods.recording_methods.RecordUpdateTimerType
import com.mediasfu.sdk.methods.recording_methods.StartRecordingParameters
import com.mediasfu.sdk.methods.recording_methods.StopRecordingParameters
import com.mediasfu.sdk.methods.recording_methods.UpdateRecordingParameters
import com.mediasfu.sdk.methods.recording_methods.checkPauseState
import com.mediasfu.sdk.methods.recording_methods.checkResumeState
import com.mediasfu.sdk.methods.recording_methods.recordPauseTimer
import com.mediasfu.sdk.methods.recording_methods.recordResumeTimer
import com.mediasfu.sdk.methods.recording_methods.recordStartTimer
import com.mediasfu.sdk.methods.recording_methods.recordUpdateTimer
import com.mediasfu.sdk.methods.whiteboard_methods.CaptureCanvasStreamParameters
import com.mediasfu.sdk.methods.whiteboard_methods.StopCanvasStreamParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.connectSendTransportScreen
import com.mediasfu.sdk.consumers.createSendTransport
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.UserRecordingParams
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.coroutines.Job

internal fun MediasfuGenericState.createConfirmRecordingParameters(): ConfirmRecordingParameters =
    ConfirmRecordingParametersAdapter(this)

internal fun MediasfuGenericState.createStartRecordingParameters(): StartRecordingParameters =
    StartRecordingParametersAdapter(this)

internal fun MediasfuGenericState.createUpdateRecordingParameters(): UpdateRecordingParameters =
    UpdateRecordingParametersAdapter(this)

internal fun MediasfuGenericState.createStopRecordingParameters(): StopRecordingParameters =
    StopRecordingParametersAdapter(this)

private class ConfirmRecordingParametersAdapter(
    private val state: MediasfuGenericState
) : ConfirmRecordingParameters {
    private val parameters: MediasfuParameters
        get() = state.parameters
    private val recording: RecordingState
        get() = state.recording
    private val room: RoomState
        get() = state.room
    private val breakout: BreakoutState
        get() = state.breakout

    override val showAlert: ShowAlert?
        get() = parameters.showAlert

    override val recordingMediaOptions: String
        get() = recording.recordingMediaOptions

    override val recordingAudioOptions: String
        get() = recording.recordingAudioOptions

    override val recordingVideoOptions: String
        get() = recording.recordingVideoOptions

    override val recordingVideoType: String
        get() = recording.recordingVideoType

    override val recordingDisplayType: String
        get() = recording.recordingDisplayType

    override val recordingNameTags: Boolean
        get() = recording.recordingNameTags

    override val recordingBackgroundColor: String
        get() = recording.recordingBackgroundColor

    override val recordingNameTagsColor: String
        get() = recording.recordingNameTagsColor

    override val recordingOrientationVideo: String
        get() = recording.recordingOrientationVideo

    override val recordingAddHls: Boolean
        get() = recording.recordingAddHLS

    override val recordingAddText: Boolean
        get() = recording.recordingAddText

    override val recordingCustomText: String
        get() = recording.recordingCustomText

    override val recordingCustomTextPosition: String
        get() = recording.recordingCustomTextPosition

    override val recordingCustomTextColor: String
        get() = recording.recordingCustomTextColor

    override val meetingDisplayType: String
        get() = room.meetingDisplayType

    override val recordingVideoParticipantsFullRoomSupport: Boolean
        get() = parameters.recordingVideoParticipantsFullRoomSupport

    override val recordingAllParticipantsSupport: Boolean
        get() = recording.recordingAllParticipantsSupport

    override val recordingVideoParticipantsSupport: Boolean
        get() = recording.recordingVideoParticipantsSupport

    override val recordingSupportForOtherOrientation: Boolean
        get() = parameters.recordingSupportForOtherOrientation

    override val recordingPreferredOrientation: String
        get() = parameters.recordingPreferredOrientation

    override val recordingMultiFormatsSupport: Boolean
        get() = parameters.recordingMultiFormatsSupport

    override val recordingVideoOptimized: Boolean
        get() = recording.recordingVideoOptimized

    override val recordingAllParticipantsFullRoomSupport: Boolean
        get() = parameters.recordingAllParticipantsFullRoomSupport

    override val meetingVideoOptimized: Boolean
        get() = room.meetingVideoOptimized

    override val eventType: EventType
        get() = room.eventType

    override val breakOutRoomStarted: Boolean
        get() = breakout.breakOutRoomStarted

    override val breakOutRoomEnded: Boolean
        get() = breakout.breakOutRoomEnded

    override val updateRecordingDisplayType: (String) -> Unit
        get() = recording::updateRecordingDisplayType

    override val updateRecordingVideoOptimized: (Boolean) -> Unit
        get() = recording::updateRecordingVideoOptimized

    override val updateUserRecordingParams: (UserRecordingParams) -> Unit
        get() = { value ->
            parameters.updateUserRecordingParams(value)
            state.propagateParameterChanges()
        }

    override val updateConfirmedToRecord: (Boolean) -> Unit
        get() = state.room::updateConfirmedToRecord

    override fun getUpdatedAllParams(): ConfirmRecordingParameters = this
}

private open class BaseRePortParameters<T>(
    state: MediasfuGenericState
) : BasePrepopulateParameters(state.parameters, state), RePortParameters
    where T : RePortParameters,
          T : PrepopulateUserMediaParameters {
    protected val parameters: MediasfuParameters
        get() = backing
    protected val recording: RecordingState
        get() = state.recording
    protected val streams: StreamsState
        get() = state.streams
    protected val room: RoomState
        get() = state.room

    override val socket: SocketManager?
        get() = parameters.socket

    override val localSocket: SocketManager?
        get() = parameters.localSocket

    override val roomName: String
        get() = room.roomName

    @Suppress("UNCHECKED_CAST")
    override val screenStates: List<Any>
        get() = streams.screenStates as List<Any>

    override val updateDateState: Int?
        get() = parameters.updateDateState

    override val lastUpdate: Int?
        get() = parameters.lastUpdate

    override val nForReadjust: Int?
        get() = parameters.nForReadjust

    override val eventType: EventType
        get() = parameters.eventType

    override val shared: Boolean
        get() = parameters.shared

    override val shareScreenStarted: Boolean
        get() = parameters.shareScreenStarted

    override val whiteboardStarted: Boolean
        get() = parameters.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = parameters.whiteboardEnded

    override val showAlert: ShowAlert?
        get() = parameters.showAlert

    override fun updateUpdateDateState(timestamp: Int?) {
        parameters.updateUpdateDateState(timestamp)
        state.propagateParameterChanges()
    }

    override fun updateLastUpdate(lastUpdate: Int?) {
        parameters.updateLastUpdate(lastUpdate)
        state.propagateParameterChanges()
    }

    override fun updateNForReadjust(nForReadjust: Int) {
        parameters.updateNForReadjust(nForReadjust)
        state.propagateParameterChanges()
    }

    override suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>> =
        parameters.autoAdjust(options)

    override val recordingDisplayType: String
        get() = recording.recordingDisplayType

    override val recordingVideoOptimized: Boolean
        get() = recording.recordingVideoOptimized

    override val activeNames: List<String>
        get() = streams.activeNames

    override val prevActiveNames: List<String>
        get() = streams.prevActiveNames

    override val prevScreenStates: List<ScreenState>
        get() = streams.prevScreenStates

    override val islevel: String
        get() = room.islevel

    override val mainScreenPerson: String
        get() = streams.mainScreenPerson

    override val adminOnMainScreen: Boolean
        get() = streams.adminOnMainScreen

    override val mainScreenFilled: Boolean
        get() = streams.mainScreenFilled

    override val recordStarted: Boolean
        get() = recording.recordStarted

    override val recordStopped: Boolean
        get() = recording.recordStopped

    override val recordPaused: Boolean
        get() = recording.recordPaused

    override val recordResumed: Boolean
        get() = recording.recordResumed

    override val updateScreenStates: (List<ScreenState>) -> Unit
        get() = { states -> streams.updateScreenStates(states) }

    override val updatePrevScreenStates: (List<ScreenState>) -> Unit
        get() = { states -> streams.updatePrevScreenStates(states) }

    override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = createCompareActiveNamesParameters())
            parameters.compareActiveNames(resolved)
        }

    override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = createCompareScreenStatesParameters())
            parameters.compareScreenStates(resolved)
        }

    override val trigger: suspend (TriggerOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = createTriggerParameters())
            parameters.trigger(resolved)
        }

    override val updatePrevActiveNames: (List<String>) -> Unit
        get() = { names -> streams.updatePrevActiveNames(names) }

    @Suppress("UNCHECKED_CAST")
    override fun getUpdatedAllParams(): T = this as T

    protected fun createTriggerParameters(): TriggerParameters = object : TriggerParameters {
        override val socket: SocketManager?
            get() = parameters.socket
        override val localSocket: SocketManager?
            get() = parameters.localSocket
        override val roomName: String
            get() = room.roomName
        @Suppress("UNCHECKED_CAST")
        override val screenStates: List<Any>
            get() = streams.screenStates as List<Any>
        @Suppress("UNCHECKED_CAST")
        override val participants: List<Any>
            get() = parameters.participants as List<Any>
        override val updateDateState: Int?
            get() = parameters.updateDateState
        override val lastUpdate: Int?
            get() = parameters.lastUpdate
        override val nForReadjust: Int?
            get() = parameters.nForReadjust
        override val eventType: EventType
            get() = parameters.eventType
        override val shared: Boolean
            get() = parameters.shared
        override val shareScreenStarted: Boolean
            get() = parameters.shareScreenStarted
        override val whiteboardStarted: Boolean
            get() = parameters.whiteboardStarted
        override val whiteboardEnded: Boolean
            get() = parameters.whiteboardEnded
        override val showAlert: ShowAlert?
            get() = parameters.showAlert

        override fun updateUpdateDateState(timestamp: Int?) {
            parameters.updateUpdateDateState(timestamp)
            state.propagateParameterChanges()
        }

        override fun updateLastUpdate(lastUpdate: Int?) {
            parameters.updateLastUpdate(lastUpdate)
            state.propagateParameterChanges()
        }

        override fun updateNForReadjust(nForReadjust: Int) {
            parameters.updateNForReadjust(nForReadjust)
            state.propagateParameterChanges()
        }

        override suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>> =
            parameters.autoAdjust(options)

        override fun getUpdatedAllParams(): TriggerParameters = this
    }

    protected fun createCompareActiveNamesParameters(): CompareActiveNamesParameters =
        object : CompareActiveNamesParameters, TriggerParameters by createTriggerParameters() {
            override val activeNames: List<String>
                get() = streams.activeNames
            override val prevActiveNames: List<String>
                get() = streams.prevActiveNames
            override val updatePrevActiveNames: (List<String>) -> Unit
                get() = { names -> streams.updatePrevActiveNames(names) }
            override val trigger: suspend (TriggerOptions) -> Unit
                get() = { options ->
                    val resolved = options.copy(parameters = createTriggerParameters())
                    parameters.trigger(resolved)
                }
            override fun getUpdatedAllParams(): CompareActiveNamesParameters = this
        }

    protected fun createCompareScreenStatesParameters(): CompareScreenStatesParameters =
        object : CompareScreenStatesParameters, TriggerParameters by createTriggerParameters() {
            override val recordingDisplayType: String
                get() = recording.recordingDisplayType
            override val recordingVideoOptimized: Boolean
                get() = recording.recordingVideoOptimized
            @Suppress("UNCHECKED_CAST")
            override val screenStates: List<Any>
                get() = streams.screenStates as List<Any>
            override val prevScreenStates: List<ScreenState>
                get() = streams.prevScreenStates
            override val activeNames: List<String>
                get() = streams.activeNames
            override val trigger: suspend (TriggerOptions) -> Unit
                get() = { options ->
                    val resolved = options.copy(parameters = createTriggerParameters())
                    parameters.trigger(resolved)
                }
            override fun getUpdatedAllParams(): CompareScreenStatesParameters = this
        }
}

private class StartRecordingParametersAdapter(
    state: MediasfuGenericState
) : BaseRePortParameters<StartRecordingParameters>(state), StartRecordingParameters {
    private val display get() = state.display

    override val userRecordingParams: UserRecordingParams
        get() = parameters.userRecordingParams

    override val updateIsRecordingModalVisible: (Boolean) -> Unit
        get() = { visible -> state.modals.setRecordingVisibility(visible) }

    override val confirmedToRecord: Boolean
        get() = room.confirmedToRecord

    override val recordingMediaOptions: String
        get() = recording.recordingMediaOptions

    override val videoAlreadyOn: Boolean
        get() = parameters.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = parameters.audioAlreadyOn

    override val startReport: Boolean
        get() = parameters.startReport

    override val endReport: Boolean
        get() = parameters.endReport

    override val canRecord: Boolean
        get() = recording.canRecord

    override val updateClearedToRecord: (Boolean) -> Unit
        get() = recording::updateClearedToRecord

    override val updateRecordStarted: (Boolean) -> Unit
        get() = recording::updateRecordStarted

    override val updateRecordPaused: (Boolean) -> Unit
        get() = recording::updateRecordPaused

    override val updateRecordResumed: (Boolean) -> Unit
        get() = recording::updateRecordResumed

    override val updateStartReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.startReport != value) {
                parameters.updateStartReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateEndReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.endReport != value) {
                parameters.updateEndReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateCanRecord: (Boolean) -> Unit
        get() = recording::updateCanRecord

    override val updateRecordingProgressTime: (String) -> Unit
        get() = recording::updateRecordingProgressTime

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = createRePortParameters())
            parameters.rePort(resolved)
        }

    override val recordStartTimer: RecordStartTimerType
        get() = { options: RecordStartTimerOptions ->
            val resolved = options.copy(parameters = createRecordStartTimerParameters())
            com.mediasfu.sdk.methods.recording_methods.recordStartTimer(resolved)
        }

    override val recordResumeTimer: RecordResumeTimerType
        get() = { options: RecordResumeTimerOptions ->
            val resolved = options.copy(parameters = createRecordResumeTimerParameters())
            com.mediasfu.sdk.methods.recording_methods.recordResumeTimer(resolved)
        }

    override val recordUpdateTimer: RecordUpdateTimerType
        get() = { options -> com.mediasfu.sdk.methods.recording_methods.recordUpdateTimer(options) }

    override val whiteboardStarted: Boolean
        get() = parameters.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = parameters.whiteboardEnded

    override val showAlert: ShowAlert?
        get() = parameters.showAlert

    override val recordTimerJob: Job?
        get() = parameters.recordTimerJob

    override val isTimerRunning: Boolean
        get() = parameters.isTimerRunning

    override val canPauseResume: Boolean
        get() = parameters.canPauseResume

    override val recordChangeSeconds: Int
        get() = parameters.recordChangeSeconds

    override val recordPaused: Boolean
        get() = recording.recordPaused

    override val recordStopped: Boolean
        get() = recording.recordStopped

    override val recordStartTime: Long?
        get() = parameters.recordStartTime

    override val recordElapsedTime: Int
        get() = parameters.recordElapsedTime

    override val updateRecordStartTime: (Long?) -> Unit
        get() = { value ->
            parameters.updateRecordStartTime(value)
            state.propagateParameterChanges()
        }

    override val updateRecordTimerJob: (Job?) -> Unit
        get() = { job ->
            parameters.updateRecordTimerJob(job)
            state.propagateParameterChanges()
        }

    override val updateIsTimerRunning: (Boolean) -> Unit
        get() = { running ->
            parameters.updateIsTimerRunning(running)
            state.propagateParameterChanges()
        }

    override val updateCanPauseResume: (Boolean) -> Unit
        get() = { value ->
            parameters.updateCanPauseResume(value)
            state.propagateParameterChanges()
        }

    override val updateRecordElapsedTime: (Int) -> Unit
        get() = { value ->
            parameters.updateRecordElapsedTime(value)
            state.propagateParameterChanges()
        }

    // -----------------------------------------------------------------------------------------
    // CaptureCanvasStreamParameters implementation (extends ConnectSendTransportScreenParameters)
    // -----------------------------------------------------------------------------------------
    override val canvasStream: MediaStream?
        get() = parameters.canvasStream as? MediaStream
    
    override val updateCanvasStream: (MediaStream?) -> Unit
        get() = { stream -> parameters.updateCanvasStream(stream) }
    
    override val shapes: List<WhiteboardShape>
        get() = parameters.shapes
    
    override val useImageBackground: Boolean
        get() = parameters.useImageBackground
    
    override val webRtcDevice: WebRtcDevice?
        get() = parameters.device

    // -----------------------------------------------------------------------------------------
    // SendTransportSessionParameters / CreateSendTransportParameters implementation
    // -----------------------------------------------------------------------------------------
    override val member: String
        get() = parameters.member
    
    override val device: WebRtcDevice?
        get() = parameters.device
    
    override val rtpCapabilities: RtpCapabilities?
        get() = parameters.rtpCapabilities
    
    override val routerRtpCapabilities: RtpCapabilities?
        get() = parameters.routerRtpCapabilities
    
    override val extendedRtpCapabilities: OrtcUtils.ExtendedRtpCapabilities?
        get() = parameters.extendedRtpCapabilities
    
    override val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?
        get() = { caps ->
            parameters.extendedRtpCapabilities = caps
            state.propagateParameterChanges()
        }
    
    override var transportCreated: Boolean
        get() = parameters.transportCreated
        set(value) {
            parameters.transportCreated = value
            state.propagateParameterChanges()
        }
    
    override var localTransportCreated: Boolean
        get() = parameters.localTransportCreated
        set(value) {
            parameters.localTransportCreated = value
            state.propagateParameterChanges()
        }
    
    override var producerTransport: WebRtcTransport?
        get() = parameters.producerTransport
        set(value) {
            parameters.producerTransport = value
            state.propagateParameterChanges()
        }
    
    override var localProducerTransport: WebRtcTransport?
        get() = parameters.localProducerTransport
        set(value) {
            parameters.localProducerTransport = value
            state.propagateParameterChanges()
        }
    
    override val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { transport ->
            parameters.producerTransport = transport
            state.propagateParameterChanges()
        }
    
    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { transport ->
            parameters.localProducerTransport = transport
            state.propagateParameterChanges()
        }
    
    override val updateTransportCreated: (Boolean) -> Unit
        get() = { created ->
            parameters.transportCreated = created
            state.propagateParameterChanges()
        }
    
    override val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { created ->
            parameters.localTransportCreated = created
            state.propagateParameterChanges()
        }
    
    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
        get() = { options ->
            createSendTransport(options)
        }

    // -----------------------------------------------------------------------------------------
    // ConnectSendTransportScreenParameters implementation
    // -----------------------------------------------------------------------------------------
    override val screenProducer: WebRtcProducer?
        get() = parameters.screenProducer
    
    override val localScreenProducer: WebRtcProducer?
        get() = parameters.localScreenProducer
    
    override val localStream: MediaStream?
        get() = parameters.localStream
    
    override val localStreamScreen: MediaStream?
        get() = parameters.localStreamScreen
    
    override val screenParams: ProducerOptionsType?
        get() = parameters.screenParams
    
    override val params: ProducerOptionsType?
        get() = parameters.params
    
    override val defScreenID: String
        get() = parameters.screenId
    
    override val updateMainWindow: Boolean
        get() = parameters.updateMainWindow
    
    override val updateScreenProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            parameters.screenProducer = producer
            state.propagateParameterChanges()
        }
    
    override val updateLocalScreenProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            parameters.localScreenProducer = producer
            state.propagateParameterChanges()
        }
    
    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            parameters.localStream = stream
            state.propagateParameterChanges()
        }
    
    override val updateLocalStreamScreen: (MediaStream?) -> Unit
        get() = { stream ->
            parameters.localStreamScreen = stream
            state.propagateParameterChanges()
        }
    
    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value ->
            parameters.updateMainWindow = value
            state.propagateParameterChanges()
        }
    
    override val updateDefScreenID: (String) -> Unit
        get() = { value ->
            parameters.screenId = value
            state.propagateParameterChanges()
        }
    
    override val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit
        get() = { options ->
            connectSendTransportScreen(options)
        }

    override fun getUpdatedAllParams(): StartRecordingParameters = this

    private fun createRePortParameters(): RePortParameters = object : RePortParameters,
        TriggerParameters by createTriggerParameters() {
        override val recordingDisplayType: String
            get() = recording.recordingDisplayType
        override val recordingVideoOptimized: Boolean
            get() = recording.recordingVideoOptimized
        override val prevScreenStates: List<ScreenState>
            get() = streams.prevScreenStates
        override val activeNames: List<String>
            get() = streams.activeNames
        override val prevActiveNames: List<String>
            get() = streams.prevActiveNames
        override val updatePrevActiveNames: (List<String>) -> Unit
            get() = { names -> streams.updatePrevActiveNames(names) }
        override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createCompareActiveNamesParameters())
                parameters.compareActiveNames(resolved)
            }
        override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createCompareScreenStatesParameters())
                parameters.compareScreenStates(resolved)
            }
        override val updateScreenStates: (List<ScreenState>) -> Unit
            get() = { states -> streams.updateScreenStates(states) }
        override val updatePrevScreenStates: (List<ScreenState>) -> Unit
            get() = { states -> streams.updatePrevScreenStates(states) }
        override val recordStarted: Boolean
            get() = recording.recordStarted
        override val recordStopped: Boolean
            get() = recording.recordStopped
        override val recordPaused: Boolean
            get() = recording.recordPaused
        override val recordResumed: Boolean
            get() = recording.recordResumed
        override val islevel: String
            get() = room.islevel
        override val mainScreenPerson: String
            get() = streams.mainScreenPerson
        override val adminOnMainScreen: Boolean
            get() = streams.adminOnMainScreen
        override val mainScreenFilled: Boolean
            get() = streams.mainScreenFilled
        override val trigger: suspend (TriggerOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createTriggerParameters())
                parameters.trigger(resolved)
            }
        override fun getUpdatedAllParams(): RePortParameters = this
    }

    private fun createRecordStartTimerParameters(): RecordStartTimerParameters = object : RecordStartTimerParameters {
        override val recordStartTime: Long?
            get() = parameters.recordStartTime
        override val recordTimerJob: Job?
            get() = parameters.recordTimerJob
        override val isTimerRunning: Boolean
            get() = parameters.isTimerRunning
        override val canPauseResume: Boolean
            get() = parameters.canPauseResume
        override val recordChangeSeconds: Int
            get() = parameters.recordChangeSeconds
        override val recordPaused: Boolean
            get() = recording.recordPaused
        override val recordStopped: Boolean
            get() = recording.recordStopped
        override val roomName: String?
            get() = room.roomName
        override val recordElapsedTime: Int
            get() = parameters.recordElapsedTime
        override val updateRecordStartTime: (Long?) -> Unit
            get() = { value ->
                parameters.updateRecordStartTime(value)
                state.propagateParameterChanges()
            }
        override val updateRecordTimerJob: (Job?) -> Unit
            get() = { job ->
                parameters.updateRecordTimerJob(job)
                state.propagateParameterChanges()
            }
        override val updateIsTimerRunning: (Boolean) -> Unit
            get() = { running ->
                parameters.updateIsTimerRunning(running)
                state.propagateParameterChanges()
            }
        override val updateCanPauseResume: (Boolean) -> Unit
            get() = { value ->
                parameters.updateCanPauseResume(value)
                state.propagateParameterChanges()
            }
        override val updateRecordElapsedTime: (Int) -> Unit
            get() = { value ->
                parameters.updateRecordElapsedTime(value)
                state.propagateParameterChanges()
            }
        override val updateRecordingProgressTime: (String) -> Unit
            get() = recording::updateRecordingProgressTime
        override fun getUpdatedAllParams(): RecordStartTimerParameters = this
    }

    private fun createRecordResumeTimerParameters(): RecordResumeTimerParameters =
        object : RecordResumeTimerParameters, PrepopulateUserMediaParameters {
            override val isTimerRunning: Boolean
                get() = parameters.isTimerRunning
            override val canPauseResume: Boolean
                get() = parameters.canPauseResume
            override val recordElapsedTime: Int
                get() = parameters.recordElapsedTime
            override val recordStartTime: Long?
                get() = parameters.recordStartTime
            override val recordTimerJob: Job?
                get() = parameters.recordTimerJob
            override val showAlert: ShowAlert?
                get() = parameters.showAlert
            override val recordPaused: Boolean
                get() = recording.recordPaused
            override val recordStopped: Boolean
                get() = recording.recordStopped
            override val roomName: String?
                get() = room.roomName
            override val recordUpdateTimer: RecordUpdateTimerType
                get() = ::recordUpdateTimer
            override val updateRecordStartTime: (Long) -> Unit
                get() = { value ->
                    parameters.updateRecordStartTime(value)
                    state.propagateParameterChanges()
                }
            override val updateRecordTimerJob: (Job?) -> Unit
                get() = { job ->
                    parameters.updateRecordTimerJob(job)
                    state.propagateParameterChanges()
                }
            override val updateIsTimerRunning: (Boolean) -> Unit
                get() = { running ->
                    parameters.updateIsTimerRunning(running)
                    state.propagateParameterChanges()
                }
            override val updateCanPauseResume: (Boolean) -> Unit
                get() = { value ->
                    parameters.updateCanPauseResume(value)
                    state.propagateParameterChanges()
                }
            override val updateRecordElapsedTime: (Int) -> Unit
                get() = { elapsed ->
                    parameters.updateRecordElapsedTime(elapsed)
                    state.propagateParameterChanges()
                }
            override val updateRecordingProgressTime: (String) -> Unit
                get() = recording::updateRecordingProgressTime
            override val participants: List<Participant>
                get() = parameters.participants
            override val allVideoStreams: List<Stream>
                    get() = parameters.allVideoStreams
            override val islevel: String
                get() = room.islevel
            override val member: String
                get() = parameters.member
            override val shared: Boolean
                get() = parameters.shared
            override val shareScreenStarted: Boolean
                get() = parameters.shareScreenStarted
            override val eventType: EventType
                get() = parameters.eventType
            override val screenId: String?
                get() = parameters.screenId.takeIf { it.isNotBlank() }
            override val forceFullDisplay: Boolean
                get() = parameters.forceFullDisplay
            override val socket: SocketManager?
                get() = parameters.socket
            override val localSocket: SocketManager?
                get() = parameters.localSocket
            override val updateMainWindow: Boolean
                get() = parameters.updateMainWindow
            override val mainScreenFilled: Boolean
                get() = streams.mainScreenFilled
            override val adminOnMainScreen: Boolean
                get() = streams.adminOnMainScreen
            override val mainScreenPerson: String
                get() = streams.mainScreenPerson
            override val videoAlreadyOn: Boolean
                get() = parameters.videoAlreadyOn
            override val audioAlreadyOn: Boolean
                get() = parameters.audioAlreadyOn
            override val oldAllStreams: List<Stream>
                get() = parameters.oldAllStreams
            override val screenForceFullDisplay: Boolean
                get() = parameters.screenForceFullDisplay
            override val localStreamScreen: com.mediasfu.sdk.webrtc.MediaStream?
                get() = parameters.localStreamScreen
            override val remoteScreenStream: List<Stream>
                 get() = parameters.remoteScreenStream
            override val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
                get() = parameters.localStreamVideo
            override val mainHeightWidth: Double
                get() = parameters.mainHeightWidth
            override val isWideScreen: Boolean
                get() = parameters.isWideScreen
            override val localUIMode: Boolean
                get() = parameters.localUIMode
            override val whiteboardStarted: Boolean
                get() = parameters.whiteboardStarted
            override val whiteboardEnded: Boolean
                get() = parameters.whiteboardEnded
            override val virtualStream: Any?
                get() = parameters.virtualStream
            override val keepBackground: Boolean
                get() = parameters.keepBackground
            override val annotateScreenStream: Boolean
                get() = parameters.annotateScreenStream
            override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
                get() = parameters.audioDecibels
            override val updateMainScreenPerson: (String) -> Unit
                get() = streams::updateMainScreenPerson
            override val updateMainScreenFilled: (Boolean) -> Unit
                get() = streams::updateMainScreenFilled
            override val updateAdminOnMainScreen: (Boolean) -> Unit
                get() = streams::updateAdminOnMainScreen
            override val updateMainHeightWidth: (Double) -> Unit
                get() = display::updateMainHeightWidth
            override val updateScreenForceFullDisplay: (Boolean) -> Unit
                get() = display::updateScreenForceFullDisplay
            override val updateUpdateMainWindow: (Boolean) -> Unit
                get() = { value ->
                    if (parameters.updateMainWindow != value) {
                        parameters.updateMainWindow = value
                        state.propagateParameterChanges()
                    }
                }
            override val updateShowAlert: (ShowAlert?) -> Unit
                get() = { handler ->
                    parameters.updateShowAlert(handler)
                    state.propagateParameterChanges()
                }
            override val updateMainGridStream: (List<com.mediasfu.sdk.ui.MediaSfuUIComponent>) -> Unit
                get() = { components ->
                    parameters.updateMainGridStream(components)
                    state.propagateParameterChanges()
                }
            override fun getUpdatedAllParams(): RecordResumeTimerParameters = this
        }
}

/**
 * Adapter for UpdateRecordingParameters - provides all required properties and functions
 * for the updateRecording method to pause/resume recording.
 */
private class UpdateRecordingParametersAdapter(
    state: MediasfuGenericState
) : BaseRePortParameters<UpdateRecordingParameters>(state), UpdateRecordingParameters {
    private val display get() = state.display

    override val userRecordingParams: UserRecordingParams
        get() = parameters.userRecordingParams

    override val updateIsRecordingModalVisible: (Boolean) -> Unit
        get() = { visible -> state.modals.setRecordingVisibility(visible) }

    override val confirmedToRecord: Boolean
        get() = room.confirmedToRecord

    override val recordingMediaOptions: String
        get() = recording.recordingMediaOptions

    override val videoAlreadyOn: Boolean
        get() = parameters.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = parameters.audioAlreadyOn

    override val recordChangeSeconds: Int
        get() = parameters.recordChangeSeconds

    override val pauseRecordCount: Int
        get() = parameters.pauseRecordCount

    override val startReport: Boolean
        get() = parameters.startReport

    override val endReport: Boolean
        get() = parameters.endReport

    override val canRecord: Boolean
        get() = recording.canRecord

    override val canPauseResume: Boolean
        get() = parameters.canPauseResume

    override val recordingVideoPausesLimit: Int
        get() = parameters.recordingVideoPausesLimit

    override val recordingAudioPausesLimit: Int
        get() = parameters.recordingAudioPausesLimit

    override val isTimerRunning: Boolean
        get() = parameters.isTimerRunning

    override val recordElapsedTime: Int
        get() = parameters.recordElapsedTime

    override val recordStartTime: Long?
        get() = parameters.recordStartTime

    override val recordTimerJob: kotlinx.coroutines.Job?
        get() = parameters.recordTimerJob

    override val updateRecordStartTime: (Long) -> Unit
        get() = { value ->
            parameters.updateRecordStartTime(value)
            state.propagateParameterChanges()
        }

    override val updateRecordTimerJob: (kotlinx.coroutines.Job?) -> Unit
        get() = { job ->
            parameters.updateRecordTimerJob(job)
            state.propagateParameterChanges()
        }

    override val updateIsTimerRunning: (Boolean) -> Unit
        get() = { running ->
            parameters.updateIsTimerRunning(running)
            state.propagateParameterChanges()
        }

    override val updateRecordElapsedTime: (Int) -> Unit
        get() = { elapsed ->
            parameters.updateRecordElapsedTime(elapsed)
            state.propagateParameterChanges()
        }

    override val updateRecordingProgressTime: (String) -> Unit
        get() = recording::updateRecordingProgressTime

    override val updateCanPauseResume: (Boolean) -> Unit
        get() = { value ->
            parameters.updateCanPauseResume(value)
            state.propagateParameterChanges()
        }

    override val updatePauseRecordCount: (Int) -> Unit
        get() = { value ->
            parameters.updatePauseRecordCount(value)
            state.propagateParameterChanges()
        }

    override val updateClearedToRecord: (Boolean) -> Unit
        get() = recording::updateClearedToRecord

    override val updateRecordPaused: (Boolean) -> Unit
        get() = recording::updateRecordPaused

    override val updateRecordResumed: (Boolean) -> Unit
        get() = recording::updateRecordResumed

    override val updateStartReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.startReport != value) {
                parameters.updateStartReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateEndReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.endReport != value) {
                parameters.updateEndReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateCanRecord: (Boolean) -> Unit
        get() = recording::updateCanRecord

    override val rePort: suspend (RePortOptions) -> Unit
        get() = { options ->
            val resolved = options.copy(parameters = createRePortParameters())
            parameters.rePort(resolved)
        }

    override val checkPauseState: CheckPauseStateType
        get() = { options: CheckPauseStateOptions ->
            com.mediasfu.sdk.methods.recording_methods.checkPauseState(options)
        }

    override val checkResumeState: CheckResumeStateType
        get() = { options: CheckResumeStateOptions ->
            com.mediasfu.sdk.methods.recording_methods.checkResumeState(options)
        }

    override val recordPauseTimer: RecordPauseTimerType
        get() = { options: RecordPauseTimerOptions ->
            com.mediasfu.sdk.methods.recording_methods.recordPauseTimer(options)
        }

    override val recordResumeTimer: RecordResumeTimerType
        get() = { options: RecordResumeTimerOptions ->
            val resolved = options.copy(parameters = createRecordResumeTimerParameters())
            com.mediasfu.sdk.methods.recording_methods.recordResumeTimer(resolved)
        }

    override val recordUpdateTimer: RecordUpdateTimerType
        get() = { options -> com.mediasfu.sdk.methods.recording_methods.recordUpdateTimer(options) }

    override val showAlert: ShowAlert?
        get() = parameters.showAlert

    override val recordStarted: Boolean
        get() = recording.recordStarted

    override val recordPaused: Boolean
        get() = recording.recordPaused

    override val recordResumed: Boolean
        get() = recording.recordResumed

    override val recordStopped: Boolean
        get() = recording.recordStopped

    override fun getUpdatedAllParams(): UpdateRecordingParameters = this

    private fun createRePortParameters(): RePortParameters = object : RePortParameters,
        TriggerParameters by createTriggerParameters() {
        override val recordingDisplayType: String
            get() = recording.recordingDisplayType
        override val recordingVideoOptimized: Boolean
            get() = recording.recordingVideoOptimized
        override val prevScreenStates: List<ScreenState>
            get() = streams.prevScreenStates
        override val activeNames: List<String>
            get() = streams.activeNames
        override val prevActiveNames: List<String>
            get() = streams.prevActiveNames
        override val updatePrevActiveNames: (List<String>) -> Unit
            get() = { names -> streams.updatePrevActiveNames(names) }
        override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createCompareActiveNamesParameters())
                parameters.compareActiveNames(resolved)
            }
        override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createCompareScreenStatesParameters())
                parameters.compareScreenStates(resolved)
            }
        override val updateScreenStates: (List<ScreenState>) -> Unit
            get() = { states -> streams.updateScreenStates(states) }
        override val updatePrevScreenStates: (List<ScreenState>) -> Unit
            get() = { states -> streams.updatePrevScreenStates(states) }
        override val recordStarted: Boolean
            get() = recording.recordStarted
        override val recordStopped: Boolean
            get() = recording.recordStopped
        override val recordPaused: Boolean
            get() = recording.recordPaused
        override val recordResumed: Boolean
            get() = recording.recordResumed
        override val islevel: String
            get() = room.islevel
        override val mainScreenPerson: String
            get() = streams.mainScreenPerson
        override val adminOnMainScreen: Boolean
            get() = streams.adminOnMainScreen
        override val mainScreenFilled: Boolean
            get() = streams.mainScreenFilled
        override val trigger: suspend (TriggerOptions) -> Unit
            get() = { options ->
                val resolved = options.copy(parameters = createTriggerParameters())
                parameters.trigger(resolved)
            }
        override fun getUpdatedAllParams(): RePortParameters = this
    }

    private fun createRecordResumeTimerParameters(): RecordResumeTimerParameters =
        object : RecordResumeTimerParameters, PrepopulateUserMediaParameters {
            override val isTimerRunning: Boolean
                get() = parameters.isTimerRunning
            override val canPauseResume: Boolean
                get() = parameters.canPauseResume
            override val recordElapsedTime: Int
                get() = parameters.recordElapsedTime
            override val recordStartTime: Long?
                get() = parameters.recordStartTime
            override val recordTimerJob: Job?
                get() = parameters.recordTimerJob
            override val showAlert: ShowAlert?
                get() = parameters.showAlert
            override val recordPaused: Boolean
                get() = recording.recordPaused
            override val recordStopped: Boolean
                get() = recording.recordStopped
            override val roomName: String?
                get() = room.roomName
            override val recordUpdateTimer: RecordUpdateTimerType
                get() = ::recordUpdateTimer
            override val updateRecordStartTime: (Long) -> Unit
                get() = { value ->
                    parameters.updateRecordStartTime(value)
                    state.propagateParameterChanges()
                }
            override val updateRecordTimerJob: (Job?) -> Unit
                get() = { job ->
                    parameters.updateRecordTimerJob(job)
                    state.propagateParameterChanges()
                }
            override val updateIsTimerRunning: (Boolean) -> Unit
                get() = { running ->
                    parameters.updateIsTimerRunning(running)
                    state.propagateParameterChanges()
                }
            override val updateCanPauseResume: (Boolean) -> Unit
                get() = { value ->
                    parameters.updateCanPauseResume(value)
                    state.propagateParameterChanges()
                }
            override val updateRecordElapsedTime: (Int) -> Unit
                get() = { elapsed ->
                    parameters.updateRecordElapsedTime(elapsed)
                    state.propagateParameterChanges()
                }
            override val updateRecordingProgressTime: (String) -> Unit
                get() = recording::updateRecordingProgressTime
            override val participants: List<Participant>
                get() = parameters.participants
            override val allVideoStreams: List<Stream>
                get() = parameters.allVideoStreams
            override val islevel: String
                get() = room.islevel
            override val member: String
                get() = parameters.member
            override val shared: Boolean
                get() = parameters.shared
            override val shareScreenStarted: Boolean
                get() = parameters.shareScreenStarted
            override val eventType: EventType
                get() = parameters.eventType
            override val screenId: String?
                get() = parameters.screenId.takeIf { it.isNotBlank() }
            override val forceFullDisplay: Boolean
                get() = parameters.forceFullDisplay
            override val socket: SocketManager?
                get() = parameters.socket
            override val localSocket: SocketManager?
                get() = parameters.localSocket
            override val updateMainWindow: Boolean
                get() = parameters.updateMainWindow
            override val mainScreenFilled: Boolean
                get() = streams.mainScreenFilled
            override val adminOnMainScreen: Boolean
                get() = streams.adminOnMainScreen
            override val mainScreenPerson: String
                get() = streams.mainScreenPerson
            override val videoAlreadyOn: Boolean
                get() = parameters.videoAlreadyOn
            override val audioAlreadyOn: Boolean
                get() = parameters.audioAlreadyOn
            override val oldAllStreams: List<Stream>
                get() = parameters.oldAllStreams
            override val screenForceFullDisplay: Boolean
                get() = parameters.screenForceFullDisplay
            override val localStreamScreen: com.mediasfu.sdk.webrtc.MediaStream?
                get() = parameters.localStreamScreen
            override val remoteScreenStream: List<Stream>
                get() = parameters.remoteScreenStream
            override val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
                get() = parameters.localStreamVideo
            override val mainHeightWidth: Double
                get() = parameters.mainHeightWidth
            override val isWideScreen: Boolean
                get() = parameters.isWideScreen
            override val localUIMode: Boolean
                get() = parameters.localUIMode
            override val whiteboardStarted: Boolean
                get() = parameters.whiteboardStarted
            override val whiteboardEnded: Boolean
                get() = parameters.whiteboardEnded
            override val virtualStream: Any?
                get() = parameters.virtualStream
            override val keepBackground: Boolean
                get() = parameters.keepBackground
            override val annotateScreenStream: Boolean
                get() = parameters.annotateScreenStream
            override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
                get() = parameters.audioDecibels
            override val updateMainScreenPerson: (String) -> Unit
                get() = streams::updateMainScreenPerson
            override val updateMainScreenFilled: (Boolean) -> Unit
                get() = streams::updateMainScreenFilled
            override val updateAdminOnMainScreen: (Boolean) -> Unit
                get() = streams::updateAdminOnMainScreen
            override val updateMainHeightWidth: (Double) -> Unit
                get() = display::updateMainHeightWidth
            override val updateScreenForceFullDisplay: (Boolean) -> Unit
                get() = display::updateScreenForceFullDisplay
            override val updateUpdateMainWindow: (Boolean) -> Unit
                get() = { value ->
                    if (parameters.updateMainWindow != value) {
                        parameters.updateMainWindow = value
                        state.propagateParameterChanges()
                    }
                }
            override val updateShowAlert: (ShowAlert?) -> Unit
                get() = { handler ->
                    parameters.updateShowAlert(handler)
                    state.propagateParameterChanges()
                }
            override val updateMainGridStream: (List<com.mediasfu.sdk.ui.MediaSfuUIComponent>) -> Unit
                get() = { components ->
                    parameters.updateMainGridStream(components)
                    state.propagateParameterChanges()
                }
            override fun getUpdatedAllParams(): RecordResumeTimerParameters = this
        }
}

/**
 * Adapter for StopRecordingParameters - provides all required properties and functions
 * for the stopRecording method.
 */
private class StopRecordingParametersAdapter(
    private val state: MediasfuGenericState
) : StopRecordingParameters {
    private val parameters: MediasfuParameters
        get() = state.parameters
    private val recording: RecordingState
        get() = state.recording
    private val room: RoomState
        get() = state.room

    override val roomName: String
        get() = room.roomName

    override val socket: SocketManager?
        get() = parameters.socket

    override val localSocket: SocketManager?
        get() = parameters.localSocket

    override val showAlert: ShowAlert?
        get() = parameters.showAlert

    override val startReport: Boolean
        get() = parameters.startReport

    override val endReport: Boolean
        get() = parameters.endReport

    override val recordStarted: Boolean
        get() = recording.recordStarted

    override val recordPaused: Boolean
        get() = recording.recordPaused

    override val recordStopped: Boolean
        get() = recording.recordStopped

    override val isTimerRunning: Boolean
        get() = parameters.isTimerRunning

    override val canPauseResume: Boolean
        get() = parameters.canPauseResume

    override val recordTimerJob: Job?
        get() = parameters.recordTimerJob

    override val updateRecordPaused: (Boolean) -> Unit
        get() = recording::updateRecordPaused

    override val updateRecordStopped: (Boolean) -> Unit
        get() = recording::updateRecordStopped

    override val updateStartReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.startReport != value) {
                parameters.updateStartReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateEndReport: (Boolean) -> Unit
        get() = { value ->
            if (parameters.endReport != value) {
                parameters.updateEndReport(value)
                state.propagateParameterChanges()
            }
        }

    override val updateShowRecordButtons: (Boolean) -> Unit
        get() = recording::updateShowRecordButtons

    override val updateRecordStarted: (Boolean) -> Unit
        get() = recording::updateRecordStarted

    override val updateRecordResumed: (Boolean) -> Unit
        get() = recording::updateRecordResumed

    override val updateCanRecord: (Boolean) -> Unit
        get() = recording::updateCanRecord

    override val updateIsTimerRunning: (Boolean) -> Unit
        get() = { value ->
            parameters.updateIsTimerRunning(value)
            state.propagateParameterChanges()
        }

    override val updateCanPauseResume: (Boolean) -> Unit
        get() = { value ->
            parameters.updateCanPauseResume(value)
            state.propagateParameterChanges()
        }

    override val updateRecordElapsedTime: (Int) -> Unit
        get() = { value ->
            parameters.updateRecordElapsedTime(value)
            state.propagateParameterChanges()
        }

    override val updateRecordingProgressTime: (String) -> Unit
        get() = recording::updateRecordingProgressTime

    override val updateRecordStartTime: (Long?) -> Unit
        get() = { value ->
            parameters.updateRecordStartTime(value)
            state.propagateParameterChanges()
        }

    override val updateRecordTimerJob: (Job?) -> Unit
        get() = { job ->
            parameters.updateRecordTimerJob(job)
            state.propagateParameterChanges()
        }

    override val updateRecordState: (String) -> Unit
        get() = { value ->
            parameters.updateRecordState(value)
            state.propagateParameterChanges()
        }

    override val updateClearedToRecord: ((Boolean) -> Unit)?
        get() = recording::updateClearedToRecord

    override val updateClearedToResume: ((Boolean) -> Unit)?
        get() = { value ->
            parameters.updateClearedToResume(value)
            state.propagateParameterChanges()
        }

    override val updatePauseRecordCount: ((Int) -> Unit)?
        get() = { value ->
            parameters.updatePauseRecordCount(value)
            state.propagateParameterChanges()
        }

    override val updateIsRecordingModalVisible: (Boolean) -> Unit
        get() = { visible -> state.modals.setRecordingVisibility(visible) }

    override val whiteboardStarted: Boolean
        get() = parameters.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = parameters.whiteboardEnded

    override val recordingMediaOptions: String
        get() = recording.recordingMediaOptions
    
    override fun asStopCanvasStreamParameters(): StopCanvasStreamParameters? {
        return object : StopCanvasStreamParameters {
            override val canvasStream: MediaStream?
                get() = parameters.canvasStream as? MediaStream
            
            override val updateCanvasStream: (MediaStream?) -> Unit
                get() = { stream -> parameters.updateCanvasStream(stream) }
            
            override val screenProducer: WebRtcProducer?
                get() = parameters.screenProducer
            
            override val socket: com.mediasfu.sdk.socket.SocketManager?
                get() = parameters.socket
            
            override val localScreenProducer: WebRtcProducer?
                get() = parameters.localScreenProducer
            
            override val localSocket: com.mediasfu.sdk.socket.SocketManager?
                get() = parameters.localSocket
            
            override fun updateScreenProducer(producer: WebRtcProducer?) {
                parameters.screenProducer = producer
            }
            
            override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
                parameters.localScreenProducer = producer
            }
        }
    }
}