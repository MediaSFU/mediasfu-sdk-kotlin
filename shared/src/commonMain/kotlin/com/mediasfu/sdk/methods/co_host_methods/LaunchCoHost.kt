package com.mediasfu.sdk.methods.co_host_methods

/**
 * Defines options for launching the co-host modal, including the function to
 * update visibility and the current modal visibility state.
 */
data class LaunchCoHostOptions(
    val updateIsCoHostModalVisible: (Boolean) -> Unit,
    val isCoHostModalVisible: Boolean
)

/**
 * Type definition for the function that launches the co-host modal.
 */
typealias LaunchCoHostType = (LaunchCoHostOptions) -> Unit

/**
 * Toggles the visibility of the co-host modal.
 * 
 * This function calls `updateIsCoHostModalVisible` with the negated value of
 * `isCoHostModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchCoHostOptions(
 *     updateIsCoHostModalVisible = { isVisible ->
 *         // Update visibility here
 *     },
 *     isCoHostModalVisible = false
 * )
 * 
 * launchCoHost(options)
 * // Toggles the co-host modal to visible.
 * ```
 */
fun launchCoHost(options: LaunchCoHostOptions) {
    options.updateIsCoHostModalVisible(!options.isCoHostModalVisible)
}
