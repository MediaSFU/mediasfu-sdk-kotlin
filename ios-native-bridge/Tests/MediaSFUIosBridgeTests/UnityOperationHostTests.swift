import Dispatch
import XCTest
@testable import MediaSFUIosBridge

#if MEDIA_SFU_HAS_MEDIASOUP_CLIENT && canImport(WebRTC)
import WebRTC
#endif

final class UnityOperationHostTests: XCTestCase {
    func testOperationHostLoadsCapabilitiesOnceAndRejectsDifferentReload() throws {
        let device = FakeUnityLoadableDeviceAdapter()
        let host = MediaSFUIosUnityWebRtcOperationHost(device: device)

        let initialResponse = try decodeResponse(host.invoke(
            operationName: "loadDeviceRtpCapabilities",
            payloadJson: try encodeJson([
                "roomRtpCapabilitiesJson": "{\"b\":1,\"a\":2}"
            ])
        ))
        XCTAssertTrue(initialResponse.success)

        let samePayloadResponse = try decodeResponse(host.invoke(
            operationName: "loadDeviceRtpCapabilities",
            payloadJson: try encodeJson([
                "roomRtpCapabilitiesJson": "{\"a\":2,\"b\":1}"
            ])
        ))
        XCTAssertTrue(samePayloadResponse.success)
        XCTAssertEqual(device.loadedRtpCapabilitiesJsons.count, 1)

        let differentPayloadResponse = try decodeResponse(host.invoke(
            operationName: "loadDeviceRtpCapabilities",
            payloadJson: try encodeJson([
                "roomRtpCapabilitiesJson": "{\"a\":3}"
            ])
        ))
        XCTAssertFalse(differentPayloadResponse.success)
        XCTAssertEqual(differentPayloadResponse.error, "native_bridge_operation_failed")
        XCTAssertTrue(differentPayloadResponse.detail.contains("cannot reload different router RTP capabilities"))
    }

    func testOperationHostHandlesSendTransportConnectProduceAndProducerLifecycle() throws {
        let device = FakeUnityLoadableDeviceAdapter()
        var disposedKinds: [MediaSFUUnityTrackKind] = []
        let host = MediaSFUIosUnityWebRtcOperationHost(device: device) { kind in
            MediaSFUUnityLocalTrackResource(track: FakeTrack()) {
                disposedKinds.append(kind)
            }
        }

        _ = host.invoke(
            operationName: "loadDeviceRtpCapabilities",
            payloadJson: try encodeJson([
                "roomRtpCapabilitiesJson": "{\"router\":true}"
            ])
        )

        let initializeResponse = try decodeResponse(host.invoke(
            operationName: "initializeSendTransport",
            payloadJson: try encodeJson([
                "transport": makeTransportPayload(id: "send-1", rawAppDataSource: "camera")
            ])
        ))
        XCTAssertTrue(initializeResponse.success)
        XCTAssertEqual(device.lastSendParams?["id"] as? String, "send-1")
        XCTAssertEqual((device.lastSendParams?["appData"] as? [String: Any])?["source"] as? String, "camera")

        device.sendTransport.emitConnect(dtlsJson: "{\"role\":\"client\"}")

        let connectParametersResponse = try decodeResponse(host.invoke(
            operationName: "createSendTransportConnectParameters"
        ))
        XCTAssertTrue(connectParametersResponse.success)
        XCTAssertEqual(
            (connectParametersResponse.result as? [String: Any])?["dtlsParametersJson"] as? String,
            "{\"role\":\"client\"}"
        )

        let completeConnectResponse = try decodeResponse(host.invoke(
            operationName: "completeSendTransportConnect",
            payloadJson: try encodeJson([
                "success": true,
                "errorDetail": ""
            ])
        ))
        XCTAssertTrue(completeConnectResponse.success)
        XCTAssertEqual(device.sendTransport.connectCallbackCount, 1)

        let produceRequestResponse = try decodeResponse(host.invoke(
            operationName: "createProduceRequest",
            payloadJson: try encodeJson([
                "trackKind": MediaSFUUnityTrackKind.audio.rawValue,
                "enabled": true,
                "localUserName": "host-user",
                "localIsLevel": "host",
                "participantName": "",
                "participantIslevel": ""
            ])
        ))
        XCTAssertTrue(produceRequestResponse.success)

        let produceRequest = (produceRequestResponse.result as? [String: Any])?["produceRequest"] as? [String: Any]
        let produceKind = produceRequest?["kind"] as? String
        let produceName = produceRequest?["name"] as? String
        let produceLevel = produceRequest?["isLevel"] as? String
        XCTAssertEqual(produceKind, "audio")
        XCTAssertEqual(produceName, "host-user")
        XCTAssertEqual(produceLevel, "host")
        XCTAssertEqual((try MediaSFUBridgeJson.decodeObject(produceRequest?["appDataJson"] as? String ?? "{}"))["mediaTag"] as? String, "audio")

        let bindProducerResponse = try decodeResponse(host.invoke(
            operationName: "bindProducer",
            payloadJson: try encodeJson([
                "trackKind": MediaSFUUnityTrackKind.audio.rawValue,
                "producerId": "server-producer-a"
            ])
        ))
        XCTAssertTrue(bindProducerResponse.success)
        XCTAssertEqual(device.sendTransport.lastCallbackProducerId, "server-producer-a")
        XCTAssertEqual(device.sendTransport.lastProducer?.id, "server-producer-a")

        XCTAssertTrue(try decodeResponse(host.invoke(
            operationName: "pauseProducer",
            payloadJson: try encodeJson([
                "trackKind": MediaSFUUnityTrackKind.audio.rawValue
            ])
        )).success)
        XCTAssertEqual(device.sendTransport.lastProducer?.pauseCount, 1)

        XCTAssertTrue(try decodeResponse(host.invoke(
            operationName: "resumeProducer",
            payloadJson: try encodeJson([
                "trackKind": MediaSFUUnityTrackKind.audio.rawValue
            ])
        )).success)
        XCTAssertEqual(device.sendTransport.lastProducer?.resumeCount, 1)

        XCTAssertTrue(try decodeResponse(host.invoke(
            operationName: "closeProducer",
            payloadJson: try encodeJson([
                "trackKind": MediaSFUUnityTrackKind.audio.rawValue
            ])
        )).success)
        XCTAssertEqual(device.sendTransport.lastProducer?.closeCount, 1)
        XCTAssertEqual(disposedKinds, [.audio])
    }

    func testOperationHostHandlesReceiveTransportAndConsumerLifecycle() throws {
        let device = FakeUnityLoadableDeviceAdapter()
        let host = MediaSFUIosUnityWebRtcOperationHost(device: device)

        _ = host.invoke(
            operationName: "loadDeviceRtpCapabilities",
            payloadJson: try encodeJson([
                "roomRtpCapabilitiesJson": "{\"router\":true}"
            ])
        )

        let initializeResponse = try decodeResponse(host.invoke(
            operationName: "initializeReceiveTransport",
            payloadJson: try encodeJson([
                "remoteProducer": ["producerId": "remote-1"],
                "transport": makeTransportPayload(id: "recv-1", rawAppDataSource: "remote")
            ])
        ))
        XCTAssertTrue(initializeResponse.success)
        XCTAssertEqual(device.lastRecvParams?["id"] as? String, "recv-1")
        XCTAssertEqual((device.lastRecvParams?["appData"] as? [String: Any])?["source"] as? String, "remote")

        device.recvTransport.emitConnect(dtlsJson: "{\"role\":\"server\"}")

        let connectParametersResponse = try decodeResponse(host.invoke(
            operationName: "createReceiveTransportConnectParameters",
            payloadJson: try encodeJson([
                "remoteProducer": ["producerId": "remote-1"]
            ])
        ))
        XCTAssertTrue(connectParametersResponse.success)
        XCTAssertEqual(
            (connectParametersResponse.result as? [String: Any])?["dtlsParametersJson"] as? String,
            "{\"role\":\"server\"}"
        )

        let completeResponse = try decodeResponse(host.invoke(
            operationName: "completeReceiveTransportConnect",
            payloadJson: try encodeJson([
                "remoteProducer": ["producerId": "remote-1"],
                "success": true,
                "errorDetail": ""
            ])
        ))
        XCTAssertTrue(completeResponse.success)
        XCTAssertEqual(device.recvTransport.connectCallbackCount, 1)

        let bindConsumerResponse = try decodeResponse(host.invoke(
            operationName: "bindConsumer",
            payloadJson: try encodeJson([
                "remoteProducer": ["producerId": "remote-1"],
                "consumeResponse": [
                    "consumerId": "consumer-1",
                    "producerId": "producer-1",
                    "kind": "video",
                    "rtpParametersJson": "{\"mid\":\"1\"}",
                    "serverConsumerId": "server-consumer-1",
                    "rawPayload": ""
                ]
            ])
        ))
        XCTAssertTrue(bindConsumerResponse.success)
        XCTAssertEqual(device.recvTransport.lastConsumeId, "consumer-1")
        XCTAssertEqual(device.recvTransport.lastConsumeProducerId, "producer-1")
        XCTAssertEqual(device.recvTransport.lastConsumeKind, "video")
        XCTAssertEqual(device.recvTransport.lastConsumer?.id, "consumer-1")

        let closeResponse = try decodeResponse(host.invoke(
            operationName: "closeConsumer",
            payloadJson: try encodeJson([
                "remoteProducerId": "remote-1"
            ])
        ))
        XCTAssertTrue(closeResponse.success)
        XCTAssertEqual(device.recvTransport.lastConsumer?.closeCount, 1)
    }

    func testOperationHostReturnsUnknownOperationResponse() throws {
        let host = MediaSFUIosUnityWebRtcOperationHost(device: FakeUnityLoadableDeviceAdapter())
        let response = try decodeResponse(host.invoke(operationName: "unsupportedOperation"))

        XCTAssertFalse(response.success)
        XCTAssertEqual(response.error, "native_bridge_unknown_operation")
        XCTAssertTrue(response.detail.contains("unsupportedOperation"))
    }

#if os(macOS) && MEDIA_SFU_HAS_MEDIASOUP_CLIENT && canImport(WebRTC) && canImport(AVFoundation)
    func testDefaultTrackFactoryCreatesMacOSScreenTrackOrReturnsPermissionError() {
        do {
            let trackFactory = MediaSFUIosUnityDefaultLocalTrackFactory(factory: RTCPeerConnectionFactory())
            let resource = try trackFactory.makeTrackResource(for: MediaSFUUnityTrackKind.screen)
            XCTAssertEqual(resource.track.kind, "video")
            resource.dispose()
        } catch {
            let detail = error.localizedDescription
            XCTAssertTrue(
                detail.contains("Screen Recording access"),
                "unexpected screen-share failure: \(detail)"
            )
            XCTAssertFalse(detail.contains("does not yet support screen-share"))
        }
    }
#endif
}

private final class FakeUnityLoadableDeviceAdapter: MediaSFULoadableDeviceAdapter {
    let sendTransport = BlockingSendTransportAdapter()
    let recvTransport = RecordingRecvTransportAdapter()

    var loadedRtpCapabilitiesJsons: [String] = []
    var lastSendParams: [String: Any?]?
    var lastRecvParams: [String: Any?]?

    func load(routerRtpCapabilitiesJson: String) throws {
        loadedRtpCapabilitiesJsons.append(routerRtpCapabilitiesJson)
    }

    func currentRtpCapabilitiesJson() -> String? {
        loadedRtpCapabilitiesJsons.last
    }

    func createSendTransport(params: [String : Any?]) throws -> MediaSFUSendTransportAdapter {
        lastSendParams = params
        return sendTransport
    }

    func createRecvTransport(params: [String : Any?]) throws -> MediaSFURecvTransportAdapter {
        lastRecvParams = params
        return recvTransport
    }
}

private class BaseRecordingTransportAdapter: MediaSFUTransportAdapter {
    let id: String
    var connectionState: String = "new"
    var connectListener: MediaSFUNativeConnectListener?
    var stateListener: ((String) -> Void)?
    var connectCallbackCount = 0
    var connectErrbackMessages: [String] = []

    init(id: String) {
        self.id = id
    }

    func close() {
        connectionState = "closed"
        stateListener?("closed")
    }

    func setOnConnect(_ listener: MediaSFUNativeConnectListener?) {
        connectListener = listener
    }

    func setOnConnectionStateChange(_ listener: ((String) -> Void)?) {
        stateListener = listener
    }

    func emitConnect(dtlsJson: String) {
        connectListener?(
            dtlsJson,
            { self.connectCallbackCount += 1 },
            { error in self.connectErrbackMessages.append(error.localizedDescription) }
        )
    }
}

private final class BlockingSendTransportAdapter: BaseRecordingTransportAdapter, MediaSFUSendTransportAdapter {
    var produceListener: MediaSFUNativeProduceListener?
    var lastProducedTrack: MediaSFUNativeTrack?
    var lastProducedAppDataJson: String?
    var lastCallbackProducerId: String?
    var lastProducer: RecordingProducerAdapter?

    init() {
        super.init(id: "send-adapter")
    }

    func setOnProduce(_ listener: MediaSFUNativeProduceListener?) {
        produceListener = listener
    }

    func produce(
        track: MediaSFUNativeTrack,
        encodingsJson: String?,
        appDataJson: String?
    ) throws -> MediaSFUProducerAdapter {
        guard let produceListener else {
            throw TestOperationError("produce was invoked without a registered onProduce listener")
        }

        lastProducedTrack = track
        lastProducedAppDataJson = appDataJson

        let callbackSemaphore = DispatchSemaphore(value: 0)
        var callbackProducerId: String?
        var callbackError: Error?

        produceListener(
            resolveKind(from: appDataJson),
            "{\"mid\":\"audio0\"}",
            appDataJson,
            { producerId in
                self.lastCallbackProducerId = producerId
                callbackProducerId = producerId
                callbackSemaphore.signal()
            },
            { error in
                callbackError = error
                callbackSemaphore.signal()
            }
        )

        if callbackSemaphore.wait(timeout: .now() + 2) == .timedOut {
            throw TestOperationError("Timed out waiting for bindProducer to provide a producer id")
        }

        if let callbackError {
            throw callbackError
        }

        let producer = RecordingProducerAdapter(
            id: callbackProducerId ?? "producer-missing-id",
            kind: resolveKind(from: appDataJson)
        )
        lastProducer = producer
        return producer
    }

    private func resolveKind(from appDataJson: String?) -> String {
        guard let appDataJson,
              let appData = try? MediaSFUBridgeJson.decodeObject(appDataJson),
              let mediaTag = appData["mediaTag"] as? String else {
            return "audio"
        }

        return mediaTag == "audio" ? "audio" : "video"
    }
}

private final class RecordingRecvTransportAdapter: BaseRecordingTransportAdapter, MediaSFURecvTransportAdapter {
    var lastConsumeId: String?
    var lastConsumeProducerId: String?
    var lastConsumeKind: String?
    var lastConsumeRtpParametersJson: String?
    var lastConsumer: RecordingConsumerAdapter?

    init() {
        super.init(id: "recv-adapter")
    }

    func consume(id: String, producerId: String, kind: String, rtpParametersJson: String) throws -> MediaSFUConsumerAdapter {
        lastConsumeId = id
        lastConsumeProducerId = producerId
        lastConsumeKind = kind
        lastConsumeRtpParametersJson = rtpParametersJson
        let consumer = RecordingConsumerAdapter(id: id, kind: kind, track: FakeTrack())
        lastConsumer = consumer
        return consumer
    }
}

private final class RecordingProducerAdapter: MediaSFUProducerAdapter {
    let id: String
    let kind: String
    var paused = false
    var closeCount = 0
    var pauseCount = 0
    var resumeCount = 0

    init(id: String, kind: String) {
        self.id = id
        self.kind = kind
    }

    func close() {
        closeCount += 1
    }

    func pause() {
        paused = true
        pauseCount += 1
    }

    func resume() {
        paused = false
        resumeCount += 1
    }

    func replaceTrack(_ track: MediaSFUNativeTrack) throws {
    }

    func getStatsJson() -> String? {
        nil
    }
}

private final class RecordingConsumerAdapter: MediaSFUConsumerAdapter {
    let id: String
    let kind: String
    let track: MediaSFUNativeTrack?
    var paused = false
    var closeCount = 0

    init(id: String, kind: String, track: MediaSFUNativeTrack?) {
        self.id = id
        self.kind = kind
        self.track = track
    }

    func close() {
        closeCount += 1
    }

    func pause() {
        paused = true
    }

    func resume() {
        paused = false
    }

    func getStatsJson() -> String? {
        nil
    }
}

private struct DecodedOperationResponse {
    let success: Bool
    let error: String
    let detail: String
    let result: Any?
}

private struct TestOperationError: LocalizedError {
    let message: String

    init(_ message: String) {
        self.message = message
    }

    var errorDescription: String? {
        message
    }
}

private func decodeResponse(_ json: String) throws -> DecodedOperationResponse {
    let object = try MediaSFUBridgeJson.decodeObject(json)
    return DecodedOperationResponse(
        success: object["success"] as? Bool ?? false,
        error: object["error"] as? String ?? "",
        detail: object["detail"] as? String ?? "",
        result: object["result"]
    )
}

private func encodeJson(_ value: [String: Any]) throws -> String {
    try MediaSFUBridgeJson.encode(value)
}

private func makeTransportPayload(id: String, rawAppDataSource: String) -> [String: Any] {
    [
        "id": id,
        "iceParametersJson": "{\"usernameFragment\":\"ufrag\"}",
        "iceCandidatesJson": "[{\"protocol\":\"udp\"}]",
        "dtlsParametersJson": "{\"role\":\"auto\"}",
        "sctpParametersJson": "{\"port\":5000}",
        "rawPayload": "{\"appData\":{\"source\":\"\(rawAppDataSource)\"}}"
    ]
}