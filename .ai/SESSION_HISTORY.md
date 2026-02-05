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

### Phase 4: Language Learning Features (NEXT)

Implementation of the approved design:
- User onboarding & profiles
- Vocabulary SRS system
- Pedagogical AI Director features
- Progress & gamification

See: `docs/plans/2026-02-05-language-learning-features-design.md`

### Phase 5: WebSocket Backend

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
