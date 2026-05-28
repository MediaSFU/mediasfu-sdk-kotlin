# MediaSFU Unity API Contract

This document freezes the first public C# surface for the Unity SDK before the runtime internals are implemented.

## Purpose

The Unity package should start with a stable developer-facing API, then fill in REST, socket, and media layers behind it. That keeps early experimentation from leaking transport details into the public surface.

## Initial Namespace

`MediaSFU.Unity`

## Main Entry Point

`MediaSfuClient`

### Constructor

```csharp
var client = new MediaSfuClient(new MediaSfuClientOptions { ... });
```

## Primary Methods

| Method | Purpose | Planned backend alignment |
| --- | --- | --- |
| `CreateRoomAsync(MediaSfuCreateRoomRequest)` | REST create room | `CreateRoomOnMediaSfu.kt` |
| `JoinRoomAsync(MediaSfuJoinRoomRequest)` | REST join room | `JoinRoomOnMediaSfu.kt` |
| `ConnectMediaAsync()` | Build the socket plan, perform an Engine.IO polling-open probe, open the websocket transport through namespace connect, validate a standard room with `joinRoom`, and start inbound event routing | Kotlin `PreJoinPage` handoff plus producer/consumer flows |
| `BuildSocketConnectionPlan()` | Resolve the future Socket.IO base URL, namespace, room name, and timeouts after REST create/join | Unity Phase 1 signaling scaffold |
| `AttachWebRtcDevice(IMediaSfuWebRtcDevice)` | Supply a unified media engine that owns both publish and playback | Kotlin `WebRtcDevice`-style install point |
| `AttachLocalMediaBackend(IMediaSfuLocalMediaBackend)` | Supply the local media implementation used by mic, camera, and screen-share control | Unity WebRTC or native-plugin bridge |
| `AttachRemoteMediaBridge(IMediaSfuRemoteMediaBridge)` | Supply the remote media implementation used by receive transport and playback binding | Unity WebRTC or native-plugin bridge |
| `LeaveRoomAsync()` | Leave room and cleanup | Shared socket/media teardown flows |
| `EndMeetingAsync()` | End the room as the host and close the local session | Shared `disconnectUser` host-exit flow |
| `SetMicrophoneEnabledAsync(bool)` | Mute/unmute local mic | Producer control layer |
| `SetCameraEnabledAsync(bool)` | Enable/disable local camera | Producer control layer |
| `SetScreenShareEnabledAsync(bool)` | Start/stop screen share | Unity WebRTC or native plugin lane |
| `RequestMediaPermissionAsync(MediaSfuTrackKind)` | Send an approval request for audio, video, or screen-share when the room requires approval or the host has restricted the lane | Shared `participantRequest` flow |
| `ControlParticipantMediaAsync(MediaSfuParticipant, MediaSfuHostControlType)` | Apply a host-side control action to a participant media lane | Shared `controlMedia` flow |
| `RemoveParticipantAsync(MediaSfuParticipant)` | Remove a participant from the live room | Shared `disconnectUserInitiate` flow |
| `UpdateCoHostAsync(string, IReadOnlyList<MediaSfuCoHostResponsibility>)` | Assign or revoke the current co-host and responsibilities | Shared `updateCoHost` flow |
| `CreateMediaWebRtcTransportAsync(MediaSfuWebRtcTransportRequest)` | Create a send or receive mediasoup transport through the live socket session | Shared producer/consumer transport setup |
| `ConnectMediaWebRtcTransportAsync(MediaSfuTransportConnectRequest)` | Complete DTLS connect for a send or receive transport | Shared producer/consumer transport setup |
| `ProduceMediaTrackAsync(MediaSfuProduceRequest)` | Produce a local track over the live socket session | Shared producer setup |
| `ConsumeMediaTrackAsync(MediaSfuConsumeRequest)` | Request a remote consumer over the live socket session | Shared consumer setup |
| `ResumeConsumerAsync(string)` | Resume a previously created remote consumer | Shared consumer control |
| `PauseConsumerAsync(string)` | Pause a previously created remote consumer | Shared consumer control |
| `SendChatMessageAsync(string)` | Send a room chat message | Shared socket event contract |
| `CreatePollAsync(MediaSfuPollCreateRequest)` | Create and open a room poll | Shared `createPoll` flow |
| `VotePollAsync(MediaSfuPollVoteRequest)` | Submit a participant vote on an active poll | Shared `votePoll` flow |
| `EndPollAsync(string)` | End an active poll | Shared `endPoll` flow |
| `RespondToRoomRequestAsync(MediaSfuRoomRequest, bool)` | Accept or reject a pending audio, video, screenshare, or chat request | Shared `updateUserofRequestStatus` flow |
| `RespondToWaitingParticipantAsync(MediaSfuWaitingRoomParticipant, bool)` | Allow or deny a waiting-room participant | Shared `allowUserIn` flow |
| `StartOrUpdateBreakoutRoomsAsync(MediaSfuBreakoutRoomsRequest)` | Start or update breakout-room assignments | Shared `startBreakout` / `updateBreakout` flow |
| `StopBreakoutRoomsAsync()` | Stop the active breakout-room session | Shared `stopBreakout` flow |
| `StartOrUpdateWhiteboardAsync(MediaSfuWhiteboardSessionRequest)` | Start or update whiteboard participants | Shared `startWhiteboard` / `updateWhiteboard` flow |
| `StopWhiteboardAsync()` | Stop the active whiteboard session | Shared `stopWhiteboard` flow |
| `SendWhiteboardActionAsync(string, MediaSfuWhiteboardShape)` | Emit an incremental whiteboard action such as draw, shape, clear, undo, redo, or delete | Shared `updateBoardAction` flow |
| `UpdateWhiteboardShapesAsync(IReadOnlyList<MediaSfuWhiteboardShape>)` | Replace the whiteboard shape snapshot | Shared `updateBoardAction` with `action=shapes` |
| `ClearWhiteboardAsync()` | Clear active whiteboard shapes and undo/redo state | Shared `updateBoardAction` with `action=clear` |
| `ConfirmPresence(bool)` | Clear the local "still there" prompt and optionally suppress future prompts for the current client instance | Shared confirm-here modal flow |

## Events

| Event | Purpose |
| --- | --- |
| `ConnectionStateChanged` | State machine transitions for create, join, reconnect, and failure |
| `RoomChanged` | Active room/session payload changed |
| `ParticipantJoined` | Remote or local participant entered room state |
| `ParticipantLeft` | Participant left room state |
| `TrackAdded` | Remote or local audio/video/screen/whiteboard track became available |
| `TrackRemoved` | Track removed or stopped |
| `RemoteProducerAvailable` | Typed remote producer discovery event for receive transport / consume orchestration |
| `RemoteProducerClosed` | Typed remote producer teardown event keyed by remote producer id |
| `SocketEventReceived` | Raw inbound Socket.IO event surface for unsupported or future room events |
| `MessageReceived` | Parsed room chat message received from the socket layer |
| `ErrorOccurred` | Non-transport-specific SDK error surface |

## Create Request Shape

`MediaSfuCreateRoomRequest` mirrors the current Kotlin `CreateMediaSFURoomOptions` contract, including the advanced room fields already added in the shared SDK:

- core fields: `UserName`, `DurationMinutes`, `Capacity`, `ScheduledDateEpochMillis`, `SecureCode`, `EventType`
- optional host/control fields: `RoomName`, `AdminPasscode`, `IsLevel`
- nested config: `MeetingRoomParameters`, `RecordingParameters`
- safety/buffering fields: `RecordOnly`, `SafeRoom`, `AutoStartSafeRoom`, `SafeRoomAction`, `DataBuffer`, `BufferType`
- advanced room fields: `SupportSip`, `DirectionSip`, `PreferPcma`, `SupportTranslation`, `TranslationConfigNickName`, `SupportFlexRoom`, `SupportMaxRoom`

## Join Request Shape

`MediaSfuJoinRoomRequest` mirrors the current Kotlin `JoinMediaSFURoomOptions` contract:

- `MeetingId`
- `UserName`
- `AdminPasscode`
- `IsLevel`

## Client Configuration

`MediaSfuClientOptions` currently freezes these decisions:

- connection mode: `Cloud`, `CommunityEdition`, or `Hybrid`
- credentials for MediaSFU cloud access
- `LocalLink` for community or hybrid flows
- `BaseUrl` for non-production, private backends, or explicit cloud endpoint overrides; empty uses the production `https://mediasfu.com/v1/rooms` endpoint
- `ConnectMediaSfu` flag for cloud-backed room creation or join
- reconnect and ack timeout defaults that can later map to the chosen Socket.IO client

Local capture and producer wiring are intentionally not part of `MediaSfuClientOptions`. They now attach separately through `AttachLocalMediaBackend(IMediaSfuLocalMediaBackend)` so the runtime facade can stay transport-focused while different Unity media backends plug in behind the same public client. The package now also includes `MediaSfuTransportBackedLocalMediaBackend`, a reusable concrete backend that consumes the runtime transport helpers and delegates only the package-specific capture/producer primitives to `IMediaSfuTransportBackedLocalMediaAdapter`, plus `MediaSfuDelegateTransportBackedLocalMediaAdapter` for lighter-weight delegate-based integration. `IMediaSfuWebRtcEngine`, `MediaSfuDelegateWebRtcEngine`, and `MediaSfuWebRtcEngineDevice` now add a typed one-engine install path on top of those same transport-backed primitives.

## Socket Planning Surface

`MediaSfuSocketConnectionPlan` is the current Phase 1 signaling scaffold.

It captures:

- resolved socket base URL
- namespace
- room name
- connect timeout
- ack timeout
- whether the resolved base URL uses secure transport

`MediaSfuClient` now exposes:

- `BuildSocketConnectionPlan()` for explicit plan generation after a successful create/join
- `LastSocketConnectionPlan` so callers can inspect the most recent plan produced by `ConnectMediaAsync()`

`MediaSfuSocketHandshake` captures the live Engine.IO polling-open result.

It includes:

- polling transport URL
- Engine.IO session id
- advertised upgrades
- ping interval
- ping timeout
- max payload

`MediaSfuClient` also exposes `LastSocketHandshake` so callers can inspect the last successful polling probe.

## Room Validation Surface

`MediaSfuRoomValidation` captures the latest standard-room `joinRoom` acknowledgment when that validation path is available.

It includes:

- whether RTP capabilities were returned
- the raw RTP capabilities JSON payload
- receive IPs
- secure code
- host and safe-room flags
- banned, suspended, and no-admin status flags
- the raw acknowledgment payload

`MediaSfuClient` exposes `LastRoomValidation` so callers can inspect the latest successful `joinRoom` validation.

## Media Control Signal Surface

The Unity facade now exposes two layers for local media control:

- `AttachWebRtcDevice(IMediaSfuWebRtcDevice)` now provides the single media-engine install point described by the Unity plan, while still allowing hosts to attach the publish and playback halves separately when needed.
- `AttachLocalMediaBackend(IMediaSfuLocalMediaBackend)` lets a Unity WebRTC or native-plugin implementation handle real capture and producer setup while reusing the active room, validation snapshot, raw socket emit delegates, and typed transport helpers for `createWebRtcTransport`, `transport-connect`, and `transport-produce` from the runtime client.
- The standalone `com.mediasfu.mediasoup-client-unity` package provides `MediaSfuTransportBackedLocalMediaBackend` as the default Unity-side producer state machine for send transport creation, transport connect, produce, audio pause/resume, and video/screen close behavior. A package-specific `IMediaSfuTransportBackedLocalMediaAdapter` or `IMediaSfuWebRtcEngine` implementation still needs to provide the actual capture, DTLS parameter extraction, and producer binding primitives, but `MediaSfuDelegateTransportBackedLocalMediaAdapter`, `MediaSfuDelegateWebRtcEngine`, and `MediaSfuWebRtcEngineLocalMediaAdapter` can now supply those primitives from delegates or a single engine implementation instead of a dedicated adapter class.
- `AttachRemoteMediaBridge(IMediaSfuRemoteMediaBridge)` lets a Unity WebRTC or native-plugin implementation handle receive transport and playback binding while reusing typed producer-discovery events plus typed transport helpers for `createWebRtcTransport`, `transport-recv-connect`, `consume`, and `consumer-resume` from the runtime client.
- The standalone client package provides `MediaSfuTransportBackedRemoteMediaBridge` as the default Unity-side consumer state machine for receive transport creation, recv connect, consume, and consumer resume behavior. A package-specific `IMediaSfuTransportBackedRemoteMediaAdapter` or `IMediaSfuWebRtcEngine` implementation still needs to provide the actual DTLS extraction and playback binding primitives, but `MediaSfuDelegateTransportBackedRemoteMediaAdapter`, `MediaSfuDelegateWebRtcEngine`, and `MediaSfuWebRtcEngineRemoteMediaAdapter` can now supply those primitives from delegates or a single engine implementation instead of a dedicated adapter class.
- Transport-backed adapters and `IMediaSfuWebRtcEngine` implementations must now also support loading router RTP capabilities before transport creation. That matches the mediasoup device lifecycle already used by Android and iOS and removes the need for native backends to rely on out-of-band device preload state.
- Transport-backed adapters and `IMediaSfuWebRtcEngine` implementations must now also support explicit send and receive connect completion after Unity emits `transport-connect` or `transport-recv-connect`. That gives Android and iOS mediasoup-backed backends a place to release the pending native `onConnect` callback only after the Unity signaling step has actually run.
- `MediaSfuTransportBackedWebRtcDevice` is supplied by the standalone client package and bundles `MediaSfuTransportBackedLocalMediaBackend` and `MediaSfuTransportBackedRemoteMediaBridge` behind one `IMediaSfuWebRtcDevice` surface for hosts that want a single installable media engine.
- `MediaSfuWebRtcEngineDevice` is supplied by the standalone client package and wraps one `IMediaSfuWebRtcEngine` implementation into a full `IMediaSfuWebRtcDevice`.
- `MediaSfuNativePluginWebRtcEngine` is supplied by the standalone client package. It keeps the MediaSFU Unity SDK free of hard WebRTC references and talks to a native bridge binary over the exported `MediaSfuUnity*` C symbols documented in `NATIVE_PLUGIN_CONTRACT.md`.
- `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)` now provides the non-throwing managed install path for the current native-plugin backend target. It probes the native bridge with `describeBackend`, rejects placeholder backends up front, and only returns `Success` when the bridge is both present and non-placeholder.
- `MediaSfuNativePluginWebRtcEngine.TryGetBackendInfo(...)` now exposes the same backend probe directly for tooling, diagnostics, and validation flows that need to inspect the bridge without attaching it as a media device. Installed backends can now also report optional platform and capability flags such as audio, video, screen-share, whiteboard, and receive support.
- `CreateMediaWebRtcTransportAsync()`, `ConnectMediaWebRtcTransportAsync()`, `ConsumeMediaTrackAsync()`, `ResumeConsumerAsync()`, and `PauseConsumerAsync()` now expose the receive-side transport and consumer signaling path directly so Unity playback code can reuse the same typed socket helpers instead of building raw consume payloads.
- Without an attached backend, the fallback control paths are limited to the socket signals that can be emitted safely before full producer setup exists:

- `SetMicrophoneEnabledAsync(false)` emits `pauseProducerMedia` with `mediaTag=audio`
- `SetCameraEnabledAsync(false)` emits `pauseProducerMedia` with `mediaTag=video`
- `SetScreenShareEnabledAsync(false)` emits `closeScreenProducer`, then `pauseProducerMedia` with `mediaTag=screen`

When a backend is attached, `SetMicrophoneEnabledAsync`, `SetCameraEnabledAsync`, and `SetScreenShareEnabledAsync` return the backend result and still update local participant state through the shared room path on success. Before enable, those calls now also honor the current room media settings for audio, video, and screenshare: `allow` enables directly, `approval` fails with an explicit request-needed message unless a one-time accepted request grant is present, and `disallow` fails immediately. Accepted grants from `hostRequestResponse` are consumed after the next successful enable, which matches the shared one-shot approval flow. Without a backend, media-enable calls still return `Deferred` because Unity capture, producer creation, and mediasoup transport setup are not implemented yet.

`RequestMediaPermissionAsync(MediaSfuTrackKind)` now emits the shared `participantRequest` payload for audio, video, and screen-share requests. Unity sends that request when the room setting is `approval` or when a host restriction is active, and it refuses redundant requests while one is already pending or still inside the shared retry cooldown after rejection.

## Socket Event Surface

`MediaSfuClient` now keeps a reusable receive loop alive after the namespace connection succeeds.

Current Unity-facing surfaces are:

- `SocketEventReceived` for raw inbound Socket.IO events, including namespace, event name, optional ack id, and raw JSON payload
- `MessageReceived` for parsed `receiveMessage` packets
- `ParticipantJoined`, `ParticipantLeft`, and `RoomChanged` updates driven by `personJoined`, `allMembers`, `allMembersRest`, `ban`, and `updatedCoHost` role changes
- `RoomChanged` updates now also carry pending requests, co-host responsibilities, room media settings, host-imposed chat and media restrictions, meeting time-remaining state, and the "still there" confirmation flag driven by `participantRequested`, `updateMediaSettings`, `controlMediaHost`, `meetingTimeRemaining`, and `meetingStillThere`
- `RoomChanged` updates now also carry waiting-room participants driven by `allWaitingRoomMembers`, so moderation UIs can act on the shared `allowUserIn` flow without a separate Unity-side cache
- `RoomChanged` now also carries aggregate moderation count and the last waiting-room name notice driven by `userWaiting`, which matches the shared incremental waiting-room signal even when the server has not sent a fresh actionable waiting-room snapshot yet
- `RoomChanged` now also carries poll list, active poll, poll-prompt visibility, and last poll status driven by `pollUpdated`, mirroring the shared poll merge behavior without a separate Unity-side poll cache
- `RoomChanged` now also carries breakout room assignments, breakout lifecycle flags, and host-room assignment driven by `breakoutRoomUpdated`, while host-side participant snapshots from the same payload continue to reuse the existing participant diff path
- `RoomChanged` now also carries screen-producer receive state driven by `screenProducerId`, including deferred screen receive state until a compatible participant snapshot lands
- `RoomChanged` now also carries the latest consuming-domain update driven by `updateConsumingDomains`, preserving receive-domain and alternate-domain instructions on room state until the consume transport layer is implemented
- `RoomChanged` now also carries collaborative whiteboard state driven by `whiteboardAction` and `whiteboardUpdated`, including incremental actions, full shape snapshots, whiteboard users, lifecycle flags, background toggle state, and the last action name
- `RoomChanged` updates now also carry recording parameters plus recording runtime state driven by `roomRecordParams`, `RecordingNotice`, `timeLeftRecording`, and `stoppedRecording`, `startRecords` / `reInitiateRecording` now relay the shared `startRecordIng` socket emit when the server requests recording startup or recovery, and public `StartRecordingAsync()`, `PauseRecordingAsync()`, `ResumeRecordingAsync()`, and `StopRecordingAsync()` now emit the host-side `startRecord`, `pauseRecord`, `resumeRecord`, and `stopRecord` controls with Socket.IO acknowledgment handling
- forced local cleanup plus `ErrorOccurred` and `RoomChanged` updates driven by `disconnectUserSelf` and `meetingEnded`
- `TrackRemoved` updates driven by `producer-media-closed`

`SendChatMessageAsync()` now emits `sendMessage` on the live socket transport for room-wide chat messages, and it refuses to send when the room state indicates the host has restricted local chat.

`RespondToRoomRequestAsync()` now emits `updateUserofRequestStatus`, removes the handled request from `CurrentRoom.PendingRequests` through `RoomChanged`, and seeds the one-shot screen-share classification hint used when a host-approved participant screen-share still arrives over the consume path as a bare `video` producer before a dedicated screen marker is available.

`RespondToWaitingParticipantAsync()` now emits `allowUserIn` and removes the handled participant from `CurrentRoom.WaitingRoomParticipants` through `RoomChanged`.

`RemoveParticipantAsync()` now emits `disconnectUserInitiate`, immediately removes the targeted participant from the local host room snapshot on acknowledgment success, and relies on the shared `disconnectUserSelf` path to clear the removed participant on the remote client.

`EndMeetingAsync()` now emits `disconnectUser` using the shared host-exit payload `{ member, roomName, ban }`, then closes the local socket/session so the host leaves through the same contract that produces inbound `meetingEnded` on remote participants.

`UpdateCoHostAsync()` now emits `updateCoHost`, normalizes the shared `No coHost` revoke sentinel, and updates `CurrentRoom.CoHost` plus `CurrentRoom.CoHostResponsibilities` through the same room-diff path that inbound `updatedCoHost` packets already use.

`CreatePollAsync()`, `VotePollAsync()`, and `EndPollAsync()` now emit `createPoll`, `votePoll`, and `endPoll`, and they reuse the existing `pollUpdated` room merge so `CurrentRoom.Polls`, `CurrentRoom.ActivePoll`, `CurrentRoom.LastPollStatus`, and poll-modal visibility stay aligned with the shared contract.

`StartOrUpdateBreakoutRoomsAsync()` now emits `startBreakout` or `updateBreakout` depending on the current breakout state, `StopBreakoutRoomsAsync()` emits `stopBreakout`, and both update `CurrentRoom.Breakout` on acknowledgment success while preserving the existing inbound `breakoutRoomUpdated` merge. `StartOrUpdateWhiteboardAsync()` now emits `startWhiteboard` or `updateWhiteboard`, `StopWhiteboardAsync()` emits `stopWhiteboard`, and `SendWhiteboardActionAsync()`, `UpdateWhiteboardShapesAsync()`, and `ClearWhiteboardAsync()` emit `updateBoardAction` using the same action/payload shape as the shared, React, and Flutter implementations. The checked-in validation harness now includes these collaboration slices and the code compiles in the Unity project; fresh live credentialed validation proof for this new slice still requires `MEDIASFU_UNITY_API_USERNAME` and `MEDIASFU_UNITY_API_KEY` in the runner environment.

`ConfirmPresence()` now clears the local confirm-here flag on `CurrentRoom`, and it can suppress future `meetingStillThere` prompts for the lifetime of the current Unity client instance.

## Result Contract

All async methods return `MediaSfuOperationResult<T>`.

This has three explicit states:

- `Success`: runtime behavior completed successfully
- `Failure`: input validation or runtime behavior failed
- `Deferred`: the API is present, but the runtime implementation for that path is not wired yet

`CreateRoomAsync` and `JoinRoomAsync` now perform real REST requests and return parsed room data. `ConnectMediaAsync` validates that a room exists, derives a socket connection plan, performs an Engine.IO polling-open probe, opens a websocket transport through the initial Socket.IO namespace connect, runs a standard cloud-style `joinRoom` acknowledgment validation when the room payload supports it, starts a reusable receive loop, exposes the latest plan, handshake, and validation through `LastSocketConnectionPlan`, `LastSocketHandshake`, and `LastRoomValidation`, and returns `Success` once socket-side room validation is live. `AttachWebRtcDevice(IMediaSfuWebRtcDevice)` is the single handoff point for the actual Unity media engine supplied by `com.mediasfu.mediasoup-client-unity`, while `AttachLocalMediaBackend(IMediaSfuLocalMediaBackend)` and `AttachRemoteMediaBridge(IMediaSfuRemoteMediaBridge)` remain available for hosts that want to wire publish and playback separately. `CreateMediaWebRtcTransportAsync()`, `ConnectMediaWebRtcTransportAsync()`, `ConsumeMediaTrackAsync()`, `ResumeConsumerAsync()`, and `PauseConsumerAsync()` expose the receive-side socket contract directly so Unity playback code can create recv transports and consumers without hand-building raw Socket.IO payloads. `RemoteProducerAvailable` and `RemoteProducerClosed` surface typed remote producer discovery for that playback bridge. `LeaveRoomAsync`, recording controls, moderation events, waiting-room events, polls, breakout rooms, whiteboard updates, screen producer routing, host controls, and room lifecycle events update `CurrentRoom` through the shared room-state path.

## Current Runtime Coverage

The current package and standalone client dependency cover these runtime surfaces:

1. Unity Editor on macOS and backend-install flows through the shared standalone `MediaSfuUnity*` ABI when the installed bridge probe succeeds
2. iOS player builds through the client package `MediaSFUNativeBridge.xcframework` plus companion `WebRTC.xcframework`
3. Additional Unity targets such as Android, Windows, or WebGL behind the same ABI only as product requirements require and backend packaging is validated

The current repo-level support claim should remain narrower than the full contract surface: batchmode room lifecycle validation is proven, and real-media support still depends on the native bridge behind `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)`. In the checked-in local macOS/editor validation project, that bridge probe now resolves a non-placeholder backend and the runtime media validator has already passed locally, so use `Documentation~/DELIVERY_CHECKLIST.md` as the exit-criteria source for that proven path before broadening any support claim to other Unity targets or environments.

## Media Strategy

The contract is intentionally agnostic to the final media backend.

The current implementation path is hybrid:

1. C# REST and socket layer in Unity
2. the current native-plugin backend path behind the stable `MediaSfuUnity*` ABI, with local runtime proof on the checked-in macOS/editor install and packaged iOS player binaries under the same contract
3. Unity WebRTC or additional native bridges for the remaining Unity targets where mediasoup, capture, or packaging gaps still remain

## What Phase 0 Does Not Commit To Yet

- exact Socket.IO package choice
- exact mediasoup transport implementation path
- Unity texture and audio device plumbing details
- final prefab set or meeting UI layout

Those are implementation details behind the public contract above.

## Current Delivery State

- Phase 0 contract freeze: complete
- Phase 1 REST create/join: started and wired in `MediaSfuClient`
- Socket.IO room validation plus reusable emit/ack/event routing, participant-ban removal, co-host role updates, waiting-room snapshots, pending-request/media-settings/host-restriction room updates, handled-request removal, and forced room-exit cleanup: started and wired in `MediaSfuClient`
- Local audio/video/screen stop/resume control signaling, host-side restriction enforcement for re-enable attempts, request responses, waiting-room responses, and local confirm-here acknowledgment: started and wired in `MediaSfuClient`
- Media transport orchestration, producer control, and consumer control runtime slices: wired in reusable Unity runtime classes; the checked-in macOS/editor native-plugin path is locally runtime-validated and the packaged iOS native-plugin path ships under the same ABI; additional backend coverage for the remaining Unity targets is still optional expansion work
- Recording event coverage and participant-side request state handling: started and wired in `MediaSfuClient`
- Breakout and whiteboard authoring: public Unity client APIs are wired for start/update/stop and board actions, the checked-in runtime validation harness includes the new slices, and Unity import/compile validation is green; live credentialed validation proof is pending credentials in the validation environment
- Sample-scene completeness: partial; the bootstrap now surfaces room recording, aggregate moderation count, waiting-room notice, active poll state, breakout state, screen-producer receive state, consume-domain updates, whiteboard state, local request-response state, and selected moderation snapshots through scene-usable sample events plus inspector-visible sample state, exposes count and visibility flags for simple UI binding, and includes sample-facing wrappers for breakout and whiteboard authoring, but it is still not a full Unity UI
