package com.mediasfu.sdk.ui.components.display

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.*

/**
 * AlertComponent - Displays alert messages with auto-hide functionality.
 *
 * This component shows success/error messages with customizable colors and duration.
 * It automatically hides after the specified duration or can be dismissed by tap.
 *
 * @property options Configuration options for the alert component
 */
data class AlertComponentOptions(
    val isVisible: Boolean = false,
    val message: String = "",
    val type: AlertType = AlertType.SUCCESS,
    val duration: Long = 3000L,
    val textColor: Int = 0xFF000000.toInt(),
    val onHide: (() -> Unit)? = null,
)

enum class AlertType {
    SUCCESS,
    ERROR
}

interface AlertComponent : MediaSfuUIComponent {
    val options: AlertComponentOptions
    override val id: String get() = "alert"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Gets the background color based on alert type
     */
    fun getBackgroundColor(): Int {
        return when (options.type) {
            AlertType.SUCCESS -> 0xFF4CAF50.toInt() // Green
            AlertType.ERROR -> 0xFFF44336.toInt() // Red
        }
    }
    
    /**
     * Handles alert dismissal
     */
    fun handleDismiss() {
        options.onHide?.invoke()
    }
    
    /**
     * Starts auto-hide timer
     */
    fun startAutoHideTimer(scope: CoroutineScope) {
        scope.launch {
            delay(options.duration)
            handleDismiss()
        }
    }
}

/**
 * Default implementation of AlertComponent
 */
class DefaultAlertComponent(
    override val options: AlertComponentOptions
) : AlertComponent {
    fun render(): Any {
        return mapOf(
            "type" to "alert",
            "visible" to options.isVisible,
            "message" to options.message,
            "backgroundColor" to getBackgroundColor(),
            "textColor" to options.textColor,
            "onDismiss" to ::handleDismiss
        )
    }
}

/**
 * Composable extension for rendering AlertComponent in Jetpack Compose
 */
@Composable
fun AlertComponent.renderCompose() {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(options.isVisible) {
        if (options.isVisible) {
            startAutoHideTimer(scope)
        }
    }
    
    AnimatedVisibility(
        visible = options.isVisible,
        enter = slideInVertically() + fadeIn(),
        exit = slideOutVertically() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(getBackgroundColor()))
                    .clickable { handleDismiss() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = options.message,
                    color = Color(options.textColor),
                    fontSize = 14.sp
                )
            }
        }
    }
}
