package com.mediasfu.sdk.socket

import com.mediasfu.sdk.model.RecordingParams
import com.mediasfu.sdk.model.RoomRecordParamsOptions
import com.mediasfu.sdk.model.RoomRecordParamsParameters
import kotlin.test.Test
import kotlin.test.assertEquals

private class RoomRecordParamsFixture : RoomRecordParamsParameters {
    var audioPausesLimit: Int? = null
    var audioPausesCount: Int? = null
    var audioSupport: Boolean? = null
    var audioPeopleLimit: Int? = null
    var audioParticipantsTimeLimit: Int? = null
    var videoPausesCount: Int? = null
    var videoPausesLimit: Int? = null
    var videoSupport: Boolean? = null
    var videoPeopleLimit: Int? = null
    var videoParticipantsTimeLimit: Int? = null
    var allParticipantsSupport: Boolean? = null
    var videoParticipantsSupport: Boolean? = null
    var allParticipantsFullRoomSupport: Boolean? = null
    var videoParticipantsFullRoomSupport: Boolean? = null
    var preferredOrientation: String? = null
    var supportForOtherOrientation: Boolean? = null
    var multiFormatsSupport: Boolean? = null

    override val updateRecordingAudioPausesLimit: (Int) -> Unit = { audioPausesLimit = it }
    override val updateRecordingAudioPausesCount: (Int) -> Unit = { audioPausesCount = it }
    override val updateRecordingAudioSupport: (Boolean) -> Unit = { audioSupport = it }
    override val updateRecordingAudioPeopleLimit: (Int) -> Unit = { audioPeopleLimit = it }
    override val updateRecordingAudioParticipantsTimeLimit: (Int) -> Unit = { audioParticipantsTimeLimit = it }
    override val updateRecordingVideoPausesCount: (Int) -> Unit = { videoPausesCount = it }
    override val updateRecordingVideoPausesLimit: (Int) -> Unit = { videoPausesLimit = it }
    override val updateRecordingVideoSupport: (Boolean) -> Unit = { videoSupport = it }
    override val updateRecordingVideoPeopleLimit: (Int) -> Unit = { videoPeopleLimit = it }
    override val updateRecordingVideoParticipantsTimeLimit: (Int) -> Unit = { videoParticipantsTimeLimit = it }
    override val updateRecordingAllParticipantsSupport: (Boolean) -> Unit = { allParticipantsSupport = it }
    override val updateRecordingVideoParticipantsSupport: (Boolean) -> Unit = { videoParticipantsSupport = it }
    override val updateRecordingAllParticipantsFullRoomSupport: (Boolean) -> Unit = { allParticipantsFullRoomSupport = it }
    override val updateRecordingVideoParticipantsFullRoomSupport: (Boolean) -> Unit = { videoParticipantsFullRoomSupport = it }
    override val updateRecordingPreferredOrientation: (String) -> Unit = { preferredOrientation = it }
    override val updateRecordingSupportForOtherOrientation: (Boolean) -> Unit = { supportForOtherOrientation = it }
    override val updateRecordingMultiFormatsSupport: (Boolean) -> Unit = { multiFormatsSupport = it }
}

class RoomRecordParamsTest {
    @Test
    fun updatesAllRecordingParameters() {
        val recordParams = RecordingParams(
            recordingAudioPausesLimit = 3,
            recordingAudioSupport = true,
            recordingAudioPeopleLimit = 10,
            recordingAudioParticipantsTimeLimit = 60,
            recordingVideoPausesLimit = 4,
            recordingVideoSupport = true,
            recordingVideoPeopleLimit = 12,
            recordingVideoParticipantsTimeLimit = 90,
            recordingAllParticipantsSupport = true,
            recordingVideoParticipantsSupport = false,
            recordingAllParticipantsFullRoomSupport = true,
            recordingVideoParticipantsFullRoomSupport = false,
            recordingPreferredOrientation = "landscape",
            recordingSupportForOtherOrientation = true,
            recordingMultiFormatsSupport = true,
            recordingHlsSupport = true,
            recordingAudioPausesCount = 1,
            recordingVideoPausesCount = 2
        )

        val fixture = RoomRecordParamsFixture()

        val options = RoomRecordParamsOptions(
            recordParams = recordParams,
            parameters = fixture
        )

        roomRecordParams(options)

        assertEquals(3, fixture.audioPausesLimit)
        assertEquals(1, fixture.audioPausesCount)
        assertEquals(true, fixture.audioSupport)
        assertEquals(10, fixture.audioPeopleLimit)
        assertEquals(60, fixture.audioParticipantsTimeLimit)
        assertEquals(2, fixture.videoPausesCount)
        assertEquals(4, fixture.videoPausesLimit)
        assertEquals(true, fixture.videoSupport)
        assertEquals(12, fixture.videoPeopleLimit)
        assertEquals(90, fixture.videoParticipantsTimeLimit)
        assertEquals(true, fixture.allParticipantsSupport)
        assertEquals(false, fixture.videoParticipantsSupport)
        assertEquals(true, fixture.allParticipantsFullRoomSupport)
        assertEquals(false, fixture.videoParticipantsFullRoomSupport)
        assertEquals("landscape", fixture.preferredOrientation)
        assertEquals(true, fixture.supportForOtherOrientation)
        assertEquals(true, fixture.multiFormatsSupport)
    }
}