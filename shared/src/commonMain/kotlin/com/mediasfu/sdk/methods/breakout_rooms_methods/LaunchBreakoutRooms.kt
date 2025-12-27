package com.mediasfu.sdk.methods.breakout_rooms_methods

/**
 * Defines options for launching breakout rooms, including the function to
 * update visibility and the current modal visibility state.
 */
data class LaunchBreakoutRoomsOptions(
    val updateIsBreakoutRoomsModalVisible: (Boolean) -> Unit,
    val isBreakoutRoomsModalVisible: Boolean
)

/**
 * Type definition for the function that launches breakout rooms.
 */
typealias LaunchBreakoutRoomsType = (LaunchBreakoutRoomsOptions) -> Unit

/**
 * Launches the breakout rooms by toggling the visibility of the breakout rooms modal.
 * 
 * This function calls `updateIsBreakoutRoomsModalVisible` with the negated value of
 * `isBreakoutRoomsModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchBreakoutRoomsOptions(
 *     updateIsBreakoutRoomsModalVisible = { isVisible ->
 *         // Update visibility here
 *     },
 *     isBreakoutRoomsModalVisible = false
 * )
 * 
 * launchBreakoutRooms(options)
 * // Toggles the breakout rooms modal to visible.
 * ```
 */
fun launchBreakoutRooms(options: LaunchBreakoutRoomsOptions) {
    options.updateIsBreakoutRoomsModalVisible(!options.isBreakoutRoomsModalVisible)
}
