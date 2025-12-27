package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.network.httpClient
import com.mediasfu.sdk.network.mediaSfuJson
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

/**
 * Options for joining a room on MediaSFU.
 *
 * @property action The action to perform ('join')
 * @property meetingID The ID of the meeting to join
 * @property userName The username of the user joining
 * @property adminPasscode The admin passcode for the room (optional)
 * @property islevel The access level
 */
@Serializable
data class JoinMediaSFURoomOptions(
    val action: String,
    val meetingID: String,
    val userName: String,
    val adminPasscode: String? = null,
    val islevel: String = "0"
)

/**
 * Options for joining a room on MediaSFU.
 *
 * @property payload The room join payload
 * @property apiUserName The API username
 * @property apiKey The API key
 * @property localLink The local link for community edition servers
 */
data class JoinMediaSFUOptions(
    val payload: JoinMediaSFURoomOptions,
    val apiUserName: String,
    val apiKey: String,
    val localLink: String = ""
)

/**
 * Sends a request to join an existing room on MediaSFU.
 *
 * This function validates the provided credentials and dynamically determines
 * the endpoint based on the `localLink`. It performs an HTTP POST request
 * with the provided payload and returns the response or an error.
 *
 * ### Parameters:
 * - `payload` (JoinMediaSFURoomOptions): The payload containing room join details.
 * - `apiUserName` (String): The API username used for authentication.
 * - `apiKey` (String): The API key for authentication (must be exactly 64 characters).
 * - `localLink` (String, optional): A local link for community edition servers.
 *
 * ### Returns:
 * - A `CreateJoinRoomResult` containing:
 *   - `success` (Boolean): Indicates whether the request was successful.
 *   - `data` (CreateJoinRoomResponse | CreateJoinRoomError): The response data or error details.
 *
 * ### Example Usage:
 * ```kotlin
 * val payload = JoinMediaSFURoomOptions(
 *     action = "join",
 *     meetingID = "testRoom123",
 *     userName = "user123",
 *     adminPasscode = "admin123",
 *     islevel = "0"
 * )
 *
 * val options = JoinMediaSFUOptions(
 *     payload = payload,
 *     apiUserName = "username",
 *     apiKey = "your-64-character-api-key",
 *     localLink = ""
 * )
 *
 * val result = joinRoomOnMediaSfu(options)
 * ```
 *
 * ### Platform Notes:
 * - **Android**: Uses Android HTTP client
 * - **iOS**: Uses iOS HTTP client
 * - **Common**: Platform-agnostic interface
 *
 * @param options Configuration options for joining a room on MediaSFU
 * @return CreateJoinRoomResult containing success status and response data
 */
suspend fun joinRoomOnMediaSfu(options: JoinMediaSFUOptions): CreateJoinRoomResult {
    val payload = options.payload
    val apiUserName = options.apiUserName
    val apiKey = options.apiKey
    val localLink = options.localLink

    if (apiKey.length != 64) {
        return CreateJoinRoomResult(
            success = false,
            data = CreateJoinRoomError(
                error = "API key must be exactly 64 characters",
                success = false
            )
        )
    }

    if (payload.meetingID.isBlank()) {
        return CreateJoinRoomResult(
            success = false,
            data = CreateJoinRoomError(
                error = "Meeting ID cannot be empty",
                success = false
            )
        )
    }

    if (payload.userName.isBlank()) {
        return CreateJoinRoomResult(
            success = false,
            data = CreateJoinRoomError(
                error = "User name cannot be empty",
                success = false
            )
        )
    }

    val endpoint = resolveJoinEndpoint(localLink)

    return runCatching {
        httpClient.post(endpoint) {
            header(HttpHeaders.Authorization, "Bearer ${apiUserName}:${apiKey}")
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
    }.fold(
        onSuccess = { response ->
            val bodyText = response.bodyAsText()
            if (response.status.isSuccess()) {
                val data = runCatching {
                    mediaSfuJson.decodeFromString<CreateJoinRoomResponse>(bodyText)
                }.getOrNull()

                if (data != null) {
                    CreateJoinRoomResult(success = true, data = data)
                } else {
                    CreateJoinRoomResult(
                        success = false,
                        data = CreateJoinRoomError(
                            error = "Unable to parse join room response",
                            success = false
                        )
                    )
                }
            } else {
                CreateJoinRoomResult(success = false, data = extractJoinError(bodyText))
            }
        },
        onFailure = { throwable ->
            val error = when (throwable) {
                is ResponseException -> {
                    val errorBody = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    extractJoinError(errorBody)
                }

                else -> CreateJoinRoomError(
                    error = throwable.message ?: "Failed to join room",
                    success = false
                )
            }

            CreateJoinRoomResult(success = false, data = error)
        }
    )
}

private fun resolveJoinEndpoint(localLink: String): String {
    return if (localLink.isNotBlank()) {
        val normalized = localLink.trimEnd('/')
        "$normalized/joinRoom"
    } else {
        "https://mediasfu.com/v1/rooms"
    }
}

private fun extractJoinError(bodyText: String?): CreateJoinRoomError {
    if (bodyText.isNullOrBlank()) {
        return CreateJoinRoomError(error = "Unknown error", success = false)
    }

    val parsed = runCatching {
        mediaSfuJson.decodeFromString<CreateJoinRoomError>(bodyText)
    }.getOrNull()

    return parsed?.copy(success = parsed.success ?: false)
        ?: CreateJoinRoomError(error = bodyText, success = false)
}
