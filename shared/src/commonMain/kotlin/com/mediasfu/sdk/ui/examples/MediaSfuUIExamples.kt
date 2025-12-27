package com.mediasfu.sdk.ui.examples

import kotlinx.datetime.Clock
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.*
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.webrtc.MediaStream

/**
 * MediaSFU UI Examples - Comprehensive examples showing how to use the MediaSFU UI components.
 * 
 * This file provides practical examples of how to create and use various UI components
 * in the MediaSFU Kotlin Multiplatform SDK.
 */

/**
 * Example 1: Basic Video Call Interface
 * 
 * This example shows how to create a basic video call interface with:
 * - Video grid layout
 * - Control buttons
 * - Meeting timer
 * - Participant information
 */
class BasicVideoCallExample {
    
    fun createVideoCallInterface(): MediaSfuUIComponent {
        // Create a grid for video layout
        val videoGrid = MediaSfuUIFactory.createGrid(
            GridOptions(
                columns = 2,
                rows = 2,
                spacing = 8f,
                style = ComponentStyle(
                    backgroundColor = Color(0.1f, 0.1f, 0.1f),
                    padding = EdgeInsets.all(16f)
                )
            )
        )
        
        // Create control buttons
        val controlButtons = MediaSfuUIFactory.createControlButtons(
            ControlButtonsOptions(
                buttons = listOf(
                    ControlButtonOptions(
                        id = "mute_audio",
                        text = "Mute",
                        icon = "mic",
                        alternateIcon = "mic_off",
                        active = false,
                        onClick = { toggleAudio() }
                    ),
                    ControlButtonOptions(
                        id = "toggle_video",
                        text = "Video",
                        icon = "videocam",
                        alternateIcon = "videocam_off",
                        active = true,
                        onClick = { toggleVideo() }
                    ),
                    ControlButtonOptions(
                        id = "screen_share",
                        text = "Share",
                        icon = "screen_share",
                        active = false,
                        onClick = { toggleScreenShare() }
                    ),
                    ControlButtonOptions(
                        id = "end_call",
                        text = "End",
                        icon = "call_end",
                        backgroundColor = Color.Red,
                        onClick = { endCall() }
                    )
                ),
                layoutDirection = LayoutDirection.Horizontal,
                alignment = Alignment.Center,
                style = ComponentStyle(
                    backgroundColor = Color(0.2f, 0.2f, 0.2f, 0.8f),
                    padding = EdgeInsets.all(16f),
                    borderRadius = 25f
                )
            )
        )
        
        // Create meeting timer
        val meetingTimer = MeetingProgressTimer(
            MeetingProgressTimerOptions(
                isRunning = true,
                timeFormat = TimeFormat.HHMMSS,
                style = ComponentStyle(
                    textColor = Color.White,
                    fontSize = 18f,
                    fontWeight = FontWeight.Bold,
                    backgroundColor = Color(0f, 0f, 0f, 0.5f),
                    padding = EdgeInsets.symmetric(horizontal = 12f, vertical = 6f),
                    borderRadius = 15f
                )
            )
        )
        
        // Create a container to hold all components
        return createContainer(
            children = listOf(videoGrid, controlButtons, meetingTimer),
            style = ComponentStyle(
                backgroundColor = Color.Black,
                padding = EdgeInsets.zero
            )
        )
    }
    
    private fun toggleAudio() {
        // Implement audio toggle logic
    }
    
    private fun toggleVideo() {
        // Implement video toggle logic
    }
    
    private fun toggleScreenShare() {
        // Implement screen share toggle logic
    }
    
    private fun endCall() {
        // Implement end call logic
    }
}

/**
 * Example 2: Video Card with Participant Information
 * 
 * This example shows how to create a video card with:
 * - Video stream display
 * - Participant name and status
 * - Audio waveform visualization
 * - Control buttons
 */
class VideoCardExample {
    
    fun createVideoCard(participant: Participant, videoStream: MediaStream?): VideoCard {
        return MediaSfuUIFactory.createVideoCard(
            VideoCardOptions(
                participant = participant,
                videoStream = videoStream,
                eventType = EventType.CONFERENCE,
                style = ComponentStyle(
                    backgroundColor = Color(0.17f, 0.4f, 0.56f), // #2c678f
                    borderRadius = 8f,
                    borderWidth = 2f,
                    borderColor = Color.Black
                ),
                showControls = true,
                showInfo = true,
                showWaveform = true,
                controlsPosition = ControlPosition.BottomLeft,
                infoPosition = ControlPosition.TopLeft,
                textColor = Color.White,
                barColor = Color(0.91f, 0.18f, 0.18f), // #e82e2e
                onClick = { onVideoCardClick(participant) },
                onLongPress = { onVideoCardLongPress(participant) },
                onAudioToggle = { muted -> onAudioToggle(participant, muted) },
                onVideoToggle = { videoOn -> onVideoToggle(participant, videoOn) }
            )
        )
    }
    
    private fun onVideoCardClick(participant: Participant) {
    }
    
    private fun onVideoCardLongPress(participant: Participant) {
    }
    
    private fun onAudioToggle(participant: Participant, muted: Boolean) {
    }
    
    private fun onVideoToggle(participant: Participant, videoOn: Boolean) {
    }
}

/**
 * Example 3: Modal Dialog for Settings
 * 
 * This example shows how to create a modal dialog with:
 * - Settings options
 * - Form controls
 * - Action buttons
 * - Responsive layout
 */
class SettingsModalExample {
    
    fun createSettingsModal(): Modal {
        // Create settings content
        val settingsContent = createSettingsContent()
        
        return MediaSfuUIFactory.createModal(
            ModalOptions(
                id = "settings_modal",
                content = settingsContent,
                isOpen = false,
                size = ModalSize.Medium,
                position = ModalPosition.Center,
                dismissible = true,
                showBackdrop = true,
                backdropColor = Color(0f, 0f, 0f, 0.5f),
                animateOnOpen = true,
                animateOnClose = true,
                animationDuration = 300L,
                style = ComponentStyle(
                    backgroundColor = Color.White,
                    borderRadius = 12f,
                    padding = EdgeInsets.all(24f),
                    shadow = Shadow(
                        color = Color(0f, 0f, 0f, 0.3f),
                        offsetX = 0f,
                        offsetY = 4f,
                        blurRadius = 12f
                    )
                ),
            )
        )
    }
    
    private fun createSettingsContent(): MediaSfuUIComponent {
        // Create title
        val title = MediaSfuUIFactory.createText(
            TextOptions(
                text = "Meeting Settings",
                style = ComponentStyle(
                    fontSize = 24f,
                    fontWeight = FontWeight.Bold,
                    textColor = Color.Black,
                    margin = EdgeInsets.only(bottom = 16f)
                )
            )
        )
        
        // Create audio settings section
        val audioSettings = createAudioSettingsSection()
        
        // Create video settings section
        val videoSettings = createVideoSettingsSection()
        
        // Create action buttons
        val actionButtons = createActionButtons()
        
        // Create container to hold all settings
        return createContainer(
            children = listOf(title, audioSettings, videoSettings, actionButtons),
            style = ComponentStyle(
                backgroundColor = Color.Transparent,
                padding = EdgeInsets.zero
            )
        )
    }
    
    private fun createAudioSettingsSection(): MediaSfuUIComponent {
        val sectionTitle = MediaSfuUIFactory.createText(
            TextOptions(
                text = "Audio Settings",
                style = ComponentStyle(
                    fontSize = 18f,
                    fontWeight = FontWeight.SemiBold,
                    textColor = Color.Black,
                    margin = EdgeInsets.only(bottom = 8f)
                )
            )
        )
        
        val muteButton = MediaSfuUIFactory.createButton(
            ButtonOptions(
                id = "mute_button",
                text = "Mute Audio",
                icon = "mic_off",
                buttonType = ButtonType.Secondary,
                onClick = { toggleMute() }
            )
        )
        
        return createContainer(
            children = listOf(sectionTitle, muteButton),
            style = ComponentStyle(
                backgroundColor = Color.Transparent,
                padding = EdgeInsets.only(bottom = 16f)
            )
        )
    }
    
    private fun createVideoSettingsSection(): MediaSfuUIComponent {
        val sectionTitle = MediaSfuUIFactory.createText(
            TextOptions(
                text = "Video Settings",
                style = ComponentStyle(
                    fontSize = 18f,
                    fontWeight = FontWeight.SemiBold,
                    textColor = Color.Black,
                    margin = EdgeInsets.only(bottom = 8f)
                )
            )
        )
        
        val videoOffButton = MediaSfuUIFactory.createButton(
            ButtonOptions(
                id = "video_off_button",
                text = "Turn Off Video",
                icon = "videocam_off",
                buttonType = ButtonType.Secondary,
                onClick = { toggleVideo() }
            )
        )
        
        return createContainer(
            children = listOf(sectionTitle, videoOffButton),
            style = ComponentStyle(
                backgroundColor = Color.Transparent,
                padding = EdgeInsets.only(bottom = 16f)
            )
        )
    }
    
    private fun createActionButtons(): MediaSfuUIComponent {
        val saveButton = MediaSfuUIFactory.createButton(
            ButtonOptions(
                id = "save_button",
                text = "Save",
                buttonType = ButtonType.Primary,
                onClick = { saveSettings() }
            )
        )
        
        val cancelButton = MediaSfuUIFactory.createButton(
            ButtonOptions(
                id = "cancel_button",
                text = "Cancel",
                buttonType = ButtonType.Secondary,
                onClick = { cancelSettings() }
            )
        )
        
        return MediaSfuUIFactory.createControlButtons(
            ControlButtonsOptions(
                buttons = listOf(
                    ControlButtonOptions(
                        id = "save",
                        text = "Save",
                        onClick = { saveSettings() }
                    ),
                    ControlButtonOptions(
                        id = "cancel",
                        text = "Cancel",
                        onClick = { cancelSettings() }
                    )
                ),
                layoutDirection = LayoutDirection.Horizontal,
                alignment = Alignment.End,
                spacing = 12f
            )
        )
    }
    
    private fun toggleMute() {
    }
    
    private fun toggleVideo() {
    }
    
    private fun saveSettings() {
    }
    
    private fun cancelSettings() {
    }
}

/**
 * Example 4: Participant List with Controls
 * 
 * This example shows how to create a participant list with:
 * - Participant information
 * - Control buttons for each participant
 * - Search functionality
 * - Responsive layout
 */
class ParticipantListExample {
    
    fun createParticipantList(participants: List<Participant>): MediaSfuUIComponent {
        // Create title
        val title = MediaSfuUIFactory.createText(
            TextOptions(
                text = "Participants (${participants.size})",
                style = ComponentStyle(
                    fontSize = 20f,
                    fontWeight = FontWeight.Bold,
                    textColor = Color.Black,
                    margin = EdgeInsets.only(bottom = 16f)
                )
            )
        )
        
        // Create participant items
        val participantItems = participants.map { participant ->
            createParticipantItem(participant)
        }
        
        // Create container for all participants
        val participantsContainer = createContainer(
            children = participantItems,
            style = ComponentStyle(
                backgroundColor = Color.Transparent,
                padding = EdgeInsets.zero
            )
        )
        
        // Create main container
        return createContainer(
            children = listOf(title, participantsContainer),
            style = ComponentStyle(
                backgroundColor = Color.White,
                padding = EdgeInsets.all(16f),
                borderRadius = 8f
            )
        )
    }
    
    private fun createParticipantItem(participant: Participant): MediaSfuUIComponent {
        // Create participant name
        val nameText = MediaSfuUIFactory.createText(
            TextOptions(
                text = participant.name,
                style = ComponentStyle(
                    fontSize = 16f,
                    fontWeight = FontWeight.Medium,
                    textColor = Color.Black
                )
            )
        )
        
        // Create participant status
        val statusText = MediaSfuUIFactory.createText(
            TextOptions(
                text = getParticipantStatus(participant),
                style = ComponentStyle(
                    fontSize = 14f,
                    textColor = Color.Gray
                )
            )
        )
        
        // Create control buttons
        val controlButtons = createParticipantControls(participant)
        
        // Create participant info container
        val infoContainer = createContainer(
            children = listOf(nameText, statusText),
            style = ComponentStyle(
                backgroundColor = Color.Transparent,
                padding = EdgeInsets.zero
            )
        )
        
        // Create main participant item container
        return createContainer(
            children = listOf(infoContainer, controlButtons),
            style = ComponentStyle(
                backgroundColor = Color(0.95f, 0.95f, 0.95f),
                padding = EdgeInsets.all(12f),
                margin = EdgeInsets.only(bottom = 8f),
                borderRadius = 6f
            )
        )
    }
    
    private fun createParticipantControls(participant: Participant): MediaSfuUIComponent {
        val muteButton = ControlButtonOptions(
            id = "mute_${participant.id}",
            icon = if (participant.muted == true) "mic_off" else "mic",
            textColor = if (participant.muted == true) Color.Red else Color.Green,
            onClick = { muteParticipant(participant) }
        )
        
        val removeButton = ControlButtonOptions(
            id = "remove_${participant.id}",
            icon = "person_remove",
            textColor = Color.Red,
            onClick = { removeParticipant(participant) }
        )
        
        return MediaSfuUIFactory.createControlButtons(
            ControlButtonsOptions(
                buttons = listOf(muteButton, removeButton),
                layoutDirection = LayoutDirection.Horizontal,
                alignment = Alignment.End,
                spacing = 8f
            )
        )
    }
    
    private fun getParticipantStatus(participant: Participant): String {
        return when {
            participant.muted == true -> "Muted"
            participant.videoOn == false -> "Video Off"
            else -> "Active"
        }
    }
    
    private fun muteParticipant(participant: Participant) {
    }
    
    private fun removeParticipant(participant: Participant) {
    }
}

/**
 * Helper function to create a container component.
 * This is a utility function to group multiple UI components together.
 */
private fun createContainer(
    children: List<MediaSfuUIComponent>,
    style: ComponentStyle
): MediaSfuUIComponent {
    // This would be implemented as a proper Container component
    // For now, we'll return a placeholder
    return object : BaseMediaSfuUIComponent("container_${Clock.System.now().toEpochMilliseconds()}") {
        override fun onDispose() {
            children.forEach { it.dispose() }
        }
    }
}
