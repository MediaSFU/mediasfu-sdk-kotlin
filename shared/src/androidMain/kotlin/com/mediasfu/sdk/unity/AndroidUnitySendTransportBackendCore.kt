package com.mediasfu.sdk.unity

import android.content.Context
import com.mediasfu.sdk.webrtc.AndroidWebRtcDevice
import com.mediasfu.sdk.webrtc.ConnectData
import com.mediasfu.sdk.webrtc.DtlsParameters
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.ProduceData
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpParameters
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

class AndroidUnitySendTransportBackendCore internal constructor(
    private val device: WebRtcDevice,
    private val json: Json = DefaultJson,
    private val connectEventTimeoutMs: Long = DEFAULT_CONNECT_EVENT_TIMEOUT_MS
) {
    private val stateLock = Any()
    private val produceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var loadedCapabilitiesCanonicalJson: String? = null
    private var sendTransportState: PendingSendTransportState? = null

    constructor(context: Context) : this(AndroidWebRtcDevice.getInstance(context))

    suspend fun loadDeviceRtpCapabilities(payloadJson: String) {
        val payload = decodePayload<LoadDeviceRtpCapabilitiesPayload>(payloadJson, "loadDeviceRtpCapabilities")
        val roomRtpCapabilitiesJson = payload.roomRtpCapabilitiesJson.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException(
                "loadDeviceRtpCapabilities requires a non-empty roomRtpCapabilitiesJson payload.")

        val canonicalJson = canonicalizeJson(roomRtpCapabilitiesJson)
        synchronized(stateLock) {
            val loadedCapabilities = loadedCapabilitiesCanonicalJson
            if (loadedCapabilities == canonicalJson) {
                return
            }

            if (loadedCapabilities != null && loadedCapabilities != canonicalJson) {
                throw IllegalStateException(
                    "AndroidUnitySendTransportBackendCore cannot reload different router RTP capabilities on the same backend instance.")
            }
        }

        val capabilities = runCatching {
            json.decodeFromString<RtpCapabilities>(roomRtpCapabilitiesJson)
        }.getOrElse { error ->
            throw IllegalArgumentException(
                "loadDeviceRtpCapabilities received invalid RTP capabilities JSON: ${error.message}",
                error)
        }

        device.load(capabilities).getOrElse { error ->
            throw IllegalStateException(
                "AndroidUnitySendTransportBackendCore failed to load router RTP capabilities: ${error.message}",
                error)
        }

        synchronized(stateLock) {
            loadedCapabilitiesCanonicalJson = canonicalJson
        }
    }

    fun initializeSendTransport(payloadJson: String) {
        val payload = decodePayload<InitializeSendTransportPayload>(payloadJson, "initializeSendTransport")
        val transport = payload.transport
        if (transport == null) {
            throw IllegalArgumentException("initializeSendTransport requires a transport payload.")
        }

        val previousState = synchronized(stateLock) {
            sendTransportState.also { sendTransportState = null }
        }
        previousState?.transport?.close()

        val nextState = PendingSendTransportState(
            transportId = transport.id.takeUnless { it.isBlank() }
                ?: throw IllegalArgumentException("initializeSendTransport requires a non-empty transport id."),
            transport = device.createSendTransport(buildSendTransportParams(transport))
        )

        nextState.transport.onConnect { connectData ->
            nextState.activeConnectData = connectData
            nextState.pendingConnectData.complete(connectData)
        }
        nextState.transport.onProduce { produceData ->
            val unityTrackKind = produceData.appData.toUnityTrackKindOrNull()
                ?: run {
                    produceData.errback(
                        IllegalStateException(
                            "AndroidUnitySendTransportBackendCore could not map produce appData to a Unity track kind."))
                    return@onProduce
                }

            val pendingProduceState = synchronized(stateLock) {
                sendTransportState
                    ?.takeIf { it.transportId == nextState.transportId }
                    ?.pendingProduceStates
                    ?.get(unityTrackKind)
            }

            if (pendingProduceState == null) {
                produceData.errback(
                    IllegalStateException(
                        "AndroidUnitySendTransportBackendCore did not find a pending produce request for ${unityTrackKind.mediaTag}."))
                return@onProduce
            }

            pendingProduceState.activeProduceData = produceData
            pendingProduceState.produceRequestReady.complete(
                CreateProduceRequestResponse(
                    produceRequest = UnityProduceRequestPayload(
                        kind = produceData.kind.name.lowercase(),
                        rtpParametersJson = json.encodeToString(RtpParameters.serializer(), produceData.rtpParameters),
                        appDataJson = produceData.appData?.toJsonString().orEmpty(),
                        name = pendingProduceState.participantName,
                        isLevel = pendingProduceState.participantIsLevel
                    )
                )
            )
        }
        nextState.transport.onConnectionStateChange { state ->
            if (state.equals(TransportConnectionState.CLOSED.name, ignoreCase = true) ||
                state.equals(TransportConnectionState.FAILED.name, ignoreCase = true)) {
                nextState.pendingConnectData.completeExceptionally(
                    IllegalStateException(
                        "Send transport ${nextState.transportId} entered $state before connect completion."))
            }
        }

        synchronized(stateLock) {
            sendTransportState = nextState
        }
    }

    fun createSendTransportConnectParameters(payloadJson: String = "{}") : String {
        val connectData = awaitSendConnectData("createSendTransportConnectParameters")
        return json.encodeToString(
            SendTransportConnectParametersResponse(
                dtlsParametersJson = json.encodeToString(DtlsParameters.serializer(), connectData.dtlsParameters)
            )
        )
    }

    fun completeSendTransportConnect(payloadJson: String) {
        val payload = decodePayload<CompleteTransportConnectPayload>(payloadJson, "completeSendTransportConnect")
        val state = requireSendTransportState("completeSendTransportConnect")
        val connectData = resolveActiveConnectData(state, "completeSendTransportConnect")

        state.activeConnectData = null
        if (payload.success) {
            connectData.callback()
            return
        }

        val detail = payload.errorDetail.takeUnless { it.isBlank() }
            ?: "Send transport connect failed."
        connectData.errback(IllegalStateException(detail))
    }

    fun createProduceRequest(payloadJson: String): String {
        val payload = parseCreateProduceRequestPayload(payloadJson)
        val trackKind = payload.trackKind

        val state = requireSendTransportState("createProduceRequest")
        val pendingProduceState = synchronized(stateLock) {
            if (state.activeProducers.containsKey(trackKind)) {
                throw IllegalStateException(
                    "createProduceRequest cannot start a second ${trackKind.mediaTag} producer while one is already active.")
            }

            state.pendingProduceStates[trackKind]?.let {
                throw IllegalStateException(
                    "createProduceRequest already has a pending ${trackKind.mediaTag} producer handshake.")
            }

            createPendingProduceState(trackKind, payload).also {
                state.pendingProduceStates[trackKind] = it
            }
        }

        produceScope.launch {
            runCatching {
                val producer = state.transport.produce(
                    track = pendingProduceState.track,
                    appData = buildProduceAppData(trackKind)
                )
                pendingProduceState.producerReady.complete(producer)
                synchronized(stateLock) {
                    state.pendingProduceStates.remove(trackKind)
                    state.activeProducers[trackKind] = ActiveProducerState(
                        producer = producer,
                        stream = pendingProduceState.stream,
                        track = pendingProduceState.track
                    )
                }
            }.onFailure { error ->
                pendingProduceState.produceRequestReady.completeExceptionally(error)
                pendingProduceState.producerReady.completeExceptionally(error)
                synchronized(stateLock) {
                    state.pendingProduceStates.remove(trackKind)
                }
                pendingProduceState.stream.stop()
            }
        }

        return try {
            json.encodeToString(
                pendingProduceState.produceRequestReady.get(produceEventTimeoutMs, TimeUnit.MILLISECONDS)
            )
        } catch (timeout: TimeoutException) {
            synchronized(stateLock) {
                state.pendingProduceStates.remove(trackKind)
            }
            pendingProduceState.stream.stop()
            throw IllegalStateException(
                "createProduceRequest timed out waiting for Android mediasoup produce parameters for ${trackKind.mediaTag}.",
                timeout)
        }
    }

    fun bindProducer(payloadJson: String) {
        val payload = parseBindProducerPayload(payloadJson)
        val state = requireSendTransportState("bindProducer")
        val pendingProduceState = synchronized(stateLock) {
            state.pendingProduceStates[payload.trackKind]
        } ?: throw IllegalStateException(
            "bindProducer requires an in-flight ${payload.trackKind.mediaTag} produce handshake.")

        val produceData = pendingProduceState.activeProduceData
            ?: try {
                pendingProduceState.produceRequestReady.get(produceEventTimeoutMs, TimeUnit.MILLISECONDS)
                pendingProduceState.activeProduceData
            } catch (timeout: TimeoutException) {
                null
            }
            ?: throw IllegalStateException(
                "bindProducer could not find pending produce callback data for ${payload.trackKind.mediaTag}.")

        produceData.callback(payload.producerId)

        try {
            pendingProduceState.producerReady.get(produceEventTimeoutMs, TimeUnit.MILLISECONDS)
        } catch (timeout: TimeoutException) {
            throw IllegalStateException(
                "bindProducer timed out waiting for Android mediasoup producer creation for ${payload.trackKind.mediaTag}.",
                timeout)
        }
    }

    fun pauseProducer(payloadJson: String) {
        val payload = parseTrackOperationPayload(payloadJson, "pauseProducer")
        requireActiveProducerState(payload.trackKind, "pauseProducer").producer.pause()
    }

    fun resumeProducer(payloadJson: String) {
        val payload = parseTrackOperationPayload(payloadJson, "resumeProducer")
        requireActiveProducerState(payload.trackKind, "resumeProducer").producer.resume()
    }

    fun closeProducer(payloadJson: String) {
        val payload = parseTrackOperationPayload(payloadJson, "closeProducer")
        val state = requireSendTransportState("closeProducer")
        val activeProducerState = synchronized(stateLock) {
            state.activeProducers.remove(payload.trackKind)
        } ?: throw IllegalStateException(
            "closeProducer requires an active ${payload.trackKind.mediaTag} producer.")

        activeProducerState.producer.close()
        activeProducerState.stream.stop()
    }

    fun describe(): String = "Android Unity send transport backend core"

    private fun createPendingProduceState(
        unityTrackKind: UnityTrackKind,
        payload: CreateProduceRequestPayload
    ): PendingProduceState {
        val stream = when (unityTrackKind) {
            UnityTrackKind.AUDIO -> runBlocking {
                device.getUserMedia(
                    mapOf(
                        "audio" to true,
                        "video" to false
                    )
                )
            }

            UnityTrackKind.VIDEO -> runBlocking {
                device.getUserMedia(
                    mapOf(
                        "audio" to false,
                        "video" to true
                    )
                )
            }

            UnityTrackKind.SCREEN -> throw UnsupportedOperationException(
                "AndroidUnitySendTransportBackendCore does not yet support screen-share capture because Android screen capture permission state must be provided by the host application.")

            UnityTrackKind.WHITEBOARD -> throw UnsupportedOperationException(
                "AndroidUnitySendTransportBackendCore does not yet support whiteboard track production.")
        }

        val track = when (unityTrackKind) {
            UnityTrackKind.AUDIO -> stream.getAudioTracks().firstOrNull()
            UnityTrackKind.VIDEO, UnityTrackKind.SCREEN, UnityTrackKind.WHITEBOARD -> stream.getVideoTracks().firstOrNull()
        } ?: run {
            stream.stop()
            throw IllegalStateException(
                "AndroidUnitySendTransportBackendCore did not receive a ${unityTrackKind.expectedNativeKind} track from the platform media stream.")
        }

        return PendingProduceState(
            unityTrackKind = unityTrackKind,
            participantName = payload.participantName.takeUnless { it.isBlank() }
                ?: payload.localUserName,
            participantIsLevel = payload.participantIsLevel.takeUnless { it.isBlank() }
                ?: payload.localIsLevel,
            stream = stream,
            track = track
        )
    }

    private fun buildProduceAppData(trackKind: UnityTrackKind): Map<String, Any?> {
        return mapOf(
            "mediaTag" to trackKind.mediaTag,
            "trackKind" to trackKind.wireValue
        )
    }

    private fun buildSendTransportParams(transport: UnityWebRtcTransportPayload): Map<String, Any?> {
        val params = linkedMapOf<String, Any?>(
            "id" to transport.id,
            "iceParameters" to decodeJsonValue(transport.iceParametersJson, "iceParametersJson"),
            "iceCandidates" to decodeJsonValue(transport.iceCandidatesJson, "iceCandidatesJson"),
            "dtlsParameters" to decodeJsonValue(transport.dtlsParametersJson, "dtlsParametersJson")
        )

        decodeJsonValueOrNull(transport.sctpParametersJson)?.let { params["sctpParameters"] = it }
        extractAppData(transport.rawPayload)?.let { params["appData"] = it }
        return params
    }

    private fun extractAppData(rawPayload: String): Any? {
        if (rawPayload.isBlank()) {
            return null
        }

        val root = runCatching { json.parseToJsonElement(rawPayload) }.getOrNull() as? JsonObject ?: return null
        return root["appData"]?.toDynamicValue()
    }

    private fun awaitSendConnectData(operationName: String): ConnectData {
        val state = requireSendTransportState(operationName)
        return resolveActiveConnectData(state, operationName)
    }

    private fun resolveActiveConnectData(
        state: PendingSendTransportState,
        operationName: String
    ): ConnectData {
        state.activeConnectData?.let { return it }

        return try {
            state.pendingConnectData.get(connectEventTimeoutMs, TimeUnit.MILLISECONDS).also {
                state.activeConnectData = it
            }
        } catch (timeout: TimeoutException) {
            throw IllegalStateException(
                "$operationName timed out waiting for Android mediasoup send transport connect parameters.",
                timeout)
        }
    }

    private fun requireSendTransportState(operationName: String): PendingSendTransportState {
        return synchronized(stateLock) {
            sendTransportState
        } ?: throw IllegalStateException(
            "$operationName requires initializeSendTransport to succeed first.")
    }

    private inline fun <reified T> decodePayload(payloadJson: String, operationName: String): T {
        val normalizedPayload = payloadJson.ifBlank { "{}" }
        return runCatching {
            json.decodeFromString<T>(normalizedPayload)
        }.getOrElse { error ->
            throw IllegalArgumentException(
                "$operationName received invalid JSON payload: ${error.message}",
                error)
        }
    }

    private fun canonicalizeJson(payload: String): String {
        return json.parseToJsonElement(payload).toString()
    }

    private fun decodeJsonValue(payload: String, fieldName: String): Any? {
        return decodeJsonValueOrNull(payload)
            ?: throw IllegalArgumentException("initializeSendTransport requires a non-empty $fieldName value.")
    }

    private fun decodeJsonValueOrNull(payload: String): Any? {
        if (payload.isBlank()) {
            return null
        }

        return json.parseToJsonElement(payload).toDynamicValue()
    }

    private fun parseCreateProduceRequestPayload(payloadJson: String): CreateProduceRequestPayload {
        val root = parsePayloadObject(payloadJson, "createProduceRequest")
        return CreateProduceRequestPayload(
            trackKind = UnityTrackKind.fromWireValue(root.requiredInt("trackKind", "createProduceRequest")),
            enabled = root["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            localUserName = root.optionalString("localUserName"),
            localIsLevel = root.optionalString("localIsLevel"),
            participantName = root.optionalString("participantName"),
            participantIsLevel = root.optionalString("participantIslevel")
        )
    }

    private fun parseBindProducerPayload(payloadJson: String): BindProducerPayload {
        val root = parsePayloadObject(payloadJson, "bindProducer")
        return BindProducerPayload(
            trackKind = UnityTrackKind.fromWireValue(root.requiredInt("trackKind", "bindProducer")),
            producerId = root.optionalString("producerId").takeUnless { it.isBlank() }
                ?: throw IllegalArgumentException("bindProducer requires a non-empty producerId.")
        )
    }

    private fun parseTrackOperationPayload(
        payloadJson: String,
        operationName: String
    ): TrackOperationPayload {
        val root = parsePayloadObject(payloadJson, operationName)
        return TrackOperationPayload(
            trackKind = UnityTrackKind.fromWireValue(root.requiredInt("trackKind", operationName))
        )
    }

    private fun parsePayloadObject(payloadJson: String, operationName: String): JsonObject {
        val normalizedPayload = payloadJson.ifBlank { "{}" }
        return json.parseToJsonElement(normalizedPayload) as? JsonObject
            ?: throw IllegalArgumentException("$operationName requires a JSON object payload.")
    }

    private fun JsonElement.toDynamicValue(): Any? = when (this) {
        JsonNull -> null
        is JsonObject -> entries.associate { (key, value) -> key to value.toDynamicValue() }
        is JsonArray -> map { it.toDynamicValue() }
        is JsonPrimitive -> when {
            isString -> content
            booleanOrNull != null -> booleanOrNull
            longOrNull != null -> longOrNull
            doubleOrNull != null -> doubleOrNull
            else -> content
        }
    }

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Int -> JsonPrimitive(this)
        is Long -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is Float -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this.toDouble())
        is Map<*, *> -> JsonObject(entries.associate { (key, value) ->
            val normalizedKey = key as? String
                ?: throw IllegalArgumentException("JSON object keys must be strings. Found ${key?.javaClass?.simpleName ?: "null"}.")
            normalizedKey to value.toJsonElement()
        })
        is Iterable<*> -> JsonArray(map { it.toJsonElement() })
        is Array<*> -> JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    private fun Map<String, Any?>.toJsonString(): String {
        return JsonObject(entries.associate { (key, value) -> key to value.toJsonElement() }).toString()
    }

    private fun Map<String, Any?>?.toUnityTrackKindOrNull(): UnityTrackKind? {
        val value = this?.get("trackKind") ?: this?.get("mediaTag")
        return when (value) {
            is Number -> UnityTrackKind.fromWireValueOrNull(value.toInt())
            is String -> UnityTrackKind.fromMediaTag(value)
            else -> null
        }
    }

    private fun JsonObject.optionalString(name: String): String {
        return this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun JsonObject.requiredInt(name: String, operationName: String): Int {
        return this[name]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("$operationName requires an integer $name field.")
    }

    private fun requireActiveProducerState(
        trackKind: UnityTrackKind,
        operationName: String
    ): ActiveProducerState {
        val state = requireSendTransportState(operationName)
        return synchronized(stateLock) {
            state.activeProducers[trackKind]
        } ?: throw IllegalStateException(
            "$operationName requires an active ${trackKind.mediaTag} producer.")
    }

    private data class PendingSendTransportState(
        val transportId: String,
        val transport: WebRtcTransport,
        val pendingConnectData: CompletableFuture<ConnectData> = CompletableFuture(),
        @Volatile var activeConnectData: ConnectData? = null,
        val pendingProduceStates: MutableMap<UnityTrackKind, PendingProduceState> = mutableMapOf(),
        val activeProducers: MutableMap<UnityTrackKind, ActiveProducerState> = mutableMapOf()
    )

    private data class PendingProduceState(
        val unityTrackKind: UnityTrackKind,
        val participantName: String,
        val participantIsLevel: String,
        val stream: MediaStream,
        val track: MediaStreamTrack,
        val produceRequestReady: CompletableFuture<CreateProduceRequestResponse> = CompletableFuture(),
        val producerReady: CompletableFuture<WebRtcProducer> = CompletableFuture(),
        @Volatile var activeProduceData: ProduceData? = null
    )

    private data class ActiveProducerState(
        val producer: WebRtcProducer,
        val stream: MediaStream,
        val track: MediaStreamTrack
    )

    @Serializable
    private data class LoadDeviceRtpCapabilitiesPayload(
        val roomRtpCapabilitiesJson: String = ""
    )

    @Serializable
    private data class InitializeSendTransportPayload(
        val transport: UnityWebRtcTransportPayload? = null
    )

    @Serializable
    private data class UnityWebRtcTransportPayload(
        val id: String = "",
        val iceParametersJson: String = "",
        val iceCandidatesJson: String = "",
        val dtlsParametersJson: String = "",
        val sctpParametersJson: String = "",
        val rawPayload: String = ""
    )

    @Serializable
    private data class SendTransportConnectParametersResponse(
        val dtlsParametersJson: String = ""
    )

    @Serializable
    private data class CompleteTransportConnectPayload(
        val success: Boolean = false,
        val errorDetail: String = ""
    )

    private data class CreateProduceRequestPayload(
        val trackKind: UnityTrackKind,
        val enabled: Boolean,
        val localUserName: String,
        val localIsLevel: String,
        val participantName: String,
        val participantIsLevel: String
    )

    private data class BindProducerPayload(
        val trackKind: UnityTrackKind,
        val producerId: String
    )

    private data class TrackOperationPayload(
        val trackKind: UnityTrackKind
    )

    @Serializable
    private data class CreateProduceRequestResponse(
        val produceRequest: UnityProduceRequestPayload
    )

    @Serializable
    private data class UnityProduceRequestPayload(
        val kind: String = "",
        val rtpParametersJson: String = "",
        val appDataJson: String = "",
        val name: String = "",
        val isLevel: String = ""
    )

    private enum class UnityTrackKind(
        val wireValue: Int,
        val mediaTag: String,
        val expectedNativeKind: String
    ) {
        AUDIO(0, "audio", "audio"),
        VIDEO(1, "video", "video"),
        SCREEN(2, "screen", "video"),
        WHITEBOARD(3, "whiteboard", "video");

        companion object {
            fun fromWireValue(value: Int): UnityTrackKind {
                return fromWireValueOrNull(value)
                    ?: throw IllegalArgumentException("Unsupported Unity trackKind value: $value")
            }

            fun fromWireValueOrNull(value: Int): UnityTrackKind? {
                return entries.firstOrNull { it.wireValue == value }
            }

            fun fromMediaTag(value: String): UnityTrackKind? {
                return entries.firstOrNull { it.mediaTag.equals(value, ignoreCase = true) }
            }
        }
    }

    private companion object {
        const val DEFAULT_CONNECT_EVENT_TIMEOUT_MS = 5000L
        const val produceEventTimeoutMs = 5000L

        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}