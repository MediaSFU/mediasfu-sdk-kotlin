package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.socket.ConnectionState
import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeSocketManager : SocketManager {
    override val id: String? = "fake"
    var connected: Boolean = false

    override suspend fun connect(url: String, config: SocketConfig): Result<Unit> {
        connected = true
        return Result.success(Unit)
    }

    override suspend fun disconnect(): Result<Unit> {
        connected = false
        return Result.success(Unit)
    }

    override fun isConnected(): Boolean = connected

    override fun getConnectionState(): ConnectionState =
        if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED

    override suspend fun emit(event: String, data: Map<String, Any?>) {}

    override suspend fun <T> emitWithAck(event: String, data: Map<String, Any?>, timeout: Long): T {
        throw NotImplementedError("Ack not supported in fake")
    }

    override fun emitWithAck(event: String, data: Map<String, Any?>, callback: (Any?) -> Unit) {}

    override fun on(event: String, handler: suspend (Map<String, Any?>) -> Unit) {}

    override fun off(event: String) {}

    override fun offAll() {}

    override fun onConnect(handler: suspend () -> Unit) {}

    override fun onDisconnect(handler: suspend (String) -> Unit) {}

    override fun onError(handler: suspend (Throwable) -> Unit) {}

    override fun onReconnect(handler: suspend (Int) -> Unit) {}

    override fun onReconnectAttempt(handler: suspend (Int) -> Unit) {}

    override fun onReconnectFailed(handler: suspend () -> Unit) {}
}

private class TestParameters(
    override val validate: Boolean = true,
    private val storeSockets: Boolean = true
) : CheckLimitsAndMakeRequestParameters {
    var socketState: SocketManager? = null
    var localSocketState: SocketManager? = null
    var connectionAttempts = 0

    override val apiUserName: String = "user"
    override val apiToken: String = "token"
    override val link: String = "link"
    override val userName: String = "user"

    override val socket: SocketManager?
        get() = socketState

    override val localSocket: SocketManager?
        get() = localSocketState

    override val updateSocket: (SocketManager?) -> Unit = {
        if (storeSockets) {
            socketState = it
        }
    }

    override val updateLocalSocket: (SocketManager?) -> Unit = {
        if (storeSockets) {
            localSocketState = it
        }
    }

    override val connectSocket: suspend (String, String, String, String) -> SocketManager? = { _, _, _, _ ->
        connectionAttempts += 1
        FakeSocketManager().apply { connected = true }
    }

    override fun getUpdatedAllParams(): CheckLimitsAndMakeRequestParameters = this
}

class CheckLimitsAndMakeRequestTest {

    @AfterTest
    fun tearDown() {
        CheckLimitsRateLimiter.reset()
    }

    @Test
    fun `creates sockets when none exist`() = runTest {
        val params = TestParameters()
        val options = CheckLimitsAndMakeRequestOptions(
            apiUserName = "api",
            apiToken = "token",
            link = "link",
            userName = "user",
            parameters = params
        )

        checkLimitsAndMakeRequest(options)

        assertNotNull(params.socketState)
        assertTrue(params.socketState?.isConnected() == true)
        assertNotNull(params.localSocketState)
        assertEquals(2, params.connectionAttempts)
    }

    @Test
    fun `skips connection when socket already valid`() = runTest {
        val params = TestParameters().apply {
            socketState = FakeSocketManager().apply { connected = true }
        }
        val options = CheckLimitsAndMakeRequestOptions(
            apiUserName = "api",
            apiToken = "token",
            link = "link",
            userName = "user",
            parameters = params,
            validate = true
        )

        checkLimitsAndMakeRequest(options)

        assertEquals(0, params.connectionAttempts)
        assertNotNull(params.socketState)
    }

    @Test
    fun `enforces rate limiting`() = runTest {
        val params = TestParameters(validate = false, storeSockets = false)
        val options = CheckLimitsAndMakeRequestOptions(
            apiUserName = "api",
            apiToken = "token",
            link = "link",
            userName = "user",
            parameters = params
        )

        repeat(RATE_LIMIT_MAX_REQUESTS) {
            checkLimitsAndMakeRequest(options)
        }

        val previousAttempts = params.connectionAttempts
        checkLimitsAndMakeRequest(options)

        assertEquals(previousAttempts, params.connectionAttempts)
        assertNull(params.socketState)
    }
}
