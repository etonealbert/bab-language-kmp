package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class Participant(
    val id: String,
    val name: String,
    val role: Role? = null,
    val isHost: Boolean = false
)

@Serializable
data class Role(
    val id: String,
    val name: String,
    val description: String
)
