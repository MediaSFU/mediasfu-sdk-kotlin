package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.MediaSfuUIComponent

/**
 * Options for rendering the collaborative whiteboard modal.
 */
data class WhiteboardModalOptions(
    val isVisible: Boolean = false,
    val onClose: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onClear: () -> Unit,
    val isWhiteboardActive: Boolean,
    val hasDrawingAccess: Boolean,
    val socketManager: SocketManager?
)

interface WhiteboardModal : MediaSfuUIComponent {
    val options: WhiteboardModalOptions
    override val id: String get() = "whiteboard_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true

    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
}

class DefaultWhiteboardModal(
    override val options: WhiteboardModalOptions
) : WhiteboardModal {
    fun render(): Map<String, Any?> {
        return mapOf(
            "type" to "whiteboardModal",
            "isVisible" to options.isVisible,
            "isWhiteboardActive" to options.isWhiteboardActive,
            "hasDrawingAccess" to options.hasDrawingAccess
        )
    }
}

@Composable
fun WhiteboardModal.renderCompose() {
    if (!options.isVisible) return

    AlertDialog(
        onDismissRequest = options.onClose,
        confirmButton = {
            Button(onClick = options.onClose) {
                Text(text = "Close")
            }
        },
        title = {
            Text(
                text = if (options.isWhiteboardActive) "Whiteboard Active" else "Start Whiteboard",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (options.isWhiteboardActive) {
                        "Collaborators can draw on the shared board."
                    } else {
                        "Launch a collaborative whiteboard for participants."
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = options.onStart, enabled = !options.isWhiteboardActive && options.hasDrawingAccess) {
                    Icon(Icons.Filled.Create, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Start Whiteboard")
                }

                Button(onClick = options.onStop, enabled = options.isWhiteboardActive) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Stop Whiteboard")
                }

                Button(onClick = options.onClear, enabled = options.isWhiteboardActive) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Clear Board")
                }
            }
        },
        icon = {
            Icon(Icons.Filled.Create, contentDescription = null)
        }
    )
}
