package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import com.mediasfu.sdk.ui.components.whiteboard.DefaultWhiteboardModal
import com.mediasfu.sdk.ui.components.whiteboard.WhiteboardModalOptions
import com.mediasfu.sdk.ui.components.whiteboard.renderCompose

@Composable
internal fun WhiteboardModal(state: MediasfuGenericState) {
    val options: WhiteboardModalOptions = state.createWhiteboardModalOptions()
    if (!options.isVisible) return

    val overrideContent = state.options.uiOverrides.whiteboardModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { DefaultWhiteboardModal(it).renderCompose() }
    )

    contentBuilder(options)
}
