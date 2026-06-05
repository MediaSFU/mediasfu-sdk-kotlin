import UIKit

#if canImport(shared)
import shared
#elseif canImport(MediaSFUSDK)
import MediaSFUSDK
#endif

#if canImport(shared)
private let mediaSFUKmpModuleName = "shared"
#elseif canImport(MediaSFUSDK)
private let mediaSFUKmpModuleName = "MediaSFUSDK"
#endif

/// Real framework-backed host adapter.
///
/// The shared KMP module now exports an explicit Swift-facing iOS host bridge via
/// `MediaSFUIosHostBridge`, so the sample app no longer needs to guess at the UIKit
/// mounting surface for the shared Compose UI. If Xcode surfaces the generated symbols
/// with different names, use `ios-sample-app/API_DISCOVERY_CHECKLIST.md` to capture the
/// actual names and adapt this file accordingly.
struct RealMediaSFUSDKHostAdapter: MediaSFUSDKHostAdapter {
    var integrationStatus: String {
        #if canImport(MediaSFUSDK) || canImport(shared)
        return "Using exported MediaSFU iOS host bridge from the \(mediaSFUKmpModuleName) module."
        #else
        return "The generated KMP module is not yet importable in this target. Finish the CocoaPods/Xcode setup first; the adapter will switch to the real shared host automatically once the module links."
        #endif
    }

    func makeHostedViewController(context: MediaSFUSDKHostContext) -> UIViewController {
        #if canImport(MediaSFUSDK) || canImport(shared)
        return makeFrameworkBackedViewController(context: context)
        #else
        return makeUnavailableViewController(message: integrationStatus)
        #endif
    }
}

private extension RealMediaSFUSDKHostAdapter {
    #if canImport(MediaSFUSDK) || canImport(shared)
    func makeFrameworkBackedViewController(context: MediaSFUSDKHostContext) -> UIViewController {
        NSLog("MediaSFU - host adapter module -> %@", mediaSFUKmpModuleName)
        let hostBridge = MediaSFUIosHostBridge()
        let launchConfig = hostBridge.makeLaunchConfig()
        launchConfig.apiUserName = context.sessionConfig.normalizedApiUserName
        launchConfig.apiKey = context.sessionConfig.normalizedApiKey
        launchConfig.localLink = context.sessionConfig.normalizedLocalLink
        launchConfig.userName = context.sessionConfig.normalizedUserName
        launchConfig.roomName = context.sessionConfig.normalizedRoomName
        launchConfig.connectMediaSFU = context.sessionConfig.connectMediaSFU
        launchConfig.action = context.sessionConfig.action.rawValue
        launchConfig.durationMinutes = Int32(context.sessionConfig.durationMinutes)
        launchConfig.capacity = Int32(context.sessionConfig.capacity)
        launchConfig.eventType = context.sessionConfig.eventType.rawValue
        launchConfig.scheduledDate = Int64(context.sessionConfig.normalizedScheduledDateMillis) ?? 0
        launchConfig.secureCode = context.sessionConfig.normalizedSecureCode
        launchConfig.recordOnly = context.sessionConfig.recordOnly
        launchConfig.safeRoom = context.sessionConfig.safeRoom
        launchConfig.autoStartSafeRoom = context.sessionConfig.autoStartSafeRoom
        launchConfig.safeRoomAction = context.sessionConfig.safeRoomAction.rawValue
        launchConfig.dataBuffer = context.sessionConfig.dataBuffer
        launchConfig.bufferType = context.sessionConfig.bufferType.rawValue
        launchConfig.adminPasscode = context.sessionConfig.normalizedAdminPasscode
        launchConfig.islevel = context.sessionConfig.normalizedIslevel
        let forceValidatedSession = automationFlag(named: "MEDIASFU_FORCE_VALIDATED_SESSION") ||
            automationArgument(named: "--mediasfu-force-validated-session")
        let probeReturnUI = automationFlag(named: "MEDIASFU_PROBE_RETURN_UI")
        let probeApplyBackground = automationFlag(named: "MEDIASFU_PROBE_APPLY_BACKGROUND")
        let explicitAutoProceed = automationBooleanOverride(named: "MEDIASFU_AUTO_PROCEED")
        // autoProceed=true only when the Swift form has already gathered and validated
        // everything (automation / probe runs). For a normal user launch the config
        // arrives blank (empty userName) so we let the KMP Compose pre-join — the
        // standard MediaSFU branded form with logo, identical to React/Flutter — handle
        // the pre-join interaction (returnUI=true). When the Swift form was used
        // (isPrejoinReady=true) we skip the KMP pre-join and auto-join directly.
        let autoProceed = probeReturnUI ? false : (explicitAutoProceed ?? context.sessionConfig.isPrejoinReady)
        let enableRuntimeProbes = automationFlag(named: "MEDIASFU_ENABLE_RUNTIME_PROBES") ||
            automationArgument(named: "--mediasfu-enable-runtime-probes")
        let probeEnableLocalAudio = automationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_AUDIO")
        let probeEnableLocalVideo = automationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO")
        let probeEnableScreenShare = automationFlag(named: "MEDIASFU_PROBE_ENABLE_SCREENSHARE")
        let includeControlProbes = forceValidatedSession || probeReturnUI || probeApplyBackground
        launchConfig.autoProceed = autoProceed
        setOptionalBooleanLaunchConfigValue(
            launchConfig,
            key: "forceValidatedSession",
            setterName: "setForceValidatedSession:",
            value: forceValidatedSession
        )
        let hostedViewController = hostBridge.makeHostViewController(config: launchConfig)

        return FrameworkBackedMediaSFUHostShellViewController(
            hostedViewController: hostedViewController,
            includeControlProbes: includeControlProbes,
            enableRuntimeProbes: enableRuntimeProbes,
            probeEnableLocalAudio: probeEnableLocalAudio,
            probeEnableLocalVideo: probeEnableLocalVideo,
            probeEnableScreenShare: probeEnableScreenShare,
            bridgeInstallSummary: MediaSFUBridgeInstallStatus.latestSummary,
            runtimeProbeHostBridge: hostBridge,
            launchMarker: automationValue(named: "MEDIASFU_PROBE_RUN_ID") ?? context.sessionConfig.normalizedRoomName
        )
    }

    func setOptionalBooleanLaunchConfigValue(_ launchConfig: AnyObject, key: String, setterName: String, value: Bool) {
        guard let object = launchConfig as? NSObject,
              object.responds(to: NSSelectorFromString(setterName)) else {
            return
        }

        object.setValue(value, forKey: key)
    }
    #endif

    func makeUnavailableViewController(message: String) -> UIViewController {
        let controller = UIViewController()
        controller.view.backgroundColor = .systemBackground

        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.numberOfLines = 0
        label.textAlignment = .center
        label.text = message

        controller.view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor, constant: 24),
            label.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor, constant: -24)
        ])

        return controller
    }

    func automationFlag(named name: String) -> Bool {
        guard let rawValue = SampleAppEnvironment.bootstrapOverrideValue(named: name) else {
            return false
        }

        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        default:
            return false
        }
    }

    func automationArgument(named name: String) -> Bool {
        ProcessInfo.processInfo.arguments.contains(name)
    }

    func automationBooleanOverride(named name: String) -> Bool? {
        guard let rawValue = SampleAppEnvironment.bootstrapOverrideValue(named: name) else {
            return nil
        }

        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        case "0", "false", "no", "off":
            return false
        default:
            return nil
        }
    }

    func automationValue(named name: String) -> String? {
        SampleAppEnvironment.bootstrapOverrideValue(named: name)
    }
}

private final class FrameworkBackedMediaSFUHostShellViewController: UIViewController {
    private let hostedViewController: UIViewController
    private let includeControlProbes: Bool
    private let enableRuntimeProbes: Bool
    private let probeEnableLocalAudio: Bool
    private let probeEnableLocalVideo: Bool
    private let probeEnableScreenShare: Bool
    private let bridgeInstallSummary: String
    private let runtimeProbeHostBridge: AnyObject?
    private var runtimeProbeTimer: Timer?
    private var scheduledVideoToggleWorkItem: DispatchWorkItem?
    private weak var runtimeProbeElement: UIView?
    private var audioToggleLastRequestedAt: Date?
    private var audioToggleFirstRequestedAt: Date?
    private var videoToggleLastRequestedAt: Date?
    private var screenshareToggleLastRequestedAt: Date?
    private var localMediaPrimingReadySince: Date?
    private var lastLoggedRuntimeProbeSummary: String?
    private var localPrimingDebugState: String
    private let launchMarker: String

    init(hostedViewController: UIViewController, includeControlProbes: Bool, enableRuntimeProbes: Bool, probeEnableLocalAudio: Bool, probeEnableLocalVideo: Bool, probeEnableScreenShare: Bool, bridgeInstallSummary: String, runtimeProbeHostBridge: AnyObject?, launchMarker: String) {
        self.hostedViewController = hostedViewController
        self.includeControlProbes = includeControlProbes
        self.enableRuntimeProbes = enableRuntimeProbes
        self.probeEnableLocalAudio = probeEnableLocalAudio
        self.probeEnableLocalVideo = probeEnableLocalVideo
        self.probeEnableScreenShare = probeEnableScreenShare
        self.bridgeInstallSummary = bridgeInstallSummary
        self.runtimeProbeHostBridge = runtimeProbeHostBridge
        self.localPrimingDebugState = "flags-a\(probeEnableLocalAudio ? 1 : 0)-v\(probeEnableLocalVideo ? 1 : 0)-s\(probeEnableScreenShare ? 1 : 0)"
        let sanitizedLaunchMarker = launchMarker
            .replacingOccurrences(of: ";", with: ",")
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        self.launchMarker = sanitizedLaunchMarker.isEmpty ? "callbackprobe-20260521c" : sanitizedLaunchMarker
        super.init(nibName: nil, bundle: nil)
    }

    deinit {
        NSLog("MediaSFU - FrameworkBackedMediaSFUHostShellViewController deinit called")
        persistLatestRuntimeProbeSnapshot()
        scheduledVideoToggleWorkItem?.cancel()
        runtimeProbeTimer?.invalidate()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        view.insetsLayoutMarginsFromSafeArea = false
        view.directionalLayoutMargins = .zero

        addChild(hostedViewController)
        hostedViewController.modalPresentationStyle = .fullScreen
        hostedViewController.modalPresentationCapturesStatusBarAppearance = true
        hostedViewController.view.translatesAutoresizingMaskIntoConstraints = false
        hostedViewController.view.backgroundColor = .black
        hostedViewController.view.insetsLayoutMarginsFromSafeArea = false
        hostedViewController.view.directionalLayoutMargins = .zero
        view.addSubview(hostedViewController.view)

        var constraints = [
            hostedViewController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostedViewController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hostedViewController.view.topAnchor.constraint(equalTo: view.topAnchor),
            hostedViewController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ]

        NSLayoutConstraint.activate(constraints)

        if includeControlProbes {
            addControlAccessibilityProbes()
        }

        if enableRuntimeProbes || probeEnableLocalAudio || probeEnableLocalVideo || probeEnableScreenShare {
            addRuntimeProbeElement()
        }

        if enableRuntimeProbes {
            self.runtimeProbeElement?.accessibilityValue = defaultRuntimeProbeSummary
        }

        if enableRuntimeProbes || probeEnableLocalAudio || probeEnableLocalVideo || probeEnableScreenShare {
            let bootstrapSummary = appendBridgeInstallSummary(
                to: "seq=-1;launchMarker=\(launchMarker);primingDebug=booting"
            )
            self.runtimeProbeElement?.accessibilityValue = bootstrapSummary
            logRuntimeProbeSummaryIfNeeded(bootstrapSummary)
        }

        if enableRuntimeProbes || probeEnableLocalAudio || probeEnableLocalVideo || probeEnableScreenShare {
            startRuntimeProbeUpdates()
        }

        hostedViewController.didMove(toParent: self)
    }

    override var prefersStatusBarHidden: Bool {
        true
    }

    override var prefersHomeIndicatorAutoHidden: Bool {
        true
    }

    override var childForStatusBarHidden: UIViewController? {
        hostedViewController
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        persistLatestRuntimeProbeSnapshot()
    }

    private func addControlAccessibilityProbes() {
        let probeContainer = UIStackView()
        probeContainer.translatesAutoresizingMaskIntoConstraints = false
        probeContainer.axis = .horizontal
        probeContainer.spacing = 1
        probeContainer.alpha = 0.02
        probeContainer.accessibilityIdentifier = "mediaSfuControlProbeContainer"

        let controlProbes: [(String, String, Selector?)] = [
            ("mediaSfuControlButton_audio", "Mute", #selector(handleControlProbeAudio)),
            ("mediaSfuControlButton_video", "Video", #selector(handleControlProbeVideo)),
            ("mediaSfuControlButton_screen_share", "Share", nil),
            ("mediaSfuControlButton_hang_up", "Hang Up", nil)
        ]

        controlProbes.forEach { identifier, label, action in
            let probe = UIButton(type: .custom)
            probe.translatesAutoresizingMaskIntoConstraints = false
            probe.isAccessibilityElement = true
            probe.accessibilityIdentifier = identifier
            probe.accessibilityLabel = label
            NSLayoutConstraint.activate([
                probe.widthAnchor.constraint(equalToConstant: 2),
                probe.heightAnchor.constraint(equalToConstant: 2)
            ])
            if let action {
                probe.addTarget(self, action: action, for: .touchUpInside)
            }
            probeContainer.addArrangedSubview(probe)
        }

        view.addSubview(probeContainer)
        NSLayoutConstraint.activate([
            probeContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 1),
            probeContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -1)
        ])

        // Navigation probe UIButtons live in a SEPARATE container with alpha > 0.01 so
        // UIKit's hitTest can reach them (hitTest returns nil for views with alpha <= 0.01).
        // These tiny 2×2-pt buttons are visually invisible but tappable from XCUITest to
        // bypass Compose LazyColumn touch interception and trigger modals via the Kotlin bridge.
        let navProbeContainer = UIStackView()
        navProbeContainer.translatesAutoresizingMaskIntoConstraints = false
        navProbeContainer.axis = .horizontal
        navProbeContainer.spacing = 1
        navProbeContainer.alpha = 0.02   // above UIKit 0.01 threshold, invisible to users

        let navProbes: [(String, String, Selector)] = [
            ("mediaSfuNavProbeButton_media_settings",    "Show Media Settings",    #selector(handleNavProbeMediaSettings)),
            ("mediaSfuNavProbeButton_display_settings",  "Show Display Settings",  #selector(handleNavProbeDisplaySettings)),
            ("mediaSfuNavProbeButton_recording",         "Show Recording",          #selector(handleNavProbeRecording)),
            ("mediaSfuNavProbeButton_cohost",            "Show Co-Host",            #selector(handleNavProbeCoHost)),
            ("mediaSfuNavProbeButton_requests",          "Show Requests",           #selector(handleNavProbeRequests)),
            ("mediaSfuNavProbeButton_waiting",           "Show Waiting Room",       #selector(handleNavProbeWaiting)),
            ("mediaSfuNavProbeButton_confirm_exit",      "Show Confirm Exit",       #selector(handleNavProbeConfirmExit)),
            ("mediaSfuNavProbeButton_breakout_rooms",    "Show Breakout Rooms",     #selector(handleNavProbeBreakoutRooms)),
            ("mediaSfuNavProbeButton_background",        "Show Background",          #selector(handleNavProbeBackground)),
            ("mediaSfuNavProbeButton_polls",             "Show Polls",               #selector(handleNavProbePolls)),
            ("mediaSfuNavProbeButton_panelists",         "Show Panelists",           #selector(handleNavProbePanelists)),
            ("mediaSfuNavProbeButton_permissions",       "Show Permissions",         #selector(handleNavProbePermissions)),
            ("mediaSfuNavProbeButton_translation",       "Show Translation",         #selector(handleNavProbeTranslation)),
        ]
        navProbes.forEach { identifier, label, action in
            let button = UIButton(type: .custom)
            button.translatesAutoresizingMaskIntoConstraints = false
            button.isAccessibilityElement = true
            button.accessibilityIdentifier = identifier
            button.accessibilityLabel = label
            NSLayoutConstraint.activate([
                button.widthAnchor.constraint(equalToConstant: 2),
                button.heightAnchor.constraint(equalToConstant: 2)
            ])
            button.addTarget(self, action: action, for: .touchUpInside)
            navProbeContainer.addArrangedSubview(button)
        }

        view.addSubview(navProbeContainer)
        NSLayoutConstraint.activate([
            navProbeContainer.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 1),
            navProbeContainer.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -4)
        ])
    }

    @objc private func handleControlProbeAudio() { _ = performBridgeToggle(selectorName: "triggerToggleAudio") }
    @objc private func handleControlProbeVideo() { _ = performBridgeToggle(selectorName: "triggerToggleVideo") }

    @objc private func handleNavProbeMediaSettings()   { _ = performBridgeShowModal("media_settings") }
    @objc private func handleNavProbeDisplaySettings() { _ = performBridgeShowModal("display_settings") }
    @objc private func handleNavProbeRecording()       { _ = performBridgeShowModal("recording") }
    @objc private func handleNavProbeCoHost()          { _ = performBridgeShowModal("cohost") }
    @objc private func handleNavProbeRequests()        { _ = performBridgeShowModal("requests") }
    @objc private func handleNavProbeWaiting()         { _ = performBridgeShowModal("waiting") }
    @objc private func handleNavProbeConfirmExit()     { _ = performBridgeShowModal("confirm_exit") }
    @objc private func handleNavProbeBreakoutRooms()   { _ = performBridgeShowModal("breakout_rooms") }
    @objc private func handleNavProbeBackground()      { _ = performBridgeShowModal("background") }
    @objc private func handleNavProbePolls()           { _ = performBridgeShowModal("polls") }
    @objc private func handleNavProbePanelists()       { _ = performBridgeShowModal("panelists") }
    @objc private func handleNavProbePermissions()     { _ = performBridgeShowModal("permissions") }
    @objc private func handleNavProbeTranslation()     { _ = performBridgeShowModal("translation") }

    private func performBridgeShowModal(_ name: String) -> Bool {
        #if canImport(MediaSFUSDK) || canImport(shared)
        if let typedBridge = runtimeProbeHostBridge as? MediaSFUIosHostBridge {
            return typedBridge.triggerShowModal(name: name)
        }
        #endif
        return false
    }

    private func addRuntimeProbeElement() {
        let probe = UIView()
        probe.translatesAutoresizingMaskIntoConstraints = false
        probe.isAccessibilityElement = true
        probe.accessibilityIdentifier = "mediaSfuRuntimeProbe"
        probe.alpha = 0.01
        runtimeProbeElement = probe

        view.addSubview(probe)
        NSLayoutConstraint.activate([
            probe.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 1),
            probe.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -3),
            probe.widthAnchor.constraint(equalToConstant: 1),
            probe.heightAnchor.constraint(equalToConstant: 1)
        ])
    }

    private func startRuntimeProbeUpdates() {
        NSLog("MediaSFU - startRuntimeProbeUpdates: enableRTProbes=%d probeAudio=%d probeVideo=%d probeElement=%@",
              enableRuntimeProbes, probeEnableLocalAudio, probeEnableLocalVideo,
              runtimeProbeElement != nil ? "non-nil" : "nil")
        updateRuntimeProbeLabel()
        runtimeProbeTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            if self == nil {
                NSLog("MediaSFU - timer closure: self is nil")
            }
            self?.updateRuntimeProbeLabel()
        }
        NSLog("MediaSFU - timer scheduled: %@", runtimeProbeTimer?.description ?? "nil")
    }

    private var timerDbgTick = 0

    private func updateRuntimeProbeLabel() {
        timerDbgTick += 1
        if timerDbgTick == 1 || timerDbgTick % 10 == 0 {
            NSLog("MediaSFU - probe timer tick #%d, probeElement=%@, primingState=%@",
                  timerDbgTick,
                  runtimeProbeElement != nil ? "non-nil" : "nil",
                  localPrimingDebugState)
        }
        guard let runtimeProbeElement else {
            return
        }

        let currentSummary = latestRuntimeProbeSummary() ?? runtimeProbeElement.accessibilityValue ?? defaultRuntimeProbeSummary
        primeLocalMediaIfNeeded(currentSummary: currentSummary)
        let refreshedSummary = latestRuntimeProbeSummary() ?? currentSummary
        let summary = appendBridgeInstallSummary(to: appendBackgroundProbeSummary(to: refreshedSummary))
        runtimeProbeElement.accessibilityValue = summary
        logRuntimeProbeSummaryIfNeeded(summary)
    }

    private func appendBridgeInstallSummary(to summary: String) -> String {
        var combinedSummary = summary
        if !combinedSummary.contains("launchMarker=") {
            combinedSummary += ";launchMarker=\(launchMarker)"
        }

        if !localPrimingDebugState.isEmpty {
            let newEntry = ";primingDebug=\(compactProbeValue(localPrimingDebugState))"
            if let range = combinedSummary.range(of: ";primingDebug=[^;]*", options: .regularExpression) {
                combinedSummary.replaceSubrange(range, with: newEntry)
            } else {
                combinedSummary += newEntry
            }
        }

        let trimmedSummary = bridgeInstallSummary.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedSummary.isEmpty, !combinedSummary.contains("bridgeInstall=") else {
            return combinedSummary
        }

        return "\(combinedSummary);bridgeInstall=\(compactProbeValue(trimmedSummary).prefix(260))"
    }

    private func appendBackgroundProbeSummary(to summary: String) -> String {
        guard let backgroundSummary = latestBackgroundProbeSummary(), !backgroundSummary.isEmpty else {
            return summary
        }

        var combinedSummary = summary
        backgroundSummary
            .split(separator: ";")
            .compactMap { entry -> (String, String)? in
                guard let separatorIndex = entry.firstIndex(of: "=") else {
                    return nil
                }

                let key = String(entry[..<separatorIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
                let value = String(entry[entry.index(after: separatorIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
                guard !key.isEmpty else {
                    return nil
                }
                return (key, value)
            }
            .forEach { key, value in
                let fieldPattern = ";\(NSRegularExpression.escapedPattern(for: key))=[^;]*"
                let replacement = ";\(key)=\(compactProbeValue(value))"
                if let range = combinedSummary.range(of: fieldPattern, options: .regularExpression) {
                    combinedSummary.replaceSubrange(range, with: replacement)
                } else {
                    combinedSummary += replacement
                }
            }

        return combinedSummary
    }

    private var defaultRuntimeProbeSummary: String {
        "seq=0;participants=0;visibleStreams=0;audioOnlyStreams=0;localAudio=false;localVideo=false;alertType=;alert="
    }

    private func latestRuntimeProbeSummary() -> String? {
        guard let bridge = runtimeProbeHostBridge as? NSObject else {
            return nil
        }

        let selector = NSSelectorFromString("latestRuntimeProbeSummary")
        guard bridge.responds(to: selector) else {
            return nil
        }

        return bridge.perform(selector)?.takeUnretainedValue() as? String
    }

    private func latestBackgroundProbeSummary() -> String? {
        guard let bridge = runtimeProbeHostBridge as? NSObject else {
            return nil
        }

        let selector = NSSelectorFromString("latestBackgroundProbeSummary")
        guard bridge.responds(to: selector) else {
            return nil
        }

        return bridge.perform(selector)?.takeUnretainedValue() as? String
    }

    private func primeLocalMediaIfNeeded(currentSummary: String) {
        guard isReadyToPrimeLocalMedia(currentSummary: currentSummary) else {
            localPrimingDebugState = "not-ready"
            audioToggleLastRequestedAt = nil
            videoToggleLastRequestedAt = nil
            return
        }

        if probeEnableLocalAudio {
            if currentSummary.contains("localAudio=true") {
                localPrimingDebugState = "audio-ready"
                audioToggleLastRequestedAt = nil
                audioToggleFirstRequestedAt = nil
            } else if shouldAttemptBridgeToggle(lastRequestedAt: audioToggleLastRequestedAt) {
                // Set rate-limit timestamp BEFORE background dispatch to prevent re-entrancy.
                let now = Date()
                audioToggleLastRequestedAt = now
                if audioToggleFirstRequestedAt == nil {
                    audioToggleFirstRequestedAt = now
                }
                localPrimingDebugState = "audio-dispatching"
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    guard let self else { return }
                    let success = self.performBridgeToggle(selectorName: "triggerToggleAudio")
                    DispatchQueue.main.async { [weak self] in
                        guard let self else { return }
                        if success {
                            self.localPrimingDebugState = "audio-dispatched"
                            self.attemptVideoToggleAfterAudioDispatch()
                            self.scheduleVideoToggleAfterAudioDispatch()
                        } else {
                            // Allow retry on next cycle.
                            self.audioToggleLastRequestedAt = nil
                            if self.audioToggleFirstRequestedAt == now {
                                self.audioToggleFirstRequestedAt = nil
                            }
                            self.localPrimingDebugState = "audio-bridge-false"
                        }
                    }
                }
                return

            } else {
                localPrimingDebugState = "audio-wait-rate-limit"
            }
        }

        if probeEnableLocalVideo {
            if currentSummary.contains("localVideo=true") {
                localPrimingDebugState = "video-ready"
                videoToggleLastRequestedAt = nil
            } else if !canPrimeVideoAfterAudioDispatch(currentSummary: currentSummary) {
                localPrimingDebugState = "video-wait-audio"
            } else if shouldAttemptBridgeToggle(lastRequestedAt: videoToggleLastRequestedAt) {
                videoToggleLastRequestedAt = Date()
                localPrimingDebugState = "video-dispatching"
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    guard let self else { return }
                    let success = self.performBridgeToggle(selectorName: "triggerToggleVideo")
                    DispatchQueue.main.async { [weak self] in
                        guard let self else { return }
                        if success {
                            self.localPrimingDebugState = self.immediateVideoToggleProbeState()
                        } else {
                            self.videoToggleLastRequestedAt = nil
                            self.localPrimingDebugState = "video-bridge-false"
                        }
                    }
                }
                return
            } else {
                localPrimingDebugState = "video-wait-rate-limit"
            }
        } else if !probeEnableLocalAudio && !probeEnableLocalVideo {
            localPrimingDebugState = "priming-disabled"
        }

        // Screenshare: note that on iOS this triggers the ReplayKit picker; user must confirm.
        // We still dispatch so the bridge path is exercised when the flag is set.
        if probeEnableScreenShare {
            if currentSummary.contains("localScreenShare=true") {
                localPrimingDebugState = "screenshare-ready"
                screenshareToggleLastRequestedAt = nil
            } else if shouldAttemptBridgeToggle(lastRequestedAt: screenshareToggleLastRequestedAt) {
                screenshareToggleLastRequestedAt = Date()
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    guard let self else { return }
                    let success = self.performBridgeToggle(selectorName: "triggerToggleScreenShare")
                    DispatchQueue.main.async { [weak self] in
                        guard let self else { return }
                        if success {
                            self.localPrimingDebugState = "screenshare-dispatched"
                        } else {
                            self.screenshareToggleLastRequestedAt = nil
                        }
                    }
                }
            }
        }
    }

    private func isReadyToPrimeLocalMedia(currentSummary: String) -> Bool {
        let sessionConnected = currentSummary.contains("alert=Connected to ") ||
            currentSummary.contains("lastSignalStage=rest-ok") ||
            currentSummary.contains("lastSignalStage=socket-ok") ||
            currentSummary.contains("lastSignalStage=join-ok") ||
            !currentSummary.contains("participants=0")

        guard sessionConnected else {
            localMediaPrimingReadySince = nil
            return false
        }

        if let readySince = localMediaPrimingReadySince {
            return Date().timeIntervalSince(readySince) >= 1.5
        }

        localMediaPrimingReadySince = Date()
        return false
    }

    private func shouldAttemptBridgeToggle(lastRequestedAt: Date?) -> Bool {
        guard let lastRequestedAt else {
            return true
        }

        return Date().timeIntervalSince(lastRequestedAt) >= 5
    }

    private func runtimeProbeCounterValue(in summary: String, key: String) -> Int? {
        let needle = "\(key)="
        guard let range = summary.range(of: needle) else {
            return nil
        }

        let suffix = summary[range.upperBound...]
        let digits = suffix.prefix { $0.isNumber }
        guard !digits.isEmpty else {
            return nil
        }

        return Int(digits)
    }

    private func runtimeProbeCounterAtLeast(in summary: String, key: String, minimum: Int) -> Bool {
        (runtimeProbeCounterValue(in: summary, key: key) ?? 0) >= minimum
    }

    private func canPrimeVideoAfterAudioDispatch(currentSummary: String) -> Bool {
        if !probeEnableLocalAudio {
            return true
        }

        // Wait until audio is actually produced (transport created + server ACK),
        // not just until getUserMedia succeeds. This prevents a race where both
        // audio and video call createSendTransport concurrently: audio creates
        // transport A and produces; video simultaneously creates transport B
        // (overwriting A) and its produce path fails.
        if runtimeProbeCounterAtLeast(in: currentSummary, key: "audioProduced", minimum: 1) ||
            runtimeProbeCounterAtLeast(in: currentSummary, key: "produceAckOk", minimum: 1) {
            return true
        }

        // Hard fallback: if audio was FIRST dispatched 8+ seconds ago and still
        // hasn't produced, allow video anyway so we don't block indefinitely.
        // Use firstAudioToggleAt (set only once) rather than lastAudioToggleAt
        // (reset every 5s on retry) so the clock doesn't keep resetting.
        guard let audioToggleFirstRequestedAt else {
            return false
        }

        return Date().timeIntervalSince(audioToggleFirstRequestedAt) >= 8.0
    }

    private func scheduleVideoToggleAfterAudioDispatch() {
        guard probeEnableLocalVideo else {
            return
        }

        scheduledVideoToggleWorkItem?.cancel()

        let workItem = DispatchWorkItem { [weak self] in
            guard let self else {
                return
            }

            self.scheduledVideoToggleWorkItem = nil
            self.attemptVideoToggleAfterAudioDispatch()
            let summaryToPersist = self.appendBridgeInstallSummary(
                to: self.latestRuntimeProbeSummary() ?? self.defaultRuntimeProbeSummary
            )
            self.logRuntimeProbeSummaryIfNeeded(summaryToPersist)
        }

        scheduledVideoToggleWorkItem = workItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5, execute: workItem)
    }

    private func attemptVideoToggleAfterAudioDispatch() {
        guard probeEnableLocalVideo else {
            return
        }

        let latestSummary = latestRuntimeProbeSummary() ?? defaultRuntimeProbeSummary
        if latestSummary.contains("localVideo=true") {
            localPrimingDebugState = "video-ready"
            return
        }

        // Enforce the same audio-produced guard so the scheduled timer path also
        // waits for audio transport to be fully established before dispatching video.
        guard canPrimeVideoAfterAudioDispatch(currentSummary: latestSummary) else {
            localPrimingDebugState = "video-wait-audio-produced"
            return
        }

        guard shouldAttemptBridgeToggle(lastRequestedAt: videoToggleLastRequestedAt) else {
            return
        }

        localPrimingDebugState = "video-inline-attempt"
        videoToggleLastRequestedAt = Date()
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            let success = self.performBridgeToggle(selectorName: "triggerToggleVideo")
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                if success {
                    self.localPrimingDebugState = self.immediateVideoToggleProbeState()
                } else {
                    self.videoToggleLastRequestedAt = nil
                    self.localPrimingDebugState = "video-bridge-false"
                }
            }
        }
    }

    private func immediateVideoToggleProbeState() -> String {
        guard let summary = latestRuntimeProbeSummary()?.lowercased() else {
            return "video-dispatched-summary-missing"
        }

        let hasVideoToggleStage = summary.contains("lastvideostage=toggle-dispatched") ||
            summary.contains("lastvideostage=toggle-running") ||
            summary.contains("lastvideostage=toggle-option-enter") ||
            summary.contains("lastvideostage=toggle-direct-before-click") ||
            summary.contains("lastvideostage=toggle-direct-after-click") ||
            summary.contains("lastvideostage=toggle-direct-exception")

        return hasVideoToggleStage ? "video-dispatched-probed" : "video-dispatched-no-probe"
    }

    private func performBridgeToggle(selectorName: String) -> Bool {
        if Thread.isMainThread {
            return performBridgeToggleOnCurrentThread(selectorName: selectorName)
        }

        var result = false
        DispatchQueue.main.sync { [weak self] in
            guard let self else { return }
            result = self.performBridgeToggleOnCurrentThread(selectorName: selectorName)
        }
        return result
    }

    private func performBridgeToggleOnCurrentThread(selectorName: String) -> Bool {
        #if canImport(MediaSFUSDK) || canImport(shared)
        if let typedBridge = runtimeProbeHostBridge as? MediaSFUIosHostBridge {
            switch selectorName {
            case "triggerToggleAudio":
                return typedBridge.triggerToggleAudio()
            case "triggerToggleVideo":
                return typedBridge.triggerToggleVideo()
            case "triggerToggleScreenShare":
                return typedBridge.triggerToggleScreenShare()
            default:
                break
            }
        }
        #endif

        guard let bridge = runtimeProbeHostBridge as? NSObject else {
            localPrimingDebugState = "\(selectorName)-bridge-missing"
            return false
        }

        let selector = NSSelectorFromString(selectorName)
        guard bridge.responds(to: selector) else {
            localPrimingDebugState = "\(selectorName)-selector-missing"
            return false
        }

        typealias BridgeToggleImplementation = @convention(c) (AnyObject, Selector) -> Bool
        let implementation = unsafeBitCast(bridge.method(for: selector), to: BridgeToggleImplementation.self)
        return implementation(bridge, selector)
    }

    private func compactProbeValue(_ value: String) -> String {
        value
            .replacingOccurrences(of: ";", with: ",")
            .replacingOccurrences(of: "\n", with: " ")
            .replacingOccurrences(of: "\r", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func logRuntimeProbeSummaryIfNeeded(_ summary: String) {
        guard enableRuntimeProbes, summary != lastLoggedRuntimeProbeSummary else {
            return
        }

        lastLoggedRuntimeProbeSummary = summary
        let message = "MEDIASFU_RUNTIME_PROBE \(summary)"
        NSLog("%@", message)
        if let data = (message + "\n").data(using: .utf8) {
            FileHandle.standardError.write(data)
        }
        persistRuntimeProbeSummary(summary)
    }

    private func persistRuntimeProbeSummary(_ summary: String) {
        guard let documentsDirectory = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else {
            return
        }

        let fileURL = documentsDirectory.appendingPathComponent("mediasfu_runtime_probe.txt")
        try? summary.write(to: fileURL, atomically: true, encoding: .utf8)
    }

    private func persistLatestRuntimeProbeSnapshot() {
        let latestSummary = latestRuntimeProbeSummary() ?? runtimeProbeElement?.accessibilityValue ?? defaultRuntimeProbeSummary
        let summary = appendBridgeInstallSummary(to: latestSummary)
        persistRuntimeProbeSummary(summary)
    }
}
