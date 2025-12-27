package com.mediasfu.sdk.webrtc

import cocoapods.WebRTC.RTCAudioSource
import cocoapods.WebRTC.RTCAudioTrack
import cocoapods.WebRTC.RTCMediaStream
import cocoapods.WebRTC.RTCMediaStreamTrack
import cocoapods.WebRTC.RTCMediaStreamTrackState
import cocoapods.WebRTC.RTCPeerConnectionFactory
import cocoapods.WebRTC.RTCVideoSource
import cocoapods.WebRTC.RTCVideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSLog
import platform.Foundation.NSUUID

/**
 * Lightweight iOS WebRTC device implementation that focuses on exposing native
 * media tracks to the shared UI layer. Transport support is still pending.
 */
class IOSWebRtcDevice private constructor() : WebRtcDevice {

    private val peerConnectionFactory: RTCPeerConnectionFactory = run {
        RTCPeerConnectionFactory.initialize()
        RTCPeerConnectionFactory()
    }

    private var lastLoadedCapabilities: RtpCapabilities? = null

    companion object {
        @Volatile
        private var INSTANCE: IOSWebRtcDevice? = null

        fun getInstance(): IOSWebRtcDevice {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: IOSWebRtcDevice().also { INSTANCE = it }
            }
        }
    }

    override suspend fun load(rtpCapabilities: RtpCapabilities): Result<Unit> {
        return runCatching {
            lastLoadedCapabilities = rtpCapabilities
        }
    }

    override suspend fun getUserMedia(constraints: Map<String, Any?>): MediaStream {
        return withContext(Dispatchers.Main) {
            val audioEnabled = constraints["audio"] as? Boolean ?: false
            val videoEnabled = constraints["video"] as? Boolean ?: false

            val streamId = "ios_stream_${NSUUID().UUIDString}"
            val nativeStream = peerConnectionFactory.mediaStreamWithStreamId(streamId)

            var audioSource: RTCAudioSource? = null
            var audioTrack: RTCAudioTrack? = null
            if (audioEnabled) {
                audioSource = peerConnectionFactory.audioSourceWithConstraints(null)
                audioTrack = peerConnectionFactory.audioTrackWithSource(audioSource, "audio_$streamId")
                nativeStream.addAudioTrack(audioTrack)
            }

            var videoSource: RTCVideoSource? = null
            var videoTrack: RTCVideoTrack? = null
            if (videoEnabled) {
                videoSource = peerConnectionFactory.videoSource()
                videoTrack = peerConnectionFactory.videoTrackWithSource(videoSource, "video_$streamId")
                nativeStream.addVideoTrack(videoTrack)
            }

            IOSMediaStream(
                nativeStream = nativeStream,
                audioTrack = audioTrack,
                audioSource = audioSource,
                videoTrack = videoTrack,
                videoSource = videoSource
            )
        }
    }

    override suspend fun enumerateDevices(): List<MediaDeviceInfo> {
        // iOS does not expose fine-grained device enumeration via WebRTC.
        return listOf(
            MediaDeviceInfo(
                deviceId = "default_audio_input",
                kind = "audioinput",
                label = "Default Microphone",
                groupId = "audio"
            ),
            MediaDeviceInfo(
                deviceId = "front_camera",
                kind = "videoinput",
                label = "Front Camera",
                groupId = "video"
            ),
            MediaDeviceInfo(
                deviceId = "back_camera",
                kind = "videoinput",
                label = "Back Camera",
                groupId = "video"
            )
        )
    }

    /**
     * Captures the screen for sharing using ReplayKit.
     * 
     * NOTE: iOS screen capture requires ReplayKit integration which needs:
     * 1. App Extension for broadcast upload (RPBroadcastSampleHandler)
     * 2. App Group for communication between app and extension
     * 3. User permission granted via RPScreenRecorder.shared().startCapture
     * 
     * @throws UnsupportedOperationException Currently not implemented - requires ReplayKit integration
     */
    override suspend fun getDisplayMedia(constraints: Map<String, Any?>): MediaStream {
        // TODO: Implement ReplayKit integration for iOS screen capture
        // This requires:
        // 1. RPScreenRecorder.shared().startCapture to begin capture
        // 2. Process CMSampleBuffer frames to RTCVideoFrame
        // 3. Feed frames to RTCVideoSource
        throw UnsupportedOperationException(
            "iOS screen capture requires ReplayKit integration. " +
            "Please implement RPScreenRecorder-based capture in a future update."
        )
    }

    override fun createSendTransport(params: Map<String, Any?>): WebRtcTransport {
        error("iOS send transport not implemented yet")
    }

    override fun createRecvTransport(params: Map<String, Any?>): WebRtcTransport {
        error("iOS receive transport not implemented yet")
    }

    override fun currentRtpCapabilities(): RtpCapabilities? = lastLoadedCapabilities

    override fun close() {
        runCatching { peerConnectionFactory.stopAecDump() }
            .onFailure { NSLog("MediaSFU - IOSWebRtcDevice: stopAecDump failed -> ${it.message}") }
    }

    private class IOSMediaStream(
        private val nativeStream: RTCMediaStream,
        private val audioTrack: RTCAudioTrack?,
        private val audioSource: RTCAudioSource?,
        private val videoTrack: RTCVideoTrack?,
        private val videoSource: RTCVideoSource?
    ) : MediaStream {

        private val audioWrapper = audioTrack?.let { IOSMediaStreamTrack(it) }
        private val videoWrapper = videoTrack?.let { IOSMediaStreamTrack(it) }

        override val id: String = nativeStream.streamId

        override val active: Boolean
            get() = listOfNotNull(audioWrapper, videoWrapper).any { it.isLive }

        override fun getTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper, videoWrapper)

        override fun getAudioTracks(): List<MediaStreamTrack> = listOfNotNull(audioWrapper)

        override fun getVideoTracks(): List<MediaStreamTrack> = listOfNotNull(videoWrapper)

        override fun addTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> nativeStream.addAudioTrack(native)
                    is RTCVideoTrack -> nativeStream.addVideoTrack(native)
                }
            }
        }

        override fun removeTrack(track: MediaStreamTrack) {
            if (track is IOSMediaStreamTrack) {
                when (val native = track.nativeTrack) {
                    is RTCAudioTrack -> nativeStream.removeAudioTrack(native)
                    is RTCVideoTrack -> nativeStream.removeVideoTrack(native)
                }
            }
        }

        override fun stop() {
            audioWrapper?.stop()
            videoWrapper?.stop()
            audioSource?.let { _ -> }
            videoSource?.let { _ -> }
        }
    }

    private class IOSMediaStreamTrack(
        val nativeTrack: RTCMediaStreamTrack
    ) : MediaStreamTrack {

        override val id: String
            get() = nativeTrack.trackId

        override val kind: String
            get() = nativeTrack.kind

        override val enabled: Boolean
            get() = nativeTrack.isEnabled

        val isLive: Boolean
            get() = nativeTrack.readyState == RTCMediaStreamTrackState.RTCMediaStreamTrackStateLive

        override fun stop() {
            nativeTrack.isEnabled = false
        }

        override fun asPlatformNativeTrack(): Any = nativeTrack
    }
}
