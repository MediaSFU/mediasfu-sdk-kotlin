package com.mediasfu.sdk.unity

import android.content.Context
import com.mediasfu.sdk.webrtc.AndroidWebRtcDevice
import com.mediasfu.sdk.webrtc.ConnectData
import com.mediasfu.sdk.webrtc.DtlsParameters
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.WebRtcConsumer
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcTransport
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
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
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

class AndroidUnityReceiveTransportBackendCore internal constructor(
    private val device: WebRtcDevice,
    private val json: Json = DefaultJson,
    private val connectEventTimeoutMs: Long = DEFAULT_CONNECT_EVENT_TIMEOUT_MS
) {
    private val stateLock = Any()

    private var loadedCapabilitiesCanonicalJson: String? = null
    private val receiveTransportStates = mutableMapOf<String, PendingReceiveTransportState>()

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
                    "AndroidUnityReceiveTransportBackendCore cannot reload different router RTP capabilities on the same backend instance.")
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
                "AndroidUnityReceiveTransportBackendCore failed to load router RTP capabilities: ${error.message}",
                error)
        }

        synchronized(stateLock) {
            loadedCapabilitiesCanonicalJson = canonicalJson
        }
    }

    fun initializeReceiveTransport(payloadJson: String) {
        val payload = decodePayload<InitializeReceiveTransportPayload>(payloadJson, "initializeReceiveTransport")
        val remoteProducerId = payload.remoteProducer?.producerId?.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException(
                "initializeReceiveTransport requires a remoteProducer with a non-empty producerId.")
        val transport = payload.transport
            ?: throw IllegalArgumentException("initializeReceiveTransport requires a transport payload.")

        val previousState = synchronized(stateLock) {
            receiveTransportStates.remove(remoteProducerId)
        }
        previousState?.close()

        val nextState = PendingReceiveTransportState(
            remoteProducerId = remoteProducerId,
            transportId = transport.id.takeUnless { it.isBlank() }
                ?: throw IllegalArgumentException(
                    "initializeReceiveTransport requires a transport payload with a non-empty id."),
            transport = device.createRecvTransport(buildReceiveTransportParams(transport))
        )

        nextState.transport.onConnect { connectData ->
            nextState.activeConnectData = connectData
            nextState.pendingConnectData.complete(connectData)
        }
        nextState.transport.onConnectionStateChange { state ->
            if (state.equals(TransportConnectionState.CLOSED.name, ignoreCase = true) ||
                state.equals(TransportConnectionState.FAILED.name, ignoreCase = true)) {
                nextState.pendingConnectData.completeExceptionally(
                    IllegalStateException(
                        "Receive transport ${nextState.transportId} for remote producer $remoteProducerId entered $state before connect completion."))
            }
        }

        synchronized(stateLock) {
            receiveTransportStates[remoteProducerId] = nextState
        }
    }

    fun createReceiveTransportConnectParameters(payloadJson: String): String {
        val remoteProducerId = parseRemoteProducerId(payloadJson, "createReceiveTransportConnectParameters")
        val connectData = awaitReceiveConnectData(remoteProducerId, "createReceiveTransportConnectParameters")
        return json.encodeToString(
            ReceiveTransportConnectParametersResponse(
                dtlsParametersJson = json.encodeToString(DtlsParameters.serializer(), connectData.dtlsParameters)
            )
        )
    }

    fun completeReceiveTransportConnect(payloadJson: String) {
        val payload = decodePayload<CompleteReceiveTransportConnectPayload>(
            payloadJson,
            "completeReceiveTransportConnect"
        )
        val remoteProducerId = payload.remoteProducer?.producerId?.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException(
                "completeReceiveTransportConnect requires a remoteProducer with a non-empty producerId.")
        val state = requireReceiveTransportState(remoteProducerId, "completeReceiveTransportConnect")
        val connectData = resolveActiveConnectData(
            state,
            remoteProducerId,
            "completeReceiveTransportConnect"
        )

        state.activeConnectData = null
        if (payload.success) {
            connectData.callback()
            return
        }

        val detail = payload.errorDetail.takeUnless { it.isBlank() }
            ?: "Receive transport connect failed."
        connectData.errback(IllegalStateException(detail))
    }

    fun bindConsumer(payloadJson: String) {
        val payload = decodePayload<BindConsumerPayload>(payloadJson, "bindConsumer")
        val remoteProducerId = payload.remoteProducer?.producerId?.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException(
                "bindConsumer requires a remoteProducer with a non-empty producerId.")
        val consumeResponse = payload.consumeResponse
            ?: throw IllegalArgumentException("bindConsumer requires a consumeResponse payload.")
        val state = requireReceiveTransportState(remoteProducerId, "bindConsumer")

        synchronized(stateLock) {
            if (state.activeConsumerState != null) {
                throw IllegalStateException(
                    "bindConsumer already has an active consumer for remote producer $remoteProducerId.")
            }
        }

        val consumerId = consumeResponse.consumerId.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException("bindConsumer requires a non-empty consumeResponse.consumerId.")
        val producerId = consumeResponse.producerId.takeUnless { it.isBlank() } ?: remoteProducerId
        val kind = consumeResponse.kind.takeUnless { it.isBlank() }
            ?.lowercase()
            ?: throw IllegalArgumentException("bindConsumer requires a non-empty consumeResponse.kind.")
        val rtpParameters = decodeJsonObjectValue(
            consumeResponse.rtpParametersJson,
            "consumeResponse.rtpParametersJson",
            "bindConsumer"
        )

        val consumer = state.transport.consume(
            id = consumerId,
            producerId = producerId,
            kind = kind,
            rtpParameters = rtpParameters
        )

        synchronized(stateLock) {
            state.activeConsumerState = ActiveConsumerState(consumer)
        }
    }

    fun closeConsumer(payloadJson: String) {
        val payload = decodePayload<CloseConsumerPayload>(payloadJson, "closeConsumer")
        val remoteProducerId = payload.remoteProducerId.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException("closeConsumer requires a non-empty remoteProducerId.")
        val state = synchronized(stateLock) {
            receiveTransportStates.remove(remoteProducerId)
        } ?: return

        state.close()
    }

    fun describe(): String = "Android Unity receive transport backend core"

    private fun buildReceiveTransportParams(transport: UnityWebRtcTransportPayload): Map<String, Any?> {
        val params = linkedMapOf<String, Any?>(
            "id" to transport.id,
            "iceParameters" to decodeJsonValue(transport.iceParametersJson, "iceParametersJson", "initializeReceiveTransport"),
            "iceCandidates" to decodeJsonValue(transport.iceCandidatesJson, "iceCandidatesJson", "initializeReceiveTransport"),
            "dtlsParameters" to decodeJsonValue(transport.dtlsParametersJson, "dtlsParametersJson", "initializeReceiveTransport")
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

    private fun awaitReceiveConnectData(remoteProducerId: String, operationName: String): ConnectData {
        val state = requireReceiveTransportState(remoteProducerId, operationName)
        return resolveActiveConnectData(state, remoteProducerId, operationName)
    }

    private fun resolveActiveConnectData(
        state: PendingReceiveTransportState,
        remoteProducerId: String,
        operationName: String
    ): ConnectData {
        state.activeConnectData?.let { return it }

        return try {
            state.pendingConnectData.get(connectEventTimeoutMs, TimeUnit.MILLISECONDS).also {
                state.activeConnectData = it
            }
        } catch (timeout: TimeoutException) {
            throw IllegalStateException(
                "$operationName timed out waiting for Android mediasoup receive transport connect parameters for remote producer $remoteProducerId.",
                timeout)
        }
    }

    private fun requireReceiveTransportState(
        remoteProducerId: String,
        operationName: String
    ): PendingReceiveTransportState {
        return synchronized(stateLock) {
            receiveTransportStates[remoteProducerId]
        } ?: throw IllegalStateException(
            "$operationName requires initializeReceiveTransport to succeed first for remote producer $remoteProducerId.")
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

    private fun parseRemoteProducerId(payloadJson: String, operationName: String): String {
        val payload = decodePayload<RemoteProducerPayloadEnvelope>(payloadJson, operationName)
        return payload.remoteProducer?.producerId?.takeUnless { it.isBlank() }
            ?: throw IllegalArgumentException(
                "$operationName requires a remoteProducer with a non-empty producerId.")
    }

    private fun canonicalizeJson(payload: String): String {
        return json.parseToJsonElement(payload).toString()
    }

    private fun decodeJsonValue(payload: String, fieldName: String, operationName: String): Any? {
        return decodeJsonValueOrNull(payload)
            ?: throw IllegalArgumentException(
                "$operationName requires a non-empty $fieldName value.")
    }

    private fun decodeJsonObjectValue(payload: String, fieldName: String, operationName: String): Map<String, Any?> {
        return decodeJsonValue(payload, fieldName, operationName) as? Map<String, Any?>
            ?: throw IllegalArgumentException(
                "$operationName requires $fieldName to decode to a JSON object.")
    }

    private fun decodeJsonValueOrNull(payload: String): Any? {
        if (payload.isBlank()) {
            return null
        }

        return json.parseToJsonElement(payload).toDynamicValue()
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

    private data class PendingReceiveTransportState(
        val remoteProducerId: String,
        val transportId: String,
        val transport: WebRtcTransport,
        val pendingConnectData: CompletableFuture<ConnectData> = CompletableFuture(),
        @Volatile var activeConnectData: ConnectData? = null,
        @Volatile var activeConsumerState: ActiveConsumerState? = null
    ) {
        fun close() {
            activeConsumerState?.consumer?.close()
            transport.close()
        }
    }

    private data class ActiveConsumerState(
        val consumer: WebRtcConsumer
    )

    @Serializable
    private data class LoadDeviceRtpCapabilitiesPayload(
        val roomRtpCapabilitiesJson: String = ""
    )

    @Serializable
    private data class InitializeReceiveTransportPayload(
        val remoteProducer: UnityRemoteProducerPayload? = null,
        val transport: UnityWebRtcTransportPayload? = null
    )

    @Serializable
    private data class RemoteProducerPayloadEnvelope(
        val remoteProducer: UnityRemoteProducerPayload? = null
    )

    @Serializable
    private data class CompleteReceiveTransportConnectPayload(
        val remoteProducer: UnityRemoteProducerPayload? = null,
        val success: Boolean = false,
        val errorDetail: String = ""
    )

    @Serializable
    private data class BindConsumerPayload(
        val remoteProducer: UnityRemoteProducerPayload? = null,
        val consumeResponse: UnityConsumeResponsePayload? = null
    )

    @Serializable
    private data class CloseConsumerPayload(
        val remoteProducerId: String = ""
    )

    @Serializable
    private data class UnityRemoteProducerPayload(
        val producerId: String = ""
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
    private data class UnityConsumeResponsePayload(
        val consumerId: String = "",
        val producerId: String = "",
        val kind: String = "",
        val rtpParametersJson: String = "",
        val serverConsumerId: String = "",
        val rawPayload: String = ""
    )

    @Serializable
    private data class ReceiveTransportConnectParametersResponse(
        val dtlsParametersJson: String = ""
    )

    private companion object {
        const val DEFAULT_CONNECT_EVENT_TIMEOUT_MS = 5000L

        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}