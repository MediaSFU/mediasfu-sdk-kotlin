import AVFoundation

struct MediaPermissionsCoordinator {
    struct PermissionSnapshot {
        let cameraStatus: AVAuthorizationStatus
        let microphoneStatus: AVAuthorizationStatus

        var allGranted: Bool {
            cameraStatus == .authorized && microphoneStatus == .authorized
        }

        var statusMessage: String {
            "Camera: \(description(for: cameraStatus)), Microphone: \(description(for: microphoneStatus))"
        }

        var launchBlockMessage: String? {
            guard !allGranted else {
                return nil
            }

            let blockedPermissions = [
                blockedLabel(for: cameraStatus, permissionName: "Camera"),
                blockedLabel(for: microphoneStatus, permissionName: "Microphone")
            ].compactMap { $0 }

            guard !blockedPermissions.isEmpty else {
                return "Media permissions are still pending. \(statusMessage)"
            }

            return "Media permissions are required before joining. \(statusMessage). Enable \(blockedPermissions.joined(separator: " and ")) in Settings, then retry."
        }

        private func description(for status: AVAuthorizationStatus) -> String {
            switch status {
            case .authorized:
                return "granted"
            case .denied:
                return "denied"
            case .restricted:
                return "restricted"
            case .notDetermined:
                return "not determined"
            @unknown default:
                return "unknown"
            }
        }

        private func blockedLabel(for status: AVAuthorizationStatus, permissionName: String) -> String? {
            switch status {
            case .denied, .restricted:
                return permissionName
            default:
                return nil
            }
        }
    }

    func requestInitialPermissions() async -> String {
        let snapshot = await ensureLaunchPermissions()
        return snapshot.statusMessage
    }

    func ensureLaunchPermissions() async -> PermissionSnapshot {
        let cameraStatus = await resolvedAuthorizationStatus(for: .video)
        let microphoneStatus = await resolvedAuthorizationStatus(for: .audio)
        return PermissionSnapshot(cameraStatus: cameraStatus, microphoneStatus: microphoneStatus)
    }

    private func resolvedAuthorizationStatus(for mediaType: AVMediaType) async -> AVAuthorizationStatus {
        let currentStatus = AVCaptureDevice.authorizationStatus(for: mediaType)

        guard currentStatus == .notDetermined else {
            return currentStatus
        }

        let granted = await AVCaptureDevice.requestAccess(for: mediaType)
        if granted {
            return .authorized
        }

        let updatedStatus = AVCaptureDevice.authorizationStatus(for: mediaType)
        return updatedStatus == .notDetermined ? .denied : updatedStatus
    }
}
