package com.mediasfu.sdk.consumers.socket_receive_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.WebRtcDevice

/**
 * Parameters required by the joinConsumeRoom function.
 */
interface JoinConsumeRoomParameters {
    val roomName: String
    val islevel: String
    val member: String
    val device: WebRtcDevice?

    val updateDevice: (WebRtcDevice?) -> Unit

    // Mediasfu functions
    val receiveAllPipedTransports: suspend (ReceiveAllPipedTransportsOptions) -> Unit
    val createDeviceClient: suspend (CreateDeviceClientOptions) -> WebRtcDevice?

    fun getUpdatedAllParams(): JoinConsumeRoomParameters
}

/**
 * Options for the joinConsumeRoom function.
 *
 * @property remoteSock The remote socket for communication
 * @property apiToken The API token for authentication
 * @property apiUserName The username for API access
 * @property parameters Room-specific settings
 */
data class JoinConsumeRoomOptions(
    val remoteSock: SocketManager,
    val apiToken: String,
    val apiUserName: String,
    val parameters: JoinConsumeRoomParameters
)

/**
 * Options for ReceiveAllPipedTransports.
 *
 * @property nsock The socket connection
 * @property parameters The parameters
 */
data class ReceiveAllPipedTransportsOptions(
    val nsock: SocketManager,
    val parameters: Any
)

/**
 * Options for creating a device client.
 *
 * @property rtpCapabilities RTP capabilities from the server
 */
data class CreateDeviceClientOptions(
    val rtpCapabilities: Any?
)

/**
 * Options for joining a consumption room.
 *
 * @property socket The socket connection
 * @property roomName The room name
 * @property islevel The participant level
 * @property member The member identifier
 * @property sec The security token
 * @property apiUserName The API username
 */
data class JoinConRoomOptions(
    val socket: SocketManager,
    val roomName: String,
    val islevel: String,
    val member: String,
    val sec: String,
    val apiUserName: String
)

/**
 * Response from joining a room.
 *
 * @property success Whether the join was successful
 * @property rtpCapabilities RTP capabilities if successful
 */
data class ResponseJoinRoom(
    val success: Boolean = false,
    val rtpCapabilities: Any? = null
)

/**
 * Joins a consumption room using the provided socket connection.
 *
 * @param options The join room options
 * @return Response indicating success or failure
 */
suspend fun joinConRoom(options: JoinConRoomOptions): ResponseJoinRoom {
    try {
        val response = options.socket.emitWithAck<Any?>(
            event = "joinConRoom",
            data = mapOf(
                "roomName" to options.roomName,
                "islevel" to options.islevel,
                "member" to options.member,
                "sec" to options.sec,
                "apiUserName" to options.apiUserName
            )
        )

        val responseMap = response as? Map<*, *>
        val success = responseMap?.get("success") as? Boolean ?: false
        val rtpCapabilities = responseMap?.get("rtpCapabilities")
        return ResponseJoinRoom(success = success, rtpCapabilities = rtpCapabilities)
    } catch (e: Exception) {
        Logger.e("JoinConsumeRoom", "Error joining con room: ${e.message}")
        return ResponseJoinRoom(success = false)
    }
}

/**
 * Joins a consumption room, initiates a media Device if necessary, and sets up piped transports
 * for streaming.
 *
 * This function:
 * 1. Sends a request to join a specified consumption room using provided authentication details.
 * 2. Checks if a media Device needs to be initialized, and creates it using RTP capabilities if required.
 * 3. Calls `receiveAllPipedTransports` to establish the necessary piped transports for media sharing.
 *
 * @param options An options object containing socket, authentication, and room parameters
 * @return A ResponseJoinRoom containing the result of the join operation
 * @throws Exception if there is an error joining the room, creating the Device, or setting up piped transports
 *
 * Example:
 * ```kotlin
 * val parameters = object : JoinConsumeRoomParameters {
 *     override val roomName = "test-room"
 *     override val islevel = "2"
 *     override val member = "user123"
 *     override val device = null
 *     override val createDeviceClient = { options -> null }
 *     override fun getUpdatedAllParams() = this
 * }
 *
 * val options = JoinConsumeRoomOptions(
 *     remoteSock = socket,
 *     apiToken = "your-api-token",
 *     apiUserName = "test-user",
 *     parameters = parameters
 * )
 *
 * val response = joinConsumeRoom(options)
 * ```
 */
suspend fun joinConsumeRoom(options: JoinConsumeRoomOptions): ResponseJoinRoom {
    // Extract parameters
    val remoteSock = options.remoteSock
    val apiToken = options.apiToken
    val apiUserName = options.apiUserName
    val parameters = options.parameters

    val roomName = parameters.roomName
    val islevel = parameters.islevel
    val member = parameters.member
    var device = parameters.getUpdatedAllParams().device

    val receiveAllPipedTransports = parameters.receiveAllPipedTransports
    val createDeviceClient = parameters.createDeviceClient

    try {
        // Join the consumption room
        val optionsJoinConRoom = JoinConRoomOptions(
            socket = remoteSock,
            roomName = roomName,
            islevel = islevel,
            member = member,
            sec = apiToken,
            apiUserName = apiUserName
        )
        val data = joinConRoom(optionsJoinConRoom)

        if (data.success) {
            // Setup media device if it's not already initialized
            if (device == null && data.rtpCapabilities != null) {
                val optionsDevice = CreateDeviceClientOptions(
                    rtpCapabilities = data.rtpCapabilities
                )
                val newDevice = createDeviceClient(optionsDevice)

                // Update the device in the parameters
                if (newDevice != null) {
                    parameters.updateDevice(newDevice)
                }
            }

            // Initialize piped transports
            val optionsReceive = ReceiveAllPipedTransportsOptions(
                nsock = remoteSock,
                parameters = parameters
            )
            receiveAllPipedTransports(optionsReceive)
        }

        return data
    } catch (error: Exception) {
        Logger.e("JoinConsumeRoom", "MediaSFU - Error in joinConsumeRoom: ${error.message}")
        throw Exception("Failed to join the consumption room or set up necessary components.")
    }
}

