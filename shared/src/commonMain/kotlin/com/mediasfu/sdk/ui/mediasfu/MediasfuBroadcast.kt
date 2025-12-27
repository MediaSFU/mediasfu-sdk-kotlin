package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediasfu.sdk.model.EventType

/**
 * MediasfuBroadcast - Broadcast event variant component.
 *
 * Pre-configured MediaSFU component optimized for broadcast events.
 * A broadcast is a one-to-many streaming scenario where a host streams
 * to viewers with limited interaction.
 *
 * This is a convenience wrapper around [MediasfuGeneric] with
 * `defaultEventType` set to [EventType.BROADCAST].
 *
 * Features:
 * - Host-controlled broadcasting with start/stop/pause
 * - HLS egress for external streaming (YouTube, Twitch, etc.)
 * - Recording with multiple quality options
 * - Viewer limit controls
 * - Minimal viewer UI (watch-only by default)
 * - Host control panel with broadcast buttons
 * - Chat and Q&A for viewer interaction
 *
 * @param options Configuration options for the broadcast
 * @param modifier Compose modifier for styling
 *
 * @example
 * ```kotlin
 * MediasfuBroadcast(
 *     options = MediasfuGenericOptions(
 *         credentials = Credentials("host123", "key456")
 *     )
 * )
 * ```
 *
 * @see MediasfuGeneric
 * @see MediasfuWebinar
 * @see MediasfuConference
 * @see MediasfuChat
 */
@Composable
fun MediasfuBroadcast(
    options: MediasfuGenericOptions = MediasfuGenericOptions(),
    modifier: Modifier = Modifier,
) {
    MediasfuGeneric(
        options = options.copy(defaultEventType = EventType.BROADCAST),
        modifier = modifier,
    )
}
