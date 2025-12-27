package com.mediasfu.sdk.methods.whiteboard_methods

/**
 * Options for launching the configure whiteboard modal.
 */
data class LaunchConfigureWhiteboardOptions(
    /** Function to update the visibility state of the configure whiteboard modal */
    val updateIsConfigureWhiteboardModalVisible: (Boolean) -> Unit,
    /** Current visibility state of the configure whiteboard modal */
    val isConfigureWhiteboardModalVisible: Boolean
)

/**
 * Type definition for the launchConfigureWhiteboard function.
 */
typealias LaunchConfigureWhiteboardType = (LaunchConfigureWhiteboardOptions) -> Unit

/**
 * Toggles the visibility of the configure whiteboard modal.
 *
 * This function is typically called when the host wants to start, configure,
 * or end a whiteboard session. Only hosts (level 2) can access this functionality.
 *
 * Example:
 * ```kotlin
 * launchConfigureWhiteboard(
 *     LaunchConfigureWhiteboardOptions(
 *         updateIsConfigureWhiteboardModalVisible = { updateIsConfigureWhiteboardModalVisible(it) },
 *         isConfigureWhiteboardModalVisible = false
 *     )
 * )
 * ```
 */
fun launchConfigureWhiteboard(options: LaunchConfigureWhiteboardOptions) {
    // Toggle the visibility of the configure whiteboard modal
    options.updateIsConfigureWhiteboardModalVisible(!options.isConfigureWhiteboardModalVisible)
}
