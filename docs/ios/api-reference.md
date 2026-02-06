# BabLanguageSDK - API Quick Reference

Swift-focused quick reference for iOS developers.

---

## SDK Initialization

```swift
import BabLanguageSDK

// Default initialization
let sdk = BrainSDK()

// With custom AI provider
let sdk = BrainSDK(aiProvider: MyCustomAIProvider())

// With custom persistence
let sdk = BrainSDK(
    userProfileRepository: CoreDataUserProfileRepository(),
    vocabularyRepository: CoreDataVocabularyRepository(),
    progressRepository: CoreDataProgressRepository(),
    dialogHistoryRepository: CoreDataDialogHistoryRepository()
)
```

---

## Observable Properties

| Property | Type | Description |
|----------|------|-------------|
| `state` | `StateFlow<SessionState>` | Game session state |
| `userProfile` | `StateFlow<UserProfile?>` | User's profile |
| `vocabularyStats` | `StateFlow<VocabularyStats>` | Vocabulary counts |
| `dueReviews` | `StateFlow<[VocabularyEntry]>` | Words due for review |
| `progress` | `StateFlow<UserProgress?>` | XP, levels, streaks |
| `aiCapabilities` | `AICapabilities` | Device AI info |

### Observing in Swift

```swift
Task {
    for await state in sdk.state {
        await MainActor.run {
            self.dialogLines = state.dialogHistory
        }
    }
}
```

---

## Onboarding

```swift
// Check if onboarding required
if sdk.isOnboardingRequired() {
    // Show onboarding UI
}

// Complete onboarding
let profile = UserProfile(
    id: UUID().uuidString,
    displayName: "Alice",
    nativeLanguage: "en",
    targetLanguages: [TargetLanguage(code: "es", proficiencyLevel: .a2, startedAt: now)],
    currentTargetLanguage: "es",
    interests: [.travel, .food],
    learningGoals: [.conversation],
    dailyGoalMinutes: 15,
    voiceSpeed: .normal,
    showTranslations: .onTap,
    onboardingCompleted: true,
    createdAt: now,
    lastActiveAt: now
)
await sdk.completeOnboarding(profile: profile)

// Update profile
await sdk.updateProfile { profile in
    profile.copy(dailyGoalMinutes: 20)
}
```

---

## Game Lifecycle

```swift
// Get available scenarios
let scenarios = sdk.getAvailableScenarios()

// Start solo game
sdk.startSoloGame(scenarioId: "coffee-shop", userRoleId: "customer")

// Host multiplayer game
sdk.hostGame(scenarioId: "the-heist", userRoleId: "detective")

// Scan for BLE hosts
let hosts = sdk.scanForHosts()
Task {
    for await device in hosts {
        print("Found: \(device.name)")
    }
}

// Join game
sdk.joinGame(hostDeviceId: "device-123", userRoleId: "thief")

// Generate AI dialog
sdk.generate()

// Leave game
sdk.leaveGame()

// End and save session
await sdk.endSession()
```

---

## Vocabulary

```swift
// Get due reviews
let dueWords = await sdk.getVocabularyForReview(limit: 20)

// Record review result
await sdk.recordVocabularyReview(entryId: "vocab-123", quality: .good)

// ReviewQuality options:
// .again  - Forgot completely
// .hard   - Difficult to recall
// .good   - Recalled correctly
// .easy   - Very easy

// Create new vocabulary entry
let entry = await sdk.createVocabularyEntry(
    word: "hola",
    translation: "hello"
)

// Add to vocabulary
await sdk.addToVocabulary(entry: entry)
```

---

## Progress & Stats

```swift
// Get current progress
let progress = await sdk.getProgress()
// progress.totalXP
// progress.currentLevel
// progress.currentStreak
// progress.longestStreak
// progress.totalWordsLearned

// Get session history
let sessions = await sdk.getSessionHistory(limit: 10)
// sessions[0].scenarioName
// sessions[0].playedAt
// sessions[0].durationMinutes
// sessions[0].dialogLines
```

---

## Pedagogical Features

```swift
// Request hint
sdk.requestHint()  // Default: .starterWords
sdk.requestHint(level: .visualClue)
sdk.requestHint(level: .fullTranslation)

// HintLevel options:
// .visualClue      - Image/icon hint
// .starterWords    - First few words
// .fullTranslation - Complete translation

// Trigger plot twist (host only)
sdk.triggerPlotTwist(description: "A fire alarm goes off!")

// Set secret objective (host only)
sdk.setSecretObjective(
    playerId: "player-456",
    objective: "Convince the other player to order dessert"
)
```

---

## SessionState Properties

```swift
let state = sdk.state.value

// Mode
state.mode           // .solo, .host, .client

// Connection
state.connectionStatus  // .disconnected, .connecting, .connected, .reconnecting

// Game
state.scenario       // Current scenario
state.roles          // [playerId: Role]
state.dialogHistory  // [DialogLine]
state.currentPhase   // .lobby, .active, .finished, etc.

// Multiplayer
state.peers          // [Participant]
state.localPeerId    // This device's ID
state.pendingVote    // Active vote if any
state.voteResults    // [playerId: Bool]

// Pedagogical
state.playerContexts // [playerId: PlayerContext]
state.activePlotTwist // Current twist if any
state.recentFeedback // Recent hints/recasts
state.sessionStats   // Current session stats
```

---

## DialogLine Properties

```swift
let line = state.dialogHistory.last!

line.id              // Unique ID
line.speakerId       // Who spoke (player ID or "ai-robot")
line.roleName        // Display name ("Barista", "Customer")
line.textNative      // "¿Qué desea ordenar?"
line.textTranslated  // "What would you like to order?"
line.timestamp       // Unix timestamp
```

---

## VocabularyEntry Properties

```swift
let entry = dueReviews.first!

entry.id              // Unique ID
entry.word            // "café"
entry.translation     // "coffee"
entry.language        // "es"
entry.partOfSpeech    // .noun, .verb, .adjective, etc.
entry.exampleSentence // "Quiero un café, por favor"

// SRS fields
entry.masteryLevel    // 0-5
entry.easeFactor      // Difficulty multiplier
entry.intervalDays    // Days until next review
entry.nextReviewAt    // Timestamp
entry.totalReviews    // Total review count
entry.correctReviews  // Correct count
```

---

## VocabularyStats Properties

```swift
let stats = sdk.vocabularyStats.value

stats.total          // Total words
stats.newCount       // Unseen words
stats.learningCount  // Mastery 1-2
stats.reviewingCount // Mastery 3-4
stats.masteredCount  // Mastery 5
stats.dueCount       // Due for review now
```

---

## UserProgress Properties

```swift
let progress = sdk.progress.value!

progress.totalXP              // Lifetime XP
progress.weeklyXP             // This week's XP
progress.currentLevel         // Current level
progress.currentStreak        // Days in a row
progress.longestStreak        // Best streak
progress.totalWordsLearned    // Vocabulary size
progress.wordsDueForReview    // Due now
progress.totalSessions        // Games played
progress.totalMinutesPlayed   // Time spent
progress.estimatedCEFR        // Estimated level
```

---

## Enums Reference

### SessionMode
```swift
.solo    // Playing alone with AI
.host    // Hosting multiplayer
.client  // Joined multiplayer
```

### GamePhase
```swift
.lobby          // Waiting for players
.roleSelection  // Choosing roles
.waiting        // Waiting to start
.active         // Game in progress
.voting         // Vote in progress
.finished       // Game complete
```

### ConnectionStatus
```swift
.disconnected   // Not connected
.connecting     // Establishing connection
.connected      // Connected
.reconnecting   // Lost connection, retrying
```

### CEFRLevel
```swift
.a1  // Beginner
.a2  // Elementary
.b1  // Intermediate
.b2  // Upper Intermediate
.c1  // Advanced
.c2  // Proficient
```

### ReviewQuality
```swift
.again  // Forgot
.hard   // Difficult
.good   // Correct
.easy   // Very easy
```

### HintLevel
```swift
.visualClue      // Image hint
.starterWords    // First words
.fullTranslation // Full answer
```

### VoiceSpeed
```swift
.slow    // Slower speech
.normal  // Normal speed
.fast    // Faster speech
```

### TranslationMode
```swift
.always  // Always show translations
.onTap   // Show on tap
.never   // Never show
```
