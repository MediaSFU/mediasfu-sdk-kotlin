package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Request
import kotlin.random.Random

/**
 * Options for generating a random request list.
 */
data class GenerateRandomRequestListOptions(
    val participants: List<Participant>,
    val hostName: String,
    val coHostName: String? = null,
    val numberOfRequests: Int
)

/**
 * Type definition for the request list generator function.
 */
typealias GenerateRandomRequestListType = (GenerateRandomRequestListOptions) -> List<Request>

/**
 * Generates random request entries for the provided participant list.
 */
fun generateRandomRequestList(options: GenerateRandomRequestListOptions): List<Request> {
    if (options.participants.isEmpty() || options.numberOfRequests <= 0) {
        return emptyList()
    }

    val filteredParticipants = options.participants.filter { participant ->
        participant.name != options.hostName && participant.name != options.coHostName
    }

    if (filteredParticipants.isEmpty()) {
        return emptyList()
    }

    val random = Random.Default
    val baseIcons = mutableListOf("fa-video", "fa-desktop", "fa-microphone")
    baseIcons.shuffle(random)

    val requests = mutableListOf<Request>()

    for (participant in filteredParticipants) {
        val uniqueIcons = mutableSetOf<String>()
        val requestCount = minOf(options.numberOfRequests, baseIcons.size)

        repeat(requestCount) {
            val availableIcons = baseIcons.filterNot { uniqueIcons.contains(it) }
            if (availableIcons.isEmpty()) {
                return@repeat
            }

            val icon = availableIcons[random.nextInt(availableIcons.size)]
            uniqueIcons += icon

            val normalizedName = participant.name.lowercase().replace(" ", "_")
            requests += Request(
                id = participant.id ?: "",
                icon = icon,
                name = normalizedName,
                username = normalizedName
            )
        }
    }

    return requests
}
