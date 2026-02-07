package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.WordTranslation

/**
 * Repository for caching word translations.
 * 
 * Native apps should provide platform-specific implementations:
 * - iOS: SwiftData-backed repository
 * - Android: Room-backed repository
 * 
 * Default in-memory implementation provided for SDK testing.
 */
interface TranslationCacheRepository {
    
    suspend fun get(word: String, sourceLanguage: LanguageCode, targetLanguage: LanguageCode): WordTranslation?
    
    suspend fun put(translation: WordTranslation)
    
    suspend fun getAll(): Map<String, WordTranslation>
    
    suspend fun clear()
    
    suspend fun remove(word: String, sourceLanguage: LanguageCode, targetLanguage: LanguageCode)
    
    fun generateCacheKey(word: String, sourceLanguage: LanguageCode, targetLanguage: LanguageCode): String =
        "$sourceLanguage:$targetLanguage:${word.lowercase()}"
}
