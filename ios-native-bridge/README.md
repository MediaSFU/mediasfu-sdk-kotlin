# MediaSFU iOS Native Bridge Package

This folder contains a standalone Swift Package scaffold for the **native iOS mediasoup bridge layer** referenced by the Kotlin Multiplatform `iosMain` transport adapters.

## What it is

- a place to implement the real Swift-side mediasoup wrapper
- a package that can compile independently of the KMP framework by default
- a conditional shim that can install into the generated `shared` framework when that framework is present in a host app

## What it is not

- not a complete mediasoup implementation yet
- not wired into an Xcode app target inside this repo
- not linked to a real iOS mediasoup client dependency yet

## Structure

- `Package.swift` — SwiftPM manifest
- `Sources/MediaSFUIosBridge/BridgeContracts.swift` — native-side bridge protocols
- `Sources/MediaSFUIosBridge/BridgeErrors.swift` — shared bridge error types for connect/produce/consume flows
- `Sources/MediaSFUIosBridge/BridgeJson.swift` — JSON encode/decode helpers for mediasoup payload boundaries
- `Sources/MediaSFUIosBridge/PlaceholderBridge.swift` — placeholder transport/producer/consumer handles
- `Sources/MediaSFUIosBridge/MediasoupAdapterContracts.swift` — adapter protocols for a real native mediasoup implementation
- `Sources/MediaSFUIosBridge/ClientBackedAdapters.swift` — typed option parsing and client-backed adapter wrappers for real native mediasoup clients
- `Sources/MediaSFUIosBridge/LibmediasoupClientScaffold.swift` — likely libmediasoup-style facade that plugs directly into the client-backed adapter layer
- `Sources/MediaSFUIosBridge/LibmediasoupWrapperSkeleton.swift` — concrete wrapper classes that adapt a future native mediasoup backend into the libmediasoup-style facade
- `Sources/MediaSFUIosBridge/ExpectedLibmediasoupBackendTemplate.swift` — callback-driven backend template showing where DTLS connect, produce, consume, and state hooks should be wired from a real native engine
- `Sources/MediaSFUIosBridge/AssumedLibmediasoupBindingTemplate.swift` — template for adapting a realistic foreign mediasoup binding API into the expected engine/backend contracts
- `Sources/MediaSFUIosBridge/RealMediasoupBindingEngine.swift` — concrete starting point that turns a real binding conformer into an installable adapter for the KMP bridge
- `Sources/MediaSFUIosBridge/ConcreteMediasoupBindingTemplate.swift` — practical concrete binding classes that a host app can implement directly against a chosen native mediasoup library
- `Sources/MediaSFUIosBridge/DeviceBackedBridge.swift` — bridge that wraps adapter-backed native objects
- `Sources/MediaSFUIosBridge/KmpInstallShim.swift` — conditional adapter layer into the exported KMP `shared` framework

## Intended host-app flow

1. Keep the protocol shapes aligned with the exported Kotlin bridge contracts.
2. In the host app, import both the Swift package and the generated KMP framework.
3. Use `MediaSFUKmpBridgeInstaller.installPlaceholderBridgeIfSupported()` for scaffolding or smoke tests.
4. Use `MediaSFUKmpBridgeInstaller.installDeviceBackedBridgeIfSupported(device:)` for real mediasoup integration.
5. Verify `installIosNativeMediasoupBridge(...)` runs before conference join / transport creation.

## Install modes

- **Placeholder mode**
  - Useful for startup wiring, package validation, and callback smoke tests.
  - Does not connect to a real mediasoup client.
- **Device-backed mode**
  - Intended for production integration.
  - Provide a `MediaSFUDeviceAdapter` backed by your actual native mediasoup implementation.
  - The package wraps that adapter through `DeviceBackedMediasoupBridge` and exports it to Kotlin via `KmpInstallShim.swift`.
  - If your native wrapper already has typed create/provide APIs, prefer `ClientBackedDeviceAdapter` so raw KMP params are converted into typed transport/produce/consume options once.
  - If your native wrapper closely mirrors libmediasoup transport creation, `LibmediasoupClientDeviceAdapter` is the shortest path from host app startup to KMP bridge installation.
  - If you need a compile-safe place to start implementing the real backend, use `MediaSFULibmediasoupDeviceWrapper` plus the `MediaSFURawLibmediasoup*Backend` protocols.
  - If you need a template for callback plumbing, start from `MediaSFUExpectedLibmediasoupDeviceBackend` and the `MediaSFUExpectedLibmediasoup*Engine` protocols.
  - If the real library exposes a different foreign binding shape, adapt it first through `MediaSFUAssumedLibmediasoupDeviceEngine` and the `MediaSFUAssumedLibmediasoup*Binding` protocols.
  - If you want one direct file to implement against, start with `RealMediasoupBindingEngine.makeInstallableAdapter(binding:)` and provide a concrete binding conformer there.
  - If you want the most concrete template in the repo, implement `ConcreteMediasoupNative*` in `ConcreteMediasoupBindingTemplate.swift` and pass the native device into `ConcreteMediasoupBindingTemplate.makeInstallableAdapter(nativeDevice:)`.

## Important notes

- `KmpInstallShim.swift` is wrapped in conditional imports so this package can still build without the generated KMP framework.
- The exact generated Swift symbol names from Kotlin may vary depending on framework/module naming; adjust the `shared` import and exported symbol references if needed.
- When a real mediasoup iOS client is introduced, ensure it uses a compatible WebRTC binary family with the KMP `pod("WebRTC")` integration.
- The placeholder transport handles now include test harness helpers to trigger `onConnect` and `onProduce` callbacks with JSON payloads, making it easier to replace them incrementally with a real mediasoup implementation.
- The package also includes explicit bridge error types so native failure paths can be modeled before a full mediasoup client is wired in.
- `MediaSFUKmpBridgeInstaller.installBridgeIfSupported(_:)` is the lowest-level install helper when you already have a custom `MediaSFUNativeMediasoupBridge`.
