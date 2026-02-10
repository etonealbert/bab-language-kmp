// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "BabLanguageSDK",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "BabLanguageSDK",
            targets: ["BabLanguageSDK"]
        ),
    ],
    targets: [
        .binaryTarget(
            name: "BabLanguageSDK",
            url: "https://github.com/etonealbert/bab-language-kmp/releases/download/v1.0.7/BabLanguageSDK.xcframework.zip",
            checksum: "1f54573ca5d67ecd9aa4a399c21e47bafbb3009449e23c1c93d51b33b5b39b83"
        ),
    ]
)
