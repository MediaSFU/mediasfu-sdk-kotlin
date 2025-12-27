package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediasfu.sdk.model.EventType

/**
 * MediasfuChat - Chat event variant component.
 *
 * Pre-configured MediaSFU component optimized for chat-focused events.
 * A chat session emphasizes text communication with optional audio/video.
 *
 * This is a convenience wrapper around [MediasfuGeneric] with
 * `defaultEventType` set to [EventType.CHAT].
 *
 * Features:
 * - Text-first communication interface
 * - Optional audio/video calling
 * - Message threading and replies
 * - File and media sharing
 * - Emoji reactions
 * - Read receipts
 * - Direct and group messaging
 * - Message search and history
 *
 * @param options Configuration options for the chat
 * @param modifier Compose modifier for styling
 *
 * @example
 * ```kotlin
 * MediasfuChat(
 *     options = MediasfuGenericOptions(
 *         credentials = Credentials("user123", "key456")
 *     )
 * )
 * ```
 *
 * @see MediasfuGeneric
 * @see MediasfuBroadcast
 * @see MediasfuWebinar
 * @see MediasfuConference
 */
@Composable
fun MediasfuChat(
    options: MediasfuGenericOptions = MediasfuGenericOptions(),
    modifier: Modifier = Modifier,
) {
    MediasfuGeneric(
        options = options.copy(defaultEventType = EventType.CHAT),
        modifier = modifier,
    )
}
