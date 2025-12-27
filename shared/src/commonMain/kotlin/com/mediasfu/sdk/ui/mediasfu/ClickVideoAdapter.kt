package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.EngineReorderStreamsParameters
import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.CheckPermissionType
import com.mediasfu.sdk.consumers.ConnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportVideoOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.RequestPermissionCameraType
import com.mediasfu.sdk.consumers.ResumeSendTransportVideoOptions
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioOptions
import com.mediasfu.sdk.consumers.StreamSuccessVideoType
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.methods.stream_methods.ClickVideoParameters
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils

internal fun MediasfuGenericState.createClickVideoParameters(): ClickVideoParameters =
    MediasfuClickVideoParameters(parameters, this)

private class MediasfuClickVideoParameters(
    private val backing: MediasfuParameters,
    private val state: MediasfuGenericState
) : ClickVideoParameters {

    // ---------------------------------------------------------------------
    // Basic media / permission state
    // ---------------------------------------------------------------------
    override val checkMediaPermission: Boolean get() = backing.checkMediaPermission
    override val hasCameraPermission: Boolean get() = backing.hasCameraPermission
    override val videoAlreadyOn: Boolean get() = backing.videoAlreadyOn
    override val audioOnlyRoom: Boolean get() = backing.audioOnlyRoom
    override val recordStarted: Boolean get() = backing.recordStarted
    override val recordResumed: Boolean get() = backing.recordResumed
    override val recordPaused: Boolean get() = backing.recordPaused
    override val recordStopped: Boolean get() = backing.recordStopped
    override val recordingMediaOptions: String get() = backing.recordingMediaOptions
    override val allowed: Boolean get() = backing.allowed

    override val updateAllowed: (Boolean) -> Unit
        get() = { value ->
            backing.allowed = value
            state.propagateParameterChanges()
        }

    // ---------------------------------------------------------------------
    // Identity / connection references
    // ---------------------------------------------------------------------
    override val islevel: String get() = backing.islevel
    override val youAreCoHost: Boolean get() = backing.youAreCoHost
    override val adminRestrictSetting: Boolean get() = backing.adminRestrictSetting
    override val videoRequestState: String? get() = backing.videoRequestState
    override val videoRequestTime: Long? get() = backing.videoRequestTime
    override val member: String get() = backing.member
    override val socket: SocketManager? get() = backing.socket
    override val localSocket: SocketManager? get() = backing.localSocket
    override val roomName: String get() = backing.roomName
    override val userDefaultVideoInputDevice: String get() = backing.userDefaultVideoInputDevice
    override val currentFacingMode: String get() = backing.currentFacingMode
    override val vidCons: VidCons get() = backing.vidCons
    override val frameRate: Int get() = backing.frameRate
    override val videoAction: Boolean get() = backing.videoAction
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
    // Audio transport state (ConnectSendTransportParameters support)
    // ---------------------------------------------------------------------
    override val transportCreatedAudio: Boolean get() = backing.transportCreatedAudio
    override val micAction: Boolean get() = backing.micAction
    override val audioParams: ProducerOptionsType? get() = backing.audioParams
    override val localStreamAudio: MediaStream? get() = backing.localStreamAudio
    override val defAudioID: String get() = backing.defAudioID
    override val userDefaultAudioInputDevice: String get() = backing.userDefaultAudioInputDevice
    override val aParams: ProducerOptionsType? get() = backing.aParams
    override val audioProducer: WebRtcProducer? get() = backing.audioProducer
    override val localAudioProducer: WebRtcProducer? get() = backing.localAudioProducer

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
        get() = { params ->
            backing.audioParams = params
            state.propagateParameterChanges()
        }

    override val updateLocalStreamAudio: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStreamAudio = stream
            state.propagateParameterChanges()
        }

    // ---------------------------------------------------------------------
    // Screen transport integration
    // ---------------------------------------------------------------------
    override val screenProducer: WebRtcProducer? get() = backing.screenProducer
    override val localScreenProducer: WebRtcProducer? get() = backing.localScreenProducer
    override val screenParams: ProducerOptionsType? get() = backing.screenParams
    override val defScreenID: String get() = backing.screenId
    override val canvasStream: MediaStream? get() = backing.virtualStream as? MediaStream

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

    override val updateDefScreenID: (String) -> Unit
        get() = { value ->
            backing.screenId = value
            state.propagateParameterChanges()
        }

    override val updateLocalStreamScreen: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStreamScreen = stream
            state.propagateParameterChanges()
        }

    override val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportScreen(options).getOrThrow()
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

    override val updateAudioLevel: (Double) -> Unit
        get() = { level ->
            backing.audioLevel = level
            state.propagateParameterChanges()
        }

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

    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportAudio(options).getOrThrow()
        }

    override val resumeSendTransportAudio: suspend (ResumeSendTransportAudioOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.resumeSendTransportAudio(options)
        }

    // ---------------------------------------------------------------------
    // StreamSuccess / ConnectSendTransport requirements
    // ---------------------------------------------------------------------
    override val transportCreatedVideo: Boolean get() = backing.transportCreatedVideo
    override val videoParams: ProducerOptionsType? get() = backing.videoParams
    override val localStreamVideo: MediaStream? get() = backing.localStreamVideo
    override val defVideoID: String get() = backing.defVideoID
    override val params: ProducerOptionsType? get() = backing.params
    override val vParams: ProducerOptionsType? get() = backing.vParams
    override val hostLabel: String get() = backing.hostLabel
    override val updateMainWindow: Boolean get() = backing.updateMainWindow
    override val lockScreen: Boolean get() = backing.lockScreen
    override val shared: Boolean get() = backing.shared
    override val audioAlreadyOn: Boolean get() = backing.audioAlreadyOn

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { updated -> state.room.updateParticipants(updated) }

    override val updateTransportCreatedVideo: (Boolean) -> Unit
        get() = { created ->
            backing.transportCreatedVideo = created
            state.propagateParameterChanges()
        }

    override val updateVideoAlreadyOn: (Boolean) -> Unit
        get() = { value ->
            backing.videoAlreadyOn = value
            state.media.videoAlreadyOn = value
            state.propagateParameterChanges()
        }

    override val updateVideoAction: (Boolean) -> Unit
        get() = { value ->
            backing.videoAction = value
            state.propagateParameterChanges()
        }

    override val updateVideoParams: (ProducerOptionsType?) -> Unit
        get() = { value ->
            backing.videoParams = value
            state.propagateParameterChanges()
        }

    override val updateLocalStream: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStream = stream
            state.propagateParameterChanges()
        }

    override val updateLocalStreamVideo: (MediaStream?) -> Unit
        get() = { stream ->
            backing.localStreamVideo = stream
            state.propagateParameterChanges()
        }

    override val updateDefVideoID: (String) -> Unit
        get() = { value ->
            backing.defVideoID = value
            state.propagateParameterChanges()
        }

    override val updateUserDefaultVideoInputDevice: (String) -> Unit
        get() = { value ->
            backing.userDefaultVideoInputDevice = value
            state.propagateParameterChanges()
        }

    override val updateUpdateMainWindow: (Boolean) -> Unit
        get() = { update ->
            backing.updateMainWindow = update
            state.propagateParameterChanges()
        }

    override fun updateUpdateMainWindow(update: Boolean) {
        backing.updateMainWindow = update
        state.propagateParameterChanges()
    }

    override val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.connectSendTransportVideo(options).getOrThrow()
        }

    override val resumeSendTransportVideo: suspend (ResumeSendTransportVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.resumeSendTransportVideo(options)
        }

    override val streamSuccessVideo: StreamSuccessVideoType
        get() = { options ->
            com.mediasfu.sdk.consumers.streamSuccessVideo(options)
        }

    // ---------------------------------------------------------------------
    // Permission / request helpers
    // ---------------------------------------------------------------------
    override val updateVideoRequestState: (String) -> Unit
        get() = { stateValue ->
            backing.videoRequestState = stateValue
            state.propagateParameterChanges()
        }

    override val checkPermission: CheckPermissionType
        get() = { options: CheckPermissionOptions ->
            com.mediasfu.sdk.consumers.checkPermission(options)
        }

    override val disconnectSendTransportVideo: suspend (DisconnectSendTransportVideoOptions) -> Unit
        get() = { options ->
            com.mediasfu.sdk.consumers.disconnectSendTransportVideo(options).getOrThrow()
        }

    override val requestPermissionCamera: RequestPermissionCameraType
        get() = backing.requestPermissionCamera ?: suspend { true }

    override val showAlert: ShowAlert?
        get() = backing.showAlertHandler ?: ShowAlert { message, type, duration ->
            state.alert.show(message, type, duration)
            backing.showAlert(message, type, duration)
            state.propagateParameterChanges()
        }

    // ---------------------------------------------------------------------
    // Producer accessors / mutators
    // ---------------------------------------------------------------------
    override var videoProducer: WebRtcProducer?
        get() = backing.videoProducer
        set(value) {
            backing.videoProducer = value
            state.propagateParameterChanges()
        }

    override var localVideoProducer: WebRtcProducer?
        get() = backing.localVideoProducer
        set(value) {
            backing.localVideoProducer = value
            state.propagateParameterChanges()
        }

    override val updateVideoProducer: (WebRtcProducer?) -> Unit
        get() = { producer ->
            backing.videoProducer = producer
            state.propagateParameterChanges()
        }

    override val updateLocalVideoProducer: ((WebRtcProducer?) -> Unit)?
        get() = { producer ->
            backing.localVideoProducer = producer
            state.propagateParameterChanges()
        }

    override fun updateVideoProducer(producer: WebRtcProducer?) {
        backing.videoProducer = producer
        state.propagateParameterChanges()
    }

    override fun updateLocalVideoProducer(producer: WebRtcProducer?) {
        backing.localVideoProducer = producer
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
    override val mainHeightWidth: Double get() = backing.mainHeightWidth
    override val isWideScreen: Boolean get() = backing.isWideScreen
    override val localUIMode: Boolean get() = backing.localUIMode
    override val whiteboardStarted: Boolean get() = backing.whiteboardStarted
    override val whiteboardEnded: Boolean get() = backing.whiteboardEnded
    override val virtualStream: Any? get() = backing.virtualStream
    override val keepBackground: Boolean get() = backing.keepBackground
    override val selectedBackground: VirtualBackground? get() = backing.selectedBackground
    override val annotateScreenStream: Boolean get() = backing.annotateScreenStream

    override val audioDecibels: List<com.mediasfu.sdk.model.AudioDecibels>
        get() = backing.audioDecibels

    /**
     * Auto-apply saved background callback.
     * When keepBackground=true and selectedBackground is set, this callback is invoked
     * to automatically apply the background to the new video stream when camera turns on.
     * This matches React behavior for "Save for Later" feature.
     */
    override val onAutoApplyBackground: (suspend (VirtualBackground, MediaStream) -> Unit)?
        get() = { background, newStream ->
            // Get the processor and device from connectivity
            val processor = state.connectivity.virtualBackgroundProcessor
            val webRtcDevice = backing.device
            
            if (processor != null && webRtcDevice != null) {
                try {
                    // Start processing with the new stream
                    val outputStream = processor.startProcessingWithDevice(
                        inputStream = newStream,
                        background = background,
                        device = webRtcDevice,
                        onProcessedFrame = null
                    )
                    
                    if (outputStream != null) {
                        val videoTracks = outputStream.getVideoTracks()
                        if (videoTracks.isNotEmpty()) {
                            val newTrack = videoTracks.first()
                            
                            // Set the virtual stream in parameters
                            backing.virtualStream = outputStream
                            backing.processedStream = outputStream
                            
                            // Close existing producer and create new one with processed track
                            val currentProducer = backing.videoProducer
                            val producerTransport = backing.producerTransport
                            
                            if (currentProducer != null) {
                                try {
                                    currentProducer.close()
                                    backing.videoProducer = null
                                    kotlinx.coroutines.delay(500)
                                } catch (_: Exception) { }
                            }
                            
                            if (producerTransport != null) {
                                try {
                                    val newProducer = producerTransport.produce(
                                        track = newTrack,
                                        encodings = emptyList(),
                                        codecOptions = null,
                                        appData = null
                                    )
                                    backing.videoProducer = newProducer
                                } catch (_: Exception) { }
                            }
                            
                            // Trigger grid refresh with reorderStreams to update youyou stream
                            try {
                                val prepopulateOptions = PrepopulateUserMediaOptions(
                                    name = backing.member,
                                    parameters = this
                                )
                                com.mediasfu.sdk.consumers.prepopulateUserMedia(prepopulateOptions)
                                
                                // Call reorderStreams to update the grid with virtualStream
                                reorderStreams(false, true)
                            } catch (_: Exception) { }
                            
                            state.propagateParameterChanges()
                        }
                    }
                } catch (_: Exception) { }
            }
        }

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

    override val reorderStreams: suspend (add: Boolean, screenChanged: Boolean) -> Unit
        get() = { add, screenChanged ->
            val options = com.mediasfu.sdk.consumers.ReorderStreamsOptions(
                add = add,
                screenChanged = screenChanged,
                parameters = EngineReorderStreamsParameters(backing)
            )
            com.mediasfu.sdk.consumers.reorderStreams(options).getOrThrow()
        }

    // ---------------------------------------------------------------------
    // Misc
    // ---------------------------------------------------------------------
    override fun getUpdatedAllParams(): ClickVideoParameters = this
}
