package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.HostRequestResponseOptions
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.RequestResponse
import com.mediasfu.sdk.model.ShowAlert
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HostRequestResponseTest {

    @Test
    fun acceptedMicrophoneRequestUpdatesActionAndState() = runTest {
        var micActionUpdated = false
        var audioRequestState = ""
        var audioRequestTime: Long? = null
        var alertMessage = ""
        var alertType = ""
        val requestList = mutableListOf(
            Request(id = "req1", name = "User1", icon = "fa-microphone")
        )

        val options = HostRequestResponseOptions(
            requestResponse = RequestResponse(
                id = "req1",
                type = "fa-microphone",
                action = "accepted"
            ),
            showAlert = { message, type, _ ->
                alertMessage = message
                alertType = type
            },
            requestList = requestList,
            updateRequestList = { requestList.clear(); requestList.addAll(it) },
            updateMicAction = { micActionUpdated = it },
            updateVideoAction = {},
            updateScreenAction = {},
            updateChatAction = {},
            updateAudioRequestState = { audioRequestState = it },
            updateVideoRequestState = {},
            updateScreenRequestState = {},
            updateChatRequestState = {},
            updateAudioRequestTime = { audioRequestTime = it },
            updateVideoRequestTime = {},
            updateScreenRequestTime = {},
            updateChatRequestTime = {},
            updateRequestIntervalSeconds = 240
        )

        hostRequestResponse(options)

        assertTrue(micActionUpdated, "Mic action should be enabled")
        assertEquals("accepted", audioRequestState, "Audio request state should be 'accepted'")
        assertNull(audioRequestTime, "Audio request time should not be set for accepted requests")
        assertTrue(alertMessage.contains("Audio request was accepted"), "Should show success alert")
        assertEquals("success", alertType, "Alert type should be success")
        assertTrue(requestList.isEmpty(), "Request should be removed from list")
    }

    @Test
    fun rejectedVideoRequestSetsStateAndCooldownTime() = runTest {
        var videoActionUpdated = false
        var videoRequestState = ""
        var videoRequestTime: Long? = null
        var alertMessage = ""
        var alertType = ""
        val requestList = mutableListOf(
            Request(id = "req2", name = "User2", icon = "fa-video")
        )

        val options = HostRequestResponseOptions(
            requestResponse = RequestResponse(
                id = "req2",
                type = "fa-video",
                action = "rejected"
            ),
            showAlert = { message, type, _ ->
                alertMessage = message
                alertType = type
            },
            requestList = requestList,
            updateRequestList = { requestList.clear(); requestList.addAll(it) },
            updateMicAction = {},
            updateVideoAction = { videoActionUpdated = it },
            updateScreenAction = {},
            updateChatAction = {},
            updateAudioRequestState = {},
            updateVideoRequestState = { videoRequestState = it },
            updateScreenRequestState = {},
            updateChatRequestState = {},
            updateAudioRequestTime = {},
            updateVideoRequestTime = { videoRequestTime = it },
            updateScreenRequestTime = {},
            updateChatRequestTime = {},
            updateRequestIntervalSeconds = 240
        )

        val beforeTime = System.currentTimeMillis()
        hostRequestResponse(options)
        val afterTime = System.currentTimeMillis()

        assertEquals(false, videoActionUpdated, "Video action should not be enabled")
        assertEquals("rejected", videoRequestState, "Video request state should be 'rejected'")
        assertTrue(
            videoRequestTime!! > beforeTime + 239_000,
            "Cooldown time should be at least 240 seconds in future"
        )
        assertTrue(
            videoRequestTime!! < afterTime + 241_000,
            "Cooldown time should be within reasonable bounds"
        )
        assertTrue(alertMessage.contains("Video request was not accepted"), "Should show rejection alert")
        assertEquals("danger", alertType, "Alert type should be danger")
        assertTrue(requestList.isEmpty(), "Request should be removed from list")
    }

    @Test
    fun acceptedScreenShareRequestUpdatesCorrectly() = runTest {
        var screenActionUpdated = false
        var screenRequestState = ""
        val requestList = mutableListOf(
            Request(id = "req3", name = "User3", icon = "fa-desktop")
        )

        val options = HostRequestResponseOptions(
            requestResponse = RequestResponse(
                id = "req3",
                type = "fa-desktop",
                action = "accepted"
            ),
            showAlert = null, // Test without alert
            requestList = requestList,
            updateRequestList = { requestList.clear(); requestList.addAll(it) },
            updateMicAction = {},
            updateVideoAction = {},
            updateScreenAction = { screenActionUpdated = it },
            updateChatAction = {},
            updateAudioRequestState = {},
            updateVideoRequestState = {},
            updateScreenRequestState = { screenRequestState = it },
            updateChatRequestState = {},
            updateAudioRequestTime = {},
            updateVideoRequestTime = {},
            updateScreenRequestTime = {},
            updateChatRequestTime = {},
            updateRequestIntervalSeconds = 240
        )

        hostRequestResponse(options)

        assertTrue(screenActionUpdated, "Screen action should be enabled")
        assertEquals("accepted", screenRequestState, "Screen request state should be 'accepted'")
        assertTrue(requestList.isEmpty(), "Request should be removed from list")
    }

    @Test
    fun acceptedChatRequestUpdatesCorrectly() = runTest {
        var chatActionUpdated = false
        var chatRequestState = ""
        val requestList = mutableListOf(
            Request(id = "req4", name = "User4", icon = "fa-comments")
        )

        val options = HostRequestResponseOptions(
            requestResponse = RequestResponse(
                id = "req4",
                type = "fa-comments",
                action = "accepted"
            ),
            showAlert = { _, _, _ -> },
            requestList = requestList,
            updateRequestList = { requestList.clear(); requestList.addAll(it) },
            updateMicAction = {},
            updateVideoAction = {},
            updateScreenAction = {},
            updateChatAction = { chatActionUpdated = it },
            updateAudioRequestState = {},
            updateVideoRequestState = {},
            updateScreenRequestState = {},
            updateChatRequestState = { chatRequestState = it },
            updateAudioRequestTime = {},
            updateVideoRequestTime = {},
            updateScreenRequestTime = {},
            updateChatRequestTime = {},
            updateRequestIntervalSeconds = 240
        )

        hostRequestResponse(options)

        assertTrue(chatActionUpdated, "Chat action should be enabled")
        assertEquals("accepted", chatRequestState, "Chat request state should be 'accepted'")
        assertTrue(requestList.isEmpty(), "Request should be removed from list")
    }

    @Test
    fun removesOnlyTargetedRequestFromList() = runTest {
        val requestList = mutableListOf(
            Request(id = "req1", name = "User1", icon = "fa-microphone"),
            Request(id = "req2", name = "User2", icon = "fa-video"),
            Request(id = "req3", name = "User3", icon = "fa-desktop")
        )

        val options = HostRequestResponseOptions(
            requestResponse = RequestResponse(
                id = "req2",
                type = "fa-video",
                action = "accepted"
            ),
            showAlert = { _, _, _ -> },
            requestList = requestList,
            updateRequestList = { requestList.clear(); requestList.addAll(it) },
            updateMicAction = {},
            updateVideoAction = {},
            updateScreenAction = {},
            updateChatAction = {},
            updateAudioRequestState = {},
            updateVideoRequestState = {},
            updateScreenRequestState = {},
            updateChatRequestState = {},
            updateAudioRequestTime = {},
            updateVideoRequestTime = {},
            updateScreenRequestTime = {},
            updateChatRequestTime = {},
            updateRequestIntervalSeconds = 240
        )

        hostRequestResponse(options)

        assertEquals(2, requestList.size, "Should have 2 remaining requests")
        assertTrue(requestList.any { it.id == "req1" }, "req1 should remain")
        assertTrue(requestList.any { it.id == "req3" }, "req3 should remain")
        assertTrue(requestList.none { it.id == "req2" }, "req2 should be removed")
    }
}
