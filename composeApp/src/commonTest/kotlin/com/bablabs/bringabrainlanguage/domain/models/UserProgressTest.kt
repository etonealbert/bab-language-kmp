package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserProgressTest {
    
    @Test
    fun `UserProgress tracks streaks and daily goals`() {
        val progress = UserProgress(
            userId = "user-123",
            language = "es",
            currentStreak = 7,
            longestStreak = 30,
            lastActivityDate = "2026-02-05",
            todayMinutes = 15,
            dailyGoalMinutes = 15,
            totalXP = 5000L,
            weeklyXP = 350L,
            currentLevel = 10,
            totalWordsLearned = 250,
            wordsDueForReview = 25,
            vocabularyMasteryPercent = 0.65f,
            totalSessions = 50,
            totalMinutesPlayed = 500,
            soloSessions = 40,
            multiplayerSessions = 10,
            successfulRepairs = 15,
            helpGiven = 8,
            teamWins = 5,
            estimatedCEFR = CEFRLevel.B1,
            cefrProgressPercent = 45.0f
        )
        
        assertEquals("user-123", progress.userId)
        assertEquals("es", progress.language)
        assertEquals(7, progress.currentStreak)
        assertEquals(30, progress.longestStreak)
        assertEquals(5000L, progress.totalXP)
        assertEquals(10, progress.currentLevel)
        assertEquals(CEFRLevel.B1, progress.estimatedCEFR)
    }
    
    @Test
    fun `SessionStats tracks per-session performance`() {
        val stats = SessionStats(
            sessionId = "session-456",
            startedAt = 1000L,
            endedAt = 2000L,
            mode = SessionMode.SOLO,
            scenarioId = "coffee-shop",
            linesSpoken = 20,
            wordsEncountered = 50,
            newVocabulary = 10,
            errorsDetected = 3,
            errorsCorrected = 2,
            xpEarned = 150,
            xpBreakdown = XPBreakdown(
                dialogCompletion = 50,
                vocabularyBonus = 40,
                accuracyBonus = 30,
                collaborationBonus = 0,
                streakBonus = 30
            )
        )
        
        assertEquals("session-456", stats.sessionId)
        assertEquals(1000L, stats.startedAt)
        assertEquals(2000L, stats.endedAt)
        assertEquals(SessionMode.SOLO, stats.mode)
        assertEquals(20, stats.linesSpoken)
        assertEquals(150, stats.xpEarned)
        assertEquals(50, stats.xpBreakdown.dialogCompletion)
    }
    
    @Test
    fun `XPBreakdown sums to total XP`() {
        val breakdown = XPBreakdown(
            dialogCompletion = 50,
            vocabularyBonus = 40,
            accuracyBonus = 30,
            collaborationBonus = 25,
            streakBonus = 15
        )
        
        val expectedTotal = 50 + 40 + 30 + 25 + 15
        val actualTotal = breakdown.dialogCompletion + 
                          breakdown.vocabularyBonus + 
                          breakdown.accuracyBonus + 
                          breakdown.collaborationBonus + 
                          breakdown.streakBonus
        assertEquals(expectedTotal, actualTotal)
    }
    
    @Test
    fun `Achievement tracks unlock state and progress`() {
        val locked = Achievement(
            id = "streak-7",
            name = "Week Warrior",
            description = "Maintain a 7-day streak",
            iconUrl = null,
            category = AchievementCategory.STREAK,
            unlockedAt = null,
            progress = 0.5f
        )
        
        val unlocked = locked.copy(
            unlockedAt = 1000L,
            progress = 1.0f
        )
        
        assertNull(locked.unlockedAt)
        assertEquals(0.5f, locked.progress)
        assertEquals(1000L, unlocked.unlockedAt)
        assertEquals(1.0f, unlocked.progress)
    }
    
    @Test
    fun `AchievementCategory enum contains expected values`() {
        val categories = AchievementCategory.entries
        assertTrue(categories.contains(AchievementCategory.VOCABULARY))
        assertTrue(categories.contains(AchievementCategory.STREAK))
        assertTrue(categories.contains(AchievementCategory.SOCIAL))
        assertTrue(categories.contains(AchievementCategory.MASTERY))
        assertTrue(categories.contains(AchievementCategory.EXPLORATION))
    }
}
