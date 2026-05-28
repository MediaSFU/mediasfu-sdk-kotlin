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

    func testHostCloseReturnsToBootstrap() throws {
        let app = XCUIApplication()
        app.launch()

        launchMediaSfu(in: app)

        let closeButton = app.buttons["mediaSfuCloseButton"]
        XCTAssertTrue(closeButton.waitForExistence(timeout: 10))
        closeButton.tap()

        XCTAssertTrue(waitForElementToDisappear(app.otherElements["mediaSfuHostRootView"], timeout: 10))
        XCTAssertTrue(waitForElement(app.buttons["launchMediaSfuButton"], in: app, timeout: 10, allowScroll: false))
        XCTAssertFalse(app.otherElements["mediaSfuHostRootView"].exists)
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

        let closeButton = app.buttons["mediaSfuCloseButton"]
        XCTAssertTrue(closeButton.waitForExistence(timeout: 10), app.debugDescription)
        XCTAssertTrue(waitForValue(closeButton, toContain: "seq=", timeout: 10))
        XCTAssertTrue(waitForValue(closeButton, toContain: "participants=", timeout: 10))
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

        let closeButton = app.buttons["mediaSfuCloseButton"]
        XCTAssertTrue(closeButton.waitForExistence(timeout: 20), app.debugDescription)
        ensureLocalMediaEnabledIfRequested(in: app, closeButton: closeButton)
        let matchedExpectedProbe = waitForValue(closeButton, toContain: expectedSubstring, timeout: 90)
        XCTAssertTrue(
            matchedExpectedProbe,
            "Expected runtime probe to contain \(expectedSubstring), actual: \(closeButton.value as? String ?? "")"
        )

        if matchedExpectedProbe {
            NSLog(
                "MediaSFU runtime probe matched expected=%@ actual=%@",
                expectedSubstring,
                closeButton.value as? String ?? "<missing>"
            )
            holdAfterProbeMatchIfRequested(automationEnvironment: automationEnvironment)
        }
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
        let launchButton = app.buttons["launchMediaSfuButton"]
        XCTAssertTrue(waitForElement(launchButton, in: app, timeout: 10), file: file, line: line)

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

        repeat {
            for candidate in candidates {
                if app.descendants(matching: .any)[candidate].exists {
                    return true
                }
            }

            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        return false
    }

    private func ensureLocalMediaEnabledIfRequested(in app: XCUIApplication, closeButton: XCUIElement) {
        if probeAutomationFlag(named: "MEDIASFU_SKIP_UI_LOCAL_MEDIA_TAPS") {
            return
        }

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_AUDIO") &&
            !runtimeProbeValue(for: closeButton).contains("localAudio=true") {
            enableLocalMediaControl(
                in: app,
                closeButton: closeButton,
                candidates: ["Unmute", "Mute", "mediaSfuControlButton_audio"],
                probeSubstring: "localAudio=true",
                name: "audio"
            )
        }

        if probeAutomationFlag(named: "MEDIASFU_PROBE_ENABLE_LOCAL_VIDEO") &&
            !runtimeProbeValue(for: closeButton).contains("localVideo=true") {
            enableLocalMediaControl(
                in: app,
                closeButton: closeButton,
                candidates: ["Video On", "Video Off", "Stop Video", "Video", "mediaSfuControlButton_video"],
                probeSubstring: "localVideo=true",
                name: "video"
            )
        }
    }

    private func enableLocalMediaControl(
        in app: XCUIApplication,
        closeButton: XCUIElement,
        candidates: [String],
        probeSubstring: String,
        name: String,
        file: StaticString = #filePath,
        line: UInt = #line
    ) {
        guard let control = firstExistingElement(in: app, candidates: candidates, timeout: 20) else {
            return
        }

        if !control.isHittable {
            app.swipeUp()
            RunLoop.current.run(until: Date().addingTimeInterval(0.5))
        }

        control.tap()
        acceptPositiveSystemAlertIfPresent()
        _ = waitForValue(closeButton, toContain: probeSubstring, timeout: 30, file: file, line: line)
    }

    private func firstExistingElement(
        in app: XCUIApplication,
        candidates: [String],
        timeout: TimeInterval
    ) -> XCUIElement? {
        let deadline = Date().addingTimeInterval(timeout)

        repeat {
            for candidate in candidates {
                let element = app.descendants(matching: .any)[candidate]
                if element.exists && element.isHittable {
                    return element
                }
            }

            for candidate in candidates {
                let element = app.descendants(matching: .any)[candidate]
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

    private func runtimeProbeValue(for closeButton: XCUIElement) -> String {
        closeButton.value as? String ?? ""
    }

    private func probeAutomationFlag(named name: String) -> Bool {
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

    private func holdAfterProbeMatchIfRequested(automationEnvironment: [String: String]) {
        guard let rawValue = automationEnvironment["MEDIASFU_PROBE_POST_MATCH_HOLD_SECONDS"],
              let holdSeconds = TimeInterval(rawValue),
              holdSeconds > 0 else {
            return
        }

        RunLoop.current.run(until: Date().addingTimeInterval(holdSeconds))
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
            if (element.value as? String)?.contains(substring) == true {
                return true
            }

            acceptPositiveSystemAlertIfPresent(timeout: 0.2)
            RunLoop.current.run(until: Date().addingTimeInterval(0.2))
        } while Date() < deadline

        XCTFail("Timed out waiting for value containing \(substring). Final value: \(element.value as? String ?? "")", file: file, line: line)
        return false
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