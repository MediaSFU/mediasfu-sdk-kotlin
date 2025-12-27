package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.ConnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.RequestPermissionAudioType
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioOptions
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioType
import com.mediasfu.sdk.consumers.StreamSuccessAudioOptions
import com.mediasfu.sdk.consumers.StreamSuccessAudioType
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.stream_methods.ClickAudioParameters
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

internal fun MediasfuGenericState.createClickAudioParameters(): ClickAudioParameters =
    MediasfuClickAudioParameters(parameters, this)

private class MediasfuClickAudioParameters(
    private val backing: MediasfuParameters,
    private val state: MediasfuGenericState
) : ClickAudioParameters {

    // ---------------------------------------------------------------------
    // Basic media / permission state
    // ---------------------------------------------------------------------
    override val checkMediaPermission: Boolean get() = backing.checkMediaPermission
    override val hasAudioPermission: Boolean get() = backing.hasAudioPermission
    override val audioPaused: Boolean get() = backing.audioPaused
    override val audioAlreadyOn: Boolean get() = backing.audioAlreadyOn
    override val audioOnlyRoom: Boolean get() = backing.audioOnlyRoom
    override val recordStarted: Boolean get() = backing.recordStarted
    override val recordResumed: Boolean get() = backing.recordResumed
    override val recordPaused: Boolean get() = backing.recordPaused
    override val recordStopped: Boolean get() = backing.recordStopped
    override val recordingMediaOptions: String get() = backing.recordingMediaOptions

    // ---------------------------------------------------------------------
    // Identity / connection references
    // ---------------------------------------------------------------------
    override val islevel: String get() = backing.islevel
    override val youAreCoHost: Boolean get() = backing.youAreCoHost
    override val adminRestrictSetting: Boolean get() = backing.adminRestrictSetting
    override val audioRequestState: String? get() = backing.audioRequestState
    override val audioRequestTime: Long? get() = backing.audioRequestTime
    override val member: String get() = backing.member
    override val socket: SocketManager? get() = backing.socket
    override val localSocket: SocketManager? get() = backing.localSocket
    override val roomName: String get() = backing.roomName
    override val userDefaultAudioInputDevice: String get() = backing.userDefaultAudioInputDevice
    override val micAction: Boolean get() = backing.micAction
    override val localStream: MediaStream? get() = backing.localStream
    override val participants: List<Participant> get() = backing.participants

    // ---------------------------------------------------------------------
    // Meeting level configuration
    // ---------------------------------------------------------------------
    override val audioSetting: String get() = backing.audioSetting
    override val videoSetting: String get() = backing.videoSetting
    override val screenshareSetting: String get() = backing.screenshareSetting
    override val chatSetting: String get() = backing.chatSetting
    override val updateRequestIntervalSeconds: Int get() = backing.updateRequestIntervalSeconds

    // ---------------------------------------------------------------------
    // Transport references (CreateSendTransportParameters)
    // ---------------------------------------------------------------------
    override val device: WebRtcDevice? get() = backing.device
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
            com.mediasfu.sdk.consumers.createSendTransport(options).getOrThrow()
        }

    // ---------------------------------------------------------------------
    // StreamSuccess / ConnectSendTransport requirements
    // ---------------------------------------------------------------------
    override val transportCreatedAudio: Boolean get() = backing.transportCreatedAudio
    override val audioParams: ProducerOptionsType? get() = backing.audioParams
    override val localStreamAudio: MediaStream? get() = backing.localStreamAudio
    override val defAudioID: String get() = backing.defAudioID
    override val params: ProducerOptionsType? get() = backing.params
    override val aParams: ProducerOptionsType? get() = backing.aParams
    override val hostLabel: String get() = backing.hostLabel
    override val updateMainWindow: Boolean get() = backing.updateMainWindow
    override val lockScreen: Boolean get() = backing.lockScreen
    override val shared: Boolean get() = backing.shared
    override val videoAlreadyOn: Boolean get() = backing.videoAlreadyOn

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { updated -> state.room.updateParticipants(updated) }

    override val updateTransportCreatedAudio: (Boolean) -> Unit
        get() = { created ->
            backing.transportCreatedAudio = created
            state.propagateParameterChanges()
        }

    override val updateAudioAlreadyOn: (Boolean) -> Unit
        get() = { value ->
            backing.audioAlreadyOn = value
            state.media.audioAlreadyOn = value
            state.propagateParameterChanges()
        }

    override val updateMicAction: (Boolean) -> Unit
        get() = { value ->
            backing.micAction = value
            state.propagateParameterChanges()
        }

    override val updateAudioParams: (ProducerOptionsType?) -> Unit
        get() = { value ->
            backing.audioParams = value
            state.propagateParameterChanges()
        }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStream = stream
            state.propagateParameterChanges()
        }

    override val updateLocalStreamAudio: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStreamAudio = stream
            state.propagateParameterChanges()
        }

    override val updateDefAudioID: (String) -> Unit
        get() = { value ->
            backing.defAudioID = value
            state.propagateParameterChanges()
        }

    override val updateUserDefaultAudioInputDevice: (String) -> Unit
        get() = { value ->
            backing.userDefaultAudioInputDevice = value
            state.propagateParameterChanges()
        }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { update ->
            backing.updateMainWindow = update
            state.propagateParameterChanges()
        }

    override val updateAudioLevel: (Double) -> Unit
        get() = { level ->
            backing.audioLevel = level
            state.propagateParameterChanges()
        }

    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportAudio(options).getOrThrow()
        }

    override val resumeSendTransportAudio: ResumeSendTransportAudioType
        get() = { options ->
            com.mediasfu.sdk.consumers.resumeSendTransportAudio(options)
        }

    override val streamSuccessAudio: StreamSuccessAudioType
        get() = { options ->
            com.mediasfu.sdk.consumers.streamSuccessAudio(options)
        }

    // ---------------------------------------------------------------------
    // Permission / request helpers
    // ---------------------------------------------------------------------
    override val updateAudioRequestState: (String?) -> Unit
        get() = { stateValue ->
            backing.audioRequestState = stateValue ?: "none"
            state.propagateParameterChanges()
        }

    override val updateAudioPaused: (Boolean) -> Unit
        get() = { paused ->
            backing.audioPaused = paused
            state.propagateParameterChanges()
        }

    override val checkPermission: CheckPermissionType
        get() = { options: CheckPermissionOptions ->
            com.mediasfu.sdk.consumers.checkPermission(options)
        }

    override val disconnectSendTransportAudio: suspend (DisconnectSendTransportAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.disconnectSendTransportAudio(options).getOrThrow()
        }

    override val requestPermissionAudio: RequestPermissionAudioType
        get() = backing.requestPermissionAudio ?: suspend { true }

    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler ?: ShowAlert { message, type, duration ->
            state.alert.show(message, type, duration)
            backing.showAlert(message, type, duration)
            state.propagateParameterChanges()
        }

    // ---------------------------------------------------------------------
    // Disconnect / resume helpers
    // ---------------------------------------------------------------------
    override val audioProducer: WebRtcProducer? get() = backing.audioProducer
    override val localAudioProducer: WebRtcProducer? get() = backing.localAudioProducer

    override val updateAudioProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            backing.audioProducer = producer
            state.propagateParameterChanges()
        }

    override val updateLocalAudioProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            backing.localAudioProducer = producer
            state.propagateParameterChanges()
        }

    override fun updateAudioProducer(producer: WebRtcProducer?) {
        backing.audioProducer = producer
        state.propagateParameterChanges()
    }

    override fun updateLocalAudioProducer(producer: WebRtcProducer?) {
        backing.localAudioProducer = producer
        state.propagateParameterChanges()
    }

    override fun updateUpdateMainWindow(update: Boolean) {
        backing.updateMainWindow = update
        state.propagateParameterChanges()
    }

    override suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit> =
        backing.prepopulateUserMedia(options)

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.prepopulateUserMedia(options)
        }

    // ---------------------------------------------------------------------
    // Prepopulate screen helpers
    // ---------------------------------------------------------------------
    override val allVideoStreams: List<Stream> get() = backing.allVideoStreamsState
    override val shareScreenStarted: Boolean get() = backing.shareScreenStarted
    override val eventType: EventType get() = backing.eventType
    override val screenId: String? get() = backing.screenId.ifEmpty { null }
    override val forceFullDisplay: Boolean get() = backing.forceFullDisplay
    override val mainScreenFilled: Boolean get() = backing.mainScreenFilled
    override val adminOnMainScreen: Boolean get() = backing.adminOnMainScreen
    override val mainScreenPerson: String get() = backing.mainScreenPerson
    override val oldAllStreams: List<Stream> get() = backing.oldAllStreams
    override val screenForceFullDisplay: Boolean get() = backing.screenForceFullDisplay
    override val localStreamScreen: MediaStream? get() = backing.localStreamScreen
    override val remoteScreenStream: List<Stream> get() = backing.remoteScreenStream
    override val localStreamVideo: MediaStream? get() = backing.localStreamVideo
    override val mainHeightWidth: Double get() = backing.mainHeightWidth
    override val isWideScreen: Boolean get() = backing.isWideScreen
    override val localUIMode: Boolean get() = backing.localUIMode
    override val whiteboardStarted: Boolean get() = backing.whiteboardStarted
    override val whiteboardEnded: Boolean get() = backing.whiteboardEnded
    override val virtualStream: Any? get() = backing.virtualStream
    override val keepBackground: Boolean get() = backing.keepBackground
    override val annotateScreenStream: Boolean get() = backing.annotateScreenStream

    override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
        get() = backing.audioDecibels

    override val updateMainScreenPerson: (String) -> Unit
        get() = { person ->
            backing.mainScreenPerson = person
            state.propagateParameterChanges()
        }

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = { filled ->
            backing.mainScreenFilled = filled
            state.propagateParameterChanges()
        }

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = { adminOn ->
            backing.adminOnMainScreen = adminOn
            state.propagateParameterChanges()
        }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = { value ->
            backing.mainHeightWidth = value
            state.propagateParameterChanges()
        }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = { value ->
            backing.screenForceFullDisplay = value
            state.propagateParameterChanges()
        }

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = { handler -> backing.showAlertHandler = handler }

    override val updateMainGridStream: (List<com.mediasfu.sdk.ui.MediaSfuUIComponent>) -> Unit
        get() = { components ->
            backing.updateMainGridStream(components)
            state.propagateParameterChanges()
        }

    // ---------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------
    override fun getUpdatedAllParams(): ClickAudioParameters = this
}
