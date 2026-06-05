package com.mediasfu.sdk.methods.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class CreateJoinRoomResponseNormalizationTest {
    @Test
    fun normalizesMalformedMediaSfuHostsToEndpointOrigin() {
        val response = CreateJoinRoomResponse(
            message = "Room created successfully",
            roomName = "smp8g9xz571310q7",
            publicURL = "https://.mediasfu.com/meet/smp8g9xz571310q7/token123",
            link = "https://.mediasfu.com",
            secret = "token123",
            success = true
        )

        val normalized = response.normalizedForEndpoint("https://mediasfu.com/v1/rooms") // ENDPOINT_TOGGLE

        assertEquals("https://mediasfu.com/v1/rooms", normalized.link) // ENDPOINT_TOGGLE
        assertEquals(
            "https://mediasfu.com/v1/rooms/meet/smp8g9xz571310q7/token123", // ENDPOINT_TOGGLE
            normalized.publicURL
        )
    }

    @Test
    fun preservesValidUrls() {
        val response = CreateJoinRoomResponse(
            message = "Room joined successfully",
            roomName = "room123",
            publicURL = "https://mediasfu.com/v1/rooms/meet/room123/token456", // ENDPOINT_TOGGLE
            link = "https://mediasfu.com/v1/rooms", // ENDPOINT_TOGGLE
            secret = "token456",
            success = true
        )

        val normalized = response.normalizedForEndpoint("https://mediasfu.com/v1/rooms") // ENDPOINT_TOGGLE

        assertEquals(response, normalized)
    }

    @Test
    fun mediaSfuCloudHelpersTargetProductionRoomsEndpoint() {
        assertEquals(
            "https://mediasfu.com/v1/rooms", // ENDPOINT_TOGGLE
            MEDIA_SFU_CLOUD_ROOMS_ENDPOINT
        )
    }
}