package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

/**
 * Parameters required for comparing active names.
 */
interface CompareActiveNamesParameters : TriggerParameters {
    val activeNames: List<String>
    val prevActiveNames: List<String>
    val updatePrevActiveNames: (List<String>) -> Unit

    // Mediasfu functions
    val trigger: suspend (TriggerOptions) -> Unit
    override fun getUpdatedAllParams(): CompareActiveNamesParameters
}

/**
 * Options for the compareActiveNames function.
 *
 * @property restart When true, triggers an action immediately without comparison
 * @property parameters The parameters containing active names and trigger logic
 */
data class CompareActiveNamesOptions(
    val restart: Boolean = false,
    val parameters: CompareActiveNamesParameters
)

/**
 * Compares the current `activeNames` list with the `prevActiveNames` list and triggers an action
 * if there are any differences.
 *
 * This function performs the following steps:
 * 1. If the `restart` flag is true, it triggers the action without comparison
 * 2. If `restart` is false, it compares each name in `activeNames` to check if any name is new
 *    or removed compared to `prevActiveNames`
 * 3. If a change is detected, it calls the `trigger` function with the updated `activeNames`
 * 4. Finally, it updates `prevActiveNames` to reflect the current `activeNames`
 *
 * @param options The options containing restart flag and parameters
 *
 * Example:
 * ```kotlin
 * val options = CompareActiveNamesOptions(
 *     restart = false,
 *     parameters = object : CompareActiveNamesParameters {
 *         override val activeNames = listOf("Alice", "Bob")
 *         override val prevActiveNames = listOf("Alice")
 *         // ... other required implementations
 *     }
 * )
 *
 * compareActiveNames(options)
 * ```
 */
suspend fun compareActiveNames(options: CompareActiveNamesOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    try {
        // Extract parameters
        val activeNames = parameters.activeNames
        val prevActiveNames = parameters.prevActiveNames
        val updatePrevActiveNames = parameters.updatePrevActiveNames
        val trigger = parameters.trigger

        // Restart the comparison if needed
        if (options.restart) {
            val triggerOptions = TriggerOptions(
                refActiveNames = activeNames,
                parameters = parameters
            )
            trigger(triggerOptions)
            return
        }

        // Track changes in activeNames
        val nameChanged = mutableListOf<Boolean>()

        // Compare each name in activeNames
        for (currentName in activeNames) {
            // Check if the name is present in prevActiveNames
            val hasNameChanged = !prevActiveNames.contains(currentName)

            if (hasNameChanged) {
                nameChanged.add(true)
                val triggerOptions = TriggerOptions(
                    refActiveNames = activeNames,
                    parameters = parameters
                )
                trigger(triggerOptions)
                break
            }
        }

        // Count occurrences of true in nameChanged
        val count = nameChanged.count { it }

        if (count < 1) {
            // Check for removed names in prevActiveNames
            for (currentName in prevActiveNames) {
                val hasNameChanged = !activeNames.contains(currentName)

                if (hasNameChanged) {
                    val triggerOptions = TriggerOptions(
                        refActiveNames = activeNames,
                        parameters = parameters
                    )
                    trigger(triggerOptions)
                    break
                }
            }
        }

        // Update prevActiveNames with current activeNames
        updatePrevActiveNames(activeNames.toList())
    } catch (e: Exception) {
        Logger.e("CompareActiveNames", "compareActiveNames error: ${e.message}")
    }
}

