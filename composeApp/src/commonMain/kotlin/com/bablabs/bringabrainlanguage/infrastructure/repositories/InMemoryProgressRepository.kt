package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.ProgressRepository
import com.bablabs.bringabrainlanguage.domain.models.Achievement
import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.SessionStats
import com.bablabs.bringabrainlanguage.domain.models.UserProgress
import com.bablabs.bringabrainlanguage.domain.models.XPBreakdown
import com.bablabs.bringabrainlanguage.domain.services.XPCalculator
import kotlinx.datetime.Clock

class InMemoryProgressRepository : ProgressRepository {
    
    private var progress: UserProgress? = null
    private val sessionHistory = mutableListOf<SessionStats>()
    private val achievements = mutableListOf<Achievement>()
    
    override suspend fun getProgress(language: LanguageCode): UserProgress? =
        progress?.takeIf { it.language == language }
    
    override suspend fun saveProgress(progress: UserProgress) {
        this.progress = progress
    }
    
    override suspend fun addXP(amount: Int, breakdown: XPBreakdown) {
        progress = progress?.let { p ->
            val newTotalXP = p.totalXP + amount
            val newLevel = XPCalculator.levelFromXP(newTotalXP)
            p.copy(
                totalXP = newTotalXP,
                weeklyXP = p.weeklyXP + amount,
                currentLevel = newLevel
            )
        }
    }
    
    override suspend fun recordSession(stats: SessionStats) {
        sessionHistory.add(0, stats)
        progress = progress?.copy(
            totalSessions = (progress?.totalSessions ?: 0) + 1,
            totalMinutesPlayed = (progress?.totalMinutesPlayed ?: 0) + 
                ((stats.endedAt ?: stats.startedAt) - stats.startedAt).toInt() / 60000
        )
    }
    
    override suspend fun updateStreak() {
        progress = progress?.let { p ->
            val newStreak = p.currentStreak + 1
            p.copy(
                currentStreak = newStreak,
                longestStreak = maxOf(p.longestStreak, newStreak)
            )
        }
    }
    
    override suspend fun getSessionHistory(limit: Int): List<SessionStats> =
        sessionHistory.take(limit)
    
    override suspend fun getAchievements(): List<Achievement> = achievements.toList()
    
    override suspend fun unlockAchievement(id: String) {
        val index = achievements.indexOfFirst { it.id == id }
        if (index >= 0) {
            achievements[index] = achievements[index].copy(
                unlockedAt = Clock.System.now().toEpochMilliseconds(),
                progress = 1.0f
            )
        }
    }
    
    override suspend fun clear() {
        progress = null
        sessionHistory.clear()
        achievements.clear()
    }
}
