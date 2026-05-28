package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.RecordingParams
import com.mediasfu.sdk.socket.defaultMeetingRoomParams
import com.mediasfu.sdk.socket.defaultRecordingParams

internal fun Map<String, Any?>.mapValue(vararg keys: String): Map<String, Any?>? {
    return keys.firstNotNullOfOrNull { key ->
        this[key].asStringKeyMap()
    }
}

internal fun Map<String, Any?>.toCreateMeetingRoomParams(): MeetingRoomParams {
    val defaults = defaultMeetingRoomParams()
    val itemPageLimit = intValue("itemPageLimit") ?: defaults.itemPageLimit
    val mediaType = stringValue("mediaType") ?: defaults.mediaType
    val addCoHost = boolValue("addCoHost") ?: defaults.addCoHost
    val targetOrientation = stringValue("targetOrientation") ?: defaults.targetOrientation
    val targetOrientationHost = stringValue("targetOrientationHost") ?: defaults.targetOrientationHost
    val targetResolution = stringValue("targetResolution") ?: defaults.targetResolution
    val targetResolutionHost = stringValue("targetResolutionHost") ?: defaults.targetResolutionHost
    val type = stringValue("type") ?: defaults.type
    val audioSetting = stringValue("audioSetting") ?: defaults.audioSetting
    val videoSetting = stringValue("videoSetting") ?: defaults.videoSetting
    val screenshareSetting = stringValue("screenshareSetting") ?: defaults.screenshareSetting
    val chatSetting = stringValue("chatSetting") ?: defaults.chatSetting

    return MeetingRoomParams(
        itemPageLimit = itemPageLimit,
        mediaType = mediaType,
        addCoHost = addCoHost,
        targetOrientation = targetOrientation,
        targetOrientationHost = targetOrientationHost,
        targetResolution = targetResolution,
        targetResolutionHost = targetResolutionHost,
        type = type,
        audioSetting = audioSetting,
        videoSetting = videoSetting,
        screenshareSetting = screenshareSetting,
        chatSetting = chatSetting
    )
}

internal fun Map<String, Any?>.toCreateRecordingParams(): RecordingParams {
    val defaults = defaultRecordingParams()
    return RecordingParams(
        recordingAudioPausesLimit = intValue("recordingAudioPausesLimit") ?: defaults.recordingAudioPausesLimit,
        recordingAudioSupport = boolValue("recordingAudioSupport") ?: defaults.recordingAudioSupport,
        recordingAudioPeopleLimit = intValue("recordingAudioPeopleLimit") ?: defaults.recordingAudioPeopleLimit,
        recordingAudioParticipantsTimeLimit = intValue("recordingAudioParticipantsTimeLimit") ?: defaults.recordingAudioParticipantsTimeLimit,
        recordingVideoPausesLimit = intValue("recordingVideoPausesLimit") ?: defaults.recordingVideoPausesLimit,
        recordingVideoSupport = boolValue("recordingVideoSupport") ?: defaults.recordingVideoSupport,
        recordingVideoPeopleLimit = intValue("recordingVideoPeopleLimit") ?: defaults.recordingVideoPeopleLimit,
        recordingVideoParticipantsTimeLimit = intValue("recordingVideoParticipantsTimeLimit") ?: defaults.recordingVideoParticipantsTimeLimit,
        recordingAllParticipantsSupport = boolValue("recordingAllParticipantsSupport") ?: defaults.recordingAllParticipantsSupport,
        recordingVideoParticipantsSupport = boolValue("recordingVideoParticipantsSupport") ?: defaults.recordingVideoParticipantsSupport,
        recordingAllParticipantsFullRoomSupport = boolValue("recordingAllParticipantsFullRoomSupport") ?: defaults.recordingAllParticipantsFullRoomSupport,
        recordingVideoParticipantsFullRoomSupport = boolValue("recordingVideoParticipantsFullRoomSupport") ?: defaults.recordingVideoParticipantsFullRoomSupport,
        recordingPreferredOrientation = stringValue("recordingPreferredOrientation") ?: defaults.recordingPreferredOrientation,
        recordingSupportForOtherOrientation = boolValue("recordingSupportForOtherOrientation") ?: defaults.recordingSupportForOtherOrientation,
        recordingMultiFormatsSupport = boolValue("recordingMultiFormatsSupport") ?: defaults.recordingMultiFormatsSupport,
        recordingHlsSupport = boolValue("recordingHlsSupport") ?: defaults.recordingHlsSupport,
        recordingAudioPausesCount = intValue("recordingAudioPausesCount"),
        recordingVideoPausesCount = intValue("recordingVideoPausesCount")
    )
}

private fun Any?.asStringKeyMap(): Map<String, Any?>? {
    val rawMap = this as? Map<*, *> ?: return null
    val normalized = rawMap.entries.mapNotNull { (key, value) ->
        (key as? String)?.let { it to value }
    }
    return normalized.toMap().takeIf { it.isNotEmpty() }
}