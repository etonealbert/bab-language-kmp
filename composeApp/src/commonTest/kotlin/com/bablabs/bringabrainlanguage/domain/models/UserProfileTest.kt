package com.bablabs.bringabrainlanguage.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserProfileTest {
    
    @Test
    fun `CEFRLevel values are ordered from beginner to mastery`() {
        val levels = CEFRLevel.entries
        assertEquals(6, levels.size)
        assertEquals(CEFRLevel.A1, levels[0])
        assertEquals(CEFRLevel.A2, levels[1])
        assertEquals(CEFRLevel.B1, levels[2])
        assertEquals(CEFRLevel.B2, levels[3])
        assertEquals(CEFRLevel.C1, levels[4])
        assertEquals(CEFRLevel.C2, levels[5])
    }
    
    @Test
    fun `UserProfile can be created with minimal required fields`() {
        val profile = UserProfile(
            id = "user-123",
            displayName = "John",
            nativeLanguage = "en-US",
            targetLanguages = listOf(
                TargetLanguage(
                    code = "es-ES",
                    proficiencyLevel = CEFRLevel.A1,
                    startedAt = 1000L
                )
            ),
            currentTargetLanguage = "es-ES",
            interests = setOf(Interest.TRAVEL),
            learningGoals = setOf(LearningGoal.CONVERSATION),
            dailyGoalMinutes = 15,
            voiceSpeed = VoiceSpeed.NORMAL,
            showTranslations = TranslationMode.ON_TAP,
            onboardingCompleted = true,
            createdAt = 1000L,
            lastActiveAt = 2000L
        )
        
        assertEquals("user-123", profile.id)
        assertEquals("John", profile.displayName)
        assertEquals("en-US", profile.nativeLanguage)
        assertEquals(1, profile.targetLanguages.size)
        assertEquals(CEFRLevel.A1, profile.targetLanguages[0].proficiencyLevel)
        assertTrue(profile.onboardingCompleted)
    }
    
    @Test
    fun `UserProfile supports multiple target languages`() {
        val profile = UserProfile(
            id = "user-456",
            displayName = "Maria",
            nativeLanguage = "es-ES",
            targetLanguages = listOf(
                TargetLanguage("en-US", CEFRLevel.B2, 1000L),
                TargetLanguage("fr-FR", CEFRLevel.A2, 2000L),
                TargetLanguage("de-DE", CEFRLevel.A1, 3000L)
            ),
            currentTargetLanguage = "en-US",
            interests = setOf(Interest.BUSINESS, Interest.TRAVEL),
            learningGoals = setOf(LearningGoal.WORK, LearningGoal.CONVERSATION),
            dailyGoalMinutes = 30,
            voiceSpeed = VoiceSpeed.FAST,
            showTranslations = TranslationMode.NEVER,
            onboardingCompleted = true,
            createdAt = 1000L,
            lastActiveAt = 3000L
        )
        
        assertEquals(3, profile.targetLanguages.size)
        assertEquals("en-US", profile.currentTargetLanguage)
    }
    
    @Test
    fun `Interest enum contains all expected values`() {
        val interests = Interest.entries
        assertTrue(interests.contains(Interest.TRAVEL))
        assertTrue(interests.contains(Interest.BUSINESS))
        assertTrue(interests.contains(Interest.ROMANCE))
        assertTrue(interests.contains(Interest.SCI_FI))
        assertTrue(interests.contains(Interest.EVERYDAY))
        assertTrue(interests.contains(Interest.FOOD))
        assertTrue(interests.contains(Interest.CULTURE))
        assertTrue(interests.contains(Interest.SPORTS))
        assertTrue(interests.contains(Interest.MUSIC))
        assertTrue(interests.contains(Interest.MOVIES))
    }
    
    @Test
    fun `LearningGoal enum contains all expected values`() {
        val goals = LearningGoal.entries
        assertTrue(goals.contains(LearningGoal.CONVERSATION))
        assertTrue(goals.contains(LearningGoal.READING))
        assertTrue(goals.contains(LearningGoal.LISTENING))
        assertTrue(goals.contains(LearningGoal.EXAM_PREP))
        assertTrue(goals.contains(LearningGoal.WORK))
        assertTrue(goals.contains(LearningGoal.TRAVEL))
    }
    
    @Test
    fun `VoiceSpeed enum has three levels`() {
        assertEquals(3, VoiceSpeed.entries.size)
        assertTrue(VoiceSpeed.entries.contains(VoiceSpeed.SLOW))
        assertTrue(VoiceSpeed.entries.contains(VoiceSpeed.NORMAL))
        assertTrue(VoiceSpeed.entries.contains(VoiceSpeed.FAST))
    }
    
    @Test
    fun `TranslationMode enum has three options`() {
        assertEquals(3, TranslationMode.entries.size)
        assertTrue(TranslationMode.entries.contains(TranslationMode.ALWAYS))
        assertTrue(TranslationMode.entries.contains(TranslationMode.ON_TAP))
        assertTrue(TranslationMode.entries.contains(TranslationMode.NEVER))
    }
    
    @Test
    fun `UserProfile copy works correctly`() {
        val original = UserProfile(
            id = "user-789",
            displayName = "Test",
            nativeLanguage = "en-US",
            targetLanguages = listOf(TargetLanguage("es-ES", CEFRLevel.A1, 1000L)),
            currentTargetLanguage = "es-ES",
            interests = setOf(Interest.TRAVEL),
            learningGoals = setOf(LearningGoal.CONVERSATION),
            dailyGoalMinutes = 15,
            voiceSpeed = VoiceSpeed.NORMAL,
            showTranslations = TranslationMode.ON_TAP,
            onboardingCompleted = false,
            createdAt = 1000L,
            lastActiveAt = 1000L
        )
        
        val updated = original.copy(
            onboardingCompleted = true,
            dailyGoalMinutes = 30,
            lastActiveAt = 2000L
        )
        
        assertEquals(original.id, updated.id)
        assertTrue(updated.onboardingCompleted)
        assertEquals(30, updated.dailyGoalMinutes)
        assertEquals(2000L, updated.lastActiveAt)
    }
}
