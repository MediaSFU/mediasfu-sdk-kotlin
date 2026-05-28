package com.mediasfu.sdk.ui.mediasfu

import com.mediasfu.sdk.methods.MediasfuParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaStateCallbackBridgeTest {
    private fun createState(parameters: MediasfuParameters): MediasfuGenericState {
        val options = MediasfuGenericOptions(
            sourceParameters = parameters,
            connectMediaSFU = false,
            returnUI = false,
        )

        return MediasfuGenericState(
            scope = CoroutineScope(SupervisorJob()),
            parameters = parameters,
            options = options,
        )
    }

    @Test
    fun `audio callback survives click adapter updates`() {
        val parameters = MediasfuParameters()
        val audioEvents = mutableListOf<Boolean>()
        parameters.onAudioAlreadyOnChanged = { value ->
            audioEvents += value
        }

        val state = createState(parameters)
        val clickAudioParameters = state.createClickAudioParameters()

        clickAudioParameters.updateAudioAlreadyOn(true)

        assertTrue(parameters.audioAlreadyOn)
        assertTrue(state.media.audioAlreadyOn)
        assertEquals(listOf(true), audioEvents)
    }

    @Test
    fun `video callback survives click adapter updates`() {
        val parameters = MediasfuParameters()
        val videoEvents = mutableListOf<Boolean>()
        parameters.onVideoAlreadyOnChanged = { value ->
            videoEvents += value
        }

        val state = createState(parameters)
        val clickVideoParameters = state.createClickVideoParameters()

        clickVideoParameters.updateVideoAlreadyOn(true)

        assertTrue(parameters.videoAlreadyOn)
        assertTrue(state.media.videoAlreadyOn)
        assertEquals(listOf(true), videoEvents)
    }
}