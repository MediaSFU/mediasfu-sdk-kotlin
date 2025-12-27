package com.mediasfu.sdk.methods.display_settings_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.OnScreenChangesOptions
import com.mediasfu.sdk.model.OnScreenChangesParameters
import com.mediasfu.sdk.model.OnScreenChangesType
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call

/**
 * Parameters for modifying display settings, including functions and states.
 */
interface ModifyDisplaySettingsParameters : OnScreenChangesParameters {
    // Properties
    val showAlert: ShowAlert?
    val meetingDisplayType: String
    val autoWave: Boolean
    val forceFullDisplay: Boolean
    val meetingVideoOptimized: Boolean
    val islevel: String
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordStopped: Boolean
    val recordPaused: Boolean
    val recordingDisplayType: String
    val recordingVideoOptimized: Boolean
    val prevForceFullDisplay: Boolean
    val prevMeetingDisplayType: String
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    
    // Update functions
    val updateMeetingDisplayType: (String) -> Unit
    val updateAutoWave: (Boolean) -> Unit
    val updateForceFullDisplay: (Boolean) -> Unit
    val updateMeetingVideoOptimized: (Boolean) -> Unit
    val updatePrevForceFullDisplay: (Boolean) -> Unit
    val updatePrevMeetingDisplayType: (String) -> Unit
    val updateIsDisplaySettingsModalVisible: (Boolean) -> Unit
    val updateFirstAll: (Boolean) -> Unit
    val updateUpdateMainWindow: (Boolean) -> Unit
    
    // Mediasfu function
    val onScreenChanges: OnScreenChangesType
}

/**
 * Options for modifying display settings.
 */
data class ModifyDisplaySettingsOptions(
    val parameters: ModifyDisplaySettingsParameters
)

/**
 * Type definition for modifying display settings.
 */
typealias ModifyDisplaySettingsType = suspend (ModifyDisplaySettingsOptions) -> Unit

/**
 * Adjusts meeting display settings, updating state variables and handling alerts.
 * 
 * ### Parameters:
 * - `options` (`ModifyDisplaySettingsOptions`): Contains:
 *   - `parameters`: Settings and functions, including:
 *     - Display settings (`meetingDisplayType`, `autoWave`, etc.)
 *     - Recording status flags (`recordStarted`, `recordResumed`, etc.)
 *     - Update functions for changing settings.
 * 
 * ### Workflow:
 * 1. **Auto-Wave and Force Display Settings**:
 *    - Sets `autoWave` and `forceFullDisplay` as configured in `parameters`.
 * 
 * 2. **Recording-Dependent Display Adjustments**:
 *    - If recording is active, validates compatible display types:
 *      - `meetingDisplayType` changes based on `recordingDisplayType` to ensure compatible display settings for recording sessions.
 * 
 * 3. **Breakout Room Display Restriction**:
 *    - If a breakout room is active, restricts display type to "all."
 * 
 * 4. **Display Update with On-Screen Changes**:
 *    - If the display settings or breakout room requirements change, triggers `onScreenChanges` to apply them to the UI.
 * 
 * ### Example Usage:
 * ```kotlin
 * val parameters = object : ModifyDisplaySettingsParameters {
 *     override val meetingDisplayType: String = "video"
 *     override val forceFullDisplay: Boolean = true
 *     override val recordStarted: Boolean = true
 *     override val recordingDisplayType: String = "media"
 *     // Additional parameter implementations...
 * }
 * 
 * modifyDisplaySettings(ModifyDisplaySettingsOptions(parameters = parameters))
 * ```
 * 
 * ### Error Handling:
 * - Prints error messages to the console in debug mode if an error occurs during settings modification.
 */
suspend fun modifyDisplaySettings(options: ModifyDisplaySettingsOptions) {
    try {
        val parameters = options.parameters
        val showAlert = parameters.showAlert
        var meetingDisplayType = parameters.meetingDisplayType
        val autoWave = parameters.autoWave
        val forceFullDisplay = parameters.forceFullDisplay
        var meetingVideoOptimized = parameters.meetingVideoOptimized
        val islevel = parameters.islevel
        val recordStarted = parameters.recordStarted
        val recordResumed = parameters.recordResumed
        val recordStopped = parameters.recordStopped
        val recordPaused = parameters.recordPaused
        val recordingDisplayType = parameters.recordingDisplayType
        val recordingVideoOptimized = parameters.recordingVideoOptimized
        val prevForceFullDisplay = parameters.prevForceFullDisplay
        val prevMeetingDisplayType = parameters.prevMeetingDisplayType
        
        parameters.updateAutoWave(autoWave)
        parameters.updateForceFullDisplay(forceFullDisplay)
        
        if (islevel == "2" &&
            (recordStarted || recordResumed) &&
            !recordStopped &&
            !recordPaused) {
            if (recordingDisplayType == "video" &&
                meetingDisplayType == "video" &&
                meetingVideoOptimized &&
                !recordingVideoOptimized) {
                showAlert.call(
                    message = "Meeting display type can be either video, media, or all when recording display type is non-optimized video.",
                    type = "danger",
                    duration = 3000
                )
                meetingDisplayType = recordingDisplayType
                parameters.updateMeetingDisplayType(meetingDisplayType)
                meetingVideoOptimized = recordingVideoOptimized
                parameters.updateMeetingVideoOptimized(meetingVideoOptimized)
                return
            } else if (recordingDisplayType == "media" &&
                meetingDisplayType == "video") {
                showAlert.call(
                    message = "Meeting display type can be either media or all when recording display type is media.",
                    type = "danger",
                    duration = 3000
                )
                meetingDisplayType = recordingDisplayType
                parameters.updateMeetingDisplayType(meetingDisplayType)
                return
            } else if (recordingDisplayType == "all" &&
                (meetingDisplayType == "video" || meetingDisplayType == "media")) {
                showAlert.call(
                    message = "Meeting display type can be only all when recording display type is all.",
                    type = "danger",
                    duration = 3000
                )
                meetingDisplayType = recordingDisplayType
                parameters.updateMeetingDisplayType(meetingDisplayType)
                return
            }
        }
        
        parameters.updateMeetingDisplayType(meetingDisplayType)
        parameters.updateMeetingVideoOptimized(meetingVideoOptimized)
        parameters.updateIsDisplaySettingsModalVisible(false)
        
        
        // Check if settings changed OR if we need to force refresh for "all" mode
        val settingsChanged = prevMeetingDisplayType != meetingDisplayType || prevForceFullDisplay != forceFullDisplay
        val needsRefresh = meetingDisplayType == "all" && !settingsChanged  // Force refresh if staying on "all"
        
        if (settingsChanged || needsRefresh) {
            if (parameters.breakOutRoomStarted &&
                !parameters.breakOutRoomEnded &&
                meetingDisplayType != "all") {
                showAlert.call(
                    message = "Breakout room is active. Display type can only be all.",
                    type = "danger",
                    duration = 3000
                )
                parameters.updateMeetingDisplayType(prevMeetingDisplayType)
                return
            }
            
            val newFirstAllValue = meetingDisplayType != "all"
            parameters.updateFirstAll(newFirstAllValue)
            parameters.updateUpdateMainWindow(true)
            parameters.updateMeetingDisplayType(meetingDisplayType)
            parameters.updateForceFullDisplay(forceFullDisplay)
            parameters.onScreenChanges(
                OnScreenChangesOptions(
                    changed = true,
                    parameters = parameters
                )
            )
            parameters.updatePrevForceFullDisplay(forceFullDisplay)
            parameters.updatePrevMeetingDisplayType(meetingDisplayType)
        } else {
        }
    } catch (error: Exception) {
        Logger.e("ModifyDisplaySetting", "MediaSFU - Error in modifyDisplaySettings: $error")
    }
}
