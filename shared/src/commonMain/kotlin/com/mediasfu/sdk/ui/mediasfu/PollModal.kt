package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ArrowDropUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.mediasfu.sdk.model.Poll
import kotlin.math.roundToInt

private const val POLL_TYPE_CHOOSE = "choose"
private const val POLL_TYPE_TRUE_FALSE = "trueFalse"
private const val POLL_TYPE_YES_NO = "yesNo"
private const val POLL_TYPE_CUSTOM = "custom"
private const val MIN_CUSTOM_OPTIONS = 2
private const val MAX_CUSTOM_OPTIONS = 5

@Composable
internal fun PollModal(state: MediasfuGenericState) {
    val pollsState = state.polls
    if (!pollsState.isPollModalVisible) return

    val room = state.room
    val parameters = state.parameters
    val memberName = room.member.ifBlank { parameters.member }
    val isHost = room.youAreHost || room.islevel.equals("2", ignoreCase = true)

    val activePollCandidate = pollsState.activePoll
    val activePoll = if (activePollCandidate?.isActive() == true) {
        activePollCandidate
    } else {
        pollsState.polls.firstOrNull { it.isActive() }
    }
    val previousPolls = pollsState.polls.filter { poll ->
        val hasId = !poll.id.isNullOrBlank()
        val isCurrent = activePoll?.id != null && poll.id == activePoll.id && activePoll?.isActive() == true
        hasId && !isCurrent
    }

    val questionState = remember(pollsState.isPollModalVisible) { mutableStateOf("") }
    val pollTypeState = remember(pollsState.isPollModalVisible) { mutableStateOf(POLL_TYPE_CHOOSE) }
    val customOptionInputs = remember(pollsState.isPollModalVisible) {
        mutableStateListOf("", "")
    }
    val submittingState = remember { mutableStateOf(false) }
    val votingIndexState = remember { mutableStateOf<Int?>(null) }
    val endingPollIdState = remember { mutableStateOf<String?>(null) }

    val pollTypeOptions = remember {
        listOf(
            MediasfuPollTypeOption(POLL_TYPE_CHOOSE, "Choose..."),
            MediasfuPollTypeOption(POLL_TYPE_TRUE_FALSE, "True/False"),
            MediasfuPollTypeOption(POLL_TYPE_YES_NO, "Yes/No"),
            MediasfuPollTypeOption(POLL_TYPE_CUSTOM, "Custom")
        )
    }

    val resetCreatePollState: () -> Unit = {
        questionState.value = ""
        pollTypeState.value = POLL_TYPE_CHOOSE
        customOptionInputs.clear()
        repeat(MIN_CUSTOM_OPTIONS) { customOptionInputs.add("") }
    }

    val handleEndPoll: (String) -> Unit = { pollId ->
        if (pollId.isNotBlank() && endingPollIdState.value == null) {
            endingPollIdState.value = pollId
            state.endPoll(pollId) {
                endingPollIdState.value = null
            }
        }
    }

    val props = PollModalProps(
        state = state,
        isVisible = pollsState.isPollModalVisible,
        isHost = isHost,
        memberName = memberName,
        activePoll = activePoll,
        previousPolls = previousPolls,
        pollTypeOptions = pollTypeOptions,
        questionState = questionState,
        pollTypeState = pollTypeState,
        customOptionInputs = customOptionInputs,
        submittingState = submittingState,
        votingIndexState = votingIndexState,
        endingPollIdState = endingPollIdState,
        resetCreatePollState = resetCreatePollState,
        handleEndPoll = handleEndPoll,
        onDismiss = { state.setPollModalVisible(false) }
    )

    val contentBuilder = withOverride(
        override = state.options.uiOverrides.pollModal,
        baseBuilder = { DefaultPollModalContent(it) }
    )

    contentBuilder(props)
}

/**
 * Poll Modal Content Body - The main content without dialog wrapper.
 * Can be used in unified modal system.
 */
@Composable
fun PollModalContentBody(
    props: PollModalProps,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val dividerColor = MaterialTheme.colorScheme.surfaceVariant
    val state = props.state

    val question = props.questionState.value
    val pollType = props.pollTypeState.value
    val submitting = props.submittingState.value
    val votingIndex = props.votingIndexState.value
    val endingPollId = props.endingPollIdState.value
    val customOptionInputs = props.customOptionInputs
    val activePoll = props.activePoll
    val previousPolls = props.previousPolls
    val pollTypeOptions = props.pollTypeOptions
    val memberName = props.memberName
    val isHost = props.isHost

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // FIXED: Previous Polls section now only shown to hosts/moderators
        if (isHost) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Previous Polls", style = MaterialTheme.typography.titleMedium)
                if (previousPolls.isEmpty()) {
                    Text(
                        text = "No polls available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    previousPolls.forEach { poll ->
                        PreviousPollCard(
                            poll = poll,
                            isHost = isHost,
                            onEndPoll = { id -> props.handleEndPoll(id) },
                            isEnding = endingPollId == poll.id
                        )
                    }
                }
            }
            HorizontalDivider(color = dividerColor)
        }

        if (isHost) {
            CreatePollSection(
                question = question,
                pollType = pollType,
                pollTypeOptions = pollTypeOptions,
                customOptions = customOptionInputs,
                onQuestionChange = { props.questionState.value = it },
                onPollTypeChange = { newType ->
                    props.pollTypeState.value = newType
                    if (newType == POLL_TYPE_CUSTOM && customOptionInputs.size < MIN_CUSTOM_OPTIONS) {
                        while (customOptionInputs.size < MIN_CUSTOM_OPTIONS) {
                            customOptionInputs.add("")
                        }
                    }
                },
                onCustomOptionChange = { index, value ->
                    if (index in 0 until customOptionInputs.size) {
                        customOptionInputs[index] = value
                    }
                },
                onAddCustomOption = {
                    if (customOptionInputs.size < MAX_CUSTOM_OPTIONS) {
                        customOptionInputs.add("")
                    }
                },
                onRemoveCustomOption = { index ->
                    if (customOptionInputs.size > MIN_CUSTOM_OPTIONS && index in 0 until customOptionInputs.size) {
                        customOptionInputs.removeAt(index)
                    }
                },
                onSubmit = {
                    if (submitting) return@CreatePollSection
                    val staticOptions = pollOptionsForType(pollType)
                    val submissionOptions = staticOptions ?: customOptionInputs.toList()
                    val submissionType = when {
                        pollType == POLL_TYPE_CUSTOM -> POLL_TYPE_CUSTOM
                        staticOptions != null -> pollType
                        else -> "singleChoice"
                    }
                    props.submittingState.value = true
                    state.createPoll(
                        question,
                        submissionOptions,
                        submissionType
                    ) {
                        props.submittingState.value = false
                        props.resetCreatePollState()
                    }
                },
                submitting = submitting
            )
            HorizontalDivider(color = dividerColor)
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Current Poll", style = MaterialTheme.typography.titleMedium)
            if (activePoll != null) {
                ActivePollSection(
                    poll = activePoll,
                    memberName = memberName,
                    votingIndex = votingIndex,
                    isHost = isHost,
                    onVote = { index ->
                        if (props.votingIndexState.value != null) return@ActivePollSection
                        val pollId = activePoll.id.orEmpty()
                        if (pollId.isBlank()) return@ActivePollSection
                        props.votingIndexState.value = index
                        state.voteInPoll(pollId, index) {
                            props.votingIndexState.value = null
                        }
                    },
                    onEndPoll = {
                        val pollId = activePoll.id.orEmpty()
                        if (pollId.isNotBlank()) {
                            props.handleEndPoll(pollId)
                        }
                    },
                    isEnding = endingPollId == activePoll.id
                )
            } else {
                Text(
                    text = "No active poll at the moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DefaultPollModalContent(props: PollModalProps) {
    // Wrap content body in AlertDialog for standalone modal usage
    AlertDialog(
        onDismissRequest = props.onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Polls",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = props.onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close polls")
                }
            }
        },
        text = {
            PollModalContentBody(
                props = props,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 440.dp)
                    .heightIn(max = 520.dp)
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
}

// ActivePollSection - displays current poll for voting (matches React/Flutter behavior)
@Composable
private fun ActivePollSection(
    poll: Poll,
    memberName: String,
    votingIndex: Int?,
    isHost: Boolean,
    onVote: (Int) -> Unit,
    onEndPoll: () -> Unit,
    isEnding: Boolean
) {
    val votedIndex = poll.voters[memberName]
    val pollActive = poll.isActive()
    val hasVoted = votedIndex != null
    val isVoting = votingIndex != null

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = poll.question,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        // Display voting options - simple buttons like React/Flutter
        if (poll.options.isNotEmpty()) {
            poll.options.forEachIndexed { index, option ->
                val selected = votedIndex == index || votingIndex == index
                val canVote = pollActive && !hasVoted && !isVoting
                val isSubmitting = votingIndex == index
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = if (selected) 2.dp else 1.dp,
                    onClick = if (canVote) { { onVote(index) } } else { {} }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                enabled = canVote,
                                colors = RadioButtonDefaults.colors(
                                    disabledSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                            )
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No options available for this poll.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (hasVoted && votedIndex != null && votedIndex in poll.options.indices) {
            Text(
                text = "You voted for \"${poll.options[votedIndex]}\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (pollActive && isHost && poll.id != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onEndPoll,
                enabled = !isEnding,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(if (isEnding) "Ending..." else "End Poll")
            }
        }
    }
}

// FIXED: PollOptionWithProgress now includes percentage display and progress bar
@Composable
private fun PollOptionWithProgress(
    option: String,
    votes: Int,
    percentage: Float,
    selected: Boolean,
    onClick: (() -> Unit)?,
    isSubmitting: Boolean = false
) {
    val progress = percentage / 100f
    val radioColors = RadioButtonDefaults.colors(
        disabledSelectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = onClick,
                        enabled = onClick != null,
                        colors = radioColors
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option, fontWeight = FontWeight.Medium)
                        Text(
                            text = "${formatVoteCount(votes)} (${formatPercentageLabel(percentage)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CreatePollSection(
    question: String,
    pollType: String,
    pollTypeOptions: List<MediasfuPollTypeOption>,
    customOptions: SnapshotStateList<String>,
    onQuestionChange: (String) -> Unit,
    onPollTypeChange: (String) -> Unit,
    onCustomOptionChange: (Int, String) -> Unit,
    onAddCustomOption: () -> Unit,
    onRemoveCustomOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    submitting: Boolean
) {
    val staticOptions = pollOptionsForType(pollType)
    val isCustomType = pollType == POLL_TYPE_CUSTOM

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Create a New Poll", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = question,
            onValueChange = onQuestionChange,
            label = { Text("Poll question") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3
        )
        PollTypeDropdown(
            options = pollTypeOptions,
            selectedValue = pollType,
            onValueSelected = onPollTypeChange
        )
        when {
            staticOptions != null -> StaticOptionPreviewList(staticOptions)
            isCustomType -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    customOptions.forEachIndexed { index, value ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { onCustomOptionChange(index, it) },
                                label = { Text("Option ${index + 1}") },
                                modifier = Modifier.weight(1f),
                                singleLine = false,
                                maxLines = 2
                            )
                            if (customOptions.size > MIN_CUSTOM_OPTIONS) {
                                IconButton(onClick = { onRemoveCustomOption(index) }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Remove option")
                                }
                            }
                        }
                    }
                    Text(
                        text = "Provide at least $MIN_CUSTOM_OPTIONS and at most $MAX_CUSTOM_OPTIONS options.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Text(
                    text = "Select a poll type to configure answer options.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isCustomType) Arrangement.SpaceBetween else Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isCustomType) {
                OutlinedButton(onClick = onAddCustomOption, enabled = customOptions.size < MAX_CUSTOM_OPTIONS) {
                    Text("Add option")
                }
            }
            Button(onClick = onSubmit, enabled = !submitting) {
                Text(if (submitting) "Creating..." else "Create Poll")
            }
        }
    }
}

@Composable
private fun PreviousPollCard(
    poll: Poll,
    isHost: Boolean,
    onEndPoll: ((String) -> Unit)?,
    isEnding: Boolean
) {
    val voteCounts = poll.voteCounts()
    val optionsWithCounts = if (poll.options.isNotEmpty()) {
        poll.options.mapIndexed { index, option ->
            option to voteCounts.getOrElse(index) { 0 }
        }
    } else {
        voteCounts.mapIndexed { index, count ->
            "Option ${index + 1}" to count
        }
    }
    val totalVotes = optionsWithCounts.sumOf { it.second }
    val pollId = poll.id
    val endPollHandler = onEndPoll
    val hasOptions = optionsWithCounts.isNotEmpty()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Question: ${poll.question}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (hasOptions) {
                optionsWithCounts.forEach { (option, votes) ->
                    val percentage = calculatePercentage(votes, totalVotes)
                    Text(
                        text = "$option: $votes votes (${formatPercentageLabel(percentage)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Total votes: ${formatVoteCount(totalVotes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No responses recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isHost && poll.isActive() && pollId != null && endPollHandler != null) {
                Button(
                    onClick = { endPollHandler(pollId) },
                    enabled = !isEnding,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (isEnding) "Ending..." else "End Poll")
                }
            }
        }
    }
}

@Composable
private fun PollTypeDropdown(
    options: List<MediasfuPollTypeOption>,
    selectedValue: String,
    onValueSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var textFieldSize by remember { mutableStateOf(Size.Zero) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label
        ?: options.firstOrNull()?.label.orEmpty()
    val density = LocalDensity.current
    val dropdownWidth = if (textFieldSize.width > 0) {
        with(density) { textFieldSize.width.toDp() }
    } else {
        Dp.Unspecified
    }
    val isPlaceholder = selectedValue == POLL_TYPE_CHOOSE

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Poll type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Surface(
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                onClick = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        textFieldSize = coordinates.size.toSize()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPlaceholder) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ArrowDropUp else Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = if (dropdownWidth != Dp.Unspecified) Modifier.width(dropdownWidth) else Modifier
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onValueSelected(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun StaticOptionPreviewList(options: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = false, onClick = null, enabled = false)
                    Text(option, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun pollOptionsForType(type: String): List<String>? = when (type) {
    POLL_TYPE_TRUE_FALSE -> listOf("True", "False")
    POLL_TYPE_YES_NO -> listOf("Yes", "No")
    else -> null
}

internal fun Poll.isActive(): Boolean = status.equals("active", ignoreCase = true)

private fun Poll.voteCounts(): List<Int> {
    val votesArray = votes.takeIf { it.size == options.size }
    val votesWithValues = votesArray?.takeIf { it.any { count -> count > 0 } }

    if (votesWithValues != null) return votesWithValues

    val derivedFromVoters = if (voters.isNotEmpty()) {
        MutableList(options.size) { 0 }.apply {
            voters.values.forEach { index ->
                if (index in indices) {
                    this[index] = this[index] + 1
                }
            }
        }
    } else null

    return derivedFromVoters ?: votesArray ?: List(options.size) { 0 }
}

private fun Poll.voteCountAt(index: Int): Int = voteCounts().getOrElse(index) { 0 }

private fun formatVoteCount(count: Int): String = "$count vote${if (count == 1) "" else "s"}"

private fun calculatePercentage(votes: Int, totalVotes: Int): Float {
    if (totalVotes <= 0) return 0f
    return (votes.toFloat() / totalVotes * 100f).coerceIn(0f, 100f)
}

private fun formatPercentageLabel(percentage: Float): String {
    val rounded = (percentage * 10f).roundToInt() / 10f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    return "$text%"
}

private fun formatStatusLabel(status: String?): String? {
    return status?.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}