package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.models.PartOfSpeech
import com.bablabs.bringabrainlanguage.domain.models.VocabularyEntry
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InMemoryVocabularyRepositoryExtendedTest {
    
    private val repository = InMemoryVocabularyRepository()
    
    private fun createEntry(
        id: String,
        word: String,
        language: String = "es",
        notes: String? = null
    ) = VocabularyEntry(
        id = id,
        word = word,
        translation = "translation",
        language = language,
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
        notes = notes
    )
    
    @Test
    fun `getByWord returns entry for existing word`() = runTest {
        val entry = createEntry("1", "café", "es")
        repository.upsert(entry)
        
        val result = repository.getByWord("café", "es")
        
        assertNotNull(result)
        assertEquals("café", result.word)
    }
    
    @Test
    fun `getByWord is case insensitive`() = runTest {
        val entry = createEntry("1", "Café", "es")
        repository.upsert(entry)
        
        val result = repository.getByWord("café", "es")
        
        assertNotNull(result)
    }
    
    @Test
    fun `getByWord returns null for non-existent word`() = runTest {
        val result = repository.getByWord("nonexistent", "es")
        assertNull(result)
    }
    
    @Test
    fun `getByWord respects language parameter`() = runTest {
        val esEntry = createEntry("1", "hola", "es")
        val frEntry = createEntry("2", "hola", "fr")
        repository.upsert(esEntry)
        repository.upsert(frEntry)
        
        val esResult = repository.getByWord("hola", "es")
        val frResult = repository.getByWord("hola", "fr")
        val deResult = repository.getByWord("hola", "de")
        
        assertEquals("es", esResult?.language)
        assertEquals("fr", frResult?.language)
        assertNull(deResult)
    }
    
    @Test
    fun `updateNotes updates existing entry notes`() = runTest {
        val entry = createEntry("1", "libro", "es", notes = null)
        repository.upsert(entry)
        
        repository.updateNotes("1", "This is a masculine noun")
        
        val updated = repository.getById("1")
        assertEquals("This is a masculine noun", updated?.notes)
    }
    
    @Test
    fun `updateNotes does nothing for non-existent entry`() = runTest {
        repository.updateNotes("nonexistent", "Some notes")
        
        val result = repository.getById("nonexistent")
        assertNull(result)
    }
    
    @Test
    fun `entry with notes can be stored and retrieved`() = runTest {
        val entry = createEntry("1", "palabra", "es", notes = "Very common word")
        repository.upsert(entry)
        
        val result = repository.getById("1")
        assertEquals("Very common word", result?.notes)
    }
    
    @Test
    fun `delete removes entry correctly`() = runTest {
        val entry = createEntry("1", "test", "es")
        repository.upsert(entry)
        
        repository.delete("1")
        
        assertNull(repository.getById("1"))
        assertNull(repository.getByWord("test", "es"))
    }
}
