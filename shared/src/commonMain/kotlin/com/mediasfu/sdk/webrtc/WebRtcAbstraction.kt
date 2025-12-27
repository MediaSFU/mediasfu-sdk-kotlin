// WebRtcAbstraction.kt
package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import kotlinx.serialization.Serializable

/**
 * Simplified WebRTC abstraction for MediaSFU SDK.
 * 
 * This provides a basic interface for WebRTC functionality that can be
 * implemented using platform-specific WebRTC libraries later.
 */

/**
 * Platform-agnostic media stream interface.
 * Platform implementations will provide concrete implementations.
 */
interface MediaStream {
    val id: String
    val active: Boolean
    fun getTracks(): List<MediaStreamTrack>
    fun getAudioTracks(): List<MediaStreamTrack>
    fun getVideoTracks(): List<MediaStreamTrack>
    fun addTrack(track: MediaStreamTrack)
    fun removeTrack(track: MediaStreamTrack)
    fun stop()
}

/**
 * Platform-agnostic media stream track interface.
 */
interface MediaStreamTrack {
    val id: String
    val kind: String // "audio" or "video"
    val enabled: Boolean
    
    /**
     * Enables or disables the track.
     * When disabled, audio tracks stop capturing/sending media.
     * When disabled, video tracks stop capturing/sending frames.
     */
    fun setEnabled(enabled: Boolean)
    
    fun stop()

    /**
     * Returns the underlying platform-native track instance when available.
     * This allows UI layers to bind directly to Android/iOS renderers without
     * leaking those dependencies into common code. Implementations that cannot
     * expose a native track may return null.
     */
    fun asPlatformNativeTrack(): Any? = null
}

/**
 * Virtual video source for feeding processed frames into WebRTC.
 * Used for virtual backgrounds where ML Kit processed frames need to be
 * sent to remote participants.
 */
interface VirtualVideoSource {
    /**
     * The MediaStream containing the virtual video track.
     * This stream can be used as virtualStream in PrepopulateUserMedia.
     */
    val stream: MediaStream
    
    /**
     * The video track from this virtual source.
     * This track can be used with WebRtcProducer.replaceTrack().
     */
    val videoTrack: MediaStreamTrack
    
    /**
     * Sends a processed frame to this virtual video source.
     * The frame will be encoded and sent to remote participants.
     * 
     * @param bitmap The processed bitmap (platform-specific, Any for cross-platform)
     * @param timestampNs Timestamp in nanoseconds
     * @param rotation Frame rotation in degrees (0, 90, 180, 270)
     */
    fun onFrame(bitmap: Any, timestampNs: Long, rotation: Int = 0)
    
    /**
     * Starts the virtual video source.
     */
    fun start()
    
    /**
     * Stops the virtual video source.
     */
    fun stop()
    
    /**
     * Releases all resources.
     */
    fun release()
}

/**
 * Representation of a media input or output device.
 */
data class MediaDeviceInfo(
    val deviceId: String,
    val kind: String,
    val label: String? = null,
    val groupId: String? = null
)

/**
 * Basic WebRTC device abstraction.
 */
interface WebRtcDevice {
    /**
     * Loads RTP capabilities for the device.
     */
    suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit>
    
    /**
     * Retrieves a media stream for the given constraints (camera/microphone).
     */
    suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream

    /**
     * Captures the screen for sharing.
     * 
     * On Android, this uses MediaProjection API which requires:
     * - Activity context with MediaProjectionManager
     * - User permission via startActivityForResult
     * - MediaProjection intent data passed to this method
     * 
     * On iOS, this uses ReplayKit.
     * 
     * @param constraints Screen capture constraints including:
     *   - video: Map with width, height, frameRate
     *   - audio: Boolean for system audio capture (platform-dependent)
     * @return MediaStream containing the screen capture track
     * @throws UnsupportedOperationException if screen capture is not supported on this platform
     */
    suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream

    /**
     * Enumerates the available media devices.
     */
    suspend fun enumerateDevices(): List<MediaDeviceInfo>

    /**
     * Creates a send transport from parameters.
     */
    fun createSendTransport(params: Map<String, Any?>): WebRtcTransport
    
    /**
     * Creates a receive transport from parameters.
     */
    fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport

    /**
     * Returns the device's currently loaded RTP capabilities, if available.
     * Implementations can override to provide the platform-specific snapshot;
     * the default returns {@code null} for platforms that do not expose it yet.
     */
    fun currentRtpCapabilities(): RtpCapabilities? = null

    /**
     * Sets the audio output device for playback (speaker, Bluetooth, headphones).
     * 
     * On Android API 31+, this uses AudioManager.setCommunicationDevice().
     * On older Android versions, it uses isSpeakerphoneOn or Bluetooth SCO.
     * 
     * @param deviceId The device ID to route audio to (e.g., "audio_output_123")
     * @return true if the device was successfully set, false otherwise
     */
    suspend fun setAudioOutputDevice(deviceId: String): Boolean = false

    /**
     * Creates a virtual video stream that can receive processed frames.
     * Used for virtual backgrounds where frames are processed by ML Kit and need to be
     * fed back into a WebRTC stream for sending to remote participants.
     * 
     * @param width Width of the video frames
     * @param height Height of the video frames
     * @param frameRate Target frame rate
     * @return A VirtualVideoSource that can receive processed frames and provides a MediaStream
     */
    fun createVirtualVideoSource(width: Int, height: Int, frameRate: Int): VirtualVideoSource? = null

    /**
     * Captures the whiteboard canvas as a video stream for recording.
     * Similar to HTML Canvas's captureStream(30) API in web browsers.
     * 
     * This renders the whiteboard shapes to a bitmap at the specified frame rate
     * and converts it to a WebRTC video stream.
     * 
     * @param shapesProvider Lambda that returns the current list of whiteboard shapes
     * @param useImageBackgroundProvider Lambda that returns whether to use image background (grid)
     * @param width Width of the capture (default 1280)
     * @param height Height of the capture (default 720)
     * @param frameRate Target frame rate (default 30fps)
     * @return MediaStream containing the whiteboard video track
     */
    suspend fun captureWhiteboardStream(
        shapesProvider: () -> List<Any>,
        useImageBackgroundProvider: () -> Boolean = { false },
        width: Int = 1280,
        height: Int = 720,
        frameRate: Int = 30
    ): MediaStream? = null

    /**
     * Releases device resources.
     */
    fun close()
}

/**
 * Basic WebRTC transport abstraction.
 */
interface WebRtcTransport {
    val id: String
    val type: TransportType
    val connectionState: TransportConnectionState
    
    fun close()
    fun onConnect(handler: (ConnectData) -> Unit)
    fun onProduce(handler: (ProduceData) -> Unit)
    fun onConnectionStateChange(handler: (String) -> Unit)

    fun produce(
        track: MediaStreamTrack,
        encodings: List<RtpEncodingParameters> = emptyList(),
        codecOptions: ProducerCodecOptions? = null,
        appData: Map<String, Any?>? = null
    ): WebRtcProducer
    
    /**
     * Consume media from a remote producer (only for RECEIVE transports).
     * @param id Consumer ID from server
     * @param producerId Remote producer ID
     * @param kind Media kind ("audio" or "video")
     * @param rtpParameters RTP parameters from server
     * @return WebRtcConsumer instance
     */
    fun consume(
        id: String,
        producerId: String,
        kind: String,
        rtpParameters: Map<String, Any?>
    ): WebRtcConsumer
}

/**
 * Basic WebRTC producer abstraction.
 */
interface WebRtcProducer {
    val id: String
    val kind: MediaKind
    val source: ProducerSource
    val paused: Boolean
    
    fun close()
    fun pause()
    fun resume()
    
    /**
     * Replaces the track being transmitted by this producer.
     * This is essential for camera switching - the new camera's track
     * must be sent to mediasoup so remote participants receive the new feed.
     * 
     * @param track The new MediaStreamTrack to transmit. For video producers,
     *              this should be the video track from the new camera stream.
     */
    fun replaceTrack(track: MediaStreamTrack)
}

/**
 * Basic WebRTC consumer abstraction.
 */
interface WebRtcConsumer {
    val id: String
    val kind: MediaKind
    val track: MediaStreamTrack?
    val stream: MediaStream?
    val paused: Boolean
    
    fun close()
    fun pause()
    fun resume()

    /**
     * Optional hint to select a simulcast/SVC layer. Default no-op for platforms that do not support it yet.
     */
    fun setPreferredLayers(spatialLayer: Int, temporalLayer: Int) {}

    /**
     * Optional consumer priority setter. Default no-op where unsupported.
     */
    fun setPriority(priority: Int) {}

    /**
     * Requests an intra (key) frame from the remote producer. Default no-op where unsupported.
     */
    fun requestKeyFrame() {}
}

// Type aliases for convenience
typealias Consumer = WebRtcConsumer
typealias Producer = WebRtcProducer

/**
 * Optional capability for providing inbound audio statistics from a consumer.
 */
interface AudioStatsProvider {
    suspend fun getInboundAudioStats(): AudioInboundStats?
}

/**
 * Snapshot of inbound audio statistics for a consumer.
 */
data class AudioInboundStats(
    val packetsReceived: Long,
    val packetsLost: Long,
    val audioLevel: Double?,
    val totalAudioEnergy: Double?,
    val totalSamplesDuration: Double?,
    val concealedSamples: Double?,
    val jitter: Double?,
    val bytesReceived: Long? = null,
    val lastPacketReceivedTimestamp: Double? = null,
    val ssrc: Long? = null,
    val jitterBufferDelay: Double? = null,
    val jitterBufferEmittedCount: Long? = null,
    val framesDecoded: Long? = null,
    val trackIdentifier: String? = null,
    val mid: String? = null
)

/**
 * Transport types.
 */
@Serializable
enum class TransportType {
    SEND,
    RECEIVE
}

/**
 * Transport connection states.
 */
@Serializable
enum class TransportConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED
}

/**
 * Media kinds.
 */
@Serializable
enum class MediaKind {
    AUDIO,
    VIDEO
}

/**
 * Producer sources.
 */
@Serializable
enum class ProducerSource {
    CAMERA,
    MICROPHONE,
    SCREEN
}

/**
 * Data passed to connect event handler.
 */
data class ConnectData(
    val dtlsParameters: DtlsParameters,
    val callback: () -> Unit,
    val errback: (Throwable) -> Unit
)

/**
 * Data passed to produce event handler.
 */
data class ProduceData(
    val kind: MediaKind,
    val rtpParameters: RtpParameters,
    val appData: Map<String, Any?>?,
    val callback: (String?) -> Unit,
    val errback: (Throwable) -> Unit
)

/**
 * RTP capabilities for device loading.
 */
@Serializable
data class RtpCapabilities(
    val codecs: List<RtpCodecCapability> = emptyList(),
    val headerExtensions: List<RtpHeaderExtension> = emptyList(),
    val fecMechanisms: List<String> = emptyList()
)

/**
 * RTP codec capability.
 */
@Serializable
data class RtpCodecCapability(
    val kind: MediaKind,
    val mimeType: String,
    val preferredPayloadType: Int? = null,
    val clockRate: Int,
    val channels: Int? = null,
    val parameters: Map<String, String> = emptyMap(),
    val rtcpFeedback: List<RtcpFeedback> = emptyList()
)

/**
 * RTP header extension.
 */
@Serializable
data class RtpHeaderExtension(
    val kind: MediaKind? = null,
    val uri: String,
    val preferredId: Int,
    val preferredEncrypt: Boolean = false,
    val direction: RtpHeaderDirection? = null
)

/**
 * RTP header extension direction.
 */
@Serializable
enum class RtpHeaderDirection {
    SENDRECV,
    SENDONLY,
    RECVONLY,
    INACTIVE
}

/**
 * DTLS parameters for transport connection.
 */
@Serializable
data class DtlsParameters(
    val role: DtlsRole = DtlsRole.AUTO,
    val fingerprints: List<DtlsFingerprint> = emptyList()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "role" to role.name.lowercase(),
            "fingerprints" to fingerprints.map { it.toMap() }
        )
    }
}

/**
 * DTLS role.
 */
@Serializable
enum class DtlsRole {
    AUTO,
    CLIENT,
    SERVER
}

/**
 * DTLS fingerprint.
 */
@Serializable
data class DtlsFingerprint(
    val algorithm: String,
    val value: String
) {
    fun toMap(): Map<String, String> {
        return mapOf(
            "algorithm" to algorithm,
            "value" to value
        )
    }
}

/**
 * RTP parameters for media production.
 */
@Serializable
data class RtpParameters(
    val mid: String? = null,
    val codecs: List<RtpCodecParameters> = emptyList(),
    val headerExtensions: List<RtpHeaderExtensionParameters> = emptyList(),
    val encodings: List<RtpEncodingParameters> = emptyList(),
    val rtcp: RtcpParameters? = null
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "mid" to mid,
            "codecs" to codecs.map { it.toMap() },
            "headerExtensions" to headerExtensions.map { it.toMap() },
            "encodings" to encodings.map { it.toMap() },
            "rtcp" to rtcp?.toMap()
        )
    }
}

/**
 * RTP codec parameters.
 */
@Serializable
data class RtpCodecParameters(
    val mimeType: String,
    val payloadType: Int,
    val clockRate: Int,
    val channels: Int? = null,
    val parameters: Map<String, String> = emptyMap(),
    val rtcpFeedback: List<RtcpFeedback> = emptyList()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "mimeType" to mimeType,
            "payloadType" to payloadType,
            "clockRate" to clockRate,
            "channels" to channels,
            "parameters" to parameters,
            "rtcpFeedback" to rtcpFeedback.map { it.toMap() }
        )
    }
}

/**
 * RTP header extension parameters.
 */
@Serializable
data class RtpHeaderExtensionParameters(
    val uri: String,
    val id: Int,
    val encrypt: Boolean = false,
    val parameters: Map<String, String> = emptyMap()
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "uri" to uri,
            "id" to id,
            "encrypt" to encrypt,
            "parameters" to parameters
        )
    }
}

/**
 * RTP encoding parameters.
 */
@Serializable
data class RtpEncodingParameters(
    val ssrc: Long? = null,
    val rid: String? = null,
    val active: Boolean = true,  // CRITICAL: Must be true for encoding to be used
    val maxBitrate: Int? = null,
    val minBitrate: Int? = null,
    val maxFramerate: Double? = null,
    val scalabilityMode: String? = null,
    val scaleResolutionDownBy: Double? = null,
    val dtx: Boolean? = null,
    val networkPriority: String? = null
) {
    /**
     * Convert to a map, omitting null values to match JavaScript behavior.
     * In JavaScript, undefined fields are stripped from JSON serialization.
     * This is critical for WebRTC/mediasoup compatibility - sending null 
     * for ssrc when using rid-based simulcast causes routing failures.
     */
    fun toMap(): Map<String, Any?> {
        return buildMap {
            ssrc?.let { put("ssrc", it) }  // Only include if present
            rid?.let { put("rid", it) }     // Only include if present
            put("active", active)           // Always include - server needs this
            maxBitrate?.let { put("maxBitrate", it) }
            minBitrate?.let { put("minBitrate", it) }
            maxFramerate?.let { put("maxFramerate", it) }
            scalabilityMode?.let { put("scalabilityMode", it) }
            scaleResolutionDownBy?.let { put("scaleResolutionDownBy", it) }
            dtx?.let { put("dtx", it) }
            // NOTE: networkPriority deliberately excluded - not needed for server
        }
    }
}

/**
 * RTCP feedback parameters.
 */
@Serializable
data class RtcpFeedback(
    val type: String,
    val parameter: String? = null
) {
    fun toMap(): Map<String, String?> = mapOf(
        "type" to type,
        "parameter" to parameter
    )
}

/**
 * RTCP parameters block.
 */
@Serializable
data class RtcpParameters(
    val cname: String? = null,
    val reducedSize: Boolean? = null,
    val mux: Boolean? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "cname" to cname,
        "reducedSize" to reducedSize,
        "mux" to mux
    )
}

