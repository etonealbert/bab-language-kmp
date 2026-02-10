# iOS Chat History Integration Guide - BabLanguageSDK v1.0.8

Guide for integrating the Chat History feature into your iOS app. This feature lets premium users browse their past conversation sessions.

## Table of Contents

1. [What Changed in v1.0.8](#what-changed-in-v108)
2. [SDK Changes Summary](#sdk-changes-summary)
3. [Data Models](#data-models)
4. [Observing History](#observing-history)
5. [How It Works](#how-it-works)
6. [Premium Gating](#premium-gating)
7. [SwiftUI Integration](#swiftui-integration)
8. [Migration Checklist](#migration-checklist)

---

## What Changed in v1.0.8

| Change | Impact |
|--------|--------|
| `UserProfile.isPremium` added | New `Bool` field (defaults `false`) — set to `true` for paying users |
| `BrainSDK.history` added | New `StateFlow<[HistorySession]>` property to observe |
| `endSession()` behavior changed | Now auto-saves the session to history if user is premium |
| `HistoryRepository` injectable | You can swap `MockRemoteHistoryRepository` for a real backend later |

---

## SDK Changes Summary

### New Property on BrainSDK

```swift
// Available on BrainSDK instance
sdk.history  // StateFlow<[HistorySession]> — past sessions, most recent first
```

### New Field on UserProfile

```swift
// When creating or updating a UserProfile, set isPremium
let profile = UserProfile(
    id: "user-123",
    displayName: "Maria",
    nativeLanguage: "en",
    targetLanguages: [...],
    currentTargetLanguage: "es",
    interests: [...],
    learningGoals: [...],
    dailyGoalMinutes: 15,
    voiceSpeed: .normal,
    showTranslations: .onTap,
    isPremium: true,              // ← NEW: enables history saving
    onboardingCompleted: true,
    createdAt: timestamp,
    lastActiveAt: timestamp
)
```

### Updated endSession() Behavior

```swift
// Before v1.0.8: just ends the game
try await sdk.endSession()

// After v1.0.8: ends the game AND auto-saves to history (if premium)
// No code changes needed on your side — it happens automatically
try await sdk.endSession()
```

---

## Data Models

### HistorySession

Represents a completed conversation session saved to history.

```swift
struct HistorySession {
    let sessionId: String           // Unique session identifier
    let scenarioTitle: String       // e.g. "Ordering Coffee"
    let timestamp: Int64            // When the session was saved (epoch ms)
    let targetLanguage: String      // e.g. "es"
    let durationSeconds: Int64      // How long the session lasted
    let dialogLines: [DialogLine]   // Full conversation transcript
}
```

### HistorySaveResult

Returned when `HistoryRepository.saveSession()` is called internally. You don't call this directly — `endSession()` handles it — but it's useful to understand the possible outcomes:

```swift
// Success: session was saved
HistorySaveResult.Success(session: HistorySession)

// Error: save failed
HistorySaveResult.Error(reason: HistoryErrorReason)
```

### HistoryErrorReason

```swift
enum HistoryErrorReason {
    case featureLocked   // User is not premium
    case networkError    // Simulated network failure
}
```

---

## Observing History

### Basic Observation

```swift
struct HistoryListView: View {
    let sdk: BrainSDK
    @State private var sessions: [HistorySession] = []

    var body: some View {
        List(sessions, id: \.sessionId) { session in
            HistoryRow(session: session)
        }
        .task {
            for await history in sdk.history {
                sessions = history
            }
        }
    }
}
```

### History Row Component

```swift
struct HistoryRow: View {
    let session: HistorySession

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(session.scenarioTitle)
                    .font(.headline)
                Spacer()
                Text(formatDate(session.timestamp))
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                Label("\(session.dialogLines.count) lines", systemImage: "text.bubble")
                Label(formatDuration(session.durationSeconds), systemImage: "clock")
                Label(session.targetLanguage.uppercased(), systemImage: "globe")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private func formatDate(_ epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMs) / 1000)
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private func formatDuration(_ seconds: Int64) -> String {
        let minutes = seconds / 60
        let remaining = seconds % 60
        return "\(minutes)m \(remaining)s"
    }
}
```

---

## How It Works

### Automatic Flow

```
1. User plays a session (startSoloGame / hostGame)
2. User finishes → your app calls sdk.endSession()
3. SDK internally:
   a. Captures current SessionState (dialog, scenario, stats)
   b. Builds a HistorySession from the state
   c. Calls historyRepository.saveSession(session, userProfile)
   d. If premium → saved to in-memory store, history flow updates
   e. If free → save silently ignored (FEATURE_LOCKED)
4. Your UI observing sdk.history automatically gets the new list
```

### What Gets Saved

| Field | Source |
|-------|--------|
| `sessionId` | From `SessionStats.sessionId` or auto-generated |
| `scenarioTitle` | `SessionState.scenario.name` |
| `timestamp` | Current time when `endSession()` is called |
| `targetLanguage` | `UserProfile.currentTargetLanguage` |
| `durationSeconds` | Calculated from `SessionStats.startedAt` to now |
| `dialogLines` | `SessionState.dialogHistory` (full transcript) |

---

## Premium Gating

### Setting Premium Status

Update the user's premium status via `updateProfile()`:

```swift
// When user purchases premium
try await sdk.updateProfile { profile in
    profile.doCopy(isPremium: true)  // KMP copy syntax in Swift
}

// When subscription expires
try await sdk.updateProfile { profile in
    profile.doCopy(isPremium: false)
}
```

### Handling Free Users

For free users, `endSession()` still works — the game ends normally. History just won't be saved. You can gate the history UI behind a paywall:

```swift
struct HistoryTab: View {
    let sdk: BrainSDK
    @State private var profile: UserProfile?

    var body: some View {
        Group {
            if profile?.isPremium == true {
                HistoryListView(sdk: sdk)
            } else {
                PremiumUpsellView()
            }
        }
        .task {
            for await p in sdk.userProfile {
                profile = p
            }
        }
    }
}
```

---

## SwiftUI Integration

### Complete History Screen with Session Detail

```swift
struct HistoryScreen: View {
    let sdk: BrainSDK
    @State private var sessions: [HistorySession] = []
    @State private var selectedSession: HistorySession?

    var body: some View {
        NavigationStack {
            Group {
                if sessions.isEmpty {
                    ContentUnavailableView(
                        "No History",
                        systemImage: "clock.arrow.circlepath",
                        description: Text("Your completed sessions will appear here.")
                    )
                } else {
                    List(sessions, id: \.sessionId) { session in
                        Button {
                            selectedSession = session
                        } label: {
                            HistoryRow(session: session)
                        }
                    }
                }
            }
            .navigationTitle("History")
            .sheet(item: $selectedSession) { session in
                SessionDetailView(session: session)
            }
        }
        .task {
            for await history in sdk.history {
                sessions = history
            }
        }
    }
}

// Make HistorySession work with sheet(item:)
extension HistorySession: Identifiable {
    public var id: String { sessionId }
}
```

### Session Detail (Conversation Replay)

```swift
struct SessionDetailView: View {
    let session: HistorySession

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 12) {
                    // Session metadata
                    HStack {
                        Label(session.targetLanguage.uppercased(), systemImage: "globe")
                        Label(formatDuration(session.durationSeconds), systemImage: "clock")
                        Label("\(session.dialogLines.count) lines", systemImage: "text.bubble")
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .padding(.bottom)

                    // Conversation transcript
                    ForEach(session.dialogLines, id: \.id) { line in
                        VStack(alignment: .leading, spacing: 2) {
                            Text(line.roleName)
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundStyle(.secondary)
                            Text(line.textNative)
                                .font(.body)
                            Text(line.textTranslated)
                                .font(.callout)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
                .padding()
            }
            .navigationTitle(session.scenarioTitle)
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func formatDuration(_ seconds: Int64) -> String {
        let minutes = seconds / 60
        let remaining = seconds % 60
        return "\(minutes)m \(remaining)s"
    }
}
```

---

## Migration Checklist

### Required (Breaking)

- [ ] **None** — v1.0.8 is fully backward compatible. `isPremium` defaults to `false`, existing code works unchanged.

### Recommended

- [ ] Set `isPremium = true` on `UserProfile` for paying users (otherwise history won't save)
- [ ] Add a History tab/screen in your app using `sdk.history`
- [ ] Gate the History UI behind premium check (`profile?.isPremium`)
- [ ] If using SwiftData for `UserProfile`, add `isPremium: Bool` column to your `SDUserProfile` model and update the `toKMPProfile()` / `fromKMP()` mapping

### SwiftData Migration

If you have a `SDUserProfile` SwiftData model, add the new field:

```swift
@Model
class SDUserProfile {
    // ... existing fields ...
    var isPremium: Bool = false   // ← ADD THIS
}
```

Update your repository mapping:

```swift
extension SDUserProfile {
    func toKMPProfile() -> UserProfile {
        return UserProfile(
            // ... existing fields ...
            isPremium: self.isPremium,   // ← MAP THIS
            onboardingCompleted: self.onboardingCompleted,
            createdAt: self.createdAt,
            lastActiveAt: self.lastActiveAt
        )
    }
}
```

### Future: Real Backend

The current `MockRemoteHistoryRepository` stores history in memory (lost on app restart). When your real backend is ready:

1. Create a class conforming to `HistoryRepository`
2. Inject it: `BrainSDK(historyRepository: myRealRepo)`
3. The `HistoryRepository` interface is stable — same `saveSession()`, `getSessions()`, `deleteSession()`, `clear()` contract
