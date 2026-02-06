package com.bablabs.bringabrainlanguage.domain.services

import com.bablabs.bringabrainlanguage.domain.models.XPBreakdown
import kotlin.math.min

/**
 * XP calculation service for gamification.
 * 
 * Formula:
 * - Dialog completion: 50 XP (base)
 * - Vocabulary: 10 XP per new word
 * - Accuracy: up to 30 XP based on (1 - errorRate)
 * - Collaboration: 25 XP base + 10 XP per help (multiplayer only)
 * - Streak: 5% per day bonus, max 50%
 */
object XPCalculator {
    
    private const val BASE_DIALOG_COMPLETION = 50
    private const val XP_PER_NEW_WORD = 10
    private const val MAX_ACCURACY_BONUS = 30
    private const val COLLABORATION_BONUS_BASE = 25
    private const val XP_PER_HELP = 10
    private const val STREAK_BONUS_PER_DAY = 0.05f
    private const val MAX_STREAK_BONUS = 0.5f
    
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
        
        val accuracyRate = if (linesSpoken > 0) {
            1f - (errorsDetected.toFloat() / linesSpoken)
        } else 0f
        val accuracyXP = (accuracyRate * MAX_ACCURACY_BONUS).toInt()
        
        val collabXP = if (isMultiplayer) {
            COLLABORATION_BONUS_BASE + (helpGiven * XP_PER_HELP)
        } else 0
        
        val baseXP = dialogXP + vocabXP + accuracyXP + collabXP
        val streakMultiplier = min(currentStreak * STREAK_BONUS_PER_DAY, MAX_STREAK_BONUS)
        val streakXP = (baseXP * streakMultiplier).toInt()
        
        return XPBreakdown(
            dialogCompletion = dialogXP,
            vocabularyBonus = vocabXP,
            accuracyBonus = accuracyXP,
            collaborationBonus = collabXP,
            streakBonus = streakXP
        )
    }
    
    /**
     * Calculate level from total XP using exponential scaling.
     * Level N requires sum(i * 100) for i in 1..(N-1) XP total.
     * Level 1: 0 XP, Level 2: 100 XP, Level 3: 300 XP, Level 4: 600 XP, etc.
     */
    fun levelFromXP(totalXP: Long): Int {
        var level = 1
        var requiredXP = 0L
        var increment = 100L
        while (totalXP >= requiredXP + increment) {
            requiredXP += increment
            level++
            increment += 100L
        }
        return level
    }
}
