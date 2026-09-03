# MediaSFU iOS sample app

This SwiftUI sample demonstrates both supported Apple SDK entry paths:

- hosted UI, where MediaSFU presents the create/join form and meeting UI;
- headless launch, where an app supplies validated create/join details and
  embeds the meeting experience in its own flow.

For most applications, install the published
[MediaSFU Apple SDK](https://github.com/MediaSFU/mediasfu-apple-sdk) with Swift
Package Manager and follow [Swift iOS client usage](../IOS_SWIFT_CLIENT_USAGE.md).
This sample workspace is useful when developing the Kotlin Multiplatform SDK
from source.

## Run the sample from this repository

Requirements:

- Xcode with an iOS 15 or newer simulator or device
- CocoaPods

From this directory, install the local SDK dependencies and open the workspace:

```sh
pod install
open MediaSFUSampleApp.xcworkspace
```

Select the `MediaSFUSampleApp` scheme, choose an iOS simulator or connected
iPhone, and run the app. Use the MediaSFU form to create or join a room.

For MediaSFU Cloud, enter your API username and API key and leave `localLink`
empty. Use `localLink` only for a self-hosted MediaSFU deployment. Never commit
credentials to the project, source-control configuration, or screenshots.

The app's Info.plist already includes camera and microphone permission text.
When testing on a device, accept those permissions before enabling media.

## Headless app integration

An application backend may create or join a room and return the room-scoped
`roomName`, `secret`, and `link`. Supply those values as `roomName`,
`roomApiToken`, and `roomLink`, then set `autoProceed` to `true`. Keep account
credentials on the backend.

The sample also accepts launch configuration through its form and scheme
environment, which is useful for repeatable local testing. Do not include
environment files containing credentials in source control.

## Native media bridge

The published Apple SDK includes the native mediasoup/WebRTC integration. When
building this repository's local packages instead, ensure the real native
client is linked and keep its device object alive for the lifetime of the
meeting:

```swift
let nativeDevice = MSCDevice()
MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(
    device: nativeDevice
)
```
