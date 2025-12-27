package com.mediasfu.sdk.methods.utils.mini_audio_player

import com.mediasfu.sdk.consumers.UpdateParticipantAudioDecibelsOptions
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.BreakoutParticipant
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ReUpdateInterOptions
import com.mediasfu.sdk.model.ReUpdateInterParameters
import com.mediasfu.sdk.model.ReUpdateInterType
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.consumers.UpdateParticipantAudioDecibelsType
import com.mediasfu.sdk.webrtc.Consumer
import com.mediasfu.sdk.webrtc.AudioStatsProvider
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.*
import kotlin.math.roundToInt

/**
 * Parameters for MiniAudioPlayer.
 */
interface MiniAudioPlayerParameters : ReUpdateInterParameters {
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    val limitedBreakRoom: List<BreakoutParticipant>
    val autoWave: Boolean
    val meetingDisplayType: String
    val dispActiveNames: List<String>
    val paginatedStreams: List<List<Stream>>
    val currentUserPage: Int
    val audioDecibels: List<AudioDecibels>
    
    val reUpdateInter: ReUpdateInterType
    val updateParticipantAudioDecibels: UpdateParticipantAudioDecibelsType
    val updateAudioDecibels: (List<AudioDecibels>) -> Unit
    val updateActiveSounds: (List<String>) -> Unit
    
    /**
     * Method to retrieve updated parameters.
     */
    override fun getUpdatedAllParams(): MiniAudioPlayerParameters
}

/**
 * Options for MiniAudioPlayer.
 */
data class MiniAudioPlayerOptions(
    val stream: MediaStream?,
    val consumer: Consumer,
    val remoteProducerId: String,
    val parameters: MiniAudioPlayerParameters,
    val audioStatsProvider: AudioStatsProvider? = null,
    val miniAudioComponent: ((Map<String, Any>) -> Any)? = null,
    val miniAudioProps: Map<String, Any>? = null,
    val onVisibilityChanged: ((Boolean) -> Unit)? = null
)

/**
 * Type definition for MiniAudioPlayer.
 */
typealias MiniAudioPlayerType = (MiniAudioPlayerOptions) -> Any

/**
 * A class for playing audio streams with optional waveform visualization.
 * 
 * The MiniAudioPlayer plays an audio stream and can display visual audio waveforms
 * to indicate active audio levels. It monitors audio decibels, participant status, and room
 * configurations, providing real-time visual feedback based on audio activity.
 * 
 * This class supports functionalities such as muting/unmuting, waveform display toggling,
 * updating audio decibels, and managing audio activity for participants in breakout rooms.
 */
class MiniAudioPlayer(
    private val options: MiniAudioPlayerOptions
) {
    private var showWaveModal = false
    private var isMuted = true
    private var autoWaveCheck = false
    private var consLow = false
    private val activeSounds = mutableListOf<String>()
    private var audioAnalysisJob: Job? = null
    private var lastStatus: MiniAudioStatus? = null

    private data class MiniAudioStatus(
        val participantName: String?,
        val isMuted: Boolean,
        val showWave: Boolean,
        val autoWaveEnabled: Boolean,
        val audioActiveInRoom: Boolean,
        val loudnessBucket: Int,
        val activeSoundsSnapshot: List<String>,
        val hasVideoStream: Boolean,
        val sharedOrScreen: Boolean,
        val visibleOnPage: Boolean,
        val componentVisible: Boolean,
        val packetsReceived: Long,
        val packetsLost: Long,
        val audioLevel: Double?
    )
    
    /**
     * Starts the audio analysis and monitoring.
     */
    fun startAudioAnalysis() {
        audioAnalysisJob?.cancel()
        lastStatus = null
        audioAnalysisJob = CoroutineScope(Dispatchers.Default).launch {
            if (options.stream == null) {
                return@launch
            }

            var averageLoudness = 127.75

            val statsProvider = options.audioStatsProvider

            while (isActive) {
                delay(2000)

                var packetsReceived: Long = -1
                var packetsLost: Long = -1
                var audioLevelFraction: Double? = null

                if (statsProvider != null) {
                    val stats = runCatching { statsProvider.getInboundAudioStats() }.getOrNull()
                    if (stats != null) {
                        packetsReceived = stats.packetsReceived
                        packetsLost = stats.packetsLost
                        audioLevelFraction = stats.audioLevel
                        stats.audioLevel?.let { level ->
                            val clamped = level.coerceIn(0.0, 1.0)
                            averageLoudness = 127.5 + (clamped * 127.5)
                        }
                    }
                }

                val parameters = options.parameters.getUpdatedAllParams()
                val meetingDisplayType = parameters.meetingDisplayType
                val shared = parameters.shared
                val shareScreenStarted = parameters.shareScreenStarted
                val dispActiveNames = parameters.dispActiveNames.toList()
                var adminNameStream = parameters.adminNameStream
                val participants = parameters.participants
                val autoWave = parameters.autoWave
                val updateActiveSounds = parameters.updateActiveSounds
                val paginatedStreams = parameters.paginatedStreams
                val currentUserPage = parameters.currentUserPage
                val breakOutRoomStarted = parameters.breakOutRoomStarted
                val breakOutRoomEnded = parameters.breakOutRoomEnded
                val limitedBreakRoom = parameters.limitedBreakRoom

                val participant = participants.find { it.audioID == options.remoteProducerId }

                participant?.let { activeParticipant ->
                    val existingDecibel = parameters.audioDecibels.firstOrNull { it.name == activeParticipant.name }
                    if (existingDecibel != null) {
                        averageLoudness = existingDecibel.averageLoudness
                    }
                }

                var audioActiveInRoom = true
                if (participant != null && breakOutRoomStarted && !breakOutRoomEnded) {
                    if (participant.name.isNotEmpty() && limitedBreakRoom.none { it.name == participant.name }) {
                        audioActiveInRoom = false
                    }
                }

                val participantName = participant?.name
                var inPage = -1
                val activeSnapshot: List<String>

                if (participant != null) {
                    isMuted = participant.muted ?: false

                    if (parameters.eventType != EventType.CHAT &&
                        parameters.eventType != EventType.BROADCAST) {
                        parameters.updateParticipantAudioDecibels(
                            UpdateParticipantAudioDecibelsOptions(
                                name = participant.name,
                                averageLoudness = averageLoudness,
                                audioDecibels = parameters.audioDecibels.toMutableList(),
                                updateAudioDecibels = parameters.updateAudioDecibels
                            )
                        )
                    }

                    inPage = if (paginatedStreams.size > currentUserPage) {
                        paginatedStreams[currentUserPage].indexOfFirst { it.name == participant.name }
                    } else {
                        -1
                    }

                    if (!dispActiveNames.contains(participant.name) && inPage == -1) {
                        autoWaveCheck = false
                        if (adminNameStream.isEmpty()) {
                            val adminParticipant = participants.find { it.islevel == "2" }
                            adminNameStream = adminParticipant?.name ?: ""
                        }

                        if (participant.name == adminNameStream && adminNameStream.isNotEmpty()) {
                            autoWaveCheck = true
                        }
                    } else {
                        autoWaveCheck = true
                    }

                    if (participant.videoID.isNotEmpty() ||
                        autoWaveCheck ||
                        (breakOutRoomStarted && !breakOutRoomEnded && audioActiveInRoom)) {
                        showWaveModal = false

                        if (averageLoudness > 127.5) {
                            if (participant.name.isNotEmpty() && !activeSounds.contains(participant.name)) {
                                activeSounds.add(participant.name)
                                consLow = false

                                if (!(shared || shareScreenStarted) || participant.videoID.isNotEmpty()) {
                                    if (parameters.eventType != EventType.CHAT &&
                                        parameters.eventType != EventType.BROADCAST &&
                                        participant.name.isNotEmpty()) {
                                        val optionsReUpdate = ReUpdateInterOptions(
                                            name = participant.name,
                                            add = true,
                                            average = averageLoudness,
                                            parameters = parameters
                                        )
                                        parameters.reUpdateInter(optionsReUpdate)
                                    }
                                }
                            }
                        } else {
                            if (participant.name.isNotEmpty() &&
                                activeSounds.contains(participant.name) && consLow) {
                                activeSounds.remove(participant.name)
                                if (parameters.eventType != EventType.CHAT &&
                                    parameters.eventType != EventType.BROADCAST &&
                                    participant.name.isNotEmpty()) {
                                    val optionsReUpdate = ReUpdateInterOptions(
                                        name = participant.name,
                                        average = averageLoudness,
                                        parameters = parameters
                                    )
                                    parameters.reUpdateInter(optionsReUpdate)
                                }
                            } else {
                                consLow = true
                            }
                        }
                    } else {
                        if (averageLoudness > 127.5) {
                            showWaveModal = autoWave

                            if (!activeSounds.contains(participant.name)) {
                                activeSounds.add(participant.name)
                            }

                            if ((shareScreenStarted || shared) && participant.videoID.isEmpty()) {
                                // Intentionally left blank
                            } else {
                                if (parameters.eventType != EventType.CHAT &&
                                    parameters.eventType != EventType.BROADCAST &&
                                    participant.name.isNotEmpty()) {
                                    val optionsReUpdate = ReUpdateInterOptions(
                                        name = participant.name,
                                        add = true,
                                        average = averageLoudness,
                                        parameters = parameters
                                    )
                                    parameters.reUpdateInter(optionsReUpdate)
                                }
                            }
                        } else {
                            showWaveModal = false
                            if (participant.name.isNotEmpty() && activeSounds.contains(participant.name)) {
                                activeSounds.remove(participant.name)
                            }

                            if ((shareScreenStarted || shared) && participant.videoID.isEmpty()) {
                                // Intentionally left blank
                            } else {
                                if (parameters.eventType != EventType.CHAT &&
                                    parameters.eventType != EventType.BROADCAST &&
                                    participant.name.isNotEmpty()) {
                                    val optionsReUpdate = ReUpdateInterOptions(
                                        name = participant.name,
                                        average = averageLoudness,
                                        parameters = parameters
                                    )
                                    parameters.reUpdateInter(optionsReUpdate)
                                }
                            }
                        }
                    }

                    val snapshot = activeSounds.toList()
                    updateActiveSounds(snapshot)
                    activeSnapshot = snapshot
                } else {
                    showWaveModal = false
                    isMuted = true
                    activeSnapshot = activeSounds.toList()
                }

                val loudnessBucket = (averageLoudness * 10).roundToInt()
                val componentVisible = showWaveModal && !isMuted
                val sharedOrScreen = shared || shareScreenStarted
                val visibleOnPage = (inPage != -1) || (participantName != null && dispActiveNames.contains(participantName))
                val status = MiniAudioStatus(
                    participantName = participantName,
                    isMuted = isMuted,
                    showWave = showWaveModal,
                    autoWaveEnabled = autoWaveCheck,
                    audioActiveInRoom = audioActiveInRoom,
                    loudnessBucket = loudnessBucket,
                    activeSoundsSnapshot = activeSnapshot,
                    hasVideoStream = participant?.videoID?.isNotEmpty() == true,
                    sharedOrScreen = sharedOrScreen,
                    visibleOnPage = visibleOnPage,
                    componentVisible = componentVisible,
                    packetsReceived = packetsReceived,
                    packetsLost = packetsLost,
                    audioLevel = audioLevelFraction
                )

                if (status != lastStatus) {
                    if (lastStatus?.componentVisible != status.componentVisible) {
                        options.onVisibilityChanged?.invoke(status.componentVisible)
                    }
                    lastStatus = status
                }
            }
        }
    }
    
    /**
     * Stops the audio analysis and monitoring.
     */
    fun stopAudioAnalysis() {
        audioAnalysisJob?.cancel()
        audioAnalysisJob = null
    }
    
    /**
     * Renders the mini audio component if available.
     */
    fun renderMiniAudioComponent(): Any? {
        return if (options.miniAudioComponent != null) {
            val props = mutableMapOf<String, Any>(
                "showWaveform" to showWaveModal,
                "visible" to (showWaveModal && !isMuted)
            )
            options.miniAudioProps?.let { props.putAll(it) }
            options.miniAudioComponent!!(props)
        } else {
            null
        }
    }
    
    /**
     * Gets the current state of the audio player.
     */
    fun getState(): MiniAudioPlayerState {
        return MiniAudioPlayerState(
            showWaveModal = showWaveModal,
            isMuted = isMuted,
            autoWaveCheck = autoWaveCheck,
            consLow = consLow,
            activeSounds = activeSounds.toList()
        )
    }
}

/**
 * Data class representing the state of the mini audio player.
 */
data class MiniAudioPlayerState(
    val showWaveModal: Boolean,
    val isMuted: Boolean,
    val autoWaveCheck: Boolean,
    val consLow: Boolean,
    val activeSounds: List<String>
)
