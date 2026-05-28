# MediaSFU Unity Delivery Checklist

This checklist keeps the Unity package SDK-first.

Use it to decide what should be considered supported now, what must be proven before broadening claims, and what should stay deferred until the runtime path is stable.

## Current Validation Status

The current verified local Unity path is:

1. room lifecycle and socket lifecycle validated by `Documentation~/LIVE_VALIDATION.md`
2. native-bridge installability and backend probing validated by `bash ./scripts/run_unity_runtime_backend_probe.sh` and `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)`
3. real microphone and camera produce-consume behavior, post-media chat, approval-driven moderation, host and participant screen-share paths, recording start/pause/resume/stop, co-host assignment, poll create/vote/end, participant removal, and host end-meeting forced exit validated locally by `bash ./scripts/run_unity_runtime_media_validation.sh` and repeatably exercised by `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3`

The real-media backend gate is still environment-specific, but the checked-in macOS/editor validation path is no longer theoretical.

- The current local backend probe resolves a non-placeholder macOS/editor backend through `TryCreateWebRtcDevice(...)`.
- The current local runtime media validator and soak runner have already passed against a credentialed validation environment on that same macOS/editor path.
- Additional Unity targets remain optional backend-expansion work, not blockers for the currently validated local path.

Anything broader than that should be described as planned or partial, not fully supported.

## Priority Order

1. validation gate
2. one supported media backend gate
3. integration reference gate
4. optional backend expansion
5. rich UI surfaces last

## 1. Validation Gate

Goal: keep room, socket, and baseline package behavior provable from a fresh clone.

Exit criteria:

- `bash ./scripts/run_unity_live_validation.sh` passes from the repository root with valid cloud credentials
- the checked-in validation project opens directly through `bash ./scripts/open_unity_runtime_validation.sh`
- the validation scene and build settings stay checked in and reusable
- no tested create, join, connect, participant, or chat step falls back to `Deferred`
- failure capture steps remain documented in `Documentation~/LIVE_VALIDATION.md`

## 2. One Supported Media Backend Gate

Goal: make one real media path clearly installable, testable, and supportable end to end.

Current target:

- `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)` backed by a real native backend, not only a loadable bridge scaffold

Exit criteria:

- `bash ./scripts/run_unity_runtime_backend_probe.sh` passes on the intended local environment
- `bash ./scripts/run_unity_runtime_media_validation.sh` passes on the intended cloud host
- `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3 --fail-fast` passes when repeated local parity proof is required
- the native-plugin attach helper returns `Success` in the checked-in validation scene on the intended environment
- Unity host and Unity joiner can both start microphone successfully without deferred behavior
- Unity host and Unity joiner can both start camera successfully without deferred behavior
- Unity can consume remote microphone and camera from at least one second client setup
- chat send and receive still work after the runtime media flows complete
- mute and camera stop-start round trips stay aligned with room state
- failures are captured with room, status, snapshots, backend path, and console output as documented in `Documentation~/MEDIA_RUNTIME_CHECKLIST.md`

Current local status:

- There is no current attach-time blocker on the checked-in macOS/editor path: the backend probe now resolves a real backend and the runtime media validation path is green locally.
- The remaining caution is scope, not a local parity failure: keep support claims tied to the validated package, bridge, and checklist pairings until other Unity targets are exercised the same way.

## 3. Integration Reference Gate

Goal: keep the package easy to integrate without turning it into a meeting app.

Exit criteria:

- starter flows attach the supported backend path without custom exception handling
- README and API docs point to one preferred backend path and one preferred validation flow
- the sample scene remains useful for setup, inspection, and manual validation
- support claims stay aligned with what the validation project actually proves

## 4. Optional Backend Expansion

Goal: add more targets only after the first path is stable.

Exit criteria for each added backend:

- it implements the same public contracts and does not widen the Unity-facing API unnecessarily
- it passes the same room and socket validation gate
- it has an explicit media validation checklist result on at least one practical pairing
- packaging and install steps are documented beside the native-plugin contract

Do not treat multiple half-complete backends as higher priority than one proven backend.

## 5. Rich UI Surfaces Last

Goal: keep Unity aligned with the SDK signature of the other stacks without prematurely shipping a full meeting UI.

Do later:

- moderation dashboards
- whiteboard-oriented in-scene controls
- breakout-room-focused scene UI
- app-like prefabs intended to mirror Kotlin, Flutter, or React layouts directly

Do now:

- transport, state, and validation proof
- thin integration-oriented sample surfaces
- stable contracts and predictable attach paths

## Deferred Until The Above Gates Pass

- broad claims of feature parity with Kotlin, Flutter, or React UI layers
- multiple equal-status Unity media backends
- full meeting-app prefabs or polished production UI kits
- deep whiteboard, breakout, or recording UX work beyond validation and state exposure
