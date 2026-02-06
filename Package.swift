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
            url: "https://github.com/etonealbert/bab-language-kmp/releases/download/v1.0.3/BabLanguageSDK.xcframework.zip",
            checksum: "f2b5f3f278e9179282ef62e60482f462678ad91a0c2507512e35b2d4c18dbde7"
        ),
    ]
)
