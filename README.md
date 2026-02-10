# Bring a Brain SDK

**Headless KMP SDK for collaborative language learning games.**

A Kotlin Multiplatform library providing business logic for a role-playing language learning game where 1-4 players connect via Bluetooth (offline) or WebSocket (online) to act out AI-generated dialog scenarios.

## Features

| Feature | Description |
|---------|-------------|
| **Solo Mode** | Practice with AI partner using on-device or cloud LLM |
| **Multiplayer Mode** | Host/Client architecture via Bluetooth Low Energy |
| **Language Learning** | SRS vocabulary, CEFR levels, pronunciation tracking |
| **Gamification** | XP, streaks, leaderboards, achievements |
| **Chat History** | Premium users get persistent session history |
| **Headless** | Pure logic - bring your own SwiftUI/Compose UI |
| **iOS 26 LLM** | Native on-device AI via Foundation Models |
| **Offline-First** | Works without internet using BLE |

## Platforms

| Platform | Artifact | Min Version |
|----------|----------|-------------|
| iOS | XCFramework | iOS 15+ (iOS 26 for native LLM) |
| Android | AAR | API 24+ |

---

## Quick Start

### iOS (SwiftUI)

```swift
import BabLanguageSDK

struct GameView: View {
    let sdk = BrainSDK()
    @State private var state: SessionState?
    
    var body: some View {
        VStack {
            if let leaderboard = state?.sessionLeaderboard {
                ForEach(leaderboard.rankings, id: \.playerId) { ranking in
                    Text("\(ranking.rank). \(ranking.displayName): \(ranking.score) XP")
                }
            }
            
            Button("Start Solo") {
                sdk.startSoloGame(scenarioId: "coffee-shop", userRoleId: "customer")
            }
        }
        .task {
            for await s in sdk.state { state = s }
        }
    }
}
```

### Android (Compose)

```kotlin
@Composable
fun GameScreen() {
    val sdk = remember { BrainSDK() }
    val state by sdk.state.collectAsState()
    
    Column {
        state.sessionLeaderboard?.rankings?.forEach { ranking ->
            Text("${ranking.rank}. ${ranking.displayName}: ${ranking.score} XP")
        }
        
        Button(onClick = { sdk.startSoloGame("coffee-shop", "customer") }) {
            Text("Start Solo")
        }
    }
}
```

---

## Core API

### BrainSDK

```kotlin
class BrainSDK(
    aiProvider: AIProvider? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default
)
```

#### State Flows

| Property | Type | Description |
|----------|------|-------------|
| `state` | `StateFlow<SessionState>` | Game state (dialog, players, leaderboard) |
| `userProfile` | `StateFlow<UserProfile?>` | Learner profile with CEFR level |
| `vocabularyStats` | `StateFlow<VocabularyStats>` | SRS vocabulary statistics |
| `progress` | `StateFlow<UserProgress?>` | XP, streaks, achievements |
| `history` | `StateFlow<List<HistorySession>>` | Past session history (premium only) |
| `outgoingPackets` | `Flow<OutgoingPacket>` | Packets for native BLE to transmit |

#### Game Lifecycle

| Method | Description |
|--------|-------------|
| `startSoloGame(scenarioId, roleId)` | Start solo with AI partner |
| `hostGame(scenarioId, roleId)` | Host multiplayer (starts BLE advertising) |
| `joinGame(hostDeviceId, roleId)` | Join as client |
| `generate()` | Generate next AI dialog line |
| `leaveGame()` | Leave session |
| `endSession()` | End session and save to history (premium) |

#### Multiplayer (BLE Callbacks)

| Method | Description |
|--------|-------------|
| `startHostAdvertising(): String` | Start BLE peripheral, returns service name |
| `stopHostAdvertising()` | Stop advertising |
| `onPeerConnected(peerId, name)` | Native BLE calls when peer connects |
| `onPeerDisconnected(peerId)` | Native BLE calls when peer disconnects |
| `onDataReceived(peerId, data)` | Native BLE calls with received bytes |

#### Turn Management

| Method | Description |
|--------|-------------|
| `completeLine(lineId, result)` | Complete turn with pronunciation result |
| `skipLine(lineId)` | Skip turn (no microphone) |
| `assignRole(playerId, roleId)` | Assign role in lobby |
| `setPlayerReady(playerId, ready)` | Toggle ready state |
| `startMultiplayerGame()` | Start when all ready |

#### Vocabulary & Progress

| Method | Description |
|--------|-------------|
| `getVocabularyForReview(limit)` | Get SRS due words |
| `recordVocabularyReview(entryId, quality)` | Record review result |
| `addToVocabulary(entry)` | Add new word |
| `getProgress()` | Get XP, streaks, level |

---

## Key Models

### SessionState

```kotlin
data class SessionState(
    val mode: SessionMode,                    // SOLO, HOST, CLIENT
    val dialogHistory: List<DialogLine>,      // All dialog lines
    val committedHistory: List<DialogLine>,   // Theater view (completed lines)
    val lobbyPlayers: List<LobbyPlayer>,      // Players in lobby
    val currentTurnPlayerId: String?,         // Whose turn
    val playerStats: Map<String, PlayerStats>,// Per-player XP, streaks
    val sessionLeaderboard: SessionLeaderboard?,
    // ...
)
```

### DialogLine

```kotlin
data class DialogLine(
    val id: String,
    val speakerId: String,
    val roleName: String,
    val textNative: String,        // Target language
    val textTranslated: String,    // Native language
    val visibility: LineVisibility,// PRIVATE or COMMITTED
    val pronunciationResult: PronunciationResult?
)
```

### PronunciationResult

```kotlin
data class PronunciationResult(
    val accuracy: Float,           // 0.0 to 1.0
    val errorCount: Int,
    val wordErrors: List<WordError>,
    val duration: Long,            // Milliseconds
    val skipped: Boolean
)
```

### HistorySession

```kotlin
data class HistorySession(
    val sessionId: String,
    val scenarioTitle: String,
    val timestamp: Long,
    val targetLanguage: LanguageCode,
    val durationSeconds: Long,
    val dialogLines: List<DialogLine>
)
```

### PlayerStats

```kotlin
data class PlayerStats(
    val playerId: String,
    val linesCompleted: Int,
    val xpEarned: Int,
    val currentStreak: Int,
    val perfectLines: Int,
    val averageAccuracy: Float
)
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    Your App (UI)                         │
│              SwiftUI / Jetpack Compose                   │
├─────────────────────────────────────────────────────────┤
│                      BrainSDK                            │
│    state, userProfile, vocabularyStats, outgoingPackets │
├─────────────────────────────────────────────────────────┤
│                 DialogStore (MVI)                        │
│   Intent → Execute → Packet → NetworkSession → Reduce   │
├─────────────────────────────────────────────────────────┤
│           NetworkSession          │    AIProvider        │
│  Loopback │ BLE Host │ BLE Client │  Mock │ Native LLM  │
└─────────────────────────────────────────────────────────┘
```

### Data Flow

```
Solo Mode:
  Intent → Packet → LoopbackSession (echo) → Reducer → State

Multiplayer (Host):
  Intent → Packet → outgoingPackets Flow → Native BLE → Broadcast
  Native BLE receives → onDataReceived() → Reducer → State

Multiplayer (Client):
  Native BLE receives → onDataReceived() → Reducer → State
  Intent → Packet → outgoingPackets Flow → Native BLE → Send to Host
```

---

## Build Commands

```bash
# Run all tests (~163 tests)
./gradlew :composeApp:allTests

# Build iOS XCFramework
./gradlew :composeApp:assembleBabLanguageSDKXCFramework

# Build Android AAR
./gradlew :composeApp:assembleRelease

# Compile check
./gradlew :composeApp:compileKotlinMetadata
```

---

## Project Structure

```
composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/
├── BrainSDK.kt                      # Entry point
├── domain/
│   ├── interfaces/
│   │   ├── AIProvider.kt            # AI abstraction
│   │   ├── NetworkSession.kt        # Network abstraction
│   │   ├── HistoryRepository.kt      # Chat history interface
│   │   └── *Repository.kt           # Other persistence interfaces
│   ├── models/
│   │   ├── DialogLine.kt            # Dialog with native/translated text
│   │   ├── SessionState.kt          # Complete game state
│   │   ├── PronunciationModels.kt   # Speech recognition results
│   │   ├── GamificationModels.kt    # XP, leaderboards, achievements
│   │   ├── LobbyModels.kt           # Multiplayer lobby
│   │   ├── Packet.kt                # Network packet types
│   │   ├── PacketSerializer.kt      # Packet ↔ ByteArray
│   │   ├── HistorySession.kt        # Chat history model
│   │   └── UserProgress.kt          # Streaks, levels, XP
│   ├── stores/
│   │   └── DialogStore.kt           # MVI state machine
│   └── services/
│       └── SRSScheduler.kt          # Spaced repetition algorithm
└── infrastructure/
    ├── ai/
    │   ├── MockAIProvider.kt        # Testing
    │   └── DeviceCapabilities.kt    # LLM detection
    ├── network/
    │   ├── LoopbackNetworkSession.kt
    │   └── ble/                     # BLE utilities
    └── repositories/                # In-memory defaults + MockRemoteHistoryRepository
```

---

## Documentation

| Doc | Description |
|-----|-------------|
| `docs/ios/native-ble-implementation.md` | CoreBluetooth peripheral implementation |
| `docs/ios/gamification-guide.md` | XP, achievements, SwiftUI views |
| `docs/ios/integration-guide.md` | Full iOS integration guide |
| `docs/ios/coredata-persistence-guide.md` | CoreData for vocabulary/progress |
| `docs/android/room-persistence-guide.md` | Room database implementation |
| `docs/ios-foundation-model-integration.md` | iOS 26 on-device LLM |

---

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| Phase 1: Core SDK | ✅ | MVI, models, mock AI |
| Phase 2: BLE Multiplayer | ✅ | Host/Client, packet sync |
| Phase 3: Language Learning | ✅ | SRS, CEFR, vocabulary |
| Phase 4: Gamification | ✅ | XP, streaks, leaderboards |
| Phase 5: WebSocket Backend | 📋 | Rust server, online play |

---

## Requirements

- **JDK 17** (not 25)
- **Android Studio** Koala+
- **Xcode 15+** (iOS)

---

## License

[Your License Here]
