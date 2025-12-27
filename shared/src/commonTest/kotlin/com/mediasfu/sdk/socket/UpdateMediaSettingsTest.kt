package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.Settings
import com.mediasfu.sdk.model.UpdateMediaSettingsOptions
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateMediaSettingsTest {

    @Test
    fun `updates all settings with full list`() {
        var audio = ""
        var video = ""
        var screenshare = ""
        var chat = ""

        val options = UpdateMediaSettingsOptions(
            settings = Settings(settings = listOf("enabled", "disabled", "approval", "allow")),
            updateAudioSetting = { audio = it },
            updateVideoSetting = { video = it },
            updateScreenshareSetting = { screenshare = it },
            updateChatSetting = { chat = it }
        )

        updateMediaSettings(options)

        assertEquals("enabled", audio)
        assertEquals("disabled", video)
        assertEquals("approval", screenshare)
        assertEquals("allow", chat)
    }

    @Test
    fun `uses defaults for missing settings`() {
        var audio = ""
        var video = ""
        var screenshare = ""
        var chat = ""

        val options = UpdateMediaSettingsOptions(
            settings = Settings(settings = listOf("enabled")),
            updateAudioSetting = { audio = it },
            updateVideoSetting = { video = it },
            updateScreenshareSetting = { screenshare = it },
            updateChatSetting = { chat = it }
        )

        updateMediaSettings(options)

        assertEquals("enabled", audio)
        assertEquals("allow", video)
        assertEquals("allow", screenshare)
        assertEquals("allow", chat)
    }

    @Test
    fun `handles empty settings list`() {
        var audio = ""
        var video = ""
        var screenshare = ""
        var chat = ""

        val options = UpdateMediaSettingsOptions(
            settings = Settings(settings = emptyList()),
            updateAudioSetting = { audio = it },
            updateVideoSetting = { video = it },
            updateScreenshareSetting = { screenshare = it },
            updateChatSetting = { chat = it }
        )

        updateMediaSettings(options)

        assertEquals("allow", audio)
        assertEquals("allow", video)
        assertEquals("allow", screenshare)
        assertEquals("allow", chat)
    }

    @Test
    fun `handles partial settings list`() {
        var audio = ""
        var video = ""
        var screenshare = ""
        var chat = ""

        val options = UpdateMediaSettingsOptions(
            settings = Settings(settings = listOf("disabled", "enabled")),
            updateAudioSetting = { audio = it },
            updateVideoSetting = { video = it },
            updateScreenshareSetting = { screenshare = it },
            updateChatSetting = { chat = it }
        )

        updateMediaSettings(options)

        assertEquals("disabled", audio)
        assertEquals("enabled", video)
        assertEquals("allow", screenshare)
        assertEquals("allow", chat)
    }

    @Test
    fun `handles exception gracefully`() {
        val options = UpdateMediaSettingsOptions(
            settings = Settings(settings = listOf("enabled")),
            updateAudioSetting = { throw RuntimeException("Test error") },
            updateVideoSetting = {},
            updateScreenshareSetting = {},
            updateChatSetting = {}
        )

        // Should not throw
        updateMediaSettings(options)
    }
}
