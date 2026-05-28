package com.mediasfu.sdk.ui.ios

internal object MediaSFUIosRuntimeProbeStore {
    private var updateSequence: Int = 0
    private var participantCount: Int = 0
    private var visibleStreamCount: Int = 0
    private var audioOnlyStreamCount: Int = 0
    private var consumerSignalAttemptCount: Int = 0
    private var consumerTransportSuccessCount: Int = 0
    private var consumerConsumeSuccessCount: Int = 0
    private var consumerClientTransportSuccessCount: Int = 0
    private var consumerCreateSuccessCount: Int = 0
    private var consumerResumeAckSuccessCount: Int = 0
    private var consumerResumeCount: Int = 0
    private var audioConsumerResumeCount: Int = 0
    private var videoConsumerResumeCount: Int = 0
    private var producerSignalAttemptCount: Int = 0
    private var producerAckSuccessCount: Int = 0
    private var audioProducerSuccessCount: Int = 0
    private var videoProducerSuccessCount: Int = 0
    private var producerErrorCount: Int = 0
    private var trackedAudioStreamCount: Int = 0
    private var trackedVideoStreamCount: Int = 0
    private var videoParticipantMatchCount: Int = 0
    private var localAudioEnabled: Boolean = false
    private var localVideoEnabled: Boolean = false
    private var localScreenShareEnabled: Boolean = false
    private var remoteScreenShareStarted: Boolean = false
    private var lastSignalStage: String = ""
    private var lastSignalProducerId: String = ""
    private var lastSignalDetail: String = ""
    private var lastConsumerKind: String = ""
    private var lastConsumerProducerId: String = ""
    private var lastProducerStage: String = ""
    private var lastProducerKind: String = ""
    private var lastProducerDetail: String = ""
    private var lastProduceRequestDetail: String = ""
    private var lastProduceFailureDetail: String = ""
    private var lastVideoProducerStage: String = ""
    private var lastVideoProducerDetail: String = ""
    private var lastVideoProduceFailureDetail: String = ""
    private var lastResolvedParticipant: String = ""
    private var lastAlertType: String = ""
    private var lastAlertMessage: String = ""
    private var bootstrapScreenDebug: String = ""
    private var signalScreenDebug: String = ""
    private var resumeScreenDebug: String = ""
    private var screenConsumeError: String = ""
    private var lastDeviceLoadError: String = ""

    fun reset() {
        updateSequence = 0
        participantCount = 0
        visibleStreamCount = 0
        audioOnlyStreamCount = 0
        consumerSignalAttemptCount = 0
        consumerTransportSuccessCount = 0
        consumerConsumeSuccessCount = 0
        consumerClientTransportSuccessCount = 0
        consumerCreateSuccessCount = 0
        consumerResumeAckSuccessCount = 0
        consumerResumeCount = 0
        audioConsumerResumeCount = 0
        videoConsumerResumeCount = 0
        producerSignalAttemptCount = 0
        producerAckSuccessCount = 0
        audioProducerSuccessCount = 0
        videoProducerSuccessCount = 0
        producerErrorCount = 0
        trackedAudioStreamCount = 0
        trackedVideoStreamCount = 0
        videoParticipantMatchCount = 0
        localAudioEnabled = false
        localVideoEnabled = false
        localScreenShareEnabled = false
        remoteScreenShareStarted = false
        lastSignalStage = ""
        lastSignalProducerId = ""
        lastSignalDetail = ""
        lastConsumerKind = ""
        lastConsumerProducerId = ""
        lastProducerStage = ""
        lastProducerKind = ""
        lastProducerDetail = ""
        lastProduceRequestDetail = ""
        lastProduceFailureDetail = ""
        lastVideoProducerStage = ""
        lastVideoProducerDetail = ""
        lastVideoProduceFailureDetail = ""
        lastResolvedParticipant = ""
        lastAlertType = ""
        lastAlertMessage = ""
        bootstrapScreenDebug = ""
        signalScreenDebug = ""
        resumeScreenDebug = ""
        screenConsumeError = ""
        lastDeviceLoadError = ""
    }

    fun recordParticipants(count: Int) {
        participantCount = count
        bump()
    }

    fun recordVisibleStreams(count: Int) {
        visibleStreamCount = count
        bump()
    }

    fun recordAudioOnlyStreams(count: Int) {
        audioOnlyStreamCount = count
        bump()
    }

    fun recordConsumerSignalStage(stage: String, remoteProducerId: String, detail: String) {
        val normalizedStage = stage.lowercase()
        when (normalizedStage) {
            "entered" -> consumerSignalAttemptCount += 1
            "transport-ok" -> consumerTransportSuccessCount += 1
            "consume-ok" -> consumerConsumeSuccessCount += 1
            "client-transport-ok" -> consumerClientTransportSuccessCount += 1
            "consumer-created" -> consumerCreateSuccessCount += 1
            "resume-ack-ok" -> consumerResumeAckSuccessCount += 1
        }
        lastSignalStage = sanitize(normalizedStage)
        lastSignalProducerId = sanitize(remoteProducerId)
        lastSignalDetail = sanitize(detail)
        bump()
    }

    fun recordProducerSignalStage(stage: String, kind: String, detail: String) {
        val normalizedStage = stage.lowercase()
        val normalizedKind = kind.lowercase()
        val sanitizedDetail = sanitize(detail)

        when (normalizedStage) {
            "produce-entered" -> producerSignalAttemptCount += 1
            "produce-ack-ok" -> {
                producerAckSuccessCount += 1
                when (normalizedKind) {
                    "audio" -> audioProducerSuccessCount += 1
                    "video" -> videoProducerSuccessCount += 1
                }
            }
            "produce-ack-error",
            "produce-exception",
            "producer-failed",
            "producer-error-handle",
            "track-missing",
            "transport-missing" -> producerErrorCount += 1
        }

        when (normalizedStage) {
            "produce-request" -> {
                if (sanitizedDetail.isNotBlank()) {
                    lastProduceRequestDetail = sanitizedDetail
                }
            }
            "produce-ack-error",
            "produce-exception",
            "producer-failed",
            "producer-error-handle" -> {
                if (sanitizedDetail.isNotBlank()) {
                    lastProduceFailureDetail = sanitizedDetail
                }
            }
        }

        lastProducerStage = sanitize(normalizedStage)
        lastProducerKind = sanitize(normalizedKind)
        if (sanitizedDetail.isNotBlank()) {
            lastProducerDetail = sanitizedDetail
        }

        if (normalizedKind == "video") {
            lastVideoProducerStage = sanitize(normalizedStage)
            if (sanitizedDetail.isNotBlank()) {
                lastVideoProducerDetail = sanitizedDetail
            }
            if (normalizedStage == "produce-ack-error" ||
                normalizedStage == "produce-exception" ||
                normalizedStage == "producer-failed" ||
                normalizedStage == "producer-error-handle"
            ) {
                if (sanitizedDetail.isNotBlank()) {
                    lastVideoProduceFailureDetail = sanitizedDetail
                }
            }
        }
        bump()
    }

    fun recordConsumerResumeEntered(kind: String, remoteProducerId: String) {
        consumerResumeCount += 1
        when (kind.lowercase()) {
            "audio" -> audioConsumerResumeCount += 1
            "video" -> videoConsumerResumeCount += 1
        }
        lastConsumerKind = sanitize(kind)
        lastConsumerProducerId = sanitize(remoteProducerId)
        bump()
    }

    fun recordConsumerParticipantResolution(kind: String, remoteProducerId: String, participantName: String?) {
        lastConsumerKind = sanitize(kind)
        lastConsumerProducerId = sanitize(remoteProducerId)
        lastResolvedParticipant = sanitize(participantName.orEmpty())
        if (kind.lowercase() == "video" && !participantName.isNullOrBlank()) {
            videoParticipantMatchCount += 1
        }
        bump()
    }

    fun recordTrackedStreams(kind: String, count: Int) {
        when (kind.lowercase()) {
            "audio" -> trackedAudioStreamCount = count
            "video" -> trackedVideoStreamCount = count
        }
        lastConsumerKind = sanitize(kind)
        bump()
    }

    fun recordScreenDebug(stage: String, detail: String) {
        val sanitizedDetail = sanitize(detail)
        when (stage.lowercase()) {
            "bootstrap" -> bootstrapScreenDebug = sanitizedDetail
            "signal" -> {
                signalScreenDebug = sanitizedDetail
                // Persist the first screenshare-specific failure; never overwrite with non-error stages
                if (screenConsumeError.isEmpty() &&
                    (sanitizedDetail.contains("error") || sanitizedDetail.contains("invalid") ||
                     sanitizedDetail.contains("failed") || sanitizedDetail.contains("empty") ||
                     sanitizedDetail.contains("timeout") || sanitizedDetail.contains("no-rtp") ||
                     sanitizedDetail.contains("missing"))
                ) {
                    screenConsumeError = sanitizedDetail
                }
            }
            "resume" -> resumeScreenDebug = sanitizedDetail
        }
        bump()
    }

    fun recordLocalAudioEnabled(enabled: Boolean) {
        localAudioEnabled = enabled
        bump()
    }

    fun recordLocalVideoEnabled(enabled: Boolean) {
        localVideoEnabled = enabled
        bump()
    }

    fun recordLocalScreenShareEnabled(enabled: Boolean) {
        localScreenShareEnabled = enabled
        bump()
    }

    fun recordRemoteScreenShareStarted(started: Boolean) {
        remoteScreenShareStarted = started
        bump()
    }

    fun recordAlert(message: String, type: String) {
        lastAlertMessage = sanitize(message)
        lastAlertType = sanitize(type)
        bump()
    }

    fun recordDeviceLoadError(error: String) {
        lastDeviceLoadError = sanitize(error)
        bump()
    }

    fun latestSummary(): String {
        val effectiveRemoteScreenShareStarted =
            remoteScreenShareStarted || resumeScreenDebug.contains("ss=true")

        return buildString {
            append("seq=")
            append(updateSequence)
            append(";participants=")
            append(participantCount)
            append(";visibleStreams=")
            append(visibleStreamCount)
            append(";audioOnlyStreams=")
            append(audioOnlyStreamCount)
            append(";signalAttempts=")
            append(consumerSignalAttemptCount)
            append(";transportOk=")
            append(consumerTransportSuccessCount)
            append(";consumeOk=")
            append(consumerConsumeSuccessCount)
            append(";clientTransportOk=")
            append(consumerClientTransportSuccessCount)
            append(";consumerCreated=")
            append(consumerCreateSuccessCount)
            append(";resumeAckOk=")
            append(consumerResumeAckSuccessCount)
            append(";consumerResumes=")
            append(consumerResumeCount)
            append(";audioResumes=")
            append(audioConsumerResumeCount)
            append(";videoResumes=")
            append(videoConsumerResumeCount)
            append(";produceAttempts=")
            append(producerSignalAttemptCount)
            append(";produceAckOk=")
            append(producerAckSuccessCount)
            append(";audioProduced=")
            append(audioProducerSuccessCount)
            append(";videoProduced=")
            append(videoProducerSuccessCount)
            append(";produceErrors=")
            append(producerErrorCount)
            append(";trackedAudio=")
            append(trackedAudioStreamCount)
            append(";trackedVideo=")
            append(trackedVideoStreamCount)
            append(";videoMatches=")
            append(videoParticipantMatchCount)
            append(";localAudio=")
            append(localAudioEnabled)
            append(";localVideo=")
            append(localVideoEnabled)
            append(";localScreenShare=")
            append(localScreenShareEnabled)
            append(";remoteScreenShare=")
            append(effectiveRemoteScreenShareStarted)
            append(";lastSignalStage=")
            append(lastSignalStage)
            append(";lastSignalProducer=")
            append(lastSignalProducerId)
            append(";lastSignalDetail=")
            append(lastSignalDetail)
            append(";lastConsumerKind=")
            append(lastConsumerKind)
            append(";lastConsumerProducer=")
            append(lastConsumerProducerId)
            append(";lastProduceStage=")
            append(lastProducerStage)
            append(";lastProduceKind=")
            append(lastProducerKind)
            append(";lastProduceDetail=")
            append(lastProducerDetail)
            append(";lastProduceRequestDetail=")
            append(lastProduceRequestDetail)
            append(";lastProduceFailureDetail=")
            append(lastProduceFailureDetail)
            append(";lastVideoStage=")
            append(lastVideoProducerStage)
            append(";lastVideoDetail=")
            append(lastVideoProducerDetail)
            append(";lastVideoFailureDetail=")
            append(lastVideoProduceFailureDetail)
            append(";lastParticipant=")
            append(lastResolvedParticipant)
            append(";alertType=")
            append(lastAlertType)
            append(";alert=")
            append(lastAlertMessage)
            append(";bootstrapScreen=")
            append(bootstrapScreenDebug)
            append(";signalScreen=")
            append(signalScreenDebug)
            append(";screenConsumeError=")
            append(screenConsumeError)
            append(";resumeScreen=")
            append(resumeScreenDebug)
            append(";deviceLoadError=")
            append(lastDeviceLoadError)
        }
    }

    private fun bump() {
        updateSequence += 1
    }

    private fun sanitize(value: String): String {
        return value
            .replace(';', ',')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}