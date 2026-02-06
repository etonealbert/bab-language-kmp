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
            url: "https://github.com/etonealbert/bab-language-kmp/releases/download/v1.0.2/BabLanguageSDK.xcframework.zip",
            checksum: "74ea26d7b4b76b3005c2fcd1187c21f143cb170d9f7dfe36f31357b567a74fdd"
        ),
    ]
)
