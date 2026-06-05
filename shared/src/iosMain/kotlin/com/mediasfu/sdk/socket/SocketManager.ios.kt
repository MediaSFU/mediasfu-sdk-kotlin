package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.util.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
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
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionWebSocketCloseCodeNormalClosure
import platform.Foundation.NSURLSessionWebSocketMessage
import platform.Foundation.NSURLSessionWebSocketTask

/**
 * iOS implementation of SocketManager using a lightweight Socket.IO-over-WebSocket client.
 *
 * This implementation targets the subset of Socket.IO behavior used by the shared MediaSFU layer:
 * default/custom namespace connection, event emission, acknowledgements, and basic reconnection.
 */
actual fun createSocketManager(): SocketManager = IOSSocketManager()

/**
 * Native iOS Socket.IO manager backed by NSURLSessionWebSocketTask and Engine.IO v4 framing.
 */
class IOSSocketManager : SocketManager {
    private val eventHandlers = mutableMapOf<String, suspend (Map<String, Any?>) -> Unit>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sendMutex = Mutex()

    private var connectHandler: (suspend () -> Unit)? = null
    private var disconnectHandler: (suspend (String) -> Unit)? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var reconnectHandler: (suspend (Int) -> Unit)? = null
    private var reconnectAttemptHandler: (suspend (Int) -> Unit)? = null
    private var reconnectFailedHandler: (suspend () -> Unit)? = null

    private var session: NSURLSessionWebSocketTask? = null
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
    private var connectDeferred: CompletableDeferred<Unit>? = null

    override val id: String?
        get() = socketId

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        return withContext(Dispatchers.Default) {
            if (currentState == ConnectionState.CONNECTED) {
                return@withContext Result.success(Unit)
            }

            connectDeferred?.takeIf { it.isActive }?.let { pending ->
                return@withContext awaitConnection(pending)
            }

            lastConnectUrl = url
            lastConfig = config
            manualDisconnect = false
            reconnectAttemptCount = 0

            val deferred = CompletableDeferred<Unit>()
            connectDeferred = deferred

            val openError = runCatching { openWebSocket(url, config) }.exceptionOrNull()
            if (openError != null) {
                connectDeferred = null
                currentState = ConnectionState.FAILED
                val exception = SocketException("Failed to open WebSocket: ${openError.message}", openError)
                scope.launch { errorHandler?.invoke(exception) }
                return@withContext Result.failure(exception)
            }

            awaitConnection(deferred)
        }
    }

    private suspend fun awaitConnection(deferred: CompletableDeferred<Unit>): Result<Unit> {
        return try {
            withTimeout(20_000L) { deferred.await() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            connectDeferred = null
            currentState = ConnectionState.FAILED
            val exception = SocketException("Socket connection timeout after 20s")
            scope.launch { errorHandler?.invoke(exception) }
            Result.failure(exception)
        } catch (error: Throwable) {
            connectDeferred = null
            Result.failure(SocketException(error.message ?: "Connection failed", error))
        }
    }

    override suspend fun disconnect(): Result<Unit> {
        return withContext(Dispatchers.Default) {
            manualDisconnect = true
            runCatching {
                receiveJob?.cancelAndJoin()
                receiveJob = null

                session?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
                session = null

                currentState = ConnectionState.DISCONNECTED
                socketId = null
                engineSid = null
                connectDeferred?.cancel()
                connectDeferred = null
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
        if (!isConnected()) throw disconnectedException(event)

        sendMutex.withLock {
            Logger.d("MediaSFU-Socket", "TX event=$event keys=${data.keys.joinToString(",")}")
            sendText(buildEventPacket(event = event, data = data, ackId = null))
        }
    }

    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        if (!isConnected()) throw disconnectedException(event)

        val ackId = nextAckIdentifier()
        val deferred = CompletableDeferred<Any?>()
        ackDeferreds[ackId] = deferred

        return try {
            sendMutex.withLock {
                Logger.d("MediaSFU-Socket", "TX/ack event=$event ackId=$ackId keys=${data.keys.joinToString(",")}")
                sendText(buildEventPacket(event = event, data = data, ackId = ackId))
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
        if (!isConnected()) throw disconnectedException(event)

        val ackId = nextAckIdentifier()
        val timeoutMillis = lastConfig.timeout.coerceAtLeast(5_000)
        val timeoutJob = scope.launch {
            delay(timeoutMillis)
            ackCallbacks.remove(ackId)?.let {
                Logger.w("MediaSFU-Socket", "TX/ack callback timeout event=$event ackId=$ackId timeoutMs=$timeoutMillis")
                it(mapOf("error" to "Acknowledgment timeout for event '$event'"))
            }
        }
        ackCallbacks[ackId] = { response ->
            timeoutJob.cancel()
            callback(response)
        }

        scope.launch {
            try {
                sendMutex.withLock {
                    sendText(buildEventPacket(event = event, data = data, ackId = ackId))
                }
            } catch (error: Throwable) {
                timeoutJob.cancel()
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

        Logger.i("MediaSFU-Socket", "Connecting to WebSocket URL: ${target.webSocketUrl}")
        try {
            val nsUrl = NSURL.URLWithString(target.webSocketUrl)
                ?: throw SocketException("Invalid WebSocket URL: ${target.webSocketUrl}")
            session?.cancelWithCloseCode(NSURLSessionWebSocketCloseCodeNormalClosure, reason = null)
            session = NSURLSession.sharedSession.webSocketTaskWithURL(nsUrl)
            session?.resume()
            Logger.i("MediaSFU-Socket", "Native WebSocket task started")
        } catch (e: Throwable) {
            Logger.e("MediaSFU-Socket", "WebSocket task FAILED: ${e::class.simpleName}: ${e.message}")
            throw e
        }

        receiveJob?.cancel()
        receiveJob = scope.launch {
            receiveLoop(config)
        }
    }

    private fun receiveLoop(config: SocketConfig) {
        val activeSession = session ?: return
        receiveNext(activeSession, config)
    }

    private fun receiveNext(activeSession: NSURLSessionWebSocketTask, config: SocketConfig) {
        activeSession.receiveMessageWithCompletionHandler { message, error ->
            if (session !== activeSession || manualDisconnect) return@receiveMessageWithCompletionHandler

            if (error != null) {
                val reason = error.localizedDescription ?: "Socket receive failed"
                scope.launch { handleSocketClosed(reason, config) }
                return@receiveMessageWithCompletionHandler
            }

            val text = message?.string
            if (text == null) {
                if (scope.isActive && session === activeSession && !manualDisconnect) {
                    receiveNext(activeSession, config)
                }
                return@receiveMessageWithCompletionHandler
            }

            scope.launch {
                runCatching { handleEnginePacket(text) }
                    .onFailure { failure ->
                        val exception = SocketException("Socket receive loop failed: ${failure.message}", failure)
                        errorHandler?.let { handler -> scope.launch { handler(exception) } }
                        handleSocketClosed(failure.message ?: "Socket receive loop failed", config)
                        return@launch
                    }

                if (scope.isActive && session === activeSession && !manualDisconnect) {
                    receiveNext(activeSession, config)
                }
            }
        }
    }

    private suspend fun sendText(text: String) {
        val activeSession = session ?: throw SocketException("WebSocket session is not available")
        val sent = CompletableDeferred<Unit>()
        activeSession.sendMessage(NSURLSessionWebSocketMessage(string = text)) { error: NSError? ->
            if (error != null) {
                sent.completeExceptionally(SocketException(error.localizedDescription ?: "WebSocket send failed"))
            } else {
                sent.complete(Unit)
            }
        }
        sent.await()
    }

    private suspend fun handleEnginePacket(packet: String) {
        Logger.d("MediaSFU-Socket", "RX: $packet")
        when {
            packet.startsWith("0") -> handleEngineOpen(packet.removePrefix("0"))
            packet == "2" -> sendEnginePong()
            packet.startsWith("4") -> handleSocketIoPacket(packet.removePrefix("4"))
        }
    }

    private suspend fun handleEngineOpen(payload: String) {
        val openData = payload.toJsonElementOrNull() as? JsonObject
        engineSid = openData?.get("sid")?.jsonPrimitiveContentOrNull()
        Logger.i("MediaSFU-Socket", "Engine.IO open received, sid=$engineSid; sending Socket.IO connect")
        sendSocketIoConnect()
    }

    private suspend fun sendSocketIoConnect() {
        val packet = if (namespace == "/") {
            "40"
        } else {
            "40$namespace,"
        }
        sendMutex.withLock {
            Logger.d("MediaSFU-Socket", "TX raw packet: $packet")
            sendText(packet)
        }
    }

    private suspend fun sendEnginePong() {
        sendMutex.withLock {
            sendText("3")
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

        if (!lastConfig.waitForConnectionSuccess) {
            val wasReconnecting = reconnectAttemptCount > 0 || currentState == ConnectionState.RECONNECTING
            currentState = ConnectionState.CONNECTED
            connectDeferred?.let { deferred ->
                if (deferred.isActive) deferred.complete(Unit)
                connectDeferred = null
            }
            Logger.i("MediaSFU-Socket", "Socket.IO namespace connected, socketId=$socketId")
            connectHandler?.let { handler -> scope.launch { handler() } }
            if (wasReconnecting) {
                reconnectHandler?.let { handler -> scope.launch { handler(reconnectAttemptCount) } }
            }
            reconnectAttemptCount = 0
            return
        }

        Logger.i("MediaSFU-Socket", "Socket.IO namespace connected, socketId=$socketId; waiting for connection-success")
    }

    private suspend fun handleSocketIoEvent(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        if (parsed.namespace != namespace && parsed.namespace != "/") return
        val payloadElement = parsed.payload?.toJsonElementOrNull() as? JsonArray ?: return
        if (payloadElement.isEmpty()) return

        val eventName = payloadElement.firstOrNull()?.jsonPrimitiveContentOrNull() ?: return
        val payload = payloadElement.getOrNull(1)?.toKotlinValue()
        val mapPayload = payload as? Map<String, Any?> ?: emptyMap()

        if (eventName == "connection-success") {
            val receivedSocketId = mapPayload["socketId"]?.toString()
            if (!receivedSocketId.isNullOrBlank()) socketId = receivedSocketId
            val wasReconnecting = reconnectAttemptCount > 0 || currentState == ConnectionState.RECONNECTING
            currentState = ConnectionState.CONNECTED
            connectDeferred?.let { deferred ->
                if (deferred.isActive) deferred.complete(Unit)
                connectDeferred = null
            }
            Logger.i("MediaSFU-Socket", "connection-success received, socketId=$socketId")
            connectHandler?.let { handler -> scope.launch { handler() } }
            if (wasReconnecting) {
                reconnectHandler?.let { handler -> scope.launch { handler(reconnectAttemptCount) } }
            }
            reconnectAttemptCount = 0
        }

        if (eventName == "transport-recv-connect" || eventName == "consumer-resume" || eventName == "transport-produce") {
            Logger.i("MediaSFU-Socket", "RX media event=$eventName payloadKeys=${mapPayload.keys.joinToString(",")}")
        } else {
            Logger.d("MediaSFU-Socket", "RX event=$eventName payloadKeys=${mapPayload.keys.joinToString(",")}")
        }

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

        Logger.d("MediaSFU-Socket", "RX ack ackId=$ackId payloadType=${payload?.let { it::class.simpleName } ?: "null"}")

        ackDeferreds.remove(ackId)?.complete(payload)
        ackCallbacks.remove(ackId)?.invoke(payload)
    }

    private fun handleSocketIoError(rawMeta: String) {
        val parsed = parseSocketIoMeta(rawMeta)
        val message = parsed.payload ?: "Socket.IO error"
        val exception = SocketException(message)
        currentState = ConnectionState.FAILED
        Logger.e("MediaSFU-Socket", "Socket.IO ERROR: $message")
        connectDeferred?.let { deferred ->
            if (deferred.isActive) {
                deferred.completeExceptionally(exception)
            }
            connectDeferred = null
        }
        scope.launch { errorHandler?.invoke(exception) }
    }

    private fun handleSocketClosed(reason: String, config: SocketConfig) {
        session = null
        socketId = null
        engineSid = null

        val deferred = connectDeferred
        if (deferred != null && deferred.isActive) {
            connectDeferred = null
            currentState = ConnectionState.FAILED
            Logger.e("MediaSFU-Socket", "Connection closed before Socket.IO handshake: $reason")
            deferred.completeExceptionally(SocketException("Connection closed before Socket.IO handshake: $reason"))
            scope.launch { disconnectHandler?.invoke(reason) }
            return
        }

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
        // Ensure URL has a scheme
        val withScheme = if (url.contains("://")) url else "https://$url"

        // Determine WebSocket scheme
        val rawScheme = withScheme.substringBefore("://").lowercase()
        val wsScheme = when (rawScheme) {
            "https", "wss" -> "wss"
            else -> "ws"
        }

        val afterScheme = withScheme.substringAfter("://")

        // Find where the query string / fragment starts (before that is host + path)
        val qIdx = afterScheme.indexOf('?')
        val hIdx = afterScheme.indexOf('#')
        val endIdx = minOf(
            if (qIdx >= 0) qIdx else afterScheme.length,
            if (hIdx >= 0) hIdx else afterScheme.length
        )
        val hostAndPath = afterScheme.substring(0, endIdx)
        val slashIdx = hostAndPath.indexOf('/')
        val authority = if (slashIdx >= 0) hostAndPath.substring(0, slashIdx) else hostAndPath
        val pathPart = if (slashIdx >= 0) hostAndPath.substring(slashIdx) else ""

        // The URI path IS the Socket.IO namespace (e.g. "/media")
        // socket.io-client connects to the server root /socket.io/, not /<path>/socket.io/
        val namespace = pathPart.trimEnd('/').takeIf { it.isNotBlank() } ?: "/"

        // Preserve credentials from original URL query string verbatim
        val rawQuery = if (qIdx >= 0) afterScheme.substring(qIdx + 1).substringBefore('#') else ""

        // Build WebSocket URL targeting the standard socket.io mount point at the server root
        val wsUrl = buildString {
            append(wsScheme)
            append("://")
            append(authority)
            append("/socket.io/?EIO=4&transport=websocket")
            if (rawQuery.isNotBlank()) {
                append('&')
                append(rawQuery)
            }
        }

        return ConnectionTarget(webSocketUrl = wsUrl, namespace = namespace)
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
