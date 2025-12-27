package com.mediasfu.sdk.methods.recording_methods

import com.mediasfu.sdk.model.ShowAlert

/**
 * Type definitions for launch recording callbacks.
 */
typealias UpdateClearedToRecord = (Boolean) -> Unit
typealias UpdateCanRecord = (Boolean) -> Unit
typealias UpdateIsRecordingModalVisible = (Boolean) -> Unit

/**
 * Options for launching a recording.
 */
data class LaunchRecordingOptions(
    val updateIsRecordingModalVisible: UpdateIsRecordingModalVisible,
    val isRecordingModalVisible: Boolean,
    val showAlert: ShowAlert? = null,
    val stopLaunchRecord: Boolean,
    val canLaunchRecord: Boolean,
    val recordingAudioSupport: Boolean,
    val recordingVideoSupport: Boolean,
    val updateCanRecord: UpdateCanRecord,
    val updateClearedToRecord: UpdateClearedToRecord,
    val recordStarted: Boolean,
    val recordPaused: Boolean,
    val localUIMode: Boolean
)

/**
 * Type alias for the launch recording function.
 */
typealias LaunchRecordingType = (LaunchRecordingOptions) -> Unit

/**
 * Launches the recording process based on various conditions and updates the UI accordingly.
 * 
 * The `launchRecording` function manages the initiation, configuration, and visibility of a recording process,
 * handling cases where recording is either allowed or restricted. Based on the provided `LaunchRecordingOptions`,
 * it checks for permissions, shows alerts for restrictions, and updates the visibility of the recording modal.
 * 
 * ## Parameters:
 * - `options`: An instance of `LaunchRecordingOptions` containing:
 *   - `updateIsRecordingModalVisible`: Callback to update the visibility of the recording modal.
 *   - `isRecordingModalVisible`: Boolean indicating the current visibility of the recording modal.
 *   - `showAlert`: Optional callback for showing alerts.
 *   - `stopLaunchRecord`: Indicates if launching recording should be stopped.
 *   - `canLaunchRecord`: Indicates if launching recording is permitted.
 *   - `recordingAudioSupport`: Indicates if audio recording is supported.
 *   - `recordingVideoSupport`: Indicates if video recording is supported.
 *   - `updateCanRecord`: Callback to update recording permission.
 *   - `updateClearedToRecord`: Callback to update cleared-to-record status.
 *   - `recordStarted`: Indicates if recording has already started.
 *   - `recordPaused`: Indicates if the recording is currently paused.
 *   - `localUIMode`: Indicates if the UI is in local-only mode (restricts recording).
 * 
 * ## Example Usage:
 * 
 * ```kotlin
 * // Define a showAlert function to display an alert message
 * val showAlert: ShowAlert = { message, type, duration ->
 * }
 * 
 * // Callbacks to update recording states
 * 
 * // Define options for launching recording
 * val options = LaunchRecordingOptions(
 *     updateIsRecordingModalVisible = updateIsRecordingModalVisible,
 *     isRecordingModalVisible = false,
 *     showAlert = showAlert,
 *     stopLaunchRecord = true,
 *     canLaunchRecord = true,
 *     recordingAudioSupport = true,
 *     recordingVideoSupport = false,
 *     updateCanRecord = updateCanRecord,
 *     updateClearedToRecord = updateClearedToRecord,
 *     recordStarted = false,
 *     recordPaused = false,
 *     localUIMode = false
 * )
 * 
 * // Launch recording process
 * launchRecording(options)
 * // Expected output:
 * // Recording Modal Visible: true
 * ```
 * 
 * This example sets up the options for launching recording, including alert handling and state updates.
 */
fun launchRecording(options: LaunchRecordingOptions) {
    val showAlert = options.showAlert
    
    // Check if recording is already launched
    if (!options.isRecordingModalVisible &&
        options.stopLaunchRecord &&
        !options.localUIMode) {
        showAlert?.invoke(
            "Recording has already ended or you are not allowed to record",
            "danger",
            3000
        )
        return
    }
    
    // Check if recording initiation is allowed
    if (!options.isRecordingModalVisible &&
        options.canLaunchRecord &&
        !options.localUIMode) {
        // Check if both audio and video recording are not allowed
        if (!options.recordingAudioSupport && !options.recordingVideoSupport) {
            showAlert?.invoke(
                "You are not allowed to record",
                "danger",
                3000
            )
            return
        }
        
        // Update clearedToRecord and canRecord
        options.updateClearedToRecord(false)
        options.updateCanRecord(false)
    }
    
    if (!options.isRecordingModalVisible && options.recordStarted) {
        if (!options.recordPaused) {
            showAlert?.invoke(
                "You can only re-configure recording after pausing it",
                "danger",
                3000
            )
            return
        }
    }
    
    if (!options.isRecordingModalVisible &&
        !options.recordingAudioSupport &&
        !options.recordingVideoSupport &&
        !options.localUIMode) {
        showAlert?.invoke(
            "You are not allowed to record",
            "danger",
            3000
        )
        return
    }
    
    // Update the visibility of the recording modal
    options.updateIsRecordingModalVisible(!options.isRecordingModalVisible)
}
