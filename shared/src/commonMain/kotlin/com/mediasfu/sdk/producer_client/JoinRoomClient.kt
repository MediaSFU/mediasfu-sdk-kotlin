package com.mediasfu.sdk.producer_client
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.JoinConRoomOptions
import com.mediasfu.sdk.socket.JoinRoomOptions
import com.mediasfu.sdk.socket.ResponseJoinRoom
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.socket.joinConRoom
import com.mediasfu.sdk.socket.joinRoom

/**
 * Represents options for joining a room client.
 */
data class JoinRoomClientOptions(
    val socket: SocketManager? = null,
    val roomName: String,
    val islevel: String,
    val member: String,
    val sec: String,
    val apiUserName: String,
    val consume: Boolean = false // Default to false
)

/**
 * Type definition for joining room client.
 */
typealias JoinRoomClientType = suspend (JoinRoomClientOptions) -> ResponseJoinRoom

/**
 * Joins a room by emitting the `joinRoom` or `joinConRoom` event to the server using the provided socket.
 * 
 * This function uses `JoinRoomClientOptions` as input, selecting `joinRoom` if `consume` is false
 * and `joinConRoom` if `consume` is true. It returns a `ResponseJoinRoom` object containing data
 * from the server. If the process fails, an error is thrown and a message is logged in debug mode.
 * 
 * ### Example Usage:
 * ```kotlin
 * val options = JoinRoomClientOptions(
 *     socket = socketInstance,
 *     roomName = "meeting123",
 *     islevel = "1",
 *     member = "user123",
 *     sec = "abc123",
 *     apiUserName = "user123",
 *     consume = true
 * )
 * 
 * try {
 *     val response = joinRoomClient(options)
 * } catch (error: Exception) {
 *     Logger.e("JoinRoomClient", "Error joining room: $error")
 * }
 * ```
 * 
 * In this example:
 * - The function attempts to join `meeting123` using `joinConRoom` (since `consume` is true).
 * - If successful, it prints the `roomId` from `ResponseJoinRoom`. If not, it catches and logs the error.
 */
suspend fun joinRoomClient(options: JoinRoomClientOptions): ResponseJoinRoom {
    return try {
    val socket = options.socket ?: throw IllegalArgumentException("Socket instance is required to join a room")
    val data: ResponseJoinRoom
        
        if (options.consume) {
            // Use `joinConRoom` for consuming
            val option = JoinConRoomOptions(
        socket = socket,
                roomName = options.roomName,
                islevel = options.islevel,
                member = options.member,
                sec = options.sec,
                apiUserName = options.apiUserName
            )
            data = joinConRoom(option).getOrThrow()
        } else {
            // Use `joinRoom` for non-consuming
            val option = JoinRoomOptions(
                socket = socket,
                roomName = options.roomName,
                islevel = options.islevel,
                member = options.member,
                sec = options.sec,
                apiUserName = options.apiUserName
            )
            data = joinRoom(option).getOrThrow()
        }
        
        data
    } catch (error: Exception) {
        Logger.e("JoinRoomClient", "Error joining room: $error")
        throw Exception("Failed to join the room. Please check your connection and try again.")
    }
}
