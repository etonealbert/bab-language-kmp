package com.bablabs.bringabrainlanguage.infrastructure.repositories

import com.bablabs.bringabrainlanguage.domain.interfaces.DialogHistoryRepository
import com.bablabs.bringabrainlanguage.domain.interfaces.SavedSession
import com.bablabs.bringabrainlanguage.domain.models.SessionState
import com.bablabs.bringabrainlanguage.domain.models.SessionStats
import kotlinx.datetime.Clock

class InMemoryDialogHistoryRepository : DialogHistoryRepository {
    
    private val sessions = mutableMapOf<String, SavedSession>()
    
    override suspend fun saveSession(state: SessionState, stats: SessionStats?) {
        val now = Clock.System.now().toEpochMilliseconds()
        val sessionId = stats?.sessionId ?: "session_$now"
        val scenario = state.scenario
        
        val savedSession = SavedSession(
            id = sessionId,
            scenarioName = scenario?.name ?: "Unknown",
            playedAt = stats?.startedAt ?: now,
            durationMinutes = stats?.let { 
                ((it.endedAt ?: it.startedAt) - it.startedAt).toInt() / 60000 
            } ?: 0,
            dialogLines = state.dialogHistory,
            stats = stats,
            report = null
        )
        
        sessions[sessionId] = savedSession
    }
    
    override suspend fun getSessions(limit: Int): List<SavedSession> =
        sessions.values
            .sortedByDescending { it.playedAt }
            .take(limit)
    
    override suspend fun getSession(id: String): SavedSession? = sessions[id]
    
    override suspend fun deleteSession(id: String) {
        sessions.remove(id)
    }
    
    override suspend fun clear() {
        sessions.clear()
    }
}
