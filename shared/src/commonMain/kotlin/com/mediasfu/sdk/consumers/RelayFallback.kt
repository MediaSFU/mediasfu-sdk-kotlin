// RelayFallback.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.TransportConnectionState
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * TURN relay fallback (turn-relay-v1).
 *
 * Media servers accept ICE only on a high UDP/TCP port range, so a network that blocks those ports (corporate, hotel,
 * some VPNs) used to leave a transport "connecting" forever with nothing sent. Servers that offer a relay answer
 * createWebRtcTransport with short-lived TURN credentials beside `params`:
 *
 *   { params, iceServers: [{ urls, username, credential }], iceRelayMode: "fallback" | "always", iceRelayAfterMs: 8000 }
 *
 * - "always": the transport is created with the relay already allowed (ICE uses it only when direct paths fail).
 * - "fallback": the transport starts direct; if it is still not connected `iceRelayAfterMs` after ICE starts, ICE is
 *   restarted once with the relay allowed (fresh credentials come with the restart answer).
 * If that still does not connect, the user is told their network is blocking the call instead of a silent call.
 * Every relay restart is reported to the server (`transport-ice-report`) so relay use can be counted.
 *
 * The relay mode and the wait belong to the server; there is deliberately no client option to force or tune them.
 */

/** User-facing message shown once per media socket when no path (direct or relay) connects. */
const val NETWORK_BLOCKED_MESSAGE =
    "Your network is blocking the call connection. Try another network (a phone hotspot works) or turn off your VPN, then rejoin."

/** Relay fields that sit beside `params` in a createWebRtcTransport answer. All absent on servers without a relay. */
data class IceRelayOffer(
    val iceServers: List<Map<String, Any?>> = emptyList(),
    val iceRelayMode: String? = null,
    val iceRelayAfterMs: Long? = null
) {
    val hasServers: Boolean get() = iceServers.isNotEmpty()

    companion object {
        /** Reads the relay fields from the whole createWebRtcTransport answer (not from `params`). */
        fun fromResponse(response: Map<String, Any?>?): IceRelayOffer {
            if (response == null) return IceRelayOffer()
            val afterMs = when (val raw = response["iceRelayAfterMs"]) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            return IceRelayOffer(
                iceServers = parseIceServers(response["iceServers"]),
                iceRelayMode = response["iceRelayMode"] as? String,
                iceRelayAfterMs = afterMs
            )
        }
    }
}

/** Normalises `[{ urls, username, credential }]`, dropping entries without a URL. */
internal fun parseIceServers(value: Any?): List<Map<String, Any?>> {
    val list = value as? List<*> ?: return emptyList()
    return list.mapNotNull { entry ->
        val server = entry as? Map<*, *> ?: return@mapNotNull null
        val urls = when (val raw = server["urls"]) {
            is String -> listOf(raw)
            is List<*> -> raw.mapNotNull { it as? String }
            else -> emptyList()
        }.filter { it.isNotBlank() }
        if (urls.isEmpty()) return@mapNotNull null
        buildMap<String, Any?> {
            put("urls", urls)
            (server["username"] as? String)?.let { put("username", it) }
            (server["credential"] as? String)?.let { put("credential", it) }
        }
    }
}

/**
 * Transport options for [com.mediasfu.sdk.webrtc.WebRtcDevice.createSendTransport] / `createRecvTransport`.
 * Only `always` adds the relay up front; `fallback` and older servers get the params unchanged.
 */
fun transportParamsWithRelay(params: Map<String, Any?>, offer: IceRelayOffer?): Map<String, Any?> =
    if (offer?.iceRelayMode == "always" && offer.hasServers) params + ("iceServers" to offer.iceServers) else params

/** Which candidate a connected transport uses: host, srflx, prflx or relay (and over what). */
data class SelectedCandidate(
    val candidateType: String = "unknown",
    val protocol: String = "unknown",
    val relayProtocol: String = "unknown"
)

/** Reads the selected candidate from a WebRTC stats JSON report (an array of stats, or an id-keyed object). */
internal fun selectedCandidateFromStats(statsJson: String?): SelectedCandidate {
    if (statsJson.isNullOrBlank()) return SelectedCandidate()
    return runCatching {
        val stats: List<JsonObject> = when (val root = Json.parseToJsonElement(statsJson)) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> root.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        fun JsonObject.flag(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull == true
        fun byId(id: String?): JsonObject? = id?.let { wanted -> stats.firstOrNull { it.str("id") == wanted } }

        val transportStats = stats.firstOrNull { it.str("type") == "transport" && it.str("selectedCandidatePairId") != null }
        val pair = byId(transportStats?.str("selectedCandidatePairId"))
            ?: stats.firstOrNull {
                it.str("type") == "candidate-pair" && it.str("state") == "succeeded" && (it.flag("nominated") || it.flag("selected"))
            }
        val local = byId(pair?.str("localCandidateId")) ?: return@runCatching SelectedCandidate()
        SelectedCandidate(
            candidateType = local.str("candidateType") ?: "unknown",
            protocol = local.str("protocol") ?: "unknown",
            relayProtocol = local.str("relayProtocol") ?: "unknown"
        )
    }.getOrDefault(SelectedCandidate())
}

private fun isConnectedState(state: String): Boolean = state == "connected" || state == "completed"

private fun TransportConnectionState.wireName(): String = name.lowercase()

@Suppress("UNCHECKED_CAST")
private fun Any?.asStringMap(): Map<String, Any?>? =
    (this as? Map<*, *>)?.takeIf { map -> map.keys.all { it is String } } as Map<String, Any?>?

/**
 * Watches one transport and, when it stays stuck, restarts ICE once with the relay allowed.
 *
 * The SDK transports expose a single connection-state handler and a single connect handler, so the call site
 * forwards both into [onConnectionState] and [onConnect] (state first, before its own recovery handling).
 * All work runs on one serial dispatcher, so callbacks from native threads are safe.
 */
class RelayFallbackWatcher internal constructor(
    private val transport: WebRtcTransport,
    private val socket: SocketManager,
    private val offer: IceRelayOffer?,
    private val direction: String,
    private val onBlocked: (() -> Unit)?,
    private val scope: CoroutineScope,
    private val connectWaitMs: Long,
    private val timeSource: TimeSource
) {
    private val afterMs: Long = run {
        val raw = offer?.iceRelayAfterMs
        (if (raw == null || raw == 0L) 8000L else raw).coerceIn(3000L, 20000L)
    }
    private val socketId: String? = socket.id
    private val state = MutableStateFlow(transport.connectionState.wireName())
    private var disposed = false
    private var attempted = false
    private var startedAt: TimeMark? = null
    private var timer: Job? = null

    /** True once the watcher has finished (connected, blocked, closed or the socket changed). */
    val isDisposed: Boolean get() = disposed

    /** Forward every native connection-state string (`new`, `checking`/`connecting`, `connected`, ...). */
    fun onConnectionState(newState: String) {
        scope.launch { handleState(newState.lowercase()) }
    }

    /** Forward the transport `connect` event: ICE starts then, even if no state change shows. */
    fun onConnect() {
        scope.launch { start() }
    }

    /** Stops the watcher. Safe to call more than once. */
    fun dispose() {
        if (disposed) return
        disposed = true
        timer?.cancel()
        timer = null
    }

    internal fun begin() {
        scope.launch { if (state.value == "connecting" || state.value == "checking") start() }
    }

    private fun handleState(newState: String) {
        state.value = newState
        when (newState) {
            "connecting", "checking" -> start()
            "connected", "completed" -> if (!attempted) dispose()
            // Before the relay attempt the SDK closes a failed transport; after it, the attempt decides.
            "failed", "closed" -> if (!attempted) dispose()
        }
    }

    private fun current(): Boolean = !disposed && socket.isConnected() && socket.id == socketId

    private fun waitedMs(): Long = startedAt?.elapsedNow()?.inWholeMilliseconds ?: 0L

    private fun start() {
        if (startedAt != null || disposed) return
        startedAt = timeSource.markNow()
        timer = scope.launch {
            delay(afterMs)
            timer = null
            relayRestart()
        }
    }

    private fun report(phase: String, candidate: SelectedCandidate? = null) {
        val payload = buildMap<String, Any?> {
            put("transportId", transport.id)
            put("direction", direction)
            put("phase", phase)
            put("waitedMs", waitedMs())
            if (candidate != null) {
                put("candidateType", candidate.candidateType)
                put("protocol", candidate.protocol)
                put("relayProtocol", candidate.relayProtocol)
            }
        }
        scope.launch { runCatching { socket.emit("transport-ice-report", payload) } }
    }

    private fun blocked() {
        if (!current()) return
        report("blocked")
        runCatching { onBlocked?.invoke() }
        dispose()
    }

    private suspend fun waitConnected(ms: Long): Boolean {
        if (isConnectedState(state.value)) return true
        if (ms <= 0) return false
        val settled = withTimeoutOrNull(ms) {
            state.first { isConnectedState(it) || it == "failed" || it == "closed" }
        }
        return settled != null && isConnectedState(settled)
    }

    /** No relay here: give ICE until 12 s after the relay mark, then say so instead of staying silent. */
    private suspend fun noRelay(relayMark: TimeMark) {
        val remaining = connectWaitMs - relayMark.elapsedNow().inWholeMilliseconds
        if (!waitConnected(remaining) && current()) {
            report("unavailable")
            blocked()
        }
    }

    private suspend fun relayRestart() {
        if (!current() || attempted || isConnectedState(state.value)) return
        attempted = true
        val relayMark = timeSource.markNow()
        val offered = offer?.iceServers?.takeIf { it.isNotEmpty() }
        if (offered == null) return noRelay(relayMark)

        // Apply the offered servers before asking the server to restart: a native stack that cannot take TURN
        // credentials on a live transport must not leave the server with new ICE parameters it never uses.
        // The new servers only take effect at the ICE restart below.
        val liveUpdate = runCatching { transport.updateIceServers(offered) }.isSuccess
        if (!liveUpdate && !transport.createdWithRelay) return noRelay(relayMark)

        var answer: Map<String, Any?>? = null
        for (attempt in 0 until 2) {
            if (!current() || answer?.get("iceParameters").asStringMap() != null) break
            answer = runCatching {
                withTimeout(8000) {
                    socket.emitWithAck<Any?>(
                        "transport-restart-ice",
                        mapOf("transportId" to transport.id, "relay" to true),
                        8000
                    )
                }
            }.getOrNull().asStringMap()
            // The server throttles restarts to one per 2 s; another recovery path may have just used it.
            if (answer?.get("iceParameters").asStringMap() == null && attempt == 0) delay(2100)
        }
        if (!current()) return
        if (isConnectedState(state.value)) return dispose()
        val iceParameters = answer?.get("iceParameters").asStringMap() ?: return blocked()

        val fresh = parseIceServers(answer?.get("iceServers"))
        if (fresh.isNotEmpty() && liveUpdate) {
            // Fresh credentials when the server minted them; the offered ones stay valid for 12 h otherwise.
            runCatching { transport.updateIceServers(fresh) }
        }
        if (runCatching { transport.restartIce(iceParameters) }.isFailure) return blocked()

        if (waitConnected(connectWaitMs)) {
            if (!current()) return
            val candidate = selectedCandidateFromStats(runCatching { transport.getStatsJson() }.getOrNull())
            report("connected", candidate)
            dispose()
        } else {
            blocked()
        }
    }
}

/**
 * Attaches the relay fallback to a freshly created transport.
 *
 * @param direction `send` or `recv`, as reported to the server.
 * @param onBlocked called once if no path connects; usually [showNetworkBlocked].
 */
fun attachRelayFallback(
    transport: WebRtcTransport,
    socket: SocketManager,
    offer: IceRelayOffer?,
    direction: String,
    onBlocked: (() -> Unit)? = null
): RelayFallbackWatcher = attachRelayFallback(
    transport = transport,
    socket = socket,
    offer = offer,
    direction = direction,
    onBlocked = onBlocked,
    scope = CoroutineScope(SupervisorJob() + relayDispatcher),
    connectWaitMs = 12_000L,
    timeSource = TimeSource.Monotonic
)

internal fun attachRelayFallback(
    transport: WebRtcTransport,
    socket: SocketManager,
    offer: IceRelayOffer?,
    direction: String,
    onBlocked: (() -> Unit)?,
    scope: CoroutineScope,
    connectWaitMs: Long,
    timeSource: TimeSource
): RelayFallbackWatcher = RelayFallbackWatcher(
    transport = transport,
    socket = socket,
    offer = offer,
    direction = direction,
    onBlocked = onBlocked,
    scope = scope,
    connectWaitMs = connectWaitMs,
    timeSource = timeSource
).also { it.begin() }

/** One serial lane for every watcher: the work is tiny, and it keeps [networkBlockedSockets] single-threaded. */
@OptIn(ExperimentalCoroutinesApi::class)
private val relayDispatcher = Dispatchers.Default.limitedParallelism(1)

private val networkBlockedSockets = mutableSetOf<String>()

/**
 * Shows the blocked-network message once per media socket, however many transports hit it.
 * Called from the watcher's `onBlocked`, which runs on the shared relay lane.
 */
fun showNetworkBlocked(showAlert: ShowAlert?, socket: SocketManager?) {
    val key = socket?.let { it.id ?: "socket@${it.hashCode()}" }
    if (key != null) {
        if (key in networkBlockedSockets) return
        networkBlockedSockets += key
    }
    runCatching { showAlert?.invoke(NETWORK_BLOCKED_MESSAGE, "danger", 15000) }
}

internal fun resetNetworkBlockedForTests() {
    networkBlockedSockets.clear()
}
