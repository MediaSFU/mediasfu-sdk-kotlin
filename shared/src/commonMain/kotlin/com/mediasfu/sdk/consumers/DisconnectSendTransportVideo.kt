// DisconnectSendTransportVideo.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.WebRtcProducer

typealias DisconnectSendTransportVideoType = suspend (DisconnectSendTransportVideoOptions) -> Unit

/**
 * Parameters interface for disconnecting video send transport.
 *
 * This interface defines the state and functions needed for disconnecting
 * video send transport connections and cleaning up video resources.
 */
interface DisconnectSendTransportVideoParameters {
    // Remote Video Transport and Producer
    val videoProducer: WebRtcProducer?
    val socket: SocketManager?
    
    // Local Video Transport and Producer
    val localVideoProducer: WebRtcProducer?
    val localSocket: SocketManager?
    
    // Other Parameters
    val islevel: String
    val lockScreen: Boolean
    val shared: Boolean
    val updateMainWindow: Boolean
    val hostLabel: String
    val roomName: String
    val audioAlreadyOn: Boolean
    
    // Update functions
    fun updateVideoProducer(producer: WebRtcProducer?)
    fun updateLocalVideoProducer(producer: WebRtcProducer?)
    fun updateUpdateMainWindow(update: Boolean)
    
    // MediaSFU functions
    suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit>
    
    // Get updated parameters (for accessing latest state)
    fun getUpdatedAllParams(): DisconnectSendTransportVideoParameters
}

/**
 * Options for disconnecting video send transport.
 *
 * @property parameters Parameters object for state management
 */
data class DisconnectSendTransportVideoOptions(
    val parameters: DisconnectSendTransportVideoParameters
)

/**
 * Exception thrown when video send transport disconnection fails.
 */
class DisconnectSendTransportVideoException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Disconnects the local send transport for video by closing the local video producer
 * and notifying the server.
 *
 * Workflow:
 * 1. **Close Local Video Producer**:
 *    - If an active local video producer exists, it is closed
 *    - Local state is updated to null after closing
 * 2. **Notify Server**:
 *    - Emits a pauseProducerMedia event to the server
 *    - Server is notified about the closed local video producer
 *
 * @param options Configuration options for disconnecting local video transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportVideoOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectLocalSendTransportVideo(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Local video disconnected")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectLocalSendTransportVideo(
    options: DisconnectSendTransportVideoOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        val localVideoProducer = parameters.localVideoProducer
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        
        // Check if local socket is connected
    if (localSocket == null || !localSocket.isConnected()) {
            // Local socket is not connected; nothing to disconnect
            return Result.success(Unit)
        }
        
        // Close the local video producer and update the state
        if (localVideoProducer != null) {
            try {
                // Close the producer to stop sending video
                localVideoProducer.close()
                
                // Set to null after closing
                parameters.updateLocalVideoProducer(null)
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportVideoException(
                        "Error closing local video producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        // Notify server about closed producer
        localSocket.emit(
            "pauseProducerMedia",
            mapOf(
                "mediaTag" to "video",
                "roomName" to roomName
            )
        )
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            DisconnectSendTransportVideoException(
                "Error disconnecting local video transport: ${e.message}",
                e
            )
        )
    }
}

/**
 * Disconnects the send transport for video by closing the video producer
 * and notifying the server.
 *
 * This function handles both remote and local video transport disconnection.
 * It manages producer lifecycle, updates UI state, and ensures proper cleanup
 * of video resources.
 *
 * Workflow:
 * 1. **Local Transport Disconnection**:
 *    - Disconnects local video transport if available
 *    - Closes local video producer
 * 2. **Remote Transport Disconnection**:
 *    - Closes remote video producer if it exists
 *    - Notifies server about closed producer
 * 3. **UI State Update**:
 *    - Updates main window if needed based on screen state
 *    - Repopulates user media display
 * 4. **State Cleanup**:
 *    - Sets video producer references to null
 *    - Updates all relevant state parameters
 *
 * @param options Configuration options for disconnecting video transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportVideoOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectSendTransportVideo(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Video transport disconnected successfully")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Disconnection failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectSendTransportVideo(
    options: DisconnectSendTransportVideoOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Get state
        val videoProducer = parameters.videoProducer
        val socket = parameters.socket
        val localSocket = parameters.localSocket
        val islevel = parameters.islevel
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared
        val roomName = parameters.roomName
        val hostLabel = parameters.hostLabel
        val audioAlreadyOn = parameters.audioAlreadyOn
        
        // Disconnect local video transport if available
    if (localSocket != null && localSocket.isConnected()) {
            disconnectLocalSendTransportVideo(options)
        }
        
        // Close the remote video producer if it exists
        if (videoProducer != null) {
            try {
                // Close the producer to stop sending video
                videoProducer.close()
                
                // Set to null after closing
                parameters.updateVideoProducer(null)
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportVideoException(
                        "Error closing video producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        // Notify server about closed producer
        socket?.emit(
            "pauseProducerMedia",
            mapOf(
                "mediaTag" to "video",
                "roomName" to roomName
            )
        )
        
        // Update UI if needed
        if (!audioAlreadyOn && islevel == "2") {
            if (!lockScreen && !shared) {
                parameters.updateUpdateMainWindow(true)
                
                // Prepopulate user media
                val prepopulateOptions = mapOf(
                    "name" to hostLabel,
                    "parameters" to parameters
                )
                parameters.prepopulateUserMedia(prepopulateOptions)
                
                parameters.updateUpdateMainWindow(false)
            }
        }
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            DisconnectSendTransportVideoException(
                "Error disconnecting video send transport: ${e.message}",
                e
            )
        )
    }
}
