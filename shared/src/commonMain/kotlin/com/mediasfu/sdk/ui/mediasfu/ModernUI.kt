package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

enum class SidebarContent {
    None,
    Menu,
    Participants,
    Messages,
    Requests,
    Waiting,
    CoHost,
    MediaSettings,
    DisplaySettings,
    Settings,
    Recording,
    Polls,
    BreakoutRooms,
    Share,
    Whiteboard,
    EventSettings,
    Background,
    ConfirmExit
}

class UnifiedModalState {
    var activeContent by mutableStateOf<SidebarContent?>(null)
        private set
    
    private val stack = mutableListOf<SidebarContent>()

    val canGoBack: Boolean
        get() = stack.isNotEmpty()

    fun show(content: SidebarContent, pushToStack: Boolean = false) {
        if (pushToStack && activeContent != null && activeContent != content) {
            stack.add(activeContent!!)
        } else if (!pushToStack) {
            stack.clear()
        }
        activeContent = content
    }

    fun navigateBack() {
        if (stack.isNotEmpty()) {
            activeContent = stack.removeAt(stack.lastIndex)
        } else {
            close()
        }
    }

    fun close() {
        activeContent = null
        stack.clear()
    }
}

@Composable
fun UnifiedModalHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back Button
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }

        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium
        )

        // Close Button
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}

@Composable
fun UnifiedModalHost(
    state: UnifiedModalState,
    content: @Composable (SidebarContent) -> Unit
) {
    val activeContent = state.activeContent
    if (activeContent != null) {
        // Overlay background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(enabled = true) { 
                    // Optional: Close on click outside
                    // state.close() 
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize()
            ) {
                val isSmallDevice = maxWidth < 600.dp
                val modalWidthModifier = if (isSmallDevice) {
                    Modifier.fillMaxWidth(0.8f)
                } else {
                    Modifier
                        .fillMaxWidth(0.95f)
                        .widthIn(max = 480.dp)
                }

                // Modal Content
                Surface(
                    modifier = modalWidthModifier
                        .fillMaxHeight(0.85f)
                        .padding(end = 16.dp)
                        .clickable(enabled = false) {}, // Consume clicks inside the modal
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
                    tonalElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Content Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                        ) {
                            content(activeContent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) {
        darkColorScheme()
    } else {
        lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Placeholder for LocalModernColors
val LocalModernColors = staticCompositionLocalOf { Color.Black }
