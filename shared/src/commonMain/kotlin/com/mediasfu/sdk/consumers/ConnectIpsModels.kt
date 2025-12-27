// ConnectIpsModels.kt
package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.socket.SocketManager

/**
 * Parameters interface for connecting IPs and managing socket connections.
 *
 * This interface defines the state and update functions needed for managing
 * remote socket connections to consume media streams from multiple IPs.
 */
interface ConnectIpsParameters {
    // State properties
    val roomRecvIPs: List<String>
    val consumeSockets: List<Map<String, SocketManager>>
    
    // Update functions
    fun updateRoomRecvIPs(roomRecvIPs: List<String>)
    fun updateConsumeSockets(consumeSockets: List<Map<String, SocketManager>>)
    
    // Get updated parameters (for accessing latest state)
    fun getUpdatedAllParams(): ConnectIpsParameters
}

/**
 * Options for connecting IPs and managing socket connections.
 *
 * @property consumeSockets List of socket connections for each IP (initially empty)
 * @property remIP List of remote IPs to connect to (e.g., ["100.122.1.1", "100.122.1.2"])
 * @property apiUserName API username for authentication
 * @property apiKey Optional API key for authentication
 * @property apiToken API token for authentication
 * @property newProducerMethod Optional function to handle new producer events
 * @property closedProducerMethod Optional function to handle closed producer events
 * @property joinConsumeRoomMethod Optional function to handle joining a consume room
 * @property parameters Parameters object to handle state updates
 */
data class ConnectIpsOptions(
    val consumeSockets: List<Map<String, SocketManager>>,
    val remIP: List<String>,
    val apiUserName: String,
    val apiKey: String? = null,
    val apiToken: String,
    val newProducerMethod: (suspend (NewPipeProducerOptions) -> Unit)? = null,
    val closedProducerMethod: (suspend (ProducerClosedOptions) -> Unit)? = null,
    val joinConsumeRoomMethod: (suspend (JoinConsumeRoomOptions) -> Result<Map<String, Any?>>)? = null,
    val parameters: ConnectIpsParameters
)

/**
 * Result of connecting to remote IPs.
 *
 * @property consumeSockets Updated list of socket connections with newly connected sockets
 * @property roomRecvIPs Updated list of connected IP addresses
 */
data class ConnectIpsResult(
    val consumeSockets: List<Map<String, SocketManager>>,
    val roomRecvIPs: List<String>
)

/**
 * Options for handling new pipe producer events.
 *
 * @property producerId ID of the new producer
 * @property islevel Level of the producer (e.g., "0", "1", "2")
 * @property nsock Remote socket connection
 * @property parameters Parameters for state management
 */
data class NewPipeProducerOptions(
    val producerId: String,
    val islevel: String,
    val nsock: SocketManager,
    val parameters: ConnectIpsParameters
)

/**
 * Options for handling producer closed events.
 *
 * @property remoteProducerId ID of the closed producer
 * @property parameters Parameters for state management
 */
data class ProducerClosedOptions(
    val remoteProducerId: String,
    val parameters: ConnectIpsParameters
)

/**
 * Options for joining a consume room.
 *
 * @property remoteSock Remote socket connection
 * @property apiToken API token for authentication
 * @property apiUserName API username for authentication
 * @property parameters Parameters for state management
 */
data class JoinConsumeRoomOptions(
    val remoteSock: SocketManager,
    val apiToken: String,
    val apiUserName: String,
    val parameters: ConnectIpsParameters
)

/**
 * Exception thrown when connecting to IPs fails.
 *
 * @property message Error message
 * @property cause Original exception (if any)
 */
class ConnectIpsException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
