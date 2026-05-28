import Foundation
import SwiftUI
import UIKit

struct SampleBootstrapView: View {
    @ObservedObject var launchCoordinator: SampleLaunchCoordinator
    let bridgeInstaller: MediaSFUBridgeInstaller
    let permissionsCoordinator: MediaPermissionsCoordinator
    let replayKitCoordinator: ReplayKitCoordinator
    private let hostAdapter: any MediaSFUSDKHostAdapter = MediaSFUSDKHostFactory.makeAdapter()
    @State private var didApplyAutomationOverrides = false
    @State private var didHandleAutomationLaunch = false
    @State private var showAdvancedOptions = false

    var body: some View {
        Group {
            if #available(iOS 16.0, *) {
                NavigationStack {
                    formContent
                }
            } else {
                NavigationView {
                    formContent
                }
                .navigationViewStyle(.stack)
            }
        }
    }

    private var formContent: some View {
        Form {
            Section("Meeting") {
                Picker("Action", selection: binding(\.action)) {
                    ForEach(SampleSessionAction.allCases) { action in
                        Text(action.rawValue.capitalized).tag(action)
                    }
                }
                .pickerStyle(.segmented)
                .accessibilityIdentifier("sessionActionPicker")

                TextField("Display Name", text: binding(\.userName))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .accessibilityIdentifier("displayNameField")

                if launchCoordinator.sessionConfig.action == .create {
                    Picker("Event Type", selection: binding(\.eventType)) {
                        ForEach(SampleEventType.allCases) { eventType in
                            Text(eventType.rawValue.capitalized).tag(eventType)
                        }
                    }
                    .accessibilityIdentifier("eventTypePicker")

                    TextField("Duration (minutes)", value: binding(\.durationMinutes), format: .number)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("durationMinutesField")

                    TextField("Capacity", value: binding(\.capacity), format: .number)
                        .keyboardType(.numberPad)
                        .accessibilityIdentifier("capacityField")
                } else {
                    TextField("Meeting ID", text: binding(\.roomName))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .accessibilityIdentifier("roomNameField")
                }

                if let validationMessage = launchCoordinator.sessionConfig.prejoinValidationMessage {
                    Text(validationMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .accessibilityIdentifier("prejoinValidationMessage")
                }
            }

            Section {
                Button(launchCoordinator.sessionConfig.action == .create ? "Create Meeting" : "Join Meeting") {
                    Task {
                        await launchMediaSFU()
                    }
                }
                .disabled(!launchCoordinator.sessionConfig.isPrejoinReady)
                .accessibilityIdentifier("launchMediaSfuButton")
            }

            Section {
                Button(showAdvancedOptions ? "Hide Advanced Options" : "Show Advanced Options") {
                    showAdvancedOptions.toggle()
                }
                .accessibilityIdentifier("advancedOptionsToggle")
            }

            if showAdvancedOptions {
                advancedConnectionSection
                advancedSessionSection
                runtimeSection
            }

            if let statusMessage = launchCoordinator.statusMessage {
                Section("Status") {
                    Text(statusMessage)
                        .accessibilityIdentifier("statusMessageLabel")
                }
            }
        }
        .navigationTitle("MediaSFU iOS Sample")
        .task {
            applyAutomationOverridesOnce()
            await performAutomationLaunchIfNeeded()
        }
    }

    private var advancedConnectionSection: some View {
        Section {
            Toggle("Use MediaSFU Cloud", isOn: binding(\.connectMediaSFU))
                .accessibilityIdentifier("useMediaSfuCloudToggle")
            TextField("API Username", text: binding(\.apiUserName))
                .accessibilityIdentifier("apiUsernameField")
            SecureField("API Key", text: binding(\.apiKey))
                .accessibilityIdentifier("apiKeyField")
            TextField("Local Link", text: binding(\.localLink))
                .accessibilityIdentifier("localLinkField")
        } header: {
            Text("Connection")
        } footer: {
            Text("Values entered here are saved on this device. You can also preload MEDIASFU_* entries from mediasfu_sample_env.txt in the app Documents folder.")
        }
    }

    private var advancedSessionSection: some View {
        Section("Advanced Session") {
            if launchCoordinator.sessionConfig.action == .create {
                TextField("Scheduled Date (ms epoch)", text: binding(\.scheduledDateMillis))
                    .keyboardType(.numberPad)
                    .accessibilityIdentifier("scheduledDateField")
                TextField("Secure Code", text: binding(\.secureCode))
                    .accessibilityIdentifier("secureCodeField")
                Toggle("Record Only", isOn: binding(\.recordOnly))
                    .accessibilityIdentifier("recordOnlyToggle")
                Toggle("Safe Room", isOn: binding(\.safeRoom))
                    .accessibilityIdentifier("safeRoomToggle")
                Toggle("Auto Start Safe Room", isOn: binding(\.autoStartSafeRoom))
                    .accessibilityIdentifier("autoStartSafeRoomToggle")
                Picker("Safe Room Action", selection: binding(\.safeRoomAction)) {
                    ForEach(SampleSafeRoomAction.allCases) { safeRoomAction in
                        Text(safeRoomAction.rawValue.capitalized).tag(safeRoomAction)
                    }
                }
                .accessibilityIdentifier("safeRoomActionPicker")
                Toggle("Data Buffer", isOn: binding(\.dataBuffer))
                    .accessibilityIdentifier("dataBufferToggle")
                Picker("Buffer Type", selection: binding(\.bufferType)) {
                    ForEach(SampleBufferType.allCases) { bufferType in
                        Text(bufferType.rawValue.capitalized).tag(bufferType)
                    }
                }
                .accessibilityIdentifier("bufferTypePicker")
            } else {
                SecureField("Admin Passcode", text: binding(\.adminPasscode))
                    .accessibilityIdentifier("adminPasscodeField")
                TextField("Access Level", text: binding(\.islevel))
                    .keyboardType(.numberPad)
                    .accessibilityIdentifier("accessLevelField")
            }
        }
    }

    private var runtimeSection: some View {
        Section("Runtime") {
            Button("Install Bridge") {
                launchCoordinator.updateStatus(bridgeInstaller.installIfNeeded())
            }
            Button("Describe KMP Host Integration") {
                launchCoordinator.updateStatus(hostAdapter.integrationStatus)
            }
            Button("Request Camera and Mic Permissions") {
                Task {
                    let result = await permissionsCoordinator.requestInitialPermissions()
                    launchCoordinator.updateStatus(result)
                }
            }
            Button("Check ReplayKit Availability") {
                launchCoordinator.updateStatus(replayKitCoordinator.describeAvailability())
            }
        }
    }

    private func binding<Value>(_ keyPath: WritableKeyPath<SampleSessionConfig, Value>) -> Binding<Value> {
        Binding(
            get: { launchCoordinator.sessionConfig[keyPath: keyPath] },
            set: { launchCoordinator.sessionConfig[keyPath: keyPath] = $0 }
        )
    }

    @MainActor
    private func applyAutomationOverridesOnce() {
        guard !didApplyAutomationOverrides else { return }

        didApplyAutomationOverrides = true
        applyAutomationOverridesIfNeeded()
    }

    @MainActor
    private func performAutomationLaunchIfNeeded() async {
        NSLog("MediaSFU - performAutomationLaunchIfNeeded: didHandle=%d autoLaunch=%d", didHandleAutomationLaunch ? 1 : 0, SampleAppEnvironment.automationFlag(named: "MEDIASFU_AUTO_LAUNCH") ? 1 : 0)
        guard !didHandleAutomationLaunch else { return }
        guard SampleAppEnvironment.shouldAutoLaunchFromBootstrap
        else {
            NSLog("MediaSFU - performAutomationLaunchIfNeeded: no auto-launch flag, skipping")
            return
        }

        didHandleAutomationLaunch = true
        await launchMediaSFU()
    }

    @MainActor
    private func launchMediaSFU() async {
        NSLog("MediaSFU - launchMediaSFU: entered")
        applyAutomationOverridesIfNeeded()

        if let validationMessage = launchCoordinator.sessionConfig.prejoinValidationMessage {
            NSLog("MediaSFU - launchMediaSFU: early return (validation) -> %@", validationMessage)
            launchCoordinator.updateStatus(validationMessage)
            return
        }

        NSLog("MediaSFU - launchMediaSFU: validation passed, shouldRequestPerms=%d", shouldRequestMediaPermissionsBeforeLaunch ? 1 : 0)
        var launchStatusMessages: [String] = []
        if shouldRequestMediaPermissionsBeforeLaunch {
            let permissionSnapshot = await permissionsCoordinator.ensureLaunchPermissions()
            if let launchBlockMessage = permissionSnapshot.launchBlockMessage {
                NSLog("MediaSFU - launchMediaSFU: early return (permissions) -> %@", launchBlockMessage)
                launchCoordinator.updateStatus(launchBlockMessage)
                return
            }
            launchStatusMessages.append(permissionSnapshot.statusMessage)
        }

        let installStatus = bridgeInstaller.installIfNeeded()
        NSLog("MediaSFU - launchMediaSFU: bridge install status -> '%@'", installStatus)
        if !installStatus.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            launchStatusMessages.insert(installStatus, at: 0)
        }

        guard let presenter = topMostPresenter() else {
            NSLog("MediaSFU - launchMediaSFU: early return (no presenter)")
            launchCoordinator.updateStatus("Unable to locate a UIKit presenter for MediaSFU.")
            return
        }

        NSLog("MediaSFU - launchMediaSFU: presenter found -> %@", String(describing: type(of: presenter)))
        if presenter is MediaSFUHostViewController {
            NSLog("MediaSFU - launchMediaSFU: early return (already open)")
            launchCoordinator.updateStatus("MediaSFU is already open.")
            return
        }

        var presentedController: UIViewController?
        let hostViewController = MediaSFUHostViewController(
            sessionConfig: launchCoordinator.sessionConfig,
            hostAdapter: hostAdapter,
            onClose: {
                presentedController?.dismiss(animated: true)
                launchCoordinator.closeSession()
            }
        )
        hostViewController.modalPresentationStyle = .fullScreen
        presentedController = hostViewController

        NSLog("MediaSFU - launchMediaSFU: calling presenter.present")
        launchCoordinator.launch(statusMessage: launchStatusMessages.isEmpty ? nil : launchStatusMessages.joined(separator: "\n"))
        presenter.present(hostViewController, animated: true)
        NSLog("MediaSFU - launchMediaSFU: presenter.present called")
    }

    private var shouldRequestMediaPermissionsBeforeLaunch: Bool {
        SampleAppEnvironment.shouldRequestMediaPermissionsBeforeLaunch
    }

    @MainActor
    private func topMostPresenter() -> UIViewController? {
        let rootController = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: \.isKeyWindow)?
            .rootViewController

        guard let rootController else {
            return nil
        }

        var topController = rootController
        while let presentedController = topController.presentedViewController {
            topController = presentedController
        }
        return topController
    }

    @MainActor
    private func applyAutomationOverridesIfNeeded() {
        launchCoordinator.sessionConfig = SampleAppEnvironment.resolvedSessionConfig(from: launchCoordinator.sessionConfig)
    }

    private func automationFlag(named name: String) -> Bool {
        SampleAppEnvironment.automationFlag(named: name)
    }

    private func automationArgument(named name: String) -> Bool {
        SampleAppEnvironment.automationArgument(named: name)
    }
}

enum SampleAppEnvironment {
    static func resolvedSessionConfig(from base: SampleSessionConfig) -> SampleSessionConfig {
        var config = base
        let fileOverrides = loadBootstrapFileOverrides()

        if let value = configuredValue(named: "MEDIASFU_API_USERNAME", fileOverrides: fileOverrides) {
            config.apiUserName = value
        }

        if let value = configuredValue(named: "MEDIASFU_API_KEY", fileOverrides: fileOverrides) {
            config.apiKey = value
        }

        if let value = configuredValue(named: "MEDIASFU_LOCAL_LINK", fileOverrides: fileOverrides) {
            config.localLink = value
        }

        if let value = configuredValue(named: "MEDIASFU_USER_NAME", fileOverrides: fileOverrides) {
            config.userName = value
        }

        if let value = configuredValue(named: "MEDIASFU_ROOM_NAME", fileOverrides: fileOverrides) {
            config.roomName = value
        }

        if let value = configuredValue(named: "MEDIASFU_ACTION", fileOverrides: fileOverrides)?.lowercased(),
           let action = SampleSessionAction(rawValue: value) {
            config.action = action
        }

        if let value = configuredValue(named: "MEDIASFU_CONNECT_MEDIA_SFU", fileOverrides: fileOverrides),
           let parsed = parseAutomationBool(value) {
            config.connectMediaSFU = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_DURATION_MINUTES", fileOverrides: fileOverrides),
           let parsed = Int(value) {
            config.durationMinutes = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_CAPACITY", fileOverrides: fileOverrides),
           let parsed = Int(value) {
            config.capacity = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_EVENT_TYPE", fileOverrides: fileOverrides)?.lowercased(),
           let eventType = SampleEventType(rawValue: value) {
            config.eventType = eventType
        }

        if let value = configuredValue(named: "MEDIASFU_SCHEDULED_DATE_MILLIS", fileOverrides: fileOverrides) {
            config.scheduledDateMillis = value
        }

        if let value = configuredValue(named: "MEDIASFU_SECURE_CODE", fileOverrides: fileOverrides) {
            config.secureCode = value
        }

        if let value = configuredValue(named: "MEDIASFU_RECORD_ONLY", fileOverrides: fileOverrides),
           let parsed = parseAutomationBool(value) {
            config.recordOnly = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_SAFE_ROOM", fileOverrides: fileOverrides),
           let parsed = parseAutomationBool(value) {
            config.safeRoom = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_AUTO_START_SAFE_ROOM", fileOverrides: fileOverrides),
           let parsed = parseAutomationBool(value) {
            config.autoStartSafeRoom = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_SAFE_ROOM_ACTION", fileOverrides: fileOverrides)?.lowercased(),
           let safeRoomAction = SampleSafeRoomAction(rawValue: value) {
            config.safeRoomAction = safeRoomAction
        }

        if let value = configuredValue(named: "MEDIASFU_DATA_BUFFER", fileOverrides: fileOverrides),
           let parsed = parseAutomationBool(value) {
            config.dataBuffer = parsed
        }

        if let value = configuredValue(named: "MEDIASFU_BUFFER_TYPE", fileOverrides: fileOverrides)?.lowercased(),
           let bufferType = SampleBufferType(rawValue: value) {
            config.bufferType = bufferType
        }

        if let value = configuredValue(named: "MEDIASFU_ADMIN_PASSCODE", fileOverrides: fileOverrides) {
            config.adminPasscode = value
        }

        if let value = configuredValue(named: "MEDIASFU_ISLEVEL", fileOverrides: fileOverrides) {
            config.islevel = value
        }

        return config
    }

    static func shouldShowBootstrapTools(for config: SampleSessionConfig) -> Bool {
        if let explicitOverride = explicitBootstrapToolsOverride {
            return explicitOverride
        }

        if shouldRequestMediaPermissionsBeforeLaunch {
            return true
        }

        return config.prejoinValidationMessage != nil
    }

    static var automationModeRequested: Bool {
        automationFlag(named: "MEDIASFU_AUTO_LAUNCH") ||
            automationArgument(named: "--mediasfu-auto-launch") ||
            automationFlag(named: "MEDIASFU_AUTO_PROCEED") ||
            automationArgument(named: "--mediasfu-auto-proceed") ||
            automationFlag(named: "MEDIASFU_REQUEST_PERMISSIONS_ON_LAUNCH") ||
            automationArgument(named: "--mediasfu-request-permissions-on-launch") ||
            automationFlag(named: "MEDIASFU_FORCE_VALIDATED_SESSION") ||
            automationArgument(named: "--mediasfu-force-validated-session") ||
            automationFlag(named: "MEDIASFU_ENABLE_RUNTIME_PROBES") ||
            automationArgument(named: "--mediasfu-enable-runtime-probes") ||
            automationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_AUDIO") ||
            automationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO") ||
            automationFlag(named: "MEDIASFU_PROBE_ENABLE_SCREENSHARE")
    }

    static var explicitBootstrapToolsOverride: Bool? {
        if automationArgument(named: "--mediasfu-show-bootstrap-tools") {
            return true
        }

        guard let rawValue = bootstrapOverrideValue(named: "MEDIASFU_SHOW_BOOTSTRAP_TOOLS") else {
            return nil
        }

        return parseAutomationBool(rawValue)
    }

    static var shouldAutoLaunchFromBootstrap: Bool {
        automationFlag(named: "MEDIASFU_AUTO_LAUNCH") ||
            automationArgument(named: "--mediasfu-auto-launch") ||
            automationFlag(named: "MEDIASFU_AUTO_PROCEED") ||
            automationArgument(named: "--mediasfu-auto-proceed")
    }

    static var shouldRequestMediaPermissionsBeforeLaunch: Bool {
        automationFlag(named: "MEDIASFU_REQUEST_PERMISSIONS_ON_LAUNCH") ||
            automationArgument(named: "--mediasfu-request-permissions-on-launch")
    }

    static func automationFlag(named name: String) -> Bool {
        guard let rawValue = bootstrapOverrideValue(named: name) else {
            return false
        }

        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        default:
            return false
        }
    }

    static func automationArgument(named name: String) -> Bool {
        ProcessInfo.processInfo.arguments.contains(name)
    }

    static func bootstrapOverrideValue(named name: String) -> String? {
        configuredValue(named: name, fileOverrides: loadBootstrapFileOverrides())
    }

    private static func configuredValue(named name: String, fileOverrides: [String: String]) -> String? {
        guard let rawValue = ProcessInfo.processInfo.environment[name] else {
            guard let fileValue = fileOverrides[name] else {
                return nil
            }

            let trimmedFileValue = fileValue.trimmingCharacters(in: .whitespacesAndNewlines)
            return trimmedFileValue.isEmpty ? nil : trimmedFileValue
        }

        let trimmedValue = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmedValue.isEmpty ? nil : trimmedValue
    }

    private static func loadBootstrapFileOverrides() -> [String: String] {
        for fileURL in bootstrapOverrideFileURLs() {
            if let overrides = parseBootstrapOverrideFile(at: fileURL), !overrides.isEmpty {
                return overrides
            }
        }

        return [:]
    }

    private static func bootstrapOverrideFileURLs() -> [URL] {
        var candidateURLs: [URL] = []

        if let explicitPath = ProcessInfo.processInfo.environment["MEDIASFU_BOOTSTRAP_ENV_FILE"]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !explicitPath.isEmpty {
            candidateURLs.append(URL(fileURLWithPath: explicitPath))
        }

        if let explicitCredsPath = ProcessInfo.processInfo.environment["MEDIASFU_CREDS_FILE"]?.trimmingCharacters(in: .whitespacesAndNewlines),
           !explicitCredsPath.isEmpty {
            candidateURLs.append(URL(fileURLWithPath: explicitCredsPath))
        }

        if let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first {
            candidateURLs.append(documentsDirectory.appendingPathComponent("mediasfu_sample_env.txt"))
            candidateURLs.append(documentsDirectory.appendingPathComponent("mediasfu_ui_test_env.txt"))
        }

        candidateURLs.append(URL(fileURLWithPath: "/tmp/mediasfu_sample_env.txt"))
        candidateURLs.append(URL(fileURLWithPath: "/tmp/mediasfu_ui_test_env.txt"))
        candidateURLs.append(URL(fileURLWithPath: "/tmp/mediasfu_creds.txt"))

        var seenPaths = Set<String>()
        return candidateURLs.filter { fileURL in
            seenPaths.insert(fileURL.path).inserted
        }
    }

    private static func parseBootstrapOverrideFile(at fileURL: URL) -> [String: String]? {
        guard let contents = try? String(contentsOf: fileURL, encoding: .utf8) else {
            return nil
        }

        var overrides: [String: String] = [:]
        contents.enumerateLines { rawLine, _ in
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty, !line.hasPrefix("#") else {
                return
            }

            let normalizedLine: String
            if line.hasPrefix("export ") {
                normalizedLine = String(line.dropFirst("export ".count))
            } else {
                normalizedLine = line
            }

            guard let separatorIndex = normalizedLine.firstIndex(of: "=") else {
                return
            }

            let key = normalizedLine[..<separatorIndex].trimmingCharacters(in: .whitespacesAndNewlines)
            guard !key.isEmpty else {
                return
            }

            var value = normalizedLine[normalizedLine.index(after: separatorIndex)...]
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if (value.hasPrefix("\"") && value.hasSuffix("\"")) ||
                (value.hasPrefix("'") && value.hasSuffix("'")) {
                value = String(value.dropFirst().dropLast())
            }
            overrides[key] = value
        }

        return overrides
    }

    private static func parseAutomationBool(_ rawValue: String) -> Bool? {
        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        case "0", "false", "no", "off":
            return false
        default:
            return nil
        }
    }
}