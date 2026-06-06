// SignalNewConsumerTransport.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.util.MediaSFURuntimeProbe
import com.mediasfu.sdk.util.toStringAnyMap

import com.mediasfu.sdk.methods.utils.mini_audio_player.MiniAudioPlayerParameters
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.ReUpdateInterType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.*
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom exception for signal new consumer transport operations.
 */
class SignalNewConsumerTransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Minimal stub implementation of ConsumerResumeParameters for cases where
 * consumer resume is not needed (e.g., test stubs).
 */
internal class StubConsumerResumeParameters : ConsumerResumeParameters {
    // ConsumerResumeParameters properties
    override val nStream: MediaStream? = null
    override val allAudioStreams = emptyList<Stream>()
    override val allVideoStreams = emptyList<Stream>()
    override val streamNames = emptyList<Stream>()
    override val audStreamNames = emptyList<Stream>()
    override val updateMainWindow = false
    override val shared = false
    override val shareScreenStarted = false
    override val screenId: String? = null
    override val participants = emptyList<Participant>()
    override val eventType = EventType.BROADCAST
    override val meetingDisplayType = ""
    override val mainScreenFilled = false
    override val firstRound = false
    override val lockScreen = false
    override val oldAllStreams = emptyList<Stream>()
    override val adminVidID = ""
    override val mainHeightWidth = 0.0
    override val member = ""
    override val audioOnlyStreams = emptyList<Any>()
    override val gotAllVids = false
    override val deferReceive = false
    override val firstAll = false
    override val remoteScreenStream = emptyList<Stream>()
    override val hostLabel = ""
    override val whiteboardStarted = false
    override val whiteboardEnded = false
    override val islevel = "0"
    override val forceFullDisplay = false
    
    // PrepopulateUserMediaParameters properties
    override val socket: SocketManager? = null
    override val localSocket: SocketManager? = null
    override val adminOnMainScreen = false
    override val mainScreenPerson = ""
    override val videoAlreadyOn = false
    override val audioAlreadyOn = false
    override val screenForceFullDisplay = false
    override val localStreamScreen: MediaStream? = null
    override val localStreamVideo: MediaStream? = null
    override val isWideScreen = false
    override val localUIMode = false
    override val virtualStream: Any? = null
    override val keepBackground = false
    override val annotateScreenStream = false
    override val audioDecibels: List<AudioDecibels> = emptyList()
    
    // Update functions
    override val updateUpdateMainWindow: (Boolean) -> Unit = {}
    override val updateAllAudioStreams: (List<Stream>) -> Unit = {}
    override val updateAllVideoStreams: (List<Stream>) -> Unit = {}
    override val updateStreamNames: (List<Stream>) -> Unit = {}
    override val updateAudStreamNames: (List<Stream>) -> Unit = {}
    override val updateNStream: (MediaStream?) -> Unit = {}
    override val updateMainHeightWidth: (Double) -> Unit = {}
    override val updateLockScreen: (Boolean) -> Unit = {}
    override val updateFirstAll: (Boolean) -> Unit = {}
    override val updateRemoteScreenStream: (List<Stream>) -> Unit = {}
    override val updateOldAllStreams: (List<Stream>) -> Unit = {}
    override val updateAudioOnlyStreams: (List<Any>) -> Unit = {}
    override val updateShareScreenStarted: (Boolean) -> Unit = {}
    override val updateGotAllVids: (Boolean) -> Unit = {}
    override val updateScreenId: (String) -> Unit = {}
    override val updateDeferReceive: (Boolean) -> Unit = {}
    override val updateMainScreenPerson: (String) -> Unit = {}
    override val updateMainScreenFilled: (Boolean) -> Unit = {}
    override val updateAdminOnMainScreen: (Boolean) -> Unit = {}
    override val updateScreenForceFullDisplay: (Boolean) -> Unit = {}
    override val updateShowAlert: (com.mediasfu.sdk.model.ShowAlert?) -> Unit = {}
    override val updateMainGridStream: (List<com.mediasfu.sdk.ui.MediaSfuUIComponent>) -> Unit = {}
    
    // Mediasfu functions
    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit = {}
    override val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit = {}

    override fun asReorderStreamsParameters(): ReorderStreamsParameters = StubReorderStreamsParameters

    override fun asMiniAudioPlayerParameters(): MiniAudioPlayerParameters = StubMiniAudioPlayerParameters
    
    override fun getUpdatedAllParams() = this
}

private object StubReorderStreamsParameters : ReorderStreamsParameters {
    override val allVideoStreams: List<Stream> = emptyList()
    override val participants: List<Participant> = emptyList()
    override val oldAllStreams: List<Stream> = emptyList()
    override val screenId: String = ""
    override val adminVidID: String = ""
    override val newLimitedStreams: List<Stream> = emptyList()
    override val newLimitedStreamsIDs: List<String> = emptyList()
    override val activeSounds: List<String> = emptyList()
    override val screenShareIDStream: String = ""
    override val screenShareNameStream: String = ""
    override val adminIDStream: String = ""
    override val adminNameStream: String = ""
    override fun updateAllVideoStreams(streams: List<Stream>) {}
    override fun updateParticipants(participants: List<Participant>) {}
    override fun updateOldAllStreams(streams: List<Stream>) {}
    override fun updateScreenId(id: String) {}
    override fun updateAdminVidID(id: String) {}
    override fun updateNewLimitedStreams(streams: List<Stream>) {}
    override fun updateNewLimitedStreamsIDs(ids: List<String>) {}
    override fun updateActiveSounds(sounds: List<String>) {}
    override fun updateScreenShareIDStream(id: String) {}
    override fun updateScreenShareNameStream(name: String) {}
    override fun updateAdminIDStream(id: String) {}
    override fun updateAdminNameStream(name: String) {}
    override fun updateYouYouStream(streams: List<Stream>) {}
    override fun updateYouYouStreamIDs(ids: List<String>) {}
    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> = Result.success(Unit)
    override fun getUpdatedAllParams(): ReorderStreamsParameters = this
}

private object StubMiniAudioPlayerParameters : MiniAudioPlayerParameters {
    override val breakOutRoomStarted: Boolean = false
    override val breakOutRoomEnded: Boolean = false
    override val limitedBreakRoom: List<BreakoutParticipant> = emptyList()
    override val autoWave: Boolean = false
    override val meetingDisplayType: String = ""
    override val dispActiveNames: List<String> = emptyList()
    override val paginatedStreams: List<List<Stream>> = emptyList()
    override val currentUserPage: Int = 0
    override val audioDecibels: List<AudioDecibels> = emptyList()

    override val screenPageLimit: Int = 0
    override val itemPageLimit: Int = 0
    override val reorderInterval: Int = 0
    override val fastReorderInterval: Int = 0
    override val eventType: EventType = EventType.BROADCAST
    override val participants: List<Participant> = emptyList()
    override val allVideoStreams: List<Stream> = emptyList()
    override val shared: Boolean = false
    override val shareScreenStarted: Boolean = false
    override val adminNameStream: String = ""
    override val screenShareNameStream: String = ""
    override val updateMainWindow: Boolean = false
    override val sortAudioLoudness: Boolean = false
    override val lastReorderTime: Int = 0
    override val newLimitedStreams: List<Stream> = emptyList()
    override val newLimitedStreamsIDs: List<String> = emptyList()
    override val oldSoundIds: List<String> = emptyList()
    override val addForBasic: Boolean = false
    override val screenId: String = ""
    override val adminVidID: String = ""
    override val activeSounds: List<String> = emptyList()
    override val screenShareIDStream: String = ""
    override val adminIDStream: String = ""
    override val oldAllStreams: List<Stream> = emptyList()

    override val updateUpdateMainWindow: (Boolean) -> Unit = {}
    override val updateSortAudioLoudness: (Boolean) -> Unit = {}
    override val updateLastReorderTime: (Int) -> Unit = {}
    override val updateNewLimitedStreams: (List<Stream>) -> Unit
        get() = { streams -> updateNewLimitedStreams(streams) }
    override val updateNewLimitedStreamsIDs: (List<String>) -> Unit
        get() = { ids -> updateNewLimitedStreamsIDs(ids) }
    override val updateOldSoundIds: (List<String>) -> Unit = {}
    override val updateMainHeightWidth: (Double) -> Unit = {}
    override val updateAddForBasic: (Boolean) -> Unit = {}
    override val updateItemPageLimit: (Int) -> Unit = {}
    override val updateActiveSounds: (List<String>) -> Unit
        get() = { sounds -> updateActiveSounds(sounds) }

    override val reUpdateInter: ReUpdateInterType = { _ -> }
    override val updateParticipantAudioDecibels: UpdateParticipantAudioDecibelsType = {}
    override val updateAudioDecibels: (List<AudioDecibels>) -> Unit = {}
    override val onScreenChanges: suspend (OnScreenChangesOptions) -> Unit = {}
    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit = {}
    override val changeVids: suspend (Any) -> Unit = {}

    override fun updateAllVideoStreams(streams: List<Stream>) {}
    override fun updateParticipants(participants: List<Participant>) {}
    override fun updateOldAllStreams(streams: List<Stream>) {}
    override fun updateScreenId(id: String) {}
    override fun updateAdminVidID(id: String) {}
    override fun updateNewLimitedStreams(streams: List<Stream>) {}
    override fun updateNewLimitedStreamsIDs(ids: List<String>) {}
    override fun updateActiveSounds(sounds: List<String>) {}
    override fun updateScreenShareIDStream(id: String) {}
    override fun updateScreenShareNameStream(name: String) {}
    override fun updateAdminIDStream(id: String) {}
    override fun updateAdminNameStream(name: String) {}
    override fun updateYouYouStream(streams: List<Stream>) {}
    override fun updateYouYouStreamIDs(ids: List<String>) {}

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> = Result.success(Unit)

    override fun getUpdatedAllParams(): MiniAudioPlayerParameters = this
}

/**
 * Parameters required for signaling a new consumer transport.
 * Includes consumer resume functionality for post-consume flow.
 */
interface SignalNewConsumerTransportParameters {
    /** List of currently consuming transport IDs */
    val consumingTransports: List<String>
    
    /** List of consumer transport info objects for tracking active consumers */
    val consumerTransports: List<ConsumerTransportInfo>
    
    /** Whether screen is locked */
    val lockScreen: Boolean
    
    /** Mediasoup device instance */
    val device: WebRtcDevice?
    
    /** RTP capabilities loaded from device */
    val rtpCapabilities: RtpCapabilities?

    /** Router capabilities received from the server (pre-load snapshot) */
    val routerRtpCapabilities: RtpCapabilities?
        get() = rtpCapabilities

    /**
     * Precomputed receive RTP capabilities derived from ORTC negotiation.
     * When available we can skip recomputing for each consume call.
     */
    val negotiatedRecvRtpCapabilities: RtpCapabilities?
    
    /** Updates the consuming transports list */
    fun updateConsumingTransports(transports: List<String>)
    
    /** Updates the consumer transports list with ConsumerTransportInfo objects */
    val updateConsumerTransports: (List<ConsumerTransportInfo>) -> Unit
    
    /** Consumer resume function for updating UI after consume */
    val consumerResume: suspend (ConsumerResumeOptions) -> Unit
    
    /** Provider function for consumer resume parameters */
    val consumerResumeParamsProvider: () -> ConsumerResumeParameters
    
    /** Reorder streams function for updating UI layout */
    val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    
    /** Returns updated parameters */
    fun getUpdatedAllParams(): SignalNewConsumerTransportParameters
}

/**
 * Response from consume socket event.
 */
data class ConsumeResponse(
    val id: String,
    val producerId: String,
    val kind: String,
    val rtpParameters: Map<String, Any>,
    val serverConsumerId: String = "" // Server consumer ID, defaults to empty if not provided
)

/**
 * Options for signaling a new consumer transport.
 */
data class SignalNewConsumerTransportOptions(
    val producerId: String,
    val islevel: String,
    val socket: SocketManager,
    val parameters: SignalNewConsumerTransportParameters
)

/**
 * Options for reordering streams in SignalNewConsumerTransport.
 */
data class SignalNewConsumerTransportReorderStreamsOptions(
    val streams: List<Any>,
    val parameters: SignalNewConsumerTransportParameters
)

/**
 * Options for getting videos in SignalNewConsumerTransport.
 */
data class SignalNewConsumerTransportGetVideosOptions(
    val parameters: SignalNewConsumerTransportParameters
)

/**
 * Signals a new consumer transport to start consuming media from a producer.
 *
 * This function handles the signaling process for establishing a new consumer transport
 * to receive media from a remote producer. It communicates with the server to set up
 * the consumer and transport.
 *
 * ## Features:
 * - Consumer transport signaling
 * - Server communication
 * - Transport setup
 * - Error handling
 *
 * ## Parameters:
 * - [options] Configuration options for signaling new consumer transport
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = SignalNewConsumerTransportOptions(
 *     producerId = "producer-123",
 *     socket = mySocket,
 *     parameters = myParameters
 * )
 *
 * val result = signalNewConsumerTransport(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("SignalNewConsumerTra", "Error signaling new consumer transport: ${error.message}")
 * }
 * ```
 */
suspend fun signalNewConsumerTransport(
    options: SignalNewConsumerTransportOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val socket = options.socket
        val producerId = options.producerId

        MediaSFURuntimeProbe.recordConsumerSignalStage(
            "entered",
            producerId,
            "level=${options.islevel}"
        )
        Logger.i(
            "SignalNewConsumerTransport",
            "entered producerId=$producerId level=${options.islevel} consumingAlready=${parameters.consumingTransports.contains(producerId)}"
        )
        
        // Validate required parameters
        val device = parameters.device
        if (device == null) {
            MediaSFURuntimeProbe.recordConsumerSignalStage("device-null", producerId, "")
            return Result.failure(
                SignalNewConsumerTransportException("Device is null")
            )
        }

        // Get RTP capabilities from parameters (must be set when device is loaded)
        val deviceRtpCapabilities = device.currentRtpCapabilities()
        val baseRtpCapabilities = deviceRtpCapabilities
            ?: parameters.rtpCapabilities
            ?: parameters.routerRtpCapabilities

        if (baseRtpCapabilities == null) {
            MediaSFURuntimeProbe.recordConsumerSignalStage("rtp-null", producerId, "")
            return Result.failure(
                SignalNewConsumerTransportException("RTP capabilities not loaded")
            )
        }

        val cachedNegotiatedCaps = parameters.negotiatedRecvRtpCapabilities

        var computedExtendedCaps: OrtcUtils.ExtendedRtpCapabilities? = null
        val freshlyNegotiatedCaps = if (cachedNegotiatedCaps == null) {
            parameters.routerRtpCapabilities?.let { routerCaps ->
                runCatching {
                    val extended = OrtcUtils.getExtendedRtpCapabilities(
                        localCaps = baseRtpCapabilities,
                        remoteCaps = routerCaps
                    )
                    computedExtendedCaps = extended
                    OrtcUtils.getRecvRtpCapabilities(extended)
                }.getOrNull()
            }
        } else {
            null
        }

        val rtpCapabilities = (deviceRtpCapabilities ?: cachedNegotiatedCaps ?: freshlyNegotiatedCaps ?: baseRtpCapabilities)
            .applyCurrentPlatformHeaderExtensionPolicy()
        
        // Check if already consuming this producer
        if (parameters.consumingTransports.contains(producerId)) {
            return Result.success(Unit)
        }
        
        // Add to consuming transports
        val updatedTransports = parameters.consumingTransports + producerId
        parameters.updateConsumingTransports(updatedTransports)
        
        // STEP 1: Create WebRTC transport
        val createTransportPayload = mapOf(
            "consumer" to true,
            "islevel" to options.islevel
        )
        
        val createTransportResult = runCatching {
            withTimeout(30000) {
                socket.emitWithAck<Map<String, Any?>>("createWebRtcTransport", createTransportPayload, timeout = 30000)
            }
        }

        val transportParams = createTransportResult.fold(
            onSuccess = { response ->
                    val params = response["params"].toStringAnyMap()
                    if (params.isEmpty()) {
                    MediaSFURuntimeProbe.recordConsumerSignalStage("transport-fail", producerId, "empty-params")
                    return Result.failure(SignalNewConsumerTransportException("createWebRtcTransport returned null params"))
                }
                
                if (params.containsKey("error")) {
                    val error = params["error"]
                    MediaSFURuntimeProbe.recordConsumerSignalStage("transport-fail", producerId, "$error")
                    return Result.failure(SignalNewConsumerTransportException("createWebRtcTransport error: $error"))
                }
                
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "transport-ok",
                    producerId,
                    (params["id"] as? String).orEmpty()
                )
                Logger.i(
                    "SignalNewConsumerTransport",
                    "transport-ok producerId=$producerId serverConsumerTransportId=${params["id"] as? String ?: ""}"
                )
                params
            },
            onFailure = { error ->
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "transport-fail",
                    producerId,
                    error.toProbeDetail()
                )
                Logger.e(
                    "SignalNewConsumerTransport",
                    "transport-fail producerId=$producerId error=${error.message}"
                )
                return Result.failure(SignalNewConsumerTransportException("createWebRtcTransport failed", error))
            }
        )
        
        val serverConsumerTransportId = transportParams["id"] as? String
        if (serverConsumerTransportId == null) {
            return Result.failure(SignalNewConsumerTransportException("No transport ID"))
        }
        
        val rtpCapsMap = rtpCapabilities.toMap()
        Logger.i(
            "SignalNewConsumerTransport",
            "consume-capabilities producerId=$producerId source=${if (deviceRtpCapabilities != null) "device" else if (cachedNegotiatedCaps != null) "cached-negotiated" else if (freshlyNegotiatedCaps != null) "fresh-negotiated" else "base"} codecs=${rtpCapabilities.codecDebugSummary()} headerExts=${rtpCapabilities.headerExtensions.size}"
        )
        
        // === CODEC DEBUG: Log client RTP capabilities being sent ===
        val videoCodecs = rtpCapabilities.codecs.filter { it.mimeType.startsWith("video/", ignoreCase = true) }
        videoCodecs.forEach { codec ->
            codec.parameters?.let { params ->
            }
        }
        
        // STEP 2: Emit consume event to server with ALL THREE required fields
        val consumePayload = mapOf(
            "rtpCapabilities" to rtpCapsMap,
            "remoteProducerId" to producerId,
            "serverConsumerTransportId" to serverConsumerTransportId
        )
        
        val consumeResult = runCatching {
            withTimeout(30000) {
                socket.emitWithAck<Map<String, Any?>>("consume", consumePayload, timeout = 30000)
            }
        }

        consumeResult.fold(
            onSuccess = { response ->
                // Check if response contains error (server returns {params={error={}}} on failure)
                val params = response["params"].toStringAnyMap()
                if (params.isNotEmpty()) {
                    // If params contains "error" key at all, it's an error response (even if empty {})
                    if (params.containsKey("error")) {
                        val error = params["error"] as? Map<*, *>
                        val errorMsg = error?.get("message") as? String ?: "Server returned error: ${error ?: "{}"}"
                        return@fold Result.failure(SignalNewConsumerTransportException("Server consume error: $errorMsg"))
                    }
                }
                
                // Parse successful response - extract from params if nested
                val dataMap = if (params.isNotEmpty()) params else response.toStringAnyMap()
                val consumeResponse = parseConsumeResponse(dataMap)
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "consume-ok",
                    producerId,
                    consumeResponse.kind
                )
                Logger.i(
                    "SignalNewConsumerTransport",
                    "consume-ok producerId=$producerId consumerId=${consumeResponse.id} kind=${consumeResponse.kind} rtp=${consumeResponse.rtpDebugSummary()}"
                )
                
                // === CODEC DEBUG: Log consume response from server ===
                val rtpParams = consumeResponse.rtpParameters
                @Suppress("UNCHECKED_CAST")
                val codecs = (rtpParams["codecs"] as? List<Map<String, Any?>>)
                codecs?.forEachIndexed { i, codec ->
                    val codecParams = codec["parameters"]
                    if (codecParams != null) {
                    }
                }
                @Suppress("UNCHECKED_CAST")
                val encodings = (rtpParams["encodings"] as? List<Map<String, Any?>>)
                encodings?.forEachIndexed { i, enc ->
                }
                
                // Validate required fields
                if (consumeResponse.id.isEmpty() || consumeResponse.kind.isEmpty()) {
                    return@fold Result.failure(SignalNewConsumerTransportException("Invalid consume response: missing id or kind"))
                }
                
                // STEP 3: Create client-side recv transport from STEP 1 params
                val clientTransport = runCatching {
                    device.createRecvTransport(transportParams.toStringAnyMap())
                }.getOrElse { error ->
                    MediaSFURuntimeProbe.recordConsumerSignalStage(
                        "client-transport-fail",
                        producerId,
                        error.toProbeDetail()
                    )
                    return@fold Result.failure(SignalNewConsumerTransportException("Failed to create client transport", error))
                }
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "client-transport-ok",
                    producerId,
                    clientTransport.id
                )
                Logger.i(
                    "SignalNewConsumerTransport",
                    "client-transport-ok producerId=$producerId clientTransportId=${clientTransport.id}"
                )
                
                // Set up transport connect handler (following Flutter/React pattern)
                clientTransport.onConnect { connectData ->
                    try {
                        val dtlsParameters = connectData.dtlsParameters
                        connectData.callback()
                        CoroutineScope(Dispatchers.Default).launch {
                            val payload = mapOf(
                                "dtlsParameters" to dtlsParameters.toMap(),
                                "serverConsumerTransportId" to serverConsumerTransportId
                            )
                            runCatching { socket.emit("transport-recv-connect", payload) }
                                .onFailure { error ->
                                    Logger.w(
                                        "SignalNewConsumerTransport",
                                        "transport-recv-connect emit failed producerId=$producerId transportId=$serverConsumerTransportId: ${error.message}"
                                    )
                                }
                        }
                    } catch (error: Throwable) {
                        val exception = error as? Exception
                            ?: Exception(
                                error.message ?: "Failed to connect consumer transport",
                                error
                            )
                        connectData.errback(exception)
                    }
                }
                
                // Set up connection state change handler
                clientTransport.onConnectionStateChange { state ->
                    if (state == "failed") {
                        runCatching { clientTransport.close() }
                    }
                }
                
                // STEP 4: Create consumer from consume response (following React pattern)
                val consumer = runCatching {
                    withContext(Dispatchers.Default) {
                        clientTransport.consume(
                            id = consumeResponse.id,
                            producerId = consumeResponse.producerId,
                            kind = consumeResponse.kind,
                            rtpParameters = consumeResponse.rtpParameters
                        )
                    }
                }.getOrElse { error ->
                    MediaSFURuntimeProbe.recordConsumerSignalStage(
                        "consumer-create-fail",
                        producerId,
                        error.toProbeDetail()
                    )
                    return@fold Result.failure(SignalNewConsumerTransportException("Failed to create consumer", error))
                }
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "consumer-created",
                    producerId,
                    consumer.id
                )
                Logger.i(
                    "SignalNewConsumerTransport",
                    "consumer-created producerId=$producerId consumerId=${consumer.id} kind=${consumeResponse.kind}"
                )
                
                // STEP 4.5: Add to consumerTransports list for tracking
                // This is critical for ProducerClosed to find and clean up this consumer later
                val newTransportInfo = ConsumerTransportInfo(
                    consumerTransport = clientTransport,
                    serverConsumerTransportId = serverConsumerTransportId,
                    producerId = producerId,
                    consumer = consumer,
                    socket = socket
                )
                val updatedConsumerTransports = parameters.consumerTransports.toMutableList()
                updatedConsumerTransports.removeAll { existing ->
                    existing.serverConsumerTransportId == serverConsumerTransportId ||
                        existing.producerId == producerId
                }
                updatedConsumerTransports.add(newTransportInfo)
                parameters.updateConsumerTransports(updatedConsumerTransports)

                // STEP 5: Emit consumer-resume after consume succeeds
                val serverConsumerId = consumeResponse.serverConsumerId.ifEmpty { consumeResponse.id }

                val resumeResponse = try {
                    withTimeout(30000) {
                        socket.emitWithAck<Any?>(
                            event = "consumer-resume",
                            data = mapOf("serverConsumerId" to serverConsumerId),
                            timeout = 30000
                        )
                    }
                } catch (_: Exception) {
                    runCatching {
                        socket.emit(
                            "consumer-resume",
                            mapOf("serverConsumerId" to serverConsumerId)
                        )
                    }
                    null
                }

                val resumed = resumeResponse.resumeAckSucceeded(defaultOnNull = true)
                if (resumed) {
                    MediaSFURuntimeProbe.recordConsumerSignalStage(
                        "resume-ack-ok",
                        producerId,
                        serverConsumerId
                    )
                    Logger.i(
                        "SignalNewConsumerTransport",
                        "resume-ack-ok producerId=$producerId serverConsumerId=$serverConsumerId"
                    )
                    runCatching { consumer.resume() }

                    // Consumer resumed successfully - now call consumerResume to update UI
                    // Get FRESH consumer resume parameters right before calling consumerResume
                    // This ensures we have the latest participants list populated by allMembersRest
                    val consumerResumeParams = parameters.consumerResumeParamsProvider().getUpdatedAllParams()

                    // Call consumerResume (this updates UI and media handling)
                    val resumeOptions = ConsumerResumeOptions(
                        stream = consumer.stream,
                        consumer = consumer,
                        kind = consumeResponse.kind,
                        remoteProducerId = producerId,
                        parameters = consumerResumeParams,
                        nsock = socket
                    )

                    runCatching {
                        parameters.consumerResume(resumeOptions)
                    }
                } else {
                    MediaSFURuntimeProbe.recordConsumerSignalStage(
                        "resume-ack-fail",
                        producerId,
                        serverConsumerId
                    )
                    Logger.w(
                        "SignalNewConsumerTransport",
                        "resume-ack-fail producerId=$producerId serverConsumerId=$serverConsumerId"
                    )
                }
                
                Result.success(Unit)
            },
            onFailure = { error ->
                MediaSFURuntimeProbe.recordConsumerSignalStage(
                    "consume-fail",
                    producerId,
                    error.toProbeDetail()
                )
                Logger.e(
                    "SignalNewConsumerTransport",
                    "consume-fail producerId=$producerId error=${error.message}"
                )
                Result.failure(
                    SignalNewConsumerTransportException(
                        "Failed to signal new consumer transport: ${error.message}",
                        error
                    )
                )
            }
        )
    } catch (error: Exception) {
        Result.failure(
            SignalNewConsumerTransportException(
                "signalNewConsumerTransport error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Parses the consume response from the server.
 */
private fun parseConsumeResponse(response: Map<String, Any?>): ConsumeResponse {
    return ConsumeResponse(
        id = response["id"] as? String ?: "",
        producerId = response["producerId"] as? String ?: "",
        kind = response["kind"] as? String ?: "",
        rtpParameters = response["rtpParameters"].toStringAnyMap(),
        serverConsumerId = response["serverConsumerId"] as? String ?: ""
    )
}

private fun ConsumeResponse.rtpDebugSummary(): String {
    val codecs = (rtpParameters["codecs"] as? List<*>)
        ?.mapNotNull { codec ->
            val codecMap = codec as? Map<*, *> ?: return@mapNotNull null
            val payload = (codecMap["payloadType"] ?: codecMap["pt"])?.toString() ?: "?"
            val mime = (codecMap["mimeType"] ?: codecMap["mime"])?.toString() ?: "unknown"
            "$payload/$mime"
        }
        ?.joinToString(prefix = "[", postfix = "]")
        ?: "[]"

    val headerExts = (rtpParameters["headerExtensions"] as? List<*>)
        ?.mapNotNull { (it as? Map<*, *>)?.get("uri")?.toString() }
        ?.joinToString(prefix = "[", postfix = "]")
        ?: "[]"

    val encodings = (rtpParameters["encodings"] as? List<*>)
        ?.mapIndexed { index, encoding ->
            val encMap = encoding as? Map<*, *> ?: return@mapIndexed "#$index:?"
            val ssrc = encMap["ssrc"] ?: "n/a"
            val rid = encMap["rid"] ?: "n/a"
            val active = encMap["active"] ?: encMap["enabled"] ?: "?"
            "#$index(ssrc=$ssrc,rid=$rid,active=$active)"
        }
        ?.joinToString(prefix = "[", postfix = "]")
        ?: "[]"

    val rtcp = (rtpParameters["rtcp"] as? Map<*, *>)
        ?.map { (key, value) -> "$key=$value" }
        ?.joinToString(prefix = "{", postfix = "}")
        ?: "{}"

    return "codecs=$codecs headerExts=$headerExts encodings=$encodings rtcp=$rtcp"
}

private fun Throwable.toProbeDetail(maxLen: Int = 220): String {
    val base = buildString {
        append(this@toProbeDetail::class.simpleName ?: "Throwable")
        val msg = this@toProbeDetail.message.orEmpty()
        if (msg.isNotBlank()) {
            append(": ")
            append(msg)
        }
        val causeMsg = this@toProbeDetail.cause?.message.orEmpty()
        if (causeMsg.isNotBlank() && !msg.contains(causeMsg)) {
            append(" | cause: ")
            append(causeMsg)
        }
    }
    return if (base.length <= maxLen) base else base.take(maxLen)
}

private fun RtpCapabilities.codecDebugSummary(): String =
    codecs.joinToString(prefix = "[", postfix = "]") { codec ->
        val payload = codec.preferredPayloadType?.toString() ?: "?"
        "$payload/${codec.mimeType}"
    }

/**
 * Converts RtpCapabilities to a JSON-serializable map structure for Socket.IO.
 */
private fun RtpCapabilities.toMap(): Map<String, Any> {
    val codecMaps = codecs.map { codec ->
        mutableMapOf<String, Any>(
            "kind" to codec.kind.name.lowercase(),
            "mimeType" to codec.mimeType,
            "clockRate" to codec.clockRate
        ).apply {
            codec.preferredPayloadType?.let { put("preferredPayloadType", it) }
            codec.channels?.let { put("channels", it) }
            if (codec.parameters.isNotEmpty()) {
                put("parameters", codec.parameters.mapValues { (_, value) -> value.toIntOrNull() ?: value })
            }
            if (codec.rtcpFeedback.isNotEmpty()) {
                put(
                    "rtcpFeedback",
                    codec.rtcpFeedback.map { fb ->
                        mutableMapOf<String, Any>("type" to fb.type).apply {
                            fb.parameter?.let { put("parameter", it) }
                        }
                    }
                )
            }
        }
    }

    val extensionMaps = headerExtensions.map { ext ->
        mutableMapOf<String, Any>(
            "uri" to ext.uri,
            "preferredId" to ext.preferredId,
            "preferredEncrypt" to ext.preferredEncrypt
        ).apply {
            ext.kind?.let { put("kind", it.name.lowercase()) }
            ext.direction?.let { put("direction", it.name.lowercase()) }
        }
    }

    return mapOf(
        "codecs" to codecMaps,
        "headerExtensions" to extensionMaps,
        "fecMechanisms" to fecMechanisms
    )
}

private fun ConsumeResponse.debugSummary(): String {
    val codecCount = (rtpParameters["codecs"] as? Collection<*>)?.size ?: 0
    val headerExtCount = (rtpParameters["headerExtensions"] as? Collection<*>)?.size ?: 0
    val encodingsCount = (rtpParameters["encodings"] as? Collection<*>)?.size ?: 0
    return "ConsumeResponse(id=$id, producerId=$producerId, kind=$kind, serverConsumerId=$serverConsumerId, codecs=$codecCount, headerExts=$headerExtCount, encodings=$encodingsCount)"
}

private fun Map<*, *>.toStructuredLog(maxDepth: Int = 3): String =
    mapToStructuredString(this, depth = 0, maxDepth = maxDepth)

private fun mapToStructuredString(map: Map<*, *>, depth: Int, maxDepth: Int): String {
    if (depth >= maxDepth) return "map(size=${map.size})"
    return map.entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
        val renderedKey = key?.toString() ?: "null"
        "$renderedKey:${value.toStructuredLogValue(depth + 1, maxDepth)}"
    }
}

private fun Any?.toStructuredLogValue(depth: Int, maxDepth: Int): String {
    if (this == null) return "null"
    if (depth >= maxDepth) {
        return when (this) {
            is Map<*, *> -> "map(size=${this.size})"
            is Collection<*> -> "list(size=${this.size})"
            is Array<*> -> "array(size=${this.size})"
            else -> toString()
        }
    }

    return when (this) {
        is String -> "\"${this}\""
        is Number, is Boolean -> toString()
        is Map<*, *> -> mapToStructuredString(this, depth, maxDepth)
        is Collection<*> -> this.joinToString(prefix = "[", postfix = "]") { element ->
            element.toStructuredLogValue(depth + 1, maxDepth)
        }
        is Array<*> -> this.joinToString(prefix = "[", postfix = "]") { element ->
            element.toStructuredLogValue(depth + 1, maxDepth)
        }
        else -> toString()
    }
}
