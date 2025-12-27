package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * MeetingTimerComponents - Timer display components for meeting duration
 * Extracted from MediasfuGeneric.kt to match React/Flutter component structure
 */

@Composable
internal fun MeetingProgressTimerBadge(
    state: MediasfuGenericState,
    modifier: Modifier = Modifier
) {
    // Determine background color based on recording state
    // green = not recording (default), yellow = paused, red = actively recording
    val recordState = state.recording.recordState
    val backgroundColor = when (recordState) {
        "red" -> Color(0xFFFF4D4F)   // Actively recording - red
        "yellow" -> Color(0xFFFAAD14) // Paused - yellow/amber
        else -> Color(0xFF2E7D32)     // Not recording or stopped - green (default)
    }
    
    // Show record icon when recording is active
    val isRecordingActive = recordState == "red" || recordState == "yellow"

    AnimatedVisibility(visible = state.meeting.isVisible) {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Show record indicator icon when recording is active
            if (isRecordingActive) {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = "Recording",
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
            Text(
                text = state.meeting.progressTime,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
internal fun MeetingTimerBar(meeting: MeetingState, modifier: Modifier = Modifier) {
    val elapsed = meeting.progressTime
    AnimatedVisibility(visible = meeting.isVisible) {
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1A2945)),
            modifier = modifier
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = "Meeting duration",
                        tint = Color(0xFF40C4FF)
                    )
                    Column {
                        Text(
                            text = "Meeting Duration",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = elapsed,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Text(
                    text = if (meeting.isRunning) "Live" else "Idle",
                    color = if (meeting.isRunning) Color(0xFF52C41A) else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
