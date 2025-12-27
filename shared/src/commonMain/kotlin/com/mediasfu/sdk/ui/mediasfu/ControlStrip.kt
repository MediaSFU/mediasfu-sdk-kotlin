package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * ControlStrip - Bottom control buttons for media controls
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */
@Composable
internal fun ControlStrip(state: MediasfuGenericState) {
    // Debug: Log what values are being used for remember keys
    
    val buttons = remember(
        state.media.audioAlreadyOn,
        state.media.videoAlreadyOn,
        state.media.screenAlreadyOn,
        state.recording.recordStarted,
        state.recording.showRecordButtons, // Add showRecordButtons as a key
        state.messaging.showMessagesBadge,
        state.room.participantsCounter
    ) { 
        state.primaryControlButtons() 
    }

    ControlButtonsRow(
        buttons = buttons,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
