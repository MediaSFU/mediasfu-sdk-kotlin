package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.http.URLBuilder
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * iOS implementation of SocketManager using a lightweight Socket.IO-over-WebSocket client.
 *
 * This implementation targets the subset of Socket.IO behavior used by the shared MediaSFU layer:
 * default/custom namespace connection, event emission, acknowledgements, and basic reconnection.
 */
actual fun createSocketManager(): SocketManager = IOSSocketManager()

/**
 * Native iOS Socket.IO manager backed by a WebSocket transport and Engine.IO v4 framing.
 */
class IOSSocketManager : SocketManager {
    private val httpClient = HttpClient(Darwin) {
        install(WebSockets)
    }

    private val eventHandlers = mutableMapOf<String, suspend (Map<String, Any?>) -> Unit>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sendMutex = Mutex()

    private var connectHandler: (suspend () -> Unit)? = null
    private var disconnectHandler: (suspend (String) -> Unit)? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var reconnectHandler: (suspend (Int) -> Unit)? = null
    private var reconnectAttemptHandler: (suspend (Int) -> Unit)? = null
    private var reconnectFailedHandler: (suspend () -> Unit)? = null

    private var session: DefaultClientWebSocketSession? = null
    private var receiveJob: kotlinx.coroutines.Job? = null
    private var lastConnectUrl: String? = null
    private var lastConfig: SocketConfig = SocketConfig()
    private var namespace: String = "/"
    private var socketId: String? = null
    private var engineSid: String? = null
    private var manualDisconnect = false
    private var reconnectAttemptCount = 0
    private var nextAckId = 0

    private val ackDeferreds = mutableMapOf<Int, CompletableDeferred<Any?>>()
    private val ackCallbacks = mutableMapOf<Int, (Any?) -> Unit>()

    private var currentState: ConnectionState = ConnectionState.DISCONNECTED

    override val id: String?
        get() = socketId

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        return withContext(Dispatchers.Default) {
            if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING) {
                return@withContext Result.success(Unit)
            }

            lastConnectUrl = url
            lastConfig = config
            manualDisconnect = false
            reconnectAttemptCount = 0

            runCatching {
                openWebSocket(url, config)
                Result.success(Unit)
            }.getOrElse { error ->
                currentState = ConnectionState.FAILED
                val exception = SocketException("Failed to connect: ${error.message}", error)
                scope.launch { errorHandler?.invoke(exception) }
                Result.failure(exception)
            }
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            manualDisconnect = true
            runCatching {
                receiveJob?.cancelAndJoin()
                receiveJob = null

                session?.let { activeSession ->
                    runCatching {
                        activeSession.close(CloseReason(CloseReason.Codes.NORMAL, "Client disconnect"))
                    }
                }
                session = null

                currentState = ConnectionState.DISCONNECTED
                socketId = null
                engineSid = null
                ackDeferreds.values.forEach { it.cancel() }
                ackDeferreds.clear()
                ackCallbacks.clear()
                Result.success(Unit)
            }.getOrElse { error ->
                Result.failure(SocketException("Failed to disconnect: ${error.message}", error))
            }
        }
    }
    
    override fun isConnected(): Boolean = currentState == ConnectionState.CONNECTED
    
    override fun getConnectionState(): ConnectionState = currentState
    
    override suspend fun emit(event: String, data: Map<String, Any?>) {
        val activeSession = session ?: throw disconnectedException(event)
        if (!isConnected()) throw disconnectedException(event)

        sendMutex.withLock {
            activeSession.send(Frame.Text(buildEventPacket(event = event, data = data, ackId = null)))
        }
    }
    
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        val activeSession = session ?: throw disconnectedException(event)
        if (!isConnected()) throw disconnectedException(event)

        val ackId = nextAckIdentifier()
        val deferred = CompletableDeferred<Any?>()
        ackDeferreds[ackId] = deferred

        return try {
            sendMutex.withLock {
                activeSession.send(Frame.Text(buildEventPacket(event = event, data = data, ackId = ackId)))
            }

            @Suppress("UNCHECKED_CAST")
            withTimeout(timeout) { deferred.await() as T }
        } catch (error: TimeoutCancellationException) {
            throw SocketException("Acknowledgment timeout for event '$event'", error)
        } finally {
            ackDeferreds.remove(ackId)
        }
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        val activeSession = session ?: throw disconnectedException(event)
        if (!isConnected()) throw disconnectedException(event)

        val ackId = nextAckIdentifier()
        ackCallbacks[ackId] = callback

        scope.launch {
            try {
                sendMutex.withLock {
                    activeSession.send(Frame.Text(buildEventPacket(event = event, data = data, ackId = ackId)))
                }
            } catch (error: Throwable) {
                ackCallbacks.remove(ackId)
                errorHandler?.let { handler ->
                    scope.launch {
                        handler(SocketException("Failed to emit event with ack '$event': ${error.message}", error))
                    }
                }
            }
        }
    }
    
    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {
        eventHandlers[event] = handler
    }
    
    override fun off(event: String) {
        eventHandlers.remove(event)
    }

    override fun hasListener(event: String): Boolean = eventHandlers.containsKey(event)
    
    override fun offAll() {
        eventHandlers.clear()
    }
    
    override fun onConnect(handler: suspend () -> Unit) {
        connectHandler = handler
    }
    
    override fun onDisconnect(handler: suspend (String) -> Unit) {
        disconnectHandler = handler
    }
    
    override fun onError(handler: suspend (Throwable) -> Unit) {
        errorHandler = handler
    }
    
    override fun onReconnect(handler: suspend (Int) -> Unit) {
        reconnectHandler = handler
    }
    
    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {
        reconnectAttemptHandler = handler
    }
    
    override fun onReconnectFailed(handler: suspend () -> Unit) {
        reconnectFailedHandler = handler
    }

    private suspend fun openWebSocket(url: String, config: SocketConfig) {
        val target = resolveConnectionTarget(url)
        namespace = target.namespace
        socketId = null
        engineSid = null
        currentState = if (reconnectAttemptCount > 0) ConnectionState.RECONNECTING else ConnectionState.CONNECTING

        session = httpClient.webSocketSession {
            url(target.webSocketUrl)
        }

        receiveJob?.cancel()
        receiveJob = scope.launch {
            receiveLoop(config)
        }
    }

    private suspend fun receiveLoop(config: SocketConfig) {
        val activeSession = session ?: return

        try {
            while (scope.isActive) {
                when (val frame = activeSession.incoming.receive()) {
                    is Frame.Text -> handleEnginePacket(frame.readText())
                    is Frame.Close -> {
                        handleSocketClosed("Socket closed", config)
                        return
                    }
                    else -> Unit
                }
            }
        } catch (error: ClosedReceiveChannelException) {
            handleSocketClosed("Socket channel closed", config)
        } catch (error: Throwable) {
            val exception = SocketException("Socket receive loop failed: ${error.message}", error)
            errorHandler?.let { handler -> scope.launch { handler(exception) } }
            handleSocketClosed(error.message ?: "Socket receive loop failed", config)
        }
    }

    private suspend fun handleEnginePacket(packet: String) {
        when {
            packet.startsWith("0") -> handleEngineOpen(packet.removePrefix("0"))
            packet == "2" -> sendEnginePong()
            packet.startsWith("4") -> handleSocketIoPacket(packet.removePrefix("4"))
        }
    }

    private suspend fun handleEngineOpen(payload: String) {
        val openData = payload.toJsonElementOrNull() as? JsonObject
        engineSid = openData?.get("sid")?.jsonPrimitiveContentOrNull()
        sendSocketIoConnect()
    }

    private suspend fun sendSocketIoConnect() {
        val activeSession = session ?: return
        val packet = if (namespace == "/") {
            "40"
        } else {
            "40$namespace,"
        }
        sendMutex.withLock {
            activeSession.send(Frame.Text(packet))
        }
    }

    private suspend fun sendEnginePong() {
        val activeSession = session ?: return
        sendMutex.withLock {
            activeSession.send(Frame.Text("3"))
        }
    }

    private suspend fun handleSocketIoPacket(packet: String) {
        if (packet.isEmpty()) return

        when (packet.first()) {
            '0' -> handleSocketIoConnect(packet.drop(1))
            '1' -> handleSocketClosed("Server disconnected", lastConfig)
            '2' -> handleSocketIoEvent(packet.drop(1))
            '3' -> handleSocketIoAck(packet.drop(1))
            '4' -> handleSocketIoError(packet.drop(1))
        }
    }

    private suspend fun handleSocketIoConnect(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        if (parsed.namespace != namespace && namespace != "/") return

        val payload = parsed.payload?.toJsonElementOrNull() as? JsonObject
        socketId = payload?.get("sid")?.jsonPrimitiveContentOrNull() ?: engineSid

        val wasReconnecting = reconnectAttemptCount > 0 || currentState == ConnectionState.RECONNECTING
        currentState = ConnectionState.CONNECTED
        connectHandler?.let { handler -> scope.launch { handler() } }
        if (wasReconnecting) {
            reconnectHandler?.let { handler -> scope.launch { handler(reconnectAttemptCount) } }
        }
        reconnectAttemptCount = 0
    }

    private suspend fun handleSocketIoEvent(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        if (parsed.namespace != namespace && parsed.namespace != "/") return
        val payloadElement = parsed.payload?.toJsonElementOrNull() as? JsonArray ?: return
        if (payloadElement.isEmpty()) return

        val eventName = payloadElement.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return
        val payload = payloadElement.getOrNull(1)?.toKotlinValue()
        val mapPayload = payload as? Map<String, Any?> ?: emptyMap()

        eventHandlers[eventName]?.let { handler ->
            scope.launch {
                runCatching { handler(mapPayload) }
                    .onFailure { error ->
                        errorHandler?.invoke(SocketException("Error handling event '$eventName': ${error.message}", error))
                    }
            }
        }
    }

    private fun handleSocketIoAck(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        val ackId = parsed.ackId ?: return
        val payloadElement = parsed.payload?.toJsonElementOrNull() as? JsonArray
        val payload = payloadElement?.firstOrNull()?.toKotlinValue()

        ackDeferreds.remove(ackId)?.complete(payload)
        ackCallbacks.remove(ackId)?.invoke(payload)
    }

    private fun handleSocketIoError(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        val message = parsed.payload ?: "Socket.IO error"
        val exception = SocketException(message)
        currentState = ConnectionState.FAILED
        scope.launch { errorHandler?.invoke(exception) }
    }

    private fun handleSocketClosed(reason: String, config: SocketConfig) {
        session = null
        socketId = null
        engineSid = null

        if (manualDisconnect) {
            currentState = ConnectionState.DISCONNECTED
            scope.launch { disconnectHandler?.invoke(reason) }
            return
        }

        if (!config.reconnection || reconnectAttemptCount >= config.reconnectionAttempts) {
            currentState = ConnectionState.FAILED
            scope.launch {
                disconnectHandler?.invoke(reason)
                reconnectFailedHandler?.invoke()
            }
            return
        }

        reconnectAttemptCount += 1
        currentState = ConnectionState.RECONNECTING
        scope.launch {
            disconnectHandler?.invoke(reason)
            reconnectAttemptHandler?.invoke(reconnectAttemptCount)
        }

        val reconnectUrl = lastConnectUrl ?: return
        scope.launch {
            delay(config.reconnectionDelay.coerceAtLeast(250L))
            runCatching { openWebSocket(reconnectUrl, config) }
                .onFailure { error ->
                    errorHandler?.let { handler ->
                        scope.launch {
                            handler(SocketException("Reconnect failed: ${error.message}", error))
                        }
                    }
                    handleSocketClosed(error.message ?: "Reconnect failed", config)
                }
        }
    }

    private fun disconnectedException(event: String): SocketException {
        return SocketException("Not connected to server. Failed to emit event '$event'.")
    }

    private fun nextAckIdentifier(): Int {
        nextAckId += 1
        return nextAckId
    }

    private fun resolveConnectionTarget(url: String): ConnectionTarget {
        val original = URLBuilder(url)
        val hostAndPath = url.substringAfter("://", url)
        val rawPath = hostAndPath.substringAfter('/', "")
        val pathOnly = rawPath.substringBefore('?').substringBefore('#')
        val resolvedNamespace = pathOnly
            .trim()
            .trim('/')
            .takeIf { it.isNotBlank() }
            ?.let { "/$it" }
            ?: "/"

        val scheme = if (original.protocol.name.equals("https", ignoreCase = true)) "wss" else "ws"
        val authority = buildString {
            append(original.host)
            if (original.port != 80 && original.port != 443) {
                append(":")
                append(original.port)
            }
        }
        val queryParts = mutableListOf("EIO=4", "transport=websocket")
        original.parameters.names().forEach { name ->
            original.parameters.getAll(name)?.forEach { value ->
                queryParts += "$name=$value"
            }
        }
        val wsUrl = buildString {
            append(scheme)
            append("://")
            append(authority)
            append("/socket.io/")
            if (queryParts.isNotEmpty()) {
                append('?')
                append(queryParts.joinToString("&"))
            }
        }

        return ConnectionTarget(webSocketUrl = wsUrl, namespace = resolvedNamespace)
    }

    private fun buildEventPacket(event: String, data: Map<String, Any?>, ackId: Int?): String {
        val payloadItems = buildList<Any?> {
            add(event)
            if (data.isNotEmpty()) add(data)
        }

        return buildString {
            append("42")
            if (namespace != "/") {
                append(namespace)
                append(',')
            }
            if (ackId != null) {
                append(ackId)
            }
            append(payloadItems.toJsonElement().toString())
        }
    }

    private fun parseSocketIoMeta(raw: String): ParsedSocketIoPacket {
        var index = 0
        var parsedNamespace = "/"

        if (raw.startsWith('/')) {
            val commaIndex = raw.indexOf(',')
            if (commaIndex >= 0) {
                parsedNamespace = raw.substring(0, commaIndex)
                index = commaIndex + 1
            } else {
                parsedNamespace = raw
                index = raw.length
            }
        }

        val idStart = index
        while (index < raw.length && raw[index].isDigit()) {
            index += 1
        }

        val ackId = if (index > idStart) raw.substring(idStart, index).toIntOrNull() else null
        val payload = raw.substring(index).takeIf { it.isNotBlank() }

        return ParsedSocketIoPacket(
            namespace = parsedNamespace,
            ackId = ackId,
            payload = payload
        )
    }
}

/**
 * iOS data converter.
 *
 * Until the native socket bridge is wired in, we normalize data into plain
 * Kotlin maps/lists so shared logic and tests can still round-trip payloads.
 */
actual object SocketDataConverter {
    actual fun toMap(data: Any?): Map<String, Any?> {
        return when (data) {
            is JsonObject -> normalizeMap(data.toKotlinValue() as Map<String, Any?>)
            is Map<*, *> -> normalizeMap(data)
            else -> emptyMap()
        }
    }
    
    actual fun fromMap(map: Map<String, Any?>): Any {
        return normalizeMap(map)
    }

    private fun normalizeMap(map: Map<*, *>): Map<String, Any?> {
        return map.entries.associate { (key, value) ->
            key.toString() to normalizeValue(value)
        }
    }

    private fun normalizeList(list: List<*>): List<Any?> {
        return list.map { normalizeValue(it) }
    }

    private fun normalizeValue(value: Any?): Any? {
        return when (value) {
            null, is String, is Number, is Boolean -> value
            is Map<*, *> -> normalizeMap(value)
            is List<*> -> normalizeList(value)
            is Array<*> -> normalizeList(value.toList())
            else -> value.toString()
        }
    }
}

class SocketException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

private data class ConnectionTarget(
    val webSocketUrl: String,
    val namespace: String
)

private data class ParsedSocketIoPacket(
    val namespace: String,
    val ackId: Int?,
    val payload: String?
)

private fun String.toJsonElementOrNull(): JsonElement? = runCatching {
    kotlinx.serialization.json.Json.parseToJsonElement(this)
}.getOrNull()

private fun JsonElement.toKotlinValue(): Any? = when (this) {
    is JsonObject -> this.mapValues { (_, value) -> value.toKotlinValue() }
    is JsonArray -> this.map { it.toKotlinValue() }
    is JsonNull -> null
    is JsonPrimitive -> when {
        isString -> content
        booleanOrNull != null -> booleanOrNull
        longOrNull != null -> longOrNull
        doubleOrNull != null -> doubleOrNull
        else -> content
    }
}

private fun JsonObject.jsonPrimitiveContentOrNull(key: String): String? =
    this[key]?.jsonPrimitiveContentOrNull()

private fun JsonElement.jsonPrimitiveContentOrNull(): String? =
    (this as? JsonPrimitive)?.content

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Map<*, *> -> buildJsonObject {
        this@toJsonElement.forEach { (key, value) ->
            val stringKey = key as? String ?: return@forEach
            put(stringKey, value.toJsonElement())
        }
    }
    is Iterable<*> -> buildJsonArray {
        this@toJsonElement.forEach { item -> add(item.toJsonElement()) }
    }
    is Array<*> -> buildJsonArray {
        this@toJsonElement.forEach { item -> add(item.toJsonElement()) }
    }
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    else -> JsonPrimitive(this.toString())
}
