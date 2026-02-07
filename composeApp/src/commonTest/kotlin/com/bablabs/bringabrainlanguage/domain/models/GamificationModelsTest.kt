package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GamificationModelsTest {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    @Test
    fun playerStatsDefaultsAreZero() {
        val stats = PlayerStats(playerId = "player-1")
        
        assertEquals(0, stats.linesCompleted)
        assertEquals(0, stats.totalErrors)
        assertEquals(0, stats.perfectLines)
        assertEquals(0f, stats.averageAccuracy)
        assertEquals(0, stats.currentStreak)
        assertEquals(0, stats.xpEarned)
    }
    
    @Test
    fun playerStatsSerializesCorrectly() {
        val stats = PlayerStats(
            playerId = "player-2",
            linesCompleted = 15,
            totalErrors = 3,
            perfectLines = 10,
            averageAccuracy = 0.85f,
            currentStreak = 5,
            xpEarned = 250
        )
        
        val serialized = json.encodeToString(stats)
        val deserialized = json.decodeFromString<PlayerStats>(serialized)
        
        assertEquals(stats, deserialized)
    }
    
    @Test
    fun playerRankingSerializesWithNullHighlight() {
        val ranking = PlayerRanking(
            rank = 1,
            playerId = "winner",
            displayName = "Top Player",
            score = 500,
            highlight = null
        )
        
        val serialized = json.encodeToString(ranking)
        val deserialized = json.decodeFromString<PlayerRanking>(serialized)
        
        assertEquals(ranking, deserialized)
        assertNull(deserialized.highlight)
    }
    
    @Test
    fun playerRankingSerializesWithHighlight() {
        val ranking = PlayerRanking(
            rank = 2,
            playerId = "player-3",
            displayName = "Runner Up",
            score = 450,
            highlight = "Most Improved"
        )
        
        val serialized = json.encodeToString(ranking)
        val deserialized = json.decodeFromString<PlayerRanking>(serialized)
        
        assertEquals("Most Improved", deserialized.highlight)
    }
    
    @Test
    fun sessionLeaderboardSerializesCorrectly() {
        val rankings = listOf(
            PlayerRanking(1, "p1", "Alice", 500, "Perfect Score"),
            PlayerRanking(2, "p2", "Bob", 450, null),
            PlayerRanking(3, "p3", "Charlie", 400, null)
        )
        
        val leaderboard = SessionLeaderboard(
            rankings = rankings,
            updatedAt = 1234567890L
        )
        
        val serialized = json.encodeToString(leaderboard)
        val deserialized = json.decodeFromString<SessionLeaderboard>(serialized)
        
        assertEquals(leaderboard, deserialized)
        assertEquals(3, deserialized.rankings.size)
    }
    
    @Test
    fun unlockedAchievementSerializesCorrectly() {
        val achievement = UnlockedAchievement(
            id = "first_multiplayer",
            name = "First Words Together",
            description = "Complete first multiplayer session",
            iconName = "users_icon",
            unlockedAt = 1234567890L
        )
        
        val serialized = json.encodeToString(achievement)
        val deserialized = json.decodeFromString<UnlockedAchievement>(serialized)
        
        assertEquals(achievement, deserialized)
    }
    
    @Test
    fun lineXpBreakdownTotalIsCorrect() {
        val breakdown = LineXPBreakdown(
            baseXP = 10,
            accuracyBonus = 15,
            streakBonus = 5,
            multiplayerBonus = 5,
            firstTimeScenarioBonus = 25
        )
        
        val expectedTotal = breakdown.baseXP + 
            breakdown.accuracyBonus + 
            breakdown.streakBonus + 
            breakdown.multiplayerBonus + 
            breakdown.firstTimeScenarioBonus
        
        assertEquals(expectedTotal, breakdown.total)
    }
}
