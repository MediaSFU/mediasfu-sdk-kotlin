package com.mediasfu.sdk.util

internal expect object MediaSFURuntimeProbe {
    fun recordConsumerSignalStage(stage: String, remoteProducerId: String, detail: String)
    fun recordProducerSignalStage(stage: String, kind: String, detail: String)
    fun recordConsumerResumeEntered(kind: String, remoteProducerId: String)
    fun recordConsumerParticipantResolution(kind: String, remoteProducerId: String, participantName: String?)
    fun recordTrackedStreams(kind: String, count: Int)
    fun recordScreenDebug(stage: String, detail: String)
    fun recordDeviceLoadError(error: String)
}