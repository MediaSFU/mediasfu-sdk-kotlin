import Foundation

struct SampleSessionConfig: Equatable, Codable {
    var apiUserName: String = ""
    var apiKey: String = ""
    var localLink: String = ""
    var userName: String = "tester"
    var roomName: String = "mediasfu-demo"
    var connectMediaSFU: Bool = true
    var action: SampleSessionAction = .create
    var durationMinutes: Int = 60
    var capacity: Int = 100
    var eventType: SampleEventType = .conference
    var scheduledDateMillis: String = ""
    var secureCode: String = ""
    var recordOnly: Bool = false
    var safeRoom: Bool = false
    var autoStartSafeRoom: Bool = false
    var safeRoomAction: SampleSafeRoomAction = .kick
    var dataBuffer: Bool = false
    var bufferType: SampleBufferType = .all
    var adminPasscode: String = ""
    var islevel: String = "0"

    var normalizedApiUserName: String {
        apiUserName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedApiKey: String {
        apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedLocalLink: String {
        localLink.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedUserName: String {
        userName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedRoomName: String {
        roomName.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedScheduledDateMillis: String {
        scheduledDateMillis.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedSecureCode: String {
        secureCode.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedAdminPasscode: String {
        adminPasscode.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var normalizedIslevel: String {
        islevel.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var prejoinValidationMessage: String? {
        SamplePrejoinValidation.message(for: self)
    }

    var isPrejoinReady: Bool {
        prejoinValidationMessage == nil
    }
}

enum SamplePrejoinValidation {
    static let displayNameRange = 2...10

    static func message(for config: SampleSessionConfig) -> String? {
        let displayName = config.normalizedUserName

        guard displayNameRange.contains(displayName.count) else {
            return "Display name must be 2 to 10 characters."
        }

        guard isAsciiAlphanumeric(displayName) else {
            return "Display name can only use letters and numbers."
        }

        if requiresCloudCredentials(config: config) {
            if config.normalizedApiUserName.isEmpty {
                return "API username is required when using MediaSFU Cloud."
            }

            let apiKey = config.normalizedApiKey
            if apiKey.count != 64 || !isAsciiAlphanumeric(apiKey) {
                return "API key must be exactly 64 characters when using MediaSFU Cloud."
            }
        }

        switch config.action {
        case .create:
            if config.durationMinutes < 1 {
                return "Duration must be a positive number of minutes."
            }

            if config.capacity < 1 {
                return "Capacity must be a positive number."
            }
        case .join:
            let meetingID = config.normalizedRoomName
            guard isValidMeetingID(meetingID, localLink: config.normalizedLocalLink) else {
                return "Meeting ID must be alphanumeric, at least 8 characters, and start with d, p, or s for MediaSFU Cloud."
            }
        }

        return nil
    }

    static func isValidMeetingID(_ value: String, localLink: String) -> Bool {
        guard value.count >= 8, isAsciiAlphanumeric(value), let first = value.first else {
            return false
        }

        let isLocal = isLocalEndpoint(localLink)
        let allowedPrefixes = isLocal ? ["m"] : ["d", "p", "s"]
        return allowedPrefixes.contains(String(first).lowercased())
    }

    static func requiresCloudCredentials(config: SampleSessionConfig) -> Bool {
        config.connectMediaSFU && !isLocalEndpoint(config.normalizedLocalLink)
    }

    static func isLocalEndpoint(_ localLink: String) -> Bool {
        !localLink.isEmpty && !localLink.localizedCaseInsensitiveContains("mediasfu.com")
    }

    static func isAsciiAlphanumeric(_ value: String) -> Bool {
        guard !value.isEmpty else { return false }

        return value.unicodeScalars.allSatisfy { scalar in
            (65...90).contains(Int(scalar.value)) ||
                (97...122).contains(Int(scalar.value)) ||
                (48...57).contains(Int(scalar.value))
        }
    }
}

enum SampleSessionAction: String, CaseIterable, Identifiable, Codable {
    case create
    case join

    var id: String { rawValue }
}

enum SampleEventType: String, CaseIterable, Identifiable, Codable {
    case chat
    case broadcast
    case webinar
    case conference

    var id: String { rawValue }
}

enum SampleSafeRoomAction: String, CaseIterable, Identifiable, Codable {
    case kick
    case hold

    var id: String { rawValue }
}

enum SampleBufferType: String, CaseIterable, Identifiable, Codable {
    case all
    case video
    case audio

    var id: String { rawValue }
}
