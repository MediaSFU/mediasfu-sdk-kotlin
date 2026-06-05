// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "MediaSFUIosBridge",
    platforms: [
        .iOS(.v15),
        .macOS(.v13)
    ],
    products: [
        .library(
            name: "MediaSFUIosBridge",
            type: .dynamic,
            targets: ["MediaSFUIosBridge"]
        )
    ],
    dependencies: [
        .package(path: "../../mediasfu-mediasoup-client-ios")
    ],
    targets: [
        .target(
            name: "MediaSFUIosBridge",
            dependencies: [
                .product(name: "MediaSFUMediasoupClient", package: "mediasfu-mediasoup-client-ios")
            ]
        ),
        .testTarget(
            name: "MediaSFUIosBridgeTests",
            dependencies: ["MediaSFUIosBridge"]
        )
    ]
)
