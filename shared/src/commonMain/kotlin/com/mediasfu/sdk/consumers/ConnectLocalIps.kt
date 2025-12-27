// ConnectLocalIps.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mediasfu.sdk.consumers.socket_receive_methods.NewPipeProducerOptions as SocketNewPipeProducerOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedOptions as SocketProducerClosedOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.newPipeProducer
import com.mediasfu.sdk.consumers.socket_receive_methods.producerClosed

/**
 * Parameters interface for connecting local IPs and managing socket connections.
 *
 * This interface defines the state and functions needed for managing
 * local socket connections for media consumption (e.g., on-premise deployments).
 */
interface ConnectLocalIpsParameters {
    // State properties
    val socket: SocketManager?
    
    // Get updated parameters (for accessing latest state)
    fun getUpdatedAllParams(): ConnectLocalIpsParameters
}

/**
 * Options for new producer event (local connections).
 *
 * @property producerId ID of the new producer
 * @property islevel Level indicator for the producer
 * @property nsock Socket connection
 * @property parameters Parameters for state management
 */
data class LocalNewProducerOptions(
    val producerId: String,
    val islevel: String,
    val nsock: SocketManager,
    val parameters: ConnectLocalIpsParameters
)

/**
 * Options for producer closed event (local connections).
 *
 * @property remoteProducerId ID of the closed producer
 * @property parameters Parameters for state management
 */
data class LocalProducerClosedOptions(
    val remoteProducerId: String,
    val parameters: ConnectLocalIpsParameters
)

/**
 * Options for connecting local IPs and managing socket connections.
 *
 * @property socket The local socket connection (already established)
 * @property newProducerMethod Optional function to handle new producer events
 * @property closedProducerMethod Optional function to handle closed producer events
 * @property receiveAllPipedTransportsMethod Optional function to initialize piped transports
 * @property parameters Parameters object to handle state updates
 */
data class ConnectLocalIpsOptions(
    val socket: SocketManager?,
    val newProducerMethod: (suspend (LocalNewProducerOptions) -> Unit)? = null,
    val closedProducerMethod: (suspend (LocalProducerClosedOptions) -> Unit)? = null,
    val receiveAllPipedTransportsMethod: (suspend (ReceiveAllPipedTransportsOptions) -> Unit)? = null,
    val parameters: ConnectLocalIpsParameters
)

/**
 * Options for receiving all piped transports.
 *
 * @property community Whether this is a community transport
 * @property nsock Socket connection
 * @property parameters Parameters for state management
 */
data class ReceiveAllPipedTransportsOptions(
    val community: Boolean,
    val nsock: SocketManager,
    val parameters: ConnectLocalIpsParameters
)

/**
 * Exception thrown when connecting to local IPs fails.
 *
 * @property message Error message
 * @property cause Original exception (if any)
 */
class ConnectLocalIpsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Connects to a local socket and manages socket events for media consumption.
 *
 * This function sets up event listeners on the provided local socket for handling
 * new media producers and closed producers. It utilizes the provided methods to
 * manage these events accordingly.
 *
 * ## Features:
 * - Registers event handlers on existing socket
 * - Handles 'new-producer' events
 * - Handles 'producer-closed' events
 * - Initializes piped transports for local consumption
 * - Prevents duplicate listener registration
 *
 * ## Parameters:
 * - [options] Configuration options for establishing connections and managing sockets
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val options = ConnectLocalIpsOptions(
 *     socket = localSocket,
 *     parameters = connectLocalIpsParametersInstance
 * )
 *
 * val result = connectLocalIps(options)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ConnectLocalIps", "Error connecting to local IPs: ${error.message}")
 * }
 * ```
 *
 * ## Error Handling:
 * - Returns Result.failure if socket is null
 * - Returns Result.success immediately if listeners already registered
 * - Logs errors without throwing exceptions
 *
 * ## Implementation Notes:
 * - Checks for existing listeners to avoid duplicates
 * - Uses default implementations if custom methods not provided
 * - Initializes piped transports after event registration
 * - All event handlers run in coroutine scope
 *
 * @see ConnectLocalIpsOptions
 * @see ConnectLocalIpsParameters
 */
suspend fun connectLocalIps(options: ConnectLocalIpsOptions): Result<Unit> {
    return try {
        // Get latest parameters
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Check if socket is provided
        val socket = options.socket
            ?: return Result.failure(
                ConnectLocalIpsException("Socket connection is null")
            )
        
        // Use provided methods or defaults
        val newProducerMethod = options.newProducerMethod ?: ::defaultLocalNewProducer
        val closedProducerMethod = options.closedProducerMethod ?: ::defaultLocalProducerClosed
        val receiveAllPipedTransportsMethod = options.receiveAllPipedTransportsMethod 
            ?: ::defaultReceiveAllPipedTransports
        
        // Check if listener is already set (prevent duplicates)
        val alreadyListening = hasListener(socket, "new-producer")
        if (alreadyListening) {
            return Result.success(Unit)
        }

        // Make sure we start from a clean slate before registering
        runCatching { socket.off("new-producer") }
        runCatching { socket.off("producer-closed") }
        
        // Register event handler for 'new-producer' event
        socket.on("new-producer") { payload ->
            val producerId = (payload as? Map<*, *>)?.get("producerId") as? String ?: ""
            val islevel = (payload as? Map<*, *>)?.get("islevel") as? String ?: "0"
            if (producerId.isBlank()) {
                Logger.e("ConnectLocalIps", "MediaSFU - connectLocalIps warning: 'new-producer' missing producerId; ignoring event")
                return@on
            }

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val latestParams = parameters.getUpdatedAllParams()
                    newProducerMethod(
                        LocalNewProducerOptions(
                            producerId = producerId,
                            islevel = islevel,
                            nsock = socket,
                            parameters = latestParams
                        )
                    )
                } catch (e: Exception) {
                    Logger.e("ConnectLocalIps", "MediaSFU - connectLocalIps error handling new-producer ($producerId): ${e.message}")
                }
            }
        }
        
        // Register event handler for 'producer-closed' event
        socket.on("producer-closed") { payload ->
            val remoteProducerId = (payload as? Map<*, *>)?.get("remoteProducerId") as? String ?: ""
            if (remoteProducerId.isBlank()) {
                Logger.e("ConnectLocalIps", "MediaSFU - connectLocalIps warning: 'producer-closed' missing remoteProducerId; ignoring event")
                return@on
            }

            CoroutineScope(Dispatchers.Default).launch {
                try {
                    val latestParams = parameters.getUpdatedAllParams()
                    closedProducerMethod(
                        LocalProducerClosedOptions(
                            remoteProducerId = remoteProducerId,
                            parameters = latestParams
                        )
                    )
                } catch (e: Exception) {
                    Logger.e("ConnectLocalIps", "MediaSFU - connectLocalIps error handling producer-closed ($remoteProducerId): ${e.message}")
                }
            }
        }
        
        // Initialize piped transports
        receiveAllPipedTransportsMethod(
            ReceiveAllPipedTransportsOptions(
                community = true,
                nsock = socket,
                parameters = parameters
            )
        )
        
        Result.success(Unit)
    } catch (error: Exception) {
        Logger.e("ConnectLocalIps", "MediaSFU - connectLocalIps error: ${error.message}")
        Result.failure(
            ConnectLocalIpsException(
                "connectLocalIps error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Checks if a socket has a listener registered for the given event.
 *
 * @param socket Socket to check
 * @param eventName Name of the event
 * @return true if listener exists, false otherwise
 */
fun hasListener(socket: SocketManager, eventName: String): Boolean {
    // TODO: Implement listener check in SocketManager
    // For now, return false to allow registration
    return false
}

/**
 * Default implementation for handling new producer events (local connections).
 * This is a placeholder that should be replaced with actual implementation.
 */
suspend fun defaultLocalNewProducer(options: LocalNewProducerOptions) {
    val latestParams = options.parameters.getUpdatedAllParams()
    val resolvedParams = latestParams.resolveNewPipeProducerParameters()

    if (resolvedParams == null) {
        Logger.e("ConnectLocalIps", 
            "MediaSFU - defaultLocalNewProducer: unable to resolve parameters for producer ${options.producerId}; skipping"
        )
        return
    }

    val socketOptions = SocketNewPipeProducerOptions(
        producerId = options.producerId,
        islevel = options.islevel,
        nsock = options.nsock,
        parameters = resolvedParams
    )

    runCatching { newPipeProducer(socketOptions) }
        .onFailure { error ->
            Logger.e("ConnectLocalIps", 
                "MediaSFU - defaultLocalNewProducer error for ${options.producerId}: ${error.message}"
            )
        }
}

/**
 * Default implementation for handling producer closed events (local connections).
 * This is a placeholder that should be replaced with actual implementation.
 */
suspend fun defaultLocalProducerClosed(options: LocalProducerClosedOptions) {
    val latestParams = options.parameters.getUpdatedAllParams()
    val resolvedParams = latestParams.resolveProducerClosedParameters()

    if (resolvedParams == null) {
        Logger.e("ConnectLocalIps", 
            "MediaSFU - defaultLocalProducerClosed: unable to resolve parameters for ${options.remoteProducerId}; skipping"
        )
        return
    }

    val socketOptions = SocketProducerClosedOptions(
        remoteProducerId = options.remoteProducerId,
        parameters = resolvedParams
    )

    runCatching { producerClosed(socketOptions) }
        .onFailure { error ->
            Logger.e("ConnectLocalIps", 
                "MediaSFU - defaultLocalProducerClosed error for ${options.remoteProducerId}: ${error.message}"
            )
        }
}

/**
 * Default implementation for receiving all piped transports.
 * This is a placeholder that should be replaced with actual implementation.
 */
suspend fun defaultReceiveAllPipedTransports(
    options: ReceiveAllPipedTransportsOptions
) {
    val latestParams = options.parameters.getUpdatedAllParams()
    val resolvedParams = latestParams.resolveReceiveAllPipedTransportsParameters()

    if (resolvedParams == null) {
        Logger.e("ConnectLocalIps", "MediaSFU - defaultReceiveAllPipedTransports: unable to resolve parameters; skipping initialization")
        return
    }

    runCatching {
        receiveAllPipedTransportsImpl(
            nsock = options.nsock,
            community = options.community,
            roomName = resolvedParams.roomName,
            member = resolvedParams.member,
            getPipedProducersAlt = { altOptions ->
                resolvedParams.getPipedProducersAlt(
                    altOptions.copy(parameters = resolvedParams)
                )
            }
        )
    }.onFailure { error ->
        Logger.e("ConnectLocalIps", "MediaSFU - defaultReceiveAllPipedTransports error: ${error.message}")
    }
}
