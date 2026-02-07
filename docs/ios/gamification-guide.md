# iOS Gamification System Guide

This guide explains the gamification system in the BabLanguageSDK and how to integrate it into your iOS app for an engaging multiplayer language learning experience.

## Overview

The SDK tracks player performance and provides gamification features:

- **Pronunciation Scoring** - Accuracy tracking per line
- **XP System** - Points earned based on performance
- **Streaks** - Consecutive perfect pronunciations
- **Leaderboards** - Real-time session rankings
- **Achievements** - Unlockable badges for milestones
- **Session Summaries** - Post-game statistics

## Data Models

### PronunciationResult

Tracks how well a player pronounced their dialog line.

```swift
// SDK provides this model
struct PronunciationResult {
    let errorCount: Int32       // Number of word errors
    let accuracy: Float         // 0.0 to 1.0
    let wordErrors: [WordError] // Specific mispronunciations
    let skipped: Bool           // User skipped (no mic)
    let duration: Int64         // Time to read (ms)
}

struct WordError {
    let word: String            // Mispronounced word
    let position: Int32         // Position in sentence
    let expected: String?       // Expected sound
    let heard: String?          // What was recognized
}
```

### PlayerStats

Accumulated statistics for a player during a session.

```swift
struct PlayerStats {
    let playerId: String
    let linesCompleted: Int32   // Total lines read
    let totalErrors: Int32      // Total word errors
    let perfectLines: Int32     // Lines with 100% accuracy
    let averageAccuracy: Float  // Overall accuracy
    let currentStreak: Int32    // Consecutive perfect lines
    let xpEarned: Int32         // Total XP this session
}
```

### SessionLeaderboard

Real-time rankings updated after each turn.

```swift
struct SessionLeaderboard {
    let rankings: [PlayerRanking]
    let updatedAt: Int64
}

struct PlayerRanking {
    let rank: Int32
    let playerId: String
    let displayName: String
    let score: Int32
    let highlight: String?      // "Perfect Score", "Most Improved", etc.
}
```

### Achievement

Unlockable badges for reaching milestones.

```swift
struct Achievement {
    let id: String
    let name: String
    let description: String
    let iconName: String?
    let unlockedAt: Int64?
}
```

## XP Calculation

The SDK calculates XP using this formula:

```
Total XP = Base + Accuracy Bonus + Streak Bonus + Multiplayer Bonus + First-Time Bonus

Where:
- Base XP: 10 points per line
- Accuracy Bonus: accuracy × 20 (0-20 points)
- Streak Bonus: 5 points if continuing a streak
- Multiplayer Bonus: 50% of base (5 points) in multiplayer
- First-Time Scenario Bonus: 25 points for new scenarios
```

### Example Calculations

| Scenario | Accuracy | Streak | Multiplayer | First Time | Total XP |
|----------|----------|--------|-------------|------------|----------|
| Perfect solo | 100% | Yes | No | No | 10 + 20 + 5 = 35 |
| Good multiplayer | 85% | No | Yes | No | 10 + 17 + 0 + 5 = 32 |
| First scenario | 70% | No | Yes | Yes | 10 + 14 + 0 + 5 + 25 = 54 |
| Skipped line | 0% | No | Yes | No | 0 |

## Integration

### 1. Speech Recognition Setup

```swift
import Speech
import BabLanguageSDK

class SpeechManager: ObservableObject {
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "es-ES"))!
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    
    @Published var isListening = false
    @Published var recognizedText = ""
    
    func startListening() throws {
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        guard let recognitionRequest = recognitionRequest else { return }
        
        recognitionRequest.shouldReportPartialResults = true
        
        let inputNode = audioEngine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { buffer, _ in
            self.recognitionRequest?.append(buffer)
        }
        
        audioEngine.prepare()
        try audioEngine.start()
        isListening = true
        
        recognitionTask = speechRecognizer.recognitionTask(with: recognitionRequest) { result, error in
            if let result = result {
                self.recognizedText = result.bestTranscription.formattedString
            }
        }
    }
    
    func stopListening() -> String {
        audioEngine.stop()
        recognitionRequest?.endAudio()
        recognitionTask?.cancel()
        isListening = false
        return recognizedText
    }
}
```

### 2. Pronunciation Evaluation

```swift
class PronunciationEvaluator {
    
    func evaluate(expected: String, recognized: String, duration: Int64) -> PronunciationResult {
        let expectedWords = expected.lowercased().split(separator: " ").map(String.init)
        let recognizedWords = recognized.lowercased().split(separator: " ").map(String.init)
        
        var wordErrors: [WordError] = []
        var errorCount: Int32 = 0
        
        for (index, expectedWord) in expectedWords.enumerated() {
            let recognizedWord = index < recognizedWords.count ? recognizedWords[index] : nil
            
            if recognizedWord != expectedWord {
                errorCount += 1
                wordErrors.append(WordError(
                    word: expectedWord,
                    position: Int32(index),
                    expected: expectedWord,
                    heard: recognizedWord
                ))
            }
        }
        
        // Also count extra words as errors
        if recognizedWords.count > expectedWords.count {
            errorCount += Int32(recognizedWords.count - expectedWords.count)
        }
        
        let accuracy = expectedWords.isEmpty ? 0 : 
            Float(expectedWords.count - Int(errorCount)) / Float(expectedWords.count)
        
        return PronunciationResult(
            errorCount: errorCount,
            accuracy: max(0, accuracy),
            wordErrors: wordErrors,
            skipped: false,
            duration: duration
        )
    }
    
    func createSkippedResult() -> PronunciationResult {
        return PronunciationResult.companion.skipped()
    }
}
```

### 3. Active Game View with Scoring

```swift
import SwiftUI
import BabLanguageSDK

struct ActiveGameView: View {
    let sdk: BrainSDK
    @StateObject private var speechManager = SpeechManager()
    @State private var startTime: Date?
    @State private var showResult = false
    @State private var lastResult: PronunciationResult?
    
    private let evaluator = PronunciationEvaluator()
    
    var currentLine: DialogLine? {
        sdk.state.value.pendingLine
    }
    
    var isMyTurn: Bool {
        sdk.state.value.currentTurnPlayerId == sdk.state.value.localPeerId
    }
    
    var body: some View {
        VStack(spacing: 24) {
            // Theater view - committed lines
            ScrollView {
                ForEach(Array(sdk.state.value.committedHistory.enumerated()), id: \.offset) { _, line in
                    CommittedLineView(line: line)
                }
            }
            
            Divider()
            
            // Current turn
            if isMyTurn, let line = currentLine {
                MyTurnView(
                    line: line,
                    speechManager: speechManager,
                    onComplete: { result in
                        sdk.completeLine(lineId: line.id, result: result)
                        lastResult = result
                        showResult = true
                    },
                    onSkip: {
                        sdk.skipLine(lineId: line.id)
                    }
                )
            } else {
                WaitingView(currentTurnPlayerId: sdk.state.value.currentTurnPlayerId)
            }
            
            // Leaderboard
            if let leaderboard = sdk.state.value.sessionLeaderboard {
                LeaderboardView(leaderboard: leaderboard)
            }
        }
        .sheet(isPresented: $showResult) {
            if let result = lastResult {
                ResultFeedbackView(result: result)
            }
        }
    }
}

struct MyTurnView: View {
    let line: DialogLine
    @ObservedObject var speechManager: SpeechManager
    let onComplete: (PronunciationResult) -> Void
    let onSkip: () -> Void
    
    @State private var startTime: Date?
    private let evaluator = PronunciationEvaluator()
    
    var body: some View {
        VStack(spacing: 16) {
            Text("Your Turn!")
                .font(.headline)
                .foregroundColor(.green)
            
            // Target text to read
            Text(line.textNative)
                .font(.title2)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
            
            // Translation hint
            Text(line.textTranslated)
                .font(.subheadline)
                .foregroundColor(.secondary)
            
            // Recording indicator
            if speechManager.isListening {
                HStack {
                    Circle()
                        .fill(Color.red)
                        .frame(width: 12, height: 12)
                    Text("Listening...")
                }
                
                Text(speechManager.recognizedText)
                    .font(.body)
                    .foregroundColor(.blue)
            }
            
            HStack(spacing: 20) {
                Button(action: {
                    if speechManager.isListening {
                        let recognized = speechManager.stopListening()
                        let duration = Int64((Date().timeIntervalSince(startTime ?? Date())) * 1000)
                        let result = evaluator.evaluate(
                            expected: line.textNative,
                            recognized: recognized,
                            duration: duration
                        )
                        onComplete(result)
                    } else {
                        startTime = Date()
                        try? speechManager.startListening()
                    }
                }) {
                    Image(systemName: speechManager.isListening ? "stop.circle.fill" : "mic.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(speechManager.isListening ? .red : .blue)
                }
                
                Button("Skip") {
                    onSkip()
                }
                .foregroundColor(.secondary)
            }
        }
        .padding()
    }
}
```

### 4. Result Feedback View

```swift
struct ResultFeedbackView: View {
    let result: PronunciationResult
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        VStack(spacing: 24) {
            // Score circle
            ZStack {
                Circle()
                    .stroke(scoreColor.opacity(0.3), lineWidth: 20)
                    .frame(width: 150, height: 150)
                
                Circle()
                    .trim(from: 0, to: CGFloat(result.accuracy))
                    .stroke(scoreColor, style: StrokeStyle(lineWidth: 20, lineCap: .round))
                    .frame(width: 150, height: 150)
                    .rotationEffect(.degrees(-90))
                
                VStack {
                    Text("\(Int(result.accuracy * 100))%")
                        .font(.system(size: 40, weight: .bold))
                    Text(scoreLabel)
                        .font(.headline)
                        .foregroundColor(.secondary)
                }
            }
            
            // XP earned
            if !result.skipped {
                HStack {
                    Image(systemName: "star.fill")
                        .foregroundColor(.yellow)
                    Text("+\(calculateXP()) XP")
                        .font(.title2)
                        .fontWeight(.bold)
                }
            }
            
            // Word errors
            if !result.wordErrors.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Areas to improve:")
                        .font(.headline)
                    
                    ForEach(result.wordErrors, id: \.position) { error in
                        HStack {
                            Text(error.word)
                                .foregroundColor(.red)
                            if let heard = error.heard {
                                Text("→ heard: \"\(heard)\"")
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
                .padding()
                .background(Color.red.opacity(0.1))
                .cornerRadius(12)
            }
            
            Button("Continue") {
                dismiss()
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
    
    private var scoreColor: Color {
        switch result.accuracy {
        case 0.9...1.0: return .green
        case 0.7..<0.9: return .yellow
        case 0.5..<0.7: return .orange
        default: return .red
        }
    }
    
    private var scoreLabel: String {
        switch result.accuracy {
        case 0.95...1.0: return "Perfect!"
        case 0.9..<0.95: return "Excellent!"
        case 0.8..<0.9: return "Great!"
        case 0.7..<0.8: return "Good"
        case 0.5..<0.7: return "Keep Practicing"
        default: return "Try Again"
        }
    }
    
    private func calculateXP() -> Int {
        let base = 10
        let accuracyBonus = Int(result.accuracy * 20)
        return base + accuracyBonus
    }
}
```

### 5. Leaderboard View

```swift
struct LeaderboardView: View {
    let leaderboard: SessionLeaderboard
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Leaderboard")
                .font(.headline)
            
            ForEach(leaderboard.rankings, id: \.playerId) { ranking in
                HStack {
                    // Rank badge
                    ZStack {
                        Circle()
                            .fill(rankColor(ranking.rank))
                            .frame(width: 30, height: 30)
                        Text("\(ranking.rank)")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                    }
                    
                    VStack(alignment: .leading) {
                        Text(ranking.displayName)
                            .fontWeight(.medium)
                        if let highlight = ranking.highlight {
                            Text(highlight)
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    Spacer()
                    
                    Text("\(ranking.score) pts")
                        .fontWeight(.bold)
                }
                .padding(.vertical, 4)
            }
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
    
    private func rankColor(_ rank: Int32) -> Color {
        switch rank {
        case 1: return .yellow
        case 2: return .gray
        case 3: return .brown
        default: return .blue
        }
    }
}
```

### 6. Session Summary View

```swift
struct SessionSummaryView: View {
    let summary: SessionSummary
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Text("Session Complete!")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                
                // Overall stats
                HStack(spacing: 40) {
                    StatBox(title: "Lines", value: "\(summary.totalLines)")
                    StatBox(title: "Accuracy", value: "\(Int(summary.overallAccuracy * 100))%")
                    StatBox(title: "Duration", value: formatDuration(summary.durationMs))
                }
                
                // Final rankings
                VStack(alignment: .leading, spacing: 12) {
                    Text("Final Rankings")
                        .font(.headline)
                    
                    ForEach(summary.playerRankings, id: \.playerId) { ranking in
                        FinalRankingRow(ranking: ranking, xp: summary.xpEarned[ranking.playerId] ?? 0)
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(12)
                
                // Achievements unlocked
                if !summary.achievementsUnlocked.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Achievements Unlocked!")
                            .font(.headline)
                        
                        ForEach(summary.achievementsUnlocked, id: \.id) { achievement in
                            AchievementRow(achievement: achievement)
                        }
                    }
                    .padding()
                    .background(Color.yellow.opacity(0.2))
                    .cornerRadius(12)
                }
                
                // Vocabulary learned
                if !summary.vocabularyLearned.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("New Vocabulary")
                            .font(.headline)
                        
                        ForEach(summary.vocabularyLearned, id: \.id) { entry in
                            HStack {
                                Text(entry.word)
                                    .fontWeight(.medium)
                                Spacer()
                                Text(entry.translation)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    .padding()
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(12)
                }
                
                Button("Done") {
                    dismiss()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
        }
    }
    
    private func formatDuration(_ ms: Int64) -> String {
        let seconds = ms / 1000
        let minutes = seconds / 60
        let remainingSeconds = seconds % 60
        return String(format: "%d:%02d", minutes, remainingSeconds)
    }
}

struct StatBox: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack {
            Text(value)
                .font(.title)
                .fontWeight(.bold)
            Text(title)
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct FinalRankingRow: View {
    let ranking: PlayerRanking
    let xp: Int32
    
    var body: some View {
        HStack {
            Text("#\(ranking.rank)")
                .font(.title2)
                .fontWeight(.bold)
                .foregroundColor(ranking.rank == 1 ? .yellow : .primary)
            
            Text(ranking.displayName)
            
            Spacer()
            
            VStack(alignment: .trailing) {
                Text("\(ranking.score) pts")
                    .fontWeight(.bold)
                Text("+\(xp) XP")
                    .font(.caption)
                    .foregroundColor(.green)
            }
        }
        .padding(.vertical, 4)
    }
}

struct AchievementRow: View {
    let achievement: Achievement
    
    var body: some View {
        HStack {
            Image(systemName: achievement.iconName ?? "star.fill")
                .font(.title2)
                .foregroundColor(.yellow)
            
            VStack(alignment: .leading) {
                Text(achievement.name)
                    .fontWeight(.bold)
                Text(achievement.description_)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
        }
    }
}
```

## Available Achievements

| ID | Name | Description | Trigger |
|----|------|-------------|---------|
| `first_multiplayer` | First Words Together | Complete first multiplayer session | 2+ players finish |
| `perfect_pair` | Perfect Pair | Both players 100% on exchange | Two consecutive perfects |
| `chatterbox` | Chatterbox | Complete 50 lines in one session | lines ≥ 50 |
| `language_partner` | Language Partner | Play with same friend 5 times | Session count = 5 |
| `flawless_scene` | Flawless Scene | Complete scenario with 0 errors | Accuracy = 100% |
| `streak_master` | Streak Master | 10 perfect lines in a row | Streak = 10 |
| `speed_reader` | Speed Reader | Average read time < 3 seconds | Avg duration < 3000ms |

## Observing State Changes

```swift
@MainActor
class GamificationObserver: ObservableObject {
    let sdk: BrainSDK
    
    @Published var myStats: PlayerStats?
    @Published var leaderboard: SessionLeaderboard?
    @Published var newAchievements: [Achievement] = []
    
    private var observationTask: Task<Void, Never>?
    
    init(sdk: BrainSDK) {
        self.sdk = sdk
        startObserving()
    }
    
    private func startObserving() {
        observationTask = Task {
            for await state in sdk.state {
                let myId = state.localPeerId
                
                // Update my stats
                if let stats = state.playerStats[myId] {
                    self.myStats = stats
                }
                
                // Update leaderboard
                self.leaderboard = state.sessionLeaderboard
                
                // Check for new achievements in summary
                if let summary = state.sessionSummary {
                    self.newAchievements = Array(summary.achievementsUnlocked)
                }
            }
        }
    }
    
    deinit {
        observationTask?.cancel()
    }
}
```

## Best Practices

### 1. Immediate Feedback

Show pronunciation results immediately after each line:

```swift
func handleLineComplete(result: PronunciationResult) {
    // Haptic feedback
    let generator = UINotificationFeedbackGenerator()
    if result.accuracy >= 0.9 {
        generator.notificationOccurred(.success)
    } else if result.accuracy >= 0.7 {
        generator.notificationOccurred(.warning)
    } else {
        generator.notificationOccurred(.error)
    }
    
    // Sound effect
    playScoreSound(accuracy: result.accuracy)
}
```

### 2. Progressive Difficulty

Adjust based on player performance:

```swift
func suggestNextScenario(stats: PlayerStats) -> String {
    if stats.averageAccuracy > 0.9 && stats.linesCompleted > 20 {
        return "the-heist" // More complex
    } else if stats.averageAccuracy < 0.6 {
        return "coffee-shop" // Simpler
    } else {
        return "first-date" // Medium
    }
}
```

### 3. Celebrate Streaks

```swift
struct StreakIndicator: View {
    let streak: Int32
    
    var body: some View {
        if streak >= 3 {
            HStack {
                Image(systemName: "flame.fill")
                    .foregroundColor(.orange)
                Text("\(streak) streak!")
                    .fontWeight(.bold)
            }
            .padding(8)
            .background(Color.orange.opacity(0.2))
            .cornerRadius(8)
            .transition(.scale.combined(with: .opacity))
        }
    }
}
```

### 4. Persist Progress

```swift
class ProgressPersistence {
    private let defaults = UserDefaults.standard
    
    func saveTotalXP(_ xp: Int) {
        let current = defaults.integer(forKey: "totalXP")
        defaults.set(current + xp, forKey: "totalXP")
    }
    
    func saveAchievement(_ achievement: Achievement) {
        var unlocked = defaults.stringArray(forKey: "unlockedAchievements") ?? []
        if !unlocked.contains(achievement.id) {
            unlocked.append(achievement.id)
            defaults.set(unlocked, forKey: "unlockedAchievements")
        }
    }
}
```

## Related Documentation

- [Native BLE Implementation Guide](./native-ble-implementation.md)
- [BLE Multiplayer Integration Guide](./ble-multiplayer-guide.md)
- [SKIE Integration Guide](./integration-guide.md)
