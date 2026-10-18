# Apple translation voice validation

This guide describes how to validate the Kotlin translation catalogue and stock-voice picker in the Apple framework. Android compilation alone does not establish Kotlin/Native, Swift linkage, simulator or device support.

## Pin the source and build on Mac

Use the exact Kotlin commit selected for validation and record `git rev-parse HEAD`. Preserve existing changes in both SDK repositories. Read their applicable repository instructions before editing.

Use JDK 17, Xcode with its command-line tools selected, CocoaPods and the Android SDK required by the existing Gradle project. Follow the Apple SDK's [KMP binary build guide](https://github.com/MediaSFU/mediasfu-apple-sdk/blob/main/KMP_BINARY_SYNC.md) and inspect its current helper before running it.

From the Apple SDK checkout, point the build helper at the pinned Kotlin checkout:

```sh
MEDIA_SFU_KOTLIN_REPO=/path/to/mediasfu-sdk-kotlin \
  MEDIA_SFU_JAVA_HOME="$(/usr/libexec/java_home -v 17)" \
  bash scripts/prepare_kmp_binary_dist.sh
```

For a fresh acceptance build, leave device/simulator skip options disabled and use release framework slices. Retain the device and Apple Silicon simulator framework build results, XCFramework, archive checksum and the helper's source-revision metadata. Build an Intel simulator slice only if it is part of the supported distribution.

If Kotlin/Native rejects an API or dependency, fix it in the shared source rather than substituting Android results. Keep any necessary platform adapter small and preserve the room-aware catalogue contract described in [Translation voice selection](translation-voices.md).

## Verify the actual Swift integration

The Apple SDK's `Exports.swift` conditionally imports `MediaSFUSDK` or `shared`. A Swift build that succeeds without importing either framework is insufficient. Inspect the generated framework headers and ensure the app links the freshly built runtime.

Validate the exported catalogue result, voice fields and suspend-function completion/error path from Swift. If Swift cannot consume the exported socket or collection types directly, add and test a small Swift-friendly adapter. It must preserve cancellation, bounded requests, neutral voices, provider/model metadata, and safe empty/error results.

Wire the new XCFramework into the Apple package's actual target dependencies. For binary distribution, use the repository's manifest-rendering helper with the reviewed release URL and the checksum of that exact archive. Keep source, SDK version, framework, asset and SwiftPM manifest consistent. Use a new version and immutable asset URL for a public release; preserve existing versioned archives and checksums. Publishing artifacts or changing release tags is a separate release step.

Run the Apple package tests and build the iOS sample against this actual framework. Retain simulator and device destinations, exact commands, exit codes, compiler results and source revision. Do not mark native compilation accepted from a header inspection alone.

## Runtime acceptance

Use an authorized test environment and owned room. Keep credentials on the backend. Before creating a room or placing a call, follow that environment's fixture, safety and cleanup instructions.

Verify these behaviours in the real app:

1. Join a translation-enabled room, choose an output language and load its configured provider's catalogue.
2. Select a neutral voice where available. Confirm that the outgoing settings contain that exact `voiceId` and `ttsProvider`, and that translated audio is audible.
3. Change language, room, connection and provider/model configuration. Old responses and selections must not become applicable in the new context.
4. Exercise empty, unavailable, malformed and timed-out catalogue responses. The UI must show an honest unavailable state and must not submit a guessed stock voice.
5. Preserve basic voice preference and any existing application-provided clone behaviour; do not add credential or provider-authorization bypasses.
6. Leave the room and confirm room/media cleanup and ended microphone/camera tracks.

Record native compilation, Swift linkage, simulator behaviour, physical-device audio and publication as separate results. Stop with the actual blocker when any required platform or acceptance step is unavailable.
