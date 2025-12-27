package com.mediasfu.sdk.methods.requests_methods

/**
 * Type definition for updating requests modal visibility.
 */
typealias UpdateIsRequestsModalVisible = (Boolean) -> Unit

/**
 * Parameters for launching requests in the app.
 */
data class LaunchRequestsOptions(
    val updateIsRequestsModalVisible: UpdateIsRequestsModalVisible,
    val isRequestsModalVisible: Boolean
)

/**
 * Type definition for launching requests.
 */
typealias LaunchRequestsType = (LaunchRequestsOptions) -> Unit

/**
 * Toggles the visibility state of the requests modal.
 * 
 * The [options] parameter should include:
 * - `updateIsRequestsModalVisible`: A function to update the visibility state of the requests modal.
 * - `isRequestsModalVisible`: A boolean indicating the current visibility state of the requests modal.
 * 
 * This function inverts the visibility state by passing the negated value of `isRequestsModalVisible`
 * to `updateIsRequestsModalVisible`.
 * 
 * Example:
 * ```kotlin
 * launchRequests(
 *     LaunchRequestsOptions(
 *         isRequestsModalVisible = true
 *     )
 * )
 * ```
 */
fun launchRequests(options: LaunchRequestsOptions) {
    options.updateIsRequestsModalVisible(!options.isRequestsModalVisible)
}
