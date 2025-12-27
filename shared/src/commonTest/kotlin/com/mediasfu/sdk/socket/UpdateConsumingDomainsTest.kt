package com.mediasfu.sdk.socket

import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.model.AltDomains
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.ConnectIpsOptions
import com.mediasfu.sdk.model.GetDomainsOptions
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.UpdateConsumingDomainsOptions
import com.mediasfu.sdk.model.UpdateConsumingDomainsParameters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpdateConsumingDomainsTest {

    // Mock implementation of UpdateConsumingDomainsParameters
    private class MockParameters(
        participants: List<Participant>,
        consumeSockets: List<ConsumeSocket>,
        roomRecvIps: List<String> = emptyList(),
        private val reorderStreamsFn: suspend (ReorderStreamsOptions) -> Unit = {},
        private val connectIpsFn: suspend (ConnectIpsOptions) -> Pair<List<ConsumeSocket>, List<String>> = { Pair(emptyList(), emptyList()) },
        private val getDomainsFn: suspend (GetDomainsOptions) -> Unit = {}
    ) : TestConnectIpsParameters(
        initialRoomRecvIps = roomRecvIps,
        initialConsumeSockets = consumeSockets
    ), UpdateConsumingDomainsParameters {

        init {
            updateParticipants(participants)
        }

    override val getDomains: suspend (GetDomainsOptions) -> Unit = { options -> getDomainsFn(options) }

        override fun getUpdatedAllParams(): UpdateConsumingDomainsParameters = this

    override suspend fun onReorderStreams(options: ReorderStreamsOptions) {
            reorderStreamsFn(options)
        }

        override suspend fun onConnectIps(options: ConnectIpsOptions): Pair<List<ConsumeSocket>, List<String>> {
            return connectIpsFn(options)
        }
    }

    @Test
    fun `calls getDomains when altDomains is not empty`() = runTest {
        var getDomainsOpts: GetDomainsOptions? = null

        val participants = listOf(
            Participant(name = "User1", audioID = "", videoID = "")
        )

        val parameters = MockParameters(
            participants = participants,
            consumeSockets = emptyList(),
            reorderStreamsFn = { },
            connectIpsFn = { opts -> Pair(emptyList(), emptyList()) },
            getDomainsFn = { opts ->
                getDomainsOpts = opts
            }
        )

        val options = UpdateConsumingDomainsOptions(
            domains = listOf("domain1.com"),
            altDomains = AltDomains(mapOf("domain1.com" to "alt1.com")),
            apiUserName = "testUser",
            apiKey = "testKey",
            apiToken = "testToken",
            parameters = parameters
        )

        updateConsumingDomains(options)

        assertEquals("testUser", getDomainsOpts?.apiUserName)
        assertEquals(listOf("domain1.com"), getDomainsOpts?.domains)
    }

    @Test
    fun `calls connectIps when altDomains is empty`() = runTest {
        var connectIpsOpts: ConnectIpsOptions? = null

        val participants = listOf(
            Participant(name = "User1", audioID = "", videoID = "")
        )

        val consumeSockets: List<ConsumeSocket> = listOf(
            mapOf("id" to "socket1", "janus" to false)
        )

        val parameters = MockParameters(
            participants = participants,
            consumeSockets = consumeSockets,
            reorderStreamsFn = { },
            connectIpsFn = { opts ->
                connectIpsOpts = opts
                Pair(emptyList(), emptyList())
            },
            getDomainsFn = { }
        )

        val options = UpdateConsumingDomainsOptions(
            domains = listOf("domain1.com", "domain2.com"),
            altDomains = AltDomains(emptyMap()),
            apiUserName = "testUser",
            apiKey = "testKey",
            apiToken = "testToken",
            parameters = parameters
        )

        updateConsumingDomains(options)

        assertEquals("testUser", connectIpsOpts?.apiUserName)
        assertEquals(listOf("domain1.com", "domain2.com"), connectIpsOpts?.remoteIps)
        assertEquals(consumeSockets, connectIpsOpts?.consumeSockets)
    }

    @Test
    fun `does nothing when participants list is empty`() = runTest {
        var getDomainsWasCalled = false
        var connectIpsWasCalled = false

        val parameters = MockParameters(
            participants = emptyList(),
            consumeSockets = emptyList(),
            reorderStreamsFn = { },
            connectIpsFn = { _ ->
                connectIpsWasCalled = true
                Pair(emptyList(), emptyList())
            },
            getDomainsFn = { getDomainsWasCalled = true }
        )

        val options = UpdateConsumingDomainsOptions(
            domains = listOf("domain1.com"),
            altDomains = AltDomains(mapOf("domain1.com" to "alt1.com")),
            apiUserName = "testUser",
            apiKey = "testKey",
            apiToken = "testToken",
            parameters = parameters
        )

        updateConsumingDomains(options)

        assertTrue(!getDomainsWasCalled)
        assertTrue(!connectIpsWasCalled)
    }

    @Test
    fun `handles exception gracefully`() = runTest {
        val parameters = MockParameters(
            participants = listOf(Participant(name = "User1", audioID = "", videoID = "")),
            consumeSockets = emptyList(),
            reorderStreamsFn = { },
            connectIpsFn = { _ -> throw RuntimeException("Test error") },
            getDomainsFn = { throw RuntimeException("Test error") }
        )

        val options = UpdateConsumingDomainsOptions(
            domains = listOf("domain1.com"),
            altDomains = AltDomains(emptyMap()),
            apiUserName = "testUser",
            apiKey = "testKey",
            apiToken = "testToken",
            parameters = parameters
        )

        // Should not throw
        updateConsumingDomains(options)
    }
}
