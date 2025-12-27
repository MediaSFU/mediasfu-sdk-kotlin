// Trigger.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Parameters interface for trigger functionality.
 */
interface TriggerParameters {
    val socket: SocketManager?
    val localSocket: SocketManager?
    val roomName: String
    val screenStates: List<Any> // List<ScreenState> - cast to Map<String,Any?> at runtime
    val participants: List<Any> // List<Participant>
    val updateDateState: Int?
    val lastUpdate: Int?
    val nForReadjust: Int?
    val eventType: Any? // EventType
    val shared: Boolean
    val shareScreenStarted: Boolean
    val whiteboardStarted: Boolean
    val whiteboardEnded: Boolean
    val showAlert: ShowAlert?
    
    // Update functions
    fun updateUpdateDateState(timestamp: Int?)
    fun updateLastUpdate(lastUpdate: Int?)
    fun updateNForReadjust(nForReadjust: Int)
    
    // MediaSFU functions
    suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>>
    
    // Get updated parameters
    fun getUpdatedAllParams(): TriggerParameters
}

/**
 * Options for triggering screen updates.
 */
data class TriggerOptions(
    val refActiveNames: List<String>,
    val parameters: TriggerParameters
)

/**
 * Exception thrown when trigger fails.
 */
class TriggerException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Triggers screen updates and layout adjustments based on active participants.
 * Emits "updateScreenClient" to notify the backend/server about the current layout.
 */
suspend fun trigger(
    options: TriggerOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        var refActiveNames = options.refActiveNames.toMutableList()

        val socketRef = if (parameters.localSocket != null && parameters.localSocket?.id != null) {
            parameters.localSocket
        } else {
            parameters.socket
        }

        if (socketRef == null) {
            return Result.success(Unit)
        }

        // Determine admin name - handle both Participant data class and Map representations
        var adminName = ""
        val participantsList = parameters.participants
        for (p in participantsList) {
            // Try as Participant data class first
            val participant = p as? com.mediasfu.sdk.model.Participant
            if (participant != null) {
                if (participant.islevel == "2") {
                    adminName = participant.name
                    break
                }
            } else {
                // Fallback to Map representation
                @Suppress("UNCHECKED_CAST")
                val pMap = p as? Map<String, Any?>
                if (pMap != null) {
                    val islevel = pMap["islevel"]?.toString() ?: pMap["isLevel"]?.toString()
                    if (islevel == "2") {
                        adminName = pMap["name"]?.toString() ?: ""
                        break
                    }
                }
            }
        }

        // Get screen states - handle both ScreenState data class and Map representations
        var personOnMainScreen: String? = null
        var mainfilled = false
        var adminOnMain = false
        
        val firstScreenState = parameters.screenStates.firstOrNull()
        if (firstScreenState != null) {
            // Try as ScreenState data class first
            val screenState = firstScreenState as? ScreenState
            if (screenState != null) {
                personOnMainScreen = screenState.mainScreenPerson
                mainfilled = screenState.mainScreenFilled
                adminOnMain = screenState.adminOnMainScreen
            } else {
                // Fallback to Map representation
                @Suppress("UNCHECKED_CAST")
                val stateMap = firstScreenState as? Map<String, Any?>
                if (stateMap != null) {
                    personOnMainScreen = stateMap["mainScreenPerson"]?.toString()
                    mainfilled = stateMap["mainScreenFilled"] as? Boolean ?: false
                    adminOnMain = stateMap["adminOnMainScreen"] as? Boolean ?: false
                }
            }
        }

        if (personOnMainScreen == "WhiteboardActive") {
            personOnMainScreen = adminName
        }

        val nowMs = Clock.System.now().toEpochMilliseconds()
        val timestamp = (nowMs / 1000).toInt()
        
        var eventPass = false
        val eventTypeName = parameters.eventType?.toString()?.lowercase() ?: ""
        
        
        // Conference without screen sharing - matches TypeScript exactly
        if (eventTypeName == "conference" && !(parameters.shared || parameters.shareScreenStarted)) {
            eventPass = true
            personOnMainScreen = adminName
            if (!refActiveNames.contains(adminName)) {
                refActiveNames.add(0, adminName)
            }
        }

        // Branch 1: (mainfilled && personOnMainScreen != null && adminOnMain) || eventPass
        if ((mainfilled && personOnMainScreen != null && adminOnMain) || eventPass) {
            
            // Update nForReadjust for conference (TypeScript: nForReadjust = nForReadjust! + 1)
            if (eventTypeName == "conference") {
                val currentNForReadjust = parameters.nForReadjust ?: 0
                parameters.updateNForReadjust(currentNForReadjust + 1)
            }
            
            // Add admin for whiteboard (inside branch 1 like TypeScript)
            if (!refActiveNames.contains(adminName) && parameters.whiteboardStarted && !parameters.whiteboardEnded) {
                refActiveNames.add(0, adminName)
            }
            
            val nForReadjust_ = refActiveNames.size
            var val1 = 0
            
            // Auto adjust - webinar with 0 active names gets val1=0
            if (nForReadjust_ == 0 && eventTypeName == "webinar") {
                val1 = 0
            } else {
                val autoAdjustOptions = AutoAdjustOptions(
                    n = nForReadjust_,
                    eventType = parameters.eventType,
                    shareScreenStarted = parameters.shareScreenStarted,
                    shared = parameters.shared
                )
                val result = parameters.autoAdjust(autoAdjustOptions)
                result.fold(
                    onSuccess = { values -> val1 = values.getOrElse(0) { 0 } },
                    onFailure = { val1 = nForReadjust_ }
                )
            }
            
            val calc1 = ((val1.toDouble() / 12.0) * 100.0).toInt()
            val calc2 = 100 - calc1
            
            emitUpdateScreenClient(
                socketRef = socketRef,
                parameters = parameters,
                roomName = parameters.roomName,
                refActiveNames = refActiveNames,
                mainPercent = calc2,
                mainScreenPerson = personOnMainScreen!!,
                viewType = eventTypeName,
                timestamp = timestamp
            )
            
        // Branch 2: mainfilled && personOnMainScreen != null && !adminOnMain
        } else if (mainfilled && personOnMainScreen != null && !adminOnMain) {
            
            var nForReadjust_ = refActiveNames.size
            
            // Add admin to active names if not present (TypeScript does this in branch 2)
            if (!refActiveNames.contains(adminName)) {
                refActiveNames.add(0, adminName)
            }
            
            val autoAdjustOptions = AutoAdjustOptions(
                n = nForReadjust_,
                eventType = parameters.eventType,
                shareScreenStarted = parameters.shareScreenStarted,
                shared = parameters.shared
            )
            val result = parameters.autoAdjust(autoAdjustOptions)
            var val1 = 0
            result.fold(
                onSuccess = { values -> val1 = values.getOrElse(0) { 0 } },
                onFailure = { val1 = nForReadjust_ }
            )
            
            val calc1 = ((val1.toDouble() / 12.0) * 100.0).toInt()
            val calc2 = 100 - calc1
            
            emitUpdateScreenClient(
                socketRef = socketRef,
                parameters = parameters,
                roomName = parameters.roomName,
                refActiveNames = refActiveNames,
                mainPercent = calc2,
                mainScreenPerson = personOnMainScreen!!,
                viewType = eventTypeName,
                timestamp = timestamp
            )
            
        } else {
            // TypeScript: console.log("trigger stopRecording")
        }

        Result.success(Unit)
    } catch (error: Exception) {
        Logger.e("Trigger", "MediaSFU Trigger: error - ${error.message}")
        Result.failure(
            TriggerException(
                "trigger error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Helper function to emit updateScreenClient - mirrors TypeScript emit logic
 */
private fun emitUpdateScreenClient(
    socketRef: SocketManager,
    parameters: TriggerParameters,
    roomName: String,
    refActiveNames: List<String>,
    mainPercent: Int,
    mainScreenPerson: String,
    viewType: String,
    timestamp: Int
) {
    val lastUpdate = parameters.lastUpdate
    val updateDateState = parameters.updateDateState
    
    // Check if lastUpdate is not null and at least same seconds
    if (lastUpdate == null || updateDateState != timestamp) {
        val payload = mapOf(
            "roomName" to roomName,
            "names" to refActiveNames,
            "mainPercent" to mainPercent,
            "mainScreenPerson" to mainScreenPerson,
            "viewType" to viewType
        )
        
        socketRef.emitWithAck(
            "updateScreenClient",
            payload
        ) { response ->
            val responseMap = response as? Map<String, Any?> ?: emptyMap()
            val success = responseMap["success"] as? Boolean ?: false
            val reason = responseMap["reason"]?.toString() ?: ""
            
            // Update state after emit (TypeScript does this in callback)
            parameters.updateUpdateDateState(timestamp)
            parameters.updateLastUpdate((Clock.System.now().toEpochMilliseconds() / 1000).toInt())
            
            if (success) {
            } else {
                Logger.e("Trigger", "MediaSFU Trigger: updateScreenClient failed - $reason")
            }
        }
    } else {
    }
}
