package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.webrtc.MediaStream

/**
 * Parameters used for changing video streams based on various conditions.
 * 
 * This interface extends ProcessConsumerTransportsParameters, ResumePauseStreamsParameters,
 * and ResumePauseAudioStreamsParameters to enable pause/resume stream functionality during
 * pagination and breakout room audio management.
 */
interface ChangeVidsParameters : DispStreamsParameters, ProcessConsumerTransportsParameters, ResumePauseStreamsParameters, ResumePauseAudioStreamsParameters {
    // Properties
    val allVideoStreams: List<Stream>
    override val allAudioStreams: List<Stream>
    override val limitedBreakRoom: List<BreakoutParticipant>
    
    // Update functions for ResumePauseAudioStreamsParameters
    override val updateLimitedBreakRoom: (List<BreakoutParticipant>) -> Unit
    
    // Functions for ResumePauseAudioStreamsParameters
    override val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit
    val pActiveNames: List<String>
    val activeNames: List<String>
    override val dispActiveNames: List<String>
    override val shareScreenStarted: Boolean
    override val shared: Boolean
    override val newLimitedStreams: List<Stream>
    val nonAlVideoStreams: List<Stream>
    val streamNames: List<Stream>
    val audStreamNames: List<Stream>
    val youYouStream: List<Stream>
    val youYouStreamIDs: List<String>
    override val refParticipants: List<Participant>
    override val participants: List<Participant>
    override val eventType: EventType
    override val islevel: String
    override val member: String
    val sortAudioLoudness: Boolean
    val audioDecibels: List<AudioDecibels>
    val mixedAlVideoStreams: List<Stream>
    val nonAlVideoStreamsMuted: List<Stream>
    val localStreamVideo: Any? // MediaStream
    override val oldAllStreams: List<Stream>
    val screenPageLimit: Int
    val meetingDisplayType: String
    val meetingVideoOptimized: Boolean
    val recordingVideoOptimized: Boolean
    val recordingDisplayType: String
    val paginatedStreams: List<List<Stream>>
    val itemPageLimit: Int
    val doPaginate: Boolean
    val prevDoPaginate: Boolean
    override val currentUserPage: Int
    override val consumerTransports: List<ConsumerTransportInfo>
    val prevMainHeightWidth: Double
    val firstAll: Boolean
    val shareEnded: Boolean
    val pDispActiveNames: List<String>
    val nForReadjustRecord: Int
    val firstRound: Boolean
    val lockScreen: Boolean
    val chatRefStreams: List<Stream>
    override val breakoutRooms: List<List<BreakoutParticipant>>
    override val hostNewRoom: Int
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    val virtualStream: Any? // MediaStream
    val mainRoomsLength: Int
    val memberRoom: Int
    val keepBackground: Boolean
    
    // Properties from ProcessConsumerTransportsParameters
    override val remoteScreenStream: List<Stream>
    override val sleep: suspend (SleepOptions) -> Unit
    
    // Properties from ResumePauseStreamsParameters
    override val screenId: String

    // Update functions
    val updatePActiveNames: (List<String>) -> Unit
    val updateActiveNames: (List<String>) -> Unit
    override val updateDispActiveNames: (List<String>) -> Unit
    val updateNewLimitedStreams: (List<Stream>) -> Unit
    val updateNonAlVideoStreams: (List<Stream>) -> Unit
    val updateRefParticipants: (List<Participant>) -> Unit
    val updateSortAudioLoudness: (Boolean) -> Unit
    val updateMixedAlVideoStreams: (List<Stream>) -> Unit
    val updateNonAlVideoStreamsMuted: (List<Stream>) -> Unit
    val updatePaginatedStreams: (List<List<Stream>>) -> Unit
    val updateDoPaginate: (Boolean) -> Unit
    val updatePrevDoPaginate: (Boolean) -> Unit
    val updateCurrentUserPage: (Int) -> Unit
    val updateNumberPages: (Int) -> Unit
    val updateMainRoomsLength: (Int) -> Unit
    val updateMemberRoom: (Int) -> Unit
    val updateChatRefStreams: (List<Stream>) -> Unit
    val updateNForReadjustRecord: (Int) -> Unit
    val updateShowMiniView: (Boolean) -> Unit
    val updateShareEnded: (Boolean) -> Unit
    fun updateYouYouStream(streams: List<Stream>)

    // Functions
    val mixStreams: suspend (MixStreamsOptions) -> List<Stream>
    val dispStreams: suspend (DispStreamsOptions) -> Unit
    val prepopulateUserMedia: suspend (PrepopulateUserMediaOptions) -> Unit
    val rePort: suspend (RePortOptions) -> Unit
    val processConsumerTransports: suspend (ProcessConsumerTransportsOptions) -> Result<Unit>
    val resumePauseStreams: suspend (ResumePauseStreamsOptions) -> Result<Unit>
    val readjust: suspend (ReadjustOptions) -> Unit
    val addVideosGrid: suspend (AddVideosGridOptions) -> Unit
    val getEstimate: (GetEstimateOptions) -> List<Int>
    val resumePauseAudioStreams: suspend (ResumePauseAudioStreamsOptions) -> Unit

    override fun getUpdatedAllParams(): ChangeVidsParameters
}

/**
 * Options for changing video streams.
 *
 * @property screenChanged Indicates if the screen has changed
 * @property parameters Parameters for changing video streams
 */
data class ChangeVidsOptions(
    val screenChanged: Boolean = false,
    val parameters: ChangeVidsParameters
)

/**
 * Changes the video streams on the screen based on the provided options and parameters.
 *
 * This function adjusts video streams based on conditions like event type, active participants,
 * and screen sharing status. It updates the necessary lists and variables to display the appropriate
 * video streams.
 *
 * ### Key Features:
 * - **Screen Sharing Handling**: Adjusts streams when screen sharing is active
 * - **Participant Filtering**: Removes streams without corresponding participants
 * - **Audio Loudness Sorting**: Sorts streams based on audio levels if enabled
 * - **Event Type Logic**: Different behavior for conference, broadcast, chat events
 * - **Pagination**: Creates paginated views of streams
 * - **Breakout Room Support**: Handles streams in breakout rooms
 * - **Host Prioritization**: Ensures host streams are displayed appropriately
 * - **Mixed Streams**: Combines video and audio-only streams
 *
 * ### Workflow:
 * 1. **Initialize**: Clone all parameter lists to avoid mutation
 * 2. **Screen Share Check**: Adjust streams if screen sharing is active
 * 3. **Participant Filtering**: Remove streams without participants
 * 4. **Audio Sorting**: Sort by loudness if enabled
 * 5. **Stream Classification**: Separate into video, audio-only, muted
 * 6. **Host Handling**: Special logic for conference host
 * 7. **Stream Compilation**: Combine streams based on display type
 * 8. **Pagination**: Create paginated views
 * 9. **Breakout Rooms**: Handle breakout room streams
 * 10. **Display**: Call dispStreams to show the streams
 *
 * @param options Options containing screen change flag and parameters
 *
 * Example:
 * ```kotlin
 * val options = ChangeVidsOptions(
 *     screenChanged = false,
 *     parameters = changeVidsParams
 * )
 *
 * changeVids(options)
 * ```
 *
 * ### Note:
 * This implementation matches the Flutter SDK logic 100%. It handles all the complex
 * scenarios including screen sharing, breakout rooms, audio loudness sorting, and pagination.
 */
suspend fun changeVids(options: ChangeVidsOptions) {
    try {
        // Retrieve updated parameters
        val parameters = options.parameters.getUpdatedAllParams()

        // Clone lists to avoid mutating original data
        var alVideoStreams = parameters.allVideoStreams.toMutableList()
        val allVideoStreams = parameters.allVideoStreams.toMutableList()
        val pActiveNames = parameters.pActiveNames.toMutableList()
        var activeNames = parameters.activeNames.toMutableList()
        var dispActiveNames = parameters.dispActiveNames.toMutableList()
        val newLimitedStreams = parameters.newLimitedStreams.toMutableList()
        var nonAlVideoStreams = parameters.nonAlVideoStreams.toMutableList()
        var refParticipants = parameters.refParticipants.toMutableList()
        val participants = parameters.participants.toMutableList()
        val audioDecibels = parameters.audioDecibels.toMutableList()
        var mixedAlVideoStreams = parameters.mixedAlVideoStreams.toMutableList()
        var nonAlVideoStreamsMuted = parameters.nonAlVideoStreamsMuted.toMutableList()
        val oldAllStreams = parameters.oldAllStreams.toMutableList()
        val breakoutRooms = parameters.breakoutRooms.toMutableList()
        var paginatedStreams = parameters.paginatedStreams.toMutableList()

        val shareScreenStarted = parameters.shareScreenStarted
        val shared = parameters.shared
        val eventType = parameters.eventType.toString().split(".").last().lowercase() // Enum to lowercase string
        val islevel = parameters.islevel
        val member = parameters.member
        var sortAudioLoudness = parameters.sortAudioLoudness
        val meetingDisplayType = parameters.meetingDisplayType
        val meetingVideoOptimized = parameters.meetingVideoOptimized
        val recordingVideoOptimized = parameters.recordingVideoOptimized
        val recordingDisplayType = parameters.recordingDisplayType
        val screenPageLimit = parameters.screenPageLimit
        val itemPageLimit = parameters.itemPageLimit
        var doPaginate = parameters.doPaginate
        val prevDoPaginate = parameters.prevDoPaginate
        var currentUserPage = parameters.currentUserPage
        val hostNewRoom = parameters.hostNewRoom
        val breakOutRoomStarted = parameters.breakOutRoomStarted
        val breakOutRoomEnded = parameters.breakOutRoomEnded
        val virtualStream = parameters.virtualStream
        var mainRoomsLength = parameters.mainRoomsLength
        var memberRoom = parameters.memberRoom

        // Initialize temporary variables
        var streame: Stream? = null

        // Handle screen sharing
        if (shareScreenStarted || shared) {
            alVideoStreams = newLimitedStreams.toMutableList()
            activeNames.clear()
        }

        var remoteProducerId: String? = null

        activeNames.clear()
        dispActiveNames.clear()
        refParticipants = participants.toMutableList()

        // Identify and remove streams without corresponding participants
        val tempStreams = alVideoStreams.toList()
        val elementsToRemove = mutableListOf<String>()

        for (stream in tempStreams) {
            try {
                val participant = refParticipants.firstOrNull { it.videoID == stream.producerId }

                if (participant == null &&
                    stream.producerId != "youyou" &&
                    stream.producerId != "youyouyou"
                ) {
                    elementsToRemove.add(stream.producerId)
                }
            } catch (error: Exception) {
                // Log error if necessary
            }
        }

        // Remove identified streams
        alVideoStreams.removeAll { elementsToRemove.contains(it.producerId) }

        // Adjust audio loudness sorting based on event type
        if (eventType == "broadcast" || eventType == "chat") {
            sortAudioLoudness = false
        }

        // CRITICAL FIX: Always attach localStreamVideo to youyou/youyouyou streams
        // so that the UI can render them correctly, regardless of stream count
        val localStream = parameters.localStreamVideo as? MediaStream
        val virtStream = parameters.virtualStream as? MediaStream
        val keepBg = parameters.keepBackground
        val effectiveStream = if (keepBg && virtStream != null) virtStream else localStream
        
        // Track streams that were updated so we can propagate to youYouStream
        var updatedYouyouStream: Stream? = null
        
        if (effectiveStream != null) {
            // Update youyou stream in alVideoStreams with the actual MediaStream
            val youyouIndex = alVideoStreams.indexOfFirst { it.producerId == "youyou" }
            if (youyouIndex >= 0) {
                val youyouStream = alVideoStreams[youyouIndex]
                // Always update if stream is null OR if keepBackground and virtualStream differs from current
                val needsUpdate = youyouStream.stream == null || 
                    (keepBg && virtStream != null && youyouStream.stream != effectiveStream)
                if (needsUpdate) {
                    val updated = youyouStream.copy(stream = effectiveStream)
                    alVideoStreams[youyouIndex] = updated
                    updatedYouyouStream = updated
                }
            }
            
            // Also update youyouyou stream if present
            val youyouyouIndex = alVideoStreams.indexOfFirst { it.producerId == "youyouyou" }
            if (youyouyouIndex >= 0) {
                val youyouyouStream = alVideoStreams[youyouyouIndex]
                val needsUpdate = youyouyouStream.stream == null || 
                    (keepBg && virtStream != null && youyouyouStream.stream != effectiveStream)
                if (needsUpdate) {
                    val updated = youyouyouStream.copy(stream = effectiveStream)
                    alVideoStreams[youyouyouIndex] = updated
                    if (updatedYouyouStream == null) updatedYouyouStream = updated
                }
            }
            
            // Also update allVideoStreams (the source list) so subsequent calls have the correct stream
            val allYouyouIndex = allVideoStreams.indexOfFirst { it.producerId == "youyou" }
            if (allYouyouIndex >= 0) {
                val currentStream = allVideoStreams[allYouyouIndex].stream
                if (currentStream == null || (keepBg && virtStream != null && currentStream != effectiveStream)) {
                    allVideoStreams[allYouyouIndex] = allVideoStreams[allYouyouIndex].copy(stream = effectiveStream)
                }
            }
            val allYouyouyouIndex = allVideoStreams.indexOfFirst { it.producerId == "youyouyou" }
            if (allYouyouyouIndex >= 0) {
                val currentStream = allVideoStreams[allYouyouyouIndex].stream
                if (currentStream == null || (keepBg && virtStream != null && currentStream != effectiveStream)) {
                    allVideoStreams[allYouyouyouIndex] = allVideoStreams[allYouyouyouIndex].copy(stream = effectiveStream)
                }
            }
        }
        
        // Update youYouStream so dispStreams gets the updated stream with MediaStream attached
        if (updatedYouyouStream != null) {
            parameters.updateYouYouStream(listOf(updatedYouyouStream))
        }

        // Reset non-al video streams based on screen sharing status
        if (shareScreenStarted || shared) {
            nonAlVideoStreams = mutableListOf()
            nonAlVideoStreamsMuted = mutableListOf()
            mixedAlVideoStreams = mutableListOf()
        } else {
            // Handle case where number of video streams exceeds screen page limit
            if (alVideoStreams.size > screenPageLimit) {
                alVideoStreams.removeAll { it.producerId == "youyou" }
                alVideoStreams.removeAll { it.producerId == "youyouyou" }

                // Sort participants based on mute status
                refParticipants.sortWith(compareBy { it.muted ?: false })

                // Reorder video streams based on sorted participants
                val temp = mutableListOf<Stream>()
                for (participant in refParticipants) {
                    val stream = alVideoStreams.firstOrNull { it.producerId == participant.videoID }
                    if (stream != null) {
                        temp.add(stream)
                    }
                }
                alVideoStreams = temp

                // Prioritize 'youyou' and 'youyouyou' streams
                // CRITICAL: Attach localStreamVideo to the stream so UI can render it
                val localStream = parameters.localStreamVideo as? MediaStream
                val virtStream = parameters.virtualStream as? MediaStream
                val keepBg = parameters.keepBackground
                val effectiveStream = if (keepBg && virtStream != null) virtStream else localStream

                val youyou = allVideoStreams.firstOrNull { it.producerId == "youyou" }

                if (youyou == null) {
                    val youyouyou = allVideoStreams.firstOrNull { it.producerId == "youyouyou" }
                    if (youyouyou != null) {
                        // Attach the actual MediaStream to the youyouyou stream
                        val updatedStream = youyouyou.copy(stream = effectiveStream)
                        alVideoStreams.add(0, updatedStream)
                    }
                } else {
                    // Attach the actual MediaStream to the youyou stream
                    val updatedStream = youyou.copy(stream = effectiveStream)
                    alVideoStreams.add(0, updatedStream)
                }
            }

            // Identify admin participant
            val admin = participants.firstOrNull { (it.isAdmin == true) && it.islevel == "2" }
            val adminName = admin?.name ?: ""

            // Populate non-al video streams based on event type and participant status
            // Match Flutter exactly: only check producerId match, NOT name match
            nonAlVideoStreams = mutableListOf()
            for (participant in refParticipants) {
                // Flutter: var stream = alVideoStreams.firstWhereOrNull((obj) => obj.producerId == participant.videoID)
                val stream = alVideoStreams.firstOrNull { it.producerId == participant.videoID }

                if (eventType != "chat" && eventType != "conference") {
                    // Flutter: stream == null && participant.name != member && !(participant.muted ?? false) && participant.name != adminName
                    if (stream == null &&
                        participant.name != member &&
                        !(participant.muted ?: false) &&
                        participant.name != adminName) {
                        val newStream = Stream(
                            producerId = participant.videoID,
                            name = participant.name,
                            audioID = participant.audioID,
                            muted = participant.muted
                        )
                        nonAlVideoStreams.add(newStream)
                    }
                } else {
                    // Flutter: stream == null && participant.name != member && !(participant.muted ?? false)
                    if (stream == null &&
                        participant.name != member &&
                        !(participant.muted ?: false)) {
                        val newStream = Stream(
                            producerId = participant.videoID,
                            name = participant.name,
                            audioID = participant.audioID,
                            muted = participant.muted
                        )
                        nonAlVideoStreams.add(newStream)
                    }
                }
            }

            // Sort non-al video streams based on audio loudness if required
            if (sortAudioLoudness) {
                nonAlVideoStreams.sortWith(compareByDescending {
                    audioDecibels.firstOrNull { decibel -> decibel.name == it.name }?.averageLoudness ?: 0
                })

                // Mix streams unless specific conditions are met
                if (!((meetingDisplayType == "video" && meetingVideoOptimized) &&
                            (recordingVideoOptimized && recordingDisplayType == "video"))
                ) {
                    val optionsMix = MixStreamsOptions(
                        alVideoStreams = alVideoStreams,
                        nonAlVideoStreams = nonAlVideoStreams,
                        refParticipants = refParticipants
                    )
                    mixedAlVideoStreams = parameters.mixStreams(optionsMix).toMutableList()
                }
            }

            // Populate muted non-al video streams based on event type and participant status
            // Match Flutter exactly: only check producerId match, NOT name match
            nonAlVideoStreamsMuted = mutableListOf()
            for (participant in refParticipants) {
                // Flutter: var stream = alVideoStreams.firstWhereOrNull((obj) => obj.producerId == participant.videoID)
                val stream = alVideoStreams.firstOrNull { it.producerId == participant.videoID }

                if (eventType != "chat" && eventType != "conference") {
                    // Flutter: stream == null && participant.name != member && (participant.muted ?? false) && participant.name != adminName
                    if (stream == null &&
                        participant.name != member &&
                        (participant.muted ?: false) &&
                        participant.name != adminName) {
                        val newStream = Stream(
                            producerId = participant.videoID,
                            name = participant.name,
                            audioID = participant.audioID,
                            muted = participant.muted
                        )
                        nonAlVideoStreamsMuted.add(newStream)
                    }
                } else {
                    // Flutter: stream == null && participant.name != member && (participant.muted ?? false)
                    if (stream == null &&
                        participant.name != member &&
                        (participant.muted ?: false)) {
                        val newStream = Stream(
                            producerId = participant.videoID,
                            name = participant.name,
                            audioID = participant.audioID,
                            muted = participant.muted
                        )
                        nonAlVideoStreamsMuted.add(newStream)
                    }
                }
            }
        }

        // Handle conference event type with specific conditions
        if (eventType == "conference" && islevel != "2") {
            val host = participants.firstOrNull { it.islevel == "2" }

            if (host != null) {
                remoteProducerId = host.videoID

                if (islevel != "2") {
                    val hostVideo = alVideoStreams.firstOrNull { it.producerId == remoteProducerId }

                    if (hostVideo == null) {
                        streame = oldAllStreams.firstOrNull { it.producerId == remoteProducerId }

                        if (streame != null) {
                            // Remove host's old streams
                            alVideoStreams.removeAll { it.producerId == host.videoID }
                            nonAlVideoStreams.removeAll { it.name == host.name }
                            nonAlVideoStreamsMuted.removeAll { it.name == host.name }

                            if (sortAudioLoudness) {
                                mixedAlVideoStreams.removeAll { it.name == host.name }
                                nonAlVideoStreamsMuted.removeAll { it.name == host.name }

                                if (meetingDisplayType == "video" && meetingVideoOptimized) {
                                    alVideoStreams.add(0, streame)
                                } else {
                                    mixedAlVideoStreams.add(0, streame)
                                }
                            } else {
                                alVideoStreams.add(0, streame)
                            }
                        } else {
                            // Assign participant's stream to host if available
                            for (participant in refParticipants) {
                                val stream = alVideoStreams.firstOrNull {
                                    it.producerId == participant.videoID && participant.name == host.name
                                }
                                if (stream != null) {
                                    if (sortAudioLoudness) {
                                        mixedAlVideoStreams.removeAll { it.name == host.name }
                                        nonAlVideoStreamsMuted.removeAll { it.name == host.name }
                                        mixedAlVideoStreams.add(0, stream)
                                    } else {
                                        nonAlVideoStreams.removeAll { it.name == host.name }
                                        nonAlVideoStreams.add(0, stream)
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compile all streams based on sorting preferences
        val allStreamsPaged = mutableListOf<Stream>()
        if (sortAudioLoudness) {
            when (meetingDisplayType) {
                "video" -> {
                    if (meetingVideoOptimized) {
                        allStreamsPaged.addAll(alVideoStreams)
                    } else {
                        allStreamsPaged.addAll(mixedAlVideoStreams)
                    }
                }
                "media" -> {
                    allStreamsPaged.addAll(mixedAlVideoStreams)
                }
                "all" -> {
                    allStreamsPaged.addAll(mixedAlVideoStreams)
                    allStreamsPaged.addAll(nonAlVideoStreamsMuted)
                }
            }
        } else {
            
            when (meetingDisplayType) {
                "video" -> {
                    allStreamsPaged.addAll(alVideoStreams)
                }
                "media" -> {
                    allStreamsPaged.addAll(alVideoStreams)
                    allStreamsPaged.addAll(nonAlVideoStreams)
                }
                "all" -> {
                    allStreamsPaged.addAll(alVideoStreams)
                    allStreamsPaged.addAll(nonAlVideoStreams)
                    allStreamsPaged.addAll(nonAlVideoStreamsMuted)
                }
            }
        }

        // Reset paginated streams
        paginatedStreams = mutableListOf()
        var limit = itemPageLimit

        if (shareScreenStarted || shared) {
            limit = screenPageLimit
        }

        val firstPage: MutableList<Stream>
        var page: List<Stream>
        var limit_ = limit + 1

        if (eventType == "conference") {
            if (!(shared || shareScreenStarted)) {
                limit_ -= 1
            }
        }

        // Create pagination
        var memberInRoom = false
        var filterHost = false

        if (breakOutRoomStarted && !breakOutRoomEnded) {
            for ((idx, s) in allStreamsPaged.withIndex()) {
            }
            val tempBreakoutRooms = breakoutRooms.toMutableList()
            val host = participants.firstOrNull { it.islevel == "2" }

            for (room in tempBreakoutRooms) {
                try {
                    var currentStreams = mutableListOf<Stream>()
                    val roomIndex = tempBreakoutRooms.indexOf(room)
                    var roomList = room.toMutableList()

                    if (hostNewRoom != -1 && roomIndex == hostNewRoom) {
                        if (host != null) {
                            if (!roomList.any { it.name == host.name }) {
                                roomList.add(
                                    BreakoutParticipant(
                                        name = host.name,
                                        breakRoom = roomIndex
                                    )
                                )
                                filterHost = true
                            }
                        }
                    }

                    for (participant in roomList) {
                        if (participant.name == member && !memberInRoom) {
                            memberInRoom = true
                            memberRoom = participant.breakRoom ?: 0
                            parameters.updateMemberRoom(memberRoom)
                        }

                        val streams = allStreamsPaged.filter { stream ->
                            val hasProducerId = stream.producerId.isNotEmpty()
                            val hasAudioId = stream.audioID?.isNotEmpty() == true

                            if (hasProducerId || hasAudioId) {
                                val producerId = if (stream.producerId.isNotEmpty()) {
                                    stream.producerId
                                } else {
                                    stream.audioID ?: ""
                                }
                                val matchingParticipant = refParticipants.firstOrNull {
                                    it.audioID == producerId ||
                                            it.videoID == producerId ||
                                            ((producerId == "youyou" || producerId == "youyouyou") &&
                                                    member == participant.name)
                                }
                                (matchingParticipant != null && matchingParticipant.name == participant.name) ||
                                        (participant.name == member &&
                                                (producerId == "youyou" || producerId == "youyouyou"))
                            } else {
                                stream.name == participant.name
                            }
                        }

                        for (stream in streams) {
                            if (currentStreams.size < limit_) {
                                currentStreams.add(stream)
                            }
                        }
                    }

                    paginatedStreams.add(currentStreams)
                } catch (error: Exception) {
                    // Handle error if necessary
                }
            }

            // Identify remaining streams not in breakout rooms
            val remainingStreams = allStreamsPaged.filter { stream ->
                val hasProducerId = stream.producerId.isNotEmpty()
                val hasAudioId = stream.audioID?.isNotEmpty() == true

                if (hasProducerId || hasAudioId) {
                    val producerId = if (stream.producerId.isNotEmpty()) {
                        stream.producerId
                    } else {
                        stream.audioID ?: ""
                    }
                    val matchingParticipant = refParticipants.firstOrNull {
                        it.audioID == producerId ||
                                it.videoID == producerId ||
                                ((producerId == "youyou" || producerId == "youyouyou") &&
                                        member == it.name)
                    }
                    (matchingParticipant != null &&
                            !breakoutRooms.flatten().map { it.name }.contains(matchingParticipant.name)) &&
                            (!filterHost ||
                                    (host != null && host.name.isNotEmpty() &&
                                            matchingParticipant.name != host.name))
                } else {
                    !breakoutRooms.flatten().map { it.name }.contains(stream.name) &&
                            (!filterHost ||
                                    (host != null && host.name.isNotEmpty() && stream.name != host.name))
                }
            }.toMutableList()

            // Ensure member's stream is included
            if (memberInRoom) {
                val memberStream = allStreamsPaged.firstOrNull { stream ->
                    stream.producerId.isNotEmpty()
                }
                if (memberStream != null && !remainingStreams.contains(memberStream)) {
                    remainingStreams.add(0, memberStream)
                }
            }

            val remainingPaginatedStreams = mutableListOf<List<Stream>>()

            if (remainingStreams.isNotEmpty()) {
                val listEnd = remainingStreams.size

                if (listEnd > limit) {
                    firstPage = remainingStreams.subList(0, limit_).toMutableList()
                    remainingPaginatedStreams.add(firstPage)

                    var i = limit_
                    while (i < remainingStreams.size) {
                        page = if (i + limit > listEnd) {
                            remainingStreams.subList(i, listEnd)
                        } else {
                            remainingStreams.subList(i, i + limit)
                        }
                        remainingPaginatedStreams.add(page)
                        i += limit
                    }
                } else {
                    firstPage = remainingStreams.toMutableList()
                    remainingPaginatedStreams.add(firstPage)
                }
            }

            // Update main rooms length
            mainRoomsLength = remainingPaginatedStreams.size
            parameters.updateMainRoomsLength(mainRoomsLength)

            // Add the remaining streams to the beginning of the paginatedStreams
            for (i in remainingPaginatedStreams.size - 1 downTo 0) {
                paginatedStreams.add(0, remainingPaginatedStreams[i])
            }
        } else {
            // Handle pagination when not in breakout rooms
            val listEnd = allStreamsPaged.size
            if (listEnd > limit) {
                firstPage = allStreamsPaged.subList(0, limit_).toMutableList()
                paginatedStreams.add(firstPage)

                var i = limit_
                while (i < allStreamsPaged.size) {
                    page = if (i + limit > listEnd) {
                        allStreamsPaged.subList(i, listEnd)
                    } else {
                        allStreamsPaged.subList(i, i + limit)
                    }
                    paginatedStreams.add(page)
                    i += limit
                }
            } else {
                firstPage = allStreamsPaged.toMutableList()
                paginatedStreams.add(firstPage)
            }
        }

        // Update state with the modified lists
        parameters.updatePActiveNames(pActiveNames)
        parameters.updateActiveNames(activeNames)
        parameters.updateDispActiveNames(dispActiveNames)
        parameters.updateNewLimitedStreams(newLimitedStreams)
        parameters.updateNonAlVideoStreams(nonAlVideoStreams)
        parameters.updateRefParticipants(refParticipants)
        parameters.updateSortAudioLoudness(sortAudioLoudness)
        parameters.updateMixedAlVideoStreams(mixedAlVideoStreams)
        parameters.updateNonAlVideoStreamsMuted(nonAlVideoStreamsMuted)
        parameters.updatePaginatedStreams(paginatedStreams)

        // Update pagination flags
        parameters.updatePrevDoPaginate(doPaginate)
        parameters.updateDoPaginate(false)

        var isActive = false

        if (paginatedStreams.size > 1) {
            if (!shareScreenStarted && !shared) {
                parameters.updateDoPaginate(true)
            }

            if (currentUserPage > (paginatedStreams.size - 1)) {
                currentUserPage = if (breakOutRoomStarted && !breakOutRoomEnded) {
                    0
                } else {
                    paginatedStreams.size - 1
                }
            } else if (currentUserPage == 0) {
                isActive = true
            }

            parameters.updateCurrentUserPage(currentUserPage)
            parameters.updateNumberPages(paginatedStreams.size - 1)

            // Display the first stream or paginated stream based on screen change
            if (options.screenChanged) {
                val optionsDisp = DispStreamsOptions(
                    lStreams = paginatedStreams[0],
                    ind = 0,
                    parameters = parameters,
                    breakRoom = 0,
                    inBreakRoom = false
                )
                parameters.dispStreams(optionsDisp)
            } else {
                val optionsDisp = DispStreamsOptions(
                    lStreams = paginatedStreams[0],
                    ind = 0,
                    auto = true,
                    parameters = parameters,
                    breakRoom = 0,
                    inBreakRoom = false
                )
                parameters.dispStreams(optionsDisp)
            }

            // Display current user page stream if not active
            if (!isActive) {
                val currentPageBreak = currentUserPage - mainRoomsLength
                val optionsDisp = DispStreamsOptions(
                    lStreams = paginatedStreams[currentUserPage],
                    ind = currentUserPage,
                    parameters = parameters,
                    breakRoom = currentPageBreak,
                    inBreakRoom = currentPageBreak >= 0
                )
                parameters.dispStreams(optionsDisp)
            }
        } else if (paginatedStreams.isNotEmpty()) {
            // Handle case with a single paginated stream
            parameters.updateDoPaginate(false)
            currentUserPage = 0
            parameters.updateCurrentUserPage(currentUserPage)

            val optionsDisp = DispStreamsOptions(
                lStreams = paginatedStreams[0],
                ind = 0,
                parameters = parameters,
                breakRoom = 0,
                inBreakRoom = false
            )
            if (options.screenChanged) {
                parameters.dispStreams(optionsDisp)
            } else {
                val optionsDispAuto = DispStreamsOptions(
                    lStreams = paginatedStreams[0],
                    ind = 0,
                    auto = true,
                    parameters = parameters,
                    breakRoom = 0,
                    inBreakRoom = false
                )
                parameters.dispStreams(optionsDispAuto)
            }
        }
    } catch (error: Exception) {
        Logger.e("ChangeVids", "MediaSFU - changeVids error: ${error.message}")
        error.printStackTrace()
    }
}

