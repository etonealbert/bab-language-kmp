package com.bablabs.bringabrainlanguage.domain.services

import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.ReviewQuality
import com.bablabs.bringabrainlanguage.domain.models.VocabularyEntry
import kotlin.math.max
import kotlin.math.min
import kotlinx.datetime.Clock

/**
 * Spaced Repetition Scheduler using the SM-2 algorithm.
 * 
 * Determines optimal review intervals based on user performance.
 * Key parameters:
 * - Ease Factor: Multiplier for interval (min 1.3, default 2.5)
 * - Interval: Days until next review
 * - Mastery Level: 0-5 progression indicator
 */
object SRSScheduler {
    
    private const val MIN_EASE_FACTOR = 1.3f
    private const val DEFAULT_EASE_FACTOR = 2.5f
    private const val MIN_INTERVAL_DAYS = 1
    private const val MAX_MASTERY_LEVEL = 5
    
    /**
     * Calculate the next review parameters based on user's response quality.
     * 
     * SM-2 Algorithm:
     * - AGAIN: Reset interval=1, ease-=0.2 (min 1.3), mastery-=1 (min 0)
     * - HARD: interval*1.2, ease-=0.15
     * - GOOD: interval*ease, mastery+=1 (max 5)
     * - EASY: interval*ease*1.3, ease+=0.15, mastery+=1 (max 5)
     */
    fun calculateNextReview(entry: VocabularyEntry, quality: ReviewQuality): VocabularyEntry {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        
        val (newInterval, newEaseFactor, newMastery, isCorrect) = when (quality) {
            ReviewQuality.AGAIN -> {
                val ease = max(MIN_EASE_FACTOR, entry.easeFactor - 0.2f)
                val mastery = max(0, entry.masteryLevel - 1)
                Quadruple(1, ease, mastery, false)
            }
            ReviewQuality.HARD -> {
                val interval = max(MIN_INTERVAL_DAYS, (entry.intervalDays * 1.2f).toInt())
                val ease = max(MIN_EASE_FACTOR, entry.easeFactor - 0.15f)
                Quadruple(interval, ease, entry.masteryLevel, true)
            }
            ReviewQuality.GOOD -> {
                val interval = max(MIN_INTERVAL_DAYS, (entry.intervalDays * entry.easeFactor).toInt())
                val mastery = min(MAX_MASTERY_LEVEL, entry.masteryLevel + 1)
                Quadruple(interval, entry.easeFactor, mastery, true)
            }
            ReviewQuality.EASY -> {
                val interval = max(MIN_INTERVAL_DAYS, (entry.intervalDays * entry.easeFactor * 1.3f).toInt())
                val ease = entry.easeFactor + 0.15f
                val mastery = min(MAX_MASTERY_LEVEL, entry.masteryLevel + 1)
                Quadruple(interval, ease, mastery, true)
            }
        }
        
        val nextReviewAt = currentTime + (newInterval * 24L * 60L * 60L * 1000L)
        
        return entry.copy(
            intervalDays = newInterval,
            easeFactor = newEaseFactor,
            masteryLevel = newMastery,
            nextReviewAt = nextReviewAt,
            totalReviews = entry.totalReviews + 1,
            correctReviews = if (isCorrect) entry.correctReviews + 1 else entry.correctReviews,
            lastReviewedAt = currentTime
        )
    }
    
    /**
     * Get vocabulary entries that are due for review.
     * 
     * @param entries All vocabulary entries
     * @param limit Maximum number of entries to return
     * @return Entries with nextReviewAt in the past, sorted by oldest first
     */
    fun getDueReviews(entries: List<VocabularyEntry>, limit: Int = 20): List<VocabularyEntry> {
        val now = Clock.System.now().toEpochMilliseconds()
        return entries
            .filter { it.nextReviewAt <= now }
            .sortedBy { it.nextReviewAt }
            .take(limit)
    }
    
    /**
     * Create a new vocabulary entry with default SRS values.
     * 
     * @param word The word in target language
     * @param translation The translation in user's native language
     * @param language The target language code
     * @param dialogId Optional dialog where word was first encountered
     */
    fun createNewEntry(
        word: String,
        translation: String,
        language: LanguageCode,
        dialogId: String? = null
    ): VocabularyEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        return VocabularyEntry(
            id = "vocab_${now}_${word.hashCode().toUInt()}",
            word = word,
            translation = translation,
            language = language,
            partOfSpeech = null,
            exampleSentence = null,
            audioUrl = null,
            masteryLevel = 0,
            easeFactor = DEFAULT_EASE_FACTOR,
            intervalDays = 1,
            nextReviewAt = now,
            totalReviews = 0,
            correctReviews = 0,
            firstSeenInDialogId = dialogId,
            firstSeenAt = now,
            lastReviewedAt = null,
            notes = null
        )
    }
    
    private data class Quadruple(
        val interval: Int,
        val easeFactor: Float,
        val mastery: Int,
        val isCorrect: Boolean
    )
}
