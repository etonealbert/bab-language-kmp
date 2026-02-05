# Language Learning Features Design

**Date**: 2026-02-05  
**Status**: Approved  
**Author**: AI-assisted design session

---

## Overview

This document specifies the domain models, interfaces, and SDK extensions required to transform "Bring a Brain" from a basic dialog SDK into a pedagogically-sound language learning platform.

### Design Goals

1. **Full User Profile** - Native/target languages, CEFR levels, interests, learning goals
2. **Vocabulary SRS** - Spaced repetition system with SM-2 inspired scheduling
3. **Pedagogical Features** - Information gaps, asymmetric difficulty, narrative recasts, safety net, plot twists
4. **Full Gamification** - Streaks, XP, collaborative stats, achievements
5. **Interface + Injection** - Repository interfaces with in-memory defaults

### References

- Research Report: "The Orchestrated Classroom" (strategic framework for multiplayer AI in SLA)
- Project Description: `research-of-what-i-need/BAB-project-description.md`

---

## 1. User Profile Domain Model

### Core Models

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    
    // Language Settings
    val nativeLanguage: LanguageCode,           // e.g., "en-US"
    val targetLanguages: List<TargetLanguage>,  // Can learn multiple
    val currentTargetLanguage: LanguageCode,    // Active learning target
    
    // Learning Preferences
    val interests: Set<Interest>,               // "travel", "business", "romance", "sci-fi"
    val learningGoals: Set<LearningGoal>,       // "conversation", "reading", "exam_prep"
    val dailyGoalMinutes: Int,                  // 5, 10, 15, 30
    
    // Voice Preferences
    val voiceSpeed: VoiceSpeed,                 // SLOW, NORMAL, FAST
    val showTranslations: TranslationMode,      // ALWAYS, ON_TAP, NEVER
    
    // Onboarding State
    val onboardingCompleted: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long
)

@Serializable
data class TargetLanguage(
    val code: LanguageCode,
    val proficiencyLevel: CEFRLevel,            // A1, A2, B1, B2, C1, C2
    val startedAt: Long
)

typealias LanguageCode = String  // ISO 639-1 with region, e.g., "es-ES", "en-US"

@Serializable
enum class CEFRLevel { 
    A1,  // Beginner
    A2,  // Elementary
    B1,  // Intermediate
    B2,  // Upper Intermediate
    C1,  // Advanced
    C2   // Mastery
}

@Serializable
enum class Interest { 
    TRAVEL, 
    BUSINESS, 
    ROMANCE, 
    SCI_FI, 
    EVERYDAY, 
    FOOD, 
    CULTURE, 
    SPORTS,
    MUSIC,
    MOVIES
}

@Serializable
enum class LearningGoal { 
    CONVERSATION, 
    READING, 
    LISTENING, 
    EXAM_PREP, 
    WORK, 
    TRAVEL 
}

@Serializable
enum class VoiceSpeed { SLOW, NORMAL, FAST }

@Serializable
enum class TranslationMode { ALWAYS, ON_TAP, NEVER }
```

### Repository Interface

```kotlin
package com.bablabs.bringabrainlanguage.domain.interfaces

interface UserProfileRepository {
    suspend fun getProfile(): UserProfile?
    suspend fun saveProfile(profile: UserProfile)
    suspend fun updateOnboardingComplete()
    suspend fun updateLastActive()
    suspend fun clear()
}
```

---

## 2. Vocabulary & SRS System

### Core Models

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class VocabularyEntry(
    val id: String,
    val word: String,                           // "cafe" 
    val translation: String,                    // "coffee"
    val language: LanguageCode,                 // "es"
    val partOfSpeech: PartOfSpeech?,            // NOUN, VERB, etc.
    val exampleSentence: String?,               // "Quiero un cafe, por favor"
    val audioUrl: String?,                      // TTS pronunciation URL
    
    // SRS Fields (SM-2 inspired)
    val masteryLevel: Int,                      // 0-5 (0=new, 5=mastered)
    val easeFactor: Float,                      // Default 2.5, adjusted by performance
    val intervalDays: Int,                      // Days until next review
    val nextReviewAt: Long,                     // Timestamp for next review
    val totalReviews: Int,
    val correctReviews: Int,
    
    // Context
    val firstSeenInDialogId: String?,           // Which dialog introduced it
    val firstSeenAt: Long,
    val lastReviewedAt: Long?
)

@Serializable
enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION, 
    CONJUNCTION, PRONOUN, INTERJECTION, ARTICLE
}

@Serializable
data class VocabularyReview(
    val entryId: String,
    val reviewedAt: Long,
    val quality: ReviewQuality,                 // AGAIN, HARD, GOOD, EASY
    val responseTimeMs: Long?                   // How fast they answered
)

@Serializable
enum class ReviewQuality { 
    AGAIN,  // Complete failure, reset interval
    HARD,   // Correct but difficult, small interval increase
    GOOD,   // Correct with effort, normal interval increase
    EASY    // Effortless, large interval increase
}

@Serializable
data class VocabularyStats(
    val total: Int,
    val newCount: Int,          // masteryLevel 0
    val learningCount: Int,     // masteryLevel 1-2
    val reviewingCount: Int,    // masteryLevel 3-4
    val masteredCount: Int,     // masteryLevel 5
    val dueCount: Int
)
```

### SRS Scheduler

```kotlin
package com.bablabs.bringabrainlanguage.domain.services

import com.bablabs.bringabrainlanguage.domain.models.*

/**
 * SM-2 inspired spaced repetition scheduler.
 * 
 * Algorithm:
 * - AGAIN: Reset to interval=1, ease-=0.2 (min 1.3)
 * - HARD: interval*1.2, ease-=0.15
 * - GOOD: interval*ease
 * - EASY: interval*ease*1.3, ease+=0.15
 */
object SRSScheduler {
    
    private const val MIN_EASE_FACTOR = 1.3f
    private const val DEFAULT_EASE_FACTOR = 2.5f
    
    /**
     * Calculate next review based on user's response quality.
     */
    fun calculateNextReview(
        entry: VocabularyEntry,
        quality: ReviewQuality
    ): VocabularyEntry {
        val now = System.currentTimeMillis()
        val newInterval: Int
        val newEase: Float
        val newMastery: Int
        
        when (quality) {
            ReviewQuality.AGAIN -> {
                newInterval = 1
                newEase = maxOf(MIN_EASE_FACTOR, entry.easeFactor - 0.2f)
                newMastery = maxOf(0, entry.masteryLevel - 1)
            }
            ReviewQuality.HARD -> {
                newInterval = maxOf(1, (entry.intervalDays * 1.2f).toInt())
                newEase = maxOf(MIN_EASE_FACTOR, entry.easeFactor - 0.15f)
                newMastery = entry.masteryLevel
            }
            ReviewQuality.GOOD -> {
                newInterval = maxOf(1, (entry.intervalDays * entry.easeFactor).toInt())
                newEase = entry.easeFactor
                newMastery = minOf(5, entry.masteryLevel + 1)
            }
            ReviewQuality.EASY -> {
                newInterval = maxOf(1, (entry.intervalDays * entry.easeFactor * 1.3f).toInt())
                newEase = entry.easeFactor + 0.15f
                newMastery = minOf(5, entry.masteryLevel + 1)
            }
        }
        
        val nextReview = now + (newInterval * 24 * 60 * 60 * 1000L)
        
        return entry.copy(
            masteryLevel = newMastery,
            easeFactor = newEase,
            intervalDays = newInterval,
            nextReviewAt = nextReview,
            totalReviews = entry.totalReviews + 1,
            correctReviews = entry.correctReviews + if (quality != ReviewQuality.AGAIN) 1 else 0,
            lastReviewedAt = now
        )
    }
    
    /**
     * Get entries due for review, sorted by urgency.
     */
    fun getDueReviews(
        entries: List<VocabularyEntry>,
        limit: Int = 20
    ): List<VocabularyEntry> {
        val now = System.currentTimeMillis()
        return entries
            .filter { it.nextReviewAt <= now }
            .sortedBy { it.nextReviewAt }
            .take(limit)
    }
    
    /**
     * Create new vocabulary entry with initial SRS values.
     */
    fun createNewEntry(
        word: String,
        translation: String,
        language: LanguageCode,
        dialogId: String? = null
    ): VocabularyEntry {
        val now = System.currentTimeMillis()
        return VocabularyEntry(
            id = generateId(),
            word = word,
            translation = translation,
            language = language,
            partOfSpeech = null,
            exampleSentence = null,
            audioUrl = null,
            masteryLevel = 0,
            easeFactor = DEFAULT_EASE_FACTOR,
            intervalDays = 1,
            nextReviewAt = now, // Due immediately
            totalReviews = 0,
            correctReviews = 0,
            firstSeenInDialogId = dialogId,
            firstSeenAt = now,
            lastReviewedAt = null
        )
    }
    
    private fun generateId(): String = 
        "vocab_${System.currentTimeMillis()}_${(0..9999).random()}"
}
```

### Repository Interface

```kotlin
package com.bablabs.bringabrainlanguage.domain.interfaces

interface VocabularyRepository {
    suspend fun getAll(language: LanguageCode): List<VocabularyEntry>
    suspend fun getById(id: String): VocabularyEntry?
    suspend fun getDueForReview(language: LanguageCode, limit: Int): List<VocabularyEntry>
    suspend fun upsert(entry: VocabularyEntry)
    suspend fun upsertAll(entries: List<VocabularyEntry>)
    suspend fun recordReview(review: VocabularyReview)
    suspend fun getStats(language: LanguageCode): VocabularyStats
    suspend fun searchByWord(query: String, language: LanguageCode): List<VocabularyEntry>
}
```

---

## 3. Pedagogical Features - AI Director Models

### Information Gap Missions

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

/**
 * Secret objective assigned to a player.
 * Other players cannot see this - forces negotiation of meaning.
 * 
 * Example: "You have a $500 budget. Don't reveal the exact amount."
 */
@Serializable
data class SecretObjective(
    val playerId: String,
    val objective: String,
    val visibleToOthers: Boolean = false,
    val completedAt: Long? = null
)
```

### Asymmetric Difficulty

```kotlin
/**
 * Per-player context for asymmetric difficulty.
 * Allows different prompts/hints based on CEFR level.
 */
@Serializable
data class PlayerContext(
    val playerId: String,
    val proficiencyLevel: CEFRLevel,
    val vocabularyHints: List<String>,          // Words to include at their level
    val grammarFocus: List<String>?,            // "past tense", "conditionals"
    val secretObjective: SecretObjective?,
    val preferredComplexity: Int                // 1-10 scale for prompt complexity
)
```

### Extended DialogContext

```kotlin
package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.*

/**
 * Extended dialog context for AI generation.
 * Includes per-player contexts for asymmetric scaffolding.
 */
data class DialogContext(
    // Existing fields
    val scenario: String,
    val userRole: String,
    val aiRole: String,
    val previousLines: List<DialogLine>,
    val targetLanguage: String = "Spanish",
    val nativeLanguage: String = "English",
    
    // NEW: Multiplayer asymmetric context
    val playerContexts: Map<String, PlayerContext> = emptyMap(),
    
    // NEW: Current pedagogical state
    val activePlotTwist: PlotTwist? = null,
    val pendingNudges: List<NudgeRequest> = emptyList()
)
```

### Feedback System

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

/**
 * Feedback types for the high-flow pedagogical model.
 */
@Serializable
sealed class Feedback {
    
    /**
     * In-flow recast by NPC character.
     * Error corrected without explicit "wrong" message.
     * 
     * Example:
     * User: "I go to store yesterday"
     * NPC: "Oh, you WENT to the store yesterday? What did you buy?"
     */
    @Serializable
    data class NarrativeRecast(
        val originalText: String,
        val correctedText: String,
        val characterId: String,
        val recastLine: String,
        val errorType: ErrorType
    ) : Feedback()
    
    /**
     * Private hint shown only to the player.
     * Triggered by silence or explicit request.
     */
    @Serializable
    data class PrivateNudge(
        val playerId: String,
        val hintLevel: HintLevel,
        val content: String,
        val triggeredAt: Long
    ) : Feedback()
    
    /**
     * Post-game detailed report.
     * Metalinguistic feedback for review.
     */
    @Serializable
    data class SessionReport(
        val playerId: String,
        val sessionId: String,
        val errorsWithCorrections: List<ErrorCorrection>,
        val vocabularyEncountered: List<String>,
        val grammarPointsPracticed: List<String>,
        val fluencyScore: Float?,
        val collaborationScore: Float?,
        val totalXPEarned: Int
    ) : Feedback()
}

@Serializable
data class ErrorCorrection(
    val original: String,
    val corrected: String,
    val errorType: ErrorType,
    val explanation: String?,
    val lineId: String
)

@Serializable
enum class ErrorType {
    GRAMMAR,
    VOCABULARY,
    SPELLING,
    WORD_ORDER,
    CONJUGATION,
    GENDER_AGREEMENT,
    ARTICLE_USAGE
}

@Serializable
enum class HintLevel { 
    VISUAL_CLUE,      // Image or emoji hint
    STARTER_WORDS,    // First few words in target language
    FULL_TRANSLATION  // Complete translation as last resort
}

@Serializable
data class NudgeRequest(
    val playerId: String,
    val silenceDurationMs: Long,
    val requestedLevel: HintLevel,
    val requestedAt: Long
)
```

### Director Plot Twists

```kotlin
/**
 * Plot twist introduced by AI Director.
 * Forces spontaneous language production.
 * 
 * Example: "The waiter suddenly speaks French instead of Spanish"
 */
@Serializable
data class PlotTwist(
    val id: String,
    val description: String,
    val visualAsset: String?,           // "spilled_coffee.png"
    val triggeredAt: Long,
    val affectsPlayers: List<String>,   // Empty = all players
    val expiresAt: Long?                // Optional expiration
)
```

---

## 4. Progress & Gamification

### Core Models

```kotlin
package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProgress(
    val userId: String,
    val language: LanguageCode,
    
    // Streaks & Daily Goals
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivityDate: String,       // ISO date "2026-02-05"
    val todayMinutes: Int,
    val dailyGoalMinutes: Int,
    
    // XP & Levels
    val totalXP: Long,
    val weeklyXP: Long,
    val currentLevel: Int,
    
    // Vocabulary Progress
    val totalWordsLearned: Int,         // masteryLevel >= 3
    val wordsDueForReview: Int,
    val vocabularyMasteryPercent: Float,
    
    // Session Stats
    val totalSessions: Int,
    val totalMinutesPlayed: Int,
    val soloSessions: Int,
    val multiplayerSessions: Int,
    
    // Collaborative Stats (emphasize teamwork over individual accuracy)
    val successfulRepairs: Int,         // Clarifications that helped partner
    val helpGiven: Int,                 // Times assisted lower-level player
    val teamWins: Int,                  // Collaborative goals achieved
    
    // CEFR Progress Estimation
    val estimatedCEFR: CEFRLevel,
    val cefrProgressPercent: Float      // Progress within current level (0-100)
)

@Serializable
data class SessionStats(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: SessionMode,
    val scenarioId: String?,
    
    // Performance
    val linesSpoken: Int,
    val wordsEncountered: Int,
    val newVocabulary: Int,
    val errorsDetected: Int,
    val errorsCorrected: Int,
    
    // XP Breakdown
    val xpEarned: Int,
    val xpBreakdown: XPBreakdown
)

@Serializable
data class XPBreakdown(
    val dialogCompletion: Int,          // Base XP for finishing
    val vocabularyBonus: Int,           // New words learned
    val accuracyBonus: Int,             // Few errors
    val collaborationBonus: Int,        // Multiplayer help
    val streakBonus: Int                // Streak multiplier
)

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val category: AchievementCategory,
    val unlockedAt: Long?,
    val progress: Float                 // 0.0 to 1.0
)

@Serializable
enum class AchievementCategory {
    VOCABULARY,     // Word mastery achievements
    STREAK,         // Consistency achievements
    SOCIAL,         // Multiplayer achievements
    MASTERY,        // CEFR level achievements
    EXPLORATION     // Scenario completion achievements
}
```

### XP Calculation Service

```kotlin
package com.bablabs.bringabrainlanguage.domain.services

object XPCalculator {
    
    // Base XP values
    private const val BASE_DIALOG_COMPLETION = 50
    private const val XP_PER_NEW_WORD = 10
    private const val XP_PER_CORRECT_LINE = 5
    private const val COLLABORATION_BONUS_BASE = 25
    
    fun calculateSessionXP(
        linesSpoken: Int,
        newVocabulary: Int,
        errorsDetected: Int,
        errorsCorrected: Int,
        isMultiplayer: Boolean,
        helpGiven: Int,
        currentStreak: Int
    ): XPBreakdown {
        val dialogXP = BASE_DIALOG_COMPLETION
        val vocabXP = newVocabulary * XP_PER_NEW_WORD
        
        // Accuracy bonus: higher if fewer errors relative to lines spoken
        val accuracyRate = if (linesSpoken > 0) {
            1f - (errorsDetected.toFloat() / linesSpoken)
        } else 0f
        val accuracyXP = (accuracyRate * 30).toInt()
        
        // Collaboration bonus for multiplayer
        val collabXP = if (isMultiplayer) {
            COLLABORATION_BONUS_BASE + (helpGiven * 10)
        } else 0
        
        // Streak multiplier (5% per day, max 50%)
        val streakMultiplier = 1f + minOf(currentStreak * 0.05f, 0.5f)
        val streakXP = ((dialogXP + vocabXP + accuracyXP + collabXP) * (streakMultiplier - 1f)).toInt()
        
        return XPBreakdown(
            dialogCompletion = dialogXP,
            vocabularyBonus = vocabXP,
            accuracyBonus = accuracyXP,
            collaborationBonus = collabXP,
            streakBonus = streakXP
        )
    }
    
    fun levelFromXP(totalXP: Long): Int {
        // Exponential leveling: each level requires more XP
        // Level 1: 0 XP, Level 2: 100 XP, Level 3: 300 XP, etc.
        var level = 1
        var requiredXP = 0L
        while (totalXP >= requiredXP) {
            level++
            requiredXP += level * 100L
        }
        return level - 1
    }
}
```

### Repository Interfaces

```kotlin
package com.bablabs.bringabrainlanguage.domain.interfaces

interface ProgressRepository {
    suspend fun getProgress(language: LanguageCode): UserProgress?
    suspend fun saveProgress(progress: UserProgress)
    suspend fun addXP(amount: Int, breakdown: XPBreakdown)
    suspend fun recordSession(stats: SessionStats)
    suspend fun updateStreak()
    suspend fun getSessionHistory(limit: Int): List<SessionStats>
    suspend fun getAchievements(): List<Achievement>
    suspend fun unlockAchievement(id: String)
}

interface DialogHistoryRepository {
    suspend fun saveSession(state: SessionState, stats: SessionStats?)
    suspend fun getSessions(limit: Int): List<SavedSession>
    suspend fun getSession(id: String): SavedSession?
    suspend fun deleteSession(id: String)
}

@Serializable
data class SavedSession(
    val id: String,
    val scenarioName: String,
    val playedAt: Long,
    val durationMinutes: Int,
    val dialogLines: List<DialogLine>,
    val stats: SessionStats?,
    val report: Feedback.SessionReport?
)
```

---

## 5. Updated BrainSDK API

### Constructor Changes

```kotlin
class BrainSDK(
    // Existing
    aiProvider: AIProvider? = null,
    coroutineContext: CoroutineContext = Dispatchers.Default,
    
    // NEW: Repository injection
    userProfileRepository: UserProfileRepository = InMemoryUserProfileRepository(),
    vocabularyRepository: VocabularyRepository = InMemoryVocabularyRepository(),
    progressRepository: ProgressRepository = InMemoryProgressRepository(),
    dialogHistoryRepository: DialogHistoryRepository = InMemoryDialogHistoryRepository()
)
```

### New Public Properties

```kotlin
// Existing
val state: StateFlow<SessionState>
val aiCapabilities: AICapabilities

// NEW
val userProfile: StateFlow<UserProfile?>
val vocabularyStats: StateFlow<VocabularyStats>
val dueReviews: StateFlow<List<VocabularyEntry>>
val progress: StateFlow<UserProgress?>
```

### New Public Methods

```kotlin
// ========== ONBOARDING ==========

/** Complete onboarding with full profile */
suspend fun completeOnboarding(profile: UserProfile)

/** Check if onboarding is needed */
fun isOnboardingRequired(): Boolean

/** Update profile (languages, interests, etc.) */
suspend fun updateProfile(update: (UserProfile) -> UserProfile)

// ========== VOCABULARY ==========

/** Get words due for review */
suspend fun getVocabularyForReview(limit: Int = 20): List<VocabularyEntry>

/** Record a vocabulary review result */
suspend fun recordVocabularyReview(entryId: String, quality: ReviewQuality)

/** Manually add word to vocabulary */
suspend fun addToVocabulary(entry: VocabularyEntry)

/** Extract vocabulary from current dialog session */
suspend fun extractVocabularyFromSession(): List<VocabularyEntry>

// ========== PROGRESS ==========

/** Get current progress stats */
suspend fun getProgress(): UserProgress?

/** Get session history */
suspend fun getSessionHistory(limit: Int = 10): List<SavedSession>

// ========== PEDAGOGICAL FEATURES ==========

/** Request a hint (Safety Net) */
fun requestHint(level: HintLevel = HintLevel.STARTER_WORDS)

/** Host triggers a plot twist */
fun triggerPlotTwist(description: String)

/** Set secret objective for a player (host only) */
fun setSecretObjective(playerId: String, objective: String)

/** Get session report after game ends */
suspend fun getSessionReport(): Feedback.SessionReport?
```

### New DialogStore Intents

```kotlin
sealed class Intent {
    // ... existing intents ...
    
    // Pedagogical
    data class RequestHint(val level: HintLevel) : Intent()
    data class TriggerPlotTwist(val description: String) : Intent()
    data class SetSecretObjective(val playerId: String, val objective: String) : Intent()
    
    // Vocabulary
    data class ExtractVocabulary(val dialogLineId: String) : Intent()
    
    // Session lifecycle
    data object EndSession : Intent()
}
```

### New Packet Types

```kotlin
sealed class PacketPayload {
    // ... existing payloads ...
    
    // Pedagogical
    @Serializable
    data class PlotTwistTriggered(val twist: PlotTwist) : PacketPayload()
    
    @Serializable
    data class HintProvided(val nudge: Feedback.PrivateNudge) : PacketPayload()
    
    @Serializable
    data class SecretObjectiveAssigned(val objective: SecretObjective) : PacketPayload()
    
    @Serializable
    data class RecastGenerated(val recast: Feedback.NarrativeRecast) : PacketPayload()
}
```

---

## 6. Updated SessionState

```kotlin
@Serializable
data class SessionState(
    // Existing fields
    val mode: SessionMode = SessionMode.SOLO,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val peers: List<Participant> = emptyList(),
    val localPeerId: String = "",
    val scenario: Scenario? = null,
    val roles: Map<String, Role> = emptyMap(),
    val dialogHistory: List<DialogLine> = emptyList(),
    val currentPhase: GamePhase = GamePhase.LOBBY,
    val pendingVote: PendingVote? = null,
    val voteResults: Map<String, Boolean> = emptyMap(),
    val vectorClock: VectorClock = VectorClock(),
    val lastSyncTimestamp: Long = 0L,
    
    // NEW: Pedagogical state
    val playerContexts: Map<String, PlayerContext> = emptyMap(),
    val activePlotTwist: PlotTwist? = null,
    val recentFeedback: List<Feedback> = emptyList(),
    val sessionStats: SessionStats? = null
)
```

---

## 7. File Structure After Implementation

```
composeApp/src/commonMain/kotlin/com/bablabs/bringabrainlanguage/
├── BrainSDK.kt                         # Updated with new methods
├── domain/
│   ├── interfaces/
│   │   ├── AIProvider.kt               # Extended DialogContext
│   │   ├── NetworkSession.kt           
│   │   ├── UserProfileRepository.kt    # NEW
│   │   ├── VocabularyRepository.kt     # NEW
│   │   ├── ProgressRepository.kt       # NEW
│   │   └── DialogHistoryRepository.kt  # NEW
│   ├── models/
│   │   ├── DialogLine.kt
│   │   ├── Packet.kt                   # Extended with new payloads
│   │   ├── Participant.kt
│   │   ├── SessionState.kt             # Extended
│   │   ├── VectorClock.kt
│   │   ├── UserProfile.kt              # NEW
│   │   ├── VocabularyEntry.kt          # NEW
│   │   ├── UserProgress.kt             # NEW
│   │   ├── Feedback.kt                 # NEW (sealed class)
│   │   ├── PlotTwist.kt                # NEW
│   │   └── Achievement.kt              # NEW
│   ├── stores/
│   │   └── DialogStore.kt              # Extended with new intents
│   └── services/
│       ├── SRSScheduler.kt             # NEW
│       └── XPCalculator.kt             # NEW
└── infrastructure/
    ├── ai/
    │   ├── MockAIProvider.kt
    │   ├── NativeLLMProvider.kt
    │   └── DeviceCapabilities.kt
    ├── network/
    │   ├── LoopbackNetworkSession.kt
    │   └── ble/
    │       └── ...
    └── persistence/                    # NEW
        ├── InMemoryUserProfileRepository.kt
        ├── InMemoryVocabularyRepository.kt
        ├── InMemoryProgressRepository.kt
        └── InMemoryDialogHistoryRepository.kt
```

---

## 8. Implementation Phases

### Phase 4A: Core Models (1-2 days)
1. Create `UserProfile.kt` with all enums
2. Create `VocabularyEntry.kt` with SRS fields
3. Create `UserProgress.kt` with gamification fields
4. Create `Feedback.kt` sealed class
5. Create `PlotTwist.kt` and `SecretObjective.kt`
6. Write unit tests for all models

### Phase 4B: SRS Service (1 day)
1. Implement `SRSScheduler.kt` with SM-2 algorithm
2. Implement `XPCalculator.kt`
3. Write comprehensive tests

### Phase 4C: Repository Interfaces (1 day)
1. Create all repository interfaces
2. Implement in-memory repositories
3. Write tests

### Phase 4D: SDK Integration (2-3 days)
1. Update `BrainSDK.kt` constructor
2. Add new public methods
3. Extend `DialogStore` with new intents
4. Extend `Packet` with new payload types
5. Update `SessionState`
6. Integration tests

### Phase 4E: AI Provider Enhancement (1-2 days)
1. Extend `DialogContext` for asymmetric difficulty
2. Update `MockAIProvider` to demonstrate features
3. Document prompt engineering guidelines for implementations

---

## 9. Testing Strategy

### Unit Tests Required

- `UserProfileTest.kt` - Profile construction, validation
- `VocabularyEntryTest.kt` - SRS field calculations
- `SRSSchedulerTest.kt` - SM-2 algorithm correctness
- `XPCalculatorTest.kt` - XP breakdown accuracy
- `FeedbackTest.kt` - Sealed class serialization

### Integration Tests Required

- `OnboardingFlowTest.kt` - Profile creation → first game
- `VocabularyExtractionTest.kt` - Dialog → vocabulary entries
- `ProgressTrackingTest.kt` - Session → XP → level up
- `AsymmetricDifficultyTest.kt` - Mixed-level multiplayer

---

## 10. Migration Notes

### Breaking Changes
None. All new features are additive.

### Backwards Compatibility
- Existing `BrainSDK()` constructor still works (uses in-memory defaults)
- Existing methods unchanged
- Apps can adopt new features incrementally

---

*Design approved: 2026-02-05*
