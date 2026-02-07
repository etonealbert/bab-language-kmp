package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.models.CEFRLevel
import com.bablabs.bringabrainlanguage.domain.models.Interest
import com.bablabs.bringabrainlanguage.domain.models.LearningGoal
import com.bablabs.bringabrainlanguage.domain.models.PartOfSpeech
import com.bablabs.bringabrainlanguage.domain.models.TargetLanguage
import com.bablabs.bringabrainlanguage.domain.models.TranslationMode
import com.bablabs.bringabrainlanguage.domain.models.UserProfile
import com.bablabs.bringabrainlanguage.domain.models.VoiceSpeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrainSDKVocabularyTest {
    
    private fun createTestProfile() = UserProfile(
        id = "test-user",
        displayName = "Test User",
        nativeLanguage = "en",
        targetLanguages = listOf(
            TargetLanguage("es", CEFRLevel.B1, System.currentTimeMillis())
        ),
        currentTargetLanguage = "es",
        interests = setOf(Interest.TRAVEL),
        learningGoals = setOf(LearningGoal.CONVERSATION),
        dailyGoalMinutes = 15,
        voiceSpeed = VoiceSpeed.NORMAL,
        showTranslations = TranslationMode.ON_TAP,
        onboardingCompleted = true,
        createdAt = System.currentTimeMillis(),
        lastActiveAt = System.currentTimeMillis()
    )
    
    @Test
    fun `translateWord returns translation for known word`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val result = sdk.translateWord(
            word = "café",
            sentenceContext = "Quiero un café"
        )
        
        assertEquals("café", result.word)
        assertEquals("coffee", result.translation)
        assertEquals(PartOfSpeech.NOUN, result.partOfSpeech)
        assertNotNull(result.phoneticSpelling)
    }
    
    @Test
    fun `translateWord uses profile languages by default`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val result = sdk.translateWord(
            word = "hola",
            sentenceContext = ""
        )
        
        assertEquals("es", result.sourceLanguage)
        assertEquals("en", result.targetLanguage)
    }
    
    @Test
    fun `translateWord respects explicit language parameters`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val result = sdk.translateWord(
            word = "bonjour",
            sentenceContext = "",
            sourceLanguage = "fr",
            targetLanguage = "de"
        )
        
        assertEquals("fr", result.sourceLanguage)
        assertEquals("de", result.targetLanguage)
    }
    
    @Test
    fun `translateWord caches results`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        sdk.translateWord("café", "")
        
        val cache = sdk.translationCache.first()
        assertTrue(cache.isNotEmpty())
        assertTrue(cache.keys.any { it.contains("café") })
    }
    
    @Test
    fun `clearTranslationCache empties the cache`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        sdk.translateWord("café", "")
        assertTrue(sdk.translationCache.first().isNotEmpty())
        
        sdk.clearTranslationCache()
        
        assertTrue(sdk.translationCache.first().isEmpty())
    }
    
    @Test
    fun `addToVocabulary with parameters creates entry`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val entry = sdk.addToVocabulary(
            word = "perro",
            translation = "dog",
            partOfSpeech = PartOfSpeech.NOUN,
            exampleSentence = "El perro es grande"
        )
        
        assertEquals("perro", entry.word)
        assertEquals("dog", entry.translation)
        assertEquals(PartOfSpeech.NOUN, entry.partOfSpeech)
        assertEquals("El perro es grande", entry.exampleSentence)
        assertEquals("es", entry.language)
    }
    
    @Test
    fun `isInVocabulary returns true for added words`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        assertFalse(sdk.isInVocabulary("gato"))
        
        sdk.addToVocabulary(
            word = "gato",
            translation = "cat"
        )
        
        assertTrue(sdk.isInVocabulary("gato"))
    }
    
    @Test
    fun `isInVocabulary is case insensitive`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        sdk.addToVocabulary(word = "Casa", translation = "house")
        
        assertTrue(sdk.isInVocabulary("casa"))
        assertTrue(sdk.isInVocabulary("CASA"))
        assertTrue(sdk.isInVocabulary("Casa"))
    }
    
    @Test
    fun `getVocabularyEntry returns entry for existing word`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        sdk.addToVocabulary(
            word = "libro",
            translation = "book",
            partOfSpeech = PartOfSpeech.NOUN
        )
        
        val entry = sdk.getVocabularyEntry("libro")
        
        assertNotNull(entry)
        assertEquals("libro", entry.word)
        assertEquals("book", entry.translation)
    }
    
    @Test
    fun `getVocabularyEntry returns null for non-existent word`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val entry = sdk.getVocabularyEntry("nonexistent")
        
        assertNull(entry)
    }
    
    @Test
    fun `removeFromVocabulary deletes entry`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val entry = sdk.addToVocabulary(word = "mesa", translation = "table")
        assertTrue(sdk.isInVocabulary("mesa"))
        
        sdk.removeFromVocabulary(entry.id)
        
        assertFalse(sdk.isInVocabulary("mesa"))
    }
    
    @Test
    fun `updateVocabularyNotes adds notes to entry`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        val entry = sdk.addToVocabulary(word = "silla", translation = "chair")
        assertNull(sdk.getVocabularyEntry("silla")?.notes)
        
        sdk.updateVocabularyNotes(entry.id, "La silla - feminine noun")
        
        val updated = sdk.getVocabularyEntry("silla")
        assertEquals("La silla - feminine noun", updated?.notes)
    }
    
    @Test
    fun `vocabularyEntries StateFlow updates when entries added`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        assertTrue(sdk.vocabularyEntries.first().isEmpty())
        
        sdk.addToVocabulary(word = "uno", translation = "one")
        sdk.addToVocabulary(word = "dos", translation = "two")
        
        val entries = sdk.vocabularyEntries.first()
        assertEquals(2, entries.size)
    }
    
    @Test
    fun `vocabularyStats updates when entries added`() = runTest {
        val sdk = BrainSDK()
        sdk.completeOnboarding(createTestProfile())
        
        assertEquals(0, sdk.vocabularyStats.first().total)
        
        sdk.addToVocabulary(word = "primero", translation = "first")
        
        assertEquals(1, sdk.vocabularyStats.first().total)
        assertEquals(1, sdk.vocabularyStats.first().newCount)
    }
}
