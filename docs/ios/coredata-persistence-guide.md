# CoreData Persistence Guide for BabLanguageSDK

**Version**: 1.0  
**SDK Version**: Phase 4+  
**Last Updated**: 2026-02-05

---

## Overview

The BabLanguageSDK uses a **repository injection pattern** - it defines interfaces for data persistence but provides only in-memory implementations by default. For production apps, you must implement these repositories using CoreData (or your preferred persistence layer).

This guide provides:
1. Complete data model specifications
2. CoreData entity designs with relationships
3. Swift protocol implementations
4. Bridge code examples

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Data Model Specifications](#data-model-specifications)
3. [CoreData Model Design](#coredata-model-design)
4. [Repository Implementations](#repository-implementations)
5. [SDK Integration](#sdk-integration)
6. [Migration Strategies](#migration-strategies)
7. [Best Practices](#best-practices)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        Your iOS App                              │
├─────────────────────────────────────────────────────────────────┤
│  CoreData Stack                    │  Repository Implementations │
│  ┌─────────────────┐               │  ┌───────────────────────┐  │
│  │ NSManagedObject │◄──────────────┼──│ CoreDataUserProfile   │  │
│  │   Subclasses    │               │  │      Repository       │  │
│  └─────────────────┘               │  └───────────────────────┘  │
│           │                        │             │               │
│           ▼                        │             ▼               │
│  ┌─────────────────┐               │  ┌───────────────────────┐  │
│  │ NSPersistentCon │               │  │   BrainSDK Instance   │  │
│  │    tainer       │               │  │ (injected repositories)│  │
│  └─────────────────┘               │  └───────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### Repository Interfaces (from SDK)

| Interface | Purpose |
|-----------|---------|
| `UserProfileRepository` | User profile, languages, preferences |
| `VocabularyRepository` | Words with SRS scheduling data |
| `ProgressRepository` | XP, streaks, achievements, session stats |
| `DialogHistoryRepository` | Saved game sessions with dialog history |

---

## Data Model Specifications

### 1. UserProfile

Primary user profile containing language settings and preferences.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `id` | `String` | `String` | **Primary Key**, UUID recommended |
| `displayName` | `String` | `String` | User's display name |
| `nativeLanguage` | `String` | `String` | ISO 639-1 code (e.g., "en-US") |
| `targetLanguages` | `List<TargetLanguage>` | `NSOrderedSet<CDTargetLanguage>` | To-many relationship |
| `currentTargetLanguage` | `String` | `String` | Currently active target language |
| `interests` | `Set<Interest>` | `String` | JSON-encoded array or Transformable |
| `learningGoals` | `Set<LearningGoal>` | `String` | JSON-encoded array or Transformable |
| `dailyGoalMinutes` | `Int` | `Int16` | 5, 10, 15, or 30 |
| `voiceSpeed` | `VoiceSpeed` | `String` | "SLOW", "NORMAL", "FAST" |
| `showTranslations` | `TranslationMode` | `String` | "ALWAYS", "ON_TAP", "NEVER" |
| `onboardingCompleted` | `Boolean` | `Bool` | Has user completed onboarding |
| `createdAt` | `Long` | `Date` | Epoch ms → Date |
| `lastActiveAt` | `Long` | `Date` | Last activity timestamp |

#### TargetLanguage (Embedded/Relationship)

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `code` | `String` | `String` | ISO 639-1 code |
| `proficiencyLevel` | `CEFRLevel` | `String` | "A1", "A2", "B1", "B2", "C1", "C2" |
| `startedAt` | `Long` | `Date` | When user started learning |

#### Enums

```swift
// CEFRLevel - Store as String in CoreData
enum CEFRLevel: String, Codable {
    case A1, A2, B1, B2, C1, C2
}

// Interest - Store as JSON array or comma-separated string
enum Interest: String, Codable, CaseIterable {
    case TRAVEL, BUSINESS, ROMANCE, SCI_FI, EVERYDAY
    case FOOD, CULTURE, SPORTS, MUSIC, MOVIES
}

// LearningGoal - Store as JSON array or comma-separated string
enum LearningGoal: String, Codable, CaseIterable {
    case CONVERSATION, READING, LISTENING, EXAM_PREP, WORK, TRAVEL
}

// VoiceSpeed - Store as String
enum VoiceSpeed: String, Codable {
    case SLOW, NORMAL, FAST
}

// TranslationMode - Store as String
enum TranslationMode: String, Codable {
    case ALWAYS, ON_TAP, NEVER
}
```

---

### 2. VocabularyEntry

Words learned with SRS (Spaced Repetition System) scheduling data.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `id` | `String` | `String` | **Primary Key** |
| `word` | `String` | `String` | Word in target language |
| `translation` | `String` | `String` | Translation in native language |
| `language` | `String` | `String` | ISO language code |
| `partOfSpeech` | `PartOfSpeech?` | `String?` | Optional, nullable |
| `exampleSentence` | `String?` | `String?` | Optional example |
| `audioUrl` | `String?` | `String?` | TTS URL if available |
| `masteryLevel` | `Int` | `Int16` | 0-5 (0=new, 5=mastered) |
| `easeFactor` | `Float` | `Float` | SM-2 ease factor, default 2.5 |
| `intervalDays` | `Int` | `Int32` | Days until next review |
| `nextReviewAt` | `Long` | `Date` | When to show for review |
| `totalReviews` | `Int` | `Int32` | Total review count |
| `correctReviews` | `Int` | `Int32` | Correct review count |
| `firstSeenInDialogId` | `String?` | `String?` | Which dialog introduced it |
| `firstSeenAt` | `Long` | `Date` | First encounter timestamp |
| `lastReviewedAt` | `Long?` | `Date?` | Last review timestamp |

#### PartOfSpeech Enum

```swift
enum PartOfSpeech: String, Codable, CaseIterable {
    case NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION
    case CONJUNCTION, PRONOUN, INTERJECTION, ARTICLE
}
```

#### SRS Query Requirements

Your CoreData implementation must support these queries efficiently:

```swift
// Get entries due for review (nextReviewAt <= now), ordered by urgency
func getDueForReview(language: String, limit: Int) -> [VocabularyEntry]

// Search by word prefix
func searchByWord(query: String, language: String) -> [VocabularyEntry]

// Calculate stats
func getStats(language: String) -> VocabularyStats
```

**Recommended Indexes**:
- `language` + `nextReviewAt` (compound index for due reviews)
- `language` + `word` (for search)
- `masteryLevel` (for stats calculation)

---

### 3. UserProgress

Gamification and progress tracking per language.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `userId` | `String` | `String` | **Primary Key** (composite with language) |
| `language` | `String` | `String` | **Primary Key** (composite with userId) |
| `currentStreak` | `Int` | `Int32` | Current day streak |
| `longestStreak` | `Int` | `Int32` | Best streak ever |
| `lastActivityDate` | `String` | `String` | ISO date "2026-02-05" |
| `todayMinutes` | `Int` | `Int16` | Minutes played today |
| `dailyGoalMinutes` | `Int` | `Int16` | Daily goal setting |
| `totalXP` | `Long` | `Int64` | Total XP earned |
| `weeklyXP` | `Long` | `Int64` | XP this week |
| `currentLevel` | `Int` | `Int32` | Current level |
| `totalWordsLearned` | `Int` | `Int32` | Words with mastery >= 3 |
| `wordsDueForReview` | `Int` | `Int32` | Words needing review |
| `vocabularyMasteryPercent` | `Float` | `Float` | 0.0 - 100.0 |
| `totalSessions` | `Int` | `Int32` | Total sessions played |
| `totalMinutesPlayed` | `Int` | `Int32` | Total play time |
| `soloSessions` | `Int` | `Int32` | Solo mode count |
| `multiplayerSessions` | `Int` | `Int32` | Multiplayer count |
| `successfulRepairs` | `Int` | `Int32` | Clarifications that helped |
| `helpGiven` | `Int` | `Int32` | Times helped others |
| `teamWins` | `Int` | `Int32` | Collaborative wins |
| `estimatedCEFR` | `CEFRLevel` | `String` | Estimated proficiency |
| `cefrProgressPercent` | `Float` | `Float` | Progress within level |

---

### 4. SessionStats

Per-session performance metrics.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `sessionId` | `String` | `String` | **Primary Key** |
| `startedAt` | `Long` | `Date` | Session start time |
| `endedAt` | `Long?` | `Date?` | Session end time |
| `mode` | `SessionMode` | `String` | "SOLO", "HOST", "CLIENT" |
| `scenarioId` | `String?` | `String?` | Scenario played |
| `linesSpoken` | `Int` | `Int32` | Dialog lines contributed |
| `wordsEncountered` | `Int` | `Int32` | Total words seen |
| `newVocabulary` | `Int` | `Int32` | New words learned |
| `errorsDetected` | `Int` | `Int32` | Errors found |
| `errorsCorrected` | `Int` | `Int32` | Errors fixed via recasts |
| `xpEarned` | `Int` | `Int32` | Total XP for session |
| `xpBreakdown` | `XPBreakdown` | `Transformable` or separate fields | See below |

#### XPBreakdown (Embedded)

Store as JSON Transformable or as separate columns:

| Field | Type | Notes |
|-------|------|-------|
| `dialogCompletion` | `Int` | Base completion XP |
| `vocabularyBonus` | `Int` | New words bonus |
| `accuracyBonus` | `Int` | Low error bonus |
| `collaborationBonus` | `Int` | Multiplayer help bonus |
| `streakBonus` | `Int` | Streak multiplier bonus |

---

### 5. Achievement

Gamification achievements.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `id` | `String` | `String` | **Primary Key** |
| `name` | `String` | `String` | Display name |
| `description` | `String` | `String` | Achievement description |
| `iconUrl` | `String?` | `String?` | Icon asset URL |
| `category` | `AchievementCategory` | `String` | Category enum |
| `unlockedAt` | `Long?` | `Date?` | When unlocked, null if locked |
| `progress` | `Float` | `Float` | 0.0 - 1.0 progress |

#### AchievementCategory Enum

```swift
enum AchievementCategory: String, Codable {
    case VOCABULARY, STREAK, SOCIAL, MASTERY, EXPLORATION
}
```

---

### 6. SavedSession (Dialog History)

Saved game sessions with full dialog history.

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `id` | `String` | `String` | **Primary Key** |
| `scenarioName` | `String` | `String` | Scenario display name |
| `playedAt` | `Long` | `Date` | When session occurred |
| `durationMinutes` | `Int` | `Int16` | Session duration |
| `dialogLines` | `List<DialogLine>` | `NSOrderedSet<CDDialogLine>` | To-many relationship |
| `stats` | `SessionStats?` | Relationship | Optional to-one |
| `report` | `SessionReport?` | `Transformable` or relationship | Session feedback |

#### DialogLine (Relationship)

| Field | Kotlin Type | Swift/CoreData Type | Notes |
|-------|-------------|---------------------|-------|
| `id` | `String` | `String` | **Primary Key** |
| `speakerId` | `String` | `String` | Who spoke |
| `roleName` | `String` | `String` | Role display name |
| `textNative` | `String` | `String` | Text in target language |
| `textTranslated` | `String` | `String` | Translation |
| `timestamp` | `Long` | `Date` | When spoken |
| `session` | - | `CDSavedSession` | Inverse relationship |

---

## CoreData Model Design

### Entity-Relationship Diagram

```
┌─────────────────┐       ┌─────────────────────┐
│  CDUserProfile  │───────│  CDTargetLanguage   │
│─────────────────│  1:N  │─────────────────────│
│ id              │       │ code                │
│ displayName     │       │ proficiencyLevel    │
│ nativeLanguage  │       │ startedAt           │
│ interests (JSON)│       │ profile (inverse)   │
│ ...             │       └─────────────────────┘
└─────────────────┘

┌─────────────────┐       ┌─────────────────────┐
│ CDVocabularyEnt │       │   CDUserProgress    │
│─────────────────│       │─────────────────────│
│ id              │       │ userId              │
│ word            │       │ language (compound) │
│ translation     │       │ currentStreak       │
│ language        │       │ totalXP             │
│ masteryLevel    │       │ ...                 │
│ easeFactor      │       └─────────────────────┘
│ nextReviewAt    │
│ ...             │       ┌─────────────────────┐
└─────────────────┘       │   CDAchievement     │
                          │─────────────────────│
┌─────────────────┐       │ id                  │
│ CDSavedSession  │───────│ name                │
│─────────────────│  1:N  │ unlockedAt          │
│ id              │       │ progress            │
│ scenarioName    │       └─────────────────────┘
│ playedAt        │
│ dialogLines     │───────┌─────────────────────┐
│ stats           │  1:1  │  CDSessionStats     │
└─────────────────┘       │─────────────────────│
        │                 │ sessionId           │
        │ 1:N             │ xpEarned            │
        ▼                 │ ...                 │
┌─────────────────┐       └─────────────────────┘
│  CDDialogLine   │
│─────────────────│
│ id              │
│ speakerId       │
│ textNative      │
│ session (inv)   │
└─────────────────┘
```

### Xcode Data Model (.xcdatamodeld)

Create a new CoreData model with these entities:

#### CDUserProfile

```xml
<entity name="CDUserProfile" representedClassName="CDUserProfile" syncable="YES">
    <attribute name="id" attributeType="String"/>
    <attribute name="displayName" attributeType="String"/>
    <attribute name="nativeLanguage" attributeType="String"/>
    <attribute name="currentTargetLanguage" attributeType="String"/>
    <attribute name="interestsJSON" attributeType="String"/>
    <attribute name="learningGoalsJSON" attributeType="String"/>
    <attribute name="dailyGoalMinutes" attributeType="Integer 16" defaultValueString="15"/>
    <attribute name="voiceSpeed" attributeType="String" defaultValueString="NORMAL"/>
    <attribute name="showTranslations" attributeType="String" defaultValueString="ALWAYS"/>
    <attribute name="onboardingCompleted" attributeType="Boolean" defaultValueString="NO"/>
    <attribute name="createdAt" attributeType="Date"/>
    <attribute name="lastActiveAt" attributeType="Date"/>
    <relationship name="targetLanguages" optional="YES" toMany="YES" deletionRule="Cascade" ordered="YES" destinationEntity="CDTargetLanguage" inverseName="profile"/>
</entity>
```

#### CDVocabularyEntry

```xml
<entity name="CDVocabularyEntry" representedClassName="CDVocabularyEntry" syncable="YES">
    <attribute name="id" attributeType="String"/>
    <attribute name="word" attributeType="String"/>
    <attribute name="translation" attributeType="String"/>
    <attribute name="language" attributeType="String"/>
    <attribute name="partOfSpeech" optional="YES" attributeType="String"/>
    <attribute name="exampleSentence" optional="YES" attributeType="String"/>
    <attribute name="audioUrl" optional="YES" attributeType="String"/>
    <attribute name="masteryLevel" attributeType="Integer 16" defaultValueString="0"/>
    <attribute name="easeFactor" attributeType="Float" defaultValueString="2.5"/>
    <attribute name="intervalDays" attributeType="Integer 32" defaultValueString="1"/>
    <attribute name="nextReviewAt" attributeType="Date"/>
    <attribute name="totalReviews" attributeType="Integer 32" defaultValueString="0"/>
    <attribute name="correctReviews" attributeType="Integer 32" defaultValueString="0"/>
    <attribute name="firstSeenInDialogId" optional="YES" attributeType="String"/>
    <attribute name="firstSeenAt" attributeType="Date"/>
    <attribute name="lastReviewedAt" optional="YES" attributeType="Date"/>
    
    <!-- Indexes for query performance -->
    <fetchIndex name="byLanguageAndReviewDate">
        <fetchIndexElement property="language" type="Binary" order="ascending"/>
        <fetchIndexElement property="nextReviewAt" type="Binary" order="ascending"/>
    </fetchIndex>
    <fetchIndex name="byWord">
        <fetchIndexElement property="language" type="Binary" order="ascending"/>
        <fetchIndexElement property="word" type="Binary" order="ascending"/>
    </fetchIndex>
</entity>
```

---

## Repository Implementations

### CoreDataUserProfileRepository

```swift
import CoreData
import BabLanguageSDK

final class CoreDataUserProfileRepository: UserProfileRepository {
    
    private let container: NSPersistentContainer
    private let context: NSManagedObjectContext
    
    init(container: NSPersistentContainer) {
        self.container = container
        self.context = container.viewContext
    }
    
    // MARK: - UserProfileRepository Protocol
    
    func getProfile() async -> UserProfile? {
        let request = CDUserProfile.fetchRequest()
        request.fetchLimit = 1
        
        do {
            guard let cdProfile = try context.fetch(request).first else {
                return nil
            }
            return cdProfile.toSDKModel()
        } catch {
            print("Failed to fetch profile: \(error)")
            return nil
        }
    }
    
    func saveProfile(profile: UserProfile) async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            // Find existing or create new
            let request = CDUserProfile.fetchRequest()
            request.predicate = NSPredicate(format: "id == %@", profile.id)
            
            let cdProfile: CDUserProfile
            if let existing = try? self.context.fetch(request).first {
                cdProfile = existing
            } else {
                cdProfile = CDUserProfile(context: self.context)
            }
            
            // Map SDK model to CoreData
            cdProfile.update(from: profile, context: self.context)
            
            try? self.context.save()
        }
    }
    
    func updateOnboardingComplete() async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = CDUserProfile.fetchRequest()
            request.fetchLimit = 1
            
            if let profile = try? self.context.fetch(request).first {
                profile.onboardingCompleted = true
                try? self.context.save()
            }
        }
    }
    
    func updateLastActive() async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = CDUserProfile.fetchRequest()
            request.fetchLimit = 1
            
            if let profile = try? self.context.fetch(request).first {
                profile.lastActiveAt = Date()
                try? self.context.save()
            }
        }
    }
    
    func clear() async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = CDUserProfile.fetchRequest()
            let profiles = (try? self.context.fetch(request)) ?? []
            profiles.forEach { self.context.delete($0) }
            try? self.context.save()
        }
    }
}

// MARK: - CoreData Model Extensions

extension CDUserProfile {
    
    func toSDKModel() -> UserProfile {
        let targetLangs = (targetLanguages?.array as? [CDTargetLanguage])?.map { 
            $0.toSDKModel() 
        } ?? []
        
        let interests = decodeInterests(from: interestsJSON ?? "[]")
        let goals = decodeLearningGoals(from: learningGoalsJSON ?? "[]")
        
        return UserProfile(
            id: id ?? "",
            displayName: displayName ?? "",
            nativeLanguage: nativeLanguage ?? "en-US",
            targetLanguages: targetLangs,
            currentTargetLanguage: currentTargetLanguage ?? "",
            interests: interests,
            learningGoals: goals,
            dailyGoalMinutes: Int32(dailyGoalMinutes),
            voiceSpeed: VoiceSpeed(rawValue: voiceSpeed ?? "NORMAL") ?? .normal,
            showTranslations: TranslationMode(rawValue: showTranslations ?? "ALWAYS") ?? .always,
            onboardingCompleted: onboardingCompleted,
            createdAt: Int64(createdAt?.timeIntervalSince1970 ?? 0) * 1000,
            lastActiveAt: Int64(lastActiveAt?.timeIntervalSince1970 ?? 0) * 1000
        )
    }
    
    func update(from profile: UserProfile, context: NSManagedObjectContext) {
        self.id = profile.id
        self.displayName = profile.displayName
        self.nativeLanguage = profile.nativeLanguage
        self.currentTargetLanguage = profile.currentTargetLanguage
        self.interestsJSON = encodeInterests(profile.interests)
        self.learningGoalsJSON = encodeLearningGoals(profile.learningGoals)
        self.dailyGoalMinutes = Int16(profile.dailyGoalMinutes)
        self.voiceSpeed = profile.voiceSpeed.rawValue
        self.showTranslations = profile.showTranslations.rawValue
        self.onboardingCompleted = profile.onboardingCompleted
        self.createdAt = Date(timeIntervalSince1970: TimeInterval(profile.createdAt / 1000))
        self.lastActiveAt = Date(timeIntervalSince1970: TimeInterval(profile.lastActiveAt / 1000))
        
        // Update target languages (clear and recreate)
        if let existing = targetLanguages {
            existing.forEach { context.delete($0 as! NSManagedObject) }
        }
        
        let langSet = NSMutableOrderedSet()
        for lang in profile.targetLanguages {
            let cdLang = CDTargetLanguage(context: context)
            cdLang.code = lang.code
            cdLang.proficiencyLevel = lang.proficiencyLevel.rawValue
            cdLang.startedAt = Date(timeIntervalSince1970: TimeInterval(lang.startedAt / 1000))
            cdLang.profile = self
            langSet.add(cdLang)
        }
        self.targetLanguages = langSet
    }
    
    private func decodeInterests(from json: String) -> Set<Interest> {
        guard let data = json.data(using: .utf8),
              let strings = try? JSONDecoder().decode([String].self, from: data) else {
            return []
        }
        return Set(strings.compactMap { Interest(rawValue: $0) })
    }
    
    private func encodeInterests(_ interests: Set<Interest>) -> String {
        let strings = interests.map { $0.rawValue }
        guard let data = try? JSONEncoder().encode(strings) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }
    
    // Similar for LearningGoals...
}
```

### CoreDataVocabularyRepository

```swift
import CoreData
import BabLanguageSDK

final class CoreDataVocabularyRepository: VocabularyRepository {
    
    private let context: NSManagedObjectContext
    
    init(context: NSManagedObjectContext) {
        self.context = context
    }
    
    func getAll(language: String) async -> [VocabularyEntry] {
        let request = CDVocabularyEntry.fetchRequest()
        request.predicate = NSPredicate(format: "language == %@", language)
        request.sortDescriptors = [NSSortDescriptor(key: "word", ascending: true)]
        
        do {
            let results = try context.fetch(request)
            return results.map { $0.toSDKModel() }
        } catch {
            print("Failed to fetch vocabulary: \(error)")
            return []
        }
    }
    
    func getById(id: String) async -> VocabularyEntry? {
        let request = CDVocabularyEntry.fetchRequest()
        request.predicate = NSPredicate(format: "id == %@", id)
        request.fetchLimit = 1
        
        return try? context.fetch(request).first?.toSDKModel()
    }
    
    func getDueForReview(language: String, limit: Int32) async -> [VocabularyEntry] {
        let request = CDVocabularyEntry.fetchRequest()
        request.predicate = NSPredicate(
            format: "language == %@ AND nextReviewAt <= %@",
            language,
            Date() as NSDate
        )
        request.sortDescriptors = [
            NSSortDescriptor(key: "nextReviewAt", ascending: true)
        ]
        request.fetchLimit = Int(limit)
        
        do {
            let results = try context.fetch(request)
            return results.map { $0.toSDKModel() }
        } catch {
            print("Failed to fetch due reviews: \(error)")
            return []
        }
    }
    
    func upsert(entry: VocabularyEntry) async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = CDVocabularyEntry.fetchRequest()
            request.predicate = NSPredicate(format: "id == %@", entry.id)
            
            let cdEntry: CDVocabularyEntry
            if let existing = try? self.context.fetch(request).first {
                cdEntry = existing
            } else {
                cdEntry = CDVocabularyEntry(context: self.context)
            }
            
            cdEntry.update(from: entry)
            try? self.context.save()
        }
    }
    
    func upsertAll(entries: [VocabularyEntry]) async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            for entry in entries {
                let request = CDVocabularyEntry.fetchRequest()
                request.predicate = NSPredicate(format: "id == %@", entry.id)
                
                let cdEntry: CDVocabularyEntry
                if let existing = try? self.context.fetch(request).first {
                    cdEntry = existing
                } else {
                    cdEntry = CDVocabularyEntry(context: self.context)
                }
                
                cdEntry.update(from: entry)
            }
            
            try? self.context.save()
        }
    }
    
    func getStats(language: String) async -> VocabularyStats {
        let request = CDVocabularyEntry.fetchRequest()
        request.predicate = NSPredicate(format: "language == %@", language)
        
        do {
            let entries = try context.fetch(request)
            let now = Date()
            
            let newCount = entries.filter { $0.masteryLevel == 0 }.count
            let learningCount = entries.filter { $0.masteryLevel >= 1 && $0.masteryLevel <= 2 }.count
            let reviewingCount = entries.filter { $0.masteryLevel >= 3 && $0.masteryLevel <= 4 }.count
            let masteredCount = entries.filter { $0.masteryLevel == 5 }.count
            let dueCount = entries.filter { ($0.nextReviewAt ?? Date.distantFuture) <= now }.count
            
            return VocabularyStats(
                total: Int32(entries.count),
                newCount: Int32(newCount),
                learningCount: Int32(learningCount),
                reviewingCount: Int32(reviewingCount),
                masteredCount: Int32(masteredCount),
                dueCount: Int32(dueCount)
            )
        } catch {
            return VocabularyStats(
                total: 0, newCount: 0, learningCount: 0,
                reviewingCount: 0, masteredCount: 0, dueCount: 0
            )
        }
    }
    
    func searchByWord(query: String, language: String) async -> [VocabularyEntry] {
        let request = CDVocabularyEntry.fetchRequest()
        request.predicate = NSPredicate(
            format: "language == %@ AND word BEGINSWITH[cd] %@",
            language,
            query
        )
        request.sortDescriptors = [NSSortDescriptor(key: "word", ascending: true)]
        request.fetchLimit = 20
        
        do {
            return try context.fetch(request).map { $0.toSDKModel() }
        } catch {
            return []
        }
    }
    
    func delete(id: String) async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = CDVocabularyEntry.fetchRequest()
            request.predicate = NSPredicate(format: "id == %@", id)
            
            if let entry = try? self.context.fetch(request).first {
                self.context.delete(entry)
                try? self.context.save()
            }
        }
    }
    
    func clear() async {
        await context.perform { [weak self] in
            guard let self else { return }
            
            let request = NSFetchRequest<NSFetchRequestResult>(entityName: "CDVocabularyEntry")
            let deleteRequest = NSBatchDeleteRequest(fetchRequest: request)
            
            try? self.context.execute(deleteRequest)
            try? self.context.save()
        }
    }
}

// MARK: - Model Conversion

extension CDVocabularyEntry {
    
    func toSDKModel() -> VocabularyEntry {
        VocabularyEntry(
            id: id ?? "",
            word: word ?? "",
            translation: translation ?? "",
            language: language ?? "",
            partOfSpeech: partOfSpeech.flatMap { PartOfSpeech(rawValue: $0) },
            exampleSentence: exampleSentence,
            audioUrl: audioUrl,
            masteryLevel: Int32(masteryLevel),
            easeFactor: easeFactor,
            intervalDays: intervalDays,
            nextReviewAt: Int64((nextReviewAt?.timeIntervalSince1970 ?? 0) * 1000),
            totalReviews: totalReviews,
            correctReviews: correctReviews,
            firstSeenInDialogId: firstSeenInDialogId,
            firstSeenAt: Int64((firstSeenAt?.timeIntervalSince1970 ?? 0) * 1000),
            lastReviewedAt: lastReviewedAt.map { Int64($0.timeIntervalSince1970 * 1000) }
        )
    }
    
    func update(from entry: VocabularyEntry) {
        self.id = entry.id
        self.word = entry.word
        self.translation = entry.translation
        self.language = entry.language
        self.partOfSpeech = entry.partOfSpeech?.rawValue
        self.exampleSentence = entry.exampleSentence
        self.audioUrl = entry.audioUrl
        self.masteryLevel = Int16(entry.masteryLevel)
        self.easeFactor = entry.easeFactor
        self.intervalDays = entry.intervalDays
        self.nextReviewAt = Date(timeIntervalSince1970: TimeInterval(entry.nextReviewAt / 1000))
        self.totalReviews = entry.totalReviews
        self.correctReviews = entry.correctReviews
        self.firstSeenInDialogId = entry.firstSeenInDialogId
        self.firstSeenAt = Date(timeIntervalSince1970: TimeInterval(entry.firstSeenAt / 1000))
        self.lastReviewedAt = entry.lastReviewedAt.map { 
            Date(timeIntervalSince1970: TimeInterval($0 / 1000)) 
        }
    }
}
```

---

## SDK Integration

### Setting Up the SDK with CoreData Repositories

```swift
import BabLanguageSDK
import CoreData

class AppDelegate: NSObject, UIApplicationDelegate {
    
    // CoreData stack
    lazy var persistentContainer: NSPersistentContainer = {
        let container = NSPersistentContainer(name: "BabLanguage")
        container.loadPersistentStores { _, error in
            if let error = error {
                fatalError("CoreData failed: \(error)")
            }
        }
        return container
    }()
    
    // SDK with injected repositories
    lazy var sdk: BrainSDK = {
        let context = persistentContainer.viewContext
        
        return BrainSDK(
            aiProvider: nil,  // Uses default/native
            coroutineContext: nil,
            userProfileRepository: CoreDataUserProfileRepository(container: persistentContainer),
            vocabularyRepository: CoreDataVocabularyRepository(context: context),
            progressRepository: CoreDataProgressRepository(context: context),
            dialogHistoryRepository: CoreDataDialogHistoryRepository(context: context)
        )
    }()
}
```

### SwiftUI Environment Injection

```swift
@main
struct BabLanguageApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    
    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(SDKWrapper(sdk: appDelegate.sdk))
        }
    }
}

// Wrapper for SwiftUI observation
@MainActor
class SDKWrapper: ObservableObject {
    let sdk: BrainSDK
    
    @Published var profile: UserProfile?
    @Published var vocabularyStats: VocabularyStats?
    
    init(sdk: BrainSDK) {
        self.sdk = sdk
        
        // Observe SDK StateFlows
        Task {
            for await profile in sdk.userProfile {
                self.profile = profile
            }
        }
        
        Task {
            for await stats in sdk.vocabularyStats {
                self.vocabularyStats = stats
            }
        }
    }
}
```

---

## Migration Strategies

### Lightweight Migrations

CoreData supports automatic lightweight migrations for simple changes:

```swift
let container = NSPersistentContainer(name: "BabLanguage")

let description = container.persistentStoreDescriptions.first
description?.setOption(true as NSNumber, forKey: NSMigratePersistentStoresAutomaticallyOption)
description?.setOption(true as NSNumber, forKey: NSInferMappingModelAutomaticallyOption)
```

### Versioning

When updating models, create a new model version in Xcode:
1. Select your `.xcdatamodeld` file
2. Editor → Add Model Version
3. Set new version as current

---

## Best Practices

### 1. Background Context for Writes

```swift
func saveInBackground(_ work: @escaping (NSManagedObjectContext) -> Void) {
    container.performBackgroundTask { context in
        work(context)
        if context.hasChanges {
            try? context.save()
        }
    }
}
```

### 2. Batch Operations for Large Imports

```swift
func importVocabulary(_ entries: [VocabularyEntry]) async {
    await container.performBackgroundTask { context in
        context.undoManager = nil  // Disable for performance
        
        for (index, entry) in entries.enumerated() {
            let cdEntry = CDVocabularyEntry(context: context)
            cdEntry.update(from: entry)
            
            // Save in batches of 100
            if index % 100 == 0 {
                try? context.save()
                context.reset()
            }
        }
        
        try? context.save()
    }
}
```

### 3. Efficient SRS Queries

Use fetch limits and proper indexes:

```swift
func getNextReviewBatch() async -> [VocabularyEntry] {
    let request = CDVocabularyEntry.fetchRequest()
    request.predicate = NSPredicate(format: "nextReviewAt <= %@", Date() as NSDate)
    request.sortDescriptors = [NSSortDescriptor(key: "nextReviewAt", ascending: true)]
    request.fetchLimit = 20  // Don't load all at once
    request.returnsObjectsAsFaults = false  // Prefetch data
    
    // ...
}
```

### 4. CloudKit Sync (Optional)

For iCloud sync, use `NSPersistentCloudKitContainer`:

```swift
lazy var persistentContainer: NSPersistentCloudKitContainer = {
    let container = NSPersistentCloudKitContainer(name: "BabLanguage")
    // ... configuration
    return container
}()
```

---

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| "Managed object context not found" | Ensure context is passed correctly to repositories |
| Slow vocabulary queries | Add composite index on (language, nextReviewAt) |
| Data not persisting | Call `context.save()` after modifications |
| Crash on background thread | Use `context.perform {}` for all operations |

### Debug Logging

```swift
// Enable CoreData SQL logging
let args = ProcessInfo.processInfo.arguments
if args.contains("-com.apple.CoreData.SQLDebug") {
    print("CoreData SQL debugging enabled")
}
```

---

## Summary

| Repository | Key Entities | Indexes Recommended |
|------------|--------------|---------------------|
| `UserProfileRepository` | CDUserProfile, CDTargetLanguage | id |
| `VocabularyRepository` | CDVocabularyEntry | (language, nextReviewAt), (language, word) |
| `ProgressRepository` | CDUserProgress, CDSessionStats, CDAchievement | (userId, language) |
| `DialogHistoryRepository` | CDSavedSession, CDDialogLine | playedAt |

---

*Document Version: 1.0 - February 2026*
