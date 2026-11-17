package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.TestSocketManager
import com.mediasfu.sdk.webrtc.ConnectData
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.ProduceData
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.TransportType
import com.mediasfu.sdk.webrtc.WebRtcConsumer
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val servers = listOf(
    mapOf<String, Any?>(
        "urls" to listOf("turns:turn-ca.mediasfu.com:443?transport=tcp"),
        "username" to "1:x",
        "credential" to "c"
    )
)

private const val RELAY_STATS = """[
  {"id":"T","type":"transport","selectedCandidatePairId":"P"},
  {"id":"P","type":"candidate-pair","localCandidateId":"L","state":"succeeded"},
  {"id":"L","type":"local-candidate","candidateType":"relay","protocol":"udp","relayProtocol":"tls"}
]"""

private class FakeRelayTransport(
    override val createdWithRelay: Boolean = false,
    private val canUpdateIceServers: Boolean = true
) : WebRtcTransport {
    override val id: String = "t1"
    override val type: TransportType = TransportType.SEND
    override var connectionState: TransportConnectionState = TransportConnectionState.NEW
    val updates = mutableListOf<List<Map<String, Any?>>>()
    val restarts = mutableListOf<Map<String, Any?>>()

    override fun updateIceServers(iceServers: List<Map<String, Any?>>) {
        if (!canUpdateIceServers) throw IllegalStateException("[json.exception.type_error.302] type must be string")
        updates += iceServers
    }

    override fun restartIce(iceParameters: Map<String, Any?>) {
        restarts += iceParameters
    }

    override fun getStatsJson(): String = RELAY_STATS

    override fun close() {}
    override fun onConnect(handler: (ConnectData) -> Unit) {}
    override fun onProduce(handler: (ProduceData) -> Unit) {}
    override fun onConnectionStateChange(handler: (String) -> Unit) {}
    override fun produce(
        track: MediaStreamTrack,
        encodings: List<RtpEncodingParameters>,
        codecOptions: com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions?,
        codec: com.mediasfu.sdk.webrtc.RtpCodecCapability?,
        appData: Map<String, Any?>?
    ): WebRtcProducer = error("not used")

    override fun consume(id: String, producerId: String, kind: String, rtpParameters: Map<String, Any?>): WebRtcConsumer =
        error("not used")
}

private class FakeRelaySocket(
    private val onRestart: (Map<String, Any?>) -> Any? = { null }
) : TestSocketManager() {
    val restartRequests = mutableListOf<Map<String, Any?>>()
    val reports = mutableListOf<Map<String, Any?>>()

    override suspend fun emit(event: String, data: Map<String, Any?>) {
        if (event == "transport-ice-report") reports += data
    }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> emitWithAck(event: String, data: Map<String, Any?>, timeout: Long): T {
        if (event == "transport-restart-ice") {
            restartRequests += data
            return onRestart(data) as T
        }
        return Unit as T
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RelayFallbackTest {

    @AfterTest
    fun reset() = resetNetworkBlockedForTests()

    private fun TestScope.attach(
        transport: FakeRelayTransport,
        socket: FakeRelaySocket,
        offer: IceRelayOffer?,
        direction: String = "send",
        onBlocked: () -> Unit = {}
    ): RelayFallbackWatcher = attachRelayFallback(
        transport = transport,
        socket = socket,
        offer = offer,
        direction = direction,
        onBlocked = onBlocked,
        scope = backgroundScope,
        connectWaitMs = 12_000L,
        timeSource = testScheduler.timeSource
    )

    private fun TestScope.setState(watcher: RelayFallbackWatcher, transport: FakeRelayTransport, state: String) {
        transport.connectionState = when (state) {
            "connected" -> TransportConnectionState.CONNECTED
            "checking", "connecting" -> TransportConnectionState.CONNECTING
            "failed" -> TransportConnectionState.FAILED
            "closed" -> TransportConnectionState.CLOSED
            else -> TransportConnectionState.NEW
        }
        watcher.onConnectionState(state)
        runCurrent()
    }

    private fun TestScope.advance(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun readsTheOfferBesideParamsAndAddsServersOnlyInAlwaysMode() {
        val answer = mapOf(
            "params" to mapOf("id" to "x", "iceServers" to servers),
            "iceServers" to servers,
            "iceRelayMode" to "fallback",
            "iceRelayAfterMs" to 8000
        )
        val offer = IceRelayOffer.fromResponse(answer)
        assertEquals(servers, offer.iceServers)
        assertEquals("fallback", offer.iceRelayMode)
        assertEquals(8000L, offer.iceRelayAfterMs)

        val params = mapOf<String, Any?>("id" to "x")
        assertEquals(params, transportParamsWithRelay(params, offer))
        assertEquals(params + ("iceServers" to servers), transportParamsWithRelay(params, offer.copy(iceRelayMode = "always")))
        assertEquals(params, transportParamsWithRelay(params, IceRelayOffer.fromResponse(mapOf("params" to params))))
        assertEquals(params, transportParamsWithRelay(params, null))
        // Relay fields inside params are never read.
        assertFalse(IceRelayOffer.fromResponse(mapOf("params" to mapOf("iceServers" to servers))).hasServers)
    }

    @Test
    fun doesNothingWhenIceConnectsInTime() = runTest {
        val transport = FakeRelayTransport()
        val socket = FakeRelaySocket()
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback"))
        setState(watcher, transport, "checking")
        advance(3000)
        setState(watcher, transport, "connected")
        advance(20_000)
        assertTrue(socket.restartRequests.isEmpty())
        assertTrue(socket.reports.isEmpty())
        assertTrue(watcher.isDisposed)
    }

    @Test
    fun restartsIceOnceWithTheRelayAfterEightSecondsAndReportsTheRelayCandidate() = runTest {
        val transport = FakeRelayTransport()
        val fresh = listOf(mapOf<String, Any?>("urls" to listOf("turn:turn-ca.mediasfu.com:3478?transport=udp"), "username" to "2:y", "credential" to "d"))
        val socket = FakeRelaySocket { mapOf("iceParameters" to mapOf("usernameFragment" to "n"), "iceServers" to fresh) }
        var blocked = 0
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback", 8000), onBlocked = { blocked++ })
        setState(watcher, transport, "checking")
        advance(7999)
        assertTrue(socket.restartRequests.isEmpty())
        advance(1)
        assertEquals(listOf(mapOf<String, Any?>("transportId" to "t1", "relay" to true)), socket.restartRequests)
        assertEquals(listOf(servers, fresh), transport.updates)
        assertEquals(listOf(mapOf<String, Any?>("usernameFragment" to "n")), transport.restarts)

        setState(watcher, transport, "connected")
        assertEquals(1, socket.reports.size)
        val report = socket.reports.single()
        assertEquals("t1", report["transportId"])
        assertEquals("send", report["direction"])
        assertEquals("connected", report["phase"])
        assertEquals("relay", report["candidateType"])
        assertEquals("udp", report["protocol"])
        assertEquals("tls", report["relayProtocol"])
        assertEquals(0, blocked)

        advance(60_000)
        assertEquals(1, socket.restartRequests.size)
    }

    @Test
    fun tellsTheUserTheNetworkIsBlockingWhenTheRelayRestartDoesNotConnect() = runTest {
        val transport = FakeRelayTransport()
        val socket = FakeRelaySocket { mapOf("iceParameters" to emptyMap<String, Any?>(), "iceServers" to servers) }
        var blocked = 0
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback"), direction = "recv", onBlocked = { blocked++ })
        setState(watcher, transport, "checking")
        advance(8000)
        assertEquals(1, transport.restarts.size)
        advance(11_999)
        assertEquals(0, blocked)
        advance(1)
        assertEquals(1, blocked)
        assertEquals(listOf("blocked"), socket.reports.map { it["phase"] })
        assertEquals("recv", socket.reports.single()["direction"])
    }

    @Test
    fun retriesOnceWhenTheServerThrottlesARestart() = runTest {
        val transport = FakeRelayTransport()
        var calls = 0
        val socket = FakeRelaySocket {
            if (++calls == 1) mapOf("error" to "Recovery already attempted; retry later")
            else mapOf("iceParameters" to mapOf("usernameFragment" to "n"))
        }
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback"))
        setState(watcher, transport, "checking")
        advance(8000)
        assertEquals(1, calls)
        assertTrue(transport.restarts.isEmpty())
        advance(2100)
        assertEquals(2, calls)
        // No fresh servers in the answer: the offered ones (valid 12 h) stay in place.
        assertEquals(listOf(servers), transport.updates)
        assertEquals(1, transport.restarts.size)
    }

    @Test
    fun withoutARelayOfferStillSaysTheNetworkIsBlockingInsteadOfStayingSilent() = runTest {
        val transport = FakeRelayTransport()
        val socket = FakeRelaySocket()
        var blocked = 0
        val watcher = attach(transport, socket, IceRelayOffer(), onBlocked = { blocked++ })
        watcher.onConnect()
        runCurrent()
        advance(8000)
        advance(12_000)
        assertEquals(1, blocked)
        assertTrue(socket.restartRequests.isEmpty())
        assertEquals(listOf("unavailable", "blocked"), socket.reports.map { it["phase"] })
    }

    @Test
    fun aNativeStackWithoutTurnCredentialsSkipsTheServerRestartAndSaysBlocked() = runTest {
        val transport = FakeRelayTransport(canUpdateIceServers = false)
        val socket = FakeRelaySocket { mapOf("iceParameters" to mapOf("usernameFragment" to "n")) }
        var blocked = 0
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback"), onBlocked = { blocked++ })
        setState(watcher, transport, "checking")
        advance(8000)
        advance(12_000)
        assertTrue(socket.restartRequests.isEmpty(), "the server must not restart ICE the client cannot follow")
        assertTrue(transport.restarts.isEmpty())
        assertEquals(1, blocked)
        assertEquals(listOf("unavailable", "blocked"), socket.reports.map { it["phase"] })
    }

    @Test
    fun aTransportCreatedWithTheRelayRestartsIceEvenWhenServersCannotBeReplaced() = runTest {
        val transport = FakeRelayTransport(createdWithRelay = true, canUpdateIceServers = false)
        val socket = FakeRelaySocket { mapOf("iceParameters" to mapOf("usernameFragment" to "n"), "iceServers" to servers) }
        val watcher = attach(transport, socket, IceRelayOffer(servers, "always"))
        setState(watcher, transport, "checking")
        advance(8000)
        assertEquals(1, socket.restartRequests.size)
        assertEquals(1, transport.restarts.size)
        setState(watcher, transport, "connected")
        assertEquals(listOf("connected"), socket.reports.map { it["phase"] })
    }

    @Test
    fun stopsWhenTheSocketChanges() = runTest {
        val transport = FakeRelayTransport()
        val socket = FakeRelaySocket { mapOf("iceParameters" to mapOf("usernameFragment" to "n")) }
        var blocked = 0
        val watcher = attach(transport, socket, IceRelayOffer(), onBlocked = { blocked++ })
        setState(watcher, transport, "checking")
        socket.setId("another-socket")
        advance(30_000)
        assertEquals(0, blocked)
        assertTrue(socket.reports.isEmpty())
    }

    @Test
    fun clampsTheServerWaitIntoItsSafeRange() = runTest {
        val transport = FakeRelayTransport()
        val socket = FakeRelaySocket { mapOf("iceParameters" to mapOf("usernameFragment" to "n")) }
        val watcher = attach(transport, socket, IceRelayOffer(servers, "fallback", iceRelayAfterMs = 500))
        setState(watcher, transport, "checking")
        advance(2999)
        assertTrue(socket.restartRequests.isEmpty())
        advance(1)
        assertEquals(1, socket.restartRequests.size)
    }

    @Test
    fun showsTheBlockedMessageOncePerSocket() {
        val messages = mutableListOf<Triple<String, String, Int>>()
        val alert = ShowAlert { message, type, duration -> messages += Triple(message, type, duration) }
        val socket = FakeRelaySocket()
        showNetworkBlocked(alert, socket)
        showNetworkBlocked(alert, socket)
        assertEquals(listOf(Triple(NETWORK_BLOCKED_MESSAGE, "danger", 15000)), messages)
        assertEquals(
            "Your network is blocking the call connection. Try another network (a phone hotspot works) or turn off your VPN, then rejoin.",
            NETWORK_BLOCKED_MESSAGE
        )
    }

    @Test
    fun readsTheSelectedCandidateFromBothStatsShapes() {
        assertEquals(SelectedCandidate("relay", "udp", "tls"), selectedCandidateFromStats(RELAY_STATS))
        val keyed = """{"P":{"id":"P","type":"candidate-pair","localCandidateId":"L","state":"succeeded","nominated":true},
            "L":{"id":"L","type":"local-candidate","candidateType":"srflx","protocol":"tcp"}}"""
        assertEquals(SelectedCandidate("srflx", "tcp", "unknown"), selectedCandidateFromStats(keyed))
        assertEquals(SelectedCandidate(), selectedCandidateFromStats("not json"))
        assertEquals(SelectedCandidate(), selectedCandidateFromStats(null))
    }
}
