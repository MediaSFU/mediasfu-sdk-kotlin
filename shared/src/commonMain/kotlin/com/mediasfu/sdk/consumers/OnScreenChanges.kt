package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.EventType

/**
 * Parameters for handling screen changes.
 */

interface OnScreenChangesParameters : ReorderStreamsParameters {
    val eventType: EventType
    val shareScreenStarted: Boolean
    val shared: Boolean
    val addForBasic: Boolean
    val updateMainHeightWidth: (Double) -> Unit
    val updateAddForBasic: (Boolean) -> Unit
    val itemPageLimit: Int
    val updateItemPageLimit: (Int) -> Unit
    val reorderStreams: suspend (ReorderStreamsOptions) -> Unit

    override fun getUpdatedAllParams(): OnScreenChangesParameters
}

/**
 * Options for handling screen changes.
 *
 * @property changed Indicates if a screen change occurred
 * @property parameters The parameters defining display behaviors and update functions
 */
data class OnScreenChangesOptions(
    val changed: Boolean = false,
    val parameters: OnScreenChangesParameters
)

/**
 * Handles screen changes and adjusts the display settings based on event type and screen sharing status.
 *
 * This function updates the layout parameters, such as the main height/width and item page limit,
 * based on the current event type (e.g., broadcast, chat, conference) and the screen sharing status.
 * It also invokes the reordering of streams if a screen change is detected.
 *
 * @param options The options for managing screen changes
 *
 * Example:
 * ```kotlin
 * val parameters = object : OnScreenChangesParameters {
 *     override val eventType = "conference"
 *     override val shareScreenStarted = false
 *     override val shared = false
 *     override val addForBasic = false
 *     override val updateMainHeightWidth = { value ->
 *     }
 *     override val updateAddForBasic = { value ->
 *     }
 *     override val itemPageLimit = 4
 *     override val updateItemPageLimit = { value ->
 *     }
 *     override val reorderStreams = { options ->
 *     }
 *     override fun getUpdatedAllParams() = this
 *     // ... other required implementations
 * }
 *
 * val options = OnScreenChangesOptions(
 *     changed = true,
 *     parameters = parameters
 * )
 *
 * onScreenChanges(options)
 * ```
 */
suspend fun onScreenChanges(options: OnScreenChangesOptions) {
    try {
        val parameters = options.parameters
        

        // Destructure parameters
        var addForBasic = parameters.addForBasic
        val updateMainHeightWidth = parameters.updateMainHeightWidth
        val updateAddForBasic = parameters.updateAddForBasic
        var itemPageLimit = parameters.itemPageLimit
        val updateItemPageLimit = parameters.updateItemPageLimit
        val reorderStreams = parameters.reorderStreams
        val eventType = parameters.eventType

        // Remove element with id 'controlButtons'
        addForBasic = false
        updateAddForBasic(addForBasic)

        when (eventType) {
            EventType.BROADCAST -> {
                addForBasic = true
                updateAddForBasic(addForBasic)
                itemPageLimit = 1
                updateItemPageLimit(itemPageLimit)
                updateMainHeightWidth(100.0)

            }
            EventType.CHAT -> {
                addForBasic = true
                updateAddForBasic(addForBasic)
                itemPageLimit = 2
                updateItemPageLimit(itemPageLimit)
                updateMainHeightWidth(0.0)

            }
            EventType.CONFERENCE -> {
                if (!(parameters.shareScreenStarted || parameters.shared)) {
                    updateMainHeightWidth(0.0)

                } else {
                }
            }
            EventType.WEBINAR -> {
                // For WEBINAR, always show host on main grid (don't set mainHeightWidth to 0)
                // The prepopulateUserMedia will show video/audio/minicard based on host state

            }
            else -> {
                // For other event types without screen share, hide main grid
                if (!(parameters.shareScreenStarted || parameters.shared)) {
                    updateMainHeightWidth(0.0)
                } else {
                }
            }
        }

        // Update the mini cards grid by reordering streams
        val reorderOptions = ReorderStreamsOptions(
            add = false,
            screenChanged = options.changed,
            parameters = parameters
        )
        reorderStreams(reorderOptions)
    } catch (e: Exception) {
        Logger.e("OnScreenChanges", "onScreenChanges error: ${e.message}")
    }
}

