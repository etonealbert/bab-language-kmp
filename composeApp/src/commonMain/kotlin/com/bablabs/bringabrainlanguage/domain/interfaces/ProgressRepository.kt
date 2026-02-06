package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.Achievement
import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.SessionStats
import com.bablabs.bringabrainlanguage.domain.models.UserProgress
import com.bablabs.bringabrainlanguage.domain.models.XPBreakdown

interface ProgressRepository {
    suspend fun getProgress(language: LanguageCode): UserProgress?
    suspend fun saveProgress(progress: UserProgress)
    suspend fun addXP(amount: Int, breakdown: XPBreakdown)
    suspend fun recordSession(stats: SessionStats)
    suspend fun updateStreak()
    suspend fun getSessionHistory(limit: Int = 20): List<SessionStats>
    suspend fun getAchievements(): List<Achievement>
    suspend fun unlockAchievement(id: String)
    suspend fun clear()
}
