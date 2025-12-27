// DisconnectSendTransportScreen.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.WebRtcProducer

/**
 * Parameters interface for disconnecting screen send transport.
 *
 * This interface defines the state and functions needed for disconnecting
 * screen share send transport connections and cleaning up screen resources.
 */
interface DisconnectSendTransportScreenParameters {
    // Remote Screen Transport and Producer
    val screenProducer: WebRtcProducer?
    val socket: SocketManager?
    
    // Local Screen Transport and Producer
    val localScreenProducer: WebRtcProducer?
    val localSocket: SocketManager?
    
    // Other Parameters
    val roomName: String
    
    // Update functions
    fun updateScreenProducer(producer: WebRtcProducer?)
    fun updateLocalScreenProducer(producer: WebRtcProducer?)
    
    // Get updated parameters (for accessing latest state)
    fun getUpdatedAllParams(): DisconnectSendTransportScreenParameters
}

/**
 * Options for disconnecting screen send transport.
 *
 * @property parameters Parameters object for state management
 */
data class DisconnectSendTransportScreenOptions(
    val parameters: DisconnectSendTransportScreenParameters
)

/**
 * Exception thrown when screen send transport disconnection fails.
 */
class DisconnectSendTransportScreenException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Disconnects the local send transport for screen sharing by closing the local 
 * screen producer and notifying the server.
 *
 * Workflow:
 * 1. **Close Local Screen Producer**:
 *    - If an active local screen producer exists, it is closed
 *    - Local state is updated to reflect the closed producer
 * 2. **Notify Server**:
 *    - Emits closeScreenProducer event to the server
 *    - Emits pauseProducerMedia event to notify about paused screen sharing
 *
 * @param options Configuration options for disconnecting local screen transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportScreenOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectLocalSendTransportScreen(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Local screen disconnected")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectLocalSendTransportScreen(
    options: DisconnectSendTransportScreenOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        val localScreenProducer = parameters.localScreenProducer
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        
        // Check if local socket is connected
        if (localSocket == null || !localSocket.isConnected()) {
            // Local socket is not connected; nothing to disconnect
            return Result.success(Unit)
        }
        
        // Close the local screen producer and update the state
        if (localScreenProducer != null) {
            try {
                // Close the producer to stop sending screen
                localScreenProducer.close()
                
                // Set to null after closing
                parameters.updateLocalScreenProducer(null)
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportScreenException(
                        "Error closing local screen producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        // Notify the server about closing the local screen producer and pausing screen sharing
        localSocket.emit("closeScreenProducer", emptyMap<String, Any>())
        
        localSocket.emit(
            "pauseProducerMedia",
            mapOf(
                "mediaTag" to "screen",
                "roomName" to roomName
            )
        )
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            DisconnectSendTransportScreenException(
                "Error disconnecting local screen transport: ${e.message}",
                e
            )
        )
    }
}

/**
 * Disconnects the send transport for screen sharing by closing the screen producer
 * and notifying the server.
 *
 * This function handles both remote and local screen transport disconnection.
 * It manages producer lifecycle and ensures proper cleanup of screen sharing resources.
 *
 * Workflow:
 * 1. **Local Transport Disconnection**:
 *    - Disconnects local screen transport if available
 *    - Closes local screen producer
 *    - Notifies local server
 * 2. **Remote Transport Disconnection**:
 *    - Closes remote screen producer if it exists
 *    - Notifies remote server about closed producer
 * 3. **State Cleanup**:
 *    - Clears screen producer references
 *    - Updates all relevant state parameters
 * 4. **Server Notification**:
 *    - Emits closeScreenProducer event
 *    - Emits pauseProducerMedia event with screen tag
 *
 * @param options Configuration options for disconnecting screen transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportScreenOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectSendTransportScreen(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Screen transport disconnected successfully")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Disconnection failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectSendTransportScreen(
    options: DisconnectSendTransportScreenOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Get state
        val screenProducer = parameters.screenProducer
        val socket = parameters.socket
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        
        // Disconnect local screen transport if available
        if (localSocket != null && localSocket.isConnected()) {
            disconnectLocalSendTransportScreen(options)
        }
        
        // Close the remote screen producer if it exists
        if (screenProducer != null) {
            try {
                // Close the producer to stop sending screen
                screenProducer.close()
                
                // Update screen producer state
                parameters.updateScreenProducer(null)
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportScreenException(
                        "Error closing screen producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        // Notify the server about closing the screen producer and pausing screen sharing
        socket?.emit("closeScreenProducer", emptyMap<String, Any>())
        
        socket?.emit(
            "pauseProducerMedia",
            mapOf(
                "mediaTag" to "screen",
                "roomName" to roomName
            )
        )
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            DisconnectSendTransportScreenException(
                "Error disconnecting screen send transport: ${e.message}",
                e
            )
        )
    }
}
