package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import kotlinx.datetime.Clock

/**
 * Parameters for re-porting screen states and active names.
 */
interface RePortParameters : TriggerParameters, CompareScreenStatesParameters, CompareActiveNamesParameters {
    val islevel: String
    val mainScreenPerson: String
    val adminOnMainScreen: Boolean
    val mainScreenFilled: Boolean
    val recordStarted: Boolean
    val recordStopped: Boolean
    val recordPaused: Boolean
    val recordResumed: Boolean
    override val screenStates: List<Any> // List<ScreenState>
    override val prevScreenStates: List<ScreenState>

    // Update functions
    val updateScreenStates: (List<ScreenState>) -> Unit
    val updatePrevScreenStates: (List<ScreenState>) -> Unit

    // Mediasfu functions
    val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit
    val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit

    // Method to get updated parameters
    override fun getUpdatedAllParams(): RePortParameters
}

/**
 * Options for the rePort function.
 *
 * @property restart If true, only re-compares active names without updating screen states
 * @property parameters The parameters containing screen state and recording information
 */
data class RePortOptions(
    val restart: Boolean = false,
    val parameters: RePortParameters
)

typealias RePortType = suspend (RePortOptions) -> Unit

/**
 * Re-ports the screen states and active names for the main screen in a conference or event session.
 *
 * This function updates the current and previous screen states, compares active names and screen
 * states, and adds a timestamp. If recording is started or resumed, it performs the re-porting
 * operations. If `restart` is true, it only re-compares active names.
 *
 * @param options The options containing restart flag and parameters
 *
 * Example:
 * ```kotlin
 * val rePortOptions = RePortOptions(
 *     restart = true,
 *     parameters = object : RePortParameters {
 *         override val islevel = "2"
 *         override val mainScreenPerson = "Admin"
 *         override val adminOnMainScreen = true
 *         override val mainScreenFilled = true
 *         override val recordStarted = true
 *         override val recordStopped = false
 *         override val recordPaused = false
 *         override val recordResumed = false
 *         override val screenStates = listOf(/* existing screen states */)
 *         override val prevScreenStates = listOf(/* previous screen states */)
 *         override val updateScreenStates = { newStates ->
 *         }
 *         override val updatePrevScreenStates = { prevStates ->
 *         }
 *         override val compareActiveNames = { options ->
 *         }
 *         override val compareScreenStates = { options ->
 *         }
 *         override fun getUpdatedAllParams() = this
 *         // ... other required implementations
 *     }
 * )
 *
 * rePort(rePortOptions)
 * ```
 */
suspend fun rePort(options: RePortOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    val restart = options.restart

    try {
        val islevel = parameters.islevel
        val mainScreenPerson = parameters.mainScreenPerson
        val adminOnMainScreen = parameters.adminOnMainScreen
        val mainScreenFilled = parameters.mainScreenFilled
        val recordStarted = parameters.recordStarted
        val recordStopped = parameters.recordStopped
        val recordPaused = parameters.recordPaused
        val recordResumed = parameters.recordResumed
        val screenStates = parameters.screenStates
        val prevScreenStates = parameters.prevScreenStates
        val updateScreenStates = parameters.updateScreenStates
        val updatePrevScreenStates = parameters.updatePrevScreenStates
        val compareActiveNames = parameters.compareActiveNames
        val compareScreenStates = parameters.compareScreenStates

        if (restart) {
            val compareOptions = CompareActiveNamesOptions(
                restart = true,
                parameters = parameters
            )
            compareActiveNames(compareOptions)
            return
        }

        if (islevel == "2") {
            // Get current timestamp (in seconds)
            val currentTimestamp = (Clock.System.now().toEpochMilliseconds() / 1000).toInt()

            // Create current screen state
            val currentScreenState = ScreenState(
                mainScreenPerson = mainScreenPerson,
                mainScreenProducerId = "",
                mainScreenFilled = mainScreenFilled,
                adminOnMainScreen = adminOnMainScreen,
                updateMainWindow = false
            )

            val currentScreenStates = listOf(currentScreenState)

            // Update previous screen states
            updatePrevScreenStates(screenStates.mapNotNull { it as? ScreenState })
            // Update current screen states
            updateScreenStates(currentScreenStates)

            // Compare active names
            val compareNamesOptions = CompareActiveNamesOptions(
                restart = false,
                parameters = parameters
            )
            compareActiveNames(compareNamesOptions)

            // Compare screen states if recording is active
            if ((recordStarted && !recordStopped && !recordPaused) || recordResumed) {
                val compareStatesOptions = CompareScreenStatesOptions(
                    restart = false,
                    parameters = parameters
                )
                compareScreenStates(compareStatesOptions)
            }
        }
    } catch (e: Exception) {
        Logger.e("RePort", "rePort error: ${e.message}")
    }
}

