package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.EngineConnectSendTransportParameters
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.testutil.TestWebRtcDevice
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CreateSendTransportTeardownGuardTest {
    @Test
    fun `callback-time device lookup observes teardown`() {
        val parameters = MediasfuParameters()
        parameters.device = TestWebRtcDevice()
        val adapter = EngineConnectSendTransportParameters(parameters)

        assertNotNull(currentSendTransportDevice(adapter))

        // Models room teardown while createWebRtcTransport acknowledgement is in flight.
        parameters.device = null

        assertNull(currentSendTransportDevice(adapter))
    }
}
