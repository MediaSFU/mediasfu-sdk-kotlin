package com.mediasfu.sdk.methods.co_host_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.call
import com.mediasfu.sdk.socket.SocketManager

/**
 * Defines the options for modifying co-host settings.
 */
data class ModifyCoHostSettingsOptions(
    val roomName: String,
    val showAlert: ShowAlert? = null,
    val selectedParticipant: String,
    val coHost: String,
    val coHostResponsibility: List<CoHostResponsibility>,
    val updateIsCoHostModalVisible: (Boolean) -> Unit,
    val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit,
    val updateCoHost: (String) -> Unit,
    val socket: SocketManager? = null
)

/**
 * Type definition for modifying co-host settings.
 */
typealias ModifyCoHostSettingsType = suspend (ModifyCoHostSettingsOptions) -> Unit

/**
 * Modifies the co-host settings for a specified room.
 * 
 * This function allows updating the co-host settings by selecting a participant,
 * setting co-host responsibilities, and emitting an update event via socket.
 * 
 * If the room is in demo mode, an alert is shown instead.
 * 
 * Example:
 * ```kotlin
 * val options = ModifyCoHostSettingsOptions(
 *     roomName = "mainRoom",
 *     showAlert = { alert -> Logger.d("ModifyCoHostSettings", alert.message) },
 *     selectedParticipant = "User123",
 *     coHost = "No coHost",
 *     coHostResponsibility = listOf(CoHostResponsibility(name = "media", value = true)),
 *     socket = socketInstance
 * )
 * 
 * modifyCoHostSettings(options)
 * // Updates co-host settings and emits the event to the server.
 * ```
 */
suspend fun modifyCoHostSettings(options: ModifyCoHostSettingsOptions) {
    // Check if the room is in demo mode
    if (options.roomName.lowercase().startsWith("d")) {
        options.showAlert.call(
            message = "You cannot add a co-host in demo mode.",
            type = "danger",
            duration = 3000
        )
        return
    }
    
    var newCoHost = options.coHost
    
    if (options.coHost != "No coHost" ||
        (options.selectedParticipant.isNotEmpty() &&
            options.selectedParticipant != "Select a participant")) {
        if (options.selectedParticipant.isNotEmpty() &&
            options.selectedParticipant != "Select a participant") {
            newCoHost = options.selectedParticipant
            options.updateCoHost(newCoHost)
        }
        
        options.updateCoHostResponsibility(options.coHostResponsibility)
        
        val coHostResponsibilityMap = options.coHostResponsibility.map { item ->
            mapOf(
                "name" to item.name,
                "value" to item.value,
                "dedicated" to item.dedicated
            )
        }
        
        // Emit socket event to update co-host information
        if (options.socket != null) {
            options.socket.emit("updateCoHost", mapOf(
                "roomName" to options.roomName,
                "coHost" to newCoHost,
                "coHostResponsibility" to coHostResponsibilityMap
            ))
        }
    }
    
    // Close the co-host modal
    options.updateIsCoHostModalVisible(false)
}
