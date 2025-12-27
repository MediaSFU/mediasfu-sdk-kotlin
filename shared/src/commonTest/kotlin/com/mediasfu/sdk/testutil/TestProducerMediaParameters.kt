package com.mediasfu.sdk.testutil

import com.mediasfu.sdk.consumers.AutoAdjustOptions
import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.consumers.CompareActiveNamesOptions
import com.mediasfu.sdk.consumers.CompareScreenStatesOptions
import com.mediasfu.sdk.consumers.GetVideosOptions
import com.mediasfu.sdk.consumers.OnScreenChangesOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.ReUpdateInterOptions
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.TriggerOptions
import com.mediasfu.sdk.model.CloseAndResizeOptions
import com.mediasfu.sdk.model.CloseAndResizeType
import com.mediasfu.sdk.model.PrepopulateUserMediaType
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ProducerMediaClosedParameters
import com.mediasfu.sdk.model.ProducerMediaPausedParameters
import com.mediasfu.sdk.model.ProducerMediaResumedParameters
import com.mediasfu.sdk.model.ReUpdateInterType
import com.mediasfu.sdk.model.ReorderStreamsType
import com.mediasfu.sdk.consumers.ScreenState
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.TransportType as ModelTransportType
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.webrtc.MediaStream

/**
 * Test double for [ProducerMediaClosedParameters] with extensive state tracking.
 */
class TestProducerMediaClosedParameters(
    transports: List<ModelTransportType> = emptyList(),
    hostLabel: String = "Host",
    sharedInitial: Boolean = true,
    eventType: EventType = EventType.CONFERENCE
) : ProducerMediaClosedParameters {

    private val base = TestControlMediaHostParameters(eventType = eventType)

    private var consumerTransportsValue = transports.toMutableList()
    private var allAudioStreamsValue: List<Stream> = emptyList()
    private var activeNamesValue: List<String> = emptyList()
    private var prevActiveNamesValue: List<String> = emptyList()
    private var streamNamesValue: List<Stream> = emptyList()
    private var recordingDisplayTypeValue: String = "grid"
    private var recordingVideoOptimizedValue: Boolean = false
    private var meetingDisplayTypeValue: String = "media"
    private var gotAllVidsValue: Boolean = false
    private var recordStartedValue: Boolean = false
    private var recordStoppedValue: Boolean = false
    private var recordPausedValue: Boolean = false
    private var recordResumedValue: Boolean = false
    private var screenStatesValue: List<Any> = emptyList()
    private var prevScreenStatesValue: List<ScreenState> = emptyList()
    private var updateDateStateValue: Int? = null
    private var lastUpdateValue: Int? = null
    private var nForReadjustValue: Int? = null

    val updatedTransports = mutableListOf<List<ModelTransportType>>()
    val closeAndResizeCalls = mutableListOf<CloseAndResizeOptions>()
    val prepopulateCalls = mutableListOf<PrepopulateUserMediaOptions>()
    val reorderCalls = mutableListOf<ReorderStreamsOptions>()
    val compareActiveNamesCalls = mutableListOf<CompareActiveNamesOptions>()
    val compareScreenStatesCalls = mutableListOf<CompareScreenStatesOptions>()
    val triggerCalls = mutableListOf<TriggerOptions>()
    val autoAdjustCalls = mutableListOf<AutoAdjustOptions>()
    val getVideosCalls = mutableListOf<GetVideosOptions>()
    val rePortCalls = mutableListOf<RePortOptions>()

    init {
        base.assignHostLabel(hostLabel)
        base.updateShared(sharedInitial)
    }

    var shareScreenStartedValue: Boolean
        get() = base.shareScreenStarted
        set(value) {
            base.updateShareScreenStarted(value)
        }

    var screenIdValue: String
        get() = base.screenId ?: ""
        set(value) {
            base.updateScreenId(value)
        }

    var shareEndedValue: Boolean
        get() = base.shareEnded
        set(value) {
            base.updateShareEnded(value)
        }

    override val consumerTransports: List<ModelTransportType>
        get() = consumerTransportsValue

    override val hostLabel: String
        get() = base.hostLabel

    override val shared: Boolean
        get() = base.shared

    override val updateConsumerTransports: (List<ModelTransportType>) -> Unit = { updated ->
        consumerTransportsValue = updated.toMutableList()
        updatedTransports += updated
    }

    override val updateShared: (Boolean) -> Unit = { value ->
        base.updateShared(value)
    }

    override val updateScreenId: (String) -> Unit = { id ->
        base.updateScreenId(id)
    }

    override val updateShareScreenStarted: (Boolean) -> Unit = { value ->
        base.updateShareScreenStarted(value)
    }

    override val updateShareEnded: (Boolean) -> Unit = { value ->
        base.updateShareEnded(value)
    }

    override val closeAndResize: CloseAndResizeType = { options ->
        closeAndResizeCalls += options
    }

    override val prepopulateUserMedia: PrepopulateUserMediaType = { options ->
        prepopulateCalls += options
        base.prepopulateUserMedia(options)
    }

    override val reorderStreams: ReorderStreamsType = { options ->
        reorderCalls += options
        base.reorderStreams(options)
    }

    override val participants: List<Participant>
        get() = base.participants

    override val allVideoStreams: List<Stream>
        get() = base.allVideoStreams

    override val islevel: String
        get() = base.islevel

    override val member: String
        get() = base.member

    override val shareScreenStarted: Boolean
        get() = base.shareScreenStarted

    override val eventType: EventType
        get() = base.eventType

    override val screenId: String
        get() = base.screenId ?: ""

    override val forceFullDisplay: Boolean
        get() = base.forceFullDisplay

    override val socket: SocketManager?
        get() = base.socket

    override val localSocket: SocketManager?
        get() = base.localSocket

    override val updateMainWindow: Boolean
        get() = base.updateMainWindow

    override val mainScreenFilled: Boolean
        get() = base.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = base.adminOnMainScreen

    override val mainScreenPerson: String
        get() = base.mainScreenPerson

    override val videoAlreadyOn: Boolean
        get() = base.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = base.audioAlreadyOn

    override val oldAllStreams: List<Stream>
        get() = base.oldAllStreams

    override val screenForceFullDisplay: Boolean
        get() = base.screenForceFullDisplay

    override val localStreamScreen: MediaStream?
        get() = base.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = base.remoteScreenStream

    override val localStreamVideo: MediaStream?
        get() = base.localStreamVideo

    override val mainHeightWidth: Double
        get() = base.mainHeightWidth

    override val isWideScreen: Boolean
        get() = base.isWideScreen

    override val localUIMode: Boolean
        get() = base.localUIMode

    override val whiteboardStarted: Boolean
        get() = base.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = base.whiteboardEnded

    override val virtualStream: Any?
        get() = base.virtualStream

    override val keepBackground: Boolean
        get() = base.keepBackground

    override val annotateScreenStream: Boolean
        get() = base.annotateScreenStream

    override val updateMainScreenPerson: (String) -> Unit = base.updateMainScreenPerson

    override val updateMainScreenFilled: (Boolean) -> Unit = base.updateMainScreenFilled

    override val updateAdminOnMainScreen: (Boolean) -> Unit = base.updateAdminOnMainScreen

    override val updateMainHeightWidth: (Double) -> Unit = base.updateMainHeightWidth

    override val updateScreenForceFullDisplay: (Boolean) -> Unit = base.updateScreenForceFullDisplay

    override val updateUpdateMainWindow: (Boolean) -> Unit = base.updateUpdateMainWindow

    override val updateShowAlert: (ShowAlert?) -> Unit = base.updateShowAlert

    override val allAudioStreams: List<Stream>
        get() = allAudioStreamsValue

    override val activeNames: List<String>
        get() = activeNamesValue

    override val streamNames: List<Stream>
        get() = streamNamesValue

    override val recordingDisplayType: String
        get() = recordingDisplayTypeValue

    override val recordingVideoOptimized: Boolean
        get() = recordingVideoOptimizedValue

    override val adminVidID: String
        get() = base.adminVidID

    override val adminIDStream: String
        get() = base.adminIDStream

    override val newLimitedStreams: List<Stream>
        get() = base.newLimitedStreams

    override val newLimitedStreamsIDs: List<String>
        get() = base.newLimitedStreamsIDs

    override val meetingDisplayType: String
        get() = meetingDisplayTypeValue

    override val deferReceive: Boolean
        get() = base.deferReceive

    override val lockScreen: Boolean
        get() = base.lockScreen

    override val firstAll: Boolean
        get() = base.firstAll

    override val firstRound: Boolean
        get() = base.firstRound

    override val gotAllVids: Boolean
        get() = gotAllVidsValue

    override val shareEnded: Boolean
        get() = base.shareEnded

    override val updateActiveNames: (List<String>) -> Unit = { names ->
        activeNamesValue = names
    }

    override val updateAllVideoStreams: (List<Stream>) -> Unit = { streams ->
        base.updateAllVideoStreams(streams)
    }

    override val updateAllAudioStreams: (List<Stream>) -> Unit = { streams ->
        allAudioStreamsValue = streams
    }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit = { streams ->
        base.updateNewLimitedStreams(streams)
    }

    override val updateOldAllStreams: (List<Stream>) -> Unit = { streams ->
        base.updateOldAllStreams(streams)
    }

    override val updateDeferReceive: (Boolean) -> Unit = { value ->
        base.updateDeferReceive(value)
    }

    override val updateLockScreen: (Boolean) -> Unit = { value ->
        base.updateLockScreen(value)
    }

    override val updateFirstAll: (Boolean) -> Unit = { value ->
        base.updateFirstAll(value)
    }

    override val updateFirstRound: (Boolean) -> Unit = { value ->
        base.updateFirstRound(value)
    }

    override val getVideos: suspend (GetVideosOptions) -> Unit = { options ->
        getVideosCalls += options
    }

    override val rePort: suspend (RePortOptions) -> Unit = { options ->
        rePortCalls += options
    }

    override val activeSounds: List<String>
        get() = base.activeSounds

    override val screenShareIDStream: String
        get() = base.screenShareIDStream

    override val screenShareNameStream: String
        get() = base.screenShareNameStream

    override val adminNameStream: String
        get() = base.adminNameStream

    override fun updateAllVideoStreams(streams: List<Stream>) {
        base.updateAllVideoStreams(streams)
    }

    override fun updateParticipants(participants: List<Participant>) {
        base.updateParticipants(participants)
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        base.updateOldAllStreams(streams)
    }

    override fun updateScreenId(id: String) {
        base.updateScreenId(id)
    }

    override fun updateAdminVidID(id: String) {
        base.updateAdminVidID(id)
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        base.updateNewLimitedStreams(streams)
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        base.updateNewLimitedStreamsIDs(ids)
    }

    override fun updateActiveSounds(sounds: List<String>) {
        base.updateActiveSounds(sounds)
    }

    override fun updateScreenShareIDStream(id: String) {
        base.updateScreenShareIDStream(id)
    }

    override fun updateScreenShareNameStream(name: String) {
        base.updateScreenShareNameStream(name)
    }

    override fun updateAdminIDStream(id: String) {
        base.updateAdminIDStream(id)
    }

    override fun updateAdminNameStream(name: String) {
        base.updateAdminNameStream(name)
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        base.updateYouYouStream(streams)
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        base.updateYouYouStreamIDs(ids)
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        base.updateYouYouStreamIDs(ids)
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        base.updateYouYouStreamIDs(ids)
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> = base.changeVids(options)

    override val recordStarted: Boolean
        get() = recordStartedValue

    override val recordStopped: Boolean
        get() = recordStoppedValue

    override val recordPaused: Boolean
        get() = recordPausedValue

    override val recordResumed: Boolean
        get() = recordResumedValue

    override val screenStates: List<Any>
        get() = screenStatesValue

    override val prevScreenStates: List<ScreenState>
        get() = prevScreenStatesValue

    override val updateScreenStates: (List<ScreenState>) -> Unit = { screens ->
        screenStatesValue = screens
    }

    override val updatePrevScreenStates: (List<ScreenState>) -> Unit = { screens ->
        prevScreenStatesValue = screens
    }

    override val compareActiveNames: suspend (CompareActiveNamesOptions) -> Unit = { options ->
        compareActiveNamesCalls += options
    }

    override val compareScreenStates: suspend (CompareScreenStatesOptions) -> Unit = { options ->
        compareScreenStatesCalls += options
    }

    override val trigger: suspend (TriggerOptions) -> Unit = { options ->
        triggerCalls += options
    }

    override val prevActiveNames: List<String>
        get() = prevActiveNamesValue

    override val updatePrevActiveNames: (List<String>) -> Unit = { names ->
        prevActiveNamesValue = names
    }

    override val roomName: String
        get() = base.roomName

    override val updateDateState: Int?
        get() = updateDateStateValue

    override val lastUpdate: Int?
        get() = lastUpdateValue

    override val nForReadjust: Int?
        get() = nForReadjustValue

    override fun updateUpdateDateState(timestamp: Int?) {
        updateDateStateValue = timestamp
    }

    override fun updateLastUpdate(lastUpdate: Int?) {
        lastUpdateValue = lastUpdate
    }

    override fun updateNForReadjust(nForReadjust: Int) {
        nForReadjustValue = nForReadjust
    }

    override suspend fun autoAdjust(options: AutoAdjustOptions): Result<List<Int>> {
        autoAdjustCalls += options
        return Result.success(emptyList())
    }

    override val showAlert: ShowAlert?
        get() = base.showAlert

    override fun getUpdatedAllParams(): ProducerMediaClosedParameters = this

    fun updateRecordingState(
        started: Boolean = recordStartedValue,
        stopped: Boolean = recordStoppedValue,
        paused: Boolean = recordPausedValue,
        resumed: Boolean = recordResumedValue
    ) {
        recordStartedValue = started
        recordStoppedValue = stopped
        recordPausedValue = paused
        recordResumedValue = resumed
    }

    fun updateStreamNames(streams: List<Stream>) {
        streamNamesValue = streams
    }

    fun updateRecordingDisplay(
        displayType: String = recordingDisplayTypeValue,
        videoOptimized: Boolean = recordingVideoOptimizedValue
    ) {
        recordingDisplayTypeValue = displayType
        recordingVideoOptimizedValue = videoOptimized
    }

    fun updateMeetingDisplayType(value: String) {
        meetingDisplayTypeValue = value
    }

    fun updateGotAllVids(value: Boolean) {
        gotAllVidsValue = value
    }
}

/**
 * Test double for [ProducerMediaPausedParameters] with delegate wiring.
 */
class TestProducerMediaPausedParameters(
    participants: List<Participant>,
    activeSounds: List<String> = emptyList(),
    meetingDisplayType: String = "media",
    meetingVideoOptimized: Boolean = false,
    oldSoundIds: List<String> = emptyList(),
    sharedInitial: Boolean = false,
    shareScreenStartedInitial: Boolean = false,
    updateMainWindowInitial: Boolean = false,
    hostLabel: String = "Host",
    level: String = "1",
    eventType: EventType = EventType.CONFERENCE,
    screenPageLimit: Int = 6,
    itemPageLimit: Int = 6,
    reorderInterval: Int = 10_000,
    fastReorderInterval: Int = 5_000,
    sortAudioLoudnessInitial: Boolean = false,
    lastReorderTimeInitial: Int = 0
) : ProducerMediaPausedParameters {

    private val base = TestControlMediaHostParameters(eventType = eventType)

    private var meetingDisplayTypeValue = meetingDisplayType
    private var meetingVideoOptimizedValue = meetingVideoOptimized
    private var levelValue = level
    private var screenPageLimitValue = screenPageLimit
    private var itemPageLimitValue = itemPageLimit
    private var reorderIntervalValue = reorderInterval
    private var fastReorderIntervalValue = fastReorderInterval
    private var sortAudioLoudnessValue = sortAudioLoudnessInitial
    private var lastReorderTimeValue = lastReorderTimeInitial
    private var oldSoundIdsValue: List<String> = oldSoundIds.toList()

    val updateActiveSoundsCalls = mutableListOf<List<String>>()
    val updateMainWindowCalls = mutableListOf<Boolean>()
    val prepopulateCalls = mutableListOf<PrepopulateUserMediaOptions>()
    val reorderCalls = mutableListOf<ReorderStreamsOptions>()
    val reUpdateCalls = mutableListOf<ReUpdateInterOptions>()
    val changeVidsCalls = mutableListOf<ChangeVidsOptions>()
    val updateSortAudioLoudnessCalls = mutableListOf<Boolean>()
    val updateLastReorderTimeCalls = mutableListOf<Int>()
    val updateOldSoundIdsCalls = mutableListOf<List<String>>()
    val updateAddForBasicCalls = mutableListOf<Boolean>()
    val updateItemPageLimitCalls = mutableListOf<Int>()
    val onScreenChangesCalls = mutableListOf<OnScreenChangesOptions>()

    init {
        base.assignHostLabel(hostLabel)
        base.updateParticipants(participants)
        base.updateActiveSounds(activeSounds)
        base.updateShared(sharedInitial)
        base.updateShareScreenStarted(shareScreenStartedInitial)
        base.updateUpdateMainWindow(updateMainWindowInitial)
        base.updateItemPageLimit(itemPageLimitValue)
    }

    override val participants: List<Participant>
        get() = base.participants

    override val allVideoStreams: List<Stream>
        get() = base.allVideoStreams

    override val activeSounds: List<String>
        get() = base.activeSounds

    override val oldAllStreams: List<Stream>
        get() = base.oldAllStreams

    override val screenId: String
        get() = base.screenId ?: ""

    override val adminVidID: String
        get() = base.adminVidID

    override val newLimitedStreams: List<Stream>
        get() = base.newLimitedStreams

    override val newLimitedStreamsIDs: List<String>
        get() = base.newLimitedStreamsIDs

    override val screenShareIDStream: String
        get() = base.screenShareIDStream

    override val screenShareNameStream: String
        get() = base.screenShareNameStream

    override val adminIDStream: String
        get() = base.adminIDStream

    override val adminNameStream: String
        get() = base.adminNameStream

    override val socket: SocketManager?
        get() = base.socket

    override val localSocket: SocketManager?
        get() = base.localSocket

    override val member: String
        get() = base.member

    override val forceFullDisplay: Boolean
        get() = base.forceFullDisplay

    override val mainScreenFilled: Boolean
        get() = base.mainScreenFilled

    override val adminOnMainScreen: Boolean
        get() = base.adminOnMainScreen

    override val mainScreenPerson: String
        get() = base.mainScreenPerson

    override val mainHeightWidth: Double
        get() = base.mainHeightWidth

    override val isWideScreen: Boolean
        get() = base.isWideScreen

    override val localUIMode: Boolean
        get() = base.localUIMode

    override val videoAlreadyOn: Boolean
        get() = base.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = base.audioAlreadyOn

    override val screenForceFullDisplay: Boolean
        get() = base.screenForceFullDisplay

    override val remoteScreenStream: List<Stream>
        get() = base.remoteScreenStream

    override val localStreamVideo: MediaStream?
        get() = base.localStreamVideo

    override val localStreamScreen: MediaStream?
        get() = base.localStreamScreen

    override val virtualStream: Any?
        get() = base.virtualStream

    override val keepBackground: Boolean
        get() = base.keepBackground

    override val annotateScreenStream: Boolean
        get() = base.annotateScreenStream

    override val whiteboardStarted: Boolean
        get() = base.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = base.whiteboardEnded

    override val updateMainScreenPerson: (String) -> Unit = base.updateMainScreenPerson

    override val updateMainScreenFilled: (Boolean) -> Unit = base.updateMainScreenFilled

    override val updateAdminOnMainScreen: (Boolean) -> Unit = base.updateAdminOnMainScreen

    override val updateMainHeightWidth: (Double) -> Unit = base.updateMainHeightWidth

    override val updateScreenForceFullDisplay: (Boolean) -> Unit = base.updateScreenForceFullDisplay

    override val updateShowAlert: (ShowAlert?) -> Unit = base.updateShowAlert

    override val meetingDisplayType: String
        get() = meetingDisplayTypeValue

    override val meetingVideoOptimized: Boolean
        get() = meetingVideoOptimizedValue

    override val oldSoundIds: List<String>
        get() = oldSoundIdsValue

    override val shared: Boolean
        get() = base.shared

    override val shareScreenStarted: Boolean
        get() = base.shareScreenStarted

    override val addForBasic: Boolean
        get() = base.addForBasic

    override val updateMainWindow: Boolean
        get() = base.updateMainWindow

    override val hostLabel: String
        get() = base.hostLabel

    override val level: String
        get() = levelValue

    override val islevel: String
        get() = base.islevel

    override val updateActiveSounds: (List<String>) -> Unit = { sounds ->
        updateActiveSoundsCalls += sounds
        base.updateActiveSounds(sounds)
    }

    override val updateAddForBasic: (Boolean) -> Unit = { value ->
        updateAddForBasicCalls += value
        base.updateAddForBasic(value)
    }

    override val updateUpdateMainWindow: (Boolean) -> Unit = { value ->
        updateMainWindowCalls += value
        base.updateUpdateMainWindow(value)
    }

    override val updateNewLimitedStreams: (List<Stream>) -> Unit = { streams ->
        base.updateNewLimitedStreams(streams)
    }

    override val updateNewLimitedStreamsIDs: (List<String>) -> Unit = { ids ->
        base.updateNewLimitedStreamsIDs(ids)
    }

    override val reorderStreams: ReorderStreamsType = { options ->
        reorderCalls += options
        base.reorderStreams(options)
    }

    override val prepopulateUserMedia: PrepopulateUserMediaType = { options ->
        prepopulateCalls += options
        base.prepopulateUserMedia(options)
    }

    override val reUpdateInter: ReUpdateInterType = { options ->
        reUpdateCalls += options
    }

    override val screenPageLimit: Int
        get() = screenPageLimitValue

    override val updateItemPageLimit: (Int) -> Unit = { value ->
        itemPageLimitValue = value
        updateItemPageLimitCalls += value
        base.updateItemPageLimit(value)
    }

    override val itemPageLimit: Int
        get() = itemPageLimitValue

    override val reorderInterval: Int
        get() = reorderIntervalValue

    override val fastReorderInterval: Int
        get() = fastReorderIntervalValue

    override val sortAudioLoudness: Boolean
        get() = sortAudioLoudnessValue

    override val lastReorderTime: Int
        get() = lastReorderTimeValue

    override val eventType: EventType
        get() = base.eventType

    override val updateSortAudioLoudness: (Boolean) -> Unit = { value ->
        sortAudioLoudnessValue = value
        updateSortAudioLoudnessCalls += value
    }

    override val updateLastReorderTime: (Int) -> Unit = { value ->
        lastReorderTimeValue = value
        updateLastReorderTimeCalls += value
    }

    override val updateOldSoundIds: (List<String>) -> Unit = { ids ->
        oldSoundIdsValue = ids
        updateOldSoundIdsCalls += ids
    }

    override val onScreenChanges: suspend (OnScreenChangesOptions) -> Unit = { options ->
        onScreenChangesCalls += options
        base.onScreenChanges(options)
    }

    override val changeVids: suspend (Any) -> Unit = { argument ->
        val options = argument as? ChangeVidsOptions
        if (options != null) {
            changeVidsCalls += options
            base.changeVids(options)
        }
    }

    override fun updateParticipants(participants: List<Participant>) {
        base.updateParticipants(participants)
    }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        base.updateAllVideoStreams(streams)
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        base.updateOldAllStreams(streams)
    }

    override fun updateScreenId(id: String) {
        base.updateScreenId(id)
    }

    override fun updateAdminVidID(id: String) {
        base.updateAdminVidID(id)
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        base.updateNewLimitedStreams(streams)
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        base.updateNewLimitedStreamsIDs(ids)
    }

    override fun updateActiveSounds(sounds: List<String>) {
        updateActiveSoundsCalls += sounds
        base.updateActiveSounds(sounds)
    }

    override fun updateScreenShareIDStream(id: String) {
        base.updateScreenShareIDStream(id)
    }

    override fun updateScreenShareNameStream(name: String) {
        base.updateScreenShareNameStream(name)
    }

    override fun updateAdminIDStream(id: String) {
        base.updateAdminIDStream(id)
    }

    override fun updateAdminNameStream(name: String) {
        base.updateAdminNameStream(name)
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        base.updateYouYouStream(streams)
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> {
        changeVidsCalls += options
        return base.changeVids(options)
    }

    override fun getUpdatedAllParams(): ProducerMediaPausedParameters = this

    fun setMeetingDisplayType(value: String) {
        meetingDisplayTypeValue = value
    }

    fun setMeetingVideoOptimized(value: Boolean) {
        meetingVideoOptimizedValue = value
    }

    fun setLevel(value: String) {
        levelValue = value
    }
}

/**
 * Test double for [ProducerMediaResumedParameters].
 */
class TestProducerMediaResumedParameters(
    participants: List<Participant>,
    meetingDisplayType: String = "media",
    sharedInitial: Boolean = false,
    shareScreenStartedInitial: Boolean = false,
    mainScreenFilledInitial: Boolean = false,
    hostLabel: String = "Host",
    eventType: EventType = EventType.CONFERENCE
) : ProducerMediaResumedParameters {

    private val base = TestControlMediaHostParameters(eventType = eventType)

    private var meetingDisplayTypeValue = meetingDisplayType

    val updateMainWindowCalls = mutableListOf<Boolean>()
    val prepopulateCalls = mutableListOf<PrepopulateUserMediaOptions>()
    val reorderCalls = mutableListOf<ReorderStreamsOptions>()

    init {
        base.assignHostLabel(hostLabel)
        base.updateParticipants(participants)
        base.updateShared(sharedInitial)
        base.updateShareScreenStarted(shareScreenStartedInitial)
        base.updateMainScreenFilled(mainScreenFilledInitial)
    }

    override val meetingDisplayType: String
        get() = meetingDisplayTypeValue

    override val participants: List<Participant>
        get() = base.participants

    override val allVideoStreams: List<Stream>
        get() = base.allVideoStreams

    override val adminVidID: String
        get() = base.adminVidID

    override val islevel: String
        get() = base.islevel

    override val member: String
        get() = base.member

    override val screenId: String
        get() = base.screenId ?: ""

    override val forceFullDisplay: Boolean
        get() = base.forceFullDisplay

    override val shared: Boolean
        get() = base.shared

    override val shareScreenStarted: Boolean
        get() = base.shareScreenStarted

    override val eventType: EventType
        get() = base.eventType

    override val mainScreenFilled: Boolean
        get() = base.mainScreenFilled

    override val hostLabel: String
        get() = base.hostLabel

    override val socket: SocketManager?
        get() = base.socket

    override val localSocket: SocketManager?
        get() = base.localSocket

    override val updateMainWindow: Boolean
        get() = base.updateMainWindow

    override val adminOnMainScreen: Boolean
        get() = base.adminOnMainScreen

    override val mainScreenPerson: String
        get() = base.mainScreenPerson

    override val videoAlreadyOn: Boolean
        get() = base.videoAlreadyOn

    override val audioAlreadyOn: Boolean
        get() = base.audioAlreadyOn

    override val oldAllStreams: List<Stream>
        get() = base.oldAllStreams

    override val screenForceFullDisplay: Boolean
        get() = base.screenForceFullDisplay

    override val localStreamScreen: MediaStream?
        get() = base.localStreamScreen

    override val remoteScreenStream: List<Stream>
        get() = base.remoteScreenStream

    override val localStreamVideo: MediaStream?
        get() = base.localStreamVideo

    override val mainHeightWidth: Double
        get() = base.mainHeightWidth

    override val isWideScreen: Boolean
        get() = base.isWideScreen

    override val localUIMode: Boolean
        get() = base.localUIMode

    override val whiteboardStarted: Boolean
        get() = base.whiteboardStarted

    override val whiteboardEnded: Boolean
        get() = base.whiteboardEnded

    override val virtualStream: Any?
        get() = base.virtualStream

    override val keepBackground: Boolean
        get() = base.keepBackground

    override val annotateScreenStream: Boolean
        get() = base.annotateScreenStream

    override val updateMainScreenPerson: (String) -> Unit
        get() = base.updateMainScreenPerson

    override val updateMainScreenFilled: (Boolean) -> Unit
        get() = base.updateMainScreenFilled

    override val updateAdminOnMainScreen: (Boolean) -> Unit
        get() = base.updateAdminOnMainScreen

    override val updateMainHeightWidth: (Double) -> Unit
        get() = base.updateMainHeightWidth

    override val updateScreenForceFullDisplay: (Boolean) -> Unit
        get() = base.updateScreenForceFullDisplay

    override val updateShowAlert: (ShowAlert?) -> Unit
        get() = base.updateShowAlert

    override val updateUpdateMainWindow: (Boolean) -> Unit = { value ->
        updateMainWindowCalls += value
        base.updateUpdateMainWindow(value)
    }

    // ReorderStreamsParameters properties
    override val newLimitedStreams: List<Stream>
        get() = base.newLimitedStreams

    override val newLimitedStreamsIDs: List<String>
        get() = base.newLimitedStreamsIDs

    override val activeSounds: List<String>
        get() = base.activeSounds

    override val screenShareIDStream: String
        get() = base.screenShareIDStream

    override val screenShareNameStream: String
        get() = base.screenShareNameStream

    override val adminIDStream: String
        get() = base.adminIDStream

    override val adminNameStream: String
        get() = base.adminNameStream

    override val reorderStreams: ReorderStreamsType = { options ->
        reorderCalls += options
        base.reorderStreams(options)
    }

    override val prepopulateUserMedia: PrepopulateUserMediaType = { options ->
        prepopulateCalls += options
        base.prepopulateUserMedia(options)
    }

    override fun updateParticipants(participants: List<Participant>) {
        base.updateParticipants(participants)
    }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        base.updateAllVideoStreams(streams)
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        base.updateOldAllStreams(streams)
    }

    override fun updateScreenId(id: String) {
        base.updateScreenId(id)
    }

    override fun updateAdminVidID(id: String) {
        base.updateAdminVidID(id)
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        base.updateNewLimitedStreams(streams)
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        base.updateNewLimitedStreamsIDs(ids)
    }

    override fun updateActiveSounds(sounds: List<String>) {
        base.updateActiveSounds(sounds)
    }

    override fun updateScreenShareIDStream(id: String) {
        base.updateScreenShareIDStream(id)
    }

    override fun updateScreenShareNameStream(name: String) {
        base.updateScreenShareNameStream(name)
    }

    override fun updateAdminIDStream(id: String) {
        base.updateAdminIDStream(id)
    }

    override fun updateAdminNameStream(name: String) {
        base.updateAdminNameStream(name)
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        base.updateYouYouStream(streams)
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> = base.changeVids(options)

    override fun getUpdatedAllParams(): ProducerMediaResumedParameters = this

    fun setMeetingDisplayType(value: String) {
        meetingDisplayTypeValue = value
    }
}
