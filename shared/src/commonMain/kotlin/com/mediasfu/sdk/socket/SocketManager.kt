package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.SocketConfig

/**
 * Socket manager for handling real-time communication with MediaSFU servers.
 * 
 * This interface defines the contract for managing Socket.IO connections,
 * including connection lifecycle, event emission, and event registration.
 * 
 * Usage:
 * ```kotlin
 * val socketManager: SocketManager = SocketManagerImpl()
 * 
 * // Connect to server
 * socketManager.connect("https://mediasfu.com", SocketConfig())
 * 
 * // Register event handlers
 * socketManager.on("message") { data ->
 * }
 * 
 * // Emit events
 * socketManager.emit("sendMessage", mapOf("text" to "Hello"))
 * 
 * // Disconnect
 * socketManager.disconnect()
 * ```
 */
interface SocketManager {

    /** Unique identifier of the underlying socket connection, when available. */
    val id: String?
    
    // ========================================================================
    // Connection Lifecycle
    // ========================================================================
    
    /**
     * Connect to a Socket.IO server.
     * 
     * @param url The WebSocket server URL (e.g., "https://mediasfu.com")
     * @param config Socket configuration (reconnection settings, timeout, etc.)
     * @return Result indicating success or failure with error details
     */
    suspend fun connect(url: String, config: SocketConfig = SocketConfig()): Result<Unit>
    
    /**
     * Disconnect from the Socket.IO server.
     * Cleans up all event handlers and closes the connection.
     * 
     * @return Result indicating success or failure with error details
     */
    suspend fun disconnect(): Result<Unit>
    
    /**
     * Check if currently connected to the server.
     * 
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean
    
    /**
     * Get the current connection state.
     * 
     * @return Current connection state
     */
    fun getConnectionState(): ConnectionState
    
    // ========================================================================
    // Event Emission
    // ========================================================================
    
    /**
     * Emit an event to the server without waiting for acknowledgment.
     * 
     * @param event The event name
    * @param data The event data as a map
     */
    suspend fun emit(event: String, data: Map<String, Any?>)
    
    /**
     * Emit an event and wait for acknowledgment from the server.
     * 
     * @param event The event name
     * @param data The event data as a map
    * @param timeout Timeout in milliseconds (default: 5000)
     */
    suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long = 5000
    ): T

    /** Emit an event and invoke [callback] when the acknowledgment arrives. */
    fun emitWithAck(
        event: String,
        data: Map<String, Any?>,
        callback: (Any?) -> Unit
    )
    
    // ========================================================================
    // Event Registration
    // ========================================================================
    
    /**
     * Register a handler for a specific event.
     * The handler will be called whenever the event is received from the server.
     * 
     * @param event The event name to listen for
     * @param handler Suspend function to handle the event data
     */
    fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit)
    
    /**
     * Unregister a handler for a specific event.
     * 
     * @param event The event name to stop listening for
     */
    fun off(event: String)
    
    /**
     * Remove all event handlers.
     */
    fun offAll()
    
    // ========================================================================
    // Connection State Handlers
    // ========================================================================
    
    /**
     * Register a handler for connection events.
     * Called when the socket successfully connects.
     * 
     * @param handler Suspend function called on connection
     */
    fun onConnect(handler: suspend () -> Unit)
    
    /**
     * Register a handler for disconnection events.
     * Called when the socket disconnects (either intentionally or due to error).
     * 
     * @param handler Suspend function called on disconnection with reason
     */
    fun onDisconnect(handler: suspend (String) -> Unit)
    
    /**
     * Register a handler for error events.
     * Called when a connection or communication error occurs.
     * 
     * @param handler Suspend function called on error
     */
    fun onError(handler: suspend (Throwable) -> Unit)
    
    /**
     * Register a handler for reconnection events.
     * Called when the socket successfully reconnects after a disconnection.
     * 
     * @param handler Suspend function called on reconnection with attempt number
     */
    fun onReconnect(handler: suspend (Int) -> Unit)
    
    /**
     * Register a handler for reconnection attempts.
     * Called before each reconnection attempt.
     * 
     * @param handler Suspend function called before reconnection with attempt number
     */
    fun onReconnectAttempt(handler: suspend (Int) -> Unit)
    
    /**
     * Register a handler for reconnection failures.
     * Called when all reconnection attempts have been exhausted.
     * 
     * @param handler Suspend function called on reconnection failure
     */
    fun onReconnectFailed(handler: suspend () -> Unit)
}

/**
 * Connection state enum
 */
enum class ConnectionState {
    /** Not connected and not attempting to connect */
    DISCONNECTED,
    
    /** Currently attempting to establish connection */
    CONNECTING,
    
    /** Successfully connected */
    CONNECTED,
    
    /** Connection lost, attempting to reconnect */
    RECONNECTING,
    
    /** Connection failed and all reconnection attempts exhausted */
    FAILED
}
