# SDK Architecture Overview

Deep dive into the BabLanguageSDK architecture for iOS developers.

## Architecture Layers

```
┌─────────────────────────────────────────────────────────────────┐
│                        iOS Application                          │
│                    (SwiftUI / UIKit)                            │
├─────────────────────────────────────────────────────────────────┤
│                         BrainSDK                                │
│  • Entry point for all SDK operations                           │
│  • Exposes StateFlow<T> properties for reactive observation     │
│  • Coordinates repositories, store, and AI provider             │
├─────────────────────────────────────────────────────────────────┤
│                        DialogStore                              │
│  • MVI (Model-View-Intent) state machine                        │
│  • Receives Intents, executes business logic                    │
│  • Produces Packets for network sync                            │
│  • Reduces incoming Packets to update state                     │
├─────────────────────────────────────────────────────────────────┤
│                     Domain Services                             │
│  • SRSScheduler: SM-2 spaced repetition algorithm               │
│  • XPCalculator: Gamification XP calculation                    │
├─────────────────────────────────────────────────────────────────┤
│                      Repositories                               │
│  • UserProfileRepository: User profile persistence              │
│  • VocabularyRepository: Word database                          │
│  • ProgressRepository: XP, levels, achievements                 │
│  • DialogHistoryRepository: Saved sessions                      │
├─────────────────────────────────────────────────────────────────┤
│                     Infrastructure                              │
│  ┌─────────────────────────┬───────────────────────────────┐   │
│  │      AIProvider         │      NetworkSession           │   │
│  │  • MockAIProvider       │  • LoopbackNetworkSession     │   │
│  │  • NativeLLMProvider    │  • BleHostSession             │   │
│  │  • CloudAIProvider      │  • BleClientSession           │   │
│  └─────────────────────────┴───────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## MVI Pattern

The SDK uses Model-View-Intent (MVI) for unidirectional data flow:

```
     ┌──────────────────────────────────────────────────────┐
     │                                                      │
     ▼                                                      │
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐   │
│  View   │───▶│  Intent │───▶│ Execute │───▶│ Packet  │───┘
│ (Swift) │    │         │    │         │    │         │
└─────────┘    └─────────┘    └─────────┘    └────┬────┘
     ▲                                            │
     │                                            ▼
┌─────────┐                               ┌─────────────┐
│  State  │◀──────────────────────────────│   Reduce    │
│         │                               │             │
└─────────┘                               └─────────────┘
```

### Flow Example: Generate Dialog

1. **View** calls `sdk.generate()`
2. **Intent** `Intent.Generate` is dispatched to DialogStore
3. **Execute** creates DialogContext, calls AIProvider.generate()
4. **Packet** `PacketPayload.DialogLineAdded` is sent to NetworkSession
5. **Reduce** handles packet, appends line to dialogHistory
6. **State** updates, SwiftUI view recomposes

---

## Key Data Models

### SessionState

The central state object containing all game information:

```kotlin
data class SessionState(
    val mode: SessionMode,                    // SOLO, HOST, CLIENT
    val connectionStatus: ConnectionStatus,  // DISCONNECTED, CONNECTING, CONNECTED
    val peers: List<Participant>,             // Connected players
    val localPeerId: String,                  // This device's ID
    
    val scenario: Scenario?,                  // Current scenario
    val roles: Map<String, Role>,             // Player ID → Role mapping
    val dialogHistory: List<DialogLine>,      // All dialog lines
    val currentPhase: GamePhase,              // LOBBY, ACTIVE, FINISHED, etc.
    
    val pendingVote: PendingVote?,            // Active vote
    val voteResults: Map<String, Boolean>,    // Vote outcomes
    
    val vectorClock: VectorClock,             // CRDT sync
    val lastSyncTimestamp: Long,
    
    // Pedagogical state
    val playerContexts: Map<String, PlayerContext>,
    val activePlotTwist: PlotTwist?,
    val recentFeedback: List<Feedback>,
    val sessionStats: SessionStats?
)
```

### DialogLine

A single line of dialog:

```kotlin
data class DialogLine(
    val id: String,
    val speakerId: String,
    val roleName: String,
    val textNative: String,      // "Hola, ¿qué desea?"
    val textTranslated: String,  // "Hello, what would you like?"
    val timestamp: Long
)
```

### VocabularyEntry

A word in the user's vocabulary:

```kotlin
data class VocabularyEntry(
    val id: String,
    val word: String,
    val translation: String,
    val language: LanguageCode,
    val partOfSpeech: PartOfSpeech?,
    val exampleSentence: String?,
    
    // SRS fields (SM-2 algorithm)
    val masteryLevel: Int,       // 0-5 (0=new, 5=mastered)
    val easeFactor: Float,       // Difficulty multiplier
    val intervalDays: Int,       // Days until next review
    val nextReviewAt: Long,      // Timestamp
    val totalReviews: Int,
    val correctReviews: Int,
    
    val firstSeenInDialogId: String?,
    val firstSeenAt: Long,
    val lastReviewedAt: Long?
)
```

---

## Spaced Repetition (SM-2)

The SRSScheduler implements the SM-2 algorithm for optimal review intervals:

### Review Quality Responses

| Quality | Effect on Interval | Effect on Ease Factor | Effect on Mastery |
|---------|-------------------|----------------------|-------------------|
| AGAIN | Reset to 1 day | -0.2 (min 1.3) | -1 (min 0) |
| HARD | × 1.2 | -0.15 | No change |
| GOOD | × ease factor | No change | +1 (max 5) |
| EASY | × ease factor × 1.3 | +0.15 | +1 (max 5) |

### Swift Usage

```swift
// Record a review
await sdk.recordVocabularyReview(entryId: entry.id, quality: .good)

// Get due reviews
let dueWords = await sdk.getVocabularyForReview(limit: 20)

// Create new entry
let entry = await sdk.createVocabularyEntry(
    word: "café",
    translation: "coffee"
)
await sdk.addToVocabulary(entry: entry)
```

---

## XP & Gamification

### XP Calculation

```kotlin
object XPCalculator {
    fun calculateSessionXP(
        linesSpoken: Int,
        newVocabulary: Int,
        errorsDetected: Int,
        errorsCorrected: Int,
        isMultiplayer: Boolean,
        helpGiven: Int,
        currentStreak: Int
    ): XPBreakdown
}
```

### XP Breakdown

| Component | Formula |
|-----------|---------|
| Dialog Completion | 50 XP (base) |
| Vocabulary Bonus | 10 XP × new words |
| Accuracy Bonus | Up to 30 XP based on (1 - error rate) |
| Collaboration Bonus | 25 XP + 10 XP × help given (multiplayer only) |
| Streak Bonus | 5% per streak day (max 50%) |

### Level Progression

Exponential leveling: Level N requires sum(i × 100) for i = 1 to N-1

| Level | Total XP Required |
|-------|-------------------|
| 1 | 0 |
| 2 | 100 |
| 3 | 300 |
| 4 | 600 |
| 5 | 1000 |

---

## Pedagogical Features

### 1. Plot Twists

Unexpected events injected by the AI or host:

```swift
// Host triggers a plot twist
sdk.triggerPlotTwist(description: "A famous celebrity walks into the cafe!")
```

### 2. Secret Objectives

Hidden goals for players (Information Gaps):

```swift
// Assign secret objective to player
sdk.setSecretObjective(
    playerId: "player-123",
    objective: "You have a $50 budget but don't reveal the exact amount"
)
```

### 3. Hints (Safety Net)

| Hint Level | Description |
|------------|-------------|
| `VISUAL_CLUE` | Image or icon hint |
| `STARTER_WORDS` | First few words of response |
| `FULL_TRANSLATION` | Complete translation |

```swift
// Request a hint
sdk.requestHint(level: .starterWords)
```

### 4. Narrative Recasts

In-character error correction:

```
Player: "Yo quiero un cafe calor"
AI (Barista): "Ah, un café CALIENTE. ¡Por supuesto!"
```

---

## Multiplayer Architecture

### BLE Session Modes

```
┌─────────────────┐         ┌─────────────────┐
│      HOST       │◀───────▶│     CLIENT      │
│ BleHostSession  │   BLE   │ BleClientSession│
└────────┬────────┘         └────────┬────────┘
         │                           │
         ▼                           ▼
┌─────────────────┐         ┌─────────────────┐
│   DialogStore   │         │   DialogStore   │
│ (Primary State) │         │ (Synced State)  │
└─────────────────┘         └─────────────────┘
```

### Vector Clock Sync

CRDT-style conflict resolution using Lamport timestamps:

```kotlin
data class VectorClock(val timestamps: Map<String, Long>) {
    fun increment(peerId: String): VectorClock
    fun merge(other: VectorClock): VectorClock
    fun compare(other: VectorClock): Comparison  // BEFORE, AFTER, CONCURRENT, EQUAL
}
```

### Packet Types

| Type | Description |
|------|-------------|
| `FULL_STATE_SNAPSHOT` | Complete state sync |
| `DIALOG_LINE_ADDED` | New dialog line |
| `VOTE_REQUEST` | Request vote |
| `VOTE_CAST` | Cast vote |
| `HANDSHAKE` | New peer joined |
| `HEARTBEAT` | Keep-alive |

---

## Repository Pattern

All persistence is abstracted behind interfaces, allowing custom implementations:

### Default: In-Memory

```kotlin
class InMemoryVocabularyRepository : VocabularyRepository {
    private val entries = mutableMapOf<String, VocabularyEntry>()
    // ...
}
```

### Custom: Core Data

```swift
class CoreDataVocabularyRepository: VocabularyRepository {
    let container: NSPersistentContainer
    
    func getAll(language: LanguageCode) async -> [VocabularyEntry] {
        // Fetch from Core Data
    }
    
    func upsert(entry: VocabularyEntry) async {
        // Save to Core Data
    }
}
```

### Injection

```swift
let sdk = BrainSDK(
    userProfileRepository: CoreDataUserProfileRepository(),
    vocabularyRepository: CoreDataVocabularyRepository(),
    progressRepository: CoreDataProgressRepository(),
    dialogHistoryRepository: CoreDataDialogHistoryRepository()
)
```

---

## AIProvider Interface

Implement custom AI backends:

```kotlin
interface AIProvider {
    suspend fun generate(context: DialogContext): DialogLine
}

data class DialogContext(
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val previousLines: List<DialogLine>
)
```

### Available Providers

| Provider | Description | Requirements |
|----------|-------------|--------------|
| `MockAIProvider` | Canned responses for testing | None |
| `NativeLLMProvider` | iOS Foundation Models | iOS 26+ |
| Custom | Your implementation | Implement `AIProvider` |

---

## Thread Safety

- All SDK methods are thread-safe
- StateFlow can be observed from any thread
- UI updates should be dispatched to MainActor

```swift
Task {
    for await state in sdk.state {
        await MainActor.run {
            // Update UI
        }
    }
}
```

---

## Memory Management

- SDK uses Kotlin's structured concurrency
- Observation tasks should be cancelled when views disappear
- Repositories use in-memory storage by default (inject persistent repos for production)

```swift
class ViewModel: ObservableObject {
    private var observationTask: Task<Void, Never>?
    
    func startObserving() {
        observationTask = Task {
            for await state in sdk.state {
                // ...
            }
        }
    }
    
    deinit {
        observationTask?.cancel()
    }
}
```
