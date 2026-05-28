package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.socket.TestSocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ReceiveAllBootstrapSocket(
    private val createReceiveResponse: Any? = mapOf("producersExist" to true),
    private val createReceiveError: Throwable? = null
) : TestSocketManager() {
    val ackCalls = mutableListOf<Triple<String, Map<String, Any?>, Long>>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        ackCalls += Triple(event, data, timeout)

        if (event == "createReceiveAllTransportsPiped" || event == "createReceiveAllTransports") {
            createReceiveError?.let { throw it }
            return createReceiveResponse as T
        }

        return emptyList<String>() as T
    }
}

class ReceiveAllPipedTransportsTest {

    @Test
    fun receiveAllPipedTransports_usesExtendedAckTimeout() = runTest {
        val socket = ReceiveAllBootstrapSocket()
        val fetchedLevels = mutableListOf<String>()

        receiveAllPipedTransportsImpl(
            nsock = socket,
            community = false,
            roomName = "room-1",
            member = "member-1",
            getPipedProducersAlt = { fetchedLevels += it.islevel }
        )

        assertEquals(1, socket.ackCalls.size)
        assertEquals("createReceiveAllTransportsPiped", socket.ackCalls.first().first)
        assertEquals(30_000L, socket.ackCalls.first().third)
        assertEquals(listOf("0", "1", "2"), fetchedLevels)
    }

    @Test
    fun receiveAllPipedTransports_fallsBackToLevelFetchWhenCreateReceiveTimesOut() = runTest {
        val socket = ReceiveAllBootstrapSocket(
            createReceiveError = IllegalStateException("Acknowledgment timeout for event 'createReceiveAllTransportsPiped'")
        )
        val fetchedLevels = mutableListOf<String>()

        receiveAllPipedTransportsImpl(
            nsock = socket,
            community = false,
            roomName = "room-1",
            member = "member-1",
            getPipedProducersAlt = { fetchedLevels += it.islevel }
        )

        assertTrue(socket.ackCalls.isNotEmpty())
        assertEquals("createReceiveAllTransportsPiped", socket.ackCalls.first().first)
        assertEquals(listOf("0", "1", "2"), fetchedLevels)
    }

    @Test
    fun receiveAllPipedTransports_skipsLevelFetchWhenServerReportsNoProducers() = runTest {
        val socket = ReceiveAllBootstrapSocket(
            createReceiveResponse = mapOf("producersExist" to false)
        )
        val fetchedLevels = mutableListOf<String>()

        receiveAllPipedTransportsImpl(
            nsock = socket,
            community = false,
            roomName = "room-1",
            member = "member-1",
            getPipedProducersAlt = { fetchedLevels += it.islevel }
        )

        assertEquals(1, socket.ackCalls.size)
        assertTrue(fetchedLevels.isEmpty())
    }
}
