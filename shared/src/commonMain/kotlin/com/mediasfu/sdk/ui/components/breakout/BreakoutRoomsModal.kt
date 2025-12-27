package com.mediasfu.sdk.ui.components.breakout

import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlin.collections.buildMap

private const val MAX_BREAKOUT_ROOMS = 10

/**
 * BreakoutRoomsModal - Modal for managing breakout rooms.
 *
 * Displays breakout rooms with options to create, edit, delete rooms and manage participants.
 *
 * @property options Configuration options for the breakout rooms modal
 */
data class BreakoutParticipant(
    val name: String,
    val breakRoom: Int? = null,
)

typealias BreakoutRoom = List<BreakoutParticipant>

data class EditRoomModalOptions(
    val editRoomModalVisible: Boolean = false,
    val updateEditRoomModalVisible: (Boolean) -> Unit,
    val currentRoom: List<BreakoutParticipant>? = null,
    val participantsRef: List<Participant> = emptyList(),
    val handleAddParticipant: (Int, BreakoutParticipant) -> Unit,
    val handleRemoveParticipant: (Int, BreakoutParticipant) -> Unit,
    val currentRoomIndex: Int? = null,
    val backgroundColor: Int = 0xFF88ABC2.toInt(),
)

abstract class BreakoutRoomsModalParameters {
    abstract val participants: List<Participant>
    abstract val showAlert: ShowAlert?
    abstract val socket: SocketManager?
    abstract val localSocket: SocketManager?
    abstract val itemPageLimit: Int
    abstract val meetingDisplayType: String
    abstract val prevMeetingDisplayType: String
    abstract val roomName: String
    abstract val shareScreenStarted: Boolean
    abstract val shared: Boolean
    abstract val breakOutRoomStarted: Boolean
    abstract val breakOutRoomEnded: Boolean
    abstract val canStartBreakout: Boolean
    abstract val newParticipantAction: String
    abstract val breakoutRooms: List<List<BreakoutParticipant>>
    
    // Update functions
    abstract val updateBreakOutRoomStarted: (Boolean) -> Unit
    abstract val updateBreakOutRoomEnded: (Boolean) -> Unit
    abstract val updateCurrentRoomIndex: (Int) -> Unit
    abstract val updateCanStartBreakout: (Boolean) -> Unit
    abstract val updateNewParticipantAction: (String) -> Unit
    abstract val updateBreakoutRooms: (List<List<BreakoutParticipant>>) -> Unit
    abstract val updateMeetingDisplayType: (String) -> Unit
    
    abstract fun getUpdatedAllParams(): BreakoutRoomsModalParameters
}

data class BreakoutRoomsModalOptions(
    val isVisible: Boolean = false,
    val onBreakoutRoomsClose: () -> Unit,
    val parameters: BreakoutRoomsModalParameters,
    val position: String = "topRight",
    val backgroundColor: Int = 0xFF83C0E9.toInt(),
)

interface BreakoutRoomsModal : MediaSfuUIComponent {
    val options: BreakoutRoomsModalOptions
    override val id: String get() = "breakout_rooms_modal"
    override val isVisible: Boolean get() = options.isVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /** Adds an empty breakout room (max 10). */
    fun handleAddRoom() {
        val currentRooms = options.parameters.breakoutRooms
        if (currentRooms.size >= MAX_BREAKOUT_ROOMS) {
            options.alert("Maximum $MAX_BREAKOUT_ROOMS rooms allowed.", "danger")
            return
        }
        val updated = currentRooms + listOf(emptyList())
        options.commitRooms(updated, revalidate = false)
    }

    /** Creates random assignments across [roomCount] rooms. */
    fun handleRandomAssign(roomCount: Int) {
        if (!options.validateRoomCount(roomCount)) return
        val participants = options.assignableParticipants()
        if (participants.isEmpty()) {
            options.alert("No eligible participants to assign.", "danger")
            return
        }
        val shuffled = participants.map { BreakoutParticipant(name = it.name) }.shuffled()
        val newRooms = MutableList(roomCount) { mutableListOf<BreakoutParticipant>() }
        shuffled.forEachIndexed { index, participant ->
            val targetRoom = index % roomCount
            newRooms[targetRoom].add(participant.copy(breakRoom = targetRoom))
        }
        options.commitRooms(newRooms)
        options.alert("Participants randomly assigned to $roomCount rooms.", "success")
    }

    /** Creates empty rooms for manual assignment. */
    fun handleManualAssign(roomCount: Int) {
        if (!options.validateRoomCount(roomCount)) return
        val newRooms = List(roomCount) { emptyList<BreakoutParticipant>() }
        options.commitRooms(newRooms, revalidate = false)
        options.alert("$roomCount empty rooms created for manual assignment.", "success")
    }

    /** Persists rooms after running validation. */
    fun handleSaveRooms() {
        val success = validateBreakoutRooms(
            breakoutRooms = options.parameters.breakoutRooms,
            itemPageLimit = options.parameters.itemPageLimit,
            showAlert = options.parameters.showAlert,
            onCanStart = options.parameters.updateCanStartBreakout
        )
        if (success) {
            options.alert("Rooms saved successfully!", "success")
        }
    }

    /** Handles editing a room at the given index. */
    fun handleEditRoom(roomIndex: Int) {
        options.parameters.updateCurrentRoomIndex(roomIndex)
    }

    /** Removes room at [roomIndex] and revalidates. */
    fun handleDeleteRoom(roomIndex: Int) {
        val currentRooms = options.parameters.breakoutRooms.toMutableList()
        if (roomIndex !in currentRooms.indices) return
        currentRooms.removeAt(roomIndex)
        options.commitRooms(currentRooms)
        options.alert("Room deleted successfully.", "success")
    }

    /** Removes [participant] from room. */
    fun handleRemoveParticipant(roomIndex: Int, participant: BreakoutParticipant) {
        val currentRooms = options.parameters.breakoutRooms.toMutableList()
        if (roomIndex !in currentRooms.indices) return
        val room = currentRooms[roomIndex].toMutableList()
        val removed = room.removeAll { it.name == participant.name }
        if (!removed) return
        currentRooms[roomIndex] = room
        options.commitRooms(currentRooms)
    }

    /** Adds [participant] to room if capacity allows. */
    fun handleAddParticipant(roomIndex: Int, participant: BreakoutParticipant) {
        val currentRooms = options.parameters.breakoutRooms.toMutableList()
        if (roomIndex !in currentRooms.indices) return
        val room = currentRooms[roomIndex].toMutableList()
        if (room.size >= options.parameters.itemPageLimit) {
            options.alert("Room is at capacity (${options.parameters.itemPageLimit} max).", "danger")
            return
        }
        if (room.any { it.name == participant.name }) {
            options.alert("Participant already assigned to this room.", "danger")
            return
        }
        room.add(participant.copy(breakRoom = roomIndex))
        currentRooms[roomIndex] = room
        options.commitRooms(currentRooms)
    }

    /** Stores the preferred action for newly joining participants. */
    fun handleNewParticipantAction(action: String) {
        options.parameters.updateNewParticipantAction(action)
    }

    /** Starts or updates breakout rooms after validation. */
    fun handleStartBreakout() {
        val canStart = validateBreakoutRooms(
            breakoutRooms = options.parameters.breakoutRooms,
            itemPageLimit = options.parameters.itemPageLimit,
            showAlert = options.parameters.showAlert,
            onCanStart = options.parameters.updateCanStartBreakout
        )
        if (!canStart || !options.parameters.canStartBreakout) return

        if (!options.parameters.breakOutRoomStarted || options.parameters.breakOutRoomEnded) {
            options.parameters.updateMeetingDisplayType("all")
        }
        options.parameters.updateBreakOutRoomStarted(true)
        options.parameters.updateBreakOutRoomEnded(false)

        val payload = mapOf(
            "breakoutRooms" to options.parameters.breakoutRooms,
            "newParticipantAction" to options.parameters.newParticipantAction,
            "roomName" to options.parameters.roomName
        )

        GlobalScope.launch {
            options.parameters.socket?.emitWithAck("startBreakout", payload) { }
            options.parameters.localSocket?.emitWithAck("startBreakout", payload) { }
        }

        options.alert("Breakout rooms started!", "success")
    }

    /** Stops breakout rooms and restores meeting layout. */
    fun handleStopBreakout() {
        options.parameters.updateBreakOutRoomEnded(true)
        options.parameters.updateBreakOutRoomStarted(false)
        options.parameters.updateMeetingDisplayType(options.parameters.prevMeetingDisplayType)

        val payload = mapOf("roomName" to options.parameters.roomName)
        GlobalScope.launch {
            options.parameters.socket?.emitWithAck("stopBreakout", payload) { }
            options.parameters.localSocket?.emitWithAck("stopBreakout", payload) { }
        }

        options.alert("Breakout rooms stopped.", "success")
    }

    /** Returns participants not yet assigned to any breakout room. */
    fun getUnassignedParticipants(): List<Participant> {
        val assignedNames = options.parameters.breakoutRooms.flatten().map { it.name }.toSet()
        return options.assignableParticipants().filter { it.name !in assignedNames }
    }

    /** Returns participants eligible for assignment (excludes host). */
    fun getAvailableParticipants(): List<Participant> = options.assignableParticipants()
}

/**
 * Default implementation of BreakoutRoomsModal
 */
class DefaultBreakoutRoomsModal(
    override val options: BreakoutRoomsModalOptions
) : BreakoutRoomsModal {
    fun render(): Map<String, Any> {
        return buildMap {
            put("type", "breakoutRoomsModal")
            put("isVisible", options.isVisible)
            put("onClose", options.onBreakoutRoomsClose)
            put("rooms", options.parameters.breakoutRooms)
            put("canStartBreakout", options.parameters.canStartBreakout)
            put("canUpdateBreakout", options.parameters.breakOutRoomStarted && !options.parameters.breakOutRoomEnded && options.parameters.canStartBreakout)
            put("breakOutRoomStarted", options.parameters.breakOutRoomStarted)
            put("breakOutRoomEnded", options.parameters.breakOutRoomEnded)
            put("position", options.position)
            put("backgroundColor", options.backgroundColor)
            put("onAddRoom", { handleAddRoom() })
            put("onEditRoom", { roomIndex: Int -> handleEditRoom(roomIndex) })
            put("onDeleteRoom", { roomIndex: Int -> handleDeleteRoom(roomIndex) })
            put("onRemoveParticipant", { roomIndex: Int, participant: BreakoutParticipant ->
                handleRemoveParticipant(roomIndex, participant)
            })
            put("onAddParticipant", { roomIndex: Int, participant: BreakoutParticipant ->
                handleAddParticipant(roomIndex, participant)
            })
            put("onRandomAssign", { rooms: Int -> handleRandomAssign(rooms) })
            put("onManualAssign", { rooms: Int -> handleManualAssign(rooms) })
            put("onSaveRooms", { handleSaveRooms() })
            put("maxRooms", MAX_BREAKOUT_ROOMS)
            put("onStartBreakout", { handleStartBreakout() })
            put("onStopBreakout", { handleStopBreakout() })
            put("newParticipantAction", options.parameters.newParticipantAction)
            put(
                "newParticipantActionOptions",
                listOf(
                    mapOf("value" to "autoAssignNewRoom", "label" to "Add to new room"),
                    mapOf("value" to "autoAssignAvailableRoom", "label" to "Add to open room"),
                    mapOf("value" to "manualAssign", "label" to "No action")
                )
            )
            put("onNewParticipantActionChange", { action: String -> handleNewParticipantAction(action) })
            put("availableParticipants", getAvailableParticipants())
            put("unassignedParticipants", getUnassignedParticipants())
        }
    }
}

/**
 * Validates breakout room assignments and invokes [onCanStart] with the result.
 */
fun validateBreakoutRooms(
    breakoutRooms: List<List<BreakoutParticipant>>,
    itemPageLimit: Int,
    showAlert: ShowAlert?,
    onCanStart: (Boolean) -> Unit = {}
): Boolean {
    if (breakoutRooms.isEmpty()) {
        onCanStart(false)
        return false
    }

    if (breakoutRooms.any { it.isEmpty() }) {
        showAlert?.invoke("All rooms must have at least one participant.", "danger", 3000)
        onCanStart(false)
        return false
    }

    breakoutRooms.forEach { room ->
        val names = room.map { it.name }
        if (names.size != names.distinct().size) {
            showAlert?.invoke("Duplicate participant names found in a room.", "danger", 3000)
            onCanStart(false)
            return false
        }
    }

    if (breakoutRooms.any { it.size > itemPageLimit }) {
        showAlert?.invoke("Room exceeds capacity limit of $itemPageLimit.", "danger", 3000)
        onCanStart(false)
        return false
    }

    onCanStart(true)
    return true
}

private fun BreakoutRoomsModalOptions.assignableParticipants(): List<Participant> =
    parameters.participants.filter { it.islevel != "2" }

private fun BreakoutRoomsModalOptions.alert(message: String, type: String) {
    parameters.showAlert?.invoke(message, type, 3000)
}

private fun BreakoutRoomsModalOptions.validateRoomCount(roomCount: Int): Boolean {
    if (roomCount <= 0) {
        alert("Please enter a valid number of rooms.", "danger")
        return false
    }
    if (roomCount > MAX_BREAKOUT_ROOMS) {
        alert("Maximum $MAX_BREAKOUT_ROOMS rooms allowed.", "danger")
        return false
    }
    return true
}

private fun BreakoutRoomsModalOptions.commitRooms(
    rooms: List<List<BreakoutParticipant>>,
    revalidate: Boolean = true
) {
    parameters.updateBreakoutRooms(rooms)
    if (revalidate) {
        validateBreakoutRooms(
            breakoutRooms = rooms,
            itemPageLimit = parameters.itemPageLimit,
            showAlert = parameters.showAlert,
            onCanStart = parameters.updateCanStartBreakout
        )
    } else {
        parameters.updateCanStartBreakout(false)
    }
}
