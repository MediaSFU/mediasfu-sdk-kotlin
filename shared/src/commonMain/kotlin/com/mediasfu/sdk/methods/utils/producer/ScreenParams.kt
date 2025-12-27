package com.mediasfu.sdk.methods.utils.producer

import com.mediasfu.sdk.webrtc.RtpEncodingParameters

/**
 * Represents screen parameters for video encoding, particularly for screen sharing.
 * 
 * This object contains the default encoding parameters used for screen
 * sharing producer creation in WebRTC streams.
 */
object ScreenParams {
    
    /**
     * Gets the default screen encoding parameters.
     * 
     * @return ProducerOptionsType with default screen encoding settings
     */
    fun getScreenParams(): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = 3000000,
                    minBitrate = 500000,
                    maxFramerate = null,
                    scalabilityMode = null,
                    scaleResolutionDownBy = null,
                    dtx = false,
                    networkPriority = "high"
                )
            ),
            codecOptions = ProducerCodecOptions(
                videoGoogleStartBitrate = 1000
            )
        )
    }
    
    /**
     * Gets custom screen encoding parameters.
     * 
     * @param maxBitrate Maximum bitrate for screen encoding
     * @param minBitrate Minimum bitrate for screen encoding
     * @return ProducerOptionsType with custom screen encoding settings
     */
    fun getCustomScreenParams(
        maxBitrate: Int = 3000000,
        minBitrate: Int = 500000
    ): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = maxBitrate,
                    minBitrate = minBitrate,
                    maxFramerate = null,
                    scalabilityMode = null,
                    scaleResolutionDownBy = null,
                    dtx = false,
                    networkPriority = "high"
                )
            ),
            codecOptions = ProducerCodecOptions(
                videoGoogleStartBitrate = 1000
            )
        )
    }
}
