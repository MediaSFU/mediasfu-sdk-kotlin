package com.mediasfu.sdk.unity

import android.content.Context
import com.mediasfu.sdk.webrtc.AndroidWebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

internal interface AndroidUnityWebRtcOperationInvoker {
    fun invoke(operationName: String, payloadJson: String = ""): String

    fun describe(): String
}

internal class AndroidUnityWebRtcOperationHost internal constructor(
    private val sendBackend: AndroidUnitySendTransportBackendCore,
    private val receiveBackend: AndroidUnityReceiveTransportBackendCore,
    private val json: Json = DefaultJson
) : AndroidUnityWebRtcOperationInvoker {
    constructor(context: Context) : this(
        AndroidUnitySendTransportBackendCore(context),
        AndroidUnityReceiveTransportBackendCore(context)
    )

    constructor(device: WebRtcDevice) : this(
        AndroidUnitySendTransportBackendCore(device),
        AndroidUnityReceiveTransportBackendCore(device)
    )

    override fun invoke(operationName: String, payloadJson: String): String {
        return when (operationName) {
            OperationNames.DESCRIBE_BACKEND -> invokeJsonOperation {
                describeBackend()
            }

            OperationNames.LOAD_DEVICE_RTP_CAPABILITIES -> invokeVoidOperation {
                runBlocking {
                    sendBackend.loadDeviceRtpCapabilities(payloadJson)
                    receiveBackend.loadDeviceRtpCapabilities(payloadJson)
                }
            }

            OperationNames.INITIALIZE_SEND_TRANSPORT -> invokeVoidOperation {
                sendBackend.initializeSendTransport(payloadJson)
            }

            OperationNames.CREATE_SEND_TRANSPORT_CONNECT_PARAMETERS -> invokeJsonOperation {
                sendBackend.createSendTransportConnectParameters()
            }

            OperationNames.COMPLETE_SEND_TRANSPORT_CONNECT -> invokeVoidOperation {
                sendBackend.completeSendTransportConnect(payloadJson)
            }

            OperationNames.CREATE_PRODUCE_REQUEST -> invokeJsonOperation {
                sendBackend.createProduceRequest(payloadJson)
            }

            OperationNames.BIND_PRODUCER -> invokeVoidOperation {
                sendBackend.bindProducer(payloadJson)
            }

            OperationNames.PAUSE_PRODUCER -> invokeVoidOperation {
                sendBackend.pauseProducer(payloadJson)
            }

            OperationNames.RESUME_PRODUCER -> invokeVoidOperation {
                sendBackend.resumeProducer(payloadJson)
            }

            OperationNames.CLOSE_PRODUCER -> invokeVoidOperation {
                sendBackend.closeProducer(payloadJson)
            }

            OperationNames.INITIALIZE_RECEIVE_TRANSPORT -> invokeVoidOperation {
                receiveBackend.initializeReceiveTransport(payloadJson)
            }

            OperationNames.CREATE_RECEIVE_TRANSPORT_CONNECT_PARAMETERS -> invokeJsonOperation {
                receiveBackend.createReceiveTransportConnectParameters(payloadJson)
            }

            OperationNames.COMPLETE_RECEIVE_TRANSPORT_CONNECT -> invokeVoidOperation {
                receiveBackend.completeReceiveTransportConnect(payloadJson)
            }

            OperationNames.BIND_CONSUMER -> invokeVoidOperation {
                receiveBackend.bindConsumer(payloadJson)
            }

            OperationNames.CLOSE_CONSUMER -> invokeVoidOperation {
                receiveBackend.closeConsumer(payloadJson)
            }

            else -> unknownOperationResponse(operationName)
        }
    }

    override fun describe(): String {
        return "Android Unity WebRTC operation host backed by ${sendBackend.describe()} and ${receiveBackend.describe()}"
    }

    private fun describeBackend(): String {
        return json.encodeToString(
            BackendDescriptionResponse(
                description = describe(),
                backendKind = "installed",
                isPlaceholder = false,
                platform = "android",
                supportsAudio = true,
                supportsVideo = true,
                supportsScreenShare = false,
                supportsWhiteboard = false,
                supportsReceive = true
            )
        )
    }

    private inline fun invokeVoidOperation(block: () -> Unit): String {
        return try {
            block()
            successResponse(JsonNull)
        } catch (error: Throwable) {
            failedOperationResponse(error)
        }
    }

    private inline fun invokeJsonOperation(block: () -> String): String {
        return try {
            val resultJson = block()
            val result = resultJson.takeUnless { it.isBlank() }
                ?.let { json.parseToJsonElement(it) }
                ?: JsonNull
            successResponse(result)
        } catch (error: Throwable) {
            failedOperationResponse(error)
        }
    }

    private fun successResponse(result: JsonElement): String {
        return json.encodeToString(
            OperationResponseEnvelope(
                success = true,
                error = "",
                detail = "",
                result = result
            )
        )
    }

    private fun failedOperationResponse(error: Throwable): String {
        return json.encodeToString(
            OperationResponseEnvelope(
                success = false,
                error = ErrorCodes.OPERATION_FAILED,
                detail = error.message ?: "MediaSFU Android Unity operation host failed without an error detail.",
                result = JsonNull
            )
        )
    }

    private fun unknownOperationResponse(operationName: String): String {
        return json.encodeToString(
            OperationResponseEnvelope(
                success = false,
                error = ErrorCodes.UNKNOWN_OPERATION,
                detail = "${describe()} does not recognize operation $operationName.",
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

    @Serializable
    private data class BackendDescriptionResponse(
        val description: String,
        val backendKind: String,
        val isPlaceholder: Boolean,
        val platform: String,
        val supportsAudio: Boolean,
        val supportsVideo: Boolean,
        val supportsScreenShare: Boolean,
        val supportsWhiteboard: Boolean,
        val supportsReceive: Boolean
    )

    private object ErrorCodes {
        const val OPERATION_FAILED = "native_bridge_operation_failed"
        const val UNKNOWN_OPERATION = "native_bridge_unknown_operation"
    }

    private object OperationNames {
        const val DESCRIBE_BACKEND = "describeBackend"
        const val LOAD_DEVICE_RTP_CAPABILITIES = "loadDeviceRtpCapabilities"
        const val INITIALIZE_SEND_TRANSPORT = "initializeSendTransport"
        const val CREATE_SEND_TRANSPORT_CONNECT_PARAMETERS = "createSendTransportConnectParameters"
        const val COMPLETE_SEND_TRANSPORT_CONNECT = "completeSendTransportConnect"
        const val CREATE_PRODUCE_REQUEST = "createProduceRequest"
        const val BIND_PRODUCER = "bindProducer"
        const val PAUSE_PRODUCER = "pauseProducer"
        const val RESUME_PRODUCER = "resumeProducer"
        const val CLOSE_PRODUCER = "closeProducer"
        const val INITIALIZE_RECEIVE_TRANSPORT = "initializeReceiveTransport"
        const val CREATE_RECEIVE_TRANSPORT_CONNECT_PARAMETERS = "createReceiveTransportConnectParameters"
        const val COMPLETE_RECEIVE_TRANSPORT_CONNECT = "completeReceiveTransportConnect"
        const val BIND_CONSUMER = "bindConsumer"
        const val CLOSE_CONSUMER = "closeConsumer"
    }

    private companion object {
        val DefaultJson = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }
}