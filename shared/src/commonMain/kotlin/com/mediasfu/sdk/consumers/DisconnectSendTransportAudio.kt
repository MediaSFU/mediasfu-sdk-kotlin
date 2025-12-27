// DisconnectSendTransportAudio.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.WebRtcProducer

typealias DisconnectSendTransportAudioType = suspend (DisconnectSendTransportAudioOptions) -> Unit

/**
 * Parameters interface for disconnecting audio send transport.
 *
 * This interface defines the state and functions needed for disconnecting
 * audio send transport connections and cleaning up audio resources.
 */
interface DisconnectSendTransportAudioParameters {
    // Remote Audio Transport and Producer
    val audioProducer: WebRtcProducer?
    val socket: SocketManager?
    
    // Local Audio Transport and Producer
    val localAudioProducer: WebRtcProducer?
    val localSocket: SocketManager?
    
    // Other Parameters
    val videoAlreadyOn: Boolean
    val islevel: String
    val lockScreen: Boolean
    val shared: Boolean
    val updateMainWindow: Boolean
    val hostLabel: String
    val roomName: String
    
    // Update functions
    fun updateAudioProducer(producer: WebRtcProducer?)
    fun updateLocalAudioProducer(producer: WebRtcProducer?)
    fun updateUpdateMainWindow(update: Boolean)
    
    // MediaSFU functions
    suspend fun prepopulateUserMedia(options: Map<String, Any>): Result<Unit>
    
    // Get updated parameters (for accessing latest state)
    fun getUpdatedAllParams(): DisconnectSendTransportAudioParameters
}

/**
 * Options for disconnecting audio send transport.
 *
 * @property parameters Parameters object for state management
 */
data class DisconnectSendTransportAudioOptions(
    val parameters: DisconnectSendTransportAudioParameters
)

/**
 * Exception thrown when audio send transport disconnection fails.
 */
class DisconnectSendTransportAudioException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Disconnects the local send transport for audio by pausing the local audio producer
 * and notifying the server.
 *
 * Workflow:
 * 1. **Pause Local Audio Producer**:
 *    - If an active local audio producer exists, it is paused
 *    - Local state is updated to reflect the paused producer
 * 2. **Notify Server**:
 *    - Emits a pauseProducerMedia event to the server
 *    - Server is notified about the paused local audio producer
 *
 * @param options Configuration options for disconnecting local audio transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportAudioOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectLocalSendTransportAudio(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Local audio disconnected")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectLocalSendTransportAudio(
    options: DisconnectSendTransportAudioOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        val localAudioProducer = parameters.localAudioProducer
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        
        // Check if local socket is connected
        if (localSocket == null || !localSocket.isConnected()) {
            // Local socket is not connected; nothing to disconnect
            return Result.success(Unit)
        }
        
        // Pause the local audio producer
        if (localAudioProducer != null) {
            try {
                // Pause the producer to stop sending audio
                localAudioProducer.pause()
                
                // Notify server about paused producer
                localSocket.emit(
                    "pauseProducerMedia",
                    mapOf(
                        "mediaTag" to "audio",
                        "roomName" to roomName
                    )
                )
                
                // Update local audio producer state (keep reference, just paused)
                // Note: Unlike Flutter/React which sets to null, we keep the producer
                // so it can be resumed later
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportAudioException(
                        "Error pausing local audio producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(
            DisconnectSendTransportAudioException(
                "Error disconnecting local audio transport: ${e.message}",
                e
            )
        )
    }
}

/**
 * Disconnects the send transport for audio by pausing the audio producer
 * and notifying the server.
 *
 * This function handles both remote and local audio transport disconnection.
 * It manages producer lifecycle, updates UI state, and ensures proper cleanup
 * of audio resources.
 *
 * Workflow:
 * 1. **Local Transport Disconnection**:
 *    - Disconnects local audio transport if available
 *    - Pauses local audio producer
 * 2. **Remote Transport Disconnection**:
 *    - Pauses remote audio producer if it exists
 *    - Notifies server about paused producer
 * 3. **UI State Update**:
 *    - Updates main window if needed based on screen state
 *    - Repopulates user media display
 * 4. **State Cleanup**:
 *    - Clears audio producer references
 *    - Updates all relevant state parameters
 *
 * @param options Configuration options for disconnecting audio transport
 * @return Result indicating success or failure
 *
 * Example usage:
 * ```kotlin
 * val options = DisconnectSendTransportAudioOptions(
 *     parameters = myParameters
 * )
 * 
 * disconnectSendTransportAudio(options).onSuccess {
 *     Logger.e("DisconnectSendTransp", "Audio transport disconnected successfully")
 * }.onFailure { error ->
 *     Logger.e("DisconnectSendTransp", "Disconnection failed: ${error.message}")
 * }
 * ```
 */
suspend fun disconnectSendTransportAudio(
    options: DisconnectSendTransportAudioOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        
        // Get state
        val audioProducer = parameters.audioProducer
        val socket = parameters.socket
        val localSocket = parameters.localSocket
        val videoAlreadyOn = parameters.videoAlreadyOn
        val islevel = parameters.islevel
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared
        val roomName = parameters.roomName
        val hostLabel = parameters.hostLabel
        
        // Disconnect local audio transport if available
        if (localSocket != null && localSocket.isConnected()) {
            disconnectLocalSendTransportAudio(options)
        }
        
        // Pause the remote audio producer if it exists
        if (audioProducer != null) {
            try {
                // Pause the producer to stop sending audio
                audioProducer.pause()
                
                // Notify server about paused producer
                socket?.emit(
                    "pauseProducerMedia",
                    mapOf(
                        "mediaTag" to "audio",
                        "roomName" to roomName
                    )
                )
                
                // Note: Keep the producer reference so it can be resumed later
                // The producer is paused, not closed
            } catch (e: Exception) {
                return Result.failure(
                    DisconnectSendTransportAudioException(
                        "Error pausing audio producer: ${e.message}",
                        e
                    )
                )
            }
        }
        
        // Update UI if needed
        if (!videoAlreadyOn && islevel == "2") {
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
            DisconnectSendTransportAudioException(
                "Error disconnecting audio send transport: ${e.message}",
                e
            )
        )
    }
}
