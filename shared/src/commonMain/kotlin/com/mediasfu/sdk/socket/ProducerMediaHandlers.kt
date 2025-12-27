package com.mediasfu.sdk.socket
import com.mediasfu.sdk.util.Logger

import kotlinx.datetime.Clock
import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters
import com.mediasfu.sdk.model.AllMembersOptions
import com.mediasfu.sdk.model.AllMembersParameters
import com.mediasfu.sdk.model.AllMembersRestOptions
import com.mediasfu.sdk.model.AllMembersRestParameters
import com.mediasfu.sdk.model.AllWaitingRoomMembersOptions
import com.mediasfu.sdk.model.BanParticipantOptions
import com.mediasfu.sdk.model.BanParticipantParameters
import com.mediasfu.sdk.model.ControlMediaHostOptions
import com.mediasfu.sdk.model.ControlMediaHostParameters
import com.mediasfu.sdk.model.DisconnectOptions
import com.mediasfu.sdk.model.DisconnectSendTransportAudioOptions
import com.mediasfu.sdk.model.DisconnectSendTransportVideoOptions
import com.mediasfu.sdk.model.DisconnectSendTransportScreenOptions
import com.mediasfu.sdk.model.DisconnectUserSelfOptions
import com.mediasfu.sdk.model.AltDomains
import com.mediasfu.sdk.model.ConnectIpsOptions
import com.mediasfu.sdk.model.GetDomainsOptions
import com.mediasfu.sdk.model.HostRequestResponseOptions
import com.mediasfu.sdk.model.ParticipantRequestedOptions
import com.mediasfu.sdk.model.UpdateConsumingDomainsOptions
import com.mediasfu.sdk.model.PersonJoinedOptions
import com.mediasfu.sdk.model.ReceiveMessageOptions
import com.mediasfu.sdk.model.UpdatedCoHostOptions
import com.mediasfu.sdk.model.UserWaitingOptions
import com.mediasfu.sdk.model.StopShareScreenOptions
import com.mediasfu.sdk.model.CloseAndResizeOptions
import com.mediasfu.sdk.model.CoHostResponsibility
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MediaStreamController
import com.mediasfu.sdk.model.Message
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Settings
import com.mediasfu.sdk.model.UpdateMediaSettingsOptions
import com.mediasfu.sdk.model.PrepopulateUserMediaOptions
import com.mediasfu.sdk.model.ProducerMediaClosedOptions
import com.mediasfu.sdk.model.ProducerMediaPausedOptions
import com.mediasfu.sdk.model.ProducerMediaResumedOptions
import com.mediasfu.sdk.model.RecordingNoticeOptions
import com.mediasfu.sdk.model.ReInitiateRecordingOptions
import com.mediasfu.sdk.model.ReUpdateInterOptions
import com.mediasfu.sdk.model.ReorderStreamsOptions
import com.mediasfu.sdk.model.RoomRecordParamsOptions
import com.mediasfu.sdk.model.ScreenProducerIdOptions
import com.mediasfu.sdk.model.SoundPlayerOptions
import com.mediasfu.sdk.model.StartRecordsOptions
import com.mediasfu.sdk.model.StoppedRecordingOptions
import com.mediasfu.sdk.model.TimeLeftRecordingOptions
import com.mediasfu.sdk.model.MeetingTimeRemainingOptions
import com.mediasfu.sdk.model.MeetingStillThereOptions
import com.mediasfu.sdk.model.MeetingEndedOptions
import com.mediasfu.sdk.model.ConnectLocalIpsOptions
import com.mediasfu.sdk.model.OnScreenChangesOptions
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.model.formatElapsedTime
import kotlinx.coroutines.delay
import com.mediasfu.sdk.model.runSuspendingIgnoringCancellation
import com.mediasfu.sdk.socket.SocketManager

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/all_members.dart. */
suspend fun allMembers(options: AllMembersOptions) {
    var params = options.parameters.getUpdatedAllParams()
    
    params.allVideoStreams.forEachIndexed { index, stream ->
    }

    params.updateParticipantsAll(
        options.members.map { member ->
            Participant(
                id = member.id,
                audioID = member.audioID,
                videoID = member.videoID,
                name = member.name,
                isBanned = member.isBanned,
                isSuspended = member.isSuspended
            )
        }
    )

    params.updateParticipants(
        options.members.filterNot { participant ->
            participant.isBanned || participant.isSuspended
        }
    )

    params = params.getUpdatedAllParams()
    
    params.allVideoStreams.forEachIndexed { index, stream ->
    }

    if (params.dispActiveNames.isNotEmpty()) {
        val missingNames = params.dispActiveNames.filter { name ->
            params.participants.none { it.name == name }
        }
        if (missingNames.isNotEmpty()) {
            params.reorderStreams(
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = params
                )
            )
        }
    }

    var onLocal = params.roomRecvIps.size == 1 && params.roomRecvIps.firstOrNull() == "none"

    if (!params.membersReceived && !onLocal) {
        if (params.roomRecvIps.isEmpty()) {
            while (params.roomRecvIps.isEmpty()) {
                params.sleep(SleepOptions(ms = 10))
                params = params.getUpdatedAllParams()
            }
            onLocal = params.roomRecvIps.size == 1 && params.roomRecvIps.firstOrNull() == "none"
            if (!onLocal) {
                handleConnections(options, params)
                params = params.getUpdatedAllParams()
            }
        } else {
            handleConnections(options, params)
            params = params.getUpdatedAllParams()
        }
    }

    if (onLocal && !params.membersReceived) {
        params.connectLocalIps?.let { connectLocalIps ->
            val refreshed = params.getUpdatedAllParams()
            val localSocket = (refreshed as? PrepopulateUserMediaParameters)?.localSocket
                ?: (refreshed.socket as? SocketManager)
            if (localSocket == null) {
                return@let
            }
            connectLocalIps(
                ConnectLocalIpsOptions(
                    socket = localSocket,
                    parameters = refreshed
                )
            )
        }

        params.sleep(SleepOptions(ms = 50))
        params.updateIsLoadingModalVisible(false)
        params = params.getUpdatedAllParams()
    }

    val updatedRequests = options.requests.filter { request ->
        params.participants.any { it.id == request.id }
    }
    params.updateRequestList(updatedRequests)
    params.updateTotalReqWait(updatedRequests.size + params.waitingRoomList.size)
    params.updateCoHost(options.coHost)
    params.updateCoHostResponsibility(options.coHostRes)
    
    // Update media settings if provided
    if (options.settings.isNotEmpty()) {
        val refreshed = params.getUpdatedAllParams()
        refreshed.updateAudioSetting(options.settings.getOrElse(0) { refreshed.audioSetting })
        if (options.settings.size > 1) {
            refreshed.updateVideoSetting(options.settings[1])
        }
        if (options.settings.size > 2) {
            refreshed.updateScreenshareSetting(options.settings[2])
        }
        if (options.settings.size > 3) {
            refreshed.updateChatSetting(options.settings[3])
        }
    }

    
    // Reset firstAll to false when in 'all' mode to allow continuous updates
    if (params.meetingDisplayType == "all" && params.firstAll) {
        params.updateFirstAll(false)
    }
    

    if (!params.lockScreen && !params.firstAll) {
        params.onScreenChanges(OnScreenChangesOptions(parameters = params))
        val afterParams = params.getUpdatedAllParams()
        afterParams.newLimitedStreams.forEachIndexed { index, stream ->
        }
        // Always update firstAll based on meetingDisplayType after onScreenChanges
        val newFirstAllValue = params.meetingDisplayType != "all"
        params.updateFirstAll(newFirstAllValue)
    } else if (params.isLevel == "2" && !params.hostFirstSwitch) {
        params.onScreenChanges(OnScreenChangesOptions(parameters = params))
        val afterParams = params.getUpdatedAllParams()
        params.updateHostFirstSwitch(true)
    } else {
    }
}

private suspend fun handleConnections(
    options: AllMembersOptions,
    baseParams: AllMembersParameters
) {
    val params = baseParams.getUpdatedAllParams()

    if (params.deferScreenReceived && params.screenId.isNotEmpty()) {
        params.updateShareScreenStarted(true)
    }

    val (sockets, ips) = params.connectIps(
        ConnectIpsOptions(
            consumeSockets = options.consumeSockets,
            remoteIps = params.roomRecvIps,
            apiUserName = options.apiUserName,
            apiKey = options.apiKey,
            apiToken = options.apiToken,
            parameters = params
        )
    )

    params.updateConsumeSockets(sockets)
    params.updateRoomRecvIps(ips)

    params.updateMembersReceived(true)
    params.sleep(SleepOptions(ms = 250))
    params.updateIsLoadingModalVisible(false)
    params.updateDeferScreenReceived(false)
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/all_members_rest.dart. */
suspend fun allMembersRest(options: AllMembersRestOptions) {
    options.members.forEachIndexed { index, member ->
    }
    var params: AllMembersRestParameters = options.parameters.getUpdatedAllParams()

    val activeMembers = options.members.filterNot { participant ->
        participant.isBanned || participant.isSuspended
    }
    params.updateParticipantsAll(activeMembers)
    params.updateParticipants(activeMembers)

    params = params.getUpdatedAllParams()
    params.participants.forEachIndexed { index, participant ->
    }

    // Check if participants left (dispActiveNames has names not in participants)
    if (params.dispActiveNames.isNotEmpty()) {
        val missingNames = params.dispActiveNames.filter { name ->
            params.participants.none { it.name == name }
        }
        Logger.e("ProducerMediaHandler", "MediaSFU - allMembersRest: found ${missingNames.size} missing names")
        if (missingNames.isNotEmpty() && params.membersReceived) {
            Logger.e("ProducerMediaHandler", "MediaSFU - allMembersRest: calling reorderStreams for ${missingNames.size} missing dispActiveNames")
            params.reorderStreams(
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = params
                )
            )
            params = params.getUpdatedAllParams()
        }
    }

    var onLocal = params.roomRecvIps.size == 1 && params.roomRecvIps.firstOrNull() == "none"

    if (!onLocal) {
        if (!params.membersReceived) {
            if (params.roomRecvIps.isEmpty()) {
                while (true) {
                    params.sleep(SleepOptions(ms = 10))
                    params = params.getUpdatedAllParams()
                    val currentIps = params.roomRecvIps
                    if (currentIps.isNotEmpty()) {
                        onLocal = currentIps.size == 1 && currentIps.firstOrNull() == "none"
                        if (!onLocal) {
                            handleServerConnection(options, params)
                            params.updateIsLoadingModalVisible(false)
                            params = params.getUpdatedAllParams()
                        }
                        break
                    }
                }
            } else {
                handleServerConnection(options, params)
                params.updateIsLoadingModalVisible(false)
                params = params.getUpdatedAllParams()
            }
        } else if (params.screenId.isNotEmpty() && params.deferScreenReceived) {
            params.updateShareScreenStarted(true)
        } else {
        }
    } else {
    }

    if (onLocal && !params.membersReceived) {
        params.connectLocalIps?.let { connectLocalIps ->
            connectLocalIps(
                ConnectLocalIpsOptions(
                    socket = params.socket,
                    parameters = params
                )
            )
        }

        params.sleep(SleepOptions(ms = 50))
        params.updateIsLoadingModalVisible(false)
        params = params.getUpdatedAllParams()
    }

    val filteredRequests = params.requestList.filter { request ->
        params.participants.any { it.id == request.id }
    }
    params.updateRequestList(filteredRequests)
    params.updateCoHost(options.coHost)
    params.updateCoHostResponsibility(options.coHostRes)

    
    // Reset firstAll to false when in 'all' mode to allow continuous updates
    if (params.meetingDisplayType == "all" && params.firstAll) {
        params.updateFirstAll(false)
    }
    
    
    if (!params.lockScreen && !params.firstAll) {
        params.onScreenChanges(OnScreenChangesOptions(parameters = params))
        val afterParams = params.getUpdatedAllParams()
        afterParams.newLimitedStreams.forEachIndexed { index, stream ->
        }
        // Always update firstAll based on meetingDisplayType after onScreenChanges
        val newFirstAllValue = params.meetingDisplayType != "all"
        params.updateFirstAll(newFirstAllValue)
    } else {
    }

    val refreshed = params.getUpdatedAllParams()
    if (refreshed.membersReceived) {
        val settings = options.settings
        if (settings.isNotEmpty()) {
            refreshed.updateAudioSetting(settings.getOrElse(0) { refreshed.audioSetting })
        }
        if (settings.size > 1) {
            refreshed.updateVideoSetting(settings[1])
        }
        if (settings.size > 2) {
            refreshed.updateScreenshareSetting(settings[2])
        }
        if (settings.size > 3) {
            refreshed.updateChatSetting(settings[3])
        }
    }
}

private suspend fun handleServerConnection(
    options: AllMembersRestOptions,
    params: AllMembersRestParameters
) {
    if (params.deferScreenReceived && params.screenId.isNotEmpty()) {
        params.updateShareScreenStarted(true)
    }

    val (sockets, ips) = params.connectIps(
        ConnectIpsOptions(
            consumeSockets = options.consumeSockets,
            remoteIps = params.roomRecvIps,
            apiUserName = options.apiUserName,
            apiKey = options.apiKey,
            apiToken = options.apiToken,
            parameters = params
        )
    )

    params.updateConsumeSockets(sockets)
    params.updateRoomRecvIps(ips)
    params.updateMembersReceived(true)
    params.sleep(SleepOptions(ms = 250))
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/all_waiting_room_members.dart. */
suspend fun allWaitingRoomMembers(options: AllWaitingRoomMembersOptions) {
    val totalRequests = options.waitingParticipants.size
    options.updateWaitingRoomList(options.waitingParticipants)
    options.updateTotalReqWait(totalRequests)
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/ban_participant.dart. */
suspend fun banParticipant(options: BanParticipantOptions) {
    var params: BanParticipantParameters = options.parameters.getUpdatedAllParams()

    val shouldBan = params.activeNames.contains(options.name) ||
        params.dispActiveNames.contains(options.name)

    if (!shouldBan) {
        return
    }

    val updatedParticipants = params.participants.filterNot { it.name == options.name }

    if (updatedParticipants.size == params.participants.size) {
        return
    }

    params.updateParticipants(updatedParticipants)
    params = params.getUpdatedAllParams()

    params.reorderStreams(
        ReorderStreamsOptions(
            add = false,
            screenChanged = true,
            parameters = params
        )
    )
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/control_media_host.dart. */
suspend fun controlMediaHost(options: ControlMediaHostOptions) {
    var params: ControlMediaHostParameters = options.parameters.getUpdatedAllParams()

    params.updateAdminRestrictSetting(true)

    try {
        when (options.type) {
            "audio" -> {
                (params.localStream as? MediaStreamController)?.disableAudio()
                params.updateLocalStream(params.localStream)

                params.disconnectSendTransportAudio(
                    DisconnectSendTransportAudioOptions(parameters = params)
                )
                params.updateAudioAlreadyOn(false)
            }

            "video" -> {
                (params.localStream as? MediaStreamController)?.disableVideo()
                params.updateLocalStream(params.localStream)

                val videoOptions = DisconnectSendTransportVideoOptions(parameters = params)
                val onScreenOptions = OnScreenChangesOptions(changed = true, parameters = params)

                params.disconnectSendTransportVideo(videoOptions)
                params.onScreenChanges(onScreenOptions)
                params.updateVideoAlreadyOn(false)

                params = params.getUpdatedAllParams()

                (params.localStreamVideo as? MediaStreamController)?.disableVideo()
                params.updateLocalStreamVideo(params.localStreamVideo)
                params.disconnectSendTransportVideo(videoOptions)
                params.onScreenChanges(onScreenOptions)
                params.updateVideoAlreadyOn(false)
            }

            "screenshare" -> {
                (params.localStreamScreen as? MediaStreamController)?.disableVideo()
                params.updateLocalStreamScreen(params.localStreamScreen)

                params.disconnectSendTransportScreen(
                    DisconnectSendTransportScreenOptions(parameters = params)
                )
                params.stopShareScreen(StopShareScreenOptions(parameters = params))
                params.updateScreenAlreadyOn(false)
            }

            "chat" -> params.updateChatAlreadyOn(false)

            "all" -> {
                try {
                    val updated = params.getUpdatedAllParams()
                    (updated.localStream as? MediaStreamController)?.disableAudio()
                    updated.updateLocalStream(updated.localStream)
                    updated.disconnectSendTransportAudio(
                        DisconnectSendTransportAudioOptions(parameters = updated)
                    )
                    updated.updateAudioAlreadyOn(false)
                } catch (_: Throwable) {
                }

                try {
                    val updated = params.getUpdatedAllParams()
                    (updated.localStreamScreen as? MediaStreamController)?.disableVideo()
                    updated.updateLocalStreamScreen(updated.localStreamScreen)
                    updated.disconnectSendTransportScreen(
                        DisconnectSendTransportScreenOptions(parameters = updated)
                    )
                    updated.stopShareScreen(StopShareScreenOptions(parameters = updated))
                    updated.updateScreenAlreadyOn(false)
                } catch (_: Throwable) {
                }

                try {
                    val updated = params.getUpdatedAllParams()
                    val videoOptions = DisconnectSendTransportVideoOptions(parameters = updated)
                    val onScreenOptions = OnScreenChangesOptions(changed = true, parameters = updated)

                    (updated.localStream as? MediaStreamController)?.disableVideo()
                    updated.updateLocalStream(updated.localStream)
                    updated.disconnectSendTransportVideo(videoOptions)
                    updated.onScreenChanges(onScreenOptions)
                    updated.updateVideoAlreadyOn(false)

                    val newest = updated.getUpdatedAllParams()
                    (newest.localStreamVideo as? MediaStreamController)?.disableVideo()
                    newest.updateLocalStreamVideo(newest.localStreamVideo)
                    newest.disconnectSendTransportVideo(videoOptions)
                    newest.onScreenChanges(onScreenOptions)
                    newest.updateVideoAlreadyOn(false)
                } catch (_: Throwable) {
                }
            }

            else -> throw IllegalArgumentException("Invalid media control type")
        }
    } catch (_: Throwable) {
        // Mirror Flutter behaviour: swallow errors (debug logging only there)
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/disconnect.dart. */
suspend fun disconnect(options: DisconnectOptions) {
    if (options.onWeb && !options.redirectUrl.isNullOrEmpty()) {
        // Flutter implementation would trigger a web redirect; no equivalent action required here.
        return
    }

    options.showAlert?.invoke(
        "You have been disconnected from the session.",
        "danger",
        3_000
    )

    val updateValidated = options.updateValidated ?: return

    delay(2_000)
    updateValidated(false)
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/disconnect_user_self.dart. */
suspend fun disconnectUserSelf(options: DisconnectUserSelfOptions) {
    val socket = options.socket ?: return
    val payload = mapOf(
        "member" to options.member,
        "roomName" to options.roomName,
        "ban" to true
    )

    socket.emit("disconnectUser", payload)

    try {
        val localSocket = options.localSocket
        if (localSocket != null && localSocket.isConnected) {
            localSocket.emit("disconnectUser", payload)
            localSocket.emit("disconnectUser", payload)
        }
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/get_domains.dart. */
suspend fun getDomains(options: GetDomainsOptions) {
    val updatedParams = options.parameters.getUpdatedAllParams()
    val ipsToConnect = mutableListOf<String>()

    try {
        // Process each domain and check if IP is already connected
        for (domain in options.domains) {
            val ipToCheck = (options.altDomains.data[domain] as? String) ?: domain

            // Add IP if not already connected
            if (!updatedParams.roomRecvIps.contains(ipToCheck)) {
                ipsToConnect.add(ipToCheck)
            }
        }

        // Connect to IPs
        if (ipsToConnect.isNotEmpty()) {
            val optionsConnect = ConnectIpsOptions(
                consumeSockets = updatedParams.consumeSockets,
                remoteIps = ipsToConnect,
                apiUserName = options.apiUserName,
                apiKey = options.apiKey,
                apiToken = options.apiToken,
                parameters = updatedParams
            )
            updatedParams.connectIps(optionsConnect)
        }
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/host_request_response.dart. */
suspend fun hostRequestResponse(options: HostRequestResponseOptions) {
    val requestResponse = options.requestResponse
    val requestType = requestResponse.type ?: return
    val isAccepted = requestResponse.action == "accepted"
    val alertDuration = 10000

    // Filter out the processed request from the list
    val updatedRequestList = options.requestList.filter { it.id != requestResponse.id }
    options.updateRequestList(updatedRequestList)

    fun showRequestAlert(action: String, message: String) {
        options.showAlert?.invoke(
            "$action $message",
            if (isAccepted) "success" else "danger",
            alertDuration
        )
    }

    // Map request types to their corresponding actions
    val requestDisplayNames = mapOf(
        "fa-microphone" to "Audio",
        "fa-video" to "Video",
        "fa-desktop" to "Screen share",
        "fa-comments" to "Chat"
    )

    val requestTypeMap = mapOf(
        "fa-microphone" to Triple(
            options.updateMicAction,
            options.updateAudioRequestState,
            options.updateAudioRequestTime
        ),
        "fa-video" to Triple(
            options.updateVideoAction,
            options.updateVideoRequestState,
            options.updateVideoRequestTime
        ),
        "fa-desktop" to Triple(
            options.updateScreenAction,
            options.updateScreenRequestState,
            options.updateScreenRequestTime
        ),
        "fa-comments" to Triple(
            options.updateChatAction,
            options.updateChatRequestState,
            options.updateChatRequestTime
        )
    )

    val requestActions = requestTypeMap[requestType]
    val requestName = requestDisplayNames[requestType]

    if (requestActions != null && requestName != null) {
        val (actionUpdate, stateUpdate, timeUpdate) = requestActions

        if (isAccepted) {
            showRequestAlert(requestName, "request was accepted; click the button again to begin.")
            actionUpdate(true)
            stateUpdate("accepted")
        } else {
            showRequestAlert(requestName, "request was not accepted")
            stateUpdate("rejected")

            val nextRequestTime = Clock.System.now().toEpochMilliseconds() +
                    (options.updateRequestIntervalSeconds * 1000L)
            timeUpdate(nextRequestTime)
        }
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/participant_requested.dart. */
fun participantRequested(options: ParticipantRequestedOptions) {
    // Add the user request to the request list
    val updatedRequestList = options.requestList + options.userRequest
    options.updateRequestList(updatedRequestList)

    // Update the total count of requests and waiting room participants
    val reqCount = updatedRequestList.size + options.waitingRoomList.size
    options.updateTotalReqWait(reqCount)
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/user_waiting.dart. */
fun userWaiting(options: UserWaitingOptions) {
    try {
        // Display alert if provided
        try {
            options.showAlert?.invoke(
                "${options.name} joined the waiting room.",
                "success",
                3000
            )
        } catch (_: Throwable) {
            // Swallow alert errors but continue with update
        }

        // Increment the total waiting requests and update
        val updatedTotalReqWait = options.totalReqWait + 1
        options.updateTotalReqWait(updatedTotalReqWait)
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/person_joined.dart. */
fun personJoined(options: PersonJoinedOptions) {
    options.showAlert?.invoke(
        "${options.name} has joined the event.",
        "success",
        3000
    )
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/updated_co_host.dart. */
fun updatedCoHost(options: UpdatedCoHostOptions) {
    try {
        if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
            // Update co-host if event type is not broadcast or chat
            options.updateCoHost(options.coHost)
            options.updateCoHostResponsibility(options.coHostResponsibility)

            if (options.member == options.coHost) {
                if (!options.youAreCoHost) {
                    options.updateYouAreCoHost(true)
                    options.showAlert?.invoke(
                        "You are now a co-host.",
                        "success",
                        3000
                    )
                }
            } else {
                options.updateYouAreCoHost(false)
            }
        } else if (options.islevel != "2") {
            options.updateYouAreCoHost(true)
        }
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/update_media_settings.dart. */
fun updateMediaSettings(options: UpdateMediaSettingsOptions) {
    try {
        // Get the settings with defaults
        val audioSetting = options.settings.getOrElse(0) { "allow" }
        val videoSetting = options.settings.getOrElse(1) { "allow" }
        val screenshareSetting = options.settings.getOrElse(2) { "allow" }
        val chatSetting = options.settings.getOrElse(3) { "allow" }

        // Update each setting
        options.updateAudioSetting(audioSetting)
        options.updateVideoSetting(videoSetting)
        options.updateScreenshareSetting(screenshareSetting)
        options.updateChatSetting(chatSetting)
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/update_consuming_domains.dart. */
suspend fun updateConsumingDomains(options: UpdateConsumingDomainsOptions) {
    try {
        // Access latest parameters
        val updatedParams = options.parameters.getUpdatedAllParams()

        // Check if participants list is non-empty
        if (updatedParams.participants.isNotEmpty()) {
            // Check if altDomains has entries
            if (options.altDomains.data.isNotEmpty()) {
                val optionsGet = GetDomainsOptions(
                    domains = options.domains,
                    altDomains = options.altDomains,
                    apiUserName = options.apiUserName,
                    apiKey = options.apiKey,
                    apiToken = options.apiToken,
                    parameters = updatedParams
                )
                updatedParams.getDomains(optionsGet)
            } else {
                val optionsConnect = ConnectIpsOptions(
                    consumeSockets = updatedParams.consumeSockets,
                    remoteIps = options.domains,
                    apiUserName = options.apiUserName,
                    apiKey = options.apiKey,
                    apiToken = options.apiToken,
                    parameters = updatedParams
                )
                updatedParams.connectIps(optionsConnect)
            }
        }
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow errors for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/receive_message.dart. */
suspend fun receiveMessage(options: ReceiveMessageOptions) {
    val message = options.message
    
    // Add new message to the list
    var messages = options.messages + Message(
        sender = message.sender,
        receivers = message.receivers,
        message = message.message,
        timestamp = message.timestamp,
        group = message.group
    )

    // Filter out messages with banned senders
    messages = if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        messages.filter { msg ->
            options.participantsAll.any { participant ->
                participant.name == msg.sender && participant.isBanned != true
            }
        }
    } else {
        messages.filter { msg ->
            val participant = options.participantsAll.firstOrNull { it.name == msg.sender }
            participant?.isBanned != true
        }
    }
    options.updateMessages(messages)

    // Separate group and direct messages
    val oldGroupMessages = options.messages.filter { it.group }
    val oldDirectMessages = options.messages.filter { !it.group }
    val groupMessages = messages.filter { it.group }
    val directMessages = messages.filter { !it.group }

    // Group messages logic
    if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        if (oldGroupMessages.size != groupMessages.size) {
            val newGroupMessages = groupMessages.filter { msg ->
                oldGroupMessages.none { it.timestamp == msg.timestamp }
            }

            val relevantNewGroupMessages = newGroupMessages.filter { msg ->
                msg.sender == options.member || msg.receivers.contains(options.member)
            }

            if (newGroupMessages.isNotEmpty() && newGroupMessages.size != relevantNewGroupMessages.size) {
                options.updateShowMessagesBadge(true)
            }
        }
    }

    // Direct messages logic
    if (options.eventType != EventType.BROADCAST && options.eventType != EventType.CHAT) {
        if (oldDirectMessages.size != directMessages.size) {
            val newDirectMessages = directMessages.filter { msg ->
                oldDirectMessages.none { it.timestamp == msg.timestamp }
            }

            val relevantNewDirectMessages = newDirectMessages.filter { msg ->
                msg.sender == options.member || msg.receivers.contains(options.member)
            }

            if ((newDirectMessages.isNotEmpty() && relevantNewDirectMessages.isNotEmpty()) ||
                (newDirectMessages.isNotEmpty() && (options.isLevel == "2" || options.coHost == options.member))
            ) {
                if (options.isLevel == "2" || options.coHost == options.member) {
                    if (newDirectMessages.size != relevantNewDirectMessages.size) {
                        options.updateShowMessagesBadge(true)
                    }
                } else if (relevantNewDirectMessages.isNotEmpty()) {
                    if (newDirectMessages.size != relevantNewDirectMessages.size) {
                        options.updateShowMessagesBadge(true)
                    }
                }
            }
        }
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/producer_media_closed.dart. */
suspend fun producerMediaClosed(options: ProducerMediaClosedOptions) {
    val updatedParameters = options.parameters.getUpdatedAllParams()

    val consumerTransports = updatedParameters.consumerTransports
    val producerToClose = consumerTransports.firstOrNull { it.producerId == options.producerId }

    if (producerToClose == null) {
        return
    }

    if (producerToClose.producerId.isNotEmpty()) {
        runSuspendingIgnoringCancellation {
            producerToClose.consumerTransport.close()
        }

        runSuspendingIgnoringCancellation {
            producerToClose.consumer.close()
        }

        val updatedTransports = consumerTransports.filterNot { it.producerId == options.producerId }
        updatedParameters.updateConsumerTransports(updatedTransports)

        val closeOptions = CloseAndResizeOptions(
            producerId = options.producerId,
            kind = options.kind,
            parameters = updatedParameters
        )
        updatedParameters.closeAndResize(closeOptions)
    } else if (options.kind == "screenshare" || options.kind == "screen") {
        if (updatedParameters.shared) {
            updatedParameters.updateShared(false)
        } else {
            updatedParameters.updateShareScreenStarted(false)
            updatedParameters.updateScreenId("")
        }

        updatedParameters.updateShareEnded(true)

        val prepopulateOptions = PrepopulateUserMediaOptions(
            name = updatedParameters.hostLabel,
            parameters = updatedParameters
        )
        updatedParameters.prepopulateUserMedia(prepopulateOptions)

        val reorderOptions = ReorderStreamsOptions(
            add = false,
            screenChanged = true,
            parameters = updatedParameters
        )
        updatedParameters.reorderStreams(reorderOptions)
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/re_initiate_recording.dart. */
suspend fun reInitiateRecording(options: ReInitiateRecordingOptions) {
    if (options.adminRestrictSetting) {
        return
    }

    val socket = options.socket ?: return

    socket.emitWithAck(
        event = "startRecordIng",
        data = mapOf(
            "roomName" to options.roomName,
            "member" to options.member
        )
    ) { _ -> }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/start_records.dart. */
suspend fun startRecords(options: StartRecordsOptions) {
    val socket = options.socket ?: return

    try {
        socket.emitWithAck(
            event = "startRecordIng",
            data = mapOf(
                "roomName" to options.roomName,
                "member" to options.member
            )
        ) { response ->
            // Flutter implementation only logs the success flag in debug mode; parity not required.
            if (response["success"] == true) {
                // no-op
            }
        }
    } catch (_: Throwable) {
        // Mirror Flutter: swallow exceptions (logs are debug-only there).
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/stopped_recording.dart. */
suspend fun stoppedRecording(options: StoppedRecordingOptions) {
    try {
        if (options.state == "stop") {
            options.showAlert?.invoke(
                "The recording has stopped - ${options.reason}.",
                "danger",
                3000
            )
        }
    } catch (_: Throwable) {
        // Flutter handler only logs in debug mode; swallow exceptions for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/time_left_recording.dart. */
fun timeLeftRecording(options: TimeLeftRecordingOptions) {
    try {
        options.showAlert?.invoke(
            "The recording will stop in less than ${options.timeLeft} seconds.",
            "danger",
            3000
        )
    } catch (_: Throwable) {
        // Flutter implementation logs only in debug mode; swallow exceptions for parity.
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/meeting_time_remaining.dart. */
suspend fun meetingTimeRemaining(options: MeetingTimeRemainingOptions) {
    val minutes = options.timeRemaining / 60_000
    val seconds = (options.timeRemaining % 60_000) / 1_000
    val timeRemainingString = "$minutes:${seconds.toString().padStart(2, '0')}"

    if (options.eventType != EventType.CHAT) {
        options.showAlert?.invoke(
            "The event will end in $timeRemainingString minutes.",
            "success",
            3000
        )
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/meeting_still_there.dart. */
suspend fun meetingStillThere(options: MeetingStillThereOptions) {
    options.updateIsConfirmHereModalVisible(true)
}

    /** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/meeting_ended.dart. */
    suspend fun meetingEnded(options: MeetingEndedOptions) {
        if (options.eventType != EventType.CHAT) {
            options.showAlert?.invoke(
                "The meeting has ended. Redirecting to the home page...",
                "danger",
                2000
            )
        }

        if (options.onWeb && !options.redirectUrl.isNullOrEmpty()) {
            delay(2_000)
            // Flutter implementation would redirect via browser; no action here.
        } else {
            val updateValidated = options.updateValidated
            if (updateValidated != null) {
                delay(2_000)
                updateValidated(false)
            }
        }
    }

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/screen_producer_id.dart. */
fun screenProducerId(options: ScreenProducerIdOptions) {
    val host = options.participants.firstOrNull { participant ->
        participant.ScreenID == options.screenId && participant.ScreenOn
    } ?: Participant(name = "")

    options.updateScreenId(options.producerId)

    if (host.name.isNotEmpty() && options.membersReceived) {
        options.updateShareScreenStarted(true)
        options.updateDeferScreenReceived(false)
    } else {
        options.updateDeferScreenReceived(true)
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/room_record_params.dart. */
fun roomRecordParams(options: RoomRecordParamsOptions) {
    val recordParams = options.recordParams
    val params = options.parameters

    params.updateRecordingAudioPausesLimit(recordParams.recordingAudioPausesLimit)
    params.updateRecordingAudioPausesCount(recordParams.recordingAudioPausesCount ?: 0)
    params.updateRecordingAudioSupport(recordParams.recordingAudioSupport)
    params.updateRecordingAudioPeopleLimit(recordParams.recordingAudioPeopleLimit)
    params.updateRecordingAudioParticipantsTimeLimit(recordParams.recordingAudioParticipantsTimeLimit)
    params.updateRecordingVideoPausesCount(recordParams.recordingVideoPausesCount ?: 0)
    params.updateRecordingVideoPausesLimit(recordParams.recordingVideoPausesLimit)
    params.updateRecordingVideoSupport(recordParams.recordingVideoSupport)
    params.updateRecordingVideoPeopleLimit(recordParams.recordingVideoPeopleLimit)
    params.updateRecordingVideoParticipantsTimeLimit(recordParams.recordingVideoParticipantsTimeLimit)
    params.updateRecordingAllParticipantsSupport(recordParams.recordingAllParticipantsSupport)
    params.updateRecordingVideoParticipantsSupport(recordParams.recordingVideoParticipantsSupport)
    params.updateRecordingAllParticipantsFullRoomSupport(recordParams.recordingAllParticipantsFullRoomSupport)
    params.updateRecordingVideoParticipantsFullRoomSupport(recordParams.recordingVideoParticipantsFullRoomSupport)
    params.updateRecordingPreferredOrientation(recordParams.recordingPreferredOrientation)
    params.updateRecordingSupportForOtherOrientation(recordParams.recordingSupportForOtherOrientation)
    params.updateRecordingMultiFormatsSupport(recordParams.recordingMultiFormatsSupport)
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/recording_notice.dart. */
suspend fun recordingNotice(options: RecordingNoticeOptions) {
    val params = options.parameters.getUpdatedAllParams()
    val state = options.state
    val pauseCount = options.pauseCount
    val timeDone = options.timeDone

    var recordElapsedTime = params.recordElapsedTime
    var recordStartTime = params.recordStartTime ?: 0L

    try {
        if (params.isLevel != "2") {
            when (state) {
                "pause" -> {
                    params.updateRecordStarted(true)
                    params.updateRecordPaused(true)
                    params.updateRecordState("yellow")
                    if (params.eventType != EventType.BROADCAST) {
                        params.playSound(
                            SoundPlayerOptions("https://www.mediasfu.com/sounds/record-paused.mp3")
                        )
                    }
                }
                "stop" -> {
                    params.updateRecordStarted(true)
                    params.updateRecordStopped(true)
                    params.updateRecordState("green")
                    if (params.eventType != EventType.BROADCAST) {
                        params.playSound(
                            SoundPlayerOptions("https://www.mediasfu.com/sounds/record-stopped.mp3")
                        )
                    }
                }
                else -> {
                    params.updateRecordState("red")
                    params.updateRecordStarted(true)
                    params.updateRecordPaused(false)
                    if (params.eventType != EventType.BROADCAST) {
                        params.playSound(
                            SoundPlayerOptions("https://www.mediasfu.com/sounds/record-progress.mp3")
                        )
                    }
                }
            }
        } else {
            if (state == "pause" && options.userRecordingParams != null) {
                val userRecordingParams = options.userRecordingParams
                params.updateRecordState("yellow")
                params.updateUserRecordingParams(userRecordingParams)

                val mainSpecs = userRecordingParams.mainSpecs
                params.updateRecordingMediaOptions(mainSpecs.mediaOptions)
                params.updateRecordingAudioOptions(mainSpecs.audioOptions)
                params.updateRecordingVideoOptions(mainSpecs.videoOptions)
                params.updateRecordingVideoType(mainSpecs.videoType)
                params.updateRecordingVideoOptimized(mainSpecs.videoOptimized)
                params.updateRecordingDisplayType(mainSpecs.recordingDisplayType)
                params.updateRecordingAddHls(mainSpecs.addHls)

                val dispSpecs = userRecordingParams.dispSpecs
                params.updateRecordingNameTags(dispSpecs.nameTags)
                params.updateRecordingBackgroundColor(dispSpecs.backgroundColor)
                params.updateRecordingNameTagsColor(dispSpecs.nameTagsColor)
                params.updateRecordingOrientationVideo(dispSpecs.orientationVideo)

                val textSpecs = userRecordingParams.textSpecs
                params.updateRecordingAddText(textSpecs?.addText ?: false)
                params.updateRecordingCustomText(textSpecs?.customText ?: "")
                params.updateRecordingCustomTextPosition(textSpecs?.customTextPosition ?: "")
                params.updateRecordingCustomTextColor(textSpecs?.customTextColor ?: "")

                params.updatePauseRecordCount(pauseCount)

                if (timeDone != 0) {
                    recordElapsedTime = timeDone / 1_000
                    val currentMillis = params.currentTimeProvider()
                    recordStartTime = currentMillis - timeDone
                    params.updateRecordElapsedTime(recordElapsedTime)
                    params.updateRecordStartTime(recordStartTime)
                }

                params.updateRecordStarted(true)
                params.updateRecordPaused(true)
                params.updateCanLaunchRecord(false)
                params.updateRecordStopped(false)
                params.updateShowRecordButtons(true)
                params.updateIsTimerRunning(false)
                params.updateCanPauseResume(true)

                if (timeDone != 0) {
                    params.updateRecordingProgressTime(formatElapsedTime(recordElapsedTime))
                    params.updateRecordState("yellow")
                }

                params.playSound(
                    SoundPlayerOptions("https://www.mediasfu.com/sounds/record-paused.mp3")
                )
            } else if (state == "stop") {
                params.updateRecordStarted(true)
                params.updateRecordStopped(true)
                params.updateCanLaunchRecord(false)
                params.updateShowRecordButtons(false)
                params.updateRecordState("green")
                params.playSound(
                    SoundPlayerOptions("https://www.mediasfu.com/sounds/record-stopped.mp3")
                )
            } else {
                // Recording started (not paused or stopped)
                params.updateRecordState("red")
                params.updateRecordStarted(true)
                params.updateRecordPaused(false)
                params.updateShowRecordButtons(true)  // Show expanded record buttons when recording starts
                params.playSound(
                    SoundPlayerOptions("https://www.mediasfu.com/sounds/record-progress.mp3")
                )
            }
        }
    } catch (e: Throwable) {
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/producer_media_paused.dart. */
suspend fun producerMediaPaused(options: ProducerMediaPausedOptions) {
    val updatedParameters = options.parameters.getUpdatedAllParams()

    val activeSounds = updatedParameters.activeSounds.toMutableList()
    val meetingDisplayType = updatedParameters.meetingDisplayType
    val meetingVideoOptimized = updatedParameters.meetingVideoOptimized
    val participants = updatedParameters.participants
    val oldSoundIds = updatedParameters.oldSoundIds
    val shared = updatedParameters.shared
    val shareScreenStarted = updatedParameters.shareScreenStarted
    val hostLabel = updatedParameters.hostLabel
    val level = updatedParameters.level

    val updateActiveSounds = updatedParameters.updateActiveSounds
    val updateUpdateMainWindow = updatedParameters.updateUpdateMainWindow

    val reorderStreams = updatedParameters.reorderStreams
    val prepopulateUserMedia = updatedParameters.prepopulateUserMedia
    val reUpdateInter = updatedParameters.reUpdateInter

    var activeSoundsChanged = false

    for (participant in participants) {
        if (participant.muted) {
            val participantLevel = participant.islevel ?: ""
            val videoID = participant.videoID

            if (participantLevel == "2" &&
                videoID.isNotEmpty() &&
                !shared &&
                !shareScreenStarted &&
                level != "2"
            ) {
                updateUpdateMainWindow(true)
                prepopulateUserMedia(
                    PrepopulateUserMediaOptions(
                        name = hostLabel,
                        parameters = updatedParameters
                    )
                )
                updateUpdateMainWindow(false)
            }

            if (shareScreenStarted || shared) {
                if (activeSounds.contains(participant.name)) {
                    activeSounds.remove(participant.name)
                    activeSoundsChanged = true
                }

                reUpdateInter(
                    ReUpdateInterOptions(
                        name = participant.name,
                        add = false,
                        force = true,
                        parameters = updatedParameters
                    )
                )
            }
        }
    }

    if (activeSoundsChanged) {
        updateActiveSounds(activeSounds)
    }

    // Check if participant should be removed from display when they have no video in media/video display mode
    if (meetingDisplayType == "media" ||
        (meetingDisplayType == "video" && !meetingVideoOptimized)
    ) {
        val participant = participants.firstOrNull { it.name == options.name }
            ?: Participant(
                name = "",
                audioID = "",
                videoID = ""
            )
        val hasVideo = participant.videoID.isNotEmpty()

        if (!hasVideo && !(shareScreenStarted || shared)) {
            reorderStreams(
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = updatedParameters
                )
            )
        }
    }
    if (options.kind == "audio") {
        val participant = participants.firstOrNull {
            it.audioID == options.producerId || it.name == options.name
        } ?: Participant(
            name = "",
            audioID = "",
            videoID = ""
        )

        if (participant.name.isNotEmpty() && oldSoundIds.contains(participant.name)) {
            reUpdateInter(
                ReUpdateInterOptions(
                    name = participant.name,
                    add = false,
                    force = true,
                    parameters = updatedParameters
                )
            )
        }
    }
}

/** Kotlin replica of mediasfu_sdk/lib/producers/socket_receive_methods/producer_media_resumed.dart. */
suspend fun producerMediaResumed(options: ProducerMediaResumedOptions) {
    val updatedParameters = options.parameters.getUpdatedAllParams()

    val participants = updatedParameters.participants

    val participant = participants.firstOrNull { it.name == options.name }
        ?: Participant(name = "", audioID = "", videoID = "")

    if (participant.name.isNotEmpty() &&
        !updatedParameters.mainScreenFilled &&
        participant.islevel == "2"
    ) {
        updatedParameters.updateUpdateMainWindow(true)
        updatedParameters.prepopulateUserMedia(
            PrepopulateUserMediaOptions(
                name = updatedParameters.hostLabel,
                parameters = updatedParameters
            )
        )
        updatedParameters.updateUpdateMainWindow(false)
    }

    if (updatedParameters.meetingDisplayType == "media") {
        val hasVideo = participant.videoID.isNotEmpty()
        if (!hasVideo && !(updatedParameters.shareScreenStarted || updatedParameters.shared)) {
            updatedParameters.reorderStreams(
                ReorderStreamsOptions(
                    add = false,
                    screenChanged = true,
                    parameters = updatedParameters
                )
            )
        }
    }
}