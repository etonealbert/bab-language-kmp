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
            url: "https://github.com/etonealbert/bab-language-kmp/releases/download/v1.0.5/BabLanguageSDK.xcframework.zip",
            checksum: "ea2386f10a5b96c7ded748346ccfb4b304fcd16b147f26a62291d7e15979d9e5"
        ),
    ]
)
