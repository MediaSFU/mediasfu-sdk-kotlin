import Foundation

public final class DeviceBackedMediasoupBridge: MediaSFUNativeMediasoupBridge {
    private let device: MediaSFUDeviceAdapter

    public init(device: MediaSFUDeviceAdapter) {
        self.device = device
    }

    public func createSendTransport(params: [String : Any?]) -> MediaSFUNativeSendTransportHandle {
        do {
            let transport = try device.createSendTransport(params: params)
            return AdapterBackedSendTransportHandle(adapter: transport)
        } catch {
            return FailingSendTransportHandle(error: error)
        }
    }

    public func createRecvTransport(params: [String : Any?]) -> MediaSFUNativeRecvTransportHandle {
        do {
            let transport = try device.createRecvTransport(params: params)
            return AdapterBackedRecvTransportHandle(adapter: transport)
        } catch {
            return FailingRecvTransportHandle(error: error)
        }
    }
}

final class AdapterBackedSendTransportHandle: MediaSFUNativeSendTransportHandle {
    private let adapter: MediaSFUSendTransportAdapter

    init(adapter: MediaSFUSendTransportAdapter) {
        self.adapter = adapter
    }

    var id: String { adapter.id }
    func connectionState() -> String { adapter.connectionState }
    func close() { adapter.close() }
    func setOnConnect(_ listener: MediaSFUNativeConnectListener?) { adapter.setOnConnect(listener) }
    func setOnConnectionStateChange(_ listener: ((String) -> Void)?) { adapter.setOnConnectionStateChange(listener) }
    func setOnProduce(_ listener: MediaSFUNativeProduceListener?) { adapter.setOnProduce(listener) }

    func produce(
        track: MediaSFUNativeTrack,
        encodingsJson: String?,
        appDataJson: String?
    ) -> MediaSFUNativeProducerHandle {
        do {
            let producer = try adapter.produce(track: track, encodingsJson: encodingsJson, appDataJson: appDataJson)
            return AdapterBackedProducerHandle(adapter: producer)
        } catch {
            return FailingProducerHandle(error: error)
        }
    }
}

final class AdapterBackedRecvTransportHandle: MediaSFUNativeRecvTransportHandle {
    private let adapter: MediaSFURecvTransportAdapter

    init(adapter: MediaSFURecvTransportAdapter) {
        self.adapter = adapter
    }

    var id: String { adapter.id }
    func connectionState() -> String { adapter.connectionState }
    func close() { adapter.close() }
    func setOnConnect(_ listener: MediaSFUNativeConnectListener?) { adapter.setOnConnect(listener) }
    func setOnConnectionStateChange(_ listener: ((String) -> Void)?) { adapter.setOnConnectionStateChange(listener) }

    func consume(id: String, producerId: String, kind: String, rtpParametersJson: String) -> MediaSFUNativeConsumerHandle {
        do {
            let consumer = try adapter.consume(id: id, producerId: producerId, kind: kind, rtpParametersJson: rtpParametersJson)
            return AdapterBackedConsumerHandle(adapter: consumer)
        } catch {
            return FailingConsumerHandle(error: error)
        }
    }
}

final class AdapterBackedProducerHandle: MediaSFUNativeProducerHandle {
    private let adapter: MediaSFUProducerAdapter

    init(adapter: MediaSFUProducerAdapter) {
        self.adapter = adapter
    }

    var id: String { adapter.id }
    var kind: String { adapter.kind }
    func isPaused() -> Bool { adapter.paused }
    func close() { adapter.close() }
    func pause() { adapter.pause() }
    func resume() { adapter.resume() }
    func replaceTrack(_ track: MediaSFUNativeTrack) {
        try? adapter.replaceTrack(track)
    }
}

final class AdapterBackedConsumerHandle: MediaSFUNativeConsumerHandle {
    private let adapter: MediaSFUConsumerAdapter

    init(adapter: MediaSFUConsumerAdapter) {
        self.adapter = adapter
    }

    var id: String { adapter.id }
    var kind: String { adapter.kind }
    var track: MediaSFUNativeTrack? { adapter.track }
    func isPaused() -> Bool { adapter.paused }
    func close() { adapter.close() }
    func pause() { adapter.pause() }
    func resume() { adapter.resume() }
}

final class FailingSendTransportHandle: MediaSFUNativeSendTransportHandle {
    private let error: Error

    init(error: Error) { self.error = error }

    var id: String { "send-error" }
    func connectionState() -> String { "failed" }
    func close() {}
    func setOnConnect(_ listener: MediaSFUNativeConnectListener?) {
        listener?("{}", {}, { _ in })
    }
    func setOnConnectionStateChange(_ listener: ((String) -> Void)?) {
        listener?("failed")
    }
    func setOnProduce(_ listener: MediaSFUNativeProduceListener?) {
        listener?("audio", "{}", nil, { _ in }, { _ in })
    }
    func produce(track: MediaSFUNativeTrack, encodingsJson: String?, appDataJson: String?) -> MediaSFUNativeProducerHandle {
        FailingProducerHandle(error: error)
    }
}

final class FailingRecvTransportHandle: MediaSFUNativeRecvTransportHandle {
    private let error: Error

    init(error: Error) { self.error = error }

    var id: String { "recv-error" }
    func connectionState() -> String { "failed" }
    func close() {}
    func setOnConnect(_ listener: MediaSFUNativeConnectListener?) {
        listener?("{}", {}, { _ in })
    }
    func setOnConnectionStateChange(_ listener: ((String) -> Void)?) {
        listener?("failed")
    }
    func consume(id: String, producerId: String, kind: String, rtpParametersJson: String) -> MediaSFUNativeConsumerHandle {
        FailingConsumerHandle(error: error)
    }
}

final class FailingProducerHandle: MediaSFUNativeProducerHandle {
    private let error: Error

    init(error: Error) { self.error = error }

    var id: String { "producer-error" }
    var kind: String { "audio" }
    func isPaused() -> Bool { true }
    func close() {}
    func pause() {}
    func resume() {}
    func replaceTrack(_ track: MediaSFUNativeTrack) {
        _ = error
    }
}

final class FailingConsumerHandle: MediaSFUNativeConsumerHandle {
    private let error: Error

    init(error: Error) { self.error = error }

    var id: String { "consumer-error" }
    var kind: String { "audio" }
    var track: MediaSFUNativeTrack? { nil }
    func isPaused() -> Bool { true }
    func close() {}
    func pause() {}
    func resume() { _ = error }
}