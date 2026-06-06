// CreateSendTransport.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.webrtc.*
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.util.toStringAnyMap
import com.mediasfu.sdk.util.MediaSFURuntimeProbe
import kotlinx.datetime.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Shared state required by both create/connect transport flows.
 */
interface SendTransportSessionParameters {
    val islevel: String
    val member: String
    val socket: SocketManager?
    val localSocket: SocketManager?
    val device: WebRtcDevice?
    val rtpCapabilities: RtpCapabilities?
    val routerRtpCapabilities: RtpCapabilities?
    val extendedRtpCapabilities: OrtcUtils.ExtendedRtpCapabilities?
    var transportCreated: Boolean
    var localTransportCreated: Boolean
    var producerTransport: WebRtcTransport?
    var localProducerTransport: WebRtcTransport?

    val updateExtendedRtpCapabilities: ((OrtcUtils.ExtendedRtpCapabilities?) -> Unit)?

    val updateProducerTransport: (WebRtcTransport?) -> Unit
        get() = { transport -> producerTransport = transport }

    val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
        get() = { transport -> localProducerTransport = transport }

    val updateTransportCreated: (Boolean) -> Unit
        get() = { created -> transportCreated = created }

    val updateLocalTransportCreated: (Boolean) -> Unit
        get() = { created -> localTransportCreated = created }
}

/**
 * Contract describing the mutable parameters required to create WebRTC send transports.
 */
interface CreateSendTransportParameters : SendTransportSessionParameters {
    val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.createSendTransport(options) }
}

/**
 * Options for creating WebRTC send transports.
 */
data class CreateSendTransportOptions(
    val option: String, // 'audio', 'video', 'screen', or 'all'
    val parameters: CreateSendTransportParameters,
    val audioConstraints: Map<String, Any>? = null,
    val videoConstraints: Map<String, Any>? = null
)

/**
 * Exception thrown when creating send transport fails.
 */
class CreateSendTransportException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Creates a WebRTC send transport for media transmission.
 *
 * This function initiates a WebRTC send transport with a server for sending media streams.
 * It performs the following actions:
 * 1. Emits a `createWebRtcTransport` event to the server to request transport creation
 * 2. Sets up transport event handlers for connecting, producing, and monitoring state
 * 3. Provides basic transport management functionality
 *
 * ## Features:
 * - Creates both local and remote WebRTC transports
 * - Handles DTLS connection establishment
 * - Manages media production events
 * - Monitors connection state changes
 *
 * ## Parameters:
 * - [options] Configuration options for transport creation
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 */
suspend fun createSendTransport(options: CreateSendTransportOptions): Result<Unit> {
    return try {
        val parameters = options.parameters
        
        // Determine the correct socket to use for transport creation.
        // In CE-only mode (connectMediaSFU=false), localSocket is the authoritative socket
        // that was used for joinRoom. We must use the same socket for transport creation
        // so the server can find peers[socket.id].
        val localSocket = parameters.localSocket
        val mainSocket = parameters.socket
        
        // Prefer localSocket if it's connected (has an id), otherwise fall back to mainSocket
        val socket = if (localSocket?.id?.isNotEmpty() == true) {
            localSocket
        } else {
            mainSocket
        } ?: return Result.failure(CreateSendTransportException("Socket connection is null"))
        

        val device = parameters.device
            ?: return Result.failure(CreateSendTransportException("Device is null"))

        ensureDeviceLoadedForSendTransport(parameters, device).getOrElse { error ->
            return Result.failure(
                CreateSendTransportException(
                    "Device is not ready for send transport creation: ${error.message}",
                    error
                )
            )
        }
        
        val extendedCaps = resolveExtendedRtpCapabilities(parameters)
        val requiredKinds = requiredMediaKinds(options.option)
        if (extendedCaps != null && requiredKinds.isNotEmpty()) {
            val unsupportedKinds = requiredKinds.filterNot { kind ->
                OrtcUtils.canSend(kind, extendedCaps)
            }
            if (unsupportedKinds.isNotEmpty()) {
                val names = unsupportedKinds.joinToString { it.name.lowercase() }
                return Result.failure(CreateSendTransportException(
                    "Cannot produce required media kinds ($names) with current RTP negotiation"
                ))
            }
        }

        // Determine if we're using localSocket as the primary socket
        // If so, we're in CE-only mode and should NOT create a separate local transport
        val mainSocketId = mainSocket?.id
        val localSocketId = localSocket?.id
        val usingLocalSocketAsPrimary = socket === localSocket || socket?.id == localSocketId
        
        // Determine if we're in true hybrid mode:
        // - mainSocket is connected (to cloud)
        // - localSocket is connected (to CE) 
        // - They have different IDs
        // - AND we're using mainSocket as primary (not localSocket)
        val isHybridMode = !mainSocketId.isNullOrEmpty() && 
                           !localSocketId.isNullOrEmpty() && 
                           mainSocketId != localSocketId &&
                           !usingLocalSocketAsPrimary

        // Create local send transport first (wrapped in try-catch per Flutter/React pattern)
        // Local transport is ONLY created in hybrid mode when cloud is primary and CE is secondary
        if (isHybridMode) {
            try {
                createLocalSendTransport(options)
            } catch (_: Exception) {
                // Local transport creation failure is non-fatal
            }
        }
        
        // Create remote send transport (always executed - this is the main transport)
        createRemoteSendTransport(options, socket, device)
        
        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            CreateSendTransportException(
                "Error creating send transport: ${error.message}",
                error
            )
        )
    }
}

/**
 * Creates a local WebRTC send transport.
 */
private suspend fun createLocalSendTransport(options: CreateSendTransportOptions) {
    val parameters = options.parameters
    val mainSocket = parameters.socket
    val localSocket = parameters.localSocket
    
    // Per React/Flutter: Skip local transport if:
    // 1. No localSocket exists or not connected
    // 2. No mainSocket exists or not connected (CE-only mode - no cloud)
    // 3. localSocket.id equals mainSocket.id (same socket)
    // Local transport is ONLY created in hybrid mode when BOTH sockets are connected with DIFFERENT IDs
    if (localSocket == null) return
    
    val localSocketId = localSocket.id
    if (localSocketId.isNullOrEmpty()) return
    
    // Skip if mainSocket doesn't exist or isn't connected (CE-only mode)
    val mainSocketId = mainSocket?.id
    if (mainSocketId.isNullOrEmpty()) return
    
    // Skip if same socket (both pointing to same server)
    if (localSocketId == mainSocketId) return
    
    val device = parameters.device ?: return
    
    try {
        val response = withTimeout(30000) {
            localSocket.emitWithAck<Map<String, Any?>>(
                "createWebRtcTransport",
                mapOf(
                    "consumer" to false,
                    "islevel" to parameters.islevel
                )
            )
        }

        val params = response["params"].toStringAnyMap()
            .ifEmpty { throw CreateSendTransportException("Missing params in local transport response") }

        val localTransport = device.createSendTransport(params)

        setupSendTransportHandlers(
            transport = localTransport,
            socket = localSocket,
            islevel = parameters.islevel,
            member = parameters.member
        )

        parameters.updateLocalProducerTransport?.invoke(localTransport)
        parameters.updateLocalTransportCreated(true)

        // Invoke option-specific connect based on the media type (same logic as remote)
        when (options.option) {
            "audio" -> {
                val audioParams = parameters as? ConnectSendTransportAudioParameters
                if (audioParams != null) {
                    val audioStream = audioParams.localStreamAudio
                    if (audioStream != null) {
                        val audioOptions = ConnectSendTransportAudioOptions(
                            stream = audioStream,
                            targetOption = "local",
                            parameters = audioParams,
                            audioConstraints = options.audioConstraints
                        )
                        connectSendTransportAudio(audioOptions)
                    }
                }
            }
            "video" -> {
                val videoParams = parameters as? ConnectSendTransportVideoParameters
                val refreshedParams = videoParams?.getUpdatedAllParams()
                if (refreshedParams != null) {
                    MediaSFURuntimeProbe.recordProducerSignalStage(
                        "create-video-connect",
                        "video",
                        "target=local stream=${refreshedParams.localStreamVideo?.id ?: refreshedParams.localStream?.id ?: "none"} params=${refreshedParams.resolveVideoProducerOptions() != null}"
                    )
                    val videoOptions = ConnectSendTransportVideoOptions(
                        targetOption = "local",
                        videoParams = refreshedParams.resolveVideoProducerOptions(),
                        parameters = refreshedParams,
                        videoConstraints = options.videoConstraints
                    )
                    connectSendTransportVideo(videoOptions).getOrElse { error ->
                        MediaSFURuntimeProbe.recordProducerSignalStage(
                            "producer-failed",
                            "video",
                            "target=local error=${error.message ?: "unknown"}"
                        )
                        throw CreateSendTransportException(
                            "Failed to connect local video transport after create: ${error.message}",
                            error
                        )
                    }
                }
            }
            "screen" -> {
                val screenParams = parameters as? ConnectSendTransportScreenParameters
                if (screenParams != null) {
                    val screenStream = screenParams.localStreamScreen
                    if (screenStream != null) {
                        val screenOptions = ConnectSendTransportScreenOptions(
                            targetOption = "local",
                            stream = screenStream,
                            parameters = screenParams
                        )
                        connectSendTransportScreen(screenOptions)
                    }
                }
            }
            "all" -> {
                val connectParameters = parameters as? ConnectSendTransportParameters
                if (connectParameters != null) {
                    val connectOptions = ConnectSendTransportOptions(
                        option = options.option,
                        parameters = connectParameters,
                        audioConstraints = options.audioConstraints,
                        videoConstraints = options.videoConstraints,
                        targetOption = "local"
                    )
                    connectSendTransport(connectOptions)
                }
            }
            else -> { }
        }
    } catch (error: Exception) {
        throw CreateSendTransportException(
            "Failed to create local transport: ${error.message}",
            error
        )
    }
}

/**
 * Creates a remote WebRTC send transport.
 */
private suspend fun createRemoteSendTransport(
    options: CreateSendTransportOptions,
    socket: SocketManager,
    device: WebRtcDevice
) {
    val parameters = options.parameters
    
    try {
        val response = withTimeout(30000) {
            socket.emitWithAck<Map<String, Any?>>(
                "createWebRtcTransport",
                mapOf(
                    "consumer" to false,
                    "islevel" to parameters.islevel
                )
            )
        }

        val params = response["params"].toStringAnyMap()
            .ifEmpty { throw CreateSendTransportException("Missing params in remote transport response") }

        val remoteTransport = device.createSendTransport(params)

        setupSendTransportHandlers(
            transport = remoteTransport,
            socket = socket,
            islevel = parameters.islevel,
            member = parameters.member
        )

        parameters.updateProducerTransport(remoteTransport)
        parameters.updateTransportCreated(true)

        // Invoke option-specific connect based on the media type
        // NOTE: We cast to the specific interface (Audio/Video/Screen) rather than the combined
        // ConnectSendTransportParameters, because callers like StreamSuccessAudio only implement
        // the specific interface for their media type.
        when (options.option) {
            "audio" -> {
                val audioParams = parameters as? ConnectSendTransportAudioParameters
                if (audioParams != null) {
                    val audioStream = audioParams.localStreamAudio
                    if (audioStream != null) {
                        val audioOptions = ConnectSendTransportAudioOptions(
                            stream = audioStream,
                            targetOption = "remote",
                            parameters = audioParams,
                            audioConstraints = options.audioConstraints
                        )
                        connectSendTransportAudio(audioOptions)
                    }
                }
            }
            "video" -> {
                val videoParams = parameters as? ConnectSendTransportVideoParameters
                val refreshedParams = videoParams?.getUpdatedAllParams()
                if (refreshedParams != null) {
                    MediaSFURuntimeProbe.recordProducerSignalStage(
                        "create-video-connect",
                        "video",
                        "target=remote stream=${refreshedParams.localStreamVideo?.id ?: refreshedParams.localStream?.id ?: "none"} params=${refreshedParams.resolveVideoProducerOptions() != null}"
                    )
                    val videoOptions = ConnectSendTransportVideoOptions(
                        targetOption = "remote",
                        videoParams = refreshedParams.resolveVideoProducerOptions(),
                        parameters = refreshedParams,
                        videoConstraints = options.videoConstraints
                    )
                    connectSendTransportVideo(videoOptions).getOrElse { error ->
                        MediaSFURuntimeProbe.recordProducerSignalStage(
                            "producer-failed",
                            "video",
                            "target=remote error=${error.message ?: "unknown"}"
                        )
                        throw CreateSendTransportException(
                            "Failed to connect remote video transport after create: ${error.message}",
                            error
                        )
                    }
                }
            }
            "screen" -> {
                val screenParams = parameters as? ConnectSendTransportScreenParameters
                if (screenParams != null) {
                    val screenStream = screenParams.localStreamScreen
                    if (screenStream != null) {
                        val screenOptions = ConnectSendTransportScreenOptions(
                            targetOption = "remote",
                            stream = screenStream,
                            parameters = screenParams
                        )
                        connectSendTransportScreen(screenOptions)
                    }
                }
            }
            "all" -> {
                // For "all" option, we need the full combined interface
                val connectParameters = parameters as? ConnectSendTransportParameters
                if (connectParameters != null) {
                    val connectOptions = ConnectSendTransportOptions(
                        option = options.option,
                        parameters = connectParameters,
                        audioConstraints = options.audioConstraints,
                        videoConstraints = options.videoConstraints,
                        targetOption = "remote"
                    )
                    connectSendTransport(connectOptions)
                }
            }
            else -> {}
        }
    } catch (error: Exception) {
        throw CreateSendTransportException(
            "Failed to create remote transport: ${error.message}",
            error
        )
    }
}

private fun ConnectSendTransportVideoParameters.resolveVideoProducerOptions() = when (this) {
    is ConnectSendTransportParameters -> videoParams
    is StreamSuccessVideoParameters -> videoParams
    else -> null
}

internal fun resolveExtendedRtpCapabilities(
    parameters: SendTransportSessionParameters
): OrtcUtils.ExtendedRtpCapabilities? {
    parameters.extendedRtpCapabilities?.let { return it }

    val localCaps = parameters.rtpCapabilities
    val remoteCaps = parameters.routerRtpCapabilities
    if (localCaps == null || remoteCaps == null) {
        return null
    }

    return runCatching {
        OrtcUtils.getExtendedRtpCapabilities(localCaps, remoteCaps)
    }.getOrNull()?.also { computed ->
        parameters.updateExtendedRtpCapabilities?.invoke(computed)
    }
}

private suspend fun ensureDeviceLoadedForSendTransport(
    parameters: SendTransportSessionParameters,
    device: WebRtcDevice
): Result<Unit> {
    val routerCaps = parameters.routerRtpCapabilities ?: parameters.rtpCapabilities
        ?: return Result.success(Unit)

    findFirstNullTypePath(routerCaps)?.let { nullTypePath ->
        Logger.e(
            "CreateSendTransport",
            "device-load routerRtpCapabilities contains null type at path=$nullTypePath"
        )
    }

    val needsInitialLoad =
        device.currentRtpCapabilities() == null || parameters.extendedRtpCapabilities == null

    if (!needsInitialLoad) {
        return Result.success(Unit)
    }

    return runCatching {
        device.load(routerCaps).getOrThrow()
    }
}

private fun requiredMediaKinds(option: String): Set<MediaKind> {
    return when (option.lowercase()) {
        "audio" -> setOf(MediaKind.AUDIO)
        "video", "screen" -> setOf(MediaKind.VIDEO)
        "all" -> setOf(MediaKind.AUDIO, MediaKind.VIDEO)
        else -> emptySet()
    }
}

private fun setupSendTransportHandlers(
    transport: WebRtcTransport,
    socket: SocketManager,
    islevel: String,
    member: String
) {
    transport.onConnect { connectData ->
        val startedAtMs = Clock.System.now().toEpochMilliseconds()
        try {
            val dtlsMap = connectData.dtlsParameters.toMap()
            Logger.i(
                "CreateSendTransport",
                "transport-connect start transportId=${transport.id} role=${dtlsMap["role"] ?: "unknown"} fingerprints=${(dtlsMap["fingerprints"] as? List<*>)?.size ?: 0}"
            )

            val payload = mapOf("dtlsParameters" to dtlsMap)
            launchTransport {
                runCatching { socket.emit("transport-connect", payload) }
                    .onFailure { error ->
                        Logger.w(
                            "CreateSendTransport",
                            "transport-connect emit failed transportId=${transport.id}: ${error.message}"
                        )
                    }
            }
            connectData.callback()
            Logger.i(
                "CreateSendTransport",
                "transport-connect callback transportId=${transport.id} latencyMs=${Clock.System.now().toEpochMilliseconds() - startedAtMs}"
            )
        } catch (error: Exception) {
            Logger.e(
                "CreateSendTransport",
                "transport-connect failed transportId=${transport.id} latencyMs=${Clock.System.now().toEpochMilliseconds() - startedAtMs}: ${error.message}"
            )
            connectData.errback(error)
        }
    }

    transport.onProduce { produceData ->
        val startedAtMs = Clock.System.now().toEpochMilliseconds()
        val rtpMap = produceData.rtpParameters.toMap()
        val callbackKind = produceData.kind.name.lowercase()
        val produceKind = resolveProduceKind(callbackKind, produceData.appData)
        if (produceKind != callbackKind) {
            Logger.w(
                "CreateSendTransport",
                "transport-produce kind override native=$callbackKind effective=$produceKind transportId=${transport.id}"
            )
        }
        MediaSFURuntimeProbe.recordProducerSignalStage(
            "produce-entered",
            produceKind,
            "transport=${transport.id}"
        )
        
        // Normalize string numbers to actual numbers (e.g., apt: "96" -> apt: 96)
        val normalizedRtpMap = normalizeRtpNumbers(rtpMap) as? Map<String, Any?> ?: rtpMap
        
        // Add missing rtcpFeedback to codecs (native Android library strips these)
        val fixedRtpMap = sanitizeRtpParametersForSignaling(
            addRtcpFeedbackToCodecs(normalizedRtpMap, produceKind)
        )

        val cleanRtpMap = fixedRtpMap.applyCurrentPlatformHeaderExtensionPolicy()

        findFirstNullTypePath(cleanRtpMap)?.let { nullTypePath ->
            Logger.e(
                "CreateSendTransport",
                "transport-produce payload contains null type at path=$nullTypePath kind=$produceKind transportId=${transport.id}"
            )
        }
        
        val payload = mutableMapOf<String, Any?>(
            "transportId" to transport.id,
            "kind" to produceKind,
            "rtpParameters" to cleanRtpMap,
            "islevel" to islevel,
            "name" to member
        )
        produceData.appData?.let { 
            payload["appData"] = it
        }

        Logger.i(
            "CreateSendTransport",
            "transport-produce start transportId=${transport.id} kind=$produceKind codecs=${(cleanRtpMap["codecs"] as? List<*>)?.size ?: 0} encodings=${(cleanRtpMap["encodings"] as? List<*>)?.size ?: 0} hasAppData=${produceData.appData != null}"
        )
        MediaSFURuntimeProbe.recordProducerSignalStage(
            "produce-request",
            produceKind,
            describeProduceRequest(
                transportId = transport.id,
                kind = produceKind,
                rtpMap = cleanRtpMap,
                appData = produceData.appData
            )
        )

        try {
            socket.emitWithAck("transport-produce", payload) { response ->
                handleProduceAck(
                    response,
                    onSuccess = { id: String? ->
                        val latencyMs = Clock.System.now().toEpochMilliseconds() - startedAtMs
                        Logger.i(
                            "CreateSendTransport",
                            "transport-produce ack transportId=${transport.id} producerId=${id ?: "null"} latencyMs=$latencyMs"
                        )
                        MediaSFURuntimeProbe.recordProducerSignalStage(
                            "produce-ack-ok",
                            produceKind,
                            "id=${id ?: "null"},ms=$latencyMs"
                        )
                        produceData.callback(id)
                    },
                    onError = { error: Throwable ->
                        val latencyMs = Clock.System.now().toEpochMilliseconds() - startedAtMs
                        Logger.e(
                            "CreateSendTransport",
                            "transport-produce ack failed transportId=${transport.id} latencyMs=$latencyMs: ${error.message}"
                        )
                        MediaSFURuntimeProbe.recordProducerSignalStage(
                            "produce-ack-error",
                            produceKind,
                            error.message.orEmpty()
                        )
                        produceData.errback(error)
                    }
                )
            }
        } catch (error: Exception) {
            Logger.e(
                "CreateSendTransport",
                "transport-produce emit failed transportId=${transport.id} latencyMs=${Clock.System.now().toEpochMilliseconds() - startedAtMs}: ${error.message}"
            )
            MediaSFURuntimeProbe.recordProducerSignalStage(
                "produce-exception",
                produceKind,
                error.message.orEmpty()
            )
            produceData.errback(error)
        }
    }

    transport.onConnectionStateChange { state ->
        Logger.i("CreateSendTransport", "transport-state transportId=${transport.id} state=$state")
        when (state.lowercase()) {
            "failed" -> transport.close()
            "closed" -> { /* Transport shut down */ }
        }
    }
}

private fun handleProduceAck(
    response: Any?,
    onSuccess: (String?) -> Unit,
    onError: (Throwable) -> Unit
) {
    when (response) {
        is Map<*, *> -> {
            val errorValue = response["error"]
            if (errorValue != null) {
                onError(IllegalStateException(errorValue.toString()))
            } else {
                val id = response["id"]?.toString()
                if (id.isNullOrEmpty()) {
                    onError(IllegalStateException("Missing producer id in transport acknowledgment"))
                } else {
                    onSuccess(id)
                }
            }
        }
        null -> {
            onError(IllegalStateException("Empty response from transport acknowledgment"))
        }
        else -> {
            onSuccess(response.toString())
        }
    }
}

private fun launchTransport(block: suspend () -> Unit) {
    CoroutineScope(Dispatchers.Default).launch {
        block()
    }
}

private fun resolveProduceKind(nativeKind: String, appData: Map<String, Any?>?): String {
    val explicitKind = listOf("kind", "mediaKind", "mediaTag", "source")
        .firstNotNullOfOrNull { key -> appData?.get(key)?.toString()?.trim()?.lowercase()?.takeIf { it.isNotBlank() } }

    return when (explicitKind) {
        "audio", "mic", "microphone" -> "audio"
        "video", "camera", "screen", "screenshare", "screen-share" -> "video"
        else -> nativeKind
    }
}

private fun describeProduceRequest(
    transportId: String,
    kind: String,
    rtpMap: Map<String, Any?>,
    appData: Any?
): String {
    val codecs = rtpMap["codecs"] as? List<*>
    val encodings = rtpMap["encodings"] as? List<*>
    val headerExtensions = rtpMap["headerExtensions"] as? List<*>
    val appDataMap = appData as? Map<*, *>
    val appDataKeys = appDataMap
        ?.keys
        ?.mapNotNull { it?.toString() }
        ?.sorted()
        .orEmpty()
    val appDataSource = appDataMap?.get("source")?.toString().orEmpty()
    val appDataMediaTag = appDataMap?.get("mediaTag")?.toString().orEmpty()
    val appDataTrackId = appDataMap?.containsKey("trackId") == true

    return buildString {
        append("transport=")
        append(transportId)
        append(",kind=")
        append(kind)
        append(",codecs=")
        append(codecs?.size ?: 0)
        append(",encodings=")
        append(encodings?.size ?: 0)
        append(",headerExt=")
        append(headerExtensions?.size ?: 0)
        append(",appDataPresent=")
        append(appData != null)
        append(",appDataKeys=")
        append(appDataKeys.joinToString("|"))
        append(",appDataSource=")
        append(appDataSource.ifBlank { "none" })
        append(",appDataMediaTag=")
        append(appDataMediaTag.ifBlank { "none" })
        append(",appDataTrackId=")
        append(appDataTrackId)
    }
}

/**
 * Adds RTCP feedback mechanisms to codecs.
 * The native Android library strips rtcpFeedback during transport negotiation,
 * but these feedback mechanisms are essential for video quality and are expected by MediaSFU server.
 */
private fun addRtcpFeedbackToCodecs(rtpMap: Map<String, Any?>, kind: String): Map<String, Any?> {
    val codecs = rtpMap["codecs"] as? List<*> ?: return rtpMap
    
    val fixedCodecs = codecs.map { codecObj ->
        val codec = codecObj as? Map<*, *> ?: return@map codecObj
        val mimeType = codec["mimeType"] as? String ?: return@map codecObj
        val fixedCodec = codec.toMutableMap()
        
        // Add feedback based on codec type
        if (kind.uppercase() == "VIDEO") {
            // Remove null channels field for video codecs (only meaningful for audio)
            if (fixedCodec["channels"] == null) {
                fixedCodec.remove("channels")
            }
            
            if (mimeType.contains("rtx", ignoreCase = true)) {
                // RTX codecs get empty rtcpFeedback array (matches web version)
                fixedCodec["rtcpFeedback"] = emptyList<Map<String, String>>()
            } else {
                // VP8/VP9/H264 codecs get full feedback mechanisms
                fixedCodec["rtcpFeedback"] = listOf(
                    mapOf("type" to "goog-remb", "parameter" to ""),
                    mapOf("type" to "transport-cc", "parameter" to ""),
                    mapOf("type" to "ccm", "parameter" to "fir"),
                    mapOf("type" to "nack", "parameter" to ""),
                    mapOf("type" to "nack", "parameter" to "pli")
                )
            }
        }
        
        fixedCodec
    }
    
    val fixedRtpMap = rtpMap.toMutableMap()
    fixedRtpMap["codecs"] = fixedCodecs
    return fixedRtpMap
}

private fun sanitizeRtpParametersForSignaling(rtpMap: Map<String, Any?>): Map<String, Any?> {
    val codecs = (rtpMap["codecs"] as? List<*>)?.map { codecObj ->
        val codec = codecObj as? Map<*, *> ?: return@map codecObj
        val mutableCodec = codec
            .filterKeys { it is String }
            .mapKeys { it.key as String }
            .toMutableMap()

        val feedback = (mutableCodec["rtcpFeedback"] as? List<*>)
            ?.mapNotNull { item ->
                val feedbackMap = item as? Map<*, *> ?: return@mapNotNull null
                val type = feedbackMap["type"]?.toString()?.trim().orEmpty()
                if (type.isBlank()) return@mapNotNull null

                mapOf(
                    "type" to type,
                    "parameter" to feedbackMap["parameter"]?.toString().orEmpty()
                )
            }
            ?: emptyList()

        mutableCodec["rtcpFeedback"] = feedback
        deepPruneNulls(mutableCodec)
    } ?: emptyList()

    val sanitized = rtpMap.toMutableMap()
    sanitized["codecs"] = codecs
    return (deepPruneNulls(sanitized) as? Map<String, Any?>) ?: sanitized
}

private fun deepPruneNulls(value: Any?): Any? = when (value) {
    is Map<*, *> -> value.entries
        .mapNotNull { entry ->
            val key = entry.key as? String ?: return@mapNotNull null
            val pruned = deepPruneNulls(entry.value) ?: return@mapNotNull null
            key to pruned
        }
        .toMap()
    is List<*> -> value.mapNotNull { deepPruneNulls(it) }
    else -> value
}

private fun findFirstNullTypePath(value: Any?, path: String = ""): String? = when (value) {
    is Map<*, *> -> {
        value.entries.forEach { entry ->
            val key = entry.key as? String ?: return@forEach
            val childPath = if (path.isEmpty()) key else "$path.$key"
            if (key == "type" && entry.value == null) {
                return childPath
            }

            findFirstNullTypePath(entry.value, childPath)?.let { return it }
        }
        null
    }
    is List<*> -> {
        value.forEachIndexed { index, item ->
            val childPath = "$path[$index]"
            findFirstNullTypePath(item, childPath)?.let { return it }
        }
        null
    }
    else -> null
}

/**
 * Normalizes string numbers to actual numbers in RTP parameters.
 * The native library sometimes returns numeric values as strings (e.g., apt: "96")
 * but MediaSFU server expects actual numbers for codec parameters.
 * 
 * IMPORTANT: Preserves hex strings like profile-level-id ("42e01f") which must remain as strings.
 */
private fun normalizeRtpNumbers(value: Any?, key: String? = null): Any? = when (value) {
    null -> null
    is Map<*, *> -> value.entries.fold(mutableMapOf<String, Any?>()) { acc, entry ->
        val mapKey = entry.key
        if (mapKey is String) {
            acc[mapKey] = normalizeRtpNumbers(entry.value, mapKey)
        }
        acc
    }
    is List<*> -> value.map { item -> normalizeRtpNumbers(item, null) }
    is Array<*> -> value.map { item -> normalizeRtpNumbers(item, null) }
    is String -> {
        // CRITICAL: Preserve profile-level-id as string (it's a hex value like "42e01f")
        // Also preserve any string that looks like a hex value (contains letters a-f)
        // CRITICAL: Preserve 'mid' as string - mediasoup server expects string, not number
        val looksLikeHex = value.any { it in 'a'..'f' || it in 'A'..'F' }
        val isProfileLevelId = key?.equals("profile-level-id", ignoreCase = true) == true
        val isMid = key?.equals("mid", ignoreCase = true) == true
        val isRid = key?.equals("rid", ignoreCase = true) == true
        
        if (isProfileLevelId || looksLikeHex || isMid || isRid) {
            value // Keep as string - these are identifiers, not numbers
        } else {
            // Try to parse string numbers to actual numbers
            value.toIntOrNull() ?: value.toLongOrNull() ?: value.toDoubleOrNull() ?: value
        }
    }
    else -> value
}

private fun deepStringify(value: Any?): Any? = when (value) {
    null -> ""  // Convert null to empty string for mediasoup server
    is Map<*, *> -> value.entries.fold(mutableMapOf<String, Any?>()) { acc, entry ->
        val key = entry.key
        if (key is String) {
            val stringifiedValue = deepStringify(entry.value)
            // Only include non-null/non-empty values to avoid server validation errors
            if (stringifiedValue != null && stringifiedValue != "") {
                acc[key] = stringifiedValue
            }
        }
        acc
    }
    is List<*> -> value.mapNotNull { item ->
        val stringified = deepStringify(item)
        if (stringified != null && stringified != "") stringified else null
    }
    is Array<*> -> value.mapNotNull { item ->
        val stringified = deepStringify(item)
        if (stringified != null && stringified != "") stringified else null
    }
    is Boolean, is Number -> value.toString()
    else -> value
}
