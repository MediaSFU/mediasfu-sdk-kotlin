// ConnectSendTransportVideo.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.ProducerCodecOptions
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.utils.producer.VParams
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Parameters interface for connecting video send transports.
 */
interface ConnectSendTransportVideoParameters : ResumeSendTransportVideoParameters, CreateSendTransportParameters {
    val localStream: MediaStream?
    val updateLocalStreamVideo: (MediaStream?) -> Unit
    val updateLocalStream: (MediaStream?) -> Unit

    override fun getUpdatedAllParams(): ConnectSendTransportVideoParameters

    val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportVideo(options) }
}

/**
 * Options for connecting video send transports.
 */
data class ConnectSendTransportVideoOptions(
    val videoParams: ProducerOptionsType? = null,
    val parameters: ConnectSendTransportVideoParameters,
    val videoConstraints: Map<String, Any>? = null,
    val targetOption: String = "all" // 'all', 'local', 'remote'
)

/**
 * Exception thrown when connecting video send transport fails.
 */
class ConnectSendTransportVideoException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Connects the local send transport for video by producing video data.
 */
suspend fun connectLocalSendTransportVideo(
    stream: MediaStream?,
    track: MediaStreamTrack?,
    encodings: List<RtpEncodingParameters>,
    codecOptions: ProducerCodecOptions?,
    parameters: ConnectSendTransportVideoParameters
): Result<Unit> {
    return try {
        val updatedParams = parameters.getUpdatedAllParams()

        val localTransport = updatedParams.localProducerTransport
        if (localTransport == null) {
            return Result.success(Unit)
        }

        stream?.let { currentStream ->
            updatedParams.updateLocalStreamVideo(currentStream)

            val existingLocalStream = updatedParams.localStream as? MediaStream
            if (existingLocalStream == null || existingLocalStream === currentStream) {
                if (updatedParams.localStream == null) {
                    updatedParams.updateLocalStream(currentStream)
                }
            } else if (track != null) {
                runCatching {
                    existingLocalStream.addTrack(track)
                    updatedParams.updateLocalStream(existingLocalStream)
                }
            }
        }

        updatedParams.updateLocalProducerTransport?.invoke(localTransport)
        updatedParams.updateLocalVideoProducer?.invoke(updatedParams.localVideoProducer)

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ConnectSendTransportVideoException(
                "Error connecting local video transport: ${error.message}",
                error
            )
        )
    }
}

/**
 * Connects the send transport for video by producing video data and updating the local video producer and transport.
 *
 * This function sets up and connects the video stream for media sharing, handling updates to
 * local video streams and producer transports.
 *
 * ## Features:
 * - Handles video track management
 * - Manages media constraints
 * - Sets up stream and track management
 * - Handles transport production for video
 * - Updates video producer state
 *
 * ## Parameters:
 * - [options] Configuration options for video transport connection
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val videoOptions = ConnectSendTransportVideoOptions(
 *     videoParams = myVideoParams,
 *     targetOption = "all",
 *     videoConstraints = myVideoConstraints,
 *     parameters = myConnectSendTransportVideoParameters
 * )
 *
 * val result = connectSendTransportVideo(videoOptions)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ConnectSendTransport", "Error setting up video stream: ${error.message}")
 * }
 * ```
 *
 * ## Error Handling:
 * - Returns Result.failure if required parameters are missing
 * - Logs errors for debugging
 * - Handles transport creation failures gracefully
 */
suspend fun connectSendTransportVideo(
    options: ConnectSendTransportVideoOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val targetOption = options.targetOption.lowercase()
        val videoOptions = options.videoParams

        val extendedCaps = resolveExtendedRtpCapabilities(parameters)
        val negotiatedCodec = ProducerCapabilityAlignment.selectCodec(MediaKind.VIDEO, extendedCaps)

        val existingLocalStreamVideo = parameters.localStreamVideo as? MediaStream
        val existingLocalStream = parameters.localStream as? MediaStream
        val candidateStream = videoOptions?.stream ?: existingLocalStreamVideo ?: existingLocalStream
        val candidateTrack = videoOptions?.track ?: runCatching {
            candidateStream?.getVideoTracks()?.firstOrNull()
        }.getOrNull()

        val alignedVideoOptions = ProducerCapabilityAlignment.alignProducerOptions(
            baseOptions = videoOptions,
            stream = candidateStream,
            track = candidateTrack,
            negotiatedCodec = negotiatedCodec,
            label = "video",
            fallbackKind = ProducerFallbackKind.CAMERA
        )

        val explicitStream = alignedVideoOptions.stream
        val incomingStream = explicitStream ?: candidateStream
        val resolvedTrack = alignedVideoOptions.track ?: candidateTrack

        val defaultVideoParams = VParams.getVideoParams()
        val resolvedEncodings = alignedVideoOptions.encodings.ifEmpty { defaultVideoParams.encodings }
        val resolvedCodecOptions = alignedVideoOptions.codecOptions ?: defaultVideoParams.codecOptions

        if (explicitStream != null) {
            existingLocalStreamVideo?.takeIf { it !== explicitStream }?.removeVideoTracksSafely()
            existingLocalStream?.takeIf { it !== explicitStream }?.removeVideoTracksSafely()

            parameters.updateLocalStreamVideo(explicitStream)

            if (existingLocalStream == null) {
                parameters.updateLocalStream(explicitStream)
            } else if (existingLocalStream !== explicitStream && resolvedTrack != null) {
                runCatching {
                    existingLocalStream.removeVideoTracksSafely()
                    existingLocalStream.addTrack(resolvedTrack)
                    parameters.updateLocalStream(existingLocalStream)
                }
            }
        } else if (incomingStream != null) {
            parameters.updateLocalStreamVideo(incomingStream)
            if (existingLocalStream == null) {
                parameters.updateLocalStream(incomingStream)
            }
        }

        if (targetOption == "all" || targetOption == "remote") {
            val producerTransport = parameters.producerTransport
                ?: return Result.failure(
                    ConnectSendTransportVideoException("Producer transport is null during remote connect")
                )

            parameters.updateProducerTransport(producerTransport)

            val existingProducer = parameters.videoProducer
            if (existingProducer == null) {
                val trackForProduction = resolvedTrack
                if (trackForProduction != null) {
                    // CRITICAL: Do NOT send appData for camera video - server uses appData presence to detect screen share
                    // Web client sends empty appData {} for camera, so we must do the same
                    // Screen share should pass appData to identify as screen share
                    val remoteProduceAppData: Map<String, Any?>? = null  // Empty/null for camera video
                    val remoteProducer = runCatching {
                        producerTransport.produce(
                            track = trackForProduction,
                            encodings = resolvedEncodings,
                            codecOptions = resolvedCodecOptions,
                            appData = remoteProduceAppData
                        )
                    }.getOrElse { error ->
                        return Result.failure(
                            ConnectSendTransportVideoException(
                                "Failed to produce on remote transport: ${error.message}",
                                error
                            )
                        )
                    }

                    parameters.updateVideoProducer(remoteProducer)
                }
            }

            if (parameters.islevel == "2") {
                parameters.updateUpdateMainWindow(true)
            }
        }

        if (targetOption == "all" || targetOption == "local") {
            connectLocalSendTransportVideo(
                stream = incomingStream,
                track = resolvedTrack,
                encodings = resolvedEncodings,
                codecOptions = resolvedCodecOptions,
                parameters = parameters
            )
        }

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ConnectSendTransportVideoException(
                "connectSendTransportVideo error: ${error.message}",
                error
            )
        )
    }

}

private fun MediaStream.removeVideoTracksSafely() {
    try {
        val tracks = runCatching { getVideoTracks().toList() }.getOrDefault(emptyList())
        tracks.forEach { track ->
            runCatching { removeTrack(track) }
        }
    } catch (_: Exception) {
        // Silently handle errors
    }
}