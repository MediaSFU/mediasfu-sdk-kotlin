package com.mediasfu.sdk.unity

import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import com.mediasfu.sdk.webrtc.ConnectData
import com.mediasfu.sdk.webrtc.DtlsFingerprint
import com.mediasfu.sdk.webrtc.DtlsParameters
import com.mediasfu.sdk.webrtc.DtlsRole
import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.RtcpFeedback
import com.mediasfu.sdk.webrtc.RtcpParameters
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.RtpCodecParameters
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.RtpHeaderExtensionParameters
import com.mediasfu.sdk.webrtc.RtpParameters
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.TransportType
import com.mediasfu.sdk.webrtc.WebRtcConsumer
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidUnityReceiveTransportBackendCoreTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun receiveTransportConnectLifecycle_returnsDtlsAndCompletesCallback() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport(id = "recv-transport-1", type = TransportType.RECEIVE)
        device.recvTransport = transport
        val backend = AndroidUnityReceiveTransportBackendCore(device)

        backend.initializeReceiveTransport(receiveTransportPayload())

        var callbackCount = 0
        var errbackMessage = ""
        transport.fireConnect(
            ConnectData(
                dtlsParameters = sampleDtlsParameters(),
                callback = { callbackCount += 1 },
                errback = { error -> errbackMessage = error.message ?: "" }
            )
        )

        val responseJson = backend.createReceiveTransportConnectParameters(remoteProducerPayload())
        assertTrue(responseJson.contains("dtlsParametersJson"))
        assertTrue(responseJson.contains("sha-256"))

        backend.completeReceiveTransportConnect(
            "{" +
                "\"remoteProducer\":{" +
                "\"producerId\":\"remote-producer-1\"}," +
                "\"success\":true," +
                "\"errorDetail\":\"\"}"
        )

        assertEquals(1, callbackCount)
        assertEquals("", errbackMessage)
    }

    @Test
    fun bindConsumer_andCloseConsumer_manageActiveConsumerLifecycle() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport(id = "recv-transport-1", type = TransportType.RECEIVE)
        device.recvTransport = transport
        val backend = AndroidUnityReceiveTransportBackendCore(device)

        backend.initializeReceiveTransport(receiveTransportPayload())
        transport.fireConnect(
            ConnectData(
                dtlsParameters = sampleDtlsParameters(),
                callback = { },
                errback = { error -> throw error }
            )
        )
        backend.createReceiveTransportConnectParameters(remoteProducerPayload())
        backend.completeReceiveTransportConnect(
            "{" +
                "\"remoteProducer\":{" +
                "\"producerId\":\"remote-producer-1\"}," +
                "\"success\":true," +
                "\"errorDetail\":\"\"}"
        )

        backend.bindConsumer(bindConsumerPayload())

        val consumer = assertNotNull(transport.lastConsumedConsumer)
        assertEquals("consumer-1", consumer.id)
        assertEquals("remote-producer-1", transport.lastConsumedProducerId)
        assertEquals("audio", transport.lastConsumedKind)

        backend.closeConsumer("{\"remoteProducerId\":\"remote-producer-1\"}")

        assertEquals(true, consumer.closed)
        assertEquals(true, transport.closed)
    }

    @Test
    fun createReceiveTransportConnectParameters_withoutInitialize_fails() {
        val backend = AndroidUnityReceiveTransportBackendCore(FakeWebRtcDevice())

        val error = assertFailsWith<IllegalStateException> {
            backend.createReceiveTransportConnectParameters(remoteProducerPayload())
        }

        assertTrue(error.message?.contains("initializeReceiveTransport") == true)
    }

    @Test
    fun loadDeviceRtpCapabilities_forwardsParsedCapabilities() = runTest {
        val device = FakeWebRtcDevice()
        val backend = AndroidUnityReceiveTransportBackendCore(device)
        val capabilities = sampleRtpCapabilities()

        backend.loadDeviceRtpCapabilities(
            "{" +
                "\"roomRtpCapabilitiesJson\":" + json.encodeToString(json.encodeToString(capabilities)) +
                "}"
        )

        assertEquals(1, device.loadCalls.size)
        assertEquals(capabilities, device.loadCalls.single())
    }

    private fun receiveTransportPayload(): String {
        val iceParametersJson = "{\"usernameFragment\":\"user\",\"password\":\"pass\",\"iceLite\":true}"
        val iceCandidatesJson = "[]"
        val dtlsParametersJson = "{\"role\":\"auto\",\"fingerprints\":[]}"
        return "{" +
            "\"remoteProducer\":{" +
            "\"producerId\":\"remote-producer-1\"}," +
            "\"transport\":{" +
            "\"id\":\"recv-transport-1\"," +
            "\"iceParametersJson\":" + json.encodeToString(iceParametersJson) + "," +
            "\"iceCandidatesJson\":" + json.encodeToString(iceCandidatesJson) + "," +
            "\"dtlsParametersJson\":" + json.encodeToString(dtlsParametersJson) + "," +
            "\"sctpParametersJson\":" + json.encodeToString("") + "," +
            "\"rawPayload\":" + json.encodeToString("{}") +
            "}}"
    }

    private fun remoteProducerPayload(): String {
        return "{\"remoteProducer\":{\"producerId\":\"remote-producer-1\"}}"
    }

    private fun bindConsumerPayload(): String {
        return "{" +
            "\"remoteProducer\":{" +
            "\"producerId\":\"remote-producer-1\"}," +
            "\"consumeResponse\":{" +
            "\"consumerId\":\"consumer-1\"," +
            "\"producerId\":\"remote-producer-1\"," +
            "\"kind\":\"audio\"," +
            "\"rtpParametersJson\":" + json.encodeToString(json.encodeToString(sampleRtpParameters())) + "," +
            "\"serverConsumerId\":\"server-consumer-1\"," +
            "\"rawPayload\":" + json.encodeToString("{}") +
            "}}"
    }

    private fun sampleRtpCapabilities(): RtpCapabilities {
        return RtpCapabilities(
            codecs = listOf(
                RtpCodecCapability(
                    kind = MediaKind.AUDIO,
                    mimeType = "audio/opus",
                    preferredPayloadType = 111,
                    clockRate = 48000,
                    channels = 2,
                    parameters = emptyMap(),
                    rtcpFeedback = listOf(RtcpFeedback(type = "nack", parameter = "pli"))
                )
            )
        )
    }

    private fun sampleDtlsParameters(): DtlsParameters {
        return DtlsParameters(
            role = DtlsRole.AUTO,
            fingerprints = listOf(
                DtlsFingerprint(
                    algorithm = "sha-256",
                    value = "AA:BB:CC"
                )
            )
        )
    }

    private fun sampleRtpParameters(): RtpParameters {
        return RtpParameters(
            mid = "0",
            codecs = listOf(
                RtpCodecParameters(
                    mimeType = "audio/opus",
                    payloadType = 111,
                    clockRate = 48000,
                    channels = 2,
                    parameters = emptyMap(),
                    rtcpFeedback = emptyList()
                )
            ),
            headerExtensions = listOf(
                RtpHeaderExtensionParameters(
                    uri = "urn:ietf:params:rtp-hdrext:sdes:mid",
                    id = 1,
                    encrypt = false,
                    parameters = emptyMap()
                )
            ),
            encodings = listOf(RtpEncodingParameters(ssrc = 1234L)),
            rtcp = RtcpParameters(cname = "cname", reducedSize = true, mux = true)
        )
    }

    private class FakeWebRtcDevice : WebRtcDevice {
        val loadCalls = mutableListOf<RtpCapabilities>()
        var recvTransport: FakeWebRtcTransport = FakeWebRtcTransport(
            id = "recv-transport-1",
            type = TransportType.RECEIVE
        )

        override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
            loadCalls += rtpCapabilities
            return Result.success(Unit)
        }

        override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun enumerateDevices(): List<MediaDeviceInfo> = emptyList()

        override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
            throw UnsupportedOperationException("Not needed in test")
        }

        override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
            return recvTransport
        }

        override fun close() {
        }
    }

    private class FakeWebRtcTransport(
        override val id: String,
        override val type: TransportType
    ) : WebRtcTransport {
        private var connectHandler: ((ConnectData) -> Unit)? = null

        override val connectionState: TransportConnectionState = TransportConnectionState.NEW

        var closed: Boolean = false
        var lastConsumedProducerId: String = ""
        var lastConsumedKind: String = ""
        var lastConsumedConsumer: FakeWebRtcConsumer? = null

        override fun close() {
            closed = true
        }

        override fun onConnect(handler: (ConnectData) -> Unit) {
            connectHandler = handler
        }

        override fun onProduce(handler: (com.mediasfu.sdk.webrtc.ProduceData) -> Unit) {
        }

        override fun onConnectionStateChange(handler: (String) -> Unit) {
        }

        override fun produce(
            track: MediaStreamTrack,
            encodings: List<RtpEncodingParameters>,
            codecOptions: ProducerCodecOptions?,
            appData: Map<String, Any?>?
        ): WebRtcProducer {
            throw UnsupportedOperationException("Not needed in test")
        }

        override fun consume(
            id: String,
            producerId: String,
            kind: String,
            rtpParameters: Map<String, Any?>
        ): WebRtcConsumer {
            lastConsumedProducerId = producerId
            lastConsumedKind = kind
            return FakeWebRtcConsumer(id = id, kind = if (kind == "video") MediaKind.VIDEO else MediaKind.AUDIO).also {
                lastConsumedConsumer = it
            }
        }

        fun fireConnect(connectData: ConnectData) {
            connectHandler?.invoke(connectData)
        }
    }

    private class FakeWebRtcConsumer(
        override val id: String,
        override val kind: MediaKind
    ) : WebRtcConsumer {
        override val track: MediaStreamTrack? = null
        override val stream: MediaStream? = null
        override var paused: Boolean = false
        var closed: Boolean = false

        override fun close() {
            closed = true
            paused = true
        }

        override fun pause() {
            paused = true
        }

        override fun resume() {
            paused = false
        }
    }
}