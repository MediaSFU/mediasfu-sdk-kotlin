package com.mediasfu.sdk.producer_client
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.DimensionConstraints
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.VidCons
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.socket.ResponseJoinRoom
import com.mediasfu.sdk.webrtc.MediaKind as WebRtcMediaKind
import com.mediasfu.sdk.webrtc.RtcpFeedback
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpHeaderDirection
import com.mediasfu.sdk.webrtc.RtpEncodingParameters
import com.mediasfu.sdk.webrtc.RtpHeaderExtension
import com.mediasfu.sdk.webrtc.RtpCodecCapability as WebRtcRtpCodecCapability
import com.mediasfu.sdk.methods.utils.producer.AParams
import com.mediasfu.sdk.methods.utils.producer.HParams
import com.mediasfu.sdk.methods.utils.producer.ScreenParams
import com.mediasfu.sdk.methods.utils.producer.VParams
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType

/**
 * Type definitions for update functions.
 */
typealias UpdateRtpCapabilities = (RtpCapabilities?) -> Unit
typealias UpdateRoomRecvIPs = (List<String>) -> Unit
typealias UpdateMeetingRoomParams = (MeetingRoomParams?) -> Unit
typealias UpdateItemPageLimit = (Int) -> Unit
typealias UpdateAudioOnlyRoom = (Boolean) -> Unit
typealias UpdateAddForBasic = (Boolean) -> Unit
typealias UpdateScreenPageLimit = (Int) -> Unit
typealias UpdateVidCons = (VidCons) -> Unit
typealias UpdateFrameRate = (Int) -> Unit
typealias UpdateAdminPasscode = (String) -> Unit
typealias UpdateEventType = (EventType) -> Unit
typealias UpdateYouAreCoHost = (Boolean) -> Unit
typealias UpdateAutoWave = (Boolean) -> Unit
typealias UpdateForceFullDisplay = (Boolean) -> Unit
typealias UpdateChatSetting = (String) -> Unit
typealias UpdateMeetingDisplayType = (String) -> Unit
typealias UpdateAudioSetting = (String) -> Unit
typealias UpdateVideoSetting = (String) -> Unit
typealias UpdateScreenshareSetting = (String) -> Unit
typealias UpdateHParams = (ProducerOptionsType) -> Unit
typealias UpdateVParams = (ProducerOptionsType) -> Unit
typealias UpdateScreenParams = (ProducerOptionsType) -> Unit
typealias UpdateAParams = (ProducerOptionsType) -> Unit
typealias UpdateMainHeightWidth = (Double) -> Unit
typealias UpdateTargetResolution = (String) -> Unit
typealias UpdateTargetResolutionHost = (String) -> Unit

// Recording-related update function typedefs
typealias UpdateRecordingAudioPausesLimit = (Int) -> Unit
typealias UpdateRecordingAudioPausesCount = (Int) -> Unit
typealias UpdateRecordingAudioSupport = (Boolean) -> Unit
typealias UpdateRecordingAudioPeopleLimit = (Int) -> Unit
typealias UpdateRecordingAudioParticipantsTimeLimit = (Int) -> Unit
typealias UpdateRecordingVideoPausesCount = (Int) -> Unit
typealias UpdateRecordingVideoPausesLimit = (Int) -> Unit
typealias UpdateRecordingVideoSupport = (Boolean) -> Unit
typealias UpdateRecordingVideoPeopleLimit = (Int) -> Unit
typealias UpdateRecordingVideoParticipantsTimeLimit = (Int) -> Unit
typealias UpdateRecordingAllParticipantsSupport = (Boolean) -> Unit
typealias UpdateRecordingVideoParticipantsSupport = (Boolean) -> Unit
typealias UpdateRecordingAllParticipantsFullRoomSupport = (Boolean) -> Unit
typealias UpdateRecordingVideoParticipantsFullRoomSupport = (Boolean) -> Unit
typealias UpdateRecordingPreferredOrientation = (String) -> Unit
typealias UpdateRecordingSupportForOtherOrientation = (Boolean) -> Unit
typealias UpdateRecordingMultiFormatsSupport = (Boolean) -> Unit
typealias UpdateRecordingVideoOptions = (String) -> Unit
typealias UpdateRecordingAudioOptions = (String) -> Unit

/**
 * Parameters for updating room configuration.
 */
interface UpdateRoomParametersClientParameters {
    // Core properties
    val rtpCapabilities: RtpCapabilities?
    val roomRecvIPs: List<String>
    val meetingRoomParams: MeetingRoomParams?
    val itemPageLimit: Int
    val audioOnlyRoom: Boolean
    val addForBasic: Boolean
    val screenPageLimit: Int
    val shareScreenStarted: Boolean
    val shared: Boolean
    val targetOrientation: String
    val vidCons: VidCons
    val recordingVideoSupport: Boolean
    val frameRate: Int
    val adminPasscode: String
    val eventType: EventType
    val youAreCoHost: Boolean
    val autoWave: Boolean
    val forceFullDisplay: Boolean
    val chatSetting: String
    val meetingDisplayType: String
    val audioSetting: String
    val videoSetting: String
    val screenshareSetting: String
    val hParams: ProducerOptionsType?
    val vParams: ProducerOptionsType?
    val screenParams: ProducerOptionsType?
    val aParams: ProducerOptionsType?
    val islevel: String
    val showAlert: ShowAlert?
    val roomData: ResponseJoinRoom
    
    // Update function callbacks
    val updateRtpCapabilities: UpdateRtpCapabilities
    val updateRoomRecvIPs: UpdateRoomRecvIPs
    val updateMeetingRoomParams: UpdateMeetingRoomParams
    val updateItemPageLimit: UpdateItemPageLimit
    val updateAudioOnlyRoom: UpdateAudioOnlyRoom
    val updateAddForBasic: UpdateAddForBasic
    val updateScreenPageLimit: UpdateScreenPageLimit
    val updateVidCons: UpdateVidCons
    val updateFrameRate: UpdateFrameRate
    val updateAdminPasscode: UpdateAdminPasscode
    val updateEventType: UpdateEventType
    val updateYouAreCoHost: UpdateYouAreCoHost
    val updateAutoWave: UpdateAutoWave
    val updateForceFullDisplay: UpdateForceFullDisplay
    val updateChatSetting: UpdateChatSetting
    val updateMeetingDisplayType: UpdateMeetingDisplayType
    val updateAudioSetting: UpdateAudioSetting
    val updateVideoSetting: UpdateVideoSetting
    val updateScreenshareSetting: UpdateScreenshareSetting
    val updateHParams: UpdateHParams
    val updateVParams: UpdateVParams
    val updateScreenParams: UpdateScreenParams
    val updateAParams: UpdateAParams
    val updateMainHeightWidth: UpdateMainHeightWidth
    val updateTargetResolution: UpdateTargetResolution
    val updateTargetResolutionHost: UpdateTargetResolutionHost
    
    // Recording-related update functions
    val updateRecordingAudioPausesLimit: UpdateRecordingAudioPausesLimit
    val updateRecordingAudioPausesCount: UpdateRecordingAudioPausesCount
    val updateRecordingAudioSupport: UpdateRecordingAudioSupport
    val updateRecordingAudioPeopleLimit: UpdateRecordingAudioPeopleLimit
    val updateRecordingAudioParticipantsTimeLimit: UpdateRecordingAudioParticipantsTimeLimit
    val updateRecordingVideoPausesCount: UpdateRecordingVideoPausesCount
    val updateRecordingVideoPausesLimit: UpdateRecordingVideoPausesLimit
    val updateRecordingVideoSupport: UpdateRecordingVideoSupport
    val updateRecordingVideoPeopleLimit: UpdateRecordingVideoPeopleLimit
    val updateRecordingVideoParticipantsTimeLimit: UpdateRecordingVideoParticipantsTimeLimit
    val updateRecordingAllParticipantsSupport: UpdateRecordingAllParticipantsSupport
    val updateRecordingVideoParticipantsSupport: UpdateRecordingVideoParticipantsSupport
    val updateRecordingAllParticipantsFullRoomSupport: UpdateRecordingAllParticipantsFullRoomSupport
    val updateRecordingVideoParticipantsFullRoomSupport: UpdateRecordingVideoParticipantsFullRoomSupport
    val updateRecordingPreferredOrientation: UpdateRecordingPreferredOrientation
    val updateRecordingSupportForOtherOrientation: UpdateRecordingSupportForOtherOrientation
    val updateRecordingMultiFormatsSupport: UpdateRecordingMultiFormatsSupport
    val updateRecordingVideoOptions: UpdateRecordingVideoOptions
    val updateRecordingAudioOptions: UpdateRecordingAudioOptions
    
    fun getUpdatedAllParams(): UpdateRoomParametersClientParameters
}

/**
 * Options class for updating room parameters.
 */
data class UpdateRoomParametersClientOptions(
    val parameters: UpdateRoomParametersClientParameters
)

/**
 * Type definition for updating room parameters client.
 */
typealias UpdateRoomParametersClientType = (UpdateRoomParametersClientOptions) -> Unit

/**
 * Updates the room configuration parameters based on provided options.
 * 
 * The `updateRoomParametersClient` function allows for the flexible and dynamic
 * updating of room parameters such as video, audio, and screen sharing configurations.
 * It takes in an `UpdateRoomParametersClientOptions` object that contains the room parameters,
 * along with multiple update functions used to apply changes to these parameters.
 * 
 * Key configurable parameters include:
 * - **Video Encoding Parameters**: Bitrate, resolution, and scalability settings for video encodings.
 * - **Audio and Video Settings**: Controls for media type (audio-only or video) and individual codec settings.
 * - **Recording Parameters**: Settings for audio and video recording limitations, support, and orientation.
 * - **Screen Sharing and Frame Rate**: Adjustments for screen-sharing constraints and display frame rates.
 * 
 * The function will update each parameter according to the current room configuration
 * and level, ensuring appropriate values are applied for different event types and device constraints.
 */
fun updateRoomParametersClient(options: UpdateRoomParametersClientOptions) {
    try {
        val params = options.parameters.getUpdatedAllParams()
        
        if (params.roomData.rtpCapabilities == null) {
            params.showAlert?.invoke(
                message = "Sorry, you are not allowed to join this room. ${params.roomData.reason ?: ""}",
                type = "danger",
                duration = 3000
            )
            return
        }
        
    params.updateRtpCapabilities(params.roomData.rtpCapabilities?.toWebRtcCapabilities())
        params.updateAdminPasscode(params.roomData.secureCode ?: "")
        params.updateRoomRecvIPs(params.roomData.roomRecvIPs ?: emptyList())
        params.updateMeetingRoomParams(params.roomData.meetingRoomParams)
        
        val recordingParams = params.roomData.recordingParams
        params.updateRecordingAudioPausesLimit(recordingParams?.recordingAudioPausesLimit ?: 0)
        params.updateRecordingAudioPausesCount(recordingParams?.recordingAudioPausesCount ?: 0)
        params.updateRecordingAudioSupport(recordingParams?.recordingAudioSupport ?: false)
        params.updateRecordingAudioPeopleLimit(recordingParams?.recordingAudioPeopleLimit ?: 0)
        params.updateRecordingAudioParticipantsTimeLimit(recordingParams?.recordingAudioParticipantsTimeLimit ?: 0)
        params.updateRecordingVideoPausesCount(recordingParams?.recordingVideoPausesCount ?: 0)
        params.updateRecordingVideoPausesLimit(recordingParams?.recordingVideoPausesLimit ?: 0)
        params.updateRecordingVideoSupport(recordingParams?.recordingVideoSupport ?: false)
        params.updateRecordingVideoPeopleLimit(recordingParams?.recordingVideoPeopleLimit ?: 0)
        params.updateRecordingVideoParticipantsTimeLimit(recordingParams?.recordingVideoParticipantsTimeLimit ?: 0)
        params.updateRecordingAllParticipantsSupport(recordingParams?.recordingAllParticipantsSupport ?: false)
        params.updateRecordingVideoParticipantsSupport(recordingParams?.recordingVideoParticipantsSupport ?: false)
        params.updateRecordingAllParticipantsFullRoomSupport(recordingParams?.recordingAllParticipantsFullRoomSupport ?: false)
        params.updateRecordingVideoParticipantsFullRoomSupport(recordingParams?.recordingVideoParticipantsFullRoomSupport ?: false)
        params.updateRecordingPreferredOrientation(recordingParams?.recordingPreferredOrientation ?: "")
        params.updateRecordingSupportForOtherOrientation(recordingParams?.recordingSupportForOtherOrientation ?: false)
        params.updateRecordingMultiFormatsSupport(recordingParams?.recordingMultiFormatsSupport ?: false)
        
        val meetingParams = params.roomData.meetingRoomParams
        params.updateItemPageLimit(meetingParams?.itemPageLimit ?: 0)
        val eventType_ = when (meetingParams?.type?.lowercase()) {
            "conference" -> EventType.CONFERENCE
            "webinar" -> EventType.WEBINAR
            "broadcast" -> EventType.BROADCAST
            "chat" -> EventType.CHAT
            else -> EventType.CONFERENCE
        }
        params.updateEventType(eventType_)
        if (eventType_ == EventType.CHAT && params.islevel != "2") {
            params.updateYouAreCoHost(true)
        }
        if (eventType_ == EventType.CHAT ||
            eventType_ == EventType.BROADCAST) {
            params.updateAutoWave(false)
            params.updateMeetingDisplayType("all")
            params.updateForceFullDisplay(true)
            params.updateChatSetting(meetingParams?.chatSetting ?: "allow")
            
            if (eventType_ == EventType.BROADCAST) {
                params.updateRecordingVideoOptions("mainScreen")
                params.updateRecordingAudioOptions("host")
                params.updateItemPageLimit(1)
            }
        }
        params.updateAudioSetting(meetingParams?.audioSetting ?: "allow")
        params.updateVideoSetting(meetingParams?.videoSetting ?: "allow")
        params.updateScreenshareSetting(meetingParams?.screenshareSetting ?: "allow")
        params.updateChatSetting(meetingParams?.chatSetting ?: "allow")
        
        params.updateAudioOnlyRoom(meetingParams?.mediaType != "video")
        
    if (eventType_ == EventType.CONFERENCE) {
            if (params.shared || params.shareScreenStarted) {
                params.updateMainHeightWidth(100.0)
            } else {
                params.updateMainHeightWidth(0.0)
            }
        }
        
        params.updateScreenPageLimit(meetingParams?.itemPageLimit ?: 2)
        
        val targetOrientation = if (params.islevel == "2") {
            meetingParams?.targetOrientationHost ?: "neutral"
        } else {
            meetingParams?.targetOrientation ?: "neutral"
        }
        val targetResolution = if (params.islevel == "2") {
            meetingParams?.targetResolutionHost ?: "sd"
        } else {
            meetingParams?.targetResolution ?: "sd"
        }
        
        val (frameRate, vdCons) = getVideoConstraints(targetOrientation, targetResolution)
        
        val hParams = HParams.getH264Params()
        val vParams = VParams.getVideoParams()
        
        val updatedHParams = updateEncodingBitrates(hParams, targetResolution)
        val updatedVParams = updateEncodingBitrates(vParams, targetResolution)
        
        params.updateVidCons(vdCons)
        params.updateFrameRate(frameRate)
        params.updateHParams(updatedHParams)
        params.updateVParams(updatedVParams)
        params.updateScreenParams(params.screenParams ?: ScreenParams.getScreenParams())
        params.updateAParams(params.aParams ?: AParams.getAudioParams())
    } catch (error: Exception) {
        Logger.e("UpdateRoomParameters", "Update room parameters error: $error")
        try {
            val showAlert = options.parameters.showAlert
            showAlert?.invoke(
                message = error.toString(),
                type = "danger",
                duration = 3000
            )
        } catch (e: Exception) {
            // Ignore alert errors
        }
    }
}

/**
 * Helper function to get video constraints based on orientation and resolution.
 */
private fun getVideoConstraints(targetOrientation: String, targetResolution: String): Pair<Int, VidCons> {
    val frameRate: Int
    val vdCons: VidCons

    fun buildVidCons(width: Int, height: Int): VidCons = VidCons(
        width = DimensionConstraints(ideal = width, exact = width),
        height = DimensionConstraints(ideal = height, exact = height)
    )

    when (targetOrientation) {
        "landscape" -> {
            when (targetResolution) {
                "hd" -> {
                    vdCons = buildVidCons(1280, 720)
                    frameRate = 30
                }
                "fhd" -> {
                    vdCons = buildVidCons(1920, 1080)
                    frameRate = 30
                }
                "qhd" -> {
                    vdCons = buildVidCons(2560, 1440)
                    frameRate = 30
                }
                "QnHD" -> {
                    vdCons = buildVidCons(960, 540)
                    frameRate = 30
                }
                else -> {
                    vdCons = buildVidCons(640, 480)
                    frameRate = 30
                }
            }
        }
        "neutral" -> {
            when (targetResolution) {
                "hd" -> {
                    vdCons = buildVidCons(1280, 720)
                    frameRate = 30
                }
                "fhd" -> {
                    vdCons = buildVidCons(1920, 1080)
                    frameRate = 30
                }
                "qhd" -> {
                    vdCons = buildVidCons(2560, 1440)
                    frameRate = 30
                }
                "QnHD" -> {
                    vdCons = buildVidCons(960, 540)
                    frameRate = 30
                }
                else -> {
                    vdCons = buildVidCons(640, 480)
                    frameRate = 30
                }
            }
        }
        else -> { // portrait
            when (targetResolution) {
                "hd" -> {
                    vdCons = buildVidCons(720, 1280)
                    frameRate = 30
                }
                "fhd" -> {
                    vdCons = buildVidCons(1080, 1920)
                    frameRate = 30
                }
                "qhd" -> {
                    vdCons = buildVidCons(1440, 2560)
                    frameRate = 30
                }
                "QnHD" -> {
                    vdCons = buildVidCons(540, 960)
                    frameRate = 30
                }
                else -> {
                    vdCons = buildVidCons(480, 640)
                    frameRate = 30
                }
            }
        }
    }

    return Pair(frameRate, vdCons)
}

private fun com.mediasfu.sdk.model.RtpCapabilities.toWebRtcCapabilities(): RtpCapabilities {
    val codecs = codecs.map { codec ->
        WebRtcRtpCodecCapability(
            kind = codec.kind.toWebRtc(),
            mimeType = codec.mimeType,
            preferredPayloadType = codec.preferredPayloadType,
            clockRate = codec.clockRate,
            channels = codec.channels,
            parameters = codec.parameters?.mapValues { (_, value) -> value?.toString() ?: "" } ?: emptyMap(),
            rtcpFeedback = codec.rtcpFeedback?.map { fb -> RtcpFeedback(fb.type, fb.parameter) } ?: emptyList()
        )
    }

    val headerExtensions = headerExtensions.map { extension ->
        RtpHeaderExtension(
            kind = extension.kind?.toWebRtc(),
            uri = extension.uri,
            preferredId = extension.preferredId,
            preferredEncrypt = extension.preferredEncrypt ?: false,
            direction = extension.direction?.toHeaderDirection()
        )
    }

    return RtpCapabilities(
        codecs = codecs,
        headerExtensions = headerExtensions,
        fecMechanisms = fecMechanisms
    )
}

private fun String?.toHeaderDirection(): RtpHeaderDirection? = when (this?.lowercase()) {
    "sendrecv" -> RtpHeaderDirection.SENDRECV
    "sendonly" -> RtpHeaderDirection.SENDONLY
    "recvonly" -> RtpHeaderDirection.RECVONLY
    "inactive" -> RtpHeaderDirection.INACTIVE
    else -> null
}

private fun com.mediasfu.sdk.model.MediaKind.toWebRtc(): WebRtcMediaKind = when (this) {
    com.mediasfu.sdk.model.MediaKind.AUDIO -> WebRtcMediaKind.AUDIO
    com.mediasfu.sdk.model.MediaKind.VIDEO -> WebRtcMediaKind.VIDEO
}

/**
 * Helper function to adjust bitrate for encoding configurations.
 */
private fun updateEncodingBitrates(producerOptions: ProducerOptionsType, targetResolution: String): ProducerOptionsType {
    val factor = when (targetResolution) {
        "hd" -> 4.0
        "fhd" -> 8.0
        "qhd" -> 16.0
        "QnHD" -> 0.25
        else -> 1.0
    }
    
    val updatedEncodings = producerOptions.encodings.map { encoding ->
        RtpEncodingParameters(
            rid = encoding.rid,
            maxBitrate = encoding.maxBitrate?.let { (it * factor).toInt() },
            minBitrate = encoding.minBitrate?.let { (it * factor).toInt() },
            maxFramerate = encoding.maxFramerate,
            scalabilityMode = encoding.scalabilityMode,
            scaleResolutionDownBy = encoding.scaleResolutionDownBy,
            dtx = encoding.dtx
            // NOTE: networkPriority deliberately excluded - not needed
        )
    }
    
    return ProducerOptionsType(encodings = updatedEncodings)
}
