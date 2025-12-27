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
 * Options for creating a room on MediaSFU.
 *
 * @property action The action to perform ('create')
 * @property duration The duration of the room in minutes
 * @property capacity The maximum capacity of the room
 * @property userName The username of the room creator
 * @property scheduledDate Unix timestamp (in milliseconds) for the scheduled date (optional)
 * @property secureCode Secure code for the room host (optional)
 * @property eventType The type of event (optional)
 * @property recordOnly Whether the room is for media production only (optional)
 * @property safeRoom Whether the room is a safe room (optional)
 * @property autoStartSafeRoom Automatically start the safe room feature (optional)
 * @property safeRoomAction Action for the safe room (optional)
 * @property dataBuffer Whether to return data buffer (optional)
 * @property bufferType Type of buffer data (optional)
 */
@Serializable
data class CreateMediaSFURoomOptions(
    val action: String,
    val duration: Int,
    val capacity: Int,
    val userName: String,
    val scheduledDate: Long? = null,
    val secureCode: String? = null,
    val eventType: String? = null,
    val roomName: String? = null,
    val adminPasscode: String? = null,
    val islevel: String? = null,
    val recordOnly: Boolean? = false,
    val safeRoom: Boolean? = false,
    val autoStartSafeRoom: Boolean? = false,
    val safeRoomAction: String? = "kick",
    val dataBuffer: Boolean? = false,
    val bufferType: String? = "all"
)

/**
 * Options for creating a room on MediaSFU.
 *
 * @property payload The room creation payload
 * @property apiUserName The API username
 * @property apiKey The API key
 * @property localLink The local link for community edition servers
 */
data class CreateMediaSFUOptions(
    val payload: CreateMediaSFURoomOptions,
    val apiUserName: String,
    val apiKey: String,
    val localLink: String = ""
)

/**
 * Sends a request to create a new room on MediaSFU.
 *
 * This function validates the provided credentials and dynamically determines
 * the endpoint based on the `localLink`. It performs an HTTP POST request
 * with the given payload and returns the response or an error.
 *
 * ### Parameters:
 * - `payload` (CreateMediaSFURoomOptions): The payload containing room creation details.
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
 * val payload = CreateMediaSFURoomOptions(
 *     action = "create",
 *     duration = 60,
 *     capacity = 10,
 *     userName = "hostUser",
 *     roomName = "my-room",
 *     adminPasscode = "admin123",
 *     islevel = "0",
 *     eventType = "conference"
 * )
 *
 * val options = CreateMediaSFUOptions(
 *     payload = payload,
 *     apiUserName = "username",
 *     apiKey = "your-64-character-api-key",
 *     localLink = ""
 * )
 *
 * val result = createRoomOnMediaSfu(options)
 * ```
 *
 * ### Platform Notes:
 * - **Android**: Uses Android HTTP client
 * - **iOS**: Uses iOS HTTP client
 * - **Common**: Platform-agnostic interface
 *
 * @param options Configuration options for creating a room on MediaSFU
 * @return CreateJoinRoomResult containing success status and response data
 */
suspend fun createRoomOnMediaSfu(options: CreateMediaSFUOptions): CreateJoinRoomResult {
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

    if (payload.userName.isBlank()) {
        return CreateJoinRoomResult(
            success = false,
            data = CreateJoinRoomError(
                error = "User name cannot be empty",
                success = false
            )
        )
    }

    val endpoint = resolveCreateEndpoint(localLink)

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
                            error = "Unable to parse create room response",
                            success = false
                        )
                    )
                }
            } else {
                CreateJoinRoomResult(
                    success = false,
                    data = extractError(bodyText)
                )
            }
        },
        onFailure = { throwable ->
            val error = when (throwable) {
                is ResponseException -> {
                    val errorBody = runCatching { throwable.response.bodyAsText() }.getOrNull()
                    extractError(errorBody)
                }

                else -> CreateJoinRoomError(
                    error = throwable.message ?: "Failed to create room",
                    success = false
                )
            }

            CreateJoinRoomResult(success = false, data = error)
        }
    )
}

private fun resolveCreateEndpoint(localLink: String): String {
    return if (localLink.isNotBlank()) {
        val normalized = localLink.trimEnd('/')
        "$normalized/createRoom"
    } else {
        "https://mediasfu.com/v1/rooms/"
    }
}

private fun extractError(bodyText: String?): CreateJoinRoomError {
    if (bodyText.isNullOrBlank()) {
        return CreateJoinRoomError(error = "Unknown error", success = false)
    }

    val parsed = runCatching {
        mediaSfuJson.decodeFromString<CreateJoinRoomError>(bodyText)
    }.getOrNull()

    return parsed?.copy(success = parsed.success ?: false)
        ?: CreateJoinRoomError(error = bodyText, success = false)
}
