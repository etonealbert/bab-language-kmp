package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

/**
 * Result of a word translation with rich metadata.
 * Returned by TranslationProvider when translating a single word.
 *
 * @property word The original word in source language (e.g., "café")
 * @property translation The primary translation (e.g., "coffee")
 * @property partOfSpeech Grammatical category (noun, verb, etc.)
 * @property phoneticSpelling Pronunciation guide (e.g., "ka-FEH")
 * @property audioUrl URL to pronunciation audio file
 * @property definitions List of possible definitions
 * @property exampleSentences Usage examples with translations
 * @property relatedWords Synonyms, antonyms, or related vocabulary
 * @property sourceLanguage The language of the original word
 * @property targetLanguage The language of the translation
 * @property contextUsed The sentence context used for disambiguation
 */
@Serializable
data class WordTranslation(
    val word: String,
    val translation: String,
    val partOfSpeech: PartOfSpeech? = null,
    val phoneticSpelling: String? = null,
    val audioUrl: String? = null,
    val definitions: List<String> = emptyList(),
    val exampleSentences: List<ExampleSentence> = emptyList(),
    val relatedWords: List<String> = emptyList(),
    val sourceLanguage: LanguageCode,
    val targetLanguage: LanguageCode,
    val contextUsed: String? = null
)

/**
 * An example sentence showing word usage in context.
 * Contains both the native (target) language version and translation.
 *
 * @property native The sentence in target language (e.g., "Quiero un café, por favor")
 * @property translated The sentence in source language (e.g., "I want a coffee, please")
 */
@Serializable
data class ExampleSentence(
    val native: String,
    val translated: String
)

/**
 * Mastery level for vocabulary entries based on SRS progress.
 * Computed from the numeric masteryLevel (0-5) in VocabularyEntry.
 *
 * Categories:
 * - NEW: Never reviewed (masteryLevel 0, totalReviews 0)
 * - LEARNING: Early stages (masteryLevel 1-2, or < 7 days interval)
 * - REVIEWING: Intermediate (masteryLevel 3-4, 7-30 days interval)
 * - MASTERED: Well-learned (masteryLevel 5, > 30 days interval)
 */
@Serializable
enum class MasteryLevel {
    NEW,
    LEARNING,
    REVIEWING,
    MASTERED;

    companion object {
        /**
         * Compute the mastery level from a VocabularyEntry.
         * Uses both masteryLevel and intervalDays for accurate classification.
         */
        fun fromEntry(entry: VocabularyEntry): MasteryLevel {
            return when {
                entry.masteryLevel == 0 && entry.totalReviews == 0 -> NEW
                entry.masteryLevel <= 2 || entry.intervalDays < 7 -> LEARNING
                entry.masteryLevel <= 4 || entry.intervalDays <= 30 -> REVIEWING
                else -> MASTERED
            }
        }

        /**
         * Compute mastery level from raw numeric values.
         */
        fun fromLevel(masteryLevel: Int, intervalDays: Int, totalReviews: Int): MasteryLevel {
            return when {
                masteryLevel == 0 && totalReviews == 0 -> NEW
                masteryLevel <= 2 || intervalDays < 7 -> LEARNING
                masteryLevel <= 4 || intervalDays <= 30 -> REVIEWING
                else -> MASTERED
            }
        }
    }
}

/**
 * Extension property to get the computed MasteryLevel for a VocabularyEntry.
 */
val VocabularyEntry.mastery: MasteryLevel
    get() = MasteryLevel.fromEntry(this)
