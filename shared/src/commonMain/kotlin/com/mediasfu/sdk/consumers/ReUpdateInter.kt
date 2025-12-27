package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import kotlinx.datetime.Clock

/**
 * Parameters for re-updating interactions and stream layouts.
 */
interface ReUpdateInterParameters : OnScreenChangesParameters, ReorderStreamsParameters {
    val screenPageLimit: Int
    override val itemPageLimit: Int
    val reorderInterval: Int
    val fastReorderInterval: Int
    override val eventType: EventType
    override val participants: List<Participant>
    override val allVideoStreams: List<Stream>
    override val shared: Boolean
    override val shareScreenStarted: Boolean
    override val adminNameStream: String
    override val screenShareNameStream: String
    val updateMainWindow: Boolean
    val sortAudioLoudness: Boolean
    val lastReorderTime: Int
    override val newLimitedStreams: List<Stream>
    override val newLimitedStreamsIDs: List<String>
    val oldSoundIds: List<String>

    // Update functions
    val updateUpdateMainWindow: (Boolean) -> Unit
    val updateSortAudioLoudness: (Boolean) -> Unit
    val updateLastReorderTime: (Int) -> Unit
    val updateNewLimitedStreams: (List<Stream>) -> Unit
    val updateNewLimitedStreamsIDs: (List<String>) -> Unit
    val updateOldSoundIds: (List<String>) -> Unit

    // Mediasfu functions
    val onScreenChanges: suspend (OnScreenChangesOptions) -> Unit
    override val reorderStreams: suspend (ReorderStreamsOptions) -> Unit
    val changeVids: suspend (Any) -> Unit // ChangeVidsOptions

    override fun getUpdatedAllParams(): ReUpdateInterParameters
}

/**
 * Options for re-updating interactions.
 *
 * @property name Name of the participant whose stream might be updated
 * @property add Determines if a stream should be added to the layout
 * @property force Forces the removal of a stream if true
 * @property average Audio loudness average for determining active speakers
 * @property parameters Parameters containing configuration settings
 */
data class ReUpdateInterOptions(
    val name: String,
    val add: Boolean = false,
    val force: Boolean = false,
    val average: Double = 127.0,
    val parameters: ReUpdateInterParameters
)

/**
 * Updates the layout or content of the media streams based on user activity, screen share, or
 * conference settings.
 *
 * This function reorganizes or adds video streams to the screen layout. It dynamically adjusts
 * stream visibility and layout based on screen sharing, conference activity, and audio loudness
 * changes. If a user is actively sharing or speaking loudly, the function may promote their
 * stream to a prominent position based on defined intervals.
 *
 * @param options The options for updating the stream layout
 *
 * Example:
 * ```kotlin
 * val parameters = object : ReUpdateInterParameters {
 *     override val screenPageLimit = 6
 *     override val itemPageLimit = 3
 *     override val reorderInterval = 10000
 *     override val fastReorderInterval = 5000
 *     override val eventType = "conference"
 *     override val participants = listOf(/* participants */)
 *     override val allVideoStreams = listOf(/* streams */)
 *     override val shared = false
 *     override val shareScreenStarted = false
 *     // ... other implementations
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * val options = ReUpdateInterOptions(
 *     name = "participant1",
 *     add = true,
 *     force = false,
 *     average = 150.0,
 *     parameters = parameters
 * )
 *
 * reUpdateInter(options)
 * ```
 */
suspend fun reUpdateInter(options: ReUpdateInterOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    try {
    val eventType = parameters.eventType
        val shared = parameters.shared
        val shareScreenStarted = parameters.shareScreenStarted
        val updateMainWindow = parameters.updateMainWindow
        val sortAudioLoudness = parameters.sortAudioLoudness
        val lastReorderTime = parameters.lastReorderTime
        val reorderInterval = parameters.reorderInterval
        val fastReorderInterval = parameters.fastReorderInterval

        val updateUpdateMainWindow = parameters.updateUpdateMainWindow
        val updateSortAudioLoudness = parameters.updateSortAudioLoudness
        val updateLastReorderTime = parameters.updateLastReorderTime

        val onScreenChanges = parameters.onScreenChanges
        val reorderStreams = parameters.reorderStreams

        // Get current timestamp
        val currentTime = (Clock.System.now().toEpochMilliseconds() / 1000).toInt()

        // Check if we should perform reordering based on time interval
        val timeDiff = currentTime - lastReorderTime
        val shouldReorder = when {
            options.force -> true
            options.add -> timeDiff >= fastReorderInterval / 1000
            else -> timeDiff >= reorderInterval / 1000
        }

        if (!shouldReorder) {
            return
        }

        // Update last reorder time
        updateLastReorderTime(currentTime)

        // Handle different event types
        when (eventType) {
            EventType.BROADCAST -> {
                // Broadcast logic
                if (shared || shareScreenStarted) {
                    updateUpdateMainWindow(true)

                    val onScreenOptions = OnScreenChangesOptions(
                        changed = true,
                        parameters = parameters
                    )
                    onScreenChanges(onScreenOptions)
                }
            }
            EventType.CONFERENCE -> {
                // Conference logic
                if (!shared && !shareScreenStarted) {
                    // Update sorting if audio loudness changed
                    if (options.average > 127.5 && !sortAudioLoudness) {
                        updateSortAudioLoudness(true)
                    }

                    // Reorder streams
                    val reorderOptions = ReorderStreamsOptions(
                        add = options.add,
                        screenChanged = false,
                        parameters = parameters
                    )
                    reorderStreams(reorderOptions)
                }
            }
            else -> {
                // Default logic for other event types
                val reorderOptions = ReorderStreamsOptions(
                    add = options.add,
                    screenChanged = false,
                    parameters = parameters
                )
                reorderStreams(reorderOptions)
            }
        }
    } catch (e: Exception) {
        Logger.e("ReUpdateInter", "reUpdateInter error: ${e.message}")
    }
}

