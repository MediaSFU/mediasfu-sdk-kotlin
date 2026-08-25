package com.mediasfu.sdk.headless

import com.mediasfu.sdk.MediaSfuEngine
import com.mediasfu.sdk.methods.utils.GetParticipantMediaOptions
import com.mediasfu.sdk.methods.utils.GetParticipantMediaParameters
import com.mediasfu.sdk.methods.utils.getParticipantMedia
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.socket.ConnectionState
import com.mediasfu.sdk.webrtc.MediaStream

/** Availability of an optional high-level feature on this platform. */
data class MediaSfuCapability(
    val available: Boolean,
    val reason: String? = null
) {
    companion object {
        val Available = MediaSfuCapability(true)
        fun unavailable(reason: String) = MediaSfuCapability(false, reason)
    }
}

data class MediaSfuHeadlessCapabilities(
    val roomSession: MediaSfuCapability = MediaSfuCapability.Available,
    val participantModeration: MediaSfuCapability = MediaSfuCapability.Available,
    val recordingState: MediaSfuCapability = MediaSfuCapability.Available,
    val pollsState: MediaSfuCapability = MediaSfuCapability.Available,
    val breakoutState: MediaSfuCapability = MediaSfuCapability.Available,
    val whiteboardState: MediaSfuCapability = MediaSfuCapability.Available,
    val mediaDevices: MediaSfuCapability = MediaSfuCapability.Available,
    val viewerMode: MediaSfuCapability = MediaSfuCapability.unavailable(
        "The Kotlin SDK has no verified viewer-session protocol contract."
    ),
    val hlsPlayback: MediaSfuCapability = MediaSfuCapability.unavailable(
        "HLS is a recording option; no Kotlin playback contract is exposed."
    )
)

data class MediaSfuReadinessSnapshot(
    val connectionState: ConnectionState,
    val connected: Boolean,
    val validated: Boolean,
    val roomName: String,
    val member: String,
    val socketReady: Boolean,
    val deviceReady: Boolean,
    val mediaControlsReady: Boolean
)

data class MediaSfuParticipantSnapshot(
    val id: String?,
    val name: String,
    val level: String?,
    val isHost: Boolean,
    val isAdmin: Boolean,
    val audioProducerId: String,
    val videoProducerId: String,
    val screenProducerId: String?,
    val audioOn: Boolean,
    val videoOn: Boolean,
    val screenOn: Boolean,
    val muted: Boolean,
    val banned: Boolean,
    val suspended: Boolean,
    val canUseWhiteboard: Boolean,
    val breakoutRoom: Int?
)

data class MediaSfuStreamSnapshot(
    val id: String?,
    val producerId: String,
    val name: String?,
    val muted: Boolean,
    val active: Boolean,
    val audioTrackCount: Int,
    val videoTrackCount: Int
)

data class MediaSfuMediaSnapshot(
    val localAudioOn: Boolean,
    val localVideoOn: Boolean,
    val localScreenOn: Boolean,
    val videoStreams: List<MediaSfuStreamSnapshot>,
    val audioStreams: List<MediaSfuStreamSnapshot>
)

data class MediaSfuPermissionSnapshot(
    val audio: String,
    val video: String,
    val screenShare: String,
    val chat: String
)

data class MediaSfuRecordingSnapshot(
    val started: Boolean,
    val paused: Boolean,
    val canRecordAudio: Boolean,
    val canRecordVideo: Boolean,
    val mediaOption: String,
    val progress: String,
    val addsHlsOutput: Boolean
)

data class MediaSfuPollSnapshot(
    val id: String?,
    val question: String,
    val type: String?,
    val options: List<String>,
    val votes: List<Int>,
    val status: String?
)

data class MediaSfuBreakoutSnapshot(
    val started: Boolean,
    val ended: Boolean,
    val rooms: List<List<String>>
)

data class MediaSfuWhiteboardSnapshot(
    val started: Boolean,
    val ended: Boolean,
    val users: Map<String, Boolean>
)

data class MediaSfuDeviceSnapshot(
    val id: String,
    val kind: String,
    val label: String,
    val groupId: String
)

data class MediaSfuSessionExtrasSnapshot(
    val messageCount: Int,
    val waitingParticipants: Map<String, String>,
    val requests: Map<String, String>,
    val devices: List<MediaSfuDeviceSnapshot>
)

data class MediaSfuHeadlessSnapshot(
    val readiness: MediaSfuReadinessSnapshot,
    val participants: List<MediaSfuParticipantSnapshot>,
    val media: MediaSfuMediaSnapshot,
    val permissions: MediaSfuPermissionSnapshot,
    val recording: MediaSfuRecordingSnapshot,
    val polls: List<MediaSfuPollSnapshot>,
    val activePoll: MediaSfuPollSnapshot?,
    val breakout: MediaSfuBreakoutSnapshot,
    val whiteboard: MediaSfuWhiteboardSnapshot,
    val extras: MediaSfuSessionExtrasSnapshot,
    val capabilities: MediaSfuHeadlessCapabilities
)

/**
 * UI-independent facade over [MediaSfuEngine]. All query results are copied immutable values;
 * callers must capture a new snapshot to observe later parameter publications.
 */
class MediaSfuHeadlessController(
    private val engine: MediaSfuEngine
) {
    val capabilities: MediaSfuHeadlessCapabilities = MediaSfuHeadlessCapabilities()

    fun snapshot(): MediaSfuHeadlessSnapshot {
        val p = engine.getCurrentParams()
        val readiness = MediaSfuReadinessSnapshot(
            connectionState = engine.connectionState(),
            connected = engine.isConnected(),
            validated = p.validated,
            roomName = p.roomName,
            member = p.member,
            socketReady = p.socket?.id?.isNotEmpty() == true || p.localSocket?.id?.isNotEmpty() == true,
            deviceReady = p.device != null,
            mediaControlsReady = p.validated && p.device != null
        )
        val participants = p.participants.map { participant ->
            MediaSfuParticipantSnapshot(
                id = participant.id,
                name = participant.name,
                level = participant.islevel,
                isHost = participant.isHost,
                isAdmin = participant.isAdmin,
                audioProducerId = participant.audioID,
                videoProducerId = participant.videoID,
                screenProducerId = participant.ScreenID,
                audioOn = participant.audioOn,
                videoOn = participant.videoOn,
                screenOn = participant.ScreenOn,
                muted = participant.muted,
                banned = participant.isBanned,
                suspended = participant.isSuspended,
                canUseWhiteboard = participant.useBoard,
                breakoutRoom = participant.breakRoom
            )
        }
        val media = MediaSfuMediaSnapshot(
            localAudioOn = p.audioAlreadyOn,
            localVideoOn = p.videoAlreadyOn,
            localScreenOn = p.screenAlreadyOn,
            videoStreams = p.allVideoStreams.map(::streamSnapshot),
            audioStreams = p.allAudioStreams.map(::streamSnapshot)
        )
        val polls = p.polls.map(::pollSnapshot)
        return MediaSfuHeadlessSnapshot(
            readiness = readiness,
            participants = participants,
            media = media,
            permissions = MediaSfuPermissionSnapshot(
                p.audioSetting, p.videoSetting, p.screenshareSetting, p.chatSetting
            ),
            recording = MediaSfuRecordingSnapshot(
                started = p.recordStarted,
                paused = p.recordPaused,
                canRecordAudio = p.recordingAudioSupport,
                canRecordVideo = p.recordingVideoSupport,
                mediaOption = p.recordingMediaOptions,
                progress = p.recordingProgressTime,
                addsHlsOutput = p.recordingAddHLS
            ),
            polls = polls,
            activePoll = p.poll?.let(::pollSnapshot),
            breakout = MediaSfuBreakoutSnapshot(
                p.breakOutRoomStarted,
                p.breakOutRoomEnded,
                p.breakoutRooms.map { room -> room.map { it.name } }
            ),
            whiteboard = MediaSfuWhiteboardSnapshot(
                p.whiteboardStarted,
                p.whiteboardEnded,
                p.whiteboardUsers.associate { it.name to it.useBoard }
            ),
            extras = MediaSfuSessionExtrasSnapshot(
                messageCount = p.messages.size,
                waitingParticipants = p.waitingRoomList.associate { it.id to it.name },
                requests = p.requestList.associate { it.id to (it.name ?: it.username ?: "") },
                devices = (p.audioInputs + p.audioOutputs + p.videoInputs).map {
                    MediaSfuDeviceSnapshot(it.deviceId, it.kind.toString(), it.label, it.groupId)
                }
            ),
            capabilities = capabilities
        )
    }

    suspend fun checkPermission(type: String): Result<Int> {
        val p = engine.getCurrentParams()
        return engine.checkMediaPermission(
            permissionType = type,
            audioSetting = p.audioSetting,
            videoSetting = p.videoSetting,
            screenshareSetting = p.screenshareSetting,
            chatSetting = p.chatSetting
        )
    }

    suspend fun moderateParticipant(
        participantId: String,
        participantName: String,
        mediaType: String
    ): Result<Unit> = engine.controlParticipantMedia(participantId, participantName, mediaType)

    suspend fun prepareLocalMedia(mediaType: String): Result<Unit> = engine.prepopulateMedia(mediaType)

    suspend fun disconnect() = engine.disconnect()

    /** Returns the live media handle separately from immutable query snapshots. */
    suspend fun participantMedia(
        participantId: String = "",
        participantName: String = "",
        kind: String = "video"
    ): MediaStream? {
        val p = engine.getCurrentParams()
        val parameters = object : GetParticipantMediaParameters {
            override val allVideoStreams = p.allVideoStreams.toList()
            override val allAudioStreams = p.allAudioStreams.toList()
            override val participants = p.participants.toList()
        }
        return getParticipantMedia(
            GetParticipantMediaOptions(participantId, participantName, kind, parameters)
        )
    }
}

private fun streamSnapshot(stream: Stream): MediaSfuStreamSnapshot {
    val media = stream.stream
    return MediaSfuStreamSnapshot(
        id = stream.id,
        producerId = stream.producerId,
        name = stream.name,
        muted = stream.muted ?: false,
        active = media?.active ?: false,
        audioTrackCount = runCatching { media?.getAudioTracks()?.size ?: 0 }.getOrDefault(0),
        videoTrackCount = runCatching { media?.getVideoTracks()?.size ?: 0 }.getOrDefault(0)
    )
}

private fun pollSnapshot(poll: com.mediasfu.sdk.model.Poll) = MediaSfuPollSnapshot(
    id = poll.id,
    question = poll.question,
    type = poll.type,
    options = poll.options.toList(),
    votes = poll.votes.toList(),
    status = poll.status
)
