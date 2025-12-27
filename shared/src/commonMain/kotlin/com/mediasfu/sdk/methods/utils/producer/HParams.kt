package com.mediasfu.sdk.methods.utils.producer

import com.mediasfu.sdk.webrtc.RtpEncodingParameters

/**
 * Represents the H.264 encoding parameters and codec options.
 * 
 * This object contains the default encoding parameters used for H.264
 * video producer creation in WebRTC streams with scalable video encoding.
 */
object HParams {
    
    /**
     * Gets the default H.264 encoding parameters.
     * 
     * @return ProducerOptionsType with default H.264 encoding settings
     */
    fun getH264Params(): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r2",
                    maxBitrate = 240000,
                    minBitrate = 48000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = 4.0,
                    dtx = false,
                    networkPriority = "high"
                ),
                RtpEncodingParameters(
                    rid = "r1",
                    maxBitrate = 480000,
                    minBitrate = 96000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = 2.0,
                    dtx = false,
                    networkPriority = "high"
                ),
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = 960000,
                    minBitrate = 192000,
                    maxFramerate = null,
                    scalabilityMode = "L1T3",
                    scaleResolutionDownBy = null,
                    dtx = false,
                    networkPriority = "high"
                )
            ),
            codecOptions = ProducerCodecOptions(
                videoGoogleStartBitrate = 384
            )
        )
    }
    
    /**
     * Gets custom H.264 encoding parameters.
     * 
     * @param maxBitrate Maximum bitrate for H.264 encoding
     * @param minBitrate Minimum bitrate for H.264 encoding
     * @param scalabilityMode Scalability mode for H.264 encoding
     * @return ProducerOptionsType with custom H.264 encoding settings
     */
    fun getCustomH264Params(
        maxBitrate: Int = 960000,
        minBitrate: Int = 192000,
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
                videoGoogleStartBitrate = 384
            )
        )
    }
}
