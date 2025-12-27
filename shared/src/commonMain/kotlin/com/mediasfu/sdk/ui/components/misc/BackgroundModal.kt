package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * BackgroundModal - Virtual background selector for video customization.
 *
 * Provides interface for selecting virtual backgrounds, blur effects,
 * or custom images for video calls.
 *
 * @property options Configuration options for the background modal
 */
data class BackgroundModalOptions(
    val isVisible: Boolean = false,
    val onClose: () -> Unit,
    val onSelectBackground: (BackgroundOption) -> Unit,
    val currentBackground: String? = null,
    val currentBlurAmount: Int = 0,
    val customBackgrounds: List<String> = emptyList(),
    val showBlurOption: Boolean = true,
    val showNoneOption: Boolean = true,
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val position: String = "center",
)

data class BackgroundOption(
    val type: BackgroundType,
    val imageUrl: String? = null,
    val blurAmount: Int = 0, // 0-100
)

enum class BackgroundType {
    NONE,
    BLUR,
    IMAGE,
    CUSTOM
}

interface BackgroundModal : MediaSfuUIComponent {
    val options: BackgroundModalOptions
    override val id: String get() = "background_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets default background options
     */
    fun getDefaultBackgrounds(): List<BackgroundOption> {
        val backgrounds = mutableListOf<BackgroundOption>()
        
        if (options.showNoneOption) {
            backgrounds.add(BackgroundOption(BackgroundType.NONE))
        }
        
        if (options.showBlurOption) {
            backgrounds.add(BackgroundOption(BackgroundType.BLUR, blurAmount = 10))
            backgrounds.add(BackgroundOption(BackgroundType.BLUR, blurAmount = 25))
            backgrounds.add(BackgroundOption(BackgroundType.BLUR, blurAmount = 50))
        }
        
        return backgrounds
    }
    
    /**
     * Gets custom background options
     */
    fun getCustomBackgroundOptions(): List<BackgroundOption> {
        return options.customBackgrounds.map { url ->
            BackgroundOption(BackgroundType.CUSTOM, imageUrl = url)
        }
    }
    
    /**
     * Checks if background is currently selected
     */
    fun isSelected(option: BackgroundOption): Boolean {
        return when (option.type) {
            BackgroundType.NONE -> options.currentBackground == null
            BackgroundType.BLUR -> options.currentBackground == null && option.blurAmount == options.currentBlurAmount
            BackgroundType.IMAGE, BackgroundType.CUSTOM -> options.currentBackground == option.imageUrl
        }
    }
}

/**
 * Default implementation of BackgroundModal
 */
class DefaultBackgroundModal(
    override val options: BackgroundModalOptions
) : BackgroundModal {
    fun render(): Any {
        return mapOf(
            "type" to "backgroundModal",
            "isVisible" to options.isVisible,
            "onClose" to options.onClose,
            "onSelectBackground" to options.onSelectBackground,
            "currentBackground" to options.currentBackground,
            "currentBlurAmount" to options.currentBlurAmount,
            "defaultBackgrounds" to getDefaultBackgrounds(),
            "customBackgrounds" to getCustomBackgroundOptions(),
            "backgroundColor" to options.backgroundColor,
            "position" to options.position
        )
    }
}
