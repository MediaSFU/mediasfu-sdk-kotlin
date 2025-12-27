package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import com.mediasfu.sdk.ui.components.background.BackgroundModal
import com.mediasfu.sdk.ui.components.background.BackgroundModalOptions

/**
 * Background Modal Wrapper - Wrapper for the BackgroundModal component in MediasfuGeneric
 *
 * This wrapper creates the BackgroundModalOptions from MediasfuGenericState and
 * renders the BackgroundModal composable when visible.
 *
 * Features:
 * - Virtual background selection
 * - Blur backgrounds
 * - Solid color backgrounds
 * - Custom image upload
 */
@Composable
fun BackgroundModalWrapper(state: MediasfuGenericState) {
    val props = state.createBackgroundModalProps()
    if (!props.isVisible) return

    BackgroundModal(props)
}
