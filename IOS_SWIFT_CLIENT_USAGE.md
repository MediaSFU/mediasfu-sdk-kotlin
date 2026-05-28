# MediaSFU Swift iOS Client Usage

This guide covers the Swift-facing iOS client shipped from `mediasfu-sdk-kotlin`. It is the app integration layer that hosts the shared MediaSFU UI on iOS through the exported Kotlin Multiplatform framework.

For the lower-level standalone mediasoup/WebRTC package, see the [mediasfu-mediasoup-client-apple](https://github.com/MediaSFU/mediasfu-mediasoup-client-apple) repository.

## What You Get

- A Swift-friendly `MediaSFUIosHostBridge` exported by the shared KMP framework.
- A `MediaSFUIosLaunchConfig` object so Swift apps do not need to construct the full Kotlin `MediasfuGenericOptions` object directly.
- Full hosted UI mode for normal create/join flows.
- No-UI mode parity with React and Flutter through `autoProceed`, which maps to `returnUI=false` plus create/join prejoin options.
- Optional native iOS bridge wiring through `MediaSFUIosBridge` and `MediaSFUMediasoupClient` when the real WebRTC/libmediasoupclient artifacts are linked.

## Endpoint Policy

Production cloud is the default. Leave `localLink` empty for MediaSFU Cloud, and provide `apiUserName` plus a 64-character `apiKey`.

Use `localLink` only for MediaSFU CE or a private backend root, such as `https://your-ce-host.example.com`. Do not put the cloud `/v1/rooms` endpoint into `localLink`.

The default endpoint is production cloud (`https://mediasfu.com/v1/rooms`). Do not override `MEDIASFU_CLOUD_ROOMS_ENDPOINT` in shipping code; that env var is reserved for self-hosted or custom backend deployments only.

## Install In An iOS App

The sample app uses CocoaPods for the generated KMP framework:

```ruby
target 'YourApp' do
  use_frameworks!
  pod 'shared', :path => '../shared'
end
```

Then run:

```sh
cd ios-sample-app
pod install
open MediaSFUSampleApp.xcworkspace
```

In Swift, import the generated framework as either `shared` or `MediaSFUSDK`, depending on the module name Xcode exposes:

```swift
#if canImport(shared)
import shared
#elseif canImport(MediaSFUSDK)
import MediaSFUSDK
#endif
```

## Hosted UI Mode

Hosted UI mode presents the shared MediaSFU interface. This is the mode most apps should start with.

```swift
let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-64-character-api-key"
config.localLink = ""
config.connectMediaSFU = true
config.userName = "alice"
config.roomName = "mediasfu-demo"
config.action = "create"
config.eventType = "conference"
config.durationMinutes = 60
config.capacity = 100
config.autoProceed = false
config.useModernUI = true
config.useModernTheme = true

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

The sample app wraps this with `MediaSFUHostViewController`, which pins the hosted controller to the full window and hides the status/home indicator while in-call.

## No-UI Mode

React uses `returnUI={false}` and `noUIPreJoinOptions`. Flutter uses `returnUI: false` with either `noUIPreJoinOptionsCreate` or `noUIPreJoinOptionsJoin`.

The Swift bridge mirrors that through `config.autoProceed = true`:

- `action = "create"` builds the create no-UI options and bypasses the prejoin UI.
- `action = "join"` builds the join no-UI options and bypasses the prejoin UI.
- `returnUI` becomes false only when those no-UI options are available.

### No-UI Create

```swift
let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-64-character-api-key"
config.connectMediaSFU = true
config.localLink = ""
config.action = "create"
config.userName = "host1"
config.eventType = "conference"
config.durationMinutes = 60
config.capacity = 100
config.secureCode = ""
config.safeRoom = false
config.recordOnly = false
config.autoProceed = true

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

### No-UI Join

```swift
let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-64-character-api-key"
config.connectMediaSFU = true
config.localLink = ""
config.action = "join"
config.roomName = "s1234567"
config.userName = "guest1"
config.adminPasscode = ""
config.islevel = "0"
config.autoProceed = true

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

## Media Controls From Swift

`MediaSFUIosHostBridge` exposes Swift-callable control hooks after the host has been created:

```swift
let didRequestAudio = bridge.triggerToggleAudio()
let didRequestVideo = bridge.triggerToggleVideo()
let didRequestScreenShare = bridge.triggerToggleScreenShare()
```

These return `false` when the shared UI has not installed a handler yet. Call them after the room UI is mounted.

## Sample App Configuration

The sample app reads values from the Swift bootstrap form, process environment, or env-style files. Useful keys include:

```text
MEDIASFU_API_USERNAME=your-api-username
MEDIASFU_API_KEY=your-64-character-api-key
MEDIASFU_LOCAL_LINK=
MEDIASFU_CONNECT_MEDIA_SFU=true
MEDIASFU_USER_NAME=alice
MEDIASFU_ROOM_NAME=mediasfu-demo
MEDIASFU_ACTION=create
MEDIASFU_AUTO_PROCEED=true
MEDIASFU_EVENT_TYPE=conference
MEDIASFU_DURATION_MINUTES=60
MEDIASFU_CAPACITY=100
```

File lookup order:

1. `MEDIASFU_BOOTSTRAP_ENV_FILE`
2. `MEDIASFU_CREDS_FILE`
3. `Documents/mediasfu_sample_env.txt`
4. `Documents/mediasfu_ui_test_env.txt`
5. `/tmp/mediasfu_sample_env.txt`, `/tmp/mediasfu_ui_test_env.txt`, `/tmp/mediasfu_creds.txt` on simulator/macOS-backed runs

## Native Bridge Modes

The KMP-hosted Swift client can run with the shared WebRTC abstractions, and it can also install the local native iOS bridge package for real mediasoup/WebRTC validation.

Use these flags when validating the real native bridge path:

```sh
MEDIA_SFU_ENABLE_IOS_NATIVE_BRIDGE_PACKAGE=1 \
MEDIA_SFU_ENABLE_REAL_LIBMEDIASOUPCLIENT_BINDING=1 \
MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE=1 \
xcodebuild -workspace ios-sample-app/MediaSFUSampleApp.xcworkspace \
  -scheme MediaSFUSampleApp \
  -configuration Debug \
  -sdk iphoneos \
  -destination 'generic/platform=iOS' \
  build CODE_SIGNING_ALLOWED=NO
```

For simulator compile checks, pre-sync the shared framework before Xcode invokes the CocoaPods script phase:

```sh
cd mediasfu-sdk-kotlin
ARCHS=arm64 SDK_NAME=iphonesimulator PLATFORM_NAME=iphonesimulator CONFIGURATION=Debug \
  ./gradlew --no-daemon -p "$PWD" :shared:syncFramework \
  -Pkotlin.native.cocoapods.platform=iphonesimulator \
  -Pkotlin.native.cocoapods.archs=arm64 \
  -Pkotlin.native.cocoapods.configuration=Debug

OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES \
  ./gradlew :shared:compileKotlinIosSimulatorArm64 --no-daemon --stacktrace
```

## Validation Checklist

- Hosted UI opens full-screen from Swift.
- Create flow works with `autoProceed=false` and the visible prejoin UI.
- No-UI create flow works with `autoProceed=true`, `action="create"`.
- No-UI join flow works with `autoProceed=true`, `action="join"` and an active room.
- Local audio/video toggles are callable through the bridge.
- Screen-share publish uses `appData.mediaTag=screen-video` and remote screen-share consume updates the runtime probe.
- Production cloud is the default; staging appears only when explicitly supplied for validation.

## Current Status

- Physical iPhone validation has confirmed local video production, local screen-share production, browser-to-iPhone screen-share consume, and late-join receive refresh through this hosted path.
- The shared UI has responsive video, audio, mini-card, and modal sizing for iPhone-width layouts.
- macOS package tests pass for the safe native package surface, but real macOS media is not claimed until the macOS WebRTC artifact exports the required ObjC symbols and a runtime gate passes.
