package com.mediasfu.sdk.methods.settings_methods

/**
 * Options for launching the settings modal.
 */
data class LaunchSettingsOptions(
    val updateIsSettingsModalVisible: UpdateIsSettingsModalVisible,
    val isSettingsModalVisible: Boolean
)

/**
 * Type definition for the launchSettings function.
 */
typealias LaunchSettingsType = (LaunchSettingsOptions) -> Unit

/**
 * Toggles the visibility state of the settings modal.
 * 
 * The [options] parameter should include:
 * - `updateIsSettingsModalVisible`: A function to update the visibility state of the settings modal.
 * - `isSettingsModalVisible`: The current visibility state of the settings modal.
 * 
 * Example usage:
 * ```kotlin
 * launchSettings(
 *     LaunchSettingsOptions(
 *         updateIsSettingsModalVisible = { visible -> setModalVisible(visible) },
 *         isSettingsModalVisible = false
 *     )
 * )
 * ```
 */
fun launchSettings(options: LaunchSettingsOptions) {
    // Toggle the visibility of the settings modal
    options.updateIsSettingsModalVisible(!options.isSettingsModalVisible)
}
