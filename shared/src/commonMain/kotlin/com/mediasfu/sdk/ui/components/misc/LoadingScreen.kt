package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * LoadingScreen - Full-screen loading state with progress indicator.
 *
 * Displays loading spinner with optional message and progress percentage.
 *
 * @property options Configuration options for the loading screen
 */
data class LoadingScreenOptions(
    val isVisible: Boolean = false,
    val message: String = "Loading...",
    val progress: Int? = null, // 0-100, null = indeterminate
    val showLogo: Boolean = true,
    val logoUrl: String = "",
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    val spinnerColor: Int = 0xFF2196F3.toInt(), // Blue
    val textColor: Int = 0xFF000000.toInt(),
)

interface LoadingScreen : MediaSfuUIComponent {
    val options: LoadingScreenOptions
    override val id: String get() = "loading_screen"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets loading screen container style
     */
    fun getContainerStyle(): Map<String, Any> {
        return mapOf(
            "position" to "fixed",
            "top" to "0",
            "left" to "0",
            "right" to "0",
            "bottom" to "0",
            "backgroundColor" to options.backgroundColor,
            "display" to "flex",
            "flexDirection" to "column",
            "alignItems" to "center",
            "justifyContent" to "center",
            "zIndex" to "9999"
        )
    }
    
    /**
     * Gets progress text display
     */
    fun getProgressText(): String {
        return if (options.progress != null) {
            "${options.message} (${options.progress}%)"
        } else {
            options.message
        }
    }
    
    /**
     * Gets spinner style
     */
    fun getSpinnerStyle(): Map<String, Any> {
        return mapOf(
            "width" to "60px",
            "height" to "60px",
            "borderRadius" to "50%",
            "border" to "4px solid ${options.spinnerColor}33", // 20% opacity
            "borderTop" to "4px solid ${options.spinnerColor}",
            "animation" to "spin 1s linear infinite"
        )
    }
}

/**
 * Default implementation of LoadingScreen
 */
class DefaultLoadingScreen(
    override val options: LoadingScreenOptions
) : LoadingScreen {
    fun render(): Any {
        return mapOf(
            "type" to "loadingScreen",
            "isVisible" to options.isVisible,
            "message" to options.message,
            "progressText" to getProgressText(),
            "progress" to options.progress,
            "showLogo" to options.showLogo,
            "logoUrl" to options.logoUrl,
            "containerStyle" to getContainerStyle(),
            "spinnerStyle" to getSpinnerStyle(),
            "textColor" to options.textColor
        )
    }
}
