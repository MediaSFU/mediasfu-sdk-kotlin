package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Represents the state of a screen/participant.
 */
data class ScreenState(
    val mainScreenPerson: String? = null,
    val mainScreenProducerId: String? = null,
    val mainScreenFilled: Boolean = false,
    val adminOnMainScreen: Boolean = false,
    val mainScreenVidCon: Any? = null,
    val updateMainWindow: Boolean = false,
    val mainHeightWidth: Double = 0.0,
    val isWideScreen: Boolean = false,
    val localStreamVideo: Any? = null
) {
    /**
     * Convert ScreenState to a map for comparison purposes.
     */
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "mainScreenPerson" to mainScreenPerson,
            "mainScreenProducerId" to mainScreenProducerId,
            "mainScreenFilled" to mainScreenFilled,
            "adminOnMainScreen" to adminOnMainScreen,
            "mainScreenVidCon" to mainScreenVidCon,
            "updateMainWindow" to updateMainWindow,
            "mainHeightWidth" to mainHeightWidth,
            "isWideScreen" to isWideScreen,
            "localStreamVideo" to localStreamVideo
        )
    }
}

/**
 * Parameters for comparing screen states.
 */
interface CompareScreenStatesParameters : TriggerParameters {
    val recordingDisplayType: String
    val recordingVideoOptimized: Boolean
    override val screenStates: List<Any> // List<ScreenState> - must match TriggerParameters
    val prevScreenStates: List<ScreenState>
    val activeNames: List<String>

    // Mediasfu functions
    val trigger: suspend (TriggerOptions) -> Unit
    override fun getUpdatedAllParams(): CompareScreenStatesParameters
}

/**
 * Options for comparing screen states.
 *
 * @property restart When true, the function exits without performing comparisons
 * @property parameters The parameters containing screen states and trigger logic
 */
data class CompareScreenStatesOptions(
    val restart: Boolean = false,
    val parameters: CompareScreenStatesParameters
)

/**
 * Compares the current `screenStates` list with the `prevScreenStates` list and triggers actions
 * if there are differences.
 *
 * This is useful for detecting changes in screen states and responding accordingly in a real-time
 * application. The function performs the following steps:
 * 1. If the `restart` flag is true, it skips the comparison and exits early
 * 2. Iterates through each pair of `screenStates` and `prevScreenStates`, comparing key-value pairs
 * 3. If any differences are detected between a current and previous screen state, it triggers an action
 * 4. The trigger action is based on the `recordingDisplayType` and `recordingVideoOptimized` flags
 *
 * @param options The options containing restart flag and parameters
 *
 * Example:
 * ```kotlin
 * val options = CompareScreenStatesOptions(
 *     restart = false,
 *     parameters = object : CompareScreenStatesParameters {
 *         override val recordingDisplayType = "video"
 *         override val recordingVideoOptimized = true
 *         override val screenStates = listOf(
 *             ScreenState(mainScreenPerson = "Alice", mainScreenFilled = true),
 *             ScreenState(mainScreenPerson = "Bob", mainScreenFilled = false)
 *         )
 *         override val prevScreenStates = listOf(
 *             ScreenState(mainScreenPerson = "Alice", mainScreenFilled = false),
 *             ScreenState(mainScreenPerson = "Bob", mainScreenFilled = false)
 *         )
 *         override val activeNames = listOf("Alice", "Bob")
 *         override val trigger = { opts ->
 *         }
 *         override fun getUpdatedAllParams() = this
 *         // ... other required implementations
 *     }
 * )
 *
 * compareScreenStates(options)
 * ```
 */
suspend fun compareScreenStates(options: CompareScreenStatesOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        // Extract parameters
        val recordingDisplayType = parameters.recordingDisplayType
        val recordingVideoOptimized = parameters.recordingVideoOptimized
        val screenStates = parameters.screenStates
        val prevScreenStates = parameters.prevScreenStates
        val activeNames = parameters.activeNames
        val trigger = parameters.trigger

        // Restart the comparison if needed
        if (options.restart) {
            // Perform necessary actions on restart if specified
            return
        }

        // Compare each key-value pair in screenStates objects
        for (i in screenStates.indices) {
            if (i >= prevScreenStates.size) {
                // New screen state added
                val triggerOptions = TriggerOptions(
                    refActiveNames = activeNames,
                    parameters = parameters
                )
                trigger(triggerOptions)
                break
            }

            // Cast to ScreenState for comparison
            val currentScreenState = (screenStates[i] as? ScreenState)?.toMap() ?: continue
            val prevScreenState = prevScreenStates[i].toMap()

            // Check if any value has changed
            val hasChanged = currentScreenState.keys.any { key ->
                currentScreenState[key] != prevScreenState[key]
            }

            // Signal change if any value has changed
            if (hasChanged) {
                // Perform actions or trigger events based on the change
                if (recordingDisplayType == "video" && recordingVideoOptimized) {
                    val triggerOptions = TriggerOptions(
                        refActiveNames = activeNames,
                        parameters = parameters
                    )
                    trigger(triggerOptions)
                    break
                }
                val triggerOptions = TriggerOptions(
                    refActiveNames = activeNames,
                    parameters = parameters
                )
                trigger(triggerOptions)
                break
            }
        }
    } catch (e: Exception) {
        Logger.e("CompareScreenStates", "compareScreenStates error: ${e.message}")
    }
}

