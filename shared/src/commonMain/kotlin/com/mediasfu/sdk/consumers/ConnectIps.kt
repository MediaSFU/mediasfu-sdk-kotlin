// ConnectIps.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.ConnectionState
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.mediasfu.sdk.consumers.socket_receive_methods.NewPipeProducerOptions as SocketNewPipeProducerOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedOptions as SocketProducerClosedOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.newPipeProducer
import com.mediasfu.sdk.consumers.socket_receive_methods.producerClosed

/**
 * Connects to multiple remote IPs to manage socket connections for media consumption.
 *
 * This function iterates over a list of remote IPs, attempting to establish socket connections
 * and manage events for new media producers and closed producers in the connected rooms. If successful,
 * it updates the consumeSockets list with each connected socket and tracks connected IPs in roomRecvIPs.
 *
 * ## Features:
 * - Establishes socket connections to multiple remote IPs
 * - Handles socket events (new-pipe-producer, producer-closed)
 * - Joins consumption rooms for each connected IP
 * - Updates state with connected sockets and IPs
 * - Provides default implementations for event handlers
 *
 * ## Parameters:
 * - [options] Configuration options for establishing connections and managing sockets
 *
 * ## Returns:
 * - [Result]<[ConnectIpsResult]> containing:
 *   - Success: Updated consumeSockets and roomRecvIPs
 *   - Failure: Exception with error details
 *
 * ## Example Usage:
 * ```kotlin
 * val options = ConnectIpsOptions(
 *     consumeSockets = emptyList(),
 *     remIP = listOf("100.122.1.1", "100.122.1.2"),
 *     apiUserName = "myUserName",
 *     apiToken = "myToken",
 *     parameters = myConnectIpsParametersInstance
 * )
 *
 * val result = connectIps(options)
 * result.onSuccess { (consumeSockets, roomRecvIPs) ->
 * }
 * result.onFailure { error ->
 *     Logger.e("ConnectIps", "Connection failed: ${error.message}")
 * }
 * ```
 *
 * ## Error Handling:
 * - Returns Result.failure if authentication parameters are missing
 * - Logs errors for individual IP connection failures without stopping iteration
 * - Returns partial success if some IPs connect successfully
 *
 * ## Implementation Notes:
 * - Skips IPs that are already connected
 * - Skips empty, blank, or "none" IPs
 * - Uses default event handlers if not provided
 * - Each IP connection is attempted independently
 * - Connection timeout: 30 seconds per IP
 *
 * @see ConnectIpsOptions
 * @see ConnectIpsResult
 * @see ConnectIpsParameters
 */
suspend fun connectIps(options: ConnectIpsOptions): Result<ConnectIpsResult> {
    return try {
        // Get latest parameters
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Start with provided sockets and IPs
        val consumeSockets = options.consumeSockets.toMutableList()
        val roomRecvIPs = parameters.roomRecvIPs.toMutableList()
        
        // Check for required authentication parameters
        if (options.apiKey == null && options.apiToken.isEmpty()) {
            return Result.failure(
                ConnectIpsException("Missing required parameters for authentication")
            )
        }
        
        // Use provided methods or defaults
        val newProducerMethod = options.newProducerMethod ?: ::defaultNewPipeProducer
        val closedProducerMethod = options.closedProducerMethod ?: ::defaultProducerClosed
        val joinConsumeRoomMethod = options.joinConsumeRoomMethod ?: ::defaultJoinConsumeRoom
        
        // Iterate through remote IPs
        for (ip in options.remIP) {
            try {
                // Skip if IP is already connected
                val existingSocket = consumeSockets.find { socketMap ->
                    socketMap.containsKey(ip)
                }
                
                if (existingSocket != null || ip.isBlank() || ip == "none") {
                    continue
                }
                
                // Connect to the remote socket
                val remoteSocket = connectToRemoteSocket(
                    ip = ip,
                    apiUserName = options.apiUserName,
                    apiKey = options.apiKey ?: "",
                    apiToken = options.apiToken
                )
                
                // Check if connection was successful
                if (remoteSocket.getConnectionState() == ConnectionState.CONNECTED) {
                    // Add IP to roomRecvIPs if not already present
                    if (!roomRecvIPs.contains(ip)) {
                        roomRecvIPs.add(ip)
                        parameters.updateRoomRecvIPs(roomRecvIPs.toList())
                    }
                    
                    // Register event handlers
                    registerEventHandlers(
                        socket = remoteSocket,
                        parameters = parameters,
                        newProducerMethod = newProducerMethod,
                        closedProducerMethod = closedProducerMethod
                    )
                    
                    // Join the consumption room
                    val joinResult = joinConsumeRoomMethod(
                        JoinConsumeRoomOptions(
                            remoteSock = remoteSocket,
                            apiToken = options.apiToken,
                            apiUserName = options.apiUserName,
                            parameters = parameters
                        )
                    )
                    
                    joinResult.onSuccess { data ->
                        // Verify rtpCapabilities exist
                        val rtpCapabilities = data["rtpCapabilities"]
                        
                        if (rtpCapabilities != null) {
                            // Add the remote socket to consumeSockets
                            consumeSockets.add(mapOf(ip to remoteSocket))
                            parameters.updateConsumeSockets(consumeSockets.toList())
                        }
                    }
                    
                    // Note: If join fails, we don't add the socket but continue with other IPs
                }
            } catch (error: Exception) {
                // Log error but continue with next IP
                Logger.e("ConnectIps", "connectIps error with IP $ip: ${error.message}")
            }
        }
        
        // Return updated sockets and IPs
        Result.success(
            ConnectIpsResult(
                consumeSockets = consumeSockets.toList(),
                roomRecvIPs = roomRecvIPs.toList()
            )
        )
    } catch (error: Exception) {
        Result.failure(
            ConnectIpsException(
                "connectIps error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Connects to a remote socket using SocketManager.
 *
 * @param ip Remote IP address
 * @param apiUserName API username for authentication
 * @param apiKey API key for authentication
 * @param apiToken API token for authentication
 * @return Connected SocketManager instance
 * @throws ConnectIpsException if connection fails
 */
private suspend fun connectToRemoteSocket(
    ip: String,
    apiUserName: String,
    apiKey: String,
    apiToken: String
): SocketManager {
    val socketManager = com.mediasfu.sdk.socket.createSocketManager()
    val url = "https://$ip.mediasfu.com"
    
    // Create socket config with authentication
    val config = com.mediasfu.sdk.model.SocketConfig(
        timeout = 30000,
        reconnection = true,
        reconnectionAttempts = 3,
        reconnectionDelay = 1000
    )
    
    val connectResult = withTimeout(30000) {
        socketManager.connect(url, config)
    }
    
    connectResult.getOrThrow()
    return socketManager
}

/**
 * Registers event handlers for new-pipe-producer and producer-closed events.
 *
 * @param socket Socket to register handlers on
 * @param parameters Parameters for state management
 * @param newProducerMethod Handler for new producer events
 * @param closedProducerMethod Handler for closed producer events
 */
private fun registerEventHandlers(
    socket: SocketManager,
    parameters: ConnectIpsParameters,
    newProducerMethod: suspend (NewPipeProducerOptions) -> Unit,
    closedProducerMethod: suspend (ProducerClosedOptions) -> Unit
) {
    // Register handler for 'new-pipe-producer' event
    socket.on("new-pipe-producer") { data ->
        // Extract producer information from event data
        val producerId = (data as? Map<*, *>)?.get("producerId") as? String ?: ""
        val islevel = (data as? Map<*, *>)?.get("islevel") as? String ?: "0"
        
        // Call the new producer method in a coroutine scope
        CoroutineScope(Dispatchers.Default).launch {
            try {
                newProducerMethod(
                    NewPipeProducerOptions(
                        producerId = producerId,
                        islevel = islevel,
                        nsock = socket,
                        parameters = parameters
                    )
                )
            } catch (e: Exception) {
                Logger.e("ConnectIps", "Error handling new-pipe-producer: ${e.message}")
            }
        }
    }
    
    // Register handler for 'producer-closed' event
    socket.on("producer-closed") { data ->
        // Extract producer ID from event data
        val remoteProducerId = (data as? Map<*, *>)?.get("remoteProducerId") as? String ?: ""
        
        // Call the closed producer method in a coroutine scope
        CoroutineScope(Dispatchers.Default).launch {
            try {
                closedProducerMethod(
                    ProducerClosedOptions(
                        remoteProducerId = remoteProducerId,
                        parameters = parameters
                    )
                )
            } catch (e: Exception) {
                Logger.e("ConnectIps", "Error handling producer-closed: ${e.message}")
            }
        }
    }
}

/**
 * Default implementation for handling new pipe producer events.
 * This is a placeholder that should be replaced with actual implementation.
 */
private suspend fun defaultNewPipeProducer(options: NewPipeProducerOptions) {
    val latestParams = options.parameters.getUpdatedAllParams()
    val resolvedParams = latestParams.resolveNewPipeProducerParameters()

    if (resolvedParams == null) {
        Logger.e("ConnectIps", 
            "MediaSFU - defaultNewPipeProducer: unable to resolve parameters for producer ${options.producerId}; skipping"
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
            Logger.e("ConnectIps", 
                "MediaSFU - defaultNewPipeProducer error for ${options.producerId}: ${error.message}"
            )
        }
}

/**
 * Default implementation for handling producer closed events.
 * Delegates to the shared socket handler logic.
 */
private suspend fun defaultProducerClosed(options: ProducerClosedOptions) {
    val latestParams = options.parameters.getUpdatedAllParams()
    val resolvedParams = latestParams.resolveProducerClosedParameters()

    if (resolvedParams == null) {
        Logger.e("ConnectIps", 
            "MediaSFU - defaultProducerClosed: unable to resolve parameters for ${options.remoteProducerId}; skipping"
        )
        return
    }

    val socketOptions = SocketProducerClosedOptions(
        remoteProducerId = options.remoteProducerId,
        parameters = resolvedParams
    )

    runCatching { producerClosed(socketOptions) }
        .onFailure { error ->
            Logger.e("ConnectIps", 
                "MediaSFU - defaultProducerClosed error for ${options.remoteProducerId}: ${error.message}"
            )
        }
}

/**
 * Default implementation for joining a consume room.
 * This emits a joinConsumeRoom event and waits for response.
 */
private suspend fun defaultJoinConsumeRoom(
    options: JoinConsumeRoomOptions
): Result<Map<String, Any?>> {
    return try {
        val data = mapOf(
            "apiToken" to options.apiToken,
            "apiUserName" to options.apiUserName
        )
        
        val result = runCatching {
            withTimeout(30000) {
                options.remoteSock.emitWithAck<Map<String, Any?>>("joinConsumeRoom", data)
            }
        }

        result.fold(
            onSuccess = { responseData ->
                if (responseData.containsKey("rtpCapabilities")) {
                    Result.success(responseData)
                } else {
                    Result.failure(
                        ConnectIpsException("Invalid joinConsumeRoom response: missing rtpCapabilities")
                    )
                }
            },
            onFailure = { error ->
                Result.failure(
                    ConnectIpsException("Failed to join consume room: ${error.message}", error)
                )
            }
        )
    } catch (e: Exception) {
        Result.failure(
            ConnectIpsException("Error joining consume room: ${e.message}", e)
        )
    }
}
