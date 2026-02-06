package com.bablabs.bringabrainlanguage.domain.interfaces

import com.bablabs.bringabrainlanguage.domain.models.DialogLine
import com.bablabs.bringabrainlanguage.domain.models.Feedback
import com.bablabs.bringabrainlanguage.domain.models.SessionState
import com.bablabs.bringabrainlanguage.domain.models.SessionStats
import kotlinx.serialization.Serializable

interface DialogHistoryRepository {
    suspend fun saveSession(state: SessionState, stats: SessionStats?)
    suspend fun getSessions(limit: Int = 20): List<SavedSession>
    suspend fun getSession(id: String): SavedSession?
    suspend fun deleteSession(id: String)
    suspend fun clear()
}

@Serializable
data class SavedSession(
    val id: String,
    val scenarioName: String,
    val playedAt: Long,
    val durationMinutes: Int,
    val dialogLines: List<DialogLine>,
    val stats: SessionStats?,
    val report: Feedback.SessionReport?
)
