# MediaSFU Unity Media Runtime Checklist

Use this checklist after the batchmode live validator passes. For the checked-in headless runtime media automation, run `bash ./scripts/run_unity_runtime_media_validation.sh`. For repeated headless regression coverage on the same path, run `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3`. The checked-in headless gate now covers bidirectional microphone and camera flows, post-media chat, approval-driven audio, moderated video, participant screen-share approval, host screen-share, recording start/pause/resume/stop while media is live, co-host assignment, poll create/vote/end, participant removal, and a separate host end-meeting forced-exit room; this checklist remains the manual companion for operator-visible scene state and any extra runtime slices you still want to inspect outside batchmode.

## Scope

This checklist is focused on:

- Unity host and joiner room setup with real media backends attached
- microphone produce and remote consume
- camera produce and remote consume
- host screen-share start, observer consume, and stop
- recording start, pause, resume, and stop while media is already active
- co-host assignment, poll create/vote/end, participant removal, and host end-meeting forced exit
- mute and stop/start round trips
- cross-client verification of participant and room state

This checklist does not replace the batchmode validator. Run `Documentation~/LIVE_VALIDATION.md` first so room create/join/connect/chat regressions are already ruled out.

If you only need to prove the currently installed Unity native bridge and its capability probe before attempting cloud media flows, run `bash ./scripts/run_unity_runtime_backend_probe.sh` first. That backend-only probe does not require cloud credentials.

If you want the headless batchmode media gate before opening the scene manually, run `bash ./scripts/run_unity_runtime_media_validation.sh`. That script drives `MediaSfuRuntimeMediaValidation.Run` directly and includes post-media chat, approval-driven moderation, host screen-share, recording start/pause/resume/stop, co-host assignment, poll create/vote/end, participant removal, and a separate end-meeting slice after the bidirectional microphone and camera checks. The validation environment currently requires a 15-second wait before pause and another 15-second wait before resume, and the checked-in validator already includes both waits. If you want the same gate repeated several times before manual scene work, run `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3`.

## Recommended Test Setups

Use at least one of these pairings:

- Unity Editor on macOS + iOS sample app on simulator or device
- Unity Editor on macOS + Unity standalone player on a second machine
- Unity Editor on macOS + second Unity Editor or player session if your backend and device routing allow it

## Preconditions

- `bash ./scripts/run_unity_live_validation.sh` already passes from the repository root
- when you want the headless media gate, `bash ./scripts/run_unity_runtime_media_validation.sh` is the correct batchmode runner for `MediaSfuRuntimeMediaValidation.Run`
- when you want repeatable headless regression coverage on that same path, `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3` is the checked-in rerun wrapper
- native plugins are packaged with `bash ./scripts/build_unity_native_bridge_package.sh` and, on macOS, that packaging run now executes the host bridge smoke test before copying the editor plugin
- a real Unity scene uses a `MediaSfuRuntimeValidation.unity` scene or an equivalent scene with `MediaSfuRuntimeValidationFlow` or `MediaSfuBasicRoomFlow`
- the scene attaches either `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)` or another working `IMediaSfuWebRtcDevice`
- cloud credentials and base URL are configured in the scene
- microphone and camera permissions are granted on every participating client
- keep the Unity Inspector visible for `statusText`, `backendInfoText`, and `roomSnapshotText`

Before treating the native-plugin path as a real media backend on Unity Editor or macOS, confirm that `TryCreateWebRtcDevice(...)` succeeds. On the current checked-in macOS/editor validation path, `bash ./scripts/run_unity_runtime_backend_probe.sh` should report a non-placeholder backend first; if you still see the placeholder-backed attach failure, stop and repackage or reinstall the native bridge before room or media setup begins.

## Scene Preparation

- [ ] From the repository root, run `bash ./scripts/open_unity_runtime_validation.sh` to open the checked-in validation project and scene directly.
- [ ] Set `connectionMode` to `Cloud` and `connectMediaSfu` to `true`.
- [ ] Set `baseUrl` to your intended cloud host, or leave it empty to use the production default (`https://mediasfu.com`).
- [ ] Set `apiUserName` and `apiKey`.
- [ ] Set a short `displayName` that is easy to identify in snapshots.
- [ ] Leave `attachNativePluginEngineOnStart` enabled or run `Attach Native Plugin Engine` from the component context menu.
- [ ] Confirm Unity shows `Native plugin WebRTC engine attached.` from the `TryCreateWebRtcDevice(...)` helper or the equivalent success status from your custom media bridge.
- [ ] If you are validating in Unity Editor on macOS, confirm `TryCreateWebRtcDevice(...)` succeeds and that `bash ./scripts/run_unity_runtime_backend_probe.sh` reports a non-placeholder backend. If either check still reports the placeholder-backed attach failure, stop and fix packaging before continuing.
- [ ] Keep the Inspector visible for `statusText`, `backendInfoText`, `roomSnapshotText`, and `participantSnapshotText`.
- [ ] If `attachNativePluginEngineOnStart` is disabled or you want to inspect the bridge first, run `Probe Native Plugin Backend` from the component context menu and confirm `backendInfoText` matches the expected installed backend and capability flags.

## Room Bring-Up

- [ ] Host client creates a room and confirms `CreateRoomAsync` succeeds.
- [ ] Host client runs `ConnectMediaAsync` and confirms success.
- [ ] Joiner client joins the same room and confirms `JoinRoomAsync` succeeds.
- [ ] Joiner client runs `ConnectMediaAsync` and confirms success.
- [ ] Both clients show both participants in `participantSnapshotText`.
- [ ] Both clients show the same active room name in `roomSnapshotText`.

## Host Microphone Produce -> Remote Consume

- [ ] On the Unity host, run `Start Microphone` from the component context menu or call `SetMicrophoneEnabledAsync(true)`.
- [ ] Confirm the Unity host reports success rather than deferred behavior.
- [ ] Confirm the remote client hears host audio.
- [ ] Confirm remote participant state reflects host audio as on.
- [ ] Mute again from Unity and confirm remote audio stops.
- [ ] Unmute one more time to confirm the round trip is stable.

## Host Camera Produce -> Remote Consume

- [ ] On the Unity host, run `Start Camera` from the component context menu or call `SetCameraEnabledAsync(true)`.
- [ ] Confirm the Unity host reports success rather than deferred behavior.
- [ ] Confirm the remote client sees host video.
- [ ] Confirm remote participant state reflects host video as on.
- [ ] Stop the camera from Unity and confirm the remote video disappears.
- [ ] Start the camera again to confirm restart behavior is stable.

## Reverse Direction: Remote Produce -> Unity Consume

- [ ] On the non-Unity peer, enable microphone and confirm Unity receives audio.
- [ ] On the non-Unity peer, enable camera and confirm Unity receives video.
- [ ] Confirm Unity `participantSnapshotText` updates as remote tracks start and stop.
- [ ] Disable both tracks remotely and confirm Unity reflects the stopped state.

## Unity Joiner Produce -> Host Consume

- [ ] Repeat the microphone flow with Unity as the joiner rather than the host.
- [ ] Repeat the camera flow with Unity as the joiner rather than the host.
- [ ] Confirm host-side consume works in both directions, not only when Unity is the room creator.

## Stability Checks

- [ ] Leave and rejoin the room after successful media start and confirm media can be re-established.
- [ ] Toggle microphone rapidly three times and confirm state stays consistent.
- [ ] Toggle camera rapidly three times and confirm state stays consistent.
- [ ] Send one chat message after media is active to confirm media setup did not break signaling.

## Pass Criteria

- [ ] Unity can both produce and consume microphone audio in a live room.
- [ ] Unity can both produce and consume camera video in a live room.
- [ ] Mute and camera stop/start round trips succeed without stale room state.
- [ ] Participant snapshots stay aligned with actual media behavior.
- [ ] No operation falls back to `Deferred` for the tested media paths.

## Failure Capture

If a step fails, capture all of these before changing state:

- room name
- Unity `statusText`
- Unity `roomSnapshotText`
- which side was host and which side was joiner
- whether Unity used `MediaSfuNativePluginWebRtcEngine` or another bridge
- Unity Console output
- peer-side observable symptom, such as no audio, black video, or stale participant flags

## Follow-On Checks

Once microphone and camera pass, extend the same pattern to:

- screen share produce-consume
- recording start, pause, resume, and stop while media is active
- waiting-room and moderation flows while tracks are already live
