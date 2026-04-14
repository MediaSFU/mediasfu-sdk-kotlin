package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.util.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.AVFoundation.*
import platform.AVFAudio.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual class PlatformAudioPlayer {
    private var player: AVPlayer? = null
    private var preparedUrl: String? = null
    
    actual suspend fun play(url: String) {
        withContext(Dispatchers.Main) {
            try {
                if (preparedUrl != url || player == null) {
                    stopInternal()
                    player = createPlayer(url)
                    preparedUrl = url
                }
                player?.play()
            } catch (error: Throwable) {
                Logger.d("PlatformAudioPlayer.", "iOS audio playback error: $error")
            }
        }
    }
    
    actual suspend fun stop() {
        withContext(Dispatchers.Main) {
            try {
                stopInternal()
            } catch (error: Throwable) {
                Logger.d("PlatformAudioPlayer.", "iOS audio stop error: $error")
            }
        }
    }
    
    actual suspend fun preload(url: String) {
        withContext(Dispatchers.Main) {
            try {
                stopInternal()
                player = createPlayer(url).also { it.pause() }
                preparedUrl = url
            } catch (error: Throwable) {
                Logger.d("PlatformAudioPlayer.", "iOS audio preload error: $error")
            }
        }
    }
    
    actual suspend fun reinitialize() {
        stop()
    }

    private fun createPlayer(url: String): AVPlayer {
        configureAudioSession()
        val nsUrl = requireNotNull(NSURL.URLWithString(url)) {
            "Invalid iOS audio URL: $url"
        }
        return AVPlayer.playerWithURL(nsUrl)
    }

    private fun configureAudioSession() {
        val audioSession = AVAudioSession.sharedInstance()
        audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
        audioSession.setActive(true, error = null)
    }

    private fun stopInternal() {
        player?.pause()
        player?.replaceCurrentItemWithPlayerItem(null)
        player = null
        preparedUrl = null
    }
}
