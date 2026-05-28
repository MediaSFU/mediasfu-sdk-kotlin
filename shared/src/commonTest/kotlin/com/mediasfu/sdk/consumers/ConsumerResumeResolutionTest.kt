package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConsumerResumeResolutionTest {

    @Test
    fun screenShareResolver_prefersParticipantScreenWhenAvailable() {
        val resolved = resolveScreenShareStateForConsumerResume(
            participants = listOf(
                Participant(
                    name = "host",
                    ScreenID = "screen-live",
                    ScreenOn = true
                )
            ),
            currentScreenId = "screen-pending",
            remoteProducerId = "video-1",
            whiteboardStarted = false,
            whiteboardEnded = true
        )

        assertEquals("screen-live", resolved.screenId)
        assertEquals(true, resolved.shareScreenStarted)
    }

    @Test
    fun screenShareResolver_preservesPendingScreenIdBeforeMatchingVideoArrives() {
        val resolved = resolveScreenShareStateForConsumerResume(
            participants = emptyList(),
            currentScreenId = "screen-pending",
            remoteProducerId = "video-1",
            whiteboardStarted = false,
            whiteboardEnded = true
        )

        assertEquals("screen-pending", resolved.screenId)
        assertNull(resolved.shareScreenStarted)
    }

    @Test
    fun screenShareResolver_promotesPendingScreenIdWhenProducerMatches() {
        val resolved = resolveScreenShareStateForConsumerResume(
            participants = emptyList(),
            currentScreenId = "screen-pending",
            remoteProducerId = "screen-pending",
            whiteboardStarted = false,
            whiteboardEnded = true
        )

        assertEquals("screen-pending", resolved.screenId)
        assertEquals(true, resolved.shareScreenStarted)
    }

    @Test
    fun screenShareResolver_clearsWhenNoScreenSignalsRemain() {
        val resolved = resolveScreenShareStateForConsumerResume(
            participants = emptyList(),
            currentScreenId = "",
            remoteProducerId = "video-1",
            whiteboardStarted = false,
            whiteboardEnded = true
        )

        assertEquals("", resolved.screenId)
        assertEquals(false, resolved.shareScreenStarted)
    }

    @Test
    fun resolvesParticipantByExactVideoIdMatch() {
        val participant = Participant(name = "remote-user", videoID = "video-1")

        val resolved = resolveParticipantForRemoteProducer(
            remoteProducerId = "video-1",
            kind = "video",
            participants = listOf(participant),
            localParticipantName = "local-user"
        )

        assertEquals(participant, resolved)
    }

    @Test
    fun resolvesUniquePlaceholderParticipantForUnmatchedVideoProducer() {
        val localParticipant = Participant(name = "local-user")
        val placeholderParticipant = Participant(
            name = "remote-user",
            extra = buildJsonObject {
                put("placeholder", JsonPrimitive(true))
            }
        )

        val resolved = resolveParticipantForRemoteProducer(
            remoteProducerId = "video-1",
            kind = "video",
            participants = listOf(localParticipant, placeholderParticipant),
            localParticipantName = "local-user"
        )

        assertEquals(placeholderParticipant, resolved)
    }

    @Test
    fun resolvesParticipantByExistingNamedStreamHint() {
        val participant = Participant(name = "remote-user")

        val resolved = resolveParticipantForRemoteProducer(
            remoteProducerId = "video-1",
            kind = "video",
            participants = listOf(participant),
            localParticipantName = "local-user",
            namedStreams = listOf(Stream(producerId = "video-1", name = "remote-user"))
        )

        assertEquals(participant, resolved)
    }

    @Test
    fun doesNotGuessWhenMultiplePlaceholderCandidatesExist() {
        val first = Participant(
            name = "remote-user-1",
            extra = buildJsonObject {
                put("placeholder", JsonPrimitive(true))
            }
        )
        val second = Participant(
            name = "remote-user-2",
            extra = buildJsonObject {
                put("placeholder", JsonPrimitive(true))
            }
        )

        val resolved = resolveParticipantForRemoteProducer(
            remoteProducerId = "video-1",
            kind = "video",
            participants = listOf(first, second),
            localParticipantName = "local-user"
        )

        assertNull(resolved)
    }
}