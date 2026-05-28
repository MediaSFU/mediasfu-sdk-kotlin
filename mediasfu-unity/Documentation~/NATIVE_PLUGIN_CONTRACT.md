# Native Plugin Contract

`MediaSfuNativePluginWebRtcEngine` is the concrete native-plugin implementation of `IMediaSfuWebRtcEngine` in the standalone `com.mediasfu.mediasoup-client-unity` package. The MediaSFU Unity SDK consumes it through that package dependency.

The native client package includes native-plugin paths behind the same exported `MediaSfuUnity*` ABI:

- the shared bridge scaffold in [../../unity-native-bridge/README.md](../../unity-native-bridge/README.md), which remains the editor-loadable macOS path and the stable ABI surface for backend installation
- the packaged iOS player bridge and WebRTC companion XCFramework under the standalone client package's iOS plugin folder

## Managed Attach Helper

The client package exposes `MediaSfuNativePluginWebRtcEngine.TryCreateWebRtcDevice(...)` as the preferred attach helper for the current supported backend path.

It returns `MediaSfuOperationResult<IMediaSfuWebRtcDevice>` so validation scenes and starter flows can:

- qualify whether the packaged native bridge is currently installable and non-placeholder
- surface a normal SDK failure message instead of relying on exception-driven control flow
- attach the resulting device directly to `MediaSfuClient`

Use that helper in scene bootstrap or sample code when the native plugin path is the intended backend.

The client package also exposes `MediaSfuNativePluginWebRtcEngine.TryGetBackendInfo(...)` for diagnostics or validation flows that want to inspect the native bridge without attaching it.

The bridge probe uses the internal `describeBackend` operation. That lets the managed helper reject placeholder backends before any room or media flow starts.

## Plugin Name Resolution

- iOS player builds: `__Internal`
- all other Unity targets and the Unity Editor: `MediaSFUNativeBridge`

## Exported Symbols

Your native bridge must export these symbols:

```c
void* MediaSfuUnityCreateWebRtcEngine(const char* payloadJson);
void MediaSfuUnityDestroyWebRtcEngine(void* engineHandle);
char* MediaSfuUnityInvokeWebRtcEngine(void* engineHandle, const char* operationName, const char* payloadJson);
void MediaSfuUnityFreeString(char* responsePointer);
```

Rules:

- `MediaSfuUnityCreateWebRtcEngine` returns a non-null opaque engine handle on success.
- `MediaSfuUnityInvokeWebRtcEngine` returns a heap-allocated UTF-8 JSON string.
- Unity will call `MediaSfuUnityFreeString` exactly once for every non-null pointer returned by `MediaSfuUnityInvokeWebRtcEngine`.
- `MediaSfuUnityDestroyWebRtcEngine` must clean up the engine handle and any native resources owned by it.

## Optional Backend Registration API

The shared bridge sources in `unity-native-bridge/` also expose an optional native-side backend contract in `include/MediaSfuUnityBridgeBackend.h`.

That API is not required by the managed Unity runtime, but it lets native code keep the exported `MediaSfuUnity*` ABI stable while installing a real backend behind it.

Available functions:

```c
void MediaSfuUnityInstallWebRtcBackend(const MediaSfuUnityWebRtcBackend* backend, void* installContext);
void MediaSfuUnityResetWebRtcBackend(void);
char* MediaSfuUnityDuplicateString(const char* value);
char* MediaSfuUnityCreateFailureResponse(const char* errorCode, const char* detail);
char* MediaSfuUnityCreateSuccessResponse(const char* resultJson);
```

Use that path when you want one exported bridge file and a separate Android, macOS, or iOS backend module behind it.

The scaffold also now includes `include/MediaSfuUnityOperationBackend.h` with `MediaSfuUnityInstallOperationBackend(...)` for backends that want to register typed per-operation callbacks instead of implementing raw `invoke(operationName, payloadJson)` dispatch themselves.

That adapter keeps the exported ABI unchanged while centralizing:

- operation-name dispatch
- success/failure envelope creation
- missing-operation failure handling

The first expected backend step for real mediasoup-backed implementations is now `loadDeviceRtpCapabilities`, which lets native Android or iOS code load router RTP capabilities before transport creation.

The transport-connect handshake now also has an explicit completion phase. Native backends can return DTLS parameters through `createSendTransportConnectParameters` or `createReceiveTransportConnectParameters`, let Unity emit `transport-connect` or `transport-recv-connect`, and then finish the pending mediasoup callback through `completeSendTransportConnect` or `completeReceiveTransportConnect`.

## Create Payload

`MediaSfuUnityCreateWebRtcEngine` receives:

```json
{
  "configurationJson": "{...}",
  "enableVerboseLogging": false,
  "integrationMode": "ios-native-bridge"
}
```

`configurationJson` is an opaque string reserved for native-side configuration.

## Response Envelope

Every `MediaSfuUnityInvokeWebRtcEngine` result must use this envelope:

```json
{
  "success": true,
  "error": "",
  "detail": "",
  "result": { }
}
```

Rules:

- `success=false` marks the operation as failed.
- `detail` should contain the actionable native-side error message.
- `result` may be omitted or null for void operations.

## Operation Names

The Unity runtime currently invokes these operation names:

- `describeBackend`
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

## Payload Shapes

### `describeBackend`

```json
{}
```

Successful `result`:

```json
{
  "description": "MediaSFU Unity native bridge placeholder",
  "backendKind": "placeholder",
  "isPlaceholder": true,
  "platform": "editor",
  "supportsAudio": false,
  "supportsVideo": false,
  "supportsScreenShare": false,
  "supportsWhiteboard": false,
  "supportsReceive": false
}
```

This operation is the managed-side probe used by `TryCreateWebRtcDevice(...)` and `TryGetBackendInfo(...)` to distinguish a real installed backend from the shared placeholder scaffold.

Installed backends may also return optional capability fields so the managed runtime can fail unsupported lanes earlier. Current examples in this repo include Android and Apple installed backends reporting `platform`, `supportsAudio`, `supportsVideo`, `supportsScreenShare`, `supportsWhiteboard`, and `supportsReceive`. Capability values are platform-specific; for example, the Unity Apple native-plugin backend now reports `supportsScreenShare=true` on macOS while remaining `false` on iOS.

### `loadDeviceRtpCapabilities`

```json
{
  "roomRtpCapabilitiesJson": "{...}"
}
```

This operation should load router RTP capabilities into the native mediasoup or WebRTC device before send or receive transport creation.

### `initializeSendTransport`

```json
{
  "transport": {
    "id": "transport-id",
    "iceParametersJson": "{...}",
    "iceCandidatesJson": "[...]",
    "dtlsParametersJson": "{...}",
    "sctpParametersJson": "{...}",
    "rawPayload": "{...}"
  }
}
```

### `createSendTransportConnectParameters`

```json
{}
```

Successful `result`:

```json
{
  "dtlsParametersJson": "{...}"
}
```

### `completeSendTransportConnect`

```json
{
  "success": true,
  "errorDetail": ""
}
```

This operation should invoke the pending native send-transport connect callback on success, or its errback on failure.

### `createProduceRequest`

```json
{
  "trackKind": "Audio",
  "enabled": true,
  "localUserName": "player-one",
  "localIsLevel": "0",
  "meetingId": "s12345678",
  "roomRtpCapabilitiesJson": "{...}",
  "secureCode": "",
  "participantId": "local-participant-id",
  "participantName": "player-one",
  "participantIslevel": "0"
}
```

Successful `result`:

```json
{
  "produceRequest": {
    "kind": "audio",
    "rtpParametersJson": "{...}",
    "appDataJson": "{...}",
    "name": "player-one",
    "isLevel": "0"
  }
}
```

### `bindProducer`

```json
{
  "trackKind": "Video",
  "producerId": "producer-id"
}
```

### `pauseProducer`, `resumeProducer`, `closeProducer`

```json
{
  "trackKind": "Screen"
}
```

### `initializeReceiveTransport`

```json
{
  "remoteProducer": {
    "producerId": "remote-producer-id",
    "socketId": "socket-id",
    "participantId": "participant-id",
    "participantName": "remote-user",
    "kind": "video",
    "isLevel": "0",
    "source": "camera"
  },
  "transport": {
    "id": "transport-id",
    "iceParametersJson": "{...}",
    "iceCandidatesJson": "[...]",
    "dtlsParametersJson": "{...}",
    "sctpParametersJson": "{...}",
    "rawPayload": "{...}"
  }
}
```

### `createReceiveTransportConnectParameters`

```json
{
  "remoteProducer": {
    "producerId": "remote-producer-id",
    "socketId": "socket-id",
    "participantId": "participant-id",
    "participantName": "remote-user",
    "kind": "audio",
    "isLevel": "0",
    "source": "microphone"
  }
}
```

Successful `result`:

```json
{
  "dtlsParametersJson": "{...}"
}
```

### `completeReceiveTransportConnect`

```json
{
  "remoteProducer": {
    "producerId": "producer-1"
  },
  "success": true,
  "errorDetail": ""
}
```

This operation should invoke the pending native receive-transport connect callback for the specified remote producer on success, or its errback on failure.

### `bindConsumer`

```json
{
  "remoteProducer": {
    "producerId": "remote-producer-id",
    "socketId": "socket-id",
    "participantId": "participant-id",
    "participantName": "remote-user",
    "kind": "video",
    "isLevel": "0",
    "source": "camera"
  },
  "consumeResponse": {
    "consumerId": "consumer-id",
    "producerId": "remote-producer-id",
    "kind": "video",
    "rtpParametersJson": "{...}",
    "serverConsumerId": "server-consumer-id",
    "rawPayload": "{...}"
  }
}
```

### `closeConsumer`

```json
{
  "remoteProducerId": "remote-producer-id"
}
```

## Current Remaining Work

- implement the native bridge binary that exports these symbols
- map the payloads to the Android/iOS mediasoup or WebRTC bridge code already present elsewhere in the repo, either through the raw backend API or the typed operation-backend adapter
- validate the full create, join, publish, consume, and teardown path in a Unity runtime session
