package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Stream

/**
 * Parameters for the closeAndResize function.
 */
interface CloseAndResizeParameters : 
    ReorderStreamsParameters,
    PrepopulateUserMediaParameters,
    RePortParameters {
    val allAudioStreams: List<Stream>
    override val allVideoStreams: List<Stream>
    override val activeNames: List<String>
    override val participants: List<Participant>
    val streamNames: List<Stream>
    override val recordingDisplayType: String
    override val recordingVideoOptimized: Boolean
    override val adminIDStream: String
    override val newLimitedStreams: List<Stream>
    override val newLimitedStreamsIDs: List<String>
    override val oldAllStreams: List<Stream>
    override val shareScreenStarted: Boolean
    override val shared: Boolean
    val meetingDisplayType: String
    val deferReceive: Boolean
    val lockScreen: Boolean
    val firstAll: Boolean
    val firstRound: Boolean
    val gotAllVids: Boolean
    override val eventType: EventType
    val hostLabel: String
    val shareEnded: Boolean
    override val updateMainWindow: Boolean

    // Update functions
    val updateActiveNames: (List<String>) -> Unit
    val updateAllVideoStreams: (List<Stream>) -> Unit
    val updateAllAudioStreams: (List<Stream>) -> Unit
    val updateShareScreenStarted: (Boolean) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit
    val updateNewLimitedStreams: (List<Stream>) -> Unit
    val updateOldAllStreams: (List<Stream>) -> Unit
    val updateDeferReceive: (Boolean) -> Unit
    override val updateMainHeightWidth: (Double) -> Unit
    val updateShareEnded: (Boolean) -> Unit
    val updateLockScreen: (Boolean) -> Unit
    val updateFirstAll: (Boolean) -> Unit
    val updateFirstRound: (Boolean) -> Unit

    // Mediasfu functions
    val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
    val getVideos: suspend (GetVideosOptions) -> Unit
    val rePort: suspend (RePortOptions) -> Unit

    override fun getUpdatedAllParams(): CloseAndResizeParameters
}

/**
 * Options for the closeAndResize function.
 *
 * @property producerId The ID of the producer whose stream is being closed
 * @property kind The type of media ('audio', 'video', or 'screenshare')
 * @property parameters Parameters containing stream and participant state
 */
data class CloseAndResizeOptions(
    val producerId: String,
    val kind: String, // 'audio', 'video', or 'screenshare'
    val parameters: CloseAndResizeParameters
)

/**
 * Manages the closing and resizing of streams within a media session, adapting the layout
 * and updating participant and stream information as needed.
 *
 * ### Operations by Stream Type:
 * - **Audio Stream**: Removes audio stream, updates active names, triggers reordering
 * - **Video Stream**: Removes video from active and limited streams, updates main window
 * - **Screenshare**: Stops screen sharing, restores deferred video, triggers reordering
 *
 * ### Workflow:
 * 1. **Stream Identification**: Finds the stream by producerId
 * 2. **Stream Removal**: Removes from appropriate stream lists
 * 3. **State Updates**: Updates relevant state flags and lists
 * 4. **UI Refresh**: Triggers reordering and main window updates
 *
 * @param options Options containing producer ID, media kind, and parameters
 *
 * Example:
 * ```kotlin
 * val options = CloseAndResizeOptions(
 *     producerId = "producer-123",
 *     kind = "video",
 *     parameters = closeResizeParams
 * )
 *
 * closeAndResize(options)
 * ```
 */
suspend fun closeAndResize(options: CloseAndResizeOptions) {
    val producerId = options.producerId
    val kind = options.kind
    var parameters = options.parameters.getUpdatedAllParams()

    try {
        // Destructure parameters
        var allAudioStreams = parameters.allAudioStreams.toMutableList()
        var allVideoStreams = parameters.allVideoStreams.toMutableList()
        var activeNames = parameters.activeNames.toMutableList()
        val participants = parameters.participants
        var streamNames = parameters.streamNames.toMutableList()
        val recordingDisplayType = parameters.recordingDisplayType
        val recordingVideoOptimized = parameters.recordingVideoOptimized
        val adminIDStream = parameters.adminIDStream
        var newLimitedStreams = parameters.newLimitedStreams.toMutableList()
        val newLimitedStreamsIDs = parameters.newLimitedStreamsIDs
        var oldAllStreams = parameters.oldAllStreams.toMutableList()
        var shareScreenStarted = parameters.shareScreenStarted
        val shared = parameters.shared
        val meetingDisplayType = parameters.meetingDisplayType
        var deferReceive = parameters.deferReceive
        var lockScreen = parameters.lockScreen
        var firstAll = parameters.firstAll
        var firstRound = parameters.firstRound
        val gotAllVids = parameters.gotAllVids
        val eventType = parameters.eventType
        val hostLabel = parameters.hostLabel
        var shareEnded = parameters.shareEnded
        var updateMainWindow = parameters.updateMainWindow

        // Update functions
        val updateActiveNames = parameters.updateActiveNames
        val updateAllVideoStreams = parameters.updateAllVideoStreams
        val updateAllAudioStreams = parameters.updateAllAudioStreams
        val updateShareScreenStarted = parameters.updateShareScreenStarted
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow
        val updateNewLimitedStreams = parameters.updateNewLimitedStreams
        val updateOldAllStreams = parameters.updateOldAllStreams
        val updateDeferReceive = parameters.updateDeferReceive
        val updateMainHeightWidth = parameters.updateMainHeightWidth
        val updateShareEnded = parameters.updateShareEnded
        val updateLockScreen = parameters.updateLockScreen
        val updateFirstAll = parameters.updateFirstAll
        val updateFirstRound = parameters.updateFirstRound

        // Mediasfu functions
        val reorderStreams = parameters.reorderStreams
        val prepopulateUserMedia = parameters.prepopulateUserMedia
        val getVideos = parameters.getVideos
        val rePort = parameters.rePort

        when (kind) {
            "audio" -> {
                // Handle audio stream closure
                val participant = participants.firstOrNull { it.audioID == producerId }
                
                if (participant != null) {
                    // Remove from activeNames
                    activeNames.remove(participant.name)
                    updateActiveNames(activeNames)

                    // Remove from allAudioStreams
                    allAudioStreams.removeAll { it.producerId == producerId }
                    updateAllAudioStreams(allAudioStreams)

                    // Remove from streamNames
                    streamNames.removeAll { it.producerId == producerId }

                    // Trigger reordering if needed
                    if (meetingDisplayType != "video") {
                        val optionsReorder = ReorderStreamsOptions(
                            add = false,
                            screenChanged = true,
                            parameters = parameters
                        )
                        reorderStreams(optionsReorder)
                    }
                }
            }

            "video" -> {
                // Handle video stream closure
                val participant = participants.firstOrNull { it.videoID == producerId }

                if (participant != null) {
                    // Remove from activeNames if no audio
                    if (participant.audioID.isEmpty()) {
                        activeNames.remove(participant.name)
                        updateActiveNames(activeNames)
                    }

                    // Remove from allVideoStreams
                    allVideoStreams.removeAll { it.producerId == producerId }
                    updateAllVideoStreams(allVideoStreams)

                    // Remove from newLimitedStreams
                    newLimitedStreams.removeAll { it.producerId == producerId }
                    updateNewLimitedStreams(newLimitedStreams)

                    // Remove from streamNames
                    streamNames.removeAll { it.producerId == producerId }

                    // Update main window if admin video closed
                    if (producerId == adminIDStream) {
                        updateMainWindow = true
                        updateUpdateMainWindow(true)

                        val optionsPrepopulate = PrepopulateUserMediaOptions(
                            name = hostLabel,
                            parameters = parameters
                        )
                        prepopulateUserMedia(optionsPrepopulate)
                        updateUpdateMainWindow(false)
                    }

                    // Trigger reordering
                    val optionsReorder = ReorderStreamsOptions(
                        add = false,
                        screenChanged = meetingDisplayType == "video",
                        parameters = parameters
                    )
                    reorderStreams(optionsReorder)
                }
            }

            "screenshare" -> {
                // Handle screenshare closure
                shareScreenStarted = false
                updateShareScreenStarted(false)

                shareEnded = true
                updateShareEnded(true)

                lockScreen = false
                updateLockScreen(false)

                firstAll = false
                updateFirstAll(false)

                firstRound = false
                updateFirstRound(false)

                // Restore main height/width
                if (eventType == EventType.CONFERENCE) {
                    updateMainHeightWidth(0.0)
                }

                // Restore deferred receive
                if (deferReceive) {
                    val optionsGet = GetVideosOptions(
                        participants = participants,
                        allVideoStreams = allVideoStreams,
                        oldAllStreams = oldAllStreams,
                        adminVidID = adminIDStream.ifEmpty { null },
                        updateAllVideoStreams = { streams -> updateAllVideoStreams(streams as List<Stream>) },
                        updateOldAllStreams = { streams -> updateOldAllStreams(streams as List<Stream>) }
                    )
                    getVideos(optionsGet)
                    updateDeferReceive(false)
                }

                // Trigger reordering
                val optionsReorder = ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = parameters
                )
                reorderStreams(optionsReorder)

                // Re-port if needed
                if (eventType != EventType.CHAT) {
                    val optionsRePort = RePortOptions(
                        restart = true,
                        parameters = parameters
                    )
                    rePort(optionsRePort)
                }
            }
        }

    } catch (error: Exception) {
        Logger.e("CloseAndResize", "MediaSFU - closeAndResize error: ${error.message}")
    }
}

