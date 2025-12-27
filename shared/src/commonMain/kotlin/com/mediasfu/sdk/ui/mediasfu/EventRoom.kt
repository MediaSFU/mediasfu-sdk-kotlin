package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable

/**
 * EventRoom - Legacy wrapper retained for API parity with prior implementations.
 */
@Composable
@Deprecated("Use ConferenceRoomContent for parity with reference implementations.",
    ReplaceWith("ConferenceRoomContent(state)")
)
internal fun EventRoom(state: MediasfuGenericState) {
    ConferenceRoomContent(state)
}
