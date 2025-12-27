package com.mediasfu.sdk.ui.components.whiteboard
import com.mediasfu.sdk.util.Logger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.WhiteboardUser
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.launch

/**
 * Parameters for the ConfigureWhiteboardModal.
 */
interface ConfigureWhiteboardModalParameters {
    val participants: List<Participant>
    val showAlert: ((message: String, type: String, duration: Long) -> Unit)?
    val socket: SocketManager?
    val itemPageLimit: Int
    val islevel: String
    val roomName: String
    val eventType: String
    val shareScreenStarted: Boolean
    val shared: Boolean
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    val recordStarted: Boolean
    val recordResumed: Boolean
    val recordPaused: Boolean
    val recordStopped: Boolean
    val recordingMediaOptions: String
    val canStartWhiteboard: Boolean
    val whiteboardStarted: Boolean
    val whiteboardEnded: Boolean
    val hostLabel: String

    val updateWhiteboardStarted: (Boolean) -> Unit
    val updateWhiteboardEnded: (Boolean) -> Unit
    val updateWhiteboardUsers: (List<WhiteboardUser>) -> Unit
    val updateCanStartWhiteboard: (Boolean) -> Unit
    val updateIsConfigureWhiteboardModalVisible: (Boolean) -> Unit
    
    // Capture canvas stream for whiteboard recording when recording is active
    val captureCanvasStream: (suspend () -> Unit)?

    fun getUpdatedAllParams(): ConfigureWhiteboardModalParameters
}

/**
 * Options for ConfigureWhiteboardModal.
 */
data class ConfigureWhiteboardModalOptions(
    val isVisible: Boolean,
    val onClose: () -> Unit,
    val parameters: ConfigureWhiteboardModalParameters,
    val backgroundColor: Color = Color(0xFFF5F5F5)
)

/**
 * ConfigureWhiteboardModal - Modal for configuring and managing whiteboard sessions.
 *
 * This component provides an interface for host-controlled whiteboard management, including
 * participant selection, access control, and session lifecycle management.
 *
 * Features:
 * - Participant management with dual-list interface
 * - Session control (start, stop, update)
 * - Access validation for host permissions
 * - Screen share and recording compatibility checks
 *
 * Example:
 * ```kotlin
 * ConfigureWhiteboardModal(
 *     options = ConfigureWhiteboardModalOptions(
 *         isVisible = true,
 *         onClose = { isModalVisible = false },
 *         parameters = whiteboardParams
 *     )
 * )
 * ```
 */
@Composable
fun ConfigureWhiteboardModal(
    options: ConfigureWhiteboardModalOptions,
    modifier: Modifier = Modifier
) {
    if (!options.isVisible) return

    val params = options.parameters
    val scope = rememberCoroutineScope()

    // State for participant lists
    var assignedParticipants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var pendingParticipants by remember { mutableStateOf<List<Participant>>(emptyList()) }
    var isEditing by remember { mutableStateOf(false) }
    var canStartWhiteboard by remember { mutableStateOf(params.canStartWhiteboard) }

    // Initialize participants
    LaunchedEffect(options.isVisible) {
        if (options.isVisible) {
            // Get all non-host participants
            val allParticipants = params.participants.filter { it.islevel != "2" }

            // Separate into assigned and pending
            assignedParticipants = allParticipants.filter { it.useBoard == true }
            pendingParticipants = allParticipants.filter { it.useBoard != true }

            // Check if we can start whiteboard
            canStartWhiteboard = assignedParticipants.size <= params.itemPageLimit
            params.updateCanStartWhiteboard(canStartWhiteboard)
        }
    }

    // Check if we can start whiteboard
    fun checkCanStartWhiteboard() {
        val isValid = assignedParticipants.size <= params.itemPageLimit
        canStartWhiteboard = isValid
        params.updateCanStartWhiteboard(isValid)
    }

    // Add participant to assigned list
    fun addParticipant(participant: Participant) {
        pendingParticipants = pendingParticipants - participant
        assignedParticipants = assignedParticipants + participant
        isEditing = true
        checkCanStartWhiteboard()
    }

    // Remove participant from assigned list
    fun removeParticipant(participant: Participant) {
        assignedParticipants = assignedParticipants - participant
        pendingParticipants = pendingParticipants + participant
        isEditing = true
        checkCanStartWhiteboard()
    }

    fun buildWhiteboardPayload(
        usersData: List<Map<String, Any?>>, 
        roomName: String
    ): Map<String, Any?> = mapOf(
        "whiteboardUsers" to usersData,
        "roomName" to roomName
    )

    fun emitWhiteboardEvent(
        event: String,
        payload: Map<String, Any?>,
        onSuccess: () -> Unit,
        failureMessage: String
    ) {
        val socket = params.socket ?: run {
            scope.launch {
                params.showAlert?.invoke("Socket connection not available", "danger", 3000L)
            }
            return
        }

        try {
            socket.emitWithAck(event, payload) { response ->
                val (success, reason) = response.toAckResult()
                scope.launch {
                    if (success) {
                        onSuccess()
                    } else {
                        params.showAlert?.invoke(reason ?: failureMessage, "danger", 3000L)
                    }
                }
            }
        } catch (error: Exception) {
            scope.launch {
                params.showAlert?.invoke(error.message ?: failureMessage, "danger", 3000L)
            }
        }
    }

    // Save assignments
    fun saveAssignments() {
        if (assignedParticipants.size > params.itemPageLimit) {
            scope.launch {
                params.showAlert?.invoke("Participant limit exceeded", "danger", 3000L)
            }
            return
        }

        val whiteboardUsersData = assignedParticipants.map { participant ->
            mapOf("name" to participant.name, "useBoard" to true)
        }

        val updateLocalUsers = {
            params.updateWhiteboardUsers(
                assignedParticipants.map { participant ->
                    WhiteboardUser(name = participant.name, useBoard = true)
                }
            )
        }

        if (params.whiteboardStarted && !params.whiteboardEnded) {
            emitWhiteboardEvent(
                event = "updateWhiteboard",
                payload = buildWhiteboardPayload(whiteboardUsersData, params.roomName),
                onSuccess = {
                    updateLocalUsers()
                    params.showAlert?.invoke("Whiteboard users updated", "success", 3000L)
                },
                failureMessage = "Failed to update whiteboard users"
            )
        } else {
            updateLocalUsers()
            scope.launch {
                params.showAlert?.invoke("Whiteboard saved successfully", "success", 3000L)
            }
        }

        checkCanStartWhiteboard()
        isEditing = false
    }

    // Validate start whiteboard
    fun validateStartWhiteboard(): Boolean {
        // Check if screen sharing is active
        if (params.shareScreenStarted || params.shared) {
            params.showAlert?.invoke(
                "Cannot start whiteboard while screen sharing is active",
                "danger",
                3000L
            )
            return false
        }

        // Check if breakout rooms are active
        if (params.breakOutRoomStarted && !params.breakOutRoomEnded) {
            params.showAlert?.invoke(
                "Cannot start whiteboard while breakout rooms are active",
                "danger",
                3000L
            )
            return false
        }

        return true
    }

    // Start whiteboard
    fun startWhiteboard() {
        if (!validateStartWhiteboard()) return

        if (params.socket == null) {
            scope.launch {
                params.showAlert?.invoke("Socket connection not available", "danger", 3000L)
            }
            return
        }

        val whiteboardUsersData = assignedParticipants.map { participant ->
            mapOf("name" to participant.name, "useBoard" to true)
        }

        val emitName = if (params.whiteboardStarted && !params.whiteboardEnded) {
            "updateWhiteboard"
        } else {
            "startWhiteboard"
        }

        emitWhiteboardEvent(
            event = emitName,
            payload = buildWhiteboardPayload(whiteboardUsersData, params.roomName),
            onSuccess = {
                params.updateWhiteboardStarted(true)
                params.updateWhiteboardEnded(false)
                params.updateWhiteboardUsers(
                    assignedParticipants.map { participant ->
                        WhiteboardUser(name = participant.name, useBoard = true)
                    }
                )
                params.updateCanStartWhiteboard(false)
                params.showAlert?.invoke("Whiteboard active", "success", 3000L)
                options.onClose()
                
                // Capture canvas stream if recording is active and whiteboard just started
                // This is for when the whiteboard is started AFTER recording started
                
                if (params.islevel == "2" && (params.recordStarted || params.recordResumed)) {
                    if (!(params.recordPaused || params.recordStopped) && 
                        params.recordingMediaOptions == "video") {
                        scope.launch {
                            try {
                                params.captureCanvasStream?.invoke()
                            } catch (e: Exception) {
                                Logger.e("ConfigureWhiteboardM", "MediaSFU - ConfigureWhiteboard: Error starting canvas capture: ${e.message}")
                            }
                        }
                    } else {
                    }
                } else {
                }
            },
            failureMessage = "Failed to start whiteboard"
        )
    }

    // Stop whiteboard
    fun stopWhiteboard() {
        emitWhiteboardEvent(
            event = "stopWhiteboard",
            payload = mapOf("roomName" to params.roomName),
            onSuccess = {
                params.updateWhiteboardStarted(false)
                params.updateWhiteboardEnded(true)
                params.updateCanStartWhiteboard(true)
                params.showAlert?.invoke("Whiteboard stopped successfully", "success", 3000L)
                options.onClose()
            },
            failureMessage = "Failed to stop whiteboard"
        )
    }

    Dialog(
        onDismissRequest = options.onClose,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(12.dp),
            color = options.backgroundColor,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configure Whiteboard",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = options.onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Body - Two column layout
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Assigned participants
                    ParticipantList(
                        modifier = Modifier.weight(1f),
                        title = "Assigned",
                        participants = assignedParticipants,
                        onAction = { removeParticipant(it) },
                        actionIcon = Icons.Default.Remove,
                        actionColor = Color.Red,
                        emptyMessage = "No participants assigned"
                    )

                    // Pending participants
                    ParticipantList(
                        modifier = Modifier.weight(1f),
                        title = "Available",
                        participants = pendingParticipants,
                        onAction = { addParticipant(it) },
                        actionIcon = Icons.Default.Add,
                        actionColor = Color.Green,
                        emptyMessage = "No participants available"
                    )
                }

                HorizontalDivider()

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save button (only if editing)
                    if (isEditing) {
                        Button(
                            onClick = { saveAssignments() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2196F3)
                            )
                        ) {
                            Icon(
                                Icons.Default.Save,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Start button
                        if (canStartWhiteboard && (!params.whiteboardStarted || params.whiteboardEnded)) {
                            Button(
                                onClick = { startWhiteboard() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start")
                            }
                        }

                        // Update and Stop buttons (when whiteboard is running)
                        if (params.whiteboardStarted && !params.whiteboardEnded) {
                            Button(
                                onClick = { saveAssignments() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Update")
                            }

                            Button(
                                onClick = { stopWhiteboard() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF44336)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * ParticipantList - A reusable component for displaying a list of participants.
 */
@Composable
private fun ParticipantList(
    modifier: Modifier = Modifier,
    title: String,
    participants: List<Participant>,
    onAction: (Participant) -> Unit,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    actionColor: Color,
    emptyMessage: String
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        // Title bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF5F5F5))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .background(Color(0xFF2196F3), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${participants.size}",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // Participant list
        if (participants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyMessage,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(participants) { participant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = participant.name,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onAction(participant) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                actionIcon,
                                contentDescription = "Action",
                                tint = actionColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Any?.toAckResult(): Pair<Boolean, String?> = when (this) {
    is Map<*, *> -> {
        val success = (this["success"] as? Boolean) ?: false
        val reason = this["reason"] as? String
        success to reason
    }
    is Array<*> -> this.firstOrNull().toAckResult()
    is List<*> -> this.firstOrNull().toAckResult()
    is Boolean -> this to null
    is String -> when (this.lowercase()) {
        "true" -> true to null
        "false" -> false to null
        else -> false to this
    }
    else -> false to null
}
