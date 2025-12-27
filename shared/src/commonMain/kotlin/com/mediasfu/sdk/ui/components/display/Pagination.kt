package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.consumers.GeneratePageContentOptions
import com.mediasfu.sdk.consumers.GeneratePageContentParameters
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.launch

/**
 * Simplified breakout participant representation for pagination.
 * Only needs name field to check room membership.
 */
interface BreakoutParticipantLike {
    val name: String
}

/**
 * Extended parameters interface for Pagination with breakout room support.
 * Matches Flutter's PaginationParameters.
 */
interface PaginationParameters : GeneratePageContentParameters {
    val mainRoomsLength: Int
    val memberRoom: Int
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    override val member: String
    val breakoutRoomsCount: Int  // Simplified: just count of rooms
    fun getBreakoutRoomNames(roomIndex: Int): List<String>  // Get participant names in a room
    val hostNewRoom: Int
    val roomName: String
    override val islevel: String
    val showAlert: ((message: String, type: String, duration: Int) -> Unit)?
    val socket: SocketManager?
}

/**
 * Pagination - Displays pagination controls for navigating through pages.
 *
 * Provides page navigation with support for breakout rooms, access control,
 * and proper room indicators. Matches Flutter/React SDK implementations.
 *
 * @property options Configuration options for pagination
 */
data class PaginationOptions(
    val totalPages: Int,
    val currentUserPage: Int,
    val handlePageChange: suspend (GeneratePageContentOptions) -> Unit,
    val parameters: PaginationParameters,
    val position: String = "middle",  // left, middle, right
    val location: String = "top",     // top, middle, bottom - DEFAULT is now TOP
    val direction: String = "horizontal",
    val showAspect: Boolean = true,
    val backgroundColor: Int = 0xFFFFFFFF.toInt(), // White matching Flutter default
    val activeColor: Int = 0xFF2c678f.toInt(), // Blue matching Flutter
    val inactiveColor: Int = 0xFFFFFFFF.toInt(),
    val paginationHeight: Int = 40
)

interface Pagination : MediaSfuUIComponent {
    val options: PaginationOptions
    override val id: String get() = "pagination"
    override val isVisible: Boolean get() = options.showAspect
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /**
     * Handles clicking on a page with full breakout room logic.
     * Mirrors Flutter/React SDK's handleClick implementation.
     */
    suspend fun handleClick(page: Int) {
        if (page == options.currentUserPage) return
        
        val params = options.parameters
        val mainRoomsLength = params.mainRoomsLength
        val breakOutRoomStarted = params.breakOutRoomStarted
        val breakOutRoomEnded = params.breakOutRoomEnded
        val member = params.member
        val hostNewRoom = params.hostNewRoom
        val roomName = params.roomName
        val islevel = params.islevel
        val showAlert = params.showAlert
        val socket = params.socket
        
        if (breakOutRoomStarted && !breakOutRoomEnded && page != 0) {
            // Find which breakout room the member belongs to
            val pageInt = page - mainRoomsLength
            var memberBreakRoom = -1
            
            // Check each breakout room to find the member
            val breakoutRoomsCount = params.breakoutRoomsCount
            for (roomIdx in 0 until breakoutRoomsCount) {
                val participantNames = params.getBreakoutRoomNames(roomIdx)
                if (participantNames.contains(member)) {
                    memberBreakRoom = roomIdx
                    break
                }
            }
            
            if ((memberBreakRoom == -1 || memberBreakRoom != pageInt) && pageInt >= 0) {
                // User trying to access a room they don't belong to
                if (islevel != "2") {
                    // Non-host: deny access
                    showAlert?.invoke(
                        "You are not part of the breakout room ${pageInt + 1}.",
                        "danger",
                        3000
                    )
                    return
                }
                
                // Host: allow access and emit socket event
                options.handlePageChange(
                    GeneratePageContentOptions(
                        page = page,
                        parameters = params,
                        breakRoom = pageInt,
                        inBreakRoom = true
                    )
                )
                
                if (hostNewRoom != pageInt) {
                    socket?.emitWithAck(
                        "updateHostBreakout",
                        mapOf("newRoom" to pageInt, "roomName" to roomName)
                    ) { _ -> /* Acknowledgment received */ }
                }
            } else {
                // User accessing their own room or returning to main
                options.handlePageChange(
                    GeneratePageContentOptions(
                        page = page,
                        parameters = params,
                        breakRoom = pageInt,
                        inBreakRoom = pageInt >= 0
                    )
                )
                
                if (islevel == "2" && hostNewRoom != -1) {
                    socket?.emitWithAck(
                        "updateHostBreakout",
                        mapOf("prevRoom" to hostNewRoom, "newRoom" to -1, "roomName" to roomName)
                    ) { _ -> /* Acknowledgment received */ }
                }
            }
        } else {
            // Not in breakout session - regular page navigation
            options.handlePageChange(
                GeneratePageContentOptions(
                    page = page,
                    parameters = params,
                    breakRoom = 0,
                    inBreakRoom = false
                )
            )
            
            if (islevel == "2" && hostNewRoom != -1) {
                socket?.emitWithAck(
                    "updateHostBreakout",
                    mapOf("prevRoom" to hostNewRoom, "newRoom" to -1, "roomName" to roomName)
                ) { _ -> /* Acknowledgment received */ }
            }
        }
    }
    
    /**
     * Gets the display label for a page.
     * Returns room number for breakout rooms, with lock indicator if user not member.
     * Returns the icon to use, label text, and whether locked.
     */
    fun getPageLabel(page: Int): Triple<ImageVector?, String, Boolean> {
        val params = options.parameters
        val mainRoomsLength = params.mainRoomsLength
        val memberRoom = params.memberRoom
        val breakOutRoomStarted = params.breakOutRoomStarted
        val breakOutRoomEnded = params.breakOutRoomEnded
        val islevel = params.islevel
        
        if (breakOutRoomStarted && !breakOutRoomEnded && page >= mainRoomsLength) {
            val roomNumber = page - (mainRoomsLength - 1)
            val userRoomNumber = memberRoom + 1
            val isUserRoom = userRoomNumber == roomNumber
            val isHost = islevel == "2"
            val isLocked = !isUserRoom && !isHost
            
            // Choose icon based on room status (like Flutter modern_pagination)
            val icon = when {
                isUserRoom -> Icons.Default.MeetingRoom  // User's own breakout room
                isLocked -> Icons.Default.Lock            // Locked room
                else -> Icons.Default.Groups              // Other accessible rooms
            }
            
            return Triple(icon, roomNumber.toString(), isLocked)
        }
        
        return Triple(null, page.toString(), false)
    }
}

/**
 * Default implementation of Pagination
 */
class DefaultPagination(
    override val options: PaginationOptions
) : Pagination

/**
 * Composable extension for rendering Pagination in Jetpack Compose
 * 
 * Renders pagination at TOP of grid with proper breakout room support.
 * Shows icons for breakout rooms matching Flutter modern_pagination.
 */
@Composable
fun Pagination.renderCompose() {
    if (!options.showAspect) return
    
    val scope = rememberCoroutineScope()
    
    // Color scheme matching Flutter SDK
    val primaryColor = Color(0xFF2c678f)
    val activeTextColor = Color.White
    val inactiveTextColor = Color.Black
    val lockedColor = Color(0xFFEF4444) // Red for locked rooms
    val userRoomColor = Color(0xFF10B981) // Green for user's room
    val groupsColor = Color(0xFF6366F1)   // Indigo for accessible rooms
    
    // Generate pages list: 0 to totalPages inclusive
    val pages = (0..options.totalPages).toList()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(options.paginationHeight.dp)
            .background(Color(options.backgroundColor)),
        contentAlignment = when (options.position) {
            "left" -> Alignment.CenterStart
            "right" -> Alignment.CenterEnd
            else -> Alignment.Center
        }
    ) {
        Row(
            horizontalArrangement = when (options.position) {
                "left" -> Arrangement.Start
                "right" -> Arrangement.End
                else -> Arrangement.Center
            },
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            pages.forEach { page ->
                val isActive = page == options.currentUserPage
                val isHomePage = page == 0
                val (icon, label, isLocked) = getPageLabel(page)
                val isBreakoutRoom = icon != null
                
                val buttonBackground = if (isActive) primaryColor else Color.Transparent
                val textColor = if (isActive) activeTextColor else inactiveTextColor
                
                // Determine icon color for breakout rooms
                val iconTint = when {
                    isActive -> activeTextColor
                    isLocked -> lockedColor
                    icon == Icons.Default.MeetingRoom -> userRoomColor
                    icon == Icons.Default.Groups -> groupsColor
                    else -> inactiveTextColor
                }
                
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(buttonBackground)
                        .then(
                            if (!isActive) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.Black,
                                    shape = RoundedCornerShape(4.dp)
                                )
                            } else Modifier
                        )
                        .clickable(enabled = !isActive && !isLocked) {
                            scope.launch { handleClick(page) }
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isHomePage) {
                        // Home icon for page 0
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(18.dp),
                            tint = if (isActive) Color.Yellow else Color.Gray
                        )
                    } else if (isBreakoutRoom && icon != null) {
                        // Breakout room: Icon + room number (like Flutter modern_pagination)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = when (icon) {
                                    Icons.Default.MeetingRoom -> "Your Room"
                                    Icons.Default.Lock -> "Locked Room"
                                    Icons.Default.Groups -> "Breakout Room"
                                    else -> "Room"
                                },
                                modifier = Modifier.size(16.dp),
                                tint = iconTint
                            )
                            Text(
                                text = label,
                                color = textColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        // Regular page number
                        Text(
                            text = label,
                            color = textColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
