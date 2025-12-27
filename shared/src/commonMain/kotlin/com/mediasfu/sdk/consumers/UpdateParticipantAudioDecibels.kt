package com.mediasfu.sdk.consumers

/**
 * Represents audio decibel information for a participant.
 *
 * @property name The name of the participant
 * @property averageLoudness The average loudness level
 */
data class AudioDecibels(
    val name: String,
    var averageLoudness: Double
)

/**
 * Options for updating the audio decibels of a participant.
 *
 * @property name The name of the participant
 * @property averageLoudness The new average loudness value
 * @property audioDecibels The current list of audio decibels
 * @property updateAudioDecibels Callback to update the audio decibels list
 */
data class UpdateParticipantAudioDecibelsOptions(
    val name: String,
    val averageLoudness: Double,
    val audioDecibels: MutableList<AudioDecibels>,
    val updateAudioDecibels: (List<AudioDecibels>) -> Unit
)

/**
 * Type definition for updating participant audio decibels.
 */
typealias UpdateParticipantAudioDecibelsType = (UpdateParticipantAudioDecibelsOptions) -> Unit

/**
 * Updates the audio decibels for a participant.
 *
 * This function either updates an existing entry or adds a new entry for the participant's
 * audio decibels in the `audioDecibels` list.
 *
 * @param options An instance of UpdateParticipantAudioDecibelsOptions containing all necessary parameters
 *
 * Example:
 * ```kotlin
 * val audioDecibelsList = mutableListOf(
 *     AudioDecibels("Alice", 50.0),
 *     AudioDecibels("Bob", 45.0)
 * )
 *
 * val options = UpdateParticipantAudioDecibelsOptions(
 *     name = "Alice",
 *     averageLoudness = 60.0,
 *     audioDecibels = audioDecibelsList,
 *     updateAudioDecibels = { updatedList ->
 *     }
 * )
 *
 * updateParticipantAudioDecibels(options)
 * // Alice's averageLoudness is now 60.0
 * ```
 */
fun updateParticipantAudioDecibels(options: UpdateParticipantAudioDecibelsOptions) {
    // Check if the entry already exists in audioDecibels
    val existingEntry = options.audioDecibels.find { it.name == options.name }

    if (existingEntry != null) {
        // Entry exists, update the averageLoudness
        existingEntry.averageLoudness = options.averageLoudness
    } else {
        // Entry doesn't exist, add a new entry to audioDecibels
        options.audioDecibels.add(
            AudioDecibels(
                name = options.name,
                averageLoudness = options.averageLoudness
            )
        )
    }

    // Update the audioDecibels array
    options.updateAudioDecibels(options.audioDecibels.toList())
}

