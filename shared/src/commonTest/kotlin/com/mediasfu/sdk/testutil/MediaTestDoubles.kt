package com.mediasfu.sdk.testutil

import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportAudioParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportVideoParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoParameters
import com.mediasfu.sdk.consumers.GetVideosOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.consumers.ResumeSendTransportAudioParameters
import com.mediasfu.sdk.consumers.ResumeSendTransportVideoParameters
import com.mediasfu.sdk.consumers.OnScreenChangesOptions
import com.mediasfu.sdk.consumers.StopShareScreenOptions
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.model.ControlMediaHostParameters
import com.mediasfu.sdk.model.DisconnectSendTransportAudioType
import com.mediasfu.sdk.model.DisconnectSendTransportScreenType
import com.mediasfu.sdk.model.DisconnectSendTransportVideoType
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MediaStreamController
import com.mediasfu.sdk.model.OnScreenChangesType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.DisconnectSendTransportAudioOptions
import com.mediasfu.sdk.model.DisconnectSendTransportVideoOptions
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.StopShareScreenType
import com.mediasfu.sdk.socket.ConnectionState
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.ProducerSource
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.TransportType
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport

/**
 * Simple [MediaStreamTrack] implementation for tests with state inspection helpers.
 */
class TestMediaStreamTrack(
    override val id: String = "track-${nextId()}",
    override val kind: String = "audio",
    enabled: Boolean = true
) : MediaStreamTrack {
    private var enabledState: Boolean = enabled
    private var stoppedState: Boolean = false

    val stopCalls = mutableListOf<Unit>()

    override val enabled: Boolean
        get() = enabledState && !stoppedState

    override fun setEnabled(value: Boolean) {
        enabledState = value
    }

    fun resume() {
        stoppedState = false
    }

    override fun stop() {
        stoppedState = true
        stopCalls += Unit
    }

    val isStopped: Boolean
        get() = stoppedState

    companion object {
        private var counter = 0

        private fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/**
 * Lightweight [MediaStream] fake that tracks audio/video additions for assertions.
 */
open class TestMediaStream(
    override val id: String = "stream-${nextId()}",
    audioTracks: List<MediaStreamTrack> = emptyList(),
    videoTracks: List<MediaStreamTrack> = emptyList(),
    private var activeState: Boolean = true
) : MediaStream {
    private val audioTrackStore = audioTracks.toMutableList()
    private val videoTrackStore = videoTracks.toMutableList()

    val addTrackCalls = mutableListOf<MediaStreamTrack>()
    val removeTrackCalls = mutableListOf<MediaStreamTrack>()
    val stopCalls = mutableListOf<Unit>()

    override val active: Boolean
        get() = activeState

    fun setActive(value: Boolean) {
        activeState = value
    }

    override fun getTracks(): List<MediaStreamTrack> = audioTrackStore + videoTrackStore

    override fun getAudioTracks(): List<MediaStreamTrack> = audioTrackStore.toList()

    override fun getVideoTracks(): List<MediaStreamTrack> = videoTrackStore.toList()

    override fun addTrack(track: MediaStreamTrack) {
        addTrackCalls += track
        when (track.kind.lowercase()) {
            "audio" -> audioTrackStore += track
            "video" -> videoTrackStore += track
            else -> audioTrackStore += track
        }
    }

    override fun removeTrack(track: MediaStreamTrack) {
        removeTrackCalls += track
        if (!audioTrackStore.remove(track)) {
            videoTrackStore.remove(track)
        }
    }

    override fun stop() {
        if (!activeState) {
            return
        }
        activeState = false
        getTracks().forEach { it.stop() }
        stopCalls += Unit
    }

    fun clearAudioTracks() {
        audioTrackStore.clear()
    }

    fun clearVideoTracks() {
        videoTrackStore.clear()
    }

    companion object {
        private var counter = 0

        private fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/**
 * Minimal [WebRtcDevice] fake capturing invocation details.
 */
class TestWebRtcDevice(
    private val mediaStreamFactory: (Map<String, Any?>) -> MediaStream = { TestMediaStream() },
    private val sendTransportFactory: (Map<String, Any?>) -> WebRtcTransport = { _ ->
        TestWebRtcTransport(type = TransportType.SEND)
    },
    private val recvTransportFactory: (Map<String, Any?>) -> WebRtcTransport = { _ ->
        TestWebRtcTransport(type = TransportType.RECEIVE)
    }
) : WebRtcDevice {
    val loadCalls = mutableListOf<RtpCapabilities>()
    var loadResult: Result<Unit> = Result.success(Unit)

    val getUserMediaCalls = mutableListOf<Map<String, Any?>>()
    var nextUserMedia: MediaStream? = null
    val createdStreams = mutableListOf<MediaStream>()

    val enumerateDevicesCalls = mutableListOf<Unit>()
    var enumerateDevicesResult: List<MediaDeviceInfo> = emptyList()

    val createSendTransportCalls = mutableListOf<Map<String, Any?>>()
    var sendTransportResult: WebRtcTransport? = null

    val createRecvTransportCalls = mutableListOf<Map<String, Any?>>()
    var recvTransportResult: WebRtcTransport? = null

    val closeCalls = mutableListOf<Unit>()
    var closed: Boolean = false

    override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
        loadCalls += rtpCapabilities
        return loadResult
    }

    override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
        getUserMediaCalls += constraints
        val stream = (nextUserMedia ?: mediaStreamFactory(constraints)).also {
            createdStreams += it
        }
        nextUserMedia = null
        return stream
    }

    override suspend fun enumerateDevices(): List<MediaDeviceInfo> {
        enumerateDevicesCalls += Unit
        return enumerateDevicesResult
    }

    override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
        createSendTransportCalls += params
        return sendTransportResult ?: sendTransportFactory(params)
    }

    override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
        createRecvTransportCalls += params
        return recvTransportResult ?: recvTransportFactory(params)
    }

    override fun close() {
        closed = true
        closeCalls += Unit
    }

    fun reset() {
        nextUserMedia = null
        sendTransportResult = null
        recvTransportResult = null
        enumerateDevicesResult = emptyList()
        createdStreams.clear()
        loadCalls.clear()
        getUserMediaCalls.clear()
        enumerateDevicesCalls.clear()
        createSendTransportCalls.clear()
        createRecvTransportCalls.clear()
        closeCalls.clear()
        closed = false
    }
}

/**
 * [MediaStream] implementation that also supports [MediaStreamController] for disable tracking.
 */
class TestControllableMediaStream(
    audioTracks: List<MediaStreamTrack> = listOf(TestMediaStreamTrack(kind = "audio")),
    videoTracks: List<MediaStreamTrack> = emptyList()
) : TestMediaStream(audioTracks = audioTracks, videoTracks = videoTracks, activeState = true),
    MediaStreamController {

    var disableAudioCalls = 0
    var disableVideoCalls = 0
    var shouldThrowOnAudio: Boolean = false
    var shouldThrowOnVideo: Boolean = false

    override fun disableAudio() {
        disableAudioCalls += 1
        if (shouldThrowOnAudio) {
            throw IllegalStateException("audio")
        }
        getAudioTracks().forEach { track ->
            if (track is TestMediaStreamTrack) {
                track.setEnabled(false)
            }
        }
    }

    override fun disableVideo() {
        disableVideoCalls += 1
        if (shouldThrowOnVideo) {
            throw IllegalStateException("video")
        }
        getVideoTracks().forEach { track ->
            if (track is TestMediaStreamTrack) {
                track.setEnabled(false)
            }
        }
    }
}

/**
 * Minimal [WebRtcTransport] implementation that captures registered handlers.
 */
class TestWebRtcTransport(
    override val id: String = "transport-${nextId()}",
    override val type: TransportType = TransportType.SEND,
    private var state: TransportConnectionState = TransportConnectionState.NEW
) : WebRtcTransport {
    var onConnectHandler: ((com.mediasfu.sdk.webrtc.ConnectData) -> Unit)? = null
    var onProduceHandler: ((com.mediasfu.sdk.webrtc.ProduceData) -> Unit)? = null
    var onConnectionStateChangeHandler: ((String) -> Unit)? = null
    val produceCalls = mutableListOf<ProducedCall>()

    override val connectionState: TransportConnectionState
        get() = state

    override fun close() {
        state = TransportConnectionState.CLOSED
    }

    override fun onConnect(handler: (com.mediasfu.sdk.webrtc.ConnectData) -> Unit) {
        onConnectHandler = handler
    }

    override fun onProduce(handler: (com.mediasfu.sdk.webrtc.ProduceData) -> Unit) {
        onProduceHandler = handler
    }

    override fun onConnectionStateChange(handler: (String) -> Unit) {
        onConnectionStateChangeHandler = handler
    }

    override fun produce(
        track: MediaStreamTrack,
        encodings: List<RtpEncodingParameters>,
        codecOptions: com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions?,
        appData: Map<String, Any?>?
    ): WebRtcProducer {
        val kind = when (track.kind.lowercase()) {
            "video" -> MediaKind.VIDEO
            else -> MediaKind.AUDIO
        }
        val producer = TestWebRtcProducer(kind = kind)
        produceCalls += ProducedCall(track, encodings, codecOptions, appData, producer)
        return producer
    }

    fun updateState(newState: TransportConnectionState) {
        state = newState
        onConnectionStateChangeHandler?.invoke(newState.name.lowercase())
    }

    data class ProducedCall(
        val track: MediaStreamTrack,
        val encodings: List<RtpEncodingParameters>,
        val codecOptions: com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions?,
        val appData: Map<String, Any?>?,
        val producer: TestWebRtcProducer
    )

    companion object {
        private var counter = 0

        private fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/**
 * Minimal [WebRtcProducer] implementation with pause/resume tracking.
 */
class TestWebRtcProducer(
    override val id: String = "producer-${nextId()}",
    override val kind: com.mediasfu.sdk.webrtc.MediaKind = com.mediasfu.sdk.webrtc.MediaKind.AUDIO,
    override val source: ProducerSource = ProducerSource.MICROPHONE
) : WebRtcProducer {
    private var closed = false
    private var pausedState = false

    override val paused: Boolean
        get() = pausedState

    override fun close() {
        closed = true
    }

    override fun pause() {
        pausedState = true
    }

    override fun resume() {
        pausedState = false
    }

    val isClosed: Boolean
        get() = closed

    companion object {
        private var counter = 0

        private fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/** Simplified [SocketManager] implementation for unit tests. */
class TestSocketManager(
    override val id: String? = "socket-${nextId()}",
    private val ackResponses: MutableMap<String, Any?> = mutableMapOf()
) : SocketManager {

    private var connected = false
    private var connectionState: ConnectionState = ConnectionState.DISCONNECTED

    val connectCalls = mutableListOf<Pair<String, SocketConfig>>()
    val disconnectCalls = mutableListOf<Unit>()
    val emitCalls = mutableListOf<Pair<String, Map<String, Any?>>>()
    val emitWithAckCalls = mutableListOf<Triple<String, Map<String, Any?>, Long>>()
    val emitWithAckCallbackCalls = mutableListOf<Triple<String, Map<String, Any?>, (Any?) -> Unit>>()
    val onHandlers = mutableMapOf<String, suspend (Map<String, Any?>) -> Unit>()
    val onConnectHandlers = mutableListOf<suspend () -> Unit>()
    val onDisconnectHandlers = mutableListOf<suspend (String) -> Unit>()
    val onErrorHandlers = mutableListOf<suspend (Throwable) -> Unit>()
    val onReconnectHandlers = mutableListOf<suspend (Int) -> Unit>()
    val onReconnectAttemptHandlers = mutableListOf<suspend (Int) -> Unit>()
    val onReconnectFailedHandlers = mutableListOf<suspend () -> Unit>()

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        connectCalls += url to config
        connected = true
        connectionState = ConnectionState.CONNECTED
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        disconnectCalls += Unit
        connected = false
        connectionState = ConnectionState.DISCONNECTED
        return Result.success(Unit)
    }

    override fun isConnected(): Boolean = connected

    override fun getConnectionState(): ConnectionState = connectionState

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        emitCalls += event to data
    }

    override suspend fun <T> emitWithAck(event: String, data: Map<String, Any?>, timeout: Long): T {
        emitWithAckCalls += Triple(event, data, timeout)
        @Suppress("UNCHECKED_CAST")
        return ackResponses[event] as? T
            ?: throw IllegalStateException("No ack response prepared for $event")
    }

    override fun emitWithAck(event: String, data: Map<String, Any?>, callback: (Any?) -> Unit) {
        emitWithAckCallbackCalls += Triple(event, data, callback)
        callback(ackResponses[event])
    }

    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {
        onHandlers[event] = handler
    }

    override fun off(event: String) {
        onHandlers.remove(event)
    }

    override fun offAll() {
        onHandlers.clear()
    }

    override fun onConnect(handler: suspend () -> Unit) {
        onConnectHandlers += handler
    }

    override fun onDisconnect(handler: suspend (String) -> Unit) {
        onDisconnectHandlers += handler
    }

    override fun onError(handler: suspend (Throwable) -> Unit) {
        onErrorHandlers += handler
    }

    override fun onReconnect(handler: suspend (Int) -> Unit) {
        onReconnectHandlers += handler
    }

    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {
        onReconnectAttemptHandlers += handler
    }

    override fun onReconnectFailed(handler: suspend () -> Unit) {
        onReconnectFailedHandlers += handler
    }

    fun setAckResponse(event: String, response: Any?) {
        ackResponses[event] = response
    }

    companion object {
        private var counter = 0

        private fun nextId(): Int {
            counter += 1
            return counter
        }
    }
}

/**
 * Base implementation of [com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters] that provides
 * sane defaults and simple state tracking for tests.
 */
open class TestPrepopulateUserMediaParameters(
    participants: List<Participant> = listOf(Participant(id = "host", name = "Host", islevel = "2")),
    member: String = "Host",
    islevel: String = "2",
    eventType: EventType = EventType.CONFERENCE,
    socket: SocketManager? = null,
    localSocket: SocketManager? = null
) : com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters {
    protected var participantsState: List<Participant> = participants
    protected var allVideoStreamsState: List<Stream> = emptyList()
    protected var islevelState: String = islevel
    protected var memberState: String = member
    protected var sharedState: Boolean = false
    protected var shareScreenStartedState: Boolean = false
    protected var eventTypeState: EventType = eventType
    protected var screenIdState: String? = null
    protected var forceFullDisplayState: Boolean = false
    protected var socketState: SocketManager? = socket
    protected var localSocketState: SocketManager? = localSocket
    protected var updateMainWindowState: Boolean = false
    protected var mainScreenFilledState: Boolean = false
    protected var adminOnMainScreenState: Boolean = false
    protected var mainScreenPersonState: String = participants.firstOrNull()?.name ?: ""
    protected var videoAlreadyOnState: Boolean = false
    protected var audioAlreadyOnState: Boolean = false
    protected var oldAllStreamsState: List<Stream> = emptyList()
    protected var screenForceFullDisplayState: Boolean = false
    protected var localStreamScreenState: MediaStream? = null
    protected var remoteScreenStreamState: List<Stream> = emptyList()
    protected var localStreamVideoState: MediaStream? = null
    protected var mainHeightWidthState: Double = 0.0
    protected var isWideScreenState: Boolean = false
    protected var localUIModeState: Boolean = false
    protected var whiteboardStartedState: Boolean = false
    protected var whiteboardEndedState: Boolean = false
    protected var virtualStreamState: Any? = null
    protected var keepBackgroundState: Boolean = false
    protected var annotateScreenStreamState: Boolean = false
    protected var showAlertState: ShowAlert? = null

    val updateMainWindowCalls = mutableListOf<Boolean>()
    val mainScreenPersonUpdates = mutableListOf<String>()
    val mainScreenFilledUpdates = mutableListOf<Boolean>()
    val adminOnMainScreenUpdates = mutableListOf<Boolean>()
    val mainHeightWidthUpdates = mutableListOf<Double>()
    val screenForceFullDisplayUpdates = mutableListOf<Boolean>()
    val showAlertUpdates = mutableListOf<ShowAlert?>()

    override val participants: List<Participant>
        get() = participantsState

    override val allVideoStreams: List<Stream>
        get() = allVideoStreamsState

    override val islevel: String
        get() = islevelState

    override val member: String
        get() = memberState

    override val shared: Boolean
        get() = sharedState

    override val shareScreenStarted: Boolean
        get() = shareScreenStartedState

    override val eventType: EventType
        get() = eventTypeState

    override val screenId: String?
        get() = screenIdState

    override val forceFullDisplay: Boolean
        get() = forceFullDisplayState

    override val socket: SocketManager?
        get() = socketState

    override val localSocket: SocketManager?
        get() = localSocketState

    override val updateMainWindow: Boolean
        get() = updateMainWindowState

    override val mainScreenFilled: Boolean
        get() = mainScreenFilledState

    open val showAlert: ShowAlert?
        get() = showAlertState

    override val adminOnMainScreen: Boolean
        get() = adminOnMainScreenState

    override val mainScreenPerson: String
        get() = mainScreenPersonState

    override val videoAlreadyOn: Boolean
        get() = videoAlreadyOnState

    override val audioAlreadyOn: Boolean
        get() = audioAlreadyOnState

    override val oldAllStreams: List<Stream>
        get() = oldAllStreamsState

    override val screenForceFullDisplay: Boolean
        get() = screenForceFullDisplayState

    override val localStreamScreen: MediaStream?
        get() = localStreamScreenState

    override val remoteScreenStream: List<Stream>
        get() = remoteScreenStreamState

    override val localStreamVideo: MediaStream?
        get() = localStreamVideoState

    override val mainHeightWidth: Double
        get() = mainHeightWidthState

    override val isWideScreen: Boolean
        get() = isWideScreenState

    override val localUIMode: Boolean
        get() = localUIModeState

    override val whiteboardStarted: Boolean
        get() = whiteboardStartedState

    override val whiteboardEnded: Boolean
        get() = whiteboardEndedState

    override val virtualStream: Any?
        get() = virtualStreamState

    override val keepBackground: Boolean
        get() = keepBackgroundState

    override val annotateScreenStream: Boolean
        get() = annotateScreenStreamState

    override val updateMainScreenPerson: (String) -> Unit = {
        mainScreenPersonState = it
        mainScreenPersonUpdates += it
    }

    override val updateMainScreenFilled: (Boolean) -> Unit = {
        mainScreenFilledState = it
        mainScreenFilledUpdates += it
    }

    override val updateAdminOnMainScreen: (Boolean) -> Unit = {
        adminOnMainScreenState = it
        adminOnMainScreenUpdates += it
    }

    override val updateMainHeightWidth: (Double) -> Unit = {
        mainHeightWidthState = it
        mainHeightWidthUpdates += it
    }

    override val updateScreenForceFullDisplay: (Boolean) -> Unit = {
        screenForceFullDisplayState = it
        screenForceFullDisplayUpdates += it
    }

    override val updateUpdateMainWindow: (Boolean) -> Unit = {
        updateMainWindowState = it
        updateMainWindowCalls += it
    }

    override val updateShowAlert: (ShowAlert?) -> Unit = {
        showAlertState = it
        showAlertUpdates += it
    }

    open override fun getUpdatedAllParams(): com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters = this

    fun assignParticipants(newParticipants: List<Participant>) {
        participantsState = newParticipants
    }

    fun assignSocket(newSocket: SocketManager?) {
        socketState = newSocket
    }

    fun assignLocalSocket(newSocket: SocketManager?) {
        localSocketState = newSocket
    }

    fun assignShowAlert(alert: ShowAlert?) {
        showAlertState = alert
    }

    fun assignAudioAlreadyOn(value: Boolean) {
        audioAlreadyOnState = value
    }

    fun assignVideoAlreadyOn(value: Boolean) {
        videoAlreadyOnState = value
    }

}

/**
 * Full-featured implementation of [ConnectSendTransportAudioParameters] suitable for unit tests.
 */
class TestConnectSendTransportAudioParameters(
    participants: List<Participant> = listOf(Participant(id = "host", name = "Host", islevel = "2")),
    socket: SocketManager? = null,
    localSocket: SocketManager? = null,
    initialProducerTransport: WebRtcTransport? = TestWebRtcTransport("remote-transport"),
    initialLocalProducerTransport: WebRtcTransport? = TestWebRtcTransport("local-transport"),
    initialAudioProducer: WebRtcProducer? = TestWebRtcProducer("remote-producer"),
    initialLocalAudioProducer: WebRtcProducer? = TestWebRtcProducer("local-producer"),
    showAlert: ShowAlert? = null,
    initialMicAction: Boolean = true,
    eventType: EventType = EventType.CONFERENCE,
    webRtcDevice: WebRtcDevice? = TestWebRtcDevice()
) : TestPrepopulateUserMediaParameters(
    participants = participants,
    member = "Host",
    islevel = "2",
    eventType = eventType,
    socket = socket,
    localSocket = localSocket
), ConnectSendTransportAudioParameters, ResumeSendTransportAudioParameters {

    override val device: WebRtcDevice? = webRtcDevice

    private var localStreamState: MediaStream? = null
    private var localStreamAudioState: MediaStream? = null
    private var transportCreatedAudioState: Boolean = false
    private var micActionState: Boolean = initialMicAction
    private var audioParamsState: ProducerOptionsType? = ProducerOptionsType()
    private var paramsState: ProducerOptionsType? = ProducerOptionsType()
    private var aParamsState: ProducerOptionsType? = ProducerOptionsType()
    private var defAudioIdState: String = ""
    private var userDefaultAudioInputDeviceState: String = ""
    private var audioProducerState: WebRtcProducer? = initialAudioProducer
    private var localAudioProducerState: WebRtcProducer? = initialLocalAudioProducer
    private var hostLabelState: String = participants.firstOrNull()?.name ?: "Host"
    private var lockScreenState: Boolean = false

    override var transportCreated: Boolean = false
    override var localTransportCreated: Boolean = false
    override var producerTransport: WebRtcTransport? = initialProducerTransport
    override var localProducerTransport: WebRtcTransport? = initialLocalProducerTransport

    val participantAssignments = mutableListOf<List<Participant>>()
    val audioLevelUpdates = mutableListOf<Double>()
    val audioAlreadyOnUpdates = mutableListOf<Boolean>()
    val micActionUpdates = mutableListOf<Boolean>()
    val transportCreatedAudioUpdates = mutableListOf<Boolean>()
    val localStreamAssignments = mutableListOf<MediaStream?>()
    val localStreamAudioAssignments = mutableListOf<MediaStream?>()
    val defAudioIdUpdates = mutableListOf<String>()
    val defaultAudioDeviceUpdates = mutableListOf<String>()
    val audioParamUpdates = mutableListOf<ProducerOptionsType?>()
    val producerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val localProducerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val audioProducerUpdates = mutableListOf<WebRtcProducer?>()
    val localAudioProducerUpdates = mutableListOf<WebRtcProducer?>()
    val prepopulateCalls = mutableListOf<PrepopulateUserMediaOptions>()
    val showAlertCalls = mutableListOf<Triple<String, String, Int>>()

    init {
        showAlertState = showAlert ?: ShowAlert { message, type, duration ->
            showAlertCalls += Triple(message, type, duration)
        }
    }

    override val localStream: MediaStream?
        get() = localStreamState

    override val localStreamAudio: MediaStream?
        get() = localStreamAudioState

    override val transportCreatedAudio: Boolean
        get() = transportCreatedAudioState

    override val micAction: Boolean
        get() = micActionState

    override val audioParams: ProducerOptionsType?
        get() = audioParamsState

    override val defAudioID: String
        get() = defAudioIdState

    override val userDefaultAudioInputDevice: String
        get() = userDefaultAudioInputDeviceState

    override val params: ProducerOptionsType?
        get() = paramsState

    override val aParams: ProducerOptionsType?
        get() = aParamsState

    override val hostLabel: String
        get() = hostLabelState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val showAlert: ShowAlert?
        get() = showAlertState

    override val audioProducer: WebRtcProducer?
        get() = audioProducerState

    override val localAudioProducer: WebRtcProducer?
        get() = localAudioProducerState

    override val updateParticipants: (List<Participant>) -> Unit = { updated ->
        participantAssignments += updated
        participantsState = updated
    }

    override val updateTransportCreated: (Boolean) -> Unit = { created ->
        transportCreated = created
    }

    override val updateTransportCreatedAudio: (Boolean) -> Unit = { created ->
        transportCreatedAudioState = created
        transportCreatedAudioUpdates += created
    }

    override val updateAudioAlreadyOn: (Boolean) -> Unit = { alreadyOn ->
        audioAlreadyOnState = alreadyOn
        audioAlreadyOnUpdates += alreadyOn
    }

    override val updateMicAction: (Boolean) -> Unit = { action ->
        micActionState = action
        micActionUpdates += action
    }

    override val updateAudioParams: (ProducerOptionsType?) -> Unit = { newParams ->
        audioParamsState = newParams
        audioParamUpdates += newParams
    }

    override val updateLocalStream: (MediaStream?) -> Unit = { stream ->
        localStreamState = stream
        localStreamAssignments += stream
    }

    override val updateLocalStreamAudio: (MediaStream?) -> Unit = { stream ->
        localStreamAudioState = stream
        localStreamAudioAssignments += stream
    }

    override val updateDefAudioID: (String) -> Unit = { id ->
        defAudioIdState = id
        defAudioIdUpdates += id
    }

    override val updateUserDefaultAudioInputDevice: (String) -> Unit = { device ->
        userDefaultAudioInputDeviceState = device
        defaultAudioDeviceUpdates += device
    }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit = { transport ->
        producerTransport = transport
        producerTransportUpdates += transport
    }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)? = { transport ->
        localProducerTransport = transport
        localProducerTransportUpdates += transport
    }

    override val updateAudioLevel: (Double) -> Unit = { level ->
        audioLevelUpdates += level
    }

    override val updateAudioProducer: (WebRtcProducer?) -> Unit = { producer ->
        audioProducerState = producer
        audioProducerUpdates += producer
    }

    override val updateLocalAudioProducer: ((WebRtcProducer?) -> Unit)? = { producer ->
        localAudioProducerState = producer
        localAudioProducerUpdates += producer
    }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit = { options ->
        prepopulateCalls += options
    }

    override fun getUpdatedAllParams(): ConnectSendTransportAudioParameters = this

    fun setLockScreen(value: Boolean) {
        lockScreenState = value
    }

    fun setHostLabel(value: String) {
        hostLabelState = value
    }

    fun setShared(value: Boolean) {
        sharedState = value
    }

    fun setAudioAlreadyOn(value: Boolean) {
        audioAlreadyOnState = value
    }

    fun setVideoAlreadyOn(value: Boolean) {
        videoAlreadyOnState = value
    }

    fun setParticipants(value: List<Participant>) {
        participantsState = value
    }

    val currentShowAlert: ShowAlert?
        get() = showAlertState

    val currentLocalStreamAudio: MediaStream?
        get() = localStreamAudioState
}

/**
 * Full-featured implementation of [ConnectSendTransportVideoParameters] for tests.
 */
class TestConnectSendTransportVideoParameters(
    participants: List<Participant> = listOf(Participant(id = "host", name = "Host", islevel = "2")),
    socket: SocketManager? = null,
    localSocket: SocketManager? = null,
    initialVideoProducer: WebRtcProducer? = TestWebRtcProducer("video-producer", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.CAMERA),
    initialLocalVideoProducer: WebRtcProducer? = TestWebRtcProducer("local-video-producer", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.CAMERA),
    initialProducerTransport: WebRtcTransport? = TestWebRtcTransport("video-transport"),
    initialLocalProducerTransport: WebRtcTransport? = TestWebRtcTransport("local-video-transport"),
    hostLabel: String = "Host",
    eventType: EventType = EventType.CONFERENCE,
    webRtcDevice: WebRtcDevice? = TestWebRtcDevice()
) : TestPrepopulateUserMediaParameters(
    participants = participants,
    member = hostLabel,
    islevel = "2",
    eventType = eventType,
    socket = socket,
    localSocket = localSocket
) , ConnectSendTransportVideoParameters, ResumeSendTransportVideoParameters {

    private var localStreamState: MediaStream? = null
    private var lockScreenState: Boolean = false
    private var hostLabelState: String = hostLabel
    private val baseUpdateMainWindow = super.updateUpdateMainWindow

    override var transportCreated: Boolean = false
    override var localTransportCreated: Boolean = false
    override var producerTransport: WebRtcTransport? = initialProducerTransport
    override var localProducerTransport: WebRtcTransport? = initialLocalProducerTransport
    override var videoProducer: WebRtcProducer? = initialVideoProducer
    override var localVideoProducer: WebRtcProducer? = initialLocalVideoProducer

    override val device: WebRtcDevice? = webRtcDevice

    val videoProducerUpdates = mutableListOf<WebRtcProducer?>()
    val localVideoProducerUpdates = mutableListOf<WebRtcProducer?>()
    val producerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val localProducerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val localStreamUpdates = mutableListOf<MediaStream?>()
    val localStreamVideoUpdates = mutableListOf<MediaStream?>()
    val transportCreatedUpdates = mutableListOf<Boolean>()
    val localTransportCreatedUpdates = mutableListOf<Boolean>()
    val prepopulateCalls = mutableListOf<PrepopulateUserMediaOptions>()

    override val localStream: MediaStream?
        get() = localStreamState

    override val updateLocalStreamVideo: (MediaStream?) -> Unit = { stream ->
        localStreamVideoState = stream
        localStreamVideoUpdates += stream
    }

    override val updateLocalStream: (MediaStream?) -> Unit = { stream ->
        localStreamState = stream
        localStreamUpdates += stream
    }

    override val updateVideoProducer: (WebRtcProducer?) -> Unit = { producer ->
        videoProducer = producer
        videoProducerUpdates += producer
    }

    override val updateLocalVideoProducer: ((WebRtcProducer?) -> Unit)? = { producer ->
        localVideoProducer = producer
        localVideoProducerUpdates += producer
    }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit = { transport ->
        producerTransport = transport
        producerTransportUpdates += transport
    }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)? = { transport ->
        localProducerTransport = transport
        localProducerTransportUpdates += transport
    }

    override val updateTransportCreated: (Boolean) -> Unit = { created ->
        transportCreated = created
        transportCreatedUpdates += created
    }

    override val updateLocalTransportCreated: (Boolean) -> Unit = { created ->
        localTransportCreated = created
        localTransportCreatedUpdates += created
    }

    override val updateUpdateMainWindow: (Boolean) -> Unit = { state ->
        baseUpdateMainWindow(state)
    }

    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit = { options ->
        prepopulateCalls += options
    }

    override val hostLabel: String
        get() = hostLabelState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val audioAlreadyOn: Boolean
        get() = audioAlreadyOnState

    override fun getUpdatedAllParams(): ConnectSendTransportVideoParameters = this

    fun setLockScreen(value: Boolean) {
        lockScreenState = value
    }

    fun setAudioAlreadyOn(value: Boolean) {
        audioAlreadyOnState = value
    }

    fun setHostLabel(value: String) {
        hostLabelState = value
    }
}

/**
 * Test implementation of [ConnectSendTransportScreenParameters].
 */
class TestConnectSendTransportScreenParameters(
    initialScreenProducer: WebRtcProducer? = TestWebRtcProducer("screen-producer", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.SCREEN),
    initialLocalScreenProducer: WebRtcProducer? = TestWebRtcProducer("local-screen-producer", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.SCREEN),
    initialProducerTransport: WebRtcTransport? = TestWebRtcTransport("screen-transport"),
    initialLocalProducerTransport: WebRtcTransport? = TestWebRtcTransport("local-screen-transport"),
    screenParams: ProducerOptionsType? = ProducerOptionsType(),
    params: ProducerOptionsType? = ProducerOptionsType(),
    defScreenId: String = "",
    islevel: String = "2",
    showAlert: ShowAlert? = null
) : ConnectSendTransportScreenParameters {

    private var screenProducerState: WebRtcProducer? = initialScreenProducer
    private var localScreenProducerState: WebRtcProducer? = initialLocalScreenProducer
    private var producerTransportState: WebRtcTransport? = initialProducerTransport
    private var localProducerTransportState: WebRtcTransport? = initialLocalProducerTransport
    private var localStreamState: MediaStream? = null
    private var localStreamScreenState: MediaStream? = null
    private var screenParamsState: ProducerOptionsType? = screenParams
    private var paramsState: ProducerOptionsType? = params
    private var defScreenIdState: String = defScreenId
    private var islevelState: String = islevel
    private var updateMainWindowState: Boolean = false
    private var showAlertState: ShowAlert? = showAlert

    val screenProducerUpdates = mutableListOf<WebRtcProducer?>()
    val localScreenProducerUpdates = mutableListOf<WebRtcProducer?>()
    val producerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val localProducerTransportUpdates = mutableListOf<WebRtcTransport?>()
    val localStreamUpdates = mutableListOf<MediaStream?>()
    val localStreamScreenUpdates = mutableListOf<MediaStream?>()
    val updateMainWindowCalls = mutableListOf<Boolean>()
    val defScreenIdUpdates = mutableListOf<String>()
    val showAlertCalls = mutableListOf<Triple<String, String, Int>>()

    init {
        if (showAlertState == null) {
            showAlertState = ShowAlert { message, type, duration ->
                showAlertCalls += Triple(message, type, duration)
            }
        }
    }

    override val screenProducer: WebRtcProducer?
        get() = screenProducerState

    override val producerTransport: WebRtcTransport?
        get() = producerTransportState

    override val localScreenProducer: WebRtcProducer?
        get() = localScreenProducerState

    override val localProducerTransport: WebRtcTransport?
        get() = localProducerTransportState

    override val localStream: MediaStream?
        get() = localStreamState

    override val localStreamScreen: MediaStream?
        get() = localStreamScreenState

    override val screenParams: ProducerOptionsType?
        get() = screenParamsState

    override val params: ProducerOptionsType?
        get() = paramsState

    override val defScreenID: String
        get() = defScreenIdState

    override val islevel: String
        get() = islevelState

    override val updateMainWindow: Boolean
        get() = updateMainWindowState

    override val showAlert: ShowAlert?
        get() = showAlertState

    override val updateScreenProducer: (WebRtcProducer?) -> Unit = { producer ->
        screenProducerState = producer
        screenProducerUpdates += producer
    }

    override val updateLocalScreenProducer: ((WebRtcProducer?) -> Unit)? = { producer ->
        localScreenProducerState = producer
        localScreenProducerUpdates += producer
    }

    override val updateProducerTransport: (WebRtcTransport?) -> Unit = { transport ->
        producerTransportState = transport
        producerTransportUpdates += transport
    }

    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)? = { transport ->
        localProducerTransportState = transport
        localProducerTransportUpdates += transport
    }

    override val updateLocalStream: (MediaStream?) -> Unit = { stream ->
        localStreamState = stream
        localStreamUpdates += stream
    }

    override val updateLocalStreamScreen: (MediaStream?) -> Unit = { stream ->
        localStreamScreenState = stream
        localStreamScreenUpdates += stream
    }

    override val updateUpdateMainWindow: (Boolean) -> Unit = { state ->
        updateMainWindowState = state
        updateMainWindowCalls += state
    }

    override val updateDefScreenID: (String) -> Unit = { id ->
        defScreenIdState = id
        defScreenIdUpdates += id
    }

    override fun getUpdatedAllParams(): ConnectSendTransportScreenParameters = this

    fun setScreenParams(newParams: ProducerOptionsType?) {
        screenParamsState = newParams
    }

    fun setParams(newParams: ProducerOptionsType?) {
        paramsState = newParams
    }
}

/** Test implementation of [DisconnectSendTransportAudioParameters] with state tracking. */
class TestDisconnectSendTransportAudioParameters(
    initialAudioProducer: Any? = TestWebRtcProducer("remote-audio"),
    initialLocalAudioProducer: Any? = TestWebRtcProducer("local-audio"),
    initialSocket: SocketManager? = TestSocketManager("remote-socket"),
    initialLocalSocket: SocketManager? = TestSocketManager("local-socket"),
    videoAlreadyOn: Boolean = false,
    islevel: String = "1",
    lockScreen: Boolean = false,
    shared: Boolean = false,
    hostLabel: String = "Host",
    roomName: String = "testRoom"
) : DisconnectSendTransportAudioParameters {

    private var audioProducerState: Any? = initialAudioProducer
    private var socketState: SocketManager? = initialSocket
    private var localAudioProducerState: Any? = initialLocalAudioProducer
    private var localSocketState: SocketManager? = initialLocalSocket
    private var videoAlreadyOnState: Boolean = videoAlreadyOn
    private var islevelState: String = islevel
    private var lockScreenState: Boolean = lockScreen
    private var sharedState: Boolean = shared
    private var updateMainWindowState: Boolean = false
    private var hostLabelState: String = hostLabel
    private var roomNameState: String = roomName

    val audioProducerUpdates = mutableListOf<Any?>()
    val localAudioProducerUpdates = mutableListOf<Any?>()
    val updateMainWindowUpdates = mutableListOf<Boolean>()
    val prepopulateCalls = mutableListOf<Map<String, Any>>()

    override val audioProducer: Any?
        get() = audioProducerState

    override val socket: SocketManager?
        get() = socketState

    override val localAudioProducer: Any?
        get() = localAudioProducerState

    override val localSocket: SocketManager?
        get() = localSocketState

    override val videoAlreadyOn: Boolean
        get() = videoAlreadyOnState

    override val islevel: String
        get() = islevelState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val shared: Boolean
        get() = sharedState

    override val updateMainWindow: Boolean
        get() = updateMainWindowState

    override val hostLabel: String
        get() = hostLabelState

    override val roomName: String
        get() = roomNameState

    override fun updateAudioProducer(producer: WebRtcProducer?) {
        audioProducerState = producer
        audioProducerUpdates += producer
    }

    override fun updateLocalAudioProducer(producer: WebRtcProducer?) {
        localAudioProducerState = producer
        localAudioProducerUpdates += producer
    }

    override fun updateUpdateMainWindow(update: Boolean) {
        updateMainWindowState = update
        updateMainWindowUpdates += update
    }

    override suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit> {
        prepopulateCalls += options
        return Result.success(Unit)
    }

    override fun getUpdatedAllParams(): DisconnectSendTransportAudioParameters = this

    fun assignSocket(socket: SocketManager?) {
        socketState = socket
    }

    fun assignLocalSocket(socket: SocketManager?) {
        localSocketState = socket
    }

    fun assignVideoAlreadyOn(value: Boolean) {
        videoAlreadyOnState = value
    }

    fun assignIslevel(value: String) {
        islevelState = value
    }

    fun assignLockScreen(value: Boolean) {
        lockScreenState = value
    }

    fun assignShared(value: Boolean) {
        sharedState = value
    }

    fun assignHostLabel(value: String) {
        hostLabelState = value
    }

    fun assignRoomName(value: String) {
        roomNameState = value
    }
}

/** Test implementation of [DisconnectSendTransportVideoParameters] mirroring production state. */
class TestDisconnectSendTransportVideoParameters(
    initialVideoProducer: Any? = TestWebRtcProducer("remote-video", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.CAMERA),
    initialLocalVideoProducer: Any? = TestWebRtcProducer("local-video", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.CAMERA),
    initialSocket: SocketManager? = TestSocketManager("remote-video-socket"),
    initialLocalSocket: SocketManager? = TestSocketManager("local-video-socket"),
    islevel: String = "1",
    lockScreen: Boolean = false,
    shared: Boolean = false,
    hostLabel: String = "Host",
    roomName: String = "testRoom",
    audioAlreadyOn: Boolean = false
) : DisconnectSendTransportVideoParameters {

    private var videoProducerState: Any? = initialVideoProducer
    private var socketState: SocketManager? = initialSocket
    private var localVideoProducerState: Any? = initialLocalVideoProducer
    private var localSocketState: SocketManager? = initialLocalSocket
    private var islevelState: String = islevel
    private var lockScreenState: Boolean = lockScreen
    private var sharedState: Boolean = shared
    private var updateMainWindowState: Boolean = false
    private var hostLabelState: String = hostLabel
    private var roomNameState: String = roomName
    private var audioAlreadyOnState: Boolean = audioAlreadyOn

    val videoProducerUpdates = mutableListOf<Any?>()
    val localVideoProducerUpdates = mutableListOf<Any?>()
    val updateMainWindowUpdates = mutableListOf<Boolean>()
    val prepopulateCalls = mutableListOf<Map<String, Any>>()

    override val videoProducer: Any?
        get() = videoProducerState

    override val socket: SocketManager?
        get() = socketState

    override val localVideoProducer: Any?
        get() = localVideoProducerState

    override val localSocket: SocketManager?
        get() = localSocketState

    override val islevel: String
        get() = islevelState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val shared: Boolean
        get() = sharedState

    override val updateMainWindow: Boolean
        get() = updateMainWindowState

    override val hostLabel: String
        get() = hostLabelState

    override val roomName: String
        get() = roomNameState

    override val audioAlreadyOn: Boolean
        get() = audioAlreadyOnState

    override fun updateVideoProducer(producer: Any?) {
        videoProducerState = producer
        videoProducerUpdates += producer
    }

    override fun updateLocalVideoProducer(producer: Any?) {
        localVideoProducerState = producer
        localVideoProducerUpdates += producer
    }

    override fun updateUpdateMainWindow(update: Boolean) {
        updateMainWindowState = update
        updateMainWindowUpdates += update
    }

    override suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit> {
        prepopulateCalls += options
        return Result.success(Unit)
    }

    override fun getUpdatedAllParams(): DisconnectSendTransportVideoParameters = this

    fun assignSocket(socket: SocketManager?) {
        socketState = socket
    }

    fun assignLocalSocket(socket: SocketManager?) {
        localSocketState = socket
    }

    fun assignAudioAlreadyOn(value: Boolean) {
        audioAlreadyOnState = value
    }

    fun assignIslevel(value: String) {
        islevelState = value
    }

    fun assignLockScreen(value: Boolean) {
        lockScreenState = value
    }

    fun assignShared(value: Boolean) {
        sharedState = value
    }

    fun assignHostLabel(value: String) {
        hostLabelState = value
    }

    fun assignRoomName(value: String) {
        roomNameState = value
    }
}

/** Test implementation of [DisconnectSendTransportScreenParameters] with simple tracking. */
class TestDisconnectSendTransportScreenParameters(
    initialScreenProducer: Any? = TestWebRtcProducer("remote-screen", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.SCREEN),
    initialLocalScreenProducer: Any? = TestWebRtcProducer("local-screen", com.mediasfu.sdk.webrtc.MediaKind.VIDEO, ProducerSource.SCREEN),
    initialSocket: SocketManager? = TestSocketManager("remote-screen-socket"),
    initialLocalSocket: SocketManager? = TestSocketManager("local-screen-socket"),
    roomName: String = "testRoom"
) : DisconnectSendTransportScreenParameters {

    private var screenProducerState: Any? = initialScreenProducer
    private var socketState: SocketManager? = initialSocket
    private var localScreenProducerState: Any? = initialLocalScreenProducer
    private var localSocketState: SocketManager? = initialLocalSocket
    private var roomNameState: String = roomName

    val screenProducerUpdates = mutableListOf<Any?>()
    val localScreenProducerUpdates = mutableListOf<Any?>()

    override val screenProducer: Any?
        get() = screenProducerState

    override val socket: SocketManager?
        get() = socketState

    override val localScreenProducer: Any?
        get() = localScreenProducerState

    override val localSocket: SocketManager?
        get() = localSocketState

    override val roomName: String
        get() = roomNameState

    override fun updateScreenProducer(producer: Any?) {
        screenProducerState = producer
        screenProducerUpdates += producer
    }

    override fun updateLocalScreenProducer(producer: Any?) {
        localScreenProducerState = producer
        localScreenProducerUpdates += producer
    }

    override fun getUpdatedAllParams(): DisconnectSendTransportScreenParameters = this

    fun assignSocket(socket: SocketManager?) {
        socketState = socket
    }

    fun assignLocalSocket(socket: SocketManager?) {
        localSocketState = socket
    }

    fun assignRoomName(value: String) {
        roomNameState = value
    }
}

/** Comprehensive [ControlMediaHostParameters] implementation for tests. */
class TestControlMediaHostParameters(
    eventType: EventType = EventType.CONFERENCE
) : TestPrepopulateUserMediaParameters(eventType = eventType), ControlMediaHostParameters {

    private var localStreamState: MediaStream? = TestControllableMediaStream()
    private var shareEndedState: Boolean = false
    private var deferReceiveState: Boolean = false
    private var hostLabelState: String = "Host"
    private var lockScreenState: Boolean = false
    private var firstAllState: Boolean = false
    private var firstRoundState: Boolean = false
    private var prevForceFullDisplayState: Boolean = false
    private var adminVidIDState: String = ""
    private var addForBasicState: Boolean = false
    private var itemPageLimitState: Int = 0
    private var newLimitedStreamsState: List<Stream> = emptyList()
    private var newLimitedStreamsIDsState: List<String> = emptyList()
    private var activeSoundsState: List<String> = emptyList()
    private var screenShareIDStreamState: String = ""
    private var screenShareNameStreamState: String = ""
    private var adminIDStreamState: String = ""
    private var adminNameStreamState: String = ""
    private var youYouStreamState: List<Stream> = emptyList()
    private var youYouStreamIDsState: List<String> = emptyList()
    private var isScreenboardModalVisibleState: Boolean = false
    private var audioProducerState: Any? = null
    private var localAudioProducerState: Any? = null
    private var videoProducerState: Any? = null
    private var localVideoProducerState: Any? = null
    private var screenProducerState: Any? = null
    private var localScreenProducerState: Any? = null
    private var roomNameState: String = ""

    val adminRestrictUpdates = mutableListOf<Boolean>()
    val audioAlreadyOnUpdates = mutableListOf<Boolean>()
    val screenAlreadyOnUpdates = mutableListOf<Boolean>()
    val videoAlreadyOnUpdates = mutableListOf<Boolean>()
    val chatAlreadyOnUpdates = mutableListOf<Boolean>()
    val onScreenChangesCalls = mutableListOf<OnScreenChangesOptions>()
    val stopShareCalls = mutableListOf<StopShareScreenOptions>()
    val disconnectAudioOptions = mutableListOf<DisconnectSendTransportAudioOptions>()
    val disconnectVideoOptions = mutableListOf<DisconnectSendTransportVideoOptions>()
    val disconnectScreenOptions = mutableListOf<DisconnectSendTransportScreenOptions>()
    val audioProducerUpdates = mutableListOf<Any?>()
    val localAudioProducerUpdates = mutableListOf<Any?>()
    val videoProducerUpdates = mutableListOf<Any?>()
    val localVideoProducerUpdates = mutableListOf<Any?>()
    val screenProducerUpdates = mutableListOf<Any?>()
    val localScreenProducerUpdates = mutableListOf<Any?>()
    val reorderStreamsInvocations = mutableListOf<ReorderStreamsOptions>()
    val changeVidsInvocations = mutableListOf<ChangeVidsOptions>()
    val getVideosInvocations = mutableListOf<GetVideosOptions>()
    val prepopulateInvocations = mutableListOf<PrepopulateUserMediaOptions>()
    val prepopulateMapInvocations = mutableListOf<Map<String, Any>>()
    val updateAllVideoStreamsCalls = mutableListOf<List<Stream>>()
    val updateOldAllStreamsCalls = mutableListOf<List<Stream>>()
    val updateYouYouStreamCalls = mutableListOf<List<Stream>>()
    val updateYouYouStreamIDsCalls = mutableListOf<List<String>>()
    val updateAddForBasicCalls = mutableListOf<Boolean>()
    val updateItemPageLimitCalls = mutableListOf<Int>()
    val updateSharedCalls = mutableListOf<Boolean>()
    val updateShareScreenStartedCalls = mutableListOf<Boolean>()
    val updateShareEndedCalls = mutableListOf<Boolean>()
    val updateDeferReceiveCalls = mutableListOf<Boolean>()
    val updateLockScreenCalls = mutableListOf<Boolean>()
    val updateForceFullDisplayCalls = mutableListOf<Boolean>()
    val updateFirstAllCalls = mutableListOf<Boolean>()
    val updateFirstRoundCalls = mutableListOf<Boolean>()
    val updateAnnotateScreenStreamCalls = mutableListOf<Boolean>()
    val updateIsScreenboardModalVisibleCalls = mutableListOf<Boolean>()
    val updateMainWindowOverrideCalls = mutableListOf<Boolean>()

    val localStreamUpdates = mutableListOf<MediaStream?>()
    val localStreamVideoUpdates = mutableListOf<MediaStream?>()
    val localStreamScreenUpdates = mutableListOf<MediaStream?>()

    init {
        localStreamVideoState = TestControllableMediaStream(videoTracks = listOf(TestMediaStreamTrack(kind = "video")))
        localStreamScreenState = TestControllableMediaStream(videoTracks = listOf(TestMediaStreamTrack(kind = "video")))
    }

    override val screenProducer: Any?
        get() = screenProducerState

    override val localScreenProducer: Any?
        get() = localScreenProducerState

    override val roomName: String
        get() = roomNameState

    override val localStream: MediaStream?
        get() = localStreamState

    override val audioProducer: Any?
        get() = audioProducerState

    override val videoProducer: Any?
        get() = videoProducerState

    override val localVideoProducer: Any?
        get() = localVideoProducerState

    override val localAudioProducer: Any?
        get() = localAudioProducerState

    override val updateLocalStream: (MediaStream?) -> Unit = { stream ->
        localStreamState = stream
        localStreamUpdates += stream
    }

    override val updateLocalStreamVideo: (MediaStream?) -> Unit = { stream ->
        localStreamVideoState = stream
        localStreamVideoUpdates += stream
    }

    override val updateLocalStreamScreen: (MediaStream?) -> Unit = { stream ->
        localStreamScreenState = stream
        localStreamScreenUpdates += stream
    }

    override fun updateAudioProducer(producer: WebRtcProducer?) {
        audioProducerState = producer
        audioProducerUpdates += producer
    }

    override fun updateLocalAudioProducer(producer: WebRtcProducer?) {
        localAudioProducerState = producer
        localAudioProducerUpdates += producer
    }

    override fun updateVideoProducer(producer: WebRtcProducer?) {
        videoProducerState = producer
        videoProducerUpdates += producer
    }

    override fun updateLocalVideoProducer(producer: WebRtcProducer?) {
        localVideoProducerState = producer
        localVideoProducerUpdates += producer
    }

    override fun updateScreenProducer(producer: WebRtcProducer?) {
        screenProducerState = producer
        screenProducerUpdates += producer
    }

    override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
        localScreenProducerState = producer
        localScreenProducerUpdates += producer
    }

    override val updateAdminRestrictSetting: (Boolean) -> Unit = { value ->
        adminRestrictUpdates += value
    }

    override val updateAudioAlreadyOn: (Boolean) -> Unit = { value ->
        audioAlreadyOnState = value
        audioAlreadyOnUpdates += value
    }

    override val updateScreenAlreadyOn: (Boolean) -> Unit = { value ->
        screenAlreadyOnUpdates += value
    }

    override val updateVideoAlreadyOn: (Boolean) -> Unit = { value ->
        videoAlreadyOnState = value
        videoAlreadyOnUpdates += value
    }

    override val updateChatAlreadyOn: (Boolean) -> Unit = { value ->
        chatAlreadyOnUpdates += value
    }

    override fun updateUpdateMainWindow(update: Boolean) {
        updateMainWindowOverrideCalls += update
        super.updateUpdateMainWindow.invoke(update)
    }

    override val onScreenChanges: OnScreenChangesType = { options ->
        onScreenChangesCalls += options
    }

    override val stopShareScreen: StopShareScreenType = { options ->
        stopShareCalls += options
    }

    override val disconnectSendTransportVideo: DisconnectSendTransportVideoType = { options ->
        disconnectVideoOptions += options
    }

    override val disconnectSendTransportAudio: DisconnectSendTransportAudioType = { options ->
        disconnectAudioOptions += options
    }

    override val disconnectSendTransportScreen: DisconnectSendTransportScreenType = { options ->
        disconnectScreenOptions += options
    }

    override fun getUpdatedAllParams(): ControlMediaHostParameters = this

    override val participants: List<Participant>
        get() = participantsState

    override val allVideoStreams: List<Stream>
        get() = allVideoStreamsState

    override val oldAllStreams: List<Stream>
        get() = oldAllStreamsState

    override val screenId: String
        get() = screenIdState ?: ""

    override val adminVidID: String
        get() = adminVidIDState

    override val newLimitedStreams: List<Stream>
        get() = newLimitedStreamsState

    override val newLimitedStreamsIDs: List<String>
        get() = newLimitedStreamsIDsState

    override val activeSounds: List<String>
        get() = activeSoundsState

    override val screenShareIDStream: String
        get() = screenShareIDStreamState

    override val screenShareNameStream: String
        get() = screenShareNameStreamState

    override val adminIDStream: String
        get() = adminIDStreamState

    override val adminNameStream: String
        get() = adminNameStreamState

    override fun updateAllVideoStreams(streams: List<Stream>) {
        allVideoStreamsState = streams
        updateAllVideoStreamsCalls += streams
    }

    override fun updateParticipants(participants: List<Participant>) {
        participantsState = participants
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        oldAllStreamsState = streams
        updateOldAllStreamsCalls += streams
    }

    override fun updateScreenId(id: String) {
        screenIdState = id
    }

    override fun updateAdminVidID(id: String) {
        adminVidIDState = id
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        newLimitedStreamsState = streams
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        newLimitedStreamsIDsState = ids
    }

    override fun updateActiveSounds(sounds: List<String>) {
        activeSoundsState = sounds
    }

    override fun updateScreenShareIDStream(id: String) {
        screenShareIDStreamState = id
    }

    override fun updateScreenShareNameStream(name: String) {
        screenShareNameStreamState = name
    }

    override fun updateAdminIDStream(id: String) {
        adminIDStreamState = id
    }

    override fun updateAdminNameStream(name: String) {
        adminNameStreamState = name
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        youYouStreamState = streams
        updateYouYouStreamCalls += streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        youYouStreamIDsState = ids
        updateYouYouStreamIDsCalls += ids
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> {
        changeVidsInvocations += options
        return Result.success(Unit)
    }

    override val addForBasic: Boolean
        get() = addForBasicState

    override val updateAddForBasic: (Boolean) -> Unit = { value ->
        addForBasicState = value
        updateAddForBasicCalls += value
    }

    override val itemPageLimit: Int
        get() = itemPageLimitState

    override val updateItemPageLimit: (Int) -> Unit = { value ->
        itemPageLimitState = value
        updateItemPageLimitCalls += value
    }

    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit = { options ->
        reorderStreamsInvocations += options
    }

    override val updateShared: (Boolean) -> Unit = { value ->
        sharedState = value
        updateSharedCalls += value
    }

    override val updateShareScreenStarted: (Boolean) -> Unit = { value ->
        shareScreenStartedState = value
        updateShareScreenStartedCalls += value
    }

    override val updateShareEnded: (Boolean) -> Unit = { value ->
        shareEndedState = value
        updateShareEndedCalls += value
    }

    override val updateDeferReceive: (Boolean) -> Unit = { value ->
        deferReceiveState = value
        updateDeferReceiveCalls += value
    }

    override val updateLockScreen: (Boolean) -> Unit = { value ->
        lockScreenState = value
        updateLockScreenCalls += value
    }

    override val updateForceFullDisplay: (Boolean) -> Unit = { value ->
        forceFullDisplayState = value
        updateForceFullDisplayCalls += value
    }

    override val updateFirstAll: (Boolean) -> Unit = { value ->
        firstAllState = value
        updateFirstAllCalls += value
    }

    override val updateFirstRound: (Boolean) -> Unit = { value ->
        firstRoundState = value
        updateFirstRoundCalls += value
    }

    override val updateAnnotateScreenStream: (Boolean) -> Unit = { value ->
        annotateScreenStreamState = value
        updateAnnotateScreenStreamCalls += value
    }

    override val updateIsScreenboardModalVisible: (Boolean) -> Unit = { value ->
        isScreenboardModalVisibleState = value
        updateIsScreenboardModalVisibleCalls += value
    }

    override val updateAllVideoStreams: (List<Stream>) -> Unit = { streams ->
        updateAllVideoStreams(streams)
    }

    override val updateOldAllStreams: (List<Stream>) -> Unit = { streams ->
        updateOldAllStreams(streams)
    }

    override val prepopulateUserMedia: com.mediasfu.sdk.model.PrepopulateUserMediaType =
        { options: PrepopulateUserMediaOptions ->
            prepopulateInvocations += options
        }

    override suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit> {
        prepopulateMapInvocations += options
        return Result.success(Unit)
    }

    override val getVideos: suspend (GetVideosOptions) -> Unit = { options ->
        getVideosInvocations += options
    }

    override val shareEnded: Boolean
        get() = shareEndedState

    override val deferReceive: Boolean
        get() = deferReceiveState

    override val hostLabel: String
        get() = hostLabelState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val firstAll: Boolean
        get() = firstAllState

    override val firstRound: Boolean
        get() = firstRoundState

    override val prevForceFullDisplay: Boolean
        get() = prevForceFullDisplayState

    fun assignLocalStream(stream: MediaStream?) {
        localStreamState = stream
    }

    fun assignLocalVideoStream(stream: MediaStream?) {
        localStreamVideoState = stream
    }

    fun assignLocalScreenStream(stream: MediaStream?) {
        localStreamScreenState = stream
    }

    fun assignScreenProducer(producer: Any?) {
        screenProducerState = producer
    }

    fun assignLocalScreenProducer(producer: Any?) {
        localScreenProducerState = producer
    }

    fun assignRoomName(value: String) {
        roomNameState = value
    }

    fun assignHostLabel(label: String) {
        hostLabelState = label
    }

    fun assignLockScreen(value: Boolean) {
        lockScreenState = value
    }

    fun assignPrevForceFullDisplay(value: Boolean) {
        prevForceFullDisplayState = value
    }

    fun assignAdminVidID(value: String) {
        adminVidIDState = value
    }

    fun assignAddForBasic(value: Boolean) {
        addForBasicState = value
    }

    fun assignItemPageLimit(value: Int) {
        itemPageLimitState = value
    }

    fun assignDeferReceive(value: Boolean) {
        deferReceiveState = value
    }

    fun assignFirstAll(value: Boolean) {
        firstAllState = value
    }

    fun assignFirstRound(value: Boolean) {
        firstRoundState = value
    }

    fun assignShareEnded(value: Boolean) {
        shareEndedState = value
    }

    fun assignIsScreenboardModalVisible(value: Boolean) {
        isScreenboardModalVisibleState = value
    }
}
