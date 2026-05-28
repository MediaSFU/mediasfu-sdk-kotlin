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
import com.mediasfu.sdk.webrtc.ProduceData
import com.mediasfu.sdk.webrtc.RtcpFeedback
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.RtpCodecParameters
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.RtpParameters
import com.mediasfu.sdk.webrtc.RtpHeaderExtensionParameters
import com.mediasfu.sdk.webrtc.RtcpParameters
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.TransportType
import com.mediasfu.sdk.webrtc.WebRtcConsumer
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AndroidUnitySendTransportBackendCoreTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun loadDeviceRtpCapabilities_forwardsParsedCapabilities() = runTest {
        val device = FakeWebRtcDevice()
        val backend = AndroidUnitySendTransportBackendCore(device)
        val capabilities = sampleRtpCapabilities()

        backend.loadDeviceRtpCapabilities(
            "{" +
                "\"roomRtpCapabilitiesJson\":" + json.encodeToString(json.encodeToString(capabilities)) +
                "}"
        )

        assertEquals(1, device.loadCalls.size)
        assertEquals(capabilities, device.loadCalls.single())
    }

    @Test
    fun sendTransportConnectLifecycle_returnsDtlsAndCompletesCallback() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())

        var callbackCount = 0
        var errbackMessage = ""
        transport.fireConnect(
            ConnectData(
                dtlsParameters = sampleDtlsParameters(),
                callback = { callbackCount += 1 },
                errback = { error -> errbackMessage = error.message ?: "" }
            )
        )

        val responseJson = backend.createSendTransportConnectParameters()
        assertTrue(responseJson.contains("dtlsParametersJson"))
        assertTrue(responseJson.contains("sha-256"))

        backend.completeSendTransportConnect("{\"success\":true,\"errorDetail\":\"\"}")

        assertEquals(1, callbackCount)
        assertEquals("", errbackMessage)
    }

    @Test
    fun completeSendTransportConnect_withFailure_callsErrback() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())

        var callbackCount = 0
        var errbackMessage = ""
        transport.fireConnect(
            ConnectData(
                dtlsParameters = sampleDtlsParameters(),
                callback = { callbackCount += 1 },
                errback = { error -> errbackMessage = error.message ?: "" }
            )
        )

        backend.createSendTransportConnectParameters()
        backend.completeSendTransportConnect("{\"success\":false,\"errorDetail\":\"connect failed\"}")

        assertEquals(0, callbackCount)
        assertEquals("connect failed", errbackMessage)
    }

    @Test
    fun createSendTransportConnectParameters_withoutInitialize_fails() {
        val backend = AndroidUnitySendTransportBackendCore(FakeWebRtcDevice())

        val error = assertFailsWith<IllegalStateException> {
            backend.createSendTransportConnectParameters()
        }

        assertTrue(error.message?.contains("initializeSendTransport") == true)
    }

    @Test
    fun createProduceRequest_andBindProducer_completeTwoPhasePublish() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())

        val produceRequestJson = backend.createProduceRequest(producePayload(trackKind = 0))

        assertTrue(produceRequestJson.contains("\"produceRequest\""))
        assertTrue(produceRequestJson.contains("\"kind\":\"audio\""))
        assertTrue(produceRequestJson.contains("\"name\":\"participant-one\""))
        assertTrue(produceRequestJson.contains("mediaTag"))
        assertEquals(true, device.lastUserMediaConstraints["audio"])
        assertEquals(false, device.lastUserMediaConstraints["video"])

        backend.bindProducer("{\"trackKind\":0,\"producerId\":\"producer-a1\"}")

        assertEquals("producer-a1", transport.lastBoundProducerId)
        val producer = transport.lastProducedProducer
        assertNotNull(producer)
        assertEquals("producer-a1", producer.id)
    }

    private fun sendTransportPayload(): String {
        val iceParametersJson = "{\"usernameFragment\":\"user\",\"password\":\"pass\",\"iceLite\":true}"
        val iceCandidatesJson = "[]"
        val dtlsParametersJson = "{\"role\":\"auto\",\"fingerprints\":[]}"
        return "{" +
            "\"transport\":{" +
            "\"id\":\"send-transport-1\"," +
            "\"iceParametersJson\":" + json.encodeToString(iceParametersJson) + "," +
            "\"iceCandidatesJson\":" + json.encodeToString(iceCandidatesJson) + "," +
            "\"dtlsParametersJson\":" + json.encodeToString(dtlsParametersJson) + "," +
            "\"sctpParametersJson\":" + json.encodeToString("") + "," +
            "\"rawPayload\":" + json.encodeToString("{}") +
            "}}"
    }

    @Test
    fun createProduceRequest_duplicateTrackKindFailsWhileHandshakeIsPending() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())
        backend.createProduceRequest(producePayload(trackKind = 0))

        val error = assertFailsWith<IllegalStateException> {
            backend.createProduceRequest(producePayload(trackKind = 0))
        }

        assertTrue(error.message?.contains("pending audio producer handshake") == true)
    }

    @Test
    fun createProduceRequest_screenShareFailsUntilImplemented() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())

        val error = assertFailsWith<UnsupportedOperationException> {
            backend.createProduceRequest(producePayload(trackKind = 2))
        }

        assertTrue(error.message?.contains("does not yet support screen-share capture") == true)
    }

    @Test
    fun pauseResumeCloseProducer_manageActiveProducerLifecycle() {
        val device = FakeWebRtcDevice()
        val transport = FakeWebRtcTransport()
        device.sendTransport = transport
        val backend = AndroidUnitySendTransportBackendCore(device)

        backend.initializeSendTransport(sendTransportPayload())
        backend.createProduceRequest(producePayload(trackKind = 0))
        backend.bindProducer("{\"trackKind\":0,\"producerId\":\"producer-a1\"}")

        val producer = transport.lastProducedProducer
        assertNotNull(producer)

        backend.pauseProducer("{\"trackKind\":0}")
        assertEquals(true, producer.paused)

        backend.resumeProducer("{\"trackKind\":0}")
        assertEquals(false, producer.paused)

        backend.closeProducer("{\"trackKind\":0}")
        assertEquals(true, producer.closed)

        val secondProduceRequestJson = backend.createProduceRequest(producePayload(trackKind = 0))
        assertTrue(secondProduceRequestJson.contains("\"produceRequest\""))

        backend.bindProducer("{\"trackKind\":0,\"producerId\":\"producer-a2\"}")
        assertEquals("producer-a2", transport.lastBoundProducerId)
    }

    private fun producePayload(trackKind: Int): String {
        return "{" +
            "\"trackKind\":" + trackKind + "," +
            "\"enabled\":true," +
            "\"localUserName\":\"local-one\"," +
            "\"localIsLevel\":\"0\"," +
            "\"participantName\":\"participant-one\"," +
            "\"participantIslevel\":\"1\"" +
            "}"
    }

    private fun sampleRtpCapabilities(): RtpCapabilities {
        return RtpCapabilities(
            codecs = listOf(
                RtpCodecCapability(
                    kind = MediaKind.VIDEO,
                    mimeType = "video/VP8",
                    preferredPayloadType = 96,
                    clockRate = 90000,
                    channels = null,
                    parameters = mapOf("x-google-start-bitrate" to "1000"),
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

    private class FakeWebRtcDevice : WebRtcDevice {
        val loadCalls = mutableListOf<RtpCapabilities>()
        var sendTransport: FakeWebRtcTransport = FakeWebRtcTransport()
        var lastUserMediaConstraints: Map<String, Any?> = emptyMap()

        override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
            loadCalls += rtpCapabilities
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
            throw UnsupportedOperationException("Not needed in test")
        }

        override fun close() {
        }
    }

    private class FakeWebRtcTransport : WebRtcTransport {
        private var connectHandler: ((ConnectData) -> Unit)? = null
        private var produceHandler: ((ProduceData) -> Unit)? = null

        override val id: String = "send-transport-1"
        override val type: TransportType = TransportType.SEND
        override val connectionState: TransportConnectionState = TransportConnectionState.NEW

        var lastBoundProducerId: String = ""
        var lastProducedProducer: FakeWebRtcProducer? = null

        override fun close() {
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
            appData: Map<String, Any?>?
        ): WebRtcProducer {
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

            val producer = FakeWebRtcProducer(
                id = completion.get(1, TimeUnit.SECONDS),
                kind = produceData.kind
            )
            lastBoundProducerId = producer.id
            lastProducedProducer = producer
            return producer
        }

        override fun consume(
            id: String,
            producerId: String,
            kind: String,
            rtpParameters: Map<String, Any?>
        ): WebRtcConsumer {
            throw UnsupportedOperationException("Not needed in test")
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
        override val source = if (kind == MediaKind.AUDIO) com.mediasfu.sdk.webrtc.ProducerSource.MICROPHONE else com.mediasfu.sdk.webrtc.ProducerSource.CAMERA
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