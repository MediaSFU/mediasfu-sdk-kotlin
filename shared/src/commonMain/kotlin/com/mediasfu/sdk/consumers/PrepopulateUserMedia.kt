package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.ui.components.display.AudioCardOptions
import com.mediasfu.sdk.ui.components.display.CardVideoDisplayOptions
import com.mediasfu.sdk.ui.components.display.DefaultAudioCard
import com.mediasfu.sdk.ui.components.display.DefaultCardVideoDisplay
import com.mediasfu.sdk.ui.components.display.DefaultMiniCard
import com.mediasfu.sdk.ui.components.display.MiniCardOptions
import com.mediasfu.sdk.webrtc.MediaStream

/**
 * Parameters for the prepopulateUserMedia function.
 */
interface PrepopulateUserMediaParameters {
    // Basic properties
    val participants: List<Participant>
    val allVideoStreams: List<Stream>
    val islevel: String
    val member: String
    val shared: Boolean
    val shareScreenStarted: Boolean
    val eventType: EventType
    val screenId: String?
    val forceFullDisplay: Boolean
    val socket: SocketManager?
    val localSocket: SocketManager?
    val updateMainWindow: Boolean
    val mainScreenFilled: Boolean
    val adminOnMainScreen: Boolean
    val mainScreenPerson: String
    val videoAlreadyOn: Boolean
    val audioAlreadyOn: Boolean
    val oldAllStreams: List<Stream>
    val screenForceFullDisplay: Boolean
    val localStreamScreen: MediaStream?
    val remoteScreenStream: List<Stream>
    val localStreamVideo: MediaStream?
    val mainHeightWidth: Double
    val isWideScreen: Boolean
    val localUIMode: Boolean
    val whiteboardStarted: Boolean
    val whiteboardEnded: Boolean
    val virtualStream: Any?
    val keepBackground: Boolean
    val annotateScreenStream: Boolean
    val audioDecibels: List<AudioDecibels>

    // Update functions
    val updateMainScreenPerson: (String) -> Unit
    val updateMainScreenFilled: (Boolean) -> Unit
    val updateAdminOnMainScreen: (Boolean) -> Unit
    val updateMainHeightWidth: (Double) -> Unit
    val updateScreenForceFullDisplay: (Boolean) -> Unit
    val updateUpdateMainWindow: (Boolean) -> Unit
    val updateShowAlert: (ShowAlert?) -> Unit
    val updateMainGridStream: (List<MediaSfuUIComponent>) -> Unit

    fun getUpdatedAllParams(): PrepopulateUserMediaParameters
}

/**
 * Configuration options for the prepopulateUserMedia function.
 *
 * @property name The name of the participant
 * @property parameters The parameters containing participant details and media settings
 */
data class PrepopulateUserMediaOptions(
    val name: String,
    val parameters: PrepopulateUserMediaParameters
)

/**
 * Populates the main media grid state based on the media activity of the participant.
 *
 * This function determines the primary display based on the event type and parameters such as
 * the host, shared screens, or active video streams, updating the main media grid state
 * accordingly.
 *
 * ### Key Responsibilities:
 * - Determines which participant should be displayed on the main screen
 * - Updates main screen state (filled, person, admin status)
 * - Handles screen sharing and whiteboard scenarios
 * - Manages main screen height/width based on event type
 *
 * ### Logic Flow:
 * 1. **Participant Lookup**: Finds the participant by name
 * 2. **Event Type Handling**:
 *    - Conference: Manages admin/host display
 *    - Webinar: Handles host-specific display logic
 *    - Broadcast/Chat: Similar to conference with variations
 * 3. **Screen Sharing**: Adjusts display for shared screens or whiteboard
 * 4. **State Updates**: Updates main screen person, filled status, and dimensions
 * 5. **Component Building**: Builds and emits VideoCard/AudioCard/MiniCard to mainGridStream
 *
 * ### Example Usage:
 * ```kotlin
 * val options = PrepopulateUserMediaOptions(
 *     name = "Host",
 *     parameters = mediaParameters
 * )
 *
 * prepopulateUserMedia(options)
 * ```
 *
 * @param options The options containing participant name and parameters
 */
suspend fun prepopulateUserMedia(options: PrepopulateUserMediaOptions) {
    val name = options.name
    var parameters = options.parameters.getUpdatedAllParams()

    try {
        val participants = parameters.participants
        val allVideoStreams = parameters.allVideoStreams
        val islevel = parameters.islevel.ifBlank { "1" }
        val member = parameters.member
        val shared = parameters.shared
        val shareScreenStarted = parameters.shareScreenStarted
        val eventType = parameters.eventType
        val screenId = parameters.screenId
        var forceFullDisplay = parameters.forceFullDisplay
        var mainScreenFilled = parameters.mainScreenFilled
        var adminOnMainScreen = parameters.adminOnMainScreen
        var mainScreenPerson = parameters.mainScreenPerson
        val videoAlreadyOn = parameters.videoAlreadyOn
        val audioAlreadyOn = parameters.audioAlreadyOn
        val oldAllStreams = parameters.oldAllStreams
        var screenForceFullDisplay = parameters.screenForceFullDisplay
        val localStreamScreen = parameters.localStreamScreen
        val remoteScreenStream = parameters.remoteScreenStream
        val localStreamVideo = parameters.localStreamVideo
        var mainHeightWidth = parameters.mainHeightWidth
        val isWideScreen = parameters.isWideScreen
        val localUIMode = parameters.localUIMode
        val whiteboardStarted = parameters.whiteboardStarted
        val whiteboardEnded = parameters.whiteboardEnded
        val virtualStream = parameters.virtualStream as? MediaStream
        val keepBackground = parameters.keepBackground
        val audioDecibels = parameters.audioDecibels
        
        // Debug log for virtual background state

        val updateMainScreenPerson = parameters.updateMainScreenPerson
        val updateMainScreenFilled = parameters.updateMainScreenFilled
        val updateAdminOnMainScreen = parameters.updateAdminOnMainScreen
        val updateMainHeightWidth = parameters.updateMainHeightWidth
        val updateScreenForceFullDisplay = parameters.updateScreenForceFullDisplay
        val updateUpdateMainWindow = parameters.updateUpdateMainWindow
        val updateMainGridStream = parameters.updateMainGridStream

        // Component colors (matching React/Flutter)
        val cardBackgroundColor = 0xFFD9E3EA.toInt() // rgba(217, 227, 234, 0.99)
        val borderStyle: Map<String, Any> = if (eventType != EventType.BROADCAST) {
            mapOf("borderColor" to 0xFF000000.toInt(), "borderWidth" to 2)
        } else {
            emptyMap()
        }
        val audioBarColor = 0xFFFF0000.toInt() // red
        val textColorWhite = 0xFFFFFFFF.toInt()

        // Helper to build VideoCard component
        fun buildVideoCard(
            videoStream: MediaStream?,
            remoteProducerId: String,
            participant: Participant,
            displayName: String,
            doMirror: Boolean
        ): MediaSfuUIComponent {
            return DefaultCardVideoDisplay(
                CardVideoDisplayOptions(
                    videoStream = videoStream,
                    remoteProducerId = remoteProducerId,
                    eventType = eventType.name.lowercase(),
                    forceFullDisplay = forceFullDisplay,
                    customStyle = borderStyle,
                    backgroundColor = cardBackgroundColor,
                    doMirror = doMirror,
                    displayLabel = displayName
                )
            )
        }

        // Helper to build AudioCard component
        fun buildAudioCard(
            participant: Participant,
            displayName: String
        ): MediaSfuUIComponent {
            return DefaultAudioCard(
                AudioCardOptions(
                    name = displayName,
                    barColor = audioBarColor,
                    textColor = textColorWhite,
                    customStyle = borderStyle,
                    controlsPosition = "topLeft",
                    infoPosition = "topRight",
                    participant = participant,
                    backgroundColor = 0x00000000, // transparent
                    audioDecibels = audioDecibels,
                    roundedImage = true,
                    showControls = islevel != "2",
                    waveformColor = 0xFF4CAF50.toInt()
                )
            )
        }

        // Helper to build MiniCard component
        fun buildMiniCard(
            displayName: String,
            participant: Participant?
        ): MediaSfuUIComponent {
            // Create a placeholder participant if null
            val resolvedParticipant = participant ?: Participant(
                name = displayName,
                islevel = islevel,
                isAdmin = islevel == "2",
                isHost = islevel == "2",
                audioID = "",
                videoID = "",
                videoOn = false,
                audioOn = false
            )
            return DefaultMiniCard(
                MiniCardOptions(
                    name = displayName,
                    showVideo = false,
                    customStyle = borderStyle,
                    backgroundColor = 0x00000000, // transparent
                    participant = resolvedParticipant,
                    videoStream = null,
                    roundedImage = true
                )
            )
        }

        // Track the component to emit
        val newComponent = mutableListOf<MediaSfuUIComponent>()

        if (eventType == EventType.CHAT) {
            if (mainHeightWidth != 0.0) {
                mainHeightWidth = 0.0
                updateMainHeightWidth(0.0)
            }
            updateUpdateMainWindow(false)
            updateMainGridStream(emptyList())
            return
        }

        // For WEBINAR, ensure mainHeightWidth is set so host is shown
        // Default to 67% height (matching React/Flutter defaults)
        if (eventType == EventType.WEBINAR && !(shareScreenStarted || shared)) {
            val desiredHeight = if (isWideScreen) 67.0 else 70.0
            if (mainHeightWidth == 0.0) {
                mainHeightWidth = desiredHeight
                updateMainHeightWidth(desiredHeight)
            }
        }

        fun Participant?.orPlaceholder(
            targetName: String,
            level: String,
            hasVideo: Boolean,
            hasAudio: Boolean
        ): Participant {
            return this ?: Participant(
                name = targetName,
                islevel = level,
                isAdmin = level == "2",
                isHost = level == "2",
                audioID = "",
                videoID = "",
                videoOn = hasVideo,
                audioOn = hasAudio
            )
        }

        var host: Participant? = null
        var hostStream: Stream? = null

        if (shareScreenStarted || shared) {
            if (eventType == EventType.CONFERENCE) {
                val desiredHeight = if (shared || shareScreenStarted) 84.0 else 0.0
                if (mainHeightWidth != desiredHeight) {
                    mainHeightWidth = desiredHeight
                    updateMainHeightWidth(desiredHeight)
                }
            }

            var shouldForceFull = forceFullDisplay
            if (!isWideScreen && (shareScreenStarted || shared)) {
                shouldForceFull = false
            }
            if (screenForceFullDisplay != shouldForceFull) {
                screenForceFullDisplay = shouldForceFull
                updateScreenForceFullDisplay(shouldForceFull)
            }
            forceFullDisplay = shouldForceFull

            if (shared) {
                host = participants.firstOrNull { it.name.equals(member, ignoreCase = true) }
                    .orPlaceholder(member.ifBlank { name }, islevel, videoAlreadyOn, audioAlreadyOn)
                hostStream = Stream(
                    producerId = member.ifBlank { host.name },
                    stream = localStreamScreen
                )
                val hostName = host.name
                if (mainScreenPerson != hostName) {
                    mainScreenPerson = hostName
                    updateMainScreenPerson(hostName)
                }
                val hostIsAdmin = islevel == "2"
                if (adminOnMainScreen != hostIsAdmin) {
                    adminOnMainScreen = hostIsAdmin
                    updateAdminOnMainScreen(hostIsAdmin)
                } else {
                    updateAdminOnMainScreen(adminOnMainScreen)
                }
            } else {
                host = participants.firstOrNull { participant ->
                    val participantScreenId = participant.ScreenID
                    participant.ScreenOn && (screenId == null || participantScreenId == screenId)
                }

                if (whiteboardStarted && !whiteboardEnded) {
                    host = Participant(
                        name = "WhiteboardActive",
                        ScreenID = "WhiteboardActive",
                        ScreenOn = true,
                        islevel = "2",
                        isAdmin = true,
                        isHost = true,
                        audioID = "",
                        videoID = "",
                        videoOn = false,
                        audioOn = false
                    )
                    hostStream = Stream(producerId = "WhiteboardActive")
                } else {
                    if (host == null) {
                        host = participants.firstOrNull { it.ScreenOn }
                    }

                    if (host != null && host.name.isNotBlank() && !host.name.contains("WhiteboardActive", ignoreCase = true)) {
                        hostStream = if (remoteScreenStream.isNotEmpty()) {
                            remoteScreenStream.first()
                        } else {
                            val targetProducer = host.ScreenID
                            allVideoStreams.firstOrNull { stream ->
                                targetProducer != null && stream.producerId == targetProducer
                            } ?: Stream(producerId = targetProducer ?: host.name)
                        }
                    }
                }

                val resolvedAdmin = (host?.islevel ?: "1") == "2"
                if (adminOnMainScreen != resolvedAdmin) {
                    adminOnMainScreen = resolvedAdmin
                    updateAdminOnMainScreen(resolvedAdmin)
                } else {
                    updateAdminOnMainScreen(adminOnMainScreen)
                }

                val resolvedName = host?.name.orEmpty()
                if (mainScreenPerson != resolvedName) {
                    mainScreenPerson = resolvedName
                    updateMainScreenPerson(resolvedName)
                }
            }
        } else {
            if (eventType == EventType.CONFERENCE) {
                if (mainHeightWidth != 0.0) {
                    mainHeightWidth = 0.0
                    updateMainHeightWidth(0.0)
                }
                updateUpdateMainWindow(false)
                updateMainGridStream(emptyList())
                return
            }

            host = participants.firstOrNull { it.islevel == "2" }
            if (host == null) {
                host = participants.firstOrNull { it.name.equals(member, ignoreCase = true) }
            }
            host = host.orPlaceholder(member.ifBlank { name }, islevel, videoAlreadyOn, audioAlreadyOn)

            val hostName = host.name
            if (mainScreenPerson != hostName) {
                mainScreenPerson = hostName
                updateMainScreenPerson(hostName)
            }
        }

        if (whiteboardStarted && !whiteboardEnded) {
            val desiredHeight = if (mainHeightWidth == 0.0) {
                if (isWideScreen) 67.0 else 70.0
            } else {
                mainHeightWidth
            }
            if (mainHeightWidth != desiredHeight) {
                mainHeightWidth = desiredHeight
                updateMainHeightWidth(desiredHeight)
            }
        }

        if (shareScreenStarted || shared) {
            if (!screenForceFullDisplay && remoteScreenStream.isNotEmpty()) {
                screenForceFullDisplay = true
                updateScreenForceFullDisplay(true)
            }
        } else if (screenForceFullDisplay) {
            screenForceFullDisplay = false
            updateScreenForceFullDisplay(false)
        }

        if (host != null) {
            val hostLevel = host.islevel ?: "1"
            val hostVideoOn = host.videoOn
            val isLocalAdmin = islevel == "2"

            if ((shareScreenStarted || shared) && hostStream != null) {
                // Screen share is active - show screen share video
                if (!(whiteboardStarted && !whiteboardEnded)) {
                    val videoCard = buildVideoCard(
                        videoStream = if (shared) hostStream.stream else hostStream.stream,
                        remoteProducerId = host.ScreenID ?: host.name,
                        participant = host,
                        displayName = host.name,
                        doMirror = false
                    )
                    newComponent.add(videoCard)
                    updateMainGridStream(newComponent.toList())
                }

                if (!mainScreenFilled) {
                    mainScreenFilled = true
                    updateMainScreenFilled(true)
                } else {
                    updateMainScreenFilled(true)
                }
                val resolvedName = host.name
                if (mainScreenPerson != resolvedName) {
                    mainScreenPerson = resolvedName
                    updateMainScreenPerson(resolvedName)
                }
                val hostIsAdmin = (hostLevel == "2") || resolvedName.equals(member, ignoreCase = true)
                if (adminOnMainScreen != hostIsAdmin) {
                    adminOnMainScreen = hostIsAdmin
                    updateAdminOnMainScreen(hostIsAdmin)
                } else {
                    updateAdminOnMainScreen(adminOnMainScreen)
                }
                
                // Return after screen share handling
                updateUpdateMainWindow(false)
                return
            } else if ((!isLocalAdmin && !hostVideoOn) || (isLocalAdmin && (!hostVideoOn || !videoAlreadyOn)) || localUIMode) {
                // Video is off
                if (isLocalAdmin && videoAlreadyOn && localStreamVideo != null) {
                    // Admin's video is on - show their video
                    val videoStream = if (keepBackground && virtualStream != null) virtualStream else localStreamVideo
                    val videoCard = buildVideoCard(
                        videoStream = videoStream,
                        remoteProducerId = host.videoID.ifBlank { host.name },
                        participant = host,
                        displayName = host.name,
                        doMirror = true
                    )
                    newComponent.add(videoCard)
                    updateMainGridStream(newComponent.toList())

                    if (!mainScreenFilled) {
                        mainScreenFilled = true
                    }
                    updateMainScreenFilled(true)
                    if (!adminOnMainScreen) {
                        adminOnMainScreen = true
                    }
                    updateAdminOnMainScreen(true)
                    if (mainScreenPerson != host.name) {
                        mainScreenPerson = host.name
                        updateMainScreenPerson(host.name)
                    }
                } else {
                    // Video is off - check audio
                    val audioOn = when {
                        isLocalAdmin && audioAlreadyOn -> true
                        host.name.isNotBlank() && !isLocalAdmin -> !host.muted
                        else -> false
                    }

                    if (audioOn) {
                        // Audio is on - show audio card
                        val audioCard = buildAudioCard(
                            participant = host,
                            displayName = host.name
                        )
                        newComponent.add(audioCard)
                        updateMainGridStream(newComponent.toList())

                        if (!mainScreenFilled) {
                            mainScreenFilled = true
                        }
                        updateMainScreenFilled(true)
                        val resolvedAdmin = isLocalAdmin
                        if (adminOnMainScreen != resolvedAdmin) {
                            adminOnMainScreen = resolvedAdmin
                        }
                        updateAdminOnMainScreen(adminOnMainScreen)
                    } else {
                        // Audio is off - show mini card
                        val miniCard = buildMiniCard(
                            displayName = name,
                            participant = host
                        )
                        newComponent.add(miniCard)
                        updateMainGridStream(newComponent.toList())

                        if (mainScreenFilled) {
                            mainScreenFilled = false
                        }
                        updateMainScreenFilled(mainScreenFilled)
                        val resolvedAdmin = isLocalAdmin
                        if (adminOnMainScreen != resolvedAdmin) {
                            adminOnMainScreen = resolvedAdmin
                        }
                        updateAdminOnMainScreen(adminOnMainScreen)
                    }

                    if (mainScreenPerson != host.name) {
                        mainScreenPerson = host.name
                        updateMainScreenPerson(host.name)
                    }
                }
            } else {
                // Video is on
                if (shareScreenStarted || shared) {
                    // Screen share - handled above, but fallback
                    if (!(whiteboardStarted && !whiteboardEnded) && hostStream != null) {
                        val videoCard = buildVideoCard(
                            videoStream = hostStream.stream,
                            remoteProducerId = host.ScreenID ?: host.name,
                            participant = host,
                            displayName = host.name,
                            doMirror = false
                        )
                        newComponent.add(videoCard)
                        updateMainGridStream(newComponent.toList())

                        mainScreenFilled = true
                        updateMainScreenFilled(true)
                        adminOnMainScreen = host.islevel == "2"
                        updateAdminOnMainScreen(adminOnMainScreen)
                        mainScreenPerson = host.name
                        updateMainScreenPerson(mainScreenPerson)
                    }
                } else {
                    // Regular video - find the stream
                    var resolvedStream = hostStream
                    if (resolvedStream == null) {
                        resolvedStream = if (hostLevel == "2") {
                            when {
                                keepBackground && virtualStream != null -> Stream(
                                    producerId = "virtual",
                                    stream = virtualStream
                                )
                                localStreamVideo != null -> Stream(
                                    producerId = host.videoID.ifBlank { host.name },
                                    stream = localStreamVideo
                                )
                                else -> null
                            }
                        } else {
                            oldAllStreams.firstOrNull { stream ->
                                stream.producerId == host.videoID && stream.stream != null
                            }?.copy()
                        }
                    }

                    val hasMediaStream = resolvedStream?.stream != null
                    if (hasMediaStream) {
                        val doMirror = member == host.name
                        val videoCard = buildVideoCard(
                            videoStream = resolvedStream!!.stream,
                            remoteProducerId = host.videoID.ifBlank { host.name },
                            participant = host,
                            displayName = host.name,
                            doMirror = doMirror
                        )
                        newComponent.add(videoCard)
                        updateMainGridStream(newComponent.toList())

                        if (!mainScreenFilled) {
                            mainScreenFilled = true
                        }
                        updateMainScreenFilled(true)
                        val resolvedAdmin = hostLevel == "2"
                        if (adminOnMainScreen != resolvedAdmin) {
                            adminOnMainScreen = resolvedAdmin
                        }
                        updateAdminOnMainScreen(adminOnMainScreen)
                    } else {
                        // No stream available - show mini card
                        val miniCard = buildMiniCard(
                            displayName = name,
                            participant = host
                        )
                        newComponent.add(miniCard)
                        updateMainGridStream(newComponent.toList())

                        if (mainScreenFilled) {
                            mainScreenFilled = false
                        }
                        updateMainScreenFilled(false)
                        val resolvedAdmin = hostLevel == "2" || isLocalAdmin
                        if (adminOnMainScreen != resolvedAdmin) {
                            adminOnMainScreen = resolvedAdmin
                        }
                        updateAdminOnMainScreen(adminOnMainScreen)
                    }

                    if (mainScreenPerson != host.name) {
                        mainScreenPerson = host.name
                        updateMainScreenPerson(host.name)
                    }
                }
            }
        } else {
            // Host is null - show mini card
            val miniCard = buildMiniCard(
                displayName = name,
                participant = null
            )
            newComponent.add(miniCard)
            updateMainGridStream(newComponent.toList())

            if (mainScreenFilled) {
                mainScreenFilled = false
            }
            updateMainScreenFilled(false)
            if (adminOnMainScreen) {
                adminOnMainScreen = false
            }
            updateAdminOnMainScreen(false)
            if (mainScreenPerson.isNotEmpty()) {
                mainScreenPerson = ""
                updateMainScreenPerson("")
            }
        }

        updateUpdateMainWindow(false)
    } catch (error: Exception) {
        Logger.e("PrepopulateUserMedia", "MediaSFU - prepopulateUserMedia error: ${error.message}")
    }
}

