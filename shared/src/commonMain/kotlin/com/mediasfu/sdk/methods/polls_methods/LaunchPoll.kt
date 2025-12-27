package com.mediasfu.sdk.methods.polls_methods

/**
 * Type definition for updating poll modal visibility.
 */
typealias UpdateIsPollModalVisible = (Boolean) -> Unit

/**
 * Defines options for toggling the poll modal visibility.
 */
data class LaunchPollOptions(
    val updateIsPollModalVisible: UpdateIsPollModalVisible,
    val isPollModalVisible: Boolean
)

/**
 * Type definition for the function that toggles the poll modal visibility.
 */
typealias LaunchPollType = (LaunchPollOptions) -> Unit

/**
 * Toggles the visibility of the poll modal based on the current state.
 * 
 * This function accepts [LaunchPollOptions] and toggles the visibility state
 * by calling [updateIsPollModalVisible] with the negation of [isPollModalVisible].
 * 
 * Example:
 * ```kotlin
 * val options = LaunchPollOptions(
 *     updateIsPollModalVisible = { visible -> setPollModalVisible(visible) },
 *     isPollModalVisible = false
 * )
 * 
 * launchPoll(options)
 * ```
 */
fun launchPoll(options: LaunchPollOptions) {
    // Toggle the visibility state of the poll modal
    options.updateIsPollModalVisible(!options.isPollModalVisible)
}
