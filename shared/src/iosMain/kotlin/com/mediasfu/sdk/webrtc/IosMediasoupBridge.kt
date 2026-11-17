@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.mediasfu.sdk.webrtc

import cocoapods.WebRTC.RTCMediaStreamTrack
import kotlinx.cinterop.ExperimentalForeignApi

interface IosNativeMediasoupBridge {
    fun createSendTransport(params: Map<String, Any?>): IosNativeSendTransportHandle
    fun createRecvTransport(params: Map<String, Any?>): IosNativeRecvTransportHandle
}

interface IosNativeLoadableMediasoupBridge : IosNativeMediasoupBridge {
    fun loadRtpCapabilitiesJson(rtpCapabilitiesJson: String): String?
    fun currentRtpCapabilitiesJson(): String?
}

interface IosNativeTransportHandle {
    val id: String
    fun connectionState(): String
    fun close()
    fun setOnConnect(listener: IosNativeConnectListener?)
    fun setOnConnectionStateChange(listener: ((String) -> Unit)?)
}

/**
 * Optional (turn-relay-v1): a transport handle that can take TURN servers and restart ICE on a live transport.
 * Handles that do not adopt it keep working; the shared relay fallback then reports the relay as unavailable and
 * shows the blocked-network message instead of restarting ICE.
 */
interface IosNativeIceRelayTransportHandle {
    /** True when the handle created the transport with the `iceServers` creation param (relay mode `always`). */
    fun createdWithIceServers(): Boolean

    /** Applies `[{ urls, username, credential }]`. Returns null on success, or an error message. */
    fun updateIceServers(iceServersJson: String): String?

    /** Restarts ICE with the server's ICE parameters. Returns null on success, or an error message. */
    fun restartIce(iceParametersJson: String): String?

    /** WebRTC stats JSON for the transport, or null. */
    fun statsJson(): String?
}

interface IosNativeSendTransportHandle : IosNativeTransportHandle {
    fun setOnProduce(listener: IosNativeProduceListener?)
    fun produce(
        track: RTCMediaStreamTrack,
        encodingsJson: String?,
        codecOptionsJson: String?,
        codecJson: String?,
        appDataJson: String?
    ): IosNativeProducerHandle
}

interface IosNativeRecvTransportHandle : IosNativeTransportHandle {
    fun consume(
        id: String,
        producerId: String,
        kind: String,
        rtpParametersJson: String
    ): IosNativeConsumerHandle
}

interface IosNativeProducerHandle {
    val id: String
    val kind: String
    fun isPaused(): Boolean
    fun close()
    fun pause()
    fun resume()
    fun replaceTrack(track: RTCMediaStreamTrack)
}

interface IosNativeConsumerHandle {
    val id: String
    val kind: String
    val track: RTCMediaStreamTrack?
    fun isPaused(): Boolean
    fun close()
    fun pause()
    fun resume()
}

fun interface IosNativeConnectListener {
    fun onConnect(
        dtlsParametersJson: String,
        callback: () -> Unit,
        errback: (Throwable) -> Unit
    )
}

fun interface IosNativeProduceListener {
    fun onProduce(
        kind: String,
        rtpParametersJson: String,
        appDataJson: String?,
        callback: (String?) -> Unit,
        errback: (Throwable) -> Unit
    )
}

object IosNativeMediasoupBridgeProvider {
    var bridge: IosNativeMediasoupBridge = UnavailableIosNativeMediasoupBridge

    fun install(bridge: IosNativeMediasoupBridge) {
        this.bridge = bridge
    }

    fun reset() {
        bridge = UnavailableIosNativeMediasoupBridge
    }
}

fun installIosNativeMediasoupBridge(bridge: IosNativeMediasoupBridge) {
    IosNativeMediasoupBridgeProvider.install(bridge)
}

fun resetIosNativeMediasoupBridge() {
    IosNativeMediasoupBridgeProvider.reset()
}

fun isIosNativeMediasoupBridgeInstalled(): Boolean {
    return IosNativeMediasoupBridgeProvider.bridge !== UnavailableIosNativeMediasoupBridge
}

fun requireIosNativeMediasoupBridgeInstalled() {
    check(isIosNativeMediasoupBridgeInstalled()) {
        "iOS mediasoup bridge is not installed; call installIosNativeMediasoupBridge(...) during app startup. See IOS_TRANSPORT_BRIDGE_STRATEGY.md"
    }
}

private object UnavailableIosNativeMediasoupBridge : IosNativeMediasoupBridge {
    override fun createSendTransport(params: Map<String, Any?>): IosNativeSendTransportHandle {
        throw IllegalStateException(
            "iOS mediasoup bridge is not installed; see IOS_TRANSPORT_BRIDGE_STRATEGY.md"
        )
    }

    override fun createRecvTransport(params: Map<String, Any?>): IosNativeRecvTransportHandle {
        throw IllegalStateException(
            "iOS mediasoup bridge is not installed; see IOS_TRANSPORT_BRIDGE_STRATEGY.md"
        )
    }
}
