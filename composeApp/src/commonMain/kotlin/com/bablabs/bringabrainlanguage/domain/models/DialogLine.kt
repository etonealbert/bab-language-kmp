package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class DialogLine(
    val id: String,
    val speakerId: String,
    val roleName: String,
    val textNative: String,
    val textTranslated: String,
    val timestamp: Long,
    val assignedToPlayerId: String = "",
    val visibility: LineVisibility = LineVisibility.COMMITTED,
    val pronunciationResult: PronunciationResult? = null
)
