package com.mediasfu.sdk.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

internal actual fun createPlatformHttpClient(): HttpClient = HttpClient(OkHttp) {
    install(ContentNegotiation) {
        json(mediaSfuJson)
    }
    defaultRequest {
        header(HttpHeaders.Accept, "application/json")
    }
}
