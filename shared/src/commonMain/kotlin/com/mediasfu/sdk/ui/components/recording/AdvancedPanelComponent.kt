package com.mediasfu.sdk.ui.components.recording

import com.mediasfu.sdk.ui.*

/**
 * AdvancedPanelComponent - Advanced recording settings panel.
 *
 * Provides detailed recording configuration options including orientation,
 * overlays, and custom text.
 *
 * @property options Configuration options for the advanced panel
 */
data class AdvancedPanelComponentOptions(
    val parameters: RecordingModalParameters,
)

interface AdvancedPanelComponent : MediaSfuUIComponent {
    val options: AdvancedPanelComponentOptions
    override val id: String get() = "advanced_panel"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true

    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}

}

/**
 * Default implementation of AdvancedPanelComponent
 */
class DefaultAdvancedPanelComponent(
    override val options: AdvancedPanelComponentOptions
) : AdvancedPanelComponent {
    fun render(): Any {
        return mapOf(
            "type" to "advancedPanel",
            "parameters" to options.parameters
        )
    }
}
