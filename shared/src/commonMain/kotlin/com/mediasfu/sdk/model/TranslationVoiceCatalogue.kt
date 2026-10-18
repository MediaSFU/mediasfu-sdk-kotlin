package com.mediasfu.sdk.model

/** A stock voice returned by the room's configured TTS provider. */
data class TranslationVoice(
    val id: String,
    val name: String,
    val gender: String,
    val provider: String,
    val language: String
)

data class TranslationVoiceCatalogue(
    val provider: String = "",
    val language: String,
    val voices: List<TranslationVoice> = emptyList(),
    val status: String = "unavailable",
    val model: String? = null,
    val schemaVersion: Int = 1,
    val truncated: Boolean = false
) {
    /** Returns only an explicitly selected, currently available stock voice. */
    fun selectionConfig(voiceId: String?): Map<String, Any?>? {
        if (status != "available" || provider.isBlank()) return null
        val selected = voices.firstOrNull { it.id == voiceId && it.provider == provider } ?: return null
        return mapOf("voiceId" to selected.id, "ttsProvider" to provider)
    }
}

/** Invalid, failed or incompatible replies never supply guessed fallback IDs. */
fun parseTranslationVoiceCatalogue(response: Any?, language: String): TranslationVoiceCatalogue {
    fun unavailable() = TranslationVoiceCatalogue(language = language)
    val data = response as? Map<*, *> ?: return unavailable()
    val groups = data["voices"] as? Map<*, *> ?: return unavailable()
    val provider = (data["provider"] as? String)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        ?: return unavailable()
    if (data["schemaVersion"] != null && data["schemaVersion"] != 1) return unavailable()
    val resultLanguage = data["language"] as? String ?: language
    if (!resultLanguage.equals(language, ignoreCase = true)) return unavailable()
    val error = data["error"]
    if (error != null && error != false && error != "") return unavailable()
    val status = data["status"] as? String
    if (status != null && status != "available" && status != "empty") return unavailable()
    val seen = mutableSetOf<String>()
    val genders = listOf("male", "female", "neutral")
    val voices = genders.flatMap { group ->
        (groups[group] as? List<*>).orEmpty().mapNotNull { entry ->
            val fields = entry as? Map<*, *>
            val id = ((entry as? String) ?: (fields?.get("id") as? String))
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val declaredProvider = fields?.get("provider") as? String
            if (declaredProvider != null && !declaredProvider.equals(provider, true)) return@mapNotNull null
            if (!seen.add(id)) return@mapNotNull null
            val suppliedGender = fields?.get("gender") as? String
            val gender = if (suppliedGender == null) group else suppliedGender.takeIf { it in genders } ?: "neutral"
            TranslationVoice(
                id = id,
                name = (fields?.get("name") as? String)?.takeIf { it.isNotBlank() } ?: id,
                gender = gender,
                provider = provider,
                language = resultLanguage
            )
        }
    }
    return TranslationVoiceCatalogue(
        provider = provider,
        language = resultLanguage,
        voices = if (status == "empty") emptyList() else voices,
        status = if (status == "empty" || voices.isEmpty()) "empty" else "available",
        model = data["model"] as? String,
        truncated = data["truncated"] == true
    )
}
