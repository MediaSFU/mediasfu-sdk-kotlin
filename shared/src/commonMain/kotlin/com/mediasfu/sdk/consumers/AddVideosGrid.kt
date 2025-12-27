package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.ui.components.display.AudioCardOptions
import com.mediasfu.sdk.ui.components.display.AudioControlAction
import com.mediasfu.sdk.ui.components.display.CardVideoDisplayOptions
import com.mediasfu.sdk.ui.components.display.DefaultAudioCard
import com.mediasfu.sdk.ui.components.display.DefaultCardVideoDisplay
import com.mediasfu.sdk.ui.components.display.DefaultMiniCard
import com.mediasfu.sdk.ui.components.display.MiniCardOptions
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Context passed to custom video card builders. */
data class VideoCardRenderInput(
    val stream: Stream,
    val participant: Participant,
    val displayName: String,
    val eventType: EventType,
    val forceFullDisplay: Boolean,
    val showControls: Boolean,
    val showInfo: Boolean,
    val doMirror: Boolean,
    val parameters: AddVideosGridParameters
)

typealias CustomVideoCardBuilder = (VideoCardRenderInput) -> MediaSfuUIComponent

/** Context passed to custom audio card builders. */
data class AudioCardRenderInput(
    val stream: Stream,
    val participant: Participant,
    val displayName: String,
    val eventType: EventType,
    val parameters: AddVideosGridParameters
)

typealias CustomAudioCardBuilder = (AudioCardRenderInput) -> MediaSfuUIComponent

/** Context passed to custom mini-card builders. */
data class MiniCardRenderInput(
    val stream: Stream?,
    val participant: Participant,
    val displayName: String,
    val showVideo: Boolean,
    val eventType: EventType,
    val parameters: AddVideosGridParameters
)

typealias CustomMiniCardBuilder = (MiniCardRenderInput) -> MediaSfuUIComponent

typealias ControlMediaAdapter = suspend (participant: Participant, mediaType: String) -> kotlin.Result<Unit>

private val audioControlScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Grid recalculation payload used when syncing with the Compose display layer. */
data class UpdateMiniCardsGridOptions(
    val rows: Int,
    val cols: Int,
    val defal: Boolean,
    val actualRows: Int
)

/** Options for adding participants to the video grid. */
data class AddVideosGridOptions(
    val mainGridStreams: List<Stream>,
    val altGridStreams: List<Stream>,
    val numRows: Int,
    val numCols: Int,
    val actualRows: Int,
    val lastRowCols: Int,
    val removeAltGrid: Boolean,
    val parameters: AddVideosGridParameters
)

/** Parameters required by [addVideosGrid] to keep UI state in sync with the engine. */
interface AddVideosGridParameters {
    val eventType: EventType
    val refParticipants: List<Participant>
    val audioDecibels: List<AudioDecibels>
    val islevel: String
    val videoAlreadyOn: Boolean
    val localStreamVideo: MediaStream?
    val keepBackground: Boolean
    val virtualStream: MediaStream?
    val forceFullDisplay: Boolean
    val member: String
    val componentSizes: ComponentSizes
    val gridSizes: GridSizes
    val paginationDirection: String
    val paginationHeightWidth: Double
    val doPaginate: Boolean
    val otherGridStreams: List<List<MediaSfuUIComponent>>
    val customVideoCardBuilder: CustomVideoCardBuilder?
    val customAudioCardBuilder: CustomAudioCardBuilder?
    val customMiniCardBuilder: CustomMiniCardBuilder?
    val controlMediaAdapter: ControlMediaAdapter?

    fun updateOtherGridStreams(streams: List<List<MediaSfuUIComponent>>)
    fun updateAddAltGrid(add: Boolean)
    fun updateGridRows(rows: Int)
    fun updateGridCols(cols: Int)
    fun updateAltGridRows(rows: Int)
    fun updateAltGridCols(cols: Int)
    fun updateGridSizes(sizes: GridSizes)
    suspend fun updateMiniCardsGrid(options: UpdateMiniCardsGridOptions): Result<Unit>
    fun getUpdatedAllParams(): AddVideosGridParameters
}

private const val DEFAULT_BORDER_COLOR = 0xFF000000.toInt()
private const val TRANSPARENT_COLOR = 0x00000000
private const val CARD_BACKGROUND_COLOR = 0xFF1C2B4A.toInt()
private const val AUDIO_BAR_COLOR = 0xFFE82E2E.toInt()
private const val AUDIO_WAVEFORM_COLOR = 0xFF4CAF50.toInt()
private const val TEXT_COLOR_WHITE = 0xFFFFFFFF.toInt()

/**
 * Adds participants to the video grid layout with 100% parity to the React and Flutter SDKs.
 * 
 * This implementation mirrors the exact behavior of Flutter/React:
 * - Builds components iteratively in a loop
 * - Updates grids at the LAST iteration of each loop
 * - Calls updateMiniCardsGrid TWICE per grid (before and after updateOtherGridStreams)
 * - Handles alt grid removal with the same double-call pattern
 * 
 * @param options Configuration options for the video grid
 */
suspend fun addVideosGrid(options: AddVideosGridOptions) {
    try {
        var parameters = options.parameters.getUpdatedAllParams()
        val eventType = parameters.eventType

        // Create mutable snapshot of the grid (2 grids: main[0] and alt[1])
        val mutableGridSnapshot = parameters.otherGridStreams.toMutableSnapshot()

        // Initialize new component lists (matching Flutter exactly)
        val newComponents = mutableListOf<MutableList<MediaSfuUIComponent>>(
            mutableListOf(), // Main grid [0]
            mutableListOf()  // Alt grid [1]
        )

        // NO deduplication - Flutter/React don't deduplicate here.
        // The streams should already be correct from ChangeVids/DispStreams.
        val numToAdd = options.mainGridStreams.size

        // Pre-clear alt grid if needed (matching React/Flutter pattern)
        if (options.removeAltGrid) {
            parameters.updateAddAltGrid(false)
        }

        // =================================================================
        // MAIN GRID - Build components and update at last iteration
        // =================================================================
        
        // Use the dimensions passed from DispStreams (matching Flutter exactly)
        val effectiveMainRows = options.numRows
        val effectiveMainCols = options.numCols
        val effectiveMainActualRows = options.actualRows
        
        
        if (numToAdd == 0) {
            mutableGridSnapshot[0] = mutableListOf()
            val mainGridOptions = UpdateMiniCardsGridOptions(
                rows = effectiveMainRows,
                cols = effectiveMainCols,
                defal = true,
                actualRows = effectiveMainActualRows
            )
            parameters.updateMiniCardsGrid(mainGridOptions).logIfError("main-empty-1")
            parameters.updateOtherGridStreams(mutableGridSnapshot.toImmutable())
            parameters.updateMiniCardsGrid(mainGridOptions).logIfError("main-empty-2")
        }

        for (i in 0 until numToAdd) {
            val stream = options.mainGridStreams[i]
            
            // Build component for this stream
            val component = parameters.buildComponentForStream(
                stream = stream,
                eventType = eventType,
                isAltGrid = false
            )
            
            // Add to main grid components
            newComponents[0].add(component)

            // Update grids at the LAST iteration (matching Flutter/React exactly)
            if (i == numToAdd - 1) {
                mutableGridSnapshot[0] = newComponents[0]
                
                val mainGridOptions = UpdateMiniCardsGridOptions(
                    rows = effectiveMainRows,
                    cols = effectiveMainCols,
                    defal = true,
                    actualRows = effectiveMainActualRows
                )
                
                
                // CRITICAL: Call updateMiniCardsGrid TWICE (Flutter/React pattern)
                parameters.updateMiniCardsGrid(mainGridOptions).logIfError("main-1")
                parameters.updateOtherGridStreams(mutableGridSnapshot.toImmutable())
                parameters.updateMiniCardsGrid(mainGridOptions).logIfError("main-2")
            }
        }

        // =================================================================
        // ALT GRID - Handle based on removeAltGrid flag
        // =================================================================
        // Use recalculated values for main grid refresh (matching Flutter/React)
        val refreshMainGridOptions = UpdateMiniCardsGridOptions(
            rows = effectiveMainRows,
            cols = effectiveMainCols,
            defal = true,
            actualRows = effectiveMainActualRows
        )

        if (options.removeAltGrid) {
            // Remove alternate grid (matching Flutter/React pattern exactly)
            parameters.updateAddAltGrid(false)
            mutableGridSnapshot[1] = mutableListOf() // Clear the alternate grid

            val emptyAltOptions = UpdateMiniCardsGridOptions(
                rows = 0,
                cols = 0,
                defal = false,
                actualRows = effectiveMainActualRows
            )
            
            parameters.updateMiniCardsGrid(emptyAltOptions).logIfError("alt-clear-1")
            parameters.updateOtherGridStreams(mutableGridSnapshot.toImmutable())
            parameters.updateMiniCardsGrid(refreshMainGridOptions).logIfError("main-after-alt-clear")
            
        } else if (options.altGridStreams.isNotEmpty()) {
            // NO deduplication - Flutter/React don't deduplicate here.
            // Calculate alternate grid dimensions mirroring React layout decisions
            val altCount = options.altGridStreams.size
            val fallbackAltDimensions = calculateRowsAndColumns(
                CalculateRowsAndColumnsOptions(n = altCount)
            ).getOrElse {
                Logger.e("AddVideosGrid", "MediaSFU - addVideosGrid: calculateRowsAndColumns failed for alt grid, using defaults")
                listOf(1, altCount.coerceAtLeast(1))
            }
            val fallbackAltRows = fallbackAltDimensions.getOrElse(0) { 1 }
            val fallbackAltCols = fallbackAltDimensions.getOrElse(1) { altCount.coerceAtLeast(1) }

            val normalizedLastRowCols = options.lastRowCols.coerceAtLeast(0)
            val altRowsForLayout = if (normalizedLastRowCols > 0) 1 else fallbackAltRows.coerceAtLeast(1)
            val altColsForLayout = if (normalizedLastRowCols > 0) normalizedLastRowCols.coerceAtLeast(1) else fallbackAltCols.coerceAtLeast(1)
            val altActualRows = if (effectiveMainActualRows > 0) effectiveMainActualRows else altRowsForLayout.coerceAtLeast(1)

            // Build alt grid components (matching Flutter exactly - no deduplication)
            for (i in options.altGridStreams.indices) {
                val stream = options.altGridStreams[i]
                
                // Build component for this stream
                val component = parameters.buildComponentForStream(
                    stream = stream,
                    eventType = eventType,
                    isAltGrid = true
                )
                
                // Add to alt grid components
                newComponents[1].add(component)

                // Update grids at the LAST iteration (matching Flutter/React exactly)
                if (i == options.altGridStreams.size - 1) {
                    mutableGridSnapshot[1] = newComponents[1]
                    parameters.updateAddAltGrid(newComponents[1].isNotEmpty())
                    
                    val altGridOptions = UpdateMiniCardsGridOptions(
                        rows = altRowsForLayout,
                        cols = altColsForLayout,
                        defal = false,
                        actualRows = altActualRows
                    )
                    
                    parameters.updateMiniCardsGrid(altGridOptions).logIfError("alt-1")
                    parameters.updateOtherGridStreams(mutableGridSnapshot.toImmutable())
                    parameters.updateMiniCardsGrid(refreshMainGridOptions).logIfError("main-after-alt")
                }
            }
        }

        // Get updated parameters (matching Flutter/React)
        parameters = parameters.getUpdatedAllParams()
        
    } catch (error: Throwable) {
        Logger.e("AddVideosGrid", "MediaSFU addVideosGrid error: ${error.message}")
    }
}

/**
 * Builds a UI component for the given stream based on participant type and stream availability.
 * This matches the Flutter/React logic exactly.
 */
private fun AddVideosGridParameters.buildComponentForStream(
    stream: Stream,
    eventType: EventType,
    isAltGrid: Boolean
): MediaSfuUIComponent {
    val resolvedProducerId = stream.producerId.orEmpty()
    val hasPseudoName = resolvedProducerId.isBlank()

    // Handle streams without producerId (pseudo names)
    if (hasPseudoName) {
        val displayName = stream.name.orEmpty().ifBlank { member.ifBlank { "Guest" } }
        val audioId = stream.audioID.orEmpty()

        return if (audioId.isNotBlank()) {
            // Has audio - render audio card
            val participant = findParticipantByAudio(audioId) ?: placeholderParticipant(
                name = displayName,
                audioId = audioId
            )
            renderAudioCard(
                AudioCardRenderInput(
                    stream = stream,
                    participant = participant,
                    displayName = participant.name.ifBlank { displayName },
                    eventType = eventType,
                    parameters = this
                )
            )
        } else {
            // No audio - render mini card
            val participant = findParticipantByName(displayName) ?: placeholderParticipant(displayName)
            renderMiniCard(
                MiniCardRenderInput(
                    stream = stream,
                    participant = participant,
                    displayName = participant.name.ifBlank { displayName },
                    showVideo = false,
                    eventType = eventType,
                    parameters = this
                )
            )
        }
    }

    val normalizedProducerId = resolvedProducerId.lowercase()
    val isSelfProducer = normalizedProducerId == "youyou" || normalizedProducerId == "youyouyou"

    // Handle self producer (the current user)
    if (isSelfProducer) {
        // Flutter/React always show "You" or "You (Host)" for self - never the actual name
        val baseName = if (islevel == "2" && eventType != EventType.CHAT) "You (Host)" else "You"
        
        return if (!videoAlreadyOn) {
            // Video off - render mini card
            val participant = findParticipantByName(member) ?: placeholderParticipant(baseName, videoId = resolvedProducerId)
            renderMiniCard(
                MiniCardRenderInput(
                    stream = stream,
                    participant = participant,
                    displayName = baseName, // Always use "You" or "You (Host)" - matches Flutter exactly
                    showVideo = false,
                    eventType = eventType,
                    parameters = this
                )
            )
        } else {
            // Video on - render video card with virtual/local stream
            val effectiveStream = stream.copy(
                id = "youyouyou",
                producerId = "youyouyou",
                name = "youyouyou",
                stream = if (keepBackground && virtualStream != null) virtualStream else localStreamVideo
            )
            val participant = findParticipantByName(member) ?: placeholderParticipant(baseName, videoId = "youyouyou")
            val selfForceFullDisplay = when {
                isAltGrid -> false
                eventType == EventType.WEBINAR -> false
                else -> forceFullDisplay
            }
            renderVideoCard(
                VideoCardRenderInput(
                    stream = effectiveStream,
                    participant = participant,
                    displayName = baseName, // Always use "You" or "You (Host)" - matches Flutter exactly
                    eventType = eventType,
                    forceFullDisplay = selfForceFullDisplay,
                    showControls = false,
                    showInfo = false,
                    doMirror = true,
                    parameters = this
                )
            )
        }
    }

    // Handle regular remote participant streams
    val participant = findParticipantByVideo(resolvedProducerId)
    val displayName = participant?.name?.ifBlank { resolvedProducerId } ?: stream.name.orEmpty().ifBlank { resolvedProducerId }

    val resolvedParticipant = participant ?: placeholderParticipant(displayName, videoId = resolvedProducerId)
    val showControls = eventType != EventType.CHAT
    val showInfo = !isAltGrid

    return renderVideoCard(
        VideoCardRenderInput(
            stream = stream,
            participant = resolvedParticipant,
            displayName = displayName,
            eventType = eventType,
            forceFullDisplay = forceFullDisplay,
            showControls = showControls,
            showInfo = showInfo,
            doMirror = false,
            parameters = this
        )
    )
}

/**
 * Renders a video card using custom builder or default component.
 */
private fun AddVideosGridParameters.renderVideoCard(input: VideoCardRenderInput): MediaSfuUIComponent {
    val builder = customVideoCardBuilder
    // Debug the stream content
    
    // Create control handlers using the controlMediaAdapter
    val adapter = controlMediaAdapter
    val audioToggle: ((com.mediasfu.sdk.model.Participant) -> Unit)? = if (adapter != null && input.showControls) {
        { participant ->
            audioControlScope.launch {
                val result = adapter(participant, "audio")
                result.onFailure { error ->
                    Logger.e("AddVideosGrid", "MediaSFU - VideoCard audio toggle failed: ${error.message}")
                }
                result.onSuccess {
                }
            }
        }
    } else null

    val videoToggle: ((com.mediasfu.sdk.model.Participant) -> Unit)? = if (adapter != null && input.showControls) {
        { participant ->
            audioControlScope.launch {
                val result = adapter(participant, "video")
                result.onFailure { error ->
                    Logger.e("AddVideosGrid", "MediaSFU - VideoCard video toggle failed: ${error.message}")
                }
                result.onSuccess {
                }
            }
        }
    } else null
    
    return builder?.invoke(input) ?: DefaultCardVideoDisplay(
        CardVideoDisplayOptions(
            videoStream = input.stream.stream,
            remoteProducerId = input.stream.producerId.orEmpty(),
            eventType = input.eventType.name.lowercase(),
            forceFullDisplay = input.forceFullDisplay,
            customStyle = defaultBorderStyle(input.eventType),
            backgroundColor = CARD_BACKGROUND_COLOR,
            doMirror = input.doMirror,
            displayLabel = input.displayName,
            // Controls overlay options
            showControls = input.showControls,
            showInfo = input.showInfo,
            controlsPosition = "topLeft",
            infoPosition = "topRight",
            participant = input.participant,
            onAudioToggle = audioToggle,
            onVideoToggle = videoToggle,
            // Audio waveform (matches Flutter's VideoCard)
            audioDecibels = audioDecibels,
            barColor = 0xFFE82E2E.toInt() // Red waveform bars
        )
    )
}

/**
 * Renders an audio card using custom builder or default component.
 */
private fun AddVideosGridParameters.renderAudioCard(input: AudioCardRenderInput): MediaSfuUIComponent {
    val builder = customAudioCardBuilder
    val adapter = controlMediaAdapter
    val audioToggle: AudioControlAction? = adapter?.let { handler ->
        { participant: Participant ->
            audioControlScope.launch {
                val result = handler(participant, "audio")
                result.onFailure { error ->
                    Logger.e("AddVideosGrid", "MediaSFU - AudioCard audio toggle failed: ${error.message}")
                }
                result.onSuccess {
                }
            }
        }
    }

    val videoToggle: AudioControlAction? = adapter?.let { handler ->
        { participant: Participant ->
            audioControlScope.launch {
                val result = handler(participant, "video")
                result.onFailure { error ->
                    Logger.e("AddVideosGrid", "MediaSFU - AudioCard video toggle failed: ${error.message}")
                }
                result.onSuccess {
                }
            }
        }
    }

    return builder?.invoke(input) ?: DefaultAudioCard(
        AudioCardOptions(
            name = input.displayName,
            barColor = AUDIO_BAR_COLOR,
            textColor = TEXT_COLOR_WHITE,
            customStyle = defaultBorderStyle(input.eventType),
            controlsPosition = "topLeft",
            infoPosition = "topRight",
            participant = input.participant,
            backgroundColor = TRANSPARENT_COLOR,
            audioDecibels = audioDecibels,
            roundedImage = true,
            showControls = input.eventType != EventType.CHAT,
            waveformColor = AUDIO_WAVEFORM_COLOR,
            onToggleAudio = audioToggle,
            onToggleVideo = videoToggle
        )
    )
}

/**
 * Renders a mini card using custom builder or default component.
 */
private fun AddVideosGridParameters.renderMiniCard(input: MiniCardRenderInput): MediaSfuUIComponent {
    val builder = customMiniCardBuilder
    return builder?.invoke(input) ?: DefaultMiniCard(
        MiniCardOptions(
            name = input.displayName,
            showVideo = input.showVideo,
            customStyle = defaultBorderStyle(input.eventType),
            backgroundColor = TRANSPARENT_COLOR,
            participant = input.participant,
            videoStream = input.stream?.stream,
            roundedImage = true
        )
    )
}

/**
 * Finds a participant by video ID.
 */
private fun AddVideosGridParameters.findParticipantByVideo(videoId: String?): Participant? {
    if (videoId.isNullOrBlank()) return null
    return refParticipants.firstOrNull { it.videoID.equals(videoId, ignoreCase = true) }
}

/**
 * Finds a participant by audio ID.
 */
private fun AddVideosGridParameters.findParticipantByAudio(audioId: String?): Participant? {
    if (audioId.isNullOrBlank()) return null
    return refParticipants.firstOrNull { it.audioID.equals(audioId, ignoreCase = true) }
}

/**
 * Finds a participant by name.
 */
private fun AddVideosGridParameters.findParticipantByName(name: String?): Participant? {
    if (name.isNullOrBlank()) return null
    return refParticipants.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/**
 * Creates a placeholder participant when actual participant is not found.
 */
private fun placeholderParticipant(name: String, videoId: String? = null, audioId: String? = null): Participant {
    return Participant(
        id = videoId ?: audioId ?: name,
        name = name,
        videoID = videoId ?: "",
        audioID = audioId ?: ""
    )
}

/**
 * Returns default border style based on event type.
 */
private fun defaultBorderStyle(eventType: EventType): Map<String, Any> {
    val shouldShowBorder = eventType != EventType.BROADCAST
    return mapOf(
        "borderColor" to if (shouldShowBorder) DEFAULT_BORDER_COLOR else TRANSPARENT_COLOR,
        "borderWidth" to if (shouldShowBorder) 2 else 0
    )
}

/**
 * Converts immutable grid to mutable snapshot for modification.
 */
private fun List<List<MediaSfuUIComponent>>.toMutableSnapshot(): MutableList<MutableList<MediaSfuUIComponent>> {
    val primary = this.getOrNull(0)?.toMutableList() ?: mutableListOf()
    val alternate = this.getOrNull(1)?.toMutableList() ?: mutableListOf()
    return mutableListOf(primary, alternate)
}

/**
 * Converts mutable snapshot back to immutable list.
 */
private fun MutableList<MutableList<MediaSfuUIComponent>>.toImmutable(): List<List<MediaSfuUIComponent>> {
    return listOf(
        this.getOrElse(0) { mutableListOf() }.toList(),
        this.getOrElse(1) { mutableListOf() }.toList()
    )
}

/**
 * Logs error if Result is a failure.
 */
private fun Result<Unit>.logIfError(context: String) {
    onFailure { throwable ->
        Logger.e("AddVideosGrid", "MediaSFU addVideosGrid mini-grid update failed ($context): ${throwable.message}")
    }
}
