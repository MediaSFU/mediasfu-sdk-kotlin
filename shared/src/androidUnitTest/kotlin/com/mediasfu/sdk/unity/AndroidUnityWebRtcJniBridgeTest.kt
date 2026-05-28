package com.mediasfu.sdk.unity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidUnityWebRtcJniBridgeTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @AfterTest
    fun tearDown() {
        AndroidUnityWebRtcJniBridge.resetForTests()
    }

    @Test
    fun createInvokeDestroy_routesThroughInjectedRegistry() {
        val createdPayloads = mutableListOf<String>()
        val invokers = mutableListOf<FakeInvoker>()

        AndroidUnityWebRtcJniBridge.installRegistryFactoryForTests {
            AndroidUnityWebRtcEngineRegistry(createInvoker = { createPayloadJson: String ->
                createdPayloads += createPayloadJson
                FakeInvoker().also { invokers += it }
            })
        }

        val handle = AndroidUnityWebRtcJniBridge.createEngine("{\"integrationMode\":\"android\"}")
        assertTrue(handle > 0L)

        val invokeEnvelope = parseEnvelope(
            AndroidUnityWebRtcJniBridge.invoke(handle, "initializeSendTransport", "{\"transport\":{}}")
        )

        assertTrue(invokeEnvelope.success)
        assertEquals(listOf("{\"integrationMode\":\"android\"}"), createdPayloads)
        assertEquals(listOf("initializeSendTransport"), invokers.single().operations)
        assertEquals(listOf("{\"transport\":{}}"), invokers.single().payloads)

        AndroidUnityWebRtcJniBridge.destroyEngine(handle)

        val invalidEnvelope = parseEnvelope(
            AndroidUnityWebRtcJniBridge.invoke(handle, "initializeSendTransport", "{}")
        )
        assertEquals(false, invalidEnvelope.success)
        assertEquals("native_bridge_invalid_engine", invalidEnvelope.error)
    }

    @Test
    fun createEngine_failureStoresConsumableError() {
        AndroidUnityWebRtcJniBridge.installRegistryFactoryForTests {
            throw IllegalStateException("missing Android application context")
        }

        val handle = AndroidUnityWebRtcJniBridge.createEngine("{}")
        assertEquals(0L, handle)
        assertEquals(
            "missing Android application context",
            AndroidUnityWebRtcJniBridge.consumeLastCreateEngineError()
        )
        assertEquals("", AndroidUnityWebRtcJniBridge.consumeLastCreateEngineError())
    }

    private fun parseEnvelope(payload: String): OperationEnvelope {
        return OperationEnvelope(json.parseToJsonElement(payload).jsonObject)
    }

    private class OperationEnvelope(private val root: JsonObject) {
        val success: Boolean
            get() = root.getValue("success").jsonPrimitive.boolean

        val error: String
            get() = root.getValue("error").jsonPrimitive.content
    }

    private class FakeInvoker : AndroidUnityWebRtcOperationInvoker {
        val operations = mutableListOf<String>()
        val payloads = mutableListOf<String>()

        override fun invoke(operationName: String, payloadJson: String): String {
            operations += operationName
            payloads += payloadJson
            return "{\"success\":true,\"error\":\"\",\"detail\":\"\",\"result\":null}"
        }

        override fun describe(): String = "Fake JNI invoker"
    }
}