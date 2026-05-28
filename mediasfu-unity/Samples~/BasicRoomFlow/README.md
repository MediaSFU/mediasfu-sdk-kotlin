# Basic Room Flow Sample

This sample folder now includes a bootstrap `MonoBehaviour` for the first Unity package cut.

The package can perform REST create and join calls today, validate a room over Socket.IO, emit leave/chat/socket-level media control signals, and expose a unified `IMediaSfuWebRtcDevice` install path plus reusable local and remote media bridge hooks with typed create/connect/produce/consume helpers. Concrete media runtime owners are supplied by `com.mediasfu.mediasoup-client-unity`; with that package installed, the sample can attach the concrete native-plugin engine and drive a minimal remote video texture path, while richer multi-participant UI and broader playback surfaces remain follow-up work.

The checked-in macOS/editor validation path already proves the package backend with real credentialed validation traffic before this sample enters the picture: microphone publish, camera publish, remote consume, post-media chat, approval-driven audio, moderated video, participant screen-share approval, host screen-share start/consume/stop, recording start/pause/resume/stop, co-host assignment, poll create/vote/end, participant removal, and host end-meeting forced exit all pass through the headless validator. The validation environment currently enforces a 15-second wait before pause and another 15-second wait before resume, and the checked-in validator already accounts for both. Use this sample for scene wiring, manual observation, and lightweight UI binding rather than as the first source of runtime proof.

## Current Bootstrap Script

Use `Runtime/MediaSfuBasicRoomFlow.cs` on a GameObject to:

1. configure cloud or community connection settings in the inspector
2. trigger `CreateRoomAsync` or `JoinRoomAsync`
3. trigger `ConnectMediaAsync`, `LeaveRoomAsync`, chat send, mic mute/resume, camera start/stop, screen-share start/stop, and recording start/pause/resume/stop
4. approve or reject either the first or the currently selected pending media request exposed on `CurrentRoom.PendingRequests`
5. allow or deny either the first or the currently selected waiting-room participant exposed on `CurrentRoom.WaitingRoomParticipants`
6. acknowledge the local confirm-here prompt and optionally suppress future prompts for the current client instance
7. surface connection, error, moderation totals, waiting-room notices, active poll state, breakout state, screen-producer receive state, consume-domain updates, whiteboard state, recording, and participant-side request-response state via inspector status text, indexed inspector snapshots, dedicated `UnityEvent<string>` hooks for room, participant, breakout, consume-domain, poll, and whiteboard snapshots, plus bool/int state events for counts, visibility flags, recording paused state, and recording pause/resume availability
8. attach an `IMediaSfuLocalMediaBackend` from scene code, or hand the sample an `IMediaSfuTransportBackedLocalMediaAdapter` through `AttachTransportBackedLocalMediaAdapter(...)` so it can build and own a `MediaSfuTransportBackedLocalMediaBackend`; `MediaSfuDelegateTransportBackedLocalMediaAdapter` can be used for delegate-based bridge glue instead of a full adapter class, and the wrapper will reapply the resulting backend automatically whenever the sample recreates its underlying `MediaSfuClient`
9. attach an `IMediaSfuRemoteMediaBridge` from scene code, or hand the sample an `IMediaSfuTransportBackedRemoteMediaAdapter` through `AttachTransportBackedRemoteMediaAdapter(...)` so it can build and own a `MediaSfuTransportBackedRemoteMediaBridge` that follows typed `RemoteProducerAvailable` and `RemoteProducerClosed` events across client recreation
10. attach an `IMediaSfuWebRtcDevice` from scene code, or hand the sample both transport-backed adapters through `AttachTransportBackedWebRtcDevice(...)` so it can build and own a single `MediaSfuTransportBackedWebRtcDevice` that reattaches both publish and playback flows together across client recreation
11. hand the sample one `IMediaSfuWebRtcEngine` through `AttachTransportBackedWebRtcEngine(...)` so it can build and own a `MediaSfuWebRtcEngineDevice` and keep publish plus playback attached together across client recreation without separate adapter classes; `MediaSfuDelegateWebRtcEngine` can bootstrap that path without a dedicated engine type
12. call `AttachNativePluginWebRtcEngine(...)` to let the sample build the client package's concrete native-plugin engine for you; if the binary is missing the sample raises a status error instead of throwing, and `../../scripts/build_unity_native_bridge_package.sh` is the shortest way to refresh the current bridge scaffold for editor, Android, or iOS smoke tests
13. add `Runtime/MediaSfuNativePluginRemoteVideoView.cs` to a GameObject, point it at the same `MediaSfuBasicRoomFlow`, and optionally a target `Renderer`; when the Apple native-plugin path is attached it will follow remote video or screen-share tracks and upload the latest BGRA frame into a Unity `Texture2D`

Leaving the inspector `baseUrl` empty uses production cloud endpoints. Set it to your server root or a full `/v1/rooms` endpoint only when using a self-hosted backend.

It is still intentionally minimal. The current sample is now better suited for live scene wiring, inspector-driven moderation, state inspection, and first-pass Apple native-plugin remote video rendering, and it exposes scene-usable count plus visibility events so simple Unity UI controls can bind without parsing strings while still driving the new recording control methods from inspector or scene bindings. It now preserves either directly attached bridges, sample-owned transport-backed bridges, or a sample-owned engine-backed device across internal client recreation, but a fuller sample scene should still land once concrete multi-participant playback, audio playout validation, capture adapters, and richer moderation plus recording UI are implemented.

## Intended First Sample Scene

Build a minimal scene with:

1. an input field for API username
2. an input field for API key
3. an input field for room name or meeting ID
4. an input field for display name
5. a create button
6. a join button
7. a status label bound to `ConnectionStateChanged`

## Expected Bootstrap Flow

```csharp
var client = new MediaSfuClient(
    new MediaSfuClientOptions
    {
        ConnectionMode = MediaSfuConnectionMode.Cloud,
        // Optional: BaseUrl = "https://your-mediasfu-server.example.com",  // custom or self-hosted backend
        Credentials = new MediaSfuCredentials
        {
            ApiUserName = apiUserName,
            ApiKey = apiKey
        }
    }
);

client.ConnectionStateChanged += state => { /* update status text */ };
client.ErrorOccurred += error => { /* show error text */ };

await client.JoinRoomAsync(
    new MediaSfuJoinRoomRequest
    {
        MeetingId = roomName,
        UserName = displayName,
        IsLevel = "0"
    }
);
```

## When To Add Actual Sample Assets

Expand this into a real sample scene after the next milestone lands:

- receive-only media implemented
- rendered participant tiles available in Unity without platform-specific UI glue
- request, waiting-room, recording, and whiteboard-oriented controls surfaced in an actual Unity UI instead of status text, scene events, and inspector helpers

At that point a prefab and bootstrap `MonoBehaviour` become worth checking in.
