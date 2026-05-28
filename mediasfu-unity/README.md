# MediaSFU Unity

This package is the Phase 0 starting point for a MediaSFU Unity SDK.

It does not bundle the low-level mediasoup/WebRTC client. The current scope is:

- a Unity Package Manager package skeleton
- a stable C# public API contract
- reusable MediaSFU room, socket, moderation, recording, and media workflow orchestration
- integration seams for the standalone `com.mediasfu.mediasoup-client-unity` package
- request and response models aligned with the Kotlin SDK create and join shapes
- working REST create and join calls for cloud and community-style endpoints
- a documented path for the next REST, socket, and media milestones

## Current Status

This package is a contract-first starter with the first runtime slices now connected. `CreateRoomAsync` and `JoinRoomAsync` perform real REST calls and return parsed room results. `BuildSocketConnectionPlan()` and `ConnectMediaAsync()` can now resolve and expose the socket base URL, namespace, room name, and timeout plan after room creation or join, and `ConnectMediaAsync()` performs a real Engine.IO polling-open probe, completes the initial websocket namespace connect, validates standard cloud-style rooms with `joinRoom`, and keeps a reusable inbound event loop alive once the socket-side room validation succeeds. Chat send, ack-backed recording start/pause/resume/stop, and an optional `IMediaSfuLocalMediaBackend` seam for mic, camera, and screen-share control are wired, and the runtime now includes a concrete `MediaSfuTransportBackedLocalMediaBackend` that manages send-transport creation, transport-connect, produce, and per-track pause/close state while delegating package-specific capture and producer primitives to `IMediaSfuTransportBackedLocalMediaAdapter`. The runtime also now includes a concrete `MediaSfuTransportBackedRemoteMediaBridge` that manages receive-transport creation, recv-connect, consume, and consumer resume while delegating package-specific playback binding to `IMediaSfuTransportBackedRemoteMediaAdapter`. `MediaSfuDelegateTransportBackedLocalMediaAdapter` and `MediaSfuDelegateTransportBackedRemoteMediaAdapter` are available so Unity bridge code can supply those adapter primitives with delegates instead of full adapter classes, and `IMediaSfuWebRtcEngine` plus `MediaSfuDelegateWebRtcEngine`, `MediaSfuWebRtcEngineLocalMediaAdapter`, `MediaSfuWebRtcEngineRemoteMediaAdapter`, and `MediaSfuWebRtcEngineDevice` now provide a typed one-engine path for Unity WebRTC or native-plugin implementations that want to drive both publish and playback through a single bridge surface. Inbound `ban` now removes the named participant from Unity room state, inbound `updatedCoHost` now reflects co-host role changes through room state, inbound `allWaitingRoomMembers` now hydrates waiting-room participants onto `CurrentRoom`, inbound `userWaiting` now increments a room-level moderation count and stores the last waiting-room name notice until a full snapshot arrives, inbound `pollUpdated` now merges poll list and active poll state onto `CurrentRoom`, inbound `breakoutRoomUpdated` now hydrates breakout room state and host-side participant snapshots onto `CurrentRoom`, inbound `screenProducerId` now hydrates screen-producer receive and deferral state onto `CurrentRoom`, inbound `updateConsumingDomains` now hydrates the latest consume-domain and alternate-domain instructions onto `CurrentRoom`, inbound `whiteboardAction` now hydrates incremental collaborative whiteboard state, inbound `whiteboardUpdated` now hydrates full whiteboard user, shape, and lifecycle snapshots, inbound `participantRequested`, `updateMediaSettings`, `controlMediaHost`, `meetingTimeRemaining`, and `meetingStillThere` now update `CurrentRoom` through the standard `RoomChanged` path, inbound `hostRequestResponse` now also updates participant-side local request state on room state, `RemoteProducerAvailable` and `RemoteProducerClosed` now surface typed producer-discovery events for receive bridges, inbound `roomRecordParams`, `RecordingNotice`, `timeLeftRecording`, and `stoppedRecording` now hydrate recording config and runtime state, `startRecords` and `reInitiateRecording` now relay the shared `startRecordIng` emit, and inbound `disconnectUserSelf` plus `meetingEnded` now clear the active room and surface a forced exit through the error callback. Unity hosts can now answer pending requests and waiting-room participants, Unity clients can locally acknowledge or suppress confirm-here prompts, and the starter sample now exposes selected moderation targets, live recording-control wrappers, and attachable local/remote media bridge hooks plus scene-usable string snapshot channels and bool/int state events for counts and visibility flags in addition to the existing status output; without an attached engine or adapter-backed media bridge, the actual publish and playback media layers still defer until the package-specific media implementation is provided.

That is deliberate. The goal is to lock the Unity-facing API while incrementally filling in runtime behavior behind it.

Unity should currently be treated as a custom-UI integration SDK, not as a default full-meeting-app UI kit. The highest-value work at this stage is proving the room, socket, and media lifecycle repeatedly, then keeping one clearly supported media backend path green behind the existing contracts. Richer in-scene controls remain useful, but they are downstream of runtime proof and backend coverage.

The current native-plugin path is provided by `com.mediasfu.mediasoup-client-unity` and attaches through `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)`. Validation status depends on which native bridge is present behind the exported `MediaSfuUnity*` ABI. Keep the validation scripts as the support boundary and use `Documentation~/DELIVERY_CHECKLIST.md` for the current exit criteria before broadening claims to other Unity targets or environments.

## Local Install

From a Unity project:

1. Open Package Manager.
2. Choose `Add package from disk...`.
3. Install `com.mediasfu.mediasoup-client-unity` first, then select `mediasfu-unity/package.json` from this repository.

Cloud mode uses `https://mediasfu.com/v1/rooms` by default. For a self-hosted or custom backend, set `MediaSfuClientOptions.BaseUrl` to your server root or the full `/v1/rooms` endpoint.

## Package Layout

- `Runtime/`: MediaSFU C# contract and SDK facade
- `Documentation~/`: Unity-facing API contract and milestone notes
- `Samples~/`: starter sample guidance for the first room flow

A Unity validation project with a `MediaSfuRuntimeValidation.unity` scene and either a `MediaSfuRuntimeValidationFlow` or `MediaSfuBasicRoomFlow` component is required for the manual media gate. The validation scripts accept a `--project-path` argument to point to your project; set `MEDIASFU_UNITY_PROJECT_PATH` to configure a persistent default.

## Native Plugin Packaging

Native mediasoup/WebRTC plugin binaries live in the standalone `com.mediasfu.mediasoup-client-unity` package. Rebuild or refresh those artifacts from the SDK repo with `./scripts/build_unity_native_bridge_package.sh` when native bridge sources change, then publish the client package before publishing this SDK package.

## Codec Coverage

Unity does not implement audio/video codecs in managed C#; codec support comes from the attached native WebRTC/mediasoup backend. With the current Apple native bridge artifacts, the backend surface covers Opus, DTMF/`telephone-event`, PCMU, PCMA, VP8, VP9, and H264, with VP9 explicitly enabled in the Apple WebRTC build for browser screen-share interop. The packaged WebRTC defaults also expose the usual RTX, RED, ULPFEC/FlexFEC, comfort-noise, and related RTP capability entries when the active runtime reports them.

That means Unity can use the same codec set once it is backed by the fixed native iOS/Android media engine. Treat this as backend capability rather than a Unity-side transcoder claim; live Unity negotiation for PCMU, PCMA, and `telephone-event` should still be re-run on the larger machine when Unity validation resumes.

## Validation

The repository now includes two Unity validation tracks:

- batchmode cloud validation in `Documentation~/LIVE_VALIDATION.md`
- runtime media validation plus the manual companion checklist in `Documentation~/MEDIA_RUNTIME_CHECKLIST.md`
- priority order and exit criteria in `Documentation~/DELIVERY_CHECKLIST.md`

The validation project also now includes an editor-side runtime media automation entrypoint, `MediaSfuRuntimeMediaValidation.Run`, which covers both the backend-only probe and the real cloud media gate. Use it to confirm that the currently installed native bridge is real and that the active media path still works end to end.

For the narrower backend-only proof without cloud credentials, run `bash ./scripts/run_unity_runtime_backend_probe.sh`. That wrapper calls `MediaSfuRuntimeMediaValidation.RunBackendProbe` in batchmode and will use a temporary sibling project clone automatically if the main validation project is already open in Unity.

For the real cloud media gate, run `bash ./scripts/run_unity_runtime_media_validation.sh`. The checked-in batchmode path now covers bidirectional microphone and camera flows, post-media chat, approval-driven audio, moderated video, participant screen-share approval, host screen-share start/consume/stop, recording start/pause/resume/stop while media is already live, co-host assignment, poll create/vote/end, participant removal, and host end-meeting forced exit. The validation environment currently requires a 15-second wait before pause and another 15-second wait before resume, and the checked-in validator enforces those waits so the path stays green. For repeatability on that same path, run `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3` or raise the run count when you want a stronger local release gate.

From the repository root, the focused batchmode command is now wrapped by:

```sh
export MEDIASFU_UNITY_API_USERNAME="your-api-username"
export MEDIASFU_UNITY_API_KEY="your-64-char-api-key"
bash ./scripts/run_unity_live_validation.sh
```

That script targets the Unity project set via `MEDIASFU_UNITY_PROJECT_PATH` (or `--project-path`) and defaults to production cloud unless you override `MEDIASFU_UNITY_BASE_URL` or pass `--base-url`.

For the manual runtime gate, launch the editor directly from the repository root with:

```sh
bash ./scripts/open_unity_runtime_validation.sh
```

That launcher opens the configured validation project and `MediaSfuRuntimeValidation.unity` scene directly, so Unity Hub does not need to show the project in its recents list first. Set `MEDIASFU_UNITY_PROJECT_PATH` to your project root before running.

For automated re-runs on a licensed self-hosted macOS runner, use `.github/workflows/unity-live-validation.yml`.

## First Intended Usage

```csharp
using MediaSFU.Unity;

var client = new MediaSfuClient(
    new MediaSfuClientOptions
    {
        ConnectionMode = MediaSfuConnectionMode.Cloud,
        // Optional: BaseUrl = "https://your-mediasfu-server.example.com",  // custom or self-hosted backend
        Credentials = new MediaSfuCredentials
        {
            ApiUserName = "your-api-username",
            ApiKey = "your-64-char-api-key"
        }
    }
);

// Optional: attach the concrete native-plugin engine without relying on
// exception-driven setup in your scene code.
// var nativeDeviceResult = MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(
//     new MediaSfuNativePluginWebRtcEngineOptions
//     {
//         IntegrationMode = "ios-native-bridge",
//         ConfigurationJson = "{...}"
//     });
// if (nativeDeviceResult.Success)
// {
//     client.AttachWebRtcDevice(nativeDeviceResult.Value);
// }
//
// Or attach a single transport-backed WebRTC device from your own engine bridge.
// var device = new MediaSfuWebRtcEngineDevice(myEngine);
// client.AttachWebRtcDevice(device);
//
// Or attach a single device from separate local/remote adapters.
// var device = new MediaSfuTransportBackedWebRtcDevice(myLocalAdapter, myRemoteAdapter);
// client.AttachWebRtcDevice(device);
//
// Or attach the publish and playback sides separately.
// var backend = new MediaSfuTransportBackedLocalMediaBackend(myLocalAdapter);
// client.AttachLocalMediaBackend(backend);
// var remoteBridge = new MediaSfuTransportBackedRemoteMediaBridge(myRemoteAdapter);
// client.AttachRemoteMediaBridge(remoteBridge);

var result = await client.JoinRoomAsync(
    new MediaSfuJoinRoomRequest
    {
        MeetingId = "s12345678",
        UserName = "player-one",
        IsLevel = "0"
    }
);
```

Right now `JoinRoomAsync` performs the REST join call and returns a parsed room contract. `ConnectMediaAsync` now checks that a room exists, prepares `LastSocketConnectionPlan`, performs a live Engine.IO polling-open probe, opens a websocket transport through the initial namespace connect, validates standard cloud-style rooms via `joinRoom`, resolves generic Socket.IO acks through a reusable receive loop, exposes raw socket events plus parsed participant/chat updates through Unity events, exposes the latest probe and validation through `LastSocketHandshake` and `LastRoomValidation`, and returns `Success` once that socket-side room validation is live. `AttachWebRtcDevice()` now provides the unified media-engine install point described by the parity plan, while `AttachLocalMediaBackend()` and `AttachRemoteMediaBridge()` remain available when a host app wants to wire publish and playback separately. `AttachLocalMediaBackend()` lets a Unity WebRTC or native-plugin implementation own the real mic, camera, and screen-share producer flow while reusing the runtime socket transport through provided emit delegates plus typed backend helpers for `createWebRtcTransport`, `transport-connect`, and `transport-produce`. `AttachRemoteMediaBridge()` lets a Unity WebRTC or native-plugin implementation own the receive-transport and playback binding flow while reusing the runtime socket transport through typed producer discovery events plus typed transport helpers for `createWebRtcTransport`, `transport-recv-connect`, `consume`, and `consumer-resume`. The standalone `com.mediasfu.mediasoup-client-unity` package provides `MediaSfuTransportBackedLocalMediaBackend`, `MediaSfuTransportBackedRemoteMediaBridge`, `MediaSfuTransportBackedWebRtcDevice`, and `MediaSfuNativePluginWebRtcEngine` for adapter-backed integration. `MediaSfuWebRtcEngineLocalMediaAdapter`, `MediaSfuWebRtcEngineRemoteMediaAdapter`, and `MediaSfuWebRtcEngineDevice` package any `IMediaSfuWebRtcEngine` behind the same unified `IMediaSfuWebRtcDevice` install point. `SetMicrophoneEnabledAsync()`, `SetCameraEnabledAsync()`, and `SetScreenShareEnabledAsync()` now route through the local backend when present, and `RemoteProducerAvailable` plus `RemoteProducerClosed` can now drive the remote bridge automatically when attached. The client also now exposes typed public transport helpers for `CreateMediaWebRtcTransportAsync()`, `ConnectMediaWebRtcTransportAsync()`, `ProduceMediaTrackAsync()`, `ConsumeMediaTrackAsync()`, `ResumeConsumerAsync()`, and `PauseConsumerAsync()` so Unity code can drive producer and consumer control without hand-building Socket.IO payloads. Without an attached engine or media bridge, only the existing socket-only mute/stop paths remain active and actual publish/playback still defer. `StartRecordingAsync()`, `PauseRecordingAsync()`, `ResumeRecordingAsync()`, and `StopRecordingAsync()` now emit `startRecord`, `pauseRecord`, `resumeRecord`, and `stopRecord` with Socket.IO acknowledgment handling plus immediate `CurrentRoom.Recording` updates, `LeaveRoomAsync` now emits `disconnectUser` before clearing the local room state when a live session is active and returns `Success` once that local socket/session state is torn down, inbound `disconnectUserSelf` and `meetingEnded` now clear the room and raise an error callback for forced exits, inbound `ban` now maps onto the existing participant-removal event path, inbound `updatedCoHost` now maps onto participant role changes, inbound `allWaitingRoomMembers` now hydrates `CurrentRoom.WaitingRoomParticipants`, inbound `userWaiting` now increments `CurrentRoom.PendingModerationCount` and stores `CurrentRoom.LastWaitingParticipantName`, inbound `pollUpdated` now hydrates `CurrentRoom.Polls`, `CurrentRoom.ActivePoll`, `CurrentRoom.PollModalVisible`, and `CurrentRoom.LastPollStatus`, inbound `breakoutRoomUpdated` now hydrates `CurrentRoom.Breakout` and, for host payloads, refreshes `CurrentRoom.Participants`, inbound `screenProducerId` now hydrates `CurrentRoom.ScreenProducerId`, `CurrentRoom.ShareScreenStarted`, `CurrentRoom.DeferScreenReceived`, and the participant screen-track ids needed to resolve deferred screen receive state, inbound `updateConsumingDomains` now hydrates `CurrentRoom.ConsumingDomains`, inbound `whiteboardAction` now hydrates incremental `CurrentRoom.Whiteboard` state, inbound `whiteboardUpdated` now hydrates `CurrentRoom.Whiteboard` users, shapes, started/ended flags, and can-start state, inbound `hostRequestResponse` now removes handled requests and updates `CurrentRoom.LocalRequests`, inbound `participantRequested`, `updateMediaSettings`, `controlMediaHost`, `meetingTimeRemaining`, and `meetingStillThere` now hydrate room moderation and lifecycle state through `RoomChanged`, inbound `roomRecordParams`, `RecordingNotice`, `timeLeftRecording`, and `stoppedRecording` now hydrate `CurrentRoom.RecordingParameters` and `CurrentRoom.Recording`, and inbound `startRecords` plus `reInitiateRecording` now relay `startRecordIng` on the live socket transport. Host-driven chat restriction is enforced by `SendChatMessageAsync`, host-driven audio, video, or screen restrictions now block the corresponding re-enable calls, `RespondToRoomRequestAsync()` now emits `updateUserofRequestStatus`, `RespondToWaitingParticipantAsync()` now emits `allowUserIn`, and `ConfirmPresence()` now clears or suppresses the shared confirm-here prompt locally. Native plugin hosts should implement the exported bridge symbols documented in `Documentation~/NATIVE_PLUGIN_CONTRACT.md`. The repo still includes the shared bridge scaffold in `../unity-native-bridge/` as the stable ABI surface, while packaged native plugin artifacts ship from `com.mediasfu.mediasoup-client-unity`.

## Next Steps

1. Continue treating runtime validation as the first gate: keep the checked-in batchmode and headed validation paths current, and extend them until room lifecycle, socket lifecycle, and media lifecycle proof is routine instead of ad hoc.
2. Extend real native bridge coverage across the remaining practical targets behind the same `MediaSfuUnity*` ABI, or add one optional Unity WebRTC-backed `IMediaSfuWebRtcEngine` implementation, so the package has one clearly supported publish-and-playback path end to end.
3. If a host prefers split plumbing over the single-engine path, provide backend-specific `IMediaSfuTransportBackedLocalMediaAdapter` and `IMediaSfuTransportBackedRemoteMediaAdapter` implementations for that backend so integrators can stay within the same contract shape without custom socket plumbing.
4. Keep the starter sample scene and bootstrap `MonoBehaviour` intentionally thin and integration-oriented: useful for setup, inspection, and manual validation, but not yet a full moderation, whiteboard, or meeting-app UI surface.
