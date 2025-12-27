package com.mediasfu.sdk.methods.display_settings_methods

/**
 * Defines options for launching the display settings modal, including the function to
 * update visibility and the current modal visibility state.
 */
data class LaunchDisplaySettingsOptions(
    val updateIsDisplaySettingsModalVisible: (Boolean) -> Unit,
    val isDisplaySettingsModalVisible: Boolean
)

/**
 * Type definition for the function that launches the display settings modal.
 */
typealias LaunchDisplaySettingsType = (LaunchDisplaySettingsOptions) -> Unit

/**
 * Toggles the visibility of the display settings modal.
 * 
 * This function calls `updateIsDisplaySettingsModalVisible` with the negated value of
 * `isDisplaySettingsModalVisible` to toggle the modal's visibility.
 * 
 * Example:
 * ```kotlin
 * val options = LaunchDisplaySettingsOptions(
 *     updateIsDisplaySettingsModalVisible = { isVisible ->
 *         // Update visibility here
 *     },
 *     isDisplaySettingsModalVisible = false
 * )
 * 
 * launchDisplaySettings(options)
 * // This will open the display settings modal if it's currently closed, or close it if it's open.
 * ```
 */
fun launchDisplaySettings(options: LaunchDisplaySettingsOptions) {
    options.updateIsDisplaySettingsModalVisible(!options.isDisplaySettingsModalVisible)
}
