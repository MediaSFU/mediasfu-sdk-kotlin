package com.mediasfu.sdk.methods.message_methods

/**
 * Defines options for toggling the visibility of the messages modal.
 */
data class LaunchMessagesOptions(
    val updateIsMessagesModalVisible: (Boolean) -> Unit,
    val isMessagesModalVisible: Boolean
)

/**
 * Type definition for the function that toggles the messages modal.
 */
typealias LaunchMessagesType = (LaunchMessagesOptions) -> Unit

/**
 * Toggles the visibility state of the messages modal.
 * 
 * This function calls `updateIsMessagesModalVisible` with the negated value of
 * `isMessagesModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchMessagesOptions(
 *     isMessagesModalVisible = false
 * )
 * 
 * launchMessages(options)
 * // This will open the messages modal if it's currently closed, or close it if it's open.
 * ```
 */
fun launchMessages(options: LaunchMessagesOptions) {
    options.updateIsMessagesModalVisible(!options.isMessagesModalVisible)
}
