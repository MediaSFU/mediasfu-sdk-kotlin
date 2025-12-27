package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceiveMessageTest {
    @Test
    fun filtersBannedParticipantsAndTriggersBadge() {
        val participantAllowed = Participant(name = "Alice", isBanned = false)
        val participantBanned = Participant(name = "Bob", isBanned = true)
        val initialMessages = listOf(
            Message(
                sender = "Alice",
                receivers = listOf("Carol"),
                message = "Hi",
                timestamp = "1",
                group = false
            )
        )

        var updatedMessages: List<Message> = emptyList()
        var badgeShown = false

        val newMessage = Message(
            sender = "Bob",
            receivers = listOf("Carol"),
            message = "Hello",
            timestamp = "2",
            group = false,
            extra = JsonObject(mapOf("meta" to JsonPrimitive("test")))
        )

        val options = ReceiveMessageOptions(
            message = newMessage,
            messages = initialMessages,
            participantsAll = listOf(participantAllowed, participantBanned),
            member = "Carol",
            eventType = EventType.CONFERENCE,
            isLevel = "1",
            coHost = "",
            updateMessages = { updatedMessages = it },
            updateShowMessagesBadge = { badgeShown = it }
        )

        receiveMessage(options)

        assertEquals(1, updatedMessages.size)
        assertTrue(updatedMessages.none { it.sender == "Bob" })
        assertTrue(!badgeShown)
    }

    @Test
    fun adminGetsBadgeForNewDirectMessages() {
        val participantAllowed = Participant(name = "Alice", isBanned = false)
        val initialMessages = emptyList<Message>()

        var badgeShown = false

        val newMessage = Message(
            sender = "Alice",
            receivers = listOf("Everyone"),
            message = "Ping",
            timestamp = "1",
            group = false
        )

        val options = ReceiveMessageOptions(
            message = newMessage,
            messages = initialMessages,
            participantsAll = listOf(participantAllowed),
            member = "Admin",
            eventType = EventType.CONFERENCE,
            isLevel = "2",
            coHost = "",
            updateMessages = { },
            updateShowMessagesBadge = { badgeShown = it }
        )

        receiveMessage(options)

        assertTrue(badgeShown)
    }
}
