package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.util.Logger
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual class PlatformAudioPlayer {
    private var mediaPlayer: MediaPlayer? = null
    
    actual suspend fun play(url: String) {
        withContext(Dispatchers.IO) {
            try {
                stop()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Logger.d("PlatformAudioPlayer.", "Android audio playback error: $e")
            }
        }
    }
    
    actual suspend fun stop() {
        withContext(Dispatchers.IO) {
            try {
                mediaPlayer?.apply {
                    if (isPlaying) {
                        stop()
                    }
                    release()
                }
                mediaPlayer = null
            } catch (e: Exception) {
                Logger.d("PlatformAudioPlayer.", "Android audio stop error: $e")
            }
        }
    }
    
    actual suspend fun preload(url: String) {
        withContext(Dispatchers.IO) {
            try {
                stop()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    prepare()
                }
            } catch (e: Exception) {
                Logger.d("PlatformAudioPlayer.", "Android audio preload error: $e")
            }
        }
    }
    
    actual suspend fun reinitialize() {
        stop()
    }
}
