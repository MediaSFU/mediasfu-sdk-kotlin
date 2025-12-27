package com.mediasfu.sdk.methods.exit_methods

import com.mediasfu.sdk.socket.SocketManager

/**
 * Defines the options for confirming the exit of a member from a room.
 */
data class ConfirmExitOptions(
    val socket: SocketManager? = null,
    val localSocket: SocketManager? = null,
    val member: String,
    val roomName: String,
    val ban: Boolean = false
)

/**
 * Type definition for the function that confirms a member's exit.
 */
typealias ConfirmExitType = suspend (ConfirmExitOptions) -> Unit

/**
 * Confirms the exit of a member from a room and optionally bans them.
 * 
 * This function emits a socket event to disconnect the user from the specified room
 * and optionally bans them if [ban] is set to true.
 * 
 * Example:
 * ```kotlin
 * val options = ConfirmExitOptions(
 *     socket = socketInstance,
 *     localSocket = socketInstance,
 *     member = "JohnDoe",
 *     roomName = "Room123",
 *     ban = true
 * )
 * 
 * confirmExit(options)
 * // Disconnects "JohnDoe" from "Room123" and bans them if specified.
 * ```
 */
suspend fun confirmExit(options: ConfirmExitOptions) {
    // Emit a socket event to disconnect the user from the room
    options.socket?.emit("disconnectUser", mapOf(
        "member" to options.member,
        "roomName" to options.roomName,
        "ban" to options.ban
    ))
    
    if (options.localSocket != null && options.localSocket?.id != null) {
        options.localSocket?.emit("disconnectUser", mapOf(
            "member" to options.member,
            "roomName" to options.roomName,
            "ban" to options.ban
        ))
    }
}
