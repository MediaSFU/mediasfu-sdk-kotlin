package com.mediasfu.sdk.methods.requests_methods

import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.socket.SocketManager

/**
 * Defines the options for responding to requests.
 */
data class RespondToRequestsOptions(
    val socket: SocketManager? = null,
    val request: Request,
    val updateRequestList: (List<Request>) -> Unit,
    val requestList: List<Request>,
    val action: String,
    val roomName: String
)

/**
 * Type definition for the respondToRequests function.
 */
typealias RespondToRequestsType = suspend (RespondToRequestsOptions) -> Unit

/**
 * Responds to incoming requests by updating the request list locally and notifying the server of the request status.
 * This function is typically used to manage permissions or participation requests in real-time collaboration tools.
 * 
 * ### Parameters:
 * - [options] (`RespondToRequestsOptions`): Contains the following:
 *   - `socket`: An instance of the socket connection used to communicate with the server.
 *   - `request`: The request to respond to, containing fields like `id`, `name`, and `icon`.
 *   - `updateRequestList`: A callback function to update the list of pending requests.
 *   - `requestList`: The current list of requests.
 *   - `action`: The action to perform in response to the request (e.g., "accept" or "reject").
 *   - `roomName`: The name of the room in which the response should be processed.
 * 
 * ### Example:
 * ```kotlin
 * respondToRequests(
 *     RespondToRequestsOptions(
 *         socket = socket,
 *         request = Request(id = "123", name = "John Doe", icon = "fa-microphone"),
 *         updateRequestList = { newList -> setState { requests = newList } },
 *         requestList = requests,
 *         action = "accept",
 *         roomName = "conferenceRoom"
 *     )
 * )
 * ```
 * 
 * ### Workflow:
 * 1. Filters out the specified `request` from `requestList`.
 * 2. Updates the list of requests locally using `updateRequestList`.
 * 3. Sends the response to the server by emitting `updateUserofRequestStatus`.
 * 
 * This ensures both the local UI and the server stay in sync regarding the request's status.
 */
suspend fun respondToRequests(options: RespondToRequestsOptions) {
    // Filter out the request from the request list
    val newRequestList = options.requestList.filter { request_ ->
        !(request_.id == options.request.id &&
            request_.icon == options.request.icon &&
            request_.name == options.request.name)
    }
    
    // Update the request list with the filtered list
    options.updateRequestList(newRequestList)
    
    // Prepare the request response
    val requestResponse = mapOf(
        "id" to options.request.id,
        "name" to options.request.name,
        "type" to options.request.icon,
        "action" to options.action
    )
    
    // Emit the request response to the server
    options.socket?.emit("updateUserofRequestStatus", mapOf(
        "requestResponse" to requestResponse,
        "roomName" to options.roomName
    ))
}
