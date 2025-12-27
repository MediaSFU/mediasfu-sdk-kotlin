package com.mediasfu.sdk.methods.recording_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.DispSpecs
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MainSpecs
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.TextSpecs
import com.mediasfu.sdk.model.UserRecordingParams

/**
 * Class for recording parameters used in confirming recording settings.
 */
interface ConfirmRecordingParameters {
    // Core properties
    val showAlert: ShowAlert?
    val recordingMediaOptions: String
    val recordingAudioOptions: String
    val recordingVideoOptions: String
    val recordingVideoType: String
    val recordingDisplayType: String
    val recordingNameTags: Boolean
    val recordingBackgroundColor: String
    val recordingNameTagsColor: String
    val recordingOrientationVideo: String
    val recordingAddHls: Boolean
    val recordingAddText: Boolean
    val recordingCustomText: String
    val recordingCustomTextPosition: String
    val recordingCustomTextColor: String
    val meetingDisplayType: String
    val recordingVideoParticipantsFullRoomSupport: Boolean
    val recordingAllParticipantsSupport: Boolean
    val recordingVideoParticipantsSupport: Boolean
    val recordingSupportForOtherOrientation: Boolean
    val recordingPreferredOrientation: String
    val recordingMultiFormatsSupport: Boolean
    val recordingVideoOptimized: Boolean
    val recordingAllParticipantsFullRoomSupport: Boolean
    val meetingVideoOptimized: Boolean
    val eventType: EventType
    val breakOutRoomStarted: Boolean
    val breakOutRoomEnded: Boolean
    
    // Update functions
    val updateRecordingDisplayType: (String) -> Unit
    val updateRecordingVideoOptimized: (Boolean) -> Unit
    val updateUserRecordingParams: (UserRecordingParams) -> Unit
    val updateConfirmedToRecord: (Boolean) -> Unit
    
    /**
     * Method to retrieve updated parameters.
     */
    fun getUpdatedAllParams(): ConfirmRecordingParameters
}

/**
 * Class encapsulating options for confirming recording.
 */
data class ConfirmRecordingOptions(
    val parameters: ConfirmRecordingParameters
)

/**
 * Type alias for confirm recording function.
 */
typealias ConfirmRecordingType = suspend (ConfirmRecordingOptions) -> Unit

/**
 * Confirms the recording based on the provided parameters.
 * 
 * The [options] parameter contains various settings and callbacks related to the recording.
 * The function performs validation checks on the parameters and displays appropriate alerts if any invalid options are selected.
 * It also updates the recording display type and other related settings based on the meeting display type.
 * 
 * The function uses the following callback functions to display alerts and update recording settings:
 * - [showAlert]: A function that displays an alert with the specified message, type, and duration.
 * - [updateRecordingDisplayType]: A function that updates the recording display type.
 * - [updateRecordingVideoOptimized]: A function that updates the recording video optimization setting.
 * - [updateUserRecordingParams]: A function that updates the user recording parameters.
 * - [updateConfirmedToRecord]: A function that updates the confirmed to record setting.
 * 
 * The function returns void.
 * 
 * Example usage:
 * ```kotlin
 * confirmRecording(ConfirmRecordingOptions(
 *     parameters = object : ConfirmRecordingParameters {
 *         override val showAlert: ShowAlert? = { message, type, duration -> Logger.d("ConfirmRecording", message) }
 *         override val recordingMediaOptions: String = "video"
 *         override val recordingAudioOptions: String = "high"
 *         override val recordingVideoOptions: String = "all"
 *         override val recordingVideoType: String = "HD"
 *         override val recordingDisplayType: String = "video"
 *         override val recordingNameTags: Boolean = true
 *         override val recordingBackgroundColor: String = "#000000"
 *         override val recordingNameTagsColor: String = "#ffffff"
 *         override val recordingOrientationVideo: String = "landscape"
 *         override val recordingAddHls: Boolean = true
 *         override val recordingAddText: Boolean = true
 *         override val recordingCustomText: String = "Meeting"
 *         override val recordingCustomTextPosition: String = "top-right"
 *         override val recordingCustomTextColor: String = "#ffffff"
 *         override val meetingDisplayType: String = "video"
 *         override val recordingVideoParticipantsFullRoomSupport: Boolean = true
 *         override val recordingAllParticipantsSupport: Boolean = true
 *         override val recordingVideoParticipantsSupport: Boolean = true
 *         override val recordingSupportForOtherOrientation: Boolean = true
 *         override val recordingPreferredOrientation: String = "landscape"
 *         override val recordingMultiFormatsSupport: Boolean = true
 *         override val recordingVideoOptimized: Boolean = true
 *         override val recordingAllParticipantsFullRoomSupport: Boolean = true
 *         override val meetingVideoOptimized: Boolean = false
 *         override val eventType: EventType = EventType.broadcast
 *         override val breakOutRoomStarted: Boolean = false
 *         override val breakOutRoomEnded: Boolean = true
 *         override fun getUpdatedAllParams(): ConfirmRecordingParameters = this
 *     }
 * ))
 * ```
 */
suspend fun confirmRecording(options: ConfirmRecordingOptions) {
    // Retrieve the latest parameters if needed
    val parameters = options.parameters.getUpdatedAllParams()
    
    // Destructure parameters
    val showAlert = parameters.showAlert
    val recordingMediaOptions = parameters.recordingMediaOptions
    val recordingAudioOptions = parameters.recordingAudioOptions
    val recordingVideoOptions = parameters.recordingVideoOptions
    val recordingVideoType = parameters.recordingVideoType
    val recordingDisplayType = parameters.recordingDisplayType
    val recordingNameTags = parameters.recordingNameTags
    val recordingBackgroundColor = parameters.recordingBackgroundColor
    val recordingNameTagsColor = parameters.recordingNameTagsColor
    val recordingOrientationVideo = parameters.recordingOrientationVideo
    val recordingAddHls = parameters.recordingAddHls
    val recordingAddText = parameters.recordingAddText
    val recordingCustomText = parameters.recordingCustomText
    val recordingCustomTextPosition = parameters.recordingCustomTextPosition
    val recordingCustomTextColor = parameters.recordingCustomTextColor
    val meetingDisplayType = parameters.meetingDisplayType
    val recordingVideoParticipantsFullRoomSupport = parameters.recordingVideoParticipantsFullRoomSupport
    val recordingAllParticipantsSupport = parameters.recordingAllParticipantsSupport
    val recordingVideoParticipantsSupport = parameters.recordingVideoParticipantsSupport
    val recordingSupportForOtherOrientation = parameters.recordingSupportForOtherOrientation
    val recordingPreferredOrientation = parameters.recordingPreferredOrientation
    val recordingMultiFormatsSupport = parameters.recordingMultiFormatsSupport
    val recordingVideoOptimized = parameters.recordingVideoOptimized
    val recordingAllParticipantsFullRoomSupport = parameters.recordingAllParticipantsFullRoomSupport
    val meetingVideoOptimized = parameters.meetingVideoOptimized
    val eventType = parameters.eventType
    val breakOutRoomStarted = parameters.breakOutRoomStarted
    val breakOutRoomEnded = parameters.breakOutRoomEnded
    
    // Callback functions for updating recording settings
    val updateRecordingDisplayType = parameters.updateRecordingDisplayType
    val updateRecordingVideoOptimized = parameters.updateRecordingVideoOptimized
    val updateUserRecordingParams = parameters.updateUserRecordingParams
    val updateConfirmedToRecord = parameters.updateConfirmedToRecord
    
    // Perform validation checks similar to TypeScript logic
    if (!recordingVideoParticipantsFullRoomSupport &&
        recordingVideoOptions == "all" &&
        recordingMediaOptions == "video") {
        if (meetingDisplayType == "all" &&
            !(breakOutRoomStarted && !breakOutRoomEnded)) {
            showAlert?.invoke(
                "You are not allowed to record videos of all participants; change the meeting display type to video or video optimized.",
                "danger",
                3000
            )
            return
        }
    }
    
    if (!recordingAllParticipantsSupport && recordingVideoOptions == "all") {
        showAlert?.invoke(
            "You are only allowed to record yourself.",
            "danger",
            3000
        )
        return
    }
    
    if (!recordingVideoParticipantsSupport && recordingDisplayType == "video") {
        showAlert?.invoke(
            "You are not allowed to record other video participants.",
            "danger",
            3000
        )
        return
    }
    
    if (!recordingSupportForOtherOrientation &&
        recordingOrientationVideo == "all") {
        showAlert?.invoke(
            "You are not allowed to record all orientations.",
            "danger",
            3000
        )
        return
    }
    
    if ((recordingPreferredOrientation == "landscape" &&
            recordingOrientationVideo == "portrait") ||
        (recordingPreferredOrientation == "portrait" &&
            recordingOrientationVideo == "landscape")) {
        if (!recordingSupportForOtherOrientation) {
            showAlert?.invoke(
                "You are not allowed to record this orientation.",
                "danger",
                3000
            )
            return
        }
    }
    
    if (!recordingMultiFormatsSupport && recordingVideoType == "all") {
        showAlert?.invoke(
            "You are not allowed to record all formats.",
            "danger",
            3000
        )
        return
    }
    
    if (eventType != EventType.BROADCAST) {
        if (recordingMediaOptions == "video") {
            if (meetingDisplayType == "media" && recordingDisplayType == "all") {
                showAlert?.invoke(
                    "Recording display type can be either video, video optimized, or media when meeting display type is media.",
                    "danger",
                    3000
                )
                updateRecordingDisplayType(meetingDisplayType)
                return
            }
            
            if (meetingDisplayType == "video" &&
                (recordingDisplayType == "all" || recordingDisplayType == "media")) {
                showAlert?.invoke(
                    "Recording display type can be either video or video optimized when meeting display type is video.",
                    "danger",
                    3000
                )
                updateRecordingDisplayType(meetingDisplayType)
                return
            }
            
            if (meetingVideoOptimized && !recordingVideoOptimized) {
                showAlert?.invoke(
                    "Recording display type can only be video optimized when meeting display type is video optimized.",
                    "danger",
                    3000
                )
                updateRecordingVideoOptimized(meetingVideoOptimized)
                return
            }
        } else {
            updateRecordingDisplayType("media")
            updateRecordingVideoOptimized(false)
        }
    }
    
    if (recordingDisplayType == "all" &&
        !recordingAllParticipantsFullRoomSupport) {
        showAlert?.invoke(
            "You can only record all participants with media.",
            "danger",
            3000
        )
        return
    }
    
    // Build recording parameter specs and update state
    val mainSpecs = MainSpecs(
        mediaOptions = recordingMediaOptions,
        audioOptions = recordingAudioOptions,
        videoOptions = recordingVideoOptions,
        videoType = recordingVideoType,
        videoOptimized = recordingVideoOptimized,
        recordingDisplayType = recordingDisplayType,
        addHls = recordingAddHls
    )
    
    val dispSpecs = DispSpecs(
        nameTags = recordingNameTags,
        backgroundColor = recordingBackgroundColor,
        nameTagsColor = recordingNameTagsColor,
        orientationVideo = recordingOrientationVideo
    )
    
    val textSpecs = TextSpecs(
        addText = recordingAddText,
        customText = recordingCustomText,
        customTextPosition = recordingCustomTextPosition,
        customTextColor = recordingCustomTextColor
    )
    
    val userRecordingParams = UserRecordingParams(
        mainSpecs = mainSpecs,
        dispSpecs = dispSpecs,
        textSpecs = textSpecs
    )
    
    updateUserRecordingParams(userRecordingParams)
    updateConfirmedToRecord(true)
    
    // Show success feedback to user
    showAlert?.invoke(
        "Recording settings confirmed. You can now start recording.",
        "success",
        3000
    )
}
