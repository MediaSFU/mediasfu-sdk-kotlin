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

## 🚨 **BREAKING: AI Phone Agents at $0.10 per 1,000 minutes**

📞 **Call our live AI demos right now:**
- 🇺🇸 **+1 (785) 369-1724** - Mixed Support Demo  
- 🇬🇧 **+44 7445 146575** - AI Conversation Demo  
- 🇨🇦 **+1 (587) 407-1990** - Technical Support Demo  
- 🇨🇦 **+1 (647) 558-6650** - Friendly AI Chat Demo  

**Traditional providers charge $0.05 per minute. We charge $0.10 per 1,000 minutes. That's 500x cheaper.**

✅ **Deploy AI phone agents in 30 minutes**  
✅ **Works with ANY SIP provider** (Twilio, Telnyx, Zadarma, etc.)  
✅ **Seamless AI-to-human handoffs**  
✅ **Real-time call analytics & transcription**  

📖 **[Complete SIP/PSTN Documentation →](https://mediasfu.com/telephony)**

---

MediaSFU offers a cutting-edge streaming experience that empowers users to customize their recordings and engage their audience with high-quality streams. Whether you're a content creator, educator, or business professional, MediaSFU provides the tools you need to elevate your streaming game.

<div style="text-align: center;">

<img src="https://mediasfu.com/images/header_1.jpg" alt="Preview Page" title="Preview Page" style="max-height: 600px;">

</div>

---

# MediaSFU Kotlin SDK Documentation

## Unlock the Power of MediaSFU Community Edition  

**MediaSFU Community Edition is free and open-source**—perfect for developers who want to run their own media server without upfront costs. With robust features and simple setup, you can launch your media solution in minutes. **Ready to scale?** Upgrade seamlessly to **MediaSFU Cloud** for enterprise-grade performance and global scalability.  

**[Get started now on GitHub!](https://github.com/MediaSFU/MediaSFUOpen)** 

---

## Table of Contents

- [Features](#features)
- [Getting Started](#getting-started)
- [📱 Kotlin SDK Guide](#kotlin-sdk-guide)
  - [Quick Start](#quick-start-5-minutes)
  - [Understanding the Architecture](#understanding-mediasfu-architecture)
  - [Core Concepts & Components](#core-concepts--components)
  - [Working with Methods](#working-with-methods)
  - [Media Streams & Participants](#media-streams--participants)
  - [Customization & Styling](#customization--styling)
- [API Reference](#api-reference)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

---

# Features <a name="features"></a>

MediaSFU's Kotlin SDK comes with a host of powerful features out of the box:

1. **Screen Sharing with Annotation Support**: Share your screen with participants and annotate in real-time for enhanced presentations and collaborations.
2. **Collaborative Whiteboards**: Create and share whiteboards for real-time collaborative drawing and brainstorming sessions.
3. **Breakout Rooms**: Create multiple sub-meetings within a single session to enhance collaboration and focus.
4. **Pagination**: Efficiently handle large participant lists with seamless pagination.
5. **Polls**: Conduct real-time polls to gather instant feedback from participants.
6. **Media Access Requests Management**: Manage media access requests with ease to ensure smooth operations.
7. **Video Effects**: Apply various video effects, including virtual backgrounds, to enhance the visual experience.
8. **Chat (Direct & Group)**: Facilitate communication with direct and group chat options.
9. **Cloud Recording (track-based)**: Customize recordings with track-based options, including watermarks, name tags, background colors, and more.
10. **Managed Events**: Manage events with features to handle abandoned and inactive participants, as well as enforce time and capacity limits.

## 🆕 **New Advanced Media Access**

**Interested in getting just the media stream of a specific participant?** You can now easily retrieve individual participant streams using `parameters.getParticipantMedia()`.

**Need to access available cameras and microphones?** Use `parameters.getMediaDevicesList()` to enumerate all available media devices on the user's system programmatically.

---

# Getting Started <a name="getting-started"></a>

This section will guide users through the initial setup and installation of the Kotlin SDK.

### Documentation Reference

For comprehensive documentation on the available methods, components, and functions, please visit [mediasfu.com](https://www.mediasfu.com/kotlin/). This resource provides detailed information for this guide and additional documentation.

## Installation

### 1. Add the Package to Your Project

**Gradle (Kotlin DSL)**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}

// build.gradle.kts (app level)
dependencies {
    // For Android-only projects (recommended):
    implementation("com.mediasfu:mediasfu-sdk-android:1.0.0")
    
    // For Kotlin Multiplatform projects:
    // implementation("com.mediasfu:mediasfu-sdk:1.0.0")
}
```

**Gradle (Groovy)**

```groovy
// build.gradle (app level)
dependencies {
    implementation 'com.mediasfu:mediasfu-sdk:1.0.0'
}
```

### 2. Obtain an API Key (If Required)

You can get your API key by signing up or logging into your account at [mediasfu.com](https://www.mediasfu.com/).

> **Important:** You must obtain an API key from [mediasfu.com](https://www.mediasfu.com/) to use this package with MediaSFU Cloud. You do not need the API Key if self-hosting.

## **Self-Hosting MediaSFU**

If you plan to self-host MediaSFU or use it without MediaSFU Cloud services, you don't need an API key. You can access the open-source version of MediaSFU from the [MediaSFU Open Repository](https://github.com/MediaSFU/MediaSFUOpen).

This setup allows full flexibility and customization while bypassing the need for cloud-dependent credentials.

---

### 3. Configure Your Project

#### Android Setup

1. **Update Gradle Configuration**

   Open the `build.gradle.kts` file located at `<project root>/app/build.gradle.kts`:

   ```kotlin
   android {
       compileSdk = 34
       defaultConfig {
           minSdk = 24
           targetSdk = 34
       }
       compileOptions {
           sourceCompatibility = JavaVersion.VERSION_17
           targetCompatibility = JavaVersion.VERSION_17
       }
       kotlinOptions {
           jvmTarget = "17"
       }
       buildFeatures {
           compose = true
       }
   }
   ```

2. **AndroidManifest Permissions**

   Open the `AndroidManifest.xml` file located at `<project root>/app/src/main/AndroidManifest.xml`:

   ```xml
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.CAMERA" />
   <uses-permission android:name="android.permission.RECORD_AUDIO" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
   <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
   <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
   <uses-permission android:name="android.permission.WAKE_LOCK" />

   <!-- Optional: Bluetooth support -->
   <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
   <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
   <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
   ```

---

# 📱 Kotlin SDK Guide <a name="kotlin-sdk-guide"></a>

This comprehensive guide will walk you through everything you need to know about building real-time communication apps with MediaSFU's Kotlin SDK. Whether you're a beginner or an experienced developer, you'll find clear explanations, practical examples, and best practices.

---

## Quick Start (5 Minutes) <a name="quick-start-5-minutes"></a>

Get your first MediaSFU app running in just a few minutes.

### Step 1: Install the Package

```kotlin
// build.gradle.kts
dependencies {
    // For Android-only projects (recommended):
    implementation("com.mediasfu:mediasfu-sdk-android:1.0.0")
    
    // For Kotlin Multiplatform projects:
    // implementation("com.mediasfu:mediasfu-sdk:1.0.0")
}
```

### Step 2: Import and Use

```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.model.Credentials
import com.mediasfu.sdk.model.EventType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Option 1: Use without credentials (for testing)
            MediasfuGeneric()

            // Option 2: Use with MediaSFU Cloud credentials
            // val credentials = Credentials(
            //     apiUserName = "your_username",
            //     apiKey = "your_api_key"
            // )
            // val options = MediasfuGenericOptions(credentials = credentials)
            // MediasfuGeneric(options = options)
        }
    }
}
```

### Step 3: Run Your App

```bash
./gradlew :app:installDebug
```

**That's it!** You now have a fully functional video conferencing app with:
- ✅ Video and audio streaming
- ✅ Screen sharing
- ✅ Chat messaging
- ✅ Participant management
- ✅ Recording capabilities
- ✅ Breakout rooms

---

## Understanding MediaSFU Architecture <a name="understanding-mediasfu-architecture"></a>

Before diving deeper, let's understand how MediaSFU is structured.

### The Three-Layer Architecture

```
┌─────────────────────────────────────────────┐
│        Your Kotlin/Android Application       │
│   (MainActivity, Composables, ViewModels)   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        MediaSFU Components Layer            │
│  (MediasfuGeneric, MediasfuBroadcast, etc.) │
│        - Pre-built UI components             │
│        - Event handling                      │
│        - State management                    │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        MediaSFU Core Methods Layer          │
│   (Stream control, room management,         │
│    WebRTC handling, socket communication)   │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│        MediaSFU Backend Services            │
│ (MediaSFU Cloud or Community Edition)       │
└─────────────────────────────────────────────┘
```

### Key Concepts

#### 1. **Event Room Types**

MediaSFU provides 5 specialized room types, each optimized for specific use cases:

| Room Type | Best For | Default Event Type |
|-----------|----------|--------------------|
| **MediasfuGeneric** | General purpose meetings | `EventType.CONFERENCE` |
| **MediasfuBroadcast** | Live streaming events | `EventType.BROADCAST` |
| **MediasfuWebinar** | Educational sessions | `EventType.WEBINAR` |
| **MediasfuConference** | Business meetings | `EventType.CONFERENCE` |
| **MediasfuChat** | Interactive discussions | `EventType.CHAT` |

> **Note:** All room types are simple convenience wrappers around `MediasfuGeneric` that pre-configure the `defaultEventType`. They all accept `MediasfuGenericOptions`.

```kotlin
// Choose the right room type for your use case
import com.mediasfu.sdk.ui.mediasfu.*
import com.mediasfu.sdk.model.Credentials

val options = MediasfuGenericOptions(
    credentials = Credentials("your_username", "your_api_key")
)

// All room types use the same MediasfuGenericOptions
MediasfuWebinar(options = options)    // Pre-configured for webinars
MediasfuBroadcast(options = options)  // Pre-configured for broadcasts
MediasfuConference(options = options) // Pre-configured for conferences
MediasfuChat(options = options)       // Pre-configured for chat

// Or use MediasfuGeneric directly and set eventType manually
MediasfuGeneric(options = options.copy(defaultEventType = EventType.WEBINAR))
```

#### 2. **The Three Usage Modes**

MediaSFU offers three progressive levels of customization:

##### Mode 1: Default UI (Simplest)

Use MediaSFU's complete pre-built interface - perfect for rapid development.

```kotlin
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric

@Composable
fun App() {
    MediasfuGeneric(options = MediasfuGenericOptions(credentials = credentials))
}
```

**When to use:**
- ✅ Prototyping or MVP development
- ✅ Need a production-ready UI quickly
- ✅ Standard video conferencing features are sufficient

##### Mode 2: Custom UI with MediaSFU Backend (Most Flexible)

Build your own UI while using MediaSFU's powerful backend infrastructure.

```kotlin
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import androidx.compose.runtime.*

@Composable
fun App() {
    var parameters by remember { mutableStateOf<MediasfuParameters?>(null) }

    // MediaSFU backend (hidden UI)
    MediasfuGeneric(
        options = MediasfuGenericOptions(
            credentials = credentials,
            returnUI = false,
            noUIPreJoinOptions = NoUIPreJoinOptions(
                action = "create",
                userName = "Your Name",
                capacity = 50,
                duration = 30,
                eventType = "conference"
            )
        ),
        onParametersUpdate = { parameters = it }
    )

    // Your custom UI
    parameters?.let { params ->
        Column(modifier = Modifier.fillMaxSize()) {
            Text("Room: ${params.roomName}")
            Text("Participants: ${params.participants.size}")
            
            Row {
                Button(onClick = { params.clickVideo(params) }) {
                    Text(if (params.videoAlreadyOn) "Stop Video" else "Start Video")
                }
                Button(onClick = { params.clickAudio(params) }) {
                    Text(if (params.audioAlreadyOn) "Mute" else "Unmute")
                }
            }
        }
    }
}
```

**When to use:**
- ✅ Need complete control over UI/UX
- ✅ Building a custom branded experience
- ✅ Integrating into existing app design

##### Mode 3: Component Replacement (Balanced)

Replace specific MediaSFU components while keeping the rest of the infrastructure.

```kotlin
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.display.*

@Composable
fun CustomMainScreen(parameters: MediasfuParameters) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Custom header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(parameters.roomName ?: "Meeting")
            Text("${parameters.participants.size} participants")
        }

        // Use MediaSFU's components in your layout
        FlexibleVideo(FlexibleVideoOptions(
            customWidth = LocalConfiguration.current.screenWidthDp,
            customHeight = 400,
            parameters = parameters
        ))

        FlexibleGrid(FlexibleGridOptions(
            customWidth = LocalConfiguration.current.screenWidthDp,
            customHeight = 300,
            parameters = parameters
        ))

        // Custom footer controls
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { parameters.clickVideo(parameters) }) {
                Text(if (parameters.videoAlreadyOn) "Stop Video" else "Start Video")
            }
            Button(onClick = { parameters.clickAudio(parameters) }) {
                Text(if (parameters.audioAlreadyOn) "Mute" else "Unmute")
            }
        }
    }
}

@Composable
fun App() {
    MediasfuGeneric(
        options = options,
        customComponent = { parameters -> CustomMainScreen(parameters) }
    )
}
```

**When to use:**
- ✅ Need custom main interface but want to keep MediaSFU's components
- ✅ Partial customization with minimal effort
- ✅ Want to maintain MediaSFU's functionality while customizing layout

#### 3. **Parameters: Your Control Center**

The `parameters` object is your gateway to all MediaSFU functionality:

```kotlin
// Available in parameters object
data class MediasfuParameters(
    // Media Controls
    val clickVideo: (MediasfuParameters) -> Unit,
    val clickAudio: (MediasfuParameters) -> Unit,
    val clickScreenShare: (MediasfuParameters) -> Unit,

    // Room State
    val roomName: String?,
    val participants: List<Participant>,
    val allVideoStreams: List<Stream>,
    val allAudioStreams: List<Stream>,

    // UI State
    val videoAlreadyOn: Boolean,
    val audioAlreadyOn: Boolean,
    val screenAlreadyOn: Boolean,

    // Update Functions
    val updateVideoAlreadyOn: (Boolean) -> Unit,
    val updateAudioAlreadyOn: (Boolean) -> Unit,

    // And 200+ more properties and methods...
)
```

---

## Core Concepts & Components <a name="core-concepts--components"></a>

Now that you understand the architecture, let's explore the building blocks.

### 1. Display Components: Building Your Video Layout

MediaSFU provides powerful components for organizing and displaying media streams.

#### Simple API (Recommended)

Thanks to our Flutter/React-like API, you can use components directly:

```kotlin
import com.mediasfu.sdk.ui.*
import com.mediasfu.sdk.ui.components.display.*

@Composable
fun VideoLayout(parameters: MediasfuParameters) {
    Column {
        // Main video display
        FlexibleVideo(FlexibleVideoOptions(
            customWidth = screenWidth,
            customHeight = 600,
            parameters = parameters
        ))

        // Participant grid
        FlexibleGrid(FlexibleGridOptions(
            customWidth = screenWidth,
            customHeight = 400,
            parameters = parameters
        ))

        // Audio-only participants
        AudioGrid(AudioGridOptions(
            participants = parameters.participants.filter { !it.videoOn },
            columnsPerRow = 4
        ))
    }
}
```

#### Primary Layout Components

| Component | Purpose | Example |
|-----------|---------|---------|
| **FlexibleVideo** | Main video display | `FlexibleVideo(options)` |
| **FlexibleGrid** | Participant grid | `FlexibleGrid(options)` |
| **AudioGrid** | Audio-only participants | `AudioGrid(options)` |
| **Pagination** | Page navigation | `Pagination(options)` |

#### Card Components

| Component | Purpose | Example |
|-----------|---------|---------|
| **AudioCard** | Single audio participant | `AudioCard(options)` |
| **MiniCard** | Compact video card | `MiniCard(options)` |
| **MiniAudio** | Compact audio card | `MiniAudio(options)` |
| **CardVideoDisplay** | Video with controls | `CardVideoDisplay(options)` |

#### Container Components

| Component | Purpose | Example |
|-----------|---------|---------|
| **MainAspectComponent** | Aspect ratio container | `MainAspectComponent(options) { content }` |
| **MainContainerComponent** | Primary wrapper | `MainContainerComponent(options) { content }` |
| **MainScreenComponent** | Screen layout | `MainScreenComponent(options) { content }` |
| **MainGridComponent** | Grid layout | `MainGridComponent(options) { content }` |

**Example: Building a custom layout**

```kotlin
@Composable
fun CustomLayout(parameters: MediasfuParameters) {
    MainContainerComponent(MainContainerComponentOptions()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Main video area (60%)
            Box(modifier = Modifier.weight(3f)) {
                FlexibleVideo(FlexibleVideoOptions(
                    customWidth = screenWidth,
                    customHeight = (screenHeight * 0.6).toInt(),
                    parameters = parameters
                ))
            }

            // Participant grid (30%)
            Box(modifier = Modifier.weight(2f)) {
                FlexibleGrid(FlexibleGridOptions(
                    customWidth = screenWidth,
                    customHeight = (screenHeight * 0.3).toInt(),
                    parameters = parameters
                ))
            }

            // Audio-only participants (10%)
            Box(modifier = Modifier.height(80.dp)) {
                AudioGrid(AudioGridOptions(
                    participants = parameters.participants.filter { !it.videoOn }
                ))
            }
        }
    }
}
```

### 2. Control Components: User Interactions

**ControlButtonsComponent** - Standard control bar

```kotlin
import com.mediasfu.sdk.ui.components.ControlButtons
import com.mediasfu.sdk.ui.components.ControlButtonsOptions

ControlButtons(ControlButtonsOptions(
    parameters = parameters,
    position = "bottom" // or "top", "left", "right"
))
```

Includes: mute, video, screenshare, participants, chat, settings, etc.

**ControlButtonsAltComponent** - Alternative layout

```kotlin
ControlButtonsAltComponent(ControlButtonsAltComponentOptions(
    parameters = parameters,
    position = "top"
))
```

**ControlButtonsComponentTouch** - Touch-optimized controls

```kotlin
ControlButtonsComponentTouch(ControlButtonsComponentTouchOptions(
    parameters = parameters
))
```

### 3. Modal Components: Feature Interfaces

MediaSFU includes modals for various features:

```kotlin
// Control modal visibility via parameters
parameters.updateIsParticipantsModalVisible(true)
parameters.updateIsMessagesModalVisible(true)
parameters.updateIsSettingsModalVisible(true)
parameters.updateIsRecordingModalVisible(true)
parameters.updateIsPollModalVisible(true)
```

Available modals:
- **ParticipantsModal** - Participant list management
- **MessagesModal** - Chat interface
- **SettingsModal** - Event and room settings
- **DisplaySettingsModal** - Layout and display options
- **RecordingModal** - Recording controls and settings
- **PollModal** - Create and manage polls
- **BreakoutRoomsModal** - Breakout room management
- **MediaSettingsModal** - Camera/microphone selection
- **BackgroundModal** - Virtual background settings
- **WhiteboardModal** - Whiteboard configuration

**Example: Programmatically showing modals**

```kotlin
@Composable
fun CustomControls(parameters: MediasfuParameters) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = { parameters.updateIsParticipantsModalVisible(true) }) {
            Text("Participants (${parameters.participants.size})")
        }
        Button(onClick = { parameters.updateIsMessagesModalVisible(true) }) {
            Text("Chat")
        }
        Button(onClick = { parameters.updateIsRecordingModalVisible(true) }) {
            Text("Record")
        }
    }
}
```

---

## Working with Methods <a name="working-with-methods"></a>

MediaSFU provides comprehensive methods for controlling all aspects of your video conferencing app.

### Media Control Methods

```kotlin
// Toggle video on/off
parameters.clickVideo(parameters)

// Toggle audio on/off
parameters.clickAudio(parameters)

// Toggle screen sharing
parameters.clickScreenShare(parameters)

// Switch camera (front/back)
parameters.switchCamera()
```

### Room Management Methods

```kotlin
// Create a new room
parameters.createRoom(CreateRoomOptions(
    eventType = "conference",
    capacity = 50,
    duration = 60
))

// Join an existing room
parameters.joinRoom(JoinRoomOptions(
    roomName = "room-123",
    userName = "John Doe"
))

// Leave the room
parameters.disconnectRoom()
```

### Participant Methods

```kotlin
// Get all participants
val participants = parameters.participants

// Mute a participant (host only)
parameters.muteParticipant(participantId)

// Remove a participant (host only)
parameters.removeParticipant(participantId)

// Ban a participant (host only)
parameters.banParticipant(participantId)
```

### Recording Methods

```kotlin
// Start recording
parameters.startRecording(RecordingOptions(
    recordingType = "video",
    includeAudio = true
))

// Stop recording
parameters.stopRecording()

// Pause recording
parameters.pauseRecording()

// Resume recording
parameters.resumeRecording()
```

### Chat Methods

```kotlin
// Send a message
parameters.sendMessage(MessageOptions(
    message = "Hello everyone!",
    recipients = listOf() // Empty for broadcast
))

// Send direct message
parameters.sendDirectMessage(DirectMessageOptions(
    message = "Private message",
    recipientId = "user-123"
))
```

---

## Media Streams & Participants <a name="media-streams--participants"></a>

### Working with Streams

```kotlin
// Get all video streams
val videoStreams = parameters.allVideoStreams

// Get all audio streams
val audioStreams = parameters.allAudioStreams

// Get specific participant's media
val participantMedia = parameters.getParticipantMedia(GetParticipantMediaOptions(
    participantId = "producer-123",
    mediaType = "video"
))
```

### Media Device Utilities

```kotlin
// Get available cameras
val cameras = parameters.getMediaDevicesList("videoinput")

// Get available microphones
val microphones = parameters.getMediaDevicesList("audioinput")

// Get available speakers
val speakers = parameters.getMediaDevicesList("audiooutput")
```

### Stream Display

```kotlin
@Composable
fun ParticipantVideo(stream: Stream) {
    Box(modifier = Modifier.size(200.dp)) {
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    stream.videoTrack?.addSink(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

---

## Customization & Styling <a name="customization--styling"></a>

### Theme Customization

```kotlin
val customColors = MediasfuColors(
    primary = Color(0xFF6200EE),
    secondary = Color(0xFF03DAC6),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.White,
    onSecondary = Color.Black
)

MediasfuGeneric(
    options = MediasfuGenericOptions(
        credentials = credentials,
        colors = customColors
    )
)
```

### Custom Fonts

```kotlin
val customTypography = MediasfuTypography(
    heading = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp
    )
)

MediasfuGeneric(
    options = MediasfuGenericOptions(
        typography = customTypography
    )
)
```

### Component Overrides

```kotlin
@Composable
fun CustomAudioCard(options: AudioCardOptions) {
    // Your custom implementation
    Card(
        modifier = Modifier.padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(options.name, color = Color.White)
            // Add audio level indicator, etc.
        }
    }
}

AudioGrid(AudioGridOptions(
    participants = participants,
    customAudioCardComponent = { options -> CustomAudioCard(options) }
))
```

---

# API Reference <a name="api-reference"></a>

## MediasfuGenericOptions

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `credentials` | `Credentials?` | `null` | MediaSFU Cloud credentials |
| `returnUI` | `Boolean` | `true` | Whether to return MediaSFU UI |
| `connectMediaSFU` | `Boolean` | `true` | Connect to MediaSFU Cloud |
| `localLink` | `String?` | `null` | Self-hosted server URL |
| `noUIPreJoinOptions` | `NoUIPreJoinOptions?` | `null` | Auto-join options for headless mode |
| `colors` | `MediasfuColors?` | `null` | Custom color scheme |
| `typography` | `MediasfuTypography?` | `null` | Custom typography |

## Credentials

| Property | Type | Description |
|----------|------|-------------|
| `apiUserName` | `String` | MediaSFU API username |
| `apiKey` | `String` | MediaSFU API key |

## NoUIPreJoinOptions

| Property | Type | Description |
|----------|------|-------------|
| `action` | `String` | "create" or "join" |
| `userName` | `String` | Display name |
| `capacity` | `Int` | Max participants (create only) |
| `duration` | `Int` | Duration in minutes (create only) |
| `eventType` | `String` | "conference", "webinar", "broadcast", "chat" |
| `roomName` | `String?` | Room to join (join only) |

---

# Troubleshooting <a name="troubleshooting"></a>

## Common Issues

### Camera/Microphone Not Working

1. Check permissions in AndroidManifest.xml
2. Request runtime permissions:

```kotlin
val permissions = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)
requestPermissions(permissions, PERMISSION_REQUEST_CODE)
```

### Connection Issues

1. Verify internet connection
2. Check firewall settings (ports 443, 3000)
3. Verify credentials are correct

### Build Errors

1. Ensure minSdk >= 24
2. Update Kotlin to 1.9+
3. Enable Compose in build.gradle

---

# Contributing <a name="contributing"></a>

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

---

## License

MIT © [MediaSFU](https://mediasfu.com)

---

<p align="center">
  <strong>Built with ❤️ by the MediaSFU Team</strong>
</p>
