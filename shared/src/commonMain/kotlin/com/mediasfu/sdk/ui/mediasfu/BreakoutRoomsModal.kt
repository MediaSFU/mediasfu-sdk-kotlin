package com.mediasfu.sdk.ui.mediasfu
import com.mediasfu.sdk.util.Logger

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.components.breakout.BreakoutParticipant
import com.mediasfu.sdk.ui.components.breakout.BreakoutRoomsModalOptions
import com.mediasfu.sdk.ui.components.breakout.validateBreakoutRooms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Breakout Rooms Modal - Complete management interface for breakout rooms
 *
 * Features:
 * - Create rooms (Random/Manual/Add Room)
 * - Edit room participants
 * - Delete rooms
 * - Validate rooms before starting
 * - Start/Update/Stop breakout sessions
 * - Configure new participant action
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakoutRoomsModal(state: MediasfuGenericState) {
    val props = state.createBreakoutRoomsModalProps()
    if (!props.isVisible) return

    DefaultBreakoutRoomsModalContent(props)
}

/**
 * Breakout Rooms Modal Content Body - The main content without dialog wrapper.
 * Can be used in unified modal system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakoutRoomsModalContentBody(
    props: BreakoutRoomsModalOptions,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    
    // State management
    var numRoomsText by remember { mutableStateOf("") }
    var editRoomModalVisible by remember { mutableStateOf(false) }
    var currentRoomIndex by remember { mutableStateOf(0) }
    var currentRoom by remember { mutableStateOf<List<BreakoutParticipant>>(emptyList()) }
    var newParticipantAction by remember { mutableStateOf(props.parameters.newParticipantAction) }
    var breakoutRooms by remember { mutableStateOf(props.parameters.breakoutRooms) }
    var canStartBreakout by remember { mutableStateOf(false) }
    
    // Filter out host (islevel == '2') from participants
    val filteredParticipants = remember(props.parameters.participants) {
        props.parameters.participants.filter { it.islevel != "2" }
    }
    
    // Sync with props
    LaunchedEffect(props.parameters.breakoutRooms) {
        breakoutRooms = props.parameters.breakoutRooms
        validateBreakoutRooms(
            breakoutRooms = breakoutRooms,
            itemPageLimit = props.parameters.itemPageLimit,
            showAlert = props.parameters.showAlert,
            onCanStart = { canStart -> 
                canStartBreakout = canStart
                props.parameters.updateCanStartBreakout(canStart)
            }
        )
    }

    LaunchedEffect(props.parameters.newParticipantAction) {
        newParticipantAction = props.parameters.newParticipantAction
    }
    
    // Create handler state object
    val handlers = rememberBreakoutHandlers(
        props = props,
        scope = scope,
        filteredParticipants = filteredParticipants,
        numRoomsText = numRoomsText,
        breakoutRooms = breakoutRooms,
        newParticipantAction = newParticipantAction,
        onBreakoutRoomsChange = { breakoutRooms = it },
        onNewParticipantActionChange = { newParticipantAction = it },
        onCanStartBreakoutChange = { canStartBreakout = it }
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Number of Rooms Input
        OutlinedTextField(
            value = numRoomsText,
            onValueChange = { numRoomsText = it },
            label = { Text("Number of Rooms") },
            placeholder = { Text("Enter number (max 10)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        // Action Buttons Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Random Button
            Button(
                onClick = { handlers.handleRandomAssign() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Random")
            }
            
            // Manual Button
            Button(
                onClick = { handlers.handleManualAssign() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Manual")
            }
            
            // Add Room Button
            Button(
                onClick = { handlers.handleAddRoom() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Room")
            }
            
            // Save Rooms Button
            Button(
                onClick = { handlers.handleSaveRooms() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Save Rooms")
            }
        }
        
        HorizontalDivider()
        
        // New Participant Action Dropdown
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "New Participant Action",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            
            var expanded by remember { mutableStateOf(false) }
            val options = listOf(
                "autoAssignNewRoom" to "Add to new room",
                "autoAssignAvailableRoom" to "Add to open room",
                "manualAssign" to "No action"
            )
            val selectedLabel = options.find { it.first == newParticipantAction }?.second ?: "Add to new room"
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                handlers.handleNewParticipantActionChange(value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        
        HorizontalDivider()
        
        // Room List
        if (breakoutRooms.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No rooms created yet",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        } else {
            breakoutRooms.forEachIndexed { index, room ->
                RoomCard(
                    roomIndex = index,
                    room = room,
                    onEdit = {
                        currentRoomIndex = index
                        currentRoom = breakoutRooms[index]
                        editRoomModalVisible = true
                    },
                    onDelete = { handlers.handleDeleteRoom(index) },
                    onRemoveParticipant = { participant -> 
                        handlers.handleRemoveParticipant(index, participant) 
                    }
                )
            }
        }
        
        HorizontalDivider()
        
        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                (!props.parameters.breakOutRoomStarted || props.parameters.breakOutRoomEnded) && canStartBreakout -> {
                    Button(
                        onClick = { handlers.handleStartBreakout() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start Breakout")
                    }
                }
                props.parameters.breakOutRoomStarted && !props.parameters.breakOutRoomEnded && canStartBreakout -> {
                    Button(
                        onClick = { handlers.handleStartBreakout() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Update Breakout")
                    }
                }
            }
            
            if (props.parameters.breakOutRoomStarted && !props.parameters.breakOutRoomEnded) {
                Button(
                    onClick = { handlers.handleStopBreakout() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Stop Breakout")
                }
            }
        }
    }
    
    // Edit Room Modal
    if (editRoomModalVisible) {
        EditRoomModal(
            visible = editRoomModalVisible,
            roomIndex = currentRoomIndex,
            currentRoom = currentRoom,
            participants = filteredParticipants,
            breakoutRooms = breakoutRooms,
            onDismiss = { editRoomModalVisible = false },
            onAddParticipant = { participant -> 
                handlers.handleAddParticipant(currentRoomIndex, participant)
                currentRoom = breakoutRooms[currentRoomIndex]
            },
            onRemoveParticipant = { participant -> 
                handlers.handleRemoveParticipant(currentRoomIndex, participant)
                currentRoom = breakoutRooms[currentRoomIndex]
            }
        )
    }
}

/**
 * Helper class to hold all breakout room handlers
 */
private class BreakoutHandlers(
    val handleRandomAssign: () -> Unit,
    val handleManualAssign: () -> Unit,
    val handleAddRoom: () -> Unit,
    val handleSaveRooms: () -> Unit,
    val handleDeleteRoom: (Int) -> Unit,
    val handleAddParticipant: (Int, BreakoutParticipant) -> Unit,
    val handleRemoveParticipant: (Int, BreakoutParticipant) -> Unit,
    val handleStartBreakout: () -> Unit,
    val handleStopBreakout: () -> Unit,
    val handleNewParticipantActionChange: (String) -> Unit
)

@Composable
private fun rememberBreakoutHandlers(
    props: BreakoutRoomsModalOptions,
    scope: CoroutineScope,
    filteredParticipants: List<Participant>,
    numRoomsText: String,
    breakoutRooms: List<List<BreakoutParticipant>>,
    newParticipantAction: String,
    onBreakoutRoomsChange: (List<List<BreakoutParticipant>>) -> Unit,
    onNewParticipantActionChange: (String) -> Unit,
    onCanStartBreakoutChange: (Boolean) -> Unit
): BreakoutHandlers {
    return remember(props, numRoomsText, breakoutRooms, newParticipantAction) {
        BreakoutHandlers(
            handleRandomAssign = {
                val numRooms = numRoomsText.toIntOrNull()
                if (numRooms == null || numRooms <= 0) {
                    props.parameters.showAlert?.invoke(
                        message = "Please enter a valid number of rooms.",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                if (numRooms > 10) {
                    props.parameters.showAlert?.invoke(
                        message = "Maximum 10 rooms allowed.",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                val newRooms = MutableList(numRooms) { mutableListOf<BreakoutParticipant>() }
                val shuffled = filteredParticipants.map { BreakoutParticipant(it.name) }.shuffled()
                shuffled.forEachIndexed { index, participant ->
                    val roomIndex = index % numRooms
                    newRooms[roomIndex].add(participant.copy(breakRoom = roomIndex))
                }
                
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
                
                props.parameters.showAlert?.invoke(
                    message = "Participants randomly assigned to $numRooms rooms.",
                    type = "success",
                    duration = 3000
                )
            },
            handleManualAssign = {
                val numRooms = numRoomsText.toIntOrNull()
                if (numRooms == null || numRooms <= 0) {
                    props.parameters.showAlert?.invoke(
                        message = "Please enter a valid number of rooms.",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                if (numRooms > 10) {
                    props.parameters.showAlert?.invoke(
                        message = "Maximum 10 rooms allowed.",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                val newRooms = MutableList(numRooms) { emptyList<BreakoutParticipant>() }
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
                
                props.parameters.showAlert?.invoke(
                    message = "$numRooms empty rooms created for manual assignment.",
                    type = "success",
                    duration = 3000
                )
            },
            handleAddRoom = {
                if (breakoutRooms.size >= 10) {
                    props.parameters.showAlert?.invoke(
                        message = "Maximum 10 rooms allowed.",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                val newRooms = breakoutRooms.toMutableList()
                newRooms.add(emptyList())
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
            },
            handleSaveRooms = {
                validateBreakoutRooms(
                    breakoutRooms = breakoutRooms,
                    itemPageLimit = props.parameters.itemPageLimit,
                    showAlert = props.parameters.showAlert,
                    onCanStart = { canStart -> 
                        if (canStart) {
                            props.parameters.showAlert?.invoke(
                                message = "Rooms saved successfully!",
                                type = "success",
                                duration = 3000
                            )
                        }
                        onCanStartBreakoutChange(canStart)
                        props.parameters.updateCanStartBreakout(canStart)
                    }
                )
            },
            handleDeleteRoom = { roomIndex ->
                val newRooms = breakoutRooms.toMutableList()
                newRooms.removeAt(roomIndex)
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
                
                props.parameters.showAlert?.invoke(
                    message = "Room deleted successfully.",
                    type = "success",
                    duration = 3000
                )
            },
            handleAddParticipant = { roomIndex, participant ->
                val room = breakoutRooms[roomIndex]
                if (room.size >= props.parameters.itemPageLimit) {
                    props.parameters.showAlert?.invoke(
                        message = "Room is at capacity (${props.parameters.itemPageLimit} max).",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                val newRooms = breakoutRooms.toMutableList()
                val newRoom = room.toMutableList()
                newRoom.add(participant.copy(breakRoom = roomIndex))
                newRooms[roomIndex] = newRoom
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
            },
            handleRemoveParticipant = { roomIndex, participant ->
                val newRooms = breakoutRooms.toMutableList()
                val newRoom = breakoutRooms[roomIndex].toMutableList()
                newRoom.removeAll { it.name == participant.name }
                newRooms[roomIndex] = newRoom
                onBreakoutRoomsChange(newRooms)
                props.parameters.updateBreakoutRooms(newRooms)
            },
            handleStartBreakout = {
                if (props.parameters.shareScreenStarted || props.parameters.shared) {
                    props.parameters.showAlert?.invoke(
                        message = "You cannot start breakout rooms while screen sharing is active",
                        type = "danger",
                        duration = 3000
                    )
                    return@BreakoutHandlers
                }
                
                val emitName = if (props.parameters.breakOutRoomStarted && !props.parameters.breakOutRoomEnded) {
                    "updateBreakout"
                } else {
                    "startBreakout"
                }
                
                val filteredBreakoutRoomsMap: List<List<Map<String, Any?>>> = breakoutRooms.map { room ->
                    room.map { participant ->
                        mapOf(
                            "name" to participant.name,
                            "breakRoom" to (participant.breakRoom ?: -1)
                        )
                    }
                }
                
                val payload = mapOf(
                    "breakoutRooms" to filteredBreakoutRoomsMap,
                    "newParticipantAction" to newParticipantAction,
                    "roomName" to props.parameters.roomName
                )
                
                scope.launch {
                    props.parameters.socket?.emitWithAck(
                        emitName,
                        payload,
                        callback = { ack ->
                            @Suppress("UNCHECKED_CAST")
                            val response = ack as? Map<String, Any?>
                            val success = response?.get("success") as? Boolean ?: false
                            if (success) {
                                props.parameters.showAlert?.invoke(
                                    message = "Breakout rooms active",
                                    type = "success",
                                    duration = 3000
                                )
                                props.parameters.updateBreakOutRoomStarted(true)
                                props.parameters.updateBreakOutRoomEnded(false)
                                props.parameters.updateMeetingDisplayType("all")
                                props.onBreakoutRoomsClose()
                            } else {
                                val reason = response?.get("reason") as? String ?: "Failed to start breakout rooms"
                                props.parameters.showAlert?.invoke(
                                    message = reason,
                                    type = "danger",
                                    duration = 3000
                                )
                            }
                        }
                    )
                    
                    props.parameters.localSocket?.let { localSocket ->
                        try {
                            localSocket.emitWithAck(emitName, payload, callback = { _ -> })
                        } catch (e: Exception) {
                            Logger.e("BreakoutRoomsModal", "Error starting local breakout rooms: ${e.message}")
                        }
                    }
                }
            },
            handleStopBreakout = {
                val payload = mapOf("roomName" to props.parameters.roomName)
                
                scope.launch {
                    props.parameters.socket?.emitWithAck(
                        "stopBreakout",
                        payload,
                        callback = { ack ->
                            @Suppress("UNCHECKED_CAST")
                            val response = ack as? Map<String, Any?>
                            val success = response?.get("success") as? Boolean ?: false
                            if (success) {
                                props.parameters.showAlert?.invoke(
                                    message = "Breakout rooms stopped",
                                    type = "success",
                                    duration = 3000
                                )
                                props.parameters.updateBreakOutRoomStarted(false)
                                props.parameters.updateBreakOutRoomEnded(true)
                                props.parameters.updateMeetingDisplayType(props.parameters.prevMeetingDisplayType)
                                props.onBreakoutRoomsClose()
                            } else {
                                val reason = response?.get("reason") as? String ?: "Failed to stop breakout rooms"
                                props.parameters.showAlert?.invoke(
                                    message = reason,
                                    type = "danger",
                                    duration = 3000
                                )
                            }
                        }
                    )
                    
                    props.parameters.localSocket?.let { localSocket ->
                        try {
                            localSocket.emitWithAck("stopBreakout", payload, callback = { _ -> })
                        } catch (e: Exception) {
                            Logger.e("BreakoutRoomsModal", "Error stopping local breakout rooms: ${e.message}")
                        }
                    }
                }
            },
            handleNewParticipantActionChange = { value ->
                onNewParticipantActionChange(value)
                props.parameters.updateNewParticipantAction(value)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultBreakoutRoomsModalContent(
    props: BreakoutRoomsModalOptions
) {
    // Wrap content body in AlertDialog for standalone modal usage
    AlertDialog(
        onDismissRequest = props.onBreakoutRoomsClose,
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .fillMaxHeight(0.7f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Breakout Rooms",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = props.onBreakoutRoomsClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            BreakoutRoomsModalContentBody(
                props = props,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun RoomCard(
    roomIndex: Int,
    room: List<BreakoutParticipant>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRemoveParticipant: (BreakoutParticipant) -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Room Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Room ${roomIndex + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Room",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Delete Room",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Participants
            if (room.isEmpty()) {
                Text(
                    text = "None Assigned",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.7f)
                )
            } else {
                room.forEach { participant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = participant.name,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        IconButton(
                            onClick = { onRemoveParticipant(participant) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            
            // Participant count
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${room.size} ${if (room.size == 1) "participant" else "participants"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRoomModal(
    visible: Boolean,
    roomIndex: Int,
    currentRoom: List<BreakoutParticipant>,
    participants: List<Participant>,
    breakoutRooms: List<List<BreakoutParticipant>>,
    onDismiss: () -> Unit,
    onAddParticipant: (BreakoutParticipant) -> Unit,
    onRemoveParticipant: (BreakoutParticipant) -> Unit
) {
    if (!visible) return
    
    // Get unassigned participants
    val assignedNames = breakoutRooms.flatten().map { it.name }.toSet()
    val unassignedParticipants = participants.filter { it.name !in assignedNames }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .fillMaxHeight(0.65f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Room ${roomIndex + 1}",
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Assigned Participants Section
                Text(
                    text = "Assigned Participants",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000).copy(alpha = 0.87f)
                )
                
                if (currentRoom.isEmpty()) {
                    Text(
                        text = "None Assigned",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    currentRoom.forEach { participant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(participant.name)
                            }
                            IconButton(
                                onClick = { onRemoveParticipant(participant) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                HorizontalDivider()
                
                // Unassigned Participants Section
                Text(
                    text = "Unassigned Participants",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF000000).copy(alpha = 0.87f)
                )
                
                if (unassignedParticipants.isEmpty()) {
                    Text(
                        text = "None Pending",
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    unassignedParticipants.forEach { participant ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(participant.name)
                            }
                            IconButton(
                                onClick = { 
                                    onAddParticipant(BreakoutParticipant(name = participant.name))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Add",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

