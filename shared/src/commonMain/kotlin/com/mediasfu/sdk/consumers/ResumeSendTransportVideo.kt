package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.WebRtcProducer

/**
 * Parameters for resuming send transport video.
 */
interface ResumeSendTransportVideoParameters : PrepopulateUserMediaParameters {
    var videoProducer: WebRtcProducer?
    var localVideoProducer: WebRtcProducer?

    override val islevel: String
    val hostLabel: String
    val lockScreen: Boolean
    override val shared: Boolean
    override val audioAlreadyOn: Boolean

    val updateVideoProducer: (WebRtcProducer?) -> Unit
    val updateLocalVideoProducer: ((WebRtcProducer?) -> Unit)?
    override val updateUpdateMainWindow: (Boolean) -> Unit

    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit

    override fun getUpdatedAllParams(): ResumeSendTransportVideoParameters

    val resumeSendTransportVideo: suspend (ResumeSendTransportVideoOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.resumeSendTransportVideo(options) }
}

/**
 * Options for resuming send transport video.
 *
 * @property parameters The parameters required for resuming the send transport
 */
data class ResumeSendTransportVideoOptions(
    val parameters: ResumeSendTransportVideoParameters
)

/**
 * Resumes the local send transport for video by resuming the local video producer if available.
 */
suspend fun resumeLocalSendTransportVideo(options: ResumeSendTransportVideoOptions) {
    try {
        val parameters = options.parameters
        val localVideoProducer = parameters.localVideoProducer

        if (localVideoProducer != null) {
            // Platform-specific implementation would resume the video producer here
            parameters.updateLocalVideoProducer?.invoke(localVideoProducer)
        }
    } catch (error: Exception) {
        Logger.e("ResumeSendTransportV", "Error resuming local video send transport: ${error.message}")
        throw error
    }
}

/**
 * Resumes the video send transport and updates related UI state.
 */
suspend fun resumeSendTransportVideo(options: ResumeSendTransportVideoOptions) {
    val parameters = options.parameters

    val videoProducer = parameters.videoProducer
    val islevel = parameters.islevel
    val hostLabel = parameters.hostLabel
    val lockScreen = parameters.lockScreen
    val shared = parameters.shared
    val audioAlreadyOn = parameters.audioAlreadyOn
    val updateVideoProducer = parameters.updateVideoProducer
    val updateUpdateMainWindow = parameters.updateUpdateMainWindow
    val prepopulateUserMedia = parameters.prepopulateUserMedia

    try {
        // Platform-specific implementation would resume the video producer here
        // videoProducer?.resume()

        if (!audioAlreadyOn && islevel == "2") {
            if (!lockScreen && !shared) {
                updateUpdateMainWindow(true)
                val optionsPrepopulate = PrepopulateUserMediaOptions(
                    name = hostLabel,
                    parameters = parameters
                )
                prepopulateUserMedia(optionsPrepopulate)
                updateUpdateMainWindow(false)
            }
        }

        updateVideoProducer(videoProducer)

        // Resume any local video producer state
        try {
            resumeLocalSendTransportVideo(options)
        } catch (localError: Exception) {
            Logger.e("ResumeSendTransportV", "Error resuming local video send transport: ${localError.message}")
        }
    } catch (error: Exception) {
        Logger.e("ResumeSendTransportV", "Error during resuming video send transport: ${error.message}")
    }
}
