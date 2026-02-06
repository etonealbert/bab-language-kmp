package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VocabularyEntryTest {
    
    @Test
    fun `VocabularyEntry can be created with all SRS fields`() {
        val entry = VocabularyEntry(
            id = "vocab-123",
            word = "cafe",
            translation = "coffee",
            language = "es",
            partOfSpeech = PartOfSpeech.NOUN,
            exampleSentence = "Quiero un cafe, por favor",
            audioUrl = "https://example.com/cafe.mp3",
            masteryLevel = 0,
            easeFactor = 2.5f,
            intervalDays = 1,
            nextReviewAt = 1000L,
            totalReviews = 0,
            correctReviews = 0,
            firstSeenInDialogId = "dialog-456",
            firstSeenAt = 500L,
            lastReviewedAt = null
        )
        
        assertEquals("vocab-123", entry.id)
        assertEquals("cafe", entry.word)
        assertEquals("coffee", entry.translation)
        assertEquals("es", entry.language)
        assertEquals(PartOfSpeech.NOUN, entry.partOfSpeech)
        assertEquals(0, entry.masteryLevel)
        assertEquals(2.5f, entry.easeFactor)
        assertEquals(1, entry.intervalDays)
        assertNull(entry.lastReviewedAt)
    }
    
    @Test
    fun `VocabularyEntry can be created with minimal fields`() {
        val entry = VocabularyEntry(
            id = "vocab-789",
            word = "hola",
            translation = "hello",
            language = "es",
            partOfSpeech = null,
            exampleSentence = null,
            audioUrl = null,
            masteryLevel = 0,
            easeFactor = 2.5f,
            intervalDays = 1,
            nextReviewAt = 0L,
            totalReviews = 0,
            correctReviews = 0,
            firstSeenInDialogId = null,
            firstSeenAt = 0L,
            lastReviewedAt = null
        )
        
        assertNull(entry.partOfSpeech)
        assertNull(entry.exampleSentence)
        assertNull(entry.audioUrl)
        assertNull(entry.firstSeenInDialogId)
    }
    
    @Test
    fun `PartOfSpeech enum contains expected values`() {
        val parts = PartOfSpeech.entries
        assertTrue(parts.contains(PartOfSpeech.NOUN))
        assertTrue(parts.contains(PartOfSpeech.VERB))
        assertTrue(parts.contains(PartOfSpeech.ADJECTIVE))
        assertTrue(parts.contains(PartOfSpeech.ADVERB))
        assertTrue(parts.contains(PartOfSpeech.PREPOSITION))
        assertTrue(parts.contains(PartOfSpeech.CONJUNCTION))
        assertTrue(parts.contains(PartOfSpeech.PRONOUN))
        assertTrue(parts.contains(PartOfSpeech.INTERJECTION))
        assertTrue(parts.contains(PartOfSpeech.ARTICLE))
    }
    
    @Test
    fun `ReviewQuality enum has four levels`() {
        assertEquals(4, ReviewQuality.entries.size)
        assertTrue(ReviewQuality.entries.contains(ReviewQuality.AGAIN))
        assertTrue(ReviewQuality.entries.contains(ReviewQuality.HARD))
        assertTrue(ReviewQuality.entries.contains(ReviewQuality.GOOD))
        assertTrue(ReviewQuality.entries.contains(ReviewQuality.EASY))
    }
    
    @Test
    fun `VocabularyStats tracks mastery distribution`() {
        val stats = VocabularyStats(
            total = 100,
            newCount = 20,
            learningCount = 30,
            reviewingCount = 25,
            masteredCount = 25,
            dueCount = 15
        )
        
        assertEquals(100, stats.total)
        assertEquals(20, stats.newCount)
        assertEquals(30, stats.learningCount)
        assertEquals(25, stats.reviewingCount)
        assertEquals(25, stats.masteredCount)
        assertEquals(15, stats.dueCount)
    }
    
    @Test
    fun `VocabularyEntry copy updates SRS fields correctly`() {
        val original = VocabularyEntry(
            id = "vocab-001",
            word = "gracias",
            translation = "thank you",
            language = "es",
            partOfSpeech = PartOfSpeech.INTERJECTION,
            exampleSentence = null,
            audioUrl = null,
            masteryLevel = 1,
            easeFactor = 2.5f,
            intervalDays = 1,
            nextReviewAt = 1000L,
            totalReviews = 1,
            correctReviews = 1,
            firstSeenInDialogId = null,
            firstSeenAt = 0L,
            lastReviewedAt = 500L
        )
        
        val updated = original.copy(
            masteryLevel = 2,
            easeFactor = 2.6f,
            intervalDays = 3,
            nextReviewAt = 5000L,
            totalReviews = 2,
            correctReviews = 2,
            lastReviewedAt = 2000L
        )
        
        assertEquals(original.id, updated.id)
        assertEquals(original.word, updated.word)
        assertEquals(2, updated.masteryLevel)
        assertEquals(2.6f, updated.easeFactor)
        assertEquals(3, updated.intervalDays)
        assertEquals(5000L, updated.nextReviewAt)
        assertEquals(2, updated.totalReviews)
        assertEquals(2000L, updated.lastReviewedAt)
    }
}
