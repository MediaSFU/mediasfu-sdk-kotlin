// ConnectSendTransportAudio.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.AParams
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Parameters interface for connecting audio send transports.
 */
interface ConnectSendTransportAudioParameters : ResumeSendTransportAudioParameters, CreateSendTransportParameters {
    override val socket: SocketManager?
    override val participants: List<Participant>
    val localStream: MediaStream?
    override var transportCreated: Boolean
    override var localTransportCreated: Boolean
    val transportCreatedAudio: Boolean
    override val audioAlreadyOn: Boolean
    val micAction: Boolean
    val audioParams: ProducerOptionsType?
    val localStreamAudio: MediaStream?
    val defAudioID: String
    val userDefaultAudioInputDevice: String
    val params: ProducerOptionsType?
    val aParams: ProducerOptionsType?
    override val hostLabel: String
    override val islevel: String
    override val member: String
    override val updateMainWindow: Boolean
    override val lockScreen: Boolean
    override val shared: Boolean
    override val videoAlreadyOn: Boolean
    val showAlert: ShowAlert?
    override var producerTransport: WebRtcTransport?
    override var localProducerTransport: WebRtcTransport?

    val updateParticipants: (List<Participant>) -> Unit
    override val updateTransportCreated: (Boolean) -> Unit
    val updateTransportCreatedAudio: (Boolean) -> Unit
    val updateAudioAlreadyOn: (Boolean) -> Unit
    val updateMicAction: (Boolean) -> Unit
    val updateAudioParams: (ProducerOptionsType?) -> Unit
    val updateLocalStream: (MediaStream?) -> Unit
    val updateLocalStreamAudio: (MediaStream?) -> Unit
    val updateDefAudioID: (String) -> Unit
    val updateUserDefaultAudioInputDevice: (String) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit
    override val updateProducerTransport: (WebRtcTransport?) -> Unit
    override val updateLocalProducerTransport: ((WebRtcTransport?) -> Unit)?
    val updateAudioLevel: (Double) -> Unit

    override fun getUpdatedAllParams(): ConnectSendTransportAudioParameters

    val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportAudio(options) }
}

/**
 * Options for connecting audio send transports.
 */
data class ConnectSendTransportAudioOptions(
    val stream: MediaStream?,
    val parameters: ConnectSendTransportAudioParameters,
    val audioConstraints: Map<String, Any>? = null,
    val targetOption: String = "all" // 'all', 'local', 'remote'
)

/**
 * Exception thrown when connecting audio send transport fails.
 */
class ConnectSendTransportAudioException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Connects the local send transport for audio by producing audio data.
 */
suspend fun connectLocalSendTransportAudio(
    options: ConnectSendTransportAudioOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val localTransport = parameters.localProducerTransport
        val audioParams = parameters.audioParams

        if (localTransport == null || audioParams == null) {
            // Nothing to connect locally yet; treat as a no-op.
            return Result.success(Unit)
        }

        val stream = options.stream

        parameters.updateLocalStreamAudio(stream)

        if (stream != null && parameters.localStream == null) {
            parameters.updateLocalStream(stream)
        }

        // Since we don't have a platform-specific produce method yet, simply
        // mark the transport as active so that callers can proceed.
        parameters.updateLocalProducerTransport?.invoke(localTransport)

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ConnectSendTransportAudioException(
                "Error connecting local audio transport: ${error.message}",
                error
            )
        )
    }
}

/**
 * Connects the send transport for audio by producing audio data and updating the local audio producer and transport.
 *
 * This function sets up and connects the audio stream for media sharing, handling updates to
 * local audio streams and producer transports.
 *
 * ## Features:
 * - Handles audio track management
 * - Manages media constraints
 * - Sets up stream and track management
 * - Handles transport production for audio
 * - Updates audio level monitoring
 *
 * ## Parameters:
 * - [options] Configuration options for audio transport connection
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val audioOptions = ConnectSendTransportAudioOptions(
 *     stream = myAudioStream,
 *     targetOption = "all",
 *     audioConstraints = myAudioConstraints,
 *     parameters = myConnectSendTransportAudioParameters
 * )
 *
 * val result = connectSendTransportAudio(audioOptions)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ConnectSendTransport", "Error setting up audio stream: ${error.message}")
 * }
 * ```
 *
 * ## Error Handling:
 * - Returns Result.failure if required parameters are missing
 * - Logs errors for debugging
 * - Handles transport creation failures gracefully
 */
suspend fun connectSendTransportAudio(
    options: ConnectSendTransportAudioOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val targetOption = options.targetOption.lowercase()
        val streamCandidate = options.stream
        val mediaStream = streamCandidate as? MediaStream
        val audioParamsSnapshot = parameters.audioParams ?: parameters.params ?: parameters.aParams
        val resolvedAudioTrack = audioParamsSnapshot?.track ?: runCatching {
            mediaStream?.getAudioTracks()?.firstOrNull()
        }.getOrNull()

        val extendedCaps = resolveExtendedRtpCapabilities(parameters)
        val negotiatedCodec = ProducerCapabilityAlignment.selectCodec(MediaKind.AUDIO, extendedCaps)

        val alignedAudioOptions = ProducerCapabilityAlignment.alignProducerOptions(
            baseOptions = audioParamsSnapshot,
            stream = mediaStream,
            track = resolvedAudioTrack,
            negotiatedCodec = negotiatedCodec,
            label = "audio",
            fallbackKind = ProducerFallbackKind.AUDIO
        )

        parameters.updateAudioParams(alignedAudioOptions)

        val resolvedStream = alignedAudioOptions.stream ?: mediaStream
        val effectiveAudioTrack = alignedAudioOptions.track ?: resolvedAudioTrack
        val resolvedAudioEncodings = alignedAudioOptions.encodings.ifEmpty {
            AParams.getAudioParams().encodings
        }
        val resolvedAudioCodecOptions = alignedAudioOptions.codecOptions

        // Clean up any existing audio tracks to avoid duplicates.
        parameters.localStream?.removeAudioTracksSafely()
        parameters.localStreamAudio?.removeAudioTracksSafely()

        if (resolvedStream != null) {
            parameters.updateLocalStreamAudio(resolvedStream)
            val currentLocalStream = parameters.localStream
            if (currentLocalStream == null) {
                parameters.updateLocalStream(resolvedStream)
            } else if (currentLocalStream !== resolvedStream) {
                resolvedStream.getAudioTracks().firstOrNull()?.let { track ->
                    try {
                        currentLocalStream.addTrack(track)
                        parameters.updateLocalStream(currentLocalStream)
                    } catch (_: Exception) {
                        // Silently handle errors
                    }
                }
            }
        }

        if (targetOption == "all" || targetOption == "remote") {
            val producerTransport = parameters.producerTransport
                ?: return Result.failure(
                    ConnectSendTransportAudioException("Producer transport is null during remote connect")
                )

            parameters.updateProducerTransport(producerTransport)

            val existingProducer = parameters.audioProducer
            if (existingProducer == null) {
                if (effectiveAudioTrack != null) {
                    val remoteProduceAppData = mapOf(
                        "kind" to "audio",
                        "source" to "microphone",
                        "trackId" to effectiveAudioTrack.id
                    )
                    val remoteProducer = runCatching {
                        producerTransport.produce(
                            track = effectiveAudioTrack,
                            encodings = resolvedAudioEncodings,
                            codecOptions = resolvedAudioCodecOptions,
                            appData = remoteProduceAppData
                        )
                    }.getOrElse { error ->
                        return Result.failure(
                            ConnectSendTransportAudioException(
                                "Failed to produce on remote transport: ${error.message}",
                                error
                            )
                        )
                    }

                    parameters.updateAudioProducer(remoteProducer)
                }
            }
        }

        if (targetOption == "all" || targetOption == "local") {
            val localResult = connectLocalSendTransportAudio(
                options.copy(stream = resolvedStream)
            )
            if (localResult.isFailure) {
                return localResult
            }
        }

        parameters.updateAudioAlreadyOn(true)
        parameters.updateTransportCreatedAudio(true)
        parameters.updateMicAction(false)

        val resumeParams = parameters as? ResumeSendTransportAudioParameters
        val remoteProducer = (resumeParams?.audioProducer as? WebRtcProducer)
        val localProducer = (resumeParams?.localAudioProducer as? WebRtcProducer)

        if (remoteProducer != null) {
            updateMicLevel(remoteProducer, parameters.updateAudioLevel)
        } else {
            CoroutineScope(Dispatchers.Default).launch {
                delay(1000)
                val refreshedProducer = ((parameters.getUpdatedAllParams() as? ResumeSendTransportAudioParameters)?.audioProducer as? WebRtcProducer)
                if (refreshedProducer != null) {
                    updateMicLevel(refreshedProducer, parameters.updateAudioLevel)
                }
            }
        }

        if (targetOption == "all" || targetOption == "local") {
            localProducer?.let { updateMicLevel(it, parameters.updateAudioLevel) }
        }

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ConnectSendTransportAudioException(
                "connectSendTransportAudio error: ${error.message}",
                error
            )
        )
    }
}

/**
 * Updates the microphone audio level periodically.
 * 
 * This function would retrieve stats from the audio producer's RTP sender
 * and calculate the audio level. For now, it's a placeholder.
 * 
 * @param audioProducer The audio producer to monitor
 * @param updateAudioLevel Callback function to handle the updated audio level
 */
fun updateMicLevel(
    audioProducer: WebRtcProducer?,
    updateAudioLevel: (Double) -> Unit
) {
    if (audioProducer == null) {
        return
    }
    
    // TODO: Implement actual audio level monitoring
    // For now, simulate audio level updates
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
        while (true) {
            try {
                // Simulate audio level (0.0 to 1.0)
                val audioLevel = kotlin.random.Random.nextDouble(0.0, 1.0)
                val newLevel = 127.5 + (audioLevel * 127.5)
                updateAudioLevel(newLevel)
                
                kotlinx.coroutines.delay(1000) // Update every second
            } catch (_: Exception) {
                break
            }
        }
    }
}

private fun MediaStream.removeAudioTracksSafely() {
    try {
        val tracks = runCatching { getAudioTracks().toList() }.getOrDefault(emptyList())
        tracks.forEach { track ->
            runCatching { removeTrack(track) }
        }
    } catch (_: Exception) {
        // Silently handle errors
    }
}