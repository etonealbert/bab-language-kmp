package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.TranslationCacheRepository
import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.WordTranslation

class InMemoryTranslationCacheRepository : TranslationCacheRepository {
    
    private val cache = mutableMapOf<String, WordTranslation>()
    
    override suspend fun get(
        word: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ): WordTranslation? {
        val key = generateCacheKey(word, sourceLanguage, targetLanguage)
        return cache[key]
    }
    
    override suspend fun put(translation: WordTranslation) {
        val key = generateCacheKey(
            translation.word,
            translation.sourceLanguage,
            translation.targetLanguage
        )
        cache[key] = translation
    }
    
    override suspend fun getAll(): Map<String, WordTranslation> = cache.toMap()
    
    override suspend fun clear() {
        cache.clear()
    }
    
    override suspend fun remove(
        word: String,
        sourceLanguage: LanguageCode,
        targetLanguage: LanguageCode
    ) {
        val key = generateCacheKey(word, sourceLanguage, targetLanguage)
        cache.remove(key)
    }
}
