# MediaSFU Swift iOS Client Usage

This guide covers the Swift-facing iOS integration for MediaSFU. The easiest install path is the published Swift Package:

```text
https://github.com/MediaSFU/mediasfu-apple-sdk.git
```

That package includes the hosted MediaSFU UI and the native mediasoup/WebRTC bridge. You do not need to write Kotlin or build the Kotlin Multiplatform repository yourself.

For custom low-level mediasoup/WebRTC work, see
[mediasfu-mediasoup-client-apple](https://github.com/MediaSFU/mediasfu-mediasoup-client-apple).

## What You Get

- **`MediaSFUIosHostBridge`** — the single entry point for presenting the MediaSFU UI from Swift.
- **`MediaSFUIosLaunchConfig`** — a plain Swift object for configuring a session without writing any Kotlin.
- Hosted UI mode — the full pre-join form plus in-room UI, ready out of the box.
- Headless mode — skip the pre-join form and auto-connect with your own session parameters.
- Virtual background (iOS 15+) — person segmentation with blur, colour, or image backgrounds.
- Screen sharing, chat, polls, recording controls, and all other in-room features.

## Requirements

- **iOS 15+**.
- An API username and API key from [mediasfu.com](https://mediasfu.com) for cloud rooms.
- Swift Package Manager through Xcode.

## Endpoint Selection

MediaSFU Cloud is the default. Leave `localLink` empty and provide your
`apiUserName` and `apiKey` for cloud rooms.

Use `localLink` only for a self-hosted MediaSFU deployment you control. Do not
point `localLink` at `mediasfu.com` or any cloud room endpoint.

## Install with Swift Package Manager

In Xcode, choose **File → Add Package Dependencies…** and add:

```text
https://github.com/MediaSFU/mediasfu-apple-sdk.git
```

Then import the package:

```swift
import MediaSFUAppleSDK
import MediaSFUMediasoupClient
```

## Local Source Checkout for Contributors

If you are developing the SDK itself from this repository, you can still use the generated CocoaPods framework locally:

```ruby
target 'YourApp' do
  use_frameworks!
  pod 'shared', :path => '../path/to/mediasfu-sdk-kotlin/shared'
end
```

Then install and open the workspace:

```sh
pod install
open YourApp.xcworkspace
```

Import the local module in Swift:

```swift
import MediaSFUSDK
```

## Hosted UI Mode

Hosted UI is the default. The SDK presents a pre-join form where users enter their
display name and room details, then transitions to the full in-room UI.

```swift
import MediaSFUAppleSDK

// Store nativeDevice as a property on the presenting view/controller.
let nativeDevice = MSCDevice()
MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: nativeDevice)

let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-api-key"
config.connectMediaSFU = true
config.userName = "alice"         // pre-fills the display name field
config.action = "create"         // "create" or "join"
config.eventType = "conference"  // "conference", "broadcast", "webinar", "chat"
config.durationMinutes = 60
config.capacity = 100
config.autoProceed = false       // false = show the pre-join form

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

## Headless Mode (No Pre-Join Form)

Set `autoProceed = true` to skip the pre-join form entirely and connect automatically
using the parameters you supply. The UI goes straight to the in-room experience.

This mirrors `returnUI=false` in the React SDK and Flutter SDK.

### Reusing a backend create/join response

When your backend already performed the account-authenticated MediaSFU create/join
request, pass its room-scoped response through the launch config:

```swift
// Non-secret bootstrap values used only until the room handoff is applied.
config.apiUserName = "roomUser"
config.apiKey = String(repeating: "0", count: 64)
config.connectMediaSFU = true

config.action = "join"
config.roomName = response.roomName
config.roomApiToken = response.secret
config.roomLink = response.link
config.userName = displayName
config.autoProceed = true
```

The bootstrap values satisfy launch validation but are not used to authenticate
the room. Before the socket connection, the SDK uses `roomName` as the socket
`apiUserName`, `roomApiToken` (the response `secret`) as the socket `apiToken`, and
`roomLink` as the media node. This avoids repeating the account-level REST request
from the client. Keep account API credentials on your backend. Leave
`roomApiToken` and `roomLink` empty when the SDK should run the normal cloud
create/join flow.

### Headless Create

```swift
import MediaSFUAppleSDK

// Store nativeDevice as a property on the presenting view/controller.
let nativeDevice = MSCDevice()
MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: nativeDevice)

let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-api-key"
config.connectMediaSFU = true
config.action = "create"
config.userName = "host1"
config.eventType = "conference"
config.durationMinutes = 60
config.capacity = 100
config.autoProceed = true   // skip the pre-join form

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

### Headless Join

```swift
import MediaSFUAppleSDK

// Store nativeDevice as a property on the presenting view/controller.
let nativeDevice = MSCDevice()
MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: nativeDevice)

let bridge = MediaSFUIosHostBridge()
let config = bridge.makeLaunchConfig()

config.apiUserName = "your-api-username"
config.apiKey = "your-api-key"
config.connectMediaSFU = true
config.action = "join"
config.roomName = "s1234567"
config.userName = "guest1"
config.islevel = "0"        // use the role level assigned to this participant
config.autoProceed = true   // skip the pre-join form

let controller = bridge.makeHostViewController(config: config)
controller.modalPresentationStyle = .fullScreen
present(controller, animated: true)
```

## SwiftUI Integration

Wrap the UIKit view controller in a `UIViewControllerRepresentable` to embed MediaSFU inside SwiftUI:

```swift
import SwiftUI
import MediaSFUAppleSDK

struct MediaSFUView: UIViewControllerRepresentable {
    let apiUserName: String
    let apiKey: String
    let userName: String
    var action: String = "create"   // "create" or "join"
    var roomName: String = ""       // leave empty to auto-generate on create
    var autoProceed: Bool = false
    private let nativeDevice = MSCDevice()

    func makeUIViewController(context: Context) -> UIViewController {
        let bridge = MediaSFUIosHostBridge()
        let config = bridge.makeLaunchConfig()
        config.apiUserName = apiUserName
        config.apiKey = apiKey
        config.connectMediaSFU = true
        config.userName = userName
        config.roomName = roomName
        config.action = action
        config.durationMinutes = 60
        config.capacity = 100
        config.eventType = "conference"
        config.autoProceed = autoProceed
        MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: nativeDevice)
        return bridge.makeHostViewController(config: config)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

// Minimal SwiftUI usage:
struct ContentView: View {
    var body: some View {
        MediaSFUView(
            apiUserName: "your-api-username",
            apiKey: "your-api-key",
            userName: "alice"
        )
        .ignoresSafeArea()
    }
}
```

## Media Controls

`MediaSFUIosHostBridge` lets you trigger media actions programmatically from Swift after
the room is mounted:

```swift
let bridge = MediaSFUIosHostBridge()
// ... present the controller first ...

// Toggle microphone
bridge.triggerToggleAudio()

// Toggle camera
bridge.triggerToggleVideo()

// Start / stop screen share
bridge.triggerToggleScreenShare()
```

Each call returns `true` when the room accepted the action and `false` when its
handler is not ready. Call them after the host view controller has appeared.

## Virtual Background (iOS 15+)

Virtual background uses Apple's Vision framework for on-device person segmentation.
No extra configuration is required — the SDK checks the OS version at runtime and
enables the feature automatically.

Users can choose preset images, blur, solid colors, or a custom image from the
Virtual Background modal.

### Background options available to users

| Option | What it does |
|--------|----------|
| **None** | Passes the camera feed through unchanged |
| **Blur** | Applies a gaussian blur behind the person |
| **Color** | Fills the background with a solid color |
| **Custom image** | Replaces the background with a photo |

### Remote participants

The processed video stream (with the background applied) is what remote participants see.
The effect runs locally on the sending device before the video is encoded and transmitted.

### Simulator

Background controls appear on all iOS 15+ simulator builds. Person segmentation runs on
whatever camera frame the simulator provides. For visual accuracy, test on a physical device
with the front camera.

## Participant Level

Set `config.islevel` when joining a room:

| Value | Role |
|-------|------|
| `"0"` | Listener / viewer |
| `"1"` | Speaker / participant |
| `"2"` | Admin / host — can manage participants, start media, and control the room |

Use `"2"` when creating a room as the host.

## Room Passcode

To restrict admin access, supply a `secureCode` when creating a room. Participants
who need admin access provide the same code as `adminPasscode` when joining:

```swift
// Host (create)
config.secureCode = "your-room-passcode"

// Admin participant (join)
config.adminPasscode = "your-room-passcode"
config.islevel = "2"
```

## Event Types

Set `config.eventType` to match the type of room you are creating:

| Value | Description |
|-------|-------------|
| `"conference"` | All participants can share video and audio |
| `"broadcast"` | One broadcaster, audience members receive only |
| `"webinar"` | Host + panelists present; attendees can be promoted |
| `"chat"` | Text and audio only, no video grid |

## Supported Features

| Feature | iOS 15+ |
|---------|---------|
| Full hosted UI (create / join) | ✅ |
| Headless mode (`autoProceed`) | ✅ |
| Chat, polls, participants list | ✅ |
| Screen sharing (ReplayKit) | ✅ |
| Recording controls | ✅ |
| Virtual Background | ✅ |

## Validated Capabilities

The following capabilities have been validated on physical iPhone hardware and iOS simulator:

- Hosted UI presents full-screen from UIKit and SwiftUI.
- Create and join flows work with both the visible pre-join form and headless mode.
- Headless mode (`autoProceed = true`) skips the pre-join form and connects immediately.
- Local camera and microphone produce a live stream visible to remote participants.
- Screen share produces a stream visible to remote participants.
- Virtual Background (iOS 15+): person segmentation runs on-device; the background-replaced
  stream is transmitted to remote participants.
- In-room controls (mute, camera, hang-up, chat, participants) are accessible and functional.
