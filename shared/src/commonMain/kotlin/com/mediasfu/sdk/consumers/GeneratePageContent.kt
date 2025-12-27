package com.mediasfu.sdk.consumers
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.model.Stream

/**
 * Parameters used for generating page content for a specific user interface page.
 */
interface GeneratePageContentParameters : DispStreamsParameters {
    val paginatedStreams: List<List<Any>>

    // Update functions
    val updateCurrentUserPage: (Int) -> Unit

    // Mediasfu function
    val dispStreams: suspend (DispStreamsOptions) -> Unit

    // Method to retrieve updated parameters
    override fun getUpdatedAllParams(): GeneratePageContentParameters
}

/**
 * Options for generating page content.
 *
 * @property page The page number to generate content for
 * @property parameters The parameters containing paginated streams and display logic
 * @property breakRoom The breakout room number (-1 if not in a breakout room)
 * @property inBreakRoom Whether the user is currently in a breakout room
 */
data class GeneratePageContentOptions(
    val page: Int,
    val parameters: GeneratePageContentParameters,
    val breakRoom: Int = -1,
    val inBreakRoom: Boolean = false
)

// DispStreamsParameters is now defined in DispStreams.kt

// DispStreamsOptions is now defined in DispStreams.kt

/**
 * Generates the content for a specific page based on the provided options.
 *
 * This function updates the page content for the given page in the options,
 * which includes updating the main window and setting the current page. It then
 * calls the dispStreams function to display the streams for that page.
 *
 * @param options The options containing the page number and parameters
 *
 * Example:
 * ```kotlin
 * val options = GeneratePageContentOptions(
 *     page = 1,
 *     parameters = object : GeneratePageContentParameters {
 *         override val paginatedStreams = listOf(
 *             listOf(stream1, stream2),
 *             listOf(stream3, stream4)
 *         )
 *         override val currentUserPage = 0
 *         override val updateMainWindow = true
 *         override val updateCurrentUserPage = { page ->
 *         }
 *         override val updateUpdateMainWindow = { flag ->
 *         }
 *         override val dispStreams = { dispOptions ->
 *         }
 *         override fun getUpdatedAllParams() = this
 *     }
 * )
 *
 * generatePageContent(options)
 * ```
 */
suspend fun generatePageContent(options: GeneratePageContentOptions) {
    try {
        val paginatedStreams = options.parameters.paginatedStreams
        var currentUserPage = options.parameters.currentUserPage
        var updateMainWindow = options.parameters.updateMainWindow
        val updateCurrentUserPage = options.parameters.updateCurrentUserPage
        val updateUpdateMainWindow = options.parameters.updateUpdateMainWindow
        val dispStreams = options.parameters.dispStreams

        // Convert page to an integer if needed
        val page = options.page

        // Choose a safe page index: clamp to available pages when paginatedStreams is non-empty
        val hasPages = paginatedStreams.isNotEmpty()
        val safeIndex = when {
            !hasPages -> page
            paginatedStreams.size == 1 -> 0
            else -> page.coerceIn(0, paginatedStreams.size - 1)
        }

        // Update current user page
        currentUserPage = safeIndex
        updateCurrentUserPage(currentUserPage)

        // Update main window flag
        updateMainWindow = true
        updateUpdateMainWindow(updateMainWindow)

        // Safely get the streams for the page, using empty list if none available
        val streamsForPage = if (hasPages) {
            paginatedStreams[safeIndex] as List<Stream>
        } else {
            emptyList()
        }

        // Display streams for the specified page
        val dispOptions = DispStreamsOptions(
            lStreams = streamsForPage,
            ind = page,
            parameters = options.parameters,
            breakRoom = options.breakRoom,
            inBreakRoom = options.inBreakRoom
        )
        dispStreams(dispOptions)
    } catch (e: Exception) {
        Logger.e("GeneratePageContent", "Error generating page content: ${e.message}")
    }
}

