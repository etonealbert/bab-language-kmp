package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.models.ExampleSentence
import com.bablabs.bringabrainlanguage.domain.models.PartOfSpeech
import com.bablabs.bringabrainlanguage.domain.models.WordTranslation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryTranslationCacheRepositoryTest {
    
    private val repository = InMemoryTranslationCacheRepository()
    
    private fun createTranslation(
        word: String,
        sourceLanguage: String = "es",
        targetLanguage: String = "en"
    ) = WordTranslation(
        word = word,
        translation = "translation of $word",
        partOfSpeech = PartOfSpeech.NOUN,
        phoneticSpelling = null,
        audioUrl = null,
        definitions = emptyList(),
        exampleSentences = emptyList(),
        relatedWords = emptyList(),
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        contextUsed = null
    )
    
    @Test
    fun `get returns null for non-existent entry`() = runTest {
        val result = repository.get("nonexistent", "es", "en")
        assertNull(result)
    }
    
    @Test
    fun `put and get work correctly`() = runTest {
        val translation = createTranslation("café")
        repository.put(translation)
        
        val result = repository.get("café", "es", "en")
        assertEquals(translation, result)
    }
    
    @Test
    fun `get is case insensitive for word`() = runTest {
        val translation = createTranslation("Café")
        repository.put(translation)
        
        val result = repository.get("café", "es", "en")
        assertEquals(translation, result)
    }
    
    @Test
    fun `different language pairs are stored separately`() = runTest {
        val esEn = createTranslation("hola", "es", "en")
        val frEn = createTranslation("hola", "fr", "en")
        
        repository.put(esEn)
        repository.put(frEn)
        
        val resultEsEn = repository.get("hola", "es", "en")
        val resultFrEn = repository.get("hola", "fr", "en")
        
        assertEquals("es", resultEsEn?.sourceLanguage)
        assertEquals("fr", resultFrEn?.sourceLanguage)
    }
    
    @Test
    fun `getAll returns all cached translations`() = runTest {
        repository.put(createTranslation("uno"))
        repository.put(createTranslation("dos"))
        repository.put(createTranslation("tres"))
        
        val all = repository.getAll()
        assertEquals(3, all.size)
    }
    
    @Test
    fun `clear removes all entries`() = runTest {
        repository.put(createTranslation("uno"))
        repository.put(createTranslation("dos"))
        
        repository.clear()
        
        val all = repository.getAll()
        assertTrue(all.isEmpty())
    }
    
    @Test
    fun `remove deletes specific entry`() = runTest {
        repository.put(createTranslation("uno"))
        repository.put(createTranslation("dos"))
        
        repository.remove("uno", "es", "en")
        
        assertNull(repository.get("uno", "es", "en"))
        assertEquals("translation of dos", repository.get("dos", "es", "en")?.translation)
    }
    
    @Test
    fun `generateCacheKey creates consistent keys`() {
        val key1 = repository.generateCacheKey("café", "es", "en")
        val key2 = repository.generateCacheKey("CAFÉ", "es", "en")
        
        assertEquals(key1, key2)
        assertEquals("es:en:café", key1)
    }
}
