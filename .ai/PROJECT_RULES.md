# Bring a Brain - AI Project Rules & Context

> **Purpose**: This document provides complete context for AI assistants working on this project.
> Read this BEFORE making any changes.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture](#architecture)
3. [Key Design Decisions](#key-design-decisions)
4. [Implementation Status](#implementation-status)
5. [Code Patterns & Conventions](#code-patterns--conventions)
6. [File Structure](#file-structure)
7. [Build & Development](#build--development)
8. [Critical Constraints](#critical-constraints)
9. [API Reference](#api-reference)
10. [Future Roadmap](#future-roadmap)

---

## Project Overview

### What is "Bring a Brain"?

A **Kotlin Multiplatform (KMP) SDK** for a collaborative language learning game where 1-4 players connect via Bluetooth (offline) or WebSocket (online) to act out AI-generated dialog scenarios.

### The Core Idea

Players learn languages by role-playing scenarios (ordering coffee, hotel check-in, etc.) with either:
- **AI Partner** (Solo mode) - On-device LLM generates dialog
- **Human Partners** (Multiplayer) - 2-4 players connected via BLE

### Target Platforms

| Platform | Artifact | Min Version | Notes |
|----------|----------|-------------|-------|
| iOS | XCFramework (`BabLanguageSDK`) | iOS 15+ | iOS 26+ for native LLM |
| Android | AAR | API 24+ | No on-device LLM support |

### Key Value Propositions

1. **Headless Architecture** - SDK contains ZERO UI code; native apps bring their own SwiftUI/Compose
2. **Offline-First** - Works without internet using BLE mesh
3. **On-Device AI** - iOS 26 Foundation Models for privacy and zero-latency
4. **Cross-Platform State** - Single Kotlin codebase, consumed by both platforms

---

## Architecture

### High-Level Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Your App (UI)                             │
│              SwiftUI / Jetpack Compose                       │
├─────────────────────────────────────────────────────────────┤
│                      BrainSDK                                │
│         Entry point, exposes StateFlow<SessionState>         │
├─────────────────────────────────────────────────────────────┤
│                    DialogStore (MVI)                         │
│         Intent → Execute → Packet → Reduce → State           │
├─────────────────────────────────────────────────────────────┤
│              NetworkSession (Interface)                      │
│    LoopbackNetworkSession │ BleHostSession │ BleClientSession│
├─────────────────────────────────────────────────────────────┤
│                AIProvider (Interface)                        │
│      MockAIProvider │ NativeLLMProvider │ CloudProvider      │
└─────────────────────────────────────────────────────────────┘
```

### MVI Pattern (Model-View-Intent)

The SDK uses a strict MVI pattern:

```
User Action → Intent → Executor → Packet → NetworkSession → Reducer → New State
                                              ↑
                                     (loopback or BLE)
```

**Key Insight**: ALL state mutations flow through `NetworkSession.incomingPackets → Reducer`, even in Solo mode. This ensures:
- Consistent state management across all game modes
- Easy transition between Solo and Multiplayer
- Testable, predictable state changes

### Core Components

| Component | Role | File |
|-----------|------|------|
| `BrainSDK` | Entry point, public API | `BrainSDK.kt` |
| `DialogStore` | MVI state machine | `domain/stores/DialogStore.kt` |
| `SessionState` | Complete game state | `domain/models/SessionState.kt` |
| `NetworkSession` | Network abstraction | `domain/interfaces/NetworkSession.kt` |
| `AIProvider` | AI abstraction | `domain/interfaces/AIProvider.kt` |
| `VectorClock` | CRDT sync for multiplayer | `domain/models/VectorClock.kt` |

---

## Key Design Decisions

### 1. Unified Loopback Pattern

**Decision**: Even Solo mode goes through NetworkSession (LoopbackNetworkSession).

**Why**: 
- Same Reducer path for Solo/Multiplayer
- No special-casing in business logic
- Easy to test

```kotlin
// Solo mode: Intent → Executor → Packet → LoopbackNetworkSession → Reducer
// Multiplayer: Intent → Executor → Packet → BleSession → (network) → Reducer
```

### 2. iOS-Only Host for Offline

**Decision**: Only iOS devices can host offline games.

**Why**: 
- iOS 26 Foundation Models provide on-device AI
- Android has no equivalent on-device LLM
- Host needs AI to generate dialog

**Implication**: Android users can JOIN games but not HOST offline games.

### 3. Authoritative Host

**Decision**: Host validates all actions and broadcasts confirmed state.

**Why**:
- Prevents cheating/inconsistencies
- Single source of truth
- Simpler conflict resolution

```
Client Action → Send to Host → Host Validates → Host Broadcasts → All Clients Update
```

### 4. Star Topology BLE

**Decision**: Host = BLE Central, Clients = BLE Peripherals.

**Why**:
- Simpler than mesh networking
- Host already has authority
- Up to 3 simultaneous peripheral connections (iOS limit)

### 5. Vector Clock for Sync

**Decision**: Use CRDT-style Vector Clocks for ordering events.

**Why**:
- Handles out-of-order message delivery
- No central timestamp authority needed
- Mathematically correct conflict resolution

### 6. Injectable AIProvider

**Decision**: BrainSDK accepts custom AIProvider, with auto-detection of native LLM.

**Why**:
- Testability (MockAIProvider)
- Flexibility (cloud providers)
- Platform-specific implementations

```kotlin
// Default: auto-detect native LLM or fall back to Mock
val sdk = BrainSDK()

// Custom: inject your own provider
val sdk = BrainSDK(aiProvider = MyCloudProvider())
```

### 7. Static XCFramework

**Decision**: iOS framework is static, not dynamic.

**Why**:
- Simpler SPM distribution
- No dylib loading issues
- Better app startup time

---

## Implementation Status

### Phase 1: Walking Skeleton ✅ COMPLETE

All core models and interfaces implemented with TDD:

- [x] `VectorClock` - CRDT timestamps
- [x] `DialogLine` - Single dialog entry
- [x] `Participant` - Player representation
- [x] `Packet` - Network message types
- [x] `SessionState` - Complete game state
- [x] `NetworkSession` interface + `LoopbackNetworkSession`
- [x] `AIProvider` interface + `MockAIProvider`
- [x] `DialogStore` - MVI state machine with Reducer
- [x] `BrainSDK` - Entry point

### Phase 2: Sync Engine (BLE) ✅ COMPLETE

All 8 BLE tasks completed:

- [x] `BleConstants` - UUIDs, MTU values
- [x] `PacketFragmenter` - Fragment/reassemble packets for BLE MTU
- [x] `BleScanner` (expect/actual) - Discover nearby hosts
- [x] `BleHostSession` - Host manages multiple client connections
- [x] `BleClientSession` - Client connects to host
- [x] `DialogStore` updated with HostGame/JoinGame intents
- [x] `BrainSDK` updated with multiplayer methods
- [x] `MultiplayerSimulationTest` - Integration test

### Phase 3: iOS 26 Foundation Model Docs ✅ COMPLETE

- [x] `NativeLLMProvider.kt` (expect/actual interface)
- [x] `DeviceCapabilities.kt` for capability detection
- [x] Integration guide: `docs/ios-foundation-model-integration.md`
- [x] `BrainSDK` updated to support injectable AIProvider

### Phase 4: Language Learning Features 📋 DESIGNED (Pending Implementation)

Full design approved: `docs/plans/2026-02-05-language-learning-features-design.md`

**4A: User Onboarding & Profile**
- [ ] `UserProfile` model with CEFR levels, interests, goals
- [ ] `UserProfileRepository` interface + in-memory impl
- [ ] Onboarding flow in BrainSDK

**4B: Vocabulary & SRS**
- [ ] `VocabularyEntry` model with SM-2 SRS fields
- [ ] `SRSScheduler` service for spaced repetition
- [ ] `VocabularyRepository` interface + in-memory impl
- [ ] Vocabulary extraction from dialogs

**4C: Pedagogical Features (AI Director)**
- [ ] `SecretObjective` - Information gap missions
- [ ] `PlayerContext` - Asymmetric difficulty per player
- [ ] `Feedback` sealed class (NarrativeRecast, PrivateNudge, SessionReport)
- [ ] `PlotTwist` - Director interventions
- [ ] Extended `DialogContext` for asymmetric prompts

**4D: Progress & Gamification**
- [ ] `UserProgress` model with streaks, XP, levels
- [ ] `XPCalculator` service
- [ ] `SessionStats` and `Achievement` models
- [ ] `ProgressRepository` interface + in-memory impl

**4E: SDK Integration**
- [ ] Update `BrainSDK` with new methods
- [ ] Extend `DialogStore` with new intents
- [ ] Add new `Packet` payload types
- [ ] Update `SessionState` with pedagogical fields

### Phase 5: WebSocket Backend 📋 PLANNED

- [ ] Rust server for online multiplayer
- [ ] WebSocket NetworkSession implementation
- [ ] Matchmaking service
- [ ] Cloud AI provider integration

---

## Code Patterns & Conventions

### Kotlin Style

```kotlin
// Use data classes for models
data class DialogLine(
    val id: String,
    val speakerId: String,
    val textNative: String,
    val textTranslated: String,
    val timestamp: VectorClock
)

// Use sealed classes for ADTs
sealed class Intent {
    data class StartSoloGame(val scenarioId: String, val roleId: String) : Intent()
    data class Generate(val prompt: String) : Intent()
    object LeaveGame : Intent()
}

// Use interfaces for abstractions
interface NetworkSession {
    val incomingPackets: Flow<Packet>
    suspend fun send(packet: Packet)
    suspend fun close()
}
```

### expect/actual Pattern

For platform-specific code:

```kotlin
// commonMain - declare expectation
expect class BleScanner() {
    fun scan(): Flow<DiscoveredDevice>
}

// androidMain - Android implementation
actual class BleScanner {
    actual fun scan(): Flow<DiscoveredDevice> = flow {
        // Android BLE scanning with Kable
    }
}

// iosMain - iOS implementation
actual class BleScanner {
    actual fun scan(): Flow<DiscoveredDevice> = flow {
        // iOS BLE scanning with Kable
    }
}
```

### Test Patterns

```kotlin
class VectorClockTest {
    @Test
    fun `increment increases own counter`() {
        val clock = VectorClock(mapOf("A" to 1))
        val incremented = clock.increment("A")
        assertEquals(2, incremented.counters["A"])
    }
    
    @Test
    fun `merge takes max of each counter`() {
        val clock1 = VectorClock(mapOf("A" to 2, "B" to 1))
        val clock2 = VectorClock(mapOf("A" to 1, "B" to 3))
        val merged = clock1.merge(clock2)
        assertEquals(mapOf("A" to 2, "B" to 3), merged.counters)
    }
}
```

### Naming Conventions

| Element | Convention | Example |
|---------|------------|---------|
| Files | PascalCase | `DialogStore.kt` |
| Classes | PascalCase | `BleHostSession` |
| Functions | camelCase | `startSoloGame()` |
| Properties | camelCase | `dialogHistory` |
| Constants | SCREAMING_SNAKE | `SERVICE_UUID` |
| Test files | `*Test.kt` | `VectorClockTest.kt` |

---

## File Structure

```
composeApp/src/
├── commonMain/kotlin/com/bablabs/bringabrainlanguage/
│   ├── BrainSDK.kt                    # Main entry point
│   ├── domain/
│   │   ├── interfaces/
│   │   │   ├── AIProvider.kt          # AI abstraction
│   │   │   └── NetworkSession.kt      # Network abstraction
│   │   ├── models/
│   │   │   ├── DialogLine.kt          # Single dialog entry
│   │   │   ├── Packet.kt              # Network packet types
│   │   │   ├── Participant.kt         # Player representation
│   │   │   ├── SessionState.kt        # Complete game state
│   │   │   └── VectorClock.kt         # CRDT sync
│   │   └── stores/
│   │       └── DialogStore.kt         # MVI state machine
│   └── infrastructure/
│       ├── ai/
│       │   ├── MockAIProvider.kt      # Development/testing
│       │   ├── NativeLLMProvider.kt   # iOS 26 Foundation Models (expect)
│       │   └── DeviceCapabilities.kt  # Capability detection
│       └── network/
│           ├── LoopbackNetworkSession.kt  # Solo mode
│           └── ble/
│               ├── BleConstants.kt        # UUIDs, MTU
│               ├── BleScanner.kt          # Device discovery (expect)
│               ├── BleHostSession.kt      # Multiplayer host
│               ├── BleClientSession.kt    # Multiplayer client
│               └── PacketFragmenter.kt    # BLE MTU handling
├── androidMain/kotlin/.../infrastructure/
│   ├── ai/
│   │   └── NativeLLMProvider.android.kt   # Stub (no Android LLM)
│   └── network/ble/
│       └── BleScanner.android.kt          # Android BLE impl
├── iosMain/kotlin/.../infrastructure/
│   ├── ai/
│   │   └── NativeLLMProvider.ios.kt       # iOS Foundation Models bridge
│   └── network/ble/
│       └── BleScanner.ios.kt              # iOS BLE impl
└── commonTest/kotlin/...                   # 45+ unit tests
```

---

## Build & Development

### Prerequisites

- **Android Studio** Koala (2024.1) or newer
- **JDK 17** (NOT JDK 25 - causes compatibility issues)
- **Xcode 15+** (for iOS)

### Build Commands

| Task | Command |
|------|---------|
| Run all tests | `./gradlew :composeApp:allTests` |
| Build iOS XCFramework | `./gradlew :composeApp:assembleBabLanguageSDKXCFramework` |
| Build Android AAR | `./gradlew :composeApp:assembleRelease` |
| Publish to GitHub Packages | `./gradlew :composeApp:publish` |

### Windows WSL Note

Java is installed on Windows side, so use:

```bash
cmd.exe /c "cd /d C:\Users\alber\CodeBase\PET\BringABrain && gradlew.bat :composeApp:allTests"
```

### Test Coverage

- 45+ unit tests
- Integration tests for Solo and Multiplayer modes
- All tests pass on Android
- iOS tests require physical device (BLE, Foundation Models)

### Dependencies

Key libraries used:

| Library | Purpose | Version |
|---------|---------|---------|
| Kotlin Multiplatform | Cross-platform | 2.0.0 |
| Kable | BLE abstraction | 0.30.0 |
| Ktor | Networking | (via libs.versions) |
| MVIKotlin | MVI architecture | (via libs.versions) |
| Decompose | Navigation | (via libs.versions) |
| kotlinx-coroutines | Async | 1.8.0 |
| kotlinx-serialization | JSON | 1.6.3 |
| kotlinx-datetime | Timestamps | 0.6.0 |

---

## Critical Constraints

### DO NOT

1. **Add UI code to SDK**
   - No `@Composable` functions
   - No SwiftUI views
   - No platform-specific UI

2. **Suppress type errors**
   - Never use `as any`
   - Never use `@ts-ignore`
   - Never use `@Suppress("UNCHECKED_CAST")` without justification

3. **Break the MVI pattern**
   - All state changes MUST go through Reducer
   - No direct state mutation
   - No bypassing NetworkSession

4. **Hardcode platform-specific code in commonMain**
   - Use expect/actual pattern
   - Keep commonMain pure Kotlin

5. **Add unnecessary dependencies**
   - SDK should be minimal
   - Each dependency adds to app size

### ALWAYS

1. **Write tests first (TDD)**
   - Every feature needs tests
   - Tests document expected behavior

2. **Use sealed classes for ADTs**
   - Intent, Packet, GamePhase, etc.
   - Exhaustive when statements

3. **Make state immutable**
   - data classes with val
   - Copy on change

4. **Document public API**
   - KDoc for all public functions
   - Clear parameter descriptions

5. **Follow existing patterns**
   - Look at similar code before adding new
   - Consistency > cleverness

---

## API Reference

### BrainSDK

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
| `hostGame(scenarioId, userRoleId)` | Host multiplayer game (iOS only offline) |
| `joinGame(hostDeviceId, userRoleId)` | Join hosted game as client |
| `scanForHosts(): Flow<DiscoveredDevice>` | Scan for nearby hosts via BLE |
| `generate()` | Generate next AI dialog line |
| `leaveGame()` | Leave current game session |
| `getAvailableScenarios(): List<Scenario>` | Get available game scenarios |

### SessionState

```kotlin
data class SessionState(
    val mode: SessionMode,           // SOLO, HOST, CLIENT
    val connectionStatus: ConnectionStatus,
    val scenario: Scenario?,
    val roles: Map<String, Role>,
    val dialogHistory: List<DialogLine>,
    val currentPhase: GamePhase,     // LOBBY, WAITING, ACTIVE, VOTING, FINISHED
    val vectorClock: VectorClock,
    val participants: List<Participant>,
    val error: GameError?
)
```

### Game Modes

| Mode | Description |
|------|-------------|
| `SOLO` | Single player with AI partner |
| `HOST` | Multiplayer host (manages game state) |
| `CLIENT` | Multiplayer client (receives state from host) |

### Game Phases

| Phase | Description |
|-------|-------------|
| `LOBBY` | Waiting for players to join |
| `WAITING` | Game starting, loading scenario |
| `ACTIVE` | Game in progress |
| `VOTING` | Players voting on performance |
| `FINISHED` | Game complete, showing results |

---

## Future Roadmap

### Phase 4: WebSocket Backend

- Rust server for online multiplayer
- WebSocket NetworkSession implementation
- Matchmaking service
- Cloud AI provider integration (OpenAI, Claude)

### Phase 5: Content System

- Scenario creation tools
- Community scenarios
- Progress tracking
- Spaced repetition for vocabulary

### Phase 6: Advanced Features

- Voice input/output
- Pronunciation scoring
- Multi-language support
- Achievement system

---

## Quick Reference for AI Assistants

### When Asked to Add a Feature

1. Check if it fits the headless architecture (no UI in SDK)
2. Write tests first
3. Follow existing patterns in codebase
4. Use expect/actual for platform-specific code
5. Update this document if architecture changes

### When Asked to Fix a Bug

1. Write a failing test that reproduces the bug
2. Fix the bug
3. Verify test passes
4. Check for similar issues elsewhere

### When Asked About Architecture

- Refer to the Architecture section above
- Point to `docs/plans/` for detailed design docs
- Reference `docs/ios-foundation-model-integration.md` for iOS 26 LLM

### Common File Locations

| Need | Location |
|------|----------|
| Entry point | `BrainSDK.kt` |
| State machine | `domain/stores/DialogStore.kt` |
| Game state | `domain/models/SessionState.kt` |
| Network types | `domain/models/Packet.kt` |
| BLE code | `infrastructure/network/ble/` |
| AI code | `infrastructure/ai/` |
| Tests | `commonTest/` |
| Build config | `composeApp/build.gradle.kts` |
| iOS framework name | `BabLanguageSDK` |

---

*Last updated: February 2025*
*Phases 1-3 complete, Phase 4 planned*
