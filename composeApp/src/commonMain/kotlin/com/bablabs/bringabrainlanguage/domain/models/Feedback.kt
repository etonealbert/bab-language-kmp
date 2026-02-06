package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
sealed class Feedback {
    
    @Serializable
    data class NarrativeRecast(
        val originalText: String,
        val correctedText: String,
        val characterId: String,
        val recastLine: String,
        val errorType: ErrorType
    ) : Feedback()
    
    @Serializable
    data class PrivateNudge(
        val playerId: String,
        val hintLevel: HintLevel,
        val content: String,
        val triggeredAt: Long
    ) : Feedback()
    
    @Serializable
    data class SessionReport(
        val playerId: String,
        val sessionId: String,
        val errorsWithCorrections: List<ErrorCorrection>,
        val vocabularyEncountered: List<String>,
        val grammarPointsPracticed: List<String>,
        val fluencyScore: Float?,
        val collaborationScore: Float?,
        val totalXPEarned: Int
    ) : Feedback()
}

@Serializable
data class ErrorCorrection(
    val original: String,
    val corrected: String,
    val errorType: ErrorType,
    val explanation: String?,
    val lineId: String
)

@Serializable
enum class ErrorType {
    GRAMMAR, VOCABULARY, SPELLING, WORD_ORDER,
    CONJUGATION, GENDER_AGREEMENT, ARTICLE_USAGE
}

@Serializable
enum class HintLevel { VISUAL_CLUE, STARTER_WORDS, FULL_TRANSLATION }
