package com.mediasfu.sdk.model

import com.mediasfu.sdk.consumers.CloseAndResizeOptions as ConsumerCloseAndResizeOptions
import com.mediasfu.sdk.consumers.CloseAndResizeParameters as ConsumerCloseAndResizeParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportAudioParameters as ConsumerDisconnectSendTransportAudioParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters as ConsumerDisconnectSendTransportScreenParameters
import com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions as ConsumerDisconnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.DisconnectSendTransportVideoParameters as ConsumerDisconnectSendTransportVideoParameters
import com.mediasfu.sdk.consumers.OnScreenChangesOptions as ConsumerOnScreenChangesOptions
import com.mediasfu.sdk.consumers.OnScreenChangesParameters as ConsumerOnScreenChangesParameters
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions as ConsumerPrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters as ConsumerPrepopulateUserMediaParameters
import com.mediasfu.sdk.consumers.ReUpdateInterOptions as ConsumerReUpdateInterOptions
import com.mediasfu.sdk.consumers.ReUpdateInterParameters as ConsumerReUpdateInterParameters
import com.mediasfu.sdk.consumers.ReorderStreamsOptions as ConsumerReorderStreamsOptions
import com.mediasfu.sdk.consumers.ReorderStreamsParameters as ConsumerReorderStreamsParameters
import com.mediasfu.sdk.consumers.StopShareScreenParameters as ConsumerStopShareScreenParameters
import com.mediasfu.sdk.consumers.StopShareScreenType as ConsumerStopShareScreenType
import com.mediasfu.sdk.consumers.StopShareScreenOptions as ConsumerStopShareScreenOptions
import com.mediasfu.sdk.webrtc.MediaStream
import kotlinx.coroutines.CancellationException
import com.mediasfu.sdk.socket.SocketManager

typealias CloseAndResizeParameters = ConsumerCloseAndResizeParameters
typealias PrepopulateUserMediaParameters = ConsumerPrepopulateUserMediaParameters
typealias ReorderStreamsParameters = ConsumerReorderStreamsParameters
typealias ReUpdateInterParameters = ConsumerReUpdateInterParameters
typealias OnScreenChangesParameters = ConsumerOnScreenChangesParameters

typealias CloseAndResizeOptions = ConsumerCloseAndResizeOptions
typealias PrepopulateUserMediaOptions = ConsumerPrepopulateUserMediaOptions
typealias ReorderStreamsOptions = ConsumerReorderStreamsOptions
typealias ReUpdateInterOptions = ConsumerReUpdateInterOptions
typealias DisconnectSendTransportScreenOptions = ConsumerDisconnectSendTransportScreenOptions
typealias StopShareScreenOptions = ConsumerStopShareScreenOptions
typealias OnScreenChangesOptions = ConsumerOnScreenChangesOptions
typealias StopShareScreenType = ConsumerStopShareScreenType

/**
 * Shared producer-related models and functional contracts mirroring the Flutter SDK.
 */

/** Abstraction for transports that can be closed asynchronously. */
interface ConsumerTransportLike {
    suspend fun close()
}

/** Abstraction for consumers that can be closed asynchronously. */
interface ConsumerLike {
    suspend fun close()
}

/** Minimal abstraction of a socket supporting emitWithAck semantics. */
interface SocketLike {
    fun emitWithAck(event: String, data: Map<String, Any?>, ack: (Map<String, Any?>) -> Unit)
    fun emit(event: String, data: Map<String, Any?>)
    val isConnected: Boolean
        get() = true
    val id: String?
        get() = null
}

/** Minimal abstraction around a media stream to toggle track activity in platform code. */
interface MediaStreamController {
    fun disableAudio()
    fun disableVideo()
}

/**
 * Representation of a transport entry used when wiring producer media handlers.
 */
data class TransportType(
    val producerId: String,
    val consumer: ConsumerLike,
    val serverConsumerTransportId: String,
    val consumerTransport: ConsumerTransportLike,
    val socket: Any? = null,
    val extra: MutableMap<String, Any?> = mutableMapOf()
) {
    operator fun get(key: String): Any? = when (key) {
        "consumerTransport" -> consumerTransport
        else -> extra[key]
    }
}

typealias CloseAndResizeType = suspend (CloseAndResizeOptions) -> Unit
typealias PrepopulateUserMediaType = suspend (PrepopulateUserMediaOptions) -> Unit
typealias ReorderStreamsType = suspend (ReorderStreamsOptions) -> Unit
typealias ReUpdateInterType = suspend (ReUpdateInterOptions) -> Unit
typealias ReInitiateRecordingType = suspend (ReInitiateRecordingOptions) -> Unit
typealias SoundPlayer = (SoundPlayerOptions) -> Unit
typealias RoomRecordParamsType = (RoomRecordParamsOptions) -> Unit
typealias ScreenProducerIdType = (ScreenProducerIdOptions) -> Unit
typealias StartRecordsType = suspend (StartRecordsOptions) -> Unit
typealias StoppedRecordingType = suspend (StoppedRecordingOptions) -> Unit
typealias TimeLeftRecordingType = (TimeLeftRecordingOptions) -> Unit
typealias MeetingTimeRemainingType = suspend (MeetingTimeRemainingOptions) -> Unit
typealias MeetingStillThereType = suspend (MeetingStillThereOptions) -> Unit
typealias MeetingEndedType = suspend (MeetingEndedOptions) -> Unit
typealias SleepType = suspend (SleepOptions) -> Unit
typealias OnScreenChangesType = suspend (OnScreenChangesOptions) -> Unit
typealias ConnectIpsType = suspend (ConnectIpsOptions) -> Pair<List<ConsumeSocket>, List<String>>
typealias ConnectLocalIpsType = suspend (ConnectLocalIpsOptions) -> Unit
typealias AllMembersType = suspend (AllMembersOptions) -> Unit
typealias AllMembersRestType = suspend (AllMembersRestOptions) -> Unit
typealias AllWaitingRoomMembersType = suspend (AllWaitingRoomMembersOptions) -> Unit
typealias DisconnectSendTransportVideoType = suspend (DisconnectSendTransportVideoOptions) -> Unit
typealias DisconnectSendTransportAudioType = suspend (DisconnectSendTransportAudioOptions) -> Unit
typealias DisconnectSendTransportScreenType = suspend (DisconnectSendTransportScreenOptions) -> Unit
typealias ControlMediaHostType = suspend (ControlMediaHostOptions) -> Unit
typealias BanParticipantType = suspend (BanParticipantOptions) -> Unit
typealias DisconnectType = suspend (DisconnectOptions) -> Unit
typealias DisconnectUserSelfType = suspend (DisconnectUserSelfOptions) -> Unit
typealias GetDomainsType = suspend (GetDomainsOptions) -> Unit

typealias ConsumeSocket = Map<String, Any?>

data class ScreenProducerIdOptions(
    val producerId: String,
    val screenId: String,
    val membersReceived: Boolean,
    val shareScreenStarted: Boolean,
    val deferScreenReceived: Boolean,
    val participants: List<Participant>,
    val updateScreenId: (String) -> Unit,
    val updateShareScreenStarted: (Boolean) -> Unit,
    val updateDeferScreenReceived: (Boolean) -> Unit
)

data class StartRecordsOptions(
    val roomName: String,
    val member: String,
    val socket: SocketLike?
)

data class StoppedRecordingOptions(
    val state: String,
    val reason: String,
    val showAlert: ShowAlert?
)

data class TimeLeftRecordingOptions(
    val timeLeft: Int,
    val showAlert: ShowAlert?
)

data class MeetingTimeRemainingOptions(
    val timeRemaining: Int,
    val eventType: EventType,
    val showAlert: ShowAlert?
)

data class MeetingStillThereOptions(
    val updateIsConfirmHereModalVisible: (Boolean) -> Unit
)

data class MeetingEndedOptions(
    val showAlert: ShowAlert?,
    val redirectUrl: String?,
    val onWeb: Boolean,
    val eventType: EventType,
    val updateValidated: ((Boolean) -> Unit)?
)

data class DisconnectOptions(
    val showAlert: ShowAlert?,
    val redirectUrl: String?,
    val onWeb: Boolean,
    val updateValidated: ((Boolean) -> Unit)?
)

data class DisconnectUserSelfOptions(
    val member: String,
    val roomName: String,
    val socket: SocketLike?,
    val localSocket: SocketLike?
)

data class GetDomainsOptions(
    val domains: List<String>,
    val altDomains: AltDomains,
    val apiUserName: String,
    val apiKey: String?,
    val apiToken: String,
    val parameters: GetDomainsParameters
)

interface GetDomainsParameters : ConnectIpsParameters {
    override fun getUpdatedAllParams(): GetDomainsParameters
}

data class HostRequestResponseOptions(
    val requestResponse: RequestResponse,
    val showAlert: ShowAlert?,
    val requestList: List<Request>,
    val updateRequestList: (List<Request>) -> Unit,
    val updateMicAction: (Boolean) -> Unit,
    val updateVideoAction: (Boolean) -> Unit,
    val updateScreenAction: (Boolean) -> Unit,
    val updateChatAction: (Boolean) -> Unit,
    val updateAudioRequestState: (String) -> Unit,
    val updateVideoRequestState: (String) -> Unit,
    val updateScreenRequestState: (String) -> Unit,
    val updateChatRequestState: (String) -> Unit,
    val updateAudioRequestTime: (Long?) -> Unit,
    val updateVideoRequestTime: (Long?) -> Unit,
    val updateScreenRequestTime: (Long?) -> Unit,
    val updateChatRequestTime: (Long?) -> Unit,
    val updateRequestIntervalSeconds: Int
)

data class ParticipantRequestedOptions(
    val userRequest: Request,
    val requestList: List<Request>,
    val waitingRoomList: List<WaitingRoomParticipant>,
    val updateTotalReqWait: (Int) -> Unit,
    val updateRequestList: (List<Request>) -> Unit
)

data class UserWaitingOptions(
    val name: String,
    val showAlert: ShowAlert?,
    val totalReqWait: Int,
    val updateTotalReqWait: (Int) -> Unit
)

data class ReceiveMessageOptions(
    val message: Message,
    val messages: List<Message>,
    val participantsAll: List<Participant>,
    val member: String,
    val eventType: EventType,
    val isLevel: String,
    val coHost: String,
    val updateMessages: (List<Message>) -> Unit,
    val updateShowMessagesBadge: (Boolean) -> Unit
)

data class SleepOptions(val ms: Int)

data class ConnectIpsOptions(
    val consumeSockets: List<ConsumeSocket>,
    val remoteIps: List<String>,
    val apiUserName: String,
    val apiKey: String?,
    val apiToken: String,
    val parameters: ConnectIpsParameters
)

data class ConnectLocalIpsOptions(
    val socket: SocketManager?,
    val parameters: ConnectLocalIpsParameters
)

data class AllMembersOptions(
    val members: List<Participant>,
    val requests: List<Request>,
    val settings: List<String>,
    val coHost: String,
    val coHostRes: List<CoHostResponsibility>,
    val parameters: AllMembersParameters,
    val consumeSockets: List<ConsumeSocket>,
    val apiUserName: String,
    val apiKey: String?,
    val apiToken: String
)

data class AllMembersRestOptions(
    val members: List<Participant>,
    val settings: List<String>,
    val coHost: String,
    val coHostRes: List<CoHostResponsibility>,
    val parameters: AllMembersRestParameters,
    val consumeSockets: List<ConsumeSocket>,
    val apiUserName: String,
    val apiKey: String?,
    val apiToken: String
)

data class AllWaitingRoomMembersOptions(
    val waitingParticipants: List<WaitingRoomParticipant>,
    val updateWaitingRoomList: (List<WaitingRoomParticipant>) -> Unit,
    val updateTotalReqWait: (Int) -> Unit
)

data class BanParticipantOptions(
    val name: String,
    val parameters: BanParticipantParameters
)

data class DisconnectSendTransportVideoOptions(
    val parameters: DisconnectSendTransportVideoParameters
)

data class DisconnectSendTransportAudioOptions(
    val parameters: DisconnectSendTransportAudioParameters
)

data class ControlMediaHostOptions(
    val type: String,
    val parameters: ControlMediaHostParameters
)

interface ConnectLocalIpsParameters {
    val socket: SocketManager?
}

interface ConnectIpsParameters : ReorderStreamsParameters {
    val consumeSockets: List<ConsumeSocket>
    val roomRecvIps: List<String>
    val updateRoomRecvIps: (List<String>) -> Unit
    val updateConsumeSockets: (List<ConsumeSocket>) -> Unit
    val reorderStreams: ReorderStreamsType
    val connectIps: ConnectIpsType
}

interface AllMembersParameters :
    OnScreenChangesParameters,
    ConnectIpsParameters,
    ConnectLocalIpsParameters,
    ReorderStreamsParameters {

    val participantsAll: List<Participant>
    override val participants: List<Participant>
    val dispActiveNames: List<String>
    val requestList: List<Request>
    val coHost: String
    val coHostResponsibility: List<CoHostResponsibility>
    val lockScreen: Boolean
    val firstAll: Boolean
    val membersReceived: Boolean
    val deferScreenReceived: Boolean
    override val screenId: String
    override val shareScreenStarted: Boolean
    val meetingDisplayType: String
    val audioSetting: String
    val videoSetting: String
    val screenshareSetting: String
    val chatSetting: String
    val hostFirstSwitch: Boolean
    val waitingRoomList: List<WaitingRoomParticipant>
    val isLevel: String

    val updateParticipantsAll: (List<Participant>) -> Unit
    val updateParticipants: (List<Participant>) -> Unit
    val updateRequestList: (List<Request>) -> Unit
    val updateCoHost: (String) -> Unit
    val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit
    val updateFirstAll: (Boolean) -> Unit
    val updateMembersReceived: (Boolean) -> Unit
    val updateDeferScreenReceived: (Boolean) -> Unit
    val updateShareScreenStarted: (Boolean) -> Unit
    val updateAudioSetting: (String) -> Unit
    val updateVideoSetting: (String) -> Unit
    val updateScreenshareSetting: (String) -> Unit
    val updateChatSetting: (String) -> Unit
    val updateIsLoadingModalVisible: (Boolean) -> Unit
    val updateTotalReqWait: (Int) -> Unit
    val updateHostFirstSwitch: (Boolean) -> Unit

    override val connectIps: ConnectIpsType
    val connectLocalIps: ConnectLocalIpsType?
    val onScreenChanges: OnScreenChangesType
    val sleep: SleepType

    override fun getUpdatedAllParams(): AllMembersParameters
}

/**
 * Contract describing the dependencies required by the producer media closed handler.
 */
interface ProducerMediaClosedParameters :
    CloseAndResizeParameters,
    PrepopulateUserMediaParameters,
    ReorderStreamsParameters {

    val consumerTransports: List<TransportType>
    override val hostLabel: String
    override val shared: Boolean

    val updateConsumerTransports: (List<TransportType>) -> Unit
    val updateShared: (Boolean) -> Unit
    override val updateShareScreenStarted: (Boolean) -> Unit
    val updateScreenId: (String) -> Unit
    override val updateShareEnded: (Boolean) -> Unit

    val closeAndResize: CloseAndResizeType
    override val prepopulateUserMedia: PrepopulateUserMediaType
    override val reorderStreams: ReorderStreamsType

    override fun getUpdatedAllParams(): ProducerMediaClosedParameters
}

data class ProducerMediaClosedOptions(
    val producerId: String,
    val kind: String,
    val parameters: ProducerMediaClosedParameters
)

data class ProducerMediaPausedOptions(
    val producerId: String,
    val kind: String,
    val name: String,
    val parameters: ProducerMediaPausedParameters
)

interface ProducerMediaPausedParameters :
    PrepopulateUserMediaParameters,
    ReorderStreamsParameters,
    ReUpdateInterParameters {

    override val activeSounds: List<String>
    val meetingDisplayType: String
    val meetingVideoOptimized: Boolean
    override val participants: List<Participant>
    override val oldSoundIds: List<String>
    override val shared: Boolean
    override val shareScreenStarted: Boolean
    override val updateMainWindow: Boolean
    val hostLabel: String
    val level: String

    val updateActiveSounds: (List<String>) -> Unit
    override val updateUpdateMainWindow: (Boolean) -> Unit

    override val reorderStreams: ReorderStreamsType
    val prepopulateUserMedia: PrepopulateUserMediaType
    val reUpdateInter: ReUpdateInterType

    override fun getUpdatedAllParams(): ProducerMediaPausedParameters
}

data class ProducerMediaResumedOptions(
    val name: String,
    val kind: String,
    val parameters: ProducerMediaResumedParameters
)

interface ProducerMediaResumedParameters :
    PrepopulateUserMediaParameters,
    ReorderStreamsParameters {

    val meetingDisplayType: String
    override val participants: List<Participant>
    override val shared: Boolean
    override val shareScreenStarted: Boolean
    override val mainScreenFilled: Boolean
    val hostLabel: String

    override val updateUpdateMainWindow: (Boolean) -> Unit

    val reorderStreams: ReorderStreamsType
    val prepopulateUserMedia: PrepopulateUserMediaType

    override fun getUpdatedAllParams(): ProducerMediaResumedParameters
}

internal suspend inline fun runSuspendingIgnoringCancellation(crossinline block: suspend () -> Unit) {
    try {
        block()
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
    }
}

data class ReInitiateRecordingOptions(
    val roomName: String,
    val member: String,
    val socket: SocketLike?,
    val adminRestrictSetting: Boolean = false
)

data class SoundPlayerOptions(
    val soundUrl: String
)

fun formatElapsedTime(recordElapsedTime: Int): String {
    val hours = recordElapsedTime / 3600
    val minutes = (recordElapsedTime % 3600) / 60
    val seconds = recordElapsedTime % 60
    return "${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

data class RecordingNoticeOptions(
    val state: String,
    val userRecordingParams: UserRecordingParams?,
    val pauseCount: Int,
    val timeDone: Int,
    val parameters: RecordingNoticeParameters
)

interface RecordingNoticeParameters {
    val isLevel: String
    val userRecordingParams: UserRecordingParams
    val recordElapsedTime: Int
    val recordStartTime: Long?
    val recordStarted: Boolean
    val recordPaused: Boolean
    val canLaunchRecord: Boolean
    val recordStopped: Boolean
    val isTimerRunning: Boolean
    val canPauseResume: Boolean
    val eventType: EventType

    val updateRecordingProgressTime: (String) -> Unit
    val updateShowRecordButtons: (Boolean) -> Unit
    val updateUserRecordingParams: (UserRecordingParams) -> Unit
    val updateRecordingMediaOptions: (String) -> Unit
    val updateRecordingAudioOptions: (String) -> Unit
    val updateRecordingVideoOptions: (String) -> Unit
    val updateRecordingVideoType: (String) -> Unit
    val updateRecordingVideoOptimized: (Boolean) -> Unit
    val updateRecordingDisplayType: (String) -> Unit
    val updateRecordingAddHls: (Boolean) -> Unit
    val updateRecordingNameTags: (Boolean) -> Unit
    val updateRecordingBackgroundColor: (String) -> Unit
    val updateRecordingNameTagsColor: (String) -> Unit
    val updateRecordingOrientationVideo: (String) -> Unit
    val updateRecordingAddText: (Boolean) -> Unit
    val updateRecordingCustomText: (String) -> Unit
    val updateRecordingCustomTextPosition: (String) -> Unit
    val updateRecordingCustomTextColor: (String) -> Unit
    val updatePauseRecordCount: (Int) -> Unit
    val updateRecordElapsedTime: (Int) -> Unit
    val updateRecordStartTime: (Long?) -> Unit
    val updateRecordStarted: (Boolean) -> Unit
    val updateRecordPaused: (Boolean) -> Unit
    val updateCanLaunchRecord: (Boolean) -> Unit
    val updateRecordStopped: (Boolean) -> Unit
    val updateIsTimerRunning: (Boolean) -> Unit
    val updateCanPauseResume: (Boolean) -> Unit
    val updateRecordState: (String) -> Unit

    val playSound: SoundPlayer
    val currentTimeProvider: () -> Long
    fun getUpdatedAllParams(): RecordingNoticeParameters
}

data class RoomRecordParamsOptions(
    val recordParams: RecordingParams,
    val parameters: RoomRecordParamsParameters
)

interface RoomRecordParamsParameters {
    val updateRecordingAudioPausesLimit: (Int) -> Unit
    val updateRecordingAudioPausesCount: (Int) -> Unit
    val updateRecordingAudioSupport: (Boolean) -> Unit
    val updateRecordingAudioPeopleLimit: (Int) -> Unit
    val updateRecordingAudioParticipantsTimeLimit: (Int) -> Unit
    val updateRecordingVideoPausesCount: (Int) -> Unit
    val updateRecordingVideoPausesLimit: (Int) -> Unit
    val updateRecordingVideoSupport: (Boolean) -> Unit
    val updateRecordingVideoPeopleLimit: (Int) -> Unit
    val updateRecordingVideoParticipantsTimeLimit: (Int) -> Unit
    val updateRecordingAllParticipantsSupport: (Boolean) -> Unit
    val updateRecordingVideoParticipantsSupport: (Boolean) -> Unit
    val updateRecordingAllParticipantsFullRoomSupport: (Boolean) -> Unit
    val updateRecordingVideoParticipantsFullRoomSupport: (Boolean) -> Unit
    val updateRecordingPreferredOrientation: (String) -> Unit
    val updateRecordingSupportForOtherOrientation: (Boolean) -> Unit
    val updateRecordingMultiFormatsSupport: (Boolean) -> Unit
}

interface AllMembersRestParameters : AllMembersParameters {
    override fun getUpdatedAllParams(): AllMembersRestParameters
}

interface BanParticipantParameters : ReorderStreamsParameters {
    val activeNames: List<String>
    val dispActiveNames: List<String>
    override val participants: List<Participant>

    val updateParticipants: (List<Participant>) -> Unit
    val reorderStreams: ReorderStreamsType

    override fun getUpdatedAllParams(): BanParticipantParameters
}

typealias StopShareScreenParameters = com.mediasfu.sdk.consumers.StopShareScreenParameters

typealias DisconnectSendTransportVideoParameters = com.mediasfu.sdk.consumers.DisconnectSendTransportVideoParameters

typealias DisconnectSendTransportAudioParameters = com.mediasfu.sdk.consumers.DisconnectSendTransportAudioParameters

typealias DisconnectSendTransportScreenParameters = com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters

interface ControlMediaHostParameters :
    OnScreenChangesParameters,
    StopShareScreenParameters,
    DisconnectSendTransportVideoParameters,
    DisconnectSendTransportAudioParameters,
    DisconnectSendTransportScreenParameters {

    val localStream: MediaStream?
    val updateLocalStream: (MediaStream?) -> Unit
    override val localStreamVideo: MediaStream?
    val updateLocalStreamVideo: (MediaStream?) -> Unit
    override val localStreamScreen: MediaStream?
    override val updateLocalStreamScreen: (MediaStream?) -> Unit

    val updateAdminRestrictSetting: (Boolean) -> Unit
    val updateAudioAlreadyOn: (Boolean) -> Unit
    val updateScreenAlreadyOn: (Boolean) -> Unit
    val updateVideoAlreadyOn: (Boolean) -> Unit
    val updateChatAlreadyOn: (Boolean) -> Unit

    val onScreenChanges: OnScreenChangesType
    val stopShareScreen: StopShareScreenType
    val disconnectSendTransportVideo: DisconnectSendTransportVideoType
    val disconnectSendTransportAudio: DisconnectSendTransportAudioType
    override val disconnectSendTransportScreen: DisconnectSendTransportScreenType

    override fun getUpdatedAllParams(): ControlMediaHostParameters
}

/** Kotlin replica of PersonJoinedOptions from mediasfu_sdk/lib/producers/socket_receive_methods/person_joined.dart. */
data class PersonJoinedOptions(
    val name: String,
    val showAlert: ShowAlert? = null
)

/** Kotlin replica of UpdatedCoHostOptions from mediasfu_sdk/lib/producers/socket_receive_methods/updated_co_host.dart. */
data class UpdatedCoHostOptions(
    val coHost: String,
    val coHostResponsibility: List<CoHostResponsibility>,
    val showAlert: ShowAlert? = null,
    val eventType: EventType,
    val islevel: String,
    val member: String,
    val youAreCoHost: Boolean,
    val updateCoHost: (String) -> Unit,
    val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit,
    val updateYouAreCoHost: (Boolean) -> Unit
)

/** Kotlin replica of UpdateMediaSettingsOptions from mediasfu_sdk/lib/producers/socket_receive_methods/update_media_settings.dart. */
data class UpdateMediaSettingsOptions(
    val settings: List<String>,
    val updateAudioSetting: (String) -> Unit,
    val updateVideoSetting: (String) -> Unit,
    val updateScreenshareSetting: (String) -> Unit,
    val updateChatSetting: (String) -> Unit
)

/** Kotlin replica of UpdateConsumingDomainsParameters from mediasfu_sdk/lib/producers/socket_receive_methods/update_consuming_domains.dart. */
interface UpdateConsumingDomainsParameters : ConnectIpsParameters, GetDomainsParameters {
    override val participants: List<Participant>
    val getDomains: GetDomainsType
    override fun getUpdatedAllParams(): UpdateConsumingDomainsParameters
}

/** Kotlin replica of UpdateConsumingDomainsOptions from mediasfu_sdk/lib/producers/socket_receive_methods/update_consuming_domains.dart. */
data class UpdateConsumingDomainsOptions(
    val domains: List<String>,
    val altDomains: AltDomains,
    val apiUserName: String,
    val apiKey: String,
    val apiToken: String,
    val parameters: UpdateConsumingDomainsParameters
)