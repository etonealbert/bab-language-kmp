package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PedagogicalModelsTest {
    
    @Test
    fun `PlotTwist represents AI Director interventions`() {
        val twist = PlotTwist(
            id = "twist-001",
            description = "The waiter suddenly speaks French instead of Spanish",
            visualAsset = "confused_waiter.png",
            triggeredAt = 1000L,
            affectsPlayers = listOf("player-1", "player-2"),
            expiresAt = 5000L
        )
        
        assertEquals("twist-001", twist.id)
        assertEquals("The waiter suddenly speaks French instead of Spanish", twist.description)
        assertEquals("confused_waiter.png", twist.visualAsset)
        assertEquals(2, twist.affectsPlayers.size)
        assertEquals(5000L, twist.expiresAt)
    }
    
    @Test
    fun `PlotTwist with empty affectsPlayers means all players`() {
        val globalTwist = PlotTwist(
            id = "twist-002",
            description = "Breaking news interrupts the cafe",
            visualAsset = null,
            triggeredAt = 2000L,
            affectsPlayers = emptyList(),
            expiresAt = null
        )
        
        assertTrue(globalTwist.affectsPlayers.isEmpty())
        assertNull(globalTwist.expiresAt)
    }
    
    @Test
    fun `SecretObjective creates information gap missions`() {
        val objective = SecretObjective(
            playerId = "player-123",
            objective = "You have a $500 budget. Don't reveal the exact amount.",
            visibleToOthers = false,
            completedAt = null
        )
        
        assertEquals("player-123", objective.playerId)
        assertEquals(false, objective.visibleToOthers)
        assertNull(objective.completedAt)
        
        val completed = objective.copy(completedAt = 5000L)
        assertEquals(5000L, completed.completedAt)
    }
    
    @Test
    fun `PlayerContext enables asymmetric difficulty`() {
        val beginnerContext = PlayerContext(
            playerId = "beginner-player",
            proficiencyLevel = CEFRLevel.A1,
            vocabularyHints = listOf("cafe", "leche", "por favor"),
            grammarFocus = listOf("present tense"),
            secretObjective = null,
            preferredComplexity = 2
        )
        
        val advancedContext = PlayerContext(
            playerId = "advanced-player",
            proficiencyLevel = CEFRLevel.B2,
            vocabularyHints = listOf("descafeinado", "cortado", "sin lactosa"),
            grammarFocus = listOf("subjunctive", "conditionals"),
            secretObjective = SecretObjective(
                playerId = "advanced-player",
                objective = "Convince them to try a new drink",
                visibleToOthers = false,
                completedAt = null
            ),
            preferredComplexity = 7
        )
        
        assertEquals(CEFRLevel.A1, beginnerContext.proficiencyLevel)
        assertEquals(2, beginnerContext.preferredComplexity)
        assertEquals(CEFRLevel.B2, advancedContext.proficiencyLevel)
        assertEquals(7, advancedContext.preferredComplexity)
        assertTrue(advancedContext.secretObjective != null)
    }
    
    @Test
    fun `NudgeRequest tracks silence duration`() {
        val request = NudgeRequest(
            playerId = "player-456",
            silenceDurationMs = 8000L,
            requestedLevel = HintLevel.STARTER_WORDS,
            requestedAt = 10000L
        )
        
        assertEquals("player-456", request.playerId)
        assertEquals(8000L, request.silenceDurationMs)
        assertEquals(HintLevel.STARTER_WORDS, request.requestedLevel)
    }
}
