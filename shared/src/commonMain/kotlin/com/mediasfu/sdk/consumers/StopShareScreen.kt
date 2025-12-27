package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.delay

typealias StopShareScreenType = suspend (StopShareScreenOptions) -> Unit

interface StopShareScreenParameters :
    DisconnectSendTransportScreenParameters,
    ReorderStreamsParameters,
    PrepopulateUserMediaParameters {
    override val shared: Boolean
    override val shareScreenStarted: Boolean
    val shareEnded: Boolean
    override val updateMainWindow: Boolean
    val deferReceive: Boolean
    val hostLabel: String
    val lockScreen: Boolean
    override val forceFullDisplay: Boolean
    val firstAll: Boolean
    val firstRound: Boolean
    override val localStreamScreen: MediaStream?
    override val eventType: EventType
    val prevForceFullDisplay: Boolean
    override val annotateScreenStream: Boolean
    override val participants: List<Participant>
    override val allVideoStreams: List<Stream>
    override val oldAllStreams: List<Stream>
    override val adminVidID: String
    
    /**
     * Callback to stop the screen capture foreground service (Android only).
     */
    val stopScreenCaptureService: (() -> Unit)?

    val updateShared: (Boolean) -> Unit
    val updateShareScreenStarted: (Boolean) -> Unit
    val updateShareEnded: (Boolean) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit
    val updateDeferReceive: (Boolean) -> Unit
    val updateLockScreen: (Boolean) -> Unit
    val updateForceFullDisplay: (Boolean) -> Unit
    val updateFirstAll: (Boolean) -> Unit
    val updateFirstRound: (Boolean) -> Unit
    val updateLocalStreamScreen: (MediaStream?) -> Unit
    override val updateMainHeightWidth: (Double) -> Unit
    val updateAnnotateScreenStream: (Boolean) -> Unit
    val updateIsScreenboardModalVisible: (Boolean) -> Unit
    val updateAllVideoStreams: (List<Stream>) -> Unit
    val updateOldAllStreams: (List<Stream>) -> Unit

    val disconnectSendTransportScreen: suspend (DisconnectSendTransportScreenOptions) -> Unit
    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
    val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    val getVideos: suspend (GetVideosOptions) -> Unit

    override fun getUpdatedAllParams(): StopShareScreenParameters
}

data class StopShareScreenOptions(
    val parameters: StopShareScreenParameters
)

/**
 * Stops the screen sharing process and updates various states and UI elements accordingly.
 *
 * This function is designed to stop the screen sharing session and reset related states. It performs
 * several key actions:
 * 1. Resets screen sharing states (shared, shareScreenStarted, shareEnded) and updates main UI flags
 * 2. Stops the local screen stream and disconnects the transport for screen sharing
 * 3. Manages screen annotation states by toggling the annotation overlay as needed
 * 4. Prepopulates user media and triggers a reordering of video streams if layout changes are necessary
 *
 * @param options The options containing parameters for stopping screen share
 *
 * Example:
 * ```kotlin
 * val parameters = object : StopShareScreenParameters {
 *     override val shared = true
 *     override val shareScreenStarted = true
 *     override val shareEnded = false
 *     override val updateMainWindow = true
 *     override val deferReceive = false
 *     override val hostLabel = "Host"
 *     override val lockScreen = false
 *     override val forceFullDisplay = false
 *     override val firstAll = false
 *     override val firstRound = false
 *     override val localStreamScreen = localStream
 *     override val eventType = "conference"
 *     override val prevForceFullDisplay = false
 *     override val annotateScreenStream = false
 *     // ... other update functions
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * val options = StopShareScreenOptions(parameters = parameters)
 * stopShareScreen(options)
 * ```
 */
suspend fun stopShareScreen(options: StopShareScreenOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        // Destructure necessary properties
        var shared = parameters.shared
        var shareScreenStarted = parameters.shareScreenStarted
        var shareEnded = parameters.shareEnded
        var updateMainWindow = parameters.updateMainWindow
        var deferReceive = parameters.deferReceive
        val hostLabel = parameters.hostLabel
        var lockScreen = parameters.lockScreen
        var forceFullDisplay = parameters.forceFullDisplay
        var firstAll = parameters.firstAll
        var firstRound = parameters.firstRound
        val localStreamScreen = parameters.localStreamScreen
        val eventType = parameters.eventType?.toString() ?: ""
        val prevForceFullDisplay = parameters.prevForceFullDisplay
        var annotateScreenStream = parameters.annotateScreenStream

        // Update functions
        val updateShared = parameters.updateShared
        val updateShareScreenStarted = parameters.updateShareScreenStarted
        val updateShareEnded = parameters.updateShareEnded
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow
        val updateDeferReceive = parameters.updateDeferReceive
        val updateLockScreen = parameters.updateLockScreen
        val updateForceFullDisplay = parameters.updateForceFullDisplay
        val updateFirstAll = parameters.updateFirstAll
        val updateFirstRound = parameters.updateFirstRound
        val updateLocalStreamScreen = parameters.updateLocalStreamScreen
        val updateMainHeightWidth = parameters.updateMainHeightWidth
        val updateAnnotateScreenStream = parameters.updateAnnotateScreenStream
        val updateIsScreenboardModalVisible = parameters.updateIsScreenboardModalVisible

        // Mediasfu functions
        val disconnectSendTransportScreen = parameters.disconnectSendTransportScreen
        val prepopulateUserMedia = parameters.prepopulateUserMedia
        val reorderStreams = parameters.reorderStreams
        val getVideos = parameters.getVideos

        // Begin updating states
        shared = false
        updateShared(shared)
        shareScreenStarted = false
        updateShareScreenStarted(shareScreenStarted)
        shareEnded = true
        updateShareEnded(shareEnded)
        updateMainWindow = true
        updateUpdateMainWindow(updateMainWindow)
        
        // Stop the screen capture foreground service (Android)
        try {
            parameters.stopScreenCaptureService?.invoke()
        } catch (e: Exception) {
            Logger.e("StopShareScreen", "Error stopping screen capture service: ${e.message}")
        }

        // Handle deferReceive
        if (deferReceive) {
            updateDeferReceive(false)
            val optionsGet = GetVideosOptions(
                participants = parameters.participants,
                allVideoStreams = parameters.allVideoStreams,
                oldAllStreams = parameters.oldAllStreams,
                adminVidID = parameters.adminVidID,
                updateAllVideoStreams = parameters.updateAllVideoStreams,
                updateOldAllStreams = parameters.updateOldAllStreams
            )
            getVideos(optionsGet)
        }

        // Stop all tracks in the local screen stream (simplified for KMP)
        if (localStreamScreen != null) {
            try {
                // Platform-specific implementation would stop tracks here
                updateLocalStreamScreen(null)
            } catch (e: Exception) {
                Logger.e("StopShareScreen", "Error stopping localStreamScreen tracks: ${e.message}")
            }
        }

        // Disconnect send transport screen
        try {
            val optionsDisconnect = DisconnectSendTransportScreenOptions(parameters = parameters as DisconnectSendTransportScreenParameters)
            disconnectSendTransportScreen(optionsDisconnect)
        } catch (e: Exception) {
            Logger.e("StopShareScreen", "Error disconnecting send transport screen: ${e.message}")
        }

        // Handle screen annotation
        if (annotateScreenStream) {
            annotateScreenStream = false
            updateAnnotateScreenStream(annotateScreenStream)
            updateIsScreenboardModalVisible(true)
            delay(500)
            updateIsScreenboardModalVisible(false)
        }

        // Update mainHeightWidth if event type is conference
        if (eventType.contains("conference", ignoreCase = true)) {
            updateMainHeightWidth(0.0)
        }

        // Prepopulate user media
        try {
            val optionsPrepopulate = PrepopulateUserMediaOptions(
                name = hostLabel,
                parameters = parameters
            )
            prepopulateUserMedia(optionsPrepopulate)
        } catch (e: Exception) {
            Logger.e("StopShareScreen", "Error in prepopulateUserMedia: ${e.message}")
        }

        // Reorder streams
        try {
            val optionsReorder = ReorderStreamsOptions(
                add = false,
                screenChanged = true,
                parameters = parameters as ReorderStreamsParameters
            )
            reorderStreams(optionsReorder)
        } catch (e: Exception) {
            Logger.e("StopShareScreen", "Error in reorderStreams: ${e.message}")
        }

        // Reset UI states
        lockScreen = false
        updateLockScreen(lockScreen)
        forceFullDisplay = prevForceFullDisplay
        updateForceFullDisplay(forceFullDisplay)
        firstAll = false
        updateFirstAll(firstAll)
        firstRound = false
        updateFirstRound(firstRound)
    } catch (e: Exception) {
        Logger.e("StopShareScreen", "stopShareScreen error: ${e.message}")
    }
}

