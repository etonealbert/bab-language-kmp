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
            url: "https://github.com/etonealbert/bab-language-kmp/releases/download/v1.0.0/BabLanguageSDK.xcframework.zip",
            checksum: "6cadf68ce275971221e69d0f216a6cb904e5e49f97c16d64a6ea46c075449dfe"
        ),
    ]
)
