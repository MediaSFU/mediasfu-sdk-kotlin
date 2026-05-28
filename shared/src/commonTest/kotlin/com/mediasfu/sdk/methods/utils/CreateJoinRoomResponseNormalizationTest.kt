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

        val normalized = response.normalizedForEndpoint("https://staging.mediasfu.com/v1/rooms")

        assertEquals("https://staging.mediasfu.com", normalized.link)
        assertEquals(
            "https://staging.mediasfu.com/meet/smp8g9xz571310q7/token123",
            normalized.publicURL
        )
    }

    @Test
    fun preservesValidUrls() {
        val response = CreateJoinRoomResponse(
            message = "Room joined successfully",
            roomName = "room123",
            publicURL = "https://staging.mediasfu.com/meet/room123/token456",
            link = "https://staging.mediasfu.com",
            secret = "token456",
            success = true
        )

        val normalized = response.normalizedForEndpoint("https://staging.mediasfu.com/v1/rooms")

        assertEquals(response, normalized)
    }

    @Test
    fun mediaSfuCloudHelpersTargetProductionRoomsEndpoint() {
        assertEquals(
            "https://mediasfu.com/v1/rooms",
            MEDIA_SFU_CLOUD_ROOMS_ENDPOINT
        )
    }
}