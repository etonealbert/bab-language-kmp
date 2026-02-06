package com.bablabs.bringabrainlanguage.domain.services

import com.bablabs.bringabrainlanguage.domain.models.ReviewQuality
import com.bablabs.bringabrainlanguage.domain.models.VocabularyEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SRSSchedulerTest {
    
    private fun createTestEntry(
        masteryLevel: Int = 0,
        easeFactor: Float = 2.5f,
        intervalDays: Int = 1,
        totalReviews: Int = 0,
        correctReviews: Int = 0
    ) = VocabularyEntry(
        id = "test-vocab",
        word = "cafe",
        translation = "coffee",
        language = "es",
        partOfSpeech = null,
        exampleSentence = null,
        audioUrl = null,
        masteryLevel = masteryLevel,
        easeFactor = easeFactor,
        intervalDays = intervalDays,
        nextReviewAt = 0L,
        totalReviews = totalReviews,
        correctReviews = correctReviews,
        firstSeenInDialogId = null,
        firstSeenAt = 0L,
        lastReviewedAt = null
    )
    
    @Test
    fun `AGAIN resets interval to 1 day and decreases ease factor`() {
        val entry = createTestEntry(masteryLevel = 3, easeFactor = 2.5f, intervalDays = 10)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.AGAIN)
        
        assertEquals(1, updated.intervalDays)
        assertEquals(2.3f, updated.easeFactor, 0.01f)
        assertEquals(2, updated.masteryLevel)
        assertEquals(1, updated.totalReviews)
        assertEquals(0, updated.correctReviews)
    }
    
    @Test
    fun `HARD increases interval slightly and decreases ease factor`() {
        val entry = createTestEntry(masteryLevel = 2, easeFactor = 2.5f, intervalDays = 5)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.HARD)
        
        assertEquals(6, updated.intervalDays)
        assertEquals(2.35f, updated.easeFactor, 0.01f)
        assertEquals(2, updated.masteryLevel)
        assertEquals(1, updated.totalReviews)
        assertEquals(1, updated.correctReviews)
    }
    
    @Test
    fun `GOOD increases interval by ease factor and increases mastery`() {
        val entry = createTestEntry(masteryLevel = 1, easeFactor = 2.5f, intervalDays = 3)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.GOOD)
        
        assertEquals(7, updated.intervalDays)
        assertEquals(2.5f, updated.easeFactor, 0.01f)
        assertEquals(2, updated.masteryLevel)
        assertEquals(1, updated.totalReviews)
        assertEquals(1, updated.correctReviews)
    }
    
    @Test
    fun `EASY increases interval significantly and increases ease factor`() {
        val entry = createTestEntry(masteryLevel = 2, easeFactor = 2.5f, intervalDays = 5)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.EASY)
        
        assertEquals(16, updated.intervalDays)
        assertEquals(2.65f, updated.easeFactor, 0.01f)
        assertEquals(3, updated.masteryLevel)
        assertEquals(1, updated.totalReviews)
        assertEquals(1, updated.correctReviews)
    }
    
    @Test
    fun `ease factor never goes below minimum of 1_3`() {
        val entry = createTestEntry(easeFactor = 1.4f)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.AGAIN)
        
        assertEquals(1.3f, updated.easeFactor, 0.01f)
    }
    
    @Test
    fun `mastery level never goes below 0`() {
        val entry = createTestEntry(masteryLevel = 0)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.AGAIN)
        
        assertEquals(0, updated.masteryLevel)
    }
    
    @Test
    fun `mastery level never exceeds 5`() {
        val entry = createTestEntry(masteryLevel = 5)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.EASY)
        
        assertEquals(5, updated.masteryLevel)
    }
    
    @Test
    fun `interval is always at least 1 day`() {
        val entry = createTestEntry(intervalDays = 0, easeFactor = 1.3f)
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.HARD)
        
        assertTrue(updated.intervalDays >= 1)
    }
    
    @Test
    fun `nextReviewAt is set correctly based on interval`() {
        val entry = createTestEntry(intervalDays = 1)
        val beforeUpdate = System.currentTimeMillis()
        val updated = SRSScheduler.calculateNextReview(entry, ReviewQuality.GOOD)
        val afterUpdate = System.currentTimeMillis()
        
        val expectedMinMs = beforeUpdate + (updated.intervalDays * 24 * 60 * 60 * 1000L)
        val expectedMaxMs = afterUpdate + (updated.intervalDays * 24 * 60 * 60 * 1000L)
        
        assertTrue(updated.nextReviewAt >= expectedMinMs - 1000)
        assertTrue(updated.nextReviewAt <= expectedMaxMs + 1000)
    }
    
    @Test
    fun `getDueReviews returns entries with nextReviewAt in past`() {
        val now = System.currentTimeMillis()
        val dueEntry = createTestEntry().copy(id = "due", nextReviewAt = now - 1000)
        val futureEntry = createTestEntry().copy(id = "future", nextReviewAt = now + 100000)
        val entries = listOf(dueEntry, futureEntry)
        
        val dueReviews = SRSScheduler.getDueReviews(entries)
        
        assertEquals(1, dueReviews.size)
        assertEquals("due", dueReviews[0].id)
    }
    
    @Test
    fun `getDueReviews respects limit parameter`() {
        val now = System.currentTimeMillis()
        val entries = (1..10).map { 
            createTestEntry().copy(id = "entry-$it", nextReviewAt = now - it * 1000) 
        }
        
        val dueReviews = SRSScheduler.getDueReviews(entries, limit = 5)
        
        assertEquals(5, dueReviews.size)
    }
    
    @Test
    fun `getDueReviews sorts by nextReviewAt ascending`() {
        val now = System.currentTimeMillis()
        val entry1 = createTestEntry().copy(id = "oldest", nextReviewAt = now - 5000)
        val entry2 = createTestEntry().copy(id = "newer", nextReviewAt = now - 1000)
        val entry3 = createTestEntry().copy(id = "middle", nextReviewAt = now - 3000)
        
        val dueReviews = SRSScheduler.getDueReviews(listOf(entry1, entry2, entry3))
        
        assertEquals("oldest", dueReviews[0].id)
        assertEquals("middle", dueReviews[1].id)
        assertEquals("newer", dueReviews[2].id)
    }
    
    @Test
    fun `createNewEntry creates entry with correct initial SRS values`() {
        val entry = SRSScheduler.createNewEntry("hola", "hello", "es", "dialog-123")
        
        assertEquals("hola", entry.word)
        assertEquals("hello", entry.translation)
        assertEquals("es", entry.language)
        assertEquals("dialog-123", entry.firstSeenInDialogId)
        assertEquals(0, entry.masteryLevel)
        assertEquals(2.5f, entry.easeFactor, 0.01f)
        assertEquals(1, entry.intervalDays)
        assertEquals(0, entry.totalReviews)
        assertEquals(0, entry.correctReviews)
        assertTrue(entry.id.startsWith("vocab_"))
    }
}
