# MediaSFU Unity Live Validation

This package includes a reusable batchmode room/socket runner at `scripts/run_unity_live_validation.sh`, a reusable batchmode runtime media runner at `scripts/run_unity_runtime_media_validation.sh`, a reusable batchmode runtime media soak runner at `scripts/run_unity_runtime_media_soak.sh`, and a direct editor launcher at `scripts/open_unity_runtime_validation.sh`. Point each script at your validation Unity project using `MEDIASFU_UNITY_PROJECT_PATH` or the `--project-path` flag.

Use this flow to prove the current cloud room lifecycle in batchmode before moving on to real media validation.

For the current Unity delivery order and support exit criteria, use `Documentation~/DELIVERY_CHECKLIST.md` alongside this validation guide.

Batchmode validation only proves room and socket lifecycle. It does not prove that the installed native bridge is a real media backend for Unity Editor or macOS.

The validation project must include a `MediaSfuRuntimeValidation.unity` scene with a scene-local `MediaSfuRuntimeValidationFlow` component for the manual microphone and camera gate. That flow exposes a `backendInfoText` inspector field and a `Probe Native Plugin Backend` context-menu action so operators can inspect the installed bridge before attempting media actions.

For a headless backend-only check that does not need cloud credentials, use `bash ./scripts/run_unity_runtime_backend_probe.sh`. It runs `MediaSfuRuntimeMediaValidation.RunBackendProbe` in batchmode and will clone the validation project automatically if the checked-in project is already open in another Unity editor.

For the real headless runtime media gate after this room/socket validator passes, use `bash ./scripts/run_unity_runtime_media_validation.sh`. That wrapper runs `MediaSfuRuntimeMediaValidation.Run` in batchmode, forwards the configured cloud or local-link environment, and uses the same temporary project clone behavior when the checked-in project is already open in Unity. The current checked-in gate proves bidirectional microphone and camera flows, post-media chat, approval-driven audio, moderated video, participant screen-share approval, host screen-share start/consume/stop, recording start/pause/resume/stop while media is already live, co-host assignment, poll create/vote/end, participant removal, and a separate host end-meeting forced-exit room. The validation backend currently enforces a 15-second wait before pause and another 15-second wait before resume, and the checked-in validator now respects both windows.

For repeated regression coverage on the same runtime media path, use `bash ./scripts/run_unity_runtime_media_soak.sh --runs 3`. That wrapper reruns the checked-in runtime media validator, preserves per-run Unity and wrapper logs in one directory, and fails if any run misses the remote-frame or final pass markers.

You do not need Unity Hub to list the project first. The launcher script opens the checked-in project and scene directly by path.

## What It Proves

The live validator covers:

- package resolution from the checked-in Unity validation project
- compile-surface loading for the public Unity SDK types
- iOS plugin importer validation for `MediaSFUNativeBridge.xcframework` and `WebRTC.xcframework`
- production or custom-cloud room create
- host socket connect and `joinRoom` validation
- joiner REST join plus socket connect
- participant discovery on both clients
- chat send and receive across the live room

## Local Run

From the repository root:

```sh
export MEDIASFU_UNITY_API_USERNAME="your-api-username"
export MEDIASFU_UNITY_API_KEY="your-64-char-api-key"
bash ./scripts/run_unity_live_validation.sh
```

Defaults:

- Unity app: `/Applications/Unity/Hub/Editor/2022.3.62f3/Unity.app`
- validation project: configured via `MEDIASFU_UNITY_PROJECT_PATH` or `--project-path`
- base URL: `https://mediasfu.com`
- timeout: `45` seconds

You can override them:

```sh
bash ./scripts/run_unity_live_validation.sh \
  --unity-app "/Applications/Unity/Hub/Editor/2022.3.62f3/Unity.app" \
  --project-path "path/to/your/UnityValidationProject" \
  --base-url "https://mediasfu.com" \
  --timeout-seconds 60
```

To inspect the resolved command without running Unity:

```sh
bash ./scripts/run_unity_live_validation.sh --dry-run
```

For the actual runtime media path, keep the same credentials exported and run:

```sh
bash ./scripts/run_unity_runtime_media_validation.sh
```

For a small repeatability gate on the same path, run:

```sh
bash ./scripts/run_unity_runtime_media_soak.sh --runs 3
```

## Prerequisites

- Unity `2022.3.62f3` installed and activated on the machine
- Unity iOS Build Support installed for that editor
- `com.mediasfu.mediasoup-client-unity` installed with `MediaSFUNativeBridge.xcframework`
- `com.mediasfu.mediasoup-client-unity` installed with `WebRTC.xcframework`
- `MEDIASFU_UNITY_API_USERNAME` and `MEDIASFU_UNITY_API_KEY` exported in the shell

## GitHub Actions Target

The repository also includes manual workflows at `.github/workflows/unity-live-validation.yml` and `.github/workflows/unity-runtime-media-soak.yml`.

It is intentionally configured for a licensed self-hosted macOS runner because the validator requires:

- a real Unity installation at a known path
- an activated Unity license
- iOS Build Support in that editor

Before Unity batchmode starts, that workflow also runs the focused Android Unity backend unit test `com.mediasfu.sdk.unity.AndroidUnityWebRtcOperationHostTest` so operation-host regressions fail before cloud validation begins.

Use the runtime media soak workflow when you want repeated runtime produce-consume coverage on the licensed self-hosted macOS runner without manually scripting the reruns.

Required secrets:

- `MEDIASFU_UNITY_API_USERNAME`
- `MEDIASFU_UNITY_API_KEY`

Recommended runner labels:

- `self-hosted`
- `macOS`

## Expected Success Signal

Successful runs end with both of these log lines:

- `Unity live validation passed with room ...`
- `MediaSFU Unity package validation passed.`

Successful soak runs print one `RUN N result=pass ...` summary line per pass and finish with `SUMMARY pass=... fail=0 logs_dir=...`.

If the batchmode run passes, use `Documentation~/MEDIA_RUNTIME_CHECKLIST.md` for the next stage: real microphone and camera produce-consume validation outside batchmode.

For the interactive editor path, run:

```sh
bash ./scripts/open_unity_runtime_validation.sh
```

If you need to regenerate the runtime scene inside the validation project, run Unity with `-executeMethod MediaSfuRuntimeValidationSceneBuilder.CreateScene` or use the editor menu item `MediaSFU/Create Runtime Validation Scene`.
