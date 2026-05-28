package com.mediasfu.sdk.ui.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.model.Credentials
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.util.MediaSFURuntimeProbe
import com.mediasfu.sdk.webrtc.WebRtcFactory
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import platform.Foundation.NSLog
import platform.UIKit.UIViewController

/**
 * Swift-facing launch configuration for hosting the shared MediaSFU UI from an iOS app shell.
 *
 * This avoids forcing Swift callers to construct `MediasfuGenericOptions` directly, which is
 * awkward because the options type includes Compose callbacks and other Kotlin-centric fields.
 */
class MediaSFUIosLaunchConfig {
    var apiUserName: String = ""
    var apiKey: String = ""
    var localLink: String = ""
    var userName: String = "tester"
    var roomName: String = "mediasfu-demo"
    var connectMediaSFU: Boolean = true
    var action: String = "create"
    var durationMinutes: Int = 60
    var capacity: Int = 100
    var eventType: String = "conference"
    var scheduledDate: Long = 0L
    var secureCode: String = ""
    var adminPasscode: String = ""
    var islevel: String = "0"
    var recordOnly: Boolean = false
    var safeRoom: Boolean = false
    var autoStartSafeRoom: Boolean = false
    var safeRoomAction: String = "kick"
    var dataBuffer: Boolean = false
    var bufferType: String = "all"
    var autoProceed: Boolean = false
    var useModernTheme: Boolean = true
    var useModernUI: Boolean = true
}

/**
 * Explicit iOS host bridge exported from the shared KMP framework.
 *
 * Swift host apps can use this class to obtain a `UIViewController` backed by the shared
 * Compose MediaSFU UI without needing to guess at the Compose/UIKit mounting surface.
 */
class MediaSFUIosHostBridge {
    private val bridgeScope = MainScope()
    private var latestOptions: MediasfuGenericOptions? = null

    fun resetRuntimeProbe() {
        MediaSFUIosRuntimeProbeStore.reset()
    }

    fun latestRuntimeProbeSummary(): String = MediaSFUIosRuntimeProbeStore.latestSummary()

    fun triggerToggleAudio(): Boolean {
        val handler = latestOptions?.onToggleAudio ?: return false
        bridgeScope.launch {
            handler()
        }
        return true
    }

    fun triggerToggleVideo(): Boolean {
        val handler = latestOptions?.onToggleVideo
        if (handler == null) {
            MediaSFURuntimeProbe.recordProducerSignalStage("toggle-missing-handler", "video", "")
            return false
        }

        MediaSFURuntimeProbe.recordProducerSignalStage("toggle-dispatched", "video", "")
        bridgeScope.launch(start = CoroutineStart.UNDISPATCHED) {
            MediaSFURuntimeProbe.recordProducerSignalStage("toggle-bridge-enter", "video", "")
            try {
                MediaSFURuntimeProbe.recordProducerSignalStage("toggle-call-handler", "video", "")
                handler()
                MediaSFURuntimeProbe.recordProducerSignalStage("toggle-handler-return", "video", "")
            } catch (error: Throwable) {
                MediaSFURuntimeProbe.recordProducerSignalStage(
                    "toggle-exception",
                    "video",
                    error.message.orEmpty()
                )
                throw error
            }
        }
        return true
    }

    fun triggerToggleScreenShare(): Boolean {
        val handler = latestOptions?.onToggleScreenShare ?: return false
        bridgeScope.launch {
            handler()
        }
        return true
    }

    fun makeHostViewController(config: MediaSFUIosLaunchConfig): UIViewController {
        val parameters = buildParameters(config)
        val options = buildOptions(config, parameters)
        latestOptions = options
        return makeHostViewController(options)
    }

    fun makeHostViewController(
        apiUserName: String,
        apiKey: String,
        localLink: String,
        userName: String,
        roomName: String,
        connectMediaSFU: Boolean,
        action: String,
    ): UIViewController {
        val config = MediaSFUIosLaunchConfig().apply {
            this.apiUserName = apiUserName
            this.apiKey = apiKey
            this.localLink = localLink
            this.userName = userName
            this.roomName = roomName
            this.connectMediaSFU = connectMediaSFU
            this.action = action
        }
        return makeHostViewController(config)
    }

    fun makeHostViewController(options: MediasfuGenericOptions): UIViewController {
        latestOptions = options
        return ComposeUIViewController {
            MediasfuGeneric(
                options = options,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    fun makeLaunchConfig(): MediaSFUIosLaunchConfig = MediaSFUIosLaunchConfig()

    private fun buildParameters(config: MediaSFUIosLaunchConfig): MediasfuParameters {
        val trimmedApiUserName = config.apiUserName.trim()
        val trimmedApiKey = config.apiKey.trim()
        val trimmedLocalLink = config.localLink.trim()
        val trimmedUserName = config.userName.trim().ifBlank { "tester" }
        val trimmedRoomName = config.roomName.trim().ifBlank { "mediasfu-demo" }

        MediaSFUIosRuntimeProbeStore.reset()

        return MediasfuParameters().apply {
            device = runCatching { WebRtcFactory.createDevice() }
                .onFailure { error ->
                    NSLog("MediaSFU - MediaSFUIosHostBridge: failed to create WebRTC device -> ${error.message}")
                }
                .getOrNull()
            apiUserName = trimmedApiUserName
            apiKey = trimmedApiKey
            link = trimmedLocalLink
            member = trimmedUserName
            roomName = trimmedRoomName
            validated = false
            onParticipantsUpdated = { participants ->
                MediaSFUIosRuntimeProbeStore.recordParticipants(participants.size)
            }
            onLStreamsUpdated = { streams ->
                MediaSFUIosRuntimeProbeStore.recordVisibleStreams(streams.size)
            }
            onAudioOnlyStreamsUpdated = { streams ->
                MediaSFUIosRuntimeProbeStore.recordAudioOnlyStreams(streams.size)
            }
            onAudioAlreadyOnChanged = { enabled ->
                MediaSFUIosRuntimeProbeStore.recordLocalAudioEnabled(enabled)
            }
            onVideoAlreadyOnChanged = { enabled ->
                MediaSFUIosRuntimeProbeStore.recordLocalVideoEnabled(enabled)
            }
            onScreenAlreadyOnChanged = { enabled ->
                MediaSFUIosRuntimeProbeStore.recordLocalScreenShareEnabled(enabled)
            }
            onShareScreenStartedChanged = { started ->
                MediaSFUIosRuntimeProbeStore.recordRemoteScreenShareStarted(started)
            }
            onAlertStateChanged = { message, type, _ ->
                MediaSFUIosRuntimeProbeStore.recordAlert(message, type)
            }
        }
    }

    private fun buildOptions(
        config: MediaSFUIosLaunchConfig,
        parameters: MediasfuParameters,
    ): MediasfuGenericOptions {
        val trimmedApiUserName = config.apiUserName.trim()
        val trimmedApiKey = config.apiKey.trim()
        val trimmedLocalLink = config.localLink.trim()
        val normalizedAction = config.action.trim().lowercase().ifBlank { "create" }
        val trimmedUserName = config.userName.trim().ifBlank { "tester" }
        val trimmedRoomName = config.roomName.trim().ifBlank { "mediasfu-demo" }
        val trimmedEventType = config.eventType.trim().lowercase().ifBlank { "conference" }
        val scheduledDate = config.scheduledDate.takeIf { it > 0L }
        val trimmedSecureCode = config.secureCode.trim().ifBlank { null }
        val trimmedAdminPasscode = config.adminPasscode.trim().ifBlank { null }
        val trimmedSafeRoomAction = config.safeRoomAction.trim().ifBlank { "kick" }
        val trimmedBufferType = config.bufferType.trim().ifBlank { "all" }
        val effectiveDuration = config.durationMinutes.coerceAtLeast(1)
        val effectiveCapacity = config.capacity.coerceAtLeast(1)
        val credentials = if (config.connectMediaSFU && trimmedApiUserName.isNotEmpty() && trimmedApiKey.isNotEmpty()) {
            Credentials(
                apiUserName = trimmedApiUserName,
                apiKey = trimmedApiKey,
            )
        } else {
            null
        }

        MediaSFURuntimeProbe.recordConsumerSignalStage(
            stage = "launch-config",
            remoteProducerId = "",
            detail = "action=${normalizedAction},cloud=${if (config.connectMediaSFU) 1 else 0},creds=${if (credentials != null) 1 else 0},room=${trimmedRoomName},user=${trimmedUserName}"
        )

        val noUiCreateOptions = if (config.autoProceed && normalizedAction == "create") {
            buildMap<String, Any> {
                put("action", "create")
                put("duration", effectiveDuration)
                put("capacity", effectiveCapacity)
                put("userName", trimmedUserName)
                put("eventType", trimmedEventType)
                put("recordOnly", config.recordOnly)
                scheduledDate?.let { put("scheduledDate", it) }
                trimmedSecureCode?.let { put("secureCode", it) }
                put("safeRoom", config.safeRoom)
                put("autoStartSafeRoom", config.autoStartSafeRoom)
                put("safeRoomAction", trimmedSafeRoomAction)
                put("dataBuffer", config.dataBuffer)
                put("bufferType", trimmedBufferType)
            }
        } else {
            null
        }

        val noUiJoinOptions = if (config.autoProceed && normalizedAction == "join") {
            buildMap<String, Any> {
                put("action", "join")
                put("meetingID", trimmedRoomName)
                put("userName", trimmedUserName)
                trimmedAdminPasscode?.let { put("adminPasscode", it) }
                put("islevel", config.islevel.trim().ifBlank { "0" })
            }
        } else {
            null
        }

        return MediasfuGenericOptions(
            localLink = trimmedLocalLink,
            connectMediaSFU = config.connectMediaSFU,
            credentials = credentials,
            sourceParameters = parameters,
            returnUI = noUiCreateOptions == null && noUiJoinOptions == null,
            noUIPreJoinOptionsCreate = noUiCreateOptions,
            noUIPreJoinOptionsJoin = noUiJoinOptions,
            defaultCreateMode = normalizedAction == "create",
            defaultDisplayName = trimmedUserName,
            defaultMeetingId = trimmedRoomName,
            defaultDurationMinutes = effectiveDuration,
            defaultCapacity = effectiveCapacity,
            defaultEventType = when (trimmedEventType) {
                "chat" -> EventType.CHAT
                "broadcast" -> EventType.BROADCAST
                "webinar" -> EventType.WEBINAR
                else -> EventType.CONFERENCE
            },
            useModernTheme = config.useModernTheme,
            useModernUI = config.useModernUI,
        )
    }
}
