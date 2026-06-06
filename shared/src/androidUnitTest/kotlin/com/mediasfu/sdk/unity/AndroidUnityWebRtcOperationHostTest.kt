package com.mediasfu.sdk.unity

import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import com.mediasfu.sdk.webrtc.ConnectData
import com.mediasfu.sdk.webrtc.MediaDeviceInfo
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.ProduceData
import com.mediasfu.sdk.webrtc.RtcpParameters
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
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidUnityWebRtcOperationHostTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun invoke_describeBackend_returnsCapabilitiesEnvelope() {
        val host = AndroidUnityWebRtcOperationHost(FakeWebRtcDevice())

        val envelope = parseEnvelope(host.invoke("describeBackend", "{}"))

        assertTrue(envelope.success)
        assertEquals("", envelope.error)

        val result = envelope.requireResultObject()
        assertEquals("installed", result.getValue("backendKind").jsonPrimitive.content)
        assertEquals(false, result.getValue("isPlaceholder").jsonPrimitive.boolean)
        assertEquals("android", result.getValue("platform").jsonPrimitive.content)
        assertEquals(true, result.getValue("supportsAudio").jsonPrimitive.boolean)
        assertEquals(true, result.getValue("supportsVideo").jsonPrimitive.boolean)
        assertEquals(false, result.getValue("supportsScreenShare").jsonPrimitive.boolean)
        assertEquals(false, result.getValue("supportsWhiteboard").jsonPrimitive.boolean)
        assertEquals(true, result.getValue("supportsReceive").jsonPrimitive.boolean)
        assertTrue(result.getValue("description").jsonPrimitive.content.contains("Android Unity WebRTC operation host"))
    }

    @Test
    fun invoke_createProduceRequestAndBindProducer_returnsSuccessEnvelope() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport(id = "send-transport-1", type = TransportType.SEND)
        device.sendTransport = transport
        val host = AndroidUnityWebRtcOperationHost(device)

        val initializeEnvelope = parseEnvelope(
            host.invoke("initializeSendTransport", sendTransportPayload())
        )
        assertTrue(initializeEnvelope.success)

        val produceEnvelope = parseEnvelope(
            host.invoke("createProduceRequest", producePayload(trackKind = 0))
        )

        assertTrue(produceEnvelope.success)
        assertEquals("", produceEnvelope.error)
        val produceRequest = produceEnvelope.requireResultObject()["produceRequest"]?.jsonObject
        assertEquals("audio", produceRequest?.get("kind")?.jsonPrimitive?.content)
        assertEquals("participant-one", produceRequest?.get("name")?.jsonPrimitive?.content)

        val bindEnvelope = parseEnvelope(
            host.invoke("bindProducer", "{\"trackKind\":0,\"producerId\":\"producer-a1\"}")
        )

        assertTrue(bindEnvelope.success)
        assertEquals("producer-a1", transport.lastBoundProducerId)
        assertEquals(true, device.lastUserMediaConstraints["audio"])
        assertEquals(false, device.lastUserMediaConstraints["video"])
    }

    @Test
    fun invoke_pauseResumeCloseProducer_returnsSuccessEnvelopes() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport(id = "send-transport-1", type = TransportType.SEND)
        device.sendTransport = transport
        val host = AndroidUnityWebRtcOperationHost(device)

        parseEnvelope(host.invoke("initializeSendTransport", sendTransportPayload()))
        parseEnvelope(host.invoke("createProduceRequest", producePayload(trackKind = 0)))
        parseEnvelope(host.invoke("bindProducer", "{\"trackKind\":0,\"producerId\":\"producer-a1\"}"))

        val producer = assertNotNull(transport.lastProducedProducer)

        val pauseEnvelope = parseEnvelope(host.invoke("pauseProducer", "{\"trackKind\":0}"))
        assertTrue(pauseEnvelope.success)
        assertEquals(true, producer.paused)

        val resumeEnvelope = parseEnvelope(host.invoke("resumeProducer", "{\"trackKind\":0}"))
        assertTrue(resumeEnvelope.success)
        assertEquals(false, producer.paused)

        val closeEnvelope = parseEnvelope(host.invoke("closeProducer", "{\"trackKind\":0}"))
        assertTrue(closeEnvelope.success)
        assertEquals(true, producer.closed)
    }

    @Test
    fun invoke_receiveTransportLifecycle_returnsSuccessEnvelopes() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport(id = "recv-transport-1", type = TransportType.RECEIVE)
        device.recvTransport = transport
        val host = AndroidUnityWebRtcOperationHost(device)

        val initializeEnvelope = parseEnvelope(
            host.invoke("initializeReceiveTransport", receiveTransportPayload())
        )
        assertTrue(initializeEnvelope.success)

        transport.fireConnect(
            ConnectData(
                dtlsParameters = sampleDtlsParameters(),
                callback = { transport.connectCallbackCount += 1 },
                errback = { error -> transport.connectErrbackMessage = error.message ?: "" }
            )
        )

        val connectParametersEnvelope = parseEnvelope(
            host.invoke(
                "createReceiveTransportConnectParameters",
                "{\"remoteProducer\":{\"producerId\":\"remote-producer-1\"}}"
            )
        )
        assertTrue(connectParametersEnvelope.success)
        assertTrue(
            connectParametersEnvelope.requireResultObject()["dtlsParametersJson"]
                ?.jsonPrimitive
                ?.content
                ?.contains("sha-256") == true
        )

        val completeEnvelope = parseEnvelope(
            host.invoke(
                "completeReceiveTransportConnect",
                "{" +
                    "\"remoteProducer\":{" +
                    "\"producerId\":\"remote-producer-1\"}," +
                    "\"success\":true," +
                    "\"errorDetail\":\"\"}"
            )
        )
        assertTrue(completeEnvelope.success)
        assertEquals(1, transport.connectCallbackCount)
        assertEquals("", transport.connectErrbackMessage)

        val bindEnvelope = parseEnvelope(
            host.invoke("bindConsumer", bindConsumerPayload())
        )
        assertTrue(bindEnvelope.success)
        assertEquals("remote-producer-1", transport.lastConsumedProducerId)
        assertEquals("audio", transport.lastConsumedKind)
        assertEquals("consumer-1", transport.lastConsumedConsumer?.id)

        val closeEnvelope = parseEnvelope(
            host.invoke("closeConsumer", "{\"remoteProducerId\":\"remote-producer-1\"}")
        )

        assertTrue(closeEnvelope.success)
        assertEquals(true, transport.closed)
        assertEquals(true, transport.lastConsumedConsumer?.closed)
    }

    @Test
    fun invoke_unknownOperation_returnsUnknownOperationEnvelope() {
        val host = AndroidUnityWebRtcOperationHost(FakeWebRtcDevice())

        val envelope = parseEnvelope(host.invoke("doSomethingElse", "{}"))

        assertEquals(false, envelope.success)
        assertEquals("native_bridge_unknown_operation", envelope.error)
        assertTrue(envelope.detail.contains("does not recognize operation doSomethingElse"))
    }

    @Test
    fun invoke_backendFailure_returnsOperationFailedEnvelope() {
        val host = AndroidUnityWebRtcOperationHost(FakeWebRtcDevice())

        val envelope = parseEnvelope(host.invoke("createSendTransportConnectParameters", "{}"))

        assertEquals(false, envelope.success)
        assertEquals("native_bridge_operation_failed", envelope.error)
        assertTrue(envelope.detail.contains("initializeSendTransport"))
    }

    private fun parseEnvelope(payload: String): OperationEnvelope {
        return OperationEnvelope(json.parseToJsonElement(payload).jsonObject)
    }

    private fun sendTransportPayload(): String {
        val iceParametersJson = "{\"usernameFragment\":\"user\",\"password\":\"pass\",\"iceLite\":true}"
        val iceCandidatesJson = "[]"
        val dtlsParametersJson = "{\"role\":\"auto\",\"fingerprints\":[]}"
        return "{" +
            "\"transport\":{" +
            "\"id\":\"send-transport-1\"," +
            "\"iceParametersJson\":" + JsonPrimitive(iceParametersJson) + "," +
            "\"iceCandidatesJson\":" + JsonPrimitive(iceCandidatesJson) + "," +
            "\"dtlsParametersJson\":" + JsonPrimitive(dtlsParametersJson) + "," +
            "\"sctpParametersJson\":" + JsonPrimitive("") + "," +
            "\"rawPayload\":" + JsonPrimitive("{}") +
            "}}"
    }

    private fun producePayload(trackKind: Int): String {
        return "{" +
            "\"trackKind\":" + trackKind + "," +
            "\"enabled\":true," +
            "\"localUserName\":\"local-one\"," +
            "\"localIsLevel\":\"0\"," +
            "\"meetingId\":\"meeting-1\"," +
            "\"roomRtpCapabilitiesJson\":\"\"," +
            "\"secureCode\":\"\"," +
            "\"participantId\":\"participant-1\"," +
            "\"participantName\":\"participant-one\"," +
            "\"participantIslevel\":\"1\"" +
            "}"
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
            "\"iceParametersJson\":" + JsonPrimitive(iceParametersJson) + "," +
            "\"iceCandidatesJson\":" + JsonPrimitive(iceCandidatesJson) + "," +
            "\"dtlsParametersJson\":" + JsonPrimitive(dtlsParametersJson) + "," +
            "\"sctpParametersJson\":" + JsonPrimitive("") + "," +
            "\"rawPayload\":" + JsonPrimitive("{}") +
            "}}"
    }

    private fun bindConsumerPayload(): String {
        return "{" +
            "\"remoteProducer\":{" +
            "\"producerId\":\"remote-producer-1\"}," +
            "\"consumeResponse\":{" +
            "\"consumerId\":\"consumer-1\"," +
            "\"producerId\":\"remote-producer-1\"," +
            "\"kind\":\"audio\"," +
            "\"rtpParametersJson\":" + JsonPrimitive(sampleRtpParametersJson()) + "," +
            "\"serverConsumerId\":\"server-consumer-1\"," +
            "\"rawPayload\":" + JsonPrimitive("{}") +
            "}}"
    }

    private fun sampleDtlsParameters(): com.mediasfu.sdk.webrtc.DtlsParameters {
        return com.mediasfu.sdk.webrtc.DtlsParameters(
            role = com.mediasfu.sdk.webrtc.DtlsRole.AUTO,
            fingerprints = listOf(
                com.mediasfu.sdk.webrtc.DtlsFingerprint(
                    algorithm = "sha-256",
                    value = "AA:BB:CC"
                )
            )
        )
    }

    private fun sampleRtpParametersJson(): String {
        return "{" +
            "\"mid\":\"0\"," +
            "\"codecs\":[{" +
            "\"mimeType\":\"audio/opus\"," +
            "\"payloadType\":111," +
            "\"clockRate\":48000," +
            "\"channels\":2," +
            "\"parameters\":{}," +
            "\"rtcpFeedback\":[]" +
            "}]," +
            "\"headerExtensions\":[{" +
            "\"uri\":\"urn:ietf:params:rtp-hdrext:sdes:mid\"," +
            "\"id\":1," +
            "\"encrypt\":false," +
            "\"parameters\":{}" +
            "}]," +
            "\"encodings\":[{\"ssrc\":1234}]," +
            "\"rtcp\":{" +
            "\"cname\":\"cname\"," +
            "\"reducedSize\":true," +
            "\"mux\":true}}"
    }

    private class OperationEnvelope(private val root: JsonObject) {
        val success: Boolean
            get() = root.getValue("success").jsonPrimitive.boolean

        val error: String
            get() = root.getValue("error").jsonPrimitive.content

        val detail: String
            get() = root.getValue("detail").jsonPrimitive.content

        fun requireResultObject(): JsonObject {
            return root.getValue("result").jsonObject
        }
    }

    private class FakeWebRtcDevice : WebRtcDevice {
        var sendTransport: FakeWebRtcTransport = FakeWebRtcTransport(
            id = "send-transport-1",
            type = TransportType.SEND
        )
        var recvTransport: FakeWebRtcTransport = FakeWebRtcTransport(
            id = "recv-transport-1",
            type = TransportType.RECEIVE
        )
        var lastUserMediaConstraints: Map<String, Any?> = emptyMap()

        override suspend fun load(rtpCapabilities: com.mediasfu.sdk.webrtc.RtpCapabilities): Result<Unit> {
            return Result.success(Unit)
        }

        override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
            lastUserMediaConstraints = constraints
            val audioTracks = if (constraints["audio"] == true) {
                mutableListOf<MediaStreamTrack>(FakeMediaStreamTrack(kind = "audio"))
            } else {
                mutableListOf()
            }
            val videoTracks = if (constraints["video"] == true) {
                mutableListOf<MediaStreamTrack>(FakeMediaStreamTrack(kind = "video"))
            } else {
                mutableListOf()
            }
            return FakeMediaStream(audioTracks = audioTracks, videoTracks = videoTracks)
        }

        override suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream {
            throw UnsupportedOperationException("Not needed in test")
        }

        override suspend fun enumerateDevices(): List<MediaDeviceInfo> = emptyList()

        override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
            return sendTransport
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
        private var produceHandler: ((ProduceData) -> Unit)? = null

        override val connectionState: TransportConnectionState = TransportConnectionState.NEW

        var lastBoundProducerId: String = ""
        var lastProducedProducer: FakeWebRtcProducer? = null
        var lastConsumedProducerId: String = ""
        var lastConsumedKind: String = ""
        var lastConsumedConsumer: FakeWebRtcConsumer? = null
        var connectCallbackCount: Int = 0
        var connectErrbackMessage: String = ""
        var closed: Boolean = false

        override fun close() {
            closed = true
        }

        override fun onConnect(handler: (ConnectData) -> Unit) {
            connectHandler = handler
        }

        override fun onProduce(handler: (ProduceData) -> Unit) {
            produceHandler = handler
        }

        override fun onConnectionStateChange(handler: (String) -> Unit) {
        }

        override fun produce(
            track: MediaStreamTrack,
            encodings: List<RtpEncodingParameters>,
            codecOptions: ProducerCodecOptions?,
            codec: RtpCodecCapability?,
            appData: Map<String, Any?>?
        ): WebRtcProducer {
            if (type != TransportType.SEND) {
                throw IllegalStateException("produce called on non-send transport")
            }

            val completion = CompletableFuture<String>()
            val produceData = ProduceData(
                kind = if (track.kind == "video") MediaKind.VIDEO else MediaKind.AUDIO,
                rtpParameters = sampleRtpParameters(),
                appData = appData,
                callback = { producerId -> completion.complete(producerId.orEmpty()) },
                errback = { error -> completion.completeExceptionally(error) }
            )

            val handler = produceHandler ?: throw IllegalStateException("Produce handler was not registered")
            handler(produceData)

            val producerId = completion.get(1, TimeUnit.SECONDS)
            lastBoundProducerId = producerId
            return FakeWebRtcProducer(id = producerId, kind = produceData.kind).also {
                lastProducedProducer = it
            }
        }

        override fun consume(
            id: String,
            producerId: String,
            kind: String,
            rtpParameters: Map<String, Any?>
        ): WebRtcConsumer {
            if (type != TransportType.RECEIVE) {
                throw IllegalStateException("consume called on non-receive transport")
            }

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

    private class FakeMediaStream(
        private val audioTracks: MutableList<MediaStreamTrack> = mutableListOf(),
        private val videoTracks: MutableList<MediaStreamTrack> = mutableListOf()
    ) : MediaStream {
        override val id: String = "stream-1"
        override var active: Boolean = true

        override fun getTracks(): List<MediaStreamTrack> = audioTracks + videoTracks

        override fun getAudioTracks(): List<MediaStreamTrack> = audioTracks.toList()

        override fun getVideoTracks(): List<MediaStreamTrack> = videoTracks.toList()

        override fun addTrack(track: MediaStreamTrack) {
            if (track.kind == "audio") {
                audioTracks += track
            } else {
                videoTracks += track
            }
        }

        override fun removeTrack(track: MediaStreamTrack) {
            audioTracks.remove(track)
            videoTracks.remove(track)
        }

        override fun stop() {
            active = false
            audioTracks.forEach { it.stop() }
            videoTracks.forEach { it.stop() }
        }
    }

    private class FakeMediaStreamTrack(
        override val id: String = "track-1",
        override val kind: String
    ) : MediaStreamTrack {
        private var enabledState = true

        override val enabled: Boolean
            get() = enabledState

        override fun setEnabled(enabled: Boolean) {
            enabledState = enabled
        }

        override fun stop() {
            enabledState = false
        }
    }

    private class FakeWebRtcProducer(
        override val id: String,
        override val kind: MediaKind
    ) : WebRtcProducer {
        override val source = if (kind == MediaKind.AUDIO) {
            com.mediasfu.sdk.webrtc.ProducerSource.MICROPHONE
        } else {
            com.mediasfu.sdk.webrtc.ProducerSource.CAMERA
        }
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

        override fun replaceTrack(track: MediaStreamTrack) {
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

    private companion object {
        fun sampleRtpParameters(): RtpParameters {
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
    }
}
