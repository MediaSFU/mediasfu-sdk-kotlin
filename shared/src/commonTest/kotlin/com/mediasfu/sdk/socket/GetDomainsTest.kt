package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.AltDomains
import com.mediasfu.sdk.model.ConnectIpsOptions
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.GetDomainsOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

private class FakeGetDomainsParameters : TestConnectIpsParameters(
    initialRoomRecvIps = listOf("100.122.1.1")
) {
    val connectIpsCalls = mutableListOf<ConnectIpsOptions>()

    override suspend fun onConnectIps(options: ConnectIpsOptions): Pair<List<ConsumeSocket>, List<String>> {
        connectIpsCalls += options
        return Pair(emptyList(), emptyList())
    }
}

class GetDomainsTest {
    @Test
    fun connectsToNewDomainsNotInRoomRecvIps() = runTest {
        val params = FakeGetDomainsParameters()
        val options = GetDomainsOptions(
            domains = listOf("domain1.com", "domain2.com", "100.122.1.1"),
            altDomains = AltDomains(),
            apiUserName = "myUser",
            apiKey = "myKey",
            apiToken = "myToken",
            parameters = params
        )

        getDomains(options)

        assertEquals(1, params.connectIpsCalls.size)
        val call = params.connectIpsCalls.first()
        assertEquals(listOf("domain1.com", "domain2.com"), call.remoteIps)
        assertEquals("myUser", call.apiUserName)
        assertEquals("myKey", call.apiKey)
        assertEquals("myToken", call.apiToken)
    }

    @Test
    fun usesAltDomainsWhenProvided() = runTest {
        val params = FakeGetDomainsParameters()
        val options = GetDomainsOptions(
            domains = listOf("domain1.com", "domain2.com"),
            altDomains = AltDomains(
                altDomains = mapOf(
                    "domain1.com" to "alt1.domain.com",
                    "domain2.com" to "alt2.domain.com"
                )
            ),
            apiUserName = "myUser",
            apiKey = null,
            apiToken = "myToken",
            parameters = params
        )

        getDomains(options)

        assertEquals(1, params.connectIpsCalls.size)
        val call = params.connectIpsCalls.first()
        assertTrue(call.remoteIps.containsAll(listOf("alt1.domain.com", "alt2.domain.com")))
    }

    @Test
    fun skipsDomainsAlreadyConnected() = runTest {
        val params = FakeGetDomainsParameters()
        val options = GetDomainsOptions(
            domains = listOf("100.122.1.1", "newdomain.com"),
            altDomains = AltDomains(),
            apiUserName = "myUser",
            apiKey = "myKey",
            apiToken = "myToken",
            parameters = params
        )

        getDomains(options)

        assertEquals(1, params.connectIpsCalls.size)
        val call = params.connectIpsCalls.first()
        assertEquals(listOf("newdomain.com"), call.remoteIps)
    }
}
