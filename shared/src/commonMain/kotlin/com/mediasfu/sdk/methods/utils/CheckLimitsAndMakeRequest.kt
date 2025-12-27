package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.socket.SocketManager
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal const val RATE_LIMIT_WINDOW_MS = 60_000L
internal const val RATE_LIMIT_MAX_REQUESTS = 5

internal object CheckLimitsRateLimiter {
    private val requestLog: MutableMap<String, MutableList<Long>> = mutableMapOf()
    private val mutex = Mutex()

    suspend fun allow(username: String, currentTime: Long = Clock.System.now().toEpochMilliseconds()): Boolean {
        return mutex.withLock {
            val history = requestLog.getOrPut(username) { mutableListOf() }
            history.removeAll { currentTime - it > RATE_LIMIT_WINDOW_MS }
            if (history.size >= RATE_LIMIT_MAX_REQUESTS) {
                return@withLock false
            }
            history.add(currentTime)
            return@withLock true
        }
    }

    suspend fun reset() {
        mutex.withLock {
            requestLog.clear()
        }
    }
}

/**
 * Parameters for checking limits and making requests.
 *
 * @property apiUserName The API username
 * @property apiToken The API token
 * @property link The link to connect
 * @property userName The user's display name
 * @property parameters The parameters for callbacks and socket handling
 * @property validate Whether to validate the primary socket connection
 */
interface CheckLimitsAndMakeRequestParameters {
    val apiUserName: String
    val apiToken: String
    val link: String
    val userName: String
    val validate: Boolean
    
    // Socket management
    val socket: SocketManager?
    val localSocket: SocketManager?
    
    // Update functions
    val updateSocket: (SocketManager?) -> Unit
    val updateLocalSocket: (SocketManager?) -> Unit
    
    // Connection functions
    val connectSocket: suspend (String, String, String, String) -> SocketManager?
    
    fun getUpdatedAllParams(): CheckLimitsAndMakeRequestParameters
}

/**
 * Options for checking limits and making requests.
 *
 * @property apiUserName The API username
 * @property apiToken The API token
 * @property link The link to connect
 * @property userName The user's display name
 * @property parameters The parameters for callbacks and socket handling
 * @property validate Whether to validate the primary socket connection
 */
data class CheckLimitsAndMakeRequestOptions(
    val apiUserName: String,
    val apiToken: String,
    val link: String,
    val userName: String,
    val parameters: CheckLimitsAndMakeRequestParameters,
    val validate: Boolean = true
)

/**
 * Checks for rate limits and establishes a socket connection if permissible.
 *
 * ### Parameters:
 * - `apiUserName` (String): The API username.
 * - `apiToken` (String): The API token.
 * - `link` (String): The link to connect.
 * - `userName` (String): The user's display name.
 * - `parameters` (CheckLimitsAndMakeRequestParameters): The parameters for callbacks and socket handling.
 * - `validate` (Boolean): Whether to validate the primary socket connection.
 *
 * ### Overview:
 * 1. **Rate Limiting**: Checks if the user has exceeded rate limits
 * 2. **Validation**: Validates API credentials and connection parameters
 * 3. **Socket Connection**: Establishes socket connection if permissible
 * 4. **Error Handling**: Provides fallback behavior for errors
 *
 * ### Example Usage:
 * ```kotlin
 * val options = CheckLimitsAndMakeRequestOptions(
 *     apiUserName = "user123",
 *     apiToken = "token456",
 *     link = "https://mediasfu.com/room/abc123",
 *     userName = "John Doe",
 *     parameters = checkLimitsParameters,
 *     validate = true
 * )
 * checkLimitsAndMakeRequest(options)
 * ```
 *
 * ### Platform Notes:
 * - **Android**: Uses Android-specific networking and storage
 * - **iOS**: Uses iOS-specific networking and storage
 * - **Common**: Core logic is platform-agnostic
 *
 * @param options Configuration options for checking limits and making requests
 */
suspend fun checkLimitsAndMakeRequest(options: CheckLimitsAndMakeRequestOptions) {
    try {
        val apiUserName = options.apiUserName
        val apiToken = options.apiToken
        val link = options.link
        val userName = options.userName
        val parameters = options.parameters
        val validate = options.validate
        
        // Validate input parameters
        if (apiUserName.isBlank()) {
            return
        }
        
        if (apiToken.isBlank()) {
            return
        }
        
        if (link.isBlank()) {
            return
        }
        
        if (userName.isBlank()) {
            return
        }
        
        if (!CheckLimitsRateLimiter.allow(apiUserName)) {
            return
        }

        val updatedParams = parameters.getUpdatedAllParams()
        val existingSocket = updatedParams.socket
        val existingLocalSocket = updatedParams.localSocket
        val isLocalLink = !link.contains("mediasfu.com", ignoreCase = true)

        if (validate && existingSocket?.isConnected() == true) {
            return
        }

        delay(50)

        val primarySocket = updatedParams.connectSocket(apiUserName, apiToken, link, userName)
        if (primarySocket == null) {
            Logger.e("CheckLimitsAndMakeRe", "Failed to establish primary socket connection")
            return
        }
        updatedParams.updateSocket(primarySocket)

        if (isLocalLink && (existingLocalSocket == null || existingLocalSocket.isConnected().not())) {
            val localSocket = updatedParams.connectSocket(apiUserName, apiToken, link, userName)
            if (localSocket != null) {
                updatedParams.updateLocalSocket(localSocket)
            } else {
                Logger.e("CheckLimitsAndMakeRe", "Failed to establish local socket connection")
            }
        } else if (!isLocalLink && existingLocalSocket != null) {
        }

        
    } catch (e: Exception) {
        Logger.e("CheckLimitsAndMakeRe", "Error checking limits and making request: ${e.message}")
    }
}
