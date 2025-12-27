package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.methods.utils.mini_audio_player.MiniAudioPlayer
import com.mediasfu.sdk.methods.utils.mini_audio_player.MiniAudioPlayerOptions
import com.mediasfu.sdk.methods.utils.mini_audio_player.MiniAudioPlayerParameters
import com.mediasfu.sdk.ui.components.display.DefaultMiniAudio
import com.mediasfu.sdk.ui.components.display.MiniAudioOptions
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.AudioStatsProvider
import com.mediasfu.sdk.webrtc.WebRtcConsumer
import kotlinx.coroutines.delay

/**
 * Parameters for handling the resumption of audio and video streams in a media session.
 *
 * Extends functionalities of ReorderStreamsParameters, PrepopulateUserMediaParameters,
 * and MiniAudioPlayerParameters to provide comprehensive controls for managing audio and video streams.
 */
interface ConsumerResumeParameters :
    PrepopulateUserMediaParameters {
    val nStream: MediaStream?
    val allAudioStreams: List<Stream>
    override val allVideoStreams: List<Stream>
    val streamNames: List<Stream>
    val audStreamNames: List<Stream>
    override val updateMainWindow: Boolean
    override val shared: Boolean
    override val shareScreenStarted: Boolean
    override val screenId: String?
    override val participants: List<Participant>
    override val eventType: EventType
    val meetingDisplayType: String
    override val mainScreenFilled: Boolean
    val firstRound: Boolean
    val lockScreen: Boolean
    override val oldAllStreams: List<Stream>
    val adminVidID: String // Not in parent
    override val mainHeightWidth: Double
    override val member: String
    val audioOnlyStreams: List<Any> // List<Widget> equivalent
    val gotAllVids: Boolean
    val deferReceive: Boolean
    val firstAll: Boolean
    override val remoteScreenStream: List<Stream>
    val hostLabel: String
    override val whiteboardStarted: Boolean
    override val whiteboardEnded: Boolean
    override val islevel: String
    override val forceFullDisplay: Boolean

    // Update functions
    override val updateUpdateMainWindow: (Boolean) -> Unit
    val updateAllAudioStreams: (List<Stream>) -> Unit
    val updateAllVideoStreams: (List<Stream>) -> Unit
    val updateStreamNames: (List<Stream>) -> Unit
    val updateAudStreamNames: (List<Stream>) -> Unit
    val updateNStream: (MediaStream?) -> Unit
    override val updateMainHeightWidth: (Double) -> Unit
    val updateLockScreen: (Boolean) -> Unit
    val updateFirstAll: (Boolean) -> Unit
    val updateRemoteScreenStream: (List<Stream>) -> Unit
    val updateOldAllStreams: (List<Stream>) -> Unit
    val updateAudioOnlyStreams: (List<Any>) -> Unit // List<Widget> equivalent
    val updateShareScreenStarted: (Boolean) -> Unit
    val updateGotAllVids: (Boolean) -> Unit
    val updateScreenId: (String) -> Unit
    val updateDeferReceive: (Boolean) -> Unit

    // Mediasfu functions
    val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit

    fun asReorderStreamsParameters(): ReorderStreamsParameters
    fun asMiniAudioPlayerParameters(): MiniAudioPlayerParameters

    override fun getUpdatedAllParams(): ConsumerResumeParameters
}

// NOTE: Removed ReorderStreamsParameters inheritance due to interface conflict:
// ReorderStreamsParameters declares updateAllVideoStreams as fun but ConsumerResumeParameters needs it as val.
// This prevents compilation in Kotlin. The consumer implementation calls reorderStreams directly instead.

/**
 * Configuration options for the consumerResume function.
 *
 * @property stream The media stream that is being resumed
 * @property consumer The consumer object associated with the media stream
 * @property kind The type of the media stream ('audio' or 'video')
 * @property remoteProducerId The producer ID of the remote media stream
 * @property parameters Parameters for managing state updates and triggering functions
 * @property nsock The socket connection for managing real-time events
 */
data class ConsumerResumeOptions(
    val stream: MediaStream?,
    val consumer: WebRtcConsumer?,
    val kind: String,
    val remoteProducerId: String,
    val parameters: ConsumerResumeParameters,
    val nsock: SocketManager
)

/**
 * Handles the resumption of a media stream (either audio or video) by managing the socket
 * connections, updating the UI, and reordering streams as necessary.
 *
 * This function performs comprehensive handling of media streams by leveraging socket connections,
 * organizing UI components, and performing real-time updates to the video and audio grids.
 *
 * ### Audio Resumption:
 * - Adds the resumed audio to the list of active audio streams
 * - Updates the UI with an audio-only component (MiniAudioPlayer equivalent)
 * - Triggers a reordering of streams if required
 * - Manages screen sharing state detection
 * - Handles host label filtering
 *
 * ### Video Resumption:
 * - Adds the resumed video to the list of active video streams
 * - Manages screen sharing updates (if applicable)
 * - Reorders streams based on the screen sharing state or participant role
 * - Handles admin video stream filtering
 * - Manages lock screen and defer receive states
 *
 * ### Workflow:
 * 1. **Stream Type Detection**: Determines if audio or video
 * 2. **Participant Lookup**: Finds the participant associated with the stream
 * 3. **Screen Share Detection**: Checks for active screen sharing
 * 4. **Stream Management**: Adds stream to appropriate lists
 * 5. **UI Updates**: Triggers UI component updates
 * 6. **Stream Reordering**: Calls reorderStreams as needed
 * 7. **State Updates**: Updates various state flags
 *
 * @param options Options containing the stream details and parameters
 *
 * Example:
 * ```kotlin
 * val options = ConsumerResumeOptions(
 *     stream = myMediaStream,
 *     consumer = myConsumer,
 *     kind = "audio",
 *     remoteProducerId = "producerId123",
 *     parameters = consumerResumeParams,
 *     nsock = socket
 * )
 *
 * consumerResume(options)
 * ```
 *
 * ### Note:
 * This implementation matches the Flutter SDK logic 100%. Platform-specific parts (like MiniAudioPlayer widget)
 * are represented as placeholders that can be implemented per platform.
 */
suspend fun consumerResume(options: ConsumerResumeOptions) {
    try {
        // Destructure options for easy access
    val stream = options.stream
        val kind = options.kind
        val remoteProducerId = options.remoteProducerId
        val parameters = options.parameters
        val nsock = options.nsock
        val consumer = options.consumer

        // Refresh parameters using the latest state
        val updatedParams = parameters.getUpdatedAllParams()

        // Destructure updatedParams
        var allAudioStreams = updatedParams.allAudioStreams.toMutableList()
        var allVideoStreams = updatedParams.allVideoStreams.toMutableList()
        var streamNames = updatedParams.streamNames.toMutableList()
        var audStreamNames = updatedParams.audStreamNames.toMutableList()
        val mainScreenFilled = updatedParams.mainScreenFilled
        var shareScreenStarted = updatedParams.shareScreenStarted
        var screenId = updatedParams.screenId
        val participants = updatedParams.participants
        val eventType = updatedParams.eventType
        val meetingDisplayType = updatedParams.meetingDisplayType
        val lockScreen = updatedParams.lockScreen
        val firstAll = updatedParams.firstAll
        var oldAllStreams = updatedParams.oldAllStreams.toMutableList()
        var adminVidID = updatedParams.adminVidID
        var mainHeightWidth = updatedParams.mainHeightWidth
        val member = updatedParams.member
        var audioOnlyStreams = updatedParams.audioOnlyStreams.toMutableList()
        val gotAllVids = updatedParams.gotAllVids
        val deferReceive = updatedParams.deferReceive
        val firstRound = updatedParams.firstRound
        var remoteScreenStream = updatedParams.remoteScreenStream.toMutableList()
        val hostLabel = updatedParams.hostLabel
        val whiteboardStarted = updatedParams.whiteboardStarted
        val whiteboardEnded = updatedParams.whiteboardEnded

        // Update functions
        val updateUpdateMainWindow = updatedParams.updateUpdateMainWindow
        val updateAllAudioStreams = updatedParams.updateAllAudioStreams
        val updateAllVideoStreams = updatedParams.updateAllVideoStreams
        val updateStreamNames = updatedParams.updateStreamNames
        val updateAudStreamNames = updatedParams.updateAudStreamNames
        val updateNStream = updatedParams.updateNStream
        val updateMainHeightWidth = updatedParams.updateMainHeightWidth
        val updateLockScreen = updatedParams.updateLockScreen
        val updateFirstAll = updatedParams.updateFirstAll
        val updateRemoteScreenStream = updatedParams.updateRemoteScreenStream
        val updateOldAllStreams = updatedParams.updateOldAllStreams
        val updateAudioOnlyStreams = updatedParams.updateAudioOnlyStreams
        val updateShareScreenStarted = updatedParams.updateShareScreenStarted
        val updateGotAllVids = updatedParams.updateGotAllVids
        val updateScreenId = updatedParams.updateScreenId
        val updateDeferReceive = updatedParams.updateDeferReceive

        // Mediasfu functions
        val reorderStreams = updatedParams.reorderStreams
        val prepopulateUserMedia = updatedParams.prepopulateUserMedia

        if (kind == "audio") {
            // ----- Handling Audio Resumption -----

            // Find participant with audioID == remoteProducerId
            val participant = participants.firstOrNull { it.audioID == remoteProducerId }
            val name = participant?.name ?: ""

            // If the participant is the host, no action is needed
            if (name == hostLabel) {
                return
            }

            // Find any participant currently sharing the screen
            val screenParticipantAlt = participants.firstOrNull {
                it.ScreenID != null && it.ScreenOn == true && it.ScreenID!!.isNotEmpty()
            }

            if (screenParticipantAlt != null) {
                screenId = screenParticipantAlt.ScreenID!!
                updateScreenId(screenId)
                if (!shareScreenStarted) {
                    updateShareScreenStarted(true)
                }
            } else if (whiteboardStarted && !whiteboardEnded) {
                // Whiteboard is active; no changes to screen sharing
            } else {
                // No screen sharing; reset screen ID and share screen status
                screenId = ""
                updateScreenId(screenId)
                updateShareScreenStarted(false)
            }

            // Update the main media stream
            updateNStream(stream)

            // TODO: Platform-specific implementation needed
            // Create a MiniAudioPlayer widget equivalent
            // In Flutter, this creates a visual component for audio-only streams
            // For KMP, this would be a platform-specific UI component
            //
            // val nTrack = MiniAudioPlayer(
            //     stream = stream,
            //     consumer = consumer,
            //     remoteProducerId = remoteProducerId,
            //     name = name,
            //     showWaveform = true
            // )
            val safeName = if (name.isNotEmpty()) name else remoteProducerId
            val audioPlayerConsumer = consumer
            val miniAudioParameters = updatedParams.asMiniAudioPlayerParameters()

            var miniAudioComponent: Any? = null
            val componentIndex = audioOnlyStreams.size
            
            if (audioPlayerConsumer != null) {
                val miniAudioProps: Map<String, Any> = mapOf(
                    "customStyle" to mapOf(
                        "backgroundColor" to "#171717"
                    ),
                    "backgroundColor" to 0xFF171717.toInt(),
                    "name" to safeName,
                    "showWaveform" to true,
                    "overlayPosition" to "topRight",
                    "barColor" to "#FFFFFF",
                    "textColor" to "#FFFFFF",
                    "imageSource" to "https://mediasfu.com/images/logo192.png",
                    "roundedImage" to true,
                    "imageStyle" to emptyMap<String, Any>(),
                    "audioDecibels" to miniAudioParameters.audioDecibels
                )

                val componentBuilder: (Map<String, Any>) -> Any = { props ->
                    val componentName = props["name"] as? String ?: safeName
                    val visibleProp = props["visible"] as? Boolean ?: true
                    val showWaveformProp = props["showWaveform"] as? Boolean ?: true
                    val customStyleProp = (props["customStyle"] as? Map<String, Any>) ?: emptyMap()
                    val backgroundColorProp = props["backgroundColor"] as? Int ?: 0xFF171717.toInt()
                    val audioDecibelsProp = (props["audioDecibels"] as? List<*>)
                        ?.filterIsInstance<AudioDecibels>()
                        ?: miniAudioParameters.audioDecibels

                    DefaultMiniAudio(
                        MiniAudioOptions(
                            name = componentName,
                            visible = visibleProp,
                            showWaveform = showWaveformProp,
                            customStyle = customStyleProp,
                            backgroundColor = backgroundColorProp,
                            audioDecibels = audioDecibelsProp
                        )
                    )
                }

                val miniAudioPlayer = MiniAudioPlayer(
                    MiniAudioPlayerOptions(
                        stream = stream,
                        consumer = audioPlayerConsumer,
                        remoteProducerId = remoteProducerId,
                        parameters = miniAudioParameters,
                        audioStatsProvider = audioPlayerConsumer as? AudioStatsProvider,
                        miniAudioComponent = componentBuilder,
                        miniAudioProps = miniAudioProps,
                        onVisibilityChanged = { visible ->
                            val updatedProps = miniAudioProps.toMutableMap().apply {
                                put("visible", visible)
                            }
                            val updatedComponent = componentBuilder(updatedProps)
                            val currentList = updatedParams.audioOnlyStreams.toMutableList()
                            if (componentIndex < currentList.size) {
                                currentList[componentIndex] = updatedComponent
                                updateAudioOnlyStreams(currentList)
                            }
                        }
                    )
                )
                miniAudioPlayer.startAudioAnalysis()

                miniAudioComponent = miniAudioPlayer.renderMiniAudioComponent()
                    ?: componentBuilder(miniAudioProps)
            } else {
                miniAudioComponent = DefaultMiniAudio(
                    MiniAudioOptions(
                        name = safeName,
                        showWaveform = true,
                        customStyle = mapOf("backgroundColor" to "#171717"),
                        backgroundColor = 0xFF171717.toInt()
                    )
                )
            }

            miniAudioComponent?.let {
                audioOnlyStreams.add(it)
                updateAudioOnlyStreams(audioOnlyStreams)
            }

            // Add the new audio stream to allAudioStreams
            allAudioStreams.add(Stream(producerId = remoteProducerId, stream = stream))
            updateAllAudioStreams(allAudioStreams)

            if (name.isNotEmpty()) {
                // Add to audStreamNames
                val newAudioStream = Stream(
                    producerId = remoteProducerId,
                    name = name
                )
                audStreamNames.add(newAudioStream)
                updateAudStreamNames(audStreamNames)

                // If the main screen is not filled and the participant is at level 2, prepopulate user media
                if (!mainScreenFilled && participant?.islevel == "2") {
                    updateUpdateMainWindow(true)
                    
                    val optionsPrepopulate = PrepopulateUserMediaOptions(
                        name = hostLabel,
                        parameters = updatedParams as PrepopulateUserMediaParameters
                    )
                    prepopulateUserMedia(optionsPrepopulate)
                    updateUpdateMainWindow(false)
                }
            } else {
                return
            }

            // Determine display type and update UI accordingly
            val checker: Boolean
            var altChecker = false

            if (meetingDisplayType == "video") {
                checker = participant != null && participant.name.isNotEmpty() && participant.videoID.isNotEmpty()
            } else {
                checker = true
                altChecker = true
            }

            if (checker) {
                if (shareScreenStarted) {
                    if (!altChecker) {
                        // Reorder streams based on updated parameters
                        val optionsReorder = ReorderStreamsOptions(
                            add = true,
                            screenChanged = false,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                        reorderStreams(optionsReorder)

                        // Non-web delay simulation
                        delay(1000)
                        reorderStreams(optionsReorder)
                    }
                } else {
                    if (altChecker && meetingDisplayType != "video") {
                        val optionsReorder = ReorderStreamsOptions(
                            add = false,
                            screenChanged = true,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                        reorderStreams(optionsReorder)
                        
                        // Non-web delay simulation
                        delay(1000)
                        reorderStreams(optionsReorder)
                    }
                }
            }
        } else if (kind == "video") {
            // ----- Handling Video Resumption -----

            // Update the main media stream
            updateNStream(stream)

            // Find any participant currently sharing the screen
            val screenParticipantAlt = participants.firstOrNull {
                it.ScreenID != null && it.ScreenOn == true && it.ScreenID!!.isNotEmpty()
            }

            if (screenParticipantAlt != null) {
                screenId = screenParticipantAlt.ScreenID!!
                updateScreenId(screenId)
                if (!shareScreenStarted) {
                    shareScreenStarted = true
                    updateShareScreenStarted(true)
                }
            } else if (whiteboardStarted && !whiteboardEnded) {
                // Whiteboard is active; no changes to screen sharing
            } else {
                // No screen sharing; reset screen ID and share screen status
                screenId = ""
                updateScreenId(screenId)
                updateShareScreenStarted(false)
            }

            // Check if the resumed video is a screen share
            if (remoteProducerId == screenId) {
                // Manage screen sharing on the main screen
                updateUpdateMainWindow(true)
                val newRemoteScreen = Stream(
                    producerId = remoteProducerId,
                    stream = stream,
                    socket_ = nsock
                )

                remoteScreenStream = mutableListOf(newRemoteScreen)
                updateRemoteScreenStream(remoteScreenStream)

                if (eventType == EventType.CONFERENCE) {
                    if (shareScreenStarted) {
                        if (mainHeightWidth == 0.0) {
                            updateMainHeightWidth(84.0)
                        }
                    } else {
                        if (mainHeightWidth > 0.0) {
                            updateMainHeightWidth(0.0)
                        }
                    }
                }

                if (!lockScreen) {
                    val optionsPrepopulate = PrepopulateUserMediaOptions(
                        name = hostLabel,
                        parameters = updatedParams as PrepopulateUserMediaParameters
                    )
                    prepopulateUserMedia(optionsPrepopulate)
                    
                    val optionsReorder = ReorderStreamsOptions(
                        add = false,
                        screenChanged = true,
                        parameters = updatedParams.asReorderStreamsParameters()
                    )
                    reorderStreams(optionsReorder)

                    // Non-web delay simulation
                    delay(1000)
                    prepopulateUserMedia(optionsPrepopulate)
                    reorderStreams(optionsReorder)
                } else {
                    if (!firstAll) {
                        val optionsPrepopulate = PrepopulateUserMediaOptions(
                            name = hostLabel,
                            parameters = updatedParams as PrepopulateUserMediaParameters
                        )
                        val optionsReorder = ReorderStreamsOptions(
                            add = false,
                            screenChanged = true,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                        prepopulateUserMedia(optionsPrepopulate)
                        reorderStreams(optionsReorder)

                        // Non-web delay simulation
                        delay(1000)
                        prepopulateUserMedia(optionsPrepopulate)
                        reorderStreams(optionsReorder)
                    }
                }

                // Update lock screen and firstAll flags
                updateLockScreen(true)
                updateFirstAll(true)
            } else {
                // Non-screen share video resumed

                // Find the participant associated with the resumed video
                val participant = participants.firstOrNull { it.videoID == remoteProducerId }

                if (participant != null &&
                    participant.name.isNotEmpty() &&
                    participant.name != hostLabel
                ) {
                    // Add the new video stream to allVideoStreams
                    allVideoStreams.add(
                        Stream(
                            producerId = remoteProducerId,
                            stream = stream,
                            socket_ = nsock
                        )
                    )
                    updateAllVideoStreams(allVideoStreams)
                }

                if (participant != null) {
                    val name = participant.name
                    // Add to streamNames
                    val newStreamName = Stream(
                        producerId = remoteProducerId,
                        name = name
                    )
                    streamNames.add(newStreamName)
                    updateStreamNames(streamNames)
                }

                // If not screenshare, filter out admin streams
                if (!shareScreenStarted) {
                    val admin = participants.firstOrNull {
                        (it.isAdmin == true || it.isHost == true) && it.islevel == "2"
                    }

                    if (admin != null && admin.videoID.isNotEmpty()) {
                        val adminVideoID = admin.videoID
                        
                        // Backup oldAllStreams
                        val oldAllStreamsBackup = if (oldAllStreams.isNotEmpty()) {
                            oldAllStreams.toList()
                        } else {
                            emptyList()
                        }

                        // Filter oldAllStreams for adminVidID
                        oldAllStreams = allVideoStreams.filter { it.producerId == adminVideoID }.toMutableList()
                        updateOldAllStreams(oldAllStreams)

                        if (oldAllStreams.isEmpty()) {
                            oldAllStreams = oldAllStreamsBackup.toMutableList()
                            updateOldAllStreams(oldAllStreams)
                        }

                        // Remove adminVidID streams from allVideoStreams
                        allVideoStreams = allVideoStreams.filter { it.producerId != adminVideoID }.toMutableList()
                        updateAllVideoStreams(allVideoStreams)

                        // If the resumed producer is the admin, update main window
                        if (remoteProducerId == adminVideoID) {
                            updateUpdateMainWindow(true)
                        }
                    }

                    // Update gotAllVids flag
                    updateGotAllVids(true)
                } else {
                    // Check if the videoID is either that of the admin or that of the screen participant
                    val admin = participants.firstOrNull { it.islevel == "2" }
                    val screenParticipant = participants.firstOrNull { it.ScreenID == screenId }

                    val adminVideoID = admin?.videoID
                    val screenParticipantVidID = screenParticipant?.videoID

                    if ((adminVideoID != null && adminVideoID.isNotEmpty()) ||
                        (screenParticipantVidID != null && screenParticipantVidID.isNotEmpty())
                    ) {
                        if (adminVideoID == remoteProducerId ||
                            screenParticipantVidID == remoteProducerId
                        ) {
                            val optionsReorder = ReorderStreamsOptions(
                                add = true,
                                screenChanged = false,
                                parameters = updatedParams.asReorderStreamsParameters()
                            )
                            reorderStreams(optionsReorder)
                            return
                        }
                    }
                }

                // Update the UI based on lockScreen and shareScreenStarted flags
                if (lockScreen || shareScreenStarted) {
                    updateDeferReceive(true)
                    if (!firstAll) {
                        val optionsReorder = ReorderStreamsOptions(
                            add = false,
                            screenChanged = true,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                        reorderStreams(optionsReorder)

                        // Non-web delay simulation
                        delay(1000)
                        reorderStreams(optionsReorder)
                    }
                } else {
                    reorderStreams(
                        ReorderStreamsOptions(
                            add = true,
                            screenChanged = false,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                    )

                    // Non-web delay simulation
                    delay(1000)
                    reorderStreams(
                        ReorderStreamsOptions(
                            add = true,
                            screenChanged = true,
                            parameters = updatedParams.asReorderStreamsParameters()
                        )
                    )
                }
            }
        }
    } catch (error: Exception) {
        // Error during consumer resume
    }
}
