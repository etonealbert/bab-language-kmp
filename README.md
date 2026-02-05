# Bring a Brain SDK

**Headless KMP SDK for collaborative language learning games.**

A Kotlin Multiplatform library that provides all business logic for a role-playing language learning game where 1-4 players connect via Bluetooth (offline) or WebSocket (online) to act out AI-generated dialog scenarios.

## Features

- **Solo Mode**: Practice with AI partner using on-device or cloud LLM
- **Multiplayer Mode**: Host/Client architecture via Bluetooth Low Energy
- **Headless Architecture**: Pure logic, no UI - bring your own SwiftUI/Compose
- **iOS 26 Foundation Models**: Native on-device AI integration (optional)
- **Offline-First**: Works without internet using BLE mesh networking

## Platforms

| Platform | Artifact | Min Version |
|----------|----------|-------------|
| iOS | XCFramework | iOS 15+ (iOS 26 for native LLM) |
| Android | AAR | API 24+ |

---

## Quick Start

### iOS (SwiftUI)

```swift
import Shared

struct ContentView: View {
    let sdk = BrainSDK()
    
    var body: some View {
        VStack {
            // Observe state changes
            Text("Mode: \(sdk.state.value.mode)")
            
            Button("Start Solo Game") {
                sdk.startSoloGame(scenarioId: "coffee-shop", userRoleId: "customer")
            }
            
            Button("Generate Dialog") {
                sdk.generate()
            }
        }
    }
}
```

### Android (Jetpack Compose)

```kotlin
@Composable
fun GameScreen() {
    val sdk = remember { BrainSDK() }
    val state by sdk.state.collectAsState()
    
    Column {
        Text("Mode: ${state.mode}")
        Text("Dialog: ${state.dialogHistory.size} lines")
        
        Button(onClick = { 
            sdk.startSoloGame("coffee-shop", "customer") 
        }) {
            Text("Start Solo Game")
        }
        
        Button(onClick = { sdk.generate() }) {
            Text("Generate Dialog")
        }
    }
}
```

---

## API Reference

### BrainSDK

The main entry point for the SDK.

```kotlin
class BrainSDK(
    aiProvider: AIProvider? = null,  // Custom AI provider (optional)
    coroutineContext: CoroutineContext = Dispatchers.Default
)
```

#### Properties

| Property | Type | Description |
|----------|------|-------------|
| `state` | `StateFlow<SessionState>` | Observable game state |
| `aiCapabilities` | `AICapabilities` | Device AI capability info |

#### Methods

| Method | Description |
|--------|-------------|
| `startSoloGame(scenarioId, userRoleId)` | Start solo game with AI partner |
| `hostGame(scenarioId, userRoleId)` | Host multiplayer game (iOS only for offline) |
| `joinGame(hostDeviceId, userRoleId)` | Join hosted game as client |
| `scanForHosts(): Flow<DiscoveredDevice>` | Scan for nearby hosts via BLE |
| `generate()` | Generate next AI dialog line |
| `leaveGame()` | Leave current game session |
| `getAvailableScenarios(): List<Scenario>` | Get available game scenarios |

### SessionState

The complete game state, updated via `StateFlow`.

```kotlin
data class SessionState(
    val mode: SessionMode,           // SOLO, HOST, CLIENT
    val connectionStatus: ConnectionStatus,
    val scenario: Scenario?,
    val roles: Map<String, Role>,
    val dialogHistory: List<DialogLine>,
    val currentPhase: GamePhase,     // LOBBY, WAITING, ACTIVE, VOTING, FINISHED
    val vectorClock: VectorClock,
    // ...
)
```

### Game Modes

| Mode | Description |
|------|-------------|
| `SOLO` | Single player with AI partner |
| `HOST` | Multiplayer host (manages game state) |
| `CLIENT` | Multiplayer client (receives state from host) |

---

## Installation

### iOS (Swift Package Manager)

1. In Xcode: **File → Add Package Dependencies**
2. Enter: `https://github.com/etonealbert/bab-language-kmp`
3. Select branch: `main`

```swift
import Shared

let sdk = BrainSDK()
```

### Android (Gradle)

**settings.gradle.kts:**
```kotlin
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/etonealbert/bab-language-kmp")
            credentials {
                username = "YOUR_GITHUB_USERNAME"
                password = "YOUR_GITHUB_PAT"
            }
        }
    }
}
```

**build.gradle.kts:**
```kotlin
dependencies {
    implementation("com.bablabs:brain-sdk:1.0.0")
}
```

---

## iOS 26 Foundation Model Integration

For on-device AI on iOS 26+, see the integration guide:

📄 **[docs/ios-foundation-model-integration.md](docs/ios-foundation-model-integration.md)**

This enables:
- Zero-latency inference
- Complete privacy (no cloud)
- Works offline

```swift
// Check if native LLM is available
let caps = sdk.aiCapabilities
if caps.hasNativeLLM {
    print("Using on-device AI!")
}
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Your App (UI)                         │
│              SwiftUI / Jetpack Compose                   │
├─────────────────────────────────────────────────────────┤
│                      BrainSDK                            │
│         Entry point, exposes StateFlow<SessionState>     │
├─────────────────────────────────────────────────────────┤
│                    DialogStore (MVI)                     │
│         Intent → Execute → Packet → Reduce → State       │
├─────────────────────────────────────────────────────────┤
│              NetworkSession (Interface)                  │
│    LoopbackNetworkSession │ BleHostSession │ BleClient   │
├─────────────────────────────────────────────────────────┤
│                AIProvider (Interface)                    │
│      MockAIProvider │ NativeLLMProvider │ CloudProvider  │
└─────────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Unified Loopback Pattern**: ALL state mutations flow through `NetworkSession.incomingPackets → Reducer`, even in Solo mode
2. **Vector Clock Sync**: CRDT-style conflict resolution for multiplayer
3. **Headless**: No UI code in SDK - pure business logic only

---

## Project Structure

```
composeApp/src/
├── commonMain/kotlin/com/bablabs/bringabrainlanguage/
│   ├── BrainSDK.kt                    # Main entry point
│   ├── domain/
│   │   ├── interfaces/
│   │   │   ├── AIProvider.kt          # AI abstraction
│   │   │   └── NetworkSession.kt      # Network abstraction
│   │   ├── models/
│   │   │   ├── DialogLine.kt
│   │   │   ├── Packet.kt              # Network packet types
│   │   │   ├── SessionState.kt        # Game state
│   │   │   └── VectorClock.kt         # CRDT sync
│   │   └── stores/
│   │       └── DialogStore.kt         # MVI state machine
│   └── infrastructure/
│       ├── ai/
│       │   ├── MockAIProvider.kt      # Development/testing
│       │   ├── NativeLLMProvider.kt   # iOS 26 Foundation Models
│       │   └── DeviceCapabilities.kt  # Capability detection
│       └── network/
│           ├── LoopbackNetworkSession.kt  # Solo mode
│           └── ble/
│               ├── BleHostSession.kt      # Multiplayer host
│               ├── BleClientSession.kt    # Multiplayer client
│               └── PacketFragmenter.kt    # BLE MTU handling
├── androidMain/                       # Android-specific implementations
├── iosMain/                           # iOS-specific implementations
└── commonTest/                        # Unit & integration tests
```

---

## Development

### Prerequisites

- **Android Studio** Koala or newer
- **JDK 17** (not JDK 25)
- **Xcode 15+** (for iOS)

### Build Commands

| Task | Command |
|------|---------|
| Run all tests | `./gradlew :composeApp:allTests` |
| Build iOS XCFramework | `./gradlew :composeApp:assembleSharedXCFramework` |
| Build Android AAR | `./gradlew :composeApp:assembleRelease` |

### Test Coverage

- 45+ unit tests
- Integration tests for Solo and Multiplayer modes
- All tests pass on Android (iOS tests require device)

---

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1: Walking Skeleton | ✅ Complete | Core MVI, models, mock AI |
| Phase 2: Sync Engine (BLE) | ✅ Complete | Host/Client sessions, packet fragmentation |
| Phase 3: iOS 26 LLM Docs | ✅ Complete | Integration guide for Foundation Models |
| Phase 4: WebSocket Backend | 📋 Planned | Rust server, online multiplayer |

---

## License

[Your License Here]

---

## Contributing

This is a headless SDK. **DO NOT** add:
- `@Composable` functions
- UI components
- Platform-specific UI code

All UI belongs in the consuming apps, not here.
