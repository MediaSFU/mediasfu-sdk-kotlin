package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream

/**
 * Parameters for resuming/pausing audio streams based on breakout rooms.
 */
interface ResumePauseAudioStreamsParameters : ProcessConsumerTransportsAudioParameters {
    val breakoutRooms: List<List<BreakoutParticipant>>
    val refParticipants: List<Participant>
    val allAudioStreams: List<Stream>
    val participants: List<Participant>
    val islevel: String
    val eventType: EventType?
    val consumerTransports: List<ConsumerTransportInfo>
    val limitedBreakRoom: List<BreakoutParticipant>
    val hostNewRoom: Int
    val member: String

    // Update functions
    val updateLimitedBreakRoom: (List<BreakoutParticipant>) -> Unit

    // Mediasfu functions
    val processConsumerTransportsAudio: suspend (ProcessConsumerTransportsAudioOptions) -> Unit

    // Method to get updated parameters
    override fun getUpdatedAllParams(): ResumePauseAudioStreamsParameters
}

/**
 * Options for resuming/pausing audio streams.
 *
 * @property breakRoom The breakout room number (-1 for no room)
 * @property inBreakRoom Whether the user is currently in a breakout room
 * @property parameters The parameters for managing audio streams
 */
data class ResumePauseAudioStreamsOptions(
    val breakRoom: Int? = null,
    val inBreakRoom: Boolean? = null,
    val parameters: ResumePauseAudioStreamsParameters
)

/**
 * Options for processing consumer transports audio.
 *
 * @property consumerTransports The list of consumer transports
 * @property lStreams The list of streams to process
 * @property parameters The parameters for processing
 */
data class ProcessConsumerTransportsAudioOptions(
    val consumerTransports: List<ConsumerTransportInfo>,
    val lStreams: List<Stream>,
    val parameters: ProcessConsumerTransportsAudioParameters
)

/**
 * Resumes or pauses audio streams for participants based on breakout room status and event type.
 */
suspend fun resumePauseAudioStreams(options: ResumePauseAudioStreamsOptions) {
    val parameters = options.parameters.getUpdatedAllParams()

    val breakoutRooms = parameters.breakoutRooms
    val refParticipants = parameters.refParticipants
    val allAudioStreams = parameters.allAudioStreams
    val participants = parameters.participants
    val islevel = parameters.islevel
    val eventType = parameters.eventType
    val consumerTransports = parameters.consumerTransports
    val hostNewRoom = parameters.hostNewRoom
    val member = parameters.member

    val updateLimitedBreakRoom = parameters.updateLimitedBreakRoom
    val processConsumerTransportsAudio = parameters.processConsumerTransportsAudio

    val currentStreams = mutableListOf<Stream>()
    val breakRoom = options.breakRoom ?: -1
    val inBreakRoom = options.inBreakRoom ?: false

    var room: List<BreakoutParticipant> = if (inBreakRoom && breakRoom != -1) {
        breakoutRooms.getOrNull(breakRoom) ?: emptyList()
    } else {
        val breakoutNames = breakoutRooms.flatten().map { it.name }
        refParticipants.filter { participant ->
            participant.name.isNotEmpty() && participant.name !in breakoutNames
        }.map { participant ->
            BreakoutParticipant(
                name = participant.name,
                breakRoom = participant.breakRoom ?: -1
            )
        }
    }
    updateLimitedBreakRoom(room)

    try {
        var addHostAudio = false
        val eventTypeName = eventType?.name?.lowercase().orEmpty()

        if (islevel != "2" && eventTypeName.contains("conference")) {
            val roomMember = breakoutRooms.firstOrNull { r -> r.any { p -> p.name == member } }
            val memberBreakRoom = roomMember?.let { breakoutRooms.indexOf(it) } ?: -1

            when {
                inBreakRoom && breakRoom != hostNewRoom -> {
                    val hostName = participants.firstOrNull { it.islevel == "2" }?.name
                    if (hostName != null) {
                        room = room.filter { it.name != hostName }
                    }
                }
                !inBreakRoom && hostNewRoom != -1 && hostNewRoom != memberBreakRoom -> {
                    val hostName = participants.firstOrNull { it.islevel == "2" }?.name
                    if (hostName != null) {
                        room = room.filter { it.name != hostName }
                    }
                }
                else -> addHostAudio = true
            }
        }

        for (participant in room) {
            val participantAudioIds = refParticipants.filter { it.name == participant.name }
                .mapNotNull { ref -> ref.audioID.takeIf { it.isNotEmpty() } }

            if (participantAudioIds.isEmpty()) continue

            val streams = allAudioStreams.filter { stream ->
                val producerId = when {
                    stream.producerId.isNotEmpty() -> stream.producerId
                    !stream.audioID.isNullOrEmpty() -> stream.audioID!!
                    !stream.videoID.isNullOrEmpty() -> stream.videoID!!
                    else -> ""
                }
                producerId.isNotEmpty() && producerId in participantAudioIds
            }

            currentStreams.addAll(streams)
        }

        if (islevel != "2" && (eventTypeName.contains("webinar") || addHostAudio)) {
            val host = participants.firstOrNull { it.islevel == "2" }
            val hostAudioID = host?.audioID.orEmpty()
            val hostName = host?.name.orEmpty()

            if (hostAudioID.isNotEmpty()) {
                val hostStream = allAudioStreams.firstOrNull { stream ->
                    stream.producerId == hostAudioID || stream.audioID == hostAudioID
                }
                if (hostStream != null && hostStream !in currentStreams) {
                    currentStreams.add(hostStream)
                    if (hostName.isNotEmpty() && room.none { it.name == hostName }) {
                        room = room + BreakoutParticipant(hostName, -1)
                    }
                    updateLimitedBreakRoom(room)
                }
            }
        }

        val optionsProcess = ProcessConsumerTransportsAudioOptions(
            consumerTransports = consumerTransports,
            lStreams = currentStreams,
            parameters = parameters
        )
        processConsumerTransportsAudio(optionsProcess)
    } catch (error: Exception) {
        Logger.e("ResumePauseAudioStre", "Error in resumePauseAudioStreams: ${error.message}")
    }
}

