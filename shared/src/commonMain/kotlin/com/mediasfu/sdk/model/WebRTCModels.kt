package com.mediasfu.sdk.model

/**
 * WebRTC-related models for MediaSFU SDK
 * 
 * These models represent the core WebRTC types used throughout the SDK,
 * including Transport, Producer, Consumer, and RTP parameter types.
 * 
 * Reference: mediasoup-client specification and Flutter SDK types
 */

// ============================================================================
// Transport Types
// ============================================================================

/**
 * Represents a WebRTC transport for sending or receiving media
 */
data class Transport(
    val id: String,
    val iceParameters: IceParameters,
    val iceCandidates: List<IceCandidate>,
    val dtlsParameters: DtlsParameters,
    val sctpParameters: SctpParameters? = null,
    val iceState: IceState = IceState.NEW,
    val connectionState: ConnectionState = ConnectionState.NEW,
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * ICE (Interactive Connectivity Establishment) parameters
 */
data class IceParameters(
    val usernameFragment: String,
    val password: String,
    val iceLite: Boolean? = null
)

/**
 * ICE candidate for establishing peer connection
 */
data class IceCandidate(
    val foundation: String,
    val priority: Long,
    val ip: String,
    val protocol: TransportProtocol,
    val port: Int,
    val type: CandidateType,
    val tcpType: TcpType? = null,
    val generation: Int? = null,
    val networkId: Int? = null,
    val networkCost: Int? = null
)

/**
 * DTLS (Datagram Transport Layer Security) parameters
 */
data class DtlsParameters(
    val role: DtlsRole? = null,
    val fingerprints: List<Fingerprint>
)

/**
 * DTLS fingerprint for secure connection
 */
data class Fingerprint(
    val algorithm: String,
    val value: String
)

/**
 * SCTP (Stream Control Transmission Protocol) parameters for data channels
 */
data class SctpParameters(
    val port: Int,
    val OS: Int,
    val MIS: Int,
    val maxMessageSize: Int
)

// ============================================================================
// Producer/Consumer Types
// ============================================================================

/**
 * Represents a media producer (sender)
 */
data class Producer(
    val id: String,
    val kind: MediaKind,
    val rtpParameters: RtpParameters,
    val paused: Boolean = false,
    val maxSpatialLayer: Int? = null,
    val appData: Map<String, Any?> = emptyMap(),
    val producerLabel: String? = null
)

/**
 * Represents a media consumer (receiver)
 */
data class Consumer(
    val id: String,
    val producerId: String,
    val kind: MediaKind,
    val rtpParameters: RtpParameters,
    val paused: Boolean = false,
    val producerPaused: Boolean = false,
    val priority: Int = 1,
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * Data producer for non-media data channels
 */
data class DataProducer(
    val id: String,
    val sctpStreamParameters: SctpStreamParameters,
    val label: String,
    val protocol: String,
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * Data consumer for non-media data channels
 */
data class DataConsumer(
    val id: String,
    val dataProducerId: String,
    val sctpStreamParameters: SctpStreamParameters,
    val label: String,
    val protocol: String,
    val appData: Map<String, Any?> = emptyMap()
)

// ============================================================================
// RTP Types
// ============================================================================

/**
 * RTP parameters for media transmission
 */
data class RtpParameters(
    val mid: String? = null,
    val codecs: List<RtpCodecParameters>,
    val headerExtensions: List<RtpHeaderExtensionParameters> = emptyList(),
    val encodings: List<RtpEncodingParameters> = emptyList(),
    val rtcp: RtcpParameters? = null
)

/**
 * RTP codec parameters
 */
data class RtpCodecParameters(
    val mimeType: String,
    val payloadType: Int,
    val clockRate: Int,
    val channels: Int? = null,
    val parameters: Map<String, Any?>? = null,
    val rtcpFeedback: List<RtcpFeedback>? = null
)

/**
 * RTP header extension parameters
 */
data class RtpHeaderExtensionParameters(
    val uri: String,
    val id: Int,
    val encrypt: Boolean? = null,
    val parameters: Map<String, Any?>? = null
)

/**
 * RTP encoding parameters
 */
data class RtpEncodingParameters(
    val ssrc: Long? = null,
    val rid: String? = null,
    val codecPayloadType: Int? = null,
    val rtx: Rtx? = null,
    val dtx: Boolean? = null,
    val scalabilityMode: String? = null,
    val scaleResolutionDownBy: Double? = null,
    val maxBitrate: Int? = null,
    val maxFramerate: Double? = null,
    val adaptivePtime: Boolean? = null,
    val priority: String? = null,
    val networkPriority: String? = null
)

/**
 * RTX (Retransmission) parameters
 */
data class Rtx(
    val ssrc: Long
)

/**
 * RTCP parameters
 */
data class RtcpParameters(
    val cname: String? = null,
    val reducedSize: Boolean? = null,
    val mux: Boolean? = null
)

/**
 * RTCP feedback parameters
 */
data class RtcpFeedback(
    val type: String,
    val parameter: String? = null
)

/**
 * SCTP stream parameters
 */
data class SctpStreamParameters(
    val streamId: Int,
    val ordered: Boolean = true,
    val maxPacketLifeTime: Int? = null,
    val maxRetransmits: Int? = null
)

// ============================================================================
// RTP Capabilities
// ============================================================================

/**
 * RTP capabilities of a device
 */
data class RtpCapabilities(
    val codecs: List<RtpCodecCapability>,
    val headerExtensions: List<RtpHeaderExtensionCapability> = emptyList(),
    val fecMechanisms: List<String> = emptyList()
)

/**
 * RTP codec capability
 */
data class RtpCodecCapability(
    val kind: MediaKind,
    val mimeType: String,
    val preferredPayloadType: Int? = null,
    val clockRate: Int,
    val channels: Int? = null,
    val parameters: Map<String, Any?>? = null,
    val rtcpFeedback: List<RtcpFeedback>? = null
)

/**
 * RTP header extension capability
 */
data class RtpHeaderExtensionCapability(
    val kind: MediaKind,
    val uri: String,
    val preferredId: Int,
    val preferredEncrypt: Boolean? = null,
    val direction: String? = null
)

// ============================================================================
// Device and Capabilities
// ============================================================================

/**
 * Mediasoup device information
 */
data class DeviceInfo(
    val loaded: Boolean = false,
    val rtpCapabilities: RtpCapabilities? = null,
    val sctpCapabilities: SctpCapabilities? = null,
    val canProduce: Map<MediaKind, Boolean> = emptyMap()
)

/**
 * SCTP capabilities of a device
 */
data class SctpCapabilities(
    val numStreams: NumSctpStreams
)

/**
 * Number of SCTP streams
 */
data class NumSctpStreams(
    val OS: Int,
    val MIS: Int
)

// ============================================================================
// Enums
// ============================================================================

/**
 * Media kind (audio or video)
 */
enum class MediaKind {
    AUDIO,
    VIDEO;

    override fun toString(): String = name.lowercase()
}

/**
 * DTLS role
 */
enum class DtlsRole {
    AUTO,
    CLIENT,
    SERVER;

    override fun toString(): String = name.lowercase()
}

/**
 * ICE connection state
 */
enum class IceState {
    NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED;

    override fun toString(): String = name.lowercase()
}

/**
 * Connection state
 */
enum class ConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    FAILED,
    DISCONNECTED,
    CLOSED;

    override fun toString(): String = name.lowercase()
}

/**
 * Transport protocol
 */
enum class TransportProtocol {
    UDP,
    TCP;

    override fun toString(): String = name.lowercase()
}

/**
 * ICE candidate type
 */
enum class CandidateType {
    HOST,
    SRFLX,  // Server reflexive
    PRFLX,  // Peer reflexive
    RELAY;

    override fun toString(): String = name.lowercase()
}

/**
 * TCP type for TCP candidates
 */
enum class TcpType {
    ACTIVE,
    PASSIVE,
    SO;  // Simultaneous-open

    override fun toString(): String = name.lowercase()
}

// ============================================================================
// Transport Options
// ============================================================================

/**
 * Options for creating a send transport
 */
data class SendTransportOptions(
    val id: String,
    val iceParameters: IceParameters,
    val iceCandidates: List<IceCandidate>,
    val dtlsParameters: DtlsParameters,
    val sctpParameters: SctpParameters? = null,
    val iceServers: List<IceServer> = emptyList(),
    val iceTransportPolicy: IceTransportPolicy = IceTransportPolicy.ALL,
    val additionalSettings: Map<String, Any?> = emptyMap(),
    val proprietaryConstraints: Map<String, Any?> = emptyMap(),
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * Options for creating a receive transport
 */
data class RecvTransportOptions(
    val id: String,
    val iceParameters: IceParameters,
    val iceCandidates: List<IceCandidate>,
    val dtlsParameters: DtlsParameters,
    val sctpParameters: SctpParameters? = null,
    val iceServers: List<IceServer> = emptyList(),
    val iceTransportPolicy: IceTransportPolicy = IceTransportPolicy.ALL,
    val additionalSettings: Map<String, Any?> = emptyMap(),
    val proprietaryConstraints: Map<String, Any?> = emptyMap(),
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * ICE server configuration
 */
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
    val credentialType: CredentialType = CredentialType.PASSWORD
)

/**
 * ICE transport policy
 */
enum class IceTransportPolicy {
    ALL,
    RELAY;

    override fun toString(): String = name.lowercase()
}

/**
 * Credential type for ICE servers
 */
enum class CredentialType {
    PASSWORD,
    OAUTH;

    override fun toString(): String = name.lowercase()
}

// ============================================================================
// Producer/Consumer Options
// ============================================================================

/**
 * Options for producing media
 */
data class ProduceOptions(
    val kind: MediaKind,
    val rtpParameters: RtpParameters,
    val paused: Boolean = false,
    val appData: Map<String, Any?> = emptyMap(),
    val zeroRtpOnPause: Boolean = false,
    val disableTrackOnPause: Boolean = true,
    val stopTracks: Boolean = true
)

/**
 * Options for consuming media
 */
data class ConsumeOptions(
    val id: String,
    val producerId: String,
    val kind: MediaKind,
    val rtpParameters: RtpParameters,
    val paused: Boolean = false,
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * Options for producing data
 */
data class ProduceDataOptions(
    val ordered: Boolean = true,
    val maxPacketLifeTime: Int? = null,
    val maxRetransmits: Int? = null,
    val label: String = "",
    val protocol: String = "",
    val appData: Map<String, Any?> = emptyMap()
)

/**
 * Options for consuming data
 */
data class ConsumeDataOptions(
    val id: String,
    val dataProducerId: String,
    val sctpStreamParameters: SctpStreamParameters,
    val label: String = "",
    val protocol: String = "",
    val appData: Map<String, Any?> = emptyMap()
)

// ============================================================================
// Statistics
// ============================================================================

/**
 * Base statistics for WebRTC components
 */
data class Stats(
    val type: String,
    val timestamp: Long,
    val id: String,
    val values: Map<String, Any?>
)

/**
 * Transport statistics
 */
data class TransportStats(
    val transportId: String,
    val timestamp: Long,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val iceState: IceState,
    val connectionState: ConnectionState,
    val selectedCandidatePair: Map<String, Any?>? = null,
    val availableOutgoingBitrate: Long? = null,
    val availableIncomingBitrate: Long? = null
)

/**
 * Producer statistics
 */
data class ProducerStats(
    val producerId: String,
    val timestamp: Long,
    val kind: MediaKind,
    val mimeType: String,
    val bytesSent: Long = 0,
    val packetsSent: Long = 0,
    val packetsLost: Long = 0,
    val framesEncoded: Long? = null,
    val framesSent: Long? = null,
    val bitrate: Long? = null,
    val roundTripTime: Double? = null
)

/**
 * Consumer statistics
 */
data class ConsumerStats(
    val consumerId: String,
    val timestamp: Long,
    val kind: MediaKind,
    val mimeType: String,
    val bytesReceived: Long = 0,
    val packetsReceived: Long = 0,
    val packetsLost: Long = 0,
    val framesDecoded: Long? = null,
    val framesReceived: Long? = null,
    val framesDropped: Long? = null,
    val bitrate: Long? = null,
    val jitter: Double? = null
)
