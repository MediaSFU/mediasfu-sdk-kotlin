package com.mediasfu.sdk.util

internal actual object MediaSFURuntimeProbe {
    actual fun recordConsumerSignalStage(stage: String, remoteProducerId: String, detail: String) = Unit

    actual fun recordProducerSignalStage(stage: String, kind: String, detail: String) = Unit

    actual fun recordConsumerResumeEntered(kind: String, remoteProducerId: String) = Unit

    actual fun recordConsumerParticipantResolution(
        kind: String,
        remoteProducerId: String,
        participantName: String?
    ) = Unit

    actual fun recordTrackedStreams(kind: String, count: Int) = Unit

    actual fun recordScreenDebug(stage: String, detail: String) = Unit

    actual fun recordDeviceLoadError(error: String) = Unit
}