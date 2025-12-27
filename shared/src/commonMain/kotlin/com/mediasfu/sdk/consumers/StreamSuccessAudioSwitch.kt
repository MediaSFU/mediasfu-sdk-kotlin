package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcProducer

/**
 * Parameters for stream success audio switch handling.
 */
interface StreamSuccessAudioSwitchParameters :
    ConnectSendTransportAudioParameters,
    PrepopulateUserMediaParameters {
    override val localSocket: SocketManager?
    val roomName: String
    val audioPaused: Boolean
    override var localTransportCreated: Boolean

    val updateAudioProducerSwitch: (WebRtcProducer?) -> Unit
    val updateLocalAudioProducerSwitch: ((WebRtcProducer?) -> Unit)?
    val updateLocalStreamSwitch: (MediaStream?) -> Unit
    val updateAudioParamsSwitch: (ProducerOptionsType?) -> Unit
    val updateDefAudioIDSwitch: (String) -> Unit
    val updateUserDefaultAudioInputDeviceSwitch: (String) -> Unit

    override fun getUpdatedAllParams(): StreamSuccessAudioSwitchParameters
}

/**
 * Options for stream success audio switch.
 *
 * @property stream The new audio media stream to switch to
 * @property parameters Parameters for audio switch handling
 * @property audioConstraints Optional audio constraints
 */
data class StreamSuccessAudioSwitchOptions(
    val stream: MediaStream,
    val parameters: StreamSuccessAudioSwitchParameters,
    val audioConstraints: Map<String, Any>? = null
)

/**
 * Manages switching to a new audio stream, updating the audio producer, local streams, and UI state as necessary.
 *
 * This function handles the transition to a new audio stream by performing several key actions:
 * 1. **Audio Device Check**: Checks if the audio device has changed. If so, it closes the current audio producer,
 *    updates the audio device ID, and prepares the new audio stream for transmission.
 * 2. **Local Stream Update**: Updates `localStream` and `localStreamAudio` with the new audio track.
 * 3. **Transport Handling**: Creates a new audio send transport if one does not exist; otherwise, it connects to the
 *    existing transport with the updated audio parameters.
 * 4. **UI and Event Handling**: Updates UI elements based on user level, screen lock status, and if video is already on.
 *
 * ### Workflow:
 * 1. **Extract Audio Track**: Gets the audio track and device ID from the new stream
 * 2. **Device Change Detection**: Compares new device ID with current device ID
 * 3. **Producer Management**: Closes existing producer if device changed
 * 4. **Socket Emission**: Pauses audio on the server side
 * 5. **Stream Updates**: Updates local audio streams
 * 6. **Transport Handling**: Creates or connects transport as needed
 * 7. **Pause Handling**: Pauses producer if audio was paused
 * 8. **UI Refresh**: Updates UI if needed based on user level
 *
 * @param options Options containing the new audio stream and parameters
 *
 * Example:
 * ```kotlin
 * val options = StreamSuccessAudioSwitchOptions(
 *     stream = newAudioStream,
 *     parameters = streamSuccessAudioSwitchParams,
 *     audioConstraints = mapOf("echoCancellation" to true)
 * )
 *
 * streamSuccessAudioSwitch(options)
 * ```
 *
 * ### Note:
 * Matches Flutter's stream_success_audio_switch.dart implementation.
 */
suspend fun streamSuccessAudioSwitch(options: StreamSuccessAudioSwitchOptions) {
    try {
        // Retrieve updated parameters
        val parameters = options.parameters.getUpdatedAllParams()
        val stream = options.stream

        // Extract parameters
        var audioProducer = parameters.audioProducer
        var localAudioProducer = parameters.localAudioProducer
        val socket = parameters.socket
        val localSocket = parameters.localSocket
        val roomName = parameters.roomName
        var localStream = parameters.localStream
        var localStreamAudio = parameters.localStreamAudio
        var audioParams = parameters.audioParams
        val audioPaused = parameters.audioPaused
        val audioAlreadyOn = parameters.audioAlreadyOn
        val transportCreated = parameters.transportCreated
        var defAudioID = parameters.defAudioID
        var userDefaultAudioInputDevice = parameters.userDefaultAudioInputDevice
        val hostLabel = parameters.hostLabel
        var updateMainWindow = parameters.updateMainWindow
        val videoAlreadyOn = parameters.videoAlreadyOn
        val islevel = parameters.islevel
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared

        // Update functions
        val updateAudioProducer = parameters.updateAudioProducerSwitch
        val updateLocalAudioProducer = parameters.updateLocalAudioProducerSwitch
        val updateLocalStream = parameters.updateLocalStreamSwitch
        val updateAudioParams = parameters.updateAudioParamsSwitch
        val updateDefAudioID = parameters.updateDefAudioIDSwitch
        val updateUserDefaultAudioInputDevice = parameters.updateUserDefaultAudioInputDeviceSwitch
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow

        // Mediasfu functions
        val connectSendTransportAudio = parameters.connectSendTransportAudio
        val prepopulateUserMedia = parameters.prepopulateUserMedia

        // Get the new audio track and device ID from the stream
        val newAudioTrack = stream.getAudioTracks().firstOrNull()
        // Get device ID from track settings - may return empty string if not available
        val newDefAudioID: String = newAudioTrack?.id ?: ""

        // Check if the audio device has changed (always process for new stream)
        if (newAudioTrack != null) {

            // Close the current audioProducer (matching Flutter behavior)
            if (audioProducer != null) {
                try {
                    audioProducer.close()
                    updateAudioProducer(null)
                } catch (e: Exception) {
                    Logger.e("StreamSuccessAudioSw", "MediaSFU - Error closing audio producer: ${e.message}")
                }
            }

            // Emit a pauseProducerMedia event to pause the audio media
            socket?.emit("pauseProducerMedia", mapOf(
                "mediaTag" to "audio",
                "roomName" to roomName,
                "force" to true
            ))

            // Handle local socket if available
            try {
                if (localSocket != null && localSocket.isConnected()) {
                    if (localAudioProducer != null) {
                        try {
                            localAudioProducer.close()
                            updateLocalAudioProducer?.invoke(null)
                        } catch (e: Exception) {
                            Logger.e("StreamSuccessAudioSw", "MediaSFU - Error closing local audio producer: ${e.message}")
                        }
                    }
                    localSocket.emit("pauseProducerMedia", mapOf(
                        "mediaTag" to "audio",
                        "roomName" to roomName
                    ))
                }
            } catch (error: Exception) {
                Logger.e("StreamSuccessAudioSw", "MediaSFU - Error in streamSuccessAudioSwitch localSocket pauseProducerMedia: ${error.message}")
            }

            // Update the localStreamAudio with the new audio stream
            localStreamAudio = stream

            // Update localStream with new audio track
            if (localStream == null || localStream.getAudioTracks().isEmpty()) {
                localStream = localStreamAudio
            } else {
                // Remove existing audio tracks and add the new one
                try {
                    for (track in localStream.getAudioTracks()) {
                        localStream.removeTrack(track)
                    }
                    if (localStreamAudio.getAudioTracks().isNotEmpty()) {
                        localStream.addTrack(localStreamAudio.getAudioTracks().first())
                    }
                } catch (e: Exception) {
                    Logger.e("StreamSuccessAudioSw", "MediaSFU - Error updating local stream tracks: ${e.message}")
                    localStream = stream
                }
            }

            // Update localStream
            updateLocalStream(localStream)

            // Get the new default audio device ID from the new audio track
            val audioTracked = localStream.getAudioTracks().firstOrNull()
            defAudioID = audioTracked?.id ?: newDefAudioID
            updateDefAudioID(defAudioID)

            // Update userDefaultAudioInputDevice
            userDefaultAudioInputDevice = defAudioID
            updateUserDefaultAudioInputDevice(userDefaultAudioInputDevice)

            // Update audioParams with the new audio track
            if (audioParams != null) {
                audioParams = audioParams.copy(
                    track = stream.getAudioTracks().firstOrNull(),
                    stream = stream
                )
                updateAudioParams(audioParams)
            }

            // Sleep for 500ms to allow cleanup (matching Flutter behavior)
            kotlinx.coroutines.delay(500)

            // Connect the transport with new audio
            try {
                val optionsConnect = ConnectSendTransportAudioOptions(
                    stream = localStreamAudio,
                    audioConstraints = options.audioConstraints,
                    parameters = parameters
                )
                connectSendTransportAudio(optionsConnect)
            } catch (error: Exception) {
                Logger.e("StreamSuccessAudioSw", "MediaSFU - Error in streamSuccessAudioSwitch connectSendTransportAudio: ${error.message}")
            }

            // If audio is paused and not already on, pause the audioProducer and emit a pauseProducerMedia event
            if (audioPaused && !audioAlreadyOn) {
                // Re-fetch the updated producer after connect
                val updatedParams = parameters.getUpdatedAllParams()
                val updatedAudioProducer = updatedParams.audioProducer
                if (updatedAudioProducer != null) {
                    try {
                        updatedAudioProducer.pause()
                        updateAudioProducer(updatedAudioProducer)
                    } catch (e: Exception) {
                        Logger.e("StreamSuccessAudioSw", "MediaSFU - Error pausing audio producer: ${e.message}")
                    }
                }
                socket?.emit("pauseProducerMedia", mapOf(
                    "mediaTag" to "audio",
                    "roomName" to roomName
                ))

                try {
                    if (localSocket != null && localSocket.isConnected()) {
                        val updatedLocalAudioProducer = updatedParams.localAudioProducer
                        if (updatedLocalAudioProducer != null) {
                            try {
                                updatedLocalAudioProducer.pause()
                                updateLocalAudioProducer?.invoke(updatedLocalAudioProducer)
                            } catch (e: Exception) {
                                Logger.e("StreamSuccessAudioSw", "MediaSFU - Error pausing local audio producer: ${e.message}")
                            }
                        }
                        localSocket.emit("pauseProducerMedia", mapOf(
                            "mediaTag" to "audio",
                            "roomName" to roomName
                        ))
                    }
                } catch (error: Exception) {
                    Logger.e("StreamSuccessAudioSw", "MediaSFU - Error in streamSuccessAudioSwitch localSocket pauseProducerMedia: ${error.message}")
                }
            }
        } else {
        }

        // Update the UI based on the participant's level and screen lock status
        if (!videoAlreadyOn && islevel == "2") {
            if (!lockScreen && !shared) {
                updateMainWindow = true
                updateUpdateMainWindow(updateMainWindow)
                val optionsPrepopulate = PrepopulateUserMediaOptions(
                    name = hostLabel,
                    parameters = parameters as PrepopulateUserMediaParameters
                )
                prepopulateUserMedia(optionsPrepopulate)
                updateMainWindow = false
                updateUpdateMainWindow(updateMainWindow)
            }
        }

    } catch (error: Exception) {
        Logger.e("StreamSuccessAudioSw", "MediaSFU - Error in streamSuccessAudioSwitch: ${error.message}")
    }
}

