package com.mediasfu.sdk.ui.components.polls
import com.mediasfu.sdk.socket.SocketManager
import com.mediasfu.sdk.model.*
import com.mediasfu.sdk.model.*

import com.mediasfu.sdk.model.Poll
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.participants.ShowAlertOptions

/**
 * PollModal - Modal for creating, viewing, and voting on polls.
 *
 * Provides interface for poll management including creation, voting, and viewing results.
 *
 * @property options Configuration options for the poll modal
 */
data class PollModalOptions(
    val isPollModalVisible: Boolean = false,
    val onClose: () -> Unit,
    val position: String = "topRight",
    val backgroundColor: Int = 0xFFF5F5F5.toInt(),
    val member: String,
    val islevel: String,
    val polls: List<Poll>,
    val poll: Poll?,
    val socket: SocketManager?,
    val roomName: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val updateIsPollModalVisible: (Boolean) -> Unit,
    val handleCreatePoll: (HandleCreatePollOptions) -> Unit,
    val handleEndPoll: (HandleEndPollOptions) -> Unit,
    val handleVotePoll: (HandleVotePollOptions) -> Unit,
)

data class HandleCreatePollOptions(
    val poll: Poll,
    val socket: SocketManager?,
    val roomName: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val updateIsPollModalVisible: (Boolean) -> Unit,
)

data class HandleEndPollOptions(
    val pollId: String,
    val socket: SocketManager?,
    val roomName: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
    val updateIsPollModalVisible: (Boolean) -> Unit,
)

data class HandleVotePollOptions(
    val pollId: String,
    val optionIndex: Int,
    val socket: SocketManager?,
    val member: String,
    val roomName: String,
    val showAlert: ((ShowAlertOptions) -> Unit)?,
)

interface PollModal : MediaSfuUIComponent {
    val options: PollModalOptions
    override val id: String get() = "poll_modal"
    override val isVisible: Boolean get() = true
    override val isEnabled: Boolean get() = true
    
    override fun show() {}
    override fun hide() {}
    override fun enable() {}
    override fun disable() {}
    override fun dispose() {}
    
    
    /**
     * Checks if user can create polls
     */
    fun canCreatePoll(): Boolean {
        return options.islevel == "2"
    }
    
    /**
     * Checks if user can end poll
     */
    fun canEndPoll(): Boolean {
        return options.islevel == "2"
    }
    
    /**
     * Gets active poll if any
     */
    fun getActivePoll(): Poll? {
        return options.polls.find { it.status == "active" } ?: options.poll
    }
    
    /**
     * Gets previous polls
     */
    fun getPreviousPolls(): List<Poll> {
        return options.polls.filter { it.status != "active" }
    }
    
    /**
     * Checks if user has voted on poll
     */
    fun hasVoted(poll: Poll): Boolean {
        return poll.voters?.containsKey(options.member) == true
    }
}

/**
 * Default implementation of PollModal
 */
class DefaultPollModal(
    override val options: PollModalOptions
) : PollModal {
    fun render(): Any {
        val activePoll = getActivePoll()
        
        return mapOf(
            "type" to "pollModal",
            "isVisible" to options.isPollModalVisible,
            "onClose" to options.onClose,
            "backgroundColor" to options.backgroundColor,
            "position" to options.position,
            "canCreatePoll" to canCreatePoll(),
            "canEndPoll" to canEndPoll(),
            "activePoll" to activePoll,
            "previousPolls" to getPreviousPolls(),
            "hasVoted" to (activePoll?.let { hasVoted(it) } ?: false),
            "onCreatePoll" to options.handleCreatePoll,
            "onEndPoll" to options.handleEndPoll,
            "onVotePoll" to options.handleVotePoll
        )
    }
}
