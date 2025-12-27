package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcTransport

typealias StreamSuccessVideoType = suspend (StreamSuccessVideoOptions) -> Unit

/**
 * Parameters for stream success video handling.
 */
interface StreamSuccessVideoParameters :
    CreateSendTransportParameters,
    ConnectSendTransportVideoParameters,
    ResumeSendTransportVideoParameters,
    PrepopulateUserMediaParameters {
    override val socket: SocketManager?
    override val localSocket: SocketManager?
    override val participants: List<Participant>
    override val localStream: MediaStream?
    override var transportCreated: Boolean
    val transportCreatedVideo: Boolean
    override val videoAlreadyOn: Boolean
    val videoAction: Boolean
    val videoParams: ProducerOptionsType?
    override val localStreamVideo: MediaStream?
    val defVideoID: String
    val userDefaultVideoInputDevice: String
    val params: ProducerOptionsType?
    val vParams: ProducerOptionsType?
    override val hostLabel: String
    override val islevel: String
    override val member: String
    override var producerTransport: WebRtcTransport?
    override var localProducerTransport: WebRtcTransport?
    override val updateMainWindow: Boolean
    override val lockScreen: Boolean
    override val shared: Boolean
    override val audioAlreadyOn: Boolean
    val showAlert: ShowAlert?
    
    // Camera permission allowed flag - set to true when camera is successfully started
    val allowed: Boolean

    val updateParticipants: (List<Participant>) -> Unit
    override val updateTransportCreated: (Boolean) -> Unit
    val updateTransportCreatedVideo: (Boolean) -> Unit
    val updateVideoAlreadyOn: (Boolean) -> Unit
    val updateVideoAction: (Boolean) -> Unit
    val updateVideoParams: (ProducerOptionsType?) -> Unit
    override val updateLocalStream: (MediaStream?) -> Unit
    override val updateLocalStreamVideo: (MediaStream?) -> Unit
    val updateDefVideoID: (String) -> Unit
    val updateUserDefaultVideoInputDevice: (String) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit
    
    // Update allowed flag when camera is successfully started
    val updateAllowed: (Boolean) -> Unit

    override val updateProducerTransport: (WebRtcTransport?) -> Unit
    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?

    override val createSendTransport: suspend (CreateSendTransportOptions) -> Unit
    override val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
    override val resumeSendTransportVideo: suspend (ResumeSendTransportVideoOptions) -> Unit
    
    // ReorderStreams function for UI update after video is set up (simplified signature)
    val reorderStreams: suspend (add: Boolean, screenChanged: Boolean) -> Unit
    
    // Virtual background auto-apply support
    // When keepBackground is true and selectedBackground is set, auto-apply when camera turns on
    // Note: keepBackground is already defined in PrepopulateUserMediaParameters (parent interface)
    val selectedBackground: VirtualBackground?
        get() = null
    
    /**
     * Callback to automatically apply a saved background when camera turns on.
     * This is called when keepBackground=true and selectedBackground is set.
     * Matches React behavior: when user has a saved background and turns on camera,
     * the background is automatically applied to the new stream.
     */
    val onAutoApplyBackground: (suspend (VirtualBackground, MediaStream) -> Unit)?
        get() = null

    override fun getUpdatedAllParams(): StreamSuccessVideoParameters
}

/**
 * Options for stream success video.
 *
 * @property stream The video media stream
 * @property parameters Parameters for video stream handling
 * @property videoConstraints Optional video constraints
 */
data class StreamSuccessVideoOptions(
    val stream: MediaStream?,
    val parameters: StreamSuccessVideoParameters,
    val videoConstraints: Map<String, Any>? = null
)

/**
 * Manages the setup and successful transition of video streaming.
 *
 * This function handles the configuration of video transports, updates video settings,
 * and manages UI state when video streaming is successfully established.
 *
 * ### Key Responsibilities:
 * - Sets up or switches to a new local video stream
 * - Manages default video device configuration
 * - Creates or connects to video transport if required
 * - Updates participant video states
 * - Manages UI components based on user roles
 *
 * ### Workflow:
 * 1. **Stream Setup**: Configures the local video stream
 * 2. **Device Management**: Updates default video input device
 * 3. **Transport Handling**: Creates/connects transport as needed
 * 4. **State Updates**: Updates video and participant states
 * 5. **UI Refresh**: Triggers UI updates if needed
 *
 * @param options Options containing the video stream and parameters
 *
 * Example:
 * ```kotlin
 * val options = StreamSuccessVideoOptions(
 *     stream = videoMediaStream,
 *     parameters = streamSuccessVideoParams
 * )
 *
 * streamSuccessVideo(options)
 * ```
 *
 * ### Note:
 * This is a simplified stub implementation. Full implementation requires:
 * - Platform-specific MediaStream handling
 * - WebRTC video track management
 * - Device enumeration and selection
 * - Video producer creation and management
 */
suspend fun streamSuccessVideo(options: StreamSuccessVideoOptions) {
    val incomingStream = options.stream ?: run {
        Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo: stream missing -> aborting video setup")
        return
    }
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        val participants = parameters.participants
        var transportCreated = parameters.transportCreated
        var transportCreatedVideo = parameters.transportCreatedVideo
        var videoAlreadyOn = parameters.videoAlreadyOn
        var videoAction = parameters.videoAction
        var videoParams = parameters.videoParams
        val hostLabel = parameters.hostLabel
        val islevel = parameters.islevel
        val member = parameters.member
        val lockScreen = parameters.lockScreen
        val shared = parameters.shared

        val updateParticipants = parameters.updateParticipants
        val updateTransportCreated = parameters.updateTransportCreated
        val updateTransportCreatedVideo = parameters.updateTransportCreatedVideo
        val updateVideoAlreadyOn = parameters.updateVideoAlreadyOn
        val updateVideoAction = parameters.updateVideoAction
        val updateVideoParams = parameters.updateVideoParams
        val updateLocalStream = parameters.updateLocalStream
        val updateLocalStreamVideo = parameters.updateLocalStreamVideo
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow

        val prepopulateUserMedia = parameters.prepopulateUserMedia

        // Sync local stream references with the incoming stream
        updateLocalStreamVideo(incomingStream)

        val existingLocalStream = parameters.localStream
        val incomingTrack = runCatching { incomingStream.getVideoTracks().firstOrNull() }.getOrNull()
        if (existingLocalStream == null || existingLocalStream === incomingStream) {
            updateLocalStream(incomingStream)
        } else {
            runCatching {
                existingLocalStream.getVideoTracks().forEach { track ->
                    existingLocalStream.removeTrack(track)
                }
                incomingTrack?.let { track ->
                    existingLocalStream.addTrack(track)
                }
            }.onFailure { error ->
                Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo: unable to sync existing local stream -> ${error.message}")
            }
            updateLocalStream(existingLocalStream)
        }

        // Prepare producer options so transports can connect/produce
        val preparedVideoParams = (parameters.params ?: parameters.vParams)?.let { base ->
            base.copy(
                stream = incomingStream,
                track = incomingTrack
            )
        }

        if (preparedVideoParams != null) {
            videoParams = preparedVideoParams
            updateVideoParams(preparedVideoParams)
        } else if (videoParams == null) {
        }

        // Mark camera as allowed (permission granted, camera started successfully)
        // This is required for SwitchVideo to work properly
        parameters.updateAllowed(true)

        if (!videoAlreadyOn) {
            updateVideoAlreadyOn(true)
            videoAlreadyOn = true
        }

        if (!transportCreated) {
            val optionsCreate = CreateSendTransportOptions(
                option = "video",
                parameters = parameters,
                videoConstraints = options.videoConstraints
            )
            val createResult = com.mediasfu.sdk.consumers.createSendTransport(optionsCreate)
            if (createResult.isFailure) {
                val error = createResult.exceptionOrNull()
                Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo: create send transport failed -> ${error?.message}")
                throw error ?: CreateSendTransportException("Failed to create video transport")
            }
            transportCreated = true
            updateTransportCreated(true)
        } else {
            // Transport already exists - close existing video producer and create new one
            // This is critical for camera switching - matching Flutter/React behavior
            val videoProducer = parameters.videoProducer
            if (videoProducer != null) {
                try {
                    videoProducer.close()
                    // Clear the producer reference after closing
                    parameters.updateVideoProducer(null)
                    kotlinx.coroutines.delay(500) // Allow time for cleanup (matching React's 500ms delay)
                } catch (e: Exception) {
                    Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo: Error closing video producer: ${e.message}")
                }
            }
            
            val optionsConnect = ConnectSendTransportVideoOptions(
                targetOption = "all",
                videoParams = videoParams ?: preparedVideoParams,
                parameters = parameters,
                videoConstraints = options.videoConstraints
            )
            val connectResult = com.mediasfu.sdk.consumers.connectSendTransportVideo(optionsConnect)
            if (connectResult.isFailure) {
                val error = connectResult.exceptionOrNull()
                Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo: connect send transport failed -> ${error?.message}")
                throw error ?: ConnectSendTransportVideoException("Failed to connect video transport")
            }
            transportCreatedVideo = true
            updateTransportCreatedVideo(true)
        }

        if (videoAction) {
            videoAction = false
            updateVideoAction(false)
        }

        val updatedParticipants = participants.map { participant ->
            if (participant.name == member) {
                participant.copy(videoOn = true)
            } else {
                participant
            }
        }
        updateParticipants(updatedParticipants)

        if (islevel == "2" && !lockScreen && !shared) {
            updateUpdateMainWindow(true)
            val optionsPrepopulate = PrepopulateUserMediaOptions(
                name = hostLabel,
                parameters = parameters
            )
            prepopulateUserMedia(optionsPrepopulate)
            updateUpdateMainWindow(false)
        }

        transportCreatedVideo = true
        updateTransportCreatedVideo(true)

        // Reupdate the screen display (matching Flutter behavior)
        val reorderStreams = parameters.reorderStreams
        if (lockScreen) {
            parameters.updateVideoAlreadyOn(true)
            reorderStreams(true, true)
        } else {
            parameters.updateVideoAlreadyOn(true)
            reorderStreams(false, true)
        }

        // === AUTO-APPLY SAVED BACKGROUND ===
        // Check if user has a saved background that should be auto-applied
        // This matches React behavior: when keepBackground=true and selectedBackground is set,
        // automatically apply the background to the new stream
        val keepBackground = parameters.keepBackground
        val savedBackground = parameters.selectedBackground
        val onAutoApplyBackground = parameters.onAutoApplyBackground
        
        if (keepBackground && savedBackground != null && 
            savedBackground.type != com.mediasfu.sdk.model.BackgroundType.NONE &&
            onAutoApplyBackground != null) {
            try {
                // Auto-apply the saved background to the new stream
                onAutoApplyBackground(savedBackground, incomingStream)
            } catch (_: Exception) {
                // Don't fail the whole video setup if background fails
            }
        }

    } catch (error: Exception) {
        Logger.e("StreamSuccessVideo", "MediaSFU - streamSuccessVideo error: ${error.message}")
        parameters.showAlert.call(
            message = "Error setting up video stream: ${error.message}",
            type = "danger",
            duration = 3000
        )
    }
}