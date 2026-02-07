package com.bablabs.bringabrainlanguage.infrastructure.translation

import com.bablabs.bringabrainlanguage.domain.models.PartOfSpeech
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MockTranslationProviderTest {
    
    private val provider = MockTranslationProvider()
    
    @Test
    fun `isAvailable returns true`() {
        assertTrue(provider.isAvailable())
    }
    
    @Test
    fun `translateWord returns cached translation for known words`() = runTest {
        val result = provider.translateWord(
            word = "café",
            sentenceContext = "Quiero un café",
            sourceLanguage = "es",
            targetLanguage = "en"
        )
        
        assertEquals("café", result.word)
        assertEquals("coffee", result.translation)
        assertEquals(PartOfSpeech.NOUN, result.partOfSpeech)
        assertEquals("ka-FEH", result.phoneticSpelling)
        assertTrue(result.definitions.isNotEmpty())
        assertTrue(result.exampleSentences.isNotEmpty())
        assertTrue(result.relatedWords.isNotEmpty())
    }
    
    @Test
    fun `translateWord is case insensitive`() = runTest {
        val result = provider.translateWord(
            word = "CAFÉ",
            sentenceContext = "",
            sourceLanguage = "es",
            targetLanguage = "en"
        )
        
        assertEquals("coffee", result.translation)
    }
    
    @Test
    fun `translateWord returns generic translation for unknown words`() = runTest {
        val result = provider.translateWord(
            word = "desconocido",
            sentenceContext = "Una palabra desconocida",
            sourceLanguage = "es",
            targetLanguage = "en"
        )
        
        assertEquals("desconocido", result.word)
        assertTrue(result.translation.contains("desconocido"))
        assertEquals("es", result.sourceLanguage)
        assertEquals("en", result.targetLanguage)
        assertEquals("Una palabra desconocida", result.contextUsed)
    }
    
    @Test
    fun `translateWord includes context when provided`() = runTest {
        val context = "Me gusta el café por la mañana"
        val result = provider.translateWord(
            word = "café",
            sentenceContext = context,
            sourceLanguage = "es",
            targetLanguage = "en"
        )
        
        assertEquals(context, result.contextUsed)
    }
    
    @Test
    fun `translateWord respects language parameters`() = runTest {
        val result = provider.translateWord(
            word = "hola",
            sentenceContext = "",
            sourceLanguage = "es-MX",
            targetLanguage = "en-US"
        )
        
        assertEquals("es-MX", result.sourceLanguage)
        assertEquals("en-US", result.targetLanguage)
    }
    
    @Test
    fun `translateWord handles all known words`() = runTest {
        val knownWords = listOf("café", "hola", "gracias", "agua", "libro")
        
        for (word in knownWords) {
            val result = provider.translateWord(word, "", "es", "en")
            assertNotNull(result.partOfSpeech)
            assertNotNull(result.phoneticSpelling)
            assertTrue(result.definitions.isNotEmpty())
        }
    }
}
