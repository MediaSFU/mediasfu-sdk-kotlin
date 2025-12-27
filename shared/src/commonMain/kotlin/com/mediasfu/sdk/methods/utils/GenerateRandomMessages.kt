package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.random.Random

/**
 * Options for generating random messages.
 */
data class GenerateRandomMessagesOptions(
    val participants: List<Participant>,
    val member: String,
    val coHost: String? = null,
    val host: String,
    val forChatBroadcast: Boolean = false
)

/**
 * Type definition for the random message generator function.
 */
typealias GenerateRandomMessagesType = (GenerateRandomMessagesOptions) -> List<Message>

/**
 * Generates random direct and group messages for the provided [options].
 */
fun generateRandomMessages(options: GenerateRandomMessagesOptions): List<Message> {
    if (options.participants.isEmpty()) {
        return emptyList()
    }

    val random = Random.Default
    val participantsByName = options.participants.associateBy { it.name }

    fun randomReceiver(sender: String): String {
        val potentialReceivers = options.participants
            .map { it.name }
            .filter { it != sender }

        return if (potentialReceivers.isNotEmpty()) {
            potentialReceivers[random.nextInt(potentialReceivers.size)]
        } else {
            sender
        }
    }

    val referenceNames = buildList {
        if (options.forChatBroadcast) {
            add(options.member)
            add(options.host)
        } else {
            add(options.member)
            options.coHost?.let { add(it) }
            add(options.host)
            options.participants.forEach { add(it.name) }
        }
    }.filter { it.isNotBlank() }
        .distinct()
        .filter { participantsByName.containsKey(it) || it == options.member || it == options.host || it == options.coHost }

    val timeZone = TimeZone.currentSystemDefault()
    val baseTimestamp = Clock.System.now().toEpochMilliseconds()

    val messages = mutableListOf<Message>()
    var timeIncrement = 0L

    for (sender in referenceNames) {
        val timestampMillis = baseTimestamp + timeIncrement
        val timestamp = Instant.fromEpochMilliseconds(timestampMillis)
            .toLocalDateTime(timeZone)
            .let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}:${it.second.toString().padStart(2, '0')}" }

        messages += Message(
            sender = sender,
            receivers = listOf(randomReceiver(sender)),
            message = "Direct message from $sender",
            timestamp = timestamp,
            group = false
        )

        val receivers = options.participants.map { it.name }
        messages += Message(
            sender = sender,
            receivers = receivers,
            message = "Group message from $sender",
            timestamp = timestamp,
            group = true
        )

        timeIncrement += 15_000L
    }

    return messages
}
