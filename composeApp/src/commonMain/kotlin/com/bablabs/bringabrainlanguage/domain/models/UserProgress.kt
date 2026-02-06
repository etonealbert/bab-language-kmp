package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProgress(
    val userId: String,
    val language: LanguageCode,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivityDate: String,
    val todayMinutes: Int,
    val dailyGoalMinutes: Int,
    val totalXP: Long,
    val weeklyXP: Long,
    val currentLevel: Int,
    val totalWordsLearned: Int,
    val wordsDueForReview: Int,
    val vocabularyMasteryPercent: Float,
    val totalSessions: Int,
    val totalMinutesPlayed: Int,
    val soloSessions: Int,
    val multiplayerSessions: Int,
    val successfulRepairs: Int,
    val helpGiven: Int,
    val teamWins: Int,
    val estimatedCEFR: CEFRLevel,
    val cefrProgressPercent: Float
)

@Serializable
data class SessionStats(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val mode: SessionMode,
    val scenarioId: String?,
    val linesSpoken: Int,
    val wordsEncountered: Int,
    val newVocabulary: Int,
    val errorsDetected: Int,
    val errorsCorrected: Int,
    val xpEarned: Int,
    val xpBreakdown: XPBreakdown
)

@Serializable
data class XPBreakdown(
    val dialogCompletion: Int,
    val vocabularyBonus: Int,
    val accuracyBonus: Int,
    val collaborationBonus: Int,
    val streakBonus: Int
) {
    val total: Int
        get() = dialogCompletion + vocabularyBonus + accuracyBonus + collaborationBonus + streakBonus
}

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val category: AchievementCategory,
    val unlockedAt: Long?,
    val progress: Float
)

@Serializable
enum class AchievementCategory {
    VOCABULARY, STREAK, SOCIAL, MASTERY, EXPLORATION
}
