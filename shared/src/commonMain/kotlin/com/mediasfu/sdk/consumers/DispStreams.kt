package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream

interface DispStreamsParameters {
    val lStreams: List<Stream>
    val participants: List<com.mediasfu.sdk.model.Participant>
    val refParticipants: List<com.mediasfu.sdk.model.Participant>
    val currentUserPage: Int
    val hostLabel: String
    val mainHeightWidth: Double
    val updateMainWindow: Boolean
    val shared: Boolean
    val shareScreenStarted: Boolean
    val eventType: com.mediasfu.sdk.model.EventType
    val islevel: String
    val member: String

    val updateLStreams: (List<Stream>) -> Unit
    val updateUpdateMainWindow: (Boolean) -> Unit

    fun getUpdatedAllParams(): DispStreamsParameters
}

/**
 * Options for displaying streams.
 */
data class DispStreamsOptions(
    val lStreams: List<Stream>,
    val ind: Int,
    val auto: Boolean = false,
    val chatSkip: Boolean = false,
    val forChatCard: Any? = null,
    val forChatID: Any? = null,
    val parameters: DispStreamsParameters,
    val breakRoom: Int = -1,
    val inBreakRoom: Boolean = false
)

/**
 * Displays streams in the UI based on the provided options using parity logic.
 */
suspend fun dispStreams(options: DispStreamsOptions) {
    try {
        val baseParameters = options.parameters.getUpdatedAllParams()
        val params = baseParameters as? ChangeVidsParameters ?: run {
            Logger.e("DispStreams", "MediaSFU dispStreams: missing ChangeVidsParameters support")
            return
        }

    val ind = options.ind
    val auto = options.auto
    val chatSkip = options.chatSkip
    val forChatId = options.forChatID
    val breakRoom = options.breakRoom
    val inBreakRoom = options.inBreakRoom
    

    val prepopulateParams = params as? PrepopulateUserMediaParameters
    val rePortParams = params as? RePortParameters
    val getEstimateParams = params as? GetEstimateParameters
    val readjustParams = params as? ReadjustParameters
    val processTransportsParams = params as? ProcessConsumerTransportsParameters
    val resumeAudioParams = params as? ResumePauseAudioStreamsParameters
    val resumeStreamsParams = params as? ResumePauseStreamsParameters
    val addVideosGridParams = params as? AddVideosGridParameters

        var lStreams = options.lStreams.toMutableList()
        val filteredStreams = lStreams.filterNot { stream ->
            val producerId = stream.producerId.lowercase()
            val streamId = stream.id?.lowercase()
            val name = stream.name?.lowercase()
            producerId == "youyou" ||
                producerId == "youyouyou" ||
                streamId == "youyou" ||
                streamId == "youyouyou" ||
                name == "youyou" ||
                name == "youyouyou"
        }.toMutableList()

        val streamNames = params.streamNames
        val audStreamNames = params.audStreamNames
        val participants = params.participants
        val meetingDisplayType = params.meetingDisplayType
        val meetingVideoOptimized = params.meetingVideoOptimized
        val recordingDisplayType = params.recordingDisplayType
        val recordingVideoOptimized = params.recordingVideoOptimized
        val eventTypeName = params.eventType.name.lowercase()
        val shareScreenStarted = params.shareScreenStarted
        val shared = params.shared
        val firstAll = params.firstAll
        val prevMainHeightWidth = params.prevMainHeightWidth
        val mainHeightWidth = params.mainHeightWidth
        val hostLabel = params.hostLabel
        val firstRound = params.firstRound
        val lockScreen = params.lockScreen
        val keepBackground = params.keepBackground
        val virtualStream = params.virtualStream
        val localStreamVideo = params.localStreamVideo
        val breakOutRoomStarted = params.breakOutRoomStarted
        val breakOutRoomEnded = params.breakOutRoomEnded
        val consumerTransports = params.consumerTransports
        val prevDoPaginate = params.prevDoPaginate
        val doPaginate = params.doPaginate

        var updateMainWindow = params.updateMainWindow
        var refParticipants = params.refParticipants.toMutableList()
        var activeNames = params.activeNames.toMutableList()
        var dispActiveNames = params.dispActiveNames.toMutableList()
        val previousDispNames = params.pDispActiveNames
        var chatRefStreams = params.chatRefStreams.toMutableList()
        var shareEnded = params.shareEnded

        var proceed = true

        if (eventTypeName != "chat" && (ind == 0 || (params.islevel != "2" && params.currentUserPage == ind))) {
            proceed = false

            activeNames.clear()
            dispActiveNames.clear()

            for (stream in filteredStreams) {
                val name = resolveParticipantName(
                    stream = stream,
                    displayType = recordingDisplayType,
                    videoOptimized = recordingVideoOptimized,
                    streamNames = streamNames,
                    audioStreamNames = audStreamNames,
                    refParticipants = refParticipants
                )
                if (!name.isNullOrEmpty() && !activeNames.contains(name)) {
                    activeNames.add(name)
                }
            }
            params.updateActiveNames(activeNames.toList())

            for (stream in filteredStreams) {
                val name = resolveParticipantName(
                    stream = stream,
                    displayType = meetingDisplayType,
                    videoOptimized = meetingVideoOptimized,
                    streamNames = streamNames,
                    audioStreamNames = audStreamNames,
                    refParticipants = refParticipants
                )
                if (!name.isNullOrEmpty() && !dispActiveNames.contains(name)) {
                    dispActiveNames.add(name)
                    if (!previousDispNames.contains(name)) {
                        proceed = true
                    }
                }
            }
            params.updateDispActiveNames(dispActiveNames.toList())
            
            // Check if any participant was removed from display (parity fix)
            if (previousDispNames.any { !dispActiveNames.contains(it) }) {
                proceed = true
            }

            // Force proceed if not firstAll (Parity with TypeScript)
            if (!firstAll) {
                proceed = true
            }

            if (filteredStreams.isEmpty() && (shareScreenStarted || shared || !firstAll)) {
                proceed = true
            }

            if (!shareScreenStarted && !shared && prevMainHeightWidth != mainHeightWidth) {
                updateMainWindow = true
                params.updateUpdateMainWindow(true)
            }

            params.updateNForReadjustRecord(activeNames.size)
        }

        if (!proceed && auto) {
            if ((updateMainWindow && !lockScreen && !shared) || !firstRound) {
                if (prepopulateParams != null) {
                    params.prepopulateUserMedia(
                        PrepopulateUserMediaOptions(
                            name = hostLabel,
                            parameters = prepopulateParams
                        )
                    )
                } else {
                    Logger.e("DispStreams", "MediaSFU dispStreams: missing PrepopulateUserMediaParameters")
                }
            }

            if (ind == 0 && eventTypeName != "chat") {
                if (rePortParams != null) {
                    params.rePort(RePortOptions(parameters = rePortParams))
                } else {
                    Logger.e("DispStreams", "MediaSFU dispStreams: missing RePortParameters")
                }
            }
            return
        }

        
        when (eventTypeName) {
            "broadcast" -> {
                lStreams = filteredStreams
                params.updateLStreams(lStreams.toList())
            }

            "chat" -> {
                if (forChatId != null) {
                    lStreams = chatRefStreams.toMutableList()
                    params.updateLStreams(lStreams.toList())
                } else {
                    if (params.islevel != "2") {
                        val host = participants.firstOrNull { it.islevel == "2" }
                        val remoteProducerId = host?.videoID
                        if (remoteProducerId != null && remoteProducerId.isNotEmpty()) {
                            val hostStream = params.oldAllStreams.firstOrNull { it.producerId == remoteProducerId }
                            if (hostStream != null) {
                                lStreams = lStreams.filter { it.name != host.name }.toMutableList()
                                lStreams.add(hostStream)
                            }
                        }
                    }

                    val selfStream = lStreams.firstOrNull {
                        it.producerId.equals("youyou", ignoreCase = true) ||
                            it.producerId.equals("youyouyou", ignoreCase = true)
                    }

                    lStreams = lStreams.filterNot {
                        it.producerId.equals("youyou", ignoreCase = true) ||
                            it.producerId.equals("youyouyou", ignoreCase = true)
                    }.toMutableList()

                    if (selfStream != null) {
                        lStreams.add(selfStream)
                    }

                    chatRefStreams = lStreams.toMutableList()
                    params.updateLStreams(lStreams.toList())
                    params.updateChatRefStreams(chatRefStreams.toList())
                }
            }
            
            else -> {
                // For conference, webinar, and other event types:
                // Keep the lStreams passed from changeVids (which already includes all participants).
                // Only use filteredStreams for name resolution, not for stream selection.
                // The lStreams from changeVids already contains the correct set of streams to display.
            }
        }

        val totalStreams = lStreams.size
        val estimate = try {
            if (getEstimateParams != null) {
                val result = params.getEstimate(GetEstimateOptions(n = totalStreams, parameters = getEstimateParams))
                result
            } else {
                Logger.e("DispStreams", "MediaSFU dispStreams: missing GetEstimateParameters")
                listOf(totalStreams, 0, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            listOf(totalStreams, 0, 0)
        }
        val rows = estimate.getOrNull(1) ?: 0
        val cols = estimate.getOrNull(2) ?: 0

        var gridResult = runCatching {
            checkGrid(CheckGridOptions(rows = rows, cols = cols, actives = totalStreams))
        }.getOrElse {
            Logger.e("DispStreams", "MediaSFU dispStreams: checkGrid failed - ${it.message}")
            CheckGridResult(false, 0, 0, 0, 0, 0, 0)
        }

        if (chatSkip && eventTypeName == "chat") {
            gridResult = gridResult.copy(numRows = 1, numCols = 1, actualRows = 1)
        }

        if (readjustParams != null) {
            params.readjust(
                ReadjustOptions(
                    n = totalStreams,
                    state = ind,
                    parameters = readjustParams
                )
            )
        } else {
            Logger.e("DispStreams", "MediaSFU dispStreams: missing ReadjustParameters")
        }

        if (doPaginate || prevDoPaginate != doPaginate || shared || shareScreenStarted || shareEnded) {
            if (processTransportsParams != null) {
                params.processConsumerTransports(
                    ProcessConsumerTransportsOptions(
                        consumerTransports = consumerTransports,
                        lStreams_ = filteredStreams.toList(),
                        parameters = processTransportsParams
                    )
                ).onFailure {
                    Logger.e("DispStreams", "MediaSFU dispStreams: processConsumerTransports failed - ${it.message}")
                }
            } else {
                Logger.e("DispStreams", "MediaSFU dispStreams: missing ProcessConsumerTransportsParameters")
            }

            try {
                // Apply breakout-room audio filtering both in lobby and inside rooms.
                if (breakOutRoomStarted && !breakOutRoomEnded) {
                    if (resumeAudioParams != null) {
                        params.resumePauseAudioStreams(
                            ResumePauseAudioStreamsOptions(
                                breakRoom = breakRoom,
                                inBreakRoom = inBreakRoom,
                                parameters = resumeAudioParams
                            )
                        )
                    }
                } else {
                    // Breakout finished or never started: restore full audio set before video resumption.
                    if (resumeAudioParams != null) {
                        params.resumePauseAudioStreams(
                            ResumePauseAudioStreamsOptions(
                                breakRoom = -1,
                                inBreakRoom = false,
                                parameters = resumeAudioParams
                            )
                        )
                    }
                    if (resumeStreamsParams != null) {
                        params.resumePauseStreams(ResumePauseStreamsOptions(parameters = resumeStreamsParams))
                    }
                }
            } catch (error: Exception) {
                Logger.e("DispStreams", "MediaSFU dispStreams: resumePause* (1) failed - ${error.message}")
            }

            try {
                if (!breakOutRoomStarted || (breakOutRoomStarted && breakOutRoomEnded)) {
                    if (resumeStreamsParams != null) {
                        params.resumePauseStreams(ResumePauseStreamsOptions(parameters = resumeStreamsParams))
                    } else {
                        Logger.e("DispStreams", "MediaSFU dispStreams: missing ResumePauseStreamsParameters")
                    }
                }
            } catch (error: Exception) {
                Logger.e("DispStreams", "MediaSFU dispStreams: resumePauseStreams (2) failed - ${error.message}")
            }

            if (shareEnded) {
                shareEnded = false
                params.updateShareEnded(false)
            }
        }

        // Match Flutter: use numToAdd directly to split main/alt grids
        val numToAdd = gridResult.numToAdd

        val mainGridStreams = lStreams.take(numToAdd)
        val altGridStreams = lStreams.drop(numToAdd)

        if (addVideosGridParams != null) {
            params.addVideosGrid(
                AddVideosGridOptions(
                    mainGridStreams = mainGridStreams,
                    altGridStreams = altGridStreams,
                    numRows = gridResult.numRows,
                    numCols = gridResult.numCols,
                    actualRows = gridResult.actualRows,
                    lastRowCols = gridResult.lastRowCols,
                    removeAltGrid = gridResult.removeAltGrid,
                    parameters = addVideosGridParams
                )
            )
        } else {
            Logger.e("DispStreams", "MediaSFU dispStreams: missing AddVideosGridParameters")
        }

        if (updateMainWindow && !lockScreen && !shared) {
            if (prepopulateParams != null) {
                params.prepopulateUserMedia(
                    PrepopulateUserMediaOptions(
                        name = hostLabel,
                        parameters = prepopulateParams
                    )
                )
            }
        } else if (!firstRound) {
            if (prepopulateParams != null) {
                params.prepopulateUserMedia(
                    PrepopulateUserMediaOptions(
                        name = hostLabel,
                        parameters = prepopulateParams
                    )
                )
            }
        }

        if (ind == 0 && eventTypeName != "chat") {
            if (rePortParams != null) {
                params.rePort(RePortOptions(parameters = rePortParams))
            }
        }
    } catch (error: Exception) {
        Logger.e("DispStreams", "MediaSFU dispStreams error: ${error.message}")
    }
}

private fun resolveParticipantName(
    stream: Stream,
    displayType: String,
    videoOptimized: Boolean,
    streamNames: List<Stream>,
    audioStreamNames: List<Stream>,
    refParticipants: List<Participant>
): String? {
    val normalizedDisplay = displayType.lowercase()
    val hasProducer = stream.producerId.isNotEmpty()
    val audioId = stream.audioID.orEmpty()
    val hasAudio = audioId.isNotEmpty()
    val streamName = stream.name.orEmpty()
    val hasName = streamName.isNotEmpty()

    val checkLevel = when (normalizedDisplay) {
        "video" -> {
            if (videoOptimized) {
                if (hasProducer) 0 else -1
            } else {
                if (hasProducer || hasAudio) 1 else -1
            }
        }

        "media" -> if (hasProducer || hasAudio) 1 else -1
        else -> if (hasProducer || hasAudio || hasName) 2 else -1
    }

    if (checkLevel < 0) return null

    fun fromProducer(): String? = if (hasProducer) {
        streamNames.firstOrNull { it.producerId == stream.producerId }?.name
    } else {
        null
    }

    fun fromAudio(): String? {
        if (!hasAudio) return null
        return audioStreamNames.firstOrNull { it.producerId == audioId }?.name
            ?: refParticipants.firstOrNull { it.audioID == audioId }?.name
    }

    fun fromName(): String? = if (hasName) {
        refParticipants.firstOrNull { it.name == streamName }?.name ?: streamName
    } else {
        null
    }

    return when (checkLevel) {
        0 -> fromProducer()
        1 -> fromProducer() ?: fromAudio()
        else -> fromProducer() ?: fromAudio() ?: fromName()
    }
}