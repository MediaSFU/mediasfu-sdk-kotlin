package com.mediasfu.sdk.socket

import com.mediasfu.sdk.util.Logger
import com.mediasfu.sdk.model.SocketConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/**
 * Android implementation of SocketManager using Socket.IO Java client.
 */
actual fun createSocketManager(): SocketManager = SocketManagerImpl()

/**
 * Android implementation of SocketManager
 */
class SocketManagerImpl : SocketManager {
    private var socket: Socket? = null
    private val eventHandlers = mutableMapOf<String, suspend (Map<String, Any?>) -> Unit>()
    
    // Connection state handlers
    private var connectHandler: (suspend () -> Unit)? = null
    private var disconnectHandler: (suspend (String) -> Unit)? = null
    private var errorHandler: (suspend (Throwable) -> Unit)? = null
    private var reconnectHandler: (suspend (Int) -> Unit)? = null
    private var reconnectAttemptHandler: (suspend (Int) -> Unit)? = null
    private var reconnectFailedHandler: (suspend () -> Unit)? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var currentState: ConnectionState = ConnectionState.DISCONNECTED

    override val id: String?
        get() = socket?.id()
    
    // ========================================================================
    // Connection Lifecycle
    // ========================================================================
    
    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                if (socket?.connected() == true) {
                    return@withContext Result.success(Unit)
                }
                
                val options = IO.Options().apply {
                    reconnection = config.reconnection
                    reconnectionAttempts = config.reconnectionAttempts
                    reconnectionDelay = config.reconnectionDelay
                    reconnectionDelayMax = config.reconnectionDelayMax
                    timeout = config.timeout
                    
                    // Set transports
                    if (config.transports.isNotEmpty()) {
                        transports = config.transports.toTypedArray()
                    }
                }
                
                currentState = ConnectionState.CONNECTING
                
                socket = IO.socket(URI.create(url), options).apply {
                    setupInternalHandlers()
                    
                    if (config.autoConnect) {
                        connect()
                    }
                }
                
                Result.success(Unit)
            } catch (e: Exception) {
                currentState = ConnectionState.FAILED
                Result.failure(SocketException("Failed to connect: ${e.message}", e))
            }
        }
    }
    
    override suspend fun disconnect(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                socket?.disconnect()
                socket?.off()
                socket = null
                currentState = ConnectionState.DISCONNECTED
                eventHandlers.clear()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(SocketException("Failed to disconnect: ${e.message}", e))
            }
        }
    }
    
    override fun isConnected(): Boolean = socket?.connected() ?: false
    
    override fun getConnectionState(): ConnectionState = currentState
    
    // ========================================================================
    // Event Emission
    // ========================================================================
    
    override suspend fun emit(event: String, data: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                val socket = socket ?: throw SocketException("Not connected to server")
                socket.emit(event, SocketDataConverter.fromMap(data))
            } catch (e: Exception) {
                throw SocketException("Failed to emit event '$event': ${e.message}", e)
            }
        }
    }
    
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        return withContext(Dispatchers.IO) {
            try {
                val socket = socket ?: throw SocketException("Not connected to server")

                val deferred = CompletableDeferred<T>()
                val jsonData = SocketDataConverter.fromMap(data)
                
                try {
                    socket.emit(event, arrayOf(jsonData)) { args: Array<Any> ->
                        scope.launch {
                            try {
                                val payload = args.firstOrNull()
                                val normalized = when (payload) {
                                    is JSONObject -> SocketDataConverter.toMap(payload)
                                    is Map<*, *> -> @Suppress("UNCHECKED_CAST") (payload as Map<String, Any?>)
                                    else -> payload
                                }
                                @Suppress("UNCHECKED_CAST")
                                val result = (normalized ?: Unit) as T
                                deferred.complete(result)
                            } catch (e: Exception) {
                                deferred.completeExceptionally(
                                    SocketException("Failed to parse acknowledgment for event '$event': ${e.message}", e)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    throw SocketException("socket.emit() failed for event '$event': ${e.message}", e)
                }

                try {
                    withTimeout(timeout) { deferred.await() }
                } catch (e: TimeoutCancellationException) {
                    throw SocketException("Acknowledgment timeout for event '$event'", e)
                }
            } catch (e: Exception) {
                if (e is SocketException) throw e
                throw SocketException("Failed to emit event with ack '$event': ${e.message}", e)
            }
        }
    }

    override fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    ) {
        val socket = socket ?: throw SocketException("Not connected to server")
        
        // Flutter passes empty array [] for events with no data (like requestScreenShare)
        // If data is empty, pass empty array; otherwise wrap the JSON object in array
        val emitArgs: Array<Any> = if (data.isEmpty()) {
            emptyArray()
        } else {
            arrayOf(SocketDataConverter.fromMap(data))
        }

        socket.emit(event, emitArgs) { args: Array<Any> ->
            val payload = args.firstOrNull()
            val normalized = when (payload) {
                is JSONObject -> SocketDataConverter.toMap(payload)
                is Map<*, *> -> @Suppress("UNCHECKED_CAST") (payload as Map<String, Any?>)
                else -> payload
            }
            callback(normalized)
        }
    }
    
    // ========================================================================
    // Event Registration
    // ========================================================================
    
    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {
        eventHandlers[event] = handler
        socket?.on(event) { args ->
            scope.launch {
                try {
                    val data = if (args.isNotEmpty()) {
                        SocketDataConverter.toMap(args[0])
                    } else {
                        emptyMap()
                    }
                    handler(data)
                } catch (e: Exception) {
                    errorHandler?.invoke(
                        SocketException("Error handling event '$event': ${e.message}", e)
                    )
                }
            }
        }
    }
    
    override fun off(event: String) {
        eventHandlers.remove(event)
        socket?.off(event)
    }
    
    override fun offAll() {
        eventHandlers.clear()
        socket?.off()
        setupInternalHandlers() // Re-setup internal handlers
    }
    
    // ========================================================================
    // Connection State Handlers
    // ========================================================================
    
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
    
    // ========================================================================
    // Internal Handlers
    // ========================================================================
    
    private fun setupInternalHandlers() {
        socket?.on(Socket.EVENT_CONNECT) {
            currentState = ConnectionState.CONNECTED
            scope.launch { connectHandler?.invoke() }
        }
        
        socket?.on(Socket.EVENT_DISCONNECT) { args ->
            val wasReconnecting = currentState == ConnectionState.RECONNECTING
            currentState = if (wasReconnecting) {
                ConnectionState.RECONNECTING
            } else {
                ConnectionState.DISCONNECTED
            }
            
            scope.launch {
                val reason = args.getOrNull(0)?.toString() ?: "unknown"
                disconnectHandler?.invoke(reason)
            }
        }
        
        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            if (currentState == ConnectionState.CONNECTING) {
                currentState = ConnectionState.FAILED
            }
            
            scope.launch {
                val error = args.getOrNull(0)
                val exception = if (error is Exception) {
                    SocketException("Connection error", error)
                } else {
                    SocketException("Connection error: ${error?.toString() ?: "unknown"}")
                }
                errorHandler?.invoke(exception)
            }
        }
        
        socket?.on("reconnect") { args ->
            currentState = ConnectionState.CONNECTED
            scope.launch {
                val attempt = (args.getOrNull(0) as? Int) ?: 0
                reconnectHandler?.invoke(attempt)
            }
        }
        
        socket?.on("reconnecting") { args ->
            currentState = ConnectionState.RECONNECTING
            scope.launch {
                val attempt = (args.getOrNull(0) as? Int) ?: 0
                reconnectAttemptHandler?.invoke(attempt)
            }
        }
        
        socket?.on("reconnect_error") { args ->
            scope.launch {
                val error = args.getOrNull(0)
                val exception = if (error is Exception) {
                    SocketException("Reconnection error", error)
                } else {
                    SocketException("Reconnection error: ${error?.toString() ?: "unknown"}")
                }
                errorHandler?.invoke(exception)
            }
        }
        
        socket?.on("reconnect_failed") {
            currentState = ConnectionState.FAILED
            scope.launch { reconnectFailedHandler?.invoke() }
        }
    }
}

/**
 * Android implementation of data converter using JSONObject
 */
actual object SocketDataConverter {
    actual fun toMap(data: Any?): Map<String, Any?> {
        return when (data) {
            is JSONObject -> jsonObjectToMap(data)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                data as Map<String, Any?>
            }
            else -> emptyMap()
        }
    }
    
    actual fun fromMap(map: Map<String, Any?>): Any {
        try {
            val json = mapToJsonObject(map)
            Logger.d("SocketManager.androi", "MediaSFU - SocketDataConverter.fromMap: successfully converted map with ${map.size} keys")
            return json
        } catch (e: Exception) {
            Logger.d("SocketManager.androi", "MediaSFU - SocketDataConverter.fromMap: EXCEPTION during conversion: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }
    
    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val json = JSONObject()
        map.forEach { (key, value) ->
            try {
                json.put(key, convertToJson(value))
            } catch (e: Exception) {
                Logger.d("SocketManager.androi", "MediaSFU - mapToJsonObject: EXCEPTION for key '$key': ${e.javaClass.simpleName}: ${e.message}")
                throw e
            }
        }
        return json
    }
    
    private fun convertToJson(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any?>)
            }
            is List<*> -> {
                org.json.JSONArray().apply {
                    value.forEach { item ->
                        put(convertToJson(item))
                    }
                }
            }
            is String, is Number, is Boolean -> value
            else -> value.toString()
        }
    }
    
    private fun jsonObjectToMap(json: JSONObject): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        json.keys().forEach { key ->
            val value = json.get(key)
            map[key] = when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return map
    }

    private fun jsonArrayToList(array: JSONArray): List<Any?> {
        val list = mutableListOf<Any?>()
        for (index in 0 until array.length()) {
            val value = array.get(index)
            list += when (value) {
                is JSONObject -> jsonObjectToMap(value)
                is JSONArray -> jsonArrayToList(value)
                JSONObject.NULL -> null
                else -> value
            }
        }
        return list
    }
}

/**
 * Custom exception for socket-related errors
 */
class SocketException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
