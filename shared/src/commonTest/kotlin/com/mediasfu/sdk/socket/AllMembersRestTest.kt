package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.AllMembersRestOptions
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.Settings
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class AllMembersRestTest {
    @Test
    fun allMembersRestConnectsRemoteIpsAndAppliesSettings() = runTest {
        val params = FakeAllMembersParameters().apply {
            roomRecvIpsState = listOf("10.0.0.1")
            deferScreenReceivedState = true
            screenIdState = "screen-1"
            meetingDisplayTypeState = "speaker"
            seedRequestList(
                listOf(
                    Request(id = "1", icon = "hand"),
                    Request(id = "2", icon = "hand")
                )
            )
        }

        val members = listOf(
            Participant(id = "1", name = "Alice", audioID = "a1", videoID = "v1"),
            Participant(id = "2", name = "Bob", audioID = "a2", videoID = "v2", isBanned = true),
            Participant(id = "3", name = "Carol", audioID = "a3", videoID = "v3", isSuspended = true)
        )

        val options = AllMembersRestOptions(
            members = members,
            settings = Settings(listOf("mute", "allow", "block", "disable")),
            coHost = "NewHost",
            coHostRes = listOf(CoHostResponsibility(name = "manage", value = true, dedicated = false)),
            parameters = params,
            consumeSockets = emptyList(),
            apiUserName = "apiUser",
            apiKey = null,
            apiToken = "token"
        )

        allMembersRest(options)

        assertEquals(listOf("Alice"), params.participants.map { it.name })
        assertEquals(listOf("Alice"), params.participantsAll.map { it.name })
        assertEquals(1, params.connectIpsCount)
        assertEquals(listOf(mapOf("socket" to "remoteSocket")), params.consumeSocketsState)
        assertEquals(listOf("10.0.0.2"), params.roomRecvIps)
        assertTrue(params.membersReceived)
        assertTrue(params.shareScreenStartedState)
        assertEquals(listOf(250), params.sleepCalls)
        assertEquals(false, params.isLoadingVisibleState)
        assertEquals(listOf("1"), params.requestList.map { it.id })
        assertEquals("NewHost", params.coHost)
        assertEquals(1, params.onScreenChangesCount)
        assertTrue(params.firstAll)
        assertEquals(listOf("mute"), params.audioSettingUpdates)
        assertEquals(listOf("allow"), params.videoSettingUpdates)
        assertEquals(listOf("block"), params.screenshareSettingUpdates)
        assertEquals(listOf("disable"), params.chatSettingUpdates)
    }

    @Test
    fun allMembersRestHandlesLocalConnections() = runTest {
        val params = FakeAllMembersParameters().apply {
            roomRecvIpsState = listOf("none")
            connectLocalIpsLambda = { _ ->
                connectLocalIpsCount += 1
            }
            meetingDisplayTypeState = "all"
            seedRequestList(
                listOf(
                    Request(id = "1", icon = "hand"),
                    Request(id = "2", icon = "hand")
                )
            )
        }

        val members = listOf(
            Participant(id = "1", name = "Alice", audioID = "a1", videoID = "v1")
        )

        val options = AllMembersRestOptions(
            members = members,
            settings = Settings(listOf("allow", "allow", "allow", "allow")),
            coHost = "Host",
            coHostRes = emptyList(),
            parameters = params,
            consumeSockets = emptyList(),
            apiUserName = "apiUser",
            apiKey = null,
            apiToken = "token"
        )

        allMembersRest(options)

        assertEquals(0, params.connectIpsCount)
        assertEquals(1, params.connectLocalIpsCount)
        assertEquals(listOf(50), params.sleepCalls)
        assertEquals(false, params.isLoadingVisibleState)
        assertEquals(listOf("1"), params.requestList.map { it.id })
        assertEquals("Host", params.coHost)
        assertEquals(1, params.onScreenChangesCount)
        assertFalse(params.firstAll)
        assertTrue(params.audioSettingUpdates.isEmpty())
        assertTrue(params.videoSettingUpdates.isEmpty())
    }
}
