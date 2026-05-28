package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.consumers.socket_receive_methods.CreateDeviceClientOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.JoinConsumeRoomOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.JoinConsumeRoomParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.joinConsumeRoom
import com.mediasfu.sdk.consumers.socket_receive_methods.scheduleReceiveAllPipedTransportsRetry
import com.mediasfu.sdk.socket.TestSocketManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class JoinConsumeRoomSocket : TestSocketManager() {
    val ackCalls = mutableListOf<Pair<String, Map<String, Any?>>>()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> emitWithAck(
        event: String,
        data: Map<String, Any?>,
        timeout: Long
    ): T {
        ackCalls += event to data

        return when (event) {
            "joinConRoom" -> mapOf(
                "success" to true,
                "rtpCapabilities" to mapOf("codecs" to emptyList<Any>())
            ) as T
            else -> error("Unexpected ack event: $event")
        }
    }
}

private class TestJoinConsumeRoomParameters : JoinConsumeRoomParameters {
    override val roomName: String = "room-1"
    override val islevel: String = "2"
    override val member: String = "member-1"
    override val device = null

    val receiveAllCalls = mutableListOf<Any>()

    override val updateDevice: (com.mediasfu.sdk.webrtc.WebRtcDevice?) -> Unit = { }
    override val receiveAllPipedTransports: suspend (com.mediasfu.sdk.consumers.socket_receive_methods.ReceiveAllPipedTransportsOptions) -> Unit = {
        receiveAllCalls += it.parameters
    }
    override val createDeviceClient: suspend (CreateDeviceClientOptions) -> com.mediasfu.sdk.webrtc.WebRtcDevice? = { null }
    override fun getUpdatedAllParams(): JoinConsumeRoomParameters = this
}

class JoinConsumeRoomTest {

    @Test
    fun joinConsumeRoom_initializesReceiveAllPipedTransportsImmediately() = runTest {
        val socket = JoinConsumeRoomSocket()
        val parameters = TestJoinConsumeRoomParameters()

        joinConsumeRoom(
            JoinConsumeRoomOptions(
                remoteSock = socket,
                apiToken = "token-1",
                apiUserName = "user-1",
                parameters = parameters
            )
        )

        assertEquals(1, parameters.receiveAllCalls.size)
        assertEquals(1, socket.ackCalls.size)
        assertEquals("joinConRoom", socket.ackCalls.first().first)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun scheduleReceiveAllPipedTransportsRetry_runsDelayedBootstrap() = runTest {
        val socket = JoinConsumeRoomSocket()
        val parameters = TestJoinConsumeRoomParameters()

        scheduleReceiveAllPipedTransportsRetry(
            remoteSock = socket,
            parametersProvider = { parameters },
            receiveAllPipedTransports = parameters.receiveAllPipedTransports,
            delayMs = 30_000L,
            scope = this
        )

        runCurrent()
        assertEquals(0, parameters.receiveAllCalls.size)

        advanceTimeBy(30_000L)
        runCurrent()

        assertEquals(1, parameters.receiveAllCalls.size)
    }
}