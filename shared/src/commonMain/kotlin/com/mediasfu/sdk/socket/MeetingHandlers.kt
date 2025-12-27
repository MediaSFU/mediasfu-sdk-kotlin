package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Request
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.WaitingRoomParticipant
import kotlinx.coroutines.delay

/** Kotlin replicas of mediasfu_sdk/lib/producers/socket_receive_methods meeting-related handlers. */

data class MeetingEndedOptions(
    val showAlert: ShowAlert? = null,
    val redirectUrl: String? = null,
    val onWeb: Boolean,
    val eventType: EventType,
    val updateValidated: ((Boolean) -> Unit)? = null
)

suspend fun meetingEnded(options: MeetingEndedOptions) {
    if (options.eventType != EventType.CHAT) {
        options.showAlert?.invoke(
            "The meeting has ended. Redirecting to the home page...",
            "danger",
            2_000
        )
    }

    if (options.onWeb && !options.redirectUrl.isNullOrEmpty()) {
        delay(2_000)
    } else if (options.updateValidated != null) {
        delay(2_000)
        options.updateValidated.invoke(false)
    }
}

data class MeetingStillThereOptions(
    val updateIsConfirmHereModalVisible: (Boolean) -> Unit
)

fun meetingStillThere(options: MeetingStillThereOptions) {
    options.updateIsConfirmHereModalVisible(true)
}

data class MeetingTimeRemainingOptions(
    val timeRemainingMillis: Int,
    val showAlert: ShowAlert? = null,
    val eventType: EventType
)

fun meetingTimeRemaining(options: MeetingTimeRemainingOptions) {
    val minutes = options.timeRemainingMillis / 60_000
    val seconds = (options.timeRemainingMillis % 60_000) / 1_000
    val timeRemainingString = "$minutes:${seconds.toString().padStart(2, '0')}"

    if (options.eventType != EventType.CHAT) {
        options.showAlert?.invoke(
            "The event will end in $timeRemainingString minutes.",
            "success",
            3_000
        )
    }
}

data class ParticipantRequestedOptions(
    val userRequest: Request,
    val requestList: List<Request>,
    val waitingRoomList: List<WaitingRoomParticipant>,
    val updateTotalReqWait: (Int) -> Unit,
    val updateRequestList: (List<Request>) -> Unit
)

fun participantRequested(options: ParticipantRequestedOptions) {
    val updatedRequestList = options.requestList.toMutableList().apply {
        add(options.userRequest)
    }
    options.updateRequestList(updatedRequestList)

    val reqCount = updatedRequestList.size + options.waitingRoomList.size
    options.updateTotalReqWait(reqCount)
}

data class PersonJoinedOptions(
    val name: String,
    val showAlert: ShowAlert? = null
)

fun personJoined(options: PersonJoinedOptions) {
    options.showAlert?.invoke(
        "${options.name} has joined the event.",
        "success",
        3_000
    )
}
