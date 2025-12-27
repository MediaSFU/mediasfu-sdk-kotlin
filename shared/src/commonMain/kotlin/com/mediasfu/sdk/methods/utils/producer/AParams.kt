package com.mediasfu.sdk.methods.utils.producer

import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.RtpEncodingParameters

/**
 * Represents audio encoding parameters for audio sharing.
 * 
 * This object contains the default encoding parameters used for audio
 * producer creation in WebRTC streams.
 */
object AParams {
    
    /**
     * Gets the default audio encoding parameters.
     * 
     * @return ProducerOptionsType with default audio encoding settings
     */
    fun getAudioParams(): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = 64000,
                    maxFramerate = null,
                    scaleResolutionDownBy = null,
                    dtx = true
                )
            )
        )
    }
    
    /**
     * Gets custom audio encoding parameters.
     * 
     * @param maxBitrate Maximum bitrate for audio encoding
     * @param dtx Whether to enable DTX (Discontinuous Transmission)
     * @return ProducerOptionsType with custom audio encoding settings
     */
    fun getCustomAudioParams(
        maxBitrate: Int = 64000,
        dtx: Boolean = true
    ): ProducerOptionsType {
        return ProducerOptionsType(
            encodings = listOf(
                RtpEncodingParameters(
                    rid = "r0",
                    maxBitrate = maxBitrate,
                    maxFramerate = null,
                    scaleResolutionDownBy = null,
                    dtx = dtx
                )
            )
        )
    }
}

/**
 * Data class representing producer options type.
 * 
 * @property encodings List of RTP encoding parameters
 */
data class ProducerOptionsType(
    val encodings: List<RtpEncodingParameters> = emptyList(),
    val codecOptions: ProducerCodecOptions? = null,
    val track: MediaStreamTrack? = null,
    val stream: MediaStream? = null,
    val codec: RtpCodecCapability? = null,
    private val extraProperties: MutableMap<String, Any?> = mutableMapOf()
) {
    operator fun get(key: String): Any? = extraProperties[key]

    operator fun set(key: String, value: Any?) {
        extraProperties[key] = value
    }

    fun toMap(): Map<String, Any?> {
        return buildMap {
            put("encodings", encodings.map { it.toMap() })
            put("codecOptions", codecOptions)
            put("track", track)
            put("stream", stream)
            put("codec", codec)
            putAll(extraProperties)
        }
    }
}
