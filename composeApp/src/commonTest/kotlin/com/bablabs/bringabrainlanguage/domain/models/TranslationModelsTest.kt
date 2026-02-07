package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationModelsTest {
    
    @Test
    fun `WordTranslation can be created with all fields`() {
        val translation = WordTranslation(
            word = "café",
            translation = "coffee",
            partOfSpeech = PartOfSpeech.NOUN,
            phoneticSpelling = "ka-FEH",
            audioUrl = "https://example.com/cafe.mp3",
            definitions = listOf("A hot drink", "A small restaurant"),
            exampleSentences = listOf(
                ExampleSentence("Quiero un café", "I want a coffee")
            ),
            relatedWords = listOf("cafetería", "cafeína"),
            sourceLanguage = "es",
            targetLanguage = "en",
            contextUsed = "Ordering at a cafe"
        )
        
        assertEquals("café", translation.word)
        assertEquals("coffee", translation.translation)
        assertEquals(PartOfSpeech.NOUN, translation.partOfSpeech)
        assertEquals("ka-FEH", translation.phoneticSpelling)
        assertEquals(2, translation.definitions.size)
        assertEquals(1, translation.exampleSentences.size)
        assertEquals(2, translation.relatedWords.size)
        assertEquals("es", translation.sourceLanguage)
        assertEquals("en", translation.targetLanguage)
    }
    
    @Test
    fun `WordTranslation can be created with minimal fields`() {
        val translation = WordTranslation(
            word = "hola",
            translation = "hello",
            sourceLanguage = "es",
            targetLanguage = "en"
        )
        
        assertEquals("hola", translation.word)
        assertEquals("hello", translation.translation)
        assertNull(translation.partOfSpeech)
        assertNull(translation.phoneticSpelling)
        assertNull(translation.audioUrl)
        assertTrue(translation.definitions.isEmpty())
        assertTrue(translation.exampleSentences.isEmpty())
        assertTrue(translation.relatedWords.isEmpty())
        assertNull(translation.contextUsed)
    }
    
    @Test
    fun `ExampleSentence contains native and translated text`() {
        val example = ExampleSentence(
            native = "Buenos días",
            translated = "Good morning"
        )
        
        assertEquals("Buenos días", example.native)
        assertEquals("Good morning", example.translated)
    }
    
    @Test
    fun `MasteryLevel NEW for fresh entries`() {
        val entry = createTestEntry(masteryLevel = 0, totalReviews = 0, intervalDays = 1)
        
        assertEquals(MasteryLevel.NEW, entry.mastery)
        assertEquals(MasteryLevel.NEW, MasteryLevel.fromEntry(entry))
    }
    
    @Test
    fun `MasteryLevel LEARNING for early stage entries`() {
        val entry1 = createTestEntry(masteryLevel = 1, totalReviews = 2, intervalDays = 3)
        val entry2 = createTestEntry(masteryLevel = 2, totalReviews = 5, intervalDays = 5)
        val entry3 = createTestEntry(masteryLevel = 3, totalReviews = 3, intervalDays = 5)
        
        assertEquals(MasteryLevel.LEARNING, entry1.mastery)
        assertEquals(MasteryLevel.LEARNING, entry2.mastery)
        assertEquals(MasteryLevel.LEARNING, entry3.mastery)
    }
    
    @Test
    fun `MasteryLevel REVIEWING for intermediate entries`() {
        val entry = createTestEntry(masteryLevel = 3, totalReviews = 10, intervalDays = 14)
        
        assertEquals(MasteryLevel.REVIEWING, entry.mastery)
    }
    
    @Test
    fun `MasteryLevel MASTERED for well-learned entries`() {
        val entry = createTestEntry(masteryLevel = 5, totalReviews = 20, intervalDays = 60)
        
        assertEquals(MasteryLevel.MASTERED, entry.mastery)
    }
    
    @Test
    fun `MasteryLevel fromLevel works with raw values`() {
        assertEquals(MasteryLevel.NEW, MasteryLevel.fromLevel(0, 1, 0))
        assertEquals(MasteryLevel.LEARNING, MasteryLevel.fromLevel(2, 5, 3))
        assertEquals(MasteryLevel.REVIEWING, MasteryLevel.fromLevel(4, 20, 10))
        assertEquals(MasteryLevel.MASTERED, MasteryLevel.fromLevel(5, 45, 20))
    }
    
    @Test
    fun `VocabularyEntry notes field exists`() {
        val entry = VocabularyEntry(
            id = "test-1",
            word = "palabra",
            translation = "word",
            language = "es",
            partOfSpeech = PartOfSpeech.NOUN,
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
            lastReviewedAt = null,
            notes = "This is a common word"
        )
        
        assertEquals("This is a common word", entry.notes)
    }
    
    @Test
    fun `VocabularyEntry notes defaults to null`() {
        val entry = VocabularyEntry(
            id = "test-2",
            word = "libro",
            translation = "book",
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
        
        assertNull(entry.notes)
    }
    
    private fun createTestEntry(
        masteryLevel: Int,
        totalReviews: Int,
        intervalDays: Int
    ) = VocabularyEntry(
        id = "test-vocab",
        word = "test",
        translation = "test",
        language = "es",
        partOfSpeech = null,
        exampleSentence = null,
        audioUrl = null,
        masteryLevel = masteryLevel,
        easeFactor = 2.5f,
        intervalDays = intervalDays,
        nextReviewAt = 0L,
        totalReviews = totalReviews,
        correctReviews = totalReviews,
        firstSeenInDialogId = null,
        firstSeenAt = 0L,
        lastReviewedAt = null
    )
}
