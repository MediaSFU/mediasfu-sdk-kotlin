package com.mediasfu.sdk.ui.components.requests

import com.mediasfu.sdk.ui.*

/**
 * # RenderRequestComponent
 *
 * A component that renders an individual request item with options to accept or reject.
 * Displays the request name, relevant icon, and action buttons for real-time response handling.
 * Replicates the Flutter SDK's render_request_component.dart functionality.
 *
 * ## Features
 * - Request name display
 * - Icon representation (microphone, video, screen share, etc.)
 * - Accept button (green checkmark)
 * - Reject button (red X)
 * - Real-time action handling via socket
 *
 * ## Usage Example
 * ```kotlin
 * val options = RenderRequestComponentOptions(
 *     request = Request(id = "1", name = "John Doe", icon = "fa-microphone"),
 *     requestList = requests,
 *     updateRequestList = { newList -> /* update state */ },
 *     roomName = "MainRoom"
 * )
 *
 * val component = DefaultRenderRequestComponent(options)
 * val rendered = component.render()
 * ```
 *
 * @property options Configuration options for the render request component
 */

/**
 * Request data structure
 */
data class Request(
    val id: String,
    val name: String?,
    val icon: String,  // "fa-microphone", "fa-desktop", "fa-video", "fa-comments"
    val username: String? = null
)

/**
 * Options for responding to requests
 */
data class RespondToRequestsOptions(
    val request: Request,
    val updateRequestList: (List<Request>) -> Unit,
    val requestList: List<Request>,
    val action: String,  // "accepted" or "rejected"
    val roomName: String,
    val socket: Any?
)

/**
 * Configuration options for the RenderRequestComponent
 *
 * @property request The request data to render
 * @property onRequestItemPress Function to handle accept/reject actions
 * @property requestList The current list of requests
 * @property updateRequestList Function to update the request list state
 * @property roomName The room identifier
 * @property socket Socket instance for emitting responses
 */
data class RenderRequestComponentOptions(
    val request: Request,
    val onRequestItemPress: (RespondToRequestsOptions) -> Unit = {},
    val requestList: List<Request>,
    val updateRequestList: (List<Request>) -> Unit,
    val roomName: String,
    val socket: Any? = null
)

/**
 * Interface for the RenderRequestComponent
 */
interface RenderRequestComponent : MediaSfuUIComponent {
    val options: RenderRequestComponentOptions
    override val id: String get() = "render_request_${options.request.id}"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true

    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}

    
    /**
     * Gets the appropriate icon name based on the icon string
     *
     * @param icon The icon identifier (e.g., "fa-microphone")
     * @return The icon name for rendering
     */
    fun getIconName(icon: String): String
}

/**
 * Default implementation of RenderRequestComponent
 */
class DefaultRenderRequestComponent(
    override val options: RenderRequestComponentOptions
) : RenderRequestComponent {
    
    override fun getIconName(icon: String): String {
        return when (icon) {
            "fa-microphone" -> "mic"
            "fa-desktop" -> "desktop_windows"
            "fa-video" -> "videocam"
            "fa-comments" -> "comment"
            else -> "error"
        }
    }
    
    private fun handleRequestAction(action: String) {
        val respondOptions = RespondToRequestsOptions(
            request = options.request,
            updateRequestList = options.updateRequestList,
            requestList = options.requestList,
            action = action,
            roomName = options.roomName,
            socket = options.socket
        )
        options.onRequestItemPress(respondOptions)
    }
    
    fun render(): Map<String, Any> {
        return mapOf(
            "type" to "renderRequestComponent",
            "container" to mapOf(
                "display" to "flex",
                "flexDirection" to "row",
                "alignItems" to "center",
                "padding" to "4px 0"
            ),
            "sections" to listOf(
                // Request Name (5/11 flex)
                mapOf(
                    "type" to "text",
                    "flex" to "5",
                    "content" to (options.request.name ?: "Unknown"),
                    "color" to "#000000"
                ),
                
                // Request Icon (2/11 flex)
                mapOf(
                    "type" to "icon",
                    "flex" to "2",
                    "icon" to getIconName(options.request.icon),
                    "size" to "24px",
                    "color" to "#000000"
                ),
                
                // Accept Button (2/11 flex)
                mapOf(
                    "type" to "iconButton",
                    "flex" to "2",
                    "icon" to "check",
                    "size" to "24px",
                    "color" to "#4CAF50",  // green
                    "onClick" to { handleRequestAction("accepted") }
                ),
                
                // Reject Button (2/11 flex)
                mapOf(
                    "type" to "iconButton",
                    "flex" to "2",
                    "icon" to "close",
                    "size" to "24px",
                    "color" to "#F44336",  // red
                    "onClick" to { handleRequestAction("rejected") }
                ),
                
                // Spacer (1/11 flex)
                mapOf(
                    "type" to "spacer",
                    "flex" to "1"
                )
            )
        )
    }
}
