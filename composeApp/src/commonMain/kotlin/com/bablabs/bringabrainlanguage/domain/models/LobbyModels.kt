package com.bablabs.bringabrainlanguage.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class LobbyPlayer(
    val peerId: String,
    val displayName: String,
    val assignedRole: Role?,
    val isReady: Boolean,
    val connectionQuality: ConnectionQuality = ConnectionQuality.GOOD
)

@Serializable
enum class ConnectionQuality { 
    EXCELLENT, 
    GOOD, 
    FAIR, 
    POOR 
}

@Serializable
data class ConnectedPeer(
    val peerId: String,
    val displayName: String,
    val isHost: Boolean = false,
    val lastSeen: Long = 0L
)
