package com.mediasfu.sdk.ui.components.requests

import com.mediasfu.sdk.ui.*

/**
 * # RequestsModal
 *
 * A modal widget that displays a list of requests with search/filter functionality.
 * Allows users to accept or reject each request using real-time actions through socket.
 * Replicates the Flutter SDK's requests_modal.dart functionality.
 *
 * ## Features
 * - Request counter display in header
 * - Search/filter input field
 * - Scrollable list of requests
 * - Individual request rendering with accept/reject buttons
 * - Real-time request handling via socket
 * - Modal positioning
 * - Customizable background color
 *
 * ## Usage Example
 * ```kotlin
 * val options = RequestsModalOptions(
 *     isRequestsModalVisible = true,
 *     onRequestClose = { /* close modal */ },
 *     requestCounter = 5,
 *     onRequestFilterChange = { query -> /* filter requests */ },
 *     requestList = listOf(
 *         Request(id = "1", name = "John", icon = "fa-microphone")
 *     ),
 *     updateRequestList = { newList -> /* update state */ },
 *     roomName = "MainRoom"
 * )
 *
 * val modal = DefaultRequestsModal(options)
 * val rendered = modal.render()
 * ```
 *
 * @property options Configuration options for the requests modal
 */

/**
 * Parameters interface for requests modal
 */
interface RequestsModalParameters {
    fun getUpdatedAllParams(): RequestsModalParameters
}

/**
 * Configuration options for the RequestsModal component
 *
 * @property isRequestsModalVisible Whether the modal is visible
 * @property onRequestClose Callback to close the modal
 * @property requestCounter Count of active requests
 * @property onRequestFilterChange Callback for filter/search query changes
 * @property requestList List of current requests to display
 * @property onRequestItemPress Callback for handling request item actions
 * @property updateRequestList Function to update the request list
 * @property roomName Name of the room associated with requests
 * @property socket Socket instance for emitting responses
 * @property backgroundColor Background color for the modal (hex string)
 * @property position Modal position on screen (topRight, topLeft, center, etc.)
 * @property renderRequestComponent Custom function to render each request item
 * @property parameters Additional parameters
 */
data class RequestsModalOptions(
    val isRequestsModalVisible: Boolean,
    val onRequestClose: () -> Unit,
    val requestCounter: Int,
    val onRequestFilterChange: (String) -> Unit,
    val requestList: List<Request>,
    val onRequestItemPress: (RespondToRequestsOptions) -> Unit = {},
    val updateRequestList: (List<Request>) -> Unit,
    val roomName: String,
    val socket: Any? = null,
    val backgroundColor: String = "#83C0E9",
    val position: String = "topRight",
    val renderRequestComponent: (RenderRequestComponentOptions) -> Map<String, Any> = { options ->
        DefaultRenderRequestComponent(options).render()
    },
    val parameters: RequestsModalParameters? = null
)

/**
 * Interface for the RequestsModal component
 */
interface RequestsModal : MediaSfuUIComponent {
    val options: RequestsModalOptions
    override val id: String get() = "requests_modal"
    override val isVisible: Boolean get() = options.isRequestsModalVisible
    override val isEnabled: Boolean get() = true

    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}

}

/**
 * Default implementation of RequestsModal
 */
class DefaultRequestsModal(
    override val options: RequestsModalOptions
) : RequestsModal {
    
    fun render(): Map<String, Any> {
        return mapOf(
            "type" to "requestsModal",
            "visible" to options.isRequestsModalVisible,
            "position" to options.position,
            "container" to mapOf(
                "width" to "400px",
                "maxWidth" to "80%",
                "height" to "65vh",
                "padding" to "10px",
                "backgroundColor" to options.backgroundColor,
                "borderRadius" to "10px"
            ),
            "header" to mapOf(
                "display" to "flex",
                "justifyContent" to "space-between",
                "alignItems" to "center",
                "padding" to "8px",
                "title" to mapOf(
                    "text" to "Requests ${options.requestCounter}",
                    "fontSize" to "18px",
                    "fontWeight" to "bold",
                    "color" to "#000000"
                ),
                "closeButton" to mapOf(
                    "icon" to "close",
                    "color" to "#000000",
                    "onClick" to options.onRequestClose
                )
            ),
            "divider" to mapOf(
                "height" to "1px",
                "color" to "#000000"
            ),
            "searchField" to mapOf(
                "type" to "textField",
                "placeholder" to "Search ...",
                "padding" to "8px",
                "border" to "1px solid #CCCCCC",
                "borderRadius" to "4px",
                "margin" to "8px 0",
                "onChange" to options.onRequestFilterChange
            ),
            "requestList" to mapOf(
                "type" to "scrollableList",
                "flex" to "1",
                "overflowY" to "auto",
                "items" to options.requestList.map { request ->
                    mapOf(
                        "type" to "requestItem",
                        "padding" to "4px 0",
                        "content" to options.renderRequestComponent(
                            RenderRequestComponentOptions(
                                request = request,
                                requestList = options.requestList,
                                updateRequestList = options.updateRequestList,
                                roomName = options.roomName,
                                socket = options.socket,
                                onRequestItemPress = options.onRequestItemPress
                            )
                        )
                    )
                }
            )
        )
    }
}
