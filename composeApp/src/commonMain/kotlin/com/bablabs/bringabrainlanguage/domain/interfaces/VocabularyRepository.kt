package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.VocabularyEntry
import com.bablabs.bringabrainlanguage.domain.models.VocabularyStats

interface VocabularyRepository {
    suspend fun getAll(language: LanguageCode): List<VocabularyEntry>
    suspend fun getById(id: String): VocabularyEntry?
    suspend fun getDueForReview(language: LanguageCode, limit: Int = 20): List<VocabularyEntry>
    suspend fun upsert(entry: VocabularyEntry)
    suspend fun upsertAll(entries: List<VocabularyEntry>)
    suspend fun getStats(language: LanguageCode): VocabularyStats
    suspend fun searchByWord(query: String, language: LanguageCode): List<VocabularyEntry>
    suspend fun delete(id: String)
    suspend fun clear()
}
