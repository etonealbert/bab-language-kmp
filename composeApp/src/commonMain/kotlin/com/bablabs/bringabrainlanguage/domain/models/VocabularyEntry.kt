package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class VocabularyEntry(
    val id: String,
    val word: String,
    val translation: String,
    val language: LanguageCode,
    val partOfSpeech: PartOfSpeech?,
    val exampleSentence: String?,
    val audioUrl: String?,
    val masteryLevel: Int,
    val easeFactor: Float,
    val intervalDays: Int,
    val nextReviewAt: Long,
    val totalReviews: Int,
    val correctReviews: Int,
    val firstSeenInDialogId: String?,
    val firstSeenAt: Long,
    val lastReviewedAt: Long?
)

@Serializable
enum class PartOfSpeech {
    NOUN, VERB, ADJECTIVE, ADVERB, PREPOSITION,
    CONJUNCTION, PRONOUN, INTERJECTION, ARTICLE
}

@Serializable
enum class ReviewQuality { AGAIN, HARD, GOOD, EASY }

@Serializable
data class VocabularyStats(
    val total: Int,
    val newCount: Int,
    val learningCount: Int,
    val reviewingCount: Int,
    val masteredCount: Int,
    val dueCount: Int
)
