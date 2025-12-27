package com.mediasfu.sdk.methods.participants_methods

/**
 * Callback function type for updating the visibility of the participants modal.
 */
typealias UpdateIsParticipantsModalVisible = (Boolean) -> Unit

/**
 * Defines options for launching the participants modal.
 */
data class LaunchParticipantsOptions(
    val updateIsParticipantsModalVisible: UpdateIsParticipantsModalVisible,
    val isParticipantsModalVisible: Boolean
)

/**
 * Type definition for the function that launches the participants modal.
 */
typealias LaunchParticipantsType = (LaunchParticipantsOptions) -> Unit

/**
 * Toggles the visibility of the participants modal.
 * 
 * This function takes an [options] parameter of type [LaunchParticipantsOptions],
 * which includes:
 * - `updateIsParticipantsModalVisible`: A callback function to update the visibility state of the participants modal.
 * - `isParticipantsModalVisible`: The current visibility state of the participants modal.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchParticipantsOptions(
 *     isParticipantsModalVisible = true
 * )
 * 
 * launchParticipants(options)
 * // This will toggle the visibility state of the participants modal.
 * ```
 */
fun launchParticipants(options: LaunchParticipantsOptions) {
    options.updateIsParticipantsModalVisible(!options.isParticipantsModalVisible)
}
