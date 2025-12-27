package com.mediasfu.sdk.model

/**
 * Represents a participant in a breakout room.
 *
 * @property name Participant name
 * @property breakRoom Breakout room number/index
 */
data class BreakoutParticipant(
    val name: String,
    val breakRoom: Int? = null
)

