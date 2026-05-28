import UIKit

struct PlaceholderMediaSFUSDKHostAdapter: MediaSFUSDKHostAdapter {
    var integrationStatus: String {
        "Using placeholder MediaSFUSDK host adapter. Replace MediaSFUSDKHostFactory.makeAdapter() with a real exported KMP UI host adapter once the framework entry point is confirmed in Xcode."
    }

    func makeHostedViewController(context: MediaSFUSDKHostContext) -> UIViewController {
        PlaceholderMediaSFUSDKHostViewController(context: context, integrationStatus: integrationStatus)
    }
}

final class PlaceholderMediaSFUSDKHostViewController: UIViewController {
    private let context: MediaSFUSDKHostContext
    private let integrationStatus: String

    init(context: MediaSFUSDKHostContext, integrationStatus: String) {
        self.context = context
        self.integrationStatus = integrationStatus
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground

        let roomLabel = UILabel()
        roomLabel.translatesAutoresizingMaskIntoConstraints = false
        roomLabel.numberOfLines = 0
        roomLabel.textAlignment = .center
        let roomName = context.sessionConfig.normalizedRoomName.isEmpty
            ? "<unset>"
            : context.sessionConfig.normalizedRoomName
        roomLabel.text = "MediaSFU host placeholder\n\nRoom: \(roomName)"

        let statusLabel = UILabel()
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        statusLabel.numberOfLines = 0
        statusLabel.textAlignment = .center
        statusLabel.font = .preferredFont(forTextStyle: .footnote)
        statusLabel.textColor = .secondaryLabel
        statusLabel.text = integrationStatus

        let closeButton = UIButton(type: .system)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.setTitle("Close", for: .normal)
        closeButton.accessibilityIdentifier = "mediaSfuCloseButton"
        closeButton.addAction(UIAction { [weak self] _ in
            self?.context.onClose()
        }, for: .touchUpInside)

        view.addSubview(roomLabel)
        view.addSubview(statusLabel)
        view.addSubview(closeButton)

        NSLayoutConstraint.activate([
            roomLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            roomLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: -40),
            roomLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 24),
            roomLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -24),

            statusLabel.topAnchor.constraint(equalTo: roomLabel.bottomAnchor, constant: 16),
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),

            closeButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 20),
            closeButton.centerXAnchor.constraint(equalTo: view.centerXAnchor)
        ])
    }
}
