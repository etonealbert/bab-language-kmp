# Bring a Brain SDK (Core Logic)

**The "Brain" of the Bring a Brain Language Learning Platform.**

This repository contains the pure business logic, state management (MVI), and networking layer for the Bring a Brain application. It is strictly a **Logic SDK**—it contains NO UI components.

- **Architecture:** MVIKotlin + Decompose
- **Networking:** Ktor (WebSockets) + Kable (Bluetooth Low Energy)
- **State:** Kotlin Multiplatform (CommonMain)
- **Targets:** iOS (XCFramework) & Android (AAR)

---

## 📦 Installation for Consumers

### iOS Developers (SwiftUI)
This SDK is distributed as a binary XCFramework via Swift Package Manager (SPM).

1. Open your iOS Project in **Xcode**.
2. Go to **File > Add Package Dependencies...**
3. Enter the repository URL:
   ```text
   [https://github.com/YOUR_GITHUB_USERNAME/bab-language-kmp](https://github.com/YOUR_GITHUB_USERNAME/bab-language-kmp)

```

4. Select the **main** branch (or a specific Release Tag).
5. **Important:** If asked for authentication, you must have a GitHub Account connected to Xcode.

**Usage in Swift:**

```swift
import Shared // The SDK Module

// Initialize the Logic
let sdk = BrainSDK()
print(sdk.initialize())

```

### Android Developers (Jetpack Compose)

This SDK is distributed via GitHub Packages.

**1. Add the Repository**
In your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("[https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/bab-language-kmp](https://maven.pkg.github.com/YOUR_GITHUB_USERNAME/bab-language-kmp)")
            credentials {
                username = "YOUR_GITHUB_USERNAME"
                password = "YOUR_GITHUB_PAT" // Personal Access Token
            }
        }
    }
}

```

**2. Add the Dependency**
In your `libs.versions.toml`:

```toml
[libraries]
brain-sdk = { group = "com.bablabs", name = "brain-sdk", version = "1.0.0" }

```

**3. Implement**
In your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.brain.sdk)
}

```

---

## 🛠 Internal Development (Contributors)

If you are working **on** the SDK itself (adding logic, fixing bugs), follow these steps.

### Prerequisites

* **Android Studio** (Koala or newer)
* **JDK 17** (Do NOT use JDK 25)
* **Xcode 15+** (For iOS compilation)

### Build Commands

| Task | Command | Output Location |
| --- | --- | --- |
| **Build iOS Binary** | `./gradlew :composeApp:assembleSharedXCFramework` | `composeApp/build/XCFrameworks/release/` |
| **Build Android Binary** | `./gradlew :composeApp:assembleRelease` | `composeApp/build/outputs/aar/` |
| **Run Tests** | `./gradlew check` | `build/reports/` |

### Architecture Overview

The code lives in `composeApp/src/commonMain`. We follow a strict **"Headless"** architecture:

* **Decompose Components:** Handle navigation state (e.g., `RootComponent`, `GameComponent`).
* **MVI Stores:** Handle logic state (e.g., `DialogStore`, `ConnectionStore`).
* **Repositories:** Abstract data sources (Local DB vs. Bluetooth vs. Server).

**DO NOT add `@Composable` functions to `commonMain`.** UI belongs in the client apps, not here.


