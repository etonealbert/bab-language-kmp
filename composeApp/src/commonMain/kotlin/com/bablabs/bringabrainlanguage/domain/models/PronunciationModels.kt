package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

/**
 * Result of a player's pronunciation attempt on a dialog line.
 * 
 * Populated by native speech recognition on iOS (Speech Framework) 
 * or Android (SpeechRecognizer), then passed to SDK via completeLine().
 */
@Serializable
data class PronunciationResult(
    /** Number of word-level errors detected */
    val errorCount: Int,
    
    /** Overall accuracy score from 0.0 to 1.0 */
    val accuracy: Float,
    
    /** List of specific word errors for detailed feedback */
    val wordErrors: List<WordError>,
    
    /** True if user skipped reading (microphone unavailable) */
    val skipped: Boolean,
    
    /** Time taken to read the line in milliseconds */
    val duration: Long
) {
    companion object {
        /**
         * Create a skipped result (user didn't attempt reading).
         */
        fun skipped(): PronunciationResult = PronunciationResult(
            errorCount = 0,
            accuracy = 0f,
            wordErrors = emptyList(),
            skipped = true,
            duration = 0L
        )
        
        /**
         * Create a perfect result (100% accuracy).
         */
        fun perfect(duration: Long): PronunciationResult = PronunciationResult(
            errorCount = 0,
            accuracy = 1.0f,
            wordErrors = emptyList(),
            skipped = false,
            duration = duration
        )
    }
}

/**
 * Represents a single word-level pronunciation error.
 */
@Serializable
data class WordError(
    /** The word that was mispronounced */
    val word: String,
    
    /** Zero-based position of the word in the sentence */
    val position: Int,
    
    /** Expected pronunciation (phonetic or text) */
    val expected: String?,
    
    /** What the speech recognizer heard */
    val heard: String?
)

/**
 * Visibility state of a dialog line in multiplayer.
 */
@Serializable
enum class LineVisibility {
    /** Only the assigned player can see the line (while reading) */
    PRIVATE,
    
    /** All players can see the line in theater view */
    COMMITTED
}
