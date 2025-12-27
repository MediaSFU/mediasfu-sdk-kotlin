package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ScreenProducerIdOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ScreenProducerFixture {
    var lastScreenId: String? = null
    val shareScreenUpdates: MutableList<Boolean> = mutableListOf()
    val deferScreenUpdates: MutableList<Boolean> = mutableListOf()

    fun options(
        producerId: String = "producer-screen",
        screenId: String = "screen-1",
        membersReceived: Boolean = true,
        shareScreenStarted: Boolean = false,
        deferScreenReceived: Boolean = false,
        participants: List<Participant>
    ): ScreenProducerIdOptions = ScreenProducerIdOptions(
        producerId = producerId,
        screenId = screenId,
        membersReceived = membersReceived,
        shareScreenStarted = shareScreenStarted,
        deferScreenReceived = deferScreenReceived,
        participants = participants,
        updateScreenId = { lastScreenId = it },
        updateShareScreenStarted = { shareScreenUpdates.add(it) },
        updateDeferScreenReceived = { deferScreenUpdates.add(it) }
    )
}

class ScreenProducerIdTest {
    @Test
    fun startsSharingWhenHostPresentAndMembersReceived() {
        val fixture = ScreenProducerFixture()
        val participants = listOf(
            Participant(
                name = "Host",
                ScreenID = "screen-1",
                ScreenOn = true
            )
        )

        screenProducerId(fixture.options(participants = participants))

        assertEquals("producer-screen", fixture.lastScreenId)
        assertEquals(listOf(true), fixture.shareScreenUpdates)
        assertEquals(listOf(false), fixture.deferScreenUpdates)
    }

    @Test
    fun defersWhenHostNotPresent() {
        val fixture = ScreenProducerFixture()
        val participants = listOf(
            Participant(
                name = "Guest",
                ScreenID = "screen-2",
                ScreenOn = true
            )
        )

        screenProducerId(fixture.options(participants = participants))

        assertEquals("producer-screen", fixture.lastScreenId)
        assertTrue(fixture.shareScreenUpdates.isEmpty())
        assertEquals(listOf(true), fixture.deferScreenUpdates)
    }

    @Test
    fun defersWhenMembersNotReceived() {
        val fixture = ScreenProducerFixture()
        val participants = listOf(
            Participant(
                name = "Host",
                ScreenID = "screen-1",
                ScreenOn = true
            )
        )

        screenProducerId(
            fixture.options(
                membersReceived = false,
                participants = participants
            )
        )

        assertEquals("producer-screen", fixture.lastScreenId)
        assertTrue(fixture.shareScreenUpdates.isEmpty())
        assertEquals(listOf(true), fixture.deferScreenUpdates)
    }
}
