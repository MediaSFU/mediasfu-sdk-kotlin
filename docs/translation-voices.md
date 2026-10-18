# Translation voice selection

The MediaSFU Kotlin SDK discovers stock TTS voices from the authenticated room's configured provider. The built-in translation settings offer a basic gender preference and an Advanced stock-voice picker. The same catalogue API is available to applications with a custom UI.

## Use the built-in picker

Join a translation-enabled room, open translation settings, enable spoken translation and choose an output language. Select **Advanced**, wait for the catalogue and select a voice before saving. Entries include their reported gender; neutral voices remain neutral. The menu scrolls through the returned list.

The catalogue is authoritative for that room and output language. Changing the room, connection, language or translation configuration clears the previous selection. An unavailable or empty catalogue does not substitute an unrelated provider's voice IDs. Basic selection continues to use the existing server-controlled voice policy.

## Discover voices in a custom UI

Call this suspend function with the socket used to join the room:

```kotlin
import com.mediasfu.sdk.socket.fetchTranslationVoices

val catalogue = fetchTranslationVoices(
    socket = joinedSocket,
    roomName = joinedRoomName,
    language = outputLanguage
)

val voices = catalogue.voices
// Display the entries and let the user select one. Do not assume the first voice.
```

`TranslationVoiceCatalogue` includes `provider`, `language`, `voices`, `status`, `model`, `schemaVersion` and `truncated`. Each voice includes its exact `id`, display `name`, `gender`, provider and language. The request waits at most 20 seconds by default. Cancellation follows the calling coroutine; timeouts and unavailable responses return an unavailable catalogue with no selectable voices.

When the user applies a selection, obtain its validated payload from the same current catalogue:

```kotlin
val voiceConfig = catalogue.selectionConfig(selectedVoiceId)
if (voiceConfig != null) {
    joinedSocket.emit(
        "translation:setMyLanguage",
        mapOf(
            "roomName" to joinedRoomName,
            "language" to spokenLanguage,
            "defaultOutputLanguage" to outputLanguage,
            "enabled" to true,
            "voiceConfig" to voiceConfig
        )
    )
}
```

The selection contains both `voiceId` and `ttsProvider`. Keep it bound to the room, connection, output language and configuration that produced the catalogue; refetch after any of those changes. The server still validates authorization, provider compatibility and the selected ID. Never embed provider API keys or choose another account's credentials in a client application.

The classic event-settings API retains `onApplyTranslationSettings` for existing integrations. Applications using `EventSettingsModalOptions` can opt into the additive `onApplyTranslationVoiceSettings` callback to receive a stock-voice configuration alongside the existing language and listening preferences.

## Compatibility and validation

The parser supports version 1 catalogues, including legacy string voice IDs grouped by gender. Unsupported schemas, malformed responses, provider errors and mismatched-language replies cannot authorize a stock selection. Voice cloning continues to use the separate server-validated clone configuration; the stock catalogue does not enumerate or create clones.

Android validation commands, using JDK 17 and an installed Android SDK:

```sh
./gradlew :shared:compileDebugKotlinAndroid :shared:compileReleaseKotlinAndroid \
  :shared:testDebugUnitTest \
  --tests com.mediasfu.sdk.socket.TranslationVoiceCatalogueTest \
  -Pandroid.useAndroidX=true \
  '-Dorg.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=1024m' \
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=2
```

These checks cover catalogue parsing, socket requests, bounded waits, cancellation and selection state. They do not establish physical-device audio playback or Apple framework acceptance. See [Apple translation validation](apple-translation-validation.md) for the Mac build and runtime checks.
