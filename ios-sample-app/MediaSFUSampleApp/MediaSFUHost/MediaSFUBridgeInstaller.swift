import Foundation

#if canImport(MediaSFUIosBridge)
import MediaSFUIosBridge
#endif

#if canImport(MediaSFUSDK)
import MediaSFUSDK
#endif

#if canImport(MediaSFUMediasoupClient)
import MediaSFUMediasoupClient
#endif

#if canImport(WebRTC)
import WebRTC
#endif

struct MediaSFUBridgeInstaller {
    func installIfNeeded() -> String {
        // Keep simulator-specific manual audio as the workaround for the iOS
        // 26.x WebRTC crash path, but let physical devices use the normal
        // WebRTC audio lifecycle so microphone capture is actually enabled.
        #if canImport(WebRTC)
        let audioSession = RTCAudioSession.sharedInstance()
        #if targetEnvironment(simulator)
        audioSession.useManualAudio = true
        audioSession.isAudioEnabled = false
        #else
        audioSession.useManualAudio = false
        audioSession.isAudioEnabled = true
        #endif
        #endif

        let requiresRealNativeBridge = Self.automationFlag(named: "MEDIASFU_REQUIRE_REAL_NATIVE_BRIDGE")

        #if canImport(MediaSFUIosBridge) && canImport(MediaSFUSDK) && canImport(WebRTC)
        if MediaSFUSDKDirectBridgeInstaller.isBridgeInstalled() {
            return MediaSFUBridgeInstallStatus.record("KMP bridge already installed in the MediaSFUSDK runtime.")
        }

        var nativeClientModeDescription: String?

        #if canImport(MediaSFUMediasoupClient)
        let device = MSCDevice()
        nativeClientModeDescription = device.nativeIntegrationMode.rawValue
        if MediaSFUSDKDirectBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: device) {
            return MediaSFUBridgeInstallStatus.record("KMP bridge installed with MediaSFUMediasoupClient in the MediaSFUSDK runtime (mode: \(device.nativeIntegrationMode.rawValue)). The native device will load router RTP capabilities during session bootstrap.")
        }
        #endif

        if requiresRealNativeBridge {
            if let nativeClientModeDescription {
                return MediaSFUBridgeInstallStatus.record("Real native bridge required but unavailable in the MediaSFUSDK runtime (mode: \(nativeClientModeDescription)).")
            }

            return MediaSFUBridgeInstallStatus.record("Real native bridge required but MediaSFUMediasoupClient is not linked in this target.")
        }

        if MediaSFUSDKDirectBridgeInstaller.installPlaceholderBridgeIfSupported() {
            if let nativeClientModeDescription {
                return MediaSFUBridgeInstallStatus.record("KMP bridge install path is available through the MediaSFUSDK runtime. Placeholder mode is active because MediaSFUMediasoupClient is linked but not fully native yet (mode: \(nativeClientModeDescription)).")
            }

            return MediaSFUBridgeInstallStatus.record("KMP bridge install path is available through the MediaSFUSDK runtime. Placeholder mode is active for scaffold bootstrapping because MediaSFUMediasoupClient is not linked in this target.")
        }
        #endif

        #if canImport(MediaSFUIosBridge)
        if MediaSFUKmpBridgeInstaller.isKmpModuleAvailable() {
            var nativeClientModeDescription: String?

            #if canImport(MediaSFUMediasoupClient)
            let device = MSCDevice()
            nativeClientModeDescription = device.nativeIntegrationMode.rawValue
            if MediaSFUKmpBridgeInstaller.installMediaSFUMediasoupClientBridgeIfSupported(device: device) {
                return MediaSFUBridgeInstallStatus.record("KMP bridge installed with MediaSFUMediasoupClient (mode: \(device.nativeIntegrationMode.rawValue)). The native device will load router RTP capabilities during session bootstrap.")
            }
            #endif

            if requiresRealNativeBridge {
                if let nativeClientModeDescription {
                    return MediaSFUBridgeInstallStatus.record("Real native bridge required but unavailable (mode: \(nativeClientModeDescription)).")
                }

                return MediaSFUBridgeInstallStatus.record("Real native bridge required but MediaSFUMediasoupClient is not linked in this target.")
            }

            let installed = MediaSFUKmpBridgeInstaller.installPlaceholderBridgeIfSupported()
            if installed {
                if let nativeClientModeDescription {
                    return MediaSFUBridgeInstallStatus.record("KMP bridge install path is available. Placeholder mode is active because MediaSFUMediasoupClient is linked but not fully native yet (mode: \(nativeClientModeDescription)).")
                }

                return MediaSFUBridgeInstallStatus.record("KMP bridge install path is available. Placeholder mode is active for scaffold bootstrapping because MediaSFUMediasoupClient is not linked in this target.")
            }

            return MediaSFUBridgeInstallStatus.record("KMP framework not yet linked into the sample app target.")
        }

        return MediaSFUBridgeInstallStatus.record(MediaSFUKmpBridgeInstaller.bridgeInstallModeDescription())
        #else
        return MediaSFUBridgeInstallStatus.record("MediaSFUIosBridge is not linked in this build. Host UI can still be validated, but native bridge installation is unavailable until the package is added back without a WebRTC collision.")
        #endif
    }

    private static func automationFlag(named name: String) -> Bool {
        guard let rawValue = ProcessInfo.processInfo.environment[name] else {
            return false
        }

        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        default:
            return false
        }
    }
}

enum MediaSFUBridgeInstallStatus {
    private(set) static var latestSummary = ""

    static func record(_ summary: String) -> String {
        latestSummary = summary
        let message = "MediaSFU - bridge install status -> \(summary)"
        NSLog("%@", message)
        if let data = (message + "\n").data(using: .utf8) {
            FileHandle.standardError.write(data)
        }
        return summary
    }
}

#if canImport(MediaSFUIosBridge) && canImport(MediaSFUSDK) && canImport(WebRTC)
private enum MediaSFUSDKDirectBridgeInstaller {
    static func isBridgeInstalled() -> Bool {
        IosMediasoupBridgeKt.isIosNativeMediasoupBridgeInstalled()
    }

    @discardableResult
    static func installBridgeIfSupported(_ bridge: MediaSFUNativeMediasoupBridge) -> Bool {
        IosMediasoupBridgeKt.installIosNativeMediasoupBridge(bridge: MediaSFUSDKBridgeAdapter(bridge: bridge))
        return true
    }

    @discardableResult
    static func installPlaceholderBridgeIfSupported() -> Bool {
        installBridgeIfSupported(PlaceholderMediasoupBridge())
    }

    @discardableResult
    static func installDeviceBackedBridgeIfSupported(device: MediaSFUDeviceAdapter) -> Bool {
        installBridgeIfSupported(DeviceBackedMediasoupBridge(device: device))
    }

    #if canImport(MediaSFUMediasoupClient)
    @discardableResult
    static func installMediaSFUMediasoupClientBridgeIfSupported(device: MSCDevice) -> Bool {
        guard device.nativeIntegrationMode == .fullyBundledNative else {
            return false
        }

        let adapter = MediaSFUMediasoupClientBridgeFactory.makeInstallableAdapter(device: device)
        return installDeviceBackedBridgeIfSupported(device: adapter)
    }
    #endif
}

private final class MediaSFUSDKBridgeAdapter: IosNativeMediasoupBridge {
    private let bridge: MediaSFUNativeMediasoupBridge

    init(bridge: MediaSFUNativeMediasoupBridge) {
        self.bridge = bridge
    }

    func createSendTransport(params: [String : Any]) -> IosNativeSendTransportHandle {
        let mapped = params.reduce(into: [String: Any?]()) { partial, item in
            partial[String(describing: item.key)] = item.value
        }
        return MediaSFUSDKSendTransportAdapter(handle: bridge.createSendTransport(params: mapped))
    }

    func createRecvTransport(params: [String : Any]) -> IosNativeRecvTransportHandle {
        let mapped = params.reduce(into: [String: Any?]()) { partial, item in
            partial[String(describing: item.key)] = item.value
        }
        return MediaSFUSDKRecvTransportAdapter(handle: bridge.createRecvTransport(params: mapped))
    }

    func loadRtpCapabilitiesJson(rtpCapabilitiesJson: String) -> String? {
        guard let loadableBridge = bridge as? MediaSFUNativeLoadableMediasoupBridge else {
            NSLog("MediaSFU loadRtpCapabilitiesJson: bridge cast to MediaSFUNativeLoadableMediasoupBridge failed (bridge type: %@)", String(describing: type(of: bridge)))
            return "bridge-cast-to-loadable-failed"
        }

        do {
            try loadableBridge.load(routerRtpCapabilitiesJson: rtpCapabilitiesJson)
            return nil
        } catch {
            NSLog("MediaSFU loadRtpCapabilitiesJson: load threw: %@", error.localizedDescription)
            return error.localizedDescription
        }
    }

    func currentRtpCapabilitiesJson() -> String? {
        (bridge as? MediaSFUNativeLoadableMediasoupBridge)?.currentRtpCapabilitiesJson()
    }
}

private final class MediaSFUSDKSendTransportAdapter: IosNativeSendTransportHandle {
    private let handle: MediaSFUNativeSendTransportHandle

    init(handle: MediaSFUNativeSendTransportHandle) {
        self.handle = handle
    }

    var id: String { handle.id }

    func connectionState() -> String { handle.connectionState() }

    func close() { handle.close() }

    func setOnConnect(listener: IosNativeConnectListener?) {
        handle.setOnConnect(listener.map { listener in
            { dtlsJson, callback, errback in
                listener.onConnect(dtlsParametersJson: dtlsJson, callback: callback, errback: { error in errback(error.asError()) })
            }
        })
    }

    func setOnConnectionStateChange(listener: ((String) -> Void)?) {
        handle.setOnConnectionStateChange(listener)
    }

    func setOnProduce(listener: IosNativeProduceListener?) {
        handle.setOnProduce(listener.map { listener in
            { kind, rtpJson, appDataJson, callback, errback in
                listener.onProduce(kind: kind, rtpParametersJson: rtpJson, appDataJson: appDataJson, callback: callback, errback: { error in errback(error.asError()) })
            }
        })
    }

    func produce(track: RTCMediaStreamTrack, encodingsJson: String?, appDataJson: String?) -> IosNativeProducerHandle {
        MediaSFUSDKProducerAdapter(handle: handle.produce(track: track, encodingsJson: encodingsJson, appDataJson: appDataJson))
    }
}

private final class MediaSFUSDKRecvTransportAdapter: IosNativeRecvTransportHandle {
    private let handle: MediaSFUNativeRecvTransportHandle

    init(handle: MediaSFUNativeRecvTransportHandle) {
        self.handle = handle
    }

    var id: String { handle.id }

    func connectionState() -> String { handle.connectionState() }

    func close() { handle.close() }

    func setOnConnect(listener: IosNativeConnectListener?) {
        handle.setOnConnect(listener.map { listener in
            { dtlsJson, callback, errback in
                listener.onConnect(dtlsParametersJson: dtlsJson, callback: callback, errback: { error in errback(error.asError()) })
            }
        })
    }

    func setOnConnectionStateChange(listener: ((String) -> Void)?) {
        handle.setOnConnectionStateChange(listener)
    }

    func consume(id: String, producerId: String, kind: String, rtpParametersJson: String) -> IosNativeConsumerHandle {
        MediaSFUSDKConsumerAdapter(handle: handle.consume(id: id, producerId: producerId, kind: kind, rtpParametersJson: rtpParametersJson))
    }
}

private final class MediaSFUSDKProducerAdapter: IosNativeProducerHandle {
    private let handle: MediaSFUNativeProducerHandle

    init(handle: MediaSFUNativeProducerHandle) {
        self.handle = handle
    }

    var id: String { handle.id }
    var kind: String { handle.kind }

    func isPaused() -> Bool { handle.isPaused() }

    func close() { handle.close() }

    func pause() { handle.pause() }

    func replaceTrack(track: RTCMediaStreamTrack) { handle.replaceTrack(track) }

    func resume() { handle.resume() }

    func statsJson() -> String? { handle.statsJson() }
}

private final class MediaSFUSDKConsumerAdapter: IosNativeConsumerHandle {
    private let handle: MediaSFUNativeConsumerHandle

    init(handle: MediaSFUNativeConsumerHandle) {
        self.handle = handle
    }

    var id: String { handle.id }
    var kind: String { handle.kind }
    var track: RTCMediaStreamTrack? { handle.track }

    func isPaused() -> Bool { handle.isPaused() }

    func close() { handle.close() }

    func pause() { handle.pause() }

    func resume() { handle.resume() }

    func statsJson() -> String? { handle.statsJson() }
}
#endif
