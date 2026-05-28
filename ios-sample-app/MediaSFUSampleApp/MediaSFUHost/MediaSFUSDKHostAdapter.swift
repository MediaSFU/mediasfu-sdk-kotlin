import UIKit

struct MediaSFUSDKHostContext {
    let sessionConfig: SampleSessionConfig
    let onClose: () -> Void
}

protocol MediaSFUSDKHostAdapter {
    var integrationStatus: String { get }
    func makeHostedViewController(context: MediaSFUSDKHostContext) -> UIViewController
}

enum MediaSFUSDKHostFactory {
    static func makeAdapter() -> any MediaSFUSDKHostAdapter {
        RealMediaSFUSDKHostAdapter()
    }
}
