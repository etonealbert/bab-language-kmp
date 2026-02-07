package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.VocabularyRepository
import com.bablabs.bringabrainlanguage.domain.models.LanguageCode
import com.bablabs.bringabrainlanguage.domain.models.VocabularyEntry
import com.bablabs.bringabrainlanguage.domain.models.VocabularyStats
import com.bablabs.bringabrainlanguage.domain.services.SRSScheduler
import kotlinx.datetime.Clock

class InMemoryVocabularyRepository : VocabularyRepository {
    
    private val entries = mutableMapOf<String, VocabularyEntry>()
    
    override suspend fun getAll(language: LanguageCode): List<VocabularyEntry> =
        entries.values.filter { it.language == language }
    
    override suspend fun getById(id: String): VocabularyEntry? = entries[id]
    
    override suspend fun getByWord(word: String, language: LanguageCode): VocabularyEntry? =
        entries.values.find { it.word.equals(word, ignoreCase = true) && it.language == language }
    
    override suspend fun getDueForReview(language: LanguageCode, limit: Int): List<VocabularyEntry> =
        SRSScheduler.getDueReviews(getAll(language), limit)
    
    override suspend fun upsert(entry: VocabularyEntry) {
        entries[entry.id] = entry
    }
    
    override suspend fun upsertAll(entries: List<VocabularyEntry>) {
        entries.forEach { upsert(it) }
    }
    
    override suspend fun getStats(language: LanguageCode): VocabularyStats {
        val languageEntries = getAll(language)
        val now = Clock.System.now().toEpochMilliseconds()
        
        return VocabularyStats(
            total = languageEntries.size,
            newCount = languageEntries.count { it.masteryLevel == 0 && it.totalReviews == 0 },
            learningCount = languageEntries.count { it.masteryLevel in 1..2 },
            reviewingCount = languageEntries.count { it.masteryLevel in 3..4 },
            masteredCount = languageEntries.count { it.masteryLevel == 5 },
            dueCount = languageEntries.count { it.nextReviewAt <= now }
        )
    }
    
    override suspend fun searchByWord(query: String, language: LanguageCode): List<VocabularyEntry> =
        getAll(language).filter { 
            it.word.contains(query, ignoreCase = true) || 
            it.translation.contains(query, ignoreCase = true) 
        }
    
    override suspend fun delete(id: String) {
        entries.remove(id)
    }
    
    override suspend fun updateNotes(id: String, notes: String) {
        entries[id]?.let { entry ->
            entries[id] = entry.copy(notes = notes)
        }
    }
    
    override suspend fun clear() {
        entries.clear()
    }
}
