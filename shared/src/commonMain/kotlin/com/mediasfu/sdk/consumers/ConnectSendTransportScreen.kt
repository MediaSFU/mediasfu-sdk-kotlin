// ConnectSendTransportScreen.kt
package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.methods.utils.producer.ScreenParams
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Parameters interface for connecting screen send transports.
 */
interface ConnectSendTransportScreenParameters : CreateSendTransportParameters {
    val screenProducer: WebRtcProducer?
    val localScreenProducer: WebRtcProducer?
    val localStream: MediaStream?
    val localStreamScreen: MediaStream?
    val screenParams: ProducerOptionsType?
    val params: ProducerOptionsType?
    val defScreenID: String
    val updateMainWindow: Boolean
    val showAlert: ShowAlert?

    val updateScreenProducer: (WebRtcProducer?) -> Unit
    val updateLocalScreenProducer: ((WebRtcProducer?) -> Unit)?
    val updateLocalStream: (MediaStream?) -> Unit
    val updateLocalStreamScreen: (MediaStream?) -> Unit
    val updateUpdateMainWindow: (Boolean) -> Unit
    val updateDefScreenID: (String) -> Unit

    fun getUpdatedAllParams(): ConnectSendTransportScreenParameters

    val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.connectSendTransportScreen(options) }
}

/**
 * Options for connecting screen send transports.
 */
data class ConnectSendTransportScreenOptions(
    val stream: MediaStream?,
    val parameters: ConnectSendTransportScreenParameters,
    val targetOption: String = "all" // 'all', 'local', 'remote'
)

/**
 * Exception thrown when connecting screen send transport fails.
 */
class ConnectSendTransportScreenException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Connects the local send transport for screen sharing by producing screen data.
 */
suspend fun connectLocalSendTransportScreen(
    stream: MediaStream?,
    parameters: ConnectSendTransportScreenParameters
): Result<Unit> {
    return try {
        val updatedParams = parameters.getUpdatedAllParams()

        val localTransport = updatedParams.localProducerTransport
        if (localTransport == null) {
            return Result.success(Unit)
        }

        stream?.let { currentStream ->
            updatedParams.updateLocalStreamScreen(currentStream)

            val existingLocalStream = updatedParams.localStream
            if (existingLocalStream == null) {
                updatedParams.updateLocalStream(currentStream)
            } else if (existingLocalStream !== currentStream) {
                runCatching {
                    // Replace existing screen tracks with the incoming ones when available.
                    existingLocalStream.getVideoTracks().forEach { track ->
                        existingLocalStream.removeTrack(track)
                    }
                    currentStream.getVideoTracks().firstOrNull()?.let { track ->
                        existingLocalStream.addTrack(track)
                    }
                    updatedParams.updateLocalStream(existingLocalStream)
                }.onFailure { _ ->
                    // Silently handle sync errors
                }
            }
        }

        updatedParams.updateLocalProducerTransport?.invoke(localTransport)
        updatedParams.updateLocalScreenProducer?.invoke(updatedParams.localScreenProducer)

        Result.success(Unit)
    } catch (error: Exception) {
        Result.failure(
            ConnectSendTransportScreenException(
                "Error connecting local screen transport: ${error.message}",
                error
            )
        )
    }
}

/**
 * Connects the send transport for screen sharing by producing screen data and updating the local screen producer and transport.
 *
 * This function sets up and connects the screen stream for media sharing, handling updates to
 * local screen streams and producer transports.
 *
 * ## Features:
 * - Handles screen track management
 * - Manages screen capture constraints
 * - Sets up stream and track management
 * - Handles transport production for screen sharing
 * - Updates screen producer state
 *
 * ## Parameters:
 * - [options] Configuration options for screen transport connection
 *
 * ## Returns:
 * - [Result]<[Unit]> indicating success or failure
 *
 * ## Example Usage:
 * ```kotlin
 * val screenOptions = ConnectSendTransportScreenOptions(
 *     stream = myScreenStream,
 *     targetOption = "all",
 *     parameters = myConnectSendTransportScreenParameters
 * )
 *
 * val result = connectSendTransportScreen(screenOptions)
 * result.onSuccess {
 * }
 * result.onFailure { error ->
 *     Logger.e("ConnectSendTransport", "Error setting up screen stream: ${error.message}")
 * }
 * ```
 *
 * ## Error Handling:
 * - Returns Result.failure if required parameters are missing
 * - Logs errors for debugging
 * - Handles transport creation failures gracefully
 */
suspend fun connectSendTransportScreen(
    options: ConnectSendTransportScreenOptions
): Result<Unit> {
    return try {
        val parameters = options.parameters.getUpdatedAllParams()
        val targetOption = options.targetOption.lowercase()

        val defaultScreenParams = ScreenParams.getScreenParams()
        val baseScreenParams = parameters.screenParams ?: parameters.params

        val extendedCaps = resolveExtendedRtpCapabilities(parameters)
        val negotiatedCodec = ProducerCapabilityAlignment.selectCodec(MediaKind.VIDEO, extendedCaps)

        val candidateStream = options.stream
            ?: baseScreenParams?.stream
            ?: parameters.localStreamScreen
            ?: parameters.localStream

        val candidateTrack: MediaStreamTrack? = baseScreenParams?.track ?: runCatching {
            candidateStream?.getVideoTracks()?.firstOrNull()
        }.getOrNull()

        val effectiveParams = ProducerCapabilityAlignment.alignProducerOptions(
            baseOptions = baseScreenParams,
            stream = candidateStream,
            track = candidateTrack,
            negotiatedCodec = negotiatedCodec,
            label = "screen",
            fallbackKind = ProducerFallbackKind.SCREEN
        )

        val resolvedStream = effectiveParams.stream ?: candidateStream
        val resolvedTrack: MediaStreamTrack? = effectiveParams.track ?: candidateTrack

        resolvedStream?.let { stream ->
            parameters.updateLocalStreamScreen(stream)

            val existingLocalStream = parameters.localStream
            when {
                existingLocalStream == null -> parameters.updateLocalStream(stream)
                existingLocalStream === stream -> Unit
                else -> runCatching {
                    existingLocalStream.removeVideoTracksSafely()
                    stream.getVideoTracks().firstOrNull()?.let { track ->
                        existingLocalStream.addTrack(track)
                    }
                    parameters.updateLocalStream(existingLocalStream)
                }
            }
        }

        if (targetOption == "all" || targetOption == "remote") {
            val producerTransport = parameters.producerTransport
                ?: return Result.failure(
                    ConnectSendTransportScreenException("Producer transport is null during remote screen connect")
                )

            parameters.updateProducerTransport(producerTransport)

            val existingProducer = parameters.screenProducer
            if (existingProducer != null) {
                if (parameters.defScreenID.isBlank()) {
                    parameters.updateDefScreenID(existingProducer.id)
                }
            } else {
                val trackForProduction = resolvedTrack ?: run {
                    val message = "No screen track available for remote production"
                    parameters.showAlert?.invoke(message, "danger", 3000)
                    return Result.failure(ConnectSendTransportScreenException(message))
                }

                val encodings = effectiveParams.encodings.takeIf { it.isNotEmpty() }
                    ?: defaultScreenParams.encodings
                val codecOptions = effectiveParams.codecOptions ?: defaultScreenParams.codecOptions

                val appData = mapOf(
                    "kind" to "video",
                    "source" to "screen",
                    "trackId" to trackForProduction.id
                )

                val remoteProducer = runCatching {
                    producerTransport.produce(
                        track = trackForProduction,
                        encodings = encodings,
                        codecOptions = codecOptions,
                        appData = appData
                    )
                }.onFailure { error ->
                    parameters.showAlert?.invoke(
                        "Unable to start screen share: ${error.message}",
                        "danger",
                        3000
                    )
                }.getOrElse { error ->
                    return Result.failure(
                        ConnectSendTransportScreenException(
                            "Failed to produce on remote screen transport: ${error.message}",
                            error
                        )
                    )
                }

                parameters.updateScreenProducer(remoteProducer)
                parameters.updateDefScreenID(remoteProducer.id)
            }

            if (parameters.islevel == "2") {
                parameters.updateUpdateMainWindow(true)
            }
        }

        if (targetOption == "all" || targetOption == "local") {
            val localResult = connectLocalSendTransportScreen(
                stream = resolvedStream,
                parameters = parameters
            )
            if (localResult.isFailure) {
                localResult.exceptionOrNull()?.let { error ->
                    parameters.showAlert?.invoke(
                        "Unable to connect local screen transport: ${error.message}",
                        "danger",
                        3000
                    )
                }
                return localResult
            }
        }

        Result.success(Unit)
    } catch (error: Exception) {
        runCatching {
            options.parameters.getUpdatedAllParams().showAlert?.invoke(
                "Error connecting screen transport: ${error.message}",
                "danger",
                3000
            )
        }
        Result.failure(
            ConnectSendTransportScreenException(
                "connectSendTransportScreen error: ${error.message}",
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