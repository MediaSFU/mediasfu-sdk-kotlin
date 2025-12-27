package com.mediasfu.sdk.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Tests for WebRTC models
 * Verifies proper construction, serialization, and validation of WebRTC types
 */
class WebRTCModelsTest {

    // ========================================================================
    // Transport Tests
    // ========================================================================

    @Test
    fun `Transport should be created with required parameters`() {
        val iceParams = IceParameters(
            usernameFragment = "test-user",
            password = "test-pass"
        )
        
        val iceCandidate = IceCandidate(
            foundation = "1",
            priority = 2130706431,
            ip = "192.168.1.1",
            protocol = TransportProtocol.UDP,
            port = 50000,
            type = CandidateType.HOST
        )
        
        val fingerprint = Fingerprint(
            algorithm = "sha-256",
            value = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        )
        
        val dtlsParams = DtlsParameters(
            role = DtlsRole.AUTO,
            fingerprints = listOf(fingerprint)
        )
        
        val transport = Transport(
            id = "transport-1",
            iceParameters = iceParams,
            iceCandidates = listOf(iceCandidate),
            dtlsParameters = dtlsParams
        )
        
        assertEquals("transport-1", transport.id)
        assertEquals("test-user", transport.iceParameters.usernameFragment)
        assertEquals(1, transport.iceCandidates.size)
        assertEquals(DtlsRole.AUTO, transport.dtlsParameters.role)
        assertEquals(IceState.NEW, transport.iceState)
        assertNull(transport.sctpParameters)
    }

    @Test
    fun `Transport should support SCTP parameters`() {
        val sctpParams = SctpParameters(
            port = 5000,
            OS = 1024,
            MIS = 1024,
            maxMessageSize = 262144
        )
        
        val transport = Transport(
            id = "transport-2",
            iceParameters = IceParameters("user", "pass"),
            iceCandidates = emptyList(),
            dtlsParameters = DtlsParameters(fingerprints = emptyList()),
            sctpParameters = sctpParams
        )
        
        assertNotNull(transport.sctpParameters)
        assertEquals(5000, transport.sctpParameters?.port)
        assertEquals(1024, transport.sctpParameters?.OS)
        assertEquals(262144, transport.sctpParameters?.maxMessageSize)
    }

    @Test
    fun `IceCandidate should handle all types`() {
        val hostCandidate = IceCandidate(
            foundation = "1",
            priority = 2130706431,
            ip = "192.168.1.1",
            protocol = TransportProtocol.UDP,
            port = 50000,
            type = CandidateType.HOST
        )
        
        val srflxCandidate = IceCandidate(
            foundation = "2",
            priority = 1694498815,
            ip = "203.0.113.1",
            protocol = TransportProtocol.UDP,
            port = 50001,
            type = CandidateType.SRFLX
        )
        
        val relayCandidate = IceCandidate(
            foundation = "3",
            priority = 16777215,
            ip = "198.51.100.1",
            protocol = TransportProtocol.UDP,
            port = 50002,
            type = CandidateType.RELAY
        )
        
        assertEquals(CandidateType.HOST, hostCandidate.type)
        assertEquals(CandidateType.SRFLX, srflxCandidate.type)
        assertEquals(CandidateType.RELAY, relayCandidate.type)
    }

    @Test
    fun `IceCandidate should support TCP with tcpType`() {
        val tcpCandidate = IceCandidate(
            foundation = "4",
            priority = 2113932031,
            ip = "192.168.1.2",
            protocol = TransportProtocol.TCP,
            port = 50003,
            type = CandidateType.HOST,
            tcpType = TcpType.PASSIVE
        )
        
        assertEquals(TransportProtocol.TCP, tcpCandidate.protocol)
        assertEquals(TcpType.PASSIVE, tcpCandidate.tcpType)
    }

    // ========================================================================
    // Producer/Consumer Tests
    // ========================================================================

    @Test
    fun `Producer should be created with RTP parameters`() {
        val codec = RtpCodecParameters(
            mimeType = "audio/opus",
            payloadType = 111,
            clockRate = 48000,
            channels = 2
        )
        
        val rtpParams = RtpParameters(
            mid = "0",
            codecs = listOf(codec)
        )
        
        val producer = Producer(
            id = "producer-1",
            kind = MediaKind.AUDIO,
            rtpParameters = rtpParams,
            paused = false
        )
        
        assertEquals("producer-1", producer.id)
        assertEquals(MediaKind.AUDIO, producer.kind)
        assertEquals(111, producer.rtpParameters.codecs[0].payloadType)
        assertFalse(producer.paused)
    }

    @Test
    fun `Consumer should reference producer`() {
        val codec = RtpCodecParameters(
            mimeType = "video/VP8",
            payloadType = 96,
            clockRate = 90000
        )
        
        val rtpParams = RtpParameters(
            mid = "1",
            codecs = listOf(codec)
        )
        
        val consumer = Consumer(
            id = "consumer-1",
            producerId = "producer-1",
            kind = MediaKind.VIDEO,
            rtpParameters = rtpParams
        )
        
        assertEquals("consumer-1", consumer.id)
        assertEquals("producer-1", consumer.producerId)
        assertEquals(MediaKind.VIDEO, consumer.kind)
    }

    @Test
    fun `Producer should support video with multiple encodings`() {
        val encoding1 = RtpEncodingParameters(
            ssrc = 11111,
            rid = "high",
            maxBitrate = 2000000
        )
        
        val encoding2 = RtpEncodingParameters(
            ssrc = 22222,
            rid = "medium",
            maxBitrate = 1000000
        )
        
        val encoding3 = RtpEncodingParameters(
            ssrc = 33333,
            rid = "low",
            maxBitrate = 500000
        )
        
        val rtpParams = RtpParameters(
            codecs = listOf(
                RtpCodecParameters(
                    mimeType = "video/VP8",
                    payloadType = 96,
                    clockRate = 90000
                )
            ),
            encodings = listOf(encoding1, encoding2, encoding3)
        )
        
        val producer = Producer(
            id = "producer-video",
            kind = MediaKind.VIDEO,
            rtpParameters = rtpParams
        )
        
        assertEquals(3, producer.rtpParameters.encodings.size)
        assertEquals("high", producer.rtpParameters.encodings[0].rid)
        assertEquals(2000000, producer.rtpParameters.encodings[0].maxBitrate)
    }

    // ========================================================================
    // RTP Parameters Tests
    // ========================================================================

    @Test
    fun `RtpCodecParameters should support audio codecs`() {
        val opusCodec = RtpCodecParameters(
            mimeType = "audio/opus",
            payloadType = 111,
            clockRate = 48000,
            channels = 2,
            parameters = mapOf(
                "minptime" to "10",
                "useinbandfec" to "1"
            )
        )
        
        assertEquals("audio/opus", opusCodec.mimeType)
        assertEquals(48000, opusCodec.clockRate)
        assertEquals(2, opusCodec.channels)
        assertEquals("1", opusCodec.parameters?.get("useinbandfec"))
    }

    @Test
    fun `RtpCodecParameters should support video codecs with feedback`() {
        val feedback = listOf(
            RtcpFeedback("goog-remb"),
            RtcpFeedback("transport-cc"),
            RtcpFeedback("ccm", "fir"),
            RtcpFeedback("nack"),
            RtcpFeedback("nack", "pli")
        )
        
        val vp8Codec = RtpCodecParameters(
            mimeType = "video/VP8",
            payloadType = 96,
            clockRate = 90000,
            rtcpFeedback = feedback
        )
        
        assertEquals("video/VP8", vp8Codec.mimeType)
        assertEquals(5, vp8Codec.rtcpFeedback?.size)
        assertEquals("goog-remb", vp8Codec.rtcpFeedback?.get(0)?.type)
        assertEquals("pli", vp8Codec.rtcpFeedback?.get(4)?.parameter)
    }

    @Test
    fun `RtpHeaderExtensionParameters should be constructed properly`() {
        val headerExt = RtpHeaderExtensionParameters(
            uri = "urn:ietf:params:rtp-hdrext:ssrc-audio-level",
            id = 1,
            encrypt = false
        )
        
        assertEquals("urn:ietf:params:rtp-hdrext:ssrc-audio-level", headerExt.uri)
        assertEquals(1, headerExt.id)
        assertEquals(false, headerExt.encrypt)
    }

    @Test
    fun `RtpEncodingParameters should support RTX`() {
        val rtx = Rtx(ssrc = 99999)
        
        val encoding = RtpEncodingParameters(
            ssrc = 11111,
            rtx = rtx,
            maxBitrate = 1000000,
            scalabilityMode = "L1T3"
        )
        
        assertNotNull(encoding.rtx)
        assertEquals(99999, encoding.rtx?.ssrc)
        assertEquals("L1T3", encoding.scalabilityMode)
    }

    // ========================================================================
    // RTP Capabilities Tests
    // ========================================================================

    @Test
    fun `RtpCapabilities should contain codec list`() {
        val audioCodec = RtpCodecCapability(
            kind = MediaKind.AUDIO,
            mimeType = "audio/opus",
            preferredPayloadType = 111,
            clockRate = 48000,
            channels = 2
        )
        
        val videoCodec = RtpCodecCapability(
            kind = MediaKind.VIDEO,
            mimeType = "video/VP8",
            preferredPayloadType = 96,
            clockRate = 90000
        )
        
        val capabilities = RtpCapabilities(
            codecs = listOf(audioCodec, videoCodec)
        )
        
        assertEquals(2, capabilities.codecs.size)
        assertEquals(MediaKind.AUDIO, capabilities.codecs[0].kind)
        assertEquals(MediaKind.VIDEO, capabilities.codecs[1].kind)
    }

    @Test
    fun `RtpHeaderExtensionCapability should specify direction`() {
        val headerExtCap = RtpHeaderExtensionCapability(
            kind = MediaKind.AUDIO,
            uri = "urn:ietf:params:rtp-hdrext:ssrc-audio-level",
            preferredId = 1,
            direction = "sendrecv"
        )
        
        assertEquals("sendrecv", headerExtCap.direction)
    }

    // ========================================================================
    // Device Tests
    // ========================================================================

    @Test
    fun `DeviceInfo should track loaded state`() {
        val deviceInfo = DeviceInfo(
            loaded = true,
            canProduce = mapOf(
                MediaKind.AUDIO to true,
                MediaKind.VIDEO to true
            )
        )
        
        assertTrue(deviceInfo.loaded)
        assertTrue(deviceInfo.canProduce[MediaKind.AUDIO] ?: false)
        assertTrue(deviceInfo.canProduce[MediaKind.VIDEO] ?: false)
    }

    @Test
    fun `SctpCapabilities should specify stream numbers`() {
        val sctpCap = SctpCapabilities(
            numStreams = NumSctpStreams(OS = 1024, MIS = 1024)
        )
        
        assertEquals(1024, sctpCap.numStreams.OS)
        assertEquals(1024, sctpCap.numStreams.MIS)
    }

    // ========================================================================
    // Enum Tests
    // ========================================================================

    @Test
    fun `MediaKind enum should have correct values`() {
        assertEquals("audio", MediaKind.AUDIO.toString())
        assertEquals("video", MediaKind.VIDEO.toString())
    }

    @Test
    fun `DtlsRole enum should have correct values`() {
        assertEquals("auto", DtlsRole.AUTO.toString())
        assertEquals("client", DtlsRole.CLIENT.toString())
        assertEquals("server", DtlsRole.SERVER.toString())
    }

    @Test
    fun `IceState enum should have all states`() {
        val states = IceState.values()
        assertEquals(7, states.size)
        assertTrue(states.contains(IceState.NEW))
        assertTrue(states.contains(IceState.CHECKING))
        assertTrue(states.contains(IceState.CONNECTED))
        assertTrue(states.contains(IceState.COMPLETED))
        assertTrue(states.contains(IceState.FAILED))
        assertTrue(states.contains(IceState.DISCONNECTED))
        assertTrue(states.contains(IceState.CLOSED))
    }

    @Test
    fun `ConnectionState enum should have all states`() {
        val states = ConnectionState.values()
        assertEquals(6, states.size)
        assertTrue(states.contains(ConnectionState.NEW))
        assertTrue(states.contains(ConnectionState.CONNECTING))
        assertTrue(states.contains(ConnectionState.CONNECTED))
    }

    // ========================================================================
    // Transport Options Tests
    // ========================================================================

    @Test
    fun `SendTransportOptions should be created with defaults`() {
        val options = SendTransportOptions(
            id = "send-transport-1",
            iceParameters = IceParameters("user", "pass"),
            iceCandidates = emptyList(),
            dtlsParameters = DtlsParameters(fingerprints = emptyList())
        )
        
        assertEquals("send-transport-1", options.id)
        assertEquals(IceTransportPolicy.ALL, options.iceTransportPolicy)
        assertTrue(options.iceServers.isEmpty())
        assertTrue(options.appData.isEmpty())
    }

    @Test
    fun `IceServer should support STUN and TURN`() {
        val stunServer = IceServer(
            urls = listOf("stun:stun.l.google.com:19302")
        )
        
        val turnServer = IceServer(
            urls = listOf("turn:turn.example.com:3478"),
            username = "user123",
            credential = "pass456",
            credentialType = CredentialType.PASSWORD
        )
        
        assertEquals(1, stunServer.urls.size)
        assertNull(stunServer.username)
        
        assertEquals(1, turnServer.urls.size)
        assertEquals("user123", turnServer.username)
        assertEquals("pass456", turnServer.credential)
    }

    // ========================================================================
    // Producer/Consumer Options Tests
    // ========================================================================

    @Test
    fun `ProduceOptions should configure production`() {
        val options = ProduceOptions(
            kind = MediaKind.AUDIO,
            rtpParameters = RtpParameters(
                codecs = listOf(
                    RtpCodecParameters(
                        mimeType = "audio/opus",
                        payloadType = 111,
                        clockRate = 48000
                    )
                )
            ),
            paused = false,
            appData = mapOf("source" to "microphone")
        )
        
        assertEquals(MediaKind.AUDIO, options.kind)
        assertFalse(options.paused)
        assertEquals("microphone", options.appData["source"])
        assertTrue(options.disableTrackOnPause)
    }

    @Test
    fun `ConsumeOptions should reference producer`() {
        val options = ConsumeOptions(
            id = "consumer-1",
            producerId = "producer-1",
            kind = MediaKind.VIDEO,
            rtpParameters = RtpParameters(
                codecs = listOf(
                    RtpCodecParameters(
                        mimeType = "video/VP8",
                        payloadType = 96,
                        clockRate = 90000
                    )
                )
            )
        )
        
        assertEquals("consumer-1", options.id)
        assertEquals("producer-1", options.producerId)
        assertEquals(MediaKind.VIDEO, options.kind)
    }

    // ========================================================================
    // Data Producer/Consumer Tests
    // ========================================================================

    @Test
    fun `DataProducer should support ordered and unordered delivery`() {
        val orderedProducer = DataProducer(
            id = "data-producer-1",
            sctpStreamParameters = SctpStreamParameters(
                streamId = 1,
                ordered = true
            ),
            label = "chat",
            protocol = "json"
        )
        
        val unorderedProducer = DataProducer(
            id = "data-producer-2",
            sctpStreamParameters = SctpStreamParameters(
                streamId = 2,
                ordered = false,
                maxRetransmits = 3
            ),
            label = "metrics",
            protocol = "binary"
        )
        
        assertTrue(orderedProducer.sctpStreamParameters.ordered)
        assertFalse(unorderedProducer.sctpStreamParameters.ordered)
        assertEquals(3, unorderedProducer.sctpStreamParameters.maxRetransmits)
    }

    @Test
    fun `DataConsumer should match DataProducer`() {
        val dataConsumer = DataConsumer(
            id = "data-consumer-1",
            dataProducerId = "data-producer-1",
            sctpStreamParameters = SctpStreamParameters(
                streamId = 1,
                ordered = true
            ),
            label = "chat",
            protocol = "json"
        )
        
        assertEquals("data-producer-1", dataConsumer.dataProducerId)
        assertEquals("chat", dataConsumer.label)
        assertEquals("json", dataConsumer.protocol)
    }

    // ========================================================================
    // Statistics Tests
    // ========================================================================

    @Test
    fun `TransportStats should track bytes and packets`() {
        val stats = TransportStats(
            transportId = "transport-1",
            timestamp = System.currentTimeMillis(),
            bytesSent = 1024000,
            bytesReceived = 2048000,
            packetsSent = 1000,
            packetsReceived = 2000,
            iceState = IceState.CONNECTED,
            connectionState = ConnectionState.CONNECTED,
            availableOutgoingBitrate = 1000000
        )
        
        assertEquals(1024000, stats.bytesSent)
        assertEquals(2048000, stats.bytesReceived)
        assertEquals(IceState.CONNECTED, stats.iceState)
        assertEquals(1000000, stats.availableOutgoingBitrate)
    }

    @Test
    fun `ProducerStats should include codec info`() {
        val stats = ProducerStats(
            producerId = "producer-1",
            timestamp = System.currentTimeMillis(),
            kind = MediaKind.VIDEO,
            mimeType = "video/VP8",
            bytesSent = 500000,
            packetsSent = 500,
            framesEncoded = 300,
            bitrate = 800000
        )
        
        assertEquals(MediaKind.VIDEO, stats.kind)
        assertEquals("video/VP8", stats.mimeType)
        assertEquals(300, stats.framesEncoded)
        assertEquals(800000, stats.bitrate)
    }

    @Test
    fun `ConsumerStats should track quality metrics`() {
        val stats = ConsumerStats(
            consumerId = "consumer-1",
            timestamp = System.currentTimeMillis(),
            kind = MediaKind.AUDIO,
            mimeType = "audio/opus",
            bytesReceived = 250000,
            packetsReceived = 250,
            packetsLost = 5,
            jitter = 0.02
        )
        
        assertEquals(MediaKind.AUDIO, stats.kind)
        assertEquals(5, stats.packetsLost)
        assertEquals(0.02, stats.jitter)
    }
}
