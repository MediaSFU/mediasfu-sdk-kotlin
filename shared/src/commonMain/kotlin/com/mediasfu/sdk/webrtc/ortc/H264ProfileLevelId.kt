package com.mediasfu.sdk.webrtc.ortc

internal object H264ProfileLevelIdHelper {
    private val PROFILE_LEVEL_ID_REGEX = Regex("^[0-9a-fA-F]{6}$")
    private const val CONSTRAINT_SET3_FLAG = 0x10

    private enum class H264Profile {
        CONSTRAINED_BASELINE,
        BASELINE,
        MAIN,
        CONSTRAINED_HIGH,
        HIGH,
        PREDICTIVE_HIGH_444
    }

    private enum class H264Level(val value: Int) {
        L1_B(0),
        L1(10),
        L1_1(11),
        L1_2(12),
        L1_3(13),
        L2(20),
        L2_1(21),
        L2_2(22),
        L3(30),
        L3_1(31),
        L3_2(32),
        L4(40),
        L4_1(41),
        L4_2(42),
        L5(50),
        L5_1(51),
        L5_2(52);

        companion object {
            fun fromValue(value: Int): H264Level? = entries.firstOrNull { it.value == value }
        }
    }

    private data class ProfileLevelId(
        val profile: H264Profile,
        val level: H264Level
    )

    private data class BitPattern(val mask: Int, val value: Int) {
        fun matches(input: Int): Boolean = value == (input and mask)
    }

    private data class ProfilePattern(
        val profileIdc: Int,
        val pattern: BitPattern,
        val profile: H264Profile
    )

    private val DEFAULT_PROFILE_LEVEL_ID = ProfileLevelId(H264Profile.CONSTRAINED_BASELINE, H264Level.L3_1)

    private val PROFILE_PATTERNS = listOf(
        ProfilePattern(0x42, bitPattern("x1xx0000"), H264Profile.CONSTRAINED_BASELINE),
        ProfilePattern(0x4D, bitPattern("1xxx0000"), H264Profile.CONSTRAINED_BASELINE),
        ProfilePattern(0x58, bitPattern("11xx0000"), H264Profile.CONSTRAINED_BASELINE),
        ProfilePattern(0x42, bitPattern("x0xx0000"), H264Profile.BASELINE),
        ProfilePattern(0x58, bitPattern("10xx0000"), H264Profile.BASELINE),
        ProfilePattern(0x4D, bitPattern("0x0x0000"), H264Profile.MAIN),
        ProfilePattern(0x64, bitPattern("00000000"), H264Profile.HIGH),
        ProfilePattern(0x64, bitPattern("00001100"), H264Profile.CONSTRAINED_HIGH),
        ProfilePattern(0xF4, bitPattern("00000000"), H264Profile.PREDICTIVE_HIGH_444)
    )

    fun isSameProfile(localParams: Map<String, String>, remoteParams: Map<String, String>): Boolean {
        val local = parseSdpProfileLevelId(localParams) ?: return false
        val remote = parseSdpProfileLevelId(remoteParams) ?: return false
        return local.profile == remote.profile
    }

    fun generateProfileLevelIdForAnswer(
        localParams: Map<String, String>,
        remoteParams: Map<String, String>
    ): String? {
        val localHasValue = localParams.containsKeyIgnoreCase("profile-level-id")
        val remoteHasValue = remoteParams.containsKeyIgnoreCase("profile-level-id")
        if (!localHasValue && !remoteHasValue) {
            return null
        }

        val localProfile = parseSdpProfileLevelId(localParams)
            ?: throw IllegalArgumentException("Invalid local profile-level-id")
        val remoteProfile = parseSdpProfileLevelId(remoteParams)
            ?: throw IllegalArgumentException("Invalid remote profile-level-id")

        require(localProfile.profile == remoteProfile.profile) { "H264 profiles do not match" }

        val levelAsymmetryAllowed = isLevelAsymmetryAllowed(localParams) &&
            isLevelAsymmetryAllowed(remoteParams)

        val answerLevel = if (levelAsymmetryAllowed) {
            localProfile.level
        } else {
            minLevel(localProfile.level, remoteProfile.level)
        }

        return profileLevelIdToString(ProfileLevelId(localProfile.profile, answerLevel))
    }

    private fun parseSdpProfileLevelId(params: Map<String, String>): ProfileLevelId? {
        val rawValue = params.valueIgnoreCase("profile-level-id") ?: return DEFAULT_PROFILE_LEVEL_ID
        return parseProfileLevelId(rawValue)
    }

    private fun parseProfileLevelId(rawValue: String): ProfileLevelId? {
        if (!PROFILE_LEVEL_ID_REGEX.matches(rawValue)) {
            return null
        }

        val numeric = rawValue.toLong(16)
        if (numeric == 0L) {
            return null
        }

        val levelIdc = (numeric and 0xFF).toInt()
        val profileIop = ((numeric shr 8) and 0xFF).toInt()
        val profileIdc = ((numeric shr 16) and 0xFF).toInt()

        val baseLevel = H264Level.fromValue(levelIdc) ?: return null
        val level = if (baseLevel == H264Level.L1_1 && (profileIop and CONSTRAINT_SET3_FLAG) != 0) {
            H264Level.L1_B
        } else {
            baseLevel
        }

        val profile = PROFILE_PATTERNS.firstOrNull { pattern ->
            pattern.profileIdc == profileIdc && pattern.pattern.matches(profileIop)
        }?.profile ?: return null

        return ProfileLevelId(profile, level)
    }

    private fun profileLevelIdToString(profileLevelId: ProfileLevelId): String? {
        if (profileLevelId.level == H264Level.L1_B) {
            return when (profileLevelId.profile) {
                H264Profile.CONSTRAINED_BASELINE -> "42f00b"
                H264Profile.BASELINE -> "42100b"
                H264Profile.MAIN -> "4d100b"
                else -> null
            }
        }

        val prefix = when (profileLevelId.profile) {
            H264Profile.CONSTRAINED_BASELINE -> "42e0"
            H264Profile.BASELINE -> "4200"
            H264Profile.MAIN -> "4d00"
            H264Profile.CONSTRAINED_HIGH -> "640c"
            H264Profile.HIGH -> "6400"
            H264Profile.PREDICTIVE_HIGH_444 -> "f400"
        }

        return "$prefix${profileLevelId.level.value.toString(16).padStart(2, '0')}"
    }

    private fun minLevel(a: H264Level, b: H264Level): H264Level {
        val aValue = if (a == H264Level.L1_B) 0 else a.value
        val bValue = if (b == H264Level.L1_B) 0 else b.value
        return if (aValue <= bValue) a else b
    }

    private fun isLevelAsymmetryAllowed(params: Map<String, String>): Boolean {
        val value = params.valueIgnoreCase("level-asymmetry-allowed") ?: return false
        return value == "1" || value.equals("true", ignoreCase = true)
    }

    private fun Map<String, String>.valueIgnoreCase(key: String): String? {
        return entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    private fun Map<String, String>.containsKeyIgnoreCase(key: String): Boolean {
        return keys.any { it.equals(key, ignoreCase = true) }
    }

    private fun bitPattern(pattern: String): BitPattern {
        require(pattern.length == 8)
        val mask = byteMask(pattern, 'x').inv() and 0xFF
        val value = byteMask(pattern, '1')
        return BitPattern(mask, value)
    }

    private fun byteMask(pattern: String, target: Char): Int {
        var result = 0
        pattern.forEachIndexed { index, char ->
            if (char == target) {
                result = result or (1 shl (7 - index))
            }
        }
        return result
    }
}
