// swift-tools-version:6.0
import PackageDescription

let package = Package(
    name: "Shared",
    platforms: [
        .iOS(.v18),
        .macOS(.v15)
    ],
    products: [
        .library(
            name: "Shared",
            targets: ["Shared"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "Shared",
            path: "./composeApp/build/XCFrameworks/release/Shared.xcframework"
        ),
    ]
)