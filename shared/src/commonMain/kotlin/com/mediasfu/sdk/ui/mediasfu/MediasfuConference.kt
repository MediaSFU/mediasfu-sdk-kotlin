package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediasfu.sdk.model.EventType

/**
 * MediasfuConference - Conference event variant component.
 *
 * Pre-configured MediaSFU component optimized for conference events.
 * A conference provides equal participation for all attendees with
 * full audio/video capabilities.
 *
 * This is a convenience wrapper around [MediasfuGeneric] with
 * `defaultEventType` set to [EventType.CONFERENCE].
 *
 * Features:
 * - Equal participation for all members
 * - Full audio/video for every participant
 * - Grid and spotlight view layouts
 * - Screen sharing support
 * - Breakout rooms
 * - Recording capabilities
 * - Whiteboard collaboration
 * - Real-time chat and reactions
 *
 * @param options Configuration options for the conference
 * @param modifier Compose modifier for styling
 *
 * @example
 * ```kotlin
 * MediasfuConference(
 *     options = MediasfuGenericOptions(
 *         credentials = Credentials("user123", "key456")
 *     )
 * )
 * ```
 *
 * @see MediasfuGeneric
 * @see MediasfuBroadcast
 * @see MediasfuWebinar
 * @see MediasfuChat
 */
@Composable
fun MediasfuConference(
    options: MediasfuGenericOptions = MediasfuGenericOptions(),
    modifier: Modifier = Modifier,
) {
    MediasfuGeneric(
        options = options.copy(defaultEventType = EventType.CONFERENCE),
        modifier = modifier,
    )
}
