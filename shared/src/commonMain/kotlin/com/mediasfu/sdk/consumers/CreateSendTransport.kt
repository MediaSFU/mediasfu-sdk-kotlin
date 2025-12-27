// CreateSendTransport.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.webrtc.*
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils
import com.mediasfu.sdk.socket.SocketManager
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

        val params = response["params"] as? Map<String, Any?>
            ?: throw CreateSendTransportException("Missing params in local transport response")

        val localTransport = device.createSendTransport(params as Map<String, Any>)

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
                // Video requires the combined interface since videoParams is defined there
                val combinedParams = parameters as? ConnectSendTransportParameters
                if (combinedParams != null) {
                    val vParams = combinedParams.videoParams
                    if (vParams != null) {
                        val videoOptions = ConnectSendTransportVideoOptions(
                            targetOption = "local",
                            videoParams = vParams,
                            parameters = combinedParams,
                            videoConstraints = options.videoConstraints
                        )
                        connectSendTransportVideo(videoOptions)
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

        val params = response["params"] as? Map<String, Any?>
            ?: throw CreateSendTransportException("Missing params in remote transport response")

        val remoteTransport = device.createSendTransport(params as Map<String, Any>)

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
                // Video requires the combined interface since videoParams is defined there
                val combinedParams = parameters as? ConnectSendTransportParameters
                if (combinedParams != null) {
                    val vParams = combinedParams.videoParams
                    if (vParams != null) {
                        val videoOptions = ConnectSendTransportVideoOptions(
                            targetOption = "remote",
                            videoParams = vParams,
                            parameters = combinedParams,
                            videoConstraints = options.videoConstraints
                        )
                        connectSendTransportVideo(videoOptions)
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
        launchTransport {
            try {
                // Get dtlsParameters - DO NOT override the role!
                // The native mediasoup-client-android defaults to "server" role for send transport.
                // This means the client is the DTLS server, and the SFU becomes the DTLS client.
                val dtlsMap = connectData.dtlsParameters.toMap()

                socket.emit(
                    "transport-connect",
                    mapOf("dtlsParameters" to dtlsMap)
                )
                connectData.callback()
            } catch (error: Exception) {
                connectData.errback(error)
            }
        }
    }

    transport.onProduce { produceData ->
        val rtpMap = produceData.rtpParameters.toMap()
        
        // Normalize string numbers to actual numbers (e.g., apt: "96" -> apt: 96)
        val normalizedRtpMap = normalizeRtpNumbers(rtpMap) as? Map<String, Any?> ?: rtpMap
        
        // Add missing rtcpFeedback to codecs (native Android library strips these)
        val fixedRtpMap = addRtcpFeedbackToCodecs(normalizedRtpMap, produceData.kind.name)
        
        val payload = mutableMapOf<String, Any?>(
            "transportId" to transport.id,
            "kind" to produceData.kind.name.lowercase(),
            "rtpParameters" to fixedRtpMap,
            "islevel" to islevel,
            "name" to member
        )
        produceData.appData?.let { 
            payload["appData"] = it
        }

        try {
            socket.emitWithAck("transport-produce", payload) { response ->
                handleProduceAck(
                    response,
                    onSuccess = { id: String? -> produceData.callback(id) },
                    onError = { error: Throwable -> produceData.errback(error) }
                )
            }
        } catch (error: Exception) {
            produceData.errback(error)
        }
    }

    transport.onConnectionStateChange { state ->
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
