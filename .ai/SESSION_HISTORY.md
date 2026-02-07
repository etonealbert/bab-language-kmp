# Bring a Brain - Development Session History

> Chronicle of major development decisions and implementations.

---

## Session: February 2025 - Initial SDK Development

### Phase 1: Walking Skeleton

**Goal**: Create minimal viable SDK structure with TDD approach.

**Completed**:

1. **VectorClock** - CRDT-style logical timestamps
   - `increment(nodeId)` - Advance clock for a node
   - `merge(other)` - Combine two clocks (max of each)
   - `happenedBefore(other)` - Causal ordering comparison

2. **DialogLine** - Single dialog entry
   - Native text, translated text, speaker ID
   - VectorClock timestamp for ordering

3. **Participant** - Player representation
   - ID, name, assigned role
   - Connection status

4. **Packet** - Network message types (sealed class)
   - `DialogAdded`, `StateSync`, `PlayerJoined`, `PlayerLeft`
   - JSON serializable for network transport

5. **SessionState** - Complete game state
   - Mode (SOLO/HOST/CLIENT)
   - Dialog history, participants, scenario
   - Current phase, vector clock

6. **NetworkSession** interface
   - `incomingPackets: Flow<Packet>`
   - `send(packet)`, `close()`

7. **LoopbackNetworkSession**
   - Immediately echoes packets back
   - Used for Solo mode

8. **AIProvider** interface
   - `generate(prompt, context): DialogLine`
   - `isAvailable(): Boolean`

9. **MockAIProvider**
   - Returns canned responses
   - For testing and development

10. **DialogStore** - MVI state machine
    - Intent handling (Executor)
    - State reduction (Reducer)
    - StateFlow emission

11. **BrainSDK** - Entry point
    - Public API surface
    - Wires up components

**Tests**: 30+ unit tests, all passing

---

### Phase 2: Sync Engine (BLE)

**Goal**: Enable offline multiplayer via Bluetooth Low Energy.

**Key Decisions**:

1. **Star Topology**: Host = Central, Clients = Peripherals
   - Simpler than mesh
   - Host already authoritative

2. **Authoritative Host**: All actions validated by host
   - Host broadcasts confirmed state
   - No client-side prediction

3. **Packet Fragmentation**: BLE has ~512 byte MTU
   - `PacketFragmenter` splits large packets
   - Reassembles on receive

**Completed**:

1. **BleConstants**
   - Service UUID: `550e8400-e29b-41d4-a716-446655440000`
   - Characteristic UUIDs for TX/RX
   - MTU constants

2. **PacketFragmenter**
   - `fragment(bytes, mtu): List<ByteArray>`
   - `reassemble(fragments): ByteArray`
   - Header format: [total][index][payload]

3. **BleScanner** (expect/actual)
   - `scan(): Flow<DiscoveredDevice>`
   - Uses Kable library
   - Platform-specific implementations

4. **BleHostSession**
   - Manages GATT server
   - Tracks connected clients
   - Broadcasts state to all

5. **BleClientSession**
   - Connects to host
   - Receives state updates
   - Sends actions to host

6. **DialogStore Updates**
   - `HostGame` intent - Start hosting
   - `JoinGame` intent - Join as client
   - Mode transitions in Reducer

7. **BrainSDK Updates**
   - `hostGame()`, `joinGame()`, `scanForHosts()`
   - `leaveGame()` for cleanup

8. **MultiplayerSimulationTest**
   - Integration test simulating host + 2 clients
   - Verifies state sync

**Tests**: 45+ tests total

---

### Phase 3: iOS 26 Foundation Model Docs

**Goal**: Document integration with Apple's on-device LLM.

**Key Decisions**:

1. **iOS-Only Feature**: Android has no equivalent
   - iOS 26+ required (beta as of Feb 2025)
   - Requires Apple Silicon device

2. **Bridge Pattern**: Swift implementation, Kotlin interface
   - Swift: `IOSLLMBridge` using FoundationModels framework
   - Kotlin: `NativeLLMProvider` expect/actual

3. **Fallback Strategy**: 
   - Native LLM → Cloud AI → Mock AI

**Completed**:

1. **NativeLLMProvider.kt** (commonMain - expect)
   ```kotlin
   expect fun createNativeLLMBridge(): NativeLLMBridge?
   ```

2. **NativeLLMProvider.ios.kt** (iosMain - actual)
   - Bridges to Swift IOSLLMBridge
   - Handles availability checking

3. **NativeLLMProvider.android.kt** (androidMain - actual)
   - Returns null (not available)

4. **DeviceCapabilities.kt**
   - `AICapabilities` data class
   - `hasNativeLLM`, `hasCloudAI` flags

5. **Integration Guide**
   - `docs/ios-foundation-model-integration.md`
   - Step-by-step Swift implementation
   - Memory management guidance
   - Structured output with @Generable

6. **BrainSDK Updates**
   - Constructor accepts optional `AIProvider`
   - Auto-detects native LLM if available
   - Exposes `aiCapabilities` property

---

### Framework Rename

**Change**: Renamed iOS framework from `Shared` to `BabLanguageSDK`

**Files Modified**:
- `composeApp/build.gradle.kts` - XCFramework name
- `README.md` - Import statements, build commands
- `docs/ios-foundation-model-integration.md` - Import statements

**New Build Command**:
```bash
./gradlew :composeApp:assembleBabLanguageSDKXCFramework
```

**Swift Import**:
```swift
import BabLanguageSDK
```

---

### Phase 4: Language Learning Features Implementation

**Date**: 2026-02-05

**Goal**: Implement pedagogically-sound language learning features based on approved design.

**Completed**:

#### 4A: Domain Models (Prior Session)
- `UserProfile.kt` - Full learner profile with CEFR levels, interests, goals
- `VocabularyEntry.kt` - Word with SRS fields (mastery 0-5, ease factor, intervals)
- `Feedback.kt` - NarrativeRecast, PrivateNudge, SessionReport sealed class
- `PedagogicalModels.kt` - PlotTwist, SecretObjective, PlayerContext, NudgeRequest
- `UserProgress.kt` - Streaks, XP, levels, achievements
- `SessionStats.kt` - Per-session performance metrics
- `CEFRLevel.kt` - A1-C2 proficiency levels
- `LanguageCode.kt` - ISO language codes

#### 4B: Domain Services
- `SRSScheduler.kt` - SM-2 inspired spaced repetition algorithm
  - Calculates next review based on quality rating (0-5)
  - Adjusts ease factor dynamically
  - Tracks mastery levels (0-5)
  - 15 unit tests
- `XPCalculator.kt` - Gamification XP calculation
  - Base XP for dialog participation
  - Bonuses for first correct, streaks, difficulty
  - Collaborative multipliers
  - 12 unit tests

#### 4C: Repository Interfaces & Implementations
- `UserProfileRepository.kt` - Profile persistence interface
- `VocabularyRepository.kt` - Vocabulary CRUD with SRS queries
- `ProgressRepository.kt` - XP, achievements, streaks
- `DialogHistoryRepository.kt` - Saved sessions with `SavedSession` model
- `InMemoryUserProfileRepository.kt` - Default in-memory implementation
- `InMemoryVocabularyRepository.kt` - Default in-memory implementation
- `InMemoryProgressRepository.kt` - Default in-memory implementation
- `InMemoryDialogHistoryRepository.kt` - Default in-memory implementation

#### 4D: SDK & Store Updates
- `SessionState.kt` - Added pedagogical fields:
  - `playerContexts: Map<String, PlayerContext>` - Per-player difficulty contexts
  - `activePlotTwist: PlotTwist?` - Current AI Director intervention
  - `recentFeedback: List<Feedback>` - Recent corrections/nudges
  - `sessionStats: SessionStats?` - Current session metrics
- `DialogStore.kt` - New intents:
  - `RequestHint` - Safety net for anxious learners
  - `TriggerPlotTwist` - AI Director interventions
  - `SetSecretObjective` - Information gaps
  - `EndSession` - Clean session termination
- `BrainSDK.kt` - Major rewrite with:
  - Repository injection pattern (UserProfile, Vocabulary, Progress, DialogHistory)
  - New StateFlows: `userProfile`, `vocabularyStats`, `dueReviews`, `progress`
  - Onboarding methods: `completeOnboarding()`, `isOnboardingRequired()`, `updateProfile()`
  - Vocabulary methods: `getVocabularyForReview()`, `recordVocabularyReview()`, `addToVocabulary()`, `createVocabularyEntry()`
  - Progress methods: `getProgress()`, `getSessionHistory()`
  - Pedagogical methods: `requestHint()`, `triggerPlotTwist()`, `setSecretObjective()`
  - Lifecycle: `endSession()`
- `VectorClock.kt` - Changed from `@JvmInline value class` to `data class` for KMP compatibility
- `NativeLLMProvider.kt` - Fixed `System.currentTimeMillis()` to `Clock.System.now().toEpochMilliseconds()`

#### 4E: iOS Documentation
- `docs/ios/integration-guide.md` - Comprehensive integration guide (~450 lines)
  - SDK installation via SPM
  - SwiftUI patterns for state observation
  - Session lifecycle management
  - Vocabulary and SRS integration
  - Progress tracking and gamification
- `docs/ios/sdk-architecture.md` - Architecture deep dive (~350 lines)
  - MVI pattern explanation
  - State flow diagrams
  - Repository injection patterns
  - Error handling strategies
- `docs/ios/api-reference.md` - Swift API quick reference (~400 lines)
  - All public classes and methods
  - Swift-friendly type mappings
  - Code examples for each API

#### 4F: Build Fixes
- Fixed `AndroidManifest.xml` - Removed non-existent `MainActivity` reference
  - Now a minimal SDK manifest with only BLE permissions
  - Lint errors resolved

#### 4G: Native Persistence Documentation
- `docs/ios/coredata-persistence-guide.md` - CoreData implementation guide (~600 lines)
  - Complete data model specifications for all 6 entities
  - CoreData entity designs with relationships and indexes
  - Swift protocol implementations for all 4 repository interfaces
  - SDK integration patterns with dependency injection
  - Migration strategies and best practices
- `docs/android/room-persistence-guide.md` - Room implementation guide (~700 lines)
  - Gradle setup with KSP annotation processing
  - Room entity definitions with proper type mappings
  - DAO interfaces with efficient queries and indexes
  - Repository implementations bridging Room to SDK interfaces
  - Hilt/Dagger dependency injection examples
  - Migration strategies using Room's built-in tools

**Test Results**: 100 unit tests passing

**Build Status**: Clean build (no lint errors)

---

### Phase 4 Design: Language Learning Features

**Date**: 2026-02-05

**Goal**: Design comprehensive language learning features based on SLA pedagogical research.

**Research Sources**:
- "The Orchestrated Classroom" strategic framework for multiplayer AI in SLA
- Project description: `research-of-what-i-need/BAB-project-description.md`
- Web research on CEFR levels, spaced repetition, gamification patterns

**Design Decisions Made (via brainstorming session)**:

| Decision Area | Choice |
|---------------|--------|
| User Profile Scope | Full Profile (languages, CEFR, interests, goals, preferences) |
| Vocabulary System | Full SRS with SM-2 inspired algorithm |
| Pedagogical Features | All 5: Information Gaps, Asymmetric Difficulty, Narrative Recasts, Safety Net, Plot Twists |
| Progress Tracking | Full Gamification (streaks, XP, collaborative stats, achievements) |
| Feedback Architecture | Models + AI Decides (SDK provides models, AIProvider handles timing) |
| Persistence Strategy | Interface + Injection (repositories with in-memory defaults) |

**New Models Designed**:

1. **UserProfile** - Full learner profile with CEFR levels, interests, goals
2. **VocabularyEntry** - Word with SRS fields (mastery 0-5, ease factor, intervals)
3. **SRSScheduler** - SM-2 inspired algorithm for optimal review scheduling
4. **Feedback** (sealed class) - NarrativeRecast, PrivateNudge, SessionReport
5. **SecretObjective** - Information gap missions per player
6. **PlayerContext** - Asymmetric difficulty with per-player vocabulary hints
7. **PlotTwist** - AI Director interventions
8. **UserProgress** - Streaks, XP, levels, collaborative stats
9. **SessionStats** - Per-session performance metrics
10. **Achievement** - Gamification achievements

**New Interfaces Designed**:

1. `UserProfileRepository` - Profile persistence
2. `VocabularyRepository` - Vocabulary with SRS queries
3. `ProgressRepository` - Progress and achievements
4. `DialogHistoryRepository` - Saved sessions

**Key Pedagogical Concepts Implemented**:

| SLA Theory | Implementation |
|------------|----------------|
| Zone of Proximal Development | CEFR-based `PlayerContext` with appropriate vocabulary hints |
| Comprehensible Input (i+1) | Asymmetric prompts based on proficiency |
| Interaction Hypothesis | `SecretObjective` forces negotiation of meaning |
| Affective Filter | `PrivateNudge` safety net reduces anxiety |
| Task-Based Language Teaching | Information gaps require communication to complete tasks |

**Design Document**: `docs/plans/2026-02-05-language-learning-features-design.md`

---

## Architecture Decisions Record

### ADR-001: Unified Loopback Pattern

**Context**: Need consistent state management across Solo and Multiplayer.

**Decision**: ALL state mutations flow through NetworkSession → Reducer, even Solo.

**Consequences**:
- (+) Same code path for all modes
- (+) Easy to test
- (+) No special-casing
- (-) Slight overhead for Solo mode

### ADR-002: iOS-Only Offline Host

**Context**: Need AI for dialog generation in offline mode.

**Decision**: Only iOS devices (with Foundation Models) can host offline games.

**Consequences**:
- (+) Guaranteed AI availability for host
- (+) Better user experience
- (-) Android users can only join, not host offline

### ADR-003: Authoritative Host

**Context**: Need to prevent inconsistent state in multiplayer.

**Decision**: Host validates all actions, broadcasts confirmed state.

**Consequences**:
- (+) Single source of truth
- (+) No complex conflict resolution
- (-) Higher latency for clients
- (-) Host is single point of failure

### ADR-004: Static XCFramework

**Context**: Need to distribute iOS framework via SPM.

**Decision**: Use static framework, not dynamic.

**Consequences**:
- (+) Simpler distribution
- (+) No dylib loading issues
- (+) Better app startup time
- (-) Larger app binary size

### ADR-005: Repository Interface + Injection Pattern

**Context**: SDK needs to persist user profiles, vocabulary, and progress, but shouldn't mandate a specific database.

**Decision**: Define repository interfaces in SDK, provide in-memory implementations as defaults. Apps inject their own SQLDelight/CoreData implementations.

**Consequences**:
- (+) SDK remains lightweight (no SQLDelight dependency in core)
- (+) Apps can use their preferred persistence layer
- (+) Easy to test with in-memory implementations
- (-) Apps must implement repositories for production

### ADR-006: SM-2 Inspired SRS Algorithm

**Context**: Need spaced repetition for vocabulary retention.

**Decision**: Implement SM-2 inspired algorithm with mastery levels 0-5, ease factor, and interval scheduling.

**Consequences**:
- (+) Proven algorithm used by Anki
- (+) Adapts to learner performance
- (+) Simple enough to implement in pure Kotlin
- (-) May need tuning for language learning context

### ADR-007: AI-Driven Feedback Timing

**Context**: Need to decide where error detection and feedback logic lives.

**Decision**: SDK provides data models for errors, recasts, and reports. AIProvider implementation decides WHEN and HOW to give feedback via prompt engineering.

**Consequences**:
- (+) Flexibility across different LLM implementations
- (+) SDK stays focused on data models
- (+) Prompt engineering can evolve independently
- (-) Feedback quality depends on AIProvider implementation

### ADR-008: Asymmetric Difficulty via PlayerContext

**Context**: Mixed-proficiency groups need different prompts.

**Decision**: Extend DialogContext with per-player PlayerContext containing CEFR level, vocabulary hints, and grammar focus.

**Consequences**:
- (+) Enables learning together despite skill gaps
- (+) Each player stays in their ZPD
- (+) Host can see all contexts, players see only their own
- (-) Increases AIProvider prompt complexity

---

## Known Issues & Workarounds

### JDK Compatibility

**Issue**: JDK 25 causes Gradle build failures.

**Workaround**: Use JDK 17.

### Windows WSL

**Issue**: Gradle wrapper doesn't work directly in WSL.

**Workaround**: Use `cmd.exe /c "gradlew.bat ..."`.

### iOS Simulator

**Issue**: BLE and Foundation Models don't work in simulator.

**Workaround**: Test on physical device.

---

## Future Considerations

### Phase 5: WebSocket Backend (NEXT)

- Rust server (Axum framework)
- WebSocket NetworkSession implementation
- Matchmaking service
- Cloud AI providers (OpenAI, Claude)

### Phase 6: Content System

- Scenario editor
- Community content
- Advanced vocabulary tools

### Phase 7: Polish

- Voice input/output
- Pronunciation scoring
- Multi-language scenarios

---

## Session: February 7, 2025 - BLE Multiplayer SDK Implementation

### Goal
Implement the SDK-side multiplayer types and methods designed in the BLE multiplayer design document.

### Completed

#### New Model Files Created
1. **`PronunciationModels.kt`** - Speech recognition results
   - `PronunciationResult` - Accuracy, errors, duration, skipped flag
   - `WordError` - Per-word mispronunciation details
   - `LineVisibility` enum - PRIVATE (reading) vs COMMITTED (theater view)
   - Factory methods: `PronunciationResult.skipped()`, `PronunciationResult.perfect(duration)`

2. **`LobbyModels.kt`** - Multiplayer lobby state
   - `LobbyPlayer` - Peer ID, name, assigned role, ready state, connection quality
   - `ConnectionQuality` enum - EXCELLENT, GOOD, FAIR, POOR
   - `ConnectedPeer` - Basic peer info for tracking

3. **`GamificationModels.kt`** - Scoring and leaderboards
   - `PlayerStats` - Lines completed, errors, perfect lines, streaks, XP
   - `SessionLeaderboard` - Real-time rankings
   - `PlayerRanking` - Rank, score, highlight text
   - `SessionSummary` - End-game statistics
   - `UnlockedAchievement` - Multiplayer achievement unlocks
   - `LineXPBreakdown` - Per-line XP calculation

4. **`OutgoingPacket.kt`** - BLE transmission wrapper
   - `targetPeerId` - null for broadcast, specific ID for unicast
   - `data: ByteArray` - Serialized packet bytes
   - Factory methods: `broadcast()`, `unicast()`

#### Enhanced Existing Files

1. **`DialogLine.kt`** - Added multiplayer fields:
   - `assignedToPlayerId: String` - Who should read this line
   - `visibility: LineVisibility` - PRIVATE while reading, COMMITTED after
   - `pronunciationResult: PronunciationResult?` - Filled when completed

2. **`SessionState.kt`** - Added 12 multiplayer fields:
   - `lobbyPlayers: List<LobbyPlayer>` - Players in lobby
   - `isAdvertising: Boolean` - BLE advertising state
   - `connectedPeers: List<ConnectedPeer>` - Connected devices
   - `currentTurnPlayerId: String?` - Whose turn
   - `pendingLine: DialogLine?` - Line being read (PRIVATE)
   - `committedHistory: List<DialogLine>` - Theater view
   - `playerStats: Map<String, PlayerStats>` - Per-player performance
   - `sessionLeaderboard: SessionLeaderboard?` - Rankings
   - `sessionSummary: SessionSummary?` - End-game summary

3. **`Packet.kt`** - Added 7 new packet types:
   - `LINE_COMPLETED`, `LINE_SKIPPED` - Turn completion
   - `ROLE_ASSIGNED`, `PLAYER_READY` - Lobby management
   - `GAME_STARTED`, `TURN_ADVANCED` - Game flow
   - `LEADERBOARD_UPDATE` - Real-time rankings
   - Corresponding `PacketPayload` sealed class variants

4. **`DialogStore.kt`** - Added 11 new Intent types:
   - `StartAdvertising`, `StopAdvertising` - BLE advertising
   - `PeerConnected`, `PeerDisconnected` - Connection events
   - `DataReceived` - BLE data callback
   - `CompleteLine`, `SkipLine` - Turn completion
   - `AssignRole`, `SetPlayerReady` - Lobby management
   - `StartMultiplayerGame` - Game start
   - Corresponding execute handlers and reduce handlers

5. **`BrainSDK.kt`** - Added 11 new public methods:
   - `startHostAdvertising(): String` - Returns service name
   - `stopHostAdvertising()` - Stop advertising
   - `onPeerConnected(peerId, peerName)` - Native BLE callback
   - `onPeerDisconnected(peerId)` - Native BLE callback
   - `onDataReceived(fromPeerId, data)` - Native BLE callback
   - `completeLine(lineId, result)` - Complete turn with pronunciation
   - `skipLine(lineId)` - Skip turn (no microphone)
   - `assignRole(playerId, roleId)` - Assign role in lobby
   - `setPlayerReady(playerId, isReady)` - Toggle ready state
   - `startMultiplayerGame()` - Start when all ready
   - `outgoingPackets: Flow<OutgoingPacket>` - For native BLE transmission

#### Test Files Created (51 new tests)
1. `PronunciationModelsTest.kt` - 5 tests
2. `LobbyModelsTest.kt` - 6 tests
3. `GamificationModelsTest.kt` - 7 tests
4. `OutgoingPacketTest.kt` - 5 tests
5. `MultiplayerPacketSerializationTest.kt` - 9 tests
6. `DialogStoreMultiplayerTest.kt` - 10 tests
7. `BrainSDKMultiplayerTest.kt` - 9 tests

#### Documentation Created
1. **`docs/ios/native-ble-implementation.md`** (~400 lines)
   - SDK ↔ Native BLE contract
   - Complete `BLEHostManager` with `CBPeripheralManager`
   - Kotlin ByteArray ↔ Swift Data conversion
   - Packet fragmentation/reassembly
   - Error handling and best practices

2. **`docs/ios/gamification-guide.md`** (~500 lines)
   - All gamification data models
   - XP calculation formula with examples
   - Speech recognition integration
   - Pronunciation evaluation logic
   - Complete SwiftUI views for game UI
   - Achievement system

#### Build Fixes
- Renamed `Achievement` → `UnlockedAchievement` (avoid duplicate with UserProgress.kt)
- Renamed `XPBreakdown` → `LineXPBreakdown` (avoid duplicate with UserProgress.kt)
- Added `kotlin.apple.xcodeCompatibility.nowarn=true` to gradle.properties

### Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| SDK handles game logic, native app handles BLE transport | Kable doesn't support peripheral mode; CoreBluetooth needed for hosting |
| `outgoingPackets: Flow` for native consumption | Native app subscribes and transmits via BLE |
| Pronunciation tracking in SDK, recognition in native | Speech APIs are platform-specific |
| Turn-based with PRIVATE→COMMITTED visibility | Other players see "reading..." until done |
| Separate `LineXPBreakdown` from session `XPBreakdown` | Different XP models for per-line vs per-session |

### Files Modified Summary
- 4 new model files in `domain/models/`
- 4 enhanced model files
- 1 enhanced store file
- 1 enhanced SDK file
- 7 new test files
- 2 new documentation files
- 1 gradle.properties update

### Test Count
- Previous: ~100 tests
- Added: ~51 tests
- Total: ~151 tests

---

## Session: February 7, 2025 (Continued) - Packet Serialization & Game Logic

### Goal
Complete the SDK-side BLE multiplayer implementation with packet serialization, XP calculation, turn advancement, and leaderboard updates.

### Completed

#### New Files Created

1. **`PacketSerializer.kt`** - Packet ↔ ByteArray conversion
   - `encode(packet): ByteArray` - JSON serialize for BLE transmission
   - `decode(data): Packet?` - Deserialize with null safety for corrupt data
   - `decodeOrThrow(data): Packet` - Throwing variant for explicit error handling

2. **`PacketSerializerTest.kt`** - 7 serialization tests
   - Round-trip tests for various packet types
   - Invalid/empty data handling tests

3. **`DialogStoreTurnAndXPTest.kt`** - 5 XP and turn logic tests
   - XP calculation verification
   - Streak tracking (increment on 90%+ accuracy, reset otherwise)
   - Best streak preservation
   - Leaderboard updates after line completion

#### Enhanced Files

1. **`DialogStore.kt`** - Major enhancements:
   - **`Intent.DataReceived`**: Now deserializes ByteArray → Packet using PacketSerializer
   - **`Intent.CompleteLine`**: Now includes:
     - XP calculation via `calculateLineXP()`
     - Stats update (lines, errors, perfect lines, streaks)
     - Leaderboard recalculation via `calculateLeaderboard()`
     - Turn advancement via `advanceTurn()`
     - Outgoing packet emission in multiplayer mode
   - **New private helper functions**:
     - `isMultiplayerMode()` - Check HOST/CLIENT mode
     - `emitPacketToPeers()` - Broadcast packets via `_outgoingPackets` Flow
     - `calculateLineXP()` - XP formula: base + accuracy + speed + fluency bonuses × streak multiplier
     - `calculateLeaderboard()` - Sort players by XP, assign ranks
     - `advanceTurn()` - Round-robin turn advancement

2. **`BrainSDK.kt`** - Cleanup:
   - Removed unused `_outgoingPackets` MutableSharedFlow
   - `outgoingPackets` now directly exposes `dialogStore.outgoingPackets`
   - Removed unused imports

### XP Calculation Formula

```kotlin
baseXP = 10
accuracyBonus = accuracy × 10 (0-10 points)
speedBonus = 5 if duration ≤ 3 seconds
fluencyBonus = 10 if accuracy ≥ 95% AND no errors
streakMultiplier = 1.0 + (streak × 0.1), capped at 2.0

totalXP = (baseXP + accuracyBonus + speedBonus + fluencyBonus) × streakMultiplier
```

**Example**: Perfect line (100% accuracy, 2s, 5-streak) = (10 + 10 + 5 + 10) × 1.5 = 52 XP

### Streak Logic

- **Increment**: accuracy ≥ 90%
- **Reset**: accuracy < 90%
- **Best streak**: Always preserved (max of current and historical best)

### Multiplayer Packet Flow (After Line Completion)

1. SDK calculates results locally
2. Emits `LINE_COMPLETED` packet with pronunciation result
3. Emits `TURN_ADVANCED` packet with next player ID
4. Emits `LEADERBOARD_UPDATE` packet with updated rankings
5. Native layer receives via `outgoingPackets` Flow → broadcasts via BLE

### Files Modified Summary
- 2 new source files
- 2 enhanced source files
- 2 new test files
- Session history update

### Test Count
- Previous: ~151 tests
- Added: ~12 tests (7 serialization + 5 XP/turn)
- Total: ~163 tests
