package com.mediasfu.sdk.methods.menu_methods

/**
 * Defines options for toggling the visibility of the menu modal.
 */
data class LaunchMenuModalOptions(
    val updateIsMenuModalVisible: (Boolean) -> Unit,
    val isMenuModalVisible: Boolean
)

/**
 * Type definition for the function that toggles the menu modal.
 */
typealias LaunchMenuModalType = (LaunchMenuModalOptions) -> Unit

/**
 * Toggles the visibility of the menu modal.
 * 
 * This function calls `updateIsMenuModalVisible` with the negated value of
 * `isMenuModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchMenuModalOptions(
 *     isMenuModalVisible = false
 * )
 * 
 * launchMenuModal(options)
 * // This will open the modal if it's currently closed, or close it if it's open.
 * ```
 */
fun launchMenuModal(options: LaunchMenuModalOptions) {
    options.updateIsMenuModalVisible(!options.isMenuModalVisible)
}
