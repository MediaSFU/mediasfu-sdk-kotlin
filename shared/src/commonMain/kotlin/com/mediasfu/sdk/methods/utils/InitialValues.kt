package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.socket.ResponseJoinRoom

/**
 * Initial values for the MediaSFU SDK state.
 * 
 * This object contains all the default values used to initialize the SDK state,
 * including room configuration, participant management, media settings, and UI state.
 * 
 * ### Usage:
 * ```kotlin
 * val initialState = InitialValues.getInitialState()
 * ```
 * 
 * ### Categories:
 * - **Socket & Device**: Connection and device information
 * - **Room Details**: Room configuration and access control
 * - **Participants**: Participant management and filtering
 * - **Media Settings**: Audio/video configuration
 * - **UI State**: Interface state and display settings
 * - **Recording**: Recording configuration and state
 * - **Timers**: Meeting and recording timers
 * - **Breakout Rooms**: Breakout room configuration
 * - **Co-Host**: Co-host permissions and responsibilities
 */
object InitialValues {
    
    /**
     * Gets the initial state map with all default values.
     * 
     * @return Map containing all initial state values
     */
    fun getInitialState(): Map<String, Any?> {
        return buildMap {
            // Socket and device information
            put("socket", null)
            put("localSocket", null)
            put("roomData", ResponseJoinRoom())
            put("device", null)
            put("apiKey", "")
            put("apiUserName", "")
            put("apiToken", "")
            put("link", "")
            
            // Room Details
            put("roomName", "")
            put("member", "")
            put("adminPasscode", "")
            put("islevel", "0")
            put("coHost", "No coHost")
            put("coHostResponsibility", listOf(
                CoHostResponsibility(name = "participants", value = false, dedicated = false),
                CoHostResponsibility(name = "media", value = false, dedicated = false),
                CoHostResponsibility(name = "waiting", value = false, dedicated = false),
                CoHostResponsibility(name = "chat", value = false, dedicated = false)
            ))
            put("youAreCoHost", false)
            put("youAreHost", false)
            put("confirmedToRecord", false)
            put("meetingDisplayType", "media")
            put("meetingVideoOptimized", false)
            put("eventType", EventType.WEBINAR)
            
            // Participants
            put("participants", emptyList<Participant>())
            put("filteredParticipants", emptyList<Participant>())
            put("participantsCounter", 0)
            put("participantsFilter", "")
            put("consumeSockets", emptyList<Map<String, Any?>>())
            put("rtpCapabilities", null)
            put("roomRecvIPs", emptyList<String>())
            put("meetingRoomParams", null)
            
            // Pagination and Display
            put("itemPageLimit", 4)
            put("audioOnlyRoom", false)
            put("addForBasic", false)
            put("screenPageLimit", 4)
            put("shareScreenStarted", false)
            put("shared", false)
            put("targetOrientation", "landscape")
            
            // Media Streams
            put("localStream", null)
            put("localStreamAudio", null)
            put("localStreamVideo", null)
            put("virtualStream", null)
            put("allVideoStreams", emptyList<Stream>())
            put("allAudioStreams", emptyList<Stream>())
            put("lStreams", emptyList<Stream>())
            put("oldAllStreams", emptyList<Stream>())
            put("streamNames", emptyList<Stream>())
            put("audStreamNames", emptyList<Stream>())
            put("chatRefStreams", emptyList<Stream>())
            
            // Transport and Producer
            put("sendTransport", null)
            put("recvTransport", null)
            put("audioProducer", null)
            put("videoProducer", null)
            put("screenProducer", null)
            put("localAudioProducer", null)
            put("localVideoProducer", null)
            put("localScreenProducer", null)
            put("consumerTransports", emptyList<Map<String, Any?>>())
            
            // Audio/Video Settings
            put("audioParams", null)
            put("videoParams", null)
            put("screenParams", null)
            put("audioPaused", false)
            put("videoPaused", false)
            put("screenPaused", false)
            put("audioAlreadyOn", false)
            put("videoAlreadyOn", false)
            put("screenAlreadyOn", false)
            put("transportCreated", false)
            put("localTransportCreated", false)
            
            // Device Settings
            put("defAudioID", "")
            put("defVideoID", "")
            put("userDefaultAudioInputDevice", "")
            put("userDefaultVideoInputDevice", "")
            put("currentFacingMode", "user")
            put("prevFacingMode", "user")
            put("videoSwitching", false)
            put("audioSwitching", false)
            
            // UI State
            put("updateMainWindow", false)
            put("showMiniView", false)
            put("mainScreenFilled", false)
            put("mainHeightWidth", 0.0)
            put("prevMainHeightWidth", 0.0)
            put("hostLabel", "")
            put("lockScreen", false)
            put("keepBackground", false)
            put("firstRound", false)
            put("firstAll", false)
            put("doPaginate", false)
            put("prevDoPaginate", false)
            put("currentUserPage", 0)
            put("nForReadjustRecord", 0)
            
            // Active Names and States
            put("activeNames", emptyList<String>())
            put("dispActiveNames", emptyList<String>())
            put("pDispActiveNames", emptyList<String>())
            put("pActiveNames", emptyList<String>())
            
            // Recording
            put("recordingDisplayType", "media")
            put("recordingVideoOptimized", false)
            put("recordingStarted", false)
            put("recordingPaused", false)
            put("recordingResumed", false)
            put("recordingStopped", false)
            put("recordingTimer", 0)
            put("recordingPauseTimer", 0)
            put("recordingResumeTimer", 0)
            put("recordingStartTimer", 0)
            put("recordingUpdateTimer", 0)
            
            // Meeting Timer
            put("meetingTimer", 0)
            put("meetingStartTime", null)
            put("meetingEndTime", null)
            put("meetingDuration", 0)
            
            // Breakout Rooms
            put("breakoutRooms", emptyList<List<BreakoutParticipant>>())
            put("breakOutRoomStarted", false)
            put("breakOutRoomEnded", false)
            put("hostNewRoom", 0)
            put("inBreakRoom", false)
            put("breakRoom", "")
            
            // Co-Host Permissions
            put("coHostCanMute", false)
            put("coHostCanRemove", false)
            put("coHostCanRecord", false)
            put("coHostCanShare", false)
            put("coHostCanChat", false)
            
            // Messages and Chat
            put("messages", emptyList<Message>())
            put("chatEnabled", true)
            put("chatVisible", false)
            put("newMessageCount", 0)
            
            // Polls
            put("polls", emptyList<Poll>())
            put("currentPoll", null)
            put("pollVisible", false)
            put("pollVotes", emptyList<Map<String, Any?>>())
            
            // Requests
            put("requests", emptyList<Request>())
            put("requestsVisible", false)
            put("newRequestCount", 0)
            
            // Waiting Room
            put("waitingRoomMembers", emptyList<Participant>())
            put("waitingRoomVisible", false)
            put("inWaitingRoom", false)
            
            // Settings
            put("settingsVisible", false)
            put("displaySettingsVisible", false)
            put("mediaSettingsVisible", false)
            put("menuModalVisible", false)
            
            // Exit
            put("exitConfirmVisible", false)
            put("exitConfirmed", false)
            
            // Permissions
            put("hasCameraPermission", false)
            put("hasMicrophonePermission", false)
            put("hasScreenSharePermission", false)
            put("checkMediaPermission", true)
            
            // Audio Monitoring
            put("audioDecibels", emptyList<AudioDecibels>())
            put("sortAudioLoudness", false)
            put("audioLevelMonitoring", false)
            
            // Grid and Layout
            put("gridRows", 2)
            put("gridColumns", 2)
            put("gridAutoAdjust", true)
            put("miniCardsVisible", false)
            put("flexibleGrid", true)
            
            // Screen Share
            put("screenShareRequested", false)
            put("screenShareApproved", false)
            put("screenShareRejected", false)
            put("screenShareEnded", false)
            put("screenId", "")
            put("screenProducerId", "")
            
            // Error Handling
            put("lastError", null)
            put("errorCount", 0)
            put("retryCount", 0)
            put("maxRetries", 3)
            
            // Debug and Logging
            put("debugMode", false)
            put("logLevel", "INFO")
            put("performanceMetrics", emptyMap<String, Any>())
        }
    }
    
    /**
     * Gets initial values for a specific category.
     * 
     * @param category The category of initial values to retrieve
     * @return Map containing initial values for the specified category
     */
    fun getInitialValuesForCategory(category: String): Map<String, Any?> {
        val allValues = getInitialState()
        return when (category.lowercase()) {
            "socket" -> allValues.filterKeys { 
                it in listOf("socket", "localSocket", "roomData", "device", "apiKey", "apiUserName", "apiToken", "link")
            }
            "room" -> allValues.filterKeys { 
                it in listOf("roomName", "member", "adminPasscode", "islevel", "coHost", "coHostResponsibility", "youAreCoHost", "youAreHost")
            }
            "participants" -> allValues.filterKeys { 
                it in listOf("participants", "filteredParticipants", "participantsCounter", "participantsFilter", "consumeSockets")
            }
            "media" -> allValues.filterKeys { 
                it in listOf("localStream", "localStreamAudio", "localStreamVideo", "virtualStream", "allVideoStreams", "allAudioStreams")
            }
            "ui" -> allValues.filterKeys { 
                it in listOf("updateMainWindow", "showMiniView", "mainScreenFilled", "mainHeightWidth", "hostLabel", "lockScreen")
            }
            "recording" -> allValues.filterKeys { 
                it in listOf("recordingDisplayType", "recordingVideoOptimized", "recordingStarted", "recordingPaused", "recordingTimer")
            }
            else -> emptyMap()
        }
    }
    
    /**
     * Gets a specific initial value by key.
     * 
     * @param key The key to retrieve
     * @return The initial value for the key, or null if not found
     */
    fun getInitialValue(key: String): Any? {
        return getInitialState()[key]
    }
    
    /**
     * Checks if a key exists in the initial values.
     * 
     * @param key The key to check
     * @return True if the key exists, false otherwise
     */
    fun hasInitialValue(key: String): Boolean {
        return getInitialState().containsKey(key)
    }
}