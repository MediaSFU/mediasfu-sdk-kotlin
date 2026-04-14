@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mediasfu.sdk.webrtc

import cocoapods.WebRTC.RTCMediaStreamTrack
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosMediasoupTransportTest {

    @AfterTest
    fun tearDown() {
        IosNativeMediasoupBridgeProvider.reset()
    }

    @Test
    fun createSendTransport_usesInstalledBridgeAndMapsState() {
        val sendHandle = FakeSendTransportHandle(id = "send-1", state = "connected")
        IosNativeMediasoupBridgeProvider.install(
            FakeBridge(sendTransportHandle = sendHandle)
        )

        val device = IOSWebRtcDevice.getInstance()
        val transport = device.createSendTransport(
            mapOf(
                "id" to "send-1",
                "iceParameters" to mapOf("usernameFragment" to "test"),
                "iceCandidates" to emptyList<Map<String, Any?>>(),
                "dtlsParameters" to mapOf(
                    "role" to "auto",
                    "fingerprints" to listOf(mapOf("algorithm" to "sha-256", "value" to "abc"))
                )
            )
        )

        assertEquals("send-1", transport.id)
        assertEquals(TransportType.SEND, transport.type)
        assertEquals(TransportConnectionState.CONNECTED, transport.connectionState)
    }

    @Test
    fun onConnect_parsesDtlsParametersAndInvokesCallbacks() {
        val sendHandle = FakeSendTransportHandle(id = "send-2")
        IosNativeMediasoupBridgeProvider.install(FakeBridge(sendTransportHandle = sendHandle))

        val transport = IOSWebRtcDevice.getInstance().createSendTransport(
            mapOf(
                "id" to "send-2",
                "iceParameters" to emptyMap<String, Any?>(),
                "iceCandidates" to emptyList<Map<String, Any?>>(),
                "dtlsParameters" to emptyMap<String, Any?>()
            )
        )

        var callbackInvoked = false
        transport.onConnect { connectData ->
            assertEquals(DtlsRole.AUTO, connectData.dtlsParameters.role)
            assertEquals(1, connectData.dtlsParameters.fingerprints.size)
            assertEquals("sha-256", connectData.dtlsParameters.fingerprints.first().algorithm)
            connectData.callback()
        }

        sendHandle.triggerConnect(
            """
            {"role":"auto","fingerprints":[{"algorithm":"sha-256","value":"fingerprint"}]}
            """.trimIndent(),
            callback = { callbackInvoked = true }
        )

        assertTrue(callbackInvoked)
    }

    @Test
    fun onProduce_mapsKindRtpAndAppData() {
        val sendHandle = FakeSendTransportHandle(id = "send-3")
        IosNativeMediasoupBridgeProvider.install(FakeBridge(sendTransportHandle = sendHandle))

        val transport = IOSWebRtcDevice.getInstance().createSendTransport(
            mapOf(
                "id" to "send-3",
                "iceParameters" to emptyMap<String, Any?>(),
                "iceCandidates" to emptyList<Map<String, Any?>>(),
                "dtlsParameters" to emptyMap<String, Any?>()
            )
        )

        var producedId: String? = null
        transport.onProduce { produceData ->
            assertEquals(MediaKind.VIDEO, produceData.kind)
            assertEquals("0", produceData.rtpParameters.mid)
            assertEquals(1, produceData.rtpParameters.codecs.size)
            assertEquals("camera", produceData.appData?.get("source"))
            produceData.callback("producer-123")
        }

        sendHandle.triggerProduce(
            kind = "video",
            rtpParametersJson = """
            {
              "mid":"0",
              "codecs":[{"mimeType":"video/VP8","payloadType":96,"clockRate":90000,"parameters":{}}],
              "headerExtensions":[],
              "encodings":[{"active":true}],
              "rtcp":{"cname":"test-cname","reducedSize":true,"mux":true}
            }
            """.trimIndent(),
            appDataJson = "{" + "\"source\":\"camera\"}" ,
            callback = { producedId = it }
        )

        assertEquals("producer-123", producedId)
    }

    @Test
    fun createRecvTransport_createsBridgeBackedConsumer() {
        val consumerHandle = FakeConsumerHandle(
            id = "consumer-1",
            kind = "audio",
            paused = false,
            track = null
        )
        val recvHandle = FakeRecvTransportHandle(id = "recv-1", consumerHandle = consumerHandle)
        IosNativeMediasoupBridgeProvider.install(FakeBridge(recvTransportHandle = recvHandle))

        val transport = IOSWebRtcDevice.getInstance().createRecvTransport(
            mapOf(
                "id" to "recv-1",
                "iceParameters" to emptyMap<String, Any?>(),
                "iceCandidates" to emptyList<Map<String, Any?>>(),
                "dtlsParameters" to emptyMap<String, Any?>()
            )
        )

        val consumer = transport.consume(
            id = "consumer-1",
            producerId = "producer-1",
            kind = "audio",
            rtpParameters = mapOf(
                "mid" to "1",
                "codecs" to listOf(mapOf("mimeType" to "audio/opus", "payloadType" to 111, "clockRate" to 48000, "channels" to 2, "parameters" to emptyMap<String, Any?>())),
                "headerExtensions" to emptyList<Map<String, Any?>>(),
                "encodings" to emptyList<Map<String, Any?>>()
            )
        )

        assertEquals("consumer-1", consumer.id)
        assertEquals(MediaKind.AUDIO, consumer.kind)
        assertEquals(false, consumer.paused)
        assertEquals(null, consumer.track)
        assertNotNull(consumer.stream)
    }

    @Test
    fun missingBridge_throwsHelpfulError() {
        IosNativeMediasoupBridgeProvider.reset()

        val error = assertFailsWith<IllegalStateException> {
            IOSWebRtcDevice.getInstance().createSendTransport(emptyMap())
        }

        assertTrue(error.message?.contains("IOS_TRANSPORT_BRIDGE_STRATEGY.md") == true)
    }
}

private class FakeBridge(
    private val sendTransportHandle: FakeSendTransportHandle = FakeSendTransportHandle(id = "send-default"),
    private val recvTransportHandle: FakeRecvTransportHandle = FakeRecvTransportHandle(id = "recv-default")
) : IosNativeMediasoupBridge {
    override fun createSendTransport(params: Map<String, Any?>): IosNativeSendTransportHandle = sendTransportHandle

    override fun createRecvTransport(params: Map<String, Any?>): IosNativeRecvTransportHandle = recvTransportHandle
}

private class FakeSendTransportHandle(
    override val id: String,
    private var state: String = "new",
    private val producerHandle: FakeProducerHandle = FakeProducerHandle(id = "producer-default", kind = "audio")
) : IosNativeSendTransportHandle {
    private var connectListener: IosNativeConnectListener? = null
    private var produceListener: IosNativeProduceListener? = null
    private var stateListener: ((String) -> Unit)? = null

    override fun connectionState(): String = state

    override fun close() {
        state = "closed"
    }

    override fun setOnConnect(listener: IosNativeConnectListener?) {
        connectListener = listener
    }

    override fun setOnConnectionStateChange(listener: ((String) -> Unit)?) {
        stateListener = listener
    }

    override fun setOnProduce(listener: IosNativeProduceListener?) {
        produceListener = listener
    }

    override fun produce(
        track: RTCMediaStreamTrack,
        encodingsJson: String?,
        appDataJson: String?
    ): IosNativeProducerHandle = producerHandle

    fun triggerConnect(
        dtlsParametersJson: String,
        callback: () -> Unit = {},
        errback: (Throwable) -> Unit = {}
    ) {
        connectListener?.onConnect(dtlsParametersJson, callback, errback)
    }

    fun triggerProduce(
        kind: String,
        rtpParametersJson: String,
        appDataJson: String?,
        callback: (String?) -> Unit = {},
        errback: (Throwable) -> Unit = {}
    ) {
        produceListener?.onProduce(kind, rtpParametersJson, appDataJson, callback, errback)
    }

    fun updateState(newState: String) {
        state = newState
        stateListener?.invoke(newState)
    }
}

private class FakeRecvTransportHandle(
    override val id: String,
    private var state: String = "new",
    private val consumerHandle: FakeConsumerHandle = FakeConsumerHandle(id = "consumer-default", kind = "audio")
) : IosNativeRecvTransportHandle {
    private var connectListener: IosNativeConnectListener? = null
    private var stateListener: ((String) -> Unit)? = null

    override fun connectionState(): String = state

    override fun close() {
        state = "closed"
    }

    override fun setOnConnect(listener: IosNativeConnectListener?) {
        connectListener = listener
    }

    override fun setOnConnectionStateChange(listener: ((String) -> Unit)?) {
        stateListener = listener
    }

    override fun consume(
        id: String,
        producerId: String,
        kind: String,
        rtpParametersJson: String
    ): IosNativeConsumerHandle = consumerHandle
}

private class FakeProducerHandle(
    override val id: String,
    override val kind: String,
    private var paused: Boolean = false
) : IosNativeProducerHandle {
    override fun isPaused(): Boolean = paused

    override fun close() = Unit

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }

    override fun replaceTrack(track: RTCMediaStreamTrack) = Unit
}

private class FakeConsumerHandle(
    override val id: String,
    override val kind: String,
    private var paused: Boolean = false,
    override val track: RTCMediaStreamTrack? = null
) : IosNativeConsumerHandle {
    override fun isPaused(): Boolean = paused

    override fun close() = Unit

    override fun pause() {
        paused = true
    }

    override fun resume() {
        paused = false
    }
}