package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.HistoryErrorReason
import com.bablabs.bringabrainlanguage.domain.interfaces.HistoryRepository
import com.bablabs.bringabrainlanguage.domain.interfaces.HistorySaveResult
import com.bablabs.bringabrainlanguage.domain.models.HistorySession
import com.bablabs.bringabrainlanguage.domain.models.UserProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Simulates a remote backend that persists chat history.
 *
 * Business rule: only premium users may save history.
 * Non-premium users receive a [HistoryErrorReason.FEATURE_LOCKED] error,
 * mirroring a 403 Forbidden from the real API.
 *
 * Every operation includes a 500 ms artificial delay to emulate network latency.
 */
class MockRemoteHistoryRepository : HistoryRepository {

    private val sessions = mutableListOf<HistorySession>()
    private val _history = MutableStateFlow<List<HistorySession>>(emptyList())
    override val history: StateFlow<List<HistorySession>> = _history.asStateFlow()

    override suspend fun saveSession(
        session: HistorySession,
        userProfile: UserProfile
    ): HistorySaveResult {
        delay(500)

        if (!userProfile.isPremium) {
            return HistorySaveResult.Error(HistoryErrorReason.FEATURE_LOCKED)
        }

        sessions.add(session)
        _history.value = sessions.sortedByDescending { it.timestamp }.toList()
        return HistorySaveResult.Success(session)
    }

    override suspend fun getSessions(): List<HistorySession> {
        delay(500)
        return sessions.sortedByDescending { it.timestamp }.toList()
    }

    override suspend fun deleteSession(sessionId: String) {
        delay(500)
        sessions.removeAll { it.sessionId == sessionId }
        _history.value = sessions.sortedByDescending { it.timestamp }.toList()
    }

    override suspend fun clear() {
        delay(500)
        sessions.clear()
        _history.value = emptyList()
    }
}
