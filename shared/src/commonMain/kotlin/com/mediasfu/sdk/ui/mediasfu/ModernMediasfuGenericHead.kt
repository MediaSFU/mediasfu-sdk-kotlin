package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders the maintained MediaSFU room interface for an existing room [state].
 *
 * This is a renderer, not a second room engine. Create the state once with
 * [rememberMediasfuGenericState], then pass that same instance here. The standard
 * [MediasfuGeneric] delegates to this function, so both entry points use the exact
 * same room, media, navigation, modal, and teardown implementation.
 *
 * Do not compose [MediasfuGeneric] and [ModernMediasfuGenericHead] for the same
 * state at the same time; doing so would draw the same interface twice.
 *
 * @param useModernTheme Overrides the state's theme wrapper when non-null.
 */
@Composable
fun ModernMediasfuGenericHead(
    state: MediasfuGenericState,
    modifier: Modifier = Modifier,
    useModernTheme: Boolean? = null,
) {
    val shouldWrapInModernTheme = useModernTheme ?: state.options.useModernTheme

    if (shouldWrapInModernTheme) {
        ModernTheme {
            MediasfuGenericContent(state = state, modifier = modifier)
        }
    } else {
        MediasfuGenericContent(state = state, modifier = modifier)
    }
}
