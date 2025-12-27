package com.mediasfu.sdk.webrtc.ortc

import com.mediasfu.sdk.webrtc.MediaKind
import com.mediasfu.sdk.webrtc.RtcpFeedback
import com.mediasfu.sdk.webrtc.RtpCapabilities
import com.mediasfu.sdk.webrtc.RtpCodecCapability
import com.mediasfu.sdk.webrtc.RtpHeaderDirection
import com.mediasfu.sdk.webrtc.RtpHeaderExtension

object OrtcUtils {
    data class CodecMatchResult(
        val local: RtpCodecCapability,
        val remote: RtpCodecCapability
    )

    data class ExtendedRtpCapabilities(
        val codecs: List<ExtendedRtpCodec>,
        val headerExtensions: List<ExtendedRtpHeaderExtension>
    )

    data class ExtendedRtpCodec(
        val mimeType: String,
        val kind: MediaKind,
        val clockRate: Int,
        val channels: Int?,
        val localPayloadType: Int?,
        val remotePayloadType: Int?,
        val localParameters: Map<String, String>,
        val remoteParameters: Map<String, String>,
        val rtcpFeedback: List<RtcpFeedback>,
        var localRtxPayloadType: Int? = null,
        var remoteRtxPayloadType: Int? = null
    )

    data class ExtendedRtpHeaderExtension(
        val kind: MediaKind?,
        val uri: String,
        val sendId: Int,
        val recvId: Int,
        val encrypt: Boolean,
        var direction: RtpHeaderDirection
    )

    fun isRtxCodec(codec: RtpCodecCapability?): Boolean {
        return codec?.mimeType?.lowercase()?.endsWith("/rtx") == true
    }

    fun matchCodecs(
        localCodec: RtpCodecCapability,
        remoteCodec: RtpCodecCapability,
        strict: Boolean = false
    ): CodecMatchResult? {
        val localMime = localCodec.mimeType.lowercase()
        val remoteMime = remoteCodec.mimeType.lowercase()
        if (localMime != remoteMime) return null
        if (localCodec.clockRate != remoteCodec.clockRate) return null
        if (localCodec.channels != remoteCodec.channels) return null

        var updatedLocal = localCodec
        var updatedRemote = remoteCodec

        when (localMime) {
            "video/h264" -> {
                val localPacketization = localCodec.parameters["packetization-mode"]?.toIntOrNull() ?: 0
                val remotePacketization = remoteCodec.parameters["packetization-mode"]?.toIntOrNull() ?: 0
                if (localPacketization != remotePacketization) return null

                if (strict) {
                    val h264Match = H264ProfileLevelIdHelper.isSameProfile(localCodec.parameters, remoteCodec.parameters)
                    if (!h264Match) return null

                    val negotiatedProfile = H264ProfileLevelIdHelper.generateProfileLevelIdForAnswer(
                        localCodec.parameters,
                        remoteCodec.parameters
                    )

                    val localParams = localCodec.parameters.toMutableMap()
                    val remoteParams = remoteCodec.parameters.toMutableMap()

                    if (negotiatedProfile != null) {
                        localParams["profile-level-id"] = negotiatedProfile
                        remoteParams["profile-level-id"] = negotiatedProfile
                    } else {
                        localParams.remove("profile-level-id")
                        remoteParams.remove("profile-level-id")
                    }

                    updatedLocal = localCodec.copy(parameters = localParams)
                    updatedRemote = remoteCodec.copy(parameters = remoteParams)
                }
            }

            "video/vp9" -> {
                if (strict) {
                    val localProfile = localCodec.parameters["profile-id"]?.toIntOrNull() ?: 0
                    val remoteProfile = remoteCodec.parameters["profile-id"]?.toIntOrNull() ?: 0
                    if (localProfile != remoteProfile) return null
                }
            }
        }

        return CodecMatchResult(updatedLocal, updatedRemote)
    }

    fun reduceRtcpFeedback(codecA: RtpCodecCapability, codecB: RtpCodecCapability): List<RtcpFeedback> {
        val reduced = mutableListOf<RtcpFeedback>()
        codecA.rtcpFeedback.forEach { aFb ->
            val match = codecB.rtcpFeedback.firstOrNull { bFb ->
                bFb.type == aFb.type && normalizeRtcpParameter(bFb.parameter) == normalizeRtcpParameter(aFb.parameter)
            }
            if (match != null) {
                reduced += match
            }
        }
        return reduced
    }

    fun matchHeaderExtensions(local: RtpHeaderExtension, remote: RtpHeaderExtension): Boolean {
        if (local.kind != null && remote.kind != null && local.kind != remote.kind) {
            return false
        }
        return local.uri == remote.uri
    }

    fun getExtendedRtpCapabilities(
        localCaps: RtpCapabilities,
        remoteCaps: RtpCapabilities
    ): ExtendedRtpCapabilities {
        val extendedCodecs = mutableListOf<ExtendedRtpCodec>()

        remoteCaps.codecs.forEach { remoteCodec ->
            if (isRtxCodec(remoteCodec)) return@forEach
            val match = localCaps.codecs.firstNotNullOfOrNull { localCodec ->
                matchCodecs(localCodec, remoteCodec, strict = true)
            } ?: return@forEach

            if (match.local.preferredPayloadType == null || remoteCodec.preferredPayloadType == null) {
                return@forEach
            }

            extendedCodecs += ExtendedRtpCodec(
                mimeType = match.local.mimeType,
                kind = match.local.kind,
                clockRate = match.local.clockRate,
                channels = match.local.channels,
                localPayloadType = match.local.preferredPayloadType,
                remotePayloadType = remoteCodec.preferredPayloadType,
                localParameters = match.local.parameters,
                remoteParameters = match.remote.parameters,
                rtcpFeedback = reduceRtcpFeedback(match.local, match.remote)
            )
        }

        extendedCodecs.forEach { extendedCodec ->
            val localRtx = localCaps.codecs.firstOrNull { codec ->
                isRtxCodec(codec) && codec.parameters["apt"]?.toIntOrNull() == extendedCodec.localPayloadType
            }
            val remoteRtx = remoteCaps.codecs.firstOrNull { codec ->
                isRtxCodec(codec) && codec.parameters["apt"]?.toIntOrNull() == extendedCodec.remotePayloadType
            }
            extendedCodec.localRtxPayloadType = localRtx?.preferredPayloadType
            extendedCodec.remoteRtxPayloadType = remoteRtx?.preferredPayloadType
        }

        val extendedExtensions = mutableListOf<ExtendedRtpHeaderExtension>()
        remoteCaps.headerExtensions.forEach { remoteExt ->
            val matchingLocal = localCaps.headerExtensions.firstOrNull { localExt ->
                matchHeaderExtensions(localExt, remoteExt)
            } ?: return@forEach

            val sendId = matchingLocal.preferredId
            val recvId = remoteExt.preferredId
            val encrypt = matchingLocal.preferredEncrypt
            var direction = RtpHeaderDirection.SENDRECV
            when (remoteExt.direction) {
                RtpHeaderDirection.SENDRECV -> direction = RtpHeaderDirection.SENDRECV
                RtpHeaderDirection.RECVONLY -> direction = RtpHeaderDirection.SENDONLY
                RtpHeaderDirection.SENDONLY -> direction = RtpHeaderDirection.RECVONLY
                RtpHeaderDirection.INACTIVE -> direction = RtpHeaderDirection.INACTIVE
                null -> {}
            }

            extendedExtensions += ExtendedRtpHeaderExtension(
                kind = remoteExt.kind,
                uri = remoteExt.uri,
                sendId = sendId,
                recvId = recvId,
                encrypt = encrypt,
                direction = direction
            )
        }

        return ExtendedRtpCapabilities(
            codecs = extendedCodecs,
            headerExtensions = extendedExtensions
        )
    }

    fun getRecvRtpCapabilities(
        extendedRtpCapabilities: ExtendedRtpCapabilities
    ): RtpCapabilities {
        val recvCodecs = mutableListOf<RtpCodecCapability>()
        extendedRtpCapabilities.codecs.forEach { extendedCodec ->
            recvCodecs += RtpCodecCapability(
                mimeType = extendedCodec.mimeType,
                kind = extendedCodec.kind,
                preferredPayloadType = extendedCodec.remotePayloadType,
                clockRate = extendedCodec.clockRate,
                channels = extendedCodec.channels,
                parameters = extendedCodec.localParameters,
                rtcpFeedback = extendedCodec.rtcpFeedback
            )

            if (extendedCodec.remoteRtxPayloadType != null && extendedCodec.remotePayloadType != null) {
                recvCodecs += RtpCodecCapability(
                    mimeType = "${extendedCodec.kind.name.lowercase()}/rtx",
                    kind = extendedCodec.kind,
                    preferredPayloadType = extendedCodec.remoteRtxPayloadType,
                    clockRate = extendedCodec.clockRate,
                    channels = extendedCodec.channels,
                    parameters = mapOf("apt" to extendedCodec.remotePayloadType.toString())
                )
            }
        }

        val recvExtensions = extendedRtpCapabilities.headerExtensions.filter { ext ->
            ext.direction == RtpHeaderDirection.SENDRECV || ext.direction == RtpHeaderDirection.RECVONLY
        }.map { ext ->
            RtpHeaderExtension(
                kind = ext.kind,
                uri = ext.uri,
                preferredId = ext.recvId,
                preferredEncrypt = ext.encrypt,
                direction = ext.direction
            )
        }

        return RtpCapabilities(
            codecs = recvCodecs,
            headerExtensions = recvExtensions
        )
    }

    fun canSend(
        kind: MediaKind,
        extendedRtpCapabilities: ExtendedRtpCapabilities?
    ): Boolean {
        if (extendedRtpCapabilities == null) {
            return false
        }

        return extendedRtpCapabilities.codecs.any { codec ->
            codec.kind == kind && codec.localPayloadType != null
        }
    }

    private fun normalizeRtcpParameter(parameter: String?): String? {
        if (parameter.isNullOrBlank()) return null
        return parameter
    }
}
