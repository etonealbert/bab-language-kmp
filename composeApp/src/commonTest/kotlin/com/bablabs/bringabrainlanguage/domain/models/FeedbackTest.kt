package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeedbackTest {
    
    @Test
    fun `NarrativeRecast captures in-flow error correction`() {
        val recast = Feedback.NarrativeRecast(
            originalText = "I go to store yesterday",
            correctedText = "I went to the store yesterday",
            characterId = "barista-01",
            recastLine = "Oh, you went to the store yesterday? What did you buy?",
            errorType = ErrorType.GRAMMAR
        )
        
        assertEquals("I go to store yesterday", recast.originalText)
        assertEquals("I went to the store yesterday", recast.correctedText)
        assertEquals("barista-01", recast.characterId)
        assertEquals(ErrorType.GRAMMAR, recast.errorType)
    }
    
    @Test
    fun `PrivateNudge provides hints at different levels`() {
        val visualHint = Feedback.PrivateNudge(
            playerId = "player-123",
            hintLevel = HintLevel.VISUAL_CLUE,
            content = "coffee_cup.png",
            triggeredAt = 1000L
        )
        
        val starterWords = Feedback.PrivateNudge(
            playerId = "player-123",
            hintLevel = HintLevel.STARTER_WORDS,
            content = "Quisiera un...",
            triggeredAt = 2000L
        )
        
        val fullTranslation = Feedback.PrivateNudge(
            playerId = "player-123",
            hintLevel = HintLevel.FULL_TRANSLATION,
            content = "I would like a coffee, please",
            triggeredAt = 3000L
        )
        
        assertEquals(HintLevel.VISUAL_CLUE, visualHint.hintLevel)
        assertEquals(HintLevel.STARTER_WORDS, starterWords.hintLevel)
        assertEquals(HintLevel.FULL_TRANSLATION, fullTranslation.hintLevel)
    }
    
    @Test
    fun `SessionReport summarizes learning outcomes`() {
        val report = Feedback.SessionReport(
            playerId = "player-456",
            sessionId = "session-789",
            errorsWithCorrections = listOf(
                ErrorCorrection(
                    original = "Yo querer cafe",
                    corrected = "Yo quiero cafe",
                    errorType = ErrorType.CONJUGATION,
                    explanation = "Use 'quiero' for first person singular",
                    lineId = "line-001"
                )
            ),
            vocabularyEncountered = listOf("cafe", "leche", "azucar"),
            grammarPointsPracticed = listOf("present tense", "articles"),
            fluencyScore = 0.75f,
            collaborationScore = 0.9f,
            totalXPEarned = 150
        )
        
        assertEquals("player-456", report.playerId)
        assertEquals("session-789", report.sessionId)
        assertEquals(1, report.errorsWithCorrections.size)
        assertEquals(ErrorType.CONJUGATION, report.errorsWithCorrections[0].errorType)
        assertEquals(3, report.vocabularyEncountered.size)
        assertEquals(150, report.totalXPEarned)
    }
    
    @Test
    fun `HintLevel enum has three levels`() {
        assertEquals(3, HintLevel.entries.size)
        assertTrue(HintLevel.entries.contains(HintLevel.VISUAL_CLUE))
        assertTrue(HintLevel.entries.contains(HintLevel.STARTER_WORDS))
        assertTrue(HintLevel.entries.contains(HintLevel.FULL_TRANSLATION))
    }
    
    @Test
    fun `ErrorType enum contains expected values`() {
        val types = ErrorType.entries
        assertTrue(types.contains(ErrorType.GRAMMAR))
        assertTrue(types.contains(ErrorType.VOCABULARY))
        assertTrue(types.contains(ErrorType.SPELLING))
        assertTrue(types.contains(ErrorType.WORD_ORDER))
        assertTrue(types.contains(ErrorType.CONJUGATION))
        assertTrue(types.contains(ErrorType.GENDER_AGREEMENT))
        assertTrue(types.contains(ErrorType.ARTICLE_USAGE))
    }
    
    @Test
    fun `Feedback sealed class can be used in when expression`() {
        val feedbackItems: List<Feedback> = listOf(
            Feedback.NarrativeRecast("a", "b", "c", "d", ErrorType.GRAMMAR),
            Feedback.PrivateNudge("p", HintLevel.STARTER_WORDS, "content", 0L),
            Feedback.SessionReport("p", "s", emptyList(), emptyList(), emptyList(), null, null, 0)
        )
        
        feedbackItems.forEach { feedback ->
            val type = when (feedback) {
                is Feedback.NarrativeRecast -> "recast"
                is Feedback.PrivateNudge -> "nudge"
                is Feedback.SessionReport -> "report"
            }
            assertTrue(type in listOf("recast", "nudge", "report"))
        }
    }
}
