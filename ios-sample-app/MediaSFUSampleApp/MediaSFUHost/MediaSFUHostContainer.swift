import SwiftUI
import UIKit

struct MediaSFUHostContainer: UIViewControllerRepresentable {
    let sessionConfig: SampleSessionConfig
    let hostAdapter: any MediaSFUSDKHostAdapter
    let onClose: () -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        MediaSFUHostViewController(
            sessionConfig: sessionConfig,
            hostAdapter: hostAdapter,
            onClose: onClose
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
    }

    @available(iOS 16.0, *)
    func sizeThatFits(_ proposal: ProposedViewSize, uiViewController: UIViewController, context: Context) -> CGSize? {
        // Use the SwiftUI-proposed size directly.  By the time this is called
        // (after the window-settle delay in prepareSessionIfNeeded), the parent
        // Group's .ignoresSafeArea() has already given us the full-screen proposal
        // (393 × 852 on iPhone 15 Pro), so we don't need to read UIScreen APIs.
        let w = proposal.width  ?? 393
        let h = proposal.height ?? 852
        return CGSize(width: w, height: h)
    }
}
