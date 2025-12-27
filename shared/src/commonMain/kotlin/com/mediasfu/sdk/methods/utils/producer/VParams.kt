package com.mediasfu.sdk.methods.utils.producer

import com.mediasfu.sdk.webrtc.RtpEncodingParameters

/**
 * Represents video parameters for encoding, particularly for scalable video encoding.
 * 
 * This object contains the default encoding parameters used for video
 * producer creation in WebRTC streams with scalable video encoding.
 */
object VParams {
    
    /**
     * Gets the default video encoding parameters.
     * 
     * @return ProducerOptionsType with default video encoding settings
     */
    fun getVideoParams(): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = 200000,
                    minBitrate = 40000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = 4.0,
                    dtx = false
                ),
                RtpEncodingParameters(
                    rid = "r1",
                    maxBitrate = 400000,
                    minBitrate = 80000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = 2.0,
                    dtx = false
                ),
                RtpEncodingParameters(
                    rid = "r2",
                    maxBitrate = 800000,
                    minBitrate = 160000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = null,
                    dtx = false
                )
            ),
            codecOptions = ProducerCodecOptions(
                videoGoogleStartBitrate = 320
            )
        )
    }
    
    /**
     * Gets custom video encoding parameters.
     * 
     * @param maxBitrate Maximum bitrate for video encoding
     * @param minBitrate Minimum bitrate for video encoding
     * @param scalabilityMode Scalability mode for video encoding
     * @return ProducerOptionsType with custom video encoding settings
     */
    fun getCustomVideoParams(
        maxBitrate: Int = 800000,
        minBitrate: Int = 160000,
        scalabilityMode: String = "L1T3"
    ): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = maxBitrate,
                    minBitrate = minBitrate,
                    maxFramerate = null,
                    scalabilityMode = scalabilityMode,
                    scaleResolutionDownBy = null,
                    dtx = false,
                    networkPriority = "high"
                )
            ),
            codecOptions = ProducerCodecOptions(
                videoGoogleStartBitrate = 320
            )
        )
    }
}

/**
 * Data class representing producer codec options.
 * 
 * @property videoGoogleStartBitrate Google-specific start bitrate for video
 */
data class ProducerCodecOptions(
    val videoGoogleStartBitrate: Int = 320
)
