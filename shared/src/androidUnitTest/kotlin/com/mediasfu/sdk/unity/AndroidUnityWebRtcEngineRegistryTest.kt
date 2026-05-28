package com.mediasfu.sdk.unity

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidUnityWebRtcEngineRegistryTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun createInvokeDestroy_routesThroughRegisteredInvoker() {
        val createdPayloads = mutableListOf<String>()
        val invokers = mutableListOf<FakeInvoker>()
        val registry = AndroidUnityWebRtcEngineRegistry(createInvoker = { createPayloadJson: String ->
            createdPayloads += createPayloadJson
            FakeInvoker().also { invokers += it }
        })

        val handle = registry.createEngine("{\"integrationMode\":\"android\"}")
        val invokeEnvelope = parseEnvelope(registry.invoke(handle, "initializeSendTransport", "{\"transport\":{}}"))

        assertTrue(invokeEnvelope.success)
        assertEquals(listOf("{\"integrationMode\":\"android\"}"), createdPayloads)
        assertEquals(listOf("initializeSendTransport"), invokers.single().operations)
        assertEquals(listOf("{\"transport\":{}}"), invokers.single().payloads)

        registry.destroyEngine(handle)

        val invalidEnvelope = parseEnvelope(registry.invoke(handle, "initializeSendTransport", "{}"))
        assertEquals(false, invalidEnvelope.success)
        assertEquals("native_bridge_invalid_engine", invalidEnvelope.error)
    }

    @Test
    fun invoke_withBlankOperation_returnsInvalidOperationEnvelope() {
        val registry = AndroidUnityWebRtcEngineRegistry(createInvoker = { _: String -> FakeInvoker() })
        val handle = registry.createEngine("{}")

        val envelope = parseEnvelope(registry.invoke(handle, "", "{}"))

        assertEquals(false, envelope.success)
        assertEquals("native_bridge_invalid_operation", envelope.error)
        assertTrue(envelope.detail.contains("empty operation name"))
    }

    private fun parseEnvelope(payload: String): OperationEnvelope {
        return OperationEnvelope(json.parseToJsonElement(payload).jsonObject)
    }

    private class OperationEnvelope(private val root: JsonObject) {
        val success: Boolean
            get() = root.getValue("success").jsonPrimitive.boolean

        val error: String
            get() = root.getValue("error").jsonPrimitive.content

        val detail: String
            get() = root.getValue("detail").jsonPrimitive.content
    }

    private class FakeInvoker : AndroidUnityWebRtcOperationInvoker {
        val operations = mutableListOf<String>()
        val payloads = mutableListOf<String>()

        override fun invoke(operationName: String, payloadJson: String): String {
            operations += operationName
            payloads += payloadJson
            return "{\"success\":true,\"error\":\"\",\"detail\":\"\",\"result\":null}"
        }

        override fun describe(): String = "Fake Android Unity invoker"
    }
}