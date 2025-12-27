package com.mediasfu.sdk.methods.breakout_rooms_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.OnScreenChangesOptions
import com.mediasfu.sdk.consumers.OnScreenChangesParameters
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.RePortParameters
import com.mediasfu.sdk.consumers.RePortType
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.OnScreenChangesType
import com.mediasfu.sdk.model.Participant

/**
 * Data class representing breakout room update information.
 */
data class BreakoutRoomUpdatedData(
    val forHost: Boolean? = null,
    val newRoom: Int? = null,
    val members: List<Participant>? = null,
    val breakoutRooms: List<List<BreakoutParticipant>>? = null,
    val status: String? = null // "started", "ended", etc.
)

/**
 * Type definitions for breakout room updates.
 */
typealias UpdateBreakoutRooms = (List<List<BreakoutParticipant>>) -> Unit
typealias UpdateBreakOutRoomStarted = (Boolean) -> Unit
typealias UpdateBreakOutRoomEnded = (Boolean) -> Unit
typealias UpdateHostNewRoom = (Int) -> Unit
typealias UpdateMeetingDisplayType = (String) -> Unit
typealias UpdateParticipantsAll = (List<Participant>) -> Unit
typealias UpdateParticipants = (List<Participant>) -> Unit

/**
 * Abstract class for parameters.
 */
interface BreakoutRoomUpdatedParameters :
    OnScreenChangesParameters,
    RePortParameters {
    
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    val breakoutRooms: List<List<BreakoutParticipant>>
    val hostNewRoom: Int
    override val islevel: String
    val participantsAll: List<Participant>
    override val participants: List<Participant>
    val meetingDisplayType: String
    val prevMeetingDisplayType: String
    
    val updateBreakoutRooms: UpdateBreakoutRooms
    val updateBreakOutRoomStarted: UpdateBreakOutRoomStarted
    val updateBreakOutRoomEnded: UpdateBreakOutRoomEnded
    val updateHostNewRoom: UpdateHostNewRoom
    val updateMeetingDisplayType: UpdateMeetingDisplayType
    val updateParticipantsAll: UpdateParticipantsAll
    val updateParticipants: UpdateParticipants
    
    val onScreenChanges: OnScreenChangesType
    val rePort: RePortType
    
    override fun getUpdatedAllParams(): BreakoutRoomUpdatedParameters
}

/**
 * Options for breakout room updated.
 */
data class BreakoutRoomUpdatedOptions(
    val data: BreakoutRoomUpdatedData,
    val parameters: BreakoutRoomUpdatedParameters
)

/**
 * Type definition for breakout room updated function.
 */
typealias BreakoutRoomUpdatedType = suspend (BreakoutRoomUpdatedOptions) -> Unit

/**
 * Handles breakout room updates based on the received data and parameters.
 * 
 * ### Parameters:
 * - `options` (`BreakoutRoomUpdatedOptions`): Contains:
 *   - `data`: Breakout room update data with information like room status and participants.
 *   - `parameters`: Provides access to state and update functions, including:
 *     - `breakOutRoomStarted`, `breakOutRoomEnded`: Track breakout room states.
 *     - `islevel`: Indicates the user's permission level (e.g., host, participant).
 *     - `participantsAll`, `participants`: Lists of all and active participants in the room.
 *     - Update functions to change room states, participants, and meeting display type.
 * 
 * ### Workflow:
 * 1. **Host Room Update**:
 *    - If the data is for the host (`data.forHost`), it updates the host's room and triggers a screen update.
 * 
 * 2. **Participant Updates for Level 2 (Host/Moderator)**:
 *    - Updates the participant list if the user has a level 2 role and data for members is available.
 * 
 * 3. **Room Status Change**:
 *    - If the room `status` is "started":
 *      - Sets `breakOutRoomStarted` to true and updates display to show all participants.
 *      - Triggers a port restart for level 2 users.
 *    - If the room `status` is "ended":
 *      - Sets `breakOutRoomEnded` to true and restores the previous meeting display type.
 * 
 * ### Example Usage:
 * ```kotlin
 * val data = BreakoutRoomUpdatedData(
 *     forHost = true,
 *     newRoom = 1,
 *     status = "started",
 *     breakoutRooms = emptyList(),
 *     members = listOf(
 *         Participant(name = "John", audioID = "a1", videoID = "v1", isBanned = false)
 *     )
 * )
 * 
 * val parameters = object : BreakoutRoomUpdatedParameters {
 *     override val breakOutRoomStarted: Boolean = false
 *     override val breakOutRoomEnded: Boolean = false
 *     override val meetingDisplayType: String = "individual"
 *     // Additional required updates and properties...
 * }
 * 
 * val options = BreakoutRoomUpdatedOptions(data = data, parameters = parameters)
 * 
 * breakoutRoomUpdated(options)
 * ```
 * 
 * ### Error Handling:
 * - If an error occurs during the update process, it prints the error message in debug mode.
 */
suspend fun breakoutRoomUpdated(options: BreakoutRoomUpdatedOptions) {
    val parameters = options.parameters.getUpdatedAllParams()
    
    var breakOutRoomStarted = parameters.breakOutRoomStarted
    var breakOutRoomEnded = parameters.breakOutRoomEnded
    val islevel = parameters.islevel
    var participantsAll = parameters.participantsAll
    var participants = parameters.participants
    var meetingDisplayType = parameters.meetingDisplayType
    var prevMeetingDisplayType = parameters.prevMeetingDisplayType
    
    val updateBreakoutRooms = parameters.updateBreakoutRooms
    val updateBreakOutRoomStarted = parameters.updateBreakOutRoomStarted
    val updateBreakOutRoomEnded = parameters.updateBreakOutRoomEnded
    val updateHostNewRoom = parameters.updateHostNewRoom
    val updateMeetingDisplayType = parameters.updateMeetingDisplayType
    val updateParticipantsAll = parameters.updateParticipantsAll
    val updateParticipants = parameters.updateParticipants
    val onScreenChanges = parameters.onScreenChanges
    val rePort = parameters.rePort
    
    val data = options.data
    
    try {
        if (data.forHost != null && data.forHost == true) {
            if (data.newRoom != null) {
                updateHostNewRoom(data.newRoom ?: -1)
            }
            onScreenChanges(OnScreenChangesOptions(
                changed = true,
                parameters = parameters
            ))
            return
        }
        
        if (islevel == "2" && data.members != null) {
            participantsAll = data.members!!.map { participant ->
                Participant(
                    isBanned = participant.isBanned,
                    name = participant.name,
                    audioID = participant.audioID,
                    videoID = participant.videoID
                )
            }
            updateParticipantsAll(participantsAll)
            
            participants = data.members!!.filter { !it.isBanned!! }
            updateParticipants(participants)
        }
        
        updateBreakoutRooms(data.breakoutRooms!!)
        
        if (data.status == "started" &&
            (breakOutRoomStarted || !breakOutRoomEnded)) {
            breakOutRoomStarted = true
            breakOutRoomEnded = false
            updateBreakOutRoomStarted(true)
            updateBreakOutRoomEnded(false)
            
            prevMeetingDisplayType = meetingDisplayType
            if (meetingDisplayType != "all") {
                meetingDisplayType = "all"
                updateMeetingDisplayType("all")
            }
            onScreenChanges(OnScreenChangesOptions(
                changed = true,
                parameters = parameters
            ))
            if (islevel == "2") {
                rePort(RePortOptions(restart = true, parameters = parameters))
            }
        } else if (data.status == "ended") {
            breakOutRoomEnded = true
            updateBreakOutRoomEnded(true)
            
            if (meetingDisplayType != prevMeetingDisplayType) {
                updateMeetingDisplayType(prevMeetingDisplayType)
            }
            onScreenChanges(OnScreenChangesOptions(
                changed = true,
                parameters = parameters
            ))
            if (islevel == "2") {
                rePort(RePortOptions(restart = true, parameters = parameters))
            }
        } else if (data.status == "started" && breakOutRoomStarted) {
            breakOutRoomStarted = true
            breakOutRoomEnded = false
            updateBreakOutRoomStarted(true)
            updateBreakOutRoomEnded(false)
            onScreenChanges(OnScreenChangesOptions(
                changed = true,
                parameters = parameters
            ))
            if (islevel == "2") {
                rePort(RePortOptions(restart = true, parameters = parameters))
            }
        }
    } catch (error: Exception) {
        Logger.e("BreakoutRoomUpdated", "Error in breakoutRoomUpdated: $error")
    }
}
