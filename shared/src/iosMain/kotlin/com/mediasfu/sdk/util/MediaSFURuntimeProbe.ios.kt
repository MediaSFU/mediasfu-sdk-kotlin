package com.mediasfu.sdk.util

import com.mediasfu.sdk.ui.ios.MediaSFUIosRuntimeProbeStore

internal actual object MediaSFURuntimeProbe {
    actual fun recordConsumerSignalStage(stage: String, remoteProducerId: String, detail: String) {
        MediaSFUIosRuntimeProbeStore.recordConsumerSignalStage(stage, remoteProducerId, detail)
    }

    actual fun recordProducerSignalStage(stage: String, kind: String, detail: String) {
        MediaSFUIosRuntimeProbeStore.recordProducerSignalStage(stage, kind, detail)
    }

    actual fun recordConsumerResumeEntered(kind: String, remoteProducerId: String) {
        MediaSFUIosRuntimeProbeStore.recordConsumerResumeEntered(kind, remoteProducerId)
    }

    actual fun recordConsumerParticipantResolution(
        kind: String,
        remoteProducerId: String,
        participantName: String?
    ) {
        MediaSFUIosRuntimeProbeStore.recordConsumerParticipantResolution(
            kind = kind,
            remoteProducerId = remoteProducerId,
            participantName = participantName,
        )
    }

    actual fun recordTrackedStreams(kind: String, count: Int) {
        MediaSFUIosRuntimeProbeStore.recordTrackedStreams(kind, count)
    }

    actual fun recordScreenDebug(stage: String, detail: String) {
        MediaSFUIosRuntimeProbeStore.recordScreenDebug(stage, detail)
    }

    actual fun recordDeviceLoadError(error: String) {
        MediaSFUIosRuntimeProbeStore.recordDeviceLoadError(error)
    }
}