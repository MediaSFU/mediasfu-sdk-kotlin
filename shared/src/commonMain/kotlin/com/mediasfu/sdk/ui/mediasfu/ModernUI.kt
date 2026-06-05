package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

private val ModernDarkColorScheme = darkColorScheme(
    primary = Color(0xFF818CF8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = Color(0xFF60A5FA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = Color(0xFF22D3EE),
    onTertiary = Color(0xFF082F49),
    tertiaryContainer = Color(0xFF164E63),
    onTertiaryContainer = Color(0xFFCFFAFE),
    background = Color(0xFF0F172A),
    onBackground = Color.White,
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFEF4444),
    onError = Color.White,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFECACA)
)

private val ModernLightColorScheme = lightColorScheme(
    primary = Color(0xFF6366F1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF06B6D4),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFFAFE),
    onTertiaryContainer = Color(0xFF164E63),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF334155),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

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
    Screenboard,
    Whiteboard,
    EventSettings,
    Background,
    ConfirmExit,
    Panelists,
    Permissions,
    TranslationSettings
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
    val headerSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
    val iconContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    val iconBorderColor = MaterialTheme.colorScheme.outlineVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .background(iconContainerColor, RoundedCornerShape(12.dp))
                        .border(1.dp, iconBorderColor, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(iconContainerColor, RoundedCornerShape(12.dp))
                    .border(1.dp, iconBorderColor, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

@Composable
fun UnifiedModalHost(
    state: UnifiedModalState,
    content: @Composable (SidebarContent) -> Unit
) {
    val activeContent = state.activeContent
    if (activeContent != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.72f),
                            Color(0xFF020617).copy(alpha = 0.92f)
                        )
                    )
                ),
            contentAlignment = Alignment.CenterEnd
        ) {
            BoxWithConstraints(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize()
            ) {
                val isSmallDevice = maxWidth < 600.dp
                val modalShape = RoundedCornerShape(24.dp)
                val modalWidthModifier = if (isSmallDevice) {
                    Modifier.fillMaxWidth(0.88f)
                } else {
                    Modifier
                        .fillMaxWidth(0.95f)
                        .widthIn(max = 520.dp)
                }

                Surface(
                    modifier = modalWidthModifier
                        .fillMaxHeight(0.9f)
                        .padding(end = 16.dp)
                        .shadow(28.dp, modalShape),
                    shape = modalShape,
                    color = Color.Transparent,
                    contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, modalShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surface,
                                        Color(0xFF111827)
                                    )
                                ),
                                modalShape
                            )
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                content(activeContent)
                            }
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
    val colorScheme = if (isDark) ModernDarkColorScheme else ModernLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// Placeholder for LocalModernColors
val LocalModernColors = staticCompositionLocalOf { Color.Black }
