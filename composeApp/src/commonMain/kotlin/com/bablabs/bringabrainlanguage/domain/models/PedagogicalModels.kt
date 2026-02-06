package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class PlotTwist(
    val id: String,
    val description: String,
    val visualAsset: String?,
    val triggeredAt: Long,
    val affectsPlayers: List<String>,
    val expiresAt: Long?
)

@Serializable
data class SecretObjective(
    val playerId: String,
    val objective: String,
    val visibleToOthers: Boolean = false,
    val completedAt: Long? = null
)

@Serializable
data class PlayerContext(
    val playerId: String,
    val proficiencyLevel: CEFRLevel,
    val vocabularyHints: List<String>,
    val grammarFocus: List<String>?,
    val secretObjective: SecretObjective?,
    val preferredComplexity: Int
)

@Serializable
data class NudgeRequest(
    val playerId: String,
    val silenceDurationMs: Long,
    val requestedLevel: HintLevel,
    val requestedAt: Long
)
