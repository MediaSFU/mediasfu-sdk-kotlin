import SwiftUI

@main
struct MediaSFUSampleApp: App {
    @StateObject private var launchCoordinator = SampleLaunchCoordinator()
    private let bridgeInstaller = MediaSFUBridgeInstaller()
    private let permissionsCoordinator = MediaPermissionsCoordinator()
    private let replayKitCoordinator = ReplayKitCoordinator()

    var body: some Scene {
        WindowGroup {
            MediaSFUSampleRootView(
                launchCoordinator: launchCoordinator,
                bridgeInstaller: bridgeInstaller,
                permissionsCoordinator: permissionsCoordinator,
                replayKitCoordinator: replayKitCoordinator
            )
        }
    }
}

struct MediaSFUSampleRootView: View {
    @ObservedObject var launchCoordinator: SampleLaunchCoordinator
    let bridgeInstaller: MediaSFUBridgeInstaller
    let permissionsCoordinator: MediaPermissionsCoordinator
    let replayKitCoordinator: ReplayKitCoordinator

    private let hostAdapter: any MediaSFUSDKHostAdapter = MediaSFUSDKHostFactory.makeAdapter()

    @State private var didPrepareSession = false
    @State private var shouldShowBootstrapTools = false
    @State private var resolvedSessionConfig = SampleSessionConfig()
    @State private var hostReloadToken = UUID()

    var body: some View {
        Group {
            if shouldShowBootstrapTools {
                SampleBootstrapView(
                    launchCoordinator: launchCoordinator,
                    bridgeInstaller: bridgeInstaller,
                    permissionsCoordinator: permissionsCoordinator,
                    replayKitCoordinator: replayKitCoordinator
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else if didPrepareSession {
                MediaSFUHostContainer(
                    sessionConfig: resolvedSessionConfig,
                    hostAdapter: hostAdapter,
                    onClose: {
                        launchCoordinator.closeSession()
                    }
                )
                .id(hostReloadToken)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .ignoresSafeArea()
            } else {
                ZStack {
                    Color.black.ignoresSafeArea()
                    ProgressView()
                        .tint(.white)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .task {
            prepareSessionIfNeeded()
        }
    }

    @MainActor
    private func prepareSessionIfNeeded() {
        guard !didPrepareSession else {
            return
        }

        let resolvedConfig = SampleAppEnvironment.resolvedSessionConfig(from: launchCoordinator.sessionConfig)
        launchCoordinator.sessionConfig = resolvedConfig

        let installStatus = bridgeInstaller.installIfNeeded()
        if !installStatus.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            launchCoordinator.updateStatus(installStatus)
        }

        if SampleAppEnvironment.shouldShowBootstrapTools(for: resolvedConfig) {
            self.shouldShowBootstrapTools = true
            self.didPrepareSession = true
            return
        }

        self.resolvedSessionConfig = resolvedConfig
        self.hostReloadToken = UUID()
        self.didPrepareSession = true
    }
}
