package com.mediasfu.sdk.network

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

internal val mediaSfuJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

internal val httpClient: HttpClient by lazy { createPlatformHttpClient() }

internal expect fun createPlatformHttpClient(): HttpClient
