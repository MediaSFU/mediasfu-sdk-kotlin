package com.mediasfu.sdk.methods.waiting_methods

/**
 * Options for launching the waiting modal.
 */
data class LaunchWaitingOptions(
    val updateIsWaitingModalVisible: (Boolean) -> Unit,
    val isWaitingModalVisible: Boolean
)

/**
 * Signature for a function that updates the visibility of the waiting modal.
 */
typealias LaunchWaitingType = (LaunchWaitingOptions) -> Unit

/**
 * Launches the waiting modal and toggles its visibility state.
 * 
 * This function uses `LaunchWaitingOptions` to update the visibility of a waiting modal.
 * It toggles the visibility by calling the `updateIsWaitingModalVisible` function with the
 * opposite of the current `isWaitingModalVisible` state.
 * 
 * ## Parameters:
 * - [options] - An instance of `LaunchWaitingOptions` containing:
 *   - `updateIsWaitingModalVisible`: A function to update the visibility of the waiting modal.
 *   - `isWaitingModalVisible`: A boolean indicating the current visibility state of the modal.
 * 
 * ## Example Usage:
 * 
 * ```kotlin
 * // Define a function to handle the visibility update
 * val updateVisibility: (Boolean) -> Unit = { isVisible ->
 * }
 * 
 * // Initialize LaunchWaitingOptions with the current visibility state and the update function
 * val waitingOptions = LaunchWaitingOptions(
 *     updateIsWaitingModalVisible = updateVisibility,
 *     isWaitingModalVisible = false // Initial state: hidden
 * )
 * 
 * // Call the launchWaiting function to toggle visibility
 * launchWaiting(waitingOptions) // Output: "Waiting modal is now: Visible"
 * ```
 */
fun launchWaiting(options: LaunchWaitingOptions) {
    // Toggle the visibility of the waiting modal
    options.updateIsWaitingModalVisible(!options.isWaitingModalVisible)
}
