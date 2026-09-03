<p align="center">
  <img src="https://www.mediasfu.com/logo192.png" width="100" alt="MediaSFU Logo">
</p>

<p align="center">
  <a href="https://twitter.com/media_sfu">
    <img src="https://img.shields.io/badge/Twitter-1DA1F2?style=for-the-badge&logo=twitter&logoColor=white" alt="Twitter" />
  </a>
  <a href="https://www.mediasfu.com/forums">
    <img src="https://img.shields.io/badge/Community-Forum-blue?style=for-the-badge&logo=discourse&logoColor=white" alt="Community Forum" />
  </a>
  <a href="https://github.com/MediaSFU">
    <img src="https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white" alt="Github" />
  </a>
  <a href="https://www.mediasfu.com/">
    <img src="https://img.shields.io/badge/Website-4285F4?style=for-the-badge&logo=google-chrome&logoColor=white" alt="Website" />
  </a>
  <a href="https://www.youtube.com/channel/UCELghZRPKMgjih5qrmXLtqw">
    <img src="https://img.shields.io/badge/YouTube-FF0000?style=for-the-badge&logo=youtube&logoColor=white" alt="Youtube" />
  </a>
</p>

<p align="center">
  <a href="https://opensource.org/licenses/MIT">
    <img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square" alt="License: MIT" />
  </a>
  <a href="https://mediasfu.com">
    <img src="https://img.shields.io/badge/Built%20with-MediaSFU-blue?style=flat-square" alt="Built with MediaSFU" />
  </a>
  <a href="https://kotlinlang.org">
    <img src="https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  </a>
  <a href="https://developer.android.com/jetpack/compose">
    <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpack-compose&logoColor=white" alt="Jetpack Compose" />
  </a>
</p>

---

# MediaSFU Kotlin SDK

**Real-time video conferencing for Android & Kotlin Multiplatform** — drop-in solution with pre-built UI or full customization.

📖 **[Full Documentation →](README_FULL.md)** | 🌐 **[mediasfu.com](https://www.mediasfu.com/)**

---

## ⚡ Quick Start (2 Minutes)

### 1. Add Dependency

```kotlin
// build.gradle.kts (app level)
dependencies {
    // For Android-only projects (recommended):
    implementation("com.mediasfu:mediasfu-sdk-android:1.0.5")
    
    // For Kotlin Multiplatform projects:
    // implementation("com.mediasfu:mediasfu-sdk:1.0.5")
}
```

The Android SDK supplies `com.mediasfu:mediasoup-client:1.0.7` transitively. Do not add or pin an older client version.

> **Protect API credentials:** Android apps cannot keep embedded API keys secret. For production, create/join rooms through your backend proxy and return only the room response the app needs. Direct credentials are for fast local or staging development. See the [MediaSFU Sandbox](https://www.mediasfu.com/sandbox) for API request/response shapes and [MediaSFU](https://www.mediasfu.com/) for API access.
>
> **MediaSFU Open** is your own locally/self-hosted MediaSFU media server. It is separate from MediaSFU Cloud; configure the SDK to use the URL of the server you operate.

### 2. Use It

```kotlin
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.model.Credentials

@Composable
fun App() {
    // Option 1: No credentials (testing/demo)
    MediasfuGeneric()
    
    // Development only: direct MediaSFU Cloud credentials
    // MediasfuGeneric(
    //     options = MediasfuGenericOptions(
    //         credentials = Credentials(apiUserName = "your_username", apiKey = "your_api_key")
    //     )
    // )
}
```

### 3. Run

```bash
./gradlew :androidApp:installDebug
```

**Done!** You have a full-featured video conferencing app with:
- ✅ Video & audio streaming
- ✅ Screen sharing
- ✅ Chat messaging
- ✅ Participant management
- ✅ Recording capabilities
- ✅ Breakout rooms & polls

---

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    // For Android-only projects (recommended):
    implementation("com.mediasfu:mediasfu-sdk-android:1.0.5")
    
    // For Kotlin Multiplatform projects:
    // implementation("com.mediasfu:mediasfu-sdk:1.0.5")
}
```

### Android Permissions

Add to `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

---

## 🎯 Room Types

All room types are convenience wrappers around `MediasfuGeneric` with pre-configured event types:

| Type | Default Event Type | Use Case |
|------|-------------------|----------|
| `MediasfuGeneric` | `CONFERENCE` | General meetings |
| `MediasfuBroadcast` | `BROADCAST` | Live streaming |
| `MediasfuWebinar` | `WEBINAR` | Educational sessions |
| `MediasfuConference` | `CONFERENCE` | Business meetings |
| `MediasfuChat` | `CHAT` | Chat-focused |

```kotlin
import com.mediasfu.sdk.ui.mediasfu.*
import com.mediasfu.sdk.model.Credentials

val options = MediasfuGenericOptions(
    // Development only. In production, create/join rooms through your backend.
    credentials = Credentials(apiUserName = "user", apiKey = "key")
)

// Pick the right one for your use case - all use the same options!
MediasfuWebinar(options = options)
MediasfuBroadcast(options = options)
MediasfuConference(options = options)
```

---

## 🧩 Components (Flutter/React-like API)

Import and use components directly — no boilerplate:

```kotlin
import com.mediasfu.sdk.ui.AudioGrid
import com.mediasfu.sdk.ui.FlexibleGrid
import com.mediasfu.sdk.ui.components.display.AudioGridOptions
import com.mediasfu.sdk.ui.components.display.FlexibleGridOptions

@Composable
fun CustomLayout(parameters: MediasfuParameters) {
    // Just like Flutter/React!
    AudioGrid(AudioGridOptions(
        participants = parameters.participants,
        columnsPerRow = 3
    ))
    
    FlexibleGrid(FlexibleGridOptions(
        parameters = parameters,
        columns = 2
    ))
}
```

### Available Components

| Component | Description |
|-----------|-------------|
| `AudioGrid` | Audio participant grid |
| `AudioCard` | Single audio participant |
| `FlexibleGrid` | Flexible video grid |
| `FlexibleVideo` | Main video display |
| `MiniCard` | Compact participant card |
| `Pagination` | Page controls |
| `AlertComponent` | Alert messages |
| `MainAspectComponent` | Layout container |

---

## 🎨 Customization Modes

### Mode 1: Default UI (Easiest)

```kotlin
@Composable
fun App() {
    MediasfuGeneric(options = options)
}
```

### Mode 2: Custom UI with MediaSFU Backend

```kotlin
@Composable
fun App() {
    var parameters by remember { mutableStateOf<MediasfuParameters?>(null) }
    
    // Hidden MediaSFU backend
    MediasfuGeneric(
        options = options.copy(returnUI = false),
        onParametersUpdate = { parameters = it }
    )
    
    // Your custom UI
    parameters?.let { params ->
        Column {
            Text("Room: ${params.roomName}")
            Button(onClick = { params.clickVideo(params) }) {
                Text(if (params.videoAlreadyOn) "Stop Video" else "Start Video")
            }
        }
    }
}
```

### Mode 3: Render the standard UI from an engine-owned state

`ModernMediasfuGenericHead` renders the exact same maintained room tree as
`MediasfuGeneric`; it does not create another socket, media controller, or copy of
room state. This is useful when your Compose application owns the state and decides
where the standard MediaSFU interface belongs.

```kotlin
@Composable
fun RoomScreen(options: MediasfuGenericOptions) {
    val roomState = rememberMediasfuGenericState(options)

    ModernMediasfuGenericHead(
        state = roomState,
        modifier = Modifier.fillMaxSize(),
    )
}
```

Create the state once and render either `MediasfuGeneric` or
`ModernMediasfuGenericHead`, not both. Modals, sidebar navigation, media controls,
reconnection, and teardown remain owned by that single state. For a fully custom
interface, continue to use headless parameters and component overrides.

### Mode 4: Replace Specific Components

```kotlin
@Composable
fun CustomMainScreen(parameters: MediasfuParameters) {
    Column {
        FlexibleVideo(FlexibleVideoOptions(parameters = parameters))
        FlexibleGrid(FlexibleGridOptions(parameters = parameters))
    }
}

MediasfuGeneric(
    options = options,
    customComponent = { CustomMainScreen(it) }
)
```

### Headless controller and live media

Use the UI-independent controller when your app owns every Compose surface:

```kotlin
import com.mediasfu.sdk.headless.MediaSfuHeadlessController

val headless = MediaSfuHeadlessController(engine)
val snapshot = headless.snapshot() // immutable copy; call again for current state

if (snapshot.readiness.mediaControlsReady) {
    val video = headless.participantMedia(
        participantName = "Ada",
        kind = "video"
    )
    // Render `video` with your platform video renderer when non-null.
}

// Do not filter audio by the visible video page: every remote audio stream is here.
val allAudio = snapshot.media.audioStreams
```

Polling and Compose rendering must call `engine.getCurrentParams()` for a pure read. `getUpdatedAllParams()` is retained for update/publication workflows and should not be used as a getter. `updateSourceParameters` is dispatched from Compose effects, after composition.

For a headless Cloud flow, surface pre-join failures in your own UI:

```kotlin
val options = MediasfuGenericOptions(
    returnUI = false,
    noUIPreJoinOptions = noUiOptions,
    onPreJoinError = { message ->
        uiState.update { it.copy(errorMessage = message) }
    }
)
```

Create and join rooms through your backend proxy in production so API credentials never ship in the app. `onPreJoinError` reports validation, connection, and timeout failures without requiring the built-in pre-join screen.

For an embedded room, set first-class fractions on `MediasfuGenericOptions`; values are clamped to the parent bounds and default to filling the parent:

```kotlin
val embedded = MediasfuGenericOptions(
    containerWidthFraction = 0.75f,
    containerHeightFraction = 0.65f
)
```

### Let a host leave without ending the room

Host exits preserve existing behavior by default and end the room. Set `endRoomOnHostExit` to `false` when your product offers a separate **Leave room** action:

```kotlin
import com.mediasfu.sdk.methods.exit_methods.ConfirmExitOptions
import com.mediasfu.sdk.methods.exit_methods.confirmExit

confirmExit(
    ConfirmExitOptions(
        socket = socket,
        member = member,
        roomName = roomName,
        endRoomOnHostExit = false
    )
)
```

The built-in host exit dialog exposes both **Leave room** and **End for everyone**. Participants still receive the normal leave action.

### Virtual backgrounds and breakout rooms in custom Compose UI

Keep the SDK background component tied to the room engine's current state and
render the processed local track when one is active; do not maintain a separate
preview-only camera pipeline. Android virtual backgrounds use the SDK's native
processor. Check platform support before offering the control on another
target.

Use the SDK breakout planner or its typed room operations for assignment,
**Save**, and **Start**. Moving a participant requires a room-membership
transition; filtering Compose tiles is presentation only and cannot update
media consumers. Render validation failures in your own surface when running
headless.

---

## 🔧 Key Methods

```kotlin
// Media controls
parameters.clickVideo(parameters)       // Toggle video
parameters.clickAudio(parameters)       // Toggle audio
parameters.clickScreenShare(parameters) // Toggle screen share

// State
parameters.videoAlreadyOn              // Boolean
parameters.audioAlreadyOn              // Boolean
parameters.participants                // List<Participant>

// Modals
parameters.updateIsParticipantsModalVisible(true)
parameters.updateIsMessagesModalVisible(true)
```

---

## 🏠 Self-Hosting

No API key needed for self-hosting. Use [MediaSFU Open](https://github.com/MediaSFU/MediaSFUOpen):

```kotlin
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions

MediasfuGeneric(
    options = MediasfuGenericOptions(
        connectMediaSFU = false,
        localLink = "http://your-server:3000"
    )
)
```

---

## 📚 More Resources

- **[Full Documentation](README_FULL.md)** — Complete API reference, all components, methods
- **[mediasfu.com](https://www.mediasfu.com/)** — Official docs
- **[Community Forum](https://www.mediasfu.com/forums)** — Get help
- **[GitHub](https://github.com/MediaSFU)** — Source code

---

## 📄 License

MIT © [MediaSFU](https://mediasfu.com)
