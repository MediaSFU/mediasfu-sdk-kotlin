package com.mediasfu.spacestek.model

import androidx.compose.ui.graphics.Color

data class Space(
    val id: String,
    val title: String,
    val hostName: String,
    val hostHandle: String,
    val isLive: Boolean = false,
    val isScheduled: Boolean = false,
    val scheduledTime: Long? = null,
    val listenerCount: Int = 0,
    val speakerCount: Int = 0,
    val topics: List<String> = emptyList(),
    val description: String = "",
    val isRecording: Boolean = false
)

data class SpaceParticipant(
    val id: String,
    val name: String,
    val handle: String,
    val avatarUrl: String? = null,
    val avatarColor: Color = Color(0xFF1DA1F2),
    val role: ParticipantRole = ParticipantRole.LISTENER,
    val isMuted: Boolean = true,
    val isSpeaking: Boolean = false,
    val isVerified: Boolean = false
)

enum class ParticipantRole {
    HOST,
    CO_HOST,
    SPEAKER,
    LISTENER
}

enum class SpaceState {
    IDLE,
    JOINING,
    CONNECTED,
    SPEAKING,
    RECONNECTING,
    ENDED
}

data class SpaceMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long
)
