package com.mediasfu.sdk.ui.components.display

import com.mediasfu.sdk.ui.*

/**
 * LoadingModal - Displays a loading indicator with backdrop overlay.
 *
 * This component shows a circular progress indicator centered on screen
 * with a semi-transparent backdrop that blocks user interaction.
 *
 * @property options Configuration options for the loading modal
 */
data class LoadingModalOptions(
    val isVisible: Boolean = false,
    val backgroundColor: Int = 0x80000000.toInt(), // Semi-transparent black
    val indicatorColor: Int = 0xFFFFFFFF.toInt(), // White
    val displayColor: Int = 0xFFFFFFFF.toInt(), // White
    val message: String? = null
)

interface LoadingModal : MediaSfuUIComponent {
    val options: LoadingModalOptions
    override val id: String get() = "loading_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
}

/**
 * Default implementation of LoadingModal
 */
class DefaultLoadingModal(
    override val options: LoadingModalOptions
) : LoadingModal {
    fun render(): Any {
        return mapOf(
            "type" to "loadingModal",
            "visible" to options.isVisible,
            "backgroundColor" to options.backgroundColor,
            "indicatorColor" to options.indicatorColor,
            "displayColor" to options.displayColor
        )
    }
}
