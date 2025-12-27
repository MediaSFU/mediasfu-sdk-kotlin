# MediaSFU Demo App

This demo app showcases different ways to use the MediaSFU Kotlin SDK, similar to the examples provided in the React and Flutter SDKs.

## Demo Modes

### 1. High-Level (Default UI)
Full MediaSFU experience with the default UI. Uses `MediasfuGeneric` with `PreJoinPage`. Best for quick integration when you want the complete MediaSFU experience out of the box.

```kotlin
MediasfuGeneric(
    options = MediasfuGenericOptions(
        preJoinPageWidget = { state -> PreJoinPage(state) },
        connectMediaSFU = true,
        credentials = credentials,
        sourceParameters = engineParameters,
        // returnUI = true (default)
    )
)
```

### 2. Community Edition
Connect to a self-hosted MediaSFU Community Edition server with the default UI.

```kotlin
MediasfuGeneric(
    options = MediasfuGenericOptions(
        preJoinPageWidget = { state -> PreJoinPage(state) },
        localLink = "http://your-server:3000",
        connectMediaSFU = false, // Don't use cloud
        sourceParameters = engineParameters,
    )
)
```

### 3. Custom UI (returnUI = false)
Use MediaSFU's backend engine while building your own completely custom UI. Access all state and methods via `sourceParameters`.

```kotlin
MediasfuGeneric(
    options = MediasfuGenericOptions(
        connectMediaSFU = true,
        credentials = credentials,
        sourceParameters = engineParameters,
        returnUI = false, // Don't show MediaSFU's default UI
        noUIPreJoinOptionsCreate = mapOf(
            "userName" to "DemoUser",
            "duration" to 30,
            "capacity" to 10,
            "eventType" to "conference"
        )
    )
)

// Then build your custom UI accessing state from engineParameters:
// - engineParameters.participants
// - engineParameters.videoAlreadyOn
// - engineParameters.audioAlreadyOn
// - engineParameters.roomName
// etc.
```

### 4. Custom Component Override
Replace the main workspace with your custom component while keeping the PreJoin flow.

```kotlin
MediasfuGeneric(
    options = MediasfuGenericOptions(
        preJoinPageWidget = { state -> PreJoinPage(state) },
        connectMediaSFU = true,
        credentials = credentials,
        sourceParameters = engineParameters,
        customComponent = { state -> 
            // Your custom workspace UI
            MyCustomWorkspace(state)
        }
    )
)

@Composable
fun MyCustomWorkspace(state: MediasfuGenericState) {
    // Access state via public API:
    val roomName = state.room.roomName
    val participants = state.room.participants
    val isVideoOn = state.media.videoAlreadyOn
    
    // Use toggle methods:
    Button(onClick = { state.toggleVideo() }) { ... }
    Button(onClick = { state.toggleAudio() }) { ... }
}
```

### 5. PreJoin Page Only
Custom wrapper around the PreJoinPage with your own branding.

```kotlin
MediasfuGeneric(
    options = MediasfuGenericOptions(
        preJoinPageWidget = { state -> 
            CustomPreJoinWrapper(state)
        },
        connectMediaSFU = true,
        credentials = credentials,
        sourceParameters = engineParameters,
    )
)

@Composable
fun CustomPreJoinWrapper(state: MediasfuGenericState) {
    Column {
        // Your custom header
        Text("Welcome to My App")
        
        // Default PreJoinPage
        PreJoinPage(state)
    }
}
```

## Key API Classes

### MediasfuGenericOptions
Main configuration options for the SDK:

| Property | Type | Description |
|----------|------|-------------|
| `preJoinPageWidget` | Composable | Custom PreJoin page widget |
| `localLink` | String | Community Edition server URL |
| `connectMediaSFU` | Boolean | Use MediaSFU Cloud (true) or CE (false) |
| `credentials` | Credentials | API credentials for cloud |
| `sourceParameters` | MediasfuParameters | Shared state object |
| `returnUI` | Boolean | Whether to render default UI |
| `noUIPreJoinOptionsCreate` | Map | Auto-create room options |
| `noUIPreJoinOptionsJoin` | Map | Auto-join room options |
| `customComponent` | Composable | Replace main workspace |

### MediasfuGenericState
Public API for controlling the SDK:

| Method/Property | Description |
|-----------------|-------------|
| `room` | Room state (roomName, participants, etc.) |
| `media` | Media state (videoAlreadyOn, audioAlreadyOn, etc.) |
| `streams` | Stream management state |
| `recording` | Recording state and controls |
| `toggleVideo()` | Toggle camera on/off |
| `toggleAudio()` | Toggle microphone on/off |
| `toggleScreenShare()` | Toggle screen sharing |

### MediasfuParameters
Low-level parameters for `returnUI = false` mode:

```kotlin
// State properties
parameters.participants       // List of all participants
parameters.messages           // Chat messages
parameters.roomName           // Current room name
parameters.member             // Current user's name
parameters.islevel            // User role ("0"=participant, "2"=host)
parameters.videoAlreadyOn     // Is video enabled
parameters.audioAlreadyOn     // Is audio enabled  
parameters.screenAlreadyOn    // Is screen share active
parameters.recordStarted      // Is recording active
```

## Running the Demo

```bash
./gradlew :demo-app:installDebug
```

## Requirements

- Android SDK 26+
- Valid MediaSFU Cloud credentials (for cloud modes)
- Community Edition server (for CE mode)
