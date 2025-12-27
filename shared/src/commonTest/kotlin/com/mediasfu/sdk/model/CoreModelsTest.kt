package com.mediasfu.sdk.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreModelsTest {
    @Test
    fun participantDefaultsAreSensible() {
        val participant = Participant(name = "Alice")
        assertEquals("Alice", participant.name)
        assertFalse(participant.muted)
        assertEquals("", participant.audioID)
    }

    @Test
    fun roomCreationOptionsExposeMeaningfulDefaults() {
        val options = RoomCreationOptions(
            action = "create",
            meetingId = "room-123",
            durationMinutes = 60,
            capacity = 100,
            userName = "host"
        )

        assertEquals(EventType.NONE, options.eventType)
        assertEquals(false, options.recordOnly)
        assertEquals(50, options.pageSize)
    }

    @Test
    fun roomCreationOptionsRoundTripJson() {
        val options = RoomCreationOptions(
            action = "create",
            meetingId = "room-json",
            durationMinutes = 90,
            capacity = 250,
            userName = "json-host",
            eventType = EventType.CONFERENCE,
            recordOnly = true
        )

        val encoded = Json.encodeToString(options)
        assertTrue(encoded.contains("\"meetingID\":"))

        val decoded = Json.decodeFromString<RoomCreationOptions>(encoded)
        assertEquals(options, decoded)
    }
    
    // ========================================================================
    // Media Stream Configuration Tests
    // ========================================================================
    
    @Test
    fun mediaStreamConstraintsShouldBeCreatedWithAudioAndVideo() {
        val audioConstraints = AudioConstraints(
            echoCancellation = true,
            noiseSuppression = true,
            autoGainControl = true,
            deviceId = "default"
        )
        
        val videoConstraints = VideoConstraints(
            width = VideoResolution(ideal = 1280),
            height = VideoResolution(ideal = 720),
            frameRate = VideoFrameRate(ideal = 30),
            facingMode = FacingMode.USER
        )
        
        val constraints = MediaStreamConstraints(
            audio = audioConstraints,
            video = videoConstraints
        )
        
        assertEquals(true, constraints.audio?.echoCancellation)
        assertEquals(1280, constraints.video?.width?.ideal)
        assertEquals(FacingMode.USER, constraints.video?.facingMode)
    }
    
    @Test
    fun audioConstraintsShouldHaveDefaultValues() {
        val constraints = AudioConstraints()
        
        assertEquals(true, constraints.echoCancellation)
        assertEquals(true, constraints.noiseSuppression)
        assertEquals(true, constraints.autoGainControl)
        assertEquals(null, constraints.deviceId)
    }
    
    @Test
    fun videoConstraintsShouldSupportVariousResolutions() {
        val hdConstraints = VideoConstraints(
            width = VideoResolution(ideal = 1280, min = 640, max = 1920),
            height = VideoResolution(ideal = 720, min = 480, max = 1080),
            frameRate = VideoFrameRate(ideal = 30, min = 15, max = 60)
        )
        
        assertEquals(1280, hdConstraints.width?.ideal)
        assertEquals(640, hdConstraints.width?.min)
        assertEquals(1920, hdConstraints.width?.max)
    }
    
    @Test
    fun facingModeEnumShouldHaveCorrectValues() {
        assertEquals("user", FacingMode.USER.toString())
        assertEquals("environment", FacingMode.ENVIRONMENT.toString())
        assertEquals("left", FacingMode.LEFT.toString())
        assertEquals("right", FacingMode.RIGHT.toString())
    }
    
    @Test
    fun mediaDeviceInfoShouldStoreDeviceInformation() {
        val deviceInfo = MediaDeviceInfo(
            deviceId = "device-123",
            kind = MediaDeviceKind.AUDIOINPUT,
            label = "Built-in Microphone",
            groupId = "group-456"
        )
        
        assertEquals("device-123", deviceInfo.deviceId)
        assertEquals(MediaDeviceKind.AUDIOINPUT, deviceInfo.kind)
        assertEquals("Built-in Microphone", deviceInfo.label)
    }
    
    // ========================================================================
    // SDK Configuration Tests
    // ========================================================================
    
    @Test
    fun mediaSfuConfigShouldBeCreatedWithDefaults() {
        val config = MediaSfuConfig(
            apiUserName = "testuser",
            apiKey = "testkey"
        )
        
        assertEquals("https://mediasfu.com", config.apiUrl)
        assertEquals("https://mediasfu.com", config.socketUrl)
        assertEquals("testuser", config.apiUserName)
        assertEquals("testkey", config.apiKey)
        assertTrue(config.recordingConfig is RecordingConfig)
        assertTrue(config.mediaConfig is MediaConfig)
        assertTrue(config.socketConfig is SocketConfig)
    }
    
    @Test
    fun recordingConfigShouldHaveSensibleDefaults() {
        val config = RecordingConfig()
        
        assertEquals("video", config.recordingMediaOptions)
        assertEquals("all", config.recordingAudioOptions)
        assertEquals("fullDisplay", config.recordingVideoType)
        assertEquals(true, config.recordingNameTags)
        assertEquals("#000000", config.recordingBackgroundColor)
        assertEquals("landscape", config.recordingOrientationVideo)
        assertEquals(false, config.recordingAddHLS)
    }
    
    @Test
    fun recordingConfigShouldBeCustomizable() {
        val config = RecordingConfig(
            recordingMediaOptions = "audio",
            recordingVideoType = "mainOnly",
            recordingNameTags = false,
            recordingBackgroundColor = "#FFFFFF",
            recordingOrientationVideo = "portrait",
            recordingAddHLS = true
        )
        
        assertEquals("audio", config.recordingMediaOptions)
        assertEquals("mainOnly", config.recordingVideoType)
        assertEquals(false, config.recordingNameTags)
        assertEquals("#FFFFFF", config.recordingBackgroundColor)
        assertEquals("portrait", config.recordingOrientationVideo)
        assertEquals(true, config.recordingAddHLS)
    }
    
    @Test
    fun mediaConfigShouldControlMediaAutoStart() {
        val config = MediaConfig(
            autoStartAudio = true,
            autoStartVideo = true,
            autoStartScreenShare = false,
            audioSetting = "allow",
            videoSetting = "allow",
            chatSetting = "disallow"
        )
        
        assertEquals(true, config.autoStartAudio)
        assertEquals(true, config.autoStartVideo)
        assertEquals(false, config.autoStartScreenShare)
        assertEquals("allow", config.audioSetting)
        assertEquals("disallow", config.chatSetting)
    }
    
    @Test
    fun mediaConfigShouldHaveDefaultSettings() {
        val config = MediaConfig()
        
        assertEquals(false, config.autoStartAudio)
        assertEquals(false, config.autoStartVideo)
        assertEquals(false, config.audioPaused)
        assertEquals("allow", config.chatSetting)
        assertEquals("allow", config.audioSetting)
    }
    
    @Test
    fun socketConfigShouldConfigureReconnection() {
        val config = SocketConfig(
            reconnection = true,
            reconnectionAttempts = 10,
            reconnectionDelay = 2000,
            reconnectionDelayMax = 10000,
            timeout = 30000
        )
        
        assertEquals(true, config.reconnection)
        assertEquals(10, config.reconnectionAttempts)
        assertEquals(2000, config.reconnectionDelay)
        assertEquals(10000, config.reconnectionDelayMax)
        assertEquals(30000, config.timeout)
    }
    
    @Test
    fun socketConfigShouldHaveSensibleDefaults() {
        val config = SocketConfig()
        
        assertEquals(true, config.reconnection)
        assertEquals(Int.MAX_VALUE, config.reconnectionAttempts)
        assertEquals(1000, config.reconnectionDelay)
        assertEquals(20000, config.timeout)
        assertEquals(true, config.autoConnect)
    }
    
    @Test
    fun socketConfigShouldSupportTransportConfiguration() {
        val config = SocketConfig(
            transports = listOf("websocket")
        )
        
        assertEquals(1, config.transports.size)
        assertEquals("websocket", config.transports[0])
    }
    
    // ========================================================================
    // Alternative Domains Tests
    // ========================================================================
    
    @Test
    fun altDomainsShouldStoreDomainMappings() {
        val altDomains = AltDomains(
            altDomains = mapOf(
                "domain1" to "alt-domain1.com",
                "domain2" to "alt-domain2.com"
            )
        )
        
        assertEquals(2, altDomains.altDomains.size)
        assertEquals("alt-domain1.com", altDomains.altDomains["domain1"])
        assertEquals("alt-domain2.com", altDomains.altDomains["domain2"])
    }
    
    @Test
    fun altDomainsShouldDefaultToEmptyMap() {
        val altDomains = AltDomains()
        
        assertTrue(altDomains.altDomains.isEmpty())
    }
}
