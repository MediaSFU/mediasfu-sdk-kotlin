package com.mediasfu.sdk.methods.exit_methods

/**
 * Defines the options for toggling the confirmation exit modal visibility.
 */
data class LaunchConfirmExitOptions(
    val updateIsConfirmExitModalVisible: (Boolean) -> Unit,
    val isConfirmExitModalVisible: Boolean
)

/**
 * Type definition for the function that toggles the confirmation exit modal.
 */
typealias LaunchConfirmExitType = (LaunchConfirmExitOptions) -> Unit

/**
 * Toggles the visibility of the confirmation exit modal.
 * 
 * This function calls `updateIsConfirmExitModalVisible` with the negated value of
 * `isConfirmExitModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchConfirmExitOptions(
 *     updateIsConfirmExitModalVisible = { isVisible ->
 *         // Update visibility state here
 *     },
 *     isConfirmExitModalVisible = false
 * )
 * 
 * launchConfirmExit(options)
 * // This will open the modal if it's currently closed, or close it if it's open.
 * ```
 */
fun launchConfirmExit(options: LaunchConfirmExitOptions) {
    options.updateIsConfirmExitModalVisible(!options.isConfirmExitModalVisible)
}
