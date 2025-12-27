package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.ConsumerLike
import com.mediasfu.sdk.model.ConsumerTransportLike
import com.mediasfu.sdk.model.ProducerMediaClosedOptions
import com.mediasfu.sdk.model.TransportType
import com.mediasfu.sdk.testutil.TestProducerMediaClosedParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeConsumer : ConsumerLike {
    var closeCount = 0

    override suspend fun close() {
        closeCount += 1
    }
}

private class FakeTransport : ConsumerTransportLike {
    var closeCount = 0

    override suspend fun close() {
        closeCount += 1
    }
}

class ProducerMediaClosedTest {
    @Test
    fun closesTransportAndTriggersResize() = runTest {
        val consumer = FakeConsumer()
        val transport = FakeTransport()
        val transportEntry = TransportType(
            producerId = "producer-1",
            consumer = consumer,
            serverConsumerTransportId = "server-1",
            consumerTransport = transport
        )

        val params = TestProducerMediaClosedParameters(transports = listOf(transportEntry))

        val options = ProducerMediaClosedOptions(
            producerId = "producer-1",
            kind = "video",
            parameters = params
        )

        producerMediaClosed(options)

        assertEquals(1, consumer.closeCount)
        assertEquals(1, transport.closeCount)
        assertEquals(listOf(emptyList<TransportType>()), params.updatedTransports)
        assertEquals(1, params.closeAndResizeCalls.size)
        assertEquals("producer-1", params.closeAndResizeCalls.first().producerId)
    }

    @Test
    fun resetsSharingStateWhenScreenshareEnds() = runTest {
        val consumer = FakeConsumer()
        val transport = FakeTransport()
        val transportEntry = TransportType(
            producerId = "",
            consumer = consumer,
            serverConsumerTransportId = "server-2",
            consumerTransport = transport
        )

        val params = TestProducerMediaClosedParameters(
            transports = listOf(transportEntry),
            sharedInitial = true
        )

        val options = ProducerMediaClosedOptions(
            producerId = "",
            kind = "screenshare",
            parameters = params
        )

        producerMediaClosed(options)

        assertFalse(params.shared)
        assertTrue(params.shareEndedValue)
        assertEquals(1, params.prepopulateCalls.size)
        assertEquals("Host", params.prepopulateCalls.first().name)
        assertEquals(1, params.reorderCalls.size)
        assertFalse(params.reorderCalls.first().add)
    }

    @Test
    fun resetsShareScreenStartedWhenNotShared() = runTest {
        val consumer = FakeConsumer()
        val transport = FakeTransport()
        val transportEntry = TransportType(
            producerId = "",
            consumer = consumer,
            serverConsumerTransportId = "server-3",
            consumerTransport = transport
        )

        val params = TestProducerMediaClosedParameters(
            transports = listOf(transportEntry),
            sharedInitial = false
        ).apply {
            shareScreenStartedValue = true
            screenIdValue = "screen-123"
        }

        val options = ProducerMediaClosedOptions(
            producerId = "",
            kind = "screen",
            parameters = params
        )

        producerMediaClosed(options)

        assertFalse(params.shareScreenStartedValue)
        assertEquals("", params.screenIdValue)
        assertTrue(params.shareEndedValue)
    }
}