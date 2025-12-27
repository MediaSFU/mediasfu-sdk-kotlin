package com.mediasfu.sdk.consumers

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.ortc.OrtcUtils

/**
 * Parameters for connecting send transport.
 */
import com.mediasfu.sdk.webrtc.MediaStream

interface ConnectSendTransportParameters :
    ConnectSendTransportAudioParameters,
    ConnectSendTransportVideoParameters,
    ConnectSendTransportScreenParameters {
    override val audioParams: ProducerOptionsType?
    val videoParams: ProducerOptionsType?
    override val localStreamAudio: MediaStream?
    override val localStream: MediaStream?
    override val localStreamVideo: MediaStream?
    override val localStreamScreen: MediaStream?
    val canvasStream: MediaStream?
    override val whiteboardStarted: Boolean
    override val whiteboardEnded: Boolean
    override val shared: Boolean
    override val islevel: String

    override val connectSendTransportAudio: suspend (ConnectSendTransportAudioOptions) -> Unit
    override val connectSendTransportVideo: suspend (ConnectSendTransportVideoOptions) -> Unit
    override val connectSendTransportScreen: suspend (ConnectSendTransportScreenOptions) -> Unit

    override fun getUpdatedAllParams(): ConnectSendTransportParameters
}

/**
 * Options for connecting send transport.
 *
 * @property option The media type to connect ('audio', 'video', 'screen', or 'all')
 * @property parameters Parameters for connection
 * @property audioConstraints Optional audio constraints
 * @property videoConstraints Optional video constraints
 * @property targetOption Target option for connection ('all' by default)
 */
data class ConnectSendTransportOptions(
    val option: String, // 'audio', 'video', 'screen', or 'all'
    val parameters: ConnectSendTransportParameters,
    val audioConstraints: Map<String, Any>? = null,
    val videoConstraints: Map<String, Any>? = null,
    val targetOption: String = "all"
)

/**
 * Connects send transport for audio, video, or screen based on the specified option.
 *
 * This function manages media stream connections, allowing flexible configuration for different types
 * of media transmission (audio, video, screen) based on the `option` provided.
 *
 * ### Workflow:
 * 1. **Audio Transport**: If `option` is 'audio', connects the audio stream
 * 2. **Video Transport**: If `option` is 'video', connects the video stream
 * 3. **Screen Transport**: If `option` is 'screen', decides which stream to connect based on
 *    whiteboard and screen sharing state
 * 4. **All Transports**: If `option` is 'all', initiates connections for both audio and video
 *
 * @param options The options containing connection type and parameters
 *
 * Example:
 * ```kotlin
 * val options = ConnectSendTransportOptions(
 *     option = "all",
 *     targetOption = "all",
 *     parameters = myConnectSendTransportParameters
 * )
 *
 * connectSendTransport(options)
 * ```
 */
suspend fun connectSendTransport(options: ConnectSendTransportOptions) {
    try {
        val videoParams = options.parameters.videoParams
        val localStreamScreen = options.parameters.localStreamScreen
        val canvasStream = options.parameters.canvasStream
        val whiteboardStarted = options.parameters.whiteboardStarted
        val whiteboardEnded = options.parameters.whiteboardEnded
        val shared = options.parameters.shared
        val islevel = options.parameters.islevel
        val targetOption = options.targetOption

        val connectSendTransportAudio = options.parameters.connectSendTransportAudio
        val connectSendTransportVideo = options.parameters.connectSendTransportVideo
        val connectSendTransportScreen = options.parameters.connectSendTransportScreen

        val extendedCaps = resolveExtendedRtpCapabilities(options.parameters)
        val canSendAudio = extendedCaps?.let { OrtcUtils.canSend(MediaKind.AUDIO, it) } ?: true
        val canSendVideo = extendedCaps?.let { OrtcUtils.canSend(MediaKind.VIDEO, it) } ?: true

        fun ensureSendCapability(kind: MediaKind, label: String): Boolean {
            val allowed = when (kind) {
                MediaKind.AUDIO -> canSendAudio
                MediaKind.VIDEO -> canSendVideo
            }
            return allowed
        }

        when (options.option) {
            "audio" -> {
                if (!ensureSendCapability(MediaKind.AUDIO, "audio")) {
                    return
                }
                val localAudioStream = options.parameters.localStreamAudio ?: return
                val optionsAudio = ConnectSendTransportAudioOptions(
                    stream = localAudioStream,
                    targetOption = targetOption,
                    parameters = options.parameters,
                    audioConstraints = options.audioConstraints
                )
                connectSendTransportAudio(optionsAudio)
            }
            "video" -> {
                if (!ensureSendCapability(MediaKind.VIDEO, "video")) {
                    return
                }
                val vParams = videoParams as? ProducerOptionsType ?: return
                val optionsVideo = ConnectSendTransportVideoOptions(
                    targetOption = targetOption,
                    videoParams = vParams,
                    parameters = options.parameters,
                    videoConstraints = options.videoConstraints
                )
                connectSendTransportVideo(optionsVideo)
            }
            "screen" -> {
                if (!ensureSendCapability(MediaKind.VIDEO, "screen")) {
                    return
                }
                if (whiteboardStarted && !whiteboardEnded && canvasStream != null &&
                    islevel == "2" && !shared) {
                    val optionsScreen = ConnectSendTransportScreenOptions(
                        targetOption = targetOption,
                        stream = canvasStream,
                        parameters = options.parameters
                    )
                    connectSendTransportScreen(optionsScreen)
                } else if (localStreamScreen != null) {
                    val optionsScreen = ConnectSendTransportScreenOptions(
                        targetOption = targetOption,
                        stream = localStreamScreen,
                        parameters = options.parameters
                    )
                    connectSendTransportScreen(optionsScreen)
                }
            }
            else -> {
                val localAudioStream = options.parameters.localStreamAudio
                if (localAudioStream != null && ensureSendCapability(MediaKind.AUDIO, "audio")) {
                    val optionsAudio = ConnectSendTransportAudioOptions(
                        targetOption = targetOption,
                        stream = localAudioStream,
                        parameters = options.parameters,
                        audioConstraints = options.audioConstraints
                    )
                    connectSendTransportAudio(optionsAudio)
                }

                val vParams = videoParams as? ProducerOptionsType ?: return
                if (!ensureSendCapability(MediaKind.VIDEO, "video")) {
                    return
                }
                val optionsVideo = ConnectSendTransportVideoOptions(
                    targetOption = targetOption,
                    videoParams = vParams,
                    parameters = options.parameters,
                    videoConstraints = options.videoConstraints
                )
                connectSendTransportVideo(optionsVideo)
            }
        }
    } catch (_: Exception) {
        // Silently handle errors
    }
}

