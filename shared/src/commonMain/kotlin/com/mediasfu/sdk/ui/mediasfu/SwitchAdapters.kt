package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.EngineReorderStreamsParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportVideoOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters
import com.mediasfu.sdk.consumers.RequestPermissionAudioType
import com.mediasfu.sdk.consumers.StreamSuccessAudioSwitchOptions
import com.mediasfu.sdk.consumers.StreamSuccessAudioSwitchParameters
import com.mediasfu.sdk.consumers.StreamSuccessVideoOptions
import com.mediasfu.sdk.consumers.StreamSuccessVideoParameters
import com.mediasfu.sdk.consumers.SwitchUserAudioOptions
import com.mediasfu.sdk.consumers.SwitchUserAudioType
import com.mediasfu.sdk.consumers.SwitchUserVideoAltOptions
import com.mediasfu.sdk.consumers.SwitchUserVideoAltType
import com.mediasfu.sdk.consumers.SwitchUserVideoOptions
import com.mediasfu.sdk.consumers.SwitchUserVideoType
import com.mediasfu.sdk.consumers.streamSuccessAudioSwitch
import com.mediasfu.sdk.consumers.streamSuccessVideo
import com.mediasfu.sdk.consumers.switchUserAudio
import com.mediasfu.sdk.consumers.switchUserVideo
import com.mediasfu.sdk.consumers.switchUserVideoAlt
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.stream_methods.ClickVideoOptions
import com.mediasfu.sdk.methods.stream_methods.SwitchAudioParameters
import com.mediasfu.sdk.methods.stream_methods.SwitchVideoAltParameters
import com.mediasfu.sdk.methods.stream_methods.SwitchVideoParameters
import com.mediasfu.sdk.methods.stream_methods.clickVideo
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils

internal fun MediasfuGenericState.createSwitchVideoParameters(): SwitchVideoParameters =
    SwitchVideoParametersAdapter(parameters, this)

internal fun MediasfuGenericState.createSwitchVideoAltParameters(): SwitchVideoAltParameters =
    SwitchVideoAltParametersAdapter(parameters, this)

internal fun MediasfuGenericState.createSwitchAudioParameters(): SwitchAudioParameters =
    SwitchAudioParametersAdapter(parameters, this)

internal open class BasePrepopulateParameters(
    protected val backing: MediasfuParameters,
    protected val state: MediasfuGenericState
) : PrepopulateUserMediaParameters {

    override val participants: List<Participant>
        get() = backing.participants

    override val allVideoStreams: List<Stream>
        get() = backing.allVideoStreams

    override val islevel: String
        get() = backing.islevel

    override val member: String
        get() = backing.member

    override val shared: Boolean
        get() = backing.shared

    override val shareScreenStarted: Boolean
        get() = backing.shareScreenStarted

    override val eventType: EventType
        get() = backing.eventType

    override val screenId: String?
        get() = backing.screenId.takeIf { it.isNotBlank() }

    override val forceFullDisplay: Boolean
        get() = backing.forceFullDisplay

    override val socket: SocketManager?
        get() = backing.socket

    override val localSocket: SocketManager?
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
        get() = { value -> state.streams.updateMainScreenPerson(value) }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { value -> state.streams.updateMainScreenFilled(value) }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { value -> state.streams.updateAdminOnMainScreen(value) }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value -> state.display.updateMainHeightWidth(value) }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { value -> state.display.updateScreenForceFullDisplay(value) }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { value ->
            if (backing.updateMainWindow != value) {
                backing.updateMainWindow = value
                state.propagateParameterChanges()
            }
        }

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = { handler ->
            backing.updateShowAlert(handler)
            state.propagateParameterChanges()
        }

    override val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit
        get() = { components ->
            backing.updateMainGridStream(components)
            state.propagateParameterChanges()
        }

    override fun getUpdatedAllParams(): PrepopulateUserMediaParameters = this
}

private open class BaseStreamSuccessVideoParameters(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BasePrepopulateParameters(backing, state), StreamSuccessVideoParameters {

    override val device: WebRtcDevice?
        get() = backing.device

    override val rtpCapabilities
        get() = backing.rtpCapabilities

    override val routerRtpCapabilities
        get() = backing.routerRtpCapabilities

    override val extendedRtpCapabilities
        get() = backing.extendedRtpCapabilities

    override val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?
        get() = { caps ->
            backing.extendedRtpCapabilities = caps
            state.propagateParameterChanges()
        }

    override val localStream: MediaStream?
        get() = backing.localStream

    override var transportCreated: Boolean
        get() = backing.transportCreated
        set(value) {
            if (backing.transportCreated != value) {
                backing.transportCreated = value
                state.propagateParameterChanges()
            }
        }

    override var localTransportCreated: Boolean
        get() = backing.localTransportCreated
        set(value) {
            if (backing.localTransportCreated != value) {
                backing.localTransportCreated = value
                state.propagateParameterChanges()
            }
        }

    override val transportCreatedVideo: Boolean
        get() = backing.transportCreatedVideo

    override val videoAction: Boolean
        get() = backing.videoAction

    override val videoParams: ProducerOptionsType?
        get() = backing.videoParams

    override val localStreamVideo: MediaStream?
        get() = backing.localStreamVideo

    override val defVideoID: String
        get() = backing.defVideoID

    override val userDefaultVideoInputDevice: String
        get() = backing.userDefaultVideoInputDevice

    override val params: ProducerOptionsType?
        get() = backing.params

    override val vParams: ProducerOptionsType?
        get() = backing.vParams

    override val hostLabel: String
        get() = backing.hostLabel

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override val shared: Boolean
        get() = backing.shared

    override val audioAlreadyOn: Boolean
        get() = backing.audioAlreadyOn

    override val showAlert: ShowAlert?
        get() = ShowAlert { message, type, duration ->
            state.showAlert(message, type, duration)
        }

    override var producerTransport: WebRtcTransport?
        get() = backing.producerTransport
        set(value) {
            if (backing.producerTransport !== value) {
                backing.producerTransport = value
                state.propagateParameterChanges()
            }
        }

    override var localProducerTransport: WebRtcTransport?
        get() = backing.localProducerTransport
        set(value) {
            if (backing.localProducerTransport !== value) {
                backing.localProducerTransport = value
                state.propagateParameterChanges()
            }
        }

    override var videoProducer: WebRtcProducer?
        get() = backing.videoProducer
        set(value) {
            if (backing.videoProducer !== value) {
                backing.videoProducer = value
                state.propagateParameterChanges()
            }
        }

    override var localVideoProducer: WebRtcProducer?
        get() = backing.localVideoProducer
        set(value) {
            if (backing.localVideoProducer !== value) {
                backing.localVideoProducer = value
                state.propagateParameterChanges()
            }
        }

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { updated -> state.room.updateParticipants(updated) }

    override val updateTransportCreated: (Boolean) -> Unit
        get() = { created -> transportCreated = created }

    override val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { created -> localTransportCreated = created }

    override val updateTransportCreatedVideo: (Boolean) -> Unit
        get() = { created ->
            if (backing.transportCreatedVideo != created) {
                backing.transportCreatedVideo = created
                state.propagateParameterChanges()
            }
        }

    override val updateVideoAlreadyOn: (Boolean) -> Unit
        get() = { value ->
            if (backing.videoAlreadyOn != value) {
                backing.videoAlreadyOn = value
                state.media.videoAlreadyOn = value
                state.propagateParameterChanges()
            }
        }

    override val updateVideoAction: (Boolean) -> Unit
        get() = { value ->
            if (backing.videoAction != value) {
                backing.videoAction = value
                state.propagateParameterChanges()
            }
        }

    override val updateVideoParams: (ProducerOptionsType?) -> Unit
        get() = { value ->
            if (backing.videoParams != value) {
                backing.videoParams = value
                state.propagateParameterChanges()
            }
        }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            if (backing.localStream !== stream) {
                backing.localStream = stream
                state.propagateParameterChanges()
            }
        }

    override val updateLocalStreamVideo: (MediaStream?) -> Unit
        get() = { stream ->
            if (backing.localStreamVideo !== stream) {
                backing.localStreamVideo = stream
                state.propagateParameterChanges()
            }
        }

    override val updateDefVideoID: (String) -> Unit
        get() = { value ->
            if (backing.defVideoID != value) {
                backing.defVideoID = value
                state.propagateParameterChanges()
            }
        }

    override val updateUserDefaultVideoInputDevice: (String) -> Unit
        get() = { value ->
            if (backing.userDefaultVideoInputDevice != value) {
                backing.userDefaultVideoInputDevice = value
                state.propagateParameterChanges()
            }
        }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { transport -> producerTransport = transport }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { transport -> localProducerTransport = transport }

    override val updateVideoProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            backing.updateVideoProducer(producer)
            state.propagateParameterChanges()
        }

    override val updateLocalVideoProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            backing.updateLocalVideoProducer(producer)
            state.propagateParameterChanges()
        }

    // Camera permission allowed flag - set to true when camera is successfully started
    override val allowed: Boolean
        get() = backing.allowed

    override val updateAllowed: (Boolean) -> Unit
        get() = { value ->
            if (backing.allowed != value) {
                backing.allowed = value
                state.propagateParameterChanges()
            }
        }

    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.createSendTransport(options).getOrThrow()
        }

    override val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportVideo(options).getOrThrow()
        }

    override val resumeSendTransportVideo: suspend (com.mediasfu.sdk.consumers.ResumeSendTransportVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.resumeSendTransportVideo(options)
        }

    override val prepopulateUserMedia: suspend (com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions) -> Unit
        get() = backing.prepopulateUserMedia

    override val reorderStreams: suspend (add: Boolean, screenChanged: Boolean) -> Unit
        get() = { add, screenChanged ->
            val options = com.mediasfu.sdk.consumers.ReorderStreamsOptions(
                add = add,
                screenChanged = screenChanged,
                parameters = EngineReorderStreamsParameters(backing)
            )
            com.mediasfu.sdk.consumers.reorderStreams(options).getOrThrow()
        }

    override fun getUpdatedAllParams(): StreamSuccessVideoParameters = this
}

private class SwitchVideoParametersAdapter(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BaseStreamSuccessVideoParameters(backing, state), SwitchVideoParameters {

    override val recordStarted: Boolean get() = backing.recordStarted
    override val recordResumed: Boolean get() = backing.recordResumed
    override val recordStopped: Boolean get() = backing.recordStopped
    override val recordPaused: Boolean get() = backing.recordPaused
    override val recordingMediaOptions: String get() = backing.recordingMediaOptions
    override val videoAlreadyOn: Boolean get() = backing.videoAlreadyOn

    override val audioOnlyRoom: Boolean get() = backing.audioOnlyRoom
    override val frameRate: Int get() = backing.frameRate

    override val vidCons: VidCons get() = backing.vidCons

    override val prevVideoInputDevice: String get() = backing.prevVideoInputDevice
    override val userDefaultVideoInputDevice: String get() = backing.userDefaultVideoInputDevice
    override val currentFacingMode: String get() = backing.currentFacingMode
    override val device: WebRtcDevice? get() = backing.device
    override val showAlert: ShowAlert?
        get() = ShowAlert { message, type, duration ->
            state.showAlert(message, type, duration)
        }
    override val hasCameraPermission: Boolean get() = backing.hasCameraPermission
    override val checkMediaPermission: Boolean get() = backing.checkMediaPermission

    override val updateVideoSwitching: (Boolean) -> Unit
        get() = { value ->
            backing.videoSwitching = value
            state.propagateParameterChanges()
        }

    override val updateUserDefaultVideoInputDevice: (String) -> Unit
        get() = super.updateUserDefaultVideoInputDevice

    override val streamSuccessVideo: suspend (StreamSuccessVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.streamSuccessVideo(options)
        }

    override val requestPermissionCamera: suspend () -> Unit
        get() = suspend {
            backing.requestPermissionCamera?.invoke()
        }

    override val defVideoID: String get() = backing.defVideoID
    override val allowed: Boolean get() = backing.allowed

    override val updateDefVideoID: (String) -> Unit
        get() = super.updateDefVideoID

    override val updatePrevVideoInputDevice: (String) -> Unit
        get() = { value ->
            backing.prevVideoInputDevice = value
            state.propagateParameterChanges()
        }

    override val updateIsMediaSettingsModalVisible: (Boolean) -> Unit
        get() = { visible -> state.modals.updateIsMediaSettingsVisible(visible) }

    override val switchUserVideo: SwitchUserVideoType
        get() = { options: SwitchUserVideoOptions ->
            com.mediasfu.sdk.consumers.switchUserVideo(options)
        }

    override fun getUpdatedAllParams(): SwitchVideoParameters = this
}

private class SwitchVideoAltParametersAdapter(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BaseStreamSuccessVideoParameters(backing, state), SwitchVideoAltParameters {

    override val recordStarted: Boolean get() = backing.recordStarted
    override val recordResumed: Boolean get() = backing.recordResumed
    override val recordStopped: Boolean get() = backing.recordStopped
    override val recordPaused: Boolean get() = backing.recordPaused
    override val recordingMediaOptions: String get() = backing.recordingMediaOptions
    override val videoAlreadyOn: Boolean get() = backing.videoAlreadyOn

    override val currentFacingMode: String get() = backing.currentFacingMode
    override val prevFacingMode: String get() = backing.prevFacingMode
    override val allowed: Boolean get() = backing.allowed
    override val audioOnlyRoom: Boolean get() = backing.audioOnlyRoom
    override val frameRate: Int get() = backing.frameRate
    override val vidCons get() = backing.vidCons
    override val device: WebRtcDevice? get() = backing.device
    override val showAlert: ShowAlert?
        get() = ShowAlert { message, type, duration ->
            state.showAlert(message, type, duration)
        }
    override val hasCameraPermission: Boolean get() = backing.hasCameraPermission
    override val checkMediaPermission: Boolean get() = backing.checkMediaPermission

    override val updateVideoSwitching: (Boolean) -> Unit
        get() = { value ->
            backing.videoSwitching = value
            state.propagateParameterChanges()
        }

    override val updateCurrentFacingMode: (String) -> Unit
        get() = { value ->
            backing.currentFacingMode = value
            state.propagateParameterChanges()
        }

    override val updatePrevFacingMode: (String) -> Unit
        get() = { value ->
            backing.prevFacingMode = value
            state.propagateParameterChanges()
        }

    override val updateIsMediaSettingsModalVisible: (Boolean) -> Unit
        get() = { visible -> state.modals.updateIsMediaSettingsVisible(visible) }

    override val requestPermissionCamera: suspend () -> Boolean
        get() = suspend { backing.requestPermissionCamera?.invoke() ?: false }

    override val streamSuccessVideo: suspend (StreamSuccessVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.streamSuccessVideo(options)
        }

    override val clickVideo: suspend () -> Unit
        get() = suspend {
            val options = ClickVideoOptions(parameters = state.createClickVideoParameters())
            com.mediasfu.sdk.methods.stream_methods.clickVideo(options)
        }

    override val switchUserVideoAlt: SwitchUserVideoAltType
        get() = { options: SwitchUserVideoAltOptions ->
            com.mediasfu.sdk.consumers.switchUserVideoAlt(options)
        }

    override fun getUpdatedAllParams(): SwitchVideoAltParameters = this
}

private open class BaseStreamSuccessAudioSwitchParameters(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BasePrepopulateParameters(backing, state), StreamSuccessAudioSwitchParameters {

    override val device: WebRtcDevice?
        get() = backing.device

    override val rtpCapabilities
        get() = backing.rtpCapabilities

    override val routerRtpCapabilities
        get() = backing.routerRtpCapabilities

    override val extendedRtpCapabilities
        get() = backing.extendedRtpCapabilities

    override val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?
        get() = { caps ->
            backing.extendedRtpCapabilities = caps
            state.propagateParameterChanges()
        }

    override val roomName: String
        get() = backing.roomName

    override val audioPaused: Boolean
        get() = backing.audioPaused

    override val micAction: Boolean
        get() = backing.micAction

    override val lockScreen: Boolean
        get() = backing.lockScreen

    override var transportCreated: Boolean
        get() = backing.transportCreated
        set(value) {
            if (backing.transportCreated != value) {
                backing.transportCreated = value
                state.propagateParameterChanges()
            }
        }

    override var localTransportCreated: Boolean
        get() = backing.localTransportCreated
        set(value) {
            if (backing.localTransportCreated != value) {
                backing.localTransportCreated = value
                state.propagateParameterChanges()
            }
        }

    override val transportCreatedAudio: Boolean
        get() = backing.transportCreatedAudio

    override val audioParams: ProducerOptionsType?
        get() = backing.audioParams

    override val localStream: MediaStream?
        get() = backing.localStream

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

    override val hostLabel: String
        get() = backing.hostLabel

    override val showAlert: ShowAlert?
        get() = backing.showAlert

    override var producerTransport: WebRtcTransport?
        get() = backing.producerTransport
        set(value) {
            if (backing.producerTransport !== value) {
                backing.producerTransport = value
                state.propagateParameterChanges()
            }
        }

    override var localProducerTransport: WebRtcTransport?
        get() = backing.localProducerTransport
        set(value) {
            if (backing.localProducerTransport !== value) {
                backing.localProducerTransport = value
                state.propagateParameterChanges()
            }
        }

    override var audioProducer: WebRtcProducer?
        get() = backing.audioProducer
        set(value) {
            if (backing.audioProducer !== value) {
                backing.audioProducer = value
                state.propagateParameterChanges()
            }
        }

    override var localAudioProducer: WebRtcProducer?
        get() = backing.localAudioProducer
        set(value) {
            if (backing.localAudioProducer !== value) {
                backing.localAudioProducer = value
                state.propagateParameterChanges()
            }
        }

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { updated -> state.room.updateParticipants(updated) }

    override val updateTransportCreated: (Boolean) -> Unit
        get() = { created -> transportCreated = created }

    override val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { created -> localTransportCreated = created }

    override val updateTransportCreatedAudio: (Boolean) -> Unit
        get() = { created ->
            if (backing.transportCreatedAudio != created) {
                backing.transportCreatedAudio = created
                state.propagateParameterChanges()
            }
        }

    override val updateAudioAlreadyOn: (Boolean) -> Unit
        get() = { value ->
            if (backing.audioAlreadyOn != value) {
                backing.audioAlreadyOn = value
                state.media.audioAlreadyOn = value
                state.propagateParameterChanges()
            }
        }

    override val updateMicAction: (Boolean) -> Unit
        get() = { value ->
            if (backing.micAction != value) {
                backing.micAction = value
                state.requests.updateMicAction(value)
                state.propagateParameterChanges()
            }
        }

    override val updateAudioParams: (ProducerOptionsType?) -> Unit
        get() = { value ->
            if (backing.audioParams != value) {
                backing.audioParams = value
                state.propagateParameterChanges()
            }
        }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            if (backing.localStream !== stream) {
                backing.localStream = stream
                state.propagateParameterChanges()
            }
        }

    override val updateLocalStreamAudio: (MediaStream?) -> Unit
        get() = { stream ->
            if (backing.localStreamAudio !== stream) {
                backing.localStreamAudio = stream
                state.propagateParameterChanges()
            }
        }

    override val updateLocalStreamSwitch: (MediaStream?) -> Unit
        get() = updateLocalStreamAudio

    override val updateAudioParamsSwitch: (ProducerOptionsType?) -> Unit
        get() = updateAudioParams

    override val updateDefAudioID: (String) -> Unit
        get() = { value ->
            if (backing.defAudioID != value) {
                backing.defAudioID = value
                state.propagateParameterChanges()
            }
        }

    override val updateDefAudioIDSwitch: (String) -> Unit
        get() = updateDefAudioID

    override val updateUserDefaultAudioInputDevice: (String) -> Unit
        get() = { value ->
            if (backing.userDefaultAudioInputDevice != value) {
                backing.userDefaultAudioInputDevice = value
                state.propagateParameterChanges()
            }
        }

    override val updateUserDefaultAudioInputDeviceSwitch: (String) -> Unit
        get() = updateUserDefaultAudioInputDevice

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { transport -> producerTransport = transport }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { transport -> localProducerTransport = transport }

    override val updateAudioProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            backing.updateAudioProducer(producer)
            state.propagateParameterChanges()
        }

    override val updateAudioProducerSwitch: (WebRtcProducer?) -> Unit
        get() = updateAudioProducer

    override val updateLocalAudioProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            backing.updateLocalAudioProducer(producer)
            state.propagateParameterChanges()
        }

    override val updateLocalAudioProducerSwitch: ((WebRtcProducer?) -> Unit)?
        get() = updateLocalAudioProducer

    override val updateAudioLevel: (Double) -> Unit
        get() = { value ->
            if (backing.audioLevel != value) {
                backing.audioLevel = value
                state.propagateParameterChanges()
            }
        }

    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportAudio(options).getOrThrow()
        }

    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.createSendTransport(options).getOrThrow()
        }

    override val prepopulateUserMedia: suspend (com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions) -> Unit
        get() = backing.prepopulateUserMedia

    override fun getUpdatedAllParams(): StreamSuccessAudioSwitchParameters = this
}

private class SwitchAudioParametersAdapter(
    backing: MediasfuParameters,
    state: MediasfuGenericState
) : BaseStreamSuccessAudioSwitchParameters(backing, state), SwitchAudioParameters {

    override val device: WebRtcDevice?
        get() = backing.device

    override val defAudioID: String get() = backing.defAudioID
    override val userDefaultAudioInputDevice: String get() = backing.userDefaultAudioInputDevice
    override val prevAudioInputDevice: String get() = backing.prevAudioInputDevice
    override val showAlert: ShowAlert?
        get() = ShowAlert { message, type, duration ->
            state.showAlert(message, type, duration)
        }
    override val hasAudioPermission: Boolean get() = backing.hasAudioPermission
    override val checkMediaPermission: Boolean get() = backing.checkMediaPermission

    override val updateUserDefaultAudioInputDevice: (String) -> Unit
        get() = { value ->
            backing.userDefaultAudioInputDevice = value
            state.propagateParameterChanges()
        }

    override val updatePrevAudioInputDevice: (String) -> Unit
        get() = { value ->
            backing.prevAudioInputDevice = value
            state.propagateParameterChanges()
        }

    override val streamSuccessAudioSwitch: suspend (StreamSuccessAudioSwitchOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.streamSuccessAudioSwitch(options)
        }

    override val requestPermissionAudio: RequestPermissionAudioType
        get() = backing.requestPermissionAudio ?: suspend { null }

    override val switchUserAudio: SwitchUserAudioType
        get() = { options: SwitchUserAudioOptions ->
            com.mediasfu.sdk.consumers.switchUserAudio(options)
        }

    override fun getUpdatedAllParams(): SwitchAudioParameters = this
}
