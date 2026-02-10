package com.bablabs.bringabrainlanguage

import com.bablabs.bringabrainlanguage.domain.interfaces.HistoryErrorReason
import com.bablabs.bringabrainlanguage.domain.interfaces.HistorySaveResult
import com.bablabs.bringabrainlanguage.domain.models.*
import com.bablabs.bringabrainlanguage.infrastructure.repositories.MockRemoteHistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BrainSDKHistoryTest {

    private fun createProfile(isPremium: Boolean) = UserProfile(
        id = "test-user",
        displayName = "Test User",
        nativeLanguage = "en",
        targetLanguages = listOf(TargetLanguage("es", CEFRLevel.B1, 1000L)),
        currentTargetLanguage = "es",
        interests = setOf(Interest.TRAVEL),
        learningGoals = setOf(LearningGoal.CONVERSATION),
        dailyGoalMinutes = 15,
        voiceSpeed = VoiceSpeed.NORMAL,
        showTranslations = TranslationMode.ON_TAP,
        isPremium = isPremium,
        onboardingCompleted = true,
        createdAt = 1000L,
        lastActiveAt = 2000L
    )

    @Test
    fun `history flow is initially empty`() {
        val sdk = BrainSDK()
        assertTrue(sdk.history.value.isEmpty())
    }

    @Test
    fun `endSession saves to history for premium user`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val historyRepo = MockRemoteHistoryRepository()
        val sdk = BrainSDK(
            coroutineContext = testDispatcher,
            historyRepository = historyRepo
        )

        sdk.completeOnboarding(createProfile(isPremium = true))
        sdk.startSoloGame(scenarioId = "coffee-shop", userRoleId = "customer")
        advanceUntilIdle()

        sdk.endSession()
        advanceUntilIdle()

        assertEquals(1, sdk.history.value.size)
        assertEquals("Ordering Coffee", sdk.history.value.first().scenarioTitle)
        assertEquals("es", sdk.history.value.first().targetLanguage)
    }

    @Test
    fun `endSession does not save to history for free user`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val historyRepo = MockRemoteHistoryRepository()
        val sdk = BrainSDK(
            coroutineContext = testDispatcher,
            historyRepository = historyRepo
        )

        sdk.completeOnboarding(createProfile(isPremium = false))
        sdk.startSoloGame(scenarioId = "coffee-shop", userRoleId = "customer")
        advanceUntilIdle()

        sdk.endSession()
        advanceUntilIdle()

        assertTrue(sdk.history.value.isEmpty())
    }

    @Test
    fun `multiple sessions accumulate in history for premium user`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val historyRepo = MockRemoteHistoryRepository()
        val profile = createProfile(isPremium = true)

        val sdk1 = BrainSDK(
            coroutineContext = testDispatcher,
            historyRepository = historyRepo
        )
        sdk1.completeOnboarding(profile)
        sdk1.startSoloGame(scenarioId = "coffee-shop", userRoleId = "customer")
        advanceUntilIdle()
        sdk1.endSession()
        advanceUntilIdle()

        val sdk2 = BrainSDK(
            coroutineContext = testDispatcher,
            historyRepository = historyRepo
        )
        sdk2.completeOnboarding(profile)
        sdk2.startSoloGame(scenarioId = "the-heist", userRoleId = "detective")
        advanceUntilIdle()
        sdk2.endSession()
        advanceUntilIdle()

        assertEquals(2, historyRepo.history.value.size)
    }

    @Test
    fun `history sessions contain dialog lines from the session`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val historyRepo = MockRemoteHistoryRepository()
        val sdk = BrainSDK(
            coroutineContext = testDispatcher,
            historyRepository = historyRepo
        )

        sdk.completeOnboarding(createProfile(isPremium = true))
        sdk.startSoloGame(scenarioId = "coffee-shop", userRoleId = "customer")
        advanceUntilIdle()

        sdk.generate()
        advanceUntilIdle()

        sdk.endSession()
        advanceUntilIdle()

        assertEquals(1, sdk.history.value.size)
        assertTrue(sdk.history.value.first().dialogLines.isNotEmpty())
    }

    @Test
    fun `mock repository returns FEATURE_LOCKED for free user directly`() = runTest {
        val repo = MockRemoteHistoryRepository()
        val session = HistorySession(
            sessionId = "s-1",
            scenarioTitle = "Test",
            timestamp = 1000L,
            targetLanguage = "es",
            durationSeconds = 60L,
            dialogLines = emptyList()
        )

        val result = repo.saveSession(session, createProfile(isPremium = false))
        assertTrue(result is HistorySaveResult.Error)
        assertEquals(HistoryErrorReason.FEATURE_LOCKED, (result as HistorySaveResult.Error).reason)
    }
}
