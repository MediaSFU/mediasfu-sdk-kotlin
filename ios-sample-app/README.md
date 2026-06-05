# iOS Sample App Scaffold

This folder contains a minimal scaffold for an iOS sample app that is intended to host the shared MediaSFU UI from `mediasfu-sdk-kotlin`.

## What this scaffold includes

- SwiftUI app entry placeholder
- bootstrap/configuration screen placeholders with the full currently-supported no-UI create/join payload surface mirrored into Swift form state
- bridge installation wrapper
- host container scaffolding plus a real adapter path that uses the exported KMP iOS host bridge when the generated module is linked
- permission coordinator placeholder
- ReplayKit coordinator placeholder
- `Info.plist` template
- generated `MediaSFUSampleApp.xcodeproj` and CocoaPods workspace
- starter `Podfile` that consumes the shared KMP pod

Note: direct linking of `ios-native-bridge` into this sample target is currently optional in the scaffold because it can introduce duplicate `WebRTC.framework` embedding when CocoaPods `shared` integration is also active.

## Deployment target note

- The checked-in local Swift package `MediaSFUIosBridge` currently declares iOS `15.0+` support.
- The sample app target and Podfile should therefore use an iOS deployment target of at least `15.0`.

## Current build target note

- The current CocoaPods WebRTC dependency chain resolves to a device-only binary in this environment, so iOS Simulator builds do not link successfully.
- Generic iOS device builds do succeed with the generated workspace and CocoaPods setup.
- Device builds are currently the reliable validation path for this scaffold.

## What this scaffold does not include yet

- device-backed native mediasoup bridge installation as the default real-session path
- completed runtime validation of create/join, produce/consume, and media controls on device
- simulator-capable WebRTC packaging in this environment
- a ReplayKit Broadcast Upload Extension

## Current Xcode project state

This folder now has a generated app project and workspace. Treat `MediaSFUSampleApp.xcworkspace` as the main entry point after running CocoaPods.

If the project is regenerated, keep the existing checked-in Swift files attached to the app target and keep `MediaSFUSampleApp/Resources/Info.plist` as the app plist source.

For the exact Xcode/CocoaPods/package wiring steps, see `XCODE_SETUP.md`.
The folder now also includes a starter `Podfile`, plus a shared-module-backed iOS host bridge used by `RealMediaSFUSDKHostAdapterTemplate.swift`.
Once the generated KMP module is importable in Xcode, the sample host adapter should switch from scaffold fallback to the real shared host automatically.
Use `API_DISCOVERY_CHECKLIST.md` only to confirm the final Xcode-visible module and symbol names if they differ from the expected defaults.

For app-facing Swift integration, endpoint policy, and no-UI usage parity with React/Flutter, see `../IOS_SWIFT_CLIENT_USAGE.md`.

## Suggested next manual Xcode steps

1. Run `pod install` from `ios-sample-app/` if the workspace needs to be refreshed.
2. Open `MediaSFUSampleApp.xcworkspace`, not the `.xcodeproj`.
3. Confirm the app target imports the generated KMP module as either `MediaSFUSDK` or `shared`.
4. Resolve the `ios-native-bridge` WebRTC duplication strategy before linking the real device-backed bridge package.
5. Validate camera, microphone, transports, ReplayKit in-app capture, whiteboard, and virtual background flows on a physical device.

## Expected first milestone

The first useful milestone is a runnable app that:

- opens the shared `MediaSFUHostContainer` directly when the session config is already valid, and falls back to `SampleBootstrapView` only for invalid or explicitly bootstrap-driven launches
- installs the iOS bridge during startup
- presents `MediaSFUHostContainer`
- switches to the exported shared host automatically once the KMP module is linked in Xcode
- lets the tester populate the supported create/join fields that map into the shared no-UI prejoin options
- confirms camera/microphone permission flow works
- is ready for device/runtime validation of the real MediaSFU host flow

## Manual launch defaults

The sample app now remembers the last session configuration entered on that device, including API username, API key, local link, display name, and the rest of the bootstrap form state. That means a normal app launch can return to a usable prejoin form instead of depending on Xcode test-only environment injection.

The bootstrap screen also checks for optional env-style override files with `MEDIASFU_*` entries. The lookup order is:

1. `MEDIASFU_BOOTSTRAP_ENV_FILE` when the process environment defines it.
2. `MEDIASFU_CREDS_FILE` when the process environment defines it.
3. `Documents/mediasfu_sample_env.txt` inside the app container.
4. `Documents/mediasfu_ui_test_env.txt` inside the app container.
5. Simulator/macOS fallback files `/tmp/mediasfu_sample_env.txt`, `/tmp/mediasfu_ui_test_env.txt`, and `/tmp/mediasfu_creds.txt`.

Process environment values still take precedence over file-backed defaults.

## Real native bridge reminder

When building the physical iPhone app for real mediasoup validation, you must enable the native libmediasoup binding at build time:

```bash
MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING=1 xcodebuild ...
```

Without that build-time flag, the app can still launch into the shared prejoin UI but the bridge will fall back to placeholder / pending-binding mode instead of `fullyBundledNative`.

For manual app launches from `Documents/mediasfu_sample_env.txt`, you can also set:

```text
MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE=1
```

The sample app now reads that override from the same bootstrap env file path as the rest of the `MEDIASFU_*` settings, so placeholder fallback is surfaced immediately instead of being easy to miss.
