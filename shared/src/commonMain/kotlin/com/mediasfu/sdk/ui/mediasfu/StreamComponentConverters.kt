package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.ui.components.display.*
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utilities for converting Streams to UI components for rendering in grids
 */

/**
 * Converts a Stream to the appropriate video/audio card component
 */
fun Stream.toDisplayComponent(
    participants: List<Participant>,
    audioDecibels: List<AudioDecibels> = emptyList(),
    isVideoCard: Boolean = true,
    showControls: Boolean = false,
    currentMemberName: String? = null,
    currentMemberLevel: String? = null,
    eventType: EventType = EventType.NONE
): MediaSfuUIComponent {
    // Debug stream content
    
    // Find the participant for this stream
    val participant = participants.find {
        it.videoID == this.producerId || it.audioID == this.producerId
    } ?: participants.find { participant ->
        this.name?.let { candidate -> participant.name.equals(candidate, ignoreCase = true) } == true
    }

    val normalizedMemberName = currentMemberName?.trim()
    val isSelfProducer = this.producerId == "youyou" || this.producerId == "youyouyou"
    val matchesCurrentMember = normalizedMemberName?.let { memberName ->
        participant?.name?.equals(memberName, ignoreCase = true) == true ||
            this.name?.equals(memberName, ignoreCase = true) == true ||
            this.producerId.equals(memberName, ignoreCase = true)
    } ?: false
    val isCurrentUser = isSelfProducer || matchesCurrentMember

    val displayName = when {
        isCurrentUser && currentMemberLevel == "2" && eventType != EventType.CHAT -> "You (Host)"
        isCurrentUser -> "You"
        participant?.name?.isNotBlank() == true -> participant.name
        !this.name.isNullOrBlank() -> this.name
        else -> this.producerId
    }

    val representativeParticipant = participant ?: Participant(
        name = displayName,
        audioID = this.audioID ?: this.producerId,
        videoID = this.videoID ?: this.producerId,
        islevel = if (isCurrentUser) currentMemberLevel else participant?.islevel,
        isAdmin = isCurrentUser && currentMemberLevel == "2",
        isHost = isCurrentUser && currentMemberLevel == "2"
    )
    val isPlaceholder = extra["placeholder"]?.jsonPrimitive?.booleanOrNull == true
    val shouldRenderAsAudio = !isPlaceholder && (this.stream == null || this.muted == true)
    
    return when {
        // Mini cards (used for mini grid)
        !isVideoCard -> {
            DefaultMiniCard(
                MiniCardOptions(
                    name = displayName,
                    participant = representativeParticipant,
                    videoStream = this.stream,
                    backgroundColor = 0xFF1C2B4A.toInt(),
                    showVideo = !isPlaceholder && this.stream != null
                )
            )
        }

        // Placeholder video tiles (no media yet but we want a slot rendered)
        isPlaceholder && isVideoCard -> {
            DefaultCardVideoDisplay(
                CardVideoDisplayOptions(
                    videoStream = null,
                    remoteProducerId = this.producerId,
                    displayLabel = displayName,
                    backgroundColor = 0xFF1C2B4A.toInt(),
                    doMirror = false
                )
            )
        }

        // Audio-only stream
        shouldRenderAsAudio -> {
            DefaultAudioCard(
                AudioCardOptions(
                    name = displayName,
                    participant = representativeParticipant,
                    audioDecibels = audioDecibels,
                    showControls = showControls,
                    barColor = 0xFFE82E2E.toInt(),
                    waveformColor = 0xFF4CAF50.toInt()
                )
            )
        }

        // Full video card (for main grids)
        else -> {
            DefaultCardVideoDisplay(
                CardVideoDisplayOptions(
                    videoStream = this.stream,
                    remoteProducerId = this.producerId,
                    backgroundColor = 0xFF1C2B4A.toInt(),
                    doMirror = isCurrentUser,
                    displayLabel = displayName
                )
            )
        }
    }
}

/**
 * Converts a list of streams to display components
 */
fun List<Stream>.toDisplayComponents(
    participants: List<Participant>,
    audioDecibels: List<AudioDecibels> = emptyList(),
    isVideoCard: Boolean = true,
    showControls: Boolean = false,
    currentMemberName: String? = null,
    currentMemberLevel: String? = null,
    eventType: EventType = EventType.NONE
): List<MediaSfuUIComponent> {
    return this.map { stream ->
        stream.toDisplayComponent(
            participants = participants,
            audioDecibels = audioDecibels,
            isVideoCard = isVideoCard,
            showControls = showControls,
            currentMemberName = currentMemberName,
            currentMemberLevel = currentMemberLevel,
            eventType = eventType
        )
    }
}

/**
 * Converts a Stream to MiniAudio component for mini view strip
 */
fun Stream.toMiniAudioComponent(
    participants: List<Participant>,
    audioDecibels: List<AudioDecibels> = emptyList()
): MiniAudio {
    val participant = participants.find { 
        it.videoID == this.producerId || it.audioID == this.producerId 
    }
    
    return DefaultMiniAudio(
        MiniAudioOptions(
            name = participant?.name ?: this.name ?: "Unknown",
            audioDecibels = audioDecibels,
            backgroundColor = 0xFF1C2B4A.toInt(),
            showWaveform = true
        )
    )
}
