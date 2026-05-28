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
        CGSize(
            width: proposal.width ?? UIScreen.main.bounds.width,
            height: proposal.height ?? UIScreen.main.bounds.height
        )
    }
}
