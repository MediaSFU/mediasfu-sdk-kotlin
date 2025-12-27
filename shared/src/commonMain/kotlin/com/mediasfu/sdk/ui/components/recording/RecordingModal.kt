package com.mediasfu.sdk.ui.components.recording

import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.ui.*

/**
 * RecordingModal - Modal for managing recording settings and controls.
 *
 * Provides interface for configuring and controlling session recording.
 *
 * @property options Configuration options for the recording modal
 */
data class RecordingModalOptions(
    val isRecordingModalVisible: Boolean = false,
    val onClose: () -> Unit,
    val backgroundColor: Int = 0xFF83C0E9.toInt(),
    val position: String = "bottomRight",
    val confirmRecording: (ConfirmRecordingOptions) -> Unit,
    val startRecording: (StartRecordingOptions) -> Unit,
    val parameters: RecordingModalParameters,
)

data class RecordingModalParameters(
    val recordPaused: Boolean,
    val recordingVideoType: String,
    val recordingDisplayType: String,
    val recordingBackgroundColor: String,
    val recordingNameTagsColor: String,
    val recordingOrientationVideo: String,
    val recordingNameTags: Boolean,
    val recordingAddText: Boolean,
    val recordingCustomText: String,
    val recordingCustomTextPosition: String,
    val recordingCustomTextColor: String,
    val recordingMediaOptions: String,
    val recordingAudioOptions: String,
    val recordingVideoOptions: String,
    val recordingAddHLS: Boolean,
    val eventType: EventType,
    val updateRecordingVideoType: (String) -> Unit,
    val updateRecordingDisplayType: (String) -> Unit,
    val updateRecordingBackgroundColor: (String) -> Unit,
    val updateRecordingNameTagsColor: (String) -> Unit,
    val updateRecordingOrientationVideo: (String) -> Unit,
    val updateRecordingNameTags: (Boolean) -> Unit,
    val updateRecordingAddText: (Boolean) -> Unit,
    val updateRecordingCustomText: (String) -> Unit,
    val updateRecordingCustomTextPosition: (String) -> Unit,
    val updateRecordingCustomTextColor: (String) -> Unit,
    val updateRecordingMediaOptions: (String) -> Unit,
    val updateRecordingAudioOptions: (String) -> Unit,
    val updateRecordingVideoOptions: (String) -> Unit,
    val updateRecordingAddHLS: (Boolean) -> Unit,
)

data class ConfirmRecordingOptions(
    val parameters: RecordingModalParameters,
)

data class StartRecordingOptions(
    val parameters: RecordingModalParameters,
)

interface RecordingModal : MediaSfuUIComponent {
    val options: RecordingModalOptions
    override val id: String get() = "recording_modal"
    override val isVisible: Boolean get() = options.isRecordingModalVisible
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    /**
     * Gets current recording mode (standard/advanced)
     */
    fun getCurrentMode(): String {
        return if (options.parameters.recordingMediaOptions == "video") {
            "standard"
        } else {
            "advanced"
        }
    }
}

/**
 * Default implementation of RecordingModal
 */
class DefaultRecordingModal(
    override val options: RecordingModalOptions
) : RecordingModal {
    fun render(): Any {
        return mapOf(
            "type" to "recordingModal",
            "isVisible" to options.isRecordingModalVisible,
            "onClose" to options.onClose,
            "backgroundColor" to options.backgroundColor,
            "position" to options.position,
            "currentMode" to getCurrentMode(),
            "parameters" to options.parameters,
            "onConfirm" to options.confirmRecording,
            "onStart" to options.startRecording
        )
    }
}
