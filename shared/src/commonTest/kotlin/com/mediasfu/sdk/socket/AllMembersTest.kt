package com.mediasfu.sdk.socket

import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.model.AllMembersOptions
import com.mediasfu.sdk.model.AllMembersRestParameters
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.ConnectLocalIpsType
import com.mediasfu.sdk.model.ConnectIpsType
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.OnScreenChangesType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.ReorderStreamsType
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.SleepType
import com.mediasfu.sdk.model.SocketLike
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FakeAllMembersParameters : AllMembersRestParameters {
    private var participantsAllState: List<Participant> = emptyList()
    private var participantsState: List<Participant> = emptyList()
    private var allVideoStreamsState: List<Stream> = emptyList()
    private var oldAllStreamsState: List<Stream> = emptyList()
    var dispActiveNamesState: List<String> = emptyList()
    private var requestListState: List<Request> = emptyList()
    var coHostState: String = ""
    var coHostResponsibilityState: List<CoHostResponsibility> = emptyList()
    var lockScreenState: Boolean = false
    var firstAllState: Boolean = false
    var membersReceivedState: Boolean = false
    var deferScreenReceivedState: Boolean = true
    var screenIdState: String = ""
    var shareScreenStartedState: Boolean = false
    var meetingDisplayTypeState: String = "all"
    var audioSettingState: String = "allow"
    var videoSettingState: String = "allow"
    var screenshareSettingState: String = "allow"
    var chatSettingState: String = "allow"
    var hostFirstSwitchState: Boolean = false
    var waitingRoomListState: List<WaitingRoomParticipant> = emptyList()
    var isLevelState: String = "1"
    var roomRecvIpsState: List<String> = emptyList()
    var consumeSocketsState: List<ConsumeSocket> = emptyList()
    var totalReqWaitState: Int = 0
    var isLoadingVisibleState: Boolean? = null
    var reorderCount: Int = 0
    var onScreenChangesCount: Int = 0
    var connectIpsCount: Int = 0
    var connectLocalIpsCount: Int = 0
    val sleepCalls: MutableList<Int> = mutableListOf()
    val audioSettingUpdates: MutableList<String> = mutableListOf()
    val videoSettingUpdates: MutableList<String> = mutableListOf()
    val screenshareSettingUpdates: MutableList<String> = mutableListOf()
    val chatSettingUpdates: MutableList<String> = mutableListOf()
    val mainHeightWidthUpdates: MutableList<Double> = mutableListOf()
    val addForBasicUpdates: MutableList<Boolean> = mutableListOf()
    val itemPageLimitUpdates: MutableList<Int> = mutableListOf()
    var adminVidIDState: String = ""
    var newLimitedStreamsState: List<Stream> = emptyList()
    var newLimitedStreamsIDsState: List<String> = emptyList()
    var activeSoundsState: List<String> = emptyList()
    var screenShareIDStreamState: String = ""
    var screenShareNameStreamState: String = ""
    var adminIDStreamState: String = ""
    var adminNameStreamState: String = ""
    var youYouStreamState: List<Stream> = emptyList()
    var youYouStreamIDsState: List<String> = emptyList()
    val changeVidsCalls: MutableList<ChangeVidsOptions> = mutableListOf()

    var connectIpsResultSockets: List<ConsumeSocket> = listOf(mapOf("socket" to "remoteSocket"))
    var connectIpsResultIps: List<String> = listOf("10.0.0.2")
    var socketState: SocketLike? = null
    var connectLocalIpsLambda: ConnectLocalIpsType? = null
    var eventTypeState: EventType = EventType.CONFERENCE
    var sharedState: Boolean = false
    var addForBasicState: Boolean = false
    var mainHeightWidthState: Double = 0.0
    var itemPageLimitState: Int = 0

    fun seedRequestList(value: List<Request>) {
        requestListState = value
    }

    override val participantsAll: List<Participant>
        get() = participantsAllState

    override val participants: List<Participant>
        get() = participantsState

    override val allVideoStreams: List<Stream>
        get() = allVideoStreamsState

    override val oldAllStreams: List<Stream>
        get() = oldAllStreamsState

    override val dispActiveNames: List<String>
        get() = dispActiveNamesState

    override val requestList: List<Request>
        get() = requestListState

    override val coHost: String
        get() = coHostState

    override val coHostResponsibility: List<CoHostResponsibility>
        get() = coHostResponsibilityState

    override val lockScreen: Boolean
        get() = lockScreenState

    override val firstAll: Boolean
        get() = firstAllState

    override val membersReceived: Boolean
        get() = membersReceivedState

    override val deferScreenReceived: Boolean
        get() = deferScreenReceivedState

    override val screenId: String
        get() = screenIdState

    override val shareScreenStarted: Boolean
        get() = shareScreenStartedState

    override val meetingDisplayType: String
        get() = meetingDisplayTypeState

    override val audioSetting: String
        get() = audioSettingState

    override val videoSetting: String
        get() = videoSettingState

    override val screenshareSetting: String
        get() = screenshareSettingState

    override val chatSetting: String
        get() = chatSettingState

    override val hostFirstSwitch: Boolean
        get() = hostFirstSwitchState

    override val waitingRoomList: List<WaitingRoomParticipant>
        get() = waitingRoomListState

    override val isLevel: String
        get() = isLevelState

    override val adminVidID: String
        get() = adminVidIDState

    override val newLimitedStreams: List<Stream>
        get() = newLimitedStreamsState

    override val newLimitedStreamsIDs: List<String>
        get() = newLimitedStreamsIDsState

    override val activeSounds: List<String>
        get() = activeSoundsState

    override val screenShareIDStream: String
        get() = screenShareIDStreamState

    override val screenShareNameStream: String
        get() = screenShareNameStreamState

    override val adminIDStream: String
        get() = adminIDStreamState

    override val adminNameStream: String
        get() = adminNameStreamState

    override val eventType: EventType
        get() = eventTypeState

    override val shared: Boolean
        get() = sharedState

    override val addForBasic: Boolean
        get() = addForBasicState

    override val itemPageLimit: Int
        get() = itemPageLimitState

    override val socket: SocketLike?
        get() = socketState

    override val consumeSockets: List<ConsumeSocket>
        get() = consumeSocketsState

    override val roomRecvIps: List<String>
        get() = roomRecvIpsState

    override val updateParticipantsAll: (List<Participant>) -> Unit
        get() = { participantsAllState = it }

    override val updateParticipants: (List<Participant>) -> Unit
        get() = { participantsState = it }

    override fun updateParticipants(participants: List<Participant>) {
        participantsState = participants
    }

    override val updateRequestList: (List<Request>) -> Unit
        get() = { requestListState = it }

    override val updateCoHost: (String) -> Unit
        get() = { coHostState = it }

    override val updateCoHostResponsibility: (List<CoHostResponsibility>) -> Unit
        get() = { coHostResponsibilityState = it }

    override val updateFirstAll: (Boolean) -> Unit
        get() = {
            firstAllState = it
        }

    override val updateMembersReceived: (Boolean) -> Unit
        get() = {
            membersReceivedState = it
        }

    override val updateDeferScreenReceived: (Boolean) -> Unit
        get() = {
            deferScreenReceivedState = it
        }

    override val updateShareScreenStarted: (Boolean) -> Unit
        get() = {
            shareScreenStartedState = it
        }

    fun updateShareScreenStarted(value: Boolean) {
        shareScreenStartedState = value
    }

    override val updateIsLoadingModalVisible: (Boolean) -> Unit
        get() = {
            isLoadingVisibleState = it
        }

    override val updateTotalReqWait: (Int) -> Unit
        get() = {
            totalReqWaitState = it
        }

    override val updateHostFirstSwitch: (Boolean) -> Unit
        get() = {
            hostFirstSwitchState = it
        }

    override val updateRoomRecvIps: (List<String>) -> Unit
        get() = {
            roomRecvIpsState = it
        }

    override val updateConsumeSockets: (List<ConsumeSocket>) -> Unit
        get() = {
            consumeSocketsState = it
        }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        allVideoStreamsState = streams
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        oldAllStreamsState = streams
    }

    override fun updateScreenId(id: String) {
        screenIdState = id
    }

    override fun updateAdminVidID(id: String) {
        adminVidIDState = id
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        newLimitedStreamsState = streams
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        newLimitedStreamsIDsState = ids
    }

    override fun updateActiveSounds(sounds: List<String>) {
        activeSoundsState = sounds
    }

    override fun updateScreenShareIDStream(id: String) {
        screenShareIDStreamState = id
    }

    override fun updateScreenShareNameStream(name: String) {
        screenShareNameStreamState = name
    }

    override fun updateAdminIDStream(id: String) {
        adminIDStreamState = id
    }

    override fun updateAdminNameStream(name: String) {
        adminNameStreamState = name
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        youYouStreamState = streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        youYouStreamIDsState = ids
    }

    override val updateAudioSetting: (String) -> Unit
        get() = {
            audioSettingState = it
            audioSettingUpdates += it
        }

    override val updateVideoSetting: (String) -> Unit
        get() = {
            videoSettingState = it
            videoSettingUpdates += it
        }

    override val updateScreenshareSetting: (String) -> Unit
        get() = {
            screenshareSettingState = it
            screenshareSettingUpdates += it
        }

    override val updateChatSetting: (String) -> Unit
        get() = {
            chatSettingState = it
            chatSettingUpdates += it
        }

    override val updateMainHeightWidth: (Double) -> Unit
        get() = {
            mainHeightWidthState = it
            mainHeightWidthUpdates += it
        }

    override val updateAddForBasic: (Boolean) -> Unit
        get() = {
            addForBasicState = it
            addForBasicUpdates += it
        }

    override val updateItemPageLimit: (Int) -> Unit
        get() = {
            itemPageLimitState = it
            itemPageLimitUpdates += it
        }

    override val connectIps: ConnectIpsType = { _ ->
        connectIpsCount += 1
        Pair(connectIpsResultSockets, connectIpsResultIps)
    }

    override val connectLocalIps: ConnectLocalIpsType?
        get() = connectLocalIpsLambda

    override val onScreenChanges: OnScreenChangesType = { _ ->
        onScreenChangesCount += 1
    }

    override val sleep: SleepType = { options ->
        sleepCalls += options.ms
    }

    override val reorderStreams: ReorderStreamsType = { _ ->
        reorderCount += 1
    }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> {
        changeVidsCalls += options
        return Result.success(Unit)
    }

    override fun getUpdatedAllParams(): AllMembersRestParameters = this
}

class AllMembersTest {
    @Test
    fun allMembersConnectsRemoteIpsAndUpdatesState() = runTest {
        val params = FakeAllMembersParameters().apply {
            dispActiveNamesState = listOf("Ghost")
            roomRecvIpsState = listOf("10.0.0.1")
            deferScreenReceivedState = true
            screenIdState = "screen123"
            meetingDisplayTypeState = "speaker"
            waitingRoomListState = listOf(WaitingRoomParticipant(name = "Waiter", id = "W1"))
        }

        val members = listOf(
            Participant(id = "1", name = "Alice", audioID = "a1", videoID = "v1"),
            Participant(id = "2", name = "Bob", audioID = "a2", videoID = "v2", isBanned = true),
            Participant(id = "3", name = "Carol", audioID = "a3", videoID = "v3", isSuspended = true)
        )
        val requests = listOf(
            Request(id = "1", icon = "hand", name = "Alice"),
            Request(id = "2", icon = "hand", name = "Bob")
        )
        val coHostRes = listOf(CoHostResponsibility(name = "manage", value = true, dedicated = false))

        val options = AllMembersOptions(
            members = members,
            requests = requests,
            settings = emptyList(),
            coHost = "NewHost",
            coHostRes = coHostRes,
            parameters = params,
            consumeSockets = emptyList(),
            apiUserName = "apiUser",
            apiKey = null,
            apiToken = "token"
        )

        allMembers(options)

        assertEquals(listOf("Alice", "Bob", "Carol"), params.participantsAll.map { it.name })
        assertEquals(listOf("Alice"), params.participants.map { it.name })
        assertEquals(1, params.reorderCount)
        assertEquals(1, params.connectIpsCount)
        assertEquals(listOf(mapOf("socket" to "remoteSocket")), params.consumeSocketsState)
        assertEquals(listOf("10.0.0.2"), params.roomRecvIps)
        assertTrue(params.membersReceived)
        assertTrue(params.shareScreenStartedState)
        assertEquals(listOf(250), params.sleepCalls)
        assertFalse(params.deferScreenReceivedState)
        assertEquals(false, params.isLoadingVisibleState)
        assertEquals(listOf("1"), params.requestList.map { it.id })
        assertEquals(params.waitingRoomList.size + 1, params.totalReqWaitState)
        assertEquals("NewHost", params.coHost)
        assertEquals(coHostRes, params.coHostResponsibility)
        assertEquals(1, params.onScreenChangesCount)
        assertTrue(params.firstAll)
    }

    @Test
    fun allMembersHandlesLocalConnections() = runTest {
        val params = FakeAllMembersParameters().apply {
            roomRecvIpsState = listOf("none")
            connectLocalIpsLambda = { _ ->
                connectLocalIpsCount += 1
            }
            meetingDisplayTypeState = "all"
            waitingRoomListState = listOf(WaitingRoomParticipant(name = "Waiter", id = "W1"))
        }

        val members = listOf(
            Participant(id = "1", name = "Alice", audioID = "a1", videoID = "v1")
        )
        val requests = listOf(
            Request(id = "1", icon = "hand", name = "Alice"),
            Request(id = "2", icon = "hand", name = "Bob")
        )

        val options = AllMembersOptions(
            members = members,
            requests = requests,
            settings = emptyList(),
            coHost = "Host",
            coHostRes = emptyList(),
            parameters = params,
            consumeSockets = emptyList(),
            apiUserName = "apiUser",
            apiKey = null,
            apiToken = "token"
        )

        allMembers(options)

        assertEquals(0, params.connectIpsCount)
        assertEquals(1, params.connectLocalIpsCount)
        assertEquals(listOf(50), params.sleepCalls)
        assertEquals(false, params.isLoadingVisibleState)
        assertEquals(listOf("1"), params.requestList.map { it.id })
        assertEquals(params.waitingRoomList.size + 1, params.totalReqWaitState)
        assertEquals(1, params.onScreenChangesCount)
        assertFalse(params.firstAll)
    }
}
