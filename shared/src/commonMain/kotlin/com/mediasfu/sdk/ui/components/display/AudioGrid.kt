package com.mediasfu.sdk.ui.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.ui.MediaSfuUIComponent

/**
 * AudioGrid - Displays a grid of audio-only participants.
 *
 * Arranges multiple audio cards in a responsive grid layout,
 * showing waveform animations for active speakers.
 *
 * @property options Configuration options for the audio grid
 */
data class AudioGridOptions(
    val participants: List<Participant>,
    val audioDecibels: List<AudioDecibels> = emptyList(),
    val backgroundColor: Int = 0xFF000000.toInt(), // Black
    val columnsPerRow: Int = 4,
    val showControls: Boolean = true,
    val customAudioCardComponent: ((AudioCardOptions) -> AudioCard)? = null,
)

interface AudioGrid : MediaSfuUIComponent {
    val options: AudioGridOptions
    override val id: String get() = "audio_grid"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Groups participants into rows based on columns per row
     */
    fun getGridRows(): List<List<Participant>> {
        val rows = mutableListOf<List<Participant>>()
        val participants = options.participants
        
        for (i in participants.indices step options.columnsPerRow) {
            val endIndex = minOf(i + options.columnsPerRow, participants.size)
            rows.add(participants.subList(i, endIndex))
        }
        
        return rows
    }
    
    /**
     * Creates audio card options for a participant
     */
    fun createAudioCardOptions(participant: Participant): AudioCardOptions {
        return AudioCardOptions(
            name = participant.name ?: "Unknown",
            participant = participant,
            audioDecibels = options.audioDecibels,
            showControls = options.showControls,
        )
    }
}

/**
 * Default implementation of AudioGrid
 */
class DefaultAudioGrid(
    override val options: AudioGridOptions
) : AudioGrid {
    fun render(): Any {
        val rows = getGridRows()
        val audioCards = options.participants.map { participant ->
            val cardOptions = createAudioCardOptions(participant)
            options.customAudioCardComponent?.invoke(cardOptions)
                ?: DefaultAudioCard(cardOptions)
        }
        
        return mapOf(
            "type" to "audioGrid",
            "rows" to rows,
            "audioCards" to audioCards,
            "backgroundColor" to options.backgroundColor,
            "columnsPerRow" to options.columnsPerRow
        )
    }
}

/**
 * Composable extension for rendering AudioGrid in Jetpack Compose
 */
@Composable
fun AudioGrid.renderCompose() {
    val rows = getGridRows()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(options.backgroundColor))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        rows.forEach { rowParticipants ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowParticipants.forEach { participant ->
                    val cardOptions = createAudioCardOptions(participant)
                    val audioCard = options.customAudioCardComponent?.invoke(cardOptions)
                        ?: DefaultAudioCard(cardOptions)
                    
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        audioCard.renderCompose()
                    }
                }
            }
        }
    }
}

