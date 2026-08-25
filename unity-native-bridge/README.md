# MediaSFU Unity Native Bridge Scaffold

This folder contains a compile-ready placeholder native bridge that matches the exported `MediaSfuUnity*` ABI used by `MediaSfuNativePluginWebRtcEngine` in the Unity package.

## What it is

- a native bridge scaffold with the exact exported symbol names expected by the Unity runtime
- a placeholder shared library target that can be built for editor smoke tests or as the starting point for real Android or iOS bridge work
- a stable C ABI boundary for adapting the existing Android and iOS mediasoup or WebRTC work into Unity

## What it is not

- not a real mediasoup implementation yet
- not a complete Android JNI bridge or iOS static-library packaging flow yet
- not enough by itself to publish or consume real media

## Files

- `CMakeLists.txt` - minimal shared-library build
- `include/MediaSfuUnityBridge.h` - exported C ABI contract
- `include/MediaSfuUnityBridgeBackend.h` - optional backend-registration contract and response helpers for real native engines
- `include/MediaSfuUnityOperationBackend.h` - typed per-operation backend adapter for real Android or iOS bridge implementations
- `src/MediaSfuUnityOperationBackend.c` - shared operation dispatcher that maps fixed Unity engine operations onto typed backend callbacks
- `src/MediaSfuUnityBridgeStub.c` - placeholder implementation that keeps engine creation and destruction working and returns explicit failure envelopes for real media operations
- `tests/MediaSfuUnityOperationBackendSmokeTest.c` - native smoke test that validates the typed operation adapter through the public bridge ABI

## Current Behavior

- `MediaSfuUnityCreateWebRtcEngine` allocates and returns an opaque engine handle.
- `MediaSfuUnityDestroyWebRtcEngine` frees that engine handle.
- `MediaSfuUnityInvokeWebRtcEngine` returns a JSON failure envelope explaining that the placeholder bridge must be replaced with a real native implementation.
- `MediaSfuUnityFreeString` frees the UTF-8 response buffer returned by `MediaSfuUnityInvokeWebRtcEngine`.

That makes the bridge suitable for attachment and binary-load smoke tests while still failing fast if real media flows are attempted.

When the same bridge is built on Android, it now also compiles an Android-specific JNI backend source that can install a raw bridge backend at `JNI_OnLoad` and forward `create`, `destroy`, and `invoke` calls into the Kotlin-side `AndroidUnityWebRtcJniBridge`. That keeps the exported C ABI stable while moving Android toward a real backend.

## Install-Ready Backend Mode

The scaffold now supports two native integration styles:

1. replace `src/MediaSfuUnityBridgeStub.c` outright with your real bridge implementation
2. keep the exported ABI file in place and install a real backend through `MediaSfuUnityInstallWebRtcBackend(...)` from `include/MediaSfuUnityBridgeBackend.h`

The second option is useful when you want one stable exported ABI layer and a separate Android or iOS backend module behind it.

`MediaSfuUnityBridgeBackend.h` currently provides:

- `MediaSfuUnityInstallWebRtcBackend(...)`
- `MediaSfuUnityResetWebRtcBackend()`
- `MediaSfuUnityDuplicateString(...)`
- `MediaSfuUnityCreateFailureResponse(...)`
- `MediaSfuUnityCreateSuccessResponse(...)`

Those helpers let a backend implementation return responses with the exact JSON envelope the managed Unity runtime already expects.

If your native backend already targets the fixed Unity engine operation set and you do not want to implement raw `invoke(operationName, payloadJson)` dispatch yourself, prefer `MediaSfuUnityInstallOperationBackend(...)` from `include/MediaSfuUnityOperationBackend.h`.

That adapter lets a backend register typed callbacks for:

- `loadDeviceRtpCapabilities`
- `initializeSendTransport`
- `createSendTransportConnectParameters`
- `completeSendTransportConnect`
- `createProduceRequest`
- `bindProducer`
- `pauseProducer`
- `resumeProducer`
- `closeProducer`
- `initializeReceiveTransport`
- `createReceiveTransportConnectParameters`
- `completeReceiveTransportConnect`
- `bindConsumer`
- `closeConsumer`

The shared bridge layer then owns the outer operation switch plus success/failure response envelopes.

The intended use is to load router RTP capabilities before send or receive transport creation so Android or iOS mediasoup-backed devices can be initialized in the same order their native SDKs already expect.

The adapter also now exposes explicit connect-completion callbacks so a backend can hold the native mediasoup `onConnect` callback until Unity has emitted the matching `transport-connect` or `transport-recv-connect` signal.

## Build

Example local build:

```sh
cmake -S unity-native-bridge -B unity-native-bridge/build
cmake --build unity-native-bridge/build
```

The resulting shared library is named `MediaSFUNativeBridge` so it matches the managed Unity import name on non-iOS targets.

Example local smoke test run:

```sh
ctest --test-dir unity-native-bridge/build --output-on-failure
```

That smoke test installs a typed operation backend through `MediaSfuUnityInstallOperationBackend(...)` and verifies create/invoke/destroy behavior through the exported `MediaSfuUnity*` symbols.

## Package Into mediasfu-unity

To copy built bridge outputs into the Unity package plugin folders, run from the repo root:

```sh
./scripts/build_unity_native_bridge_package.sh
```

That script keeps the bridge packaging standalone from the KMP Android library build. On macOS it copies the editor plugin from this repo into `mediasfu-unity/Runtime/Plugins/macOS/`, stages the real iOS `MediaSFUNativeBridge.xcframework` plus companion `WebRTC.xcframework` into `mediasfu-unity/Runtime/Plugins/iOS/`, and when an Android NDK is available it also builds Android `.so` outputs into `mediasfu-unity/Runtime/Plugins/Android/<abi>/`.

## Implement a platform backend

Replace `src/MediaSfuUnityBridgeStub.c`, or register a real backend behind it,
when adding a platform that this package does not already support. The backend
must:

1. create and own a real native media-engine handle;
2. map `MediaSfuUnityInvokeWebRtcEngine` operations onto the platform's
   mediasoup/WebRTC calls;
3. return the documented success envelopes for transport connection,
   production, producer binding, and consumer binding; and
4. package the final binary for the intended Unity Editor and player targets
   behind the same ABI.

The Android JNI source demonstrates backend registration, Kotlin registry
forwarding, and Android plugin packaging.

The payload and response envelope expected by the managed Unity runtime are documented in [../mediasfu-unity/Documentation~/NATIVE_PLUGIN_CONTRACT.md](../mediasfu-unity/Documentation~/NATIVE_PLUGIN_CONTRACT.md).
