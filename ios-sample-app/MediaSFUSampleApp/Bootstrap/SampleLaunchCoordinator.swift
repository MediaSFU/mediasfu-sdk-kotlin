import Foundation

@MainActor
final class SampleLaunchCoordinator: ObservableObject {
    @Published var sessionConfig: SampleSessionConfig {
        didSet {
            persistSessionConfig()
        }
    }
    @Published var isPresentingMediaSFU = false
    @Published var statusMessage: String?

    private static let sessionConfigDefaultsKey = "com.mediasfu.sample.sessionConfig"

    init() {
        if SampleAppEnvironment.resetSavedSessionRequested {
            UserDefaults.standard.removeObject(forKey: Self.sessionConfigDefaultsKey)
            sessionConfig = SampleSessionConfig()
        } else {
            sessionConfig = Self.loadPersistedSessionConfig()
        }
    }

    func launch(statusMessage: String? = nil) {
        self.statusMessage = statusMessage
        persistStatusMessage(statusMessage)
        isPresentingMediaSFU = true
    }

    func closeSession() {
        isPresentingMediaSFU = false
    }

    func updateStatus(_ message: String?) {
        statusMessage = message
        persistStatusMessage(message)
    }

    private func persistSessionConfig() {
        guard let encodedConfig = try? JSONEncoder().encode(sessionConfig) else {
            return
        }

        UserDefaults.standard.set(encodedConfig, forKey: Self.sessionConfigDefaultsKey)
    }

    private func persistStatusMessage(_ message: String?) {
        guard let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return
        }

        let fileURL = documentsDirectory.appendingPathComponent("mediasfu_bootstrap_status.txt")
        let payload = message?.isEmpty == false ? message! : ""
        try? payload.write(to: fileURL, atomically: true, encoding: .utf8)
    }

    private static func loadPersistedSessionConfig() -> SampleSessionConfig {
        guard let encodedConfig = UserDefaults.standard.data(forKey: sessionConfigDefaultsKey),
              let decodedConfig = try? JSONDecoder().decode(SampleSessionConfig.self, from: encodedConfig)
        else {
            return SampleSessionConfig()
        }

        return decodedConfig
    }
}
