import UIKit

final class MediaSFUHostViewController: UIViewController {
    private let hostAdapter: any MediaSFUSDKHostAdapter
    private let hostContext: MediaSFUSDKHostContext
    private var hostedViewController: UIViewController?

    init(
        sessionConfig: SampleSessionConfig,
        hostAdapter: any MediaSFUSDKHostAdapter,
        onClose: @escaping () -> Void
    ) {
        self.hostAdapter = hostAdapter
        self.hostContext = MediaSFUSDKHostContext(sessionConfig: sessionConfig, onClose: onClose)
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .fullScreen
        modalPresentationCapturesStatusBarAppearance = true
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
        view.accessibilityIdentifier = "mediaSfuHostRootView"

        let hosted = hostAdapter.makeHostedViewController(context: hostContext)
        hosted.modalPresentationStyle = .fullScreen
        hosted.modalPresentationCapturesStatusBarAppearance = true
        addChild(hosted)
        hosted.view.translatesAutoresizingMaskIntoConstraints = false
        hosted.view.backgroundColor = .black
        hosted.view.insetsLayoutMarginsFromSafeArea = false
        hosted.view.directionalLayoutMargins = .zero
        view.addSubview(hosted.view)
        NSLayoutConstraint.activate([
            hosted.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hosted.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            hosted.view.topAnchor.constraint(equalTo: view.topAnchor),
            hosted.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        hosted.didMove(toParent: self)
        hostedViewController = hosted
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

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        // Only tear down when actually being dismissed/moved away, not during
        // transient disappearances such as app backgrounding.
        guard isBeingDismissed || isMovingFromParent else {
            NSLog("MediaSFU - viewDidDisappear (not dismissed, skipping teardown)")
            return
        }
        NSLog("MediaSFU - viewDidDisappear (dismissed, tearing down hosted VC)")
        if let hostedViewController {
            hostedViewController.willMove(toParent: nil)
            hostedViewController.view.removeFromSuperview()
            hostedViewController.removeFromParent()
            self.hostedViewController = nil
        }
    }
}
