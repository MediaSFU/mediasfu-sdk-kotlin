package com.mediasfu.sdk.consumers

import kotlin.test.Test
import kotlin.test.assertEquals

class GetPipedProducersAltTest {

    @Test
    fun extractProducerIds_readsNestedProducerCollections() {
        val payload = mapOf(
            "producerIds" to listOf("audio-1", mapOf("producerId" to "video-1")),
            "screenProducerId" to "screen-1"
        )

        assertEquals(
            listOf("audio-1", "video-1", "screen-1"),
            extractProducerIds(payload)
        )
    }

    @Test
    fun extractProducerIds_fallsBackToNestedMapsWithoutPullingPlainStrings() {
        val payload = mapOf(
            "kind" to "screen",
            "meta" to mapOf(
                "producers" to listOf(
                    mapOf("remoteProducerId" to "screen-1"),
                    mapOf("id" to "video-1")
                )
            ),
            "member" to "web3002"
        )

        assertEquals(
            listOf("screen-1", "video-1"),
            extractProducerIds(payload)
        )
    }

    @Test
    fun extractProducerEntries_preservesScreenShareHintsFromNamedKeys() {
        val payload = mapOf(
            "screenProducerId" to "screen-1",
            "producerIds" to listOf("audio-1", "video-1")
        )

        assertEquals(
            listOf(
                BootstrapProducerEntry(id = "screen-1", screenShareHint = true),
                BootstrapProducerEntry(id = "audio-1", screenShareHint = false),
                BootstrapProducerEntry(id = "video-1", screenShareHint = false)
            ),
            extractProducerEntries(payload)
        )
    }
}