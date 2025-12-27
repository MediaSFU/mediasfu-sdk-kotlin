package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * FlutterParityMediasfuGeneric mirrors the structure of `mediasfu_sdk/lib/components/mediasfu_components/mediasfu_generic.dart`.
 *
 * The goal is to provide a Kotlin entry point whose sections align with the Flutter reference so
 * that we can port logic incrementally without losing our bearings. Each block is intentionally
 * ordered to match the corresponding Dart section (imports → state setup → socket glue → layout → modals).
 */
@Composable
fun FlutterParityMediasfuGeneric(
    options: MediasfuGenericOptions,
    modifier: Modifier = Modifier,
    stateFactory: @Composable (MediasfuGenericOptions) -> MediasfuGenericState = { rememberMediasfuGenericState(it) }
) {
    val state = stateFactory(options)
    FlutterParityMediasfuGenericContent(
        state = state,
        options = options,
        modifier = modifier
    )
}

/**
 * Top level content wrapper that mirrors the `return` block of the Flutter/X React versions.
 */
@Composable
private fun FlutterParityMediasfuGenericContent(
    state: MediasfuGenericState,
    options: MediasfuGenericOptions,
    modifier: Modifier = Modifier
) {
    val isValidated by state.validated.collectAsState()
    val isLoading by state.isLoading.collectAsState()
    val containerStyle = options.containerStyle
    val backgroundColor = containerStyle.backgroundColor ?: Color(0xFF0B172A)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .applyContainerStyle(containerStyle)
                .background(backgroundColor)
        ) {
            if (!isValidated) {
                PreJoinOrWelcome(state)
            } else {
                ConferenceRoomContent(state)
            }

            if (isLoading) {
                FlutterLoadingOverlay()
            }
        }

        RenderAllOverlays(state)
    }
}

/**
 * Matches the stacked modal render calls in Flutter/React for easier parity diffing.
 */
@Composable
private fun BoxScope.RenderAllOverlays(state: MediasfuGenericState) {
    MenuModal(state)
    RecordingModal(state)
    RequestsModal(state)
    WaitingModal(state)
    DisplaySettingsModal(state)
    CoHostModal(state)
    MediaSettingsModal(state)
    BreakoutRoomsModal(state)
    PollModal(state)
    ParticipantsModal(state)
    MessagesModal(state)
    SettingsModal(state)
    ShareEventModal(state)
    ConfirmExitModal(state)
    ConfirmHereModal(state)
    LoadingModal(state)

    val hasAlert by remember { derivedStateOf { state.alert.visible } }
    ParityAlertBanner(
        state = state,
        isVisible = hasAlert,
        modifier = Modifier
            .padding(16.dp)
            .align(Alignment.TopCenter)
    )
}

@Composable
private fun FlutterLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ParityAlertBanner(
    state: MediasfuGenericState,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    val alertState = state.alert
    val containerColor = when (alertState.type) {
        "danger", "error" -> Color(0xFFFF4D4F)
        "warning" -> Color(0xFFFFC53D)
        "success" -> Color(0xFF52C41A)
        else -> Color(0xFF1890FF)
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = alertState.message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            alertState.type.takeIf { it.isNotBlank() }?.let { alertType ->
                Text(
                    text = alertType.uppercase(),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Coroutine helper to stay close to the reference implementation that wires observers inside init blocks.
 * This provides a single place to launch parity-specific effects without modifying the existing state class.
 */
internal class FlutterParityScope(
    private val scope: CoroutineScope,
    private val validated: StateFlow<Boolean>
) {
    fun launchWhenValidated(onValidated: suspend () -> Unit) {
        scope.launch {
            snapshotFlow { validated.value }
                .collect { value ->
                    if (value) onValidated()
                }
        }
    }
}

/**
 * Factory to create a parity scope from an existing state.
 */
@Composable
internal fun rememberFlutterParityScope(state: MediasfuGenericState): FlutterParityScope {
    val coroutineScope = rememberCoroutineScope()
    val validatedFlow = state.validated
    return remember(state, coroutineScope, validatedFlow) {
        FlutterParityScope(scope = coroutineScope, validated = validatedFlow)
    }
}
