package com.mediasfu.sdk.producer_client

import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType

/** Camera policy only; screen sharing and audio retain their own encoding presets. */
internal fun cameraSimulcastOff(eventType: String?, capacity: Int?, simulcastOff: Boolean): Boolean =
    simulcastOff || eventType?.lowercase() == "chat" || capacity == 2

internal fun applyCameraEncodingPolicy(
    producerOptions: ProducerOptionsType,
    singleEncoding: Boolean,
): ProducerOptionsType {
    if (!singleEncoding || producerOptions.encodings.isEmpty()) return producerOptions
    val full = producerOptions.encodings.withIndex().minWithOrNull(
        compareBy<IndexedValue<com.mediasfu.sdk.webrtc.RtpEncodingParameters>> {
            it.value.scaleResolutionDownBy ?: 1.0
        }.thenByDescending { it.index }
    )!!.value
    return producerOptions.copy(encodings = listOf(full.copy(
        rid = null,
        scaleResolutionDownBy = 1.0,
        scalabilityMode = full.scalabilityMode?.let { "L1T3" },
    )))
}

/** Recording support never increases the resolution-selected camera bitrate. */
internal fun updateEncodingBitrates(producerOptions: ProducerOptionsType, targetResolution: String): ProducerOptionsType {
    val factor = when (targetResolution) {
        "hd" -> 4.0
        "fhd" -> 8.0
        "qhd" -> 16.0
        "QnHD" -> 0.25
        else -> 1.0
    }
    return producerOptions.copy(encodings = producerOptions.encodings.map { encoding ->
        encoding.copy(
            maxBitrate = encoding.maxBitrate?.let { (it * factor).toInt() },
            minBitrate = encoding.minBitrate?.let { (it * factor).toInt() },
        )
    })
}
