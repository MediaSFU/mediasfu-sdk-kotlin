package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * ConfirmExitModal - Modal for confirming exit/leave actions
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */
@Composable
internal fun ConfirmExitModal(state: MediasfuGenericState) {
    val props = state.createConfirmExitModalProps()
    if (!props.isVisible) return

    val overrideContent = state.options.uiOverrides.confirmExitModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultConfirmExitModalContent(it) }
    )

    contentBuilder(props)
}

/**
 * Public content body for ConfirmExitModal - can be embedded in unified modal system
 * Provides exit confirmation UI without AlertDialog wrapper
 */
@Composable
fun ConfirmExitModalContentBody(
    props: ConfirmExitModalProps,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Leave Event?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = props.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = props.onConfirm,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text(props.confirmLabel)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        OutlinedButton(
            onClick = props.onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun DefaultConfirmExitModalContent(props: ConfirmExitModalProps) {
    AlertDialog(
        onDismissRequest = props.onDismiss,
        title = { Text("Confirm Exit") },
        text = { Text(props.message) },
        confirmButton = {
            TextButton(onClick = props.onConfirm) {
                Text(props.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = props.onDismiss) { Text("Cancel") }
        }
    )
}
