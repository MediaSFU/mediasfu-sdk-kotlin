package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.MeetingStillThereOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class MeetingStillThereTest {
    @Test
    fun setsModalVisibleToTrue() = runTest {
        val updates = mutableListOf<Boolean>()
        val options = MeetingStillThereOptions { isVisible ->
            updates += isVisible
        }

        meetingStillThere(options)

        assertEquals(listOf(true), updates)
    }
}
