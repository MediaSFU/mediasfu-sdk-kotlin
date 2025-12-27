package com.mediasfu.sdk.ui.components.recording

import com.mediasfu.sdk.ui.*

/**
 * StandardPanelComponent - Standard recording settings panel.
 *
 * Provides basic recording configuration options.
 *
 * @property options Configuration options for the standard panel
 */
data class StandardPanelComponentOptions(
    val parameters: RecordingModalParameters,
)

interface StandardPanelComponent : MediaSfuUIComponent {
    val options: StandardPanelComponentOptions
    override val id: String get() = "standard_panel"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true

    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}

}

/**
 * Default implementation of StandardPanelComponent
 */
class DefaultStandardPanelComponent(
    override val options: StandardPanelComponentOptions
) : StandardPanelComponent {
    fun render(): Any {
        return mapOf(
            "type" to "standardPanel",
            "parameters" to options.parameters
        )
    }
}
