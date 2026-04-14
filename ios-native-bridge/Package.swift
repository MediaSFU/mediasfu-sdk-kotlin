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
                .product(name: "MediaSFUMediasoupClient", package: "MediaSFUMediasoupClient")
            ]
        ),
        .testTarget(
            name: "MediaSFUIosBridgeTests",
            dependencies: ["MediaSFUIosBridge"]
        )
    ]
)
