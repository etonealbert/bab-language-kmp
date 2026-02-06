# Room Persistence Guide for BabLanguageSDK

**Version**: 1.0  
**SDK Version**: Phase 4+  
**Last Updated**: 2026-02-05

---

## Overview

The BabLanguageSDK uses a **repository injection pattern** - it defines interfaces for data persistence but provides only in-memory implementations by default. For production Android apps, you must implement these repositories using Room (or your preferred persistence layer like Realm).

This guide provides:
1. Complete data model specifications
2. Room entity designs with relationships
3. DAO interfaces
4. Repository implementations
5. SDK integration patterns

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Gradle Setup](#gradle-setup)
3. [Room Entity Definitions](#room-entity-definitions)
4. [DAO Interfaces](#dao-interfaces)
5. [Repository Implementations](#repository-implementations)
6. [SDK Integration](#sdk-integration)
7. [Migration Strategies](#migration-strategies)
8. [Best Practices](#best-practices)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Your Android App                            │
├─────────────────────────────────────────────────────────────────┤
│  Room Database                     │  Repository Implementations │
│  ┌─────────────────┐               │  ┌───────────────────────┐  │
│  │  @Entity        │◄──────────────┼──│ RoomUserProfile       │  │
│  │   Classes       │               │  │    Repository         │  │
│  └─────────────────┘               │  └───────────────────────┘  │
│           │                        │             │               │
│           ▼                        │             ▼               │
│  ┌─────────────────┐               │  ┌───────────────────────┐  │
│  │ RoomDatabase    │               │  │   BrainSDK Instance   │  │
│  │  (@Database)    │               │  │ (injected repositories)│  │
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

## Gradle Setup

### build.gradle.kts (Module level)

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") version "1.9.22-1.0.17"  // For Room annotation processing
}

android {
    // ...
    
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.incremental", "true")
    }
}

dependencies {
    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    // BabLanguageSDK
    implementation("com.bablabs:brain-sdk:1.0.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // JSON for type converters
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}
```

---

## Room Entity Definitions

### 1. UserProfileEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "native_language")
    val nativeLanguage: String,
    
    @ColumnInfo(name = "current_target_language")
    val currentTargetLanguage: String,
    
    @ColumnInfo(name = "interests_json")
    val interestsJson: String,  // JSON-encoded Set<Interest>
    
    @ColumnInfo(name = "learning_goals_json")
    val learningGoalsJson: String,  // JSON-encoded Set<LearningGoal>
    
    @ColumnInfo(name = "daily_goal_minutes")
    val dailyGoalMinutes: Int,
    
    @ColumnInfo(name = "voice_speed")
    val voiceSpeed: String,  // VoiceSpeed.name
    
    @ColumnInfo(name = "show_translations")
    val showTranslations: String,  // TranslationMode.name
    
    @ColumnInfo(name = "onboarding_completed")
    val onboardingCompleted: Boolean,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,  // Epoch milliseconds
    
    @ColumnInfo(name = "last_active_at")
    val lastActiveAt: Long
)

@Entity(
    tableName = "target_languages",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profile_id")]
)
data class TargetLanguageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "profile_id")
    val profileId: String,
    
    val code: String,
    
    @ColumnInfo(name = "proficiency_level")
    val proficiencyLevel: String,  // CEFRLevel.name
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long
)

// Extension functions for conversion
fun UserProfileEntity.toSDKModel(targetLanguages: List<TargetLanguageEntity>): UserProfile {
    return UserProfile(
        id = id,
        displayName = displayName,
        nativeLanguage = nativeLanguage,
        targetLanguages = targetLanguages.map { it.toSDKModel() },
        currentTargetLanguage = currentTargetLanguage,
        interests = decodeInterests(interestsJson),
        learningGoals = decodeLearningGoals(learningGoalsJson),
        dailyGoalMinutes = dailyGoalMinutes,
        voiceSpeed = VoiceSpeed.valueOf(voiceSpeed),
        showTranslations = TranslationMode.valueOf(showTranslations),
        onboardingCompleted = onboardingCompleted,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt
    )
}

fun TargetLanguageEntity.toSDKModel(): TargetLanguage {
    return TargetLanguage(
        code = code,
        proficiencyLevel = CEFRLevel.valueOf(proficiencyLevel),
        startedAt = startedAt
    )
}

fun UserProfile.toEntity(): UserProfileEntity {
    return UserProfileEntity(
        id = id,
        displayName = displayName,
        nativeLanguage = nativeLanguage,
        currentTargetLanguage = currentTargetLanguage,
        interestsJson = encodeInterests(interests),
        learningGoalsJson = encodeLearningGoals(learningGoals),
        dailyGoalMinutes = dailyGoalMinutes,
        voiceSpeed = voiceSpeed.name,
        showTranslations = showTranslations.name,
        onboardingCompleted = onboardingCompleted,
        createdAt = createdAt,
        lastActiveAt = lastActiveAt
    )
}

fun TargetLanguage.toEntity(profileId: String): TargetLanguageEntity {
    return TargetLanguageEntity(
        profileId = profileId,
        code = code,
        proficiencyLevel = proficiencyLevel.name,
        startedAt = startedAt
    )
}

// JSON encoding helpers
private fun encodeInterests(interests: Set<Interest>): String {
    return interests.joinToString(",") { it.name }
}

private fun decodeInterests(json: String): Set<Interest> {
    if (json.isBlank()) return emptySet()
    return json.split(",").mapNotNull { 
        runCatching { Interest.valueOf(it) }.getOrNull() 
    }.toSet()
}

private fun encodeLearningGoals(goals: Set<LearningGoal>): String {
    return goals.joinToString(",") { it.name }
}

private fun decodeLearningGoals(json: String): Set<LearningGoal> {
    if (json.isBlank()) return emptySet()
    return json.split(",").mapNotNull { 
        runCatching { LearningGoal.valueOf(it) }.getOrNull() 
    }.toSet()
}
```

### 2. VocabularyEntryEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(
    tableName = "vocabulary_entries",
    indices = [
        Index(value = ["language", "next_review_at"]),
        Index(value = ["language", "word"]),
        Index(value = ["mastery_level"])
    ]
)
data class VocabularyEntryEntity(
    @PrimaryKey
    val id: String,
    
    val word: String,
    val translation: String,
    val language: String,
    
    @ColumnInfo(name = "part_of_speech")
    val partOfSpeech: String?,  // PartOfSpeech?.name
    
    @ColumnInfo(name = "example_sentence")
    val exampleSentence: String?,
    
    @ColumnInfo(name = "audio_url")
    val audioUrl: String?,
    
    @ColumnInfo(name = "mastery_level")
    val masteryLevel: Int,  // 0-5
    
    @ColumnInfo(name = "ease_factor")
    val easeFactor: Float,  // Default 2.5
    
    @ColumnInfo(name = "interval_days")
    val intervalDays: Int,
    
    @ColumnInfo(name = "next_review_at")
    val nextReviewAt: Long,  // Epoch ms
    
    @ColumnInfo(name = "total_reviews")
    val totalReviews: Int,
    
    @ColumnInfo(name = "correct_reviews")
    val correctReviews: Int,
    
    @ColumnInfo(name = "first_seen_in_dialog_id")
    val firstSeenInDialogId: String?,
    
    @ColumnInfo(name = "first_seen_at")
    val firstSeenAt: Long,
    
    @ColumnInfo(name = "last_reviewed_at")
    val lastReviewedAt: Long?
)

fun VocabularyEntryEntity.toSDKModel(): VocabularyEntry {
    return VocabularyEntry(
        id = id,
        word = word,
        translation = translation,
        language = language,
        partOfSpeech = partOfSpeech?.let { PartOfSpeech.valueOf(it) },
        exampleSentence = exampleSentence,
        audioUrl = audioUrl,
        masteryLevel = masteryLevel,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        nextReviewAt = nextReviewAt,
        totalReviews = totalReviews,
        correctReviews = correctReviews,
        firstSeenInDialogId = firstSeenInDialogId,
        firstSeenAt = firstSeenAt,
        lastReviewedAt = lastReviewedAt
    )
}

fun VocabularyEntry.toEntity(): VocabularyEntryEntity {
    return VocabularyEntryEntity(
        id = id,
        word = word,
        translation = translation,
        language = language,
        partOfSpeech = partOfSpeech?.name,
        exampleSentence = exampleSentence,
        audioUrl = audioUrl,
        masteryLevel = masteryLevel,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        nextReviewAt = nextReviewAt,
        totalReviews = totalReviews,
        correctReviews = correctReviews,
        firstSeenInDialogId = firstSeenInDialogId,
        firstSeenAt = firstSeenAt,
        lastReviewedAt = lastReviewedAt
    )
}
```

### 3. UserProgressEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(
    tableName = "user_progress",
    primaryKeys = ["user_id", "language"]
)
data class UserProgressEntity(
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    val language: String,
    
    @ColumnInfo(name = "current_streak")
    val currentStreak: Int,
    
    @ColumnInfo(name = "longest_streak")
    val longestStreak: Int,
    
    @ColumnInfo(name = "last_activity_date")
    val lastActivityDate: String,  // ISO date "2026-02-05"
    
    @ColumnInfo(name = "today_minutes")
    val todayMinutes: Int,
    
    @ColumnInfo(name = "daily_goal_minutes")
    val dailyGoalMinutes: Int,
    
    @ColumnInfo(name = "total_xp")
    val totalXP: Long,
    
    @ColumnInfo(name = "weekly_xp")
    val weeklyXP: Long,
    
    @ColumnInfo(name = "current_level")
    val currentLevel: Int,
    
    @ColumnInfo(name = "total_words_learned")
    val totalWordsLearned: Int,
    
    @ColumnInfo(name = "words_due_for_review")
    val wordsDueForReview: Int,
    
    @ColumnInfo(name = "vocabulary_mastery_percent")
    val vocabularyMasteryPercent: Float,
    
    @ColumnInfo(name = "total_sessions")
    val totalSessions: Int,
    
    @ColumnInfo(name = "total_minutes_played")
    val totalMinutesPlayed: Int,
    
    @ColumnInfo(name = "solo_sessions")
    val soloSessions: Int,
    
    @ColumnInfo(name = "multiplayer_sessions")
    val multiplayerSessions: Int,
    
    @ColumnInfo(name = "successful_repairs")
    val successfulRepairs: Int,
    
    @ColumnInfo(name = "help_given")
    val helpGiven: Int,
    
    @ColumnInfo(name = "team_wins")
    val teamWins: Int,
    
    @ColumnInfo(name = "estimated_cefr")
    val estimatedCEFR: String,  // CEFRLevel.name
    
    @ColumnInfo(name = "cefr_progress_percent")
    val cefrProgressPercent: Float
)

fun UserProgressEntity.toSDKModel(): UserProgress {
    return UserProgress(
        userId = userId,
        language = language,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        lastActivityDate = lastActivityDate,
        todayMinutes = todayMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
        totalXP = totalXP,
        weeklyXP = weeklyXP,
        currentLevel = currentLevel,
        totalWordsLearned = totalWordsLearned,
        wordsDueForReview = wordsDueForReview,
        vocabularyMasteryPercent = vocabularyMasteryPercent,
        totalSessions = totalSessions,
        totalMinutesPlayed = totalMinutesPlayed,
        soloSessions = soloSessions,
        multiplayerSessions = multiplayerSessions,
        successfulRepairs = successfulRepairs,
        helpGiven = helpGiven,
        teamWins = teamWins,
        estimatedCEFR = CEFRLevel.valueOf(estimatedCEFR),
        cefrProgressPercent = cefrProgressPercent
    )
}

fun UserProgress.toEntity(): UserProgressEntity {
    return UserProgressEntity(
        userId = userId,
        language = language,
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        lastActivityDate = lastActivityDate,
        todayMinutes = todayMinutes,
        dailyGoalMinutes = dailyGoalMinutes,
        totalXP = totalXP,
        weeklyXP = weeklyXP,
        currentLevel = currentLevel,
        totalWordsLearned = totalWordsLearned,
        wordsDueForReview = wordsDueForReview,
        vocabularyMasteryPercent = vocabularyMasteryPercent,
        totalSessions = totalSessions,
        totalMinutesPlayed = totalMinutesPlayed,
        soloSessions = soloSessions,
        multiplayerSessions = multiplayerSessions,
        successfulRepairs = successfulRepairs,
        helpGiven = helpGiven,
        teamWins = teamWins,
        estimatedCEFR = estimatedCEFR.name,
        cefrProgressPercent = cefrProgressPercent
    )
}
```

### 4. SessionStatsEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(
    tableName = "session_stats",
    indices = [Index("started_at")]
)
data class SessionStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    
    @ColumnInfo(name = "ended_at")
    val endedAt: Long?,
    
    val mode: String,  // SessionMode.name
    
    @ColumnInfo(name = "scenario_id")
    val scenarioId: String?,
    
    @ColumnInfo(name = "lines_spoken")
    val linesSpoken: Int,
    
    @ColumnInfo(name = "words_encountered")
    val wordsEncountered: Int,
    
    @ColumnInfo(name = "new_vocabulary")
    val newVocabulary: Int,
    
    @ColumnInfo(name = "errors_detected")
    val errorsDetected: Int,
    
    @ColumnInfo(name = "errors_corrected")
    val errorsCorrected: Int,
    
    @ColumnInfo(name = "xp_earned")
    val xpEarned: Int,
    
    // XPBreakdown as embedded fields
    @ColumnInfo(name = "xp_dialog_completion")
    val xpDialogCompletion: Int,
    
    @ColumnInfo(name = "xp_vocabulary_bonus")
    val xpVocabularyBonus: Int,
    
    @ColumnInfo(name = "xp_accuracy_bonus")
    val xpAccuracyBonus: Int,
    
    @ColumnInfo(name = "xp_collaboration_bonus")
    val xpCollaborationBonus: Int,
    
    @ColumnInfo(name = "xp_streak_bonus")
    val xpStreakBonus: Int
)

fun SessionStatsEntity.toSDKModel(): SessionStats {
    return SessionStats(
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        mode = SessionMode.valueOf(mode),
        scenarioId = scenarioId,
        linesSpoken = linesSpoken,
        wordsEncountered = wordsEncountered,
        newVocabulary = newVocabulary,
        errorsDetected = errorsDetected,
        errorsCorrected = errorsCorrected,
        xpEarned = xpEarned,
        xpBreakdown = XPBreakdown(
            dialogCompletion = xpDialogCompletion,
            vocabularyBonus = xpVocabularyBonus,
            accuracyBonus = xpAccuracyBonus,
            collaborationBonus = xpCollaborationBonus,
            streakBonus = xpStreakBonus
        )
    )
}

fun SessionStats.toEntity(): SessionStatsEntity {
    return SessionStatsEntity(
        sessionId = sessionId,
        startedAt = startedAt,
        endedAt = endedAt,
        mode = mode.name,
        scenarioId = scenarioId,
        linesSpoken = linesSpoken,
        wordsEncountered = wordsEncountered,
        newVocabulary = newVocabulary,
        errorsDetected = errorsDetected,
        errorsCorrected = errorsCorrected,
        xpEarned = xpEarned,
        xpDialogCompletion = xpBreakdown.dialogCompletion,
        xpVocabularyBonus = xpBreakdown.vocabularyBonus,
        xpAccuracyBonus = xpBreakdown.accuracyBonus,
        xpCollaborationBonus = xpBreakdown.collaborationBonus,
        xpStreakBonus = xpBreakdown.streakBonus
    )
}
```

### 5. AchievementEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    val description: String,
    
    @ColumnInfo(name = "icon_url")
    val iconUrl: String?,
    
    val category: String,  // AchievementCategory.name
    
    @ColumnInfo(name = "unlocked_at")
    val unlockedAt: Long?,
    
    val progress: Float  // 0.0 - 1.0
)

fun AchievementEntity.toSDKModel(): Achievement {
    return Achievement(
        id = id,
        name = name,
        description = description,
        iconUrl = iconUrl,
        category = AchievementCategory.valueOf(category),
        unlockedAt = unlockedAt,
        progress = progress
    )
}

fun Achievement.toEntity(): AchievementEntity {
    return AchievementEntity(
        id = id,
        name = name,
        description = description,
        iconUrl = iconUrl,
        category = category.name,
        unlockedAt = unlockedAt,
        progress = progress
    )
}
```

### 6. SavedSessionEntity & DialogLineEntity

```kotlin
package com.yourapp.data.entities

import androidx.room.*
import com.bablabs.bringabrainlanguage.domain.interfaces.SavedSession
import com.bablabs.bringabrainlanguage.domain.models.*

@Entity(
    tableName = "saved_sessions",
    indices = [Index("played_at")]
)
data class SavedSessionEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "scenario_name")
    val scenarioName: String,
    
    @ColumnInfo(name = "played_at")
    val playedAt: Long,
    
    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Int,
    
    @ColumnInfo(name = "stats_id")
    val statsId: String?,  // FK to session_stats
    
    @ColumnInfo(name = "report_json")
    val reportJson: String?  // Serialized SessionReport
)

@Entity(
    tableName = "dialog_lines",
    foreignKeys = [
        ForeignKey(
            entity = SavedSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("session_id")]
)
data class DialogLineEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "speaker_id")
    val speakerId: String,
    
    @ColumnInfo(name = "role_name")
    val roleName: String,
    
    @ColumnInfo(name = "text_native")
    val textNative: String,
    
    @ColumnInfo(name = "text_translated")
    val textTranslated: String,
    
    val timestamp: Long,
    
    @ColumnInfo(name = "line_order")
    val lineOrder: Int  // For ordering within session
)

fun DialogLineEntity.toSDKModel(): DialogLine {
    return DialogLine(
        id = id,
        speakerId = speakerId,
        roleName = roleName,
        textNative = textNative,
        textTranslated = textTranslated,
        timestamp = timestamp
    )
}

fun DialogLine.toEntity(sessionId: String, order: Int): DialogLineEntity {
    return DialogLineEntity(
        id = id,
        sessionId = sessionId,
        speakerId = speakerId,
        roleName = roleName,
        textNative = textNative,
        textTranslated = textTranslated,
        timestamp = timestamp,
        lineOrder = order
    )
}
```

---

## DAO Interfaces

### UserProfileDao

```kotlin
package com.yourapp.data.dao

import androidx.room.*
import com.yourapp.data.entities.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    
    @Query("SELECT * FROM user_profiles LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?
    
    @Query("SELECT * FROM target_languages WHERE profile_id = :profileId ORDER BY started_at")
    suspend fun getTargetLanguages(profileId: String): List<TargetLanguageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetLanguages(languages: List<TargetLanguageEntity>)
    
    @Query("DELETE FROM target_languages WHERE profile_id = :profileId")
    suspend fun deleteTargetLanguages(profileId: String)
    
    @Query("UPDATE user_profiles SET onboarding_completed = 1 WHERE id = (SELECT id FROM user_profiles LIMIT 1)")
    suspend fun updateOnboardingComplete()
    
    @Query("UPDATE user_profiles SET last_active_at = :timestamp WHERE id = (SELECT id FROM user_profiles LIMIT 1)")
    suspend fun updateLastActive(timestamp: Long)
    
    @Query("DELETE FROM user_profiles")
    suspend fun clearAll()
    
    @Transaction
    suspend fun saveProfileWithLanguages(profile: UserProfileEntity, languages: List<TargetLanguageEntity>) {
        deleteTargetLanguages(profile.id)
        insertProfile(profile)
        insertTargetLanguages(languages)
    }
}
```

### VocabularyDao

```kotlin
package com.yourapp.data.dao

import androidx.room.*
import com.yourapp.data.entities.VocabularyEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabularyDao {
    
    @Query("SELECT * FROM vocabulary_entries WHERE language = :language ORDER BY word")
    suspend fun getAll(language: String): List<VocabularyEntryEntity>
    
    @Query("SELECT * FROM vocabulary_entries WHERE id = :id")
    suspend fun getById(id: String): VocabularyEntryEntity?
    
    @Query("""
        SELECT * FROM vocabulary_entries 
        WHERE language = :language AND next_review_at <= :now
        ORDER BY next_review_at ASC
        LIMIT :limit
    """)
    suspend fun getDueForReview(language: String, now: Long, limit: Int): List<VocabularyEntryEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VocabularyEntryEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<VocabularyEntryEntity>)
    
    @Query("""
        SELECT * FROM vocabulary_entries 
        WHERE language = :language AND word LIKE :query || '%'
        ORDER BY word
        LIMIT 20
    """)
    suspend fun searchByWord(query: String, language: String): List<VocabularyEntryEntity>
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language")
    suspend fun getTotal(language: String): Int
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language AND mastery_level = 0")
    suspend fun getNewCount(language: String): Int
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language AND mastery_level BETWEEN 1 AND 2")
    suspend fun getLearningCount(language: String): Int
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language AND mastery_level BETWEEN 3 AND 4")
    suspend fun getReviewingCount(language: String): Int
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language AND mastery_level = 5")
    suspend fun getMasteredCount(language: String): Int
    
    @Query("SELECT COUNT(*) FROM vocabulary_entries WHERE language = :language AND next_review_at <= :now")
    suspend fun getDueCount(language: String, now: Long): Int
    
    @Query("DELETE FROM vocabulary_entries WHERE id = :id")
    suspend fun delete(id: String)
    
    @Query("DELETE FROM vocabulary_entries")
    suspend fun clearAll()
    
    // Flow for observing changes
    @Query("SELECT * FROM vocabulary_entries WHERE language = :language AND next_review_at <= :now ORDER BY next_review_at LIMIT 20")
    fun observeDueReviews(language: String, now: Long): Flow<List<VocabularyEntryEntity>>
}
```

### ProgressDao

```kotlin
package com.yourapp.data.dao

import androidx.room.*
import com.yourapp.data.entities.*

@Dao
interface ProgressDao {
    
    @Query("SELECT * FROM user_progress WHERE language = :language LIMIT 1")
    suspend fun getProgress(language: String): UserProgressEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: UserProgressEntity)
    
    @Query("""
        UPDATE user_progress 
        SET total_xp = total_xp + :amount,
            weekly_xp = weekly_xp + :amount
        WHERE language = :language
    """)
    suspend fun addXP(language: String, amount: Int)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordSession(stats: SessionStatsEntity)
    
    @Query("SELECT * FROM session_stats ORDER BY started_at DESC LIMIT :limit")
    suspend fun getSessionHistory(limit: Int): List<SessionStatsEntity>
    
    @Query("SELECT * FROM achievements ORDER BY unlocked_at DESC NULLS LAST")
    suspend fun getAchievements(): List<AchievementEntity>
    
    @Query("UPDATE achievements SET unlocked_at = :timestamp WHERE id = :id")
    suspend fun unlockAchievement(id: String, timestamp: Long)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievement(achievement: AchievementEntity)
    
    @Query("DELETE FROM user_progress")
    suspend fun clearProgress()
    
    @Query("DELETE FROM session_stats")
    suspend fun clearSessions()
    
    @Query("DELETE FROM achievements")
    suspend fun clearAchievements()
}
```

### DialogHistoryDao

```kotlin
package com.yourapp.data.dao

import androidx.room.*
import com.yourapp.data.entities.*

@Dao
interface DialogHistoryDao {
    
    @Query("SELECT * FROM saved_sessions ORDER BY played_at DESC LIMIT :limit")
    suspend fun getSessions(limit: Int): List<SavedSessionEntity>
    
    @Query("SELECT * FROM saved_sessions WHERE id = :id")
    suspend fun getSession(id: String): SavedSessionEntity?
    
    @Query("SELECT * FROM dialog_lines WHERE session_id = :sessionId ORDER BY line_order")
    suspend fun getDialogLines(sessionId: String): List<DialogLineEntity>
    
    @Query("SELECT * FROM session_stats WHERE session_id = :sessionId")
    suspend fun getSessionStats(sessionId: String): SessionStatsEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SavedSessionEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDialogLines(lines: List<DialogLineEntity>)
    
    @Query("DELETE FROM saved_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
    
    @Query("DELETE FROM saved_sessions")
    suspend fun clearAll()
    
    @Transaction
    suspend fun saveSessionWithLines(
        session: SavedSessionEntity,
        lines: List<DialogLineEntity>,
        stats: SessionStatsEntity?
    ) {
        insertSession(session)
        insertDialogLines(lines)
        stats?.let { /* handled by ProgressDao */ }
    }
}
```

---

## Repository Implementations

### RoomUserProfileRepository

```kotlin
package com.yourapp.data.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.UserProfileRepository
import com.bablabs.bringabrainlanguage.domain.models.UserProfile
import com.yourapp.data.dao.UserProfileDao
import com.yourapp.data.entities.toEntity
import com.yourapp.data.entities.toSDKModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomUserProfileRepository(
    private val dao: UserProfileDao
) : UserProfileRepository {
    
    override suspend fun getProfile(): UserProfile? = withContext(Dispatchers.IO) {
        val entity = dao.getProfile() ?: return@withContext null
        val languages = dao.getTargetLanguages(entity.id)
        entity.toSDKModel(languages)
    }
    
    override suspend fun saveProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        val entity = profile.toEntity()
        val languageEntities = profile.targetLanguages.map { it.toEntity(profile.id) }
        dao.saveProfileWithLanguages(entity, languageEntities)
    }
    
    override suspend fun updateOnboardingComplete() = withContext(Dispatchers.IO) {
        dao.updateOnboardingComplete()
    }
    
    override suspend fun updateLastActive() = withContext(Dispatchers.IO) {
        dao.updateLastActive(System.currentTimeMillis())
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
```

### RoomVocabularyRepository

```kotlin
package com.yourapp.data.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.VocabularyRepository
import com.bablabs.bringabrainlanguage.domain.models.*
import com.yourapp.data.dao.VocabularyDao
import com.yourapp.data.entities.toEntity
import com.yourapp.data.entities.toSDKModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RoomVocabularyRepository(
    private val dao: VocabularyDao
) : VocabularyRepository {
    
    override suspend fun getAll(language: LanguageCode): List<VocabularyEntry> = 
        withContext(Dispatchers.IO) {
            dao.getAll(language).map { it.toSDKModel() }
        }
    
    override suspend fun getById(id: String): VocabularyEntry? = 
        withContext(Dispatchers.IO) {
            dao.getById(id)?.toSDKModel()
        }
    
    override suspend fun getDueForReview(language: LanguageCode, limit: Int): List<VocabularyEntry> = 
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            dao.getDueForReview(language, now, limit).map { it.toSDKModel() }
        }
    
    override suspend fun upsert(entry: VocabularyEntry) = withContext(Dispatchers.IO) {
        dao.upsert(entry.toEntity())
    }
    
    override suspend fun upsertAll(entries: List<VocabularyEntry>) = withContext(Dispatchers.IO) {
        dao.upsertAll(entries.map { it.toEntity() })
    }
    
    override suspend fun getStats(language: LanguageCode): VocabularyStats = 
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            VocabularyStats(
                total = dao.getTotal(language),
                newCount = dao.getNewCount(language),
                learningCount = dao.getLearningCount(language),
                reviewingCount = dao.getReviewingCount(language),
                masteredCount = dao.getMasteredCount(language),
                dueCount = dao.getDueCount(language, now)
            )
        }
    
    override suspend fun searchByWord(query: String, language: LanguageCode): List<VocabularyEntry> = 
        withContext(Dispatchers.IO) {
            dao.searchByWord(query, language).map { it.toSDKModel() }
        }
    
    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
    }
    
    override suspend fun clear() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }
}
```

---

## SDK Integration

### Database Definition

```kotlin
package com.yourapp.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context
import com.yourapp.data.dao.*
import com.yourapp.data.entities.*

@Database(
    entities = [
        UserProfileEntity::class,
        TargetLanguageEntity::class,
        VocabularyEntryEntity::class,
        UserProgressEntity::class,
        SessionStatsEntity::class,
        AchievementEntity::class,
        SavedSessionEntity::class,
        DialogLineEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BabLanguageDatabase : RoomDatabase() {
    
    abstract fun userProfileDao(): UserProfileDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun progressDao(): ProgressDao
    abstract fun dialogHistoryDao(): DialogHistoryDao
    
    companion object {
        @Volatile
        private var INSTANCE: BabLanguageDatabase? = null
        
        fun getInstance(context: Context): BabLanguageDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BabLanguageDatabase::class.java,
                    "bab_language_database"
                )
                    .fallbackToDestructiveMigration()  // Dev only - use migrations in prod
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### Application Setup

```kotlin
package com.yourapp

import android.app.Application
import com.bablabs.bringabrainlanguage.BrainSDK
import com.yourapp.data.BabLanguageDatabase
import com.yourapp.data.repositories.*

class BabLanguageApplication : Application() {
    
    lateinit var sdk: BrainSDK
        private set
    
    override fun onCreate() {
        super.onCreate()
        
        val database = BabLanguageDatabase.getInstance(this)
        
        sdk = BrainSDK(
            aiProvider = null,  // Uses default
            coroutineContext = null,
            userProfileRepository = RoomUserProfileRepository(database.userProfileDao()),
            vocabularyRepository = RoomVocabularyRepository(database.vocabularyDao()),
            progressRepository = RoomProgressRepository(database.progressDao()),
            dialogHistoryRepository = RoomDialogHistoryRepository(database.dialogHistoryDao())
        )
    }
}
```

### Composable Access

```kotlin
@Composable
fun GameScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as BabLanguageApplication
    val sdk = app.sdk
    
    val state by sdk.state.collectAsState()
    val profile by sdk.userProfile.collectAsState()
    val vocabStats by sdk.vocabularyStats.collectAsState()
    
    Column {
        profile?.let { 
            Text("Welcome, ${it.displayName}!")
            Text("Level: ${it.targetLanguages.firstOrNull()?.proficiencyLevel}")
        }
        
        Text("Vocabulary: ${vocabStats.total} words, ${vocabStats.dueCount} due")
        
        // ... rest of UI
    }
}
```

### Hilt/Dagger Dependency Injection (Optional)

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BabLanguageDatabase {
        return BabLanguageDatabase.getInstance(context)
    }
    
    @Provides
    fun provideUserProfileDao(db: BabLanguageDatabase) = db.userProfileDao()
    
    @Provides
    fun provideVocabularyDao(db: BabLanguageDatabase) = db.vocabularyDao()
    
    @Provides
    fun provideProgressDao(db: BabLanguageDatabase) = db.progressDao()
    
    @Provides
    fun provideDialogHistoryDao(db: BabLanguageDatabase) = db.dialogHistoryDao()
}

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Provides
    @Singleton
    fun provideUserProfileRepository(dao: UserProfileDao): UserProfileRepository =
        RoomUserProfileRepository(dao)
    
    @Provides
    @Singleton
    fun provideVocabularyRepository(dao: VocabularyDao): VocabularyRepository =
        RoomVocabularyRepository(dao)
    
    // ... etc
}

@Module
@InstallIn(SingletonComponent::class)
object SDKModule {
    
    @Provides
    @Singleton
    fun provideBrainSDK(
        userProfileRepo: UserProfileRepository,
        vocabularyRepo: VocabularyRepository,
        progressRepo: ProgressRepository,
        dialogHistoryRepo: DialogHistoryRepository
    ): BrainSDK {
        return BrainSDK(
            aiProvider = null,
            coroutineContext = null,
            userProfileRepository = userProfileRepo,
            vocabularyRepository = vocabularyRepo,
            progressRepository = progressRepo,
            dialogHistoryRepository = dialogHistoryRepo
        )
    }
}
```

---

## Migration Strategies

### Room Migrations

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column
        database.execSQL(
            "ALTER TABLE vocabulary_entries ADD COLUMN pronunciation_score REAL"
        )
    }
}

// Use in database builder
Room.databaseBuilder(context, BabLanguageDatabase::class.java, "bab_language_database")
    .addMigrations(MIGRATION_1_2)
    .build()
```

### Auto-Migration (Room 2.4+)

```kotlin
@Database(
    entities = [...],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
abstract class BabLanguageDatabase : RoomDatabase()
```

---

## Best Practices

### 1. Use Flows for Reactive Updates

```kotlin
// In DAO
@Query("SELECT * FROM vocabulary_entries WHERE language = :language AND next_review_at <= :now")
fun observeDueReviews(language: String, now: Long): Flow<List<VocabularyEntryEntity>>

// In ViewModel
val dueReviews = vocabularyDao
    .observeDueReviews(currentLanguage, System.currentTimeMillis())
    .map { entities -> entities.map { it.toSDKModel() } }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

### 2. Batch Operations for Performance

```kotlin
@Transaction
suspend fun importVocabulary(entries: List<VocabularyEntry>) {
    // Clear existing and bulk insert
    dao.clearAll()
    entries.chunked(100).forEach { batch ->
        dao.upsertAll(batch.map { it.toEntity() })
    }
}
```

### 3. Prepopulate with Default Data

```kotlin
Room.databaseBuilder(context, BabLanguageDatabase::class.java, "bab_language_database")
    .createFromAsset("databases/prepopulated.db")  // From assets
    .build()
```

### 4. Database Inspector

Enable in Android Studio: **View → Tool Windows → App Inspection → Database Inspector**

---

## Summary

| Repository | Room Entities | Key Queries |
|------------|---------------|-------------|
| `UserProfileRepository` | UserProfileEntity, TargetLanguageEntity | Single profile, cascade languages |
| `VocabularyRepository` | VocabularyEntryEntity | Due reviews (indexed), search by word |
| `ProgressRepository` | UserProgressEntity, SessionStatsEntity, AchievementEntity | By language, session history |
| `DialogHistoryRepository` | SavedSessionEntity, DialogLineEntity | By date, with dialog lines |

---

*Document Version: 1.0 - February 2026*
