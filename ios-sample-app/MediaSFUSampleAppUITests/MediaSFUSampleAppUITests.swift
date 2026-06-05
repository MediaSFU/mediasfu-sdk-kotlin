import XCTest

final class MediaSFUSampleAppUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    override func tearDownWithError() throws {
        XCUIApplication().terminate()
    }

    func testBootstrapFormRendersCoreControls() throws {
        let app = XCUIApplication()
        app.launch()

        XCTAssertTrue(waitForElement(app.textFields["displayNameField"], in: app, timeout: 10, allowScroll: false))
        XCTAssertTrue(app.textFields["durationMinutesField"].exists)
        XCTAssertTrue(app.textFields["capacityField"].exists)
        XCTAssertTrue(waitForElement(app.buttons["advancedOptionsToggle"], in: app, timeout: 10))
        XCTAssertTrue(waitForElement(app.buttons["launchMediaSfuButton"], in: app, timeout: 10))

        openAdvancedOptions(in: app)
        XCTAssertTrue(waitForElement(app.textFields["apiUsernameField"], in: app, timeout: 10))
        XCTAssertTrue(app.secureTextFields["apiKeyField"].exists)
        XCTAssertTrue(waitForElement(app.textFields["secureCodeField"], in: app, timeout: 10))
    }

    func testSimplePrejoinValidatesDisplayName() throws {
        let app = XCUIApplication()
        app.launch()

        replaceText(in: app.textFields["displayNameField"], with: "a")

        let validationLabel = app.staticTexts["prejoinValidationMessage"]
        XCTAssertTrue(waitForElement(validationLabel, in: app, timeout: 10, allowScroll: false))
        XCTAssertTrue(validationLabel.label.contains("2 to 10"))
        XCTAssertFalse(app.buttons["launchMediaSfuButton"].isEnabled)
    }

    func testInstallBridgeDisplaysStatusMessage() throws {
        let app = XCUIApplication()
        app.launch()

        openAdvancedOptions(in: app)

        let installBridgeButton = app.buttons["Install Bridge"]
        XCTAssertTrue(waitForElement(installBridgeButton, in: app, timeout: 10))

        installBridgeButton.tap()

        let statusLabel = app.staticTexts["statusMessageLabel"]
        XCTAssertTrue(waitForElement(statusLabel, in: app, timeout: 10))
        XCTAssertFalse(statusLabel.label.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    func testReplayKitAvailabilityDisplaysStatusMessage() throws {
        let app = XCUIApplication()
        app.launch()

        openAdvancedOptions(in: app)

        let replayKitButton = app.buttons["Check ReplayKit Availability"]
        XCTAssertTrue(waitForElement(replayKitButton, in: app, timeout: 10))

        replayKitButton.tap()

        let statusLabel = app.staticTexts["statusMessageLabel"]
        XCTAssertTrue(waitForElement(statusLabel, in: app, timeout: 10))
        XCTAssertTrue(statusLabel.label.contains("ReplayKit"))
    }

    func testCreateAutomationOverridesPopulateAdvancedFields() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_SECURE_CODE"] = "24681357"
        app.launchEnvironment["MEDIASFU_RECORD_ONLY"] = "1"
        app.launchEnvironment["MEDIASFU_SAFE_ROOM"] = "true"
        app.launchEnvironment["MEDIASFU_AUTO_START_SAFE_ROOM"] = "yes"
        app.launchEnvironment["MEDIASFU_DATA_BUFFER"] = "on"
        app.launch()

        openAdvancedOptions(in: app)

        let secureCodeField = app.textFields["secureCodeField"]
        XCTAssertTrue(waitForElement(secureCodeField, in: app, timeout: 10))
        XCTAssertEqual(secureCodeField.value as? String, "24681357")
        XCTAssertEqual(app.switches["recordOnlyToggle"].value as? String, "1")
        XCTAssertEqual(app.switches["safeRoomToggle"].value as? String, "1")
        XCTAssertEqual(app.switches["autoStartSafeRoomToggle"].value as? String, "1")
    }

    func testJoinAutomationOverridesExposeJoinFields() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "joiner1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launchEnvironment["MEDIASFU_ISLEVEL"] = "1"
        app.launchEnvironment["MEDIASFU_ADMIN_PASSCODE"] = "secret-passcode"
        app.launch()

        let displayNameField = app.textFields["displayNameField"]
        XCTAssertTrue(waitForElement(displayNameField, in: app, timeout: 10, allowScroll: false))
        XCTAssertEqual(displayNameField.value as? String, "joiner1")
        XCTAssertEqual(app.textFields["roomNameField"].value as? String, "s12345678")

        openAdvancedOptions(in: app)
        XCTAssertTrue(waitForElement(app.secureTextFields["adminPasscodeField"], in: app, timeout: 10))

        let accessLevelField = app.textFields["accessLevelField"]
        XCTAssertTrue(waitForElement(accessLevelField, in: app, timeout: 10))
        XCTAssertEqual(accessLevelField.value as? String, "1")
        XCTAssertFalse(app.textFields["scheduledDateField"].exists)
    }

    func testCreateTypedFormSubmissionPresentsHostController() throws {
        let app = XCUIApplication()
        app.launch()

        replaceText(in: app.textFields["displayNameField"], with: "creator1")

        launchMediaSfu(in: app)

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
    }

    func testJoinTypedFormSubmissionPresentsHostController() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "joiner1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launch()

        launchMediaSfu(in: app)

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
    }

    func testHostLaunchDoesNotExposeWrapperCloseButtonByDefault() throws {
        let app = XCUIApplication()
        app.launch()

        launchMediaSfu(in: app)

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.buttons["mediaSfuCloseButton"].exists)
    }

    func testHostExposesCoreInRoomControlIdentifiers() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_AUTO_LAUNCH"] = "1"
        app.launchEnvironment["MEDIASFU_FORCE_VALIDATED_SESSION"] = "1"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "control1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launch()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
        assertControlExists(
            in: app,
            candidates: ["mediaSfuControlButton_audio", "Unmute", "Mute"],
            name: "audio"
        )
        assertControlExists(
            in: app,
            candidates: ["mediaSfuControlButton_video", "Video", "Stop Video"],
            name: "video"
        )
        assertControlExists(
            in: app,
            candidates: ["mediaSfuControlButton_screen_share", "Share", "Stop Share", "Loading..."],
            name: "screen share"
        )
        assertControlExists(
            in: app,
            candidates: ["mediaSfuControlButton_hang_up", "Hang Up"],
            name: "hang up"
        )
    }

    func testCaptureModernValidatedSessionScreenshots() throws {
        guard probeAutomationFlag(named: "MEDIASFU_CAPTURE_SCREENSHOTS") else {
            throw XCTSkip("Set MEDIASFU_CAPTURE_SCREENSHOTS=1 to enable the screenshot sweep.")
        }

        let environment = mediasfuEnvironment()
        let outputDirectory = screenshotOutputDirectory(from: environment)
        let inRoomApp = launchValidatedScreenshotApplication(using: environment)
        let inRoomHostRoot = waitForHostRoot(in: inRoomApp)
        captureScreenshot(named: "01-in-room", element: inRoomHostRoot, outputDirectory: outputDirectory)
        inRoomApp.terminate()

        let participantsApp = launchValidatedScreenshotApplication(using: environment)
        let participantsHostRoot = waitForHostRoot(in: participantsApp)
        tapElement(in: participantsApp, candidates: ["People"], name: "participants")
        XCTAssertTrue(waitForControl(in: participantsApp, candidates: ["Filter participants"], timeout: 10), participantsApp.debugDescription)
        captureScreenshot(named: "02-participants", element: participantsHostRoot, outputDirectory: outputDirectory)
        participantsApp.terminate()

        let chatApp = launchValidatedScreenshotApplication(using: environment)
        let chatHostRoot = waitForHostRoot(in: chatApp)
        tapElement(in: chatApp, candidates: ["Chat"], name: "chat")
        XCTAssertTrue(
            waitForControl(in: chatApp, candidates: ["Messages", "No direct messages yet", "Type a message...", "Send message"], timeout: 10),
            chatApp.debugDescription
        )
        captureScreenshot(named: "03-chat", element: chatHostRoot, outputDirectory: outputDirectory)
        chatApp.terminate()

        let menuApp = launchValidatedScreenshotApplication(using: environment)
        let menuHostRoot = waitForHostRoot(in: menuApp)
        tapElement(in: menuApp, candidates: ["Menu"], name: "menu")
        XCTAssertTrue(waitForControl(in: menuApp, candidates: ["Event Details"], timeout: 10), menuApp.debugDescription)
        captureScreenshot(named: "04-menu", element: menuHostRoot, outputDirectory: outputDirectory)

        // Screenshots 05-17: use UIKit nav probe buttons to bypass Compose LazyColumn
        // tap-interception issue. Each probe fires triggerShowModal on the KMP bridge.
        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_media_settings",
            probeName: "media settings",
            waitCandidates: ["Media Settings", "Select Microphone"],
            screenshotName: "05-media-settings",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_display_settings",
            probeName: "display settings",
            waitCandidates: ["Display Settings", "Display Option"],
            screenshotName: "06-display-settings",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_recording",
            probeName: "recording settings",
            waitCandidates: ["Recording Settings", "Standard Settings"],
            screenshotName: "07-recording-settings",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_cohost",
            probeName: "co-host settings",
            waitCandidates: ["Manage Co-Host", "Select Co-Host"],
            screenshotName: "08-cohost-settings",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_requests",
            probeName: "requests modal",
            waitCandidates: ["Search requests"],
            screenshotName: "09-requests",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_waiting",
            probeName: "waiting room modal",
            waitCandidates: ["Search waiting participants"],
            screenshotName: "10-waiting-room",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_confirm_exit",
            probeName: "confirm exit modal",
            waitCandidates: ["Leave Event?", "Exit Event"],
            screenshotName: "11-confirm-exit",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_breakout_rooms",
            probeName: "breakout rooms modal",
            waitCandidates: ["Breakout Rooms", "Add Room"],
            screenshotName: "12-breakout-rooms",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_polls",
            probeName: "polls modal",
            waitCandidates: ["Polls", "How is the call quality?"],
            screenshotName: "13-polls",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_background",
            probeName: "virtual background modal",
            waitCandidates: ["Virtual Background"],
            screenshotName: "14-virtual-background",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_panelists",
            probeName: "panelists modal",
            waitCandidates: ["Panelists", "No panelists selected"],
            screenshotName: "15-panelists",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_permissions",
            probeName: "permissions modal",
            waitCandidates: ["Permissions", "Quick presets"],
            screenshotName: "16-permissions",
            outputDirectory: outputDirectory
        )

        captureViaNavProbe(
            in: menuApp,
            hostRootView: menuHostRoot,
            probeIdentifier: "mediaSfuNavProbeButton_translation",
            probeName: "translation settings modal",
            waitCandidates: ["Translation Settings", "Spoken Language"],
            screenshotName: "17-translation-settings",
            outputDirectory: outputDirectory
        )
        menuApp.terminate()
    }

    func testValidatedSessionCanOpenMediaSettingsFromMenu() throws {
        let environment = mediasfuEnvironment()
        let app = launchValidatedScreenshotApplication(using: environment)
        let hostRootView = waitForHostRoot(in: app)

        tapElement(in: app, candidates: ["Menu"], name: "menu")
        XCTAssertTrue(waitForControl(in: app, candidates: ["Event Details"], timeout: 10), app.debugDescription)

        tapMenuCard(
            in: app,
            hostRootView: hostRootView,
            candidates: ["Set Media"],
            name: "media settings"
        )

        XCTAssertTrue(
            waitForControlAcceptingAlerts(in: app, candidates: ["Media Settings", "Select Microphone"], timeout: 15),
            app.debugDescription
        )

        app.terminate()
    }

    func testLaunchPresentsHostController() throws {
        let app = XCUIApplication()
        app.launch()

        let launchButton = app.buttons["launchMediaSfuButton"]
        XCTAssertTrue(waitForElement(launchButton, in: app, timeout: 10))

        launchButton.tap()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
    }

    func testAutomationAutoLaunchPresentsHostController() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_AUTO_LAUNCH"] = "1"
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "auto1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launchEnvironment["MEDIASFU_ISLEVEL"] = "0"
        app.launch()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))
    }

    func testAutomationAutoLaunchShowsUpdatedSharedPrejoinCopy() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_AUTO_LAUNCH"] = "1"
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "auto1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launchEnvironment["MEDIASFU_ISLEVEL"] = "0"
        app.launch()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(waitForElement(app.staticTexts["Join room"], in: app, timeout: 20, allowScroll: false), app.debugDescription)
        XCTAssertTrue(waitForElement(app.staticTexts["Enter your display name and meeting ID to continue."], in: app, timeout: 20, allowScroll: false), app.debugDescription)
        XCTAssertFalse(app.staticTexts["Use the same full-height shared prejoin flow as the other clients."].exists, app.debugDescription)
        XCTAssertFalse(app.staticTexts["Join a meeting"].exists, app.debugDescription)

        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "updated-shared-prejoin"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testAutomationAutoLaunchSkipsNativeBootstrapWhenSessionIsValid() throws {
        let app = XCUIApplication()
        app.launchEnvironment["MEDIASFU_AUTO_LAUNCH"] = "1"
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "auto1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launchEnvironment["MEDIASFU_ISLEVEL"] = "0"
        app.launch()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertFalse(app.buttons["launchMediaSfuButton"].waitForExistence(timeout: 2), app.debugDescription)
        XCTAssertFalse(app.navigationBars["MediaSFU iOS Sample"].exists, app.debugDescription)
    }

    /// Tests that `returnUI = false` (headless / no-pre-join-UI mode) hides the shared
    /// pre-join form entirely.  `MEDIASFU_AUTO_PROCEED=1` maps to `autoProceed=true` in
    /// the KMP host bridge, which sets `returnUI=false` + `noUIPreJoinOptionsJoin` so the
    /// KMP `PreJoinPage` renders an empty Box and fires the auto-connect immediately.
    func testAutoProceedSkipsPreJoinForm() throws {
        let app = XCUIApplication()
        // AUTO_PROCEED triggers returnUI=false (and also auto-launches from bootstrap).
        app.launchEnvironment["MEDIASFU_AUTO_LAUNCH"] = "1"
        app.launchEnvironment["MEDIASFU_AUTO_PROCEED"] = "1"
        app.launchEnvironment["MEDIASFU_ACTION"] = "join"
        app.launchEnvironment["MEDIASFU_USER_NAME"] = "auto1"
        app.launchEnvironment["MEDIASFU_ROOM_NAME"] = "s12345678"
        app.launchEnvironment["MEDIASFU_ISLEVEL"] = "0"
        app.launch()

        // The SDK host view must mount (bootstrap auto-launched it).
        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 15), app.debugDescription)

        // The native bootstrap form must be gone.
        XCTAssertFalse(app.buttons["launchMediaSfuButton"].waitForExistence(timeout: 2), app.debugDescription)

        // With returnUI=false, the KMP pre-join form is skipped — none of its labels should appear.
        // Give the Compose layout a brief moment to settle before asserting absence.
        RunLoop.current.run(until: Date().addingTimeInterval(2.0))
        XCTAssertFalse(app.staticTexts["Join room"].exists,
                       "Pre-join title should be hidden when returnUI=false (AUTO_PROCEED mode)")
        XCTAssertFalse(app.staticTexts["Enter your display name and meeting ID to continue."].exists,
                       "Pre-join subtitle should be hidden when returnUI=false")

        // Capture the headless state as a reference screenshot.
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = "return-ui-false-headless-state"
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testHostExposesRuntimeProbeLabelWhenEnabled() throws {
        let app = configuredApplication(additionalEnvironment: [
            "MEDIASFU_AUTO_LAUNCH": "1",
            "MEDIASFU_FORCE_VALIDATED_SESSION": "1",
            "MEDIASFU_ENABLE_RUNTIME_PROBES": "1",
            "MEDIASFU_USER_NAME": "probe1",
            "MEDIASFU_ROOM_NAME": "s12345678"
        ])
        app.launch()

        XCTAssertTrue(app.otherElements["mediaSfuHostRootView"].waitForExistence(timeout: 10))

        let runtimeProbe = app.otherElements["mediaSfuRuntimeProbe"]
        XCTAssertTrue(runtimeProbe.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(waitForValue(runtimeProbe, toContain: "seq=", timeout: 10))
        XCTAssertTrue(waitForValue(runtimeProbe, toContain: "participants=", timeout: 10))
    }

    func testRuntimeProbeMatchesExpectedLiveState() throws {
        let automationEnvironment = mediasfuEnvironment()

        guard let expectedSubstring = automationEnvironment["MEDIASFU_EXPECT_RUNTIME_PROBE_SUBSTRING"],
              !expectedSubstring.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            throw XCTSkip("Set MEDIASFU_EXPECT_RUNTIME_PROBE_SUBSTRING to enable live-session probe validation.")
        }

        let app = configuredApplication(additionalEnvironment: [
            "MEDIASFU_ENABLE_RUNTIME_PROBES": "1"
        ])
        app.launch()

        let hostRootView = app.otherElements["mediaSfuHostRootView"]
        if !hostRootView.waitForExistence(timeout: 5) {
            let launchButton = app.buttons["launchMediaSfuButton"]
            _ = waitForElement(launchButton, in: app, timeout: 10)

            if !hostRootView.waitForExistence(timeout: 5), launchButton.exists {
                if !launchButton.isHittable {
                    app.swipeUp()
                }

                if !hostRootView.waitForExistence(timeout: 1), launchButton.exists {
                    launchButton.tap()
                }
            }
        }

        XCTAssertTrue(hostRootView.waitForExistence(timeout: 20), app.debugDescription)

        let runtimeProbe = app.otherElements["mediaSfuRuntimeProbe"]
        XCTAssertTrue(runtimeProbe.waitForExistence(timeout: 20), app.debugDescription)
        proceedThroughVisibleJoinFlowIfRequested(in: app, runtimeProbe: runtimeProbe)
        ensureLocalMediaEnabledIfRequested(in: app, runtimeProbe: runtimeProbe)
        applyVirtualBackgroundIfRequested(
            in: app,
            hostRootView: hostRootView,
            runtimeProbe: runtimeProbe,
            environment: automationEnvironment
        )
        let matchedExpectedProbe = waitForValue(runtimeProbe, toContain: expectedSubstring, timeout: 90)
        captureLiveProbeScreenshotIfRequested(
            environment: automationEnvironment,
            matchedExpectedProbe: matchedExpectedProbe,
            hostRootView: hostRootView
        )
        XCTAssertTrue(
            matchedExpectedProbe,
            "Expected runtime probe to contain \(expectedSubstring), actual: \(runtimeProbe.value as? String ?? "")"
        )

        if matchedExpectedProbe {
            NSLog(
                "MediaSFU runtime probe matched expected=%@ actual=%@",
                expectedSubstring,
                runtimeProbe.value as? String ?? "<missing>"
            )
            holdAfterProbeMatchIfRequested(automationEnvironment: automationEnvironment)
        }
    }

    private func captureLiveProbeScreenshotIfRequested(
        environment: [String: String],
        matchedExpectedProbe: Bool,
        hostRootView: XCUIElement,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard probeAutomationFlag(named: "MEDIASFU_CAPTURE_LIVE_PROBE_SCREENSHOTS") else {
            return
        }

        let label = environment["MEDIASFU_PROBE_LABEL"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "-")
            .lowercased()
        let outcome = matchedExpectedProbe ? "match" : "failure"
        let screenshotName = [label, outcome]
            .compactMap { value in
                guard let value, !value.isEmpty else { return nil }
                return value
            }
            .joined(separator: "-")
            .ifEmpty("runtime-probe-\(outcome)")

        captureScreenshot(
            named: screenshotName,
            element: hostRootView,
            outputDirectory: liveProbeScreenshotOutputDirectory(from: environment),
            file: file,
            line: line
        )
    }

    private func liveProbeScreenshotOutputDirectory(from environment: [String: String]) -> String {
        let requestedDirectory = screenshotOutputDirectory(from: environment)
        if !requestedDirectory.hasPrefix("/tmp") {
            return requestedDirectory
        }

        let probeLabel = environment["MEDIASFU_PROBE_LABEL"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "-")
            .lowercased()
            .ifEmpty("runtime-probe") ?? "runtime-probe"
        let runIdComponent = environment["MEDIASFU_PROBE_RUN_ID"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "-")
            .lowercased()
            .ifEmpty("")
        let probeDirectoryName = [probeLabel, runIdComponent]
            .compactMap { value in
                guard let value, !value.isEmpty else { return nil }
                return value
            }
            .joined(separator: "-")
            .ifEmpty(probeLabel)

        let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
        let fallbackURL = documentsURL?
            .appendingPathComponent("mediasfu_probe_screenshots", isDirectory: true)
            .appendingPathComponent(probeDirectoryName, isDirectory: true)

        return fallbackURL?.path ?? requestedDirectory
    }

    private func configuredApplication(additionalEnvironment: [String: String] = [:]) -> XCUIApplication {
        let app = XCUIApplication()
        mediasfuEnvironment()
            .forEach { key, value in
                app.launchEnvironment[key] = value
            }

        additionalEnvironment.forEach { key, value in
            app.launchEnvironment[key] = value
        }

        return app
    }

    private func mediasfuEnvironment() -> [String: String] {
        var environment: [String: String] = [:]
        var loadedFilePaths: [String] = []

        for filePath in mediasfuEnvironmentFileCandidates() {
            guard let fileContents = try? String(contentsOfFile: filePath, encoding: .utf8) else {
                continue
            }

            loadedFilePaths.append(filePath)

            fileContents
                .split(whereSeparator: { $0.isNewline })
                .forEach { rawLine in
                    let line = String(rawLine).trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !line.isEmpty, !line.hasPrefix("#"), let separatorIndex = line.firstIndex(of: "=") else {
                        return
                    }

                    let key = String(line[..<separatorIndex]).trimmingCharacters(in: .whitespacesAndNewlines)
                    let value = String(line[line.index(after: separatorIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
                    guard key.hasPrefix("MEDIASFU_"), !value.isEmpty else {
                        return
                    }

                    environment[key] = value
                }
        }

        ProcessInfo.processInfo.environment
            .filter { $0.key.hasPrefix("MEDIASFU_") }
            .forEach { key, value in
                environment[key] = value
            }

        NSLog(
            "MediaSFU UI Test env loadedFiles=%@ room=%@ runId=%@ expected=%@",
            loadedFilePaths.joined(separator: ","),
            environment["MEDIASFU_ROOM_NAME"] ?? "<missing>",
            environment["MEDIASFU_PROBE_RUN_ID"] ?? "<missing>",
            environment["MEDIASFU_EXPECT_RUNTIME_PROBE_SUBSTRING"] ?? "<missing>"
        )

        return environment
    }

    private func mediasfuEnvironmentFileCandidates() -> [String] {
        let configuredPath = ProcessInfo.processInfo.environment["MEDIASFU_UI_TEST_ENV_FILE"]?.trimmingCharacters(in: .whitespacesAndNewlines)
        let documentsPath = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
            .first?
            .appendingPathComponent("mediasfu_ui_test_env.txt")
            .path
        let fallbackPath = "/tmp/mediasfu_ui_test_env.txt"

        return [documentsPath, fallbackPath, configuredPath]
            .compactMap { value in
                guard let value, !value.isEmpty else {
                    return nil
                }

                return NSString(string: value).expandingTildeInPath
            }
    }

    private func replaceText(in element: XCUIElement, with text: String, file: StaticString = #filePath, line: UInt = #line) {
        XCTAssertTrue(waitForElement(element, in: XCUIApplication(), timeout: 10), file: file, line: line)
        element.tap()

        if let currentValue = element.value as? String, !currentValue.isEmpty {
            element.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: currentValue.count))
        }

        element.typeText(text)
    }

    private func launchMediaSfu(in app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let hostRootView = app.otherElements["mediaSfuHostRootView"]
        if hostRootView.waitForExistence(timeout: 2) {
            return
        }

        let launchButton = app.buttons["launchMediaSfuButton"]
        if !waitForElement(launchButton, in: app, timeout: 10) {
            XCTAssertTrue(hostRootView.waitForExistence(timeout: 10), app.debugDescription, file: file, line: line)
            return
        }

        if !launchButton.isHittable {
            app.swipeUp()
        }

        launchButton.tap()
    }

    private func openAdvancedOptions(in app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let advancedButton = app.buttons["advancedOptionsToggle"]
        XCTAssertTrue(waitForElement(advancedButton, in: app, timeout: 10), file: file, line: line)

        if !advancedButton.isHittable {
            app.swipeUp()
        }

        advancedButton.tap()
    }

    private func assertControlExists(
        in app: XCUIApplication,
        candidates: [String],
        name: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        if waitForControl(in: app, candidates: candidates, timeout: timeout) {
            return
        }

        XCTFail(
            "Expected \(name) control using candidates \(candidates). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    private func waitForControl(
        in app: XCUIApplication,
        candidates: [String],
        timeout: TimeInterval
    ) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        let allDescendants = app.descendants(matching: .any)

        repeat {
            for candidate in candidates {
                if matchingElement(in: allDescendants, candidate: candidate).exists {
                    return true
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return false
    }

    private func ensureLocalMediaEnabledIfRequested(in app: XCUIApplication, runtimeProbe: XCUIElement) {
        if probeAutomationFlag(named: "MEDIASFU_SKIP_UI_LOCAL_MEDIA_TAPS") {
            return
        }

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_AUDIO") &&
            !runtimeProbeValue(for: runtimeProbe).contains("localAudio=true") {
            enableLocalMediaControl(
                in: app,
                runtimeProbe: runtimeProbe,
                candidates: ["Unmute", "Mute", "mediaSfuControlButton_audio"],
                probeSubstring: "localAudio=true",
                name: "audio"
            )
        }

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO") &&
            !runtimeProbeValue(for: runtimeProbe).contains("localVideo=true") {
            enableLocalMediaControl(
                in: app,
                runtimeProbe: runtimeProbe,
                candidates: ["Video On", "Video Off", "Stop Video", "Video", "mediaSfuControlButton_video"],
                probeSubstring: "localVideo=true",
                name: "video"
            )
        }

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_SCREENSHARE") &&
            !runtimeProbeValue(for: runtimeProbe).contains("localScreenShare=true") {
            enableLocalMediaControl(
                in: app,
                runtimeProbe: runtimeProbe,
                candidates: ["Stop Share", "Share", "Loading...", "mediaSfuControlButton_screen_share"],
                probeSubstring: "localScreenShare=true",
                name: "screen share"
            )
        }
    }

    private func proceedThroughVisibleJoinFlowIfRequested(
        in app: XCUIApplication,
        runtimeProbe: XCUIElement,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard probeAutomationFlag(named: "MEDIASFU_PROBE_RETURN_UI") else {
            return
        }

        let environment = mediasfuEnvironment()
        let probeLabel = environment["MEDIASFU_PROBE_LABEL"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        if probeLabel == "marker" {
            return
        }

        for _ in 0..<3 {
            if waitForValueSilently(runtimeProbe, toContain: "lastSignalStage=join-ok", timeout: 2) ||
                waitForValueSilently(runtimeProbe, toContain: "participants=2", timeout: 2) ||
                waitForValueSilently(runtimeProbe, toContain: "localAudio=true", timeout: 1) ||
                waitForValueSilently(runtimeProbe, toContain: "localVideo=true", timeout: 1) {
                return
            }

            guard waitForControl(in: app, candidates: ["Join Room", "Join room"], timeout: 4) else {
                return
            }

            tapElement(
                in: app,
                candidates: ["Join Room", "Join room"],
                name: "join room",
                timeout: 8,
                allowCoordinateTapFallback: true,
                file: file,
                line: line
            )
            acceptPositiveSystemAlertIfPresent(timeout: 2)
            RunLoop.current.run(until: Date().addingTimeInterval(1.0))
        }
    }

    private func applyVirtualBackgroundIfRequested(
        in app: XCUIApplication,
        hostRootView: XCUIElement,
        runtimeProbe: XCUIElement,
        environment: [String: String],
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard probeAutomationFlag(named: "MEDIASFU_PROBE_APPLY_BACKGROUND") else {
            return
        }

        let rawLabel = environment["MEDIASFU_PROBE_LABEL"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "-")
            .lowercased()
        let probeLabel = rawLabel?.ifEmpty("runtime-probe") ?? "runtime-probe"
        let backgroundName = environment["MEDIASFU_PROBE_BACKGROUND_NAME"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .ifEmpty("Light Blur") ?? "Light Blur"
        let outputDirectory = liveProbeScreenshotOutputDirectory(from: environment)

        let probeButton = app.buttons["mediaSfuNavProbeButton_background"]
        XCTAssertTrue(
            probeButton.waitForExistence(timeout: 8),
            "Background nav probe not found. App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
        probeButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        XCTAssertTrue(
            waitForControlAcceptingAlerts(in: app, candidates: ["Virtual Background"], timeout: 15),
            "Expected virtual background modal. App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )

        captureScreenshot(
            named: "\(probeLabel)-background-modal",
            element: hostRootView,
            outputDirectory: outputDirectory,
            file: file,
            line: line
        )

        tapElement(
            in: app,
            candidates: ["Blur"],
            name: "background blur tab",
            timeout: 10,
            allowCoordinateTapFallback: true,
            file: file,
            line: line
        )
        tapElement(
            in: app,
            candidates: [backgroundName, "Light Blur", "Medium Blur", "Strong Blur"],
            name: "background option",
            timeout: 12,
            allowCoordinateTapFallback: true,
            file: file,
            line: line
        )

        captureScreenshot(
            named: "\(probeLabel)-background-selected",
            element: hostRootView,
            outputDirectory: outputDirectory,
            file: file,
            line: line
        )

        waitForConfiguredProbeSubstrings(
            environment["MEDIASFU_EXPECT_PRE_BACKGROUND_APPLY_SUBSTRING"],
            runtimeProbe: runtimeProbe,
            file: file,
            line: line
        )

        let action = environment["MEDIASFU_PROBE_BACKGROUND_ACTION"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .ifEmpty("apply") ?? "apply"

        if action == "save" || action == "save-for-later" {
            tapElement(
                in: app,
                candidates: ["Save for Later"],
                name: "save background for later",
                timeout: 12,
                allowCoordinateTapFallback: true,
                file: file,
                line: line
            )
        } else {
            tapElement(
                in: app,
                candidates: ["Apply"],
                name: "apply background",
                timeout: 12,
                allowCoordinateTapFallback: true,
                file: file,
                line: line
            )
        }

        _ = waitForValue(runtimeProbe, toContain: "keepBackground=true", timeout: 20, file: file, line: line)
        _ = waitForValue(runtimeProbe, toContain: "selectedBackground=\(backgroundName)", timeout: 20, file: file, line: line)
        waitForConfiguredProbeSubstrings(
            environment["MEDIASFU_EXPECT_POST_BACKGROUND_SAVE_SUBSTRING"],
            runtimeProbe: runtimeProbe,
            file: file,
            line: line
        )

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO_AFTER_BACKGROUND") &&
            !runtimeProbeValue(for: runtimeProbe).contains("localVideo=true") {
            enableLocalMediaControl(
                in: app,
                runtimeProbe: runtimeProbe,
                candidates: ["Video On", "Video Off", "Stop Video", "Video", "mediaSfuControlButton_video"],
                probeSubstring: "localVideo=true",
                name: "video after saved background"
            )
        }

        waitForConfiguredProbeSubstrings(
            environment["MEDIASFU_EXPECT_POST_BACKGROUND_VIDEO_SUBSTRING"],
            runtimeProbe: runtimeProbe,
            file: file,
            line: line
        )
        RunLoop.current.run(until: Date().addingTimeInterval(2.0))

        captureScreenshot(
            named: "\(probeLabel)-background-applied",
            element: hostRootView,
            outputDirectory: outputDirectory,
            file: file,
            line: line
        )
    }

    private func waitForConfiguredProbeSubstrings(
        _ rawValue: String?,
        runtimeProbe: XCUIElement,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let substrings = rawValue?
            .components(separatedBy: "||")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty } ?? []

        for substring in substrings {
            _ = waitForValue(runtimeProbe, toContain: substring, timeout: 25, file: file, line: line)
        }
    }

    private func enableLocalMediaControl(
        in app: XCUIApplication,
        runtimeProbe: XCUIElement,
        candidates: [String],
        probeSubstring: String,
        name: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard let control = firstMediaControlElement(in: app, candidates: candidates, timeout: 20) else {
            return
        }

        if !control.isHittable {
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }

        control.tap()
        acceptPositiveSystemAlertIfPresent()
        _ = waitForValue(runtimeProbe, toContain: probeSubstring, timeout: 30, file: file, line: line)
    }

    private func firstMediaControlElement(
        in app: XCUIApplication,
        candidates: [String],
        timeout: TimeInterval
    ) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            // Prefer explicit accessibility identifiers for the media controls.
            for candidate in candidates where candidate.hasPrefix("mediaSfuControlButton_") {
                let button = app.buttons[candidate]
                if button.exists {
                    return button
                }
            }

            // Then fall back to button labels to avoid ambiguous matches with sibling images.
            for candidate in candidates {
                let predicate = NSPredicate(
                    format: "identifier == %@ OR label == %@ OR label CONTAINS[c] %@ OR value == %@ OR value CONTAINS[c] %@",
                    candidate,
                    candidate,
                    candidate,
                    candidate,
                    candidate
                )
                let button = app.buttons.matching(predicate).firstMatch
                if button.exists {
                    return button
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return firstExistingElement(in: app, candidates: candidates, timeout: timeout)
    }

    private func firstExistingElement(
        in app: XCUIApplication,
        candidates: [String],
        timeout: TimeInterval
    ) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)
        let allDescendants = app.descendants(matching: .any)

        repeat {
            for candidate in candidates {
                let element = matchingElement(in: allDescendants, candidate: candidate)
                if element.exists && element.isHittable {
                    return element
                }
            }

            for candidate in candidates {
                let element = matchingElement(in: allDescendants, candidate: candidate)
                if element.exists {
                    return element
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return nil
    }

    private func acceptPositiveSystemAlertIfPresent(timeout: TimeInterval = 8) {
        let positiveLabels = [
            "Allow",
            "OK",
            "Continue",
            "Join",
            "Start",
            "Broadcast",
            "Start Broadcast",
            "Allow While Using App",
            "While Using App",
            "Always Allow"
        ]
        let deadline = Date().addingTimeInterval(timeout)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

        repeat {
            let containers = [springboard.alerts.firstMatch, springboard.sheets.firstMatch]
            for container in containers where container.exists {
                for label in positiveLabels {
                    let button = container.buttons[label]
                    if button.exists {
                        button.tap()
                        return
                    }
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline
    }

    private func captureNestedMenuModal(
        in app: XCUIApplication,
        hostRootView: XCUIElement,
        triggerCandidates: [String],
        triggerName: String,
        waitCandidates: [String],
        screenshotName: String,
        outputDirectory: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        tapMenuCard(
            in: app,
            hostRootView: hostRootView,
            candidates: triggerCandidates,
            name: triggerName,
            timeout: 10,
            file: file,
            line: line
        )
        XCTAssertTrue(
            waitForControlAcceptingAlerts(in: app, candidates: waitCandidates, timeout: 15),
            "Expected \(triggerName) using candidates \(waitCandidates). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
        captureScreenshot(named: screenshotName, element: hostRootView, outputDirectory: outputDirectory, file: file, line: line)
        tapElement(in: app, candidates: ["Back"], name: "back from \(triggerName)", timeout: 10, file: file, line: line)
        XCTAssertTrue(
            waitForControl(in: app, candidates: ["Event Details"], timeout: 10),
            "Expected to return to the menu after \(triggerName). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    /// Tap a UIKit navigation probe button (bypasses Compose LazyColumn touch interception),
    /// wait for the target modal to appear, take a screenshot, then close the modal.
    private func captureViaNavProbe(
        in app: XCUIApplication,
        hostRootView: XCUIElement,
        probeIdentifier: String,
        probeName: String,
        waitCandidates: [String],
        screenshotName: String,
        outputDirectory: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        // Tap the UIKit nav probe button — these are real UIButtons so the tap lands reliably.
        // Use coordinate tap to bypass isHittable check (parent container has alpha=0.01).
        let probeButton = app.buttons[probeIdentifier]
        XCTAssertTrue(
            probeButton.waitForExistence(timeout: 5),
            "Nav probe '\(probeIdentifier)' not found for \(probeName). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
        probeButton.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()

        // Wait for the modal to appear (permission alerts may fire for media_settings).
        XCTAssertTrue(
            waitForControlAcceptingAlerts(in: app, candidates: waitCandidates, timeout: 15),
            "Expected \(probeName) modal using candidates \(waitCandidates). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
        captureScreenshot(named: screenshotName, element: hostRootView, outputDirectory: outputDirectory, file: file, line: line)

        // Close the modal via the header Close button (Back is absent when opened via probe).
        tapElement(in: app, candidates: ["Close"], name: "close from \(probeName)", timeout: 10, file: file, line: line)
        // Brief settle before the next probe.
        RunLoop.current.run(until: Date().addingTimeInterval(0.5))
    }

    /// Like waitForControl but also dismisses any system permission alerts (camera/mic)
    /// that may appear asynchronously while navigating to a modal.
    private func waitForControlAcceptingAlerts(
        in app: XCUIApplication,
        candidates: [String],
        timeout: TimeInterval
    ) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)
        let positiveLabels = [
            "Allow", "OK", "Continue", "Allow While Using App", "While Using App", "Always Allow"
        ]
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        let allDescendants = app.descendants(matching: .any)

        repeat {
            // Dismiss any pending permission alerts first
            let containers = [springboard.alerts.firstMatch, springboard.sheets.firstMatch]
            for container in containers where container.exists {
                for label in positiveLabels {
                    let btn = container.buttons[label]
                    if btn.exists {
                        btn.tap()
                        break
                    }
                }
            }
            // Check if the target control is now visible
            for candidate in candidates {
                if matchingElement(in: allDescendants, candidate: candidate).exists {
                    return true
                }
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.3))
        } while Date() < deadline

        return false
    }

    private func launchValidatedScreenshotApplication(using environment: [String: String]) -> XCUIApplication {
        let app = configuredApplication(additionalEnvironment: [
            "MEDIASFU_AUTO_LAUNCH": "1",
            "MEDIASFU_FORCE_VALIDATED_SESSION": "1",
            "MEDIASFU_USER_NAME": environment["MEDIASFU_USER_NAME"] ?? "capture1",
            "MEDIASFU_ROOM_NAME": environment["MEDIASFU_ROOM_NAME"] ?? "s12345678"
        ])
        app.launch()
        return app
    }

    private func waitForHostRoot(
        in app: XCUIApplication,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> XCUIElement {
        let hostRootView = app.otherElements["mediaSfuHostRootView"]
        XCTAssertTrue(hostRootView.waitForExistence(timeout: 20), app.debugDescription, file: file, line: line)
        return hostRootView
    }

    private func captureScreenshot(
        named name: String,
        element: XCUIElement,
        outputDirectory: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        XCTAssertTrue(element.exists, "Expected element to exist before capturing \(name).", file: file, line: line)
        RunLoop.current.run(until: Date().addingTimeInterval(0.6))

        let screenshot = element.screenshot()
        let attachment = XCTAttachment(screenshot: screenshot)
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)

        let outputURL = URL(fileURLWithPath: outputDirectory, isDirectory: true)
        let fileURL = outputURL.appendingPathComponent("\(name).png")

        do {
            try FileManager.default.createDirectory(at: outputURL, withIntermediateDirectories: true)
            try screenshot.pngRepresentation.write(to: fileURL)
            NSLog("MediaSFU screenshot saved path=%@", fileURL.path)
        } catch {
            NSLog("MediaSFU screenshot save skipped name=%@ error=%@", name, String(describing: error))
        }
    }

    private func screenshotOutputDirectory(from environment: [String: String]) -> String {
        let configuredPath = environment["MEDIASFU_SCREENSHOT_OUTPUT_DIR"]?
            .trimmingCharacters(in: .whitespacesAndNewlines)

        if let configuredPath, !configuredPath.isEmpty {
            return NSString(string: configuredPath).expandingTildeInPath
        }

        return "/tmp/mediasfu-ui-screenshots"
    }

    private func runtimeProbeValue(for runtimeProbe: XCUIElement) -> String {
        runtimeProbe.value as? String ?? ""
    }

    private func probeAutomationFlag(named name: String) -> Bool {
        guard let rawValue = mediasfuEnvironment()[name] else {
            return false
        }

        switch rawValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "true", "yes", "on":
            return true
        default:
            return false
        }
    }

    private func tapElement(
        in app: XCUIApplication,
        candidates: [String],
        name: String,
        timeout: TimeInterval = 10,
        scrollDirection: ScrollDirection? = nil,
        scrollContainer: XCUIElement? = nil,
        allowCoordinateTapFallback: Bool = false,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let deadline = Date().addingTimeInterval(timeout)
        var shouldScrollDown = true
        let container = scrollContainer ?? app

        repeat {
            if let element = firstInteractiveElement(in: app, candidates: candidates), element.exists {
                if element.isHittable {
                    element.tap()
                    acceptPositiveSystemAlertIfPresent(timeout: 0.5)
                    return
                }

                if allowCoordinateTapFallback && canCoordinateTap(element, in: container) {
                    element.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.8)).tap()
                    acceptPositiveSystemAlertIfPresent(timeout: 0.5)
                    return
                }
            }

            let direction = scrollDirection ?? (shouldScrollDown ? .down : .up)

            if direction == .down {
                container.swipeUp()
            } else {
                container.swipeDown()
            }
            if scrollDirection == nil {
                shouldScrollDown.toggle()
            }
            RunLoop.current.run(until: Date().addingTimeInterval(0.35))
        } while Date() < deadline

        XCTFail(
            "Expected to tap \(name) using candidates \(candidates). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    private func tapMenuCard(
        in app: XCUIApplication,
        hostRootView: XCUIElement,
        candidates: [String],
        name: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if let cardButton = firstMenuCardButton(in: app, candidates: candidates) {
                // Prefer the semantic tap (XCUITest follows accessibility hierarchy).
                // Coordinate taps on nested Compose-in-UIKit views can miss the event
                // even when the element appears hittable.
                if cardButton.isHittable {
                    cardButton.tap()
                    acceptPositiveSystemAlertIfPresent(timeout: 0.5)
                    return
                }

                // Fallback: coordinate-based tap when the element reports not-hittable
                // but is visually on screen (e.g., partially clipped by a scroll container).
                if canCoordinateTapMenuCard(cardButton) {
                    let frame = cardButton.frame
                    let appFrame = app.frame
                    let normalizedTap = CGVector(
                        dx: (frame.midX - appFrame.minX) / appFrame.width,
                        dy: (frame.midY - appFrame.minY) / appFrame.height
                    )
                    app.coordinate(withNormalizedOffset: normalizedTap).tap()
                    acceptPositiveSystemAlertIfPresent(timeout: 0.5)
                    return
                }
            }

            hostRootView.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.35))
        } while Date() < deadline

        XCTFail(
            "Expected to tap \(name) using candidates \(candidates). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    private func firstMenuCardButton(
        in app: XCUIApplication,
        candidates: [String]
    ) -> XCUIElement? {
        var firstExistingButton: XCUIElement?

        for candidate in candidates {
            let button = matchingElement(in: app.buttons, candidate: candidate)
            if button.exists && button.isHittable {
                return button
            }

            if firstExistingButton == nil, button.exists {
                firstExistingButton = button
            }
        }

        return firstExistingButton
    }

    private func canCoordinateTapMenuCard(_ element: XCUIElement) -> Bool {
        let frame = element.frame
        guard !frame.isEmpty else {
            return false
        }

        return frame.minY >= 140 && frame.maxY <= 760 && frame.height >= 60
    }

    private func canCoordinateTap(_ element: XCUIElement, in container: XCUIElement) -> Bool {
        let elementFrame = element.frame
        let containerFrame = container.frame

        guard !elementFrame.isEmpty, !containerFrame.isEmpty else {
            return false
        }

        let tapPoint = CGPoint(
            x: elementFrame.midX,
            y: elementFrame.minY + (elementFrame.height * 0.8)
        )

        return containerFrame.contains(tapPoint)
    }

    private func tapModalClose(
        in app: XCUIApplication,
        name: String,
        timeout: TimeInterval = 10,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        let closeButton = app.buttons.matching(
            NSPredicate(format: "label == %@ AND identifier != %@", "Close", "mediaSfuCloseButton")
        ).firstMatch

        let deadline = Date().addingTimeInterval(timeout)
        var shouldScrollDown = true

        repeat {
            if closeButton.exists && closeButton.isHittable {
                closeButton.tap()
                return
            }

            if shouldScrollDown {
                app.swipeUp()
            } else {
                app.swipeDown()
            }
            shouldScrollDown.toggle()
            RunLoop.current.run(until: Date().addingTimeInterval(0.35))
        } while Date() < deadline

        XCTFail(
            "Expected to tap \(name). App tree:\n\(app.debugDescription)",
            file: file,
            line: line
        )
    }

    private func firstInteractiveElement(
        in app: XCUIApplication,
        candidates: [String]
    ) -> XCUIElement? {
        for candidate in candidates {
            let prioritizedQueries: [XCUIElementQuery] = [
                app.buttons,
                app.cells,
                app.otherElements,
                app.staticTexts,
                app.images,
                app.descendants(matching: .any)
            ]

            for query in prioritizedQueries {
                let element = matchingElement(in: query, candidate: candidate)
                if element.exists && element.isHittable {
                    return element
                }
            }

            for query in prioritizedQueries {
                let element = matchingElement(in: query, candidate: candidate)
                if element.exists {
                    return element
                }
            }
        }

        return nil
    }

    private func matchingElement(in query: XCUIElementQuery, candidate: String) -> XCUIElement {
        let exactMatch = query[candidate]
        if exactMatch.exists {
            return exactMatch
        }

        let predicate = NSPredicate(
            format: "identifier == %@ OR label == %@ OR label CONTAINS[c] %@ OR value == %@ OR value CONTAINS[c] %@",
            candidate,
            candidate,
            candidate,
            candidate,
            candidate
        )
        return query.matching(predicate).firstMatch
    }

    private func holdAfterProbeMatchIfRequested(automationEnvironment: [String: String]) {
        guard let rawValue = automationEnvironment["MEDIASFU_PROBE_POST_MATCH_HOLD_SECONDS"],
              let holdSeconds = TimeInterval(rawValue),
              holdSeconds > 0 else {
            return
        }

        RunLoop.current.run(until: Date().addingTimeInterval(holdSeconds))
    }

    private func waitForValueSilently(
        _ element: XCUIElement,
        toContain substring: String,
        timeout: TimeInterval
    ) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if let value = element.value as? String,
               probeValue(value, satisfiesExpectation: substring) {
                return true
            }

            acceptPositiveSystemAlertIfPresent(timeout: 0.2)
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return false
    }

    private func waitForValue(
        _ element: XCUIElement,
        toContain substring: String,
        timeout: TimeInterval,
        file: StaticString = #filePath,
        line: UInt = #line
    ) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if let value = element.value as? String,
               probeValue(value, satisfiesExpectation: substring) {
                return true
            }

            acceptPositiveSystemAlertIfPresent(timeout: 0.2)
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        XCTFail("Timed out waiting for value matching \(substring). Final value: \(element.value as? String ?? "")", file: file, line: line)
        return false
    }

    private func probeValue(_ value: String, satisfiesExpectation expectation: String) -> Bool {
        let conditions = expectation
            .split(separator: ";", omittingEmptySubsequences: true)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        guard !conditions.isEmpty else {
            return true
        }

        let fields = runtimeProbeFields(from: value)
        return conditions.allSatisfy { condition in
            guard let equalsIndex = condition.firstIndex(of: "=") else {
                return value.contains(condition)
            }

            let key = String(condition[..<equalsIndex])
            let expectedValue = String(condition[condition.index(after: equalsIndex)...])
            guard let expectedInt = Int(expectedValue),
                  let actualValue = fields[key],
                  let actualInt = Int(actualValue) else {
                return value.contains(condition)
            }

            return expectedInt == 0 ? actualInt == 0 : actualInt >= expectedInt
        }
    }

    private func runtimeProbeFields(from value: String) -> [String: String] {
        var fields: [String: String] = [:]
        value
            .split(separator: ";", omittingEmptySubsequences: true)
            .forEach { rawField in
                let field = String(rawField)
                guard let equalsIndex = field.firstIndex(of: "=") else {
                    return
                }

                let key = String(field[..<equalsIndex])
                let fieldValue = String(field[field.index(after: equalsIndex)...])
                fields[key] = fieldValue
            }
        return fields
    }

    private func waitForElement(
        _ element: XCUIElement,
        in app: XCUIApplication,
        timeout: TimeInterval,
        allowScroll: Bool = true,
        scrollDirection: ScrollDirection = .down
    ) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if element.exists {
                return true
            }

            if allowScroll {
                switch scrollDirection {
                case .down:
                    app.swipeUp()
                case .up:
                    app.swipeDown()
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return element.exists
    }

    private func waitForElementToDisappear(_ element: XCUIElement, timeout: TimeInterval) -> Bool {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            if !element.exists {
                return true
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return !element.exists
    }

    private enum ScrollDirection {
        case down
        case up
    }
}

private extension String {
    func ifEmpty(_ fallback: @autoclosure () -> String) -> String {
        isEmpty ? fallback() : self
    }
}
