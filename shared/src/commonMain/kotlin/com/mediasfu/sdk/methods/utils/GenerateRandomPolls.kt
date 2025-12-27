package com.mediasfu.sdk.methods.utils

import com.mediasfu.sdk.model.Poll
import kotlin.random.Random

/**
 * Options for generating random polls.
 */
data class GenerateRandomPollsOptions(
    val numberOfPolls: Int
)

/**
 * Type definition for the poll generator function.
 */
typealias GenerateRandomPollsType = (GenerateRandomPollsOptions) -> List<Poll>

/**
 * Generates a list of random polls for testing and demo scenarios.
 */
fun generateRandomPolls(options: GenerateRandomPollsOptions): List<Poll> {
    if (options.numberOfPolls <= 0) {
        return emptyList()
    }

    val pollTypes = listOf("trueFalse", "yesNo", "custom")
    val random = Random.Default

    return (0 until options.numberOfPolls).map { index ->
        val type = pollTypes[random.nextInt(pollTypes.size)]
        val optionList = when (type) {
            "trueFalse" -> listOf("True", "False")
            "yesNo" -> listOf("Yes", "No")
            else -> List(random.nextInt(5) + 2) { optionIndex -> "Option ${optionIndex + 1}" }
        }

        Poll(
            id = "${index + 1}",
            question = "Random Question ${index + 1}",
            type = type,
            options = optionList,
            votes = List(optionList.size) { 0 },
            status = "inactive",
            voters = emptyMap()
        )
    }
}
