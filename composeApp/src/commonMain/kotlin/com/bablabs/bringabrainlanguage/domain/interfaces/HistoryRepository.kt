package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.HistorySession
import com.bablabs.bringabrainlanguage.domain.models.UserProfile
import kotlinx.coroutines.flow.StateFlow

interface HistoryRepository {
    val history: StateFlow<List<HistorySession>>
    suspend fun saveSession(session: HistorySession, userProfile: UserProfile): HistorySaveResult
    suspend fun getSessions(): List<HistorySession>
    suspend fun deleteSession(sessionId: String)
    suspend fun clear()
}

sealed class HistorySaveResult {
    data class Success(val session: HistorySession) : HistorySaveResult()
    data class Error(val reason: HistoryErrorReason) : HistorySaveResult()
}

enum class HistoryErrorReason {
    FEATURE_LOCKED,
    NETWORK_ERROR
}
