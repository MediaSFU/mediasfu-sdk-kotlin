package com.mediasfu.sdk.socket

import com.mediasfu.sdk.consumers.ChangeVidsOptions
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.model.ConnectIpsOptions
import com.mediasfu.sdk.model.ConnectIpsParameters
import com.mediasfu.sdk.model.ConnectIpsType
import com.mediasfu.sdk.model.ConsumeSocket
import com.mediasfu.sdk.model.GetDomainsParameters
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.ReorderStreamsType
import com.mediasfu.sdk.model.Stream

/**
 * Minimal implementation of [GetDomainsParameters] for use in unit tests.
 * Provides mutable backing state for all required read/write properties and
 * exposes overridable hooks for the behavior-oriented callbacks.
 */
open class TestConnectIpsParameters(
    initialRoomRecvIps: List<String> = emptyList(),
    initialConsumeSockets: List<ConsumeSocket> = emptyList()
) : GetDomainsParameters {

    private var _roomRecvIps: List<String> = initialRoomRecvIps
    private var _consumeSockets: List<ConsumeSocket> = initialConsumeSockets

    private var _allVideoStreams: List<Stream> = emptyList()
    private var _participants: List<Participant> = emptyList()
    private var _oldAllStreams: List<Stream> = emptyList()
    private var _screenId: String = ""
    private var _adminVidID: String = ""
    private var _newLimitedStreams: List<Stream> = emptyList()
    private var _newLimitedStreamsIDs: List<String> = emptyList()
    private var _activeSounds: List<String> = emptyList()
    private var _screenShareIDStream: String = ""
    private var _screenShareNameStream: String = ""
    private var _adminIDStream: String = ""
    private var _adminNameStream: String = ""
    private var _youYouStreams: List<Stream> = emptyList()
    private var _youYouStreamIDs: List<String> = emptyList()

    override val roomRecvIps: List<String>
        get() = _roomRecvIps
    override val consumeSockets: List<ConsumeSocket>
        get() = _consumeSockets

    override val allVideoStreams: List<Stream>
        get() = _allVideoStreams
    override val participants: List<Participant>
        get() = _participants
    override val oldAllStreams: List<Stream>
        get() = _oldAllStreams
    override val screenId: String
        get() = _screenId
    override val adminVidID: String
        get() = _adminVidID
    override val newLimitedStreams: List<Stream>
        get() = _newLimitedStreams
    override val newLimitedStreamsIDs: List<String>
        get() = _newLimitedStreamsIDs
    override val activeSounds: List<String>
        get() = _activeSounds
    override val screenShareIDStream: String
        get() = _screenShareIDStream
    override val screenShareNameStream: String
        get() = _screenShareNameStream
    override val adminIDStream: String
        get() = _adminIDStream
    override val adminNameStream: String
        get() = _adminNameStream

    override val updateRoomRecvIps: (List<String>) -> Unit = { _roomRecvIps = it }
    override val updateConsumeSockets: (List<ConsumeSocket>) -> Unit = { _consumeSockets = it }

    override fun updateAllVideoStreams(streams: List<Stream>) {
        _allVideoStreams = streams
    }

    override fun updateParticipants(participants: List<Participant>) {
        _participants = participants
    }

    override fun updateOldAllStreams(streams: List<Stream>) {
        _oldAllStreams = streams
    }

    override fun updateScreenId(id: String) {
        _screenId = id
    }

    override fun updateAdminVidID(id: String) {
        _adminVidID = id
    }

    override fun updateNewLimitedStreams(streams: List<Stream>) {
        _newLimitedStreams = streams
    }

    override fun updateNewLimitedStreamsIDs(ids: List<String>) {
        _newLimitedStreamsIDs = ids
    }

    override fun updateActiveSounds(sounds: List<String>) {
        _activeSounds = sounds
    }

    override fun updateScreenShareIDStream(id: String) {
        _screenShareIDStream = id
    }

    override fun updateScreenShareNameStream(name: String) {
        _screenShareNameStream = name
    }

    override fun updateAdminIDStream(id: String) {
        _adminIDStream = id
    }

    override fun updateAdminNameStream(name: String) {
        _adminNameStream = name
    }

    override fun updateYouYouStream(streams: List<Stream>) {
        _youYouStreams = streams
    }

    override fun updateYouYouStreamIDs(ids: List<String>) {
        _youYouStreamIDs = ids
    }

    /**
     * Captures the most recent value assigned via [updateYouYouStream].
     * Primarily intended for assertions in tests.
     */
    val youYouStreams: List<Stream>
        get() = _youYouStreams

    val youYouStreamIDs: List<String>
        get() = _youYouStreamIDs

    protected open suspend fun onReorderStreams(options: ReorderStreamsOptions) {}

    protected open suspend fun onConnectIps(options: ConnectIpsOptions): Pair<List<ConsumeSocket>, List<String>> =
        Pair(emptyList(), emptyList())

    protected open suspend fun onChangeVids(options: ChangeVidsOptions): Result<Unit> = Result.success(Unit)

    override val reorderStreams: ReorderStreamsType = { options -> onReorderStreams(options) }

    override val connectIps: ConnectIpsType = { options -> onConnectIps(options) }

    override suspend fun changeVids(options: ChangeVidsOptions): Result<Unit> = onChangeVids(options)

    override fun getUpdatedAllParams(): GetDomainsParameters = this
}
