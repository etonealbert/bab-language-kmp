package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.HistoryErrorReason
import com.bablabs.bringabrainlanguage.domain.interfaces.HistorySaveResult
import com.bablabs.bringabrainlanguage.domain.models.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MockRemoteHistoryRepositoryTest {

    private fun createPremiumProfile() = UserProfile(
        id = "premium-user",
        displayName = "Premium User",
        nativeLanguage = "en",
        targetLanguages = listOf(TargetLanguage("es", CEFRLevel.B1, 1000L)),
        currentTargetLanguage = "es",
        interests = setOf(Interest.TRAVEL),
        learningGoals = setOf(LearningGoal.CONVERSATION),
        dailyGoalMinutes = 15,
        voiceSpeed = VoiceSpeed.NORMAL,
        showTranslations = TranslationMode.ON_TAP,
        isPremium = true,
        onboardingCompleted = true,
        createdAt = 1000L,
        lastActiveAt = 2000L
    )

    private fun createFreeProfile() = createPremiumProfile().copy(
        id = "free-user",
        displayName = "Free User",
        isPremium = false
    )

    private fun createHistorySession(
        sessionId: String = "session-1",
        timestamp: Long = 1000L
    ) = HistorySession(
        sessionId = sessionId,
        scenarioTitle = "Ordering Coffee",
        timestamp = timestamp,
        targetLanguage = "es",
        durationSeconds = 300L,
        dialogLines = listOf(
            DialogLine(
                id = "line-1",
                speakerId = "user",
                roleName = "Customer",
                textNative = "Un café, por favor",
                textTranslated = "A coffee, please",
                timestamp = 1000L
            )
        )
    )

    @Test
    fun `save succeeds for premium user`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val result = repo.saveSession(createHistorySession(), createPremiumProfile())
        assertIs<HistorySaveResult.Success>(result)
        assertEquals("session-1", result.session.sessionId)
    }

    @Test
    fun `save fails with FEATURE_LOCKED for free user`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val result = repo.saveSession(createHistorySession(), createFreeProfile())
        assertIs<HistorySaveResult.Error>(result)
        assertEquals(HistoryErrorReason.FEATURE_LOCKED, result.reason)
    }

    @Test
    fun `history flow updates after premium save`() = runTest {
        val repo = MockRemoteHistoryRepository()
        assertTrue(repo.history.value.isEmpty())

        repo.saveSession(createHistorySession(), createPremiumProfile())
        assertEquals(1, repo.history.value.size)
        assertEquals("session-1", repo.history.value.first().sessionId)
    }

    @Test
    fun `history flow does not update after free user save`() = runTest {
        val repo = MockRemoteHistoryRepository()
        repo.saveSession(createHistorySession(), createFreeProfile())
        assertTrue(repo.history.value.isEmpty())
    }

    @Test
    fun `getSessions returns saved sessions sorted by timestamp descending`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val profile = createPremiumProfile()

        repo.saveSession(createHistorySession("s-1", 1000L), profile)
        repo.saveSession(createHistorySession("s-2", 3000L), profile)
        repo.saveSession(createHistorySession("s-3", 2000L), profile)

        val sessions = repo.getSessions()
        assertEquals(3, sessions.size)
        assertEquals("s-2", sessions[0].sessionId)
        assertEquals("s-3", sessions[1].sessionId)
        assertEquals("s-1", sessions[2].sessionId)
    }

    @Test
    fun `deleteSession removes session and updates flow`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val profile = createPremiumProfile()

        repo.saveSession(createHistorySession("s-1"), profile)
        repo.saveSession(createHistorySession("s-2", 2000L), profile)
        assertEquals(2, repo.history.value.size)

        repo.deleteSession("s-1")
        assertEquals(1, repo.history.value.size)
        assertEquals("s-2", repo.history.value.first().sessionId)
    }

    @Test
    fun `clear removes all sessions and updates flow`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val profile = createPremiumProfile()

        repo.saveSession(createHistorySession("s-1"), profile)
        repo.saveSession(createHistorySession("s-2", 2000L), profile)

        repo.clear()
        assertTrue(repo.history.value.isEmpty())
        assertTrue(repo.getSessions().isEmpty())
    }
}
