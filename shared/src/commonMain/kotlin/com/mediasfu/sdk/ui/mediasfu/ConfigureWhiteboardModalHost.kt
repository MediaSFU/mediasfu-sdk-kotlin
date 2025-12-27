package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.runtime.Composable
import com.mediasfu.sdk.ui.components.whiteboard.ConfigureWhiteboardModal as ConfigureWhiteboardComponent
import com.mediasfu.sdk.ui.components.whiteboard.ConfigureWhiteboardModalOptions

@Composable
internal fun ConfigureWhiteboardModal(state: MediasfuGenericState) {
    val options: ConfigureWhiteboardModalOptions = state.createConfigureWhiteboardModalOptions()
    if (!options.isVisible) return

    val overrideContent = state.options.uiOverrides.configureWhiteboardModal
    val contentBuilder = withOverride(
        override = overrideContent,
        baseBuilder = { props -> ConfigureWhiteboardComponent(props) }
    )

    contentBuilder(options)
}
