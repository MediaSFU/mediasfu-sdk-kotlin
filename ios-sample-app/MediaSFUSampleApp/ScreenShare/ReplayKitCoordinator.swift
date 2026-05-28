import ReplayKit

struct ReplayKitCoordinator {
    func describeAvailability() -> String {
        let recorder = RPScreenRecorder.shared()
        return recorder.isAvailable
            ? "ReplayKit in-app capture is available on this device/runtime."
            : "ReplayKit in-app capture is currently unavailable on this device/runtime."
    }
}
