# iOS Integration Guide - BabLanguageSDK

Complete guide for integrating the Bring a Brain language learning SDK into your iOS application.

## Table of Contents

1. [Overview](#overview)
2. [Requirements](#requirements)
3. [Installation](#installation)
4. [Quick Start](#quick-start)
5. [Core Concepts](#core-concepts)
6. [Complete Integration Example](#complete-integration-example)
7. [API Reference](#api-reference)
8. [Best Practices](#best-practices)

---

## Overview

BabLanguageSDK is a headless Kotlin Multiplatform SDK that provides all business logic for a collaborative language learning game. The SDK handles:

- **Game State Management**: Solo and multiplayer sessions via MVI architecture
- **AI Integration**: On-device (iOS 26+) or cloud-based language generation
- **Vocabulary Tracking**: SM-2 spaced repetition algorithm
- **Progress & Gamification**: XP, levels, streaks, achievements
- **BLE Networking**: Offline multiplayer via Bluetooth

**Your responsibility**: Build the UI using SwiftUI or UIKit. The SDK exposes reactive `StateFlow` objects that you observe to render your interface.

---

## Requirements

| Requirement | Version |
|-------------|---------|
| iOS | 15.0+ |
| Xcode | 15.0+ |
| Swift | 5.9+ |
| For on-device AI | iOS 26+ |

---

## Installation

### Swift Package Manager (Recommended)

1. In Xcode: **File → Add Package Dependencies**
2. Enter repository URL: `https://github.com/etonealbert/bab-language-kmp`
3. Select branch: `main`
4. Import in your Swift files:

```swift
import BabLanguageSDK
```

### Manual XCFramework

1. Build the XCFramework:
```bash
./gradlew :composeApp:assembleBabLanguageSDKXCFramework
```

2. Locate the output:
```
composeApp/build/XCFrameworks/release/BabLanguageSDK.xcframework
```

3. Drag into your Xcode project and embed the framework.

---

## Quick Start

### Minimal Implementation

```swift
import SwiftUI
import BabLanguageSDK

@main
struct LanguageLearningApp: App {
    let sdk = BrainSDK()
    
    var body: some Scene {
        WindowGroup {
            ContentView(sdk: sdk)
        }
    }
}

struct ContentView: View {
    let sdk: BrainSDK
    @State private var dialogLines: [DialogLine] = []
    
    var body: some View {
        VStack {
            List(dialogLines, id: \.id) { line in
                VStack(alignment: .leading) {
                    Text(line.roleName).font(.caption).foregroundColor(.gray)
                    Text(line.textNative).font(.body)
                    Text(line.textTranslated).font(.caption2).foregroundColor(.secondary)
                }
            }
            
            Button("Generate Dialog") {
                sdk.generate()
            }
            .padding()
        }
        .onAppear {
            sdk.startSoloGame(scenarioId: "coffee-shop", userRoleId: "customer")
            observeState()
        }
    }
    
    private func observeState() {
        Task {
            for await state in sdk.state {
                await MainActor.run {
                    dialogLines = state.dialogHistory
                }
            }
        }
    }
}
```

---

## Core Concepts

### 1. Headless Architecture

The SDK contains **zero UI code**. You build all visual elements. The SDK provides:

```
┌─────────────────────────────────────────┐
│           Your SwiftUI App              │
│    (Views, Navigation, Styling)         │
├─────────────────────────────────────────┤
│              BrainSDK                   │
│   StateFlow<SessionState>               │
│   StateFlow<UserProfile?>               │
│   StateFlow<VocabularyStats>            │
│   StateFlow<UserProgress?>              │
├─────────────────────────────────────────┤
│         DialogStore (MVI)               │
│   Intent → Execute → Packet → Reduce    │
├─────────────────────────────────────────┤
│      AIProvider / NetworkSession        │
└─────────────────────────────────────────┘
```

### 2. Observable State

All SDK state is exposed via Kotlin's `StateFlow`. In Swift, observe using async iteration:

```swift
// Observe session state
Task {
    for await state in sdk.state {
        // Update your UI
    }
}

// Observe user profile
Task {
    for await profile in sdk.userProfile {
        // Update profile UI
    }
}

// Observe vocabulary stats
Task {
    for await stats in sdk.vocabularyStats {
        // Update vocabulary dashboard
    }
}
```

### 3. Session Modes

| Mode | Description | Use Case |
|------|-------------|----------|
| `SOLO` | Play with AI partner | Practice alone |
| `HOST` | Host multiplayer game | Create a room |
| `CLIENT` | Join hosted game | Join a friend |

### 4. Game Phases

| Phase | Description |
|-------|-------------|
| `LOBBY` | Waiting for players |
| `ROLE_SELECTION` | Players choosing roles |
| `WAITING` | Waiting for game start |
| `ACTIVE` | Game in progress |
| `VOTING` | Players voting on action |
| `FINISHED` | Game complete |

---

## Complete Integration Example

### App Structure

```
MyLanguageApp/
├── MyLanguageApp.swift          # App entry, SDK initialization
├── Views/
│   ├── OnboardingView.swift     # Profile setup
│   ├── HomeView.swift           # Main menu
│   ├── GameView.swift           # Active gameplay
│   ├── VocabularyView.swift     # Review words
│   └── ProgressView.swift       # Stats & achievements
├── ViewModels/
│   └── SDKObserver.swift        # StateFlow wrappers
└── Services/
    └── CustomAIProvider.swift   # Optional custom AI
```

### SDKObserver.swift

```swift
import SwiftUI
import BabLanguageSDK
import Combine

@MainActor
class SDKObserver: ObservableObject {
    let sdk: BrainSDK
    
    @Published var sessionState: SessionState
    @Published var userProfile: UserProfile?
    @Published var vocabularyStats: VocabularyStats
    @Published var progress: UserProgress?
    @Published var dueReviews: [VocabularyEntry] = []
    
    private var observationTasks: [Task<Void, Never>] = []
    
    init(sdk: BrainSDK = BrainSDK()) {
        self.sdk = sdk
        self.sessionState = sdk.state.value
        self.userProfile = sdk.userProfile.value
        self.vocabularyStats = sdk.vocabularyStats.value
        self.progress = sdk.progress.value
        
        startObserving()
    }
    
    private func startObserving() {
        observationTasks.append(Task {
            for await state in sdk.state {
                self.sessionState = state
            }
        })
        
        observationTasks.append(Task {
            for await profile in sdk.userProfile {
                self.userProfile = profile
            }
        })
        
        observationTasks.append(Task {
            for await stats in sdk.vocabularyStats {
                self.vocabularyStats = stats
            }
        })
        
        observationTasks.append(Task {
            for await prog in sdk.progress {
                self.progress = prog
            }
        })
        
        observationTasks.append(Task {
            for await reviews in sdk.dueReviews {
                self.dueReviews = reviews
            }
        })
    }
    
    deinit {
        observationTasks.forEach { $0.cancel() }
    }
}
```

### OnboardingView.swift

```swift
import SwiftUI
import BabLanguageSDK

struct OnboardingView: View {
    @EnvironmentObject var observer: SDKObserver
    @State private var displayName = ""
    @State private var nativeLanguage = "en"
    @State private var targetLanguage = "es"
    @State private var proficiencyLevel = CEFRLevel.a1
    
    var body: some View {
        NavigationStack {
            Form {
                Section("About You") {
                    TextField("Display Name", text: $displayName)
                    Picker("Native Language", selection: $nativeLanguage) {
                        Text("English").tag("en")
                        Text("Spanish").tag("es")
                        Text("French").tag("fr")
                    }
                }
                
                Section("Learning Goal") {
                    Picker("Target Language", selection: $targetLanguage) {
                        Text("Spanish").tag("es")
                        Text("French").tag("fr")
                        Text("German").tag("de")
                    }
                    
                    Picker("Current Level", selection: $proficiencyLevel) {
                        Text("A1 - Beginner").tag(CEFRLevel.a1)
                        Text("A2 - Elementary").tag(CEFRLevel.a2)
                        Text("B1 - Intermediate").tag(CEFRLevel.b1)
                        Text("B2 - Upper Intermediate").tag(CEFRLevel.b2)
                    }
                }
                
                Section {
                    Button("Complete Setup") {
                        completeOnboarding()
                    }
                    .disabled(displayName.isEmpty)
                }
            }
            .navigationTitle("Welcome")
        }
    }
    
    private func completeOnboarding() {
        let targetLang = TargetLanguage(
            code: targetLanguage,
            proficiencyLevel: proficiencyLevel,
            startedAt: Date().timeIntervalSince1970.int64
        )
        
        let profile = UserProfile(
            id: UUID().uuidString,
            displayName: displayName,
            nativeLanguage: nativeLanguage,
            targetLanguages: [targetLang],
            currentTargetLanguage: targetLanguage,
            interests: [],
            learningGoals: [],
            dailyGoalMinutes: 15,
            voiceSpeed: .normal,
            showTranslations: .onTap,
            onboardingCompleted: true,
            createdAt: Date().timeIntervalSince1970.int64,
            lastActiveAt: Date().timeIntervalSince1970.int64
        )
        
        Task {
            await observer.sdk.completeOnboarding(profile: profile)
        }
    }
}
```

### GameView.swift

```swift
import SwiftUI
import BabLanguageSDK

struct GameView: View {
    @EnvironmentObject var observer: SDKObserver
    @State private var selectedScenario: Scenario?
    
    var body: some View {
        NavigationStack {
            if observer.sessionState.currentPhase == .active {
                ActiveGameView()
            } else {
                ScenarioSelectionView(onSelect: startGame)
            }
        }
    }
    
    private func startGame(_ scenario: Scenario, role: Role) {
        observer.sdk.startSoloGame(
            scenarioId: scenario.id,
            userRoleId: role.id
        )
    }
}

struct ActiveGameView: View {
    @EnvironmentObject var observer: SDKObserver
    
    var body: some View {
        VStack {
            // Dialog History
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 12) {
                        ForEach(observer.sessionState.dialogHistory, id: \.id) { line in
                            DialogBubble(line: line)
                                .id(line.id)
                        }
                    }
                    .padding()
                }
                .onChange(of: observer.sessionState.dialogHistory.count) { _ in
                    if let lastId = observer.sessionState.dialogHistory.last?.id {
                        proxy.scrollTo(lastId, anchor: .bottom)
                    }
                }
            }
            
            // Input Area
            HStack {
                Button(action: { observer.sdk.requestHint() }) {
                    Image(systemName: "lightbulb")
                }
                
                Spacer()
                
                Button("Generate") {
                    observer.sdk.generate()
                }
                .buttonStyle(.borderedProminent)
                
                Spacer()
                
                Button(action: endGame) {
                    Image(systemName: "xmark.circle")
                }
            }
            .padding()
        }
        .navigationTitle(observer.sessionState.scenario?.name ?? "Game")
    }
    
    private func endGame() {
        Task {
            await observer.sdk.endSession()
        }
    }
}

struct DialogBubble: View {
    let line: DialogLine
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(line.roleName)
                .font(.caption)
                .foregroundColor(.secondary)
            
            Text(line.textNative)
                .font(.body)
                .padding(12)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
            
            if !line.textTranslated.isEmpty {
                Text(line.textTranslated)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
}
```

### VocabularyView.swift

```swift
import SwiftUI
import BabLanguageSDK

struct VocabularyView: View {
    @EnvironmentObject var observer: SDKObserver
    @State private var currentIndex = 0
    @State private var showAnswer = false
    
    var body: some View {
        NavigationStack {
            VStack {
                // Stats Header
                HStack {
                    StatCard(title: "Due", value: "\(observer.vocabularyStats.dueCount)")
                    StatCard(title: "Mastered", value: "\(observer.vocabularyStats.masteredCount)")
                    StatCard(title: "Total", value: "\(observer.vocabularyStats.total)")
                }
                .padding()
                
                Spacer()
                
                if currentIndex < observer.dueReviews.count {
                    FlashcardView(
                        entry: observer.dueReviews[currentIndex],
                        showAnswer: $showAnswer,
                        onReview: handleReview
                    )
                } else {
                    Text("No reviews due!")
                        .font(.title2)
                        .foregroundColor(.secondary)
                }
                
                Spacer()
            }
            .navigationTitle("Vocabulary")
        }
    }
    
    private func handleReview(_ quality: ReviewQuality) {
        let entry = observer.dueReviews[currentIndex]
        
        Task {
            await observer.sdk.recordVocabularyReview(
                entryId: entry.id,
                quality: quality
            )
        }
        
        showAnswer = false
        currentIndex += 1
    }
}

struct FlashcardView: View {
    let entry: VocabularyEntry
    @Binding var showAnswer: Bool
    let onReview: (ReviewQuality) -> Void
    
    var body: some View {
        VStack(spacing: 20) {
            // Card
            VStack {
                Text(entry.word)
                    .font(.largeTitle)
                    .padding()
                
                if showAnswer {
                    Divider()
                    Text(entry.translation)
                        .font(.title2)
                        .foregroundColor(.secondary)
                        .padding()
                }
            }
            .frame(maxWidth: .infinity, minHeight: 200)
            .background(Color(.systemBackground))
            .cornerRadius(16)
            .shadow(radius: 4)
            .padding(.horizontal)
            .onTapGesture {
                withAnimation { showAnswer.toggle() }
            }
            
            // Review Buttons
            if showAnswer {
                HStack(spacing: 12) {
                    ReviewButton(title: "Again", color: .red) {
                        onReview(.again)
                    }
                    ReviewButton(title: "Hard", color: .orange) {
                        onReview(.hard)
                    }
                    ReviewButton(title: "Good", color: .green) {
                        onReview(.good)
                    }
                    ReviewButton(title: "Easy", color: .blue) {
                        onReview(.easy)
                    }
                }
            }
        }
    }
}

struct ReviewButton: View {
    let title: String
    let color: Color
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(title)
                .fontWeight(.medium)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
        }
        .buttonStyle(.bordered)
        .tint(color)
    }
}

struct StatCard: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack {
            Text(value)
                .font(.title2)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding()
        .background(Color(.secondarySystemBackground))
        .cornerRadius(12)
    }
}
```

---

## API Reference

### BrainSDK Properties

| Property | Type | Description |
|----------|------|-------------|
| `state` | `StateFlow<SessionState>` | Current game state |
| `userProfile` | `StateFlow<UserProfile?>` | User's profile |
| `vocabularyStats` | `StateFlow<VocabularyStats>` | Vocabulary statistics |
| `dueReviews` | `StateFlow<[VocabularyEntry]>` | Words due for review |
| `progress` | `StateFlow<UserProgress?>` | XP, level, streaks |
| `aiCapabilities` | `AICapabilities` | Device AI info |

### BrainSDK Methods

#### Game Lifecycle
| Method | Description |
|--------|-------------|
| `startSoloGame(scenarioId:userRoleId:)` | Start solo game with AI |
| `hostGame(scenarioId:userRoleId:)` | Host multiplayer game |
| `joinGame(hostDeviceId:userRoleId:)` | Join hosted game |
| `scanForHosts() -> Flow<DiscoveredDevice>` | Scan for BLE hosts |
| `generate()` | Generate next AI dialog |
| `leaveGame()` | Leave current game |
| `endSession()` | End and save session |
| `getAvailableScenarios() -> [Scenario]` | Get scenarios |

#### Onboarding
| Method | Description |
|--------|-------------|
| `completeOnboarding(profile:)` | Save user profile |
| `isOnboardingRequired() -> Bool` | Check if onboarding needed |
| `updateProfile(update:)` | Update user profile |

#### Vocabulary
| Method | Description |
|--------|-------------|
| `getVocabularyForReview(limit:)` | Get due words |
| `recordVocabularyReview(entryId:quality:)` | Record review |
| `addToVocabulary(entry:)` | Add word manually |
| `createVocabularyEntry(word:translation:)` | Create new entry |

#### Pedagogical
| Method | Description |
|--------|-------------|
| `requestHint(level:)` | Request hint |
| `triggerPlotTwist(description:)` | Trigger plot twist (host) |
| `setSecretObjective(playerId:objective:)` | Set secret goal (host) |

---

## Best Practices

### 1. SDK Lifecycle

```swift
// Create SDK once at app launch
@main
struct MyApp: App {
    @StateObject private var observer = SDKObserver()
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(observer)
        }
    }
}
```

### 2. Error Handling

```swift
Task {
    do {
        let reviews = try await sdk.getVocabularyForReview(limit: 20)
    } catch {
        // Handle error
    }
}
```

### 3. Custom AI Provider (iOS 26+)

```swift
import BabLanguageSDK

class FoundationModelProvider: AIProvider {
    func generate(context: DialogContext) async -> DialogLine {
        // Use iOS Foundation Models
        let session = LanguageModelSession()
        let response = try await session.respond(to: buildPrompt(context))
        return parseResponse(response)
    }
}

// Inject at initialization
let sdk = BrainSDK(aiProvider: FoundationModelProvider())
```

### 4. Custom Persistence

```swift
import BabLanguageSDK

class CoreDataUserProfileRepository: UserProfileRepository {
    func getProfile() async -> UserProfile? {
        // Fetch from Core Data
    }
    
    func saveProfile(profile: UserProfile) async {
        // Save to Core Data
    }
    // ... implement other methods
}

let sdk = BrainSDK(
    userProfileRepository: CoreDataUserProfileRepository(),
    vocabularyRepository: CoreDataVocabularyRepository()
)
```

### 5. Background State Sync

```swift
class AppDelegate: NSObject, UIApplicationDelegate {
    func applicationDidEnterBackground(_ application: UIApplication) {
        // SDK automatically persists state via repositories
    }
}
```

---

## Next Steps

- See [iOS Foundation Model Integration](./ios-foundation-model-integration.md) for on-device AI
- See [Architecture Overview](./sdk-architecture.md) for deep dive
- See [Multiplayer Guide](./multiplayer-guide.md) for BLE networking
