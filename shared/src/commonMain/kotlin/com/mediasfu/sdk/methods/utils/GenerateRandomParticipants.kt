package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.Participant
import kotlin.random.Random

/**
 * Options for generating a random list of participants.
 */
data class GenerateRandomParticipantsOptions(
    val member: String,
    val coHost: String? = null,
    val host: String,
    val forChatBroadcast: Boolean = false
)

/**
 * Type definition for the participant generator function.
 */
typealias GenerateRandomParticipantsType = (GenerateRandomParticipantsOptions) -> List<Participant>

/**
 * Generates a list of random participants for a meeting based on the provided [options].
 */
fun generateRandomParticipants(options: GenerateRandomParticipantsOptions): List<Participant> {
    val initialNames = listOf(
        "Alice",
        "Bob",
        "Charlie",
        "David",
        "Eve",
        "Frank",
        "Grace",
        "Hank",
        "Ivy",
        "Jack",
        "Kate",
        "Liam",
        "Mia",
        "Nina",
        "Olivia",
        "Pete",
        "Quinn",
        "Rachel",
        "Steve",
        "Tina",
        "Ursula",
        "Vince",
        "Wendy",
        "Xander",
        "Yvonne",
        "Zack"
    )

    val workingNames = if (options.forChatBroadcast) {
        initialNames.take(2).toMutableList()
    } else {
        initialNames.toMutableList()
    }

    if (!workingNames.contains(options.member)) {
        workingNames.add(0, options.member)
    }
    if (!options.coHost.isNullOrEmpty() && !options.forChatBroadcast && !workingNames.contains(options.coHost)) {
        workingNames.add(0, options.coHost)
    }
    if (!workingNames.contains(options.host)) {
        workingNames.add(0, options.host)
    }

    val filteredNames = workingNames.filter { it.length > 1 }.toMutableList()
    val random = Random.Default

    for (i in filteredNames.lastIndex downTo 1) {
        val j = random.nextInt(i + 1)
        val temp = filteredNames[i]
        filteredNames[i] = filteredNames[j]
        filteredNames[j] = temp
    }

    val participants = mutableListOf<Participant>()
    var hasLevel2Participant = false

    filteredNames.forEachIndexed { index, name ->
        val level = if (hasLevel2Participant) {
            "1"
        } else if (name == options.host) {
            "2"
        } else {
            "1"
        }

        if (level == "2") {
            hasLevel2Participant = true
        }

        val muted = if (options.forChatBroadcast) {
            true
        } else {
            random.nextBoolean()
        }

        participants += Participant(
            id = index.toString(),
            audioID = "audio-$index",
            videoID = "video-$index",
            name = name,
            islevel = level,
            muted = muted
        )
    }

    return participants
}
