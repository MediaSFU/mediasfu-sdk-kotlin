package com.mediasfu.sdk.unity

import android.content.Context
import com.mediasfu.sdk.webrtc.WebRtcDevice
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal class AndroidUnityWebRtcEngineRegistry internal constructor(
    private val createInvoker: (String) -> AndroidUnityWebRtcOperationInvoker,
    private val json: Json = DefaultJson
) {
    private val stateLock = Any()
    private val nextHandle = AtomicLong(1L)
    private val invokers = mutableMapOf<Long, AndroidUnityWebRtcOperationInvoker>()

    constructor(context: Context) : this(
        createInvoker = { AndroidUnityWebRtcOperationHost(context) }
    )

    constructor(deviceFactory: () -> WebRtcDevice) : this(
        createInvoker = { AndroidUnityWebRtcOperationHost(deviceFactory()) }
    )

    fun createEngine(payloadJson: String = ""): Long {
        val invoker = createInvoker(payloadJson)
        val handle = nextHandle.getAndIncrement()

        synchronized(stateLock) {
            invokers[handle] = invoker
        }

        return handle
    }

    fun destroyEngine(handle: Long) {
        synchronized(stateLock) {
            invokers.remove(handle)
        }
    }

    fun invoke(handle: Long, operationName: String, payloadJson: String = ""): String {
        val invoker = synchronized(stateLock) {
            invokers[handle]
        } ?: return failureResponse(
            error = ErrorCodes.INVALID_ENGINE,
            detail = "MediaSFU Android Unity engine registry received an unknown engine handle. Create the engine before invoking WebRTC operations."
        )

        if (operationName.isBlank()) {
            return failureResponse(
                error = ErrorCodes.INVALID_OPERATION,
                detail = "MediaSFU Android Unity engine registry received an empty operation name."
            )
        }

        return invoker.invoke(operationName, payloadJson)
    }

    private fun failureResponse(error: String, detail: String): String {
        return json.encodeToString(
            OperationResponseEnvelope(
                success = false,
                error = error,
                detail = detail,
                result = JsonNull
            )
        )
    }

    @Serializable
    private data class OperationResponseEnvelope(
        val success: Boolean,
        val error: String,
        val detail: String,
        val result: JsonElement = JsonNull
    )

    private object ErrorCodes {
        const val INVALID_ENGINE = "native_bridge_invalid_engine"
        const val INVALID_OPERATION = "native_bridge_invalid_operation"
    }

    private companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}