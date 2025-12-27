package com.mediasfu.sdk.webrtc

import com.mediasfu.sdk.util.Logger
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Helper that keeps Android audio routed to the loud speaker and requests focus once
 * a remote WebRTC audio track is resumed. Without this we observed calls where the
 * platform stayed in normal mode and the playback stayed muted on some devices.
 */
internal class AndroidAudioFocusManager(context: Context) {

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val hasActivatedFocus = AtomicBoolean(false)
    @Volatile private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        // If we lose focus completely, allow the next track to re-trigger routing tweaks.
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            hasActivatedFocus.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    fun ensureVoiceCommunicationRouting() {
        val manager = audioManager ?: return
        if (hasActivatedFocus.get()) return

        val voiceVolume = manager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
        val voiceMax = manager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
        val musicVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val musicMax = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: stream volumes voice=$voiceVolume/$voiceMax music=$musicVolume/$musicMax")

        if (voiceVolume == 0 && voiceMax > 0) {
            manager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, 1, 0)
            Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: voice stream volume was zero, nudged to 1")
        }

        if (musicVolume == 0 && musicMax > 0) {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0)
            Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: music stream volume was zero, nudged to 1")
        }

        Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: requesting audio focus")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setAudioAttributes(attributes)
                .build()
            if (manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                applyVoiceMode(manager)
                hasActivatedFocus.set(true)
                focusRequest = request
                Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: audio focus granted (O+)")
            }
        } else {
            @Suppress("DEPRECATION")
            val result = manager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                applyVoiceMode(manager)
                hasActivatedFocus.set(true)
                Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: audio focus granted (legacy)")
            }
        }
    }

    private fun applyVoiceMode(manager: AudioManager) {
        val currentMode = manager.mode
        val currentSpeaker = manager.isSpeakerphoneOn
        Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: current mode=$currentMode, speakerOn=$currentSpeaker")
        
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        manager.isSpeakerphoneOn = true
        AudioManagerCompat.ensureSpeaker(manager)
        
        val newMode = manager.mode
        val newSpeaker = manager.isSpeakerphoneOn
        Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: voice communication routing applied - mode=$newMode, speakerOn=$newSpeaker")
    }

    fun abandonFocus() {
        val manager = audioManager ?: return
        if (!hasActivatedFocus.getAndSet(false)) return

        Logger.d("AndroidAudioFocusMan", "MediaSFU - AndroidAudioFocusManager: abandoning audio focus")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            manager.abandonAudioFocus(focusChangeListener)
        }
        manager.mode = AudioManager.MODE_NORMAL
    }

    private object AudioManagerCompat {
        fun ensureSpeaker(manager: AudioManager) {
            manager.isSpeakerphoneOn = true
        }
    }
}
