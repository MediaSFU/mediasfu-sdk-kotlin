package com.mediasfu.sdk.ui.components.misc

import com.mediasfu.sdk.ui.*

/**
 * # ShareEventModal
 *
 * A modal widget that allows users to share event details, including meeting ID and passcode.
 * Includes copy functionality and share buttons for various platforms.
 * Replicates the Flutter SDK's share_event_modal.dart functionality.
 *
 * ## Features
 * - Meeting ID display with copy button
 * - Admin passcode display (for admin users only)
 * - Share buttons for social platforms
 * - Modal positioning
 * - Customizable background color
 * - Scrollable content
 *
 * ## Usage Example
 * ```kotlin
 * val options = ShareEventModalOptions(
 *     isShareEventModalVisible = true,
 *     onShareEventClose = { /* close modal */ },
 *     roomName = "Room 1",
 *     adminPasscode = "123456",
 *     islevel = "2",
 *     eventType = EventType.WEBINAR
 * )
 *
 * val modal = DefaultShareEventModal(options)
 * val rendered = modal.render()
 * ```
 *
 * @property options Configuration options for the share event modal
 */

/**
 * Event type enumeration
 */
enum class EventType {
    BROADCAST,
    CHAT,
    WEBINAR,
    CONFERENCE
}

/**
 * Configuration options for the ShareEventModal component
 *
 * @property backgroundColor Background color for the modal (hex string)
 * @property isShareEventModalVisible Whether the modal is visible
 * @property onShareEventClose Callback to close the modal
 * @property shareButtons Whether to show share buttons
 * @property position Modal position on screen (topRight, topLeft, center, etc.)
 * @property roomName Meeting or room name
 * @property adminPasscode Admin passcode for the event
 * @property islevel User level for displaying certain fields ('2' for admin)
 * @property eventType Type of the event
 * @property localLink Local link for the event (optional)
 */
data class ShareEventModalOptions(
    val backgroundColor: String = "#40C3E0E9",  // Color.fromRGBO(131, 192, 233, 0.25)
    val isShareEventModalVisible: Boolean,
    val onShareEventClose: () -> Unit,
    val shareButtons: Boolean = true,
    val position: String = "topRight",
    val roomName: String,
    val adminPasscode: String,
    val islevel: String,
    val eventType: EventType = EventType.WEBINAR,
    val localLink: String = ""
)

/**
 * Interface for the ShareEventModal component
 */
interface ShareEventModal : MediaSfuUIComponent {
    val options: ShareEventModalOptions
    override val id: String get() = "share_event_modal"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
}

/**
 * Default implementation of ShareEventModal
 */
class DefaultShareEventModal(
    override val options: ShareEventModalOptions
) : ShareEventModal {
    
    fun render(): Map<String, Any> {
        return mapOf(
            "type" to "shareEventModal",
            "visible" to options.isShareEventModalVisible,
            "position" to options.position,
            "container" to mapOf(
                "width" to "400px",
                "maxWidth" to "80%",
                "height" to "60vh",
                "padding" to "10px",
                "backgroundColor" to options.backgroundColor,
                "borderRadius" to "10px"
            ),
            "header" to mapOf(
                "display" to "flex",
                "justifyContent" to "flex-end",
                "closeButton" to mapOf(
                    "icon" to "close",
                    "color" to "#000000",
                    "onClick" to options.onShareEventClose
                )
            ),
            "divider" to mapOf(
                "color" to "#000000"
            ),
            "content" to mapOf(
                "display" to "flex",
                "flexDirection" to "column",
                "overflowY" to "auto",
                "gap" to "20px",
                "sections" to buildList {
                    // Admin passcode (only for admin users)
                    if (options.islevel == "2") {
                        add(mapOf(
                            "type" to "meetingPasscode",
                            "meetingPasscode" to options.adminPasscode
                        ))
                    }
                    
                    // Meeting ID
                    add(mapOf(
                        "type" to "meetingId",
                        "meetingID" to options.roomName
                    ))
                    
                    // Share buttons
                    if (options.shareButtons) {
                        add(mapOf(
                            "type" to "shareButtons",
                            "meetingID" to options.roomName,
                            "eventType" to options.eventType.name.lowercase(),
                            "localLink" to options.localLink
                        ))
                    }
                }
            )
        )
    }
}
