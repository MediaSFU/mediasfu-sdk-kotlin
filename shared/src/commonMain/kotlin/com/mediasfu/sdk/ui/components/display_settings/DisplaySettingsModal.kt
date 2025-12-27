package com.mediasfu.sdk.ui.components.display_settings

import com.mediasfu.sdk.ui.*

/**
 * # DisplaySettingsModal
 *
 * A modal component for adjusting display settings for a meeting or event.
 * Replicates the Flutter SDK's display_settings_modal.dart functionality.
 *
 * ## Features
 * - Display option selector (Video Participants Only, Media Participants Only, Show All Participants)
 * - Display Audiographs toggle
 * - Force Full Display toggle
 * - Force Video Participants toggle
 * - Save button to apply settings
 * - Modal positioning
 * - Customizable background color
 *
 * ## Usage Example
 * ```kotlin
 * val options = DisplaySettingsModalOptions(
 *     isVisible = true,
 *     onClose = { /* close modal */ },
 *     parameters = displaySettingsParams,
 *     onModifySettings = { modifyDisplaySettings(it) }
 * )
 *
 * val modal = DefaultDisplaySettingsModal(options)
 * val rendered = modal.render()
 * ```
 *
 * @property options Configuration options for the display settings modal
 */

/**
 * Parameters required by the DisplaySettingsModal
 */
interface DisplaySettingsModalParameters {
    val meetingDisplayType: String  // "video", "media", or "all"
    val autoWave: Boolean
    val forceFullDisplay: Boolean
    val meetingVideoOptimized: Boolean
    
    fun updateMeetingDisplayType(value: String)
    fun updateAutoWave(value: Boolean)
    fun updateForceFullDisplay(value: Boolean)
    fun updateMeetingVideoOptimized(value: Boolean)
}

/**
 * Options for modifying display settings
 */
data class ModifyDisplaySettingsOptions(
    val parameters: DisplaySettingsModalParameters
)

/**
 * Configuration options for the DisplaySettingsModal component
 *
 * @property isVisible Whether the modal is visible
 * @property onClose Callback to close the modal
 * @property onModifySettings Callback to apply changes in display settings
 * @property parameters Current display settings parameters
 * @property position Modal position on screen (topRight, topLeft, center, etc.)
 * @property backgroundColor Background color for the modal (hex string)
 */
data class DisplaySettingsModalOptions(
    val isVisible: Boolean,
    val onClose: () -> Unit,
    val onModifySettings: (ModifyDisplaySettingsOptions) -> Unit = {},
    val parameters: DisplaySettingsModalParameters,
    val position: String = "topRight",
    val backgroundColor: String = "#83C0E9"  // Flutter Color(0xFF83C0E9)
)

/**
 * Interface for the DisplaySettingsModal component
 */
interface DisplaySettingsModal : MediaSfuUIComponent {
    val options: DisplaySettingsModalOptions
    override val id: String get() = "display_settings_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets the display label for a display type
     *
     * @param displayType The display type ("video", "media", or "all")
     * @return The display label
     */
    fun getDisplayTypeLabel(displayType: String): String
}

/**
 * Default implementation of DisplaySettingsModal
 */
class DefaultDisplaySettingsModal(
    override val options: DisplaySettingsModalOptions
) : DisplaySettingsModal {
    
    override fun getDisplayTypeLabel(displayType: String): String {
        return when (displayType) {
            "video" -> "Video Participants Only"
            "media" -> "Media Participants Only"
            "all" -> "Show All Participants"
            else -> "Unknown"
        }
    }
    
    fun render(): Map<String, Any> {
        // Initialize state with current parameters
        var meetingDisplayType = options.parameters.meetingDisplayType
        var autoWave = options.parameters.autoWave
        var forceFullDisplay = options.parameters.forceFullDisplay
        var meetingVideoOptimized = options.parameters.meetingVideoOptimized
        
        return mapOf(
            "type" to "displaySettingsModal",
            "visible" to options.isVisible,
            "position" to options.position,
            "container" to mapOf(
                "width" to "400px",  // modalWidth: 0.8 * screenWidth, max 400
                "maxWidth" to "80%",
                "height" to "65vh",
                "padding" to "20px",
                "backgroundColor" to options.backgroundColor,
                "borderRadius" to "10px"
            ),
            "header" to mapOf(
                "display" to "flex",
                "justifyContent" to "space-between",
                "alignItems" to "center",
                "title" to mapOf(
                    "text" to "Display Settings",
                    "fontSize" to "18px",
                    "fontWeight" to "bold",
                    "color" to "#000000"
                ),
                "closeButton" to mapOf(
                    "icon" to "close",
                    "color" to "#000000",
                    "onClick" to options.onClose
                )
            ),
            "divider" to mapOf(
                "color" to "#000000"
            ),
            "sections" to listOf(
                // Display Option Dropdown
                mapOf(
                    "type" to "dropdown",
                    "label" to "Display Option:",
                    "labelStyle" to mapOf(
                        "fontSize" to "14px",
                        "fontWeight" to "bold",
                        "color" to "#000000"
                    ),
                    "selectedValue" to meetingDisplayType,
                    "options" to listOf("video", "media", "all").map { option ->
                        mapOf(
                            "value" to option,
                            "label" to getDisplayTypeLabel(option)
                        )
                    },
                    "onChange" to { newValue: String ->
                        meetingDisplayType = newValue
                    }
                ),
                
                // Display Audiographs Switch
                mapOf(
                    "type" to "switch",
                    "label" to "Display Audiographs",
                    "labelStyle" to mapOf(
                        "fontSize" to "14px",
                        "fontWeight" to "bold",
                        "color" to "#000000"
                    ),
                    "checked" to autoWave,
                    "onChange" to { newValue: Boolean ->
                        autoWave = newValue
                    }
                ),
                
                // Force Full Display Switch
                mapOf(
                    "type" to "switch",
                    "label" to "Force Full Display",
                    "labelStyle" to mapOf(
                        "fontSize" to "14px",
                        "fontWeight" to "bold",
                        "color" to "#000000"
                    ),
                    "checked" to forceFullDisplay,
                    "onChange" to { newValue: Boolean ->
                        forceFullDisplay = newValue
                    }
                ),
                
                // Force Video Participants Switch
                mapOf(
                    "type" to "switch",
                    "label" to "Force Video Participants",
                    "labelStyle" to mapOf(
                        "fontSize" to "14px",
                        "fontWeight" to "bold",
                        "color" to "#000000"
                    ),
                    "checked" to meetingVideoOptimized,
                    "onChange" to { newValue: Boolean ->
                        meetingVideoOptimized = newValue
                    }
                ),
                
                // Save Button
                mapOf(
                    "type" to "button",
                    "label" to "Save",
                    "marginTop" to "20px",
                    "onClick" to {
                        // Update parameters
                        options.parameters.updateMeetingDisplayType(meetingDisplayType)
                        options.parameters.updateAutoWave(autoWave)
                        options.parameters.updateForceFullDisplay(forceFullDisplay)
                        options.parameters.updateMeetingVideoOptimized(meetingVideoOptimized)
                        
                        // Call modify settings
                        val modifyOptions = ModifyDisplaySettingsOptions(
                            parameters = options.parameters
                        )
                        options.onModifySettings(modifyOptions)
                    }
                )
            )
        )
    }
}
