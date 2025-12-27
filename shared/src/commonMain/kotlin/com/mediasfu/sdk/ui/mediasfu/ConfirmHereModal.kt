package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * ConfirmHereModal - Modal for confirming user presence with countdown
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */
@Composable
internal fun ConfirmHereModal(state: MediasfuGenericState) {
    val props = state.createConfirmHereModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.confirmHereModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultConfirmHereModalContent(it) }
    )

    contentBuilder(props)
}

@Composable
private fun DefaultConfirmHereModalContent(props: ConfirmHereModalProps) {
    var remainingSeconds by remember(props.isVisible) {
        mutableStateOf(props.countdownSeconds.coerceAtLeast(0))
    }

    LaunchedEffect(props.isVisible) {
        if (!props.isVisible) return@LaunchedEffect
        remainingSeconds = props.countdownSeconds.coerceAtLeast(0)
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds -= 1
        }
        props.onTimeout()
    }

    AlertDialog(
        onDismissRequest = props.onDismiss,
        title = { Text("Are You Still There?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = props.message,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = remainingSeconds.coerceAtLeast(0).toString(),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(onClick = props.onConfirm) {
                Text("I'm here")
            }
        }
    )
}
