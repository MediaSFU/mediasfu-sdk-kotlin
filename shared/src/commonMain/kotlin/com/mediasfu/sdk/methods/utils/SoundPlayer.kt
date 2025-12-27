package com.mediasfu.sdk.methods.utils
import com.mediasfu.sdk.util.Logger

/**
 * Options for the SoundPlayer, encapsulating the sound URL.
 */
data class SoundPlayerOptions(
    val soundUrl: String
)

/**
 * Type definition for sound player function.
 */
typealias SoundPlayerType = suspend (SoundPlayerOptions) -> Unit

/**
 * Platform-agnostic interface for audio playback.
 * Platform-specific implementations should be provided via expect/actual.
 */
expect class PlatformAudioPlayer() {
    suspend fun play(url: String)
    suspend fun stop()
    suspend fun preload(url: String)
    suspend fun reinitialize()
}

/**
 * A sound player that plays audio from a given URL.
 * 
 * This is a platform-agnostic implementation for the Kotlin Multiplatform SDK.
 * Platform-specific implementations are provided via expect/actual.
 * 
 * Example usage:
 * ```kotlin
 * SoundPlayer.play(SoundPlayerOptions(soundUrl = "https://example.com/sound.mp3"))
 * ```
 */
object SoundPlayer {
    private val audioPlayer = PlatformAudioPlayer()
    
    /**
     * Plays the sound from the provided [options].
     */
    suspend fun play(options: SoundPlayerOptions) {
        if (!isValidUrl(options.soundUrl)) {
            Logger.e("SoundPlayer", "Invalid URL: ${options.soundUrl}")
            return
        }
        
        try {
            audioPlayer.play(options.soundUrl)
        } catch (e: Exception) {
            Logger.e("SoundPlayer", "Failed to play sound: $e")
        }
    }
    
    /**
     * Stops the currently playing sound.
     */
    suspend fun stop() {
        try {
            audioPlayer.stop()
        } catch (e: Exception) {
            Logger.e("SoundPlayer", "Failed to stop sound: $e")
        }
    }
    
    /**
     * Validates the sound URL.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Preloads the sound for faster playback.
     */
    suspend fun preload(options: SoundPlayerOptions) {
        try {
            audioPlayer.preload(options.soundUrl)
        } catch (e: Exception) {
            Logger.e("SoundPlayer", "Failed to preload sound: $e")
        }
    }
    
    /**
     * Stops and reinitializes the audio player.
     */
    suspend fun stopAndReinitialize() {
        try {
            audioPlayer.reinitialize()
        } catch (e: Exception) {
            Logger.e("SoundPlayer", "Failed to stop and reinitialize: $e")
        }
    }
    
    /**
     * Attaches listeners for playback events.
     */
    fun attachListeners() {
        // Platform-specific implementation should be provided here
        // TODO: Implement platform-specific event listeners
    }
}