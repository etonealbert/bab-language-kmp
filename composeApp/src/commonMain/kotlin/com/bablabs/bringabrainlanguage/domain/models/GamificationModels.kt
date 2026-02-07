package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class PlayerStats(
    val playerId: String,
    val linesCompleted: Int = 0,
    val totalErrors: Int = 0,
    val perfectLines: Int = 0,
    val averageAccuracy: Float = 0f,
    val currentStreak: Int = 0,
    val xpEarned: Int = 0
)

@Serializable
data class SessionLeaderboard(
    val rankings: List<PlayerRanking>,
    val updatedAt: Long
)

@Serializable
data class PlayerRanking(
    val rank: Int,
    val playerId: String,
    val displayName: String,
    val score: Int,
    val highlight: String? = null
)

@Serializable
data class SessionSummary(
    val scenarioName: String,
    val durationMs: Long,
    val totalLines: Int,
    val playerRankings: List<PlayerRanking>,
    val xpEarned: Map<String, Int>,
    val achievementsUnlocked: List<Achievement>,
    val vocabularyLearned: List<VocabularyEntry>,
    val overallAccuracy: Float
)

@Serializable
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String? = null,
    val unlockedAt: Long? = null
)

@Serializable
data class XPBreakdown(
    val baseXP: Int,
    val accuracyBonus: Int,
    val streakBonus: Int,
    val multiplayerBonus: Int,
    val firstTimeScenarioBonus: Int,
    val total: Int
)
