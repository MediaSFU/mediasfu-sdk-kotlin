package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediasfu.sdk.model.EventType

/**
 * MediasfuWebinar - Webinar event variant component.
 *
 * Pre-configured MediaSFU component optimized for webinar events.
 * A webinar is a presenter-to-audience format with controlled interaction.
 *
 * This is a convenience wrapper around [MediasfuGeneric] with
 * `defaultEventType` set to [EventType.WEBINAR].
 *
 * Features:
 * - Presenter/panelist and audience separation
 * - Raise hand functionality for audience participation
 * - Q&A moderation tools
 * - Polling and reactions
 * - Multiple presenters support
 * - Screen sharing with annotations
 * - Recording and playback
 * - Waiting room for attendees
 *
 * @param options Configuration options for the webinar
 * @param modifier Compose modifier for styling
 *
 * @example
 * ```kotlin
 * MediasfuWebinar(
 *     options = MediasfuGenericOptions(
 *         credentials = Credentials("presenter123", "key456")
 *     )
 * )
 * ```
 *
 * @see MediasfuGeneric
 * @see MediasfuBroadcast
 * @see MediasfuConference
 * @see MediasfuChat
 */
@Composable
fun MediasfuWebinar(
    options: MediasfuGenericOptions = MediasfuGenericOptions(),
    modifier: Modifier = Modifier,
) {
    MediasfuGeneric(
        options = options.copy(defaultEventType = EventType.WEBINAR),
        modifier = modifier,
    )
}
