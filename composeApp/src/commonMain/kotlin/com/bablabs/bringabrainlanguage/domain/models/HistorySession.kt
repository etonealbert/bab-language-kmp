package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class HistorySession(
    val sessionId: String,
    val scenarioTitle: String,
    val timestamp: Long,
    val targetLanguage: LanguageCode,
    val durationSeconds: Long,
    val dialogLines: List<DialogLine>
)
