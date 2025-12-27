package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.WaitingRoomParticipant

/**
 * Type definition for the waiting room list generator function.
 */
typealias GenerateRandomWaitingRoomListType = () -> List<WaitingRoomParticipant>

/**
 * Generates a deterministic waiting room list for demo scenarios.
 */
fun generateRandomWaitingRoomList(): List<WaitingRoomParticipant> {
    val names = listOf("Dimen", "Nore", "Ker", "Lor", "Mik")

    return names.mapIndexed { index, name ->
        WaitingRoomParticipant(
            name = name,
            id = index.toString()
        )
    }
}
