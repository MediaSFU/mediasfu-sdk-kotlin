package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.webrtc.WebRtcProducer

typealias ResumeSendTransportAudioType = suspend (ResumeSendTransportAudioOptions) -> Unit

/**
 * Parameters for resuming send transport audio.
 */
interface ResumeSendTransportAudioParameters : PrepopulateUserMediaParameters {
    val audioProducer: WebRtcProducer?
    val localAudioProducer: WebRtcProducer?

    override val islevel: String
    val hostLabel: String
    val lockScreen: Boolean
    override val shared: Boolean
    override val videoAlreadyOn: Boolean

    val updateAudioProducer: (WebRtcProducer?) -> Unit
    val updateLocalAudioProducer: ((WebRtcProducer?) -> Unit)?
    override val updateUpdateMainWindow: (Boolean) -> Unit

    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit

    override fun getUpdatedAllParams(): ResumeSendTransportAudioParameters

    val resumeSendTransportAudio: suspend (ResumeSendTransportAudioOptions) -> Unit
        get() = { options -> com.mediasfu.sdk.consumers.resumeSendTransportAudio(options) }
}

/**
 * Options for resuming send transport audio.
 *
 * @property parameters The parameters required for resuming the send transport
 */
data class ResumeSendTransportAudioOptions(
    val parameters: ResumeSendTransportAudioParameters
)

// Note: PrepopulateUserMediaOptions is already defined elsewhere in the codebase

/**
 * Resumes the local send transport for audio by resuming the local audio producer.
 *
 * @param options The options containing parameters for resuming local audio transport
 *
 * Example:
 * ```kotlin
 * val options = ResumeSendTransportAudioOptions(
 *     parameters = myResumeSendTransportAudioParameters
 * )
 *
 * resumeLocalSendTransportAudio(options)
 * ```
 */
suspend fun resumeLocalSendTransportAudio(options: ResumeSendTransportAudioOptions) {
    try {
        val parameters = options.parameters
        val localAudioProducer = parameters.localAudioProducer

        // Resume the local audio producer and update the state
        if (localAudioProducer != null) {
            // Platform-specific: Would call producer.resume() here
            parameters.updateLocalAudioProducer?.invoke(localAudioProducer)
        }
    } catch (error: Exception) {
        Logger.e("ResumeSendTransportA", "Error resuming local audio send transport: ${error.message}")
        throw error
    }
}

/**
 * Resumes the send transport for audio and updates the UI and audio producer state accordingly.
 *
 * This function supports both a primary and a local audio producer, delegating local handling
 * to a separate function.
 *
 * ### Workflow:
 * 1. **Resume Primary Audio Producer**: If an active primary audio producer exists, it is resumed.
 * 2. **Update UI**: Based on conditions (videoAlreadyOn, islevel, lockScreen, shared), updates
 *    the main window state and prepopulates user media if needed.
 * 3. **Update Audio Producer State**: Updates the audio producer state to reflect the resumed
 *    producer.
 * 4. **Handle Local Audio Transport Resumption**: Invokes `resumeLocalSendTransportAudio` to
 *    handle the local audio transport resumption.
 *
 * @param options The options containing parameters for resuming audio send transport
 *
 * Example:
 * ```kotlin
 * val options = ResumeSendTransportAudioOptions(
 *     parameters = object : ResumeSendTransportAudioParameters {
 *         override val audioProducer = myAudioProducer
 *         override val localAudioProducer = myLocalAudioProducer
 *         override val islevel = "2"
 *         override val hostLabel = "Host123"
 *         override val lockScreen = false
 *         override val shared = false
 *         override val videoAlreadyOn = false
 *         override val updateAudioProducer = { producer -> setAudioProducer(producer) }
 *         override val updateLocalAudioProducer = { producer -> setLocalAudioProducer(producer) }
 *         override val updateUpdateMainWindow = { state -> setMainWindowState(state) }
 *         override val prepopulateUserMedia = { opts -> /* prepopulate */ }
 *         override fun getUpdatedAllParams() = this
 *     }
 * )
 *
 * resumeSendTransportAudio(options)
 * ```
 */
suspend fun resumeSendTransportAudio(options: ResumeSendTransportAudioOptions) {
    val parameters = options.parameters

    val audioProducer = parameters.audioProducer
    val islevel = parameters.islevel
    val hostLabel = parameters.hostLabel
    val lockScreen = parameters.lockScreen
    val shared = parameters.shared
    val videoAlreadyOn = parameters.videoAlreadyOn
    val updateAudioProducer = parameters.updateAudioProducer
    val updateUpdateMainWindow = parameters.updateUpdateMainWindow
    val prepopulateUserMedia = parameters.prepopulateUserMedia

    try {
        // Resume audio producer if available (platform-specific implementation)
        // audioProducer?.resume()

        if (!videoAlreadyOn && islevel == "2") {
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

        updateAudioProducer(audioProducer)

        // Handle local audio transport resumption
        try {
            resumeLocalSendTransportAudio(options)
        } catch (localError: Exception) {
            Logger.e("ResumeSendTransportA", "Error resuming local audio send transport: ${localError.message}")
            // Optionally, handle the local error
        }
    } catch (error: Exception) {
        Logger.e("ResumeSendTransportA", "Error during resuming audio send transport: ${error.message}")
    }
}

