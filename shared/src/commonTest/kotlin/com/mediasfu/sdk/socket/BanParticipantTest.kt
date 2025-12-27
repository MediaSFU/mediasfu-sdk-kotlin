package com.mediasfu.sdk.socket

import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.model.BanParticipantOptions
import com.mediasfu.sdk.model.BanParticipantParameters
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ReorderStreamsOptions
import com.mediasfu.sdk.model.ReorderStreamsType
import com.mediasfu.sdk.model.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeBanParticipantParameters : BanParticipantParameters {
    var activeNamesState: List<String> = emptyList()
    var dispActiveNamesState: List<String> = emptyList()
    private var participantsState: List<Participant> = emptyList()
    var allVideoStreamsState: List<Stream> = emptyList()
    var oldAllStreamsState: List<Stream> = emptyList()
    var screenIdState: String = ""
    var adminVidIDState: String = ""
    var newLimitedStreamsState: List<Stream> = emptyList()
    var newLimitedStreamsIDsState: List<String> = emptyList()
    var activeSoundsState: List<String> = emptyList()
    var screenShareIDStreamState: String = ""
    var screenShareNameStreamState: String = ""
    var adminIDStreamState: String = ""
    var adminNameStreamState: String = ""
    var youYouStreamState: List<Stream> = emptyList()
    var youYouStreamIDsState: List<String> = emptyList()
    var changeVidsCalls: MutableList<ChangeVidsOptions> = mutableListOf()

    var updateCalls: Int = 0
    var reorderCalls: Int = 0
    var lastReorderOptions: ReorderStreamsOptions? = null

    override val activeNames: List<String>
        get() = activeNamesState

    override val dispActiveNames: List<String>
        get() = dispActiveNamesState

    override val participants: List<Participant>
        get() = participantsState

    override val allVideoStreams: List<Stream>
        get() = allVideoStreamsState

    override val oldAllStreams: List<Stream>
        get() = oldAllStreamsState

    override val screenId: String
        get() = screenIdState

    override val adminVidID: String
        get() = adminVidIDState

    override val newLimitedStreams: List<Stream>
        get() = newLimitedStreamsState

    override val newLimitedStreamsIDs: List<String>
        get() = newLimitedStreamsIDsState

    override val activeSounds: List<String>
        get() = activeSoundsState

    override val screenShareIDStream: String
        get() = screenShareIDStreamState

    override val screenShareNameStream: String
        get() = screenShareNameStreamState

    override val adminIDStream: String
        get() = adminIDStreamState

    override val adminNameStream: String
        get() = adminNameStreamState

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { list ->
            updateCalls += 1
            participantsState = list
        }

    override fun updateParticipants(participants: List<Participant>) {
        updateCalls += 1
        participantsState = participants
    }

    override val reorderStreams: ReorderStreamsType = { options ->
        reorderCalls += 1
        lastReorderOptions = options
    }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        allVideoStreamsState = streams
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        oldAllStreamsState = streams
    }

    override fun updateScreenId(id: String) {
        screenIdState = id
    }

    override fun updateAdminVidID(id: String) {
        adminVidIDState = id
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        newLimitedStreamsState = streams
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        newLimitedStreamsIDsState = ids
    }

    override fun updateActiveSounds(sounds: List<String>) {
        activeSoundsState = sounds
    }

    override fun updateScreenShareIDStream(id: String) {
        screenShareIDStreamState = id
    }

    override fun updateScreenShareNameStream(name: String) {
        screenShareNameStreamState = name
    }

    override fun updateAdminIDStream(id: String) {
        adminIDStreamState = id
    }

    override fun updateAdminNameStream(name: String) {
        adminNameStreamState = name
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        youYouStreamState = streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        youYouStreamIDsState = ids
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> {
        changeVidsCalls += options
        return Result.success(Unit)
    }

    override fun getUpdatedAllParams(): BanParticipantParameters = this

    fun seedParticipants(list: List<Participant>) {
        participantsState = list
    }
}

class BanParticipantTest {
    @Test
    fun bansParticipantAndReorders() = runTest {
        val params = FakeBanParticipantParameters().apply {
            activeNamesState = listOf("Alice")
            dispActiveNamesState = listOf("Alice")
            seedParticipants(
                listOf(
                    Participant(name = "Alice"),
                    Participant(name = "Bob")
                )
            )
        }

        val options = BanParticipantOptions(name = "Alice", parameters = params)

        banParticipant(options)

        assertEquals(listOf("Bob"), params.participants.map { it.name })
        assertEquals(1, params.updateCalls)
        assertEquals(1, params.reorderCalls)
        val reorderOptions = params.lastReorderOptions
        assertTrue(reorderOptions != null && reorderOptions.screenChanged)
        assertFalse(reorderOptions!!.add)
    }

    @Test
    fun doesNothingWhenParticipantNotTracked() = runTest {
        val params = FakeBanParticipantParameters().apply {
            activeNamesState = listOf("Charlie")
            dispActiveNamesState = listOf("Charlie")
            seedParticipants(
                listOf(
                    Participant(name = "Bob"),
                    Participant(name = "Dana")
                )
            )
        }

        val options = BanParticipantOptions(name = "Alice", parameters = params)

        banParticipant(options)

        assertEquals(listOf("Bob", "Dana"), params.participants.map { it.name })
        assertEquals(0, params.updateCalls)
        assertEquals(0, params.reorderCalls)
    }
}
