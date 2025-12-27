package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.DispSpecs
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.MainSpecs
import com.mediasfu.sdk.model.RecordingNoticeOptions
import com.mediasfu.sdk.model.RecordingNoticeParameters
import com.mediasfu.sdk.model.SoundPlayerOptions
import com.mediasfu.sdk.model.TextSpecs
import com.mediasfu.sdk.model.UserRecordingParams
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingNoticeParamsFixture(
    level: String,
    initialUserParams: UserRecordingParams,
    override val eventType: EventType,
    private val currentTimeMillis: Long = 25_000L
) : RecordingNoticeParameters {

    private var levelValue = level
    private var userParamsValue = initialUserParams
    private var recordElapsedTimeValue = 0
    private var recordStartTimeValue: Int? = 0
    private var recordStartedValue = false
    private var recordPausedValue = false
    private var canLaunchRecordValue = true
    private var recordStoppedValue = false
    private var isTimerRunningValue = false
    private var canPauseResumeValue = false

    var recordingProgressTimeValue: String? = null
    var showRecordButtonsValue: Boolean = false
    var recordingMediaOptionsValue: String? = null
    var recordingAudioOptionsValue: String? = null
    var recordingVideoOptionsValue: String? = null
    var recordingVideoTypeValue: String? = null
    var recordingVideoOptimizedValue: Boolean? = null
    var recordingDisplayTypeValue: String? = null
    var recordingAddHlsValue: Boolean? = null
    var recordingNameTagsValue: Boolean? = null
    var recordingBackgroundColorValue: String? = null
    var recordingNameTagsColorValue: String? = null
    var recordingOrientationVideoValue: String? = null
    var recordingAddTextValue: Boolean? = null
    var recordingCustomTextValue: String? = null
    var recordingCustomTextPositionValue: String? = null
    var recordingCustomTextColorValue: String? = null
    var pauseRecordCountValue: Int = 0
    var recordStateValue: String? = null
    val soundsPlayed = mutableListOf<String>()

    override val isLevel: String
        get() = levelValue

    override val userRecordingParams: UserRecordingParams
        get() = userParamsValue

    override val recordElapsedTime: Int
        get() = recordElapsedTimeValue

    override val recordStartTime: Int?
        get() = recordStartTimeValue

    override val recordStarted: Boolean
        get() = recordStartedValue

    override val recordPaused: Boolean
        get() = recordPausedValue

    override val canLaunchRecord: Boolean
        get() = canLaunchRecordValue

    override val recordStopped: Boolean
        get() = recordStoppedValue

    override val isTimerRunning: Boolean
        get() = isTimerRunningValue

    override val canPauseResume: Boolean
        get() = canPauseResumeValue

    override val updateRecordingProgressTime: (String) -> Unit = {
        recordingProgressTimeValue = it
    }

    override val updateShowRecordButtons: (Boolean) -> Unit = {
        showRecordButtonsValue = it
    }

    override val updateUserRecordingParams: (UserRecordingParams) -> Unit = {
        userParamsValue = it
    }

    override val updateRecordingMediaOptions: (String) -> Unit = {
        recordingMediaOptionsValue = it
    }

    override val updateRecordingAudioOptions: (String) -> Unit = {
        recordingAudioOptionsValue = it
    }

    override val updateRecordingVideoOptions: (String) -> Unit = {
        recordingVideoOptionsValue = it
    }

    override val updateRecordingVideoType: (String) -> Unit = {
        recordingVideoTypeValue = it
    }

    override val updateRecordingVideoOptimized: (Boolean) -> Unit = {
        recordingVideoOptimizedValue = it
    }

    override val updateRecordingDisplayType: (String) -> Unit = {
        recordingDisplayTypeValue = it
    }

    override val updateRecordingAddHls: (Boolean) -> Unit = {
        recordingAddHlsValue = it
    }

    override val updateRecordingNameTags: (Boolean) -> Unit = {
        recordingNameTagsValue = it
    }

    override val updateRecordingBackgroundColor: (String) -> Unit = {
        recordingBackgroundColorValue = it
    }

    override val updateRecordingNameTagsColor: (String) -> Unit = {
        recordingNameTagsColorValue = it
    }

    override val updateRecordingOrientationVideo: (String) -> Unit = {
        recordingOrientationVideoValue = it
    }

    override val updateRecordingAddText: (Boolean) -> Unit = {
        recordingAddTextValue = it
    }

    override val updateRecordingCustomText: (String) -> Unit = {
        recordingCustomTextValue = it
    }

    override val updateRecordingCustomTextPosition: (String) -> Unit = {
        recordingCustomTextPositionValue = it
    }

    override val updateRecordingCustomTextColor: (String) -> Unit = {
        recordingCustomTextColorValue = it
    }

    override val updatePauseRecordCount: (Int) -> Unit = {
        pauseRecordCountValue = it
    }

    override val updateRecordElapsedTime: (Int) -> Unit = {
        recordElapsedTimeValue = it
    }

    override val updateRecordStartTime: (Int?) -> Unit = {
        recordStartTimeValue = it
    }

    override val updateRecordStarted: (Boolean) -> Unit = {
        recordStartedValue = it
    }

    override val updateRecordPaused: (Boolean) -> Unit = {
        recordPausedValue = it
    }

    override val updateCanLaunchRecord: (Boolean) -> Unit = {
        canLaunchRecordValue = it
    }

    override val updateRecordStopped: (Boolean) -> Unit = {
        recordStoppedValue = it
    }

    override val updateIsTimerRunning: (Boolean) -> Unit = {
        isTimerRunningValue = it
    }

    override val updateCanPauseResume: (Boolean) -> Unit = {
        canPauseResumeValue = it
    }

    override val updateRecordState: (String) -> Unit = {
        recordStateValue = it
    }

    override val playSound: (SoundPlayerOptions) -> Unit = { options ->
        soundsPlayed.add(options.soundUrl)
    }

    override val currentTimeProvider: () -> Long = {
        currentTimeMillis
    }

    override fun getUpdatedAllParams(): RecordingNoticeParameters = this
}

private fun defaultUserRecordingParams(): UserRecordingParams = UserRecordingParams(
    mainSpecs = MainSpecs(
        mediaOptions = "media",
        audioOptions = "audio",
        videoOptions = "video",
        videoType = "HD",
        videoOptimized = true,
        recordingDisplayType = "display",
        addHls = true
    ),
    dispSpecs = DispSpecs(
        nameTags = true,
        backgroundColor = "#FFFFFF",
        nameTagsColor = "#000000",
        orientationVideo = "landscape"
    ),
    textSpecs = TextSpecs(
        addText = true,
        customText = "Live",
        customTextPosition = "top",
        customTextColor = "#FF0000"
    )
)

class RecordingNoticeTest {
    @Test
    fun nonAdminPauseSetsYellowAndPlaysSound() = runTest {
        val params = RecordingNoticeParamsFixture(
            level = "1",
            initialUserParams = defaultUserRecordingParams(),
            eventType = EventType.CONFERENCE
        )

        val options = RecordingNoticeOptions(
            state = "pause",
            userRecordingParams = null,
            pauseCount = 0,
            timeDone = 0,
            parameters = params
        )

        recordingNotice(options)

        assertEquals("yellow", params.recordStateValue)
        assertTrue(params.recordPaused)
        assertTrue(params.recordStarted)
        assertEquals(
            "https://www.mediasfu.com/sounds/record-paused.mp3",
            params.soundsPlayed.last()
        )
    }

    @Test
    fun broadcastSkipSoundForPause() = runTest {
        val params = RecordingNoticeParamsFixture(
            level = "1",
            initialUserParams = defaultUserRecordingParams(),
            eventType = EventType.BROADCAST
        )

        val options = RecordingNoticeOptions(
            state = "pause",
            userRecordingParams = null,
            pauseCount = 0,
            timeDone = 0,
            parameters = params
        )

        recordingNotice(options)

        assertTrue(params.soundsPlayed.isEmpty())
    }

    @Test
    fun adminPauseUpdatesRecordingParamsAndTimers() = runTest {
        val params = RecordingNoticeParamsFixture(
            level = "2",
            initialUserParams = defaultUserRecordingParams(),
            eventType = EventType.CONFERENCE,
            currentTimeMillis = 30_000L
        )

        val newParams = defaultUserRecordingParams()

        val options = RecordingNoticeOptions(
            state = "pause",
            userRecordingParams = newParams,
            pauseCount = 2,
            timeDone = 5_000,
            parameters = params
        )

        recordingNotice(options)

        assertEquals("yellow", params.recordStateValue)
        assertTrue(params.recordPaused)
        assertTrue(params.recordStarted)
        assertFalse(params.canLaunchRecord)
        assertTrue(params.showRecordButtonsValue)
        assertTrue(params.canPauseResume)
        assertEquals(2, params.pauseRecordCountValue)
        assertEquals(5, params.recordElapsedTime)
        assertEquals((30_000 / 1_000) - 5, params.recordStartTime)
        assertEquals("00:00:05", params.recordingProgressTimeValue)
        assertEquals("media", params.recordingMediaOptionsValue)
        assertEquals(true, params.recordingAddTextValue)
        assertEquals("Live", params.recordingCustomTextValue)
        assertEquals(
            "https://www.mediasfu.com/sounds/record-paused.mp3",
            params.soundsPlayed.last()
        )
    }
}