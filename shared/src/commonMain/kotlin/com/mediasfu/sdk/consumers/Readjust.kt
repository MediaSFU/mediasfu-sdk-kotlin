package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.EventType
import kotlin.math.floor

/**
 * Parameters for readjusting the layout.
 */
interface ReadjustParameters : PrepopulateUserMediaParameters {
    override val eventType: EventType
    override val shareScreenStarted: Boolean
    override val shared: Boolean
    override val mainHeightWidth: Double
    val prevMainHeightWidth: Double
    val hostLabel: String
    val firstRound: Boolean
    val lockScreen: Boolean

    // Update function
    override val updateMainHeightWidth: (Double) -> Unit

    // Mediasfu function
    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit

    // Method to get updated parameters
    override fun getUpdatedAllParams(): ReadjustParameters
}

/**
 * Options for the readjust function.
 *
 * @property n The participant count, influencing layout decisions
 * @property state The current layout state (0 for initial layout, others for adjustments)
 * @property parameters The parameters containing layout and event information
 */
data class ReadjustOptions(
    val n: Int,
    val state: Int,
    val parameters: ReadjustParameters
)

/**
 * Adjusts the layout parameters based on the current state, participant count, and event type.
 *
 * This function recalculates layout values to determine the main and secondary display areas
 * for participants in a media application. It considers various factors such as whether screen
 * sharing is active, the type of event (e.g., conference, broadcast, chat), and the number of
 * participants. If the layout changes, it triggers a function to prepopulate user media.
 *
 * @param options The options containing participant count, state, and parameters
 *
 * Example:
 * ```kotlin
 * val readjustParams = object : ReadjustParameters {
 *     override val eventType = EventType.CONFERENCE
 *     override val shareScreenStarted = false
 *     override val shared = false
 *     override val mainHeightWidth = 50.0
 *     override val prevMainHeightWidth = 50.0
 *     override val hostLabel = "HostUser"
 *     override val firstRound = true
 *     override val lockScreen = false
 *     override val updateMainHeightWidth = { width ->
 *     }
 *     override val prepopulateUserMedia = { options ->
 *     }
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * readjust(
 *     options = ReadjustOptions(
 *         n = 5,
 *         state = 1,
 *         parameters = readjustParams
 *     )
 * )
 * ```
 */
@Suppress("TooGenericExceptionCaught")
suspend fun readjust(options: ReadjustOptions) {
    try {
        val parameters = options.parameters.getUpdatedAllParams()

        val eventTypeValue = parameters.eventType.name.lowercase()
        val shareScreenStarted = parameters.shareScreenStarted
        val shared = parameters.shared
        var mainHeightWidth = parameters.mainHeightWidth
        var prevMainHeightWidth = parameters.prevMainHeightWidth
        val hostLabel = parameters.hostLabel
        val firstRound = parameters.firstRound
        val lockScreen = parameters.lockScreen
        val updateMainHeightWidth = parameters.updateMainHeightWidth
        val prepopulateUserMedia = parameters.prepopulateUserMedia

        if (options.state == 0) {
            prevMainHeightWidth = mainHeightWidth
        }

        var val1 = 6
        var val2 = 12 - val1
        var cal1 = floor((val1 / 12.0) * 100.0).toInt()
        var cal2 = 100 - cal1

        when {
            eventTypeValue == "broadcast" -> {
                val1 = 0
                val2 = 12 - val1
                if (options.n == 0) {
                    val1 = 0
                    val2 = 12 - val1
                }
            }
            eventTypeValue == "chat" ||
                (eventTypeValue == "conference" && !(shareScreenStarted || shared)) -> {
                val1 = 12
                val2 = 12 - val1
            }
            shareScreenStarted || shared -> {
                val2 = 10
                val1 = 12 - val2
            }
            else -> {
                val1 = when {
                    options.n == 0 -> 1
                    options.n in 1 until 4 -> 4
                    options.n in 4 until 6 -> 6
                    options.n in 6 until 9 -> 6
                    options.n in 9 until 12 -> 6
                    options.n in 12 until 20 -> 8
                    options.n in 20 until 50 -> 8
                    else -> 10
                }
                val2 = 12 - val1
            }
        }

        if (options.state == 0) {
            mainHeightWidth = val2.toDouble()
        }

        cal1 = floor((val1 / 12.0) * 100.0).toInt()
        cal2 = 100 - cal1

        updateMainHeightWidth(cal2.toDouble())

        if (prevMainHeightWidth != mainHeightWidth) {
            val shouldPrepopulate = (!lockScreen && !shared) || !firstRound
            if (shouldPrepopulate) {
                val prepopulateOptions = PrepopulateUserMediaOptions(
                    name = hostLabel,
                    parameters = parameters
                )
                prepopulateUserMedia(prepopulateOptions)
            }
        }
    } catch (e: Exception) {
        Logger.e("Readjust", "readjust error: ${e.message}")
    }
}

