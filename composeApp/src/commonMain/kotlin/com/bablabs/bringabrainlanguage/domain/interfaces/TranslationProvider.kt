package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.WordTranslation

/**
 * Abstraction for word translation services.
 * 
 * Implementations may use:
 * - Server API (for subscribed users)
 * - On-device Foundation Models (iOS 26 fallback)
 * - Mock data (for development/testing)
 * 
 * Native apps should inject their own implementation that delegates to
 * SwiftData/Room for caching and appropriate backend for translation.
 */
interface TranslationProvider {
    
    /**
     * Translate a single word with context awareness.
     * Context helps disambiguate words with multiple meanings.
     * 
     * @param word The word to translate
     * @param sentenceContext The surrounding sentence for disambiguation
     * @param sourceLanguage Source language code (e.g., "es")
     * @param targetLanguage Target language code (e.g., "en")
     * @return Rich translation result with definitions, examples, etc.
     */
    suspend fun translateWord(
        word: String,
        sentenceContext: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ): WordTranslation
    
    /**
     * Check if the provider is available and ready to translate.
     * May return false if offline and server-only, or if LLM not available.
     */
    fun isAvailable(): Boolean
}
